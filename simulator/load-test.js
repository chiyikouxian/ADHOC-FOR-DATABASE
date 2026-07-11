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
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'password';

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

async function getAdminToken() {
  let response;
  try {
    response = await fetch(`${BACKEND_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ADMIN_USERNAME, password: ADMIN_PASSWORD }),
    });
  } catch (error) {
    throw new Error(`Unable to reach login endpoint: ${error.message}`);
  }

  const body = await response.text();
  let payload;
  try {
    payload = body ? JSON.parse(body) : {};
  } catch {
    payload = {};
  }

  if (!response.ok || !payload.token) {
    throw new Error(`Administrator login failed (${response.status}): ${body || '<empty response>'}`);
  }
  if (payload.role !== 'admin') {
    throw new Error(`Administrator login returned unexpected role: ${payload.role || '<missing>'}`);
  }
  return payload.token;
}

function failureFromResponse(response, body) {
  return {
    type: 'http',
    status: response.status,
    statusText: response.statusText,
    body: body || '<empty response>',
  };
}

function failureFromError(error) {
  return { type: 'network', message: error.message };
}

function logFirstFailure(label, result) {
  console.log(`  ${label} first failure: ${result.firstFailure === '-' ? 'none' : result.firstFailure}`);
}

async function postRecords(path, records, token) {
  const response = await fetch(`${BACKEND_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(records),
  });
  const body = response.ok ? '' : await response.text();
  return { response, body };
}

async function runStageHttp(drones, token) {
  console.log(`\n=== ${drones} drones (HTTP) ===`);
  let requests = 0;
  let successes = 0;
  let firstFailure = null;
  const latencies = [];
  const start = Date.now();

  while (Date.now() - start < DURATION) {
    const records = genRecords(drones);
    const t0 = Date.now();
    requests++;
    try {
      const { response, body } = await postRecords('/api/telemetry', records, token);
      if (response.ok) {
        successes++;
        latencies.push(Date.now() - t0);
      } else if (!firstFailure) {
        firstFailure = failureFromResponse(response, body);
      }
    } catch (error) {
      if (!firstFailure) firstFailure = failureFromError(error);
    }
  }

  return buildResult(drones, requests, successes, latencies, start, firstFailure);
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
          resolve(buildResult(drones, count, count - errors, latencies, start, null));
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

async function runComparePath(drones, path, label, token) {
  let requests = 0;
  let successes = 0;
  let firstFailure = null;
  const latencies = [];
  const start = Date.now();

  console.log(`  -> ${label}`);
  while (Date.now() - start < DURATION) {
    const records = genRecords(drones);
    const t0 = Date.now();
    requests++;
    try {
      const { response, body } = await postRecords(path, records, token);
      if (response.ok) {
        successes++;
        latencies.push(Date.now() - t0);
      } else if (!firstFailure) {
        firstFailure = failureFromResponse(response, body);
      }
    } catch (error) {
      if (!firstFailure) firstFailure = failureFromError(error);
    }
  }

  return buildResult(drones, requests, successes, latencies, start, firstFailure);
}

async function runCompareStage(drones, token) {
  console.log(`\n=== ${drones} drones compare ===`);

  const pgResult = await runComparePath(drones, '/api/bench/pg-insert', 'PG only path', token);
  await sleep(3000);
  const tdResult = await runComparePath(drones, '/api/bench/td-insert', 'TDengine only path', token);

  console.log(`  PG: TPS=${pgResult.tps} P95=${pgResult.p95} errors=${pgResult.errors}`);
  console.log(`  TD: TPS=${tdResult.tps} P95=${tdResult.p95} errors=${tdResult.errors}`);
  logFirstFailure('PG', pgResult);
  logFirstFailure('TD', tdResult);
  const ratio = (Number(tdResult.tps) / Math.max(1, Number(pgResult.tps)) * 100).toFixed(0);
  console.log(`  TD/PG = ${ratio}%`);

  return { pg: pgResult, td: tdResult, drones, tdPgRatio: ratio + '%' };
}

function buildResult(drones, requests, successes, latencies, start, firstFailure) {
  const elapsed = (Date.now() - start) / 1000;
  const sorted = [...latencies].sort((a, b) => a - b);
  const errors = Math.max(0, requests - successes);
  const errorRate = requests === 0 ? 0 : Math.min(100, Math.max(0, errors / requests * 100));
  return {
    drones,
    requests,
    successes,
    elapsedSec: elapsed.toFixed(1),
    tps: (successes / elapsed).toFixed(1),
    p50: calcPercentile(sorted, 0.50) + 'ms',
    p95: calcPercentile(sorted, 0.95) + 'ms',
    p99: calcPercentile(sorted, 0.99) + 'ms',
    errors,
    errorRate: errorRate.toFixed(1) + '%',
    firstFailure: firstFailure ? JSON.stringify(firstFailure) : '-',
  };
}

async function main() {
  console.log('FANET load test\n');
  console.log(`Stages: ${STAGES.join(' -> ')} drones, ${DURATION / 1000}s each\n`);

  let token = null;
  if (!MODE_MQTT) {
    token = await getAdminToken();
    console.log(`[Auth] Logged in as ${ADMIN_USERNAME} with administrator JWT`);
  }

  const results = [];
  for (const n of STAGES) {
    let result;
    if (MODE_COMPARE) {
      result = await runCompareStage(n, token);
    } else if (MODE_MQTT) {
      result = await runStageMqtt(n);
    } else {
      result = await runStageHttp(n, token);
      logFirstFailure('HTTP', result);
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
      PG_errors: r.pg.errors,
      TD_errors: r.td.errors,
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
