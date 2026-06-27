package com.nbamanager.util;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Python脚本执行器 - 使用虚拟环境和唯一临时文件避免竞争条件
 */
@Slf4j
@Component
public class PythonScriptRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PythonScriptRunner.class);

    private static final String SCRIPT_DIR = "backend/scripts";
    private static final String VENV_PYTHON_WIN = "backend/scripts/venv/Scripts/python.exe";
    private static final String VENV_PYTHON_UNIX = "backend/scripts/venv/bin/python";

    /**
     * 执行Python脚本并返回JSON结果
     * @param action 脚本动作
     * @param args 额外参数
     * @param timeoutSeconds 超时时间（秒）
     * @return JSON结果，失败返回null
     */
    public JSONObject execute(String action, String[] args, int timeoutSeconds) {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            log.error("Python脚本未找到");
            return null;
        }

        // 生成唯一输出文件名，避免竞争条件
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String outputFileName = "output_" + uniqueId + ".json";
        File scriptFile = new File(scriptPath);
        File outputFile = new File(scriptFile.getParentFile(), outputFileName);

        try {
            // 构建命令（使用虚拟环境Python）
            String pythonExec = getPythonExecutable();
            String[] cmdArray = new String[3 + (args != null ? args.length : 0)];
            cmdArray[0] = pythonExec;
            cmdArray[1] = scriptPath;
            cmdArray[2] = action;
            if (args != null) {
                System.arraycopy(args, 0, cmdArray, 3, args.length);
            }

            ProcessBuilder pb = new ProcessBuilder(cmdArray);
            pb.redirectErrorStream(false);
            pb.directory(scriptFile.getParentFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("OUTPUT_FILE", outputFileName); // 传递输出文件名

            // 代理配置
            String proxyHost = com.nbamanager.config.EnvFileReader.get("NBA_PROXY_HOST");
            String proxyPort = com.nbamanager.config.EnvFileReader.get("NBA_PROXY_PORT");
            if (proxyHost != null && !proxyHost.isEmpty()) {
                pb.environment().put("NBA_PROXY_HOST", proxyHost);
            }
            if (proxyPort != null && !proxyPort.isEmpty()) {
                pb.environment().put("NBA_PROXY_PORT", proxyPort);
            }

            // 翻译API配置
            String mimoApiKey = com.nbamanager.config.EnvFileReader.get("MIMO_API_KEY");
            String mimoBaseUrl = com.nbamanager.config.EnvFileReader.get("MIMO_BASE_URL");
            if (mimoApiKey != null && !mimoApiKey.isEmpty()) {
                pb.environment().put("MIMO_API_KEY", mimoApiKey);
            }
            if (mimoBaseUrl != null && !mimoBaseUrl.isEmpty()) {
                pb.environment().put("MIMO_BASE_URL", mimoBaseUrl);
            }

            log.info("执行Python脚本: action={}, args={}", action, String.join(" ", args != null ? args : new String[0]));

            Process process = pb.start();

            // 读取stdout
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            }

            // 读取stderr
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Python脚本执行超时: action={}, timeout={}s", action, timeoutSeconds);
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python脚本执行失败: action={}, exitCode={}, stderr={}", action, exitCode, stderr.toString());
                return null;
            }

            // 从唯一输出文件读取JSON
            if (!outputFile.exists()) {
                log.error("输出文件不存在: {}", outputFile.getAbsolutePath());
                return null;
            }

            String jsonStr = new String(java.nio.file.Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
            if (jsonStr.isEmpty()) {
                log.error("输出文件为空: {}", outputFile.getAbsolutePath());
                return null;
            }

            return new JSONObject(jsonStr);

        } catch (Exception e) {
            log.error("执行Python脚本异常: action={}", action, e);
            return null;
        } finally {
            // 清理临时文件
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }
    }

    /**
     * 简化版执行方法（无额外参数）
     */
    public JSONObject execute(String action, int timeoutSeconds) {
        return execute(action, null, timeoutSeconds);
    }

    /**
     * 查找Python脚本路径
     */
    private String findScriptPath() {
        String[] possiblePaths = {
                "backend/scripts/nba_data_fetcher.py",
                "scripts/nba_data_fetcher.py",
                "../scripts/nba_data_fetcher.py",
                "nba_data_fetcher.py"
        };

        File baseDir = new File(System.getProperty("user.dir"));
        for (String path : possiblePaths) {
            File file = new File(baseDir, path);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * 获取Python可执行文件路径（优先使用虚拟环境）
     */
    private String getPythonExecutable() {
        File baseDir = new File(System.getProperty("user.dir"));

        // 检查虚拟环境Python
        String[] venvPaths = {VENV_PYTHON_WIN, VENV_PYTHON_UNIX};
        for (String venvPath : venvPaths) {
            File venvPython = new File(baseDir, venvPath);
            if (venvPython.exists()) {
                log.info("使用虚拟环境Python: {}", venvPython.getAbsolutePath());
                return venvPython.getAbsolutePath();
            }
        }

        // 降级使用系统Python
        log.warn("未找到虚拟环境Python，使用系统Python");
        return "python";
    }
}
