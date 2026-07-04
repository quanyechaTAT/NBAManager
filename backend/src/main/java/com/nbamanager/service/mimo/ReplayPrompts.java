package com.nbamanager.service.mimo;

public final class ReplayPrompts {

    private ReplayPrompts() {}

    public static final String SYSTEM_PROMPT = """
            你是 NBA 比赛事实分析助手。

            你只能使用 <FACTS> 中给出的事实。
            禁止编造任何事件、比分、时间、球员、球队、伤病、合同、战术安排。
            如果事实不足，必须回答"当前数据不足以判断"，并说明缺少哪些事实。
            所有解释必须引用事件中的球员名、比分或时间。
            你可以解释、总结、分析影响，但不能创造新事实。
            使用中文回答。
            """;

    public static String buildExplainPrompt(String eventJson, String contextJson) {
        return """
                请解释以下 NBA 比赛事件的意义和影响。

                <EVENT>
                %s
                </EVENT>

                <CONTEXT（前后事件）>
                %s
                </CONTEXT>

                要求：
                1. 用 2-3 句话解释这个事件
                2. 说明它对比赛走势的影响
                3. 引用具体的比分和球员
                """.formatted(eventJson, contextJson);
    }

    public static String buildSummaryPrompt(String gameFacts) {
        return """
                请为以下 NBA 比赛生成赛后总结。

                <FACTS>
                %s
                </FACTS>

                要求：
                1. 总结比赛结果和关键数据
                2. 指出比赛的关键转折点
                3. 评价关键球员表现
                4. 200-300字
                """.formatted(gameFacts);
    }
}
