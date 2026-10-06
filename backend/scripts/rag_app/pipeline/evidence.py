"""Fault-isolated evidence resolution; generation never chooses retrieval scope."""
from dataclasses import dataclass, field
import logging
import time

from rag_app.pipeline.evidence_gate import EvidenceGate, GateDecision
from rag_app.pipeline.query_classifier import QueryType
from rag_app.pipeline.planning import QueryPlan
from rag_app.retrieval.retriever import RetrievalResult, RetrievalQuality
from rag_app.tools.search_tool import SearchExecution, SearchQueryRewriter

logger = logging.getLogger(__name__)


@dataclass
class EvidenceBundle:
    structured_text: str = ''
    retrieved: RetrievalResult = field(default_factory=lambda: RetrievalResult([], [], [], [], RetrievalQuality(confidence_level='none')))
    search_results: list = field(default_factory=list)
    search_execution: SearchExecution = field(default_factory=SearchExecution)
    tool_trace: list = field(default_factory=list)
    decision: GateDecision = field(default_factory=lambda: GateDecision(False, 'no_evidence'))
    direct_answer: str = ''

    def prepared(self, plan: QueryPlan):
        retrieval = self.retrieved
        execution = self.search_execution
        used_tools = (['sql'] if self.structured_text else []) + (['rag'] if retrieval.documents else [])
        if execution.attempted:
            used_tools.append('web_search')
        return {
            'classification': plan.classification, 'question': plan.question,
            'resolved_question': plan.resolved_question, 'structured_text': self.structured_text,
            'direct_answer': self.direct_answer, 'refusal_message': self.decision.message,
            'documents': retrieval.documents, 'metadatas': retrieval.metadatas,
            'distances': retrieval.distances, 'rerank_scores': retrieval.rerank_scores,
            'search_results': self.search_results, 'used_tools': used_tools, 'tool_trace': self.tool_trace,
            'allowed': self.decision.allowed,
            'query_plan': plan.diagnostics(),
            'search_execution': {'attempted': execution.attempted, 'status': execution.status,
                                 'reasons': execution.reasons, 'providers': execution.attempted_providers,
                                 'successful': execution.successful_provider, 'result_count': len(self.search_results),
                                 'elapsed_ms': execution.elapsed_ms},
            'retrieval_quality': {'candidate_count': retrieval.quality.candidate_count,
                                  'accepted_count': retrieval.quality.accepted_count,
                                  'rejected_count': retrieval.quality.rejected_count,
                                  'best_distance': round(retrieval.quality.best_vector_distance, 3),
                                  'confidence': retrieval.quality.confidence_level, 'reason': retrieval.quality.reason},
        }


class EvidenceResolver:
    def __init__(self, settings, structured, retriever, search, gate=None, rewriter=None, planner=None):
        self.settings = settings
        self.structured = structured
        self.retriever = retriever
        self.search = search
        self.gate = gate or EvidenceGate()
        self.rewriter = rewriter or SearchQueryRewriter()
        self.planner = planner

    def resolve(self, plan: QueryPlan) -> EvidenceBundle:
        bundle = EvidenceBundle()
        bundle.tool_trace.append({'tool': 'query_planner', **plan.diagnostics()})
        self._structured(plan, bundle)
        if plan.can_answer_directly(bundle.structured_text):
            bundle.direct_answer = bundle.structured_text
            bundle.decision = GateDecision(True, 'structured_direct')
            return bundle

        # Scoped games and current information use authoritative sources first.
        search_first = self.settings.search_enabled and plan.web_first
        if search_first:
            self._search(plan, bundle)
        # A verified game already contains factual evidence; embeddings add no value here.
        if not (plan.historical_game and bundle.search_results) and not search_first:
            self._retrieve(plan, bundle)
        elif plan.historical_game and not bundle.search_results:
            self._retrieve(plan, bundle)
        else:
            bundle.retrieved.quality.reason = 'authoritative_web_first'
            bundle.retrieved.quality.confidence_level = 'not_needed'
            bundle.tool_trace.append({'tool': 'rag', 'skipped': True, 'reason': 'authoritative_web_first'})

        if not search_first and self.settings.search_enabled and (
            plan.force_web or plan.classification.needs_web_search or
            (plan.historical_game and (not bundle.structured_text or plan.wants_game_details)) or
            (not bundle.structured_text and not bundle.retrieved.documents and getattr(self.settings, 'allow_general_web_search', False))
        ):
            self._search(plan, bundle)

        if plan.historical_game and any(getattr(item, 'facts', None) for item in bundle.search_results):
            # The archive selects the requested game. Old SQL/vector rows must not introduce another date.
            bundle.structured_text = ''
            bundle.retrieved = RetrievalResult([], [], [], [], RetrievalQuality(confidence_level='not_needed', reason='verified_game_source'))

        self._supplement(plan, bundle)

        documents = bundle.retrieved.documents + [item.snippet or item.title for item in bundle.search_results]
        bundle.decision = self.gate.assess(bundle.structured_text, documents, plan.resolved_question,
            search_attempted=bundle.search_execution.attempted, search_success=bool(bundle.search_results))
        return bundle

    def _structured(self, plan, bundle):
        started = time.monotonic()
        status = 'empty'
        try:
            if plan.classification.needs_structured_data:
                bundle.structured_text = self.structured.search(plan.classification, plan.top_k, plan.resolved_question)
            if not bundle.structured_text and plan.classification.query_type != QueryType.MATCH:
                bundle.structured_text = self.structured.search_by_keywords(plan.resolved_question, plan.top_k)
            status = 'success' if bundle.structured_text else 'empty'
        except Exception as exc:
            status = 'failed'
            logger.warning('SQL evidence unavailable: %s', type(exc).__name__)
        bundle.tool_trace.append({'tool': 'sql', 'query_type': plan.classification.query_type.value,
                                  'status': status, 'elapsed_ms': round((time.monotonic() - started) * 1000)})

    def _retrieve(self, plan, bundle, extra_queries=()):
        started = time.monotonic()
        try:
            if getattr(self.retriever, 'ready', True) is False:
                bundle.retrieved.quality.reason = 'models_warming'
            else:
                if callable(getattr(type(self.retriever), 'retrieve_plan', None)):
                    result = self.retriever.retrieve_plan(plan, extra_queries=extra_queries)
                else:
                    result = self.retriever.retrieve(plan.resolved_question, plan.top_k, plan.classification)
                if plan.game_scope:
                    accepted = [i for i, metadata in enumerate(result.metadatas) if plan.accepts_game_document(metadata)]
                    result.quality.rejected_count += len(result.documents) - len(accepted)
                    result.quality.accepted_count = len(accepted)
                    result = RetrievalResult([result.documents[i] for i in accepted], [result.metadatas[i] for i in accepted],
                        [result.distances[i] for i in accepted], [result.rerank_scores[i] for i in accepted if i < len(result.rerank_scores)], result.quality)
                    if not accepted:
                        result.quality.reason = 'no_scope_matching_evidence'
                        result.quality.confidence_level = 'none'
                bundle.retrieved = result
        except Exception as exc:
            logger.warning('Vector evidence unavailable: %s', type(exc).__name__)
            bundle.retrieved.quality.reason = 'retrieval_error'
        quality = bundle.retrieved.quality
        bundle.tool_trace.append({'tool': 'rag', 'accepted': quality.accepted_count, 'rejected': quality.rejected_count,
            'confidence': quality.confidence_level, 'reason': quality.reason,
            'elapsed_ms': round((time.monotonic() - started) * 1000)})

    def _supplement(self, plan, bundle):
        # Synthetic text stays inside this stack frame and is never persisted or cited.
        if not (self.planner and getattr(self.settings, 'hyde_enabled', True) and plan.semantic
                and not plan.game_scope and not plan.web_first and not bundle.search_results
                and getattr(self.retriever, 'ready', True) is not False
                and callable(getattr(type(self.retriever), 'retrieve_plan', None))):
            return
        if any(meta.get('type') == 'news' and score >= .4 for meta, score in zip(
                bundle.retrieved.metadatas, bundle.retrieved.rerank_scores)):
            return
        started = time.monotonic()
        hypothesis, reason = self.planner.hypothesis(plan)
        if hypothesis:
            original = bundle.retrieved
            self._retrieve(plan, bundle, extra_queries=(hypothesis,))
            if not bundle.retrieved.documents:
                bundle.retrieved = original
        bundle.tool_trace.append({'tool': 'hyde', 'triggered': True, 'reason': reason,
            'accepted': bundle.retrieved.quality.accepted_count,
            'elapsed_ms': round((time.monotonic() - started) * 1000)})

    def _search(self, plan, bundle):
        query = plan.resolved_question
        if not plan.game_scope:
            candidates = plan.retrieval_queries or self.rewriter.rewrite(query)
            query = next((candidate for candidate in candidates if candidate != query), query)
        try:
            if plan.game_scope:
                results, execution = self.search.search(query, self.settings.search_top_k, context=plan.game_scope)
            else:
                results, execution = self.search.search(query, self.settings.search_top_k)
            bundle.search_execution = execution
            # Even adapters returning an empty diagnostic must record that a search was attempted.
            bundle.search_execution.attempted = True
            seen = set()
            for result in results:
                if plan.historical_game and (result.provider != 'espn_archive' or not plan.accepts_game_fact(result.facts)):
                    continue
                if not plan.game_scope and not plan.accepts_document(result.title + '\n' + result.snippet,
                        {'type': 'news', 'published_at': result.published_at}):
                    continue
                if result.url not in seen:
                    seen.add(result.url)
                    bundle.search_results.append(result)
            if results and not bundle.search_results:
                bundle.search_execution.status = 'empty'
                bundle.search_execution.reasons.append('no_scope_matching_evidence')
        except Exception as exc:
            logger.warning('Web evidence unavailable: %s', type(exc).__name__)
            bundle.search_execution = SearchExecution(attempted=True, status='failed', reasons=['search_error'])
        execution = bundle.search_execution
        bundle.tool_trace.append({'tool': 'web_search', 'status': execution.status,
            'providers_tried': execution.attempted_providers, 'successful_provider': execution.successful_provider,
            'result_count': len(bundle.search_results), 'elapsed_ms': execution.elapsed_ms,
            'provider_results': [{'provider': item.provider, 'status': item.status, 'count': item.result_count, 'error': item.error}
                                 for item in execution.provider_results]})
