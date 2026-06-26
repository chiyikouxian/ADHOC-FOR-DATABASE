<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import api from '../api'
import { createTimeline } from '../composables/useAnime'

const chartRef = ref(null)
const drones = ref([])
const selectedDrone = ref(1)
const timeWindow = ref('1m')
const timeRange = ref(60) // 分钟
const loading = ref(false)
let chart = null
let ws = null
let pollTimer = null

// ECharts 配置
function initChart() {
  chart = echarts.init(chartRef.value)
  chart.setOption({
    backgroundColor: 'transparent',
    textStyle: { color: '#8a8f98' },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#18191a',
      borderColor: '#23252a',
      textStyle: { color: '#f7f8f8' }
    },
    legend: { textStyle: { color: '#8a8f98' }, top: 10, data: ['电量(%)', '信号(dBm)'] },
    grid: { left: 55, right: 55, top: 55, bottom: 35 },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: '#23252a' } },
      splitLine: { show: false },
      axisLabel: { color: '#8a8f98', fontSize: 10 },
    },
    yAxis: [
      {
        type: 'value', name: '电量%', min: 0, max: 100,
        axisLine: { lineStyle: { color: '#23252a' } },
        splitLine: { lineStyle: { color: '#18191a' } },
        axisLabel: { color: '#8a8f98', fontSize: 10 },
      },
      {
        type: 'value', name: 'RSSI (dBm)',
        axisLine: { lineStyle: { color: '#23252a' } },
        splitLine: { show: false },
        axisLabel: { color: '#8a8f98', fontSize: 10 },
      }
    ],
    series: [
      { name: '电量(%)', type: 'line', smooth: true, color: '#5e6ad2', yAxisIndex: 0, data: [], showSymbol: false },
      { name: '信号(dBm)', type: 'line', smooth: true, color: '#27a644', yAxisIndex: 1, data: [], showSymbol: false }
    ]
  })
}

// 从 TDengine API 拉取遥测时序
async function fetchSeries() {
  if (!selectedDrone.value) return
  loading.value = true
  try {
    const { data } = await api.get(`/telemetry/drone/${selectedDrone.value}/series`, {
      params: { window: timeWindow.value, minutes: timeRange.value }
    })
    if (data && data.length > 0) {
      const battData = [], rssiData = []
      for (const pt of data) {
        const ts = pt.win_start || pt.ts
        battData.push([ts, pt.avg_battery != null ? Number(pt.avg_battery) : (pt.battery_pct != null ? Number(pt.battery_pct) : null)])
        rssiData.push([ts, pt.avg_rssi != null ? Number(pt.avg_rssi) : (pt.rssi != null ? Number(pt.rssi) : null)])
      }
      chart.setOption({
        series: [
          { name: '电量(%)', data: battData.filter(d => d[1] != null) },
          { name: '信号(dBm)', data: rssiData.filter(d => d[1] != null) }
        ]
      })
    }
  } catch (e) {
    console.error('遥测数据加载失败:', e)
  }
  loading.value = false
}

// 获取无人机列表
async function fetchDrones() {
  try {
    const { data } = await api.get('/drones')
    drones.value = data.filter(d => d.droneId !== 0) // 排除地面站
    if (drones.value.length > 0 && !drones.value.find(d => d.droneId === selectedDrone.value)) {
      selectedDrone.value = drones.value[0].droneId
      fetchSeries()
    }
  } catch (e) { /* silent */ }
}

// WebSocket: 收到 drone_update 时刷新当前选中机的曲线
function connectWS() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/drones`)
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'drone_update' && msg.payload?.droneId === selectedDrone.value) {
        // 有新数据，刷新曲线
        fetchSeries()
      }
    } catch (_) {}
  }
  ws.onclose = () => setTimeout(connectWS, 5000)
}

// 切换无人机/窗口时重新拉取
watch([selectedDrone, timeWindow, timeRange], () => {
  fetchSeries()
})

onMounted(async () => {
  initChart()
  await fetchDrones()
  await fetchSeries()
  connectWS()

  // 每 30s 轮询刷新（兜底）
  pollTimer = setInterval(fetchSeries, 30000)

  // 入场动画
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.tele-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.tele-controls', { opacity: [0, 1], translateY: [-10, 0] }, '-=300')
    .add('.tele-chart', { opacity: [0, 1], translateY: [30, 0] }, '-=300')
})

onUnmounted(() => {
  if (ws) ws.close()
  if (pollTimer) clearInterval(pollTimer)
  if (chart) chart.dispose()
})

window.addEventListener('resize', () => chart?.resize())
</script>

<template>
  <div class="p-6 space-y-4">
    <h1 class="tele-title text-lg font-semibold" style="opacity:0">遥测曲线</h1>

    <!-- 控制面板 -->
    <div class="tele-controls flex items-center gap-3 flex-wrap" style="opacity:0">
      <div class="flex items-center gap-2">
        <label class="text-xs text-ink-subtle">无人机</label>
        <select v-model.number="selectedDrone"
          class="h-9 px-2 bg-surface-2 border border-hairline rounded-md text-sm text-ink focus:outline-none focus:border-primary">
          <option v-for="d in drones" :key="d.droneId" :value="d.droneId">
            {{ d.serialNo || 'Drone-' + d.droneId }} ({{ d.status }})
          </option>
        </select>
      </div>
      <div class="flex items-center gap-2">
        <label class="text-xs text-ink-subtle">聚合窗口</label>
        <select v-model="timeWindow"
          class="h-9 px-2 bg-surface-2 border border-hairline rounded-md text-sm text-ink focus:outline-none focus:border-primary">
          <option value="10s">10 秒</option>
          <option value="30s">30 秒</option>
          <option value="1m">1 分钟</option>
          <option value="5m">5 分钟</option>
        </select>
      </div>
      <div class="flex items-center gap-2">
        <label class="text-xs text-ink-subtle">时间范围</label>
        <select v-model.number="timeRange"
          class="h-9 px-2 bg-surface-2 border border-hairline rounded-md text-sm text-ink focus:outline-none focus:border-primary">
          <option :value="15">15 分钟</option>
          <option :value="30">30 分钟</option>
          <option :value="60">60 分钟</option>
          <option :value="120">2 小时</option>
        </select>
      </div>
      <span v-if="loading" class="text-xs text-ink-subtle">加载中...</span>
      <span class="text-xs text-ink-subtle ml-auto">
        TDengine INTERVAL({{ timeWindow }}) 降采样 · WebSocket 实时刷新
      </span>
    </div>

    <!-- 图表 -->
    <div class="tele-chart bg-surface-1 border border-hairline rounded-lg p-4" style="opacity:0">
      <div ref="chartRef" class="w-full h-[400px]"></div>
    </div>
  </div>
</template>
