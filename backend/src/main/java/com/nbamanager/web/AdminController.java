package com.nbamanager.web;

import com.nbamanager.exception.SyncJobConflictException;
import com.nbamanager.repository.SyncJobStepRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.MatchRecordService;
import com.nbamanager.service.SyncCommand;
import com.nbamanager.service.SyncCoordinator;
import com.nbamanager.service.SyncJobStepStateService;
import com.nbamanager.service.SyncOverviewService;
import com.nbamanager.service.SyncSubmission;
import com.nbamanager.web.dto.SyncJobDetailResponse;
import com.nbamanager.web.dto.SyncJobPageResponse;
import com.nbamanager.web.dto.SyncJobResponse;
import com.nbamanager.web.dto.SyncJobStepResponse;
import com.nbamanager.web.dto.SyncModuleOverview;
import com.nbamanager.web.dto.SyncOverviewResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final SyncCoordinator syncCoordinator;
    private final MatchRecordService matchRecordService;
    private final SyncOverviewService syncOverviewService;
    private final com.nbamanager.repository.SyncJobRepository syncJobRepository;
    private final SyncJobStepRepository syncJobStepRepository;

    /** 模块概览 */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncOverviewResponse> getOverview() {
        return ResponseEntity.ok(syncOverviewService.getOverview());
    }

    /** 同步指定模块 → 新任务 202 / 复用活动任务 200 */
    @PostMapping("/sync/{module}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncJobResponse> syncModule(
            @PathVariable String module,
            @AuthenticationPrincipal UserPrincipal user) {
        String upper = module.toUpperCase();
        if (!SyncCoordinator.ALL_MODULES.contains(upper)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            SyncSubmission submission = syncCoordinator.submit(SyncCommand.manual(upper, user.getUsername()));
            SyncJobResponse response = SyncJobResponse.from(submission.job(), submission.reused());
            if (submission.reused()) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.accepted().body(response);
        } catch (SyncJobConflictException e) {
            return ResponseEntity.status(HttpStatus.valueOf(e.getStatus().value())).build();
        }
    }

    /** 全量同步 */
    @PostMapping("/sync/full")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> syncFull(@AuthenticationPrincipal UserPrincipal user) {
        try {
            SyncSubmission submission = syncCoordinator.submitFullSync("MANUAL", user.getUsername());
            return ResponseEntity.accepted().body(SyncJobResponse.from(submission.job(), false));
        } catch (SyncJobConflictException e) {
            HttpStatus status = HttpStatus.valueOf(e.getStatus().value());
            if (status == HttpStatus.CONFLICT) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("code", e.getErrorCode(), "message", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", e.getErrorCode(), "message", e.getMessage()));
        }
    }

    /** 旧接口兼容：全量同步 */
    @PostMapping("/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> syncAll(@AuthenticationPrincipal UserPrincipal user) {
        return syncFull(user);
    }

    /** 查询任务详情（含步骤） */
    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncJobDetailResponse> getJob(@PathVariable Long jobId) {
        return syncJobRepository.findById(jobId)
                .map(job -> {
                    List<SyncJobStepResponse> steps = syncJobStepRepository.findByJobIdOrderByStepOrder(jobId)
                            .stream().map(SyncJobStepResponse::from).toList();
                    return ResponseEntity.ok(SyncJobDetailResponse.from(job, steps));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 任务历史分页查询 */
    @GetMapping("/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncJobPageResponse> getJobHistory(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedSize = Math.min(size, 100);
        PageRequest pageRequest = PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<com.nbamanager.domain.SyncJob> jobPage = syncJobRepository.search(
                module != null ? module.toUpperCase() : null,
                triggerType, status, pageRequest);
        Page<SyncJobResponse> responsePage = jobPage.map(SyncJobResponse::from);
        return ResponseEntity.ok(SyncJobPageResponse.from(responsePage));
    }

    /** 模块最新任务 */
    @GetMapping("/jobs/module/{module}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncJobResponse> getLatestJob(@PathVariable String module) {
        return syncJobRepository.findTopByModuleOrderByCreatedAtDesc(module.toUpperCase())
                .map(SyncJobResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 取消任务 */
    @PostMapping("/jobs/{jobId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelJob(@PathVariable Long jobId) {
        boolean accepted = syncCoordinator.cancelJob(jobId);
        if (!accepted) {
            SyncJobResponse job = syncCoordinator.getJob(jobId);
            if (job == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "任务不存在: " + jobId));
            }
            return ResponseEntity.ok(Map.of("accepted", false, "job", job));
        }
        SyncJobResponse job = syncCoordinator.getJob(jobId);
        return ResponseEntity.accepted().body(Map.of("accepted", true, "job", job));
    }

    /** 旧接口兼容：同步状态 */
    @GetMapping("/sync-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        return ResponseEntity.ok(Map.of(
                "syncing", SyncCoordinator.ALL_MODULES.stream().anyMatch(syncCoordinator::isRunning),
                "jobs", syncCoordinator.getRecentJobs()
        ));
    }

    /** 旧接口兼容：触发同步 */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> triggerSync(@AuthenticationPrincipal UserPrincipal user) {
        return syncFull(user);
    }

    /** 清理旧比赛记录 */
    @PostMapping("/clean/matches/old")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanOldMatches() {
        try {
            int cleaned = matchRecordService.cleanOldData();
            return ResponseEntity.ok(Map.of("message", "已清理 " + cleaned + " 条旧比赛记录", "cleaned", cleaned, "status", "success"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "清理失败: " + e.getMessage(), "status", "failed"));
        }
    }

    /** 清理无效比赛记录 */
    @PostMapping("/clean/matches/invalid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanInvalidMatches() {
        try {
            int cleaned = matchRecordService.cleanInvalidData();
            return ResponseEntity.ok(Map.of("message", "已清理 " + cleaned + " 条无效比赛记录", "cleaned", cleaned, "status", "success"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "清理失败: " + e.getMessage(), "status", "failed"));
        }
    }
}
