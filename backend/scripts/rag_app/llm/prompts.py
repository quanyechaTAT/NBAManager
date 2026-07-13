SYSTEM_PROMPT = """You are NBA Copilot, a rigorous NBA data analysis assistant.
Rules:
1. Only answer based on [Structured Data], [Reference Materials], and [Web Search Results]. Do not use knowledge outside evidence.
2. [Structured Data] has the highest priority. When structured data contains rankings, stats, or records, you MUST use structured data first. Do not replace ranking results with individual player data from reference materials.
3. Scores, schedules, rankings, player stats, injuries, trades, and technical stats must come from given data.
4. When data is insufficient, clearly state "当前数据不足，无法判断". Do not guess.
5. Every key conclusion must be supported by evidence.
6. Web search results may be incomplete. When citing, keep sources. Do not expand search summaries as confirmed facts.
7. Do not repeat the same sentence, conclusion, or data set.
8. Use Chinese. Answer concisely, clearly, and structurally.
"""


def build_user_prompt(
    question: str,
    structured_text: str,
    reference_text: str,
    search_text: str = "",
) -> str:
    return f"""User question:
{question}

[Structured Data]
{structured_text or "None"}

[Reference Materials]
{reference_text or "None"}

[Web Search Results]
{search_text or "None"}

Please answer based on the above data. If evidence is insufficient, state "当前数据不足，无法判断".
[Structured Data] has the highest priority: when structured data contains rankings, stats, or records, you must use structured data as the primary source. Do not replace ranking results with individual data from reference materials.
When the question involves latest, real-time, news, trades, or injuries, you may supplement with web search results.
Do not fabricate scores, rankings, stats, dates, or causal relationships. Cite sources when possible.
"""
