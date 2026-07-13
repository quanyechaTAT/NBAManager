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

class EspnScoreboardProvider(SearchProvider):
    name = "espn_scoreboard"

    def __init__(self, timeout: int = 10):
        self.timeout = timeout

    def is_available(self) -> bool:
        return True

    def health_check(self) -> bool:
        try:
            data = self._fetch_scoreboard()
            return data is not None
        except Exception:
            return False

    def search(self, query: str, limit: int = 5, context: dict | None = None) -> list[SearchResult]:
        data = self._fetch_scoreboard()
        if not data:
            return []
        fetched_at = datetime.now(timezone.utc).isoformat()
        results = []
        for event in data.get("events", [])[:limit]:
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
                req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
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

    def __init__(self, timeout: int = 10):
        self.timeout = timeout

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
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception:
            return None


# ══════════════════════════════════════════════════════════════
# Google News RSS Provider
# ══════════════════════════════════════════════════════════════

class GoogleNewsRssProvider(SearchProvider):
    name = "google_news_rss"

    def __init__(self, timeout: int = 10):
        self.timeout = timeout

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
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
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
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
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
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
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
        self._cache: dict[str, tuple[float, list[SearchResult]]] = {}
        self._cache_ttl = settings.search_cache_ttl_seconds
        self._init_providers()

    def _init_providers(self):
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

    def search(self, query: str, limit: int = 5, context: dict | None = None,
               provider_filter: list[str] | None = None) -> tuple[list[SearchResult], SearchExecution]:
        execution = SearchExecution(attempted=True)
        start_ms = int(_time.time() * 1000)

        # 缓存检查
        cache_key = f"{query}:{limit}"
        cached = self._cache.get(cache_key)
        if cached:
            cached_time, cached_results = cached
            if _time.time() - cached_time < self._cache_ttl:
                execution.status = "success"
                execution.results = cached_results
                execution.successful_provider = cached_results[0].provider if cached_results else ""
                execution.elapsed_ms = int(_time.time() * 1000) - start_ms
                return cached_results, execution

        # 选择 Provider
        providers_to_try = self.providers
        if provider_filter:
            providers_to_try = [p for p in self.providers if p.name in provider_filter]

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

        # 去重并限制数量
        final = self._filter_results(all_results, query)[:limit]
        if final:
            execution.status = "success"
            execution.results = final
            self._cache[cache_key] = (_time.time(), final)
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
        # 查询关键词用于相关性过滤
        query_keywords = set()
        for word in re.split(r'\s+', query_lower):
            if len(word) >= 2:
                query_keywords.add(word)
        # NBA 相关关键词（不参与过滤，因为所有结果都应与 NBA 相关）
        nba_generic = {"nba", "basketball", "篮球", "比赛", "news", "latest", "最新"}

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

            # 相关性检查：标题或摘要必须包含查询中的至少一个关键词
            text = (r.title + " " + r.snippet).lower()
            specific_keywords = query_keywords - nba_generic
            if specific_keywords:
                has_keyword = any(kw in text for kw in specific_keywords)
                if not has_keyword:
                    continue  # 跳过不相关结果

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

    TEAM_EN = {
        "雷霆": "Oklahoma City Thunder", "马刺": "San Antonio Spurs",
        "湖人": "Los Angeles Lakers", "勇士": "Golden State Warriors",
        "凯尔特人": "Boston Celtics", "掘金": "Denver Nuggets",
        "尼克斯": "New York Knicks", "骑士": "Cleveland Cavaliers",
        "火箭": "Houston Rockets", "森林狼": "Minnesota Timberwolves",
        "活塞": "Detroit Pistons", "独行侠": "Dallas Mavericks",
        "76人": "Philadelphia 76ers", "热火": "Miami Heat",
        "太阳": "Phoenix Suns", "快船": "LA Clippers",
        "老鹰": "Atlanta Hawks", "公牛": "Chicago Bulls",
        "雄鹿": "Milwaukee Bucks", "鹈鹕": "New Orleans Pelicans",
        "灰熊": "Memphis Grizzlies", "国王": "Sacramento Kings",
        "开拓者": "Portland Trail Blazers", "猛龙": "Toronto Raptors",
        "篮网": "Brooklyn Nets", "黄蜂": "Charlotte Hornets",
        "步行者": "Indiana Pacers", "魔术": "Orlando Magic",
        "奇才": "Washington Wizards", "爵士": "Utah Jazz",
    }

    PLAYER_EN = {
        "文班亚马": "Victor Wembanyama", "东契奇": "Luka Doncic",
        "詹姆斯": "LeBron James", "库里": "Stephen Curry",
        "杜兰特": "Kevin Durant", "约基奇": "Nikola Jokic",
        "字母哥": "Giannis Antetokounmpo", "恩比德": "Joel Embiid",
        "伦纳德": "Kawhi Leonard", "SGA": "Shai Gilgeous-Alexander",
        "爱德华兹": "Anthony Edwards", "塔图姆": "Jayson Tatum",
    }

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
        if "最新" in query or "最近" in query or "今日" in query or "今天" in query:
            if players_en:
                candidates.append(f"{players_en[0]} latest news NBA {today_str}")
            elif teams_en:
                candidates.append(f"{teams_en[0]} latest news NBA {today_str}")
            else:
                candidates.append(f"NBA latest news {today_str}")

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

def format_search_results(results: list, limit: int = 5) -> str:
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
        snippet = f"：{content}" if content else ""
        lines.append(f"{index}. {title}{snippet}\n来源：{url}")
    return "\n".join(lines)


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
