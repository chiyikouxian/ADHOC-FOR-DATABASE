# FANET Setup

Recommended reading order:

1. [README.md](README.md)
2. [SETUP.md](SETUP.md)
3. [DEMO.md](DEMO.md)

## Prerequisites

- JDK 17 or newer
- Node.js 20 or newer
- Docker Desktop

Quick checks:

```powershell
java -version
node -v
npm -v
docker --version
docker compose version
```

## 1. Start infrastructure

From the project root:

```powershell
docker compose up -d
docker compose ps
```

Services started by Compose:

- PostgreSQL: `127.0.0.1:5432`
- TDengine REST: `127.0.0.1:6041`
- Redis: `127.0.0.1:6379`
- EMQX MQTT: `127.0.0.1:1883`
- EMQX Dashboard: `http://127.0.0.1:18083`

## 2. Initialize TDengine schema

PostgreSQL schema and seed data are loaded automatically by Compose on first boot.
TDengine needs one manual initialization step:

```powershell
docker exec -i fanet-tdengine taos -f /tmp/schema_tdengine.sql
docker exec -i fanet-tdengine taos -s "show fanet.stables;"
```

## 3. Start backend

Recommended:

```powershell
cd backend
.\start.bat
```

Notes:

- Default backend port: `18080`
- `start.ps1` will download Maven automatically if needed
- You can override it:

```powershell
$env:BACKEND_PORT="18081"
.\start.ps1
```

Health check:

```powershell
curl http://127.0.0.1:18080/api/health
```

Expected:

```json
{"status":"UP"}
```

## 4. Start frontend

```powershell
cd frontend
npm install
npm run dev
```

Default Vite proxy target:

- `http://127.0.0.1:18080`

Override it when needed:

```powershell
$env:VITE_BACKEND_URL="http://127.0.0.1:18081"
npm run dev
```

## 5. Start simulator

To feed telemetry and topology data:

```powershell
cd simulator
npm install
node simulator-mqtt.js
```

Recommendation:

- wait `30-60` seconds before testing topology, telemetry aggregation, or endurance prediction

## 6. Useful smoke tests

```powershell
curl http://127.0.0.1:18080/api/missions
curl http://127.0.0.1:18080/api/topology
curl http://127.0.0.1:18080/api/alerts
curl "http://127.0.0.1:18080/api/telemetry/drone/1/series?window=1m&minutes=60"
curl -X POST http://127.0.0.1:18080/api/ai/endurance -H "Content-Type: application/json" -d "{\"droneId\":1,\"historyMinutes\":60,\"useAI\":false}"
```

## 7. Common issues

### Frontend cannot reach backend

Check:

- backend is running
- frontend Vite proxy points to the right port
- `VITE_BACKEND_URL` is not stale in the current terminal

### TDengine time query errors

The backend now sends concrete timestamps to TDengine. If this reappears, confirm the running JAR was rebuilt after the latest code changes.

### Chinese text looks broken

If your PostgreSQL data was initialized before the latest text fix, run:

```powershell
Get-Content db\fix_runtime_text.sql | docker exec -i fanet-postgres psql -U fanet_app -d fanet
```

Then restart the backend if you also updated source code.

### Port already in use

Run backend on another port:

```powershell
$env:BACKEND_PORT="18081"
.\start.ps1
```
