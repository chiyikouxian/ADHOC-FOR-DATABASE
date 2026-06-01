# Change: 建立数据库层（PostgreSQL 关系核心 + TDengine 遥测时序）

## Why
项目目前只有方案文档，尚无任何可执行的数据库 schema。S1 阶段需要落地双库 DDL 与种子数据，作为后端骨架、遥测接入与三段核心 SQL 的地基。数据库是本课设的评分核心，schema 必须先成立。

## What Changes
- 新增 PostgreSQL 关系/事务核心 schema（`db/schema_pg.sql`）：users、drone_models、drones、missions、mission_assignments、waypoints、alerts、audit_log 八张表 + `v_drone_latest` 视图 + 跨库快照表 `drone_latest`、`network_links_snapshot`。
- 新增索引、外键约束、CHECK 约束、审计触发器、告警触发器。
- 新增 TDengine 遥测时序 schema（`db/schema_tdengine.sql`）：`telemetry`、`network_links` 两张超级表 + 标签设计 + 数据保留策略。
- 新增逼真种子数据（`db/seed_pg.sql`）：型号字典、无人机、用户、历史任务与分配、航点、若干告警。
- **BREAKING**（相对原方案）：取消单 MySQL + RANGE 分区方案；遥测不再进关系库，改由 TDengine 超表承载。

## Impact
- 受影响 specs: `database-schema`（新增能力）
- 受影响代码: `db/schema_pg.sql`、`db/schema_tdengine.sql`、`db/seed_pg.sql`（均为新建）
- 后续依赖: S2 后端双数据源配置、S3 遥测接入、S4 三段核心 SQL 均以此 schema 为前提。
