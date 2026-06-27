<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
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
let topologySignature = ''
let topologyRefreshTimer = null

const STATUS_COLORS = {
  idle: '#27a644',
  flying: '#5e6ad2',
  assigned: '#c88b3b',
  offline: '#8a8f98',
  maintenance: '#e04554',
}

function projectNodes(nodes) {
  const validNodes = nodes.filter(node => Number.isFinite(Number(node.lat)) && Number.isFinite(Number(node.lon)))
  if (!validNodes.length) {
    return new Map()
  }

  const lats = validNodes.map(node => Number(node.lat))
  const lons = validNodes.map(node => Number(node.lon))
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const minLon = Math.min(...lons)
  const maxLon = Math.max(...lons)
  const latSpan = Math.max(0.0001, maxLat - minLat)
  const lonSpan = Math.max(0.0001, maxLon - minLon)
  const width = 900
  const height = 520
  const padding = 70
  const projected = new Map()

  for (const node of nodes) {
    const lat = Number(node.lat)
    const lon = Number(node.lon)
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
      continue
    }

    const x = padding + ((lon - minLon) / lonSpan) * (width - padding * 2)
    const y = height - padding - ((lat - minLat) / latSpan) * (height - padding * 2)
    projected.set(String(node.droneNo), { x, y })
  }

  return projected
}

function buildChart() {
  if (!chart) {
    return
  }

  if (!topology.value.nodes.length) {
    chart.clear()
    return
  }

  const previewNodes = topology.value.nodes.map(node => ({
    droneNo: node.droneId,
    name: node.name,
    lat: node.lat,
    lon: node.lon,
    alt: node.alt,
    batteryPct: node.batteryPct,
    rssi: null,
    status: node.status,
    isGround: node.isGround,
  }))

  const projected = projectNodes(previewNodes)

  const nodes = previewNodes.map(node => ({
    id: String(node.droneNo),
    name: node.name,
    x: projected.get(String(node.droneNo))?.x,
    y: projected.get(String(node.droneNo))?.y,
    symbolSize: node.isGround ? 42 : 28,
    itemStyle: {
      color: node.isGround ? '#0b57d0' : (STATUS_COLORS[node.status] || '#8a8f98'),
    },
    value: [
      node.lat,
      node.lon,
      node.alt,
      node.batteryPct,
      node.rssi,
      node.status,
    ],
  }))

  const links = topology.value.edges.map(edge => ({
    source: String(edge.source),
    target: String(edge.target),
    lineStyle: {
      width: Math.max(1, Number(edge.quality || 60) / 25),
      color: edge.active ? '#334155' : '#94a3b8',
      opacity: edge.active ? 0.9 : 0.35,
    },
  }))

  chart.setOption({
    backgroundColor: 'transparent',
    animationDuration: 300,
    animationDurationUpdate: 300,
    tooltip: {
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: { color: '#f8fafc' },
      formatter: params => {
        if (params.dataType === 'node') {
          const [lat, lon, alt, batteryPct, rssi, status] = params.data.value || []
          return `${params.data.name}<br/>状态: ${status}<br/>电量: ${batteryPct}%<br/>RSSI: ${rssi ?? '-'}<br/>位置: ${lat}, ${lon}<br/>高度: ${alt}m`
        }
        return `${params.data.source} -> ${params.data.target}`
      },
    },
    series: [{
      type: 'graph',
      layout: 'none',
      roam: true,
      draggable: true,
      label: {
        show: true,
        color: '#dbe4ee',
        fontSize: 11,
      },
      lineStyle: {
        curveness: 0.12,
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 4,
          color: '#0b57d0',
        },
      },
      data: nodes,
      links,
    }],
  }, {
    notMerge: false,
    lazyUpdate: true,
  })
}

function applyTopology(nextTopology) {
  const normalized = nextTopology || { nodes: [], edges: [] }
  const nextSignature = JSON.stringify(normalized)
  if (nextSignature === topologySignature) {
    return false
  }
  topology.value = normalized
  topologySignature = nextSignature
  return true
}

function mergeTopologyPayload(payload) {
  if (!payload) {
    return false
  }

  const nextEdges = Array.isArray(payload.edges) ? payload.edges : topology.value.edges
  const incomingNodes = Array.isArray(payload.nodes) ? payload.nodes : []
  const existingById = new Map(
    (topology.value.nodes || []).map(node => [Number(node.droneId), node]),
  )

  let requiresFullFetch = false
  for (const incomingNode of incomingNodes) {
    const nodeId = Number(incomingNode.droneId)
    if (!existingById.has(nodeId)) {
      requiresFullFetch = true
      break
    }
  }

  if (requiresFullFetch) {
    return false
  }

  const mergedNodes = (topology.value.nodes || []).map(node => {
    const incoming = incomingNodes.find(item => Number(item.droneId) === Number(node.droneId))
    return incoming ? { ...node, ...incoming } : node
  })

  return applyTopology({
    nodes: mergedNodes,
    edges: nextEdges,
  })
}

async function fetchTopology(showLoading = true) {
  try {
    if (showLoading) {
      loading.value = true
    }
    const { data } = await api.get('/topology')
    if (applyTopology(data)) {
      await nextTick()
      buildChart()
    }
  } catch (error) {
    console.error('加载拓扑失败:', error)
  } finally {
    if (showLoading) {
      loading.value = false
    }
  }
}

function connectWS() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/drones`)

  ws.onmessage = event => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'topology_update') {
        if (topologyRefreshTimer) {
          clearTimeout(topologyRefreshTimer)
        }
        topologyRefreshTimer = window.setTimeout(() => {
          if (mergeTopologyPayload(msg.payload)) {
            buildChart()
          } else {
            fetchTopology(false)
          }
        }, 150)
      }
    } catch (_) {
      // ignore malformed messages
    }
  }

  ws.onclose = () => {
    setTimeout(connectWS, 5000)
  }
}

async function findRoute() {
  try {
    const { data } = await api.get(`/missions/route/${selectedDrone.value}`)
    routes.value = data
  } catch (_) {
    routes.value = []
  }
}

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  chart = echarts.init(chartRef.value)
  await fetchTopology(true)
  connectWS()

  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.topo-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.topo-graph', { opacity: [0, 1], translateX: [-20, 0] }, '-=300')
    .add('.topo-panel', { opacity: [0, 1], translateX: [20, 0] }, '-=400')

  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
  if (topologyRefreshTimer) {
    clearTimeout(topologyRefreshTimer)
  }
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="topo-title text-lg font-semibold" style="opacity:0">拓扑分析</h1>

    <div class="flex gap-4">
      <div class="topo-graph flex-1 bg-surface-1 border border-hairline rounded-lg p-4 relative" style="opacity:0">
        <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-surface-1/80">
          <span class="text-sm text-ink-muted">加载拓扑数据...</span>
        </div>
        <div ref="chartRef" class="h-[400px] w-full"></div>
        <p class="mt-2 text-xs text-ink-subtle">
          {{ topology.nodes.length }} 个节点，{{ topology.edges.length }} 条活动链路
        </p>
      </div>

      <div class="topo-panel w-[300px] space-y-4 rounded-lg border border-hairline bg-surface-1 p-4" style="opacity:0">
        <h2 class="text-sm font-medium text-ink-muted">路径查询（递归 CTE）</h2>
        <div class="space-y-2">
          <label class="text-xs text-ink-subtle">源无人机 ID</label>
          <input
            v-model.number="selectedDrone"
            type="number"
            min="1"
            class="h-9 w-full rounded-md border border-hairline bg-surface-2 px-3 text-sm text-ink focus:border-primary focus:outline-none"
          />
          <button
            @click="findRoute"
            class="h-9 w-full cursor-pointer rounded-md bg-primary text-sm text-white transition-colors hover:bg-primary-hover"
          >
            查询到地面站路径
          </button>
        </div>

        <div v-if="routes.length" class="space-y-2">
          <p class="text-xs text-ink-subtle">找到 {{ routes.length }} 条路径</p>
          <div
            v-for="(route, index) in routes"
            :key="index"
            class="rounded bg-surface-2 p-2 font-mono text-xs text-ink-muted"
          >
            {{ route.path }} ({{ route.hops }} 跳)
          </div>
        </div>

        <p v-else class="text-xs text-ink-subtle">点击查询按钮后，这里会展示可达地面站的链路路径。</p>
      </div>
    </div>
  </div>
</template>
