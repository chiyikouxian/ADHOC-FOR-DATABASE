<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const chartRef = ref(null)
const routes = ref([])
const selectedDrone = ref(4)
let chart = null

const nodes = [
  { name: '地面站', x: 300, y: 400, symbolSize: 40, itemStyle: { color: '#5e6ad2' } },
  { name: 'Drone-1', x: 200, y: 300, symbolSize: 30, itemStyle: { color: '#27a644' } },
  { name: 'Drone-2', x: 400, y: 280, symbolSize: 30, itemStyle: { color: '#27a644' } },
  { name: 'Drone-3', x: 250, y: 180, symbolSize: 30, itemStyle: { color: '#27a644' } },
  { name: 'Drone-4', x: 150, y: 100, symbolSize: 30, itemStyle: { color: '#27a644' } },
  { name: 'Drone-5', x: 400, y: 120, symbolSize: 30, itemStyle: { color: '#8a8f98' } },
]

const links = [
  { source: 'Drone-1', target: '地面站' },
  { source: 'Drone-2', target: '地面站' },
  { source: 'Drone-3', target: 'Drone-1' },
  { source: 'Drone-3', target: 'Drone-2' },
  { source: 'Drone-4', target: 'Drone-3' },
  { source: 'Drone-5', target: 'Drone-2' },
]

onMounted(() => {
  chart = echarts.init(chartRef.value)
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { backgroundColor: '#18191a', borderColor: '#23252a', textStyle: { color: '#f7f8f8' } },
    series: [{
      type: 'graph',
      layout: 'none',
      roam: true,
      label: { show: true, color: '#d0d6e0', fontSize: 11 },
      lineStyle: { color: '#34343a', width: 2, curveness: 0.1 },
      data: nodes,
      links: links,
      emphasis: { lineStyle: { width: 4, color: '#5e6ad2' } }
    }]
  })
})

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
    <h1 class="text-lg font-semibold">拓扑分析</h1>

    <div class="flex gap-4">
      <div class="flex-1 bg-surface-1 border border-hairline rounded-lg p-4">
        <div ref="chartRef" class="w-full h-[400px]"></div>
      </div>
      <div class="w-[300px] bg-surface-1 border border-hairline rounded-lg p-4 space-y-4">
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
