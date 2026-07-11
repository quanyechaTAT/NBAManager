# 聊天消息定时清理设计

日期：2026-07-11

## 背景

多人聊天室的消息存储在 MySQL `chat_messages` 表中，无自动过期机制。随着 NBA 赛季推进（常规赛 ~1230 场 + 季后赛），每场比赛可能产生数百条消息，跨赛季累积将导致表持续增长，占用数据库空间并影响查询性能。

## 目标

- 消息保留一个赛季（约 180 天），过期后自动物理删除
- 清理过程不影响正常聊天业务
- 保留天数可配置

## 方案

### 配置

`application.yml` 新增：

```yaml
app:
  chat:
    retention-days: ${CHAT_RETENTION_DAYS:180}
    cleanup-batch-size: ${CHAT_CLEANUP_BATCH_SIZE:1000}
    cleanup-cron: ${CHAT_CLEANUP_CRON:0 0 3 * * ?}
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `retention-days` | 180 | 消息保留天数 |
| `cleanup-batch-size` | 1000 | 每批删除条数 |
| `cleanup-cron` | `0 0 3 * * ?` | 定时表达式，默认每天凌晨 3 点 |

### ChatCleanupService

新建 `com.nbamanager.service.ChatCleanupService`：

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatCleanupService {

    @Value("${app.chat.retention-days:180}")
    private int retentionDays;

    @Value("${app.chat.cleanup-batch-size:1000}")
    private int batchSize;

    private final ChatMessageRepository chatMessageRepository;

    @Scheduled(cron = "${app.chat.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupExpiredMessages() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("开始清理{}天前的聊天消息, cutoff={}", retentionDays, cutoff);

        int totalDeleted = 0;
        int deleted;
        do {
            var page = PageRequest.of(0, batchSize);
            deleted = chatMessageRepository.deleteBatchBefore(cutoff, page);
            totalDeleted += deleted;
            log.debug("本批删除{}条", deleted);
        } while (deleted > 0);

        log.info("聊天消息清理完成, 共删除{}条", totalDeleted);
    }
}
```

### Repository 新增方法

在 `ChatMessageRepository` 中添加：

```java
@Modifying
@Query(value = "DELETE FROM chat_messages WHERE id IN (SELECT id FROM chat_messages WHERE created_at < :cutoff LIMIT :limit)", nativeQuery = true)
int deleteBatchBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
```

使用原生 SQL + 子查询 + LIMIT 实现批量删除，避免一次 DELETE 大量行导致长事务和锁表。

### 软删除消息处理

管理员软删除的消息（`deleted = true`）与正常消息使用相同的保留策略，到期后一并物理删除，不做特殊处理。

### 异常处理

- 每批删除在 try-catch 中执行，异常记录日志后继续下一批
- 清理任务的异常不会传播到调度器，不影响主业务
- 若某批删除失败，已成功删除的批次不受影响

### 安全约束

- `clientMessageId` 去重约束仅在消息发送时生效（`findByUserIdAndClientMessageId`），历史清理不受影响
- 物理删除不会触发软删除的广播逻辑（不走 `softDelete` 方法）
- 清理任务不删除 `deletedAt` 和 `deletedBy` 字段（这些随行一起删除）

## 依赖

- Spring `@EnableScheduling`（需确认已启用或新增配置）
- `ChatMessageRepository`（已有）

## 文件清单

| 文件 | 操作 |
|------|------|
| `backend/src/main/java/com/nbamanager/service/ChatCleanupService.java` | 新增 |
| `backend/src/main/java/com/nbamanager/repository/ChatMessageRepository.java` | 修改（添加 `deleteBatchBefore`） |
| `backend/src/main/resources/application.yml` | 修改（添加 `app.chat.*` 配置） |

## 不包含

- 不实现归档表
- 不实现消息压缩
- 不实现按比赛分表
- 不实现 Redis 缓存消息
