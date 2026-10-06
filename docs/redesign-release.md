# Courtside 核验、发布与回滚

更新日期：2026-10-04。**本轮未执行实际项目数据库备份、迁移、重同步、索引重建或发布。**

## 1. 备份与审计

记录版本、部署目录、配置位置、赛季与任务状态，记录不含凭证。维护窗口暂停调度和手工同步，等运行任务终态；取消请求接受不等于已终止。

使用 MySQL login-path 或安全凭证配置备份。替换为明确数据库与路径：

```powershell
$taskDatabaseName = '<目标数据库>'
$taskBackupFile = 'E:\Courtside-backups\courtside-before-redesign.sql'
& mysqldump --login-path=courtside-backup --single-transaction --routines --events --triggers --hex-blob --databases $taskDatabaseName "--result-file=$taskBackupFile"
if ($LASTEXITCODE -ne 0) { throw '数据库备份失败' }
Get-Item -LiteralPath $taskBackupFile | Select-Object FullName, Length, LastWriteTime
```

`--result-file` 避免 PowerShell 重定向改变 SQL 编码。先在隔离库恢复并核对行数、关联 ID、权限。备份上传目录、实际 RAG_CHROMA_PATH、索引报告、配置；复制向量文件前停止写入。

只读审计用 DB_USERNAME / DB_PASSWORD / DB_NAME / DB_HOST / DB_PORT 安全环境配置，不把密码写进命令或报告：

```powershell
python backend/scripts/audit_data_provenance.py --output docs/data-audit-before.json
```

脚本只执行 SELECT 与只读一致性事务，报告不含行值或账号。missing = 原值 NULL；published = 有效质量及 OBSERVED / DERIVED 的非空；withheld 包含缺失和未核验，两列不可相加当总数。LEGACY_SCHEMA = 缺来源列；不按 20 / 110 / 0 推断占位。

## 2. 兼容发布顺序

1. 部署 null / DataMeta 兼容前端，验证旧接口和登录列表。
2. 备份可恢复后部署后端，现有 Flyway 运行 `V20261002_01__Data_provenance`；核对 history、8 表新列及 data_verification_snapshots。
3. 迁移保留原值 ID，增加来源列、允许相关统计为空，旧记录标 UNKNOWN / UNVERIFIED。比赛和投篮原始字段不改可空 DDL，保护季后赛消费逻辑。
4. 检查 `/api/data/metadata` 及 0 / null / 未核验记录，来源时间与获取时间区分。
5. 运行 `python backend/scripts/audit_data_provenance.py --output docs/data-audit-after-migration.json`，核对原记录数与快照。

MySQL ALTER TABLE 可能隐式提交，不能以事务 rollback 代替备份恢复。隔离夹具通过不代表实际库锁、时长及恢复已验收。

## 3. 模块与赛季核验

有效管理员会话触发现有任务，记录 jobId：

| 模块 | 接口 | 核对 |
|---|---|---|
| 球队 | `POST /api/admin/sync/TEAMS` | 来源赛季、官方战绩与本地样本 |
| 球员 | `POST /api/admin/sync/PLAYERS` | ID 保留、完整字段、USG / TS / PER |
| 比赛 | `POST /api/admin/sync/MATCHES` | 状态来源赛季、0 分与收录范围 |
| 历史指定赛季 | `POST /api/historical/sync/{season}` | 五项榜单、至少 10 场、独立有效样本 |
| 投篮 | 已有球员投篮读取 / 刷新 | 真实集合、日期坐标、重复刷新数量一致 |

查询 `GET /api/admin/jobs/{jobId}` 到终态，保存步骤来源数量时间错误。成功响应 / 100% 不代替终态；PARTIAL_FAILED 需核对写入部分。整体来源失败保留原有效快照。

**本轮核验只触发指定模块，不使用含 PLAYOFF 的全量同步。** 季后赛原同步能力保留，但不属于本轮数据修复。

核验后再审计，允许数据仍缺失，不补数。统计缓存使用 `data-contract-v2::` 前缀，同步后失效对应模块；不清整个 Redis，以免影响会话。

## 4. RAG 索引

先备份实际向量目录和 `backend/scripts/runtime/rag/index_report.json`。管理员 `POST /api/rag/rebuild` 的 started 只是启动，busy 只是仍在执行；核对 Python 日志和新报告 `status=ok`、document_count、collection_count、生成时间和 chroma_path。Java `/api/rag/stats` 只有文档数及模型，不能据此判断终态。

CLI 方式在维护窗口、同一环境配置下，于 `backend/scripts` 执行：

```powershell
.\venv312\Scripts\python.exe -m rag_app.indexing.builder
```

默认清空当前集合，须先备份；需要删除旧文档时不使用 --no-clear。新文档为 data_contract_version=2，检索只读 v2。核对观测 0、合法 USG%=20、缺失 TS、未知赛季及真实来源；历史会话原回答保留。

## 5. 页面验收和回滚

按 [验收矩阵](redesign-visual-acceptance.md) 完成 72 组截图及功能、权限、季后赛图本体对比，运行类型测试构建资源审计，记录版本后恢复调度。

- 视觉回滚保留来源及 null 兼容，不以旧默认数值填页面。
- 接口回滚到保留发布规则的兼容版本；不启动会再次写入占位值的旧同步代码。
- 来源失败保留有效快照与时间，标部分覆盖。
- 数据错误先停止写入，在隔离库验证恢复后按模块主键恢复原 ID，再失效缓存和重建索引。
- 不自动删除新列、快照和 Flyway history；隐式提交后的恢复依赖备份。
- 季后赛本体基线失败，回退影响继承的外围 / 全局改动，保持内部逻辑，再四组复测。

发布记录包含版本、窗口、备份恢复、迁移、模块赛季 jobId 终态、审计缓存索引、功能截图、季后赛保护、负责人和回滚版本；未执行填写待执行。
