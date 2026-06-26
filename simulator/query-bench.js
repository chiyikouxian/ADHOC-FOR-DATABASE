/**
 * FANET 查询性能验证脚本
 * 调用 ExplainController 端点获取 EXPLAIN ANALYZE 结果
 * 验证：行锁、递归CTE、窗口函数、视图查询 的执行计划与耗时
 * 
 * 用法: node query-bench.js
 */

const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8080';

const ENDPOINTS = [
  { name: '任务调度的行锁 (FOR UPDATE)', path: '/api/explain/schedule' },
  { name: '递归拓扑 (WITH RECURSIVE)', path: '/api/explain/recursive' },
  { name: '任务排名 (RANK OVER)', path: '/api/explain/ranking' },
  { name: '视图扫描 (v_drone_latest)', path: '/api/explain/drones' },
];

async function main() {
  console.log('\n' + '='.repeat(70));
  console.log('  FANET 核心 SQL 查询性能验证 (EXPLAIN ANALYZE)');
  console.log('='.repeat(70) + '\n');

  const results = [];

  for (const ep of ENDPOINTS) {
    try {
      const start = Date.now();
      const res = await fetch(`${BACKEND_URL}${ep.path}`);
      const data = await res.json();
      const ms = Date.now() - start;

      const plan = Array.isArray(data) ? data.map(r => r['QUERY PLAN'] || r['query_plan'] || JSON.stringify(r)).join('\n') : (data.plan || JSON.stringify(data));

      // 提取执行时间
      let execTime = 'N/A';
      if (typeof plan === 'string') {
        const match = plan.match(/actual time[= ]*[\d.]+[.][.]([\d.]+)/);
        if (match) execTime = match[1] + 'ms';
        const costMatch = plan.match(/cost[= ]*([\d.]+[.][.][\d.]+)/);
        if (costMatch) execTime += ` (cost=${costMatch[1]})`;
      }

      results.push({ name: ep.name, status: res.status, execTime, latencyMs: ms });
      console.log(`✅ ${ep.name}`);
      console.log(`   耗时: ${execTime}, HTTP 延迟: ${ms}ms`);
      console.log(`   计划概要: ${typeof plan === 'string' ? plan.substring(0, 120) + '...' : JSON.stringify(plan).substring(0, 120)}`);
      console.log();
    } catch (e) {
      results.push({ name: ep.name, status: 0, execTime: 'ERROR', latencyMs: -1 });
      console.log(`❌ ${ep.name}: ${e.message}\n`);
    }
  }

  console.log('='.repeat(70));
  console.log('  汇总');
  console.log('='.repeat(70));
  console.table(results.map(r => ({
    查询: r.name,
    状态: r.status === 200 ? 'OK' : 'FAIL',
    执行时间: r.execTime,
    HTTP延迟: r.latencyMs + 'ms',
  })));

  console.log('\n💡 性能优化建议:');
  console.log('  1. 确认所有 JOIN/WHERE 列已建索引 (见 schema_pg.sql)');
  console.log('  2. 递归 CTE 确保 network_links_snapshot 数据不过期');
  console.log('  3. 窗口函数注意数据量，必要时加 LIMIT');
  console.log('  4. TDengine 侧用 INTERVAL 降采样减少返回行数');
  console.log('\n记入报告"性能优化"章节。\n');
}

main();
