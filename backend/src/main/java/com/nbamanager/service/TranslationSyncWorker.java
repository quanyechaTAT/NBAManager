package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class TranslationSyncWorker extends ModuleSyncWorker {
    private final TranslationRetryService translationRetryService;
    public TranslationSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, TranslationRetryService translationRetryService) {
        super(repo, jobStateService); this.translationRetryService = translationRetryService;
    }
    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 30);
        if (isCancelled(jobId)) return SyncResult.cancelled("TRANSLATION");
        try {
            translationRetryService.retryTranslations(jobId);
            if (isCancelled(jobId)) return SyncResult.cancelled("TRANSLATION");
            updateProgress(jobId, 90);
            return SyncResult.success("TRANSLATION", 0, 0, 0, 0, LocalDateTime.now(), "deepseek_api");
        } catch (Exception e) {
            log.error("翻译重试失败", e);
            return SyncResult.failed("TRANSLATION", "SYNC_ERROR", e.getMessage());
        }
    }
}
