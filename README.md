# 🏀 NBA Manager - NBA 数据管理系统

一个全功能的 NBA 数据管理与分析平台，基于 **Spring Boot 3** + **Vue 3** 构建，提供实时比赛数据、球员统计、社区互动等功能。

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
- 球员头像放大查看
- 球员关注功能

### 📰 赛事资讯
- 实时比赛资讯（ESPN 数据源）
- 今日赛事卡片
- 资讯收藏与浏览历史
- 比赛详细数据查看（Box Score、逐节比分）

### 💬 社区互动
- 帖子发布（讨论/新闻/辩论/预测）
- 评论与回复系统
- 点赞与收藏
- 热门帖子排行（按浏览量排序）
- 帖子编辑与删除

### 🔔 通知系统
- 点赞/评论/收藏/回复通知
- 删除单条通知
- 清空已读通知

### 👤 用户系统
- 用户注册与登录（JWT 认证）
- 个人主页
- 头像上传与放大查看
- 用户名修改
- 密码修改
- 浏览历史管理
- 收藏管理

## 🛠️ 技术栈

### 后端
- **Java 17** + **Spring Boot 3.2.5**
- **Spring Security** + **JWT** 认证
- **Spring Data JPA** + **Hibernate**
- **MySQL 8.0** 数据库
- **Redis** 缓存
- **Python 3** + **nba_api** 数据采集

### 前端
- **Vue 3** + **TypeScript**
- **Vite** 构建工具
- **Element Plus** UI 组件库
- **Pinia** 状态管理
- **Vue Router** 路由
- **Axios** HTTP 客户端
- **ECharts** 图表库

## 🚀 快速开始

### 环境要求
- Java 17+
- Node.js 18+
- Python 3.8+
- MySQL 8.0+
- Redis 6.0+（可选，用于缓存）

### 1. 克隆项目
```bash
git clone https://github.com/yourusername/nba-manager.git
cd nba-manager
```

### 2. 配置环境变量
```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填入你的配置
```

`.env` 文件配置说明：
```env
# MIMO 翻译 API（用于球员名中文翻译）
MIMO_API_KEY=your_mimo_api_key
MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1

# NBA API 代理（可选，用于访问 stats.nba.com）
NBA_PROXY_HOST=
NBA_PROXY_PORT=

# 数据库配置
DB_USERNAME=root
DB_PASSWORD=your_password

# 管理员账号
ADMIN_USER=admin
ADMIN_PASS=your_admin_password
```

### 3. 配置数据库
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE nba_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 创建 application-local.yml（不会提交到 Git）
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

### 演示账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user | user123 | 普通用户 |

> ⚠️ 首次启动会自动初始化演示数据，实际密码通过环境变量配置。

## 📁 项目结构

```
nba-manager/
├── backend/                        # Spring Boot 后端
│   ├── scripts/                    # Python 数据采集脚本
│   │   ├── nba_data_fetcher.py     # NBA 数据获取（球员、比赛、Box Score）
│   │   └── requirements.txt
│   ├── src/main/java/
│   │   └── com/nbamanager/
│   │       ├── config/             # 配置类（安全、缓存、初始化数据）
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
| `JWT_SECRET` | JWT 密钥（至少256位） | - |
| `REDIS_HOST` | Redis 主机 | localhost |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `REDIS_PASSWORD` | Redis 密码 | - |
| `MIMO_API_KEY` | MIMO 翻译 API 密钥 | - |
| `MIMO_BASE_URL` | MIMO API 地址 | - |
| `NBA_PROXY_HOST` | NBA API 代理主机 | - |
| `NBA_PROXY_PORT` | NBA API 代理端口 | - |

### 数据同步

系统支持自动数据同步：

1. **定时同步**: 每日凌晨3点自动同步球队、球员、比赛数据
2. **登录触发**: 管理员登录时自动触发同步
3. **手动同步**: 通过管理后台手动触发

数据来源：
- **球队/球员**: NBA Stats API (stats.nba.com)
- **比赛资讯**: ESPN API
- **球员名翻译**: MIMO API

## 📝 主要 API

### 认证
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `PUT /api/auth/password` - 修改密码
- `PUT /api/auth/username` - 修改用户名

### 数据
- `GET /api/teams` - 球队列表
- `GET /api/players` - 球员列表（支持分页、搜索、筛选）
- `GET /api/news` - 赛事资讯
- `GET /api/posts` - 社区帖子
- `GET /api/match-records` - 比赛记录
- `GET /api/match-detail/{gameId}/boxscore` - 比赛 Box Score
- `GET /api/match-detail/{gameId}/quarters` - 逐节比分
- `GET /api/dashboard/stats` - 数据看板

### 用户
- `GET/PUT /api/profile` - 个人资料
- `GET/POST/DELETE /api/favorites` - 收藏管理
- `GET /api/notifications` - 通知列表
- `GET /api/browse-history` - 浏览历史

## 🎨 界面特色

- 深色科技风格主题
- 响应式布局设计
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
