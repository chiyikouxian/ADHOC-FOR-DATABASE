<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const chartRef = ref(null)
let chart = null

onMounted(async () => {
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
    legend: { textStyle: { color: '#8a8f98' }, top: 10 },
    grid: { left: 50, right: 20, top: 50, bottom: 30 },
    xAxis: { type: 'category', data: [], axisLine: { lineStyle: { color: '#23252a' } } },
    yAxis: [
      { type: 'value', name: '电量%', axisLine: { lineStyle: { color: '#23252a' } }, splitLine: { lineStyle: { color: '#23252a' } } },
      { type: 'value', name: 'RSSI', axisLine: { lineStyle: { color: '#23252a' } }, splitLine: { show: false } }
    ],
    series: [
      { name: '电量', type: 'line', smooth: true, color: '#5e6ad2', data: [] },
      { name: '信号', type: 'line', smooth: true, yAxisIndex: 1, color: '#27a644', data: [] }
    ]
  })

  try {
    const { data } = await api.get('/drones')
    if (data.length > 0) {
      const labels = data.map(d => 'D-' + d.droneId)
      const battery = data.map(d => d.batteryPct?.toFixed(1) || 0)
      const rssi = data.map(d => d.rssi || 0)
      chart.setOption({
        xAxis: { data: labels },
        series: [{ data: battery }, { data: rssi }]
      })
    }
  } catch (e) { /* silent */ }
})
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="text-lg font-semibold">遥测曲线</h1>
    <div class="bg-surface-1 border border-hairline rounded-lg p-4">
      <div ref="chartRef" class="w-full h-[400px]"></div>
    </div>
  </div>
</template>
