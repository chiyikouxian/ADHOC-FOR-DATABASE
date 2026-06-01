<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const ranking = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await api.get('/missions/ranking')
    ranking.value = data
  } catch (e) { /* silent */ }
  loading.value = false
})

async function assignDrone(droneId) {
  try {
    await api.post(`/missions/4/assign/${droneId}`)
    const { data } = await api.get('/missions/ranking')
    ranking.value = data
  } catch (e) {
    alert(e.response?.data?.reason || '分配失败')
  }
}

async function resetDrone(droneId) {
  await api.post(`/missions/reset-drone/${droneId}`)
}
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="text-lg font-semibold">任务管理</h1>

    <div class="bg-surface-1 border border-hairline rounded-lg">
      <div class="px-4 py-3 border-b border-hairline flex items-center justify-between">
        <h2 class="text-sm font-medium text-ink-muted">任务完成排行榜</h2>
        <span class="text-xs text-ink-subtle">RANK() OVER 窗口函数</span>
      </div>
      <table class="w-full text-sm">
        <thead>
          <tr class="text-xs text-ink-subtle border-b border-hairline">
            <th class="px-4 py-2 text-left">排名</th>
            <th class="px-4 py-2 text-left">编号</th>
            <th class="px-4 py-2 text-left">完成任务数</th>
            <th class="px-4 py-2 text-left">平均电量</th>
            <th class="px-4 py-2 text-left">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-hairline">
          <tr v-for="r in ranking" :key="r.drone_id">
            <td class="px-4 py-2 font-mono text-primary">#{{ r.rank }}</td>
            <td class="px-4 py-2">{{ r.serial_no }}</td>
            <td class="px-4 py-2 font-mono">{{ r.completed_missions }}</td>
            <td class="px-4 py-2 font-mono">{{ r.avg_battery }}%</td>
            <td class="px-4 py-2 flex gap-2">
              <button @click="assignDrone(r.drone_id)"
                class="px-2 py-1 text-xs bg-primary/10 text-primary rounded hover:bg-primary/20 cursor-pointer">
                分配任务
              </button>
              <button @click="resetDrone(r.drone_id)"
                class="px-2 py-1 text-xs bg-surface-2 text-ink-subtle rounded hover:bg-surface-3 cursor-pointer">
                重置
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
