"""At-least-once CDC consumer and restartable indexing, embedding and training workers."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import logging
import time
import uuid

from confluent_kafka import Consumer
import redis

from discovery_app.config import Settings
from discovery_app.database import Database
from discovery_app.domain import Document, FEATURE_VERSION, item_key, timestamp
from discovery_app.index import Index, Embedder
from discovery_app.learning import train, Model
from discovery_app.profiles import Profiles, build_itemcf
from discovery_app.projection import Projection, TABLES, KINDS
from discovery_app.samples import build_samples

log = logging.getLogger(__name__)


class Worker:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.db, self.index = Database(settings), Index(settings)
        self.cache = redis.Redis.from_url(settings.redis_url, decode_responses=True, socket_timeout=.2, socket_connect_timeout=.2)
        self.projection = Projection(self.db)
        self.profiles = Profiles(self.db, self.cache, self.index)
        self.embedder = Embedder(settings)

    def targets(self): return self.db.control('indices', [self.index.alias])

    def sync(self, key):
        # Per-key DB lock spans source projection and all index writes, including concurrent reindex jobs.
        with self.db.connection() as connection, connection.cursor() as cursor:
            lock = 'discovery:index:' + key
            cursor.execute('SELECT GET_LOCK(%s,10) AS acquired', (lock,))
            if cursor.fetchone()['acquired'] != 1: raise TimeoutError('Index key is busy')
            try:
                cursor.execute('SELECT revision,content_hash FROM discovery_index_state WHERE item_key=%s FOR UPDATE', (key,))
                old = cursor.fetchone()
                doc = self.projection.project(key, cursor) or Document(key, '', deleted=True)
                doc.revision = old['revision'] + 1 if old else 1
                content_hash = doc.content_hash()
                targets = self.targets()
                vector = None
                if len(targets)>1 and not doc.deleted:
                    source = self.index.get(key)
                    if source and source.get('embedding_hash') == content_hash: vector=source.get('embedding')
                for target in targets: self.index.write(doc, target, vector)
                cursor.execute('INSERT INTO discovery_index_state(item_key,content_hash,revision,deleted,updated_at) '
                               'VALUES(%s,%s,%s,%s,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE content_hash=VALUES(content_hash),'
                               'revision=VALUES(revision),deleted=VALUES(deleted),updated_at=VALUES(updated_at)',
                               (key, content_hash, doc.revision, doc.deleted))
                if doc.deleted:
                    cursor.execute('DELETE FROM discovery_embedding_jobs WHERE item_key=%s', (key,))
                elif not old or old['content_hash'] != content_hash:
                    cursor.execute('INSERT INTO discovery_embedding_jobs(item_key,content_hash,next_attempt) '
                                   'VALUES(%s,%s,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE content_hash=VALUES(content_hash),'
                                   'attempts=0,next_attempt=VALUES(next_attempt),last_error=NULL', (key, content_hash))
                connection.commit()
            finally:
                cursor.execute('SELECT RELEASE_LOCK(%s)', (lock,))

    def process(self, envelope):
        value = envelope.get('payload', envelope)
        if not value: return
        table = value.get('source', {}).get('table', '')
        row = value.get('after') or value.get('before')
        if not row: return
        if table in KINDS:
            if table in ('players', 'teams'): self.projection.refresh_entities(force=True)
            self.sync(f"{KINDS[table]}:{row['id']}")
            if table in ('players', 'teams'):
                self.enqueue('REFRESH_ENTITIES', {'entity': f"{KINDS[table]}:{row['id']}"}, coalesce=True)
        elif table == 'discovery_entity_aliases':
            self.projection.refresh_entities(force=True)
            self.sync(row['item_key'])
            self.enqueue('REFRESH_ENTITIES', {}, coalesce=True)
        elif table == 'discovery_events' and value.get('after'):
            events = self.db.rows('SELECT * FROM discovery_events WHERE event_id=%s', (row['event_id'],))
            if events: self.profiles.consume(events[0])

    def consume(self):
        self.index.ensure()
        settings = self.settings
        tables = list(KINDS) + ['discovery_events', 'discovery_entity_aliases']
        consumer = Consumer({'bootstrap.servers': settings.kafka, 'group.id': 'nba-discovery-v1',
                             'enable.auto.commit': False, 'auto.offset.reset': 'earliest',
                             'max.poll.interval.ms': 300000, 'session.timeout.ms': 30000})
        consumer.subscribe([f'{settings.topic_prefix}.{settings.db_name}.{table}' for table in tables])
        last_health = 0
        try:
            while True:
                message = consumer.poll(1)
                if not message: continue
                if message.error():
                    if message.error().fatal(): raise RuntimeError(str(message.error()))
                    if time.monotonic()-getattr(self, '_last_broker_warning', 0)>30:
                        log.warning('Waiting for CDC topics/broker: %s', message.error())
                        self._last_broker_warning=time.monotonic()
                    continue
                if message.value():
                    payload = json.loads(message.value())
                    while True:
                        try: self.process(payload); break
                        except Exception:
                            log.exception('CDC processing failed; offset remains uncommitted')
                            time.sleep(2)
                consumer.commit(message=message, asynchronous=False)
                if time.monotonic()-last_health > 5:
                    value = payload.get('payload', payload) if message.value() else {}
                    self.db.set_control('cdc', {'at': time.time(), 'sourceAt': value.get('source', {}).get('ts_ms'),
                                               'topic': message.topic(), 'partition': message.partition(), 'offset': message.offset()})
                    last_health = time.monotonic()
        finally: consumer.close()

    def enqueue(self, kind, detail=None, user=0, coalesce=False):
        if coalesce:
            rows = self.db.rows("SELECT id FROM discovery_jobs WHERE kind=%s AND status='QUEUED' LIMIT 1", (kind,))
            if rows: return rows[0]['id']
        identifier = str(uuid.uuid4())
        self.db.execute("INSERT INTO discovery_jobs(id,kind,status,requested_by,detail_json,created_at) "
                        "VALUES(%s,%s,'QUEUED',%s,%s,UTC_TIMESTAMP(6))", (identifier, kind, user, json.dumps(detail or {})))
        return identifier

    def progress(self, job, value):
        self.db.execute('UPDATE discovery_jobs SET progress=%s WHERE id=%s', (min(99, value), job))
        self.check_cancel(job)

    def check_cancel(self, job):
        row = self.db.rows('SELECT cancel_requested FROM discovery_jobs WHERE id=%s', (job,))[0]
        if row['cancel_requested']: raise InterruptedError('Cancelled by administrator')

    def rebuild(self, job):
        self.index.ensure()
        previous = list(self.index.admin.indices.get_alias(name=self.index.alias))
        previous_targets = self.db.control('indices', previous)
        name = self.index.alias + '-' + datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S%f')
        self.index.create(name)
        targets = list(dict.fromkeys(previous + previous_targets + [name]))
        self.db.set_control('indices', targets)
        try:
            self.snapshot(job)
            self.index.admin.indices.refresh(index=name)
            self.check_cancel(job)
            actions = [{'remove': {'index': old, 'alias': self.index.alias}} for old in previous]
            actions.append({'add': {'index': name, 'alias': self.index.alias}})
            self.index.admin.indices.update_aliases(body={'actions': actions})
            # Keep the previous generation current so rollback is a single atomic alias operation.
            self.db.set_control('indexPrevious', previous)
            self.db.set_control('indices', [name] + previous[:1])
            return {'index': name, 'previous': previous}
        except Exception:
            self.db.set_control('indices', previous_targets)
            raise

    def snapshot(self, job, kinds=None):
        kinds = kinds or list(TABLES)
        count = 0
        for position, kind in enumerate(kinds):
            after = 0
            while keys := self.projection.keys(kind, after):
                for key in keys: self.sync(key)
                count += len(keys); after = item_key(keys[-1])[1]
                self.progress(job, int(90*position/len(kinds)))
        return {'indexed': count}

    def rollback_index(self):
        previous = self.db.control('indexPrevious', [])
        if not previous: raise ValueError('No previous index generation')
        current = list(self.index.admin.indices.get_alias(name=self.index.alias))
        self.index.admin.indices.update_aliases(body={'actions':
            [{'remove': {'index': name, 'alias': self.index.alias}} for name in current] +
            [{'add': {'index': previous[0], 'alias': self.index.alias}}]})
        self.db.set_control('indexPrevious', current)
        self.db.set_control('indices', [previous[0]] + current[:1])
        return {'index': previous[0]}

    def training(self, job=None):
        checkpoint = (lambda: self.check_cancel(job)) if job else (lambda: None)
        checkpoint()
        requests = self.db.rows('SELECT r.* FROM discovery_requests r JOIN discovery_preferences p ON p.user_id=r.user_id '
                               'WHERE r.created_at>=UTC_TIMESTAMP()-INTERVAL 90 DAY AND p.personalized=TRUE '
                               "AND r.created_at<=UTC_TIMESTAMP()-INTERVAL 1 DAY AND r.scene<>'SEARCH' "
                               'AND (p.cleared_at IS NULL OR r.created_at>p.cleared_at) '
                               'AND EXISTS (SELECT 1 FROM discovery_events e WHERE e.request_id=r.request_id '
                               "AND e.user_id=r.user_id AND e.event_type='IMPRESSION' "
                               'AND e.occurred_at>=r.created_at AND e.occurred_at<=r.created_at+INTERVAL 1 DAY '
                               'AND e.received_at>=UTC_TIMESTAMP()-INTERVAL 90 DAY) '
                               'ORDER BY r.created_at LIMIT 500000')
        checkpoint()
        events=[]
        for offset in range(0,len(requests),500):
            checkpoint()
            identifiers=[row['request_id'] for row in requests[offset:offset+500]]
            placeholders=','.join(['%s']*len(identifiers))
            events.extend(self.db.rows('SELECT * FROM discovery_events WHERE received_at>=UTC_TIMESTAMP()-INTERVAL 90 DAY '
                f'AND request_id IN ({placeholders}) ORDER BY received_at LIMIT %s',
                (*identifiers,2_000_001-len(events))))
            if len(events)>2_000_000:
                raise ValueError('Training event budget exceeded; keep the current model and reduce the training window')
        checkpoint()
        shaped_requests = [{'requestId': r['request_id'], 'userId': r['user_id'], 'scene': r['scene'],
                            'createdAt': r['created_at'], 'featureVersion': r['feature_version'],
                            'context': json.loads(r['context_json']), 'items': json.loads(r['items_json'])} for r in requests]
        shaped_events = [{'eventId': e['event_id'], 'requestId': e['request_id'], 'userId': e['user_id'],
                          'itemKey': e['item_key'], 'eventType': e['event_type'], 'dwellMs': e['dwell_ms'],
                          'occurredAt': e['occurred_at']} for e in events]
        samples = build_samples(shaped_requests, shaped_events, now=time.time())
        checkpoint()
        result = train(samples, self.settings.runtime / 'models', check_cancel=checkpoint)
        checkpoint()
        if result.get('version'):
            self.db.execute('INSERT INTO discovery_models(version,status,feature_version,artifact_path,artifact_sha256,'
                            'metrics_json,trained_until,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,UTC_TIMESTAMP(6))',
                            (result['version'], result['status'], FEATURE_VERSION, 'models/'+result['version'], result['sha256'],
                             json.dumps(result['metrics']), datetime.fromtimestamp(result['trainedUntil'], timezone.utc).replace(tzinfo=None)))
        self.db.set_control('training', result)
        return result

    def publish(self, detail):
        traffic = detail.get('traffic', 0)
        if traffic not in (0, 5, 20, 50): raise ValueError('Unsupported rollout percentage')
        version = detail.get('version', '')
        rows = self.db.rows("SELECT * FROM discovery_models WHERE version=%s AND status='CANDIDATE'", (version,))
        if not rows: raise ValueError('Only evaluated candidate models can be published')
        model = Model(self.settings.runtime / rows[0]['artifact_path'])
        if model.metadata['sha256'] != rows[0]['artifact_sha256']: raise ValueError('Stored model checksum mismatch')
        old = self.db.control('model', {})
        if traffic:
            shadow = self.db.control('shadow', {})
            if shadow.get('version') != version or not shadow.get('successful'):
                raise ValueError('Run this candidate in shadow mode before enabling traffic')
            if old.get('version') != version or traffic not in (old.get('traffic', 0), {0:5, 5:20, 20:50, 50:50}[old.get('traffic', 0)]):
                raise ValueError('Rollout must progress through shadow, 5%, 20%, 50%')
        if old.get('version') != version: self.db.set_control('modelPrevious', old)
        value = {'version': version, 'traffic': traffic, 'experiment': 'discovery-v1', 'at': time.time()}
        self.db.set_control('model', value)
        return value

    def jobs(self):
        self.index.ensure()
        # One job worker per DB. A crash releases the advisory lock; RUNNING jobs are retried explicitly.
        with self.db.connection() as lease, lease.cursor() as lease_cursor:
            lease_cursor.execute("SELECT GET_LOCK('discovery:jobs',0) AS acquired")
            if lease_cursor.fetchone()['acquired'] != 1: raise RuntimeError('Another jobs worker is already running')
            self.db.execute("UPDATE discovery_jobs SET status='FAILED',finished_at=UTC_TIMESTAMP(6),"
                            "detail_json=%s WHERE status='RUNNING'", (json.dumps({'error': 'Worker restarted; retry this job'}),))
            while True:
                self.schedule()
                rows = self.db.rows("SELECT * FROM discovery_jobs WHERE status='QUEUED' ORDER BY created_at LIMIT 1")
                if not rows: time.sleep(1); continue
                job = rows[0]; identifier = job['id']
                self.db.execute("UPDATE discovery_jobs SET status='RUNNING',started_at=UTC_TIMESTAMP(6) WHERE id=%s", (identifier,))
                try:
                    self.progress(identifier, 0)
                    kind, detail = job['kind'], json.loads(job['detail_json'])
                    if kind == 'REINDEX': result = self.rebuild(identifier)
                    elif kind == 'SNAPSHOT': result = self.snapshot(identifier)
                    elif kind == 'REFRESH_ENTITIES':
                        self.projection.refresh_entities(force=True)
                        result = self.snapshot(identifier, ['NEWS', 'POST', 'MATCH'])
                    elif kind == 'ITEMCF': result = build_itemcf(self.db, self.cache, check_cancel=lambda: self.check_cancel(identifier))
                    elif kind == 'TRAIN': result = self.training(identifier)
                    elif kind == 'PUBLISH': result = self.publish(detail)
                    elif kind == 'ROLLBACK_INDEX': result = self.rollback_index()
                    elif kind == 'ROLLBACK_MODEL':
                        result = self.db.control('modelPrevious', {})
                        self.db.set_control('modelPrevious', self.db.control('model', {})); self.db.set_control('model', result)
                    else: raise ValueError('Unknown job kind')
                    status = 'SUCCEEDED'
                except Exception as error:
                    log.exception('Discovery job failed: %s', identifier)
                    status = 'CANCELLED' if isinstance(error, InterruptedError) else 'FAILED'
                    result = {'error': str(error)[:500]}
                self.db.execute('UPDATE discovery_jobs SET status=%s,progress=100,detail_json=%s,finished_at=UTC_TIMESTAMP(6) '
                                'WHERE id=%s', (status, json.dumps(result), identifier))

    def schedule(self):
        now = time.time()
        if now-getattr(self, '_last_schedule', 0)<60: return
        self._last_schedule=now
        for kind, interval in [('ITEMCF', 3600), ('TRAIN', 86400)]:
            last=self.db.control('schedule:'+kind, 0)
            if now-last>=interval:
                self.enqueue(kind, coalesce=True)
                self.db.set_control('schedule:'+kind, now)

    def embeddings(self):
        self.index.ensure()
        self.embedder.warm()
        if not self.settings.embedding_enabled: return
        while True:
            rows = self.db.rows('SELECT * FROM discovery_embedding_jobs WHERE next_attempt<=UTC_TIMESTAMP(6) '
                                'ORDER BY next_attempt LIMIT 16')
            if not rows: time.sleep(1); continue
            for row in rows:
                key, content_hash = row['item_key'], row['content_hash']
                try:
                    doc = self.projection.project(key)
                    if doc and doc.content_hash() != content_hash:
                        # A newer entity/alias projection may have queued work while this worker cached the old map.
                        self.projection.refresh_entities(force=True)
                        doc = self.projection.project(key)
                    if doc and doc.content_hash() == content_hash:
                        vector = self.embedder.encode(doc.title+' '+doc.title_en+' '+doc.text, wait=True)
                        for target in self.targets(): self.index.vector(key, content_hash, vector, target)
                    self.db.execute('DELETE FROM discovery_embedding_jobs WHERE item_key=%s AND content_hash=%s', (key, content_hash))
                except Exception as error:
                    delay = min(3600, 2 ** min(11, row['attempts']+1))
                    self.db.execute('UPDATE discovery_embedding_jobs SET attempts=attempts+1,last_error=%s,'
                                    'next_attempt=TIMESTAMPADD(SECOND,%s,UTC_TIMESTAMP(6)) WHERE item_key=%s AND content_hash=%s',
                                    (str(error)[:500], delay, key, content_hash))
                    log.warning('Embedding retry queued: %s', key)


def main():
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(name)s %(message)s')
    parser = argparse.ArgumentParser()
    parser.add_argument('role', choices=['cdc', 'jobs', 'embeddings'])
    role = parser.parse_args().role
    worker = Worker(Settings())
    {'cdc': worker.consume, 'jobs': worker.jobs, 'embeddings': worker.embeddings}[role]()


if __name__ == '__main__': main()
