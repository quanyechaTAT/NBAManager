# NBA 数据演示系统

基于 **Spring Boot 3** + **Vue 3** 的轻量演示项目：登录与 JWT、角色权限、球队/球员的增删改查与分页、看板图表（ECharts）。数据在启动时由程序写死初始化，无外部数据集、无爬虫、无复杂分析。

## 功能概览

| 模块 | 说明 |
|------|------|
| 登录 | `POST /api/auth/login`，返回 JWT |
| 权限 | `ADMIN` 可增删改；`USER` 仅可浏览（写接口返回 403） |
| CRUD | 球队 `Team`、球员 `Player` |
| 分页 | Spring Data `Pageable`，前端表格分页 |
| 图表 | 各队胜/负场柱状图、球员场均得分横向柱状图 |

## 演示账号（首次启动自动写入 H2）

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user | user123 | 普通用户 |

## 技术栈

- 后端：Spring Boot 3.2、Spring Security、JWT（jjwt）、Spring Data JPA、H2 内存库
- 前端：Vue 3、Vite、TypeScript、Vue Router、Pinia、Element Plus、Axios、ECharts、vue-echarts

## 运行方式

### 1. 后端

要求：JDK 17+、Maven 3.8+（或使用 IDE 内置 Maven）。

```bash
cd backend
mvn spring-boot:run
```

启动后 API 根地址：`http://localhost:8080`  
H2 控制台（可选）：`http://localhost:8080/h2-console`（JDBC URL 与 `application.yml` 中一致）

### 2. 前端

要求：Node.js 18+。

```bash
cd frontend
npm install
npm run dev
```

浏览器访问：`http://localhost:5173`（Vite 已将 `/api` 代理到 `8080`）

### 3. 生产构建（前端）

```bash
cd frontend
npm run build
```

将 `frontend/dist` 交由任意静态服务器托管；若前后端不同域，请在后端 CORS 中增加实际前端域名，或改为同域部署。

## 目录结构

```
NBAManager/
├── backend/                 # Spring Boot
│   └── src/main/java/com/nbamanager/
│       ├── config/          # Security、CORS、初始化数据
│       ├── domain/          # 实体与角色枚举
│       ├── repository/
│       ├── service/
│       ├── security/        # JWT、UserDetails
│       └── web/             # REST 控制器与 DTO
├── frontend/                # Vue 3 + Vite
│   └── src/
│       ├── api/
│       ├── layouts/
│       ├── router/
│       ├── stores/
│       ├── utils/
│       └── views/
└── README.md
```

## 主要 API

- `POST /api/auth/login` — 登录（无需 Token）
- `GET /api/dashboard/stats` — 看板聚合数据
- `GET/POST/PUT/DELETE /api/teams` — 球队分页与维护（写操作需管理员）
- `GET/POST/PUT/DELETE /api/players` — 球员分页与维护（写操作需管理员）

查询参数：`q` 关键词；分页：`page`（从 0 开始）、`size`、`sort`。

## 说明文档（课程/答辩）

可围绕以下内容撰写：需求说明（演示范围）、用例图、ER 图（用户、球队、球员）、接口列表、权限模型（JWT + 角色）、部署步骤（JDK + Maven + Node）、截图（登录、列表、图表）。

## 许可

仅供学习与演示使用。
