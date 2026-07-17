package com.nbamanager.service;

import com.nbamanager.web.dto.DraftPickDto;
import com.nbamanager.service.PythonScriptRunner;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftSyncService {

    private final DraftService draftService;
    private final PythonScriptRunner scriptRunner;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final Map<String, Object> lastResult = new HashMap<>();

    @Async("taskExecutor")
    @CacheEvict(value = {"drafts"}, allEntries = true)
    public void syncCurrentDraftYearAsync() {
        syncDraftYear(currentDraftYear());
    }

    @CacheEvict(value = {"drafts"}, allEntries = true)
    public Map<String, Object> syncDraftYear(int year) {
        return syncDraftYear(year, null);
    }

    @CacheEvict(value = {"drafts"}, allEntries = true)
    public Map<String, Object> syncDraftYear(int year, Long jobId) {
        if (!syncing.compareAndSet(false, true)) {
            return Map.of("status", "syncing", "year", year);
        }

        try {
            JSONObject result = jobId == null
                    ? scriptRunner.execute("draft", new String[]{String.valueOf(year)}, 180)
                    : scriptRunner.executeForJob("draft", new String[]{String.valueOf(year)}, 180, jobId);
            if (result == null) {
                throw new IllegalStateException("Python draft sync returned no result");
            }

            JSONArray draftPicks = result.optJSONArray("draftPicks");
            List<DraftPickDto> picks = toDraftPickDtos(draftPicks);
            int upserted = draftService.importDraftPicks(picks);
            String quality = qualityStatus(picks.size());

            Map<String, Object> syncResult = new HashMap<>();
            syncResult.put("status", "success");
            syncResult.put("year", year);
            syncResult.put("upserted", upserted);
            syncResult.put("pickCount", picks.size());
            syncResult.put("quality", quality);
            syncResult.put("timestamp", LocalDateTime.now().toString());
            if (result.has("draftErrors")) {
                syncResult.put("errors", result.getJSONArray("draftErrors").toList());
            }

            synchronized (lastResult) {
                lastResult.clear();
                lastResult.putAll(syncResult);
            }
            log.info("Draft sync finished: year={}, picks={}, quality={}", year, picks.size(), quality);
            return syncResult;
        } catch (Exception e) {
            Map<String, Object> failure = new HashMap<>();
            failure.put("status", "failed");
            failure.put("year", year);
            failure.put("error", e.getMessage());
            failure.put("timestamp", LocalDateTime.now().toString());
            synchronized (lastResult) {
                lastResult.clear();
                lastResult.putAll(failure);
            }
            log.error("Draft sync failed: year={}", year, e);
            return failure;
        } finally {
            syncing.set(false);
        }
    }

    public Map<String, Object> getLastResult() {
        synchronized (lastResult) {
            return new HashMap<>(lastResult);
        }
    }

    public boolean isSyncing() {
        return syncing.get();
    }

    public void syncDuringJuneDraftWindow() {
        syncDraftYear(currentDraftYear());
    }

    public void syncDuringJulyDraftWindow() {
        syncDraftYear(currentDraftYear());
    }

    public void syncDraftDaily() {
        syncDraftYear(currentDraftYear());
    }

    private int currentDraftYear() {
        return LocalDate.now().getYear();
    }

    private List<DraftPickDto> toDraftPickDtos(JSONArray draftPicks) {
        List<DraftPickDto> picks = new ArrayList<>();
        if (draftPicks == null) {
            return picks;
        }
        for (int i = 0; i < draftPicks.length(); i++) {
            JSONObject item = draftPicks.getJSONObject(i);
            Long nbaPlayerId = item.optLong("nbaPlayerId", 0);
            picks.add(new DraftPickDto(
                    null,
                    item.optInt("year"),
                    item.optInt("round"),
                    item.optInt("pickNumber"),
                    item.optString("teamName", ""),
                    item.optString("teamNameEn", ""),
                    item.optString("playerName", ""),
                    item.optString("playerNameEn", ""),
                    nbaPlayerId > 0 ? nbaPlayerId : null,
                    item.optString("notes", ""),
                    null));
        }
        return picks;
    }

    private String qualityStatus(int pickCount) {
        if (pickCount >= 58) {
            return "COMPLETE";
        }
        if (pickCount > 0) {
            return "PARTIAL";
        }
        return "FAILED";
    }
}
