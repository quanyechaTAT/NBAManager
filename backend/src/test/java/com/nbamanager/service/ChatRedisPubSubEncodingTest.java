package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbamanager.config.RedisConfig;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Pub/Sub 事件体编码回归。
 *
 * <h2>缺陷</h2>
 * {@code ChatRedisPubSub} 把<b>已经序列化成 JSON 文本</b>的事件交给应用的
 * {@code RedisTemplate<String,Object>}（value serializer = GenericJackson2JsonRedisSerializer）发布，
 * 字符串被二次 JSON 编码：订阅端 {@code new String(message.getBody())} 拿到的是
 * 带转义的字面量，{@code objectMapper.readValue(body, Map.class)} 抛
 * "Cannot construct instance of LinkedHashMap ... from String value"。
 *
 * 后果：跨实例的聊天消息 / 在线状态 / 身份变更<b>全部静默失效</b>（单实例内仍正常，
 * 因此功能自测很难发现），只在日志里留一条 WARN。
 *
 * <h2>本测试</h2>
 * 1. 锁定发布用的是 {@link StringRedisTemplate}（纯字符串序列化），而不是那个 Jackson 模板；
 * 2. 用两个模板分别演示"事件体被二次编码"与"事件体保持裸 JSON"，把差异钉成可执行证据。
 */
class ChatRedisPubSubEncodingTest {

    /** 取出 publish 用的模板字段类型，确保它不会被改回 Jackson 序列化的模板。 */
    @Test
    void pubSubTemplateIsStringSerialized() throws Exception {
        Field field = ChatRedisPubSub.class.getDeclaredField("pubSubTemplate");

        assertThat(field.getType())
                .as("发布事件必须使用 StringRedisTemplate：事件体已是 JSON 文本，"
                        + "再用 Jackson 序列化会二次编码，订阅端必然解析失败")
                .isEqualTo(StringRedisTemplate.class);
    }

    /**
     * 用真实序列化器演示二次编码的确切形态。
     *
     * GenericJackson2JsonRedisSerializer 会把传入的 String 再包一层 JSON 字符串，
     * 于是 "{\"type\":...}" 变成 "\"{\\\"type\\\":...}\""。
     */
    @Test
    void jacksonTemplateDoubleEncodesJsonTextWhileStringTemplateDoesNot() {
        RedisConfig config = new RedisConfig(1800, 60, 60);
        String eventJson = "{\"type\":\"USER_IDENTITY_UPDATED\",\"userId\":1,\"username\":\"probe\"}";

        // 现状（缺陷）：应用模板的 value serializer 是 Jackson
        byte[] viaJackson = config.jacksonRedisSerializer().serialize(eventJson);

        // 修复后：StringRedisTemplate 用纯字符串序列化
        byte[] viaString = new StringRedisSerializer().serialize(eventJson);

        String decodedFromJackson = new String(viaJackson);
        String decodedFromString = new String(viaString);

        // 裸 JSON：订阅端直接 readValue(...) 就能解析
        assertThat(decodedFromString).isEqualTo(eventJson);

        // 二次编码：body 是一个"被引号包起来的字符串"，不是 JSON 对象
        assertThat(decodedFromJackson).startsWith("\"");
        assertThat(decodedFromJackson).contains("\\\"");
        assertThat(decodedFromJackson).isNotEqualTo(eventJson);

        // 证明订阅端为什么会失败：对这个 body 按 Map 解析必然抛错
        ObjectMapper mapper = new ObjectMapper();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> mapper.readValue(decodedFromJackson, Map.class)))
                .as("二次编码的事件体无法解析成 Map —— 这就是跨实例广播静默失效的原因")
                .isNotNull();
        // 而裸 JSON 可以正常解析
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> mapper.readValue(decodedFromString, Map.class)))
                .isNull();
    }
}
