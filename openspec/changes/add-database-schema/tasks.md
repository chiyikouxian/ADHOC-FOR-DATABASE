## 1. PostgreSQL 关系核心 schema（db/schema_pg.sql）
- [x] 1.1 建 users、drone_models、drones 表（外键、CHECK、状态机字段）
- [x] 1.2 建 missions、mission_assignments、waypoints 表（主从、多对多桥表、顺序字段）
- [x] 1.3 建 alerts、audit_log 表
- [x] 1.4 建跨库快照表 drone_latest、network_links_snapshot
- [x] 1.5 建索引（复合索引覆盖调度/告警/报表查询）
- [x] 1.6 建视图 v_drone_latest
- [x] 1.7 建审计触发器（drones/missions 写 audit_log）与告警去重约束

## 2. TDengine 遥测时序 schema（db/schema_tdengine.sql）
- [x] 2.1 建库（含 DURATION/KEEP 数据保留策略）
- [x] 2.2 建 telemetry 超级表（普通列 + drone_id/model_id 标签）
- [x] 2.3 建 network_links 超级表（普通列 + src/dst 标签）
- [x] 2.4 写子表自动创建说明与示例 INSERT

## 3. 种子数据（db/seed_pg.sql）
- [x] 3.1 型号字典 + 无人机实例（含 drone_id=0 地面站）
- [x] 3.2 用户（管理员/操作员，密码占位 bcrypt hash）
- [x] 3.3 历史任务 + 分配 + 航点
- [x] 3.4 drone_latest 初始快照、若干 alerts

## 4. 校验与文档
- [x] 4.1 人工核对 PG DDL 语法（PostgreSQL 16）
- [x] 4.2 人工核对 TDengine DDL 语法（TDengine 3.x）
- [x] 4.3 在 db/README.md 写明初始化顺序与连接说明
