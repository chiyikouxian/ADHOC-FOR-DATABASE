/**
 * FANET 遥测压测脚本
 * 逐步加压: 10 → 50 → 100 → 200 → 500 架机, 每轮 15 秒
 * 输出 TPS / P50 / P95 / 错误率
 * 用法: node load-test.js
 */

const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const STAGES = [10, 50, 100, 200, 500];
const DURATION = 15000; // 每轮 15s

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function genRecords(n) {
  const recs = [];
  for (let i = 0; i < n; i++) {
    recs.push({
      droneId: (i % 7) + 1,  // 只使用已有的 drone 1-7
      modelId: (i % 3) + 1,
      lat: 31.23 + (Math.random() - 0.5) * 0.05,
      lon: 121.47 + (Math.random() - 0.5) * 0.05,
      alt: 100 + Math.random() * 150,
      batteryPct: Math.random() * 100,
      rssi: -50 - Math.floor(Math.random() * 45),
    });
  }
  return recs;
}

async function runStage(drones) {
  console.log(`\n=== ${drones} 架机 ===`);
  let count = 0, errors = 0;
  const latencies = [];
  const start = Date.now();

  while (Date.now() - start < DURATION) {
    const records = genRecords(drones);
    const t0 = Date.now();
    try {
      const res = await fetch(`${BACKEND_URL}/api/telemetry`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(records),
      });
      if (!res.ok) errors++;
      latencies.push(Date.now() - t0);
      count++;
    } catch (e) { errors++; }
  }

  const elapsed = (Date.now() - start) / 1000;
  const sorted = latencies.sort((a, b) => a - b);
  const p50 = sorted[Math.floor(sorted.length * 0.5)] || 0;
  const p95 = sorted[Math.floor(sorted.length * 0.95)] || 0;
  const p99 = sorted[Math.floor(sorted.length * 0.99)] || 0;

  console.log(`  请求:${count}  TPS:${(count/elapsed).toFixed(1)}  P50:${p50}ms  P95:${p95}ms  P99:${p99}ms  错误:${errors}`);
  return { drones, count, tps: (count/elapsed).toFixed(1), p50, p95, p99, errors };
}

async function main() {
  console.log('FANET 遥测压测 — 逐步加压\n');
  const results = [];
  for (const n of STAGES) {
    results.push(await runStage(n));
    await sleep(5000);
  }
  console.log('\n=== 汇总 ===');
  console.table(results);
  console.log('\n记入报告"性能优化"章节。');
}

main();
