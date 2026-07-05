# 比赛级投篮图 - 设计规格

日期：2026-07-05
状态：已批准

## 1. 目标

在比赛详情页"投篮图"Tab 中展示真实比赛投篮数据，包含半场球场、投篮点位、筛选器和统计摘要。

### 1.1 成功标准

- 比赛详情页调用 `GET /api/shot-chart/game/{gameId}` 获取投篮数据
- ShotChart.vue 接收真实 shots 数组并渲染投篮点
- 主队/客队、命中/未中筛选可用
- 无数据时显示空状态，不伪造数据
- 前端构建通过，后端编译通过

### 1.2 不在范围内

- 球员级投篮图（已有，不修改）
- 前端直接请求 NBA API
- 模拟/随机投篮数据

## 2. 架构

```
MatchDetailView.vue
  ├── shotchart tab
  │     ├── 筛选器 (team/result)
  │     ├── 统计摘要
  │     └── <ShotChart :shots="filteredGameShots" :size="560" />
  └── loadGameShotChart() → GET /api/shot-chart/game/{gameId}

ShotChartController.java
  └── GET /game/{gameId} → ShotChartService.getGameShots(gameId)

ShotChartService.java
  └── getGameShots(gameId)
        ├── 查 GameShotChartRepository 缓存
        ├── 缓存命中 → 返回
        └── 缓存未命中 → Python fetch_game_shot_chart(gameId)
              → 坐标转换 → 保存缓存 → 返回

nba_data_fetcher.py
  └── fetch_game_shot_chart(game_id)
        └── ShotChartDetail(game_id_nullable=game_id, context_measure_simple='FGA')
```

## 3. 后端

### 3.1 新增 Entity：GameShotChart

```java
@Entity
@Table(name = "game_shot_chart")
public class GameShotChart {
    @Id @GeneratedValue
    private Long id;
    private String gameId;
    private Long teamId;
    private String teamName;
    private Long playerId;
    private String playerName;
    private Integer period;
    private String gameClock;
    private Integer locX;      // NBA 原始坐标
    private Integer locY;
    private Double chartX;     // 转换后坐标 (0-50)
    private Double chartY;     // 转换后坐标 (0-47)
    private Boolean made;
    private String shotType;
    private String actionType;
    private Integer points;
    private Integer distance;
    private String source;
    private LocalDateTime createdAt;
}
```

### 3.2 新增 Repository

```java
public interface GameShotChartRepository extends JpaRepository<GameShotChart, Long> {
    List<GameShotChart> findByGameId(String gameId);
    void deleteByGameId(String gameId);
}
```

### 3.2.1 建表方案

项目使用 `spring.jpa.hibernate.ddl-auto=update`，Hibernate 自动建表。但需手动添加索引：

```sql
-- 执行方式：通过 MySQL 客户端手动执行，或放入 db/migration/V2__game_shot_chart_index.sql
CREATE INDEX idx_gsc_game_id ON game_shot_chart(game_id);
CREATE INDEX idx_gsc_game_team ON game_shot_chart(game_id, team_id);
```

### 3.3 ShotChartService 新增 getGameShots

```java
public Map<String, Object> getGameShots(String gameId) {
    // 1. 查缓存
    List<GameShotChart> cached = repository.findByGameId(gameId);
    if (!cached.isEmpty()) return buildGameResult(cached);

    // 2. 调用 Python
    List<GameShotChart> shots = fetchGameShotsFromApi(gameId);
    if (!shots.isEmpty()) {
        repository.saveAll(shots);
        return buildGameResult(shots);
    }

    // 3. 返回空（禁止模拟数据）
    return createEmptyGameResult("暂无投篮数据，该场比赛尚未同步官方投篮坐标");
}
```

### 3.3.1 API 返回结构

成功时：
```json
{
  "gameId": "0022500001",
  "homeTeam": "尼克斯",
  "awayTeam": "马刺",
  "homeTeamId": 1610612752,
  "awayTeamId": 1610612759,
  "shots": [...],
  "summary": { "totalShots": 87, "madeShots": 41, "fgPct": 0.471, "threeMade": 12, "threeAttempted": 33, "threePct": 0.364 },
  "source": "NBA_SHOTCHARTDETAIL",
  "status": "OK"
}
```

失败/无数据时：
```json
{
  "gameId": "0022500001",
  "homeTeam": "",
  "awayTeam": "",
  "homeTeamId": null,
  "awayTeamId": null,
  "shots": [],
  "summary": { "totalShots": 0, "madeShots": 0, "fgPct": 0, "threeMade": 0, "threeAttempted": 0, "threePct": 0 },
  "source": "NONE",
  "status": "EMPTY",
  "message": "暂无投篮数据，该场比赛尚未同步官方投篮坐标"
}
```

前端筛选逻辑：优先用 teamId 匹配，若 teamId 为 null 则用 teamName 兜底。

### 3.4 ShotChartController 新增端点

```java
@GetMapping("/game/{gameId}")
public ResponseEntity<Map<String, Object>> getGameShots(@PathVariable String gameId) {
    return ResponseEntity.ok(shotChartService.getGameShots(gameId));
}
```

### 3.5 Python fetch_game_shot_chart

```python
def fetch_game_shot_chart(game_id):
    """获取比赛级投篮数据"""
    from nba_api.stats.endpoints import ShotChartDetail
    shots = ShotChartDetail(
        game_id_nullable=game_id,
        context_measure_simple='FGA',
        timeout=60
    )
    # 坐标转换（以篮筐为参考点）:
    # chartX = clamp(25.0 + LOC_X / 10.0, 0, 50)
    # chartY = clamp(5.25 + LOC_Y / 10.0, 0, 47)
    # 失败时返回 {"shots": [], "summary": {...}, "source": "NONE", "status": "EMPTY", "message": "..."}
    # 禁止返回模拟数据
```

### 3.6 坐标转换

以 ShotChart.vue 的篮筐中心 (x=25, y=5.25) 为参考点，NBA LOC_X/LOC_Y 以篮筐为原点：

```java
private double toChartX(int locX) {
    return Math.max(0, Math.min(50, 25.0 + locX / 10.0));
}
private double toChartY(int locY) {
    return Math.max(0, Math.min(47, 5.25 + locY / 10.0));
}
```

LOC_X=0 → chartX=25（篮筐正下方），LOC_X=250 → chartX=50（右侧边线）。
LOC_Y=0 → chartY=5.25（篮筐高度），LOC_Y=420 → chartY=47（中线附近）。

### 3.7 SecurityConfig

添加 `GET /api/shot-chart/**` 到 `permitAll()`。

## 4. 前端

### 4.1 扩展 ShotData 类型

```typescript
export interface ShotData {
  x: number; y: number; made: boolean
  teamId?: number; teamName?: string
  playerId?: number; playerName?: string
  period?: number; gameClock?: string
  shotType?: string; actionType?: string
  points?: number; distance?: number
}
```

### 4.2 新增 API 函数

```typescript
// frontend/src/api/shotChart.ts
export function getGameShotChart(gameId: string) {
  return request.get(`/shot-chart/game/${gameId}`, { timeout: 60000 })
}
```

### 4.3 清理 ShotChart.vue 调试代码

移除 ShotChart.vue 中的 `watch` 调试 `console.log`（第 171-176 行）。

### 4.4 MatchDetailView.vue 改造

替换 shotchart tab 内容：

```html
<el-tab-pane label="投篮图" name="shotchart">
  <div class="shotchart-panel" v-loading="shotChartLoading">
    <!-- 筛选器 -->
    <div class="shotchart-toolbar">
      <el-segmented v-model="shotTeamFilter" :options="teamFilterOptions" />
      <el-segmented v-model="shotResultFilter" :options="resultFilterOptions" />
    </div>
    <!-- 统计摘要 -->
    <div class="shotchart-summary" v-if="gameShots.length">
      <div>投篮 {{ shotSummary.made }}/{{ shotSummary.total }}，{{ shotSummary.fgPct }}%</div>
      <div>三分 {{ shotSummary.threeMade }}/{{ shotSummary.threeAttempted }}，{{ shotSummary.threePct }}%</div>
    </div>
    <!-- 球场 -->
    <ShotChart :shots="filteredGameShots" :size="560" />
  </div>
</el-tab-pane>
```

状态：
```typescript
const shotChartLoading = ref(false)
const gameShots = ref<ShotData[]>([])
const shotTeamFilter = ref<'all' | 'home' | 'away'>('all')
const shotResultFilter = ref<'all' | 'made' | 'missed'>('all')
```

过滤：
```typescript
const filteredGameShots = computed(() => {
  return gameShots.value.filter(s => {
    if (shotTeamFilter.value === 'home' && s.teamId !== homeTeamId.value) return false
    if (shotTeamFilter.value === 'away' && s.teamId !== awayTeamId.value) return false
    if (shotResultFilter.value === 'made' && !s.made) return false
    if (shotResultFilter.value === 'missed' && s.made) return false
    return true
  })
})
```

## 5. 文件清单

| 操作 | 文件 |
|------|------|
| 新增 | `domain/GameShotChart.java` |
| 新增 | `repository/GameShotChartRepository.java` |
| 修改 | `service/ShotChartService.java` — 新增 `getGameShots()` |
| 修改 | `web/ShotChartController.java` — 新增 `GET /game/{gameId}` |
| 修改 | `config/SecurityConfig.java` — 放行 `GET /api/shot-chart/**` |
| 修改 | `scripts/nba_data_fetcher.py` — 新增 `fetch_game_shot_chart()` |
| 修改 | `frontend/src/api/types.ts` — 扩展 ShotData |
| 新增 | `frontend/src/api/shotChart.ts` |
| 修改 | `frontend/src/views/MatchDetailView.vue` — 改造 shotchart tab |

## 6. 验收标准

1. 比赛详情页"投篮图"Tab 调用 `GET /api/shot-chart/game/{gameId}`
2. 有数据时 ShotChart.vue 显示投篮点（绿=命中，红=未中）
3. 无数据时显示"暂无投篮数据"
4. 主队/客队筛选可用
5. 命中/未中筛选可用
6. 统计摘要显示投篮命中率和三分命中率
7. 前端 `npm run build` 通过
8. 后端 `./mvnw compile` 通过
9. 不使用模拟投篮数据
