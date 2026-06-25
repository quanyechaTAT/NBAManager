# 首页 Hero 区域重设计 + 全局细节打磨

**日期：** 2026-06-25
**目标：** 视觉升级优先——让首页更好看，功能保持不变
**核心方案：** Apple 风格左右分栏 Hero + 全局间距/色值/动画/响应式打磨

---

## 1. Hero 区域重构

### 当前问题

- 837 行单文件组件，Hero 区域包含 AI Copilot 面板（340px 宽），导致布局拥挤
- 深色渐变背景（`#071A3D` → `#0B1F4D`）视觉层次不够清晰
- 指标数字与标签的对比度不足

### 新设计（Apple 风格左右分栏）

**布局结构：**
```
┌─────────────────────────────────────────────────────┐
│  黑色背景 (#000)                                     │
│  ┌──────────────────────┐  ┌──────────────────────┐ │
│  │  NBA DATA CENTER     │  │  ┌──────┐ ┌──────┐  │ │
│  │  赛季数据中心        │  │  │  6   │ │  30  │  │ │
│  │  2025-26 NBA Season  │  │  │今日比赛│ │活跃球队│  │ │
│  │                      │  │  └──────┘ └──────┘  │ │
│  │  [查看赛程] [AI助手]  │  │  ┌──────┐ ┌──────┐  │ │
│  │                      │  │  │ 487  │ │ 2026 │  │ │
│  │                      │  │  │已索引 │ │当前赛季│  │ │
│  │                      │  │  └──────┘ └──────┘  │ │
│  └──────────────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

**左侧（标题区）：**
- 副标题：`NBA DATA CENTER`，12px，`letter-spacing: 2px`，颜色 `#86868B`
- 主标题：`赛季数据中心`，36px，`font-weight: 700`，颜色 `#F5F5F7`，`letter-spacing: -0.5px`
- 赛季：`2025-26 NBA Season`，15px，颜色 `#86868B`
- 按钮：胶囊样式（`border-radius: 980px`）
  - 主按钮：背景 `#E85D26`，白色文字
  - 次按钮：背景 `rgba(255,255,255,0.08)`，`#F5F5F7` 文字

**右侧（指标网格）：**
- 2x2 网格布局，宽度 240px
- 每个指标卡片：背景 `rgba(255,255,255,0.04)`，`border: 1px solid rgba(255,255,255,0.06)`
- 数字：28px，`font-weight: 700`，`font-family: 'DM Sans'`
- 标签：11px，颜色 `#86868B`
- 圆角：12px

**移除项：**
- AI Copilot 面板（下移到独立区域或保留原位但缩小）
- 渐变背景（改为纯黑）
- 装饰性光效

### 技术实现

```css
.hero-section {
  background: #000;
  padding: 32px 40px;
  display: flex;
  align-items: center;
  gap: 40px;
}

.hero-left {
  flex: 1;
}

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
}
```

---

## 2. 全局细节打磨

### 2.1 间距统一

| 区域 | 间距 | CSS 变量 |
|------|------|----------|
| 区域之间 | 24px | `--space-6` |
| 卡片内部 | 16px | `--space-4` |
| 标题与内容 | 12px | `--space-3` |
| 指标网格间距 | 10px | 自定义 |

### 2.2 色值规范

**移除硬编码色值：**
- `#F05A28` → `var(--accent)`
- `#071A3D` → 移除（Hero 改为纯黑）
- 其他硬编码色值统一使用 CSS 变量

**亮色主题覆盖：**
```css
[data-theme="light"] .hero-section {
  background: #F5F5F7;
}
[data-theme="light"] .metric-card {
  background: rgba(0, 0, 0, 0.04);
  border-color: rgba(0, 0, 0, 0.08);
}
```

### 2.3 动画优化

| 动画 | 实现方式 |
|------|----------|
| 卡片进入 | `fadeInUp` 动画，延迟 0.1s |
| 数字滚动 | count-up 效果（可选，需引入库） |
| 按钮悬浮 | `transform: translateY(-1px)` + `box-shadow` |
| 页面切换 | `page-fade-slide`（已有） |

### 2.4 响应式适配

**960px 以下：**
```css
@media (max-width: 960px) {
  .hero-section {
    flex-direction: column;
    text-align: center;
  }
  .hero-metrics {
    width: 100%;
    max-width: 320px;
  }
}
```

**768px 以下：**
```css
@media (max-width: 768px) {
  .hero-section {
    padding: 24px 20px;
  }
  .hero-left h1 {
    font-size: 28px;
  }
}
```

**480px 以下：**
```css
@media (max-width: 480px) {
  .hero-section {
    padding: 20px 16px;
  }
  .hero-left h1 {
    font-size: 24px;
  }
  .metric-card {
    padding: 12px;
  }
  .metric-value {
    font-size: 22px;
  }
}
```

---

## 3. 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `frontend/src/views/DashboardView.vue` | 重构 | Hero 区域重写，移除 AI Copilot 面板 |
| `frontend/src/styles/global.css` | 修改 | 添加 Hero 相关样式变量 |

---

## 4. 不变项

- 今日比赛区域（保持原样）
- 三栏摘要区（保持原样）
- 六个功能入口（保持原样）
- 数据获取逻辑（保持原样）
- 路由配置（保持原样）

---

## 5. 验收标准

1. Hero 区域在桌面端（1440px）显示为左右分栏布局
2. Hero 区域在移动端（375px）显示为上下堆叠布局
3. 所有色值使用 CSS 变量，无硬编码
4. 间距统一使用 `--space-*` 变量
5. 卡片进入有 `fadeInUp` 动画
6. 按钮悬浮有 `translateY(-1px)` 效果
7. 亮色主题下 Hero 区域正确显示
