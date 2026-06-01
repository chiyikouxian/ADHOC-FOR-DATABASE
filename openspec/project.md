# Project Context

## Purpose
无人机自组网集群任务管理与遥测分析平台（FANET Mission & Telemetry Platform）。
真实嵌入式无人机自组网项目的**地面指挥与数据分析后台**：一端通过 MQTT/HTTP 接收无人机集群遥测，
另一端为操作员提供实时态势监控、任务调度、拓扑分析、数据复盘与 AI 智能分析。
定位为**数据库课程设计**项目，评分重点在 SQL 功底（手写裸 SQL），加分重点在 AI 功能与压测优化。
完整方案见仓库根目录 `无人机自组网平台-项目方案.md`。

## Tech Stack
- 前端：Vue 3 + ECharts
- 后端：Spring Boot + MyBatis（**双数据源**）+ Spring Security（JWT）
- 缓存/推送：Redis（最新状态缓存 + Sorted Set 链路排行 + Pub/Sub → WebSocket）
- **关系/事务核心库：PostgreSQL 16**（users、drones、missions、mission_assignments、waypoints、alerts、audit_log、视图、快照表）
- **遥测时序库：TDengine 3.x**（telemetry、network_links 超级表）
- 接入层：MQTT Broker（EMQX/Mosquitto）
- AI：大模型 API（NL2SQL / 异常检测 / 续航预测）
- **不使用 MySQL。**

## Project Conventions

### Code Style
- 后端 Java：分层（controller / service / mapper），DTO 与实体分离。
- **核心 SQL 一律 MyBatis XML 手写裸 SQL**，禁止 ORM 自动生成隐藏 SQL（课设评分重点）。
- 命名：Java camelCase / 类 PascalCase；SQL 表名列名 snake_case。
- 密钥（大模型 API Key、数据库口令）走环境变量，禁止硬编码或提交 Git。

### Architecture Patterns
- **双数据源分库（polyglot persistence）**：关系/事务 → PostgreSQL；遥测时序 → TDengine。应用层编排，不做跨库分布式事务。
- 后端配两套 `DataSource` + HikariCP 连接池 + MyBatis `SqlSessionFactory`，按 Mapper 包路径区分库。
- 跨库协作：写 TDengine 遥测时同步回写 PG `drone_latest`；拓扑分析前从 TDengine 取链路快照落 PG `network_links_snapshot`，再在 PG 跑递归 CTE。
- 三段核心 SQL 全在 PostgreSQL：任务调度 `SELECT ... FOR UPDATE` 行锁、多跳 `WITH RECURSIVE`、报表 `RANK()/AVG() OVER`。
- TDengine 用超级表 + 标签子表建模；`KEEP`/`DURATION` 管理数据保留与时间分片。

### Testing Strategy
- JUnit 5 + Mockito，覆盖率 ≥ 80%（核心调度与查询必覆盖）。
- 重点测任务调度并发：多线程模拟并发抢占同一架机，断言只有一个成功。
- 压测用 JMeter/wrk 打 TDengine 遥测写入；关系侧 `EXPLAIN ANALYZE` 调优。

### Git Workflow
- 约定式提交（feat/fix/refactor/docs/test/chore/perf/ci）。
- 自有 Git 仓库托管，**不得 fork 或抄袭 GitHub 现成源码**（课程硬约束）。

## Domain Context
FANET（Flying Ad-hoc Network）= 无人机自组网：多机无固定基站、相互中继、协同任务。
平台解决三痛点：海量遥测沉淀、动态拓扑可视化、任务调度并发冲突。
约定 `drone_id = 0` 为地面站，递归拓扑求各机到地面站的多跳路径。

## Important Constraints
- 技术栈固定：Vue 3 + Spring Boot + MyBatis + Redis + PostgreSQL + TDengine，不擅自更换。**不使用 MySQL。**
- NL2SQL 双库路由 + 各自只读账号 + SQL 白名单（只允许 SELECT、表名/超表名白名单、禁止多语句/DML/DDL）。
- 核心 SQL 手写、不抄现成源码、密钥进环境变量、每个技术栈都要有真实用途。

## External Dependencies
- MQTT Broker（EMQX / Mosquitto）：接收无人机遥测上报。
- 大模型 API：NL2SQL / 异常检测 / 续航预测（Key 走环境变量）。
- Redis：最新状态缓存、链路质量 Sorted Set 排行、Pub/Sub → WebSocket 推送。
