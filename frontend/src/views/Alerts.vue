<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import api from '../api'
import { createTimeline, stagger } from '../composables/useAnime'

const alerts = ref([])
const loading = ref(true)
let ws = null

const severityStyle = {
  critical: 'text-danger bg-danger/10 border-danger/20',
  warning: 'text-warning bg-warning/10 border-warning/20',
  info: 'text-info bg-info/10 border-info/20',
}

const typeLabel = {
  battery_drop: '电量低',
  rssi_drop: '信号弱',
  link_loss: '断链',
  gps_drift: 'GPS漂移',
}

onMounted(async () => {
  await fetchAlerts()
  connectWS()

  await nextTick()
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.alert-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.alert-row', { opacity: [0, 1], translateX: [-20, 0], delay: stagger(80) }, '-=300')
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
})

async function fetchAlerts() {
  loading.value = true
  try {
    const { data } = await api.get('/alerts')
    alerts.value = data.map(a => ({
      id: a.alert_id,
      droneId: a.drone_id,
      severity: a.severity,
      type: typeLabel[a.alert_type] || a.alert_type,
      alertType: a.alert_type,
      message: a.detail,
      time: a.created_at ? new Date(a.created_at).toLocaleString() : '',
      resolved: a.resolved,
    }))
  } catch (e) {
    console.error('告警加载失败:', e)
  }
  loading.value = false
}

function connectWS() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/drones`)

  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'new_alert' && msg.payload) {
        const a = msg.payload
        alerts.value.unshift({
          id: a.alert_id || Date.now(),
          droneId: a.drone_id,
          severity: a.severity,
          type: typeLabel[a.alert_type || a.type] || a.alert_type || a.type,
          alertType: a.alert_type,
          message: a.detail,
          time: new Date().toLocaleString(),
          resolved: false,
          _new: true,
        })
        setTimeout(() => {
          const item = alerts.value.find(x => x._new && x.id === (a.alert_id || a.id))
          if (item) {
            item._new = false
          }
        }, 3000)
      }
    } catch (_) {}
  }

  ws.onclose = () => setTimeout(connectWS, 5000)
}

async function resolveAlert(alert) {
  try {
    await api.post(`/alerts/${alert.id}/resolve`)
    alert.resolved = true
  } catch (e) {
    console.error('确认告警失败:', e)
  }
}

const activeCount = () => alerts.value.filter(a => !a.resolved).length
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="alert-title text-lg font-semibold" style="opacity:0">告警中心</h1>

    <div class="bg-surface-1 border border-hairline rounded-lg">
      <div class="px-4 py-3 border-b border-hairline flex items-center justify-between">
        <h2 class="text-sm font-medium text-ink-muted">
          实时告警
          <span v-if="!loading" class="ml-2 text-xs" :class="activeCount() > 0 ? 'text-danger' : 'text-success'">
            {{ activeCount() }} 条活跃
          </span>
        </h2>
        <div class="flex items-center gap-3">
          <span class="text-xs text-ink-subtle">
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-success animate-pulse mr-1"></span>
            WebSocket 实时推送 · 异常自恢复 · 每 10s 检测
          </span>
          <button
            @click="fetchAlerts"
            class="text-xs px-2 py-1 bg-surface-2 border border-hairline rounded text-ink-muted hover:text-ink cursor-pointer transition-colors"
          >
            刷新
          </button>
        </div>
      </div>

      <div v-if="loading" class="px-4 py-8 text-center text-xs text-ink-subtle">加载中...</div>

      <div v-else-if="alerts.length === 0" class="px-4 py-8 text-center text-xs text-ink-subtle">
        当前无告警，系统运行正常
      </div>

      <div v-else class="divide-y divide-hairline">
        <div
          v-for="a in alerts"
          :key="a.id"
          class="alert-row flex items-center justify-between px-4 py-3 transition-all"
          :class="[a._new ? 'bg-primary/5 border-l-2 border-primary' : '', a.resolved ? 'opacity-50' : '']"
          style="opacity:0"
        >
          <div class="flex items-center gap-3">
            <span :class="[severityStyle[a.severity] || severityStyle.info, 'px-2 py-0.5 rounded text-xs font-medium border']">
              {{ a.severity === 'critical' ? '严重' : a.severity === 'warning' ? '警告' : '信息' }}
            </span>
            <span class="text-xs px-1.5 py-0.5 bg-surface-2 rounded font-mono">{{ a.type }}</span>
            <span class="text-xs text-ink-subtle">Drone-{{ a.droneId }}</span>
            <span class="text-sm text-ink">{{ a.message }}</span>
            <span v-if="a.resolved" class="text-xs text-success font-medium">已恢复</span>
          </div>
          <div class="flex items-center gap-3">
            <span class="text-xs text-ink-subtle">{{ a.time }}</span>
            <button
              v-if="!a.resolved"
              @click="resolveAlert(a)"
              class="text-xs px-2 py-1 bg-surface-2 border border-hairline rounded text-ink-muted hover:text-success hover:border-success/30 cursor-pointer transition-colors"
            >
              确认
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="flex gap-4 text-xs text-ink-subtle">
      <span>严重: 需立即处理</span>
      <span>警告: 关注趋势</span>
      <span>信息: 已自动恢复</span>
    </div>
  </div>
</template>
