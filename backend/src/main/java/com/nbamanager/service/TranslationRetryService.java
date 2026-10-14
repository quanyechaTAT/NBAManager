package com.nbamanager.service;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.domain.Player;
import com.nbamanager.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 翻译重试服务
 * 定时扫描未翻译的球员记录，调用DeepSeek API进行翻译
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TranslationRetryService {

    private final PlayerRepository playerRepository;
    private final PythonScriptRunner pythonScriptRunner;
    private final PythonTimeoutProperties pythonTimeoutProperties;

    // 翻译状态跟踪 - 使用AtomicBoolean保证原子性
    private final AtomicBoolean translating = new AtomicBoolean(false);
    private volatile int translatedCount = 0;
    private volatile int failedCount = 0;

    /**
     * 每小时执行一次翻译重试（在整点后10分钟执行，避免与其他任务冲突）
     */
    public void retryTranslations() {
        retryTranslations(null);
    }

    public void retryTranslations(Long jobId) {
        // 使用compareAndSet保证原子性，防止重复执行
        if (!translating.compareAndSet(false, true)) {
            log.info("翻译任务正在进行中，跳过本次执行");
            throw new IllegalStateException("翻译任务正在进行中");
        }
        translatedCount = 0;
        failedCount = 0;

        try {
            // 查找所有未翻译的球员
            List<Player> untranslatedPlayers = playerRepository.findAll().stream()
                    .filter(p -> "UNTRANSLATED".equals(p.getTranslationStatus()) || "FAILED".equals(p.getTranslationStatus()))
                    .filter(p -> p.getNameEn() != null && !p.getNameEn().isEmpty())
                    .toList();

            if (untranslatedPlayers.isEmpty()) {
                log.info("没有需要翻译的球员记录");
            } else {
                log.info("找到 {} 条需要翻译的球员记录", untranslatedPlayers.size());

                // 批量调用Python翻译脚本
                List<String> englishNames = untranslatedPlayers.stream()
                        .map(Player::getNameEn)
                        .toList();

                Map<String, String> translations = callTranslationScript(englishNames, jobId);
                if (translations != null) {
                    // 更新球员记录
                    for (Player player : untranslatedPlayers) {
                        String cnName = translations.get(player.getNameEn());
                        if (cnName != null && !cnName.isEmpty() && !cnName.equals(player.getNameEn())) {
                            player.setName(cnName);
                            player.setTranslationStatus("API_TRANSLATED");
                            playerRepository.save(player);
                            translatedCount++;
                        } else {
                            // 保持待翻译状态，后续手动/定时重试可以再次处理
                            player.setTranslationStatus("UNTRANSLATED");
                            playerRepository.save(player);
                            failedCount++;
                        }
                    }

                    log.info("翻译重试完成: 成功 {}, 失败 {}", translatedCount, failedCount);
                }
            }

            // 新闻补翻兜底：抓取时翻译失败的新闻（title 仍为英文原文）由脚本直连库补翻，
            // 避免上游 LLM 故障导致新闻永久停留在英文（历史上 8/19 批次曾整批漏翻）
            retryPendingNewsTranslation(jobId);
            if (failedCount > 0) throw new PartialSyncException("翻译完成 " + translatedCount + " 条，失败 " + failedCount + " 条，可重试");

        } catch (Exception e) {
            log.error("翻译重试任务执行失败", e);
            throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException("翻译重试失败", e);
        } finally {
            translating.set(false);
        }
    }

    /**
     * 新闻补翻：调用 translate_pending_news.py（脚本自行判断是否有待翻条目，无则秒退）
     */
    private void retryPendingNewsTranslation(Long jobId) {
        PythonScriptRunner.PythonResult result = jobId == null
                ? pythonScriptRunner.run(
                        "translate_pending_news.py", new String[]{},
                        pythonTimeoutProperties.getTranslationSeconds())
                : pythonScriptRunner.runForJob(
                        "translate_pending_news.py", new String[]{},
                        pythonTimeoutProperties.getTranslationSeconds(), jobId);
        if (result.success()) {
            String summary = result.stdout().lines().filter(l -> l.startsWith("{\"fetched\":")).reduce((a, b) -> b)
                    .orElseThrow(() -> new IllegalStateException("新闻翻译脚本未返回执行结果"));
            org.json.JSONObject counts = new org.json.JSONObject(summary);
            translatedCount += counts.getInt("translated");
            failedCount += counts.getInt("failed");
        } else if (result.exitCode() != -2) {
            throw new IllegalStateException(result.failure("TRANSLATION").errorMessage());
        }
    }

    /**
     * 调用Python翻译脚本
     */
    private Map<String, String> callTranslationScript(List<String> englishNames, Long jobId) {
        Map<String, String> result = new ConcurrentHashMap<>();
        File tempFile = null;

        try {
            // 将英文名写入临时文件
            tempFile = File.createTempFile("translate_input_", ".json");

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < englishNames.size(); i++) {
                sb.append("\"").append(escapeJson(englishNames.get(i))).append("\"");
                if (i < englishNames.size() - 1) sb.append(",");
            }
            sb.append("]");

            java.nio.file.Files.write(tempFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

            PythonScriptRunner.PythonResult pythonResult = jobId == null
                    ? pythonScriptRunner.run(
                            "translate_names.py", new String[]{tempFile.getAbsolutePath()},
                            pythonTimeoutProperties.getTranslationSeconds())
                    : pythonScriptRunner.runForJob(
                            "translate_names.py", new String[]{tempFile.getAbsolutePath()},
                            pythonTimeoutProperties.getTranslationSeconds(), jobId);
            if (!pythonResult.success()) {
                log.warn("翻译脚本执行失败: {}", pythonResult.stderr().trim());
                if (pythonResult.exitCode() == -2) return null;
                throw new IllegalStateException(pythonResult.failure("TRANSLATION").errorMessage());
            }

            // 解析JSON输出
            String jsonStr = pythonResult.stdout().trim();
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

        } catch (Exception e) {
            throw new IllegalStateException("调用翻译脚本失败: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) tempFile.delete();
        }

        return result;
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
