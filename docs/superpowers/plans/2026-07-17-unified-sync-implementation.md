# 统一自动、手动与登录补偿同步实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将自动调度、管理员手动同步和管理员登录补偿统一到同一任务编排、Worker、锁、进度、取消和质量门链路。

**架构：** `DataSyncScheduler`、管理端 API 和 `SyncCatchupService` 只创建 `SyncCommand`，由 `SyncCoordinator` 统一去重、加锁、持久化和执行。`SyncDefinitionRegistry` 固化模块 Worker 与调度策略；`FULL_SYNC` 使用父任务和持久化步骤顺序执行，失败步骤不阻断后续步骤。

**技术栈：** Java 17、Spring Boot 3.2、Spring Data JPA、Spring Scheduling、Redis、MySQL、JUnit 5、Mockito、Vue 3、TypeScript、Axios、Element Plus、Playwright CLI。

---

## 文件结构

### 后端新增

- `backend/src/main/java/com/nbamanager/domain/SyncJobStep.java`：全量同步模块步骤实体。
- `backend/src/main/java/com/nbamanager/repository/SyncJobStepRepository.java`：步骤查询与锁定写入。
- `backend/src/main/java/com/nbamanager/service/SyncJobStepStateService.java`：步骤状态、进度和结果的短事务更新。
- `backend/src/main/java/com/nbamanager/service/SyncCommand.java`：统一提交命令。
- `backend/src/main/java/com/nbamanager/service/SyncSubmission.java`：新建或复用任务的提交结果。
- `backend/src/main/java/com/nbamanager/service/SyncExecutionContext.java`：Worker 的任务、步骤和进度区间上下文。
- `backend/src/main/java/com/nbamanager/service/SyncDefinition.java`：模块 Worker、顺序和调度策略定义。
- `backend/src/main/java/com/nbamanager/service/SyncDefinitionRegistry.java`：所有同步模块的唯一注册表。
- `backend/src/main/java/com/nbamanager/service/SyncSchedulePolicy.java`：Cron 和固定周期的下次应执行时间计算。
- `backend/src/main/java/com/nbamanager/service/DataSyncScheduler.java`：仅负责提交自动任务的调度适配器。
- `backend/src/main/java/com/nbamanager/event/AdminLoginEvent.java`：管理员登录事件。
- `backend/src/main/java/com/nbamanager/service/SyncCatchupService.java`：遗漏调度检查、互斥和延迟重试。
- `backend/src/main/java/com/nbamanager/web/dto/SyncJobStepResponse.java`：全量步骤响应。
- `backend/src/main/java/com/nbamanager/web/dto/SyncJobDetailResponse.java`：任务及步骤详情。
- `backend/src/main/java/com/nbamanager/web/dto/SyncJobPageResponse.java`：任务历史分页响应。
- `backend/src/main/resources/db/migration/V20260718_01__unify_sync_jobs.sql`：任务字段和步骤表迁移。

### 后端重点修改

- `backend/src/main/java/com/nbamanager/domain/SyncJob.java`：增加阶段字段，统一触发来源语义。
- `backend/src/main/java/com/nbamanager/repository/SyncJobRepository.java`：活动任务、最后成功和分页筛选查询。
- `backend/src/main/java/com/nbamanager/service/SyncCoordinator.java`：统一提交、任务复用、模块并发、全量独占及步骤执行。
- `backend/src/main/java/com/nbamanager/service/ModuleSyncWorker.java`：改为接收 `SyncExecutionContext` 并报告步骤进度。
- `backend/src/main/java/com/nbamanager/service/SyncJobStateService.java`：增加阶段更新与最后成功保护。
- `backend/src/main/java/com/nbamanager/service/SyncOverviewService.java`：区分最后尝试、最后成功和活动任务。
- `backend/src/main/java/com/nbamanager/web/AdminController.java`：返回 202/200 复用语义、全量冲突模块及分页历史。
- `backend/src/main/java/com/nbamanager/web/AuthController.java`：管理员登录成功后发布事件。
- `backend/src/main/java/com/nbamanager/service/NbaLiveSyncService.java`：移除数据型 `@Scheduled`，保留新闻去重维护任务。
- `backend/src/main/java/com/nbamanager/service/DraftSyncService.java`：移除三个调度入口。
- `backend/src/main/java/com/nbamanager/service/HistoricalDataSyncService.java`：移除数据调度入口。
- `backend/src/main/java/com/nbamanager/service/TranslationRetryService.java`：移除翻译调度入口。
- `backend/src/main/java/com/nbamanager/service/TeamSyncWorker.java`、`PlayerSyncWorker.java`、`MatchSyncWorker.java`：使用集中超时配置，移除 120 秒硬编码。

### 前端新增与修改

- `frontend/src/api/dataManagement.ts`：复用标记、来源、阶段、步骤和分页筛选类型。
- `frontend/src/components/admin/SyncModuleCard.vue`：模块状态、来源、阶段、时间和操作。
- `frontend/src/components/admin/FullSyncProgress.vue`：全量步骤与失败模块重试。
- `frontend/src/components/admin/SyncHistoryPanel.vue`：持久任务历史筛选和分页。
- `frontend/src/views/admin/DataSyncView.vue`：组合组件、关联复用任务、刷新恢复和轮询。

---

### 任务 1：扩展任务与全量步骤持久化模型

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/domain/SyncJobStep.java`
- 创建：`backend/src/main/java/com/nbamanager/repository/SyncJobStepRepository.java`
- 创建：`backend/src/main/java/com/nbamanager/service/SyncJobStepStateService.java`
- 创建：`backend/src/main/resources/db/migration/V20260718_01__unify_sync_jobs.sql`
- 修改：`backend/src/main/java/com/nbamanager/domain/SyncJob.java`
- 修改：`backend/src/main/java/com/nbamanager/repository/SyncJobRepository.java`
- 测试：`backend/src/test/java/com/nbamanager/service/SyncJobStepStateServiceTest.java`

- [ ] **步骤 1：编写步骤终态和进度保护测试**

```java
@Test
void updateProgress_isMonotonicAndDoesNotChangeTerminalStep() {
    SyncJobStep running = step("RUNNING", 30);
    when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(running));
    service.updateProgress(7L, 20, "FETCHING");
    assertThat(running.getProgress()).isEqualTo(30);

    running.setStatus("FAILED");
    service.updateProgress(7L, 80, "PERSISTING");
    assertThat(running.getProgress()).isEqualTo(30);
    verify(repository, never()).save(running);
}

@Test
void complete_preservesModuleResult() {
    SyncJobStep running = step("RUNNING", 60);
    when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(running));
    service.complete(7L, SyncResult.failed("NEWS", "PROVIDER_FAILED", "all providers failed"));
    assertThat(running.getStatus()).isEqualTo("FAILED");
    assertThat(running.getErrorCode()).isEqualTo("PROVIDER_FAILED");
}
```

- [ ] **步骤 2：运行测试确认模型尚不存在**

运行：

```powershell
cd backend
.\mvnw.cmd -q '-Dtest=SyncJobStepStateServiceTest' test
```

预期：测试编译失败，缺少 `SyncJobStep`、`SyncJobStepRepository` 和 `SyncJobStepStateService`。

- [ ] **步骤 3：创建步骤实体、仓库和短事务状态服务**

```java
@Entity
@Table(name = "sync_job_steps", indexes = {
        @Index(name = "idx_sync_step_job", columnList = "job_id,step_order")
})
public class SyncJobStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_id", nullable = false)
    private Long jobId;
    @Column(nullable = false, length = 32)
    private String module;
    @Column(name = "step_order", nullable = false)
    private int stepOrder;
    @Column(nullable = false, length = 20)
    private String status = "QUEUED";
    private int progress;
    @Column(length = 32)
    private String stage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int fetchedCount;
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;
    private LocalDateTime dataAsOf;
    @Column(length = 32)
    private String source;
    @Column(length = 32)
    private String errorCode;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @Version
    private Long version;
}
```

仓库必须提供：

```java
List<SyncJobStep> findByJobIdOrderByStepOrder(Long jobId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM SyncJobStep s WHERE s.id = :id")
Optional<SyncJobStep> findByIdForUpdate(@Param("id") Long id);
```

`SyncJob` 增加 `stage VARCHAR(32)`；迁移脚本创建步骤表、外键、索引和版本字段。`SyncJobRepository` 的活动查询必须包含 `CANCELLING`。

```sql
ALTER TABLE sync_jobs ADD COLUMN stage VARCHAR(32) NULL AFTER progress;

CREATE TABLE sync_job_steps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    module VARCHAR(32) NOT NULL,
    step_order INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    stage VARCHAR(32) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    data_as_of DATETIME(6) NULL,
    source VARCHAR(32) NULL,
    fetched_count INT NOT NULL DEFAULT 0,
    inserted_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(32) NULL,
    error_message TEXT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sync_step_job FOREIGN KEY (job_id) REFERENCES sync_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_sync_step_job_module UNIQUE (job_id, module),
    INDEX idx_sync_step_job (job_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **步骤 4：运行步骤状态测试**

运行：`.\mvnw.cmd -q '-Dtest=SyncJobStepStateServiceTest' test`

预期：全部通过。

- [ ] **步骤 5：提交持久化模型**

```powershell
git add backend/src/main/java/com/nbamanager/domain/SyncJob.java backend/src/main/java/com/nbamanager/domain/SyncJobStep.java backend/src/main/java/com/nbamanager/repository/SyncJobRepository.java backend/src/main/java/com/nbamanager/repository/SyncJobStepRepository.java backend/src/main/java/com/nbamanager/service/SyncJobStepStateService.java backend/src/main/resources/db/migration/V20260718_01__unify_sync_jobs.sql backend/src/test/java/com/nbamanager/service/SyncJobStepStateServiceTest.java
git commit -m "feat: add full sync step persistence"
```

### 任务 2：建立模块定义与调度策略注册表

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/service/SyncCommand.java`
- 创建：`backend/src/main/java/com/nbamanager/service/SyncSubmission.java`
- 创建：`backend/src/main/java/com/nbamanager/service/SyncSchedulePolicy.java`
- 创建：`backend/src/main/java/com/nbamanager/service/SyncDefinition.java`
- 创建：`backend/src/main/java/com/nbamanager/service/SyncDefinitionRegistry.java`
- 测试：`backend/src/test/java/com/nbamanager/service/SyncDefinitionRegistryTest.java`
- 测试：`backend/src/test/java/com/nbamanager/service/SyncSchedulePolicyTest.java`

- [ ] **步骤 1：编写注册表唯一性和调度到期测试**

```java
@Test
void registryUsesOneWorkerForEveryTriggerSource() {
    SyncDefinition news = registry.require("NEWS");
    assertThat(news.worker()).isSameAs(newsSyncWorker);
    assertThat(registry.fullSyncOrder()).containsExactly(
            "TEAMS", "PLAYERS", "MATCHES", "TODAY_GAMES", "NEWS",
            "DRAFT", "PLAYOFF", "HISTORICAL", "TRANSLATION");
}

@Test
void cronPolicyDetectsMissedDailyExecution() {
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    SyncSchedulePolicy policy = SyncSchedulePolicy.cron("0 0 3 * * ?", zone);
    ZonedDateTime lastSuccess = ZonedDateTime.of(2026, 7, 16, 3, 10, 0, 0, zone);
    assertThat(policy.isDue(lastSuccess, ZonedDateTime.of(2026, 7, 17, 8, 0, 0, 0, zone))).isTrue();
}

@Test
void fixedPolicyDoesNotRunBeforeInterval() {
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    SyncSchedulePolicy policy = SyncSchedulePolicy.fixed(Duration.ofMinutes(5));
    ZonedDateTime lastSuccess = ZonedDateTime.of(2026, 7, 17, 8, 0, 0, 0, zone);
    assertThat(policy.isDue(lastSuccess, lastSuccess.plusMinutes(4))).isFalse();
}
```

- [ ] **步骤 2：运行测试确认定义类型缺失**

运行：`.\mvnw.cmd -q '-Dtest=SyncDefinitionRegistryTest,SyncSchedulePolicyTest' test`

预期：测试编译失败，缺少统一命令和注册表类型。

- [ ] **步骤 3：实现统一命令和调度策略**

```java
public record SyncCommand(String module, String triggerType, String requestedBy) {
    public static SyncCommand manual(String module, String user) {
        return new SyncCommand(module, "MANUAL", user);
    }
    public static SyncCommand scheduled(String module) {
        return new SyncCommand(module, "SCHEDULED", "scheduler");
    }
    public static SyncCommand loginCatchup(String module, String user) {
        return new SyncCommand(module, "LOGIN_CATCHUP", user);
    }
}

public record SyncSubmission(SyncJob job, boolean reused) {}

public interface SyncSchedulePolicy {
    boolean isDue(ZonedDateTime lastSuccess, ZonedDateTime now);
    static SyncSchedulePolicy cron(String expression, ZoneId zone) {
        CronExpression cron = CronExpression.parse(expression);
        return (lastSuccess, now) -> {
            ZonedDateTime next = cron.next(lastSuccess.withZoneSameInstant(zone));
            return next != null && !next.isAfter(now.withZoneSameInstant(zone));
        };
    }
    static SyncSchedulePolicy fixed(Duration interval) {
        return (lastSuccess, now) -> !lastSuccess.plus(interval).isAfter(now);
    }
}

public record SyncDefinition(
        String module,
        ModuleSyncWorker worker,
        int fullSyncOrder,
        List<SyncSchedulePolicy> schedules) {
    public boolean isDue(ZonedDateTime lastSuccess, ZonedDateTime now) {
        return schedules.stream().anyMatch(policy -> policy.isDue(lastSuccess, now));
    }
}
```

`SyncDefinitionRegistry` 注入九个 Worker，定义现有完整顺序和策略：全量每日 3:00、季后赛 4–6 月每两小时、新闻每小时、今日比赛每五分钟、选秀三个 Cron、历史每日 4:00、翻译每小时 10 分。

```java
public SyncDefinition require(String module) {
    SyncDefinition definition = definitions.get(module);
    if (definition == null) throw new IllegalArgumentException("未知模块: " + module);
    return definition;
}

public List<String> fullSyncOrder() {
    return definitions.values().stream()
            .sorted(Comparator.comparingInt(SyncDefinition::fullSyncOrder))
            .map(SyncDefinition::module)
            .toList();
}

public List<SyncDefinition> scheduledDefinitions() {
    return definitions.values().stream()
            .filter(definition -> !definition.schedules().isEmpty())
            .toList();
}
```

- [ ] **步骤 4：运行注册表测试**

运行：`.\mvnw.cmd -q '-Dtest=SyncDefinitionRegistryTest,SyncSchedulePolicyTest' test`

预期：全部通过。

- [ ] **步骤 5：提交统一定义**

```powershell
git add backend/src/main/java/com/nbamanager/service/SyncCommand.java backend/src/main/java/com/nbamanager/service/SyncSubmission.java backend/src/main/java/com/nbamanager/service/SyncSchedulePolicy.java backend/src/main/java/com/nbamanager/service/SyncDefinition.java backend/src/main/java/com/nbamanager/service/SyncDefinitionRegistry.java backend/src/test/java/com/nbamanager/service/SyncDefinitionRegistryTest.java backend/src/test/java/com/nbamanager/service/SyncSchedulePolicyTest.java
git commit -m "feat: define unified sync registry"
```

### 任务 3：统一任务提交、复用和分布式并发门禁

**文件：**
- 修改：`backend/src/main/java/com/nbamanager/service/SyncCoordinator.java`
- 修改：`backend/src/main/java/com/nbamanager/service/SyncLockService.java`
- 修改：`backend/src/main/java/com/nbamanager/web/dto/SyncJobResponse.java`
- 修改：`backend/src/test/java/com/nbamanager/service/SyncCoordinatorTest.java`
- 修改：`backend/src/test/java/com/nbamanager/service/SyncCoordinatorConflictTest.java`

- [ ] **步骤 1：编写复用、模块并行和全量独占失败测试**

```java
@Test
void manualSubmissionReusesScheduledJobForSameModule() {
    SyncJob active = job(41L, "NEWS", "SCHEDULED", "RUNNING");
    when(repository.findActiveByModule("NEWS")).thenReturn(Optional.of(active));
    SyncSubmission result = coordinator.submit(SyncCommand.manual("NEWS", "admin"));
    assertThat(result.reused()).isTrue();
    assertThat(result.job().getId()).isEqualTo(41L);
    verify(executor, never()).execute(any(Runnable.class));
}

@Test
void differentModulesDoNotShareExecutionLock() {
    when(lockService.tryAcquire(eq("nba:sync:submit"), anyString(), any())).thenReturn(true);
    when(lockService.tryAcquire(startsWith("nba:sync:module:"), anyString(), any())).thenReturn(true);
    submitWithGeneratedId("NEWS", 51L);
    submitWithGeneratedId("TODAY_GAMES", 52L);
    verify(lockService, never()).tryAcquire(eq("nba:sync:lock:MANUAL"), anyString(), any());
}

@Test
void fullSyncReturnsConflictingModules() {
    when(repository.findByStatusIn(ACTIVE_STATUSES)).thenReturn(List.of(
            job(11L, "NEWS", "SCHEDULED", "RUNNING"),
            job(12L, "DRAFT", "MANUAL", "QUEUED")));
    assertThatThrownBy(() -> coordinator.submitFullSync("MANUAL", "admin"))
            .isInstanceOf(SyncJobConflictException.class)
            .hasMessageContaining("NEWS")
            .hasMessageContaining("DRAFT");
}
```

- [ ] **步骤 2：运行 Coordinator 专项测试确认失败**

运行：`.\mvnw.cmd -q '-Dtest=SyncCoordinatorTest,SyncCoordinatorConflictTest' test`

预期：复用 API、模块锁键和冲突模块断言失败。

- [ ] **步骤 3：实现短持有提交互斥和长持有执行锁**

提交顺序固定为：

```java
public SyncSubmission submit(SyncCommand command) {
    SyncJob active = syncJobRepository.findActiveByModule(command.module()).orElse(null);
    if (active != null) return new SyncSubmission(active, true);

    try (SyncLockHandle gate = syncLockService.acquireHandle(
            "nba:sync:submit", Duration.ofSeconds(15))) {
        SyncJob secondCheck = syncJobRepository.findActiveByModule(command.module()).orElse(null);
        if (secondCheck != null) return new SyncSubmission(secondCheck, true);
        ensureNoActiveFullSync();
        return createAndDispatch(command, "nba:sync:module:" + command.module());
    }
}
```

`SyncLockHandle` 作为 `SyncLockService` 的嵌套 `AutoCloseable` 类型，使用 token 校验 Lua 释放；普通任务只长持有模块锁。全量提交在同一短门禁内检查全部活动任务并长持有 `nba:sync:full`，从而避免全量与普通任务竞态。

`SyncJobResponse` 增加 `boolean reused`；普通查询使用 `false`，提交响应使用 `SyncSubmission.reused()`。

- [ ] **步骤 4：运行 Coordinator 测试**

运行：`.\mvnw.cmd -q '-Dtest=SyncCoordinatorTest,SyncCoordinatorConflictTest,SyncLockServiceTest' test`

预期：全部通过，既有锁丢失、Executor 拒绝和取消测试继续通过。

- [ ] **步骤 5：提交统一提交链路**

```powershell
git add backend/src/main/java/com/nbamanager/service/SyncCoordinator.java backend/src/main/java/com/nbamanager/service/SyncLockService.java backend/src/main/java/com/nbamanager/web/dto/SyncJobResponse.java backend/src/test/java/com/nbamanager/service/SyncCoordinatorTest.java backend/src/test/java/com/nbamanager/service/SyncCoordinatorConflictTest.java backend/src/test/java/com/nbamanager/service/SyncLockServiceTest.java
git commit -m "feat: unify sync submission and deduplication"
```

### 任务 4：持久化全量步骤并统一 Worker 执行上下文

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/service/SyncExecutionContext.java`
- 修改：`backend/src/main/java/com/nbamanager/service/ModuleSyncWorker.java`
- 修改：`backend/src/main/java/com/nbamanager/service/SyncCoordinator.java`
- 修改：九个 `backend/src/main/java/com/nbamanager/service/*SyncWorker.java`
- 修改：`backend/src/main/java/com/nbamanager/service/SyncJobStateService.java`
- 修改：`backend/src/test/java/com/nbamanager/service/ModuleSyncWorkerProgressTest.java`
- 修改：`backend/src/test/java/com/nbamanager/service/WorkerQualityGateTest.java`
- 创建：`backend/src/test/java/com/nbamanager/service/FullSyncStepTest.java`

- [ ] **步骤 1：编写步骤进度和失败继续测试**

```java
@Test
void workerUpdatesParentAndStepProgress() {
    SyncExecutionContext context = new SyncExecutionContext(10L, 70L, 20, 40);
    worker.sync(context);
    verify(stepStateService).updateProgress(70L, 50, "FETCHING");
    verify(jobStateService).updateProgress(10L, 30, "FETCHING");
}

@Test
void fullSyncContinuesAfterFailedStep() {
    when(teamWorker.sync(any())).thenReturn(SyncResult.failed("TEAMS", "QUALITY_GATE", "29 teams"));
    when(playerWorker.sync(any())).thenReturn(success("PLAYERS"));
    SyncResult result = coordinator.executeFullSyncForTest(90L);
    assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
    verify(playerWorker).sync(any());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SyncJobStep>> steps = ArgumentCaptor.forClass(List.class);
    verify(stepRepository).saveAll(steps.capture());
    assertThat(steps.getValue()).extracting(SyncJobStep::getModule).containsExactly(
            "TEAMS", "PLAYERS", "MATCHES", "TODAY_GAMES", "NEWS",
            "DRAFT", "PLAYOFF", "HISTORICAL", "TRANSLATION");
}
```

- [ ] **步骤 2：运行 Worker 和全量步骤测试确认失败**

运行：`.\mvnw.cmd -q '-Dtest=ModuleSyncWorkerProgressTest,WorkerQualityGateTest,FullSyncStepTest' test`

预期：缺少执行上下文、步骤状态服务调用和持久步骤创建。

- [ ] **步骤 3：实现执行上下文与阶段进度**

```java
public record SyncExecutionContext(Long jobId, Long stepId, int startProgress, int endProgress) {
    public static SyncExecutionContext single(Long jobId) {
        return new SyncExecutionContext(jobId, null, 0, 100);
    }
    public int mapProgress(int localProgress) {
        int bounded = Math.max(0, Math.min(100, localProgress));
        return startProgress + bounded * (endProgress - startProgress) / 100;
    }
}
```

`ModuleSyncWorker.sync(context)` 调用 `doSync(context.jobId())`。`updateProgress(context, local, stage)` 同时更新步骤本地进度和父任务映射进度。单模块任务的 `stepId` 为 `null`。

`SyncJobStateService.updateProgress` 签名改为：

```java
public void updateProgress(Long jobId, int progress, String stage)
```

它在同一短事务内单调更新进度、阶段和心跳。

- [ ] **步骤 4：创建并执行九个全量步骤**

Coordinator 在父任务启动时按注册表顺序创建九个 `QUEUED` 步骤。每步执行前 `markRunning`，结束后保存原始 `SyncResult`；失败累积后继续下一步。取消时当前步骤和剩余步骤均写 `CANCELLED`。

球队、球员和比赛 Worker 将 `120` 替换为 `pythonTimeoutProperties.getBulkSeconds()`，不得重新引入脚本超时常量。

- [ ] **步骤 5：运行 Worker、全量和终态保护测试**

运行：`.\mvnw.cmd -q '-Dtest=ModuleSyncWorkerProgressTest,WorkerQualityGateTest,FullSyncStepTest,SyncExecResultContractTest' test`

预期：全部通过。

- [ ] **步骤 6：提交统一执行上下文**

```powershell
git add backend/src/main/java/com/nbamanager/service/SyncExecutionContext.java backend/src/main/java/com/nbamanager/service/ModuleSyncWorker.java backend/src/main/java/com/nbamanager/service/SyncCoordinator.java backend/src/main/java/com/nbamanager/service/SyncJobStateService.java backend/src/main/java/com/nbamanager/service/TeamSyncWorker.java backend/src/main/java/com/nbamanager/service/PlayerSyncWorker.java backend/src/main/java/com/nbamanager/service/MatchSyncWorker.java backend/src/main/java/com/nbamanager/service/TodayGamesSyncWorker.java backend/src/main/java/com/nbamanager/service/NewsSyncWorker.java backend/src/main/java/com/nbamanager/service/DraftSyncWorker.java backend/src/main/java/com/nbamanager/service/PlayoffSyncWorker.java backend/src/main/java/com/nbamanager/service/HistoricalSyncWorker.java backend/src/main/java/com/nbamanager/service/TranslationSyncWorker.java backend/src/test/java/com/nbamanager/service/ModuleSyncWorkerProgressTest.java backend/src/test/java/com/nbamanager/service/WorkerQualityGateTest.java backend/src/test/java/com/nbamanager/service/FullSyncStepTest.java backend/src/test/java/com/nbamanager/service/SyncExecResultContractTest.java
git commit -m "feat: persist full sync execution steps"
```

### 任务 5：将所有数据定时任务迁移到 Coordinator

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/service/DataSyncScheduler.java`
- 修改：`backend/src/main/java/com/nbamanager/service/NbaLiveSyncService.java`
- 修改：`backend/src/main/java/com/nbamanager/service/DraftSyncService.java`
- 修改：`backend/src/main/java/com/nbamanager/service/HistoricalDataSyncService.java`
- 修改：`backend/src/main/java/com/nbamanager/service/TranslationRetryService.java`
- 创建：`backend/src/test/java/com/nbamanager/service/DataSyncSchedulerTest.java`
- 创建：`backend/src/test/java/com/nbamanager/service/ScheduledSyncArchitectureTest.java`

- [ ] **步骤 1：编写调度委托和架构约束测试**

```java
@Test
void newsScheduleSubmitsScheduledCommand() {
    scheduler.syncNews();
    verify(coordinator).submit(SyncCommand.scheduled("NEWS"));
    verifyNoInteractions(liveSyncService);
}

@Test
void dataServicesContainNoDataScheduledMethods() {
    assertThat(scheduledMethods(NbaLiveSyncService.class))
            .extracting(Method::getName)
            .containsExactly("scheduledCleanDuplicates");
    assertThat(scheduledMethods(DraftSyncService.class)).isEmpty();
    assertThat(scheduledMethods(HistoricalDataSyncService.class)).isEmpty();
    assertThat(scheduledMethods(TranslationRetryService.class)).isEmpty();
}
```

- [ ] **步骤 2：运行调度测试确认旧注解导致失败**

运行：`.\mvnw.cmd -q '-Dtest=DataSyncSchedulerTest,ScheduledSyncArchitectureTest' test`

预期：缺少 `DataSyncScheduler`，且业务服务仍包含数据型 `@Scheduled`。

- [ ] **步骤 3：实现纯提交调度器**

```java
@Service
@RequiredArgsConstructor
public class DataSyncScheduler {
    private final SyncCoordinator coordinator;

    @Scheduled(cron = "${app.sync.cron.full-sync:0 0 3 * * ?}", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncFull() { coordinator.submitFullSync("SCHEDULED", "scheduler"); }

    @Scheduled(fixedRateString = "${app.sync.interval.today-games-ms:300000}")
    public void syncTodayGames() { coordinator.submit(SyncCommand.scheduled("TODAY_GAMES")); }

    @Scheduled(fixedRateString = "${app.sync.interval.news-ms:3600000}")
    public void syncNews() { coordinator.submit(SyncCommand.scheduled("NEWS")); }

    @Scheduled(cron = "0 0 */2 4-6 * ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncPlayoff() { coordinator.submit(SyncCommand.scheduled("PLAYOFF")); }

    @Scheduled(cron = "0 */30 * 15-30 6 ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncDraftJune() { coordinator.submit(SyncCommand.scheduled("DRAFT")); }

    @Scheduled(cron = "0 */30 * 1-10 7 ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncDraftJuly() { coordinator.submit(SyncCommand.scheduled("DRAFT")); }

    @Scheduled(cron = "0 20 4 * * ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncDraftDaily() { coordinator.submit(SyncCommand.scheduled("DRAFT")); }

    @Scheduled(cron = "0 0 4 * * ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncHistorical() { coordinator.submit(SyncCommand.scheduled("HISTORICAL")); }

    @Scheduled(cron = "0 10 * * * *", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncTranslation() { coordinator.submit(SyncCommand.scheduled("TRANSLATION")); }
}
```

每个方法通过一个私有提交包装器捕获任务复用和全量冲突，只记录去重，不直接执行 Service；测试验证包装器不会向调度线程抛出已知冲突。

- [ ] **步骤 4：移除业务服务的数据调度注解**

保留可由 Worker 调用的业务方法及 `NbaLiveSyncService.scheduledCleanDuplicates()`；删除 `scheduledFullSync` 等仅为旧调度存在的方法。Play-by-Play 和聊天清理调度不属于数据管理模块，不做迁移。

- [ ] **步骤 5：运行调度测试和完整编译**

运行：

```powershell
.\mvnw.cmd -q '-Dtest=DataSyncSchedulerTest,ScheduledSyncArchitectureTest' test
.\mvnw.cmd -q -DskipTests package
```

预期：测试与编译通过。

- [ ] **步骤 6：提交调度迁移**

```powershell
git add backend/src/main/java/com/nbamanager/service/DataSyncScheduler.java backend/src/main/java/com/nbamanager/service/NbaLiveSyncService.java backend/src/main/java/com/nbamanager/service/DraftSyncService.java backend/src/main/java/com/nbamanager/service/HistoricalDataSyncService.java backend/src/main/java/com/nbamanager/service/TranslationRetryService.java backend/src/test/java/com/nbamanager/service/DataSyncSchedulerTest.java backend/src/test/java/com/nbamanager/service/ScheduledSyncArchitectureTest.java backend/src/main/resources/application.yml
git commit -m "refactor: route scheduled sync through coordinator"
```

### 任务 6：实现管理员登录遗漏调度补偿

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/event/AdminLoginEvent.java`
- 创建：`backend/src/main/java/com/nbamanager/service/SyncCatchupService.java`
- 修改：`backend/src/main/java/com/nbamanager/web/AuthController.java`
- 修改：`backend/src/main/java/com/nbamanager/repository/SyncJobRepository.java`
- 修改：`backend/src/main/resources/application.yml`
- 创建：`backend/src/test/java/com/nbamanager/service/SyncCatchupServiceTest.java`
- 创建：`backend/src/test/java/com/nbamanager/web/AdminLoginCatchupTest.java`

- [ ] **步骤 1：编写遗漏折叠、并发登录和非阻塞测试**

```java
@Test
void missedMultipleNewsIntervalsSubmitOneCatchup() {
    when(repository.findTopByModuleAndStatusOrderByFinishedAtDesc("NEWS", "SUCCESS"))
            .thenReturn(Optional.of(successAt("NEWS", now.minusHours(4))));
    catchupService.onAdminLogin(new AdminLoginEvent("admin", now));
    verify(coordinator, times(1)).submit(SyncCommand.loginCatchup("NEWS", "admin"));
}

@Test
void activeModuleIsDeduplicatedByCoordinator() {
    when(repository.findTopByModuleAndStatusOrderByFinishedAtDesc("TODAY_GAMES", "SUCCESS"))
            .thenReturn(Optional.empty());
    when(coordinator.submit(any())).thenReturn(new SyncSubmission(activeJob(), true));
    catchupService.onAdminLogin(new AdminLoginEvent("admin", now));
    verify(coordinator).submit(SyncCommand.loginCatchup("TODAY_GAMES", "admin"));
}

@Test
void loginPublishesEventAndReturnsWithoutRunningCatchupInline() {
    LoginResponse response = controller.login(new LoginRequest("admin", "secret"));
    assertThat(response.role()).isEqualTo("ADMIN");
    verify(eventPublisher).publishEvent(argThat(event -> event instanceof AdminLoginEvent));
    verifyNoInteractions(catchupService);
}
```

- [ ] **步骤 2：运行补偿测试确认缺少事件和服务**

运行：`.\mvnw.cmd -q '-Dtest=SyncCatchupServiceTest,AdminLoginCatchupTest' test`

预期：测试编译失败，缺少 `AdminLoginEvent` 和 `SyncCatchupService`。

- [ ] **步骤 3：发布管理员登录事件**

```java
public record AdminLoginEvent(String username, ZonedDateTime loggedInAt) {}
```

`AuthController.login()` 仅在认证角色为 `ADMIN` 时调用：

```java
eventPublisher.publishEvent(new AdminLoginEvent(
        principal.getUsername(), ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
```

事件发布后立即返回原 `LoginResponse`。

- [ ] **步骤 4：实现异步补偿检查与延迟重试**

```java
@Async("taskExecutor")
@EventListener
public void onAdminLogin(AdminLoginEvent event) {
    String token = UUID.randomUUID().toString();
    if (!lockService.tryAcquire("nba:sync:catchup", token, catchupGuardTtl)) return;
    try {
        ZonedDateTime now = event.loggedInAt();
        if (isMissed(registry.fullSyncDefinition(), now)) {
            submitOrDeferFullCatchup(event.username());
            return;
        }
        for (SyncDefinition definition : registry.scheduledDefinitions()) {
            Optional<SyncJob> latest = repository.findTopByModuleAndStatusOrderByFinishedAtDesc(
                    definition.module(), "SUCCESS");
            boolean due = latest.isEmpty() || definition.isDue(toZoned(latest.get().getFinishedAt()), now);
            if (due) coordinator.submit(SyncCommand.loginCatchup(definition.module(), event.username()));
        }
    } finally {
        lockService.release("nba:sync:catchup", token);
    }
}

private boolean isMissed(SyncDefinition definition, ZonedDateTime now) {
    Optional<SyncJob> latest = repository.findTopByModuleAndStatusOrderByFinishedAtDesc(
            definition.module(), "SUCCESS");
    return latest.isEmpty() || definition.isDue(toZoned(latest.get().getFinishedAt()), now);
}

private ZonedDateTime toZoned(LocalDateTime value) {
    return value.atZone(ZoneId.of("Asia/Shanghai"));
}

private void submitOrDeferFullCatchup(String username) {
    try {
        coordinator.submitFullSync("LOGIN_CATCHUP", username);
        catchupPending.set(false);
    } catch (SyncJobConflictException conflict) {
        catchupPending.set(true);
        pendingRequestedBy.set(username);
    }
}
```

全量遗漏优先检查。存在普通活动任务时，将 `catchupPending` 保持在服务状态中，并由 `@Scheduled(fixedDelayString="${app.sync.catchup-retry-ms:60000}")` 重试；成功创建全量任务后清除。Redis guard TTL 和冷却期从配置读取。

`SyncJobRepository` 增加明确的成功查询：

```java
Optional<SyncJob> findTopByModuleAndStatusOrderByFinishedAtDesc(
        String module, String status);
```

调用方固定传入状态 `SUCCESS`。

- [ ] **步骤 5：运行补偿与认证测试**

运行：`.\mvnw.cmd -q '-Dtest=SyncCatchupServiceTest,AdminLoginCatchupTest' test`

预期：全部通过，普通用户登录不发布事件。

- [ ] **步骤 6：提交登录补偿**

```powershell
git add backend/src/main/java/com/nbamanager/event/AdminLoginEvent.java backend/src/main/java/com/nbamanager/service/SyncCatchupService.java backend/src/main/java/com/nbamanager/web/AuthController.java backend/src/main/java/com/nbamanager/repository/SyncJobRepository.java backend/src/main/resources/application.yml backend/src/test/java/com/nbamanager/service/SyncCatchupServiceTest.java backend/src/test/java/com/nbamanager/web/AdminLoginCatchupTest.java
git commit -m "feat: catch up missed syncs on admin login"
```

### 任务 7：完成任务 API、概览和历史查询契约

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/web/dto/SyncJobStepResponse.java`
- 创建：`backend/src/main/java/com/nbamanager/web/dto/SyncJobDetailResponse.java`
- 创建：`backend/src/main/java/com/nbamanager/web/dto/SyncJobPageResponse.java`
- 修改：`backend/src/main/java/com/nbamanager/web/dto/SyncJobResponse.java`
- 修改：`backend/src/main/java/com/nbamanager/web/dto/SyncModuleOverview.java`
- 修改：`backend/src/main/java/com/nbamanager/service/SyncOverviewService.java`
- 修改：`backend/src/main/java/com/nbamanager/web/AdminController.java`
- 修改：`backend/src/test/java/com/nbamanager/service/SyncOverviewServiceTest.java`
- 创建：`backend/src/test/java/com/nbamanager/web/AdminSyncControllerContractTest.java`

- [ ] **步骤 1：编写 202/200、全量详情和最后成功测试**

```java
@Test
void manualSubmissionReturns200WhenExistingJobIsReused() {
    when(coordinator.submit(any())).thenReturn(new SyncSubmission(runningJob(81L, "SCHEDULED"), true));
    ResponseEntity<SyncJobResponse> response = controller.syncModule("NEWS", adminPrincipal());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().reused()).isTrue();
    assertThat(response.getBody().triggerType()).isEqualTo("SCHEDULED");
}

@Test
void overviewUsesLatestSuccessSeparatelyFromLatestAttempt() {
    when(repository.findTopByModuleOrderByCreatedAtDesc("NEWS")).thenReturn(Optional.of(failedAt(now)));
    when(repository.findTopByModuleAndStatusOrderByFinishedAtDesc("NEWS", "SUCCESS"))
            .thenReturn(Optional.of(successAt(now.minusHours(1))));
    SyncModuleOverview result = service.overviewFor("NEWS");
    assertThat(result.latestJob().status()).isEqualTo("FAILED");
    assertThat(result.lastSuccessfulAt()).isEqualTo(now.minusHours(1));
}
```

- [ ] **步骤 2：运行 API 契约测试确认响应尚不支持复用**

运行：`.\mvnw.cmd -q '-Dtest=AdminSyncControllerContractTest,SyncOverviewServiceTest' test`

预期：HTTP 状态、`reused`、步骤详情和最后成功字段断言失败。

- [ ] **步骤 3：实现提交和详情响应**

模块提交根据 `SyncSubmission.reused()` 返回 `200 OK` 或 `202 Accepted`。全量详情映射 `SyncJobStepRepository.findByJobIdOrderByStepOrder(jobId)`。全量提交的规范路径为 `POST /api/admin/sync/full`；保留现有 `/api/admin/sync-all` 作为兼容委托，二者调用同一 Controller 私有方法。

`SyncModuleOverview` 固定包含：

```java
public record SyncModuleOverview(
        String key,
        long count,
        LocalDateTime lastSuccessfulAt,
        SyncJobResponse latestJob,
        SyncJobResponse activeJob,
        SyncJobStepResponse activeFullStep) {}
```

- [ ] **步骤 4：实现分页历史筛选**

`GET /api/admin/jobs?module=NEWS&triggerType=SCHEDULED&status=FAILED&page=0&size=20` 使用 `Pageable` 和可选参数。仓库使用显式 JPQL 条件，排序固定为 `createdAt DESC`；`size` 上限为 100。

```java
@Query("SELECT j FROM SyncJob j WHERE "
        + "(:module IS NULL OR j.module = :module) AND "
        + "(:triggerType IS NULL OR j.triggerType = :triggerType) AND "
        + "(:status IS NULL OR j.status = :status)")
Page<SyncJob> search(
        @Param("module") String module,
        @Param("triggerType") String triggerType,
        @Param("status") String status,
        Pageable pageable);
```

- [ ] **步骤 5：运行 API 和概览测试**

运行：`.\mvnw.cmd -q '-Dtest=AdminSyncControllerContractTest,SyncOverviewServiceTest' test`

预期：全部通过。

- [ ] **步骤 6：提交 API 契约**

```powershell
git add backend/src/main/java/com/nbamanager/web/AdminController.java backend/src/main/java/com/nbamanager/web/dto/SyncJobResponse.java backend/src/main/java/com/nbamanager/web/dto/SyncJobStepResponse.java backend/src/main/java/com/nbamanager/web/dto/SyncJobDetailResponse.java backend/src/main/java/com/nbamanager/web/dto/SyncJobPageResponse.java backend/src/main/java/com/nbamanager/web/dto/SyncModuleOverview.java backend/src/main/java/com/nbamanager/service/SyncOverviewService.java backend/src/main/java/com/nbamanager/repository/SyncJobRepository.java backend/src/test/java/com/nbamanager/web/AdminSyncControllerContractTest.java backend/src/test/java/com/nbamanager/service/SyncOverviewServiceTest.java
git commit -m "feat: expose unified sync job history"
```

### 任务 8：升级数据管理中心交互

**文件：**
- 修改：`frontend/src/api/dataManagement.ts`
- 创建：`frontend/src/components/admin/SyncModuleCard.vue`
- 创建：`frontend/src/components/admin/FullSyncProgress.vue`
- 创建：`frontend/src/components/admin/SyncHistoryPanel.vue`
- 修改：`frontend/src/views/admin/DataSyncView.vue`

- [ ] **步骤 1：更新前端类型与 API**

```typescript
export type SyncTriggerType = 'SCHEDULED' | 'MANUAL' | 'LOGIN_CATCHUP'

export interface SyncJobResponse {
  jobId: number
  module: string
  triggerType: SyncTriggerType
  reused: boolean
  status: string
  progress: number
  stage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  errorCode: string | null
  errorMessage: string | null
}

export interface SyncModuleOverview {
  key: string
  count: number
  lastSuccessfulAt: string | null
  latestJob: SyncJobResponse | null
  activeJob: SyncJobResponse | null
  activeFullStep: SyncJobStepResponse | null
}

export function getJobHistory(filters: JobHistoryFilters) {
  return request.get<SyncJobPageResponse>('/admin/jobs', { params: filters })
}
```

- [ ] **步骤 2：实现模块卡片组件**

`SyncModuleCard.vue` 接收 `module`，发出 `sync` 和 `cancel`。卡片显示来源标签、`stage`、最后尝试、最后成功、运行时长和错误；活动任务始终允许取消，不再因其他模块运行而禁用普通模块同步按钮。

```vue
<script setup lang="ts">
import type { SyncModuleOverview } from '@/api/dataManagement'
defineProps<{ module: SyncModuleOverview }>()
defineEmits<{ sync: [module: string]; cancel: [jobId: number] }>()
</script>

<template>
  <article class="module-card">
    <header><h3>{{ module.key }}</h3><span>{{ module.count }} 条</span></header>
    <p v-if="module.activeJob">{{ module.activeJob.stage }} · {{ module.activeJob.progress }}%</p>
    <time v-if="module.lastSuccessfulAt">{{ module.lastSuccessfulAt }}</time>
    <button v-if="module.activeJob" title="取消" @click="$emit('cancel', module.activeJob.jobId)">×</button>
    <button v-else @click="$emit('sync', module.key)">同步</button>
  </article>
</template>
```

来源文本固定映射：

```typescript
const triggerLabels = {
  SCHEDULED: '自动',
  MANUAL: '手动',
  LOGIN_CATCHUP: '登录补偿',
} satisfies Record<SyncTriggerType, string>
```

- [ ] **步骤 3：实现全量步骤组件**

`FullSyncProgress.vue` 显示父任务总进度和九个步骤状态。终态为 `PARTIAL_FAILED` 时，仅失败步骤显示“重试”按钮并发出 `retry-module`。

```vue
<script setup lang="ts">
import type { SyncJobDetailResponse } from '@/api/dataManagement'
defineProps<{ job: SyncJobDetailResponse }>()
defineEmits<{ retryModule: [module: string] }>()
</script>

<template>
  <section aria-label="全量同步进度">
    <div v-for="step in job.steps" :key="step.module">
      <span>{{ step.module }}</span><span>{{ step.status }} · {{ step.progress }}%</span>
      <button v-if="['FAILED', 'PARTIAL_FAILED'].includes(step.status)"
              @click="$emit('retryModule', step.module)">重试</button>
    </div>
  </section>
</template>
```

- [ ] **步骤 4：实现持久历史组件**

`SyncHistoryPanel.vue` 提供模块、来源、状态筛选和分页；筛选变化时重新请求，不在本地日志数组中模拟历史。任务行显示开始时间、耗时、统计和错误详情。

```typescript
const filters = reactive<JobHistoryFilters>({ page: 0, size: 20 })
const page = ref<SyncJobPageResponse>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })

watch(filters, async () => {
  page.value = (await getJobHistory(filters)).data
}, { deep: true, immediate: true })
```

- [ ] **步骤 5：重构 DataSyncView 的复用与恢复逻辑**

```typescript
const { data } = await syncModule(moduleKey)
applyJobToModule(data)
if (data.reused) {
  ElMessage.info(`已关联正在运行的${triggerLabels[data.triggerType]}任务`)
} else {
  ElMessage.success('同步任务已提交')
}
startPolling()
```

页面初始化只依赖 overview 的 `activeJob` 恢复轮询。全量任务轮询详情并更新步骤；取消后等待后端终态，不前端伪造 `CANCELLED`。

- [ ] **步骤 6：运行前端构建**

运行：

```powershell
cd frontend
npm run build
```

预期：TypeScript 与 Vite 构建成功。

- [ ] **步骤 7：提交管理中心交互**

```powershell
git add frontend/src/api/dataManagement.ts frontend/src/components/admin/SyncModuleCard.vue frontend/src/components/admin/FullSyncProgress.vue frontend/src/components/admin/SyncHistoryPanel.vue frontend/src/views/admin/DataSyncView.vue
git commit -m "feat: show unified sync activity in admin center"
```

### 任务 9：完整回归与 Playwright 端到端验收

**文件：**
- 创建：`output/playwright/unified-sync-acceptance-20260718/ACCEPTANCE-REPORT.md`
- 创建：`output/playwright/unified-sync-acceptance-20260718/*.png`

- [ ] **步骤 1：扫描残留双轨调用和硬编码**

运行：

```powershell
Get-ChildItem 'backend/src/main/java/com/nbamanager/service' -Filter '*.java' |
  Select-String -Pattern '@Scheduled|runner\.runForJob.*120'
```

预期：数据型 `@Scheduled` 只存在于 `DataSyncScheduler`；`NbaLiveSyncService` 仅保留去重维护调度；不存在 Worker 的 120 秒脚本超时。

- [ ] **步骤 2：运行后端完整测试**

运行：

```powershell
cd backend
.\mvnw.cmd clean test
```

预期：Maven exit code 0，Failures 0，Errors 0。

- [ ] **步骤 3：运行 Python 与前端验证**

运行：

```powershell
python -m py_compile scripts/nba_data_fetcher.py scripts/translate_names.py
cd ..\frontend
npm run build
```

预期：Python 语法检查和 Vite 构建成功。

- [ ] **步骤 4：重启后端并确认任务恢复**

使用 `local` profile 重启 8080 后端，确认 `/api/admin/overview` 在管理员令牌下返回 200，并确认数据库不存在遗留 `QUEUED/RUNNING/CANCELLING` 测试任务。

- [ ] **步骤 5：Playwright 验证手动与自动复用**

1. 打开 `http://localhost:5173/#/admin/sync`。
2. 等待或通过测试调度触发一个 `SCHEDULED` 模块任务。
3. 点击同模块同步按钮。
4. 断言未创建第二个 `jobId`，页面显示“已关联正在运行的自动任务”。
5. 保存 `scheduled-reused.png`。

- [ ] **步骤 6：Playwright 验证取消后重新同步**

1. 点击关联任务的取消按钮。
2. 轮询到数据库和 UI 均为 `CANCELLED`。
3. 再次点击同步，断言创建新的 `MANUAL` jobId。
4. 保存 `cancelled-then-manual.png`。

- [ ] **步骤 7：Playwright 验证全量部分失败和历史筛选**

1. 使用可控测试 Provider 让一个模块质量门失败。
2. 触发全量同步，断言后续步骤仍执行。
3. 断言父任务为 `PARTIAL_FAILED`，失败模块可独立重试。
4. 按来源“自动”和状态“部分失败”筛选历史。
5. 保存 `full-partial-failed.png` 和 `history-filter.png`。

- [ ] **步骤 8：Playwright 验证管理员登录补偿**

1. 将测试模块最后成功时间设为最近应执行时间之前。
2. 管理员登录并记录登录响应耗时，断言登录不等待任务完成。
3. 打开管理中心，断言出现 `LOGIN_CATCHUP` 来源任务。
4. 保存 `login-catchup.png`。

- [ ] **步骤 9：编写验收报告并提交**

报告记录测试总数、构建结果、实际 jobId、数据库终态、接口状态以及全部截图绝对路径。清理测试账号和活动任务后提交：

```powershell
git add output/playwright/unified-sync-acceptance-20260718
git commit -m "test: verify unified sync workflows"
```

---

## 最终完成条件

- 自动、手动和登录补偿通过同一个 `SyncDefinitionRegistry` 和 Worker。
- 同模块手动操作复用活动自动任务，返回 HTTP 200 和 `reused=true`。
- 不同普通模块可受控并行，全量任务保持全局独占。
- 全量步骤持久化，失败继续，最终正确汇总为 `PARTIAL_FAILED`。
- 管理员登录异步补跑遗漏调度，多次遗漏折叠为一次。
- 自动、手动、登录补偿任务在管理中心统一可见、可取消、可筛选。
- 后端完整测试、Python 语法、前端构建和 Playwright 验收全部通过。
