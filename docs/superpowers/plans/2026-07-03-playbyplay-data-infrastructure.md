# PlayByPlay 数据基础设施 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 建立统一的 Play-by-Play 数据采集基础设施，包含 Provider 抽象、Collector、Scheduler、扩展表结构，为后续 AI Replay 和 LangGraph Copilot 提供可靠数据源。

**架构：** Java Provider 层调用 Python 脚本获取原始 Play-by-Play 数据，Java 负责标准化、校验、存库。Provider Manager 按优先级 failover（NBA → ESPN）。Spring @Scheduled 定时任务自动采集已结束比赛。不破坏现有表结构和代码。

**技术栈：** Spring Boot 3, JPA/Hibernate, MySQL, Python nba_api, ESPN API, JUnit 5, Mockito

---

## 文件结构

### 新增文件

| 文件 | 职责 |
|------|------|
| `backend/src/main/resources/db/migration/V1__extend_play_by_play.sql` | ALTER TABLE 扩展 play_by_play |
| `domain/PlayByPlaySyncLog.java` | 同步日志实体 |
| `domain/GameMomentumSnapshot.java` | 比赛走势快照实体 |
| `domain/GameTurningPoint.java` | 转折点实体 |
| `domain/GameHighlight.java` | 亮点事件实体 |
| `domain/AiReplayExplanation.java` | AI 解释缓存实体（只建表） |
| `domain/AiGameSummary.java` | AI 总结缓存实体（只建表） |
| `domain/AiConversationMemory.java` | 对话记忆实体（只建表） |
| `domain/provider/ProviderType.java` | 数据源类型枚举 |
| `domain/provider/PlayByPlayFetchResult.java` | Provider 返回值对象 |
| `domain/provider/PlayByPlayEvent.java` | 标准化事件值对象 |
| `domain/provider/PlayByPlayFetchOptions.java` | 采集选项 |
| `domain/provider/PlayByPlayProvider.java` | Provider 接口 |
| `repository/PlayByPlaySyncLogRepository.java` | 同步日志仓库 |
| `repository/GameMomentumSnapshotRepository.java` | 走势快照仓库 |
| `repository/GameTurningPointRepository.java` | 转折点仓库 |
| `repository/GameHighlightRepository.java` | 亮点仓库 |
| `repository/AiReplayExplanationRepository.java` | AI 解释仓库 |
| `repository/AiGameSummaryRepository.java` | AI 总结仓库 |
| `repository/AiConversationMemoryRepository.java` | 对话记忆仓库 |
| `service/provider/PlayByPlayProviderManager.java` | Provider 管理器 |
| `service/provider/NbaPlayByPlayProvider.java` | NBA 数据源 Provider |
| `service/provider/EspnPlayByPlayProvider.java` | ESPN 数据源 Provider |
| `service/PlayByPlayCollectorService.java` | 采集编排服务 |
| `service/PlayByPlayNormalizeService.java` | 标准化服务 |
| `service/PlayByPlayValidationService.java` | 校验服务 |
| `service/MomentumComputeService.java` | 走势计算服务 |
| `service/HighlightDetectService.java` | 亮点识别服务 |
| `service/TurningPointDetectService.java` | 转折点识别服务 |
| `service/PlayByPlaySyncLogService.java` | 同步日志服务 |
| `service/PlayByPlaySyncScheduler.java` | 定时任务调度器 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `domain/PlayByPlay.java` | 新增字段映射（source, sourceEventId, eventOrder 等） |
| `web/dto/PlayByPlayDto.java` | 新增字段（source, eventType, actionType, teamName 等） |
| `web/MatchDetailController.java` | 新增 sync 和 sync-status 接口 |
| `scripts/nba_data_fetcher.py` | 新增 espn_playbyplay action |

### 测试文件

| 文件 | 测试内容 |
|------|----------|
| `service/provider/PlayByPlayProviderManagerTest.java` | failover 逻辑 |
| `service/provider/NbaPlayByPlayProviderTest.java` | JSON 解析、字段映射 |
| `service/PlayByPlayCollectorServiceTest.java` | 完整采集流程 |
| `service/PlayByPlayNormalizeServiceTest.java` | 标准化映射 |
| `service/MomentumComputeServiceTest.java` | 分差计算 |

---

## 任务 1：PlayByPlay 实体扩展 + SQL 迁移

**文件：**
- 创建：`backend/src/main/resources/db/migration/V1__extend_play_by_play.sql`
- 修改：`backend/src/main/java/com/nbamanager/domain/PlayByPlay.java`

- [ ] **步骤 1：编写 SQL 迁移脚本**

```sql
-- V1__extend_play_by_play.sql
-- 扩展 play_by_play 表，所有变更均为 ADD COLUMN，不修改现有列

ALTER TABLE play_by_play
  ADD COLUMN source VARCHAR(40) DEFAULT 'NBA_PLAY_BY_PLAY',
  ADD COLUMN source_event_id VARCHAR(80),
  ADD COLUMN event_order INT,
  ADD COLUMN action_type VARCHAR(80),
  ADD COLUMN team_name VARCHAR(120),
  ADD COLUMN score_margin INT,
  ADD COLUMN is_scoring_play BOOLEAN DEFAULT FALSE,
  ADD COLUMN is_highlight_candidate BOOLEAN DEFAULT FALSE,
  ADD COLUMN raw_payload_json JSON,
  ADD COLUMN raw_payload_hash VARCHAR(64),
  ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE INDEX idx_pbp_game_order ON play_by_play(game_id, period, event_order);
CREATE INDEX idx_pbp_game_player ON play_by_play(game_id, player_id);
CREATE INDEX idx_pbp_game_type ON play_by_play(game_id, event_type);
CREATE INDEX idx_pbp_source_event ON play_by_play(source, source_event_id);
```

- [ ] **步骤 2：扩展 PlayByPlay 实体**

在 `PlayByPlay.java` 中新增字段（不修改现有字段）：

```java
// 在 teamId 字段之后添加：

@Column(length = 40)
private String source;

@Column(length = 80)
private String sourceEventId;

private Integer eventOrder;

@Column(length = 80)
private String actionType;

@Column(length = 120)
private String teamName;

private Integer scoreMargin;

private Boolean isScoringPlay;

private Boolean isHighlightCandidate;

@Column(columnDefinition = "JSON")
private String rawPayloadJson;

@Column(length = 64)
private String rawPayloadHash;

private LocalDateTime createdAt;

private LocalDateTime updatedAt;
```

需要在文件头部添加 `import java.time.LocalDateTime;`。

- [ ] **步骤 3：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add backend/src/main/resources/db/migration/V1__extend_play_by_play.sql
git add backend/src/main/java/com/nbamanager/domain/PlayByPlay.java
git commit -m "feat: extend PlayByPlay entity with source, eventOrder, actionType fields
```

---

## 任务 2：新增 7 个 Entity + Repository

**文件：**
- 创建：`domain/PlayByPlaySyncLog.java`
- 创建：`domain/GameMomentumSnapshot.java`
- 创建：`domain/GameTurningPoint.java`
- 创建：`domain/GameHighlight.java`
- 创建：`domain/AiReplayExplanation.java`
- 创建：`domain/AiGameSummary.java`
- 创建：`domain/AiConversationMemory.java`
- 创建：`repository/PlayByPlaySyncLogRepository.java`
- 创建：`repository/GameMomentumSnapshotRepository.java`
- 创建：`repository/GameTurningPointRepository.java`
- 创建：`repository/GameHighlightRepository.java`
- 创建：`repository/AiReplayExplanationRepository.java`
- 创建：`repository/AiGameSummaryRepository.java`
- 创建：`repository/AiConversationMemoryRepository.java`

- [ ] **步骤 1：创建 PlayByPlaySyncLog 实体**

```java
package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "play_by_play_sync_log")
@Getter
@Setter
@NoArgsConstructor
public class PlayByPlaySyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(nullable = false, length = 20)
    private String status;

    private int attemptCount;

    @Column(length = 500)
    private String errorMessage;

    private int eventCount;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime nextRetryAt;
}
```

- [ ] **步骤 2：创建 GameMomentumSnapshot 实体**

```java
package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_momentum_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class GameMomentumSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false)
    private int eventOrder;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false)
    private int homeScore;

    @Column(nullable = false)
    private int awayScore;

    @Column(nullable = false)
    private int scoreMargin;

    private Long leaderTeamId;

    private LocalDateTime createdAt;
}
```

- [ ] **步骤 3：创建 GameTurningPoint 实体**

```java
package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_turning_point")
@Getter
@Setter
@NoArgsConstructor
public class GameTurningPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    private Long eventId;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false, length = 40)
    private String type;

    private double confidence;

    @Column(length = 500)
    private String factualReason;

    @Column(columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(length = 500)
    private String citedEventIdsJson;

    private LocalDateTime createdAt;
}
```

- [ ] **步骤 4：创建 GameHighlight 实体**

```java
package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_highlight")
@Getter
@Setter
@NoArgsConstructor
public class GameHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    private Long eventId;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false, length = 40)
    private String highlightType;

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    private Long playerId;
    private Long teamId;

    @Column(length = 20)
    private String scoreAfter;

    private LocalDateTime createdAt;
}
```

- [ ] **步骤 5：创建 AiReplayExplanation 实体**

```java
package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_replay_explanation")
@Getter
@Setter
@NoArgsConstructor
public class AiReplayExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false)
    private Long eventId;

    @Column(length = 40)
    private String agentName;

    @Column(length = 40)
    private String modelProvider;

    @Column(length = 60)
    private String modelName;

    @Column(length = 20)
    private String promptVersion;

    @Column(length = 64)
    private String factsHash;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 500)
    private String citedEventIdsJson;

    @Column(length = 500)
    private String citedStatKeysJson;

    private LocalDateTime createdAt;
}
```

- [ ] **步骤 6：创建 AiGameSummary 实体**

```java
package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_game_summary")
@Getter
@Setter
@NoArgsConstructor
public class AiGameSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false, length = 40)
    private String summaryType;

    @Column(length = 40)
    private String modelProvider;

    @Column(length = 60)
    private String modelName;

    @Column(length = 20)
    private String promptVersion;

    @Column(length = 64)
    private String factsHash;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String citedEventIdsJson;

    @Column(length = 500)
    private String citedStatKeysJson;

    private LocalDateTime createdAt;
}
```

- [ ] **步骤 7：创建 AiConversationMemory 实体**

```java
package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_conversation_memory")
@Getter
@Setter
@NoArgsConstructor
public class AiConversationMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(length = 16)
    private String activeGameId;

    private Long activePlayerId;
    private Long activeTeamId;

    @Column(length = 10)
    private String activeSeason;

    @Column(length = 40)
    private String lastIntent;

    @Column(columnDefinition = "TEXT")
    private String memorySummary;

    @Column(columnDefinition = "JSON")
    private String stateJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 8：创建 7 个 Repository**

```java
// PlayByPlaySyncLogRepository.java
package com.nbamanager.repository;

import com.nbamanager.domain.PlayByPlaySyncLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayByPlaySyncLogRepository extends JpaRepository<PlayByPlaySyncLog, Long> {
    Optional<PlayByPlaySyncLog> findFirstByGameIdOrderByStartedAtDesc(String gameId);
    List<PlayByPlaySyncLog> findByStatusAndNextRetryAtBefore(String status, LocalDateTime now);
    List<PlayByPlaySyncLog> findByStatusAndStartedAtAfter(String status, LocalDateTime since);
}
```

```java
// GameMomentumSnapshotRepository.java
package com.nbamanager.repository;

import com.nbamanager.domain.GameMomentumSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameMomentumSnapshotRepository extends JpaRepository<GameMomentumSnapshot, Long> {
    List<GameMomentumSnapshot> findByGameIdOrderByEventOrderAsc(String gameId);
    void deleteByGameId(String gameId);
}
```

```java
// GameTurningPointRepository.java
package com.nbamanager.repository;

import com.nbamanager.domain.GameTurningPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameTurningPointRepository extends JpaRepository<GameTurningPoint, Long> {
    List<GameTurningPoint> findByGameIdOrderByPeriodAscGameClockDesc(String gameId);
    void deleteByGameId(String gameId);
}
```

```java
// GameHighlightRepository.java
package com.nbamanager.repository;

import com.nbamanager.domain.GameHighlight;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameHighlightRepository extends JpaRepository<GameHighlight, Long> {
    List<GameHighlight> findByGameIdOrderByPeriodAscGameClockDesc(String gameId);
    void deleteByGameId(String gameId);
}
```

```java
// AiReplayExplanationRepository.java
package com.nbamanager.repository;

import com.nbamanager.domain.AiReplayExplanation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReplayExplanationRepository extends JpaRepository<AiReplayExplanation, Long> {
    Optional<AiReplayExplanation> findByGameIdAndEventId(String gameId, Long eventId);
}
```

```java
// AiGameSummaryRepository.java
package com.nbamanager.repository;

import com.nbamanager.domain.AiGameSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGameSummaryRepository extends JpaRepository<AiGameSummary, Long> {
    Optional<AiGameSummary> findByGameIdAndSummaryType(String gameId, String summaryType);
}
```

```java
// AiConversationMemoryRepository.java
package com.nbamanager.repository;

import com.nbamanager.domain.AiConversationMemory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationMemoryRepository extends JpaRepository<AiConversationMemory, Long> {
    Optional<AiConversationMemory> findByConversationId(String conversationId);
}
```

- [ ] **步骤 9：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 10：Commit**

```bash
git add backend/src/main/java/com/nbamanager/domain/PlayByPlaySyncLog.java
git add backend/src/main/java/com/nbamanager/domain/GameMomentumSnapshot.java
git add backend/src/main/java/com/nbamanager/domain/GameTurningPoint.java
git add backend/src/main/java/com/nbamanager/domain/GameHighlight.java
git add backend/src/main/java/com/nbamanager/domain/AiReplayExplanation.java
git add backend/src/main/java/com/nbamanager/domain/AiGameSummary.java
git add backend/src/main/java/com/nbamanager/domain/AiConversationMemory.java
git add backend/src/main/java/com/nbamanager/repository/
git commit -m "feat: add 7 new entities and repositories for PlayByPlay infrastructure
```

---

## 任务 3：Provider 接口 + 枚举 + 值对象

**文件：**
- 创建：`domain/provider/ProviderType.java`
- 创建：`domain/provider/PlayByPlayFetchResult.java`
- 创建：`domain/provider/PlayByPlayEvent.java`
- 创建：`domain/provider/PlayByPlayFetchOptions.java`
- 创建：`domain/provider/PlayByPlayProvider.java`

- [ ] **步骤 1：创建 ProviderType 枚举**

```java
package com.nbamanager.domain.provider;

public enum ProviderType {
    NBA_PLAY_BY_PLAY,
    ESPN_PLAY_BY_PLAY
}
```

- [ ] **步骤 2：创建 PlayByPlayEvent 值对象**

```java
package com.nbamanager.domain.provider;

public record PlayByPlayEvent(
        String sourceEventId,
        int period,
        String gameClock,
        String eventType,
        String actionType,
        String description,
        String playerName,
        Long playerId,
        String teamName,
        Long teamId,
        int homeScore,
        int awayScore,
        int scoreMargin,
        boolean isScoringPlay,
        boolean isHighlightCandidate,
        int eventOrder,
        String rawPayloadJson
) {}
```

- [ ] **步骤 3：创建 PlayByPlayFetchResult 值对象**

```java
package com.nbamanager.domain.provider;

import java.util.List;

public record PlayByPlayFetchResult(
        boolean success,
        ProviderType source,
        List<PlayByPlayEvent> events,
        String rawPayloadJson,
        String errorMessage,
        long fetchTimeMs
) {
    public static PlayByPlayFetchResult failure(ProviderType source, String error, long timeMs) {
        return new PlayByPlayFetchResult(false, source, List.of(), null, error, timeMs);
    }

    public static PlayByPlayFetchResult success(ProviderType source, List<PlayByPlayEvent> events,
                                                 String rawJson, long timeMs) {
        return new PlayByPlayFetchResult(true, source, events, rawJson, null, timeMs);
    }
}
```

- [ ] **步骤 4：创建 PlayByPlayFetchOptions 值对象**

```java
package com.nbamanager.domain.provider;

public record PlayByPlayFetchOptions(
        Integer period,
        boolean includeRawPayload
) {
    public static PlayByPlayFetchOptions defaults() {
        return new PlayByPlayFetchOptions(null, true);
    }
}
```

- [ ] **步骤 5：创建 PlayByPlayProvider 接口**

```java
package com.nbamanager.domain.provider;

public interface PlayByPlayProvider {
    ProviderType type();
    boolean supports(String gameId);
    PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options);
}
```

- [ ] **步骤 6：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 7：Commit**

```bash
git add backend/src/main/java/com/nbamanager/domain/provider/
git commit -m "feat: add PlayByPlay provider interface, enums, and value objects
```

---

## 任务 4：NbaPlayByPlayProvider

**文件：**
- 创建：`service/provider/NbaPlayByPlayProvider.java`

- [ ] **步骤 1：编写失败的测试**

创建 `backend/src/test/java/com/nbamanager/service/provider/NbaPlayByPlayProviderTest.java`：

```java
package com.nbamanager.service.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NbaPlayByPlayProviderTest {

    private final NbaPlayByPlayProvider provider = new NbaPlayByPlayProvider();

    @Test
    void type_returnsNbaPlayByPlay() {
        assertThat(provider.type()).isEqualTo(ProviderType.NBA_PLAY_BY_PLAY);
    }

    @Test
    void supports_returnsTrueForValidGameId() {
        assertThat(provider.supports("0042500226")).isTrue();
    }

    @Test
    void supports_returnsFalseForNull() {
        assertThat(provider.supports(null)).isFalse();
    }

    @Test
    void parseEvents_mapsFieldsCorrectly() {
        // 构造模拟的 Python JSON 输出
        JSONArray events = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 1);
        event.put("gameClock", "08:52");
        event.put("description", "Donovan Mitchell makes 3-point jump shot");
        event.put("homeScore", 25);
        event.put("awayScore", 20);
        event.put("eventType", "3PT Jump Shot");
        event.put("playerId", 1628378);
        event.put("playerName", "Donovan Mitchell");
        event.put("teamId", 1610612739);
        events.put(event);

        List<PlayByPlayEvent> result = provider.parseEvents(events, "NBA_PLAY_BY_PLAY");

        assertThat(result).hasSize(1);
        PlayByPlayEvent e = result.get(0);
        assertThat(e.period()).isEqualTo(1);
        assertThat(e.gameClock()).isEqualTo("08:52");
        assertThat(e.homeScore()).isEqualTo(25);
        assertThat(e.awayScore()).isEqualTo(20);
        assertThat(e.eventType()).isEqualTo("3PT Jump Shot");
        assertThat(e.playerName()).isEqualTo("Donovan Mitchell");
        assertThat(e.scoreMargin()).isEqualTo(5);
        assertThat(e.isScoringPlay()).isTrue();
        assertThat(e.eventOrder()).isEqualTo(10532); // 1*10000 + 532
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd backend && ./mvnw test -pl . -Dtest=NbaPlayByPlayProviderTest -q`
预期：FAIL，NbaPlayByPlayProvider 类不存在

- [ ] **步骤 3：编写实现代码**

创建 `backend/src/main/java/com/nbamanager/service/provider/NbaPlayByPlayProvider.java`：

```java
package com.nbamanager.service.provider;

import com.nbamanager.config.EnvFileReader;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayProvider;
import com.nbamanager.domain.provider.ProviderType;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NbaPlayByPlayProvider implements PlayByPlayProvider {

    @Override
    public ProviderType type() {
        return ProviderType.NBA_PLAY_BY_PLAY;
    }

    @Override
    public boolean supports(String gameId) {
        return gameId != null && !gameId.isEmpty();
    }

    @Override
    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        long start = System.currentTimeMillis();
        try {
            String scriptPath = findScriptPath();
            log.info("NBA Provider: fetching playbyplay for gameId={}", gameId);

            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "playbyplay", gameId);
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            String proxyHost = EnvFileReader.get("NBA_PROXY_HOST");
            String proxyPort = EnvFileReader.get("NBA_PROXY_PORT");
            if (proxyHost != null && !proxyHost.isEmpty()) {
                pb.environment().put("NBA_PROXY_HOST", proxyHost);
            }
            if (proxyPort != null && !proxyPort.isEmpty()) {
                pb.environment().put("NBA_PROXY_PORT", proxyPort);
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            long elapsed = System.currentTimeMillis() - start;

            if (exitCode != 0) {
                return PlayByPlayFetchResult.failure(type(),
                        "Python script exited with code " + exitCode, elapsed);
            }

            String json = output.toString().trim();
            if (json.isEmpty()) {
                return PlayByPlayFetchResult.failure(type(), "Empty output from Python", elapsed);
            }

            JSONArray eventsArray = new JSONArray(json);
            List<PlayByPlayEvent> events = parseEvents(eventsArray, type().name());
            log.info("NBA Provider: fetched {} events for gameId={}", events.size(), gameId);

            return PlayByPlayFetchResult.success(type(), events, json, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("NBA Provider failed for gameId={}: {}", gameId, e.getMessage());
            return PlayByPlayFetchResult.failure(type(), e.getMessage(), elapsed);
        }
    }

    List<PlayByPlayEvent> parseEvents(JSONArray eventsArray, String sourceName) {
        List<PlayByPlayEvent> events = new ArrayList<>();
        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject obj = eventsArray.getJSONObject(i);
            int period = obj.optInt("period", 0);
            String gameClock = obj.optString("gameClock", "12:00");
            String eventType = obj.optString("eventType", "");
            String description = obj.optString("description", "");
            int homeScore = obj.optInt("homeScore", 0);
            int awayScore = obj.optInt("awayScore", 0);

            int eventOrder = period * 10000 + gameClockToSeconds(gameClock);
            int scoreMargin = homeScore - awayScore;
            boolean isScoringPlay = detectScoringPlay(eventType, description);
            boolean isHighlightCandidate = detectHighlight(eventType, description);

            String sourceEventId = obj.optString("gameId", "") + "_" + period + "_" + gameClock + "_" + i;

            events.add(new PlayByPlayEvent(
                    sourceEventId,
                    period,
                    gameClock,
                    eventType,
                    obj.optString("actionType", eventType),
                    description,
                    obj.optString("playerName", ""),
                    obj.has("playerId") ? obj.optLong("playerId") : null,
                    obj.optString("teamName", ""),
                    obj.has("teamId") ? obj.optLong("teamId") : null,
                    homeScore,
                    awayScore,
                    scoreMargin,
                    isScoringPlay,
                    isHighlightCandidate,
                    eventOrder,
                    obj.toString()
            ));
        }
        return events;
    }

    static int gameClockToSeconds(String gameClock) {
        if (gameClock == null || gameClock.isEmpty()) return 0;
        String[] parts = gameClock.split(":");
        if (parts.length != 2) return 0;
        try {
            int minutes = Integer.parseInt(parts[0].trim());
            int seconds = Integer.parseInt(parts[1].trim());
            return minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean detectScoringPlay(String eventType, String description) {
        if (eventType == null) return false;
        String lower = eventType.toLowerCase();
        boolean isShot = lower.contains("jump shot") || lower.contains("layup")
                || lower.contains("dunk") || lower.contains("free throw")
                || lower.contains("hook shot") || lower.contains("tip-in");
        return isShot && description != null && description.contains("PTS");
    }

    static boolean detectHighlight(String eventType, String description) {
        if (eventType == null) return false;
        String lower = eventType.toLowerCase();
        boolean isHighlightType = lower.contains("dunk") || lower.contains("block")
                || lower.contains("3pt") || lower.contains("steal");
        return isHighlightType && description != null
                && (description.contains("makes") || description.contains("BLOCK")
                    || description.contains("STEAL"));
    }

    private String findScriptPath() {
        String[] candidates = {
            "backend/scripts/nba_data_fetcher.py",
            "scripts/nba_data_fetcher.py",
            "nba_data_fetcher.py"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) return f.getAbsolutePath();
        }
        return "backend/scripts/nba_data_fetcher.py";
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd backend && ./mvnw test -pl . -Dtest=NbaPlayByPlayProviderTest -q`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/provider/NbaPlayByPlayProvider.java
git add backend/src/test/java/com/nbamanager/service/provider/NbaPlayByPlayProviderTest.java
git commit -m "feat: add NbaPlayByPlayProvider with Python script integration
```

---

## 任务 5：PlayByPlayNormalizeService

**文件：**
- 创建：`service/PlayByPlayNormalizeService.java`
- 创建：`backend/src/test/java/com/nbamanager/service/PlayByPlayNormalizeServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.ProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayByPlayNormalizeServiceTest {

    private final PlayByPlayNormalizeService service = new PlayByPlayNormalizeService();

    @Test
    void normalize_mapsEventToEntity() {
        PlayByPlayEvent event = new PlayByPlayEvent(
                "evt_1", 1, "08:52", "3PT Jump Shot", "3PT Jump Shot",
                "Mitchell makes 3PT", "Donovan Mitchell", 1628378L,
                "Cavaliers", 1610612739L, 25, 20, 5, true, true, 10532, "{}"
        );

        PlayByPlay result = service.normalize(event, "0042500226", ProviderType.NBA_PLAY_BY_PLAY);

        assertThat(result.getGameId()).isEqualTo("0042500226");
        assertThat(result.getPeriod()).isEqualTo(1);
        assertThat(result.getGameClock()).isEqualTo("08:52");
        assertThat(result.getHomeScore()).isEqualTo(25);
        assertThat(result.getAwayScore()).isEqualTo(20);
        assertThat(result.getSource()).isEqualTo("NBA_PLAY_BY_PLAY");
        assertThat(result.getSourceEventId()).isEqualTo("evt_1");
        assertThat(result.getEventOrder()).isEqualTo(10532);
        assertThat(result.getIsScoringPlay()).isTrue();
        assertThat(result.getScoreMargin()).isEqualTo(5);
    }

    @Test
    void normalize_listPreservesOrder() {
        PlayByPlayEvent e1 = new PlayByPlayEvent(
                "evt_1", 1, "08:52", "Jump Shot", "Jump Shot", "desc1",
                "Player A", 1L, "Team A", 10L, 25, 20, 5, true, false, 10532, "{}"
        );
        PlayByPlayEvent e2 = new PlayByPlayEvent(
                "evt_2", 1, "07:30", "Dunk", "Dunk", "desc2",
                "Player B", 2L, "Team B", 20L, 27, 20, 7, true, true, 10450, "{}"
        );

        List<PlayByPlay> result = service.normalizeAll(List.of(e1, e2), "0042500226", ProviderType.NBA_PLAY_BY_PLAY);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSourceEventId()).isEqualTo("evt_1");
        assertThat(result.get(1).getSourceEventId()).isEqualTo("evt_2");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd backend && ./mvnw test -pl . -Dtest=PlayByPlayNormalizeServiceTest -q`
预期：FAIL

- [ ] **步骤 3：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.ProviderType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlayByPlayNormalizeService {

    public PlayByPlay normalize(PlayByPlayEvent event, String gameId, ProviderType source) {
        PlayByPlay entity = new PlayByPlay();
        entity.setGameId(gameId);
        entity.setPeriod(event.period());
        entity.setGameClock(event.gameClock());
        entity.setDescription(event.description());
        entity.setHomeScore(event.homeScore());
        entity.setAwayScore(event.awayScore());
        entity.setEventType(event.eventType());
        entity.setPlayerId(event.playerId());
        entity.setPlayerName(event.playerName());
        entity.setTeamId(event.teamId());
        entity.setSource(source.name());
        entity.setSourceEventId(event.sourceEventId());
        entity.setEventOrder(event.eventOrder());
        entity.setActionType(event.actionType());
        entity.setTeamName(event.teamName());
        entity.setScoreMargin(event.scoreMargin());
        entity.setIsScoringPlay(event.isScoringPlay());
        entity.setIsHighlightCandidate(event.isHighlightCandidate());
        entity.setRawPayloadJson(event.rawPayloadJson());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    public List<PlayByPlay> normalizeAll(List<PlayByPlayEvent> events, String gameId, ProviderType source) {
        List<PlayByPlay> entities = new ArrayList<>();
        for (PlayByPlayEvent event : events) {
            entities.add(normalize(event, gameId, source));
        }
        return entities;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd backend && ./mvnw test -pl . -Dtest=PlayByPlayNormalizeServiceTest -q`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/PlayByPlayNormalizeService.java
git add backend/src/test/java/com/nbamanager/service/PlayByPlayNormalizeServiceTest.java
git commit -m "feat: add PlayByPlayNormalizeService for entity mapping
```

---

## 任务 6：PlayByPlayValidationService

**文件：**
- 创建：`service/PlayByPlayValidationService.java`

- [ ] **步骤 1：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.provider.PlayByPlayEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlayByPlayValidationService {

    public record ValidationResult(boolean valid, List<String> errors) {}

    public ValidationResult validate(List<PlayByPlayEvent> events) {
        List<String> errors = new ArrayList<>();

        if (events == null || events.isEmpty()) {
            errors.add("No events to validate");
            return new ValidationResult(false, errors);
        }

        int prevHomeScore = -1;
        int prevAwayScore = -1;

        for (int i = 0; i < events.size(); i++) {
            PlayByPlayEvent e = events.get(i);

            // period 范围检查
            if (e.period() < 1 || e.period() > 10) {
                errors.add("Invalid period " + e.period() + " at event " + i);
            }

            // gameClock 格式检查
            if (e.gameClock() == null || !e.gameClock().matches("\\d{1,2}:\\d{2}")) {
                errors.add("Invalid gameClock '" + e.gameClock() + "' at event " + i);
            }

            // 比分单调递增检查
            if (e.homeScore() < 0 || e.awayScore() < 0) {
                errors.add("Negative score at event " + i);
            }

            if (prevHomeScore >= 0 && (e.homeScore() < prevHomeScore || e.awayScore() < prevAwayScore)) {
                // 分数倒退 — 可能是新节开始，只警告不报错
                log.warn("Score decrease detected at event {}: prev={} - {}, curr={} - {}",
                        i, prevHomeScore, prevAwayScore, e.homeScore(), e.awayScore());
            }

            prevHomeScore = e.homeScore();
            prevAwayScore = e.awayScore();
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/PlayByPlayValidationService.java
git commit -m "feat: add PlayByPlayValidationService for event validation
```

---

## 任务 7：PlayByPlayProviderManager

**文件：**
- 创建：`service/provider/PlayByPlayProviderManager.java`
- 创建：`backend/src/test/java/com/nbamanager/service/provider/PlayByPlayProviderManagerTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.nbamanager.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayByPlayProviderManagerTest {

    @Mock
    private NbaPlayByPlayProvider nbaProvider;

    @Mock
    private EspnPlayByPlayProvider espnProvider;

    @Test
    void fetch_usesFirstProviderWhenSuccess() {
        PlayByPlayFetchResult successResult = PlayByPlayFetchResult.success(
                ProviderType.NBA_PLAY_BY_PLAY, List.of(), "[]", 100);

        when(nbaProvider.supports("0042500226")).thenReturn(true);
        when(nbaProvider.fetch("0042500226", PlayByPlayFetchOptions.defaults()))
                .thenReturn(successResult);

        PlayByPlayProviderManager manager = new PlayByPlayProviderManager(List.of(nbaProvider, espnProvider));
        PlayByPlayFetchResult result = manager.fetch("0042500226", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isTrue();
        assertThat(result.source()).isEqualTo(ProviderType.NBA_PLAY_BY_PLAY);
    }

    @Test
    void fetch_fallsBackToSecondProvider() {
        PlayByPlayFetchResult failResult = PlayByPlayFetchResult.failure(
                ProviderType.NBA_PLAY_BY_PLAY, "timeout", 30000);
        PlayByPlayFetchResult successResult = PlayByPlayFetchResult.success(
                ProviderType.ESPN_PLAY_BY_PLAY, List.of(), "[]", 200);

        when(nbaProvider.supports("0042500226")).thenReturn(true);
        when(nbaProvider.fetch("0042500226", PlayByPlayFetchOptions.defaults()))
                .thenReturn(failResult);
        when(espnProvider.supports("0042500226")).thenReturn(true);
        when(espnProvider.fetch("0042500226", PlayByPlayFetchOptions.defaults()))
                .thenReturn(successResult);

        PlayByPlayProviderManager manager = new PlayByPlayProviderManager(List.of(nbaProvider, espnProvider));
        PlayByPlayFetchResult result = manager.fetch("0042500226", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isTrue();
        assertThat(result.source()).isEqualTo(ProviderType.ESPN_PLAY_BY_PLAY);
    }

    @Test
    void fetch_returnsFailureWhenAllFail() {
        PlayByPlayFetchResult failResult = PlayByPlayFetchResult.failure(
                ProviderType.NBA_PLAY_BY_PLAY, "error", 100);

        when(nbaProvider.supports("0042500226")).thenReturn(true);
        when(nbaProvider.fetch("0042500226", PlayByPlayFetchOptions.defaults()))
                .thenReturn(failResult);
        when(espnProvider.supports("0042500226")).thenReturn(false);

        PlayByPlayProviderManager manager = new PlayByPlayProviderManager(List.of(nbaProvider, espnProvider));
        PlayByPlayFetchResult result = manager.fetch("0042500226", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isFalse();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd backend && ./mvnw test -pl . -Dtest=PlayByPlayProviderManagerTest -q`
预期：FAIL

- [ ] **步骤 3：编写实现代码**

```java
package com.nbamanager.service.provider;

import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayProvider;
import com.nbamanager.domain.provider.ProviderType;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlayByPlayProviderManager {

    private final List<PlayByPlayProvider> providers;

    public PlayByPlayProviderManager(List<PlayByPlayProvider> providers) {
        this.providers = providers;
    }

    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        List<String> errors = new ArrayList<>();

        for (PlayByPlayProvider provider : providers) {
            if (!provider.supports(gameId)) {
                log.info("Provider {} does not support gameId={}", provider.type(), gameId);
                continue;
            }

            log.info("Trying provider {} for gameId={}", provider.type(), gameId);
            PlayByPlayFetchResult result = provider.fetch(gameId, options);

            if (result.success()) {
                log.info("Provider {} succeeded for gameId={} with {} events",
                        provider.type(), gameId, result.events().size());
                return result;
            }

            log.warn("Provider {} failed for gameId={}: {}",
                    provider.type(), gameId, result.errorMessage());
            errors.add(provider.type() + ": " + result.errorMessage());
        }

        String aggregatedError = "All providers failed: " + String.join("; ", errors);
        log.error(aggregatedError);
        return PlayByPlayFetchResult.failure(null, aggregatedError, 0);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd backend && ./mvnw test -pl . -Dtest=PlayByPlayProviderManagerTest -q`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/provider/PlayByPlayProviderManager.java
git add backend/src/test/java/com/nbamanager/service/provider/PlayByPlayProviderManagerTest.java
git commit -m "feat: add PlayByPlayProviderManager with failover logic
```

---

## 任务 8：PlayByPlaySyncLogService

**文件：**
- 创建：`service/PlayByPlaySyncLogService.java`

- [ ] **步骤 1：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.repository.PlayByPlaySyncLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlaySyncLogService {

    private final PlayByPlaySyncLogRepository repository;

    private static final int MAX_AUTO_RETRIES = 3;

    public Optional<PlayByPlaySyncLog> getLatestLog(String gameId) {
        return repository.findFirstByGameIdOrderByStartedAtDesc(gameId);
    }

    public boolean hasSuccessfulSync(String gameId) {
        return repository.findFirstByGameIdOrderByStartedAtDesc(gameId)
                .map(log -> "SUCCESS".equals(log.getStatus()))
                .orElse(false);
    }

    public PlayByPlaySyncLog startSync(String gameId, String source) {
        PlayByPlaySyncLog log = new PlayByPlaySyncLog();
        log.setGameId(gameId);
        log.setSource(source);
        log.setStatus("PENDING");
        log.setAttemptCount(1);
        log.setStartedAt(LocalDateTime.now());
        return repository.save(log);
    }

    public void logSuccess(PlayByPlaySyncLog syncLog, int eventCount) {
        syncLog.setStatus("SUCCESS");
        syncLog.setEventCount(eventCount);
        syncLog.setCompletedAt(LocalDateTime.now());
        repository.save(syncLog);
        log.info("Sync SUCCESS for gameId={} with {} events", syncLog.getGameId(), eventCount);
    }

    public void logFailure(PlayByPlaySyncLog syncLog, String errorMessage) {
        syncLog.setStatus("FAILED");
        syncLog.setErrorMessage(errorMessage);
        syncLog.setCompletedAt(LocalDateTime.now());
        syncLog.setNextRetryAt(calculateNextRetry(syncLog.getAttemptCount()));
        repository.save(syncLog);
        log.warn("Sync FAILED for gameId={}: {}", syncLog.getGameId(), errorMessage);
    }

    public List<PlayByPlaySyncLog> getFailedLogsForRetry() {
        return repository.findByStatusAndNextRetryAtBefore("FAILED", LocalDateTime.now());
    }

    public List<PlayByPlaySyncLog> getRecentSuccessfulLogs(LocalDateTime since) {
        return repository.findByStatusAndStartedAtAfter("SUCCESS", since);
    }

    private LocalDateTime calculateNextRetry(int attempt) {
        if (attempt >= MAX_AUTO_RETRIES) return null;
        return switch (attempt) {
            case 1 -> LocalDateTime.now().plusMinutes(5);
            case 2 -> LocalDateTime.now().plusMinutes(30);
            case 3 -> LocalDateTime.now().plusHours(6);
            default -> null;
        };
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/PlayByPlaySyncLogService.java
git commit -m "feat: add PlayByPlaySyncLogService for sync status tracking
```

---

## 任务 9：MomentumComputeService

**文件：**
- 创建：`service/MomentumComputeService.java`
- 创建：`backend/src/test/java/com/nbamanager/service/MomentumComputeServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.GameMomentumSnapshot;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class MomentumComputeServiceTest {

    private final MomentumComputeService service = new MomentumComputeService();

    @Test
    void compute_createsSnapshotForEachEvent() {
        List<PlayByPlayEvent> events = List.of(
                makeEvent(1, "10:00", 2, 0, 10000),
                makeEvent(1, "08:00", 5, 3, 10120),
                makeEvent(1, "06:00", 8, 3, 10360),
                makeEvent(2, "11:00", 10, 8, 20060)
        );

        List<GameMomentumSnapshot> snapshots = service.compute("0042500226", events);

        assertThat(snapshots).hasSize(4);
        assertThat(snapshots.get(0).getScoreMargin()).isEqualTo(2);
        assertThat(snapshots.get(1).getScoreMargin()).isEqualTo(2);
        assertThat(snapshots.get(2).getScoreMargin()).isEqualTo(5);
        assertThat(snapshots.get(3).getScoreMargin()).isEqualTo(2);
        assertThat(snapshots.get(0).getGameId()).isEqualTo("0042500226");
    }

    @Test
    void compute_handlesEmptyEvents() {
        List<GameMomentumSnapshot> snapshots = service.compute("0042500226", List.of());
        assertThat(snapshots).isEmpty();
    }

    private PlayByPlayEvent makeEvent(int period, String clock, int home, int away, int order) {
        return new PlayByPlayEvent(
                "evt_" + order, period, clock, "Jump Shot", "Jump Shot",
                "desc", "Player", 1L, "Team", 10L,
                home, away, home - away, true, false, order, "{}"
        );
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd backend && ./mvnw test -pl . -Dtest=MomentumComputeServiceTest -q`
预期：FAIL

- [ ] **步骤 3：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.GameMomentumSnapshot;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameMomentumSnapshotRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MomentumComputeService {

    private final GameMomentumSnapshotRepository repository;

    @Transactional
    public List<GameMomentumSnapshot> compute(String gameId, List<PlayByPlayEvent> events) {
        repository.deleteByGameId(gameId);

        List<GameMomentumSnapshot> snapshots = new ArrayList<>();
        for (PlayByPlayEvent event : events) {
            GameMomentumSnapshot snapshot = new GameMomentumSnapshot();
            snapshot.setGameId(gameId);
            snapshot.setEventOrder(event.eventOrder());
            snapshot.setPeriod(event.period());
            snapshot.setGameClock(event.gameClock());
            snapshot.setHomeScore(event.homeScore());
            snapshot.setAwayScore(event.awayScore());
            snapshot.setScoreMargin(event.scoreMargin());
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshots.add(snapshot);
        }

        repository.saveAll(snapshots);
        log.info("Computed {} momentum snapshots for gameId={}", snapshots.size(), gameId);
        return snapshots;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd backend && ./mvnw test -pl . -Dtest=MomentumComputeServiceTest -q`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/MomentumComputeService.java
git add backend/src/test/java/com/nbamanager/service/MomentumComputeServiceTest.java
git commit -m "feat: add MomentumComputeService for game momentum snapshots
```

---

## 任务 10：HighlightDetectService

**文件：**
- 创建：`service/HighlightDetectService.java`

- [ ] **步骤 1：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.GameHighlight;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameHighlightRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HighlightDetectService {

    private final GameHighlightRepository repository;

    @Transactional
    public List<GameHighlight> detect(String gameId, List<PlayByPlayEvent> events) {
        repository.deleteByGameId(gameId);

        List<GameHighlight> highlights = new ArrayList<>();

        for (PlayByPlayEvent event : events) {
            if (!event.isHighlightCandidate()) continue;

            String highlightType = classifyHighlight(event);
            if (highlightType == null) continue;

            GameHighlight highlight = new GameHighlight();
            highlight.setGameId(gameId);
            highlight.setPeriod(event.period());
            highlight.setGameClock(event.gameClock());
            highlight.setHighlightType(highlightType);
            highlight.setTitle(buildTitle(event, highlightType));
            highlight.setDescription(event.description());
            highlight.setPlayerId(event.playerId());
            highlight.setTeamId(event.teamId());
            highlight.setScoreAfter(event.homeScore() + "-" + event.awayScore());
            highlight.setCreatedAt(LocalDateTime.now());
            highlights.add(highlight);
        }

        repository.saveAll(highlights);
        log.info("Detected {} highlights for gameId={}", highlights.size(), gameId);
        return highlights;
    }

    private String classifyHighlight(PlayByPlayEvent event) {
        String lower = event.eventType().toLowerCase();
        String desc = event.description() != null ? event.description().toUpperCase() : "";

        if (lower.contains("dunk")) return "DUNK";
        if (lower.contains("block") || desc.contains("BLOCK")) return "BLOCK";
        if (lower.contains("3pt") && desc.contains("MAKES")) return "THREE_PT";
        if (lower.contains("steal") || desc.contains("STEAL")) return "STEAL";

        // 绝杀判断：第四节/加时最后 30 秒且命中
        if (event.period() >= 4 && isLast30Seconds(event.gameClock()) && desc.contains("MAKES")) {
            return "GAME_WINNER";
        }

        return null;
    }

    private String buildTitle(PlayByPlayEvent event, String type) {
        String player = event.playerName() != null ? event.playerName() : "Unknown";
        return switch (type) {
            case "DUNK" -> player + " 扣篮";
            case "BLOCK" -> player + " 盖帽";
            case "THREE_PT" -> player + " 命中三分";
            case "STEAL" -> player + " 抢断";
            case "GAME_WINNER" -> player + " 绝杀球";
            default -> player + " 精彩表现";
        };
    }

    private boolean isLast30Seconds(String gameClock) {
        if (gameClock == null) return false;
        try {
            String[] parts = gameClock.split(":");
            int minutes = Integer.parseInt(parts[0].trim());
            int seconds = Integer.parseInt(parts[1].trim());
            return minutes == 0 && seconds <= 30;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/HighlightDetectService.java
git commit -m "feat: add HighlightDetectService for automatic highlight extraction
```

---

## 任务 11：TurningPointDetectService

**文件：**
- 创建：`service/TurningPointDetectService.java`

- [ ] **步骤 1：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.GameTurningPoint;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameTurningPointRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurningPointDetectService {

    private final GameTurningPointRepository repository;

    // 规则阈值
    private static final int RUN_THRESHOLD = 8;          // 8-0 run
    private static final int SURGE_THRESHOLD = 12;       // 12-4 surge
    private static final int CLOSE_GAME_MARGIN = 2;      // 2 possession game
    private static final int BLOWOUT_MARGIN = 8;         // 8 point lead

    @Transactional
    public List<GameTurningPoint> detect(String gameId, List<PlayByPlayEvent> events) {
        repository.deleteByGameId(gameId);

        List<GameTurningPoint> points = new ArrayList<>();

        // 检测 Runs（连续得分不丢分）
        points.addAll(detectRuns(gameId, events));

        // 检测第四节/加时反超
        points.addAll(detectOvertakes(gameId, events));

        // 检测关键时刻拉开
        points.addAll(detectClutchSeparations(gameId, events));

        repository.saveAll(points);
        log.info("Detected {} turning points for gameId={}", points.size(), gameId);
        return points;
    }

    private List<GameTurningPoint> detectRuns(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> points = new ArrayList<>();

        for (int windowSize = 8; windowSize <= events.size(); windowSize++) {
            for (int start = 0; start + windowSize <= events.size(); start++) {
                int end = start + windowSize;
                PlayByPlayEvent first = events.get(start);
                PlayByPlayEvent last = events.get(end - 1);

                int homeScored = last.homeScore() - first.homeScore();
                int awayScored = last.awayScore() - first.awayScore();

                // 某队至少得 8 分且对手 0 分
                if (homeScored >= RUN_THRESHOLD && awayScored == 0) {
                    points.add(createTurningPoint(gameId, last, "RUN",
                            0.9, first.homeTeam() + " 打出 " + homeScored + "-0 攻击波",
                            first, last));
                    break; // 同一节不再重复检测
                }
                if (awayScored >= RUN_THRESHOLD && homeScored == 0) {
                    points.add(createTurningPoint(gameId, last, "RUN",
                            0.9, first.awayTeam() + " 打出 " + awayScored + "-0 攻击波",
                            first, last));
                    break;
                }
            }
        }
        return points;
    }

    private List<GameTurningPoint> detectOvertakes(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> points = new ArrayList<>();

        for (int i = 1; i < events.size(); i++) {
            PlayByPlayEvent prev = events.get(i - 1);
            PlayByPlayEvent curr = events.get(i);

            boolean prevHomeLeading = prev.scoreMargin() > 0;
            boolean currHomeLeading = curr.scoreMargin() > 0;
            boolean prevAwayLeading = prev.scoreMargin() < 0;
            boolean currAwayLeading = curr.scoreMargin() < 0;

            // 反超发生在第四节或加时
            if (curr.period() >= 4) {
                if ((prevHomeLeading && currAwayLeading) || (prevAwayLeading && currHomeLeading)) {
                    points.add(createTurningPoint(gameId, curr, "OVERTAKE",
                            0.85, "第四节/加时反超", prev, curr));
                }
            }
        }
        return points;
    }

    private List<GameTurningPoint> detectClutchSeparations(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> points = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            PlayByPlayEvent event = events.get(i);

            // 最后 5 分钟内
            if (event.period() >= 4 && isLast5Minutes(event.gameClock())) {
                if (Math.abs(event.scoreMargin()) >= BLOWOUT_MARGIN) {
                    // 检查前 10 个事件是否接近
                    for (int j = Math.max(0, i - 10); j < i; j++) {
                        if (Math.abs(events.get(j).scoreMargin()) <= CLOSE_GAME_MARGIN) {
                            points.add(createTurningPoint(gameId, event, "CLUTCH_SEPARATION",
                                    0.8, "关键时刻拉开到 " + Math.abs(event.scoreMargin()) + " 分",
                                    events.get(j), event));
                            break;
                        }
                    }
                }
            }
        }
        return points;
    }

    private GameTurningPoint createTurningPoint(String gameId, PlayByPlayEvent event,
                                                 String type, double confidence,
                                                 String reason,
                                                 PlayByPlayEvent fromEvent,
                                                 PlayByPlayEvent toEvent) {
        GameTurningPoint tp = new GameTurningPoint();
        tp.setGameId(gameId);
        tp.setPeriod(event.period());
        tp.setGameClock(event.gameClock());
        tp.setType(type);
        tp.setConfidence(confidence);
        tp.setFactualReason(reason);
        tp.setCitedEventIdsJson("[" + fromEvent.eventOrder() + "," + toEvent.eventOrder() + "]");
        tp.setCreatedAt(LocalDateTime.now());
        return tp;
    }

    private boolean isLast5Minutes(String gameClock) {
        if (gameClock == null) return false;
        try {
            String[] parts = gameClock.split(":");
            int minutes = Integer.parseInt(parts[0].trim());
            return minutes <= 5;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/TurningPointDetectService.java
git commit -m "feat: add TurningPointDetectService with rule-based detection
```

---

## 任务 12：PlayByPlayCollectorService

**文件：**
- 创建：`service/PlayByPlayCollectorService.java`
- 创建：`backend/src/test/java/com/nbamanager/service/PlayByPlayCollectorServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.service.provider.PlayByPlayProviderManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayByPlayCollectorServiceTest {

    @Mock
    private PlayByPlayProviderManager providerManager;
    @Mock
    private PlayByPlayNormalizeService normalizeService;
    @Mock
    private PlayByPlayValidationService validationService;
    @Mock
    private PlayByPlaySyncLogService syncLogService;
    @Mock
    private MomentumComputeService momentumComputeService;
    @Mock
    private HighlightDetectService highlightDetectService;
    @Mock
    private TurningPointDetectService turningPointDetectService;
    @Mock
    private PlayByPlayRepository playByPlayRepository;

    @InjectMocks
    private PlayByPlayCollectorService collector;

    @Test
    void collect_returnsSuccessWhenProviderSucceeds() {
        String gameId = "0042500226";
        PlayByPlayEvent event = new PlayByPlayEvent(
                "evt_1", 1, "08:52", "Jump Shot", "Jump Shot", "desc",
                "Player", 1L, "Team", 10L, 25, 20, 5, true, false, 10532, "{}"
        );
        PlayByPlayFetchResult fetchResult = PlayByPlayFetchResult.success(
                ProviderType.NBA_PLAY_BY_PLAY, List.of(event), "[]", 100);

        PlayByPlaySyncLog syncLog = new PlayByPlaySyncLog();
        syncLog.setGameId(gameId);

        when(syncLogService.hasSuccessfulSync(gameId)).thenReturn(false);
        when(syncLogService.startSync(gameId, ProviderType.NBA_PLAY_BY_PLAY.name())).thenReturn(syncLog);
        when(providerManager.fetch(eq(gameId), any(PlayByPlayFetchOptions.class))).thenReturn(fetchResult);
        when(validationService.validate(any())).thenReturn(
                new PlayByPlayValidationService.ValidationResult(true, List.of()));
        when(playByPlayRepository.saveAll(any())).thenReturn(List.of());

        var result = collector.collect(gameId);

        assertThat(result.success()).isTrue();
        assertThat(result.eventCount()).isEqualTo(1);
    }

    @Test
    void collect_returnsCachedWhenAlreadySynced() {
        String gameId = "0042500226";
        when(syncLogService.hasSuccessfulSync(gameId)).thenReturn(true);

        var result = collector.collect(gameId);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("cached");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd backend && ./mvnw test -pl . -Dtest=PlayByPlayCollectorServiceTest -q`
预期：FAIL

- [ ] **步骤 3：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.service.provider.PlayByPlayProviderManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlayCollectorService {

    private final PlayByPlayProviderManager providerManager;
    private final PlayByPlayNormalizeService normalizeService;
    private final PlayByPlayValidationService validationService;
    private final PlayByPlaySyncLogService syncLogService;
    private final MomentumComputeService momentumComputeService;
    private final HighlightDetectService highlightDetectService;
    private final TurningPointDetectService turningPointDetectService;
    private final PlayByPlayRepository playByPlayRepository;

    public record CollectResult(boolean success, String source, int eventCount, String message) {}

    @Transactional
    public CollectResult collect(String gameId) {
        // 1. 缓存检查
        if (syncLogService.hasSuccessfulSync(gameId)) {
            log.info("PlayByPlay already synced for gameId={}, skipping", gameId);
            return new CollectResult(true, null, 0, "cached");
        }

        // 2. Provider 采集
        PlayByPlayFetchResult fetchResult = providerManager.fetch(gameId, PlayByPlayFetchOptions.defaults());
        String sourceName = fetchResult.source() != null ? fetchResult.source().name() : "UNKNOWN";
        PlayByPlaySyncLog syncLog = syncLogService.startSync(gameId, sourceName);

        if (!fetchResult.success()) {
            syncLogService.logFailure(syncLog, fetchResult.errorMessage());
            return new CollectResult(false, sourceName, 0, fetchResult.errorMessage());
        }

        // 3. 校验
        PlayByPlayValidationService.ValidationResult validation = validationService.validate(fetchResult.events());
        if (!validation.valid()) {
            String errors = String.join("; ", validation.errors());
            syncLogService.logFailure(syncLog, errors);
            return new CollectResult(false, sourceName, 0, errors);
        }

        // 4. 标准化 + 存库
        List<PlayByPlay> entities = normalizeService.normalizeAll(fetchResult.events(), gameId, fetchResult.source());
        playByPlayRepository.saveAll(entities);

        // 5. 计算衍生数据
        List<PlayByPlayEvent> events = fetchResult.events();
        momentumComputeService.compute(gameId, events);
        highlightDetectService.detect(gameId, events);
        turningPointDetectService.detect(gameId, events);

        // 6. 记录成功
        syncLogService.logSuccess(syncLog, events.size());

        log.info("PlayByPlay collected for gameId={} from {} with {} events",
                gameId, sourceName, events.size());
        return new CollectResult(true, sourceName, events.size(), "success");
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd backend && ./mvnw test -pl . -Dtest=PlayByPlayCollectorServiceTest -q`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/PlayByPlayCollectorService.java
git add backend/src/test/java/com/nbamanager/service/PlayByPlayCollectorServiceTest.java
git commit -m "feat: add PlayByPlayCollectorService for orchestration
```

---

## 任务 13：PlayByPlaySyncScheduler

**文件：**
- 创建：`service/PlayByPlaySyncScheduler.java`

- [ ] **步骤 1：编写实现代码**

```java
package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.repository.MatchRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlaySyncScheduler {

    private final PlayByPlayCollectorService collector;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayByPlaySyncLogService syncLogService;

    /**
     * 每 30 分钟检查：最近 24 小时已结束但未采集 PBP 的比赛
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void syncFinishedGames() {
        log.info("Scheduler: checking for finished games without PBP...");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<MatchRecord> finishedGames = matchRecordRepository
                .findByStatusAndMatchDateAfter("FINISHED", yesterday);

        int synced = 0;
        for (MatchRecord game : finishedGames) {
            String gameId = game.getNbaGameId();
            if (gameId == null || gameId.isEmpty()) continue;

            if (!syncLogService.hasSuccessfulSync(gameId)) {
                log.info("Scheduler: syncing gameId={}", gameId);
                collector.collect(gameId);
                synced++;
            }
        }

        if (synced > 0) {
            log.info("Scheduler: synced {} finished games", synced);
        }
    }

    /**
     * 每日凌晨 3 点：重试失败的同步
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void retryFailedSyncs() {
        log.info("Scheduler: retrying failed syncs...");

        List<PlayByPlaySyncLog> failedLogs = syncLogService.getFailedLogsForRetry();
        int retried = 0;

        for (PlayByPlaySyncLog log : failedLogs) {
            log.info("Scheduler: retrying gameId={}", log.getGameId());
            collector.collect(log.getGameId());
            retried++;
        }

        if (retried > 0) {
            log.info("Scheduler: retried {} failed syncs", retried);
        }
    }

    /**
     * 每 2 小时：校准最近采集的比赛
     */
    @Scheduled(fixedRate = 2 * 60 * 60 * 1000)
    public void recalibrateRecentGames() {
        log.info("Scheduler: recalibrating recent games...");

        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        List<PlayByPlaySyncLog> recentLogs = syncLogService.getRecentSuccessfulLogs(twoDaysAgo);

        int recalibrated = 0;
        for (PlayByPlaySyncLog syncLog : recentLogs) {
            String gameId = syncLog.getGameId();
            // 强制重新采集（忽略缓存）
            log.info("Scheduler: recalibrating gameId={}", gameId);
            collector.collect(gameId);
            recalibrated++;
        }

        if (recalibrated > 0) {
            log.info("Scheduler: recalibrated {} games", recalibrated);
        }
    }
}
```

需要在 `MatchRecordRepository` 中添加查询方法：

- [ ] **步骤 2：添加 Repository 查询方法**

在 `MatchRecordRepository.java` 中添加：

```java
List<MatchRecord> findByStatusAndMatchDateAfter(String status, LocalDate date);
```

- [ ] **步骤 3：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/PlayByPlaySyncScheduler.java
git add backend/src/main/java/com/nbamanager/repository/MatchRecordRepository.java
git commit -m "feat: add PlayByPlaySyncScheduler with automatic sync tasks
```

---

## 任务 14：API 端点（sync + sync-status）

**文件：**
- 修改：`web/MatchDetailController.java`
- 修改：`web/dto/PlayByPlayDto.java`

- [ ] **步骤 1：更新 PlayByPlayDto**

将 `PlayByPlayDto.java` 更新为：

```java
package com.nbamanager.web.dto;

public record PlayByPlayDto(
        Integer period,
        String gameClock,
        String description,
        Integer homeScore,
        Integer awayScore,
        String eventType,
        Long playerId,
        String playerName,
        String source,
        String actionType,
        String teamName,
        Integer scoreMargin,
        Boolean isScoringPlay,
        Boolean isHighlightCandidate,
        Integer eventOrder
) {}
```

- [ ] **步骤 2：更新 MatchDetailService.getPlayByPlay()**

在 `MatchDetailService.java` 的 `getPlayByPlay()` 方法中，更新 DTO 映射以包含新字段。查找现有的 DTO 转换代码，添加新字段映射。

- [ ] **步骤 3：在 MatchDetailController 新增 sync 接口**

```java
// 在 MatchDetailController 中添加：

@PostMapping("/{gameId}/sync")
public Map<String, Object> syncPlayByPlay(@PathVariable String gameId) {
    PlayByPlayCollectorService.CollectResult result = collector.collect(gameId);
    return Map.of(
        "success", result.success(),
        "source", result.source() != null ? result.source() : "",
        "eventCount", result.eventCount(),
        "message", result.message()
    );
}

@GetMapping("/{gameId}/sync-status")
public PlayByPlaySyncLog getSyncStatus(@PathVariable String gameId) {
    return syncLogService.getLatestLog(gameId).orElse(null);
}
```

需要注入 `PlayByPlayCollectorService` 和 `PlayByPlaySyncLogService`。

- [ ] **步骤 4：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/web/MatchDetailController.java
git add backend/src/main/java/com/nbamanager/web/dto/PlayByPlayDto.java
git add backend/src/main/java/com/nbamanager/service/MatchDetailService.java
git commit -m "feat: add sync and sync-status API endpoints
```

---

## 任务 15：EspnPlayByPlayProvider + Python 扩展

**文件：**
- 创建：`service/provider/EspnPlayByPlayProvider.java`
- 修改：`scripts/nba_data_fetcher.py`

- [ ] **步骤 1：在 nba_data_fetcher.py 新增 espn_playbyplay 函数**

在 `fetch_playbyplay` 函数之后添加：

```python
def fetch_espn_playbyplay(game_id):
    """从 ESPN 获取 Play-by-Play 数据"""
    print(f"正在从 ESPN 获取比赛 {game_id} 的 Play-by-Play...", file=sys.stderr)

    try:
        import urllib.request
        url = f"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event={game_id}"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"ESPN API 请求失败: {e}", file=sys.stderr)
        return []

    # 解析 ESPN play-by-play
    plays = data.get("plays", [])
    events = []
    period = 1

    for play in plays:
        # ESPN plays 结构：{period, clock, text, homeScore, awayScore, ...}
        period_num = play.get("period", {}).get("number", period)
        clock = play.get("clock", {}).get("displayValue", "12:00")
        text = play.get("text", "")
        home_score = _safe_int(play.get("homeScore", 0))
        away_score = _safe_int(play.get("awayScore", 0))
        scoring_play = play.get("scoringPlay", False)
        event_type = play.get("type", {}).get("text", "")

        if not text:
            continue

        events.append({
            "period": period_num,
            "gameClock": clock,
            "description": text,
            "homeScore": home_score,
            "awayScore": away_score,
            "eventType": event_type,
            "playerId": None,
            "playerName": play.get("athlete", {}).get("displayName", ""),
            "teamId": None,
            "teamName": play.get("team", {}).get("displayName", ""),
            "source": "ESPN_PLAY_BY_PLAY",
        })

    print(f"ESPN 获取到 {len(events)} 条事件", file=sys.stderr)
    return events
```

- [ ] **步骤 2：在 CLI 入口添加 espn_playbyplay action**

在 `main()` 函数的 action 处理中添加：

```python
elif action == "espn_playbyplay":
    result = fetch_espn_playbyplay(game_id)
    output = {"playByPlay": result}
```

- [ ] **步骤 3：创建 EspnPlayByPlayProvider**

```java
package com.nbamanager.service.provider;

import com.nbamanager.config.EnvFileReader;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayProvider;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.service.GameIdMappingService;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EspnPlayByPlayProvider implements PlayByPlayProvider {

    private final GameIdMappingService gameIdMappingService;

    @Override
    public ProviderType type() {
        return ProviderType.ESPN_PLAY_BY_PLAY;
    }

    @Override
    public boolean supports(String gameId) {
        if (gameId == null || gameId.isEmpty()) return false;
        // 需要能转换为 ESPN gameId
        String espnId = gameIdMappingService.getEspnGameId(gameId);
        return espnId != null && !espnId.equals(gameId);
    }

    @Override
    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        long start = System.currentTimeMillis();
        try {
            String espnGameId = gameIdMappingService.getEspnGameId(gameId);
            if (espnGameId == null || espnGameId.equals(gameId)) {
                return PlayByPlayFetchResult.failure(type(), "No ESPN mapping found", 0);
            }

            String scriptPath = findScriptPath();
            log.info("ESPN Provider: fetching playbyplay for espnGameId={}", espnGameId);

            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "espn_playbyplay", espnGameId);
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            long elapsed = System.currentTimeMillis() - start;

            if (exitCode != 0) {
                return PlayByPlayFetchResult.failure(type(),
                        "Python script exited with code " + exitCode, elapsed);
            }

            String json = output.toString().trim();
            if (json.isEmpty()) {
                return PlayByPlayFetchResult.failure(type(), "Empty output", elapsed);
            }

            JSONObject root = new JSONObject(json);
            JSONArray eventsArray = root.optJSONArray("playByPlay");
            if (eventsArray == null) {
                return PlayByPlayFetchResult.failure(type(), "No playByPlay in response", elapsed);
            }

            List<PlayByPlayEvent> events = parseEspnEvents(eventsArray);
            log.info("ESPN Provider: fetched {} events for gameId={}", events.size(), gameId);

            return PlayByPlayFetchResult.success(type(), events, json, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("ESPN Provider failed for gameId={}: {}", gameId, e.getMessage());
            return PlayByPlayFetchResult.failure(type(), e.getMessage(), elapsed);
        }
    }

    private List<PlayByPlayEvent> parseEspnEvents(JSONArray eventsArray) {
        List<PlayByPlayEvent> events = new ArrayList<>();
        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject obj = eventsArray.getJSONObject(i);
            int period = obj.optInt("period", 0);
            String gameClock = obj.optString("gameClock", "12:00");
            String eventType = obj.optString("eventType", "");
            String description = obj.optString("description", "");
            int homeScore = obj.optInt("homeScore", 0);
            int awayScore = obj.optInt("awayScore", 0);

            int eventOrder = period * 10000 + NbaPlayByPlayProvider.gameClockToSeconds(gameClock);
            int scoreMargin = homeScore - awayScore;
            boolean isScoringPlay = NbaPlayByPlayProvider.detectScoringPlay(eventType, description);
            boolean isHighlightCandidate = NbaPlayByPlayProvider.detectHighlight(eventType, description);

            events.add(new PlayByPlayEvent(
                    "espn_" + i, period, gameClock, eventType, eventType,
                    description, obj.optString("playerName", ""),
                    obj.has("playerId") ? obj.optLong("playerId") : null,
                    obj.optString("teamName", ""),
                    obj.has("teamId") ? obj.optLong("teamId") : null,
                    homeScore, awayScore, scoreMargin,
                    isScoringPlay, isHighlightCandidate, eventOrder,
                    obj.toString()
            ));
        }
        return events;
    }

    private String findScriptPath() {
        String[] candidates = {
            "backend/scripts/nba_data_fetcher.py",
            "scripts/nba_data_fetcher.py",
            "nba_data_fetcher.py"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) return f.getAbsolutePath();
        }
        return "backend/scripts/nba_data_fetcher.py";
    }
}
```

- [ ] **步骤 4：验证编译**

运行：`cd backend && ./mvnw compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbamanager/service/provider/EspnPlayByPlayProvider.java
git add backend/scripts/nba_data_fetcher.py
git commit -m "feat: add EspnPlayByPlayProvider with ESPN API integration
```

---

## 任务 16：单元测试完善

**文件：**
- 创建：`backend/src/test/java/com/nbamanager/service/provider/NbaPlayByPlayProviderTest.java`（已在任务 4 创建）

- [ ] **步骤 1：运行所有后端测试**

运行：`cd backend && ./mvnw test -q`
预期：BUILD SUCCESS，所有测试通过

- [ ] **步骤 2：修复任何失败的测试**

如果测试失败，根据错误信息修复。

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "test: verify all PlayByPlay infrastructure tests pass
```

---

## 任务 17：集成验证

**文件：**
- 无新文件，验证现有代码完整性

- [ ] **步骤 1：前端构建验证**

运行：`cd frontend && npm run build`
预期：构建成功，无 TypeScript 错误

- [ ] **步骤 2：后端编译验证**

运行：`cd backend && ./mvnw compile`
预期：BUILD SUCCESS

- [ ] **步骤 3：全量后端测试**

运行：`cd backend && ./mvnw test`
预期：BUILD SUCCESS

- [ ] **步骤 4：最终 Commit**

```bash
git add -A
git commit -m "feat: complete PlayByPlay data infrastructure (sub-project A)

- Extended PlayByPlay entity with source, eventOrder, actionType fields
- Added 7 new entities: SyncLog, MomentumSnapshot, TurningPoint, Highlight, etc.
- Added Provider abstraction: NbaPlayByPlayProvider, EspnPlayByPlayProvider
- Added ProviderManager with failover logic
- Added Collector, Normalizer, Validator, SyncLog services
- Added MomentumCompute, HighlightDetect, TurningPointDetect services
- Added SyncScheduler with automatic sync and retry
- Added sync and sync-status API endpoints
- Added ESPN Play-by-Play Python script support
- All tests passing
```
