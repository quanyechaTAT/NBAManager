package com.nbamanager.web.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * 严格字符串反序列化器：只接受 JSON string，其它标量一律拒绝。
 *
 * 背景（安全报告 §十 10.5 RISK-1）：
 * Jackson 默认会把 JSON 数字/布尔"宽容地"强制转成 String，
 * 于是 {@code {"question": 123}} 变成 "123" 通过了长度校验（>=2）并返回 200。
 * 类型契约不严会掩盖调用方 bug（客户端传错类型却拿到 200）。
 *
 * 用法：{@code @JsonDeserialize(using = StrictStringDeserializer.class) String question;}
 * 非 string 输入会抛 JsonMappingException，被 GlobalExceptionHandler 的
 * HttpMessageNotReadableException 处理器转成 400「请求体格式错误」。
 *
 * 允许 JSON null（交由字段自身的 @NotNull/业务校验决定是否合法），
 * 因为 null 与"类型错误"是两回事。
 */
public class StrictStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw JsonMappingException.from(parser,
                    "expected JSON string but got " + node.getNodeType().name().toLowerCase());
        }
        return node.textValue();
    }
}