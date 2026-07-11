import asyncio
import os
from collections import deque
from pathlib import Path

from fastmcp import Client

from generate_mcp_report_images import (
    STYLE,
    load_user_environment,
    payload_of,
    save_payload,
)


IMAGES = [
    (
        "03",
        "03_系统角色与业务用例图_MCP_v2.png",
        STYLE
        + "Create a strict UML use-case diagram. Exactly three actors on the left: 管理员, 操作员, 无人机/模拟器. "
        + "Exactly eight unique use cases in a clean two-column grid: 身份认证, 无人机状态, 任务调度, 遥测分析, "
        + "拓扑查询, 告警处置, 仿真控制, AI分析. Each phrase appears exactly once. No duplicate oval, no extra title text.",
    ),
    (
        "07",
        "07_后端分层与双数据源结构图_MCP_v2.png",
        STYLE
        + "Four-layer backend architecture with minimal labels. Top: Controller / WebSocket / MQTT Consumer. "
        + "Second: Service Layer. Third split into PostgreSQL Mapper + HikariCP and TDengine Mapper + HikariCP. "
        + "Bottom databases PostgreSQL 16 and TDengine 3.x. No other words. Do not translate HikariCP.",
    ),
    (
        "08",
        "08_双数据库数据分布图_MCP_v2.png",
        STYLE
        + "Title must be exactly 双数据库数据分布. Center: Spring Boot. Left: PostgreSQL 16 with four labels 用户/无人机/任务, "
        + "告警/审计, 最新状态/链路快照, 仿真场景. Right: TDengine 3.x with only telemetry and network_links. "
        + "One dashed arrow from TDengine to PostgreSQL labeled 快照同步. No subtitle and no other title.",
    ),
    (
        "10",
        "10_实时遥测数据流图_MCP_v2.png",
        STYLE
        + "A precise directed data-flow diagram. Arrows must follow only these directions: 无人机/模拟器 -> EMQX -> 后端消费者. "
        + "后端消费者 -> TDengine历史数据. 后端消费者 -> PostgreSQL + Redis实时状态. "
        + "后端消费者 -> Vue前端 labeled WebSocket. Vue前端 -> 后端消费者 labeled REST查询. "
        + "Do not draw any arrow from Vue前端 to 无人机/模拟器. Minimal exact labels only.",
    ),
    (
        "12",
        "12_PostgreSQL核心ER图_MCP_v2.png",
        STYLE
        + "Entity relationship overview using table-name boxes only, with no field names at all. Exact table names: users, "
        + "drone_models, drones, missions, mission_assignments, waypoints, alerts, audit_log, drone_latest, network_links_snapshot. "
        + "Relations: users 1:N missions; drone_models 1:N drones; missions 1:N mission_assignments; drones 1:N mission_assignments; "
        + "missions 1:N waypoints; drones 1:N alerts; drones 1:1 drone_latest. network_links_snapshot has dashed logical links to drones. "
        + "audit_log has dashed audit links to missions and drones. Do not invent fields or additional tables.",
    ),
    (
        "13",
        "13_仿真场景表关系图_MCP_v2.png",
        STYLE
        + "Simple three-table relationship diagram with table names only and no fields. Center top sim_scenarios. "
        + "Bottom left sim_scenario_drones, bottom right sim_scenario_links. Show sim_scenarios 1:N to each child table. "
        + "Add one exact note: 无人机编号从1开始，目标0表示地面站. No invented fields or extra tables.",
    ),
]


async def main() -> None:
    load_user_environment()
    server = Path.home() / ".codex" / "mcp" / "image_mcp.py"
    destination = Path(os.environ["MCP_IMAGE_OUTPUT_DIR"])
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
    active = {}
    completed = 0

    async with Client(config, timeout=300) as client:
        while pending or active:
            while pending and len(active) < 2:
                image_id, filename, prompt = pending.popleft()
                result = await client.call_tool("start_image", {"prompt": prompt, "size": "1536x1024"})
                payload = payload_of(result)
                if not payload.get("ok"):
                    raise RuntimeError(f"{image_id} failed to start: {payload.get('error')}")
                active[payload["job_id"]] = (image_id, filename)
                print(f"started={image_id}", flush=True)
            await asyncio.sleep(5)
            for job_id, metadata in list(active.items()):
                result = await client.call_tool("check_image", {"job_id": job_id})
                payload = payload_of(result)
                if payload.get("status") in ("pending", "running"):
                    continue
                image_id, filename = metadata
                del active[job_id]
                if not payload.get("ok"):
                    raise RuntimeError(f"{image_id} failed: {payload.get('error')}")
                save_payload(payload, destination / filename)
                completed += 1
                print(f"saved={image_id}:{filename}", flush=True)
    print(f"completed={completed}/{len(IMAGES)}", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
