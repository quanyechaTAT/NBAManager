package com.nbamanager.service;

import com.nbamanager.config.RagTimeoutProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RAG 服务
 * 通过HTTP调用Python常驻服务，避免每次加载模型
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {

    /**
     * RAG 服务地址，可用环境变量 RAG_SERVER_URL 或配置项 app.rag.server-url 覆盖
     */
    @org.springframework.beans.factory.annotation.Value("${app.rag.server-url:${RAG_SERVER_URL:http://127.0.0.1:8899}}")
    private String ragServerUrl;
    private final RagTimeoutProperties timeoutProperties;

    /** stats 10s TTL 内存缓存（见 getStats 注释） */
    private static final long STATS_TTL_MS = 10_000;
    private final java.util.concurrent.atomic.AtomicReference<RAGStats> statsCache =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicLong statsCacheAt = new java.util.concurrent.atomic.AtomicLong();

    private volatile boolean serverStarted = false;

    /** 启动即后台拉起 RAG 并预热模型：避免首个用户请求承担 30~60s 的冷启动 */
    @jakarta.annotation.PostConstruct
    public void warmupAsync() {
        Thread t = new Thread(() -> {
            try {
                ensureServerStarted();
            } catch (Exception e) {
                log.warn("RAG 后台预热失败（首次请求将重试拉起）: {}", e.getMessage());
            }
        }, "rag-warmup");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 确保RAG服务已启动
     */
    public synchronized void ensureServerStarted() {
        if (serverStarted && isServerRunning()) {
            return;
        }
        serverStarted = false;

        // 检查服务是否已经在运行
        if (isServerRunning()) {
            serverStarted = true;
            return;
        }

        // 启动RAG服务
        startRAGServer();
    }

    /**
     * 检查RAG服务是否运行
     */
    private boolean isServerRunning() {
        try {
            URL url = new URL(ragServerUrl + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // 禁用 JDK KeepAliveCache 死连接复用（uvicorn 5s 关空闲连接）
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            conn.disconnect();

            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 启动RAG服务（后台进程）
     */
    private void startRAGServer() {
        try {
            String pythonExec = getPythonExecutable();
            File scriptsDir = findScriptsDir();

            if (scriptsDir == null) {
                log.error("RAG脚本目录未找到");
                return;
            }

            log.info("启动RAG服务...");

            ProcessBuilder pb = new ProcessBuilder(pythonExec, "-m", "rag_app.main", String.valueOf(resolveRagPort()));
            pb.redirectErrorStream(true);
            pb.directory(scriptsDir);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(scriptsDir, "rag_app.log")));
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            // 抓取代理（stats.nba.com 用）对云服务是毒药：DeepSeek API 国内直连，
            // 剥离代理变量，避免 LLM 调用被 127.0.0.1:7890 之类的连接拒绝打断
            pb.environment().remove("NBA_PROXY_HOST");
            pb.environment().remove("NBA_PROXY_PORT");
            pb.environment().remove("HTTP_PROXY");
            pb.environment().remove("HTTPS_PROXY");

            // 传递DeepSeek API配置
            String deepseekApiKey = com.nbamanager.config.EnvFileReader.get("DEEPSEEK_API_KEY");
            String deepseekApiBase = com.nbamanager.config.EnvFileReader.get("DEEPSEEK_API_BASE");
            if (deepseekApiKey != null && !deepseekApiKey.isEmpty()) {
                pb.environment().put("DEEPSEEK_API_KEY", deepseekApiKey);
            }
            if (deepseekApiBase != null && !deepseekApiBase.isEmpty()) {
                pb.environment().put("DEEPSEEK_API_BASE", deepseekApiBase);
            }

            // 传递数据库配置
            String dbUser = com.nbamanager.config.EnvFileReader.get("DB_USERNAME");
            String dbPass = com.nbamanager.config.EnvFileReader.get("DB_PASSWORD");
            if (dbUser != null) pb.environment().put("DB_USERNAME", dbUser);
            if (dbPass != null) pb.environment().put("DB_PASSWORD", dbPass);

            // 后台启动
            pb.start();

            // 等待服务就绪（/health 在模型预加载完成前返回 503，循环会持续等待）
            for (int i = 0; i < 60; i++) {
                Thread.sleep(2000);
                if (isServerRunning()) {
                    log.info("RAG服务启动成功，预热中...");
                    warmupQuery();
                    serverStarted = true;
                    return;
                }
                log.info("等待RAG服务启动... ({}/60)", i + 1);
            }

            log.error("RAG服务启动超时");

        } catch (Exception e) {
            log.error("启动RAG服务失败", e);
        }
    }

    /** 冷启动预热：Chroma 首次初始化与模型首推理耗时数秒~数十秒，
     *  若留给用户第一问会表现为长时间无响应甚至断流，这里提前消耗掉 */
    private void warmupQuery() {
        try {
            URL url = new URL(ragServerUrl + "/query");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(180000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write("{\"question\":\"你好\",\"top_k\":1}".getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            conn.getInputStream().readAllBytes();
            log.info("RAG预热完成 (http {})", code);
        } catch (Exception e) {
            log.warn("RAG预热失败（不影响后续使用）: {}", e.getMessage());
        }
    }

    /** 从 ragServerUrl 解析端口，供拉起子进程时对齐监听端口 */
    private int resolveRagPort() {
        try {
            int port = new java.net.URI(ragServerUrl).getPort();
            return port > 0 ? port : 8899;
        } catch (Exception e) {
            return 8899;
        }
    }

    /**
     * RAG 查询
     */
    public RAGResult query(String question, int topK) {
        return query(question, topK, null);
    }

    public RAGResult query(String question, int topK, String sessionId) {
        long startTime = System.currentTimeMillis();

        try {
            ensureServerStarted();

            if (!isServerRunning()) {
                return RAGResult.error("RAG服务未运行");
            }

            // 调用RAG服务（使用GET请求）
            String queryUrl = ragServerUrl + "/query?question=" + java.net.URLEncoder.encode(question, "UTF-8") + "&top_k=" + topK;
            if (sessionId != null && !sessionId.isBlank()) {
                queryUrl += "&session_id=" + java.net.URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
            }
            JSONObject response = getJSON(queryUrl);

            return mapQueryResponse(response);

        } catch (Exception e) {
            log.error("RAG查询异常", e);
            return RAGResult.error("查询异常: " + e.getMessage());
        }
    }

    public RAGResult query(
            String question,
            int topK,
            String sessionId,
            List<Map<String, String>> conversationHistory) {
        long startTime = System.currentTimeMillis();

        try {
            ensureServerStarted();

            if (!isServerRunning()) {
                return RAGResult.error("RAG服务未运行");
            }

            JSONObject response = postJSON(
                    ragServerUrl + "/query",
                    buildQueryRequestBody(question, topK, sessionId, conversationHistory)
            );
            return mapQueryResponse(response);

        } catch (Exception e) {
            log.error("RAG查询异常", e);
            return RAGResult.error("查询异常: " + e.getMessage());
        }
    }

    private RAGResult mapQueryResponse(JSONObject response) {
        if (response == null) {
            return RAGResult.error("RAG服务无响应");
        }

        if (!response.optBoolean("success", false)) {
            return RAGResult.error(response.optString("error", "查询失败"));
        }

        List<String> sources = new ArrayList<>();
        JSONArray sourcesArray = response.optJSONArray("sources");
        if (sourcesArray != null) {
            for (int i = 0; i < sourcesArray.length(); i++) {
                sources.add(sourcesArray.getString(i));
            }
        }

        return RAGResult.success(
                response.optString("answer", ""),
                sources,
                response.optInt("response_time", 0),
                response.optString("model", "ritrieve_zh_v1 + deepseek-v4-flash")
        );
    }

    private JSONObject buildQueryRequestBody(
            String question,
            int topK,
            String sessionId,
            List<Map<String, String>> conversationHistory) {
        JSONObject body = new JSONObject()
                .put("question", question == null ? "" : question)
                .put("top_k", Math.max(1, Math.min(20, topK)))
                .put("force_web_search", false);
        if (sessionId != null && !sessionId.isBlank()) {
            body.put("session_id", sessionId);
        }

        JSONArray history = new JSONArray();
        if (conversationHistory != null) {
            int start = Math.max(0, conversationHistory.size() - 8);
            for (int i = start; i < conversationHistory.size(); i++) {
                Map<String, String> turn = conversationHistory.get(i);
                if (turn == null) {
                    continue;
                }

                String role = turn.getOrDefault("role", "").trim().toLowerCase(Locale.ROOT);
                String content = turn.getOrDefault("content", "").trim();
                if (!(role.equals("user") || role.equals("assistant")) || content.isEmpty()) {
                    continue;
                }
                if (content.length() > 1000) {
                    content = content.substring(0, 1000);
                }
                history.put(new JSONObject().put("role", role).put("content", content));
            }
        }
        body.put("conversation_history", history);
        return body;
    }

    /**
     * 获取RAG统计信息
     */
    /**
     * RAG 统计（10s 内存缓存，第 8 轮 P0-1）。
     *
     * 该数据（document_count / 模型名）变化频率极低，而每次实时获取要
     * ensureServerStarted + isServerRunning + 一次 java→python HTTP（p50 96ms），
     * 20 并发下 p95 175ms、全站最慢端点。缓存 10s TTL：rebuild 触发时主动清一次。
     */
    public RAGStats getStats() {
        long now = System.currentTimeMillis();
        RAGStats cached = statsCache.get();
        if (cached != null && now - statsCacheAt.get() < STATS_TTL_MS) {
            return cached;
        }
        RAGStats fresh = fetchStats();
        statsCache.set(fresh);
        statsCacheAt.set(now);
        return fresh;
    }

    private RAGStats fetchStats() {
        try {
            ensureServerStarted();

            if (!isServerRunning()) {
                return new RAGStats(0, "未启动", "未启动");
            }

            JSONObject response = getJSON(ragServerUrl + "/stats");
            if (response != null) {
                return new RAGStats(
                        response.optInt("document_count", 0),
                        response.optString("embedding_model", "未知"),
                        response.optString("llm_model", "未知")
                );
            }

            return new RAGStats(0, "错误", "错误");

        } catch (Exception e) {
            log.error("获取RAG统计失败", e);
            return new RAGStats(0, "错误", "错误");
        }
    }

    /**
     * 重建索引
     */
    public RAGStats getStatsSnapshot() {
        try {
            if (!isServerRunning()) {
                return new RAGStats(0, "not_started", "not_started");
            }

            JSONObject response = getJSON(ragServerUrl + "/stats");
            if (response != null) {
                return new RAGStats(
                        response.optInt("document_count", 0),
                        response.optString("embedding_model", "unknown"),
                        response.optString("llm_model", "unknown")
                );
            }

            return new RAGStats(0, "error", "error");
        } catch (Exception e) {
            log.debug("RAG stats snapshot unavailable: {}", e.getMessage());
            return new RAGStats(0, "error", "error");
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean rebuilding =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public boolean isRebuilding() {
        return rebuilding.get();
    }

    public boolean rebuildIndex() {
        // 单飞锁：并发触发（或前端连点）时只放行一次真实重建，其余直接返回 busy
        if (!rebuilding.compareAndSet(false, true)) {
            log.info("RAG 索引重建进行中，跳过本次触发");
            return false;
        }
        statsCache.set(null);   // 重建触发：立即清 stats 缓存（10s TTL 内也要反映新语料计数）
        try {
            ensureServerStarted();

            if (!isServerRunning()) {
                return false;
            }

            JSONObject response = postJSON(ragServerUrl + "/rebuild", new JSONObject());
            String status = response == null ? "error" : response.optString("status", "error");
            if ("busy".equals(status)) {
                throw new IllegalStateException("索引重建进行中，请稍后再试");
            }
            return "ok".equals(status) || "started".equals(status);

        } catch (IllegalStateException e) {
            // busy：交给上层以 400/语义化消息返回
            throw e;
        } catch (Exception e) {
            log.error("重建索引失败", e);
            return false;
        } finally {
            rebuilding.set(false);
        }
    }

    /**
     * 发送POST请求
     */
    private JSONObject postJSON(String urlStr, JSONObject body) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // 禁用 JDK KeepAliveCache 死连接复用（uvicorn 5s 关空闲连接）
            conn.setRequestProperty("Connection", "close");
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeoutProperties.getConnectMillis());
            conn.setReadTimeout(timeoutProperties.getReadMillis());

            byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                String response = readResponse(conn);
                conn.disconnect();
                return new JSONObject(response);
            }

            conn.disconnect();
            return null;

        } catch (Exception e) {
            log.error("POST请求失败: {}", urlStr, e);
            return null;
        }
    }

    /**
     * 发送GET请求
     */
    private JSONObject getJSON(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // 禁用 JDK KeepAliveCache 死连接复用（uvicorn 5s 关空闲连接）
            conn.setRequestProperty("Connection", "close");
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutProperties.getConnectMillis());
            conn.setReadTimeout(timeoutProperties.getReadMillis());

            int code = conn.getResponseCode();
            if (code == 200) {
                String response = readResponse(conn);
                conn.disconnect();
                return new JSONObject(response);
            }

            conn.disconnect();
            return null;

        } catch (Exception e) {
            log.error("GET请求失败: {}", urlStr, e);
            return null;
        }
    }

    /**
     * 读取响应
     */
    private String readResponse(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getPythonExecutable() {
        File baseDir = new File(System.getProperty("user.dir"));

        String[] venvPaths = {
                "scripts/venv312/Scripts/python.exe",
                "scripts/venv312/bin/python",
                "backend/scripts/venv312/Scripts/python.exe",
                "backend/scripts/venv312/bin/python",
                "scripts/venv/Scripts/python.exe",
                "scripts/venv/bin/python",
                "backend/scripts/venv/Scripts/python.exe",
                "backend/scripts/venv/bin/python"
        };

        for (String venvPath : venvPaths) {
            File venvPython = new File(baseDir, venvPath);
            if (venvPython.exists()) {
                return venvPython.getAbsolutePath();
            }
        }

        return "python";
    }

    private File findScriptsDir() {
        String[] possiblePaths = {
                "scripts",
                "backend/scripts",
                "../scripts"
        };

        File baseDir = new File(System.getProperty("user.dir"));
        for (String path : possiblePaths) {
            File dir = new File(baseDir, path);
            File ragAppDir = new File(dir, "rag_app");
            if (dir.isDirectory() && ragAppDir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    /**
     * RAG查询结果
     */
    public record RAGResult(
            boolean success,
            String answer,
            List<String> sources,
            int responseTime,
            String model,
            String error
    ) {
        public static RAGResult success(String answer, List<String> sources, int responseTime, String model) {
            return new RAGResult(true, answer, sources, responseTime, model, null);
        }

        public static RAGResult error(String error) {
            return new RAGResult(false, null, List.of(), 0, null, error);
        }
    }

    /**
     * RAG统计信息
     */
    public record RAGStats(
            int documentCount,
            String embeddingModel,
            String llmModel
    ) {}
}
