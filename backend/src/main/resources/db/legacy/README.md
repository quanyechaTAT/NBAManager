# 归档的遗留迁移（不执行）

这三个迁移属于 Flyway 启用之前的旧世代：语句为普通 `ALTER TABLE` / `CREATE INDEX`，
依赖目标表已存在。而本项目的基础表结构由 JPA `ddl-auto: update` 建立，
Flyway 又先于 Hibernate 执行——在全新数据库上这些语句必然失败，
在已有库上则会与 ddl-auto 已加的列/索引冲突，因此无法 reconciliation 后重新启用。

其中的结构性变更（play_by_play 扩展列与索引、game_shot_chart 索引、
game_turning_point 扩展列）在所有现存部署中均已生效（由 ddl-auto 或人工执行）。
保留此目录仅作历史审计；新的 schema 变更请写入 `db/migration/`，
并保证幂等（`CREATE TABLE IF NOT EXISTS` 或 INFORMATION_SCHEMA 守卫式 ALTER）。
