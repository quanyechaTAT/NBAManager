package com.nbamanager.service;

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
import java.util.concurrent.TimeUnit;

/**
 * RAG 服务
 * 通过HTTP调用Python常驻服务，避免每次加载模型
 */
@Service
@Slf4j
public class RAGService {

    private static final String RAG_SERVER_URL = "http://127.0.0.1:8899";
    private static final int TIMEOUT_MS = 30000;

    private volatile boolean serverStarted = false;

    /**
     * 确保RAG服务已启动
     */
    public synchronized void ensureServerStarted() {
        if (serverStarted) {
            return;
        }

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
            URL url = new URL(RAG_SERVER_URL + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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

            ProcessBuilder pb = new ProcessBuilder(pythonExec, "-m", "rag_app.main", "8899");
            pb.redirectErrorStream(true);
            pb.directory(scriptsDir);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(scriptsDir, "rag_app.log")));
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            // 传递MIMO API配置
            String mimoApiKey = com.nbamanager.config.EnvFileReader.get("MIMO_API_KEY");
            String mimoBaseUrl = com.nbamanager.config.EnvFileReader.get("MIMO_BASE_URL");
            if (mimoApiKey != null && !mimoApiKey.isEmpty()) {
                pb.environment().put("MIMO_API_KEY", mimoApiKey);
            }
            if (mimoBaseUrl != null && !mimoBaseUrl.isEmpty()) {
                pb.environment().put("MIMO_BASE_URL", mimoBaseUrl);
            }

            // 传递数据库配置
            String dbUser = com.nbamanager.config.EnvFileReader.get("DB_USERNAME");
            String dbPass = com.nbamanager.config.EnvFileReader.get("DB_PASSWORD");
            if (dbUser != null) pb.environment().put("DB_USERNAME", dbUser);
            if (dbPass != null) pb.environment().put("DB_PASSWORD", dbPass);

            // 后台启动
            pb.start();

            // 等待服务就绪
            for (int i = 0; i < 30; i++) {
                Thread.sleep(2000);
                if (isServerRunning()) {
                    log.info("RAG服务启动成功");
                    serverStarted = true;
                    return;
                }
                log.info("等待RAG服务启动... ({}/30)", i + 1);
            }

            log.error("RAG服务启动超时");

        } catch (Exception e) {
            log.error("启动RAG服务失败", e);
        }
    }

    /**
     * RAG 查询
     */
    public RAGResult query(String question, int topK) {
        long startTime = System.currentTimeMillis();

        try {
            ensureServerStarted();

            if (!isServerRunning()) {
                return RAGResult.error("RAG服务未运行");
            }

            // 调用RAG服务（使用GET请求）
            String queryUrl = RAG_SERVER_URL + "/query?question=" + java.net.URLEncoder.encode(question, "UTF-8") + "&top_k=" + topK;
            JSONObject response = getJSON(queryUrl);

            if (response == null) {
                return RAGResult.error("RAG服务无响应");
            }

            if (response.optBoolean("success", false)) {
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
                        response.optString("model", "ritrieve_zh_v1 + mimo-v2.5")
                );
            } else {
                return RAGResult.error(response.optString("error", "查询失败"));
            }

        } catch (Exception e) {
            log.error("RAG查询异常", e);
            return RAGResult.error("查询异常: " + e.getMessage());
        }
    }

    /**
     * 获取RAG统计信息
     */
    public RAGStats getStats() {
        try {
            ensureServerStarted();

            if (!isServerRunning()) {
                return new RAGStats(0, "未启动", "未启动");
            }

            JSONObject response = getJSON(RAG_SERVER_URL + "/stats");
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
    public boolean rebuildIndex() {
        try {
            ensureServerStarted();

            if (!isServerRunning()) {
                return false;
            }

            JSONObject response = postJSON(RAG_SERVER_URL + "/rebuild", new JSONObject());
            return response != null && "ok".equals(response.optString("status"));

        } catch (Exception e) {
            log.error("重建索引失败", e);
            return false;
        }
    }

    /**
     * 发送POST请求
     */
    private JSONObject postJSON(String urlStr, JSONObject body) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

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
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

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
