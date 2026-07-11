# 聊天消息定时清理设计

日期：2026-07-11

## 背景

多人聊天室的消息存储在 MySQL `chat_messages` 表中，无自动过期机制。随着 NBA 赛季推进（常规赛 ~1230 场 + 季后赛），每场比赛可能产生数百条消息，跨赛季累积将导致表持续增长，占用数据库空间并影响查询性能。

## 目标

- 消息保留一个赛季（约 180 天），过期后自动物理删除
- 清理过程不影响正常聊天业务
- 保留天数可配置
- 支持多实例部署时只执行一次

## 方案

### 配置

`application.yml` 新增：

```yaml
app:
  chat:
    cleanup-enabled: ${CHAT_CLEANUP_ENABLED:true}
    retention-days: ${CHAT_RETENTION_DAYS:180}
    cleanup-batch-size: ${CHAT_CLEANUP_BATCH_SIZE:1000}
    cleanup-cron: ${CHAT_CLEANUP_CRON:0 0 3 * * ?}
    cleanup-max-batches: ${CHAT_CLEANUP_MAX_BATCHES:500}
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cleanup-enabled` | true | 是否启用清理任务（本地调试、演示环境可关闭） |
| `retention-days` | 180 | 消息保留天数 |
| `cleanup-batch-size` | 1000 | 每批删除条数 |
| `cleanup-cron` | `0 0 3 * * ?` | 定时表达式，默认每天凌晨 3 点 |
| `cleanup-max-batches` | 500 | 单次运行最多清理批次数，防死循环 |

### 启用调度

新增 `SchedulingConfig.java`，使用 `@EnableScheduling`：

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

### ChatCleanupService + ChatCleanupBatchService

为避免 `@Transactional(REQUIRES_NEW)` 自调用失效，将单批删除拆到独立 Bean。

**ChatCleanupBatchService**（新建）：

```java
@Service
@RequiredArgsConstructor
public class ChatCleanupBatchService {

    private final ChatMessageRepository chatMessageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteOneBatch(Instant cutoff, int limit) {
        return chatMessageRepository.deleteBatchBefore(cutoff, limit);
    }
}
```

**ChatCleanupService**（新建）：

- **调度方法** `cleanupExpiredMessages()`：无 `@Transactional`，负责分布式锁、循环调度和异常控制
- **分布式锁**：使用 Redis `SETNX` 设置 `nba:chat:cleanup:lock`，TTL 300 秒，获取失败则跳过本次执行
- **防死循环**：使用 `cleanup-max-batches` 配置（默认 500），某批删除失败时记录日志并 break

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatCleanupService {

    @Value("${app.chat.cleanup-enabled:true}")
    private boolean enabled;

    @Value("${app.chat.retention-days:180}")
    private int retentionDays;

    @Value("${app.chat.cleanup-batch-size:1000}")
    private int batchSize;

    @Value("${app.chat.cleanup-max-batches:500}")
    private int maxBatches;

    private final ChatCleanupBatchService batchService;
    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_KEY = "nba:chat:cleanup:lock";
    private static final long LOCK_TTL_SECONDS = 300;

    @Scheduled(cron = "${app.chat.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpiredMessages() {
        if (!enabled) return;

        // 分布式锁：多实例只执行一次
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("未获取清理锁，跳过本次执行");
            return;
        }

        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            log.info("开始清理{}天前的聊天消息, cutoff={}", retentionDays, cutoff);

            int totalDeleted = 0;

            for (int batch = 0; batch < maxBatches; batch++) {
                try {
                    int deleted = batchService.deleteOneBatch(cutoff, batchSize);
                    totalDeleted += deleted;
                    log.debug("第{}批删除{}条", batch + 1, deleted);
                    if (deleted == 0) break;
                } catch (Exception e) {
                    log.warn("Chat cleanup batch {} failed, stop current run", batch + 1, e);
                    break;
                }
            }

            log.info("聊天消息清理完成, 共删除{}条", totalDeleted);
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }
}
```

### Repository 方法

`ChatMessageRepository` 新增：

```java
@Modifying
@Query(value = """
    DELETE FROM chat_messages
    WHERE id IN (
        SELECT id FROM (
            SELECT id FROM chat_messages
            WHERE created_at < :cutoff
            ORDER BY id
            LIMIT :limit
        ) t
    )
    """, nativeQuery = true)
int deleteBatchBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
```

双层子查询避免 MySQL 同表 `DELETE ... WHERE id IN (SELECT id FROM same_table)` 的兼容风险。

### 索引

`ChatMessage` 实体新增索引：

```java
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_game_time", columnList = "gameId,createdAt"),
        @Index(name = "idx_chat_game_id", columnList = "gameId"),
        @Index(name = "idx_chat_created_id", columnList = "createdAt,id")  // 新增
})
```

`createdAt,id` 复合索引服务于清理查询的 `WHERE created_at < :cutoff ORDER BY id LIMIT :limit`，避免全表扫描。

### 软删除消息处理

管理员软删除的消息（`deleted = true`）与正常消息使用相同的保留策略，到期后一并物理删除，不做特殊区分。

### 异常处理

- 每批删除在独立事务中执行（`REQUIRES_NEW`，通过独立 Bean 避免自调用失效），某批失败不影响其他批次
- 每批调用包裹在 try/catch 中，失败后记录日志并 break，不继续重试
- 调度方法的异常不传播到调度器，不影响主业务

### 安全约束

- `clientMessageId` 去重约束仅在消息发送时生效，历史清理不受影响
- 物理删除不触发软删除的广播逻辑
- Redis 锁 TTL（300 秒）覆盖预计清理时间，避免死锁

## 测试要求

| 场景 | 预期 |
|------|------|
| 保留期内消息不被删除 | 179 天前的消息仍存在 |
| 过期消息批量删除 | 181 天前的消息被删除，每批 1000 条 |
| 软删除消息到期物理删除 | deleted=true 且过期的消息同样被删除 |
| limit 生效 | 每批删除条数不超过 batchSize |
| 重复执行幂等 | 连续执行两次，第二次删除 0 条 |
| 清理失败不影响发送消息 | 清理任务异常时聊天功能正常 |
| 多实例只执行一次 | Redis 锁防止重复执行 |
| cleanup-enabled=false 时不执行 | 配置关闭后定时任务跳过 |

## 文件清单

| 文件 | 操作 |
|------|------|
| `backend/src/main/java/com/nbamanager/config/SchedulingConfig.java` | 新增 |
| `backend/src/main/java/com/nbamanager/service/ChatCleanupService.java` | 新增 |
| `backend/src/main/java/com/nbamanager/service/ChatCleanupBatchService.java` | 新增 |
| `backend/src/main/java/com/nbamanager/repository/ChatMessageRepository.java` | 修改（添加 `deleteBatchBefore`） |
| `backend/src/main/java/com/nbamanager/domain/ChatMessage.java` | 修改（添加索引） |
| `backend/src/main/resources/application.yml` | 修改（添加 `app.chat.*` 配置） |
| `backend/src/test/java/com/nbamanager/service/ChatCleanupServiceTest.java` | 新增 |

## 不包含

- 不实现归档表
- 不实现消息压缩
- 不实现按比赛分表
- 不实现 Redis 缓存消息
