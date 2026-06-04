const mqtt = require('mqtt');

const BROKER = process.env.MQTT_HOST || 'mqtt://127.0.0.1:1883';
const DRONE_COUNT = parseInt(process.argv[2] || '3', 10);
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

const client = mqtt.connect(BROKER);

client.on('connect', () => {
  console.log(`MQTT 模拟器已连接: ${BROKER}, ${DRONE_COUNT} 架机, 间隔 ${INTERVAL_MS}ms`);

  let count = 0;
  setInterval(() => {
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
    count++;
    if (count % 10 === 0) console.log(`[MQTT] #${count} 已发送 ${records.length} 条遥测`);
  }, INTERVAL_MS);
});

client.on('error', err => console.error('MQTT 错误:', err.message));
