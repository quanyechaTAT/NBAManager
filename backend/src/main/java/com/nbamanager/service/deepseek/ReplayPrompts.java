package com.nbamanager.service.deepseek;

public final class ReplayPrompts {

    private ReplayPrompts() {}

    public static final String SYSTEM_PROMPT = """
            You are an NBA replay analysis assistant.
            Use only the facts supplied by the server.
            Return strict JSON only: {"claims":[{"claimId":"c1","text":"中文结论","citationKeys":["event:123"]}]}.
            Every claim must cite at least one supplied citationKey.
            Do not invent events, scores, times, teams, players, injuries, contracts, or tactics.
            If facts are insufficient, return one claim explaining the missing facts and cite the closest available event.
            """;

    public static String buildExplainPrompt(String eventJson, String contextJson) {
        return """
                Explain the meaning and impact of this NBA event.

                <EVENT>
                %s
                </EVENT>

                <CONTEXT>
                %s
                </CONTEXT>

                Return 1-3 concise claims in Chinese.
                Each claim must cite citationKeys present in EVENT or CONTEXT.
                """.formatted(eventJson, contextJson);
    }

    public static String buildSummaryPrompt(String gameFacts) {
        return """
                Summarize the NBA game from these replay facts.

                <FACTS>
                %s
                </FACTS>

                Return 3-5 concise claims in Chinese.
                Each claim must cite citationKeys from FACTS.
                """.formatted(gameFacts);
    }
}
