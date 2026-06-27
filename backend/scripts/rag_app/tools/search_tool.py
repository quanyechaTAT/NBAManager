from __future__ import annotations

from dataclasses import dataclass
from html import unescape
from html.parser import HTMLParser
import re
from typing import Iterable
from urllib.parse import quote_plus
import urllib.request


@dataclass(frozen=True)
class SearchResult:
    title: str
    url: str
    content: str
    source: str = "baidu"

    def to_dict(self) -> dict:
        return {
            "title": self.title,
            "url": self.url,
            "content": self.content,
            "source": self.source,
        }


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
        url = f"https://www.baidu.com/s?wd={quote_plus(query)}"
        request = urllib.request.Request(
            url,
            headers={
                "User-Agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/124.0 Safari/537.36"
                ),
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.7",
            },
        )
        with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
            raw = response.read()
            charset = response.headers.get_content_charset() or "utf-8"
            return raw.decode(charset, errors="ignore")

    def parse_results(self, html: str, limit: int = 5) -> list[SearchResult]:
        parser = _BaiduHTMLParser()
        parser.feed(html)

        parsed = [
            SearchResult(
                title=_clean_text(item.get("title", "")),
                url=item.get("url", ""),
                content=_clean_text(item.get("content", "")),
            )
            for item in parser.results
        ]

        if not parsed:
            parsed = self._parse_fallback(html)

        return list(_dedupe(parsed))[:limit]

    def _parse_fallback(self, html: str) -> list[SearchResult]:
        pattern = re.compile(
            r'<h3[^>]*class="[^"]*\bt\b[^"]*"[^>]*>.*?<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>.*?</h3>',
            re.IGNORECASE | re.DOTALL,
        )
        results = []
        for url, title_html in pattern.findall(html):
            results.append(SearchResult(title=_clean_text(_strip_tags(title_html)), url=url, content=""))
        return results


def format_search_results(results: list[SearchResult], limit: int = 5) -> str:
    lines = []
    for index, result in enumerate(results[:limit], start=1):
        content = f"：{result.content}" if result.content else ""
        lines.append(f"{index}. {result.title}{content}\n来源：{result.url}")
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
