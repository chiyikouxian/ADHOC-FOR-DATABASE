/**
 * FANET 并发对照实验 — 证明 SELECT ... FOR UPDATE 行锁有效
 * 模拟 N 个并发请求同时抢占同一架空闲无人机
 * 预期结果：只有 1 个成功(200), N-1 个冲突(409)
 * 
 * 用法: 
 *   node concurrency-test.js [并发数] [droneId]
 *   node concurrency-test.js 20 1     # 20 并发抢 drone-1
 *   node concurrency-test.js 10 5     # 10 并发抢 drone-5
 * 
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
  console.log(`\n${'='.repeat(60)}`);
  console.log(`  并发对照实验 — FOR UPDATE 行锁验证`);
  console.log(`${'='.repeat(60)}`);
  console.log(`  目标无人机: drone_id=${DRONE_ID}`);
  console.log(`  并发请求数: ${CONCURRENCY}`);
  console.log(`  预期结果:   仅 1 个 200 (成功), 其余 ${CONCURRENCY - 1} 个 409 (冲突)`);
  console.log(`${'='.repeat(60)}\n`);

  // 1) 先重置无人机状态为 idle
  console.log('[准备] 重置无人机状态为 idle...');
  const resetRes = await fetch(`${BACKEND_URL}/api/missions/reset-drone/${DRONE_ID}`, { method: 'POST' });
  const resetData = await resetRes.json();
  console.log(`[准备] 重置结果: ${JSON.stringify(resetData)}`);

  await new Promise(r => setTimeout(r, 500));

  // 2) 同时发起 N 个抢占请求
  console.log(`\n[执行] 发起 ${CONCURRENCY} 个并发抢占请求...\n`);
  const promises = Array.from({ length: CONCURRENCY }, (_, i) => tryAssign(i));
  const results = await Promise.all(promises);

  // 3) 统计
  let success = 0, conflict = 0, other = 0;
  const successTimes = [], conflictTimes = [];

  for (const r of results) {
    const tag = r.status === 200 ? '✅ SUCCESS' : r.status === 409 ? '❌ CONFLICT' : '⚠️ ERROR';
    if (r.status === 200) { success++; successTimes.push(r.ms); }
    else if (r.status === 409) { conflict++; conflictTimes.push(r.ms); }
    else other++;
    console.log(`  [请求#${String(r.i).padStart(2)}] ${tag} (${r.ms}ms) ${JSON.stringify(r.data)}`);
  }

  // 4) 结论
  console.log(`\n${'='.repeat(60)}`);
  console.log(`  实验结果`);
  console.log(`${'='.repeat(60)}`);
  console.log(`  成功: ${success}  冲突: ${conflict}  错误: ${other}`);
  
  if (success === 1 && conflict === CONCURRENCY - 1) {
    console.log(`\n  ✅ 行锁有效! 仅 1 个请求成功占用无人机。`);
    console.log(`     PostgreSQL SELECT ... FOR UPDATE 正确序列化了并发请求。`);
    console.log(`     成功请求耗时: ${successTimes.join('ms, ')}ms`);
    console.log(`     冲突请求耗时范围: ${Math.min(...conflictTimes)}-${Math.max(...conflictTimes)}ms`);
  } else if (success === 0) {
    console.log(`\n  ⚠️ 所有请求均失败 - 无人机可能不是 idle 状态，请先重置。`);
    console.log(`     手动重置: curl -X POST ${BACKEND_URL}/api/missions/reset-drone/${DRONE_ID}`);
  } else if (success > 1) {
    console.log(`\n  ❌ 行锁失效! ${success} 个请求同时成功（不应发生）`);
    console.log(`     请检查 MissionMapper.xml 中 lockDroneForAssign 是否包含 FOR UPDATE。`);
  }
  console.log(`${'='.repeat(60)}\n`);
}

main();
