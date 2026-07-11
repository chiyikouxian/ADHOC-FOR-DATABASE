# FANET 无人机自组网平台

数据库课程设计项目，主题为“无人机自组网集群任务管理与遥测分析平台”。

本项目采用双数据库混合架构：

- PostgreSQL 16：任务、无人机、告警、拓扑快照等关系型核心数据
- TDengine 3.x：遥测、链路等高频时序数据
- Redis：最新状态缓存、链路排行、实时推送辅助
- Spring Boot + MyBatis：后端服务与手写 SQL
- Vue 3 + ECharts：前端可视化

## 交付亮点

- 关系型事务与时序写入分库设计，体现“按数据特征选型”
- TDengine `INTERVAL` 时间窗聚合，支持遥测降采样与集群统计
- PostgreSQL 行锁、递归 CTE、窗口函数，覆盖数据库课设重点能力
- 自组网拓扑实时同步：TDengine -> PostgreSQL/Redis -> WebSocket -> 前端
- AI 扩展：续航预测、任务复盘报告
- 已完成压测对比：TDengine 高负载写入优于 PostgreSQL-only 路径

## 当前完成情况

- 后端接口、前端页面、模拟器、压测脚本均可运行
- 默认后端端口为 `18080`
- 前端默认代理到 `http://127.0.0.1:18080`
- 关键中文数据与复盘文案乱码问题已修复

## 仓库入口

- [SETUP.md](SETUP.md)：环境启动与排错
- [DEMO.md](DEMO.md)：3 分钟演示脚本
- [db/README.md](db/README.md)：数据库对象、接口与压测结果
- [无人机自组网平台-项目方案.md](无人机自组网平台-项目方案.md)：项目方案总文档

## 快速启动

1. 启动基础设施

```powershell
docker compose up -d
docker exec -i fanet-tdengine taos -f /tmp/schema_tdengine.sql
```

2. 启动后端

```powershell
Copy-Item .env.example .env
# 编辑 .env，设置一个至少 32 字节的 JWT_SECRET
cd backend
.\start.bat
```

3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

4. 启动模拟器

```powershell
cd simulator
npm install
node simulator-mqtt.js
```

### 可控 MQTT 模拟器

`simulator-mqtt.js` 保持上面的命令行启动方式，并订阅 `fanet/simulator/control`，可在运行中接收 JSON 控制消息：

```json
{"command":"apply","params":{"publishIntervalMs":500,"motionMode":"orbit"}}
```

支持 `start`、`stop`、`apply`、`status`。启动时设置 `SIM_AUTOSTART=false` 可等待 `start` 指令；运行状态会保留发布到 `fanet/simulator/status`。

## 快速验收

```powershell
curl http://127.0.0.1:18080/api/health
curl http://127.0.0.1:18080/api/missions
curl http://127.0.0.1:18080/api/topology
curl http://127.0.0.1:18080/api/alerts
curl "http://127.0.0.1:18080/api/telemetry/drone/1/series?window=1m&minutes=60"
curl -X POST http://127.0.0.1:18080/api/ai/endurance -H "Content-Type: application/json" -d "{\"droneId\":1,\"historyMinutes\":60,\"useAI\":false}"
```

预期：

- `/api/health` 返回 `{"status":"UP"}`
- `/api/topology` 能看到 `nodes` 与 `edges`
- `/api/ai/endurance` 在模拟器运行一段时间后返回预测结果

## 压测结论摘要

最近一次 `node simulator/load-test.js --compare` 对比结果：

- 10 架：PG `126.3 TPS`，TD `160.7 TPS`
- 100 架：PG `143.8 TPS`，TD `169.1 TPS`
- 500 架：PG `52.5 TPS`，TD `106.4 TPS`

结论：在高频遥测写入场景下，TDengine-only 写入路径整体优于 PostgreSQL-only 写入路径，且负载越高差距越明显。
