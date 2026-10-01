package com.nbamanager.service.deepseek;

import com.nbamanager.web.dto.TimelineEventDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AiGroundingValidator {

    public record ValidationResult(
            boolean valid,
            String safeResponse,
            String citedEventIdsJson,
            String citedStatKeysJson) {

        public ValidationResult(boolean valid, String safeResponse) {
            this(valid, safeResponse, null, null);
        }
    }

    private static final String FALLBACK_RESPONSE = "当前 Play-by-Play 数据不足以支持这个判断。";

    public ValidationResult validate(String aiOutput, List<TimelineEventDto> facts) {
        if (aiOutput == null || aiOutput.isBlank()) {
            return new ValidationResult(false, FALLBACK_RESPONSE);
        }

        Map<String, Long> knownEvents = knownEventKeys(facts);
        if (knownEvents.isEmpty()) {
            return new ValidationResult(false, FALLBACK_RESPONSE);
        }

        try {
            JSONObject root = new JSONObject(stripCodeFence(aiOutput));
            JSONArray claims = root.optJSONArray("claims");
            if (claims == null || claims.isEmpty()) {
                return reject("AI output does not contain claims");
            }

            List<String> safeClaims = new ArrayList<>();
            Set<Long> citedEventIds = new LinkedHashSet<>();
            Set<String> citedStatKeys = new LinkedHashSet<>();

            for (int i = 0; i < claims.length(); i++) {
                JSONObject claim = claims.optJSONObject(i);
                if (claim == null) {
                    return reject("AI claim is not an object");
                }
                String text = claim.optString("text", "").trim();
                JSONArray citationKeys = claim.optJSONArray("citationKeys");
                if (text.isBlank() || citationKeys == null || citationKeys.isEmpty()) {
                    return reject("AI claim is missing text or citations");
                }
                for (int j = 0; j < citationKeys.length(); j++) {
                    String key = citationKeys.optString(j, "").trim();
                    if (knownEvents.containsKey(key)) {
                        citedEventIds.add(knownEvents.get(key));
                    } else if (key.startsWith("stat:") && key.length() > "stat:".length()) {
                        citedStatKeys.add(key.substring("stat:".length()));
                    } else {
                        return reject("AI claim cites unknown fact key: " + key);
                    }
                }
                safeClaims.add(text);
            }

            if (citedEventIds.isEmpty() && citedStatKeys.isEmpty()) {
                return reject("AI output has no usable citations");
            }

            return new ValidationResult(
                    true,
                    String.join("\n\n", safeClaims),
                    citedEventIds.isEmpty() ? null : new JSONArray(citedEventIds).toString(),
                    citedStatKeys.isEmpty() ? null : new JSONArray(citedStatKeys).toString());
        } catch (RuntimeException exception) {
            return reject("AI output is not valid grounded JSON");
        }
    }

    private Map<String, Long> knownEventKeys(List<TimelineEventDto> facts) {
        Map<String, Long> keys = new LinkedHashMap<>();
        if (facts == null) {
            return keys;
        }
        for (TimelineEventDto fact : facts) {
            if (fact.id() != null) {
                keys.put("event:" + fact.id(), fact.id());
            }
        }
        return keys;
    }

    private ValidationResult reject(String reason) {
        log.warn("Rejected ungrounded AI replay output: {}", reason);
        return new ValidationResult(false, FALLBACK_RESPONSE);
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}
