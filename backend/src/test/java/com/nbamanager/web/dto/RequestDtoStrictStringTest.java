package com.nbamanager.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 第 9 轮 I9-1 回归：用户可控字符串字段的严格类型契约。
 *
 * r9_security S4 实证：{@code {"title": 123}} 被 Jackson 强转 "123" 并 200 落库。
 * 第 3 轮已为 RAG question 引入 StrictStringDeserializer，本轮把同一契约套到
 * 全部用户可控请求 DTO 的字符串字段（PostRequest/CommentRequest/PollRequest/
 * InjuryReportRequest/TeamRequest/PlayerRequest/GameNewsRequest）。
 * 本测试直接反序列化**真实 DTO**，锁住注解不被移除。
 */
class RequestDtoStrictStringTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String VALID_POST = """
            {"title":"zr10 合法标题","content":"zr10 正文","category":"讨论","tags":"nba"}
            """;

    @Test
    void postRequestAcceptsJsonStrings() throws Exception {
        PostRequest p = mapper.readValue(VALID_POST, PostRequest.class);
        assertThat(p.title()).isEqualTo("zr10 合法标题");
        assertThat(p.category()).isEqualTo("讨论");
    }

    @Test
    void postRequestRejectsNumericTitle() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"title\":123,\"content\":\"x\",\"category\":\"y\"}", PostRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class)
                .hasMessageContaining("string");
    }

    @Test
    void postRequestRejectsBooleanTitle() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"title\":true,\"content\":\"x\",\"category\":\"y\"}", PostRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void postRequestAllowsNullForOptionalFieldSoValidationDecides() throws Exception {
        // null 与"类型错误"是两回事：tags=null 交给 @Size/业务校验
        PostRequest p = mapper.readValue(
                "{\"title\":\"t\",\"content\":\"c\",\"category\":\"cat\",\"tags\":null}", PostRequest.class);
        assertThat(p.tags()).isNull();
    }

    @Test
    void commentRequestRejectsNumericContent() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"postId\":1,\"content\":123}", CommentRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void commentRequestRejectsNumericGameId() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"postId\":1,\"content\":\"ok\",\"gameId\":401881722}", CommentRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void pollRequestRejectsNumericQuestion() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"postId\":1,\"question\":1,\"option1\":\"a\",\"option2\":\"b\"}", PollRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void injuryReportRequestRejectsNumericPlayerName() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"playerId\":1,\"playerName\":123,\"teamName\":\"t\",\"injuryType\":\"i\",\"status\":\"s\"}",
                InjuryReportRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void teamRequestRejectsNumericName() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"name\":123,\"city\":\"c\",\"conference\":\"e\",\"wins\":1,\"losses\":1}", TeamRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void playerRequestRejectsNumericName() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"name\":123,\"teamId\":1,\"position\":\"PG\",\"pointsPerGame\":1.0,\"reboundsPerGame\":1.0,"
                        + "\"assistsPerGame\":1.0,\"stealsPerGame\":1.0,\"gamesPlayed\":1,\"minutesPerGame\":1.0,"
                        + "\"fieldGoalPct\":1.0,\"threePointPct\":1.0,\"freeThrowPct\":1.0,\"blocksPerGame\":1.0,"
                        + "\"turnoversPerGame\":1.0,\"efficiency\":1.0,\"trueShootingPct\":1.0,\"usagePct\":1.0,"
                        + "\"height\":\"190\",\"weight\":90,\"country\":\"US\"}", PlayerRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void gameNewsRequestRejectsNumericTitle() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"title\":123,\"summary\":\"s\",\"content\":\"c\",\"homeTeam\":\"h\",\"awayTeam\":\"a\","
                        + "\"gameStartTime\":\"2026-09-28T10:00:00\",\"gameEndTime\":\"2026-09-28T12:00:00\"}",
                GameNewsRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }
}
