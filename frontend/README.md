# Frontend

## Development

```powershell
npm install
npm run dev
```

By default, Vite proxies:

- `/api`
- `/ws`

to:

- `http://127.0.0.1:18080`

Override the backend target when needed:

```powershell
$env:VITE_BACKEND_URL="http://127.0.0.1:18081"
npm run dev
```

## Build

```powershell
npm run build
```

## Main pages

- `Dashboard`
- `Telemetry`
- `Topology`
- `Missions`
- `Alerts`
- `AI`
