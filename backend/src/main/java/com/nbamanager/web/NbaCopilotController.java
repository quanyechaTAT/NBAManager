package com.nbamanager.web;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nba-copilot")
@Slf4j
public class NbaCopilotController {

    private static final String COPILOT_URL = "http://127.0.0.1:8898/api/nba-copilot";

    @PostMapping("/chat")
    public String chat(@RequestBody String body) {
        return proxyPost(COPILOT_URL + "/chat", body);
    }

    @GetMapping("/conversations")
    public String listConversations() {
        return proxyGet(COPILOT_URL + "/conversations");
    }

    @GetMapping("/conversations/{id}")
    public String getConversation(@PathVariable String id) {
        return proxyGet(COPILOT_URL + "/conversations/" + id);
    }

    @DeleteMapping("/conversations/{id}")
    public String deleteConversation(@PathVariable String id) {
        return proxyDelete(COPILOT_URL + "/conversations/" + id);
    }

    private String proxyPost(String urlStr, String body) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(60000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return readResponse(conn);
        } catch (Exception e) {
            log.error("Copilot proxy error: {}", e.getMessage());
            return "{\"error\": \"Copilot service unavailable\"}";
        }
    }

    private String proxyGet(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            return readResponse(conn);
        } catch (Exception e) {
            log.error("Copilot proxy error: {}", e.getMessage());
            return "{\"error\": \"Copilot service unavailable\"}";
        }
    }

    private String proxyDelete(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            return readResponse(conn);
        } catch (Exception e) {
            log.error("Copilot proxy error: {}", e.getMessage());
            return "{\"error\": \"Copilot service unavailable\"}";
        }
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(status >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }
}
