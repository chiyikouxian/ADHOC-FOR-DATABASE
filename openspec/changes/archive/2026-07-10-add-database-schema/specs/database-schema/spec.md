## ADDED Requirements

### Requirement: 关系核心库 Schema（PostgreSQL）
系统 SHALL 在 PostgreSQL 16 中提供承载用户、无人机、任务、告警等关系/事务核心数据的 schema，包含外键约束、CHECK 约束、索引、视图与触发器。

#### Scenario: 创建关系核心表
- **WHEN** 执行 `db/schema_pg.sql`
- **THEN** 创建 users、drone_models、drones、missions、mission_assignments、waypoints、alerts、audit_log 八张表
- **AND** 每个外键引用均存在且约束正确（如 drones.model_id → drone_models.model_id）
- **AND** 状态字段（drones.status、missions.status、mission_assignments.status）带 CHECK 约束

#### Scenario: 提供最新状态视图
- **WHEN** 查询视图 v_drone_latest
- **THEN** 返回每架无人机的最新关系侧状态（含 drone_latest 快照中的电量/位置）

### Requirement: 跨库快照表
系统 SHALL 在 PostgreSQL 中提供两张快照表，桥接 TDengine 时序数据，使任务调度事务与递归拓扑分析可在 PG 单库内完成，避免跨库分布式事务。

#### Scenario: 最新状态快照支撑调度行锁
- **WHEN** 任务调度事务以 `SELECT ... FOR UPDATE` 锁定候选无人机
- **THEN** 可从 drone_latest 表读到该机最新电量，无需跨库查询 TDengine

#### Scenario: 链路快照支撑递归 CTE
- **WHEN** 拓扑分析前将 TDengine 最近一窗活跃链路导入 network_links_snapshot
- **THEN** 可在 PG 上对该快照表执行 `WITH RECURSIVE` 求多跳路径

### Requirement: 遥测时序库 Schema（TDengine）
系统 SHALL 在 TDengine 3.x 中以超级表（STable）+ 标签子表模型提供遥测与链路时序数据的 schema，并配置数据保留策略。

#### Scenario: 创建遥测超级表
- **WHEN** 执行 `db/schema_tdengine.sql`
- **THEN** 创建 telemetry 超级表（普通列 ts/lat/lon/alt/battery_pct/rssi + 标签 drone_id/model_id）
- **AND** 创建 network_links 超级表（普通列 ts/link_quality/is_active + 标签 src_drone_id/dst_drone_id）
- **AND** 数据库配置 DURATION 与 KEEP 实现按时间分片与过期数据自动保留

#### Scenario: 按设备写入自动落子表
- **WHEN** 以某 drone_id 标签写入 telemetry 超级表
- **THEN** 该无人机的数据落入对应子表，查询可走标签过滤

### Requirement: 种子数据
系统 SHALL 提供逼真的关系侧种子数据，覆盖型号、无人机（含 drone_id=0 地面站）、用户、历史任务与分配、航点、初始快照与告警，供开发与演示使用。

#### Scenario: 灌入种子数据
- **WHEN** 在 schema_pg.sql 之后执行 db/seed_pg.sql
- **THEN** 各表含可用于演示的样本数据
- **AND** 存在 drone_id=0 的地面站记录，供递归拓扑求路径终点
