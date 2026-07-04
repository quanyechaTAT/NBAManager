# PlayByPlay 数据基础设施 - 设计规格

日期：2026-07-03
状态：已批准
对应任务文档：`docs/ai-game-replay-mimo-langgraph-task-plan.md` 子项目 A

## 1. 目标

建立统一的 Play-by-Play 数据采集基础设施，为后续 AI Replay（子项目 B）和 LangGraph NBA Copilot（子项目 C）提供可靠的数据源。

### 1.1 成功标准

- 指定 `gameId` 可采集完整 Timeline，每条事件有来源和原始 payload
- Provider 失败可自动降级到下一优先级数据源
- 已结束比赛可自动定时采集，失败可重试
- 管理员可手动触发指定比赛的同步
- 不破坏现有 PlayByPlay 表结构和代码

### 1.2 不在范围内

- AI Explain/Summary 生成（子项目 B）
- LangGraph Agent（子项目 C）
- 前端 Replay Tab 改造（子项目 B）
- Grounding Validator（子项目 D）

## 2. 架构概览

```
┌─────────────────────────────────────────────────────┐
│  PlayByPlaySyncScheduler (@Scheduled)               │
│    ├── syncFinishedGames()     每 30 分钟            │
│    ├── retryFailedSyncs()      每日凌晨 3 点         │
│    └── recalibrateRecentGames() 每 2 小时            │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│  PlayByPlayCollectorService                          │
│    1. 缓存检查（sync_log）                            │
│    2. 调用 ProviderManager                           │
│    3. NormalizeService 标准化                        │
│    4. ValidationService 校验                         │
│    5. 批量 upsert 写入 MySQL                         │
│    6. 计算 Momentum / Highlights / Turning Points    │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│  PlayByPlayProviderManager                           │
│    按优先级 failover 调用 Provider                    │
│    1. NBA_PLAY_BY_PLAY (优先)                        │
│    2. ESPN_PLAY_BY_PLAY (备选)                       │
└──────────────────┬──────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
┌───────────────┐  ┌───────────────────┐
│ NbaPlayByPlay │  │ EspnPlayByPlay    │
│ Provider      │  │ Provider          │
│               │  │                   │
│ → Python      │  │ → Python          │
│   playbyplay  │  │   espn_playbyplay │
└───────────────┘  └───────────────────┘
```

## 3. 数据库设计

### 3.1 扩展 `play_by_play` 表

所有变更均为 ADD COLUMN，不修改现有列，不影响现有数据和代码。

```sql
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
```

新增索引：

```sql
CREATE INDEX idx_pbp_game_order ON play_by_play(game_id, period, event_order);
CREATE INDEX idx_pbp_game_player ON play_by_play(game_id, player_id);
CREATE INDEX idx_pbp_game_type ON play_by_play(game_id, event_type);
CREATE INDEX idx_pbp_source_event ON play_by_play(source, source_event_id);
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `source` | VARCHAR(40) | 数据来源：NBA_PLAY_BY_PLAY / ESPN_PLAY_BY_PLAY |
| `source_event_id` | VARCHAR(80) | 来源系统中的事件 ID，用于去重 |
| `event_order` | INT | 事件排序序号（period * 10000 + gameClock 换算值） |
| `action_type` | VARCHAR(80) | 动作类型：3PT Jump Shot, Dunk, Free Throw 等 |
| `team_name` | VARCHAR(120) | 执行动作的球队名 |
| `score_margin` | INT | 分差（homeScore - awayScore） |
| `is_scoring_play` | BOOLEAN | 是否得分事件（2PT/3PT/FT） |
| `is_highlight_candidate` | BOOLEAN | 是否候选亮点（扣篮/三分/盖帽/绝杀） |
| `raw_payload_json` | JSON | 原始 API 返回的 JSON，用于审计 |
| `raw_payload_hash` | VARCHAR(64) | 原始 payload 的 SHA-256，用于变更检测 |
| `created_at` | DATETIME | 记录创建时间 |
| `updated_at` | DATETIME | 记录更新时间 |

### 3.2 新增 `play_by_play_sync_log`

同步日志表，记录每次采集尝试。

```java
@Entity
@Table(name = "play_by_play_sync_log")
public class PlayByPlaySyncLog {
    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false, length = 40)
    private String source;          // ProviderType.name()

    @Column(nullable = false, length = 20)
    private String status;          // PENDING / SUCCESS / FAILED

    private int attemptCount;       // 尝试次数

    @Column(length = 500)
    private String errorMessage;    // 最后一次失败原因

    private int eventCount;         // 采集到的事件数

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime nextRetryAt;  // 下次重试时间
}
```

### 3.3 新增 `game_momentum_snapshot`

缓存比赛走势数据，由 Collector 在采集成功后计算。

```java
@Entity
@Table(name = "game_momentum_snapshot")
public class GameMomentumSnapshot {
    @Id @GeneratedValue
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
    private int scoreMargin;       // homeScore - awayScore

    private Long leaderTeamId;     // 领先方的 teamId

    private LocalDateTime createdAt;
}
```

### 3.4 新增 `game_turning_point`

关键转折点，由规则引擎识别。

```java
@Entity
@Table(name = "game_turning_point")
public class GameTurningPoint {
    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    private Long eventId;          // 关联的 play_by_play.id

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false, length = 40)
    private String type;           // RUN, COMEBACK, OVERTAKE, CLUTCH 等

    private double confidence;     // 0.0 ~ 1.0

    @Column(length = 500)
    private String factualReason;  // 基于事实的描述

    @Column(columnDefinition = "TEXT")
    private String aiExplanation;  // AI 生成的解释（子项目 B 填充）

    @Column(length = 500)
    private String citedEventIdsJson;  // 引用的事件 ID 列表

    private LocalDateTime createdAt;
}
```

### 3.5 新增 `game_highlight`

亮点事件，由规则引擎自动提取。

```java
@Entity
@Table(name = "game_highlight")
public class GameHighlight {
    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    private Long eventId;          // 关联的 play_by_play.id

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false, length = 40)
    private String highlightType;  // DUNK, THREE_PT, BLOCK, STEAL, GAME_WINNER 等

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    private Long playerId;
    private Long teamId;

    @Column(length = 20)
    private String scoreAfter;     // 事件后的比分 "108-95"

    private LocalDateTime createdAt;
}
```

### 3.6 新增 `ai_replay_explanation`

事件解释缓存，子项目 B 填充。A 阶段只建表。

```java
@Entity
@Table(name = "ai_replay_explanation")
public class AiReplayExplanation {
    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false)
    private Long eventId;

    @Column(length = 40)
    private String agentName;

    @Column(length = 40)
    private String modelProvider;  // MIMO

    @Column(length = 60)
    private String modelName;      // mimo-v2.5-pro

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

### 3.7 新增 `ai_game_summary`

赛后总结缓存，子项目 B 填充。A 阶段只建表。

```java
@Entity
@Table(name = "ai_game_summary")
public class AiGameSummary {
    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false, length = 40)
    private String summaryType;    // FULL, QUARTER, KEY_MOMENTS

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

### 3.8 新增 `ai_conversation_memory`

对话记忆，子项目 C 填充。A 阶段只建表。

```java
@Entity
@Table(name = "ai_conversation_memory")
public class AiConversationMemory {
    @Id @GeneratedValue
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

## 4. Provider 层设计

### 4.1 枚举与值对象

```java
// 枚举
public enum ProviderType {
    NBA_PLAY_BY_PLAY,
    ESPN_PLAY_BY_PLAY
}

// 传输对象
public record PlayByPlayFetchResult(
    boolean success,
    ProviderType source,
    List<PlayByPlayEvent> events,
    String rawPayloadJson,
    String errorMessage,
    long fetchTimeMs
) {}

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

public record PlayByPlayFetchOptions(
    Integer period,
    boolean includeRawPayload
) {}
```

### 4.2 Provider 接口

```java
public interface PlayByPlayProvider {
    ProviderType type();
    boolean supports(String gameId);
    PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options);
}
```

### 4.3 Provider Manager

```java
@Service
public class PlayByPlayProviderManager {
    private final List<PlayByPlayProvider> providers; // 注入时按优先级排序

    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        // 按优先级依次尝试
        // 第一个成功就返回
        // 全部失败返回聚合错误
    }
}
```

### 4.4 NbaPlayByPlayProvider

- 调用 `python nba_data_fetcher.py playbyplay {gameId}`
- 超时 60 秒
- 解析 JSON 输出，映射到 `PlayByPlayEvent`
- 生成 `eventOrder`：`period * 10000 + gameClockInSeconds`，其中 `gameClockInSeconds` 将 "MM:SS" 格式转为秒数（如 "08:52" → 532）。这样同一节内 gameClock 越小（时间越靠后）eventOrder 越大，自然按时间正序排列
- 标记 `isScoringPlay`：eventType 包含 "Jump Shot"、"Layup"、"Dunk"、"Free Throw" 且 description 包含 "PTS"
- 标记 `isHighlightCandidate`：actionType 包含 "Dunk"、"Block"、"3PT" 且命中

### 4.5 EspnPlayByPlayProvider

- 新增 Python 子命令 `espn_playbyplay`
- 调用 ESPN API：`https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event={gameId}`
- 解析 ESPN 格式的 play-by-play
- 映射到相同 `PlayByPlayEvent` 结构
- `source` 标记为 `ESPN_PLAY_BY_PLAY`

## 5. Collector 层设计

### 5.1 服务拆分

| 服务 | 职责 |
|------|------|
| `PlayByPlayCollectorService` | 编排入口：缓存检查 → 采集 → 标准化 → 校验 → 存库 → 计算 |
| `PlayByPlayNormalizeService` | 纯函数：Python JSON → PlayByPlayEvent 列表映射 |
| `PlayByPlayValidationService` | 校验：比分单调递增、period 范围、gameClock 格式 |
| `MomentumComputeService` | 从 PlayByPlay 事件计算 game_momentum_snapshot |
| `HighlightDetectService` | 规则识别亮点事件 |
| `TurningPointDetectService` | 规则识别转折点 |
| `PlayByPlaySyncLogService` | 同步日志 CRUD |

### 5.2 Collector 流程

```
collect(gameId):
  1. syncLog = syncLogService.getLatestPendingOrFailed(gameId)
  2. if syncLog != null && syncLog.status == SUCCESS:
       return cached result
  3. result = providerManager.fetch(gameId, options)
  4. if !result.success():
       syncLogService.logFailure(gameId, result.errorMessage())
       return failure
  5. events = normalizeService.normalize(result)
  6. validation = validationService.validate(events)
  7. if !validation.valid():
       syncLogService.logFailure(gameId, validation.errors())
       return failure
  8. upsert to play_by_play: 按 `(game_id, source, source_event_id)` 唯一约束做 upsert
     - source_event_id 非空时：用 source + source_event_id 匹配更新
     - source_event_id 为空时：按 (game_id, period, gameClock, eventType, playerId) 匹配
     - 不存在则 INSERT，已存在则 UPDATE homeScore/awayScore/scoreMargin 等可变字段
  9. momentumComputeService.compute(gameId, events)
  10. highlightDetectService.detect(gameId, events)
  11. turningPointDetectService.detect(gameId, events)
  12. syncLogService.logSuccess(gameId, events.size())
  13. return success
```

### 5.3 重试策略

```java
private Duration getRetryDelay(int attempt) {
    return switch (attempt) {
        case 1 -> Duration.ofMinutes(5);
        case 2 -> Duration.ofMinutes(30);
        case 3 -> Duration.ofHours(6);
        default -> null; // 超过 3 次不再自动重试，允许手动重试
    };
}
```

## 6. Scheduler 设计

### 6.1 定时任务

| 任务 | 频率 | 逻辑 |
|------|------|------|
| `syncFinishedGames()` | 每 30 分钟 | 查询最近 24 小时 FINISHED 比赛，检查 sync_log，缺失的调用 collector |
| `retryFailedSyncs()` | 每日凌晨 3 点 | 查询 FAILED 且 nextRetryAt < now 的记录，重新调用 collector |
| `recalibrateRecentGames()` | 每 2 小时 | 查询最近 2 天已采集比赛，重新采集对比事件数，有新增则更新 |

### 6.2 配置

使用 `@ConditionalOnProperty` 控制开关：

```yaml
nbamanager:
  playbyplay:
    sync:
      enabled: true
      finished-games-rate: 1800000  # 30 分钟
      retry-cron: "0 0 3 * * *"
      recalibrate-rate: 7200000     # 2 小时
```

## 7. API 设计

### 7.1 管理接口

```
POST /api/match-detail/{gameId}/sync
  → 手动触发 PlayByPlay 同步
  → 响应: { success, source, eventCount, message }

GET /api/match-detail/{gameId}/sync-status
  → 查询同步状态
  → 响应: PlayByPlaySyncLog 对象
```

### 7.2 现有接口增强

`GET /api/match-detail/{gameId}/playbyplay` 现有接口不变，但返回数据将包含新字段（source, eventType, actionType 等）。需要更新 `PlayByPlayDto` 添加这些字段。

## 8. Python 脚本扩展

### 8.1 新增 `espn_playbyplay` action

在 `nba_data_fetcher.py` 中新增：

```python
def fetch_espn_playbyplay(game_id):
    """从 ESPN 获取 Play-by-Play 数据"""
    url = f"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event={game_id}"
    # 解析 ESPN 格式的 play-by-play
    # 输出与 fetch_playbyplay 相同的 JSON 结构
    # source 标记为 ESPN_PLAY_BY_PLAY
```

CLI 入口：

```python
elif action == "espn_playbyplay":
    result = fetch_espn_playbyplay(game_id)
```

### 8.2 ESPN Game ID 映射

ESPN 使用自己的 game ID 格式（如 `401547654`），与 NBA game ID（如 `0042500226`）不同。
项目已有 `GameIdMapping` 实体和 `GameIdMappingService`。EspnPlayByPlayProvider 需要：
1. 先通过 `GameIdMappingService` 将 NBA game ID 转换为 ESPN game ID
2. 再调用 ESPN API
3. 如果映射不存在，该 Provider 返回 `supports = false`

### 8.3 现有 PlayByPlay 代码兼容

- 现有 `PlayByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc()` 不修改
- 现有 `MatchDetailService.getPlayByPlay()` 方法不修改
- 新字段通过 `PlayByPlayDto` 暴露，现有调用者不受影响
- 现有 `PlayByPlay` 实体只新增字段，不修改现有字段

## 9. 文件清单

### Backend Java 新增文件

```
backend/src/main/java/com/nbamanager/
  domain/
    PlayByPlaySyncLog.java
    GameMomentumSnapshot.java
    GameTurningPoint.java
    GameHighlight.java
    AiReplayExplanation.java
    AiGameSummary.java
    AiConversationMemory.java
  domain/provider/
    ProviderType.java
    PlayByPlayFetchResult.java
    PlayByPlayEvent.java
    PlayByPlayFetchOptions.java
    PlayByPlayProvider.java (interface)
  repository/
    PlayByPlaySyncLogRepository.java
    GameMomentumSnapshotRepository.java
    GameTurningPointRepository.java
    GameHighlightRepository.java
    AiReplayExplanationRepository.java
    AiGameSummaryRepository.java
    AiConversationMemoryRepository.java
  service/provider/
    PlayByPlayProviderManager.java
    NbaPlayByPlayProvider.java
    EspnPlayByPlayProvider.java
  service/
    PlayByPlayCollectorService.java
    PlayByPlayNormalizeService.java
    PlayByPlayValidationService.java
    MomentumComputeService.java
    HighlightDetectService.java
    TurningPointDetectService.java
    PlayByPlaySyncLogService.java
    PlayByPlaySyncScheduler.java
```

### Backend Java 修改文件

```
backend/src/main/java/com/nbamanager/
  domain/PlayByPlay.java          → 新增字段映射
  web/dto/PlayByPlayDto.java      → 新增字段
  web/MatchDetailController.java  → 新增 sync 和 sync-status 接口
```

### SQL

```
backend/src/main/resources/db/migration/
  V1__extend_play_by_play.sql
```

### Python

```
backend/scripts/nba_data_fetcher.py  → 新增 espn_playbyplay action
```

### 测试

```
backend/src/test/java/com/nbamanager/service/
  provider/PlayByPlayProviderManagerTest.java
  provider/NbaPlayByPlayProviderTest.java
  PlayByPlayCollectorServiceTest.java
  PlayByPlayNormalizeServiceTest.java
  MomentumComputeServiceTest.java
```

## 10. 验收标准

1. 指定 `gameId` 可以采集完整 Timeline，每条事件有 `source` 和 `rawPayloadJson`
2. NBA Provider 失败时自动降级到 ESPN Provider
3. 全部失败时记录 sync_log 并安排重试
4. 定时任务自动采集已结束比赛
5. 管理员可通过 API 手动触发同步
6. 现有 `playbyplay` 接口返回数据包含新字段
7. 后端测试全部通过
8. 前端构建通过（无 TypeScript 错误）

## 11. 实现顺序建议

1. PlayByPlay 实体扩展 + ALTER TABLE SQL
2. 7 个新 Entity + Repository
3. Provider 接口 + 枚举 + 值对象
4. NbaPlayByPlayProvider（复用现有 Python 脚本）
5. PlayByPlayNormalizeService + ValidationService
6. PlayByPlayProviderManager
7. PlayByPlayCollectorService
8. MomentumComputeService + HighlightDetectService + TurningPointDetectService
9. PlayByPlaySyncScheduler
10. 管理 API 端点
11. EspnPlayByPlayProvider + Python 脚本扩展
12. 单元测试
13. 集成测试
