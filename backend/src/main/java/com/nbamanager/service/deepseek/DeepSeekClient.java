package com.nbamanager.service.deepseek;

import com.nbamanager.config.EnvFileReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeepSeekClient {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final int TIMEOUT_SECONDS = 60;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record ChatResponse(String content, String model, boolean success, String error) {}

    public String configuredModel() {
        String model = EnvFileReader.get("DEEPSEEK_MODEL");
        return model == null || model.isEmpty() ? DEFAULT_MODEL : model;
    }

    public ChatResponse chat(String systemPrompt, String userPrompt) {
        String apiKey = EnvFileReader.get("DEEPSEEK_API_KEY");
        String baseUrl = EnvFileReader.get("DEEPSEEK_API_BASE");
        if (baseUrl == null || baseUrl.isEmpty()) baseUrl = DEFAULT_BASE_URL;
        String model = configuredModel();

        if (apiKey == null || apiKey.isEmpty()) {
            return new ChatResponse(null, model, false, "DEEPSEEK_API_KEY not configured");
        }

        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 0.3);
            body.put("max_tokens", 2000);

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.put(userMsg);

            body.put("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("DeepSeek API error: status={}, body={}", response.statusCode(), response.body());
                return new ChatResponse(null, model, false, "HTTP " + response.statusCode());
            }

            JSONObject resp = new JSONObject(response.body());
            String content = resp.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            return new ChatResponse(content.trim(), model, true, null);

        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return new ChatResponse(null, model, false, e.getMessage());
        }
    }
}
