/**
 * FANET 遥测模拟器
 * 模拟 N 架无人机持续上报遥测数据到后端 HTTP 接口
 * 用法: node simulator.js [架数] [间隔ms]
 * 示例: node simulator.js 5 1000   (5架机,每秒上报一次)
 */

const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const DRONE_COUNT = parseInt(process.argv[2] || '5', 10);
const INTERVAL_MS = parseInt(process.argv[3] || '1000', 10);

const BASE_LAT = 31.23;
const BASE_LON = 121.47;

const drones = Array.from({ length: DRONE_COUNT }, (_, i) => ({
  droneId: i + 1,
  modelId: (i % 3) + 1,
  lat: BASE_LAT + (Math.random() - 0.5) * 0.02,
  lon: BASE_LON + (Math.random() - 0.5) * 0.02,
  alt: 100 + Math.random() * 100,
  battery: 80 + Math.random() * 20,
  rssi: -50 - Math.floor(Math.random() * 40),
}));

function tick() {
  const records = drones.map(d => {
    d.lat += (Math.random() - 0.5) * 0.0005;
    d.lon += (Math.random() - 0.5) * 0.0005;
    d.alt += (Math.random() - 0.5) * 2;
    d.battery = Math.max(0, d.battery - Math.random() * 0.1);
    d.rssi = Math.max(-95, Math.min(-40, d.rssi + (Math.random() - 0.5) * 3));
    return {
      droneId: d.droneId,
      modelId: d.modelId,
      lat: parseFloat(d.lat.toFixed(6)),
      lon: parseFloat(d.lon.toFixed(6)),
      alt: parseFloat(d.alt.toFixed(2)),
      batteryPct: parseFloat(d.battery.toFixed(2)),
      rssi: Math.round(d.rssi),
    };
  });
  return records;
}

async function send(records) {
  const body = JSON.stringify(records);
  try {
    const res = await fetch(`${BACKEND_URL}/api/telemetry`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
    });
    const data = await res.json();
    return data;
  } catch (err) {
    return { error: err.message };
  }
}

async function main() {
  console.log(`FANET 遥测模拟器启动: ${DRONE_COUNT} 架机, 间隔 ${INTERVAL_MS}ms`);
  console.log(`目标: ${BACKEND_URL}/api/telemetry`);
  let count = 0;
  setInterval(async () => {
    const records = tick();
    const result = await send(records);
    count++;
    if (count % 10 === 0 || result.error) {
      console.log(`[#${count}] ${result.error ? 'ERR: ' + result.error : 'OK ingested=' + result.ingested}`);
    }
  }, INTERVAL_MS);
}

main();
