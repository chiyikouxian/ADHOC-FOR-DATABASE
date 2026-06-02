<script setup>
import { ref, onMounted, nextTick } from 'vue'
import api from '../api'
import { animate, stagger, createTimeline } from '../composables/useAnime'

const alerts = ref([])
const loading = ref(true)

const severityStyle = {
  critical: 'text-danger bg-danger/10',
  warning: 'text-warning bg-warning/10',
  info: 'text-info bg-info/10',
}

onMounted(async () => {
  try {
    const { data } = await api.get('/drones')
    alerts.value = data
      .filter(d => d.batteryPct && d.batteryPct < 30)
      .map(d => ({
        id: d.droneId,
        severity: d.batteryPct < 15 ? 'critical' : 'warning',
        message: `Drone-${d.droneId} 电量低: ${d.batteryPct?.toFixed(1)}%`,
        time: new Date().toLocaleTimeString()
      }))
    if (alerts.value.length === 0) {
      alerts.value = [
        { id: 0, severity: 'info', message: '当前无告警,系统运行正常', time: new Date().toLocaleTimeString() }
      ]
    }
  } catch (e) { /* silent */ }
  loading.value = false
  await nextTick()
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.alert-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.alert-row', { opacity: [0, 1], translateX: [-20, 0], delay: stagger(80) }, '-=300')
})
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="alert-title text-lg font-semibold" style="opacity:0">告警中心</h1>

    <div class="bg-surface-1 border border-hairline rounded-lg">
      <div class="px-4 py-3 border-b border-hairline flex items-center justify-between">
        <h2 class="text-sm font-medium text-ink-muted">实时告警</h2>
        <span class="text-xs text-ink-subtle">基于遥测阈值检测</span>
      </div>
      <div class="divide-y divide-hairline">
        <div v-for="a in alerts" :key="a.id"
             class="alert-row flex items-center justify-between px-4 py-3" style="opacity:0">
          <div class="flex items-center gap-3">
            <span :class="[severityStyle[a.severity], 'px-2 py-0.5 rounded text-xs font-medium']">
              {{ a.severity }}
            </span>
            <span class="text-sm">{{ a.message }}</span>
          </div>
          <span class="text-xs text-ink-subtle">{{ a.time }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
