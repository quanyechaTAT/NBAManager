package com.nbamanager.service;

import com.nbamanager.config.EnvFileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PythonScriptRunner {

    @Value("${app.python.executable:}")
    private String pythonExecutable;

    // 进程注册回调，供 SyncCoordinator 注册活跃进程
    private java.util.function.BiConsumer<Long, Process> processRegistrar;
    private java.util.function.BiConsumer<Long, Process> processUnregistrar;
    private java.util.function.Predicate<Long> cancellationChecker;

    public void setProcessRegistrar(java.util.function.BiConsumer<Long, Process> registrar) {
        this.processRegistrar = registrar;
    }

    public void setProcessUnregistrar(java.util.function.BiConsumer<Long, Process> unregistrar) {
        this.processUnregistrar = unregistrar;
    }

    public void setCancellationChecker(java.util.function.Predicate<Long> cancellationChecker) {
        this.cancellationChecker = cancellationChecker;
    }

    public record PythonResult(int exitCode, String stdout, String stderr, boolean timedOut, long elapsedMs) {
        public boolean success() { return exitCode == 0 && !timedOut; }
        public com.nbamanager.web.dto.SyncResult failure(String module) {
            if (exitCode == -2) return com.nbamanager.web.dto.SyncResult.cancelled(module);
            String detail = stderr == null ? "" : stderr.strip();
            detail = detail.substring(Math.max(0, detail.length() - 1500));
            return com.nbamanager.web.dto.SyncResult.failed(module, timedOut ? "PYTHON_TIMEOUT" : "PYTHON_FAILED",
                    (timedOut ? "采集超时 (" + elapsedMs / 1000 + "s)" : "采集进程退出: " + exitCode)
                            + (detail.isBlank() ? "" : ": " + detail));
        }
    }

    public PythonResult run(String scriptName, String[] args, int timeoutSeconds) {
        return run(scriptName, args, timeoutSeconds, Map.of());
    }

    public PythonResult run(String scriptName, String[] args, int timeoutSeconds, Map<String, String> extraEnv) {
        String python = resolvePython();
        String scriptPath = resolveScriptPath(scriptName);
        String outputFile = new File(new File(scriptPath).getParentFile(),
                "output_" + UUID.randomUUID().toString().substring(0, 8) + ".json").getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(false);

        // Build command
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(python);
        cmd.add(scriptPath);
        if (args != null) {
            for (String arg : args) cmd.add(arg);
        }
        pb.command(cmd);
        pb.directory(new File(scriptPath).getParentFile());

        // Environment
        Map<String, String> env = pb.environment();
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("OUTPUT_FILE", outputFile);

        // Proxy config
        String proxyHost = EnvFileReader.get("NBA_PROXY_HOST");
        String proxyPort = EnvFileReader.get("NBA_PROXY_PORT");
        if (proxyHost != null && !proxyHost.isEmpty()) env.put("NBA_PROXY_HOST", proxyHost);
        if (proxyPort != null && !proxyPort.isEmpty()) env.put("NBA_PROXY_PORT", proxyPort);

        // Translation API config
        String deepseekApiKey = EnvFileReader.get("DEEPSEEK_API_KEY");
        String deepseekApiBase = EnvFileReader.get("DEEPSEEK_API_BASE");
        if (deepseekApiKey != null && !deepseekApiKey.isEmpty()) env.put("DEEPSEEK_API_KEY", deepseekApiKey);
        if (deepseekApiBase != null && !deepseekApiBase.isEmpty()) env.put("DEEPSEEK_API_BASE", deepseekApiBase);

        // Extra env
        env.putAll(extraEnv);

        long start = System.currentTimeMillis();
        Process process = null;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            process = pb.start();
            final Process proc = process;

            // Concurrent stdout reading
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });

            // Concurrent stderr reading
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                // Wait for reader threads
                stdoutThread.join(3000);
                stderrThread.join(3000);
                log.error("Python脚本超时({}s): {}", timeoutSeconds, scriptName);
                return new PythonResult(-1, stdout.toString(), stderr.toString(), true, elapsed);
            }

            // Wait for reader threads to finish
            stdoutThread.join(3000);
            stderrThread.join(3000);

            int exitCode = process.exitValue();
            return new PythonResult(exitCode, stdout.toString(), stderr.toString(), false, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Python脚本执行异常: {}", scriptName, e);
            if (process != null) process.destroyForcibly();
            return new PythonResult(-1, "", e.getMessage(), false, elapsed);
        } finally {
            // Cleanup temp output file
            try { new File(outputFile).delete(); } catch (Exception ignored) {}
        }
    }

    public Process startProcess(String scriptName, String[] args, Map<String, String> extraEnv) throws Exception {
        String python = resolvePython();
        String scriptPath = resolveScriptPath(scriptName);

        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(false);

        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(python);
        cmd.add(scriptPath);
        if (args != null) {
            for (String arg : args) cmd.add(arg);
        }
        pb.command(cmd);
        pb.directory(new File(scriptPath).getParentFile());

        Map<String, String> env = pb.environment();
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("OUTPUT_FILE", new File(new File(scriptPath).getParentFile(),
                "output_" + UUID.randomUUID().toString().substring(0, 8) + ".json").getAbsolutePath());

        String proxyHost = EnvFileReader.get("NBA_PROXY_HOST");
        String proxyPort = EnvFileReader.get("NBA_PROXY_PORT");
        if (proxyHost != null && !proxyHost.isEmpty()) env.put("NBA_PROXY_HOST", proxyHost);
        if (proxyPort != null && !proxyPort.isEmpty()) env.put("NBA_PROXY_PORT", proxyPort);

        String deepseekApiKey = EnvFileReader.get("DEEPSEEK_API_KEY");
        String deepseekApiBase = EnvFileReader.get("DEEPSEEK_API_BASE");
        if (deepseekApiKey != null && !deepseekApiKey.isEmpty()) env.put("DEEPSEEK_API_KEY", deepseekApiKey);
        if (deepseekApiBase != null && !deepseekApiBase.isEmpty()) env.put("DEEPSEEK_API_BASE", deepseekApiBase);

        env.putAll(extraEnv);

        return pb.start();
    }

    private String resolvePython() {
        if (pythonExecutable != null && !pythonExecutable.isEmpty()) {
            return pythonExecutable;
        }
        // Try venv from multiple base directories
        String[] candidates = {
                "scripts/venv312/Scripts/python.exe",
                "scripts/venv312/bin/python",
                "backend/scripts/venv312/Scripts/python.exe",
                "backend/scripts/venv312/bin/python",
                "venv312/Scripts/python.exe",
                "venv312/bin/python",
        };
        // Check from user.dir and its parent
        File[] bases = {
                new File(System.getProperty("user.dir")),
                new File(System.getProperty("user.dir")).getParentFile()
        };
        for (File base : bases) {
            if (base == null) continue;
            for (String c : candidates) {
                File f = new File(base, c);
                if (f.exists()) return f.getAbsolutePath();
            }
        }
        return "python";
    }

    private String resolveScriptPath(String scriptName) {
        String[] possiblePaths = {
                "backend/scripts/" + scriptName,
                "scripts/" + scriptName,
                "../scripts/" + scriptName,
                scriptName
        };
        File baseDir = new File(System.getProperty("user.dir"));
        for (String path : possiblePaths) {
            File file = new File(baseDir, path);
            if (file.exists()) return file.getAbsolutePath();
        }
        return new File(baseDir, "backend/scripts/" + scriptName).getAbsolutePath();
    }

    /**
     * 执行 Python 脚本并注册进程到 SyncCoordinator，支持取消
     */
    public PythonResult runForJob(String scriptName, String[] args, int timeoutSeconds, Long jobId) {
        if (jobId != null && cancellationChecker != null && cancellationChecker.test(jobId)) {
            return new PythonResult(-2, "", "cancelled before process start", false, 0);
        }
        String python = resolvePython();
        String scriptPath = resolveScriptPath(scriptName);

        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(false);

        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(python);
        cmd.add(scriptPath);
        if (args != null) {
            for (String arg : args) cmd.add(arg);
        }
        pb.command(cmd);
        pb.directory(new File(scriptPath).getParentFile());

        Map<String, String> env = pb.environment();
        env.put("PYTHONIOENCODING", "utf-8");
        // 唯一输出文件，避免并发任务冲突
        env.put("OUTPUT_FILE", new File(new File(scriptPath).getParentFile(),
                "output_" + (jobId != null ? jobId : UUID.randomUUID().toString().substring(0, 8)) + ".json").getAbsolutePath());

        String proxyHost = EnvFileReader.get("NBA_PROXY_HOST");
        String proxyPort = EnvFileReader.get("NBA_PROXY_PORT");
        if (proxyHost != null && !proxyHost.isEmpty()) env.put("NBA_PROXY_HOST", proxyHost);
        if (proxyPort != null && !proxyPort.isEmpty()) env.put("NBA_PROXY_PORT", proxyPort);

        String deepseekApiKey = EnvFileReader.get("DEEPSEEK_API_KEY");
        String deepseekApiBase = EnvFileReader.get("DEEPSEEK_API_BASE");
        if (deepseekApiKey != null && !deepseekApiKey.isEmpty()) env.put("DEEPSEEK_API_KEY", deepseekApiKey);
        if (deepseekApiBase != null && !deepseekApiBase.isEmpty()) env.put("DEEPSEEK_API_BASE", deepseekApiBase);

        // 唯一输出文件路径
        String outputFileName = "output_" + (jobId != null ? jobId : UUID.randomUUID().toString().substring(0, 8)) + ".json";
        File outputFile = new File(new File(scriptPath).getParentFile(), outputFileName);
        env.put("OUTPUT_FILE", outputFile.getAbsolutePath());

        long start = System.currentTimeMillis();
        Process process = null;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            process = pb.start();
            final Process proc = process;

            // Register process for cancellation
            if (processRegistrar != null && jobId != null) {
                processRegistrar.accept(jobId, proc);
            }

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) stdout.append(line).append("\n");
                } catch (Exception ignored) {}
            });
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) stderr.append(line).append("\n");
                } catch (Exception ignored) {}
            });
            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                stdoutThread.join(3000);
                stderrThread.join(3000);
                return new PythonResult(-1, stdout.toString(), stderr.toString(), true, elapsed);
            }

            stdoutThread.join(3000);
            stderrThread.join(3000);

            if (cancellationChecker != null && cancellationChecker.test(jobId)) {
                return new PythonResult(-2, stdout.toString(), "cancelled", false, elapsed);
            }
            int exitCode = process.exitValue();
            return new PythonResult(exitCode, stdout.toString(), stderr.toString(), false, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            if (process != null) process.destroyForcibly();
            return new PythonResult(-1, "", e.getMessage(), false, elapsed);
        } finally {
            if (processUnregistrar != null && jobId != null && process != null) {
                processUnregistrar.accept(jobId, process);
            }
            // 清理临时输出文件
            try { if (outputFile.exists()) outputFile.delete(); } catch (Exception ignored) {}
        }
    }

    // ========== 向后兼容：旧调用方使用 execute() 返回 JSONObject ==========

    /**
     * 兼容旧 API：执行 Python 脚本并从输出文件读取 JSON
     */
    public JSONObject execute(String action, String[] args, int timeoutSeconds) {
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = action;
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        PythonResult result = run("nba_data_fetcher.py", fullArgs, timeoutSeconds);
        if (!result.success()) {
            log.error("Python execute failed: action={}, exitCode={}, stderr={}", action, result.exitCode(), result.stderr().substring(0, Math.min(200, result.stderr().length())));
            return null;
        }
        String stdout = result.stdout().trim();
        if (stdout.isEmpty()) {
            // 尝试从输出文件读取
            return readOutputFile();
        }
        try {
            return new org.json.JSONObject(stdout);
        } catch (Exception e) {
            // stdout 可能不是 JSON，尝试从文件读取
            return readOutputFile();
        }
    }

    public JSONObject execute(String action, int timeoutSeconds) {
        return execute(action, new String[0], timeoutSeconds);
    }

    public JSONObject executeForJob(String action, String[] args, int timeoutSeconds, Long jobId) {
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = action;
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        PythonResult result = runForJob("nba_data_fetcher.py", fullArgs, timeoutSeconds, jobId);
        if (!result.success()) return null;
        String stdout = result.stdout().trim();
        if (stdout.isEmpty()) return null;
        try {
            return new JSONObject(stdout);
        } catch (Exception e) {
            log.warn("解析任务脚本输出失败: action={}, jobId={}", action, jobId);
            return null;
        }
    }

    private JSONObject readOutputFile() {
        try {
            File baseDir = new File(resolveScriptPath("nba_data_fetcher.py")).getParentFile();
            File outputFile = new File(baseDir, "output.json");
            if (outputFile.exists()) {
                String jsonStr = new String(java.nio.file.Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
                if (!jsonStr.isEmpty()) {
                    return new org.json.JSONObject(jsonStr);
                }
            }
        } catch (Exception e) {
            log.warn("读取输出文件失败: {}", e.getMessage());
        }
        return null;
    }
}
