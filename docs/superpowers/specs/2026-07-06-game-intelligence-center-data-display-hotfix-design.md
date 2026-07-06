# Game Intelligence Center 数据展示修复方案

日期：2026-07-06

## 背景

比赛详情页的 Game Intelligence Center 已完成基础改造，但当前界面仍存在 3 个影响可信度和完成度的问题：

1. 「AI 赛事分析」正文中出现多余的 `*` 号，疑似 Mimo 返回 Markdown 后被前端按纯文本展示。
2. 「比赛事件」Timeline 中的事件描述仍显示英文，应通过已有 Mimo 翻译链路展示中文。
3. 「球员表现」模块未正确显示球员头像，应与球员列表页面使用同一套 NBA 头像规则。

本方案只修复数据展示链路，不改变比赛事实、不新增 AI 编造逻辑、不重做 Game Intelligence Center 布局。

## 当前代码定位

### 前端

- `frontend/src/components/game-intelligence/AISummaryCard.vue`
  - 展示 `summary.content`
  - 当前直接插值输出，未清洗 Mimo Markdown 标记。

- `frontend/src/components/game-intelligence/ReplayTimelinePanel.vue`
  - 展示 `event.description`
  - 搜索也只检索 `event.description/playerName/teamName`。

- `frontend/src/components/game-intelligence/AIExplainPanel.vue`
  - 事实卡片同样展示 `selectedEvent.description`。

- `frontend/src/components/game-intelligence/PlayerPerformanceRail.vue`
  - `playerAvatarUrl()` 当前直接返回 `null`。
  - 因此只能显示姓名首字母 fallback。

- `frontend/src/views/PlayerListView.vue`
  - 球员列表头像规则为：
    - 主地址：`https://cdn.nba.com/headshots/nba/latest/260x190/{nbaPlayerId}.png`
    - 图片错误时隐藏或使用 fallback。

- `frontend/src/api/types.ts`
  - `TimelineEvent` 当前只有 `description`。
  - `BoxScorePlayer` 当前没有 `nbaPlayerId` 字段。

### 后端

- `backend/src/main/java/com/nbamanager/service/AiReplayService.java`
  - 已注入 `PlayByPlayTranslationService`。
  - `getTimeline()` 已通过 `toTimelineDtos()` 批量调用翻译服务。
  - `explainEvent()` 返回事件事实时已调用 `toTranslatedTimelineDto()`。
  - `buildGameFactsJson()` 已将翻译后的描述传给 Mimo 总结。

- `backend/src/main/java/com/nbamanager/service/PlayByPlayTranslationService.java`
  - 已支持 Mimo 批量翻译。
  - 已有本地 fallback 篮球术语替换。
  - 当前需要确认源码编码和输出结果，避免中文提示词或 fallback 文案乱码。

- `backend/src/main/java/com/nbamanager/web/dto/TimelineEventDto.java`
  - 当前只返回 `description`，没有中英文双字段。

- `backend/src/main/java/com/nbamanager/web/dto/BoxScorePlayerDto.java`
  - 当前没有 `nbaPlayerId`。

## 修复目标

### 目标 1：AI 赛事分析不再显示多余星号

用户看到的 AI 总结应是自然中文段落或干净列表，不应出现：

- `**`
- 行首 `*`
- 行首 `-` 被误作为正文符号
- `###`
- ```markdown 代码围栏

但不能删除比分、时间、球员姓名、球队名等真实事实。

### 目标 2：比赛事件描述显示中文

Timeline、AI Explain 事实卡片、事件搜索都优先使用中文事件描述。

例如：

- 英文原文：`Donovan Mitchell makes 3PT Jump Shot`
- 页面展示：`Donovan Mitchell 命中三分跳投` 或 Mimo 输出的更自然中文

如果 Mimo 不可用，允许 fallback 混合翻译，但不能退回全英文。

### 目标 3：球员表现头像与球员列表一致

球员表现 Top 10 卡片应使用与球员列表一致的头像来源：

```text
https://cdn.nba.com/headshots/nba/latest/260x190/{nbaPlayerId}.png
```

没有 `nbaPlayerId` 或图片加载失败时，才显示姓名首字母 fallback。

## 推荐实现方案

采用“后端保证数据契约，前端提供展示兜底”的方式修复。

不要只在前端写临时替换逻辑，否则后续 AI Explain、Highlights、Turning Points 仍可能出现同类问题。

## 任务一：清洗 AI 总结中的 Markdown 噪声

### 后端清洗

在 `AiReplayService` 中新增私有方法：

```java
private String sanitizeAiText(String text)
```

规则：

1. `null` 直接返回 `null`。
2. 删除代码围栏：
   - ```markdown
   - ```
3. 删除 Markdown 粗体/斜体标记：
   - `**文本**` -> `文本`
   - `*文本*` -> `文本`
4. 删除每行开头的列表符号，但保留正文中的乘号、比分和正常字符：
   - `* 关键节点` -> `关键节点`
   - `- 关键节点` -> `关键节点`
   - `• 关键节点` -> `关键节点`
5. 删除标题符号：
   - `### 比赛总结` -> `比赛总结`
6. 合并过多空行，最多保留一个空行。

应用位置：

- `getSummary()` 读取缓存时也要清洗：
  - 避免数据库里已有旧缓存继续显示 `*`。
- `getSummary()` Mimo 新生成后、保存前清洗：
  - 避免新缓存污染。
- `explainEvent()` 读取缓存和新生成后也建议复用同一清洗方法：
  - 防止 AI Explain 后续也出现 Markdown 噪声。

### 前端兜底

在 `AISummaryCard.vue` 中增加轻量展示函数：

```ts
function displayAiText(text?: string | null): string
```

仅做展示兜底：

- 去除 `**`
- 去除行首 `*` / `-` / `•`
- trim

模板改为：

```vue
<p v-if="displaySummary" class="summary-text">{{ displaySummary }}</p>
```

不要使用 `v-html`，避免引入 XSS 风险。

## 任务二：比赛事件描述接入 Mimo 翻译结果

### 数据契约升级

建议升级 `TimelineEventDto`，保留原始英文，新增中文字段：

```java
public record TimelineEventDto(
    Long id,
    Integer period,
    String gameClock,
    String eventType,
    String actionType,
    String description,
    String descriptionEn,
    String descriptionCn,
    String playerName,
    Long playerId,
    String teamName,
    Long teamId,
    Integer homeScore,
    Integer awayScore,
    Integer scoreMargin,
    Boolean isScoringPlay,
    Boolean isHighlightCandidate,
    Integer eventOrder,
    String source
) {}
```

兼容策略：

- `description`：继续作为前端默认展示字段，但必须填中文。
- `descriptionEn`：原始 `PlayByPlay.description`。
- `descriptionCn`：Mimo 或 fallback 翻译后的中文。

这样不会破坏现有前端，同时为后续“显示原文/翻译对照”留下能力。

### 后端映射规则

修改 `AiReplayService.toTimelineDto(PlayByPlay p, String translatedDescription)`：

```java
String original = p.getDescription();
String translated = translatedDescription;
if (translated == null || translated.isBlank()) {
    translated = translationService.translateText(original);
}

return new TimelineEventDto(
    p.getId(),
    p.getPeriod(),
    p.getGameClock(),
    p.getEventType(),
    p.getActionType(),
    translated,
    original,
    translated,
    ...
);
```

### 翻译服务检查点

检查 `PlayByPlayTranslationService.java` 文件编码和字符串内容：

- 确保源码以 UTF-8 保存。
- 确保 `buildSystemPrompt()` 和 `buildUserPrompt()` 中的中文不是乱码。
- 确保 `fallbackTranslate()` 中的中文术语不是乱码。

如果文件中已经出现乱码，应直接修复为正常中文，例如：

```java
private String buildSystemPrompt() {
    return """
            你是 NBA Play-by-Play 数据翻译器。只把输入的逐回合事件文案翻译成简体中文。
            规则：
            1. 不新增任何比赛事实，不改比分、时间、球员、球队、节次。
            2. 球员名和球队名如无确定通用中文译名，可以保留英文。
            3. 保留原数组顺序，输出长度必须与输入一致。
            4. 只输出 JSON 字符串数组，不要 Markdown，不要解释。
            """;
}
```

fallback 建议术语：

| 英文 | 中文 |
|---|---|
| makes | 命中 |
| misses | 投篮不中 |
| 3PT | 三分 |
| 2PT | 两分 |
| Jump Shot | 跳投 |
| Layup | 上篮 |
| Dunk | 扣篮 |
| Free Throw | 罚球 |
| REBOUND | 篮板 |
| Def: | 防守： |
| Off: | 进攻： |
| BLOCK | 盖帽 |
| STEAL | 抢断 |
| Turnover | 失误 |
| Foul | 犯规 |
| Timeout | 暂停 |

### 前端展示规则

更新 `frontend/src/api/types.ts`：

```ts
export interface TimelineEvent {
  ...
  description: string
  descriptionEn?: string
  descriptionCn?: string
  ...
}
```

新增工具函数，建议放在 `frontend/src/utils/replayText.ts`：

```ts
import type { TimelineEvent } from '@/api/types'

export function getTimelineDescription(event: TimelineEvent): string {
  return event.descriptionCn || event.description || event.descriptionEn || ''
}
```

替换以下位置：

- `ReplayTimelinePanel.vue`
  - 卡片描述：`{{ getTimelineDescription(event) }}`
  - 搜索匹配：同时搜索中文、英文、球员名、球队名。

- `AIExplainPanel.vue`
  - 事实描述：`{{ getTimelineDescription(selectedEvent) }}`

验收时直接打开浏览器 Network：

- `/api/ai-replay/games/{gameId}/timeline`
- 随机检查 5 条 `description` 或 `descriptionCn`
- 不应是全英文。

## 任务三：球员表现头像复用球员列表规则

### 后端 DTO 增加 nbaPlayerId

修改 `BoxScorePlayerDto`：

```java
public record BoxScorePlayerDto(
    Long playerId,
    Long nbaPlayerId,
    String playerName,
    ...
) {}
```

需要同步修改所有创建 `BoxScorePlayerDto` 的地方。

数据来源优先级：

1. 如果 `GameBoxScore` 实体已有 `nbaPlayerId`，直接使用。
2. 如果没有，在 BoxScore 查询组装时根据内部 `playerId` 查 `Player` 表，取 `Player.nbaPlayerId`。
3. 如果仍不存在，返回 `null`，前端使用 fallback。

不要把内部数据库 `playerId` 当成 NBA CDN 的头像 ID。NBA CDN 需要的是 `nbaPlayerId`。

### 前端类型更新

修改 `frontend/src/api/types.ts`：

```ts
export interface BoxScorePlayer {
  playerId: number
  nbaPlayerId?: number | null
  ...
}
```

### 前端头像实现

修改 `PlayerPerformanceRail.vue`：

```ts
const brokenAvatarIds = ref<Set<number>>(new Set())

function playerAvatarUrl(player: BoxScorePlayer): string | null {
  if (!player.nbaPlayerId) return null
  if (brokenAvatarIds.value.has(player.nbaPlayerId)) return null
  return `https://cdn.nba.com/headshots/nba/latest/260x190/${player.nbaPlayerId}.png`
}

function onAvatarError(player: BoxScorePlayer) {
  if (player.nbaPlayerId) {
    brokenAvatarIds.value = new Set([...brokenAvatarIds.value, player.nbaPlayerId])
  }
}
```

模板：

```vue
<img
  v-if="playerAvatarUrl(player)"
  :src="playerAvatarUrl(player)!"
  :alt="player.playerName"
  loading="lazy"
  @error="onAvatarError(player)"
/>
<span v-else class="avatar-initials">{{ initials(player.playerName) }}</span>
```

视觉要求：

- 头像容器保持当前 44x44，不要撑高卡片。
- `object-fit: cover` 保持。
- fallback 样式保留主客队颜色区分。
- 头像加载失败后应切回 initials，而不是留下破图图标。

## 数据迁移与缓存处理

### AI 总结旧缓存

由于 `AiGameSummary` 中可能已缓存带 `*` 的旧内容，所以必须满足至少一个条件：

1. 读取缓存时动态 `sanitizeAiText()`，推荐。
2. 或提供一次性 SQL 清理旧缓存，不推荐作为唯一方案。

推荐读取时清洗，这样无需手动删库。

### Timeline 翻译缓存

`PlayByPlayTranslationService` 当前是内存缓存。

本次不要求新增数据库翻译缓存，但必须保证：

- Mimo 翻译失败时 fallback 不是乱码。
- 多条事件批量翻译时返回顺序保持一致。
- 已经是中文的描述不再送 Mimo。

## 测试要求

### 后端单元测试

新增或更新：

1. `PlayByPlayTranslationServiceTest`
   - Mimo 成功时返回中文数组。
   - Mimo 失败时 fallback 中文术语正常，无乱码。
   - 已是中文时不调用 Mimo。

2. `AiReplayServiceTest`
   - `getTimeline()` 返回的 `description` 和 `descriptionCn` 为中文。
   - `descriptionEn` 保留英文原文。
   - `getSummary()` 对缓存内容也会清洗 Markdown 星号。

3. BoxScore 相关测试
   - `BoxScorePlayerDto` 返回 `nbaPlayerId`。
   - 没有映射时返回 `null`。

### 前端验证

运行：

```bash
cd frontend
npm run build
```

手动验收：

1. 打开比赛详情页 Game Intelligence Center。
2. AI 赛事分析正文不出现多余 `*`、`**`、`###`。
3. Timeline 事件描述为中文或中英混合 fallback，不应整句英文。
4. 点击任意 Timeline 事件，右侧 AI Explain 的事实描述也为中文。
5. 球员表现 Top 10 显示真实 NBA 头像。
6. 没有 `nbaPlayerId` 或头像加载失败时显示姓名首字母 fallback。

## 不做事项

本次不要做以下事情：

- 不重构 Game Intelligence Center 整体布局。
- 不改比赛事实生成逻辑。
- 不让前端调用 Mimo。
- 不在前端用静态字典完整翻译 Play-by-Play。
- 不把数据库内部 `playerId` 当作 NBA 官方头像 ID。
- 不使用 `v-html` 渲染 AI 内容。

## DeepSeek 执行提示词

请根据以下要求修复 NBAManager 项目的 Game Intelligence Center 数据展示问题。

修复范围：

1. AI 赛事分析中有多余 `*`、`**`、`###` 等 Markdown 噪声，需要清理。
2. 比赛事件 Timeline 和 AI Explain 事实卡片中的事件描述应显示中文，需要接入已有 Mimo 翻译链路。
3. 球员表现 Top 10 中未正确显示球员头像，需要复用球员列表页面的 NBA 头像规则。

请优先修改这些文件：

- `backend/src/main/java/com/nbamanager/service/AiReplayService.java`
- `backend/src/main/java/com/nbamanager/service/PlayByPlayTranslationService.java`
- `backend/src/main/java/com/nbamanager/web/dto/TimelineEventDto.java`
- `backend/src/main/java/com/nbamanager/web/dto/BoxScorePlayerDto.java`
- 所有创建 `BoxScorePlayerDto` 的后端 service/controller
- `frontend/src/api/types.ts`
- `frontend/src/components/game-intelligence/AISummaryCard.vue`
- `frontend/src/components/game-intelligence/ReplayTimelinePanel.vue`
- `frontend/src/components/game-intelligence/AIExplainPanel.vue`
- `frontend/src/components/game-intelligence/PlayerPerformanceRail.vue`

关键约束：

- 不能编造任何比赛事实。
- 不能让前端直接调用 Mimo。
- `description` 继续作为兼容字段，但应返回中文展示文本。
- 新增 `descriptionEn` 保存英文原文，新增 `descriptionCn` 保存中文翻译。
- `BoxScorePlayerDto` 必须新增 `nbaPlayerId`，前端头像 URL 使用 `https://cdn.nba.com/headshots/nba/latest/260x190/{nbaPlayerId}.png`。
- 图片加载失败时显示姓名首字母 fallback。
- AI 文本清洗不能删除比分、时间、球员、球队等事实。
- 不使用 `v-html`。

完成后请运行：

```bash
cd frontend
npm run build
```

并运行后端相关测试，至少覆盖：

- PlayByPlay 翻译服务
- AI Replay timeline 输出
- AI summary Markdown 清洗
- BoxScorePlayerDto 的 `nbaPlayerId`

最终验收标准：

- AI 赛事分析无多余星号。
- 比赛事件描述为中文。
- AI Explain 中的事实描述为中文。
- 球员表现头像与球员列表一致。
- 构建通过，无 TypeScript 错误。
