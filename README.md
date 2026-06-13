# 🏀 NBA Manager

一个基于 **真实数据驱动** 的 NBA 数据管理与分析平台。与传统数据管理系统不同，本系统通过自动化数据采集、智能翻译、多源数据融合等技术，实现了从数据获取到展示的全链路自动化。

## 🆚 与传统数据管理系统的核心区别

### 传统方式
- ❌ 手动录入数据，容易出错
- ❌ 数据更新不及时，依赖人工
- ❌ 单一数据源，信息不全面
- ❌ 缺少实时性，数据滞后
- ❌ 无智能处理，原始数据直接展示

### 本系统
- ✅ **自动化采集** - 通过 NBA API + ESPN API 实时获取数据
- ✅ **智能翻译** - MIMO API 自动将英文球员名/新闻翻译为中文
- ✅ **多源融合** - 球员数据来自 NBA Stats，新闻来自 ESPN，比赛数据来自 Scoreboard
- ✅ **实时同步** - 新闻每小时更新，比分每5分钟刷新
- ✅ **去重清洗** - 自动识别重复数据，保留翻译质量最好的版本
- ✅ **缓存优化** - Redis 缓存热点数据，毫秒级响应
- ✅ **历史归档** - 自动存储最近3个赛季数据，过期自动清理

## ✨ 功能特性

### 📊 数据看板
- 球队战绩概览与可视化图表
- 球员得分排行榜
- 实时数据统计

### 🏆 球队管理
- 30支 NBA 球队完整数据
- 球队战绩详情
- 对战记录与交锋统计
- 球队关注功能

### 👤 球员数据
- 500+ 球员详细资料（从 NBA API 实时同步）
- 球员生涯数据统计
- 比赛日志（支持分页）
- 球员投篮热图（真实 NBA API 数据）
- 球员对比功能
- 球员关注功能

### 📰 赛事资讯
- 实时比赛资讯（ESPN 数据源）
- 今日赛事卡片（实时比分更新）
- 资讯收藏与浏览历史
- 比赛详细数据查看（Box Score、逐节比分、Play-by-Play）

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
- 帖子发布（讨论/新闻/辩论/预测）
- 评论与回复系统
- 点赞与收藏
- 热门帖子排行

### 🔔 通知系统
- 点赞/评论/收藏/回复通知
- 删除单条通知
- 清空已读通知

### 👤 用户系统
- 用户注册与登录（JWT 认证）
- 个人主页
- 头像上传
- 密码修改
- 浏览历史管理
- 收藏管理

### 🛠️ 管理后台
- 数据管理中心（同步进度、暂停/取消）
- 模块化同步（球队/球员/比赛/新闻/季后赛等）
- 全量同步与单模块同步

## 🔧 技术架构

### 数据采集层（Python）

```
┌─────────────────────────────────────────────────────────┐
│                    数据采集架构                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────┐  │
│  │  NBA Stats   │    │   ESPN API   │    │  MIMO API │  │
│  │  (nba_api)   │    │              │    │  (翻译)    │  │
│  └──────┬───────┘    └──────┬───────┘    └─────┬─────┘  │
│         │                   │                  │        │
│         ▼                   ▼                  ▼        │
│  ┌─────────────────────────────────────────────────────┐│
│  │           nba_data_fetcher.py                       ││
│  │  - fetch_team_standings()    球队战绩               ││
│  │  - fetch_player_stats()      球员数据               ││
│  │  - fetch_today_games()       今日比赛               ││
│  │  - fetch_nba_news()          ESPN新闻               ││
│  │  - fetch_playoff_bracket()   季后赛对阵             ││
│  │  - fetch_historical_players() 历史球员              ││
│  │  - fetch_shot_chart()        投篮热图               ││
│  └─────────────────────────────────────────────────────┘│
│                           │                             │
│                           ▼                             │
│                    Spring Boot 后端                      │
└─────────────────────────────────────────────────────────┘
```

### 数据处理流程

```
原始数据 → 翻译(MIMO) → 去重清洗 → 格式化 → 存储(MySQL) → 缓存(Redis) → API输出
```

**关键处理逻辑：**

1. **智能翻译** - 球员名、新闻标题通过 MIMO API 翻译，本地映射优先，API翻译兜底
2. **去重策略** - 同一新闻按 sourceUrl 去重，保留翻译质量最好的版本（中文字符数多的优先）
3. **GameId 映射** - ESPN 的 gameId (401xxx) 与 NBA 的 gameId (00xxx) 自动映射
4. **缓存策略** - 热点数据（今日赛事、球员详情）Redis 缓存，写操作自动清除
5. **历史归档** - 自动维护最近3个赛季，超过5个赛季自动清理

### 技术栈

**后端**
- Java 17 + Spring Boot 3.2.5
- Spring Security + JWT 认证
- Spring Data JPA + Hibernate
- MySQL 8.0 数据库
- Redis 缓存
- Python 3 + nba_api 数据采集
- WebSocket/STOMP 实时通信

**前端**
- Vue 3 + TypeScript
- Vite 构建工具
- Element Plus UI 组件库
- Pinia 状态管理
- ECharts 图表库

**数据源**
- NBA Stats API (stats.nba.com) - 球队、球员、比赛数据
- ESPN API - 新闻、交易、季后赛数据
- MIMO API - 英文→中文智能翻译

## 🚀 快速开始

### 环境要求
- Java 17+
- Node.js 18+
- Python 3.8+
- MySQL 8.0+
- Redis 6.0+（可选，用于缓存）

### 1. 克隆项目
```bash
git clone https://github.com/quanyechaTAT/NBAManager.git
cd NBAManager
```

### 2. 配置环境变量
```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填入你的配置
```

`.env` 文件配置说明：
```env
# 数据库配置
DB_USERNAME=root
DB_PASSWORD=your_password_here

# MIMO 翻译 API（用于球员名中文翻译）
MIMO_API_KEY=your_mimo_api_key
MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1

# NBA API 代理（可选，用于访问 stats.nba.com）
NBA_PROXY_HOST=
NBA_PROXY_PORT=

# 初始管理员账号（首次启动时创建）
ADMIN_USER=admin
ADMIN_PASS=your_admin_password

# 初始普通用户账号
USER_USER=user
USER_PASS=your_user_password
```

### 3. 配置数据库
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE nba_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 创建本地配置（不会提交到 Git）
cat > backend/src/main/resources/application-local.yml << EOF
spring:
  datasource:
    username: root
    password: your_password
  data:
    redis:
      password:

app:
  jwt:
    secret: your-jwt-secret-key-at-least-256-bits-long
EOF
```

### 4. 安装依赖
```bash
# 后端依赖
cd backend
./mvnw install -DskipTests

# 前端依赖
cd ../frontend
npm install

# Python依赖
cd ../backend/scripts
pip install nba_api requests
```

### 5. 启动服务
```bash
# 启动后端（在 backend 目录）
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 启动前端（在 frontend 目录，新终端）
cd frontend
npm run dev
```

### 6. 访问应用
- **前端**: http://localhost:5173
- **后端 API**: http://localhost:8080

> ⚠️ 首次启动会自动初始化演示账号，账号密码通过环境变量 `ADMIN_USER`/`ADMIN_PASS` 配置。

## 📁 项目结构

```
NBAManager/
├── backend/                        # Spring Boot 后端
│   ├── scripts/                    # Python 数据采集脚本
│   │   ├── nba_data_fetcher.py     # NBA 数据获取（核心）
│   │   ├── translator.py           # 翻译工具
│   │   └── translate_names.py      # 球员名翻译
│   ├── src/main/java/
│   │   └── com/nbamanager/
│   │       ├── config/             # 配置类
│   │       ├── domain/             # JPA 实体类
│   │       ├── repository/         # 数据访问层
│   │       ├── service/            # 业务逻辑层
│   │       ├── web/                # REST 控制器
│   │       └── security/           # JWT 认证
│   └── pom.xml
├── frontend/                       # Vue 3 前端
│   ├── src/
│   │   ├── api/                    # API 调用封装
│   │   ├── components/             # 通用组件
│   │   ├── layouts/                # 页面布局
│   │   ├── router/                 # 路由配置
│   │   ├── stores/                 # Pinia 状态管理
│   │   ├── styles/                 # 全局样式
│   │   └── views/                  # 页面视图
│   └── package.json
├── .env.example                    # 环境变量模板
├── .gitignore                      # Git 忽略配置
└── README.md
```

## 🔧 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_USERNAME` | 数据库用户名 | root |
| `DB_PASSWORD` | 数据库密码 | - |
| `REDIS_HOST` | Redis 主机 | localhost |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `REDIS_PASSWORD` | Redis 密码 | - |
| `MIMO_API_KEY` | MIMO 翻译 API 密钥 | - |
| `MIMO_BASE_URL` | MIMO API 地址 | - |
| `NBA_PROXY_HOST` | NBA API 代理主机 | - |
| `NBA_PROXY_PORT` | NBA API 代理端口 | - |
| `ADMIN_USER` | 初始管理员用户名 | admin |
| `ADMIN_PASS` | 初始管理员密码 | admin123 |
| `USER_USER` | 初始普通用户名 | user |
| `USER_PASS` | 初始普通用户密码 | user123 |

### 数据同步策略

| 数据类型 | 同步频率 | 数据源 | 说明 |
|---------|---------|--------|------|
| 球队/球员 | 每日凌晨 | NBA Stats API | 基础数据 |
| 今日比赛 | 每5分钟 | NBA Scoreboard API | 实时比分 |
| 新闻资讯 | 每小时 | ESPN API | 赛事新闻 |
| 季后赛 | 每2小时(赛季期间) | NBA Stats API | 对阵与比分 |
| 历史数据 | 手动触发 | NBA Stats API | 最近3个赛季 |

### 去重与清洗逻辑

```python
# 新闻去重：同一 sourceUrl 只保留翻译质量最好的版本
if sourceUrl 已存在:
    比较中文字符数 → 保留更多的版本
    删除其他重复项

# 球员名翻译：本地映射优先，API翻译兜底
if name in PLAYER_CN_MAP:
    使用本地翻译
else:
    调用 MIMO API 翻译
    缓存到本地映射
```

## 📝 主要 API

### 认证
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `PUT /api/auth/password` - 修改密码

### 数据
- `GET /api/teams` - 球队列表
- `GET /api/players` - 球员列表（支持分页、搜索、筛选）
- `GET /api/news` - 赛事资讯
- `GET /api/news/today` - 今日赛事
- `GET /api/posts` - 社区帖子
- `GET /api/match-records` - 比赛记录
- `GET /api/match-detail/{gameId}/boxscore` - 比赛 Box Score
- `GET /api/match-detail/{gameId}/quarters` - 逐节比分
- `GET /api/dashboard/stats` - 数据看板

### 历史数据
- `GET /api/historical/seasons` - 可用赛季列表
- `GET /api/historical/players` - 指定赛季球员数据
- `GET /api/historical/teams` - 指定赛季球队数据

### 管理后台
- `POST /api/admin/sync/{module}` - 同步指定模块
- `POST /api/admin/sync/all` - 全量同步
- `POST /api/admin/pause/{module}` - 暂停同步
- `POST /api/admin/cancel/{module}` - 取消同步

## 🎨 界面特色

- 深色/浅色主题切换
- 科技感十足的 UI 设计
- 响应式布局
- 流畅的动画过渡效果
- 数据可视化图表
- 实时通知系统

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

---

⭐ 如果这个项目对你有帮助，请给它一个 Star！
