"""
RAG 联网搜索模块 — 多 Provider 架构

免费 Provider:
1. EspnScoreboardProvider — ESPN 比赛数据
2. EspnNewsProvider — ESPN 新闻
3. GoogleNewsRssProvider — Google News RSS
4. BaiduSearchProvider — baidusearch (非官方)
5. SearXNGProvider — 可选自托管

所有 Provider 实现统一 SearchProvider 接口。
"""
from __future__ import annotations
from rag_app.domain.teams import TEAM_EN
from rag_app.domain.teams import TEAM_IDS
from rag_app.domain.players import PLAYER_EN
from rag_app.tools.cache import TTLCache

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta
from html import unescape
from html.parser import HTMLParser
import json
import re
import threading
import time as _time
from typing import Iterable
from urllib.parse import quote_plus
import urllib.request
import xml.etree.ElementTree as ET


# ══════════════════════════════════════════════════════════════
# 统一搜索结果
# ══════════════════════════════════════════════════════════════

@dataclass(frozen=True)
class SearchResult:
    title: str
    url: str
    snippet: str
    provider: str
    source_domain: str = ""
    published_at: str = ""
    fetched_at: str = ""
    relevance_score: float = 0.0
    query: str = ""
    facts: dict = field(default_factory=dict, compare=False)

    def to_dict(self) -> dict:
        return {
            "title": self.title,
            "url": self.url,
            "snippet": self.snippet,
            "provider": self.provider,
            "source_domain": self.source_domain,
            "published_at": self.published_at,
            "fetched_at": self.fetched_at,
            "relevance_score": self.relevance_score,
            "facts": self.facts,
        }


@dataclass
class ProviderResult:
    provider: str
    status: str = "pending"  # success / empty / failed / skipped
    result_count: int = 0
    error: str = ""
    elapsed_ms: int = 0


@dataclass
class SearchExecution:
    attempted: bool = False
    status: str = "not_requested"  # not_requested / skipped / success / empty / degraded / failed
    reasons: list[str] = field(default_factory=list)
    attempted_providers: list[str] = field(default_factory=list)
    successful_provider: str = ""
    results: list[SearchResult] = field(default_factory=list)
    provider_results: list[ProviderResult] = field(default_factory=list)
    error_code: str = ""
    elapsed_ms: int = 0


# ══════════════════════════════════════════════════════════════
# Provider 基类
# ══════════════════════════════════════════════════════════════

class SearchProvider(ABC):
    name: str = "base"

    @abstractmethod
    def search(self, query: str, limit: int = 5, context: dict | None = None) -> list[SearchResult]:
        ...

    @abstractmethod
    def health_check(self) -> bool:
        ...

    @abstractmethod
    def is_available(self) -> bool:
        ...


# ══════════════════════════════════════════════════════════════
# ESPN Scoreboard Provider
# ══════════════════════════════════════════════════════════════

def _build_proxy_opener() -> urllib.request.OpenerDirector:
    """Build a proxy-aware opener using HTTP_PROXY/HTTPS_PROXY or NBA_PROXY_HOST/PORT env vars."""
    import os
    proxy_url = os.environ.get("HTTP_PROXY") or os.environ.get("HTTPS_PROXY") or ""
    if not proxy_url:
        host = os.environ.get("NBA_PROXY_HOST", "").strip()
        port = os.environ.get("NBA_PROXY_PORT", "").strip()
        if host and port:
            proxy_url = f"http://{host}:{port}"
    if proxy_url:
        handler = urllib.request.ProxyHandler({"http": proxy_url, "https": proxy_url})
        return urllib.request.build_opener(handler)
    return urllib.request.build_opener()


class EspnScoreboardProvider(SearchProvider):
    name = "espn_scoreboard"

    # scoreboard 是与 query 无关的全局数据：TTL 微缓存 + single-flight，
    # 并发突发（压测 20 并发曾把上游抓取排队成 63s 首字节）只放行一次真实抓取
    CACHE_TTL_SECONDS = 30

    def __init__(self, timeout: int = 30):
        self.timeout = timeout
        self._opener = _build_proxy_opener()
        self._cache_lock = threading.Lock()
        self._cache_ts = 0.0
        self._cache_data = None

    def is_available(self) -> bool:
        return True

    def health_check(self) -> bool:
        try:
            data = self._fetch_scoreboard()
            return data is not None
        except Exception:
            return False

    def _fetch_scoreboard_cached(self):
        now = _time.time()
        with self._cache_lock:
            if self._cache_data is not None and now - self._cache_ts < self.CACHE_TTL_SECONDS:
                return self._cache_data
            # single-flight：持锁抓取（其余线程在此等待，抓取完成后直接读缓存）
            data = self._fetch_scoreboard()
            if data is not None:
                self._cache_data = data
                self._cache_ts = now
            return data

    def search(self, query: str, limit: int = 5, context: dict | None = None) -> list[SearchResult]:
        data = self._fetch_scoreboard_cached()
        if not data:
            return []
        fetched_at = datetime.now(timezone.utc).isoformat()
        results = []
        events = data.get("events", [])
        if context and context.get('teams'):
            requested = {TEAM_IDS[team] for team in context['teams'] if team in TEAM_IDS}
            events = [event for event in events if requested.issubset({str(competitor.get('team', {}).get('id'))
                for competitor in (event.get('competitions') or [{}])[0].get('competitors', [])})]
            if not events:
                return [SearchResult('NBA 对阵查询说明', 'https://www.espn.com/nba/schedule',
                    '当前赛事列表未查到' + '对'.join(context['teams']) + '的对应比赛；不能提供该场比分或技术统计。请明确比赛日期。',
                    self.name, source_domain='espn.com', fetched_at=fetched_at)]
        target_date = self._query_date(query)
        if target_date is not None:
            filtered = [event for event in events if self._event_local_date(event) == target_date]
            if filtered:
                events = filtered
            else:
                # 日期明确的查询（今天/明天/具体日期）：绝不能用其他日期的赛事充数——
                # 休赛期 scoreboard 常混入夏季联赛旧比分与未来季前赛，直接输出会误导用户。
                # 改为返回一条"无比赛+近期赛程预告"结果，由引擎生成诚实回答。
                note = self._upcoming_schedule_note(events, target_date, query)
                return [note] if note else []
        for event in events[:limit]:
            competition = (event.get("competitions") or [{}])[0]
            competitors = competition.get("competitors") or []
            home = next((c for c in competitors if c.get("homeAway") == "home"), {})
            away = next((c for c in competitors if c.get("homeAway") == "away"), {})
            home_name = (home.get("team") or {}).get("displayName", "")
            away_name = (away.get("team") or {}).get("displayName", "")
            home_score = (home.get("score") or "0")
            away_score = (away.get("score") or "0")
            status = (competition.get("status") or {}).get("type", {}).get("description", "")
            title = f"{away_name} vs {home_name}"
            snippet = f"{away_name} {away_score} - {home_score} {home_name} ({status})"
            results.append(SearchResult(
                title=title,
                url=f"https://www.espn.com/nba/game/_/gameId/{event.get('id', '')}",
                snippet=snippet,
                provider=self.name,
                source_domain="espn.com",
                published_at=event.get("date", ""),
                fetched_at=fetched_at,
                query=query,
            ))
        return results

    def _upcoming_schedule_note(self, events: list, target_date, query: str):
        """目标日期无比赛时，生成"无比赛 + 近期赛程预告"的说明型结果。

        title 刻意不含 " vs "，避免被引擎的比分解析当作比赛列表。
        """
        now = datetime.now(timezone.utc)
        upcoming = []
        for event in sorted(events, key=lambda e: str(e.get("date") or "")):
            value = str(event.get("date") or "")
            try:
                event_dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
            except ValueError:
                continue
            if event_dt <= now:
                continue
            competition = (event.get("competitions") or [{}])[0]
            competitors = competition.get("competitors") or []
            home = next((c for c in competitors if c.get("homeAway") == "home"), {})
            away = next((c for c in competitors if c.get("homeAway") == "away"), {})
            home_name = (home.get("team") or {}).get("displayName", "")
            away_name = (away.get("team") or {}).get("displayName", "")
            if not home_name or not away_name:
                continue
            local = event_dt.astimezone(timezone(timedelta(hours=8)))
            upcoming.append(f"{local.month}月{local.day}日 {away_name} 对阵 {home_name}")
            if len(upcoming) >= 2:
                break
        date_text = f"{target_date.month}月{target_date.day}日"
        snippet = f"{date_text}没有安排 NBA 比赛（休赛期或当日无赛事）。"
        if upcoming:
            snippet += "近期赛程：" + "；".join(upcoming)
        return SearchResult(
            title="NBA 近期赛程说明",
            url="https://www.espn.com/nba/schedule",
            snippet=snippet,
            provider=self.name,
            source_domain="espn.com",
            published_at=now.isoformat(),
            fetched_at=now.isoformat(),
            query=query,
        )

    @staticmethod
    def _query_date(query: str):
        explicit = re.search(r"(20\d{2})[-/]([01]?\d)[-/]([0-3]?\d)", query)
        if explicit:
            try:
                return datetime(
                    int(explicit.group(1)), int(explicit.group(2)), int(explicit.group(3))
                ).date()
            except ValueError:
                return None

        china_tz = timezone(timedelta(hours=8))
        today = datetime.now(china_tz).date()
        lower = query.lower()
        if "明天" in query or "tomorrow" in lower:
            return today + timedelta(days=1)
        if "昨天" in query or "yesterday" in lower:
            return today - timedelta(days=1)
        if "今天" in query or "今日" in query or "today" in lower:
            return today
        return None

    @staticmethod
    def _event_local_date(event: dict):
        value = str(event.get("date") or "").strip()
        if not value:
            return None
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed.astimezone(timezone(timedelta(hours=8))).date()
        except ValueError:
            return None

    def _fetch_scoreboard(self) -> dict | None:
        """合并 NBA 常规赛/季后赛 + 夏季联赛数据"""
        urls = [
            "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard",
            "https://site.api.espn.com/apis/site/v2/sports/basketball/nba-summer/scoreboard",
        ]
        all_events = []
        seen_ids = set()
        for url in urls:
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "NBAManager/1.0 (+https://github.com/nbamanager)"})
                with self._opener.open(req, timeout=self.timeout) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                    for event in data.get("events", []):
                        eid = event.get("id", "")
                        if eid and eid not in seen_ids:
                            seen_ids.add(eid)
                            all_events.append(event)
            except Exception:
                continue
        if all_events:
            return {"events": all_events}
        return None


# ══════════════════════════════════════════════════════════════
# ESPN News Provider
# ══════════════════════════════════════════════════════════════

class EspnNewsProvider(SearchProvider):
    name = "espn_news"

    # scoreboard 是与 query 无关的全局数据：TTL 微缓存 + single-flight，
    # 并发突发（压测 20 并发曾把上游抓取排队成 63s 首字节）只放行一次真实抓取
    CACHE_TTL_SECONDS = 30

    def __init__(self, timeout: int = 30):
        self.timeout = timeout
        self._opener = _build_proxy_opener()
        self._cache_lock = threading.Lock()
        self._cache_ts = 0.0
        self._cache_data = None

    def is_available(self) -> bool:
        return True

    def health_check(self) -> bool:
        try:
            data = self._fetch_news()
            return data is not None
        except Exception:
            return False

    def search(self, query: str, limit: int = 5, context: dict | None = None) -> list[SearchResult]:
        data = self._fetch_news()
        if not data:
            return []
        fetched_at = datetime.now(timezone.utc).isoformat()
        results = []
        for article in data.get("articles", [])[:limit]:
            results.append(SearchResult(
                title=article.get("headline", ""),
                url=article.get("links", {}).get("web", {}).get("href", ""),
                snippet=article.get("description", ""),
                provider=self.name,
                source_domain="espn.com",
                published_at=article.get("published", ""),
                fetched_at=fetched_at,
                query=query,
            ))
        return results

    def _fetch_news(self) -> dict | None:
        url = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/news"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "NBAManager/1.0 (+https://github.com/nbamanager)"})
            with self._opener.open(req, timeout=self.timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception:
            return None


# ══════════════════════════════════════════════════════════════
# Google News RSS Provider
# ══════════════════════════════════════════════════════════════

class GoogleNewsRssProvider(SearchProvider):
    name = "google_news_rss"

    # scoreboard 是与 query 无关的全局数据：TTL 微缓存 + single-flight，
    # 并发突发（压测 20 并发曾把上游抓取排队成 63s 首字节）只放行一次真实抓取
    CACHE_TTL_SECONDS = 30

    def __init__(self, timeout: int = 30):
        self.timeout = timeout
        self._opener = _build_proxy_opener()
        self._cache_lock = threading.Lock()
        self._cache_ts = 0.0
        self._cache_data = None

    def is_available(self) -> bool:
        return True

    def health_check(self) -> bool:
        return True

    def search(self, query: str, limit: int = 5, context: dict | None = None) -> list[SearchResult]:
        encoded_query = quote_plus(query)
        url = f"https://news.google.com/rss/search?q={encoded_query}+NBA&hl=en-US&gl=US&ceid=US:en"
        fetched_at = datetime.now(timezone.utc).isoformat()
        results = []
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Accept": "application/rss+xml,application/xml;q=0.9,*/*;q=0.8",
            })
            with self._opener.open(req, timeout=self.timeout) as resp:
                xml_data = resp.read().decode("utf-8", errors="ignore")
            root = ET.fromstring(xml_data)
            for item in root.findall(".//item")[:limit]:
                title = (item.findtext("title") or "").strip()
                link = (item.findtext("link") or "").strip()
                description = (item.findtext("description") or "").strip()
                pub_date = (item.findtext("pubDate") or "").strip()
                # Extract domain
                domain = ""
                match = re.search(r"https?://([^/]+)", link)
                if match:
                    domain = match.group(1).replace("www.", "")
                results.append(SearchResult(
                    title=_clean_html(title),
                    url=link,
                    snippet=_clean_html(description)[:300],
                    provider=self.name,
                    source_domain=domain,
                    published_at=pub_date,
                    fetched_at=fetched_at,
                    query=query,
                ))
        except Exception:
            pass
        return results


# ══════════════════════════════════════════════════════════════
# Baidu Search Provider (非官方, baidusearch)
# ══════════════════════════════════════════════════════════════

class BaiduSearchProvider(SearchProvider):
    """baidusearch PyPI 包 — 非官方 Web Scraping，需限流和熔断"""
    name = "baidusearch"

    def __init__(self, timeout: int = 15, min_interval: int = 15, circuit_breaker_seconds: int = 600):
        self.timeout = timeout
        self.min_interval = min_interval
        self.circuit_breaker_seconds = circuit_breaker_seconds
        self._last_request_time = 0.0
        self._circuit_broken_until = 0.0
        self._lock = threading.Lock()

    def is_available(self) -> bool:
        try:
            import baidusearch  # noqa: F401
            return True
        except ImportError:
            return False

    def health_check(self) -> bool:
        return self.is_available()

    def search(self, query: str, limit: int = 5, context: dict | None = None) -> list[SearchResult]:
        with self._lock:
            now = _time.time()
            # 熔断检查
            if now < self._circuit_broken_until:
                return []
            # 限流
            elapsed = now - self._last_request_time
            if elapsed < self.min_interval:
                _time.sleep(self.min_interval - elapsed)
            self._last_request_time = _time.time()

        fetched_at = datetime.now(timezone.utc).isoformat()
        try:
            from baidusearch import baidusearch
            raw_results = baidusearch.search(query, num_results=min(limit, 5))
            results = []
            for item in raw_results:
                title = item.get("title", "")
                url = item.get("url", "")
                abstract = item.get("abstract", "")
                if not title or not url:
                    continue
                domain = ""
                match = re.search(r"https?://([^/]+)", url)
                if match:
                    domain = match.group(1).replace("www.", "")
                results.append(SearchResult(
                    title=_clean_html(title),
                    url=url,
                    snippet=_clean_html(abstract)[:300],
                    provider=self.name,
                    source_domain=domain,
                    fetched_at=fetched_at,
                    query=query,
                ))
            return results
        except Exception as e:
            # 熔断
            with self._lock:
                self._circuit_broken_until = _time.time() + self.circuit_breaker_seconds
            return []


# ══════════════════════════════════════════════════════════════
# SearXNG Provider (可选自托管)
# ══════════════════════════════════════════════════════════════

class SearXNGProvider(SearchProvider):
    name = "searxng"

    def __init__(self, base_url: str = "", timeout: int = 10):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def is_available(self) -> bool:
        return bool(self.base_url)

    def health_check(self) -> bool:
        if not self.base_url:
            return False
        try:
            url = f"{self.base_url}/healthz"
            req = urllib.request.Request(url, headers={"User-Agent": "NBAManager/1.0 (+https://github.com/nbamanager)"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status == 200
        except Exception:
            return False

    def search(self, query: str, limit: int = 5, context: dict | None = None) -> list[SearchResult]:
        if not self.base_url:
            return []
        fetched_at = datetime.now(timezone.utc).isoformat()
        encoded_query = quote_plus(query)
        url = f"{self.base_url}/search?q={encoded_query}&format=json&categories=news&pageno=1"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "NBAManager/1.0 (+https://github.com/nbamanager)"})
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            results = []
            for item in (data.get("results") or [])[:limit]:
                title = item.get("title", "")
                url_str = item.get("url", "")
                content = item.get("content", "")
                if not title or not url_str:
                    continue
                domain = ""
                match = re.search(r"https?://([^/]+)", url_str)
                if match:
                    domain = match.group(1).replace("www.", "")
                results.append(SearchResult(
                    title=_clean_html(title),
                    url=url_str,
                    snippet=_clean_html(content)[:300],
                    provider=self.name,
                    source_domain=domain,
                    published_at=item.get("publishedDate", ""),
                    fetched_at=fetched_at,
                    query=query,
                ))
            return results
        except Exception:
            return []


# ══════════════════════════════════════════════════════════════
# SearchService — 统一搜索调度
# ══════════════════════════════════════════════════════════════

class SearchService:
    def __init__(self, settings):
        self.settings = settings
        self.providers: list[SearchProvider] = []
        self._cache_ttl = settings.search_cache_ttl_seconds
        self._cache = TTLCache(getattr(settings, 'search_cache_max_entries', 256), self._cache_ttl)
        self._init_providers()

    def _init_providers(self):
        from rag_app.tools.game_archive import EspnArchiveProvider
        self.archive_provider = EspnArchiveProvider(self.settings.search_timeout_seconds)
        provider_map = {
            "espn_scoreboard": lambda: EspnScoreboardProvider(self.settings.search_timeout_seconds),
            "espn_news": lambda: EspnNewsProvider(self.settings.search_timeout_seconds),
            "google_news_rss": lambda: GoogleNewsRssProvider(self.settings.search_timeout_seconds),
            "baidusearch": lambda: BaiduSearchProvider(
                self.settings.search_timeout_seconds,
                self.settings.baidu_min_interval_seconds,
                self.settings.baidu_circuit_breaker_seconds,
            ),
            "searxng": lambda: SearXNGProvider(self.settings.searxng_base_url, self.settings.search_timeout_seconds),
        }
        configured = [p.strip() for p in self.settings.search_providers.split(",")]
        for name in configured:
            factory = provider_map.get(name)
            if factory:
                provider = factory()
                if provider.is_available():
                    self.providers.append(provider)

    # 比赛类查询关键词
    GAME_KEYWORDS = re.compile(
        r"比赛|比分|赛程|赛果|今日比赛|夏联|夏季联赛|summer|league|scores?|game[s]?|"
        r"scoreboard|战况|战报|结果|几比几|赢了|输了|大比分|小比分|加时"
    )

    def _is_game_query(self, query: str) -> bool:
        """判断查询是否与比赛/比分/赛程相关"""
        return bool(self.GAME_KEYWORDS.search(query.lower()))

    def search(self, query: str, limit: int = 5, context: dict | None = None,
               provider_filter: list[str] | None = None) -> tuple[list[SearchResult], SearchExecution]:
        execution = SearchExecution(attempted=True)
        start_ms = int(_time.time() * 1000)

        # 缓存检查
        cache_key = json.dumps([query, limit, context or {}, provider_filter], sort_keys=True, ensure_ascii=False)
        cached = self._cache.get(cache_key)
        if cached:
            execution.status = 'success'
            execution.results = cached
            execution.successful_provider = cached[0].provider
            execution.elapsed_ms = int(_time.time() * 1000) - start_ms
            return cached, execution

        # 按查询意图选择 Provider
        is_game = self._is_game_query(query)
        historical = bool(context and context.get('historical'))
        providers_to_try = []
        if historical and (not provider_filter or 'espn_archive' in provider_filter):
            providers_to_try.append(self.archive_provider)
        for p in self.providers:
            if historical:
                continue
            if provider_filter and p.name not in provider_filter:
                continue
            if is_game and p.name != "espn_scoreboard":
                continue
            # Scoreboard 只参与比赛类查询
            if p.name == "espn_scoreboard" and not is_game:
                continue
            providers_to_try.append(p)

        if not providers_to_try:
            execution.status = "skipped"
            execution.reasons.append("no_providers_available")
            return [], execution

        # 顺序执行，合并所有 Provider 结果
        all_results: list[SearchResult] = []
        has_failure = False
        for provider in providers_to_try:
            execution.attempted_providers.append(provider.name)
            provider_start = int(_time.time() * 1000)
            pr = ProviderResult(provider=provider.name)
            try:
                results = provider.search(query, limit, context)
                filtered = self._filter_results(results, query) if results else []
                pr.result_count = len(filtered)
                pr.status = "success" if filtered else "empty"
                pr.elapsed_ms = int(_time.time() * 1000) - provider_start
                all_results.extend(filtered)
                if filtered and not execution.successful_provider:
                    execution.successful_provider = provider.name
            except Exception as e:
                pr.status = "failed"
                pr.error = f"{type(e).__name__}: {str(e)[:100]}"
                pr.elapsed_ms = int(_time.time() * 1000) - provider_start
                has_failure = True
            execution.provider_results.append(pr)

            # ESPN 记分牌已经直接回答比赛/比分问题，无需继续请求新闻源。
            if is_game and provider.name == "espn_scoreboard" and filtered:
                break
            if len(all_results) >= limit:
                break

        # 去重并限制数量
        final = self._filter_results(all_results, query)[:limit]
        if final:
            execution.status = "success"
            execution.results = final
            self._cache.put(cache_key, final)
        elif has_failure:
            execution.status = "failed"
        else:
            execution.status = "empty"
        execution.elapsed_ms = int(_time.time() * 1000) - start_ms
        return final, execution

    def _filter_results(self, results: list[SearchResult], query: str) -> list[SearchResult]:
        filtered = []
        seen_urls = set()
        seen_titles = set()
        query_lower = query.lower()
        query_keywords = {
            word for word in re.findall(r"[a-z][a-z0-9-]+", query_lower)
            if len(word) >= 2
        }
        intent_keywords = {
            "交易": {"trade"},
            "伤病": {"injury"},
            "受伤": {"injury"},
            "复出": {"return", "injury"},
            "签约": {"sign", "signed", "signing"},
            "自由市场": {"free agency", "free", "agency"},
            "自由球员": {"free agent", "free", "agent"},
            "选秀": {"draft"},
        }
        for chinese, translated in intent_keywords.items():
            if chinese in query:
                query_keywords.update(translated)
        entity_names = {**SearchQueryRewriter.TEAM_EN, **SearchQueryRewriter.PLAYER_EN}
        for chinese, english in entity_names.items():
            if chinese in query:
                query_keywords.add(chinese.lower())
                query_keywords.update(part.lower() for part in english.split() if len(part) >= 3)
        nba_generic = {"nba", "basketball", "篮球", "比赛", "news", "latest", "最新", "scores", "score", "today", "今日", "今天"}
        specific_keywords = query_keywords - nba_generic

        # 结构化比赛 Provider：只做去重，不做关键词过滤
        scoreboard_providers = {"espn_scoreboard"}

        for r in results:
            if not r.title or not r.url:
                continue
            if not r.url.startswith("http"):
                continue
            if r.url in seen_urls:
                continue
            title_key = r.title.strip().lower()
            if title_key in seen_titles:
                continue

            # 记分牌是结构化 NBA 比赛源；新闻源仍需检查查询相关性。
            trusted_providers = {"espn_scoreboard", "espn_archive"}
            if r.provider not in trusted_providers:
                # 第三方 Provider：检查关键词相关性
                if specific_keywords:
                    text = (r.title + " " + r.snippet).lower()
                    has_keyword = any(kw in text for kw in specific_keywords)
                    if not has_keyword:
                        continue

            seen_urls.add(r.url)
            seen_titles.add(title_key)
            filtered.append(r)
        return filtered

    def health_check(self) -> dict:
        status = {}
        for p in self.providers:
            try:
                status[p.name] = p.health_check()
            except Exception:
                status[p.name] = False
        return status


# ══════════════════════════════════════════════════════════════
# SearchQueryRewriter — 查询改写
# ══════════════════════════════════════════════════════════════

class SearchQueryRewriter:
    """将中文 NBA 查询改写为英文搜索词"""

    TEAM_EN = TEAM_EN

    PLAYER_EN = PLAYER_EN

    def rewrite(self, query: str, entities: dict | None = None) -> list[str]:
        """返回中英文候选搜索词"""
        candidates = [query]
        q_lower = query.lower()

        # 提取球队和球员英文名
        teams_en = []
        players_en = []
        for cn, en in self.TEAM_EN.items():
            if cn in query:
                teams_en.append(en)
        for cn, en in self.PLAYER_EN.items():
            if cn in query:
                players_en.append(en)

        # 时间关键词
        today = datetime.now(timezone(timedelta(hours=8)))
        today_str = today.strftime("%Y-%m-%d")

        # 改写规则
        if "交易" in query or "trade" in q_lower:
            if players_en:
                candidates.append(f"{players_en[0]} trade latest NBA")
            elif teams_en:
                candidates.append(f"{teams_en[0]} trade NBA latest")
            else:
                candidates.append(f"NBA trade latest {today_str}")

        if "伤病" in query or "受伤" in query:
            if players_en:
                candidates.append(f"{players_en[0]} injury update NBA")
            else:
                candidates.append(f"NBA injury report {today_str}")

        if "夏季联赛" in query or "summer league" in q_lower:
            candidates.append(f"NBA Summer League scores {today_str}")

        if "选秀" in query or "draft" in q_lower:
            candidates.append(f"NBA draft 2026 results")

        if "自由市场" in query or "自由球员" in query:
            candidates.append(f"NBA free agency latest {today_str}")

        if "最新" in query or "最近" in query or "今日" in query or "今天" in query:
            if players_en:
                candidates.append(f"{players_en[0]} latest news NBA {today_str}")
            elif teams_en:
                candidates.append(f"{teams_en[0]} latest news NBA {today_str}")
            else:
                candidates.append(f"NBA latest news {today_str}")

        # 去重
        seen = set()
        unique = []
        for c in candidates:
            c_stripped = c.strip()
            if c_stripped and c_stripped not in seen:
                seen.add(c_stripped)
                unique.append(c_stripped)
        return unique[:5]


# ══════════════════════════════════════════════════════════════
# 旧版兼容 — BaiduSearchTool (保留向后兼容)
# ══════════════════════════════════════════════════════════════

class _BaiduHTMLParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.results: list[dict] = []
        self._in_h3 = False
        self._in_abstract = False
        self._current_title: list[str] = []
        self._current_url = ""
        self._last_result_index: int | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = {key: value or "" for key, value in attrs}
        classes = attrs_dict.get("class", "")
        if tag == "h3" and "t" in classes.split():
            self._in_h3 = True
            self._current_title = []
            self._current_url = ""
            return
        if self._in_h3 and tag == "a":
            self._current_url = attrs_dict.get("href", "")
            return
        if tag in {"div", "span"} and (
            "c-abstract" in classes or "content-right_8Zs40" in classes or "c-span-last" in classes
        ):
            self._in_abstract = True

    def handle_endtag(self, tag: str) -> None:
        if tag == "h3" and self._in_h3:
            title = _clean_text("".join(self._current_title))
            if title:
                self.results.append({"title": title, "url": self._current_url, "content": ""})
                self._last_result_index = len(self.results) - 1
            self._in_h3 = False
            self._current_title = []
            self._current_url = ""
            return
        if tag in {"div", "span"} and self._in_abstract:
            self._in_abstract = False

    def handle_data(self, data: str) -> None:
        if self._in_h3:
            self._current_title.append(data)
        elif self._in_abstract and self._last_result_index is not None:
            old = self.results[self._last_result_index].get("content", "")
            self.results[self._last_result_index]["content"] = _clean_text(f"{old} {data}")


class BaiduSearchTool:
    def __init__(self, timeout_seconds: int = 8):
        self.timeout_seconds = timeout_seconds

    def search(self, query: str, limit: int = 5) -> list[SearchResult]:
        html = self.fetch(query)
        return self.parse_results(html, limit)

    def fetch(self, query: str) -> str:
        url = f"https://www.baidu.com/s?wd={quote_plus(query)}&rn=10"
        user_agents = [
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        ]
        import random as _random
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={
                    "User-Agent": _random.choice(user_agents),
                    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.7",
                    "Accept-Encoding": "identity",
                    "Connection": "keep-alive",
                    "Cache-Control": "no-cache",
                })
                with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                    raw = response.read()
                    charset = response.headers.get_content_charset() or "utf-8"
                    html = raw.decode(charset, errors="ignore")
                    if len(html) > 2000 and "<h3" in html:
                        return html
                    if attempt < 2:
                        _time.sleep(1 + _random.random())
            except Exception:
                if attempt < 2:
                    _time.sleep(1 + _random.random())
        return ""

    def parse_results(self, html: str, limit: int = 5) -> list[SearchResult]:
        parser = _BaiduHTMLParser()
        parser.feed(html)
        fetched_at = datetime.now(timezone.utc).isoformat()
        parsed = [
            SearchResult(
                title=_clean_text(item.get("title", "")),
                url=item.get("url", ""),
                snippet=_clean_text(item.get("content", "")),
                provider="baidu_legacy",
                source_domain="baidu.com",
                fetched_at=fetched_at,
            )
            for item in parser.results
        ]
        if not parsed:
            parsed = self._parse_fallback(html, fetched_at)
        return list(_dedupe(parsed))[:limit]

    def _parse_fallback(self, html: str, fetched_at: str) -> list[SearchResult]:
        pattern = re.compile(
            r'<h3[^>]*class="[^"]*\bt\b[^"]*"[^>]*>.*?<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>.*?</h3>',
            re.IGNORECASE | re.DOTALL,
        )
        results = []
        for url, title_html in pattern.findall(html):
            results.append(SearchResult(
                title=_clean_text(_strip_tags(title_html)),
                url=url,
                snippet="",
                provider="baidu_legacy",
                fetched_at=fetched_at,
            ))
        return results


# ══════════════════════════════════════════════════════════════
# 辅助函数
# ══════════════════════════════════════════════════════════════

def format_search_results(
    results: list,
    limit: int = 5,
    max_total_chars: int = 700,
    max_title_chars: int = 120,
    max_snippet_chars: int = 320,
    max_url_chars: int = 180,
) -> str:
    lines = []
    for index, result in enumerate(results[:limit], start=1):
        if isinstance(result, dict):
            title = result.get("title", "")
            content = result.get("snippet") or result.get("content") or ""
            url = result.get("url", "")
        else:
            title = result.title
            content = result.snippet
            url = result.url
        title = _truncate_text(title, max_title_chars)
        content = _truncate_text(content, max_snippet_chars)
        url = _truncate_text(url, max_url_chars)
        snippet = f"：{content}" if content else ""
        lines.append(f"{index}. {title}{snippet}\n来源：{url}")
    formatted = "\n".join(lines)
    return _truncate_text(formatted, max_total_chars)


def _truncate_text(value: object, max_chars: int) -> str:
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    if len(text) <= max_chars:
        return text
    if max_chars <= 3:
        return text[:max_chars]
    return text[:max_chars - 3].rstrip() + "..."


def _dedupe(results: Iterable[SearchResult]) -> Iterable[SearchResult]:
    seen = set()
    for result in results:
        key = (result.url.strip(), result.title.strip())
        if not result.title or key in seen:
            continue
        seen.add(key)
        yield result


def _strip_tags(value: str) -> str:
    return re.sub(r"<[^>]+>", "", value)


def _clean_text(value: str) -> str:
    value = _strip_tags(unescape(value or ""))
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def _clean_html(value: str) -> str:
    return _clean_text(value)
