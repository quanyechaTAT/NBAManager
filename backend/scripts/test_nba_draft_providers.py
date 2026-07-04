import importlib.util
from pathlib import Path


def load_fetcher():
    script_path = Path(__file__).with_name("nba_data_fetcher.py")
    spec = importlib.util.spec_from_file_location("nba_data_fetcher", script_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_parse_nba_news_draft_order_extracts_2026_results():
    fetcher = load_fetcher()
    html = """
    <html>
      <body>
        <h1>2026 NBA Draft Results: Picks 1-60</h1>
        <p>1. Washington Wizards: AJ Dybantsa, F, BYU</p>
        <p>2. Utah Jazz: Cameron Boozer, F, Duke</p>
        <p>31. Denver Nuggets: Karim Lopez, G, New Zealand</p>
      </body>
    </html>
    """

    picks = fetcher._parse_draft_from_nba_news_order(html, 2026)

    assert len(picks) == 3
    assert picks[0]["year"] == 2026
    assert picks[0]["round"] == 1
    assert picks[0]["pickNumber"] == 1
    assert picks[0]["teamNameEn"] == "Washington Wizards"
    assert picks[0]["playerNameEn"] == "AJ Dybantsa"
    assert picks[0]["playerPosition"] == "F"
    assert picks[0]["source"] == "nba_com_news_draft_order"
    assert picks[2]["round"] == 2


def test_parse_nba_news_draft_order_extracts_official_draft_sentence_format():
    fetcher = load_fetcher()
    html = """
    <article>
      <h3>2026 Round 1 Draft Results</h3>
      <p>1. Washington Wizards draft AJ Dybantsa (BYU)</p>
      <p>2. Utah Jazz draft Darryn Peterson (Kansas)</p>
      <p>31. Denver Nuggets draft Karim Lopez (New Zealand)</p>
    </article>
    """

    picks = fetcher._parse_draft_from_nba_news_order(html, 2026)

    assert len(picks) == 3
    assert picks[0]["teamNameEn"] == "Washington Wizards"
    assert picks[0]["playerNameEn"] == "AJ Dybantsa"
    assert picks[1]["playerNameEn"] == "Darryn Peterson"
    assert picks[2]["round"] == 2
