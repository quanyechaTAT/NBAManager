# 首页 Hero 区域重设计 + 全局细节打磨 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 DashboardView.vue 的 Hero 区域重构为 Apple 风格左右分栏布局，移除 AI Copilot 面板，并统一全局间距/色值/动画/响应式

**架构：** 保持单文件组件结构，仅修改 Hero 区域的模板和样式。移除 AI Copilot 面板（下移到独立页面），将 Hero 改为纯黑背景 + 左右分栏布局，右侧为 2x2 指标网格。

**技术栈：** Vue 3 + TypeScript + Scoped CSS，使用现有设计 token 系统

---

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `frontend/src/views/DashboardView.vue` | 重构 | Hero 区域模板重写 + 样式重写 |

---

## 任务 1：重写 Hero 模板

**文件：**
- 修改：`frontend/src/views/DashboardView.vue:1-73`（模板 Hero 部分）

- [ ] **步骤 1：替换 Hero 模板**

将原来的 Hero 区域（包含 AI Copilot 面板）替换为新的 Apple 风格左右分栏布局：

```vue
<!-- 替换第 3-72 行的 hero-section 内容 -->
<section class="hero-section">
  <div class="hero-inner">
    <!-- 左侧：标题 + 按钮 -->
    <div class="hero-left">
      <p class="hero-eyebrow">NBA DATA CENTER</p>
      <h1 class="hero-title">赛季数据中心</h1>
      <p class="hero-season">2025-26 NBA Season</p>
      <div class="hero-actions">
        <button class="hero-btn primary" @click="$router.push('/news')">
          查看赛程
        </button>
        <button class="hero-btn secondary" @click="$router.push('/smart-search')">
          AI 助手
        </button>
      </div>
    </div>

    <!-- 右侧：2x2 指标网格 -->
    <div class="hero-metrics">
      <div class="metric-card">
        <span class="metric-value">{{ todayGames.length }}</span>
        <span class="metric-label">今日比赛</span>
      </div>
      <div class="metric-card">
        <span class="metric-value">{{ stats?.teamCount ?? 30 }}</span>
        <span class="metric-label">活跃球队</span>
      </div>
      <div class="metric-card">
        <span class="metric-value">{{ docCount.toLocaleString() }}</span>
        <span class="metric-label">已索引文档</span>
      </div>
      <div class="metric-card">
        <span class="metric-value">2026</span>
        <span class="metric-label">当前赛季</span>
      </div>
    </div>
  </div>
</section>
```

- [ ] **步骤 2：移除 AI Copilot 相关的 script 变量和方法**

在 `<script setup>` 中移除以下不再使用的变量和函数：

```typescript
// 移除这些变量（如果存在）
const copilotQuery = ref('')

// 移除这些函数（如果存在）
function goToCopilot() { ... }
function copilotAndGo(query: string) { ... }
```

- [ ] **步骤 3：验证模板编译**

运行：`cd frontend && npm run build 2>&1 | head -20`
预期：无 TypeScript 或模板编译错误

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "refactor(dashboard): replace Hero section with Apple-style left-right layout"
```

---

## 任务 2：重写 Hero 样式

**文件：**
- 修改：`frontend/src/views/DashboardView.vue:305-420`（样式 Hero 部分）

- [ ] **步骤 1：替换 Hero 样式**

将原来的 Hero 样式替换为新的 Apple 风格样式：

```css
/* ===== Hero Section — Apple Style ===== */
.hero-section {
  margin-bottom: var(--space-6, 24px);
}
.hero-inner {
  display: flex;
  align-items: center;
  gap: 40px;
  background: #000;
  padding: 32px 40px;
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}
.hero-left {
  flex: 1;
}
.hero-eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: #86868B;
  font-family: var(--font-body);
}
.hero-title {
  margin: 0 0 6px;
  font-size: 36px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.5px;
  color: #F5F5F7;
  font-family: var(--font-heading);
}
.hero-season {
  margin: 0 0 20px;
  font-size: 15px;
  color: #86868B;
  font-family: var(--font-body);
}
.hero-actions {
  display: flex;
  gap: 10px;
}
.hero-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 24px;
  border-radius: 980px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
  font-family: var(--font-body);
}
.hero-btn.primary {
  background: var(--accent);
  color: #fff;
}
.hero-btn.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--accent-glow);
}
.hero-btn.secondary {
  background: rgba(255, 255, 255, 0.08);
  color: #F5F5F7;
}
.hero-btn.secondary:hover {
  background: rgba(255, 255, 255, 0.12);
  transform: translateY(-1px);
}

/* 指标网格 */
.hero-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 240px;
}
.metric-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  transition: all 0.2s ease;
}
.metric-card:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
}
.metric-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  font-family: var(--font-heading);
  color: #F5F5F7;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.metric-label {
  display: block;
  font-size: 11px;
  color: #86868B;
  margin-top: 4px;
  font-family: var(--font-body);
}
```

- [ ] **步骤 2：添加亮色主题覆盖**

在样式末尾添加亮色主题覆盖：

```css
/* ===== Light Theme Hero ===== */
[data-theme="light"] .hero-inner {
  background: #F5F5F7;
}
[data-theme="light"] .hero-eyebrow {
  color: #86868B;
}
[data-theme="light"] .hero-title {
  color: #1D1D1F;
}
[data-theme="light"] .hero-season {
  color: #86868B;
}
[data-theme="light"] .hero-btn.primary {
  background: var(--accent);
  color: #fff;
}
[data-theme="light"] .hero-btn.secondary {
  background: rgba(0, 0, 0, 0.06);
  color: #1D1D1F;
}
[data-theme="light"] .hero-btn.secondary:hover {
  background: rgba(0, 0, 0, 0.1);
}
[data-theme="light"] .metric-card {
  background: rgba(0, 0, 0, 0.04);
  border-color: rgba(0, 0, 0, 0.08);
}
[data-theme="light"] .metric-value {
  color: #1D1D1F;
}
[data-theme="light"] .metric-label {
  color: #86868B;
}
```

- [ ] **步骤 3：验证样式编译**

运行：`cd frontend && npm run build 2>&1 | head -20`
预期：无 CSS 编译错误

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "style(dashboard): add Apple-style Hero CSS with light theme support"
```

---

## 任务 3：添加响应式适配

**文件：**
- 修改：`frontend/src/views/DashboardView.vue`（样式部分末尾）

- [ ] **步骤 1：添加响应式媒体查询**

在样式末尾添加响应式断点：

```css
/* ===== Responsive ===== */
@media (max-width: 960px) {
  .hero-inner {
    flex-direction: column;
    text-align: center;
    padding: 28px 24px;
    gap: 24px;
  }
  .hero-actions {
    justify-content: center;
  }
  .hero-metrics {
    width: 100%;
    max-width: 320px;
  }
}
@media (max-width: 768px) {
  .hero-inner {
    padding: 24px 20px;
  }
  .hero-title {
    font-size: 28px;
  }
  .metric-value {
    font-size: 24px;
  }
}
@media (max-width: 480px) {
  .hero-inner {
    padding: 20px 16px;
  }
  .hero-title {
    font-size: 24px;
  }
  .hero-season {
    font-size: 13px;
  }
  .metric-card {
    padding: 12px;
  }
  .metric-value {
    font-size: 22px;
  }
  .metric-label {
    font-size: 10px;
  }
}
```

- [ ] **步骤 2：验证响应式样式**

运行：`cd frontend && npm run build 2>&1 | head -20`
预期：无编译错误

- [ ] **步骤 3：Commit**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "style(dashboard): add responsive breakpoints for Hero section"
```

---

## 任务 4：清理旧样式

**文件：**
- 修改：`frontend/src/views/DashboardView.vue`（样式部分）

- [ ] **步骤 1：移除不再使用的旧 Hero 样式**

删除以下旧样式（如果仍存在）：

```css
/* 删除这些旧样式 */
.hero-content { ... }
.hero-main { ... }
.hero-main::before { ... }
.hero-badge { ... }
.hero-desc { ... }
.metric-divider { ... }
.copilot-panel { ... }
.copilot-header { ... }
.copilot-icon { ... }
.copilot-title { ... }
.copilot-sub { ... }
.copilot-input-wrap { ... }
.copilot-input { ... }
.copilot-send { ... }
.copilot-chips { ... }
.chip { ... }
```

- [ ] **步骤 2：验证无残留引用**

在 `<template>` 和 `<script>` 中搜索 `copilot` 和 `hero-main` 和 `hero-badge`，确认没有残留引用：
运行：`grep -n "copilot\|hero-main\|hero-badge\|hero-desc\|metric-divider" frontend/src/views/DashboardView.vue`
预期：无输出（无残留引用）

- [ ] **步骤 3：验证构建**

运行：`cd frontend && npm run build 2>&1 | head -20`
预期：无编译错误

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "refactor(dashboard): remove unused Hero and Copilot styles"
```

---

## 任务 5：验证与修复色值

**文件：**
- 修改：`frontend/src/views/DashboardView.vue`（全文件）

- [ ] **步骤 1：搜索硬编码色值**

运行：`grep -n "#F05A28\|#071A3D\|#0B1F4D\|rgba(240, 90, 40" frontend/src/views/DashboardView.vue`
预期：无输出（已全部替换为 CSS 变量）

- [ ] **步骤 2：如有残留硬编码色值，逐一替换**

将 `#F05A28` 替换为 `var(--accent)`
将 `rgba(240, 90, 40, ...)` 替换为 `var(--accent-glow)` 或 `var(--accent-lighter)`

- [ ] **步骤 3：最终构建验证**

运行：`cd frontend && npm run build 2>&1 | tail -5`
预期：`✓ built in xxx ms`

- [ ] **步骤 4：Commit（如有修改）**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "style(dashboard): replace hardcoded colors with CSS variables"
```

---

## 任务 6：最终验收测试

- [ ] **步骤 1：启动开发服务器**

运行：`cd frontend && npm run dev`

- [ ] **步骤 2：桌面端验证（1440px）**

访问 http://127.0.0.1:5173/dashboard，验证：
- Hero 区域为左右分栏布局
- 左侧显示标题、赛季、两个按钮
- 右侧显示 2x2 指标网格
- 背景为纯黑
- 按钮悬浮有效果

- [ ] **步骤 3：移动端验证（375px）**

调整浏览器窗口至 375px 宽度，验证：
- Hero 区域上下堆叠
- 标题居中
- 指标网格变为 2x2 全宽

- [ ] **步骤 4：亮色主题验证**

切换到亮色主题，验证：
- Hero 背景变为浅灰色（#F5F5F7）
- 文字颜色正确
- 指标卡片背景正确

- [ ] **步骤 5：最终 Commit**

```bash
git add -A
git commit -m "feat(dashboard): Apple-style Hero redesign complete"
```
