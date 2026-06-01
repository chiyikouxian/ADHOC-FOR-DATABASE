# 环境搭建指南（FANET 平台）

> 中间件全部走 Docker，你只需在 Windows 上装 3 个开发工具。

## 一、你需要手动安装（3 个）

| 工具 | 版本 | 用途 | 验证命令 |
|---|---|---|---|
| **JDK** | 21 (LTS) | Spring Boot 后端 | `java -version` |
| **Node.js** | 20 (LTS) | Vue 3 前端（自带 npm） | `node --version` |
| **Docker Desktop** | 最新 | 起 PG/TDengine/Redis/EMQX | `docker --version` |

> Git 已有（建议有空升级到新版，非阻塞）。装完上面三个，重开终端让 PATH 生效。

## 二、中间件一键启动（Docker）

```bash
# 1) 复制环境变量样例为 .env（口令走环境变量，不进 Git）
cp .env.example .env

# 2) 拉起全部中间件（首次会下载镜像，需几分钟）
docker compose up -d

# 3) 查看状态，等到 postgres/redis 显示 healthy
docker compose ps
```

### PostgreSQL — 全自动
首次启动数据目录为空时，`db/schema_pg.sql` 和 `db/seed_pg.sql` 会**自动执行**（挂在 initdb.d）。无需手动建表。

### TDengine — 需手动跑一次 schema
TDengine 没有 initdb.d 机制，容器起来后执行一次：

```bash
docker exec -i fanet-tdengine taos -f /tmp/schema_tdengine.sql
# 验证：
docker exec -i fanet-tdengine taos -s "show fanet.stables;"
```

## 三、连接信息（默认值，可在 .env 改）

| 服务 | 地址 | 端口 | 账号 |
|---|---|---|---|
| PostgreSQL | 127.0.0.1 | 5432 | fanet_app / .env 中口令 |
| TDengine REST | 127.0.0.1 | 6041 | 默认 root/taosdata |
| Redis | 127.0.0.1 | 6379 | 无 |
| EMQX MQTT | 127.0.0.1 | 1883 | — |
| EMQX 控制台 | http://127.0.0.1:18083 | 18083 | admin / public |

## 四、验证 schema 是否就绪

```bash
# PostgreSQL：应看到 8 张表 + 2 张快照表 + 1 视图
docker exec -i fanet-postgres psql -U fanet_app -d fanet -c "\dt"
docker exec -i fanet-postgres psql -U fanet_app -d fanet -c "SELECT count(*) FROM drones;"  # 应为 8

# TDengine：应看到 telemetry / network_links 两张超级表
docker exec -i fanet-tdengine taos -s "show fanet.stables;"
```

## 五、常用运维

```bash
docker compose stop          # 停（保留数据）
docker compose start         # 再启
docker compose down          # 停并删容器（卷数据仍在）
docker compose down -v       # 连数据卷一起删（重置 PG，会重新跑 initdb）
docker compose logs -f postgres   # 看某服务日志
```

## 六、后续阶段才需要

- **大模型 API Key**（S6 NL2SQL）：填进 `.env` 的 `LLM_API_KEY`，严禁提交 Git。
- **JMeter**（S6 压测）：到时再装。

---
S1 数据库已就绪。装好上面三个工具 + `docker compose up -d` 即可进入 S2 后端开发。
