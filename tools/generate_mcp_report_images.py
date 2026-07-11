import asyncio
import json
import os
import shutil
import winreg
from collections import deque
from pathlib import Path

import requests
from fastmcp import Client


def load_user_environment() -> None:
    names = [
        "MCP_API_KEY",
        "MCP_API_BASE",
        "MCP_IMAGE_OUTPUT_DIR",
        "MCP_IMAGE_MODEL",
        "MCP_API_FORMAT",
    ]
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as key:
        for name in names:
            value, _ = winreg.QueryValueEx(key, name)
            os.environ[name] = value


def payload_of(result):
    for attr in ("data", "structured_content"):
        value = getattr(result, attr, None)
        if isinstance(value, dict):
            return value
    for item in getattr(result, "content", []) or []:
        text = getattr(item, "text", None)
        if text:
            try:
                value = json.loads(text)
                if isinstance(value, dict):
                    return value
            except json.JSONDecodeError:
                pass
    return {}


def save_payload(payload: dict, target: Path) -> None:
    output_path = payload.get("output_path")
    image_url = payload.get("image_url")
    if output_path:
        shutil.copyfile(output_path, target)
    elif image_url:
        response = requests.get(image_url, timeout=(15, 120))
        response.raise_for_status()
        target.write_bytes(response.content)
    else:
        raise RuntimeError("MCP result had no output_path or image_url")


STYLE = (
    "Professional academic technology infographic for a Chinese university database course report. "
    "Clean white or very light gray background, restrained blue, cyan, green and amber palette, "
    "clear hierarchy, flat vector-style icons, precise arrows, balanced landscape composition, "
    "high legibility, no logo, no watermark, no decorative gradient, no irrelevant text. "
)


IMAGES = [
    (
        "02",
        "02_平台核心能力总览_MCP.png",
        STYLE
        + "Central node labeled exactly 'FANET平台', surrounded by six capability modules labeled exactly: "
        + "'无人机管理', '任务调度', '遥测与拓扑', '告警处置', '仿真工作台', '智能分析'. "
        + "Use a radial capability overview with simple technical icons. All Chinese text must be exact.",
    ),
    (
        "03",
        "03_系统角色与业务用例图_MCP.png",
        STYLE
        + "UML-style use case diagram. Three actors labeled exactly '管理员', '操作员', '无人机/模拟器'. "
        + "Use cases labeled exactly '身份认证', '无人机状态', '任务调度', '遥测分析', '拓扑查询', "
        + "'告警处置', '仿真控制', 'AI分析'. Connect actors to relevant use cases. All labels exact.",
    ),
    (
        "04",
        "04_系统功能结构图_MCP.png",
        STYLE
        + "Hierarchical functional structure diagram. Root labeled exactly '无人机自组网平台'. "
        + "Second level modules: '认证权限', '无人机管理', '任务管理', '数据分析', '运维告警', '扩展能力'. "
        + "Bottom infrastructure labels: 'PostgreSQL', 'TDengine', 'Redis', 'MQTT / WebSocket'.",
    ),
    (
        "05",
        "05_数据分类与存储去向图_MCP.png",
        STYLE
        + "Data classification and storage routing infographic with three left data groups and three right stores. "
        + "Exact labels: '事务型关系数据' routes to 'PostgreSQL 16'; '高频时序数据' routes to 'TDengine 3.x'; "
        + "'热点实时数据' routes to 'Redis'. Add small accurate labels '任务/用户/告警', '遥测/链路历史', '最新状态/排名'.",
    ),
    (
        "06",
        "06_系统总体架构图_MCP.png",
        STYLE
        + "Layered architecture diagram for a UAV telemetry platform. Left to right: '无人机/仿真器' to 'EMQX / MQTT' "
        + "to 'Spring Boot + MyBatis' to 'Vue 3 + ECharts'. Below the backend show three stores: 'PostgreSQL 16', "
        + "'TDengine 3.x', 'Redis'. Arrows labeled 'MQTT/HTTP', 'REST', 'WebSocket'. Exact text only.",
    ),
    (
        "07",
        "07_后端分层与双数据源结构图_MCP.png",
        STYLE
        + "Backend layered architecture. Horizontal layers labeled exactly '接口与通信层', '业务服务层', '数据访问层', '数据库层'. "
        + "Split the data access layer into 'PostgreSQL Mapper' and 'TDengine Mapper', each with an independent connection pool. "
        + "Show 'Controller / WebSocket / MQTT Consumer' at the top and exact database labels at the bottom.",
    ),
    (
        "08",
        "08_双数据库数据分布图_MCP.png",
        STYLE
        + "Polyglot persistence diagram centered on 'Spring Boot应用编排'. Left database 'PostgreSQL 16' contains exact groups "
        + "'用户/无人机/任务', '告警/审计', '最新状态/链路快照', '仿真场景'. Right database 'TDengine 3.x' contains "
        + "'telemetry' and 'network_links'. Add a dashed snapshot synchronization arrow from TDengine to PostgreSQL.",
    ),
    (
        "09",
        "09_跨库快照协作图_MCP.png",
        STYLE
        + "Five-step left-to-right data pipeline with exact step labels: '1 消息进入', '2 时序落库', '3 最新值提取', "
        + "'4 快照上移', '5 实时消费'. Under steps show exact technical labels 'MQTT/HTTP', 'TDengine', 'LAST', "
        + "'drone_latest / network_links_snapshot', 'Redis / WebSocket'. Add a callout labeled '一致性边界'.",
    ),
    (
        "10",
        "10_实时遥测数据流图_MCP.png",
        STYLE
        + "Real-time telemetry data flow. Exact nodes and order: '无人机/模拟器' -> 'EMQX' -> '后端消费者'. "
        + "From backend split to '历史分析 TDengine' and '实时状态 PostgreSQL + Redis', then converge at 'Vue前端'. "
        + "Use labels 'MQTT', 'REST', 'WebSocket' and the callout '先全量，后增量'.",
    ),
    (
        "11",
        "11_JWT认证与权限流程图_MCP.png",
        STYLE
        + "JWT authentication and RBAC flow. Exact sequence: '用户' -> '登录接口' -> '签发JWT' -> 'JWT过滤器'. "
        + "Then branch to '操作员接口' and '管理员接口'. Include exact labels '401 未认证', '403 权限不足', "
        + "'WebSocket握手校验'. Security-focused but clean academic diagram.",
    ),
    (
        "12",
        "12_PostgreSQL核心ER图_MCP.png",
        STYLE
        + "Database entity relationship diagram with exact English table names: users, drone_models, drones, missions, "
        + "mission_assignments, waypoints, alerts, audit_log, drone_latest, network_links_snapshot. "
        + "Clearly show users 1-to-many missions; drone_models 1-to-many drones; missions and drones connected through "
        + "mission_assignments; missions 1-to-many waypoints; drones 1-to-many alerts; drones 1-to-1 drone_latest. "
        + "Use PK and FK notation. Do not invent tables or fields.",
    ),
    (
        "13",
        "13_仿真场景表关系图_MCP.png",
        STYLE
        + "Database relationship diagram with three exact table names: sim_scenarios, sim_scenario_drones, sim_scenario_links. "
        + "sim_scenarios has one-to-many relations to both child tables through scenario_id. Add an explicit note exactly "
        + "'无人机编号从1开始，目标0表示地面站'. Use PK/FK notation and no invented tables.",
    ),
    (
        "14",
        "14_任务并发分配流程图_MCP.png",
        STYLE
        + "Concurrency control flow for two requests competing for one UAV. Exact labels: '请求A', '请求B', "
        + "'BEGIN', 'SELECT ... FOR UPDATE', '获得行锁', '等待并重查', 'INSERT成功', '发现冲突并回滚', "
        + "'仅一个请求成功'. Show two parallel lanes converging on a PostgreSQL transaction lock.",
    ),
]


async def main() -> None:
    load_user_environment()
    server = Path.home() / ".codex" / "mcp" / "image_mcp.py"
    destination = Path(os.environ["MCP_IMAGE_OUTPUT_DIR"])
    destination.mkdir(parents=True, exist_ok=True)
    config = {
        "mcpServers": {
            "image_draw": {
                "command": os.sys.executable,
                "args": [str(server)],
                "env": {
                    name: os.environ[name]
                    for name in (
                        "MCP_API_KEY",
                        "MCP_API_BASE",
                        "MCP_IMAGE_OUTPUT_DIR",
                        "MCP_IMAGE_MODEL",
                        "MCP_API_FORMAT",
                    )
                },
            }
        }
    }

    pending = deque(IMAGES)
    active: dict[str, tuple[str, str, str, int]] = {}
    completed = 0

    async with Client(config, timeout=300) as client:
        while pending or active:
            while pending and len(active) < 2:
                item = pending.popleft()
                image_id, filename, prompt = item[:3]
                retries = item[3] if len(item) == 4 else 0
                result = await client.call_tool(
                    "start_image", {"prompt": prompt, "size": "1536x1024"}
                )
                payload = payload_of(result)
                if not payload.get("ok"):
                    raise RuntimeError(f"{image_id} failed to start: {payload.get('error')}")
                active[payload["job_id"]] = (image_id, filename, prompt, retries)
                print(f"started={image_id}")

            await asyncio.sleep(5)
            for job_id, metadata in list(active.items()):
                image_id, filename, prompt, retries = metadata
                result = await client.call_tool("check_image", {"job_id": job_id})
                payload = payload_of(result)
                status = payload.get("status")
                if status in ("pending", "running"):
                    continue
                del active[job_id]
                if payload.get("ok") and status == "completed":
                    save_payload(payload, destination / filename)
                    completed += 1
                    print(f"saved={image_id}:{filename}")
                elif retries < 1:
                    pending.appendleft((image_id, filename, prompt, retries + 1))
                    print(f"retry={image_id}:{payload.get('error', 'unknown')}")
                else:
                    print(f"failed={image_id}:{payload.get('error', 'unknown')}")

    print(f"completed={completed}/{len(IMAGES)}")


if __name__ == "__main__":
    asyncio.run(main())
