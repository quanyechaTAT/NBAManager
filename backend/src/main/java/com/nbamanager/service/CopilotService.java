package com.nbamanager.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * NBA Copilot 多 Agent 服务（scripts/nba_copilot_app，uvicorn :8898）守护：
 * 应用启动即后台拉起，避免 /api/nba-copilot/** 成为一个永远无人应答的死接口。
 */
@Slf4j
@Service
public class CopilotService {

    @Value("${app.copilot.base-url:http://127.0.0.1:8898/api/nba-copilot}")
    private String copilotBaseUrl;

    private volatile boolean started = false;

    @PostConstruct
    public void warmup() {
        // 后台预热：启动失败不影响主应用（Controller 每次请求仍会兜底重试拉起）
        Thread t = new Thread(() -> {
            try {
                ensureServerStarted();
            } catch (Exception e) {
                log.warn("Copilot 服务预热失败（请求时将重试）: {}", e.getMessage());
            }
        }, "copilot-warmup");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void ensureServerStarted() {
        if (started && isServerRunning()) {
            return;
        }
        started = false;
        if (isServerRunning()) {
            started = true;
            return;
        }
        startServer();
    }

    private boolean isServerRunning() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(origin() + "/health").openConnection();
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

    private void startServer() {
        try {
            File appDir = findCopilotAppDir();
            if (appDir == null) {
                log.error("未找到 nba_copilot_app 目录，Copilot 服务无法启动");
                return;
            }
            log.info("启动 Copilot 服务...");
            ProcessBuilder pb = new ProcessBuilder(getPythonExecutable(), "main.py");
            pb.directory(appDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(appDir, "copilot.log")));
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("NBA_COPILOT_PORT", String.valueOf(resolvePort()));
            // DeepSeek 配置（多 Agent 的 LLM 底座）
            String apiKey = com.nbamanager.config.EnvFileReader.get("DEEPSEEK_API_KEY");
            String apiBase = com.nbamanager.config.EnvFileReader.get("DEEPSEEK_API_BASE");
            if (apiKey != null && !apiKey.isEmpty()) pb.environment().put("DEEPSEEK_API_KEY", apiKey);
            if (apiBase != null && !apiBase.isEmpty()) pb.environment().put("DEEPSEEK_API_BASE", apiBase);
            // 抓取代理对 DeepSeek API 是毒药，剥离
            pb.environment().remove("NBA_PROXY_HOST");
            pb.environment().remove("NBA_PROXY_PORT");
            pb.environment().remove("HTTP_PROXY");
            pb.environment().remove("HTTPS_PROXY");
            pb.start();

            for (int i = 0; i < 20; i++) {
                Thread.sleep(2000);
                if (isServerRunning()) {
                    log.info("Copilot 服务启动成功");
                    started = true;
                    return;
                }
            }
            log.error("Copilot 服务启动超时");
        } catch (Exception e) {
            log.error("启动 Copilot 服务失败", e);
        }
    }

    /** 从 copilotBaseUrl 解析 origin（scheme://host:port），health 探活与端口推导都用它 */
    private String origin() {
        try {
            URI uri = URI.create(copilotBaseUrl);
            int port = uri.getPort() > 0 ? uri.getPort() : 8898;
            return uri.getScheme() + "://" + uri.getHost() + ":" + port;
        } catch (Exception e) {
            return "http://127.0.0.1:18998";
        }
    }

    private int resolvePort() {
        try {
            int port = URI.create(copilotBaseUrl).getPort();
            return port > 0 ? port : 18998;
        } catch (Exception e) {
            return 8898;
        }
    }

    /** 与 RAGService 同一套 python 解析规则（venv312 优先，可用 app.python.executable 覆盖） */
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

    private File findCopilotAppDir() {
        String[] possiblePaths = {"scripts", "backend/scripts", "../scripts"};
        File baseDir = new File(System.getProperty("user.dir"));
        for (String path : possiblePaths) {
            File dir = new File(baseDir, path);
            File appDir = new File(dir, "nba_copilot_app");
            if (dir.isDirectory() && new File(appDir, "main.py").isFile()) {
                return appDir;
            }
        }
        return null;
    }
}
