# 首页球员头像 + 数据缓存设计

**日期：** 2026-06-26
**目标：** 为首页得分榜添加球员头像，并优化数据加载体验

---

## 1. 球员头像

### 数据源
- 使用 NBA 官方 CDN：`https://ak-static.cms.nba.com/wp-content/uploads/headshots/nba/latest/260x190/${nbaPlayerId}.png`
- 与球员详情页使用相同的头像获取逻辑

### 后端改动
- `TopScorerRow` 添加 `nbaPlayerId` 字段
- `DashboardService` 传递 `nbaPlayerId`

### 前端改动
- `topScorers` 类型添加 `nbaPlayerId`
- 球员行添加头像元素（40x40 圆形）
- 加载失败时显示姓名首字母占位

---

## 2. 数据缓存（Stale-While-Revalidate）

### 策略
- 使用 `sessionStorage` 缓存首页数据
- TTL：5 分钟
- 进入页面时先显示缓存数据，后台静默刷新
- 首次加载显示 Skeleton 加载动画

### 缓存结构
```ts
{
  data: DashboardStats,
  timestamp: number
}
```

### 实现
- 创建 `useDashboardCache` composable
- 封装 `fetchDashboardStats` 的缓存逻辑
- 在 `DashboardView.vue` 中使用

---

## 3. 文件变更

| 文件 | 变更 |
|------|------|
| `backend/DashboardStatsDto.java` | `TopScorerRow` 添加 `nbaPlayerId` |
| `backend/DashboardService.java` | 传递 `nbaPlayerId` |
| `frontend/api/types.ts` | 类型添加 `nbaPlayerId` |
| `frontend/composables/useDashboardCache.ts` | 新建缓存 composable |
| `frontend/views/DashboardView.vue` | 添加头像 + 使用缓存 |

---

## 4. 验收标准

1. 得分榜每行显示球员头像（40x40 圆形）
2. 头像加载失败时显示姓名首字母
3. 首次进入显示 Skeleton 动画
4. 再次进入显示缓存数据（无 loading）
5. 后台静默刷新更新数据
