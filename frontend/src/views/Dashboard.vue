<script setup>
import { ref, onMounted, nextTick } from 'vue'
import api from '../api'
import { animate, stagger, createTimeline } from '../composables/useAnime'
import { useWebSocket } from '../composables/useWebSocket'

const drones = ref([])
const stats = ref({ total: 0, flying: 0, idle: 0, offline: 0 })
const { data: wsMsg, connected } = useWebSocket('/ws/drones')

const statusColor = {
  idle: 'bg-ink-subtle',
  assigned: 'bg-primary',
  flying: 'bg-success',
  offline: 'bg-danger',
  maintenance: 'bg-warning',
}

function recalc() {
  stats.value = {
    total: drones.value.length,
    flying: drones.value.filter(d => d.status === 'flying').length,
    idle: drones.value.filter(d => d.status === 'idle').length,
    offline: drones.value.filter(d => d.status === 'offline').length,
  }
}

async function fetchDrones() {
  try {
    const { data } = await api.get('/drones')
    drones.value = data
    recalc()
  } catch (e) { /* silent */ }
}

// WebSocket 推送到达时更新对应无人机
function handleWsUpdate(payload) {
  const d = drones.value.find(d => d.droneId === payload.droneId)
  if (d) {
    d.batteryPct = payload.batteryPct
    d.rssi = payload.rssi
    d.alt = payload.alt
    d.lat = payload.lat
    d.lon = payload.lon
    recalc()
  }
}

onMounted(async () => {
  await fetchDrones()
  await nextTick()
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.dash-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.stat-card', { opacity: [0, 1], scale: [0.92, 1], delay: stagger(80) }, '-=300')
    .add('.drone-row', { opacity: [0, 1], translateX: [-20, 0], delay: stagger(50) }, '-=200')
  // 注入动画后确保元素可见
  setTimeout(() => {
    document.querySelectorAll('.drone-row,.stat-card,.dash-title').forEach(el => {
      if (el.style.opacity === '0') el.style.opacity = '1'
    })
  }, 3000)
})

import { watch } from 'vue'
watch(wsMsg, (msg) => {
  if (msg && msg.type === 'drone_update') {
    handleWsUpdate(msg.payload)
  }
})
</script>

<template>
  <div class="p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="dash-title text-lg font-semibold" style="opacity:0">实时态势大屏</h1>
      <span :class="['text-xs px-2 py-0.5 rounded', connected ? 'text-success bg-success/10' : 'text-warning bg-warning/10']">
        {{ connected ? '实时连接' : '连接中...' }}
      </span>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-4 gap-4">
      <div class="stat-card bg-surface-1 border border-hairline rounded-lg p-4" style="opacity:0">
        <p class="text-xs text-ink-subtle mb-1">总机数</p>
        <p class="text-2xl font-semibold font-mono">{{ stats.total }}</p>
      </div>
      <div class="stat-card bg-surface-1 border border-hairline rounded-lg p-4" style="opacity:0">
        <p class="text-xs text-ink-subtle mb-1">飞行中</p>
        <p class="text-2xl font-semibold font-mono text-success">{{ stats.flying }}</p>
      </div>
      <div class="stat-card bg-surface-1 border border-hairline rounded-lg p-4" style="opacity:0">
        <p class="text-xs text-ink-subtle mb-1">空闲</p>
        <p class="text-2xl font-semibold font-mono text-ink-muted">{{ stats.idle }}</p>
      </div>
      <div class="stat-card bg-surface-1 border border-hairline rounded-lg p-4" style="opacity:0">
        <p class="text-xs text-ink-subtle mb-1">离线</p>
        <p class="text-2xl font-semibold font-mono text-danger">{{ stats.offline }}</p>
      </div>
    </div>

    <!-- 无人机列表 -->
    <div class="bg-surface-1 border border-hairline rounded-lg">
      <div class="px-4 py-3 border-b border-hairline">
        <h2 class="text-sm font-medium text-ink-muted">无人机状态</h2>
      </div>
      <div class="divide-y divide-hairline">
        <div v-for="d in drones" :key="d.droneId"
             class="drone-row flex items-center justify-between px-4 py-3" style="opacity:0">
          <div class="flex items-center gap-3">
            <span :class="[statusColor[d.status] || 'bg-ink-subtle', 'w-2 h-2 rounded-full', d.status === 'flying' ? 'animate-pulse' : '']"></span>
            <span class="text-sm">{{ d.serialNo || 'Drone-' + d.droneId }}</span>
          </div>
          <div class="flex items-center gap-6 text-xs text-ink-subtle">
            <span>电量 <span class="text-ink font-mono">{{ d.batteryPct?.toFixed(0) ?? '--' }}%</span></span>
            <span>信号 <span class="text-ink font-mono">{{ d.rssi ?? '--' }} dBm</span></span>
            <span>高度 <span class="text-ink font-mono">{{ d.alt?.toFixed(0) ?? '--' }} m</span></span>
            <span class="capitalize px-2 py-0.5 rounded text-xs"
                  :class="statusColor[d.status]?.replace('bg-', 'text-')">
              {{ d.status }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
