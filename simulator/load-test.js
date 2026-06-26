/**
 * FANET telemetry load test
 *
 * Modes:
 *   node load-test.js
 *   node load-test.js --mqtt
 *   node load-test.js --compare
 */

const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:18080';
const MQTT_HOST = process.env.MQTT_HOST || 'mqtt://127.0.0.1:1883';

const args = process.argv.slice(2);
const MODE_MQTT = args.includes('--mqtt');
const MODE_COMPARE = args.includes('--compare');

const STAGES = [10, 50, 100, 200, 500];
const DURATION = MODE_COMPARE ? 10000 : 30000;

let mqtt = null;
if (MODE_MQTT) {
  mqtt = require('mqtt');
  console.log('[Mode] MQTT direct -> ' + MQTT_HOST);
} else if (MODE_COMPARE) {
  console.log('[Mode] Compare distinct write paths: PostgreSQL-only vs TDengine-only');
} else {
  console.log('[Mode] HTTP /api/telemetry -> ' + BACKEND_URL);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function genRecords(n) {
  const recs = [];
  for (let i = 0; i < n; i++) {
    recs.push({
      droneId: (i % 7) + 1,
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

function calcPercentile(sorted, pct) {
  if (sorted.length === 0) return 0;
  return sorted[Math.floor(sorted.length * pct)] || sorted[sorted.length - 1];
}

async function runStageHttp(drones) {
  console.log(`\n=== ${drones} drones (HTTP) ===`);
  let count = 0;
  let errors = 0;
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
    } catch (e) {
      errors++;
    }
  }

  return buildResult(drones, count, errors, latencies, start);
}

async function runStageMqtt(drones) {
  return new Promise((resolve) => {
    const client = mqtt.connect(MQTT_HOST);
    let count = 0;
    let errors = 0;
    const latencies = [];
    const start = Date.now();

    client.on('connect', () => {
      const interval = setInterval(() => {
        if (Date.now() - start >= DURATION) {
          clearInterval(interval);
          client.end();
          resolve(buildResult(drones, count, errors, latencies, start));
          return;
        }

        const records = genRecords(drones);
        const t0 = Date.now();
        client.publish('fanet/telemetry', JSON.stringify(records), (err) => {
          if (err) errors++;
          latencies.push(Date.now() - t0);
          count++;
        });
      }, 1000 / Math.max(1, drones / 5));
    });

    client.on('error', () => client.end());
  });
}

async function runComparePath(drones, path, label) {
  let count = 0;
  let errors = 0;
  const latencies = [];
  const start = Date.now();

  console.log(`  -> ${label}`);
  while (Date.now() - start < DURATION) {
    const records = genRecords(drones);
    const t0 = Date.now();
    try {
      const res = await fetch(`${BACKEND_URL}${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(records),
      });
      if (!res.ok) errors++;
      latencies.push(Date.now() - t0);
      count++;
    } catch (e) {
      errors++;
    }
  }

  return buildResult(drones, count, errors, latencies, start);
}

async function runCompareStage(drones) {
  console.log(`\n=== ${drones} drones compare ===`);

  const pgResult = await runComparePath(drones, '/api/bench/pg-insert', 'PG only path');
  await sleep(3000);
  const tdResult = await runComparePath(drones, '/api/bench/td-insert', 'TDengine only path');

  console.log(`  PG: TPS=${pgResult.tps} P95=${pgResult.p95} err=${pgResult.errors}`);
  console.log(`  TD: TPS=${tdResult.tps} P95=${tdResult.p95} err=${tdResult.errors}`);
  const ratio = (Number(tdResult.tps) / Math.max(1, Number(pgResult.tps)) * 100).toFixed(0);
  console.log(`  TD/PG = ${ratio}%`);

  return { pg: pgResult, td: tdResult, drones, tdPgRatio: ratio + '%' };
}

function buildResult(drones, count, errors, latencies, start) {
  const elapsed = (Date.now() - start) / 1000;
  const sorted = [...latencies].sort((a, b) => a - b);
  return {
    drones,
    requests: count,
    elapsedSec: elapsed.toFixed(1),
    tps: (count / elapsed).toFixed(1),
    p50: calcPercentile(sorted, 0.50) + 'ms',
    p95: calcPercentile(sorted, 0.95) + 'ms',
    p99: calcPercentile(sorted, 0.99) + 'ms',
    errors,
    errorRate: count > 0 ? (errors / count * 100).toFixed(1) + '%' : '0%',
  };
}

async function main() {
  console.log('FANET load test\n');
  console.log(`Stages: ${STAGES.join(' -> ')} drones, ${DURATION / 1000}s each\n`);

  const results = [];
  for (const n of STAGES) {
    let result;
    if (MODE_COMPARE) {
      result = await runCompareStage(n);
    } else if (MODE_MQTT) {
      result = await runStageMqtt(n);
    } else {
      result = await runStageHttp(n);
    }
    results.push(result);
    await sleep(5000);
  }

  console.log('\n' + '='.repeat(70));
  console.log('Load test summary');
  console.log('='.repeat(70));

  if (MODE_COMPARE) {
    console.log('\n--- PostgreSQL only path ---');
    console.table(results.map(r => r.pg));
    console.log('\n--- TDengine only path ---');
    console.table(results.map(r => r.td));
    console.log('\n--- Compare summary ---');
    console.table(results.map(r => ({
      drones: r.drones,
      PG_TPS: r.pg.tps,
      TD_TPS: r.td.tps,
      'TD/PG': r.tdPgRatio,
      PG_P95: r.pg.p95,
      TD_P95: r.td.p95,
    })));
    console.log('\nConclusion: compare mode now measures two distinct write paths instead of hitting the same API twice.');
  } else {
    console.table(results);
    console.log('\nObserve TPS drops and P95 spikes to identify throughput bottlenecks.');
  }
}

main().catch(e => {
  console.error(e);
  process.exit(1);
});
