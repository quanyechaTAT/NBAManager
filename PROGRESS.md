# NBA Manager 六大功能开发进度

> 最后更新：2026-06-01
> 当前分支：main

## 总体进度

| 阶段 | 功能 | 状态 |
|------|------|------|
| 阶段一 | 功能1：接入 NBA 官方实时数据源 | ✅ 完成 |
| 阶段一 | 功能2：扩展球员数据模型（15+字段） | ✅ 完成 |
| 阶段一 | 功能3：新增比赛详情页 | ✅ 完成 |
| 阶段一 | 功能4：新增球员详情页 | ✅ 完成 |
| 阶段一 | 功能4.5：新闻中文翻译 + 赛事资讯优化 | ✅ 完成 |
| 阶段二 | 功能5：虎扑级社区功能 | ⏳ 待实现 |
| 阶段二 | 功能6：个性化关注 + 推送通知 | ⏳ 待实现 |

---

## 已完成功能详情

### 功能1：接入 NBA 官方实时数据源

**Python 脚本**：`backend/scripts/nba_data_fetcher.py`
- 使用 `nba_api` 库（`pip install nba_api`）替代 ESPN API
- 支持子命令：`all`、`teams`、`players`、`games`、`today`、`boxscore <gameId>`、`playbyplay <gameId>`、`player_career <playerId>`、`player_gamelog <playerId> [season]`
- 自动处理 Windows 编码问题（UTF-8）

**定时同步服务**：
- `backend/.../config/SchedulerConfig.java` — 启用 `@EnableScheduling`
- `backend/.../service/NbaLiveSyncService.java`：
  - `@Scheduled(cron = "0 0 3 * * ?")` — 每日凌晨3点全量同步
  - `@Scheduled(fixedRate = 300000)` — 每5分钟同步今日比赛比分
  - 自动更新 GameNews 和 MatchRecord 的比分和状态

**NbaDataSyncService 更新**：球员同步支持 15+ 扩展字段

### 功能2：扩展球员数据模型

**Player.java 新增字段**：
- `gamesPlayed` (Integer) — 出场次数
- `minutesPerGame` (Double) — 场均上场时间
- `fieldGoalPct` (Double) — 投篮命中率
- `threePointPct` (Double) — 三分命中率
- `freeThrowPct` (Double) — 罚球命中率
- `blocksPerGame` (Double) — 场均盖帽
- `turnoversPerGame` (Double) — 场均失误
- `efficiency` (Double) — 效率值
- `trueShootingPct` (Double) — 真实命中率
- `usagePct` (Double) — 使用率
- `jerseyNumber` (Integer) — 球衣号码
- `height` (String, 32) — 身高
- `weight` (Integer) — 体重（磅）
- `country` (String, 64) — 国籍

**前端更新**：
- `PlayerListView.vue`：表格新增列（球衣、盖帽、命中率、三分、效率），表单分组（基本信息/场均数据/命中率），Excel 导出包含全部字段
- `types.ts` / `player.ts`：同步新增字段

### 功能3：比赛详情页

**新增实体**：
- `GameBoxScore.java` — Box Score 数据（24字段）
- `PlayByPlay.java` — 逐回合事件（11字段）
- `GameNews.java` — 新增 `status` 字段（SCHEDULED/LIVE/FINISHED）

**新增 API**：
- `GET /api/match-detail/{gameId}/boxscore` — 双方球员 Box Score
- `GET /api/match-detail/{gameId}/playbyplay` — Play-by-play 事件列表
- `GET /api/match-detail/{gameId}/quarters` — 逐节比分

**前端**：
- `MatchDetailView.vue`：比赛头部（比分+状态）、逐节比分表、Tab切换（统计/比赛进程）
- `NewsView.vue`：比赛卡片添加「查看比赛详细数据」按钮
- `TeamDetailView.vue`：对战记录添加详情跳转按钮

### 功能4：球员详情页

**新增实体**：
- `PlayerGameLog.java` — 球员比赛日志
- `PlayerCareerStats.java` — 球员生涯数据

**新增 API**：
- `GET /api/players/{id}/detail` — 球员完整信息
- `GET /api/players/{id}/career` — 生涯逐年数据
- `GET /api/players/{id}/gamelog` — 近期比赛日志（分页）

**前端**：
- `PlayerDetailView.vue`：球员头部卡片、关键统计卡片、数据雷达图（ECharts RadarChart）、生涯趋势图（ECharts LineChart）、比赛日志表格
- `PlayerListView.vue`：球员姓名添加点击跳转

### 功能4.5：新闻中文翻译 + 赛事资讯优化

**翻译优化** (`backend/scripts/translator.py`)：
- 新增批量翻译功能 `translate_batch()`：多条文本合并为单次 API 调用
- 内存缓存机制：避免重复翻译相同文本
- API 调用限流：防止触发速率限制
- 优化系统提示词：更专业的 NBA 新闻翻译

**本地映射增强** (`backend/scripts/nba_data_fetcher.py`)：
- 新增 `NBA_TERM_MAP`：50+ 常见 NBA 术语中英文映射（三双、绝杀、加时赛等）
- 扩展 `PLAYER_CN_MAP`：增加常用简称映射（如 Curry → 库里、Durant → 杜兰特）
- 优化 `translate_with_mapping()`：球员名 → 球队名 → NBA 术语三级翻译
- 新闻获取改为批量翻译：`fetch_nba_news()` 先本地映射，再批量 API 翻译

**赛事资讯与比赛数据关联** (`NbaLiveSyncService.java`)：
- 今日赛事同步创建 `GameNews` 记录（包括 SCHEDULED 比赛）
- 比赛进行中/已结束时实时更新标题为比分格式
- 新增 `backfillNbaGameId()`：自动从 MatchRecord 补充新闻缺失的 nbaGameId
- ESPN 新闻已存在时补充缺失的 nbaGameId

**前端优化** (`NewsView.vue`)：
- 今日赛事卡片：LIVE 状态实时显示比分
- 新增「比赛详细数据」按钮（今日卡片 + 列表 + 详情弹窗三处入口）
- 列表新增「比赛数据」列，有 nbaGameId 的新闻可直接跳转比赛详情
- 详情弹窗：无 nbaGameId 时显示禁用按钮和提示
- 新增今日赛事刷新按钮

---

## 阶段二实施方案（待实现）

### 功能5：虎扑级社区功能

**需要新建的后端文件**：

| 实体 | 字段 |
|------|------|
| `Post` | id, userId, title, content(TEXT), category(DISCUSSION/NEWS/DEBATE/PREDICTION), tags, viewCount, likeCount, commentCount, isTop, createTime, updateTime |
| `Comment` | id, postId, userId, parentId(支持盖楼), content, likeCount, createTime |
| `PostLike` | id, postId, userId, createTime（联合唯一约束） |
| `Favorite` | id, userId, targetType(POST/PLAYER/TEAM), targetId, createTime |
| `Poll` | id, postId, question, options(JSON), endTime |
| `PollVote` | id, pollId, userId, optionIndex, createTime |
| `PlayerRating` | id, playerId, userId, gameId, rating(1-10), comment, createTime |
| `DebateSide` | id, postId, side(A/B), userId, createTime |

**需要新建的 Service**：PostService, CommentService, PollService, PlayerRatingService, DebateService

**需要新建的 Controller**：
- `PostController` `/api/posts` — CRUD + 点赞 + 收藏 + 热帖排行
- `CommentController` `/api/comments` — CRUD + 盖楼查询 + 评论点赞
- `PollController` `/api/polls` — 创建投票 + 投票 + 查看结果
- `PlayerRatingController` `/api/player-ratings` — 打分 + 查看评分
- `DebateController` `/api/debates` — 创建辩论 + 选择立场 + 查看比分

**需要新建的前端文件**：
- `api/community.ts` — 社区 API
- `views/CommunityView.vue` — 社区主页面（帖子列表 + 分类Tab + 发帖按钮 + 热帖侧栏）
- `views/PostDetailView.vue` — 帖子详情（内容 + 评论区 + 投票 + 辩论）
- `components/PostCard.vue` — 帖子卡片组件
- `components/CommentTree.vue` — 评论树组件（盖楼嵌套）
- `components/PollWidget.vue` — 投票组件
- `components/DebateWidget.vue` — 辩论组件
- `components/PlayerRatingWidget.vue` — 球员评分组件

**路由**：
```typescript
{ path: 'community', name: 'community', component: () => import('@/views/CommunityView.vue'), meta: { title: '社区', requiresAuth: true } }
{ path: 'community/post', name: 'post-detail', component: () => import('@/views/PostDetailView.vue'), meta: { title: '帖子详情', requiresAuth: true } }
```

**侧边栏**：在 `MainLayout.vue` 的 el-menu 中添加「社区」菜单项

### 功能6：个性化关注 + 推送通知

**需要新建的后端文件**：

| 实体 | 字段 |
|------|------|
| `UserFavorite` | id, userId, targetType(TEAM/PLAYER), targetId, createTime |
| `Notification` | id, userId, type(GAME_START/SCORE_UPDATE/COMMENT_LIKE/POST_REPLY/SYSTEM), title, content, relatedId, isRead, createTime |

**需要新建的 Service**：UserFavoriteService, NotificationService, NotificationDispatchService

**需要新建的 Controller**：
- `FavoriteController` `/api/favorites` — 关注/取消关注球队和球员
- `NotificationController` `/api/notifications` — 通知列表、未读计数、标记已读

**需要新建的前端文件**：
- `api/favorite.ts` — 关注 API
- `api/notification.ts` — 通知 API
- `stores/notification.ts` — Pinia store（通知列表、未读计数、轮询）
- `components/NotificationBell.vue` — 通知铃铛组件

**需要修改的前端文件**：
- `MainLayout.vue` — 添加社区菜单项 + 通知铃铛
- `DashboardView.vue` — 添加「关注动态」区域
- `TeamDetailView.vue` — 添加关注按钮
- `PlayerDetailView.vue` — 添加关注按钮
- `PlayerListView.vue` — 添加关注图标
- `TeamListView.vue` — 添加关注图标

---

## 技术栈和模式参考

### 后端模式
- 实体：`@Entity @Table @Getter @Setter @NoArgsConstructor`，`@Id @GeneratedValue(IDENTITY)`
- Repository：`extends JpaRepository<Entity, Long>`，derived query methods
- Service：`@Service @RequiredArgsConstructor`，`@Cacheable`/`@CacheEvict`/`@Transactional`
- Controller：`@RestController @RequestMapping("/api/xxx")`，`@PreAuthorize("hasRole('ADMIN')")` on writes
- DTO：Java `record`，`XxxDto`（响应）/ `XxxRequest`（请求，带验证注解）

### 前端模式
- 视图：`<script setup lang="ts">`，Composition API，Element Plus
- API：`fetch*`/`create*`/`update*`/`delete*` 命名，`request.get<T>()`
- 路由：懒加载，嵌套在 MainLayout 下，`meta: { title, requiresAuth }`
- 样式：CSS 变量（`global.css`），暗色主题，`.animated-bg` + `.bg-particles` + `.bg-grid`

### 代码库关键路径
- 后端根目录：`E:/Visual Studio Code/NBAManager/backend/`
- 前端根目录：`E:/Visual Studio Code/NBAManager/frontend/`
- Python 脚本：`backend/scripts/nba_data_fetcher.py`
- 全局样式：`frontend/src/styles/global.css`
- 路由配置：`frontend/src/router/index.ts`
- 侧边栏布局：`frontend/src/layouts/MainLayout.vue`

### 依赖
- Python：`pip install nba_api`（NBA 官方 API 封装）
- 后端：Spring Boot 3.2.5, Java 17, MySQL 8.0, Redis
- 前端：Vue 3.4, Vite 5.2, Element Plus, ECharts, Pinia
