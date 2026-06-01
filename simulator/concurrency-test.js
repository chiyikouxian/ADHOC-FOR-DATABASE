/**
 * FANET 并发对照实验 — 证明 SELECT ... FOR UPDATE 行锁有效
 * 模拟 N 个并发请求同时抢占同一架空闲无人机
 * 预期结果：只有 1 个成功(409 冲突 N-1 个)
 * 用法: node concurrency-test.js [并发数] [droneId]
 * 示例: node concurrency-test.js 10 1
 */

const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const CONCURRENCY = parseInt(process.argv[2] || '10', 10);
const DRONE_ID = parseInt(process.argv[3] || '1', 10);
const MISSION_ID = 4;  // 种子数据里的 draft 任务

async function tryAssign(i) {
  const start = Date.now();
  try {
    const res = await fetch(
      `${BACKEND_URL}/api/missions/${MISSION_ID}/assign/${DRONE_ID}`,
      { method: 'POST' }
    );
    const data = await res.json();
    const ms = Date.now() - start;
    return { i, status: res.status, data, ms };
  } catch (err) {
    return { i, status: 0, data: { error: err.message }, ms: Date.now() - start };
  }
}

async function main() {
  console.log(`\n=== 并发对照实验 ===`);
  console.log(`目标: drone_id=${DRONE_ID}, 并发数=${CONCURRENCY}`);
  console.log(`预期: 仅 1 个 200(成功), 其余 409(冲突)\n`);

  // 先重置无人机状态为 idle
  await fetch(`${BACKEND_URL}/api/missions/reset-drone/${DRONE_ID}`, { method: 'POST' })
    .catch(() => {});

  // 同时发起 N 个请求
  const promises = Array.from({ length: CONCURRENCY }, (_, i) => tryAssign(i));
  const results = await Promise.all(promises);

  // 统计
  let success = 0, conflict = 0, other = 0;
  for (const r of results) {
    const tag = r.status === 200 ? 'SUCCESS' : r.status === 409 ? 'CONFLICT' : 'ERROR';
    if (r.status === 200) success++;
    else if (r.status === 409) conflict++;
    else other++;
    console.log(`  [${r.i}] ${tag} (${r.ms}ms) ${JSON.stringify(r.data)}`);
  }

  console.log(`\n=== 结果 ===`);
  console.log(`  成功: ${success}  冲突: ${conflict}  错误: ${other}`);
  if (success === 1 && conflict === CONCURRENCY - 1) {
    console.log(`  ✓ 行锁有效! 仅 1 个请求成功占用无人机`);
  } else if (success === 0) {
    console.log(`  ! 无人机可能不是 idle 状态,请先重置`);
  } else if (success > 1) {
    console.log(`  ✗ 行锁失效! 多个请求同时成功(不应发生)`);
  }
}

main();
