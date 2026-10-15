"""Cancellation must leave the last published model, graph and index usable."""
from dataclasses import replace
import time
from unittest.mock import MagicMock, Mock

import pytest

from discovery_app.config import Settings
from discovery_app.domain import FEATURE_NAMES, FEATURE_VERSION
from discovery_app.learning import train
from discovery_app.samples import TrainingGate
from discovery_app.worker import Worker


class StopWorker(BaseException):
    """Stop the test consumer only after its one queued job has finished."""


@pytest.fixture
def consumer(tmp_path):
    worker=Worker(replace(Settings(),runtime=tmp_path,embedding_enabled=False))
    worker.db=MagicMock(); worker.index=MagicMock(); worker.cache=MagicMock()
    worker.schedule=Mock(); worker.snapshot=Mock(return_value={'indexed':1})
    worker.index.alias='discovery-test'
    worker.index.admin.indices.get_alias.return_value={'previous-index':{}}
    worker.db.connection.return_value.__enter__.return_value.cursor.return_value.__enter__.return_value.fetchone.return_value={'acquired':1}
    state={'cancelled':False,'job':None,'targets':['previous-index','rollback-index']}
    worker.db.control.side_effect=lambda key,default=None:state['targets'] if key=='indices' else default
    def rows(sql,args=()):
        if 'cancel_requested' in sql: return [{'cancel_requested':state['cancelled']}]
        if "status='QUEUED'" in sql:
            if state['job'] is None: raise StopWorker()
            job=state['job']; state['job']=None; return [job]
        if 'discovery_events e LEFT JOIN' in sql:
            return [{'user_id':user,'item_key':key} for user in (1,2) for key in ('NEWS:1','NEWS:2')]
        return []
    worker.db.rows.side_effect=rows
    return worker,state


def execute(consumer,kind):
    worker,state=consumer
    state['job']={'id':'test-job','kind':kind,'detail_json':'{}'}
    with pytest.raises(StopWorker): worker.jobs()
    completed=[call.args[1] for call in worker.db.execute.call_args_list
               if call.args[0].startswith('UPDATE discovery_jobs SET status=%s,progress=100')]
    assert len(completed)==1
    return completed[0][0]


def test_cancel_during_training_does_not_register_or_publish_the_candidate(consumer,monkeypatch):
    worker,state=consumer
    def training_finishes(*args,**kwargs):
        state['cancelled']=True
        return {'version':'candidate','status':'CANDIDATE','sha256':'test','metrics':{},'trainedUntil':time.time()}
    monkeypatch.setattr('discovery_app.worker.train',training_finishes)
    assert execute(consumer,'TRAIN')=='CANCELLED'
    assert not any(call.args[0].startswith('INSERT INTO discovery_models') for call in worker.db.execute.call_args_list)
    worker.db.set_control.assert_not_called()


def test_cancel_after_graph_upload_keeps_the_previous_active_generation(consumer):
    worker,state=consumer
    worker.cache.pipeline.return_value.execute.side_effect=lambda:state.update(cancelled=True)
    assert execute(consumer,'ITEMCF')=='CANCELLED'
    worker.cache.pipeline.return_value.execute.assert_called_once()
    worker.cache.setex.assert_not_called()


def test_cancel_after_rebuild_refresh_keeps_the_original_alias_and_write_targets(consumer):
    worker,state=consumer
    worker.index.admin.indices.refresh.side_effect=lambda **kwargs:state.update(cancelled=True)
    assert execute(consumer,'REINDEX')=='CANCELLED'
    worker.index.admin.indices.update_aliases.assert_not_called()
    assert worker.db.set_control.call_args.args==('indices',state['targets'])
    assert set(state['targets']).issubset(worker.db.set_control.call_args_list[0].args[1])


@pytest.mark.parametrize('kind',['TRAIN','ITEMCF','REINDEX'])
def test_jobs_without_cancellation_still_finish_successfully(consumer,monkeypatch,kind):
    monkeypatch.setattr('discovery_app.worker.train',lambda *args,**kwargs:{'status':'INSUFFICIENT_DATA'})
    assert execute(consumer,kind)=='SUCCEEDED'


def test_cancelling_a_queued_job_never_starts_training(consumer,monkeypatch):
    consumer[1]['cancelled']=True
    training=Mock(); monkeypatch.setattr('discovery_app.worker.train',training)
    assert execute(consumer,'TRAIN')=='CANCELLED'
    training.assert_not_called()


def test_native_training_checks_cancellation_before_a_boosting_iteration(tmp_path,monkeypatch):
    from lightgbm import LGBMRanker
    entered=[]; original=LGBMRanker.fit
    def fit(self,*args,**kwargs):
        entered.append(True)
        return original(self,*args,**kwargs)
    monkeypatch.setattr(LGBMRanker,'fit',fit)
    def cancelled():
        if entered: raise InterruptedError('cancelled during fit')
    samples=[]; now=time.time()
    for group in range(20):
        for position in range(4):
            relevant=position%2==0
            values={name:0.0 for name in FEATURE_NAMES}; values['quality']=float(relevant)
            samples.append({'requestId':f'cancel-{group}','itemKey':f'NEWS:{position+1}','userId':group+1,
                'servedAt':now-(20-group)*86400,'scene':'NEWS_FEED','position':position,
                'featureVersion':FEATURE_VERSION,'label':3 if relevant else 0,**values})
    with pytest.raises(InterruptedError,match='cancelled during fit'):
        train(samples,tmp_path,gate=TrainingGate(days=1,impressions=10,positives=5,users=5),check_cancel=cancelled)
    assert entered and not list(tmp_path.iterdir())
