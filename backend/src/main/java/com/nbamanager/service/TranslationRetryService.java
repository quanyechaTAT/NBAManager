package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 翻译重试服务
 * 定时扫描未翻译的球员记录，调用MIMO API进行翻译
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TranslationRetryService {

    private final PlayerRepository playerRepository;

    // 翻译缓存，避免重复调用API
    private final Map<String, String> translationCache = new ConcurrentHashMap<>();

    // 翻译状态跟踪 - 使用AtomicBoolean保证原子性
    private final AtomicBoolean translating = new AtomicBoolean(false);
    private volatile int translatedCount = 0;
    private volatile int failedCount = 0;

    /**
     * 每小时执行一次翻译重试（在整点后10分钟执行，避免与其他任务冲突）
     */
    @Scheduled(cron = "0 10 * * * *")
    @Transactional
    public void retryTranslations() {
        // 使用compareAndSet保证原子性，防止重复执行
        if (!translating.compareAndSet(false, true)) {
            log.info("翻译任务正在进行中，跳过本次执行");
            return;
        }
        translatedCount = 0;
        failedCount = 0;

        try {
            // 查找所有未翻译的球员
            List<Player> untranslatedPlayers = playerRepository.findAll().stream()
                    .filter(p -> "UNTRANSLATED".equals(p.getTranslationStatus()))
                    .filter(p -> p.getNameEn() != null && !p.getNameEn().isEmpty())
                    .toList();

            if (untranslatedPlayers.isEmpty()) {
                log.info("没有需要翻译的球员记录");
                return;
            }

            log.info("找到 {} 条需要翻译的球员记录", untranslatedPlayers.size());

            // 批量调用Python翻译脚本
            List<String> englishNames = untranslatedPlayers.stream()
                    .map(Player::getNameEn)
                    .toList();

            Map<String, String> translations = callTranslationScript(englishNames);

            // 更新球员记录
            for (Player player : untranslatedPlayers) {
                String cnName = translations.get(player.getNameEn());
                if (cnName != null && !cnName.isEmpty() && !cnName.equals(player.getNameEn())) {
                    player.setName(cnName);
                    player.setTranslationStatus("API_TRANSLATED");
                    playerRepository.save(player);
                    translatedCount++;
                } else {
                    // 翻译失败，标记为需要人工处理
                    player.setTranslationStatus("FAILED");
                    playerRepository.save(player);
                    failedCount++;
                }
            }

            log.info("翻译重试完成: 成功 {}, 失败 {}", translatedCount, failedCount);

        } catch (Exception e) {
            log.error("翻译重试任务执行失败", e);
        } finally {
            translating.set(false);
        }
    }

    /**
     * 调用Python翻译脚本
     */
    private Map<String, String> callTranslationScript(List<String> englishNames) {
        Map<String, String> result = new ConcurrentHashMap<>();

        try {
            String scriptPath = findTranslateScript();
            if (scriptPath == null) {
                log.warn("翻译脚本未找到，跳过翻译");
                return result;
            }

            // 将英文名写入临时文件
            File tempFile = File.createTempFile("translate_input_", ".json");
            tempFile.deleteOnExit();

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < englishNames.size(); i++) {
                sb.append("\"").append(escapeJson(englishNames.get(i))).append("\"");
                if (i < englishNames.size() - 1) sb.append(",");
            }
            sb.append("]");

            java.nio.file.Files.write(tempFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

            // 调用翻译脚本
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, tempFile.getAbsolutePath());
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
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

            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 读取错误输出
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("翻译脚本执行失败: {}", errorOutput.toString().trim());
                return result;
            }

            // 解析JSON输出
            String jsonStr = output.toString().trim();
            if (!jsonStr.isEmpty()) {
                org.json.JSONArray translations = new org.json.JSONArray(jsonStr);
                for (int i = 0; i < translations.length() && i < englishNames.size(); i++) {
                    String enName = englishNames.get(i);
                    String cnName = translations.optString(i, "");
                    if (!cnName.isEmpty()) {
                        result.put(enName, cnName);
                    }
                }
            }

            // 清理临时文件
            tempFile.delete();

        } catch (Exception e) {
            log.warn("调用翻译脚本失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 查找翻译脚本路径
     */
    private String findTranslateScript() {
        String[] possiblePaths = {
                "backend/scripts/translate_names.py",
                "scripts/translate_names.py",
                "../scripts/translate_names.py",
                "translate_names.py"
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
     * 转义JSON字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    // 状态查询方法
    public boolean isTranslating() {
        return translating.get();
    }

    public int getTranslatedCount() {
        return translatedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }
}
