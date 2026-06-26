const mqtt = require('mqtt');

const BROKER = process.env.MQTT_HOST || 'mqtt://127.0.0.1:1883';
const DRONE_COUNT = parseInt(process.argv[2] || '5', 10);
const INTERVAL_MS = parseInt(process.argv[3] || '2000', 10);

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

// 预定义的自组网拓扑（模拟链路关系）
// 拓扑: 4→3→1→0, 2→1→0, 5→0
const linkTopology = [
  { src: 1, dst: 0 },
  { src: 2, dst: 1 },
  { src: 3, dst: 1 },
  { src: 4, dst: 3 },
  { src: 5, dst: 0 },
];

const client = mqtt.connect(BROKER);

client.on('connect', () => {
  console.log(`MQTT 模拟器已连接: ${BROKER}, ${DRONE_COUNT} 架机, 间隔 ${INTERVAL_MS}ms`);
  console.log(`拓扑: ${linkTopology.map(l => l.src + '→' + l.dst).join(', ')}`);

  let count = 0;
  setInterval(() => {
    // 1) 遥测数据
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

    client.publish('fanet/telemetry', JSON.stringify(records));

    // 2) 链路数据（network_links）
    const links = linkTopology.map(l => {
      // 模拟链路质量波动（50-95 之间随机游走）
      const quality = 50 + Math.floor(Math.random() * 45);
      const active = quality > 20; // 质量太差视为断开
      return {
        srcDroneId: l.src,
        dstDroneId: l.dst,
        linkQuality: quality,
        isActive: active,
        ts: new Date().toISOString(),
      };
    });

    client.publish('fanet/network_links', JSON.stringify(links));

    count++;
    if (count % 10 === 0) {
      const avgBattery = (records.reduce((s, r) => s + r.batteryPct, 0) / records.length).toFixed(1);
      console.log(`[MQTT] #${count} 遥测:${records.length}条 链路:${links.length}条 均电:${avgBattery}%`);
    }
  }, INTERVAL_MS);
});

client.on('error', err => console.error('MQTT 错误:', err.message));
