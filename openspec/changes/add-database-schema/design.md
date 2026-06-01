## Context
项目是数据库课设，采用双库混合架构：关系/事务核心进 PostgreSQL 16，遥测时序进 TDengine 3.x，不使用 MySQL。本变更落地 S1 阶段的数据库 schema 与种子数据。设计需解决两库职责边界、跨库数据如何对齐、以及三段核心 SQL 落点。

## Goals / Non-Goals
- Goals: 可执行的双库 DDL；支撑后续行锁/递归CTE/窗口函数三段核心 SQL；逼真种子数据；跨库快照表桥接。
- Non-Goals: 后端代码、MyBatis 映射、压测脚本、AI 功能（属 S2+ 阶段）。

## Decisions
- **分库边界**：遥测高频时序（telemetry、network_links）→ TDengine 超级表；其余强关系数据 → PostgreSQL。理由见方案第 2 节。
- **跨库对齐用同一套 drone_id**：TDengine 用 `drone_id` 作标签，与 PG `drones.drone_id` 同值，应用层据此关联。TDengine 不设外键。
- **快照表桥接**：PG 增设 `drone_latest`（接入服务回写每机最新电量/位置，供调度事务单库内取数）和 `network_links_snapshot`（拓扑分析前从 TDengine 导入最近一窗活跃链路，供递归 CTE）。避免跨库分布式事务。
- **drone_id = 0 = 地面站**：递归拓扑的终点，种子数据需包含。
- **审计/告警用触发器**：drones/missions 状态变更写 audit_log；体现"触发器"教学点。

## Risks / Trade-offs
- 快照表存在数据时效性（取决于回写频率）→ 调度用最新快照，可接受秒级延迟；文档注明。
- 双库无外键级联 → 应用层保证 drone_id 一致性；种子数据先建 PG 再灌 TDengine。
- TDengine 方言与 PG 不同 → DDL 分两个文件，各自方言，互不混用。

## Migration Plan
1. 先执行 `schema_pg.sql` 建关系库。
2. 执行 `schema_tdengine.sql` 建时序库。
3. 执行 `seed_pg.sql` 灌关系种子数据（依赖 PG schema）。
4. 遥测历史数据由 S3 遥测模拟器灌入 TDengine（本变更只给 INSERT 示例，不批量灌）。
回滚：DROP DATABASE 各自重建（开发期，无生产数据）。

## Open Questions
- 密码 hash 用 bcrypt 占位值，实际登录逻辑在 S2 定。
- TDengine 数据保留 KEEP 天数暂定 365 天 / DURATION 10 天，压测阶段再调。
