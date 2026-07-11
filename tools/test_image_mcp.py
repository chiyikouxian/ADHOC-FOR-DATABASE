import asyncio
import json
import os
import shutil
import winreg
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


def result_payload(result):
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

    prompt = (
        "Create a polished realistic engineering visualization for a Chinese university course report. "
        "Show six civilian quadrotor UAVs forming a flying ad-hoc wireless mesh network above a green mountainous "
        "and suburban landscape. Show clean blue wireless relay links between drones, including a multi-hop route "
        "to a clearly visible ground control station with antenna and screens. Academic, credible, daylight, "
        "wide composition, no military weapons, no logos, no watermark, no words or labels."
    )

    async with Client(config, timeout=240) as client:
        tools = await client.list_tools()
        print("tools=" + ",".join(sorted(tool.name for tool in tools)))
        result = await client.call_tool("draw_image", {"prompt": prompt, "size": "1024x1024"})
        payload = result_payload(result)
        print("ok=" + str(payload.get("ok")))
        print("fields=" + ",".join(sorted(payload.keys())))
        if not payload.get("ok"):
            print("error=" + str(payload.get("error", "unknown")))
            raise SystemExit(2)

        target = destination / "01_fanet应用场景图_MCP.png"
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
        print("saved=" + str(target))


if __name__ == "__main__":
    asyncio.run(main())
