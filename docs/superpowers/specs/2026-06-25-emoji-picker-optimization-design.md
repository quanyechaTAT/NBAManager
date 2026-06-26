# EmojiPicker 全面优化设计规格

日期：2026-06-25
状态：已批准

## 概述

将项目中的 `EmojiPicker.vue` 组件从一个基础的表情选择器（57个表情、无分组导航、搜索仅中文、定位粗糙）升级为一个专业的表情面板（~200个表情、Tab 分组导航、最近使用、中英文搜索、智能定位、键盘支持），并通过 `useEmojiPicker` composable 统一管理所有页面的 emoji 状态。

## 当前问题

1. **内容少** — 仅 57 个表情，分 5 组，偏 NBA 主题
2. **无最近使用** — 每次都要重新找
3. **搜索仅支持中文** — 输入"smile"或"😂"本身搜不到
4. **定位逻辑粗糙** — 无 anchor 时固定 `bottom:50px, left:0`；有 anchor 时无边界检测
5. **使用方式冗余** — PostDetailView 有 3 个独立的 `showXxxEmojiPicker` 状态 + 3 个 insert 函数
6. **无键盘支持** — 不能用 Esc 关闭、方向键导航

## 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 优化范围 | 全面优化 | 一次性解决所有痛点 |
| 定位方案 | 智能定位（原生计算） | 不引入额外依赖，覆盖所有边界场景 |
| 状态管理 | useEmojiPicker composable | 消除重复代码，统一 3 个页面的使用方式 |

## 模块设计

### 1. 内容扩充：57 → ~200 个表情

8 个分组，每组通过 Tab 图标切换：

| 分组 | 图标 | 数量 | 说明 |
|------|------|------|------|
| 最近使用 | 🕐 | 动态（最多16） | localStorage 持久化，MRU 顺序 |
| 篮球 | 🏀 | ~20 | 篮球、奖杯、金牌、火焰、闪电、庆祝等 |
| 表情 | 😀 | ~30 | 开心、笑哭、思考、生气、难过、震惊等 |
| 手势 | 👍 | ~25 | 点赞、鼓掌、握手、胜利、祈祷、敬礼等 |
| 爱心 | ❤️ | ~20 | 各色爱心、满分、闪亮、星星 |
| 派对 | 🎉 | ~20 | 派对、彩球、烟花、礼物、蛋糕、气球 |
| 食物 | 🍕 | ~25 | 披萨、汉堡、啤酒、咖啡、爆米花 |
| 动物 | 🐾 | ~15 | 狗、猫、狮子、鹰、熊、独角兽 |

数据结构变更 — 每个表情增加 `en` 字段用于英文搜索：
```typescript
interface EmojiItem {
  char: string    // '😀'
  name: string    // '开心'
  en: string      // 'happy grin smile'
}
```

### 2. 最近使用（localStorage）

- 键名：`nbamanager_emoji_recent`
- 最多保存 16 个
- 每次选择表情时，将其移到列表最前面（MRU 顺序）
- "最近使用"分组始终显示在 Tab 列表的第一个位置
- 首次使用或 localStorage 为空时，显示提示文字"暂无使用记录"
- 清除浏览器缓存时自动重置（无额外清理逻辑）

### 3. 搜索增强

搜索逻辑：
```
name.includes(query) || en.includes(query) || char === query
```

- 中文搜索：输入"开心" → 匹配 `name` 字段
- 英文搜索：输入"smile" → 匹配 `en` 字段
- 表情字符搜索：输入"😂" → 精确匹配 `char` 字段（用于复制粘贴场景）
- 搜索时隐藏分组标题，直接展示匹配结果
- 无匹配时显示"未找到匹配的表情"

### 4. 智能定位

核心规则：
- **始终传入 anchor ref** — 消除无 anchor 的 fallback 逻辑
- 定位算法：
  ```
  top = anchor.bottom + 4
  left = anchor.left
  // 下边界翻转
  if (top + pickerH > viewportHeight) → top = anchor.top - pickerH - 4
  // 右边界翻转
  if (left + pickerW > viewportWidth) → left = anchor.right - pickerW
  // 左边界保护
  if (left < 8) → left = 8
  ```
- 移动端（`<768px`）：弹窗居中 + `max-width: calc(100vw - 32px)`
- 点击外部关闭（保留现有 backdrop 机制）
- 使用 `Teleport to="body"` 确保不被父容器 overflow 裁剪

### 5. Composable 模式

新文件：`src/composables/useEmojiPicker.ts`

```typescript
export function useEmojiPicker() {
  const visible = ref(false)
  const anchor = ref<HTMLElement | null>(null)
  let callback: ((emoji: string) => void) | null = null

  function open(anchorEl: HTMLElement, onInsert: (emoji: string) => void) {
    anchor.value = anchorEl
    callback = onInsert
    visible.value = true
  }

  function onSelect(emoji: string) {
    callback?.(emoji)
    visible.value = false
  }

  function close() {
    visible.value = false
  }

  return { visible, anchor, open, onSelect, close }
}
```

使用方式（以 PostDetailView 为例）：
```vue
<script setup>
const emoji = useEmojiPicker()
</script>

<template>
  <!-- 评论区触发 -->
  <button ref="commentEmojiBtn" @click="emoji.open(commentEmojiBtn, e => newComment += e)">😀</button>
  <!-- 回复触发 -->
  <button ref="replyEmojiBtn" @click="emoji.open(replyEmojiBtn, e => replyContent += e)">😀</button>
  <!-- 编辑触发 -->
  <button ref="editEmojiBtn" @click="emoji.open(editEmojiBtn, e => editForm.content += e)">😀</button>

  <!-- 只需 1 个组件实例 -->
  <EmojiPicker
    :visible="emoji.visible.value"
    :anchor="emoji.anchor.value"
    @select="emoji.onSelect"
    @close="emoji.close"
  />
</template>
```

### 6. 键盘支持

- **Esc** — 关闭弹窗
- **↑ ↓ ← →** — 在表情网格中导航（高亮当前选中项）
- **Enter** — 选择当前高亮的表情
- 打开弹窗时自动聚焦搜索框
- 搜索框有内容时，方向键在搜索结果中导航

## 文件变更清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `src/components/EmojiPicker.vue` | 重写：Tab导航 + 智能定位 + 键盘 + 搜索增强 + 表情数据扩充 |
| 新增 | `src/composables/useEmojiPicker.ts` | composable 状态管理 |
| 修改 | `src/views/PostDetailView.vue` | 3个状态 → 1个 composable，3个组件实例 → 1个 |
| 修改 | `src/views/MatchDetailView.vue` | 2个状态 → 1个 composable，2个组件实例 → 1个 |
| 修改 | `src/views/CommunityView.vue` | 1个状态 → 1个 composable |

## EmojiPicker.vue 内部结构

```
EmojiPicker.vue
├── Tab 栏（最近/篮球/表情/手势/爱心/派对/食物/动物）+ 关闭按钮
├── 搜索框（中英文+表情字符搜索）
├── 内容区（按当前 Tab 显示对应分组，或搜索结果）
│   ├── 分组标题
│   └── 表情网格（按钮，hover 放大，click 选择）
└── 键盘事件处理（Esc/方向键/Enter）
```

## 测试要点

1. **功能测试**
   - 各分组表情正确显示
   - 搜索中文、英文、表情字符均能匹配
   - 最近使用记录正确持久化（刷新页面后仍在）
   - 选择表情后正确插入到目标输入框

2. **定位测试**
   - anchor 在页面中间 → 弹窗在下方
   - anchor 靠近底部 → 弹窗翻转到上方
   - anchor 靠近右侧 → 弹窗左对齐
   - 移动端视口 → 弹窗居中且不溢出

3. **键盘测试**
   - Esc 关闭弹窗
   - 方向键在表情间移动高亮
   - Enter 选择高亮表情
   - 搜索框输入时方向键在结果中导航

4. **回归测试**
   - PostDetailView 评论/回复/编辑三个场景均正常
   - MatchDetailView 评论/回复两个场景均正常
   - CommunityView 发帖场景正常
   - 深色/浅色主题均正常显示
