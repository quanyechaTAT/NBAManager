# 聊天消息定时清理 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现聊天消息定时清理功能，自动删除超过保留期的消息，防止数据库无限增长。

**架构：** Spring `@Scheduled` 定时任务 + Redis 分布式锁 + MySQL 批量物理删除。单批删除拆到独立 Bean 避免事务自调用失效。

**技术栈：** Spring Boot 3.2.5, Spring Data JPA, Spring Data Redis, MySQL, JUnit 5 + Mockito

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `backend/src/main/java/com/nbamanager/config/SchedulingConfig.java` | 启用 Spring 调度 |
| `backend/src/main/java/com/nbamanager/service/ChatCleanupBatchService.java` | 单批删除（独立 Bean，REQUIRES_NEW 事务） |
| `backend/src/main/java/com/nbamanager/service/ChatCleanupService.java` | 调度入口：分布式锁、循环、异常控制 |
| `backend/src/main/java/com/nbamanager/repository/ChatMessageRepository.java` | 新增 `deleteBatchBefore` 原生查询 |
| `backend/src/main/java/com/nbamanager/domain/ChatMessage.java` | 新增 `createdAt,id` 复合索引 |
| `backend/src/main/resources/application.yml` | 新增 `app.chat.*` 配置 |
| `backend/src/test/java/com/nbamanager/service/ChatCleanupServiceTest.java` | 调度逻辑单元测试 |
| `backend/src/test/java/com/nbamanager/service/ChatCleanupBatchServiceTest.java` | 批量删除单元测试 |

---

### 任务 1：配置项

**文件：**
- 修改：`backend/src/main/resources/application.yml`

- [ ] **步骤 1：添加 app.chat 配置块**

在 `application.yml` 的 `app:` 下追加：

```yaml
  chat:
    cleanup-enabled: ${CHAT_CLEANUP_ENABLED:true}
    retention-days: ${CHAT_RETENTION_DAYS:180}
    cleanup-batch-size: ${CHAT_CLEANUP_BATCH_SIZE:1000}
    cleanup-cron: ${CHAT_CLEANUP_CRON:0 0 3 * * ?}
    cleanup-max-batches: ${CHAT_CLEANUP_MAX_BATCHES:500}
```

- [ ] **步骤 2：验证配置加载**

运行：`cd backend && DB_PASSWORD=[REMOVED_DB_PASSWORD] ./mvnw.cmd compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "feat: add chat cleanup configuration"
```

---

### 任务 2：启用调度

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/config/SchedulingConfig.java`

- [ ] **步骤 1：创建 SchedulingConfig**

```java
package com.nbamanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

- [ ] **步骤 2：验证编译**

运行：`cd backend && ./mvnw.cmd compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/nbamanager/config/SchedulingConfig.java
git commit -m "feat: add SchedulingConfig with @EnableScheduling"
```

---

### 任务 3：Repository 批量删除方法

**文件：**
- 修改：`backend/src/main/java/com/nbamanager/repository/ChatMessageRepository.java`
- 测试：`backend/src/test/java/com/nbamanager/service/ChatCleanupBatchServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.nbamanager.repository.ChatMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatCleanupBatchServiceTest {

    @InjectMocks
    private ChatCleanupBatchService batchService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Test
    void deleteOneBatchReturnsDeletedCount() {
        Instant cutoff = Instant.now().minus(180, ChronoUnit.DAYS);
        when(chatMessageRepository.deleteBatchBefore(any(Instant.class), anyInt())).thenReturn(500);

        int deleted = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(deleted).isEqualTo(500);
    }

    @Test
    void deleteOneBatchReturnsZeroWhenNothingToDelete() {
        Instant cutoff = Instant.now().minus(180, ChronoUnit.DAYS);
        when(chatMessageRepository.deleteBatchBefore(any(Instant.class), anyInt())).thenReturn(0);

        int deleted = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(deleted).isZero();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd backend && ./mvnw.cmd test -Dtest=ChatCleanupBatchServiceTest -DfailIfNoTests=false`
预期：FAIL，编译错误 `ChatCleanupBatchService` 不存在

- [ ] **步骤 3：添加 deleteBatchBefore 到 Repository**

在 `ChatMessageRepository.java` 中添加（在现有方法之后）：

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

- [ ] **步骤 4：创建 ChatCleanupBatchService**

```java
package com.nbamanager.service;

import com.nbamanager.repository.ChatMessageRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

- [ ] **步骤 5：运行测试验证通过**

运行：`cd backend && ./mvnw.cmd test -Dtest=ChatCleanupBatchServiceTest -DfailIfNoTests=false`
预期：PASS

- [ ] **步骤 6：Commit**

```bash
git add backend/src/main/java/com/nbamanager/repository/ChatMessageRepository.java
git add backend/src/main/java/com/nbamanager/service/ChatCleanupBatchService.java
git add backend/src/test/java/com/nbamanager/service/ChatCleanupBatchServiceTest.java
git commit -m "feat: add ChatCleanupBatchService with deleteBatchBefore query"
```

---

### 任务 4：实体索引

**文件：**
- 修改：`backend/src/main/java/com/nbamanager/domain/ChatMessage.java`

- [ ] **步骤 1：添加复合索引**

修改 `@Table` 注解，添加 `idx_chat_created_id`：

```java
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_game_time", columnList = "gameId,createdAt"),
        @Index(name = "idx_chat_game_id", columnList = "gameId"),
        @Index(name = "idx_chat_created_id", columnList = "createdAt,id")
})
```

- [ ] **步骤 2：验证编译**

运行：`cd backend && ./mvnw.cmd compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/nbamanager/domain/ChatMessage.java
git commit -m "feat: add createdAt,id composite index on ChatMessage"
```

---

### 任务 5：调度服务

**文件：**
- 创建：`backend/src/main/java/com/nbamanager/service/ChatCleanupService.java`
- 测试：`backend/src/test/java/com/nbamanager/service/ChatCleanupServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatCleanupServiceTest {

    @InjectMocks
    private ChatCleanupService cleanupService;

    @Mock
    private ChatCleanupBatchService batchService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cleanupService, "enabled", true);
        ReflectionTestUtils.setField(cleanupService, "retentionDays", 180);
        ReflectionTestUtils.setField(cleanupService, "batchSize", 1000);
        ReflectionTestUtils.setField(cleanupService, "maxBatches", 500);
    }

    @Test
    void skipsExecutionWhenDisabled() {
        ReflectionTestUtils.setField(cleanupService, "enabled", false);

        cleanupService.cleanupExpiredMessages();

        verifyNoInteractions(batchService);
    }

    @Test
    void skipsExecutionWhenLockNotAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        cleanupService.cleanupExpiredMessages();

        verifyNoInteractions(batchService);
    }

    @Test
    void deletesInBatchesUntilEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenReturn(500)
                .thenReturn(200)
                .thenReturn(0);

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(3)).deleteOneBatch(any(Instant.class), eq(1000));
        verify(redisTemplate).delete("nba:chat:cleanup:lock");
    }

    @Test
    void stopsOnBatchFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenReturn(500)
                .thenThrow(new RuntimeException("DB error"));

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(2)).deleteOneBatch(any(Instant.class), eq(1000));
        verify(redisTemplate).delete("nba:chat:cleanup:lock");
    }

    @Test
    void releasesLockOnException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        cleanupService.cleanupExpiredMessages();

        verify(redisTemplate).delete("nba:chat:cleanup:lock");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd backend && ./mvnw.cmd test -Dtest=ChatCleanupServiceTest -DfailIfNoTests=false`
预期：FAIL，编译错误 `ChatCleanupService` 不存在

- [ ] **步骤 3：创建 ChatCleanupService**

```java
package com.nbamanager.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

- [ ] **步骤 4：运行测试验证通过**

运行：`cd backend && ./mvnw.cmd test -Dtest=ChatCleanupServiceTest -DfailIfNoTests=false`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/ChatCleanupService.java
git add backend/src/test/java/com/nbamanager/service/ChatCleanupServiceTest.java
git commit -m "feat: add ChatCleanupService with distributed lock and batch cleanup"
```

---

### 任务 6：全量验证

- [ ] **步骤 1：运行全部测试**

运行：`cd backend && DB_PASSWORD=[REMOVED_DB_PASSWORD] ./mvnw.cmd test -DfailIfNoTests=false`
预期：全部 PASS（允许 MatchDetailServiceTest 已知失败）

- [ ] **步骤 2：前端构建**

运行：`cd frontend && npm run build`
预期：`✓ built in ...`

- [ ] **步骤 3：最终 Commit**

```bash
git add -A
git commit -m "feat: chat message cleanup - complete implementation"
```
