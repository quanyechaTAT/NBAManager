package com.nbamanager.service;

import com.nbamanager.domain.SyncJobStep;
import com.nbamanager.repository.SyncJobStepRepository;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncJobStepStateService {

    private final SyncJobStepRepository stepRepository;

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCESS", "PARTIAL_FAILED", "FAILED", "CANCELLED"
    );

    @Transactional
    public void markRunning(Long stepId) {
        SyncJobStep step = stepRepository.findByIdForUpdate(stepId).orElse(null);
        if (step == null || TERMINAL_STATUSES.contains(step.getStatus())) return;
        step.setStatus("RUNNING");
        step.setStartedAt(LocalDateTime.now());
        stepRepository.save(step);
    }

    @Transactional
    public void updateProgress(Long stepId, int progress, String stage) {
        SyncJobStep step = stepRepository.findByIdForUpdate(stepId).orElse(null);
        if (step == null) return;
        if (TERMINAL_STATUSES.contains(step.getStatus())) return;
        int clamped = Math.max(0, Math.min(100, progress));
        if (clamped <= step.getProgress()) return;
        step.setProgress(clamped);
        if (stage != null) step.setStage(stage);
        stepRepository.save(step);
    }

    @Transactional
    public void complete(Long stepId, SyncResult result) {
        SyncJobStep step = stepRepository.findByIdForUpdate(stepId).orElse(null);
        if (step == null) return;
        if (TERMINAL_STATUSES.contains(step.getStatus())) {
            log.warn("步骤已处于终态，跳过完成写入: stepId={}, status={}", stepId, step.getStatus());
            return;
        }
        step.setStatus(result.status());
        step.setErrorCode(result.errorCode());
        step.setErrorMessage(result.errorMessage());
        step.setFinishedAt(LocalDateTime.now());
        step.setDataAsOf(result.dataAsOf());
        step.setSource(result.source());
        step.setFetchedCount(result.fetchedCount());
        step.setInsertedCount(result.insertedCount());
        step.setUpdatedCount(result.updatedCount());
        step.setSkippedCount(result.skippedCount());
        if (!"CANCELLED".equals(step.getStatus())) {
            step.setProgress(100);
        }
        stepRepository.save(step);
    }

    @Transactional
    public void markFailed(Long stepId, String errorCode, String errorMessage) {
        SyncJobStep step = stepRepository.findByIdForUpdate(stepId).orElse(null);
        if (step == null || TERMINAL_STATUSES.contains(step.getStatus())) return;
        step.setStatus("FAILED");
        step.setFinishedAt(LocalDateTime.now());
        step.setErrorCode(errorCode);
        step.setErrorMessage(errorMessage);
        stepRepository.save(step);
    }

    @Transactional
    public void markCancelled(Long stepId) {
        SyncJobStep step = stepRepository.findByIdForUpdate(stepId).orElse(null);
        if (step == null || TERMINAL_STATUSES.contains(step.getStatus())) return;
        step.setStatus("CANCELLED");
        step.setFinishedAt(LocalDateTime.now());
        step.setErrorCode("CANCELLED");
        step.setErrorMessage("步骤已取消");
        step.setProgress(100);
        stepRepository.save(step);
    }
}
