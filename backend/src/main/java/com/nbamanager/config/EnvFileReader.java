package com.nbamanager.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读取项目根目录的 .env 文件作为环境变量回退
 */
public final class EnvFileReader {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private EnvFileReader() {}

    /**
     * 获取环境变量值，优先从系统环境变量读取，回退到 .env 文件
     */
    public static String get(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isEmpty()) {
            return val;
        }
        return CACHE.get(key);
    }

    /**
     * 加载 .env 文件（应用启动时调用一次）
     */
    public static void load() {
        Path envFile = Path.of(System.getProperty("user.dir"), ".env");
        if (!Files.exists(envFile)) {
            // 尝试上级目录
            envFile = Path.of(System.getProperty("user.dir"), "..", ".env");
            if (!Files.exists(envFile)) {
                return;
            }
        }
        try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    // 去除引号
                    if (value.length() >= 2 &&
                            ((value.startsWith("\"") && value.endsWith("\"")) ||
                             (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    CACHE.put(key, value);
                }
            }
        } catch (IOException e) {
            // 忽略读取错误
        }
    }
}
