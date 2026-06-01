# 数据库初始化说明（FANET 平台）

双库混合架构：**PostgreSQL 16** 承载关系/事务核心，**TDengine 3.x** 承载遥测时序。不使用 MySQL。

## 文件清单

| 文件 | 目标库 | 作用 |
|---|---|---|
| `schema_pg.sql` | PostgreSQL 16 | 关系表、索引、外键、视图、触发器、跨库快照表 |
| `schema_tdengine.sql` | TDengine 3.x | telemetry / network_links 超级表 + 数据保留策略 |
| `seed_pg.sql` | PostgreSQL 16 | 关系侧逼真种子数据（型号/无人机/用户/任务/告警） |

## 初始化顺序

```bash
# 1) PostgreSQL：建关系 schema（假设库名 fanet，用户 fanet_app）
psql -h 127.0.0.1 -U fanet_app -d fanet -f db/schema_pg.sql

# 2) PostgreSQL：灌种子数据（依赖 schema_pg.sql）
psql -h 127.0.0.1 -U fanet_app -d fanet -f db/seed_pg.sql

# 3) TDengine：建时序 schema（taos CLI 或 REST）
taos -h 127.0.0.1 -f db/schema_tdengine.sql
```

> 遥测历史数据不在种子里，由 S3 遥测模拟器持续写入 TDengine（`schema_tdengine.sql` 末尾有 INSERT 示例）。

## 跨库约定

- TDengine 不设外键；`drone_id` 标签与 PG `drones.drone_id` 同值，应用层据此关联。
- 接入服务写 TDengine 遥测时，同步回写 PG `drone_latest`（最新状态快照），供任务调度事务单库内读电量。
- 拓扑分析前，从 TDengine 取最近一窗活跃链路写入 PG `network_links_snapshot`，再在 PG 跑递归 CTE。
- `drone_id = 0` 为地面站，递归拓扑的路径终点。

## 安全提示

- 种子用户密码为 bcrypt 占位 hash（明文 `password`），仅供开发，**上线前必须更换**。
- 数据库口令、大模型 API Key 一律走环境变量，禁止提交 Git。
- NL2SQL 对两库各用一个**只读账号** + SQL 白名单（只允许 SELECT）。

## 只读账号（NL2SQL 用，示例）

```sql
-- PostgreSQL 只读账号
CREATE ROLE fanet_ro LOGIN PASSWORD :'ro_pwd';   -- 口令走环境变量传入
GRANT CONNECT ON DATABASE fanet TO fanet_ro;
GRANT USAGE ON SCHEMA public TO fanet_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO fanet_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO fanet_ro;
```
