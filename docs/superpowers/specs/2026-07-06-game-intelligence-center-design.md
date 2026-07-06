# Game Intelligence Center 比赛智能分析中心设计规格

日期：2026-07-06  
执行对象：DeepSeek  
项目：NBA Manager / Courtside Intelligence  
范围：仅重设计比赛详情页中的「比赛回放」Tab 前端，不改后端 API，不改技术栈。

## 1. 目标

将现有「比赛回放（Game Replay）」升级为「Game Intelligence Center（比赛智能分析中心）」。

它不应只是 Play-by-Play 列表，而应成为类似 ESPN Game Center + NBA App + AI Copilot 的三栏数据分析工作台，核心体验是：

- 左侧快速浏览真实逐回合事件。
- 中间聚焦比赛走势、关键事件和统计对比。
- 右侧固定展示 AI 解说、关键时刻和影响分析。
- 所有事实只来自现有 API 返回的真实数据，AI 仅解释、总结、分析，不创造事实。

## 2. 当前上下文

主要文件：

- `frontend/src/views/MatchDetailView.vue`
- `frontend/src/components/replay/MomentumChart.vue`
- `frontend/src/components/replay/ReplayTimeline.vue`
- `frontend/src/components/replay/TurningPointList.vue`
- `frontend/src/components/replay/HighlightCards.vue`
- `frontend/src/api/aiReplay.ts`
- `frontend/src/api/types.ts`

现有可用 API：

- `fetchTimeline(gameId, period?)`
- `fetchMomentum(gameId)`
- `fetchHighlights(gameId)`
- `fetchTurningPoints(gameId)`
- `fetchGameSummary(gameId)`
- `explainEvent(gameId, eventId)`

现有可用数据：

- `boxScore`
- `quarterScores`
- `timelineEvents`
- `momentumData`
- `highlightsData`
- `turningPointsData`
- `comparisonItems`
- `sortedHomePlayers`
- `sortedAwayPlayers`

## 3. 设计定位

视觉目标：

- Apple HIG 的清晰层级。
- Linear / Vercel Dashboard 的精密、克制、现代。
- NBA App / ESPN 的体育赛事临场感。
- 当前项目 Courtside Intelligence 的深色科技定位。

设计关键词：

- 三栏 Dashboard
- 高信息密度
- 玻璃拟态卡片
- 粘性 AI 面板
- ECharts 专业走势
- 横向故事线卡片
- 深色 / 亮色主题一致
- 响应式

不要做：

- 不要继续上下堆叠所有模块。
- 不要把 Timeline 做成普通列表。
- 不要让 AI 分析只是一个普通文本框。
- 不要新增虚假数据。
- 不要修改后端 API。
- 不要把所有代码堆进一个超大 Vue 文件。

## 4. 页面总体布局

最终结构：

```text
┌────────────────────────────────────────────────────────────────────────────┐
│ Game Intelligence Header                                                   │
│ 比分、球队、状态、日期、场馆、数据源、分享、主题切换                         │
├────────────────────────────────────────────────────────────────────────────┤
│ Sticky Intelligence Tabs                                                    │
│ 回放 / 走势 / 关键转折 / 技术统计 / 球员表现                                  │
├───────────────────────────────┬────────────────────────────────────────────┤
│ 比赛走势 ECharts              │ AI Summary + Win Probability + MVP          │
├───────────────────────┬───────┴────────────────────────────────────────────┤
│ Timeline              │ AI Explain Sticky Panel                            │
│ 真实事件流             │ 当前事件解释、关联事件、影响变化                    │
├───────────────────────┴────────────────────────────────────────────────────┤
│ Turning Points Story Cards                                                  │
├────────────────────────────────────────────────────────────────────────────┤
│ Stat Comparison Bar Compare                                                 │
├────────────────────────────────────────────────────────────────────────────┤
│ Player Performance 横向滚动卡片                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

桌面端推荐 grid：

```css
.game-intel-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(520px, 1.45fr) minmax(340px, 0.95fr);
  gap: 16px;
  align-items: start;
}
```

模块区域：

- 顶部 `GameIntelHeader` 独占一行。
- 第一分析层 `GameTrendCard` 与 `AISummaryCard` 使用 `grid-template-columns: 1.45fr 0.95fr`。
- 主分析层使用三栏：左 Timeline，中间 Insight Stack，右 AI Explain。
- 下方 Story / Stats / Players 使用全宽模块。

响应式：

- `>= 1280px`：三栏布局。
- `900px - 1279px`：两栏布局，Timeline 左侧，AI Explain 右侧，走势和总结全宽上下组合。
- `< 900px`：单列布局，AI Explain 变为事件点击后的底部 Drawer 或内联展开卡片。

## 5. 组件拆分

不要继续扩大 `MatchDetailView.vue`。新增目录：

```text
frontend/src/components/game-intelligence/
  GameIntelHeader.vue
  GameIntelTabs.vue
  GameTrendCard.vue
  AISummaryCard.vue
  ReplayTimelinePanel.vue
  TimelineEventCard.vue
  AIExplainPanel.vue
  KeyMomentsPanel.vue
  TurningPointStoryRail.vue
  TurningPointStoryCard.vue
  StatComparisonPanel.vue
  StatCompareRow.vue
  PlayerPerformanceRail.vue
  PlayerPerformanceCard.vue
  MomentumMiniBar.vue
  ReplayEmptyState.vue
  replayDesignTokens.ts
```

`MatchDetailView.vue` 只保留：

- 数据加载。
- 选中事件状态。
- 将现有数据传给新组件。
- Tab 内容入口。

建议入口：

```vue
<GameIntelligenceCenter
  :game-id="gameId"
  :box-score="boxScore"
  :quarter-scores="quarterScores"
  :timeline-events="timelineEvents"
  :momentum-data="momentumData"
  :highlights="highlightsData"
  :turning-points="turningPointsData"
  :comparison-items="comparisonItems"
  :home-players="sortedHomePlayers"
  :away-players="sortedAwayPlayers"
  :selected-event="selectedTimelineEvent"
  @select-event="selectedTimelineEvent = $event"
/>
```

如果不想新增 `GameIntelligenceCenter.vue`，也可以直接在 `MatchDetailView.vue` 的 `playbyplay` Tab 中组合这些组件，但必须避免单文件膨胀。

## 6. Header 设计

组件：`GameIntelHeader.vue`

职责：

- 展示比赛身份信息。
- 建立 ESPN 式赛事中心顶部。
- 提供主题切换、分享、数据源标签。

字段来源：

- `boxScore.homeTeam`
- `boxScore.awayTeam`
- `homeTotalScore`
- `awayTotalScore`
- `status`
- `queryHomeTeam`
- `queryAwayTeam`
- `homeTeamLogo`
- `awayTeamLogo`
- 若缺少日期、场馆、观众、裁判，不要伪造；显示 `--` 或隐藏该 chip。

布局要求：

- 左：返回按钮 + 页面标题「比赛智能分析」+ subtitle `Game Intelligence Center`。
- 中：主队 Logo/名称/缩写 + 比分 + 状态 + 客队 Logo/名称/缩写。
- 右：主题切换、分享按钮、数据源 chip。
- 比分使用大号数字，`clamp(28px, 4vw, 48px)`。
- 胜方分数和队名高亮。
- 失败方降低透明度。

深色：

- 背景 `linear-gradient(180deg, rgba(11,17,32,.96), rgba(15,23,42,.86))`
- 边框 `rgba(255,255,255,.08)`

亮色：

- 背景 `#FFFFFF`
- 边框 `#E5E7EB`
- 阴影 `0 12px 30px rgba(15,23,42,.06)`

## 7. Sticky Tabs

组件：`GameIntelTabs.vue`

Tab：

- 比赛回放
- 比赛走势
- 关键转折
- 技术统计
- 球员表现

交互：

- 点击 Tab 使用 `element.scrollIntoView` 以外的方式定位。当前项目有 iframe/桌面上下文风险，建议用 `container.scrollTop` 或锚点状态，不要直接用 `scrollIntoView`。
- 当前 section 激活时高亮。
- Header 下方 sticky，`top` 依据现有导航高度调整。

样式：

- 深色：半透明玻璃容器。
- 亮色：纯白底，浅边框，不能出现灰色大块底纹。

## 8. 第一层：比赛走势 + AI Summary

### 8.1 GameTrendCard

组件：`GameTrendCard.vue`

职责：

- 替代现有 `MomentumChart.vue` 或将其升级为 ECharts。
- 用专业体育数据图表达分差走势。

数据：

- `MomentumSnapshot[]`
- `TimelineEvent[]` 用于 hover tooltip 补充事件描述。

ECharts 设计：

- 使用 `line`，`smooth: true`。
- X 轴：比赛进程，可用 `eventOrder` 映射，底部标记 Q1/Q2/Q3/Q4/OT。
- Y 轴：`scoreMargin`。
- 0 线必须清晰。
- 主队领先区域蓝色渐变。
- 客队领先区域橙红渐变。
- 关键节点使用 `markPoint` 或自定义 scatter 发光点。

Tooltip：

- 节次 + 时间。
- 比分。
- 领先方和分差。
- 关联事件描述，若无则只显示分差。

点击：

- 发出 `point-click(snapshot)`。
- 父组件根据 `eventOrder` 找最近 `TimelineEvent`，选中并定位 Timeline。

视觉：

- 图表高度桌面 300-360px。
- 不要密集白点。
- 不要大面积网格线。
- 使用 `axisPointer` 和细虚线。

### 8.2 AISummaryCard

组件：`AISummaryCard.vue`

职责：

- 显示 MIMO AI 生成的比赛摘要。
- 提取胜负因素、MVP、关键转折、胜率变化。

数据：

- `GameSummary`
- `TurningPoint[]`
- `Highlight[]`
- `BoxScore`
- 可从 `sortedHomePlayers/sortedAwayPlayers` 推导 MVP 候选，不要编造真实 MVP。

布局：

- 顶部：AI 标识 `MIMO AI` + 模型 provider。
- 主体：一句 2-3 行摘要。
- 三个 mini card：
  - Turning Point
  - MVP Candidate
  - Win Factor
- 底部：胜率变化条。

胜率变化：

- 如果后端没有真实胜率数据，不要写真实概率。
- 可显示为「比赛控制力」或「走势倾向」。
- 示例文案：`走势倾向：主队 +18`，而不是 `胜率 78%`。
- 如果产品一定要展示胜率，必须标记为 `AI 估算`，并基于分差/时间/主客场明确生成逻辑。

## 9. 第二层：Timeline + AI Explain

### 9.1 ReplayTimelinePanel

组件：`ReplayTimelinePanel.vue`

职责：

- 将现有 `ReplayTimeline.vue` 升级为真正时间轴。
- 左侧为竖向时间线，右侧为事件卡片。

筛选：

- 节次：全部/Q1/Q2/Q3/Q4/OT。
- 类型：得分、关键、犯规、换人、暂停、失误、盖帽、抢断。
- 搜索：球员名、球队名、事件描述。

事件卡片：

- 节次。
- 时间。
- 队伍 Logo。
- 球员名。
- 事件图标。
- 比分。
- 描述。
- 助攻/关联信息如果 API 没有，不展示，不伪造。

事件类型颜色：

- 得分：`#10B981`
- 失误：`#EF4444`
- 暂停：`#F59E0B`
- 换人：`#3B82F6`
- 犯规：`#F97316`
- 盖帽：`#8B5CF6`
- 抢断：`#06B6D4`

交互：

- Hover：轻微上浮，边框高亮。
- Click：选中事件，右侧 AI Explain 更新。
- 选中态：左侧 timeline dot 发光，卡片背景有蓝色光晕。
- 加载更多：如果事件过多，使用虚拟滚动。

性能：

- Timeline 超过 120 条时必须使用虚拟列表。推荐 `vue-virtual-scroller` 若项目已有；若无新增依赖，则实现简单窗口渲染。

### 9.2 AIExplainPanel

组件：`AIExplainPanel.vue`

职责：

- 右侧 sticky 面板。
- 解释当前选中事件。
- 展示事件事实、AI 解读、关联事件和影响变化。

数据：

- `selectedEvent`
- `explainEvent(gameId, eventId)`
- `MomentumSnapshot[]`
- `TimelineEvent[]`

交互：

- 选中事件后自动请求解释。
- 请求中显示 skeleton。
- 失败时显示「AI 解说暂不可用」，但仍展示真实事件事实。
- 缓存每个 eventId 的 explain，避免重复调用。

内容结构：

- Header：AI 赛事解说 + MIMO AI。
- Fact Strip：Q、时间、比分、球队、球员。
- AI Explanation：解释文本。
- Linked Facts：
  - 上一事件。
  - 下一事件。
  - 分差变化。
  - 是否关键事件。
- Actions：
  - 定位到走势点。
  - 查看球员。
  - 查看球队。

必须遵守：

- AI 解释必须引用当前事件和邻近真实事件。
- 没有解释时不生成假解释。

## 10. 第三层：Turning Points + Momentum Bar

### 10.1 TurningPointStoryRail

组件：`TurningPointStoryRail.vue`

职责：

- 将 `TurningPointList.vue` 改为横向 story card。
- 表达比赛故事线，而不是普通列表。

卡片字段：

- 类型：RUN / OVERTAKE / CLUTCH_SEPARATION 等。
- Q + 时间。
- 事实原因。
- AI explanation。
- confidence。
- momentum delta。

视觉：

- 横向滚动。
- 每张卡宽 280-340px。
- 顶部大图标和类型。
- 中间事实。
- 底部 AI 解释。
- 当前选中相关卡片高亮。

点击：

- 根据 `citedEventIdsJson` 尝试定位 Timeline。
- 若引用 ID 与前端 event.id 不一致，则按 period + gameClock 找最近事件。

### 10.2 MomentumMiniBar

组件：`MomentumMiniBar.vue`

职责：

- ESPN 式势能条。
- 按节或按事件片段显示哪方掌控比赛。

实现：

- 从 `momentumData` 计算分段。
- `scoreMargin > 0` 主队蓝色。
- `scoreMargin < 0` 客队橙色。
- 绝对值越大，bar 高度或透明度越强。

不新增后端 API。

## 11. 第四层：技术统计对比

组件：`StatComparisonPanel.vue`

职责：

- 替代普通数据对比网格。
- 用左右 bar compare 表达双方统计。

数据：

- 直接使用现有 `comparisonItems`。
- 可补充：
  - 得分
  - 篮板
  - 助攻
  - 抢断
  - 盖帽
  - 失误
  - 投篮命中率
  - 三分命中率

布局：

```text
主队数值  主队条形  指标名  客队条形  客队数值
```

规则：

- 数值更优的一侧高亮。
- 命中率类使用百分比显示。
- 失误越少越好，必须反向判断。
- Hover 显示详细说明。

## 12. 第五层：球员表现

组件：`PlayerPerformanceRail.vue`

职责：

- 横向滚动展示关键球员。
- 从 box score 中提取双方表现靠前球员。

数据：

- `sortedHomePlayers`
- `sortedAwayPlayers`

排序建议：

- 优先 `points + rebounds * 1.2 + assists * 1.5 + steals * 3 + blocks * 3 - turnovers * 2`。
- 如果有 `plusMinus`，加入 `plusMinus * 0.4`。
- 取前 8-12 名。

卡片字段：

- 球员名。
- 球队。
- 是否首发。
- PTS / REB / AST。
- STL / BLK / TO 可作为小 chip。
- plusMinus。
- 命中率。

交互：

- 点击跳转球员详情。
- Hover 展开更多统计。

不要：

- 不要伪造头像。没有头像时使用姓名首字母或球队色圆形占位。

## 13. 视觉系统

### 深色主题

```css
--gi-bg: #0B1120;
--gi-surface: rgba(15, 23, 42, 0.86);
--gi-surface-strong: #111827;
--gi-border: rgba(255, 255, 255, 0.08);
--gi-text: #F8FAFC;
--gi-muted: #94A3B8;
--gi-home: #3B82F6;
--gi-away: #F97316;
--gi-success: #10B981;
--gi-danger: #EF4444;
--gi-warning: #F59E0B;
--gi-purple: #8B5CF6;
--gi-cyan: #06B6D4;
```

### 亮色主题

```css
--gi-bg: #F8FAFC;
--gi-surface: #FFFFFF;
--gi-surface-strong: #FFFFFF;
--gi-border: #E5E7EB;
--gi-text: #0F172A;
--gi-muted: #64748B;
--gi-home: #2563EB;
--gi-away: #EA580C;
--gi-success: #059669;
--gi-danger: #DC2626;
--gi-warning: #D97706;
--gi-purple: #7C3AED;
--gi-cyan: #0891B2;
```

### 圆角

- 页面大容器：16px。
- 卡片：14px。
- 按钮 / chip：10px 或 999px。
- 不要超过 20px，避免过度圆润。

### 阴影

深色：

```css
box-shadow: 0 18px 50px rgba(2, 6, 23, 0.32);
```

亮色：

```css
box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
```

## 14. Tailwind / CSS 布局方案

如果使用 Tailwind：

```html
<section class="grid gap-4 xl:grid-cols-[minmax(320px,0.9fr)_minmax(520px,1.45fr)_minmax(340px,0.95fr)]">
  <ReplayTimelinePanel class="xl:col-span-1" />
  <div class="grid gap-4">
    <GameTrendCard />
    <StatComparisonPanel />
  </div>
  <AIExplainPanel class="xl:sticky xl:top-24" />
</section>
```

如果使用 scoped CSS：

```css
.game-intel-main {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(520px, 1.45fr) minmax(340px, 0.95fr);
  gap: 16px;
}
```

必须保证：

- 所有固定格式模块有 `min-width: 0`。
- 图表容器有固定 `min-height`。
- Sticky panel 不遮挡 header。
- 移动端不横向溢出。

## 15. Element Plus 使用方案

可用：

- `el-tabs` 保留外层 MatchDetailView 的页面 Tab。
- `el-skeleton` 用于 AI explain loading。
- `el-tooltip` 用于统计说明。
- `el-button` 用于分享、查看完整分析。
- `el-tag` 用于数据源、状态、赛季类型。

避免：

- 不要在核心 Dashboard 内大量使用 `el-card` 默认样式。
- 不要使用 Element Plus 默认灰色 segmented 背景；需要自定义为白色/深色玻璃。
- 不要在视觉主模块中使用普通 table。

## 16. ECharts 规格

组件：`GameTrendCard.vue`

配置要点：

```ts
const option = {
  grid: { left: 36, right: 24, top: 32, bottom: 34 },
  tooltip: { trigger: 'axis', confine: true },
  xAxis: {
    type: 'category',
    axisTick: { show: false },
    axisLine: { lineStyle: { color: theme.border } },
    axisLabel: { color: theme.muted }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: theme.grid } },
    axisLabel: { color: theme.muted }
  },
  series: [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 3 },
      areaStyle: { opacity: 0.18 },
      markLine: { data: [{ yAxis: 0 }] }
    },
    {
      type: 'scatter',
      symbolSize: 10,
      data: importantPoints
    }
  ]
}
```

注意：

- `ResizeObserver` 监听容器尺寸。
- 组件卸载时 dispose。
- 主题切换时重新 setOption。
- 不要每次 hover 都重建 chart。

## 17. 动画设计

页面进入：

- Header fade + translateY。
- 卡片使用 60ms stagger。

卡片：

- Hover `translateY(-2px)`。
- Border color 变亮。
- 深色下轻微 glow。

Timeline：

- 选中事件 dot pulse。
- 新选中卡片轻微闪光一次。

数字：

- 比分使用 CountUp 或 CSS 过渡。
- 如果没有现成库，不要新增重依赖；可用简单 requestAnimationFrame。

无障碍：

- 尊重 `prefers-reduced-motion`。
- 所有可点击卡片使用 button 或 `role="button"` + keyboard。

## 18. 数据映射和派生逻辑

### 18.1 MVP Candidate

不需要后端 API。根据 box score 派生：

```ts
score = points + rebounds * 1.2 + assists * 1.5 + steals * 3 + blocks * 3 - turnovers * 2 + plusMinus * 0.4
```

显示为 `MVP Candidate`，不要写 `MVP`，除非后端提供正式字段。

### 18.2 Win Factor

从 `comparisonItems` 找双方差距最大的正向指标。

- 得分、篮板、助攻、抢断、盖帽、命中率：高者优。
- 失误：低者优。

显示例：

- `篮板优势 +8`
- `三分命中率领先 6.2%`
- `失误少 4 次`

### 18.3 Turning Point

优先取 `turningPointsData[0]` 或 confidence 最高项。

若没有：

- 从 `highlightsData` 中取关键事件。
- 再没有则显示空态，不伪造。

### 18.4 AI Summary

优先使用 `fetchGameSummary(gameId)`。

如果 `content` 为空：

- 显示「AI 总结暂不可用」。
- 仍展示真实统计卡片。

## 19. 交互流程

### 点击 Timeline 事件

1. 更新 `selectedTimelineEvent`。
2. `AIExplainPanel` 检查本地缓存。
3. 若无缓存，调用 `explainEvent(gameId, event.id)`。
4. 展示 loading skeleton。
5. 成功后展示解释和引用事实。
6. 失败则展示真实事件事实和失败提示。

### 点击走势点

1. 根据 `snapshot.eventOrder` 找最近事件。
2. 设置为 selected event。
3. Timeline 内部滚动到对应卡片。
4. AI Explain 更新。

### 点击 Turning Point

1. 解析 `citedEventIdsJson`。
2. 尝试匹配 Timeline event。
3. 若匹配失败，按 period + gameClock 最近匹配。
4. 选中并定位。

## 20. 空态和异常态

必须覆盖：

- Timeline 无数据。
- Momentum 无数据。
- AI summary 为空。
- AI explain 失败。
- turning points 为空。
- box score 为空。

空态文案要具体：

- `暂无逐回合事件，系统会在赛后自动同步官方 Play-by-Play。`
- `AI 解说暂不可用，但下方事件事实来自官方数据。`
- `暂无关键转折，当前比赛没有检测到明显 Run 或反超节点。`

## 21. 验收标准

视觉验收：

- 页面首屏必须同时看到：Header、走势、AI Summary、Timeline 首屏、AI Explain。
- 不再出现大面积空白。
- 不再只是上下堆叠模块。
- 深色主题下卡片统一、边框统一、阴影统一。
- 亮色主题下所有主要卡片背景为白色，不能出现灰色大块底纹。
- Timeline 必须像真实时间轴，而非普通列表。

功能验收：

- 点击 Timeline 事件后 AI Explain 更新。
- 点击走势图节点后 Timeline 选中对应事件。
- 点击 Turning Point 后定位到相关事件。
- 技术统计对比不使用 table。
- 球员表现可横向滚动。
- 移动端不横向溢出。

性能验收：

- 200 条 Timeline 事件滚动不卡顿。
- ECharts resize 正常。
- 切换暗色/亮色主题后图表和卡片颜色正确。
- 不重复请求同一个事件解释。

构建验收：

- `npm run build` 必须通过。

## 22. 推荐实施顺序

1. 新建 `components/game-intelligence/` 目录和设计 token。
2. 新建 `GameIntelligenceCenter.vue` 容器。
3. 把 MatchDetailView 的 `playbyplay` Tab 替换为新容器。
4. 实现 Header 和 Sticky Tabs。
5. 实现 `GameTrendCard`，优先用 ECharts。
6. 实现 `AISummaryCard`，接入 `fetchGameSummary`。
7. 实现 `ReplayTimelinePanel` 和 `TimelineEventCard`。
8. 实现 `AIExplainPanel`，接入 `explainEvent` 和本地缓存。
9. 实现 `TurningPointStoryRail` 和 `MomentumMiniBar`。
10. 实现 `StatComparisonPanel`。
11. 实现 `PlayerPerformanceRail`。
12. 做暗色/亮色主题统一。
13. 做响应式。
14. 跑 `npm run build`。

## 23. DeepSeek 执行提示词

请按照本 markdown 完成 NBA Manager 比赛详情页中「比赛回放」Tab 的前端重设计，将其升级为 `Game Intelligence Center（比赛智能分析中心）`。

硬性要求：

- 不修改后端 API。
- 不修改技术栈。
- 不伪造任何比赛事实。
- 只使用现有真实数据和现有 API。
- 不把所有实现写进一个超大 Vue 文件，必须拆分组件。
- 保持暗色和亮色主题，亮色主题卡片/筛选容器必须是白色。
- 完成后运行 `npm run build`。

优先修改：

- `frontend/src/views/MatchDetailView.vue`
- 新增 `frontend/src/components/game-intelligence/*`
- 可复用或替换 `frontend/src/components/replay/*`

最终效果要接近：

- Apple HIG 的清晰层级
- Linear / Vercel Dashboard 的精致卡片
- NBA App / ESPN Game Center 的赛事临场感
- AI Copilot 的智能解释中心

