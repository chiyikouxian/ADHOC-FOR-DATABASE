<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import api from '../api'

const drones = ref([])
const stats = ref({ total: 0, flying: 0, idle: 0, offline: 0 })
let timer = null

const statusColor = {
  idle: 'bg-ink-subtle',
  assigned: 'bg-primary',
  flying: 'bg-success',
  offline: 'bg-danger',
  maintenance: 'bg-warning',
}

async function fetchDrones() {
  try {
    const { data } = await api.get('/drones')
    drones.value = data
    stats.value = {
      total: data.length,
      flying: data.filter(d => d.status === 'flying').length,
      idle: data.filter(d => d.status === 'idle').length,
      offline: data.filter(d => d.status === 'offline').length,
    }
  } catch (e) { /* silent */ }
}

onMounted(() => {
  fetchDrones()
  timer = setInterval(fetchDrones, 3000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="text-lg font-semibold">实时态势大屏</h1>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-4 gap-4">
      <div class="bg-surface-1 border border-hairline rounded-lg p-4">
        <p class="text-xs text-ink-subtle mb-1">总机数</p>
        <p class="text-2xl font-semibold font-mono">{{ stats.total }}</p>
      </div>
      <div class="bg-surface-1 border border-hairline rounded-lg p-4">
        <p class="text-xs text-ink-subtle mb-1">飞行中</p>
        <p class="text-2xl font-semibold font-mono text-success">{{ stats.flying }}</p>
      </div>
      <div class="bg-surface-1 border border-hairline rounded-lg p-4">
        <p class="text-xs text-ink-subtle mb-1">空闲</p>
        <p class="text-2xl font-semibold font-mono text-ink-muted">{{ stats.idle }}</p>
      </div>
      <div class="bg-surface-1 border border-hairline rounded-lg p-4">
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
             class="flex items-center justify-between px-4 py-3">
          <div class="flex items-center gap-3">
            <span :class="[statusColor[d.status] || 'bg-ink-subtle', 'w-2 h-2 rounded-full']"></span>
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
