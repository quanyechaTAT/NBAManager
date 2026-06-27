# 🏀 NBA Manager

一个基于 **真实数据驱动** 的 NBA 数据管理与分析平台。通过自动化数据采集、智能翻译、多源数据融合、RAG 智能问答等技术，实现从数据获取到展示的全链路自动化。

## ✨ 功能特性

### 📊 数据看板
- 球队战绩概览与可视化图表
- 球员得分排行榜（Top 8 缓存，点击跳转完整列表）
- 今日赛事实时比分卡片
- AI Copilot 快捷入口

### 🏆 球队管理
- 30 支 NBA 球队完整数据
- 东西部/分区排行榜
- 对战记录与交锋统计
- 球队关注功能

### 👤 球员数据
- 500+ 球员详细资料（NBA API 实时同步）
- 卡片/表格双视图，支持导出 Excel
- 球员生涯趋势图（ECharts 雷达图 + 折线图）
- 投篮热图（真实 NBA API 数据，SVG 渲染）
- 球员对比功能（双人雷达图 + 详细数据表）
- 球员关注与收藏

### 📰 赛事资讯
- 实时比赛资讯（ESPN 数据源）
- 今日赛事卡片（实时比分更新）
- 资讯收藏与浏览历史
- 比赛详情：Box Score、逐节比分、Play-by-Play、投篮分布图
- 比赛实时聊天室（WebSocket）

### 🤖 RAG 智能问答
- 基于 ChromaDB 向量数据库 + MIMO LLM 的智能问答
- 混合检索：向量检索 + BM25 + CrossEncoder 重排序
- SQL 结构化查询优先（球队/球员/比赛/排名）
- 百度搜索补充（新闻类查询）
- 证据门控反幻觉机制
- SSE 流式输出
- 对话历史持久化与分享

### 🏆 季后赛
- 季后赛对阵图（东西部对称展示）
- 实时比分与系列赛进度
- 球队 Logo 展示

### 📅 历史数据
- 最近三个赛季数据存储
- 赛季数据领袖排行
- 球队战绩图表与排名表
- 赛季切换与球队筛选

### 💬 社区互动
- 帖子发布（讨论/新闻/辩论/预测，支持投票）
- 评论与回复系统（树形结构，置顶，作者互动标记）
- 点赞与收藏
- 热门帖子排行

### 🔔 通知系统
- 点赞/评论/收藏/回复/比赛更新通知
- 浏览器推送通知支持
- 删除/清空已读通知

### 👤 用户系统
- 用户注册与登录（JWT 认证）
- 个人主页（关注球员/球队、收藏帖子/资讯、浏览历史）
- 头像上传
- 密码/用户名修改

### 🛠️ 管理后台
- 数据管理中心（同步进度、暂停/取消）
- 模块化同步（球队/球员/比赛/新闻/季后赛/选秀等）
- 全量同步与单模块同步

## 🔧 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3 + TypeScript)                 │
│  Element Plus · ECharts · Pinia · Vue Router · WebSocket        │
├─────────────────────────────────────────────────────────────────┤
│                      后端 (Spring Boot 3.2)                      │
│  Spring Security + JWT · JPA/Hibernate · Redis · WebSocket      │
├─────────────────────────────────────────────────────────────────┤
│                   RAG 服务 (Python FastAPI)                      │
│  ChromaDB · sentence-transformers · CrossEncoder · MIMO LLM     │
├─────────────────────────────────────────────────────────────────┤
│                    数据采集 (Python Scripts)                      │
│  NBA Stats API · ESPN API · MIMO 翻译 API · 百度搜索             │
├─────────────────────────────────────────────────────────────────┤
│                        数据层                                    │
│  MySQL 8.0 · Redis · ChromaDB (向量) · localStorage/sessionStorage│
└─────────────────────────────────────────────────────────────────┘
```

### 数据处理流程

```
NBA/ESPN API → Python 采集 → MIMO 翻译 → 去重清洗 → MySQL 存储 → Redis 缓存 → API 输出
                                                                      ↓
ChromaDB 向量索引 ← 文档分块 ← 结构化数据                            RAG 问答
                                                                      ↓
                                                              MIMO LLM 生成回答
```

### 技术栈

| 层 | 技术 |
|---|------|
| **前端** | Vue 3 + TypeScript + Vite + Element Plus + ECharts + Pinia |
| **后端** | Java 17 + Spring Boot 3.2 + Spring Security + JPA + Redis + WebSocket |
| **RAG** | Python 3 + FastAPI + ChromaDB + sentence-transformers + CrossEncoder |
| **数据采集** | Python 3 + nba_api + ESPN API + MIMO 翻译 |
| **数据库** | MySQL 8.0 + Redis + ChromaDB |
| **GPU** | CUDA 12.4 (RTX 3050 Laptop) + PyTorch 2.6 |

## 🚀 快速开始

### 环境要求
- Java 17+
- Node.js 18+
- Python 3.12+
- MySQL 8.0+
- Redis 6.0+
- CUDA 12.4+（可选，用于 RAG GPU 加速）

### 1. 克隆项目
```bash
git clone https://github.com/quanyechaTAT/NBAManager.git
cd NBAManager
```

### 2. 配置环境变量
```bash
cp .env.example .env
# 编辑 .env 文件，填入你的配置
```

`.env` 文件配置说明：
```env
# 数据库
DB_USERNAME=root
DB_PASSWORD=your_password_here

# MIMO 翻译 API
MIMO_API_KEY=your_mimo_api_key
MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1

# NBA API 代理（可选）
NBA_PROXY_HOST=
NBA_PROXY_PORT=

# 初始管理员账号
ADMIN_USER=admin
ADMIN_PASS=your_admin_password

# 初始普通用户账号
USER_USER=user
USER_PASS=your_user_password
```

### 3. 安装依赖
```bash
# 后端
cd backend && ./mvnw install -DskipTests

# 前端
cd ../frontend && npm install

# Python 数据采集
cd ../backend/scripts && pip install -r requirements.txt

# Python RAG（推荐使用虚拟环境）
cd backend/scripts
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 4. 启动服务
```bash
# 后端（在 backend 目录）
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 前端（新终端，在 frontend 目录）
npm run dev

# RAG 服务（新终端，在 backend/scripts 目录）
python -m rag_app.main 8899
```

### 5. 访问应用
- **前端**: http://localhost:5173
- **后端 API**: http://localhost:8080
- **RAG 服务**: http://localhost:8899

> ⚠️ 首次启动会自动初始化演示账号，账号密码通过环境变量配置。

## 📁 项目结构

```
NBAManager/
├── backend/                          # Spring Boot 后端
│   ├── scripts/                      # Python 脚本
│   │   ├── nba_data_fetcher.py       # NBA 数据采集（核心）
│   │   ├── rag_app/                  # RAG 智能问答服务
│   │   │   ├── api/                  # FastAPI 路由
│   │   │   ├── pipeline/             # RAG 管道（分类/检索/生成）
│   │   │   ├── retrieval/            # 检索引擎（向量/BM25/重排序）
│   │   │   ├── structured/           # SQL 结构化查询
│   │   │   ├── llm/                  # LLM 客户端与 Prompt
│   │   │   └── tools/                # 百度搜索工具
│   │   ├── translator.py             # 翻译工具
│   │   └── requirements.txt          # Python 依赖
│   └── src/main/java/com/nbamanager/
│       ├── config/                   # 配置类
│       ├── domain/                   # JPA 实体类
│       ├── repository/               # 数据访问层
│       ├── service/                  # 业务逻辑层
│       ├── web/                      # REST 控制器
│       └── security/                 # JWT 认证
├── frontend/                         # Vue 3 前端
│   └── src/
│       ├── api/                      # API 调用封装
│       ├── components/               # 通用组件
│       ├── composables/              # 组合式函数
│       ├── layouts/                  # 页面布局
│       ├── router/                   # 路由配置
│       ├── stores/                   # Pinia 状态管理
│       ├── styles/                   # 全局样式（深色/浅色主题）
│       ├── utils/                    # 工具函数
│       └── views/                    # 页面视图
└── docs/                             # 设计文档
```

## 📝 主要 API

### 认证
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `PUT /api/auth/password` - 修改密码

### 数据
- `GET /api/teams` - 球队列表
- `GET /api/players` - 球员列表（分页/搜索/筛选）
- `GET /api/news` - 赛事资讯
- `GET /api/news/today` - 今日赛事
- `GET /api/posts` - 社区帖子
- `GET /api/match-detail/{gameId}/boxscore` - 比赛 Box Score
- `GET /api/dashboard/stats` - 数据看板
- `GET /api/dashboard/top-scorers` - 得分榜（分页）

### RAG 智能问答
- `GET /api/rag/query?question=...` - 智能问答
- `GET /api/rag/stream?question=...` - 流式问答（SSE）
- `GET /api/rag/stats` - RAG 统计信息
- `POST /api/rag/rebuild` - 重建索引（管理员）

### 历史数据
- `GET /api/historical/seasons` - 可用赛季列表
- `GET /api/historical/players` - 指定赛季球员数据
- `GET /api/historical/teams` - 指定赛季球队数据

### 管理后台
- `POST /api/admin/sync/{module}` - 同步指定模块
- `POST /api/admin/sync/all` - 全量同步
- `POST /api/admin/pause/{module}` - 暂停同步

## 🎨 界面特色

- 深色/浅色主题切换（Courtside Intelligence 设计系统）
- 响应式布局（桌面端 + 移动端底部导航）
- 流畅的页面过渡动画
- 数据可视化图表（ECharts）
- 实时通知系统（WebSocket + 轮询）
- AI Copilot 快捷入口

## 🤝 贡献指南

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

- [nba_api](https://github.com/swar/nba_api) - NBA 数据 API
- [Element Plus](https://element-plus.org/) - Vue 3 UI 组件库
- [Spring Boot](https://spring.io/projects/spring-boot) - 后端框架
- [Vue.js](https://vuejs.org/) - 前端框架
- [sentence-transformers](https://www.sbert.net/) - 向量嵌入模型
- [ChromaDB](https://www.trychroma.com/) - 向量数据库

---

⭐ 如果这个项目对你有帮助，请给它一个 Star！
