# NBA Manager 数据管理系统

基于 **Spring Boot 3** + **Vue 3** 的 NBA 数据管理演示项目。支持实时 NBA 数据同步、比赛资讯管理、球员详情展示、中英文翻译等功能。

## 功能概览

| 模块 | 说明 |
|------|------|
| 登录 | `POST /api/auth/login`，返回 JWT |
| 权限 | `ADMIN` 可增删改；`USER` 仅可浏览 |
| 比赛资讯 | NBA 实时新闻获取、翻译、管理 |
| 球员数据 | 球员详情、统计数据展示 |
| 比赛详情 | 比赛结果、比分、详情页 |
| 数据同步 | ESPN 数据源实时同步 |
| 翻译 | MIMO API 中英文翻译 |

## 演示账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user | user123 | 普通用户 |

> ⚠️ 以上为默认种子密码，实际密码通过环境变量或 `application-local.yml` 配置。

## 技术栈

- **后端**：Spring Boot 3.2、Spring Security、JWT (jjwt)、Spring Data JPA、MySQL 8.0、Redis
- **前端**：Vue 3、Vite、TypeScript、Vue Router、Pinia、Element Plus、Axios
- **数据源**：ESPN API（通过 Python 脚本同步）
- **翻译**：MIMO API

## 运行方式

### 前置条件

- JDK 17+
- Maven 3.8+（或 IDE 内置）
- Node.js 18+
- MySQL 8.0
- Redis（可选，用于缓存）

### 1. 配置敏感信息

项目使用环境变量管理敏感配置。在项目根目录创建 `.env` 文件：

```env
# MIMO 翻译 API
MIMO_API_KEY=your_mimo_api_key
MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1

# NBA API 代理（可选）
NBA_PROXY_HOST=127.0.0.1
NBA_PROXY_PORT=7890

# 管理员账号
ADMIN_USER=admin
ADMIN_PASS=admin123
```

在后端配置中，创建 `backend/src/main/resources/application-local.yml`（不会被提交到 Git）：
- 复制 `application.yml` 的内容
- 填入真实的数据库密码、Redis 密码、JWT 密钥

或者通过环境变量设置：
```bash
export DB_USERNAME=root
export DB_PASSWORD=your_db_password
export REDIS_PASSWORD=your_redis_password
export JWT_SECRET=your_jwt_secret_at_least_256_bits
```

### 2. 导入数据库

```bash
mysql -u root -p < database/nba_manager_dump.sql
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
# 或指定 local profile
mvn spring-boot:run -Dspring.profiles.active=local
```

启动后 API 根地址：`http://localhost:8080`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器访问：`http://localhost:5173`

### 5. Python 脚本（数据同步/翻译）

```bash
cd backend/scripts
# 安装依赖
pip install -r requirements.txt  # 如有

# 刷新新闻数据
python refresh_news.py rebuild

# 翻译现有数据
python refresh_news.py translate
```

## 目录结构

```
NBAManager/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/nbamanager/
│   │   ├── config/             # Security、CORS、初始化数据
│   │   ├── domain/             # 实体类
│   │   ├── repository/         # JPA 仓库
│   │   ├── service/            # 业务逻辑
│   │   ├── security/           # JWT、UserDetails
│   │   └── web/                # REST 控制器与 DTO
│   └── scripts/                # Python 数据脚本
│       ├── nba_data_fetcher.py # ESPN 数据获取
│       ├── translator.py       # MIMO 翻译
│       ├── refresh_news.py     # 新闻刷新
│       └── translate_news.py   # 新闻翻译
├── frontend/                   # Vue 3 + Vite 前端
│   └── src/
│       ├── api/                # API 调用
│       ├── views/              # 页面组件
│       ├── stores/             # Pinia 状态管理
│       └── router/             # 路由配置
├── database/                   # 数据库导出（Git 忽略）
│   └── nba_manager_dump.sql
├── .env                        # 环境变量（Git 忽略）
└── README.md
```

## 主要 API

- `POST /api/auth/login` — 登录（无需 Token）
- `GET/POST/PUT/DELETE /api/news` — 比赛资讯管理
- `GET /api/players` — 球员列表
- `GET /api/matches/{id}` — 比赛详情
- `GET /api/dashboard/stats` — 看板数据

## 许可

仅供学习与演示使用。
