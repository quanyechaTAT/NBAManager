# AI 助手界面美化重构方案与 DeepSeek 执行提示词

## 1. 背景与目标

当前项目的 AI 助手页面已经具备聊天、历史会话、快捷问题、分享、导出、消息渲染等基础能力，但首页视觉仍偏普通问答页，缺少 NBA 数据助手应有的沉浸感、科技感和专业信息入口。参考设计图的方向是将首页改造成一个更完整的「NBA Copilot / NBA Courtside」式智能助手工作台。

本次任务只重构前端 AI 助手界面，不调整 RAG、LangChain、LangGraph、搜索、后端接口或数据结构。

最终目标：

- 首页首屏就是可用的 AI 助手，不做营销落地页。
- 页面填充整个可视区域，顶部工具栏、主体欢迎区、能力卡片区、底部输入框层次清晰。
- 对话输入框常驻底部，桌面端和移动端都不能漂移、遮挡或溢出。
- 保留现有聊天功能、历史会话、快捷提问、分享、导出、消息来源、相关实体等逻辑。
- 深色和浅色主题都要精致，不能出现白色底纹突兀、文字省略、文字重叠、按钮挤压等问题。
- 文件统一使用 UTF-8 保存，修复当前页面中出现的中文乱码文案。

## 2. 当前项目文件

DeepSeek 需要重点阅读并修改以下文件：

- `frontend/src/views/SmartSearchView.vue`
- `frontend/src/styles/smart-search.css`

可选阅读：

- `frontend/src/router/index.js` 或 `frontend/src/router/index.ts`：确认 AI 助手页面路由。
- `frontend/src/styles/` 下的全局主题文件：确认项目已有 CSS 变量和亮暗主题写法。
- `frontend/public/`、`frontend/src/assets/`：查找是否已有篮球、球场、NBA 风格图片素材。

修改边界：

- 优先在 `SmartSearchView.vue` 的 scoped style 中完成界面重构。
- 如果 `smart-search.css` 仍被项目引用，可以同步清理或迁移必要样式，避免旧样式覆盖新界面。
- 不新增大型 UI 依赖。
- 不修改后端接口。
- 不修改聊天 API 入参、返回结构和已有方法名。

## 3. 设计方向

参考图可以抽象为一个「NBA 智能分析驾驶舱」：

- 整体背景：深色主题下为深蓝黑底，叠加球场线条、柔和光晕和玻璃质感面板；浅色主题下为灰白背景，配合蓝紫橙渐变和轻量阴影。
- 顶部导航：左侧品牌与页面标题，右侧保留新对话、导出、分享、历史会话等操作。
- 中央欢迎区：突出 AI 助手身份、数据规模、能力说明。
- 能力入口：左侧一张大卡片作为主推荐操作，右侧 2x3 或 3x2 小卡片展示常用能力。
- 快捷问题：使用胶囊按钮，放在能力卡片下方或输入框上方。
- 底部输入框：常驻底部，带玻璃拟态容器、输入状态、字数、发送按钮。
- 聊天态：用户发送问题后，欢迎区消失，消息列表占据主体空间，底部输入框仍固定。

## 4. 页面结构方案

### 4.1 根容器

根容器继续使用：

```vue
<div class="copilot-workspace">
```

建议结构：

```text
copilot-workspace
├── sidebar 历史会话侧栏
├── modal 分享弹窗
└── workspace-main
    ├── top-bar 顶部工具栏
    ├── content-area
    │   ├── welcome-screen 空会话首页
    │   │   ├── welcome-hero
    │   │   ├── capabilities
    │   │   │   ├── cap-main
    │   │   │   └── cap-grid
    │   │   └── prompt-chips
    │   └── chat-flow 对话消息流
    └── composer-area 底部输入区
```

保留当前 Vue 响应式状态和方法，例如：

- `msgs`
- `question`
- `loading`
- `send`
- `newChat`
- `exportChat`
- `shareChat`
- `loadConv`
- `delConv`
- `retryLast`
- `cancelQuery`
- `render`
- `autoGrow`
- `handleKeydown`

只允许改视觉结构、class 命名和文案，不要破坏业务逻辑。

### 4.2 桌面端布局

桌面端建议：

- `copilot-workspace` 高度使用 `calc(100vh - 顶部主导航高度)`，如果当前项目主导航为 56px，则保持 `calc(100vh - 56px)`。
- `workspace-main` 使用三段式布局：顶部工具栏固定高度，中间内容弹性滚动，底部输入区固定。
- `top-bar` 高度建议 56px，左右对齐，背景半透明。
- `content-area` 使用 `flex: 1; overflow-y: auto;`。
- `welcome-screen` 最大宽度 1120px 到 1180px，水平居中。
- `capabilities` 使用两栏：
  - 左侧主卡片 `minmax(420px, 1fr)`
  - 右侧小卡片区域 `minmax(360px, 420px)`
- `cap-grid` 使用 2 列 3 行，卡片高度统一，避免 hover 时尺寸变化。
- `composer-area` 固定在页面底部区域，不随内容滚走。
- `composer-wrap` 最大宽度与欢迎区一致，建议 1040px 到 1120px。

### 4.3 移动端布局

移动端建议断点：

- `max-width: 900px`：能力卡片改为单列。
- `max-width: 640px`：顶部按钮只保留图标或压缩文字，快捷卡片横向滚动或两列紧凑布局。
- `composer-area` 底部常驻，并预留移动端底部导航高度，避免被底部 TabBar 遮挡。

移动端要求：

- 欢迎区不要过高，首屏必须能看到输入框。
- 能力卡片可纵向滚动，但输入框始终在底部。
- 按钮文字不能出现省略号，必要时换行。
- 输入框、发送按钮、字数提示不能重叠。

## 5. 视觉规范

### 5.1 深色主题

建议 CSS 变量：

```css
.copilot-workspace {
  --copilot-bg: #050816;
  --copilot-bg-2: #0b1020;
  --copilot-panel: rgba(15, 23, 42, 0.78);
  --copilot-panel-strong: rgba(17, 24, 39, 0.92);
  --copilot-border: rgba(148, 163, 184, 0.18);
  --copilot-border-strong: rgba(129, 140, 248, 0.36);
  --copilot-text: #f8fafc;
  --copilot-muted: #94a3b8;
  --copilot-dim: #64748b;
  --copilot-blue: #3b82f6;
  --copilot-purple: #8b5cf6;
  --copilot-cyan: #22d3ee;
  --copilot-orange: #f97316;
  --copilot-green: #22c55e;
}
```

背景建议：

- 使用深蓝黑径向渐变。
- 叠加细线球场纹理，可用 CSS `linear-gradient` 实现。
- 使用柔和的大面积光晕，但不要使用突兀的圆球装饰。
- 所有卡片使用玻璃拟态：半透明背景、细边框、柔和阴影、轻微 blur。

示例方向：

```css
.copilot-workspace {
  background:
    radial-gradient(circle at 18% 12%, rgba(59, 130, 246, .26), transparent 34%),
    radial-gradient(circle at 82% 8%, rgba(249, 115, 22, .16), transparent 30%),
    linear-gradient(135deg, #050816 0%, #0b1020 52%, #111827 100%);
  color: var(--copilot-text);
}
```

### 5.2 浅色主题

如果项目通过 `data-theme="light"`、`.light` 或 CSS 变量控制主题，DeepSeek 需要沿用现有方式。

建议浅色变量：

```css
[data-theme="light"] .copilot-workspace,
.light .copilot-workspace {
  --copilot-bg: #f6f8fc;
  --copilot-bg-2: #eef2ff;
  --copilot-panel: rgba(255, 255, 255, 0.86);
  --copilot-panel-strong: rgba(255, 255, 255, 0.96);
  --copilot-border: rgba(148, 163, 184, 0.28);
  --copilot-border-strong: rgba(59, 130, 246, 0.28);
  --copilot-text: #0f172a;
  --copilot-muted: #64748b;
  --copilot-dim: #94a3b8;
}
```

浅色主题要求：

- 不要大面积纯白底纹压住主题。
- 按钮、标签、卡片都要有蓝紫橙主题边框或淡色背景。
- 分享按钮、球队标签、位置标签等通用小元素要用主题色透明底，不要保留刺眼白色块。

### 5.3 卡片与按钮

统一规范：

- 大卡片圆角：18px 到 22px。
- 小卡片圆角：14px 到 16px。
- 输入框圆角：18px 到 22px。
- 卡片边框：1px 半透明。
- 按钮 hover：轻微上移 `translateY(-1px)`，边框变亮，背景渐变增强。
- 不使用过厚阴影，不使用单一紫色铺满全页。

主能力卡片 `cap-main`：

- 左上角放图标和标题。
- 中间展示描述文案。
- 右下或右侧放箭头图标。
- 可以加入小型状态信息，例如「今日赛事」「实时数据」「RAG + 搜索」。

小能力卡片 `cap-card`：

- 统一高度，推荐 112px 到 128px。
- 图标在上，标题在下，可加 1 行短描述。
- 内容不允许省略号，必要时换行。

## 6. 首页文案建议

需要修复当前乱码，并使用正常中文。建议文案如下：

品牌：

```text
NBA Copilot
```

主标题：

```text
你的 NBA 数据分析助手
```

副标题：

```text
基于系统知识库、实时赛事数据与联网搜索，为你生成可追溯的篮球分析结论。
```

数据提示：

```text
已连接 {{ docCount.toLocaleString() }} 条知识片段
```

主能力卡片：

```text
今日赛事分析
分析今日比赛的胜负关键、核心对位、伤病影响和战术看点。
```

小能力卡片：

```text
球员对比
球队查询
数据解读
季后赛预测
数据排行
MVP 分析
```

快捷问题：

```text
今天有哪些比赛？
分析湖人最近 5 场表现
库里和东契奇谁的三分更准？
本赛季助攻榜 Top 5
```

输入框占位：

```text
询问任何 NBA 数据、球员、球队、赛程或战术问题...
```

## 7. 聊天态设计

用户发送消息后：

- `welcome-screen` 隐藏。
- `chat-flow` 占据内容区。
- 用户消息靠右，使用蓝紫渐变或主题主色。
- AI 消息靠左，使用半透明卡片，保持较高可读性。
- AI 头像使用小型 `AI` 或篮球图标，避免突兀白底。
- loading 状态保持三点动画，但样式融入主题。
- 来源、相关实体、追问按钮采用主题色淡底和细边框。
- 错误提示使用红色淡底，不要使用纯白卡片。

消息区域要求：

- 最大宽度建议 980px 到 1080px。
- 长文本自动换行。
- Markdown 渲染区域表格可以横向滚动，不挤压页面。
- AI 回答中的来源列表、相关实体卡片不能和底部输入框重叠。

## 8. 底部输入框设计

输入区是本次美化重点。

结构建议：

```text
composer-area
├── composer-chips 对话态快捷追问
└── composer-wrap
    ├── textarea
    └── composer-footer
        ├── char-count
        └── send-btn
```

样式要求：

- `composer-area` 常驻底部，背景带渐隐遮罩，让输入框像浮在页面底部。
- `composer-wrap` 使用主题面板色、半透明边框、阴影和 focus 发光。
- `textarea` 背景透明，无原生边框，最大高度 180px。
- `send-btn` 使用蓝紫渐变，禁用态透明降低，不要纯灰白。
- 字数提示靠左或靠右，但不能和按钮重叠。
- 移动端输入框高度和按钮尺寸要适配拇指点击。

## 9. 可选视觉素材策略

DeepSeek 不应依赖联网图片。

执行时先检查本地是否有可用素材：

- `frontend/public/`
- `frontend/src/assets/`

如果存在篮球、球场、球员剪影、NBA 主题背景，可作为淡化背景使用：

- 透明度控制在 0.08 到 0.18。
- 不遮挡文字。
- 不使用外链图片。

如果没有合适素材：

- 使用 CSS 绘制球场线条、网格、渐变背景即可。
- 不要临时加入未经授权的远程图片。

## 10. 实施步骤

### Step 1：阅读和确认现状

1. 打开 `frontend/src/views/SmartSearchView.vue`。
2. 找到 template 中的 `welcome-screen`、`chat-flow`、`composer-area`。
3. 找到 scoped style 中的 `.copilot-workspace`、`.top-bar`、`.welcome-screen`、`.capabilities`、`.composer-area`。
4. 确认是否有乱码中文，统一改成 UTF-8 正常中文。

### Step 2：重构首页视觉结构

1. 保留原有 `v-if="!msgs.length"` 判断。
2. 将欢迎页整理为：
   - `welcome-hero`
   - `capabilities`
   - `prompt-chips`
3. 大卡片保留点击发送问题的能力。
4. 小卡片保留点击发送问题的能力。
5. 快捷问题保留点击发送问题的能力。

### Step 3：重写主题样式

1. 在 `.copilot-workspace` 内定义局部 CSS 变量。
2. 添加亮色主题覆盖。
3. 重写背景、顶部栏、卡片、按钮、输入框、聊天气泡。
4. 清理旧样式中导致白底突兀、文字省略、布局挤压的规则。

### Step 4：优化聊天态

1. 调整 `chat-flow` 宽度、间距、滚动区域。
2. 调整用户和 AI 消息气泡。
3. 调整 loading、error、sources、follow-ups、related entities 的主题样式。
4. 确保长文本、表格和代码块不会撑破页面。

### Step 5：移动端适配

1. `capabilities` 改为单列。
2. `cap-grid` 改为 2 列或横向滚动。
3. `top-bar` 操作按钮压缩间距。
4. `composer-area` 预留底部导航高度。
5. 测试 390px 宽度下不出现横向滚动。

### Step 6：验证

至少执行：

```bash
cd frontend
npm run build
```

浏览器手动验证：

- 打开 AI 助手页面。
- 桌面端 1440px 宽度查看首页。
- 桌面端 1920px 宽度查看首页。
- 移动端 390px 宽度查看首页。
- 发送一个问题，确认聊天态正常。
- 测试新对话。
- 测试分享按钮。
- 测试导出按钮。
- 测试历史会话侧栏。
- 测试深色和浅色主题。
- 打开控制台，确认无报错。

## 11. 验收标准

完成后必须满足：

- 首页视觉接近参考图方向：AI 工作台、NBA 主题、深色玻璃质感、能力卡片明确。
- 页面填满可视区域，不能只集中在中间一小块。
- 底部对话框常驻底部，任何状态下不遮挡主要内容。
- 桌面端能力区布局饱满，不能显得空。
- 移动端布局可用，不出现横向滚动。
- 所有按钮、标签、卡片都与主题一致，不出现突兀白底。
- 表头、按钮、卡片标题不出现不必要的省略号。
- 中文文案无乱码。
- 原有聊天、发送、历史、分享、导出逻辑不被破坏。
- `npm run build` 通过。

## 12. 交给 DeepSeek 的完整提示词

下面这段可以直接复制给 DeepSeek 执行：

```text
你现在是一个资深 Vue 前端工程师和 UI 设计工程师。请在当前项目中美化 AI 助手界面，将其改造成 NBA 智能分析工作台风格。

任务背景：
当前 AI 助手页面已经具备聊天、历史会话、快捷问题、分享、导出、消息来源和相关实体等能力，但首页视觉不够精致。我要你参考以下文字化设计方向完成前端重构：页面整体是 NBA Copilot / NBA Courtside 式 AI 助手首页，深色主题下为深蓝黑科技感背景，带球场线条、蓝紫橙光感、玻璃拟态卡片；浅色主题下为干净灰白背景，配合蓝紫橙主题色和柔和阴影。底部对话框必须常驻底部。移动端要可用。

重点文件：
1. frontend/src/views/SmartSearchView.vue
2. frontend/src/styles/smart-search.css

修改边界：
1. 只重构前端 AI 助手 UI。
2. 不修改后端接口。
3. 不修改 RAG、LangChain、LangGraph、搜索等后端逻辑。
4. 保留现有 Vue 状态、方法和业务行为，包括 msgs、question、loading、send、newChat、exportChat、shareChat、loadConv、delConv、retryLast、cancelQuery、render、autoGrow、handleKeydown 等。
5. 可以调整 template 结构和 class，但不要破坏发送消息、历史会话、分享、导出、消息渲染、来源展示和相关实体展示。
6. 不新增大型 UI 依赖。
7. 文件必须用 UTF-8 保存，并修复当前界面中所有乱码中文。

目标布局：
根容器继续使用 copilot-workspace。页面结构建议如下：

copilot-workspace
- sidebar 历史会话侧栏
- modal 分享弹窗
- workspace-main
  - top-bar 顶部工具栏
  - content-area
    - welcome-screen 空会话首页
      - welcome-hero
      - capabilities
        - cap-main 左侧主能力卡片
        - cap-grid 右侧 6 个快捷能力卡片
      - prompt-chips 快捷问题
    - chat-flow 对话消息流
  - composer-area 底部输入区

桌面端布局要求：
1. 页面填满可视区域。
2. 顶部工具栏高度约 56px。
3. 中间 content-area 可滚动。
4. welcome-screen 最大宽度 1120px 到 1180px，水平居中。
5. capabilities 使用左右两栏：左侧主卡片 minmax(420px, 1fr)，右侧小卡片区 minmax(360px, 420px)。
6. cap-grid 使用 2 列 3 行，小卡片高度统一。
7. composer-area 常驻底部，composer-wrap 最大宽度约 1040px 到 1120px。

移动端布局要求：
1. max-width: 900px 时 capabilities 改为单列。
2. max-width: 640px 时顶部按钮压缩，卡片更紧凑。
3. 输入框始终在底部，不能被底部导航遮挡。
4. 不允许出现横向滚动。
5. 文字不能溢出、重叠或不必要省略。

视觉规范：
深色主题建议变量：
--copilot-bg: #050816;
--copilot-bg-2: #0b1020;
--copilot-panel: rgba(15, 23, 42, 0.78);
--copilot-panel-strong: rgba(17, 24, 39, 0.92);
--copilot-border: rgba(148, 163, 184, 0.18);
--copilot-border-strong: rgba(129, 140, 248, 0.36);
--copilot-text: #f8fafc;
--copilot-muted: #94a3b8;
--copilot-dim: #64748b;
--copilot-blue: #3b82f6;
--copilot-purple: #8b5cf6;
--copilot-cyan: #22d3ee;
--copilot-orange: #f97316;
--copilot-green: #22c55e;

浅色主题建议变量：
--copilot-bg: #f6f8fc;
--copilot-bg-2: #eef2ff;
--copilot-panel: rgba(255, 255, 255, 0.86);
--copilot-panel-strong: rgba(255, 255, 255, 0.96);
--copilot-border: rgba(148, 163, 184, 0.28);
--copilot-border-strong: rgba(59, 130, 246, 0.28);
--copilot-text: #0f172a;
--copilot-muted: #64748b;
--copilot-dim: #94a3b8;

背景要求：
1. 深色主题使用深蓝黑渐变、球场线条、柔和光晕。
2. 浅色主题使用灰白渐变、淡蓝紫光感和柔和阴影。
3. 不要使用突兀纯白底纹。
4. 如果本地 assets/public 中有篮球或球场素材，可以低透明度作为背景；没有就用 CSS 球场线条，不要使用外链图片。

首页文案请修复为正常中文：
品牌：NBA Copilot
主标题：你的 NBA 数据分析助手
副标题：基于系统知识库、实时赛事数据与联网搜索，为你生成可追溯的篮球分析结论。
数据提示：已连接 {{ docCount.toLocaleString() }} 条知识片段
主卡片：今日赛事分析
主卡片描述：分析今日比赛的胜负关键、核心对位、伤病影响和战术看点。
小卡片标题：球员对比、球队查询、数据解读、季后赛预测、数据排行、MVP 分析
快捷问题：今天有哪些比赛？、分析湖人最近 5 场表现、库里和东契奇谁的三分更准？、本赛季助攻榜 Top 5
输入框占位：询问任何 NBA 数据、球员、球队、赛程或战术问题...

聊天态要求：
1. 用户消息靠右，使用主题渐变。
2. AI 消息靠左，使用玻璃拟态面板。
3. loading、error、sources、follow-ups、related entities 全部统一主题样式。
4. 长文本、表格、代码块不能撑破页面。
5. 消息区底部不能被 composer-area 遮挡。

底部输入框要求：
1. composer-area 常驻底部。
2. composer-wrap 使用半透明主题面板、细边框、柔和阴影和 focus 发光。
3. textarea 背景透明，最大高度 180px。
4. send-btn 使用蓝紫渐变，禁用态降低透明度。
5. 字数提示和发送按钮不能重叠。

实施步骤：
1. 阅读 SmartSearchView.vue 和 smart-search.css。
2. 修复乱码中文并确认文件 UTF-8。
3. 重构 welcome-screen 的视觉结构。
4. 重写 scoped style 中 copilot-workspace、top-bar、welcome-screen、capabilities、cap-main、cap-grid、cap-card、prompt-chips、chat-flow、msg-bubble、composer-area、composer-wrap、send-btn 等样式。
5. 处理浅色主题覆盖。
6. 处理移动端响应式。
7. 清理无用或冲突的旧样式。
8. 执行 cd frontend && npm run build。
9. 手动验证 AI 助手页面：桌面 1440px、桌面 1920px、移动 390px；发送消息、新对话、分享、导出、历史侧栏、深浅主题均正常。

验收标准：
1. 首页视觉接近 NBA AI 工作台，而不是普通聊天框。
2. 页面填满可视区域。
3. 底部对话框常驻底部。
4. 深色和浅色主题都精致统一。
5. 不出现突兀白底。
6. 不出现文字乱码。
7. 不出现文字重叠、横向滚动、按钮挤压。
8. 原有功能不被破坏。
9. npm run build 通过。

请直接修改代码完成任务，并在最后说明修改了哪些文件、验证结果是什么、是否还有需要人工确认的细节。
```

