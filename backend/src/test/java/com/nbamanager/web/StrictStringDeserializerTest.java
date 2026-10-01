package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbamanager.web.support.StrictStringDeserializer;
import org.junit.jupiter.api.Test;

/**
 * F3 回归：严格字符串反序列化。
 *
 * 安全报告 §十 10.5 RISK-1：Jackson 默认把 JSON number/boolean 强转成 String，
 * 于是 {"question": 123} 变成 "123"，绕过类型契约并返回 200。
 * 修复后只有 JSON string 能通过，其余抛 JsonMappingException（由全局处理器转 400）。
 */
class StrictStringDeserializerTest {

    /** 只声明目标字段，模拟 RAGQueryRequest/RAGStreamRequest 的 question。 */
    record Payload(
            @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                    using = StrictStringDeserializer.class)
            String question) {
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsJsonString() throws Exception {
        Payload p = mapper.readValue("{\"question\":\"湖人战绩如何\"}", Payload.class);
        assertThat(p.question()).isEqualTo("湖人战绩如何");
    }

    @Test
    void rejectsJsonNumber() {
        assertThatThrownBy(() -> mapper.readValue("{\"question\":123}", Payload.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class)
                .hasMessageContaining("string");
    }

    @Test
    void rejectsJsonBoolean() {
        assertThatThrownBy(() -> mapper.readValue("{\"question\":true}", Payload.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void rejectsJsonArray() {
        assertThatThrownBy(() -> mapper.readValue("{\"question\":[1,2]}", Payload.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void rejectsJsonObject() {
        assertThatThrownBy(() -> mapper.readValue("{\"question\":{\"a\":1}}", Payload.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void acceptsJsonNullAsNullSoBusinessValidationDecides() throws Exception {
        // null 与"类型错误"是两回事：这里返回 null，交由长度/非空校验决定语义
        assertThat(mapper.readValue("{\"question\":null}", Payload.class).question()).isNull();
    }

    @Test
    void rejectsNumericStringThatLooksLikeNumber() {
        // 数字型字符串（带引号）是合法 JSON string，应放行；长度校验在 controller 层
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> mapper.readValue("{\"question\":\"12\"}", Payload.class))).isNull();
    }
}