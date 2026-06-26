<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../api'
import { createTimeline } from '../composables/useAnime'

const chartRef = ref(null)
const routes = ref([])
const selectedDrone = ref(4)
const topology = ref({ nodes: [], edges: [] })
const loading = ref(true)
let chart = null
let ws = null

// 颜色映射
const STATUS_COLORS = {
  idle: '#27a644',
  flying: '#5e6ad2',
  assigned: '#c88b3b',
  offline: '#8a8f98',
  maintenance: '#e04554',
}

// 构建 ECharts 图
function buildChart() {
  if (!chart || !topology.value.nodes.length) return

  const nodes = topology.value.nodes.map(n => ({
    name: n.name,
    symbolSize: n.isGround ? 40 : 28,
    itemStyle: { color: n.isGround ? '#5e6ad2' : (STATUS_COLORS[n.status] || '#8a8f98') },
    droneId: n.droneId,
  }))

  const links = topology.value.edges.map(e => {
    const srcNode = topology.value.nodes.find(n => n.droneId === e.source)
    const dstNode = topology.value.nodes.find(n => n.droneId === e.target)
    return {
      source: srcNode?.name || `Drone-${e.source}`,
      target: dstNode?.name || `Drone-${e.target}`,
      lineStyle: {
        width: Math.max(1, e.quality / 30),
        color: e.active ? '#34343a' : '#23252a',
        opacity: e.active ? 0.8 : 0.3,
      }
    }
  })

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      backgroundColor: '#18191a',
      borderColor: '#23252a',
      textStyle: { color: '#f7f8f8' },
      formatter: (p) => {
        if (p.dataType === 'node') {
          const n = topology.value.nodes.find(x => x.name === p.name)
          return n ? `${n.name}<br/>状态: ${n.status}<br/>电量: ${n.batteryPct?.toFixed(1)}%` : p.name
        }
        return `${p.data.source} → ${p.data.target}`
      }
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      force: { repulsion: 200, edgeLength: [120, 300], gravity: 0.1 },
      label: { show: true, color: '#d0d6e0', fontSize: 11 },
      lineStyle: { curveness: 0.15 },
      data: nodes,
      links: links,
      emphasis: { lineStyle: { width: 4, color: '#5e6ad2' } }
    }]
  }, true) // notMerge=false 用于更新数据
}

// 获取拓扑数据
async function fetchTopology() {
  try {
    loading.value = true
    const { data } = await api.get('/topology')
    topology.value = data
    await nextTick()
    buildChart()
  } catch (e) {
    console.error('拓扑加载失败:', e)
  } finally {
    loading.value = false
  }
}

// WebSocket 实时拓扑更新
function connectWS() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/drones`)

  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'topology_update') {
        // 合并 WS 推送的拓扑数据（增量更新边，保留节点信息）
        if (msg.payload) {
          if (msg.payload.nodes) {
            // 合并节点（WS 推送的节点信息较少，只更新存在变化的字段）
            for (const newNode of msg.payload.nodes) {
              const existing = topology.value.nodes.find(n => n.droneId === newNode.droneId)
              if (existing) {
                Object.assign(existing, newNode)
              } else {
                topology.value.nodes.push(newNode)
              }
            }
          }
          if (msg.payload.edges) {
            topology.value.edges = msg.payload.edges
          }
          nextTick(() => buildChart())
        }
      }
    } catch (_) { /* ignore malformed */ }
  }

  ws.onclose = () => {
    // 5 秒后重连
    setTimeout(connectWS, 5000)
  }
}

onMounted(async () => {
  chart = echarts.init(chartRef.value)
  await fetchTopology()
  connectWS()

  // 入场动画
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.topo-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.topo-graph', { opacity: [0, 1], translateX: [-20, 0] }, '-=300')
    .add('.topo-panel', { opacity: [0, 1], translateX: [20, 0] }, '-=400')

  // 每 30 秒刷新拓扑
  window._topoInterval = setInterval(fetchTopology, 30000)
})

onUnmounted(() => {
  if (ws) ws.close()
  if (window._topoInterval) clearInterval(window._topoInterval)
  if (chart) chart.dispose()
})

// 窗口大小变化时重绘
window.addEventListener('resize', () => chart?.resize())

async function findRoute() {
  try {
    const { data } = await api.get(`/missions/route/${selectedDrone.value}`)
    routes.value = data
  } catch (e) {
    routes.value = []
  }
}
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="topo-title text-lg font-semibold" style="opacity:0">拓扑分析</h1>

    <div class="flex gap-4">
      <div class="topo-graph flex-1 bg-surface-1 border border-hairline rounded-lg p-4 relative" style="opacity:0">
        <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-surface-1/80 z-10 rounded-lg">
          <span class="text-sm text-ink-muted">加载拓扑数据...</span>
        </div>
        <div ref="chartRef" class="w-full h-[400px]"></div>
        <p class="text-xs text-ink-subtle mt-2">
          {{ topology.nodes.length }} 个节点，{{ topology.edges.length }} 条活跃链路
        </p>
      </div>
      <div class="topo-panel w-[300px] bg-surface-1 border border-hairline rounded-lg p-4 space-y-4" style="opacity:0">
        <h2 class="text-sm font-medium text-ink-muted">路径查询 (递归 CTE)</h2>
        <div class="space-y-2">
          <label class="text-xs text-ink-subtle">源无人机 ID</label>
          <input v-model.number="selectedDrone" type="number" min="1" max="7"
            class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm text-ink focus:outline-none focus:border-primary" />
          <button @click="findRoute"
            class="w-full h-9 bg-primary hover:bg-primary-hover text-white text-sm rounded-md cursor-pointer transition-colors">
            查找到地面站路径
          </button>
        </div>
        <div v-if="routes.length" class="space-y-2">
          <p class="text-xs text-ink-subtle">找到 {{ routes.length }} 条路径:</p>
          <div v-for="(r, i) in routes" :key="i"
               class="p-2 bg-surface-2 rounded text-xs font-mono text-ink-muted">
            {{ r.path }} ({{ r.hops }} 跳)
          </div>
        </div>
        <p v-else class="text-xs text-ink-subtle">点击查找按钮查询路径</p>
      </div>
    </div>
  </div>
</template>
