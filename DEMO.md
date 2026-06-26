# 演示脚本

本文件用于课程答辩或录制 3 分钟演示视频，按顺序执行即可。

## 一、演示前准备

需要保证以下组件已启动：

- `docker compose up -d`
- TDengine 已执行过 `schema_tdengine.sql`
- 后端运行在 `http://127.0.0.1:18080`
- 前端 `npm run dev`
- 模拟器 `node simulator-mqtt.js`

可先做健康检查：

```powershell
curl http://127.0.0.1:18080/api/health
```

## 二、推荐演示顺序

### 1. 项目定位与架构

口播建议：

- 这是一个无人机自组网地面指挥与遥测分析平台
- 关系数据使用 PostgreSQL，遥测时序数据使用 TDengine
- 核心设计思想是“按数据特征分库存储”

### 2. 实时拓扑页面

前端打开拓扑页，展示：

- 无人机节点与地面站
- 实时链路边
- WebSocket 实时刷新效果

可配合命令验证：

```powershell
curl http://127.0.0.1:18080/api/topology
curl "http://127.0.0.1:18080/api/telemetry/links/snapshot?seconds=60"
```

讲解点：

- 链路原始数据先进入 TDengine
- 后端定时同步最新快照到 PostgreSQL 和 Redis
- 前端通过 WebSocket 收到拓扑更新

### 3. 遥测时序页面

前端打开遥测页，展示：

- 单机曲线
- 时间窗聚合
- 电量 / RSSI 历史曲线

可配合命令验证：

```powershell
curl "http://127.0.0.1:18080/api/telemetry/drone/1/series?window=1m&minutes=60"
curl "http://127.0.0.1:18080/api/telemetry/drone/1/battery?since=2026-06-26%2000:00:00"
curl "http://127.0.0.1:18080/api/telemetry/drone/1/rssi?since=2026-06-26%2000:00:00"
```

讲解点：

- 这里重点展示 TDengine 的 `INTERVAL` 聚合能力
- 时序库更适合高频写入和按时间窗口统计

### 4. 任务与告警页面

前端打开任务页、告警页，展示：

- 任务列表和任务状态
- 实时告警高亮
- 告警确认按钮

可配合命令验证：

```powershell
curl http://127.0.0.1:18080/api/missions
curl http://127.0.0.1:18080/api/alerts
```

讲解点：

- 任务、告警等核心业务数据放在 PostgreSQL
- 这里适合展示关系建模、事务和 SQL 查询

### 5. AI 续航预测

执行命令：

```powershell
curl -X POST http://127.0.0.1:18080/api/ai/endurance -H "Content-Type: application/json" -d "{\"droneId\":1,\"historyMinutes\":60,\"useAI\":false}"
```

建议讲解：

- 根据历史电量数据拟合放电速率
- 返回预计剩余分钟数、拟合优度 `R²` 和置信度
- 这是数据库数据分析能力的延伸展示

### 6. AI 任务复盘

执行命令：

```powershell
@'
import json, urllib.request
payload = json.dumps({"missionId": 1, "useAI": False}).encode("utf-8")
req = urllib.request.Request(
    "http://127.0.0.1:18080/api/ai/report",
    data=payload,
    headers={"Content-Type": "application/json"},
    method="POST"
)
print(urllib.request.urlopen(req).read().decode("utf-8"))
'@ | python -
```

讲解点：

- 任务复盘会结合 PostgreSQL 任务信息与 TDengine 遥测统计
- 体现双库协作的数据分析链路

### 7. 压测结果

执行命令：

```powershell
cd simulator
node load-test.js --compare
```

可重点说：

- 比较 PostgreSQL-only 写入路径与 TDengine-only 写入路径
- 已验证高并发下 TDengine 更适合遥测写入
- 500 架无人机场景下，TDengine TPS 约为 PostgreSQL 的 2 倍

## 三、3 分钟口播提纲

可以压缩成下面这段逻辑：

1. 项目背景：无人机自组网会持续产生遥测、链路、任务和告警数据。
2. 选型亮点：关系数据进 PostgreSQL，时序数据进 TDengine。
3. 页面展示：拓扑实时更新、遥测曲线、任务与告警。
4. 智能分析：续航预测和任务复盘。
5. 压测结论：TDengine 在高频遥测写入场景下明显优于 PostgreSQL-only。

## 四、演示时的稳妥建议

- 先启动模拟器，等 30 到 60 秒再展示拓扑和续航预测
- PowerShell 下发 JSON 请求时，优先用 `python` 或 `curl.exe`
- 如果 `/api/ai/endurance` 提示数据不足，继续让模拟器运行一会儿再重试
- 如果历史数据库是旧版本并出现中文异常，可执行 `db/fix_runtime_text.sql`
