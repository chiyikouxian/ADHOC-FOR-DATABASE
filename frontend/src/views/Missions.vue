<script setup>
import { ref, onMounted, nextTick } from 'vue'
import api from '../api'
import { stagger, createTimeline } from '../composables/useAnime'

const ranking = ref([])
const missions = ref([])
const drones = ref([])
const selectedMissionId = ref(null)
const loading = ref(true)
const assignMsg = ref('')

onMounted(async () => {
  await Promise.all([fetchRanking(), fetchMissions(), fetchDrones()])
  loading.value = false

  await nextTick()
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.mission-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.mission-panel', { opacity: [0, 1], translateY: [-10, 0] }, '-=300')
    .add('.mission-row', { opacity: [0, 1], translateX: [-15, 0], delay: stagger(60) }, '-=300')
})

async function fetchRanking() {
  try {
    const { data } = await api.get('/missions/ranking')
    ranking.value = data
  } catch (e) {
    console.error('排名加载失败:', e)
  }
}

async function fetchMissions() {
  try {
    const { data } = await api.get('/missions')
    missions.value = data.map(m => ({
      missionId: m.mission_id ?? m.missionId,
      title: m.title,
      status: m.status,
    }))
    if (missions.value.length > 0 && !selectedMissionId.value) {
      const active = missions.value.find(m => m.status !== 'completed')
      selectedMissionId.value = active ? active.missionId : missions.value[0].missionId
    }
  } catch (e) {
    console.error('任务列表加载失败:', e)
    missions.value = []
  }
}

async function fetchDrones() {
  try {
    const { data } = await api.get('/drones')
    drones.value = data.filter(d => d.droneId !== 0)
  } catch (e) {
    console.error('无人机列表加载失败:', e)
  }
}

async function assignDrone(droneId) {
  if (!selectedMissionId.value) {
    assignMsg.value = '请先选择任务'
    setTimeout(() => { assignMsg.value = '' }, 3000)
    return
  }
  assignMsg.value = ''
  try {
    await api.post(`/missions/${selectedMissionId.value}/assign/${droneId}`)
    assignMsg.value = `✅ 无人机 ${droneId} 已分配给任务 #${selectedMissionId.value}`
    await fetchRanking()
  } catch (e) {
    assignMsg.value = `❌ ${e.response?.data?.reason || '分配失败（无人机可能不空闲或电量不足）'}`
  }
  setTimeout(() => { assignMsg.value = '' }, 5000)
}

async function resetDrone(droneId) {
  try {
    await api.post(`/missions/reset-drone/${droneId}`)
    assignMsg.value = `🔄 无人机 ${droneId} 状态已重置`
    await fetchRanking()
  } catch (e) {
    assignMsg.value = '❌ 重置失败'
  }
  setTimeout(() => { assignMsg.value = '' }, 3000)
}

const statusLabel = {
  draft: '草稿',
  scheduled: '已排期',
  running: '执行中',
  completed: '已完成',
  aborted: '已中止',
}

const statusColor = {
  draft: 'text-ink-subtle',
  scheduled: 'text-info',
  running: 'text-success',
  completed: 'text-ink-muted',
  aborted: 'text-danger',
}
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="mission-title text-lg font-semibold" style="opacity:0">任务管理</h1>

    <div class="mission-panel bg-surface-1 border border-hairline rounded-lg p-4 space-y-3" style="opacity:0">
      <h2 class="text-sm font-medium text-ink-muted">选择要分配的任务</h2>
      <div v-if="missions.length" class="flex gap-2 flex-wrap">
        <button
          v-for="m in missions"
          :key="m.missionId"
          @click="selectedMissionId = m.missionId"
          :class="['px-3 py-2 rounded-md text-sm border transition-colors cursor-pointer',
            selectedMissionId === m.missionId
              ? 'bg-primary/10 border-primary text-primary'
              : 'bg-surface-2 border-hairline text-ink-muted hover:border-primary/30']"
        >
          <span class="block font-medium">#{{ m.missionId }} {{ m.title }}</span>
          <span :class="['text-xs', statusColor[m.status] || 'text-ink-subtle']">{{ statusLabel[m.status] || m.status }}</span>
        </button>
      </div>
      <p v-else class="text-xs text-ink-subtle">暂无可选任务</p>
      <div v-if="assignMsg" :class="['text-xs', assignMsg.startsWith('✅') ? 'text-success' : assignMsg.startsWith('🔄') ? 'text-info' : 'text-danger']">
        {{ assignMsg }}
      </div>
      <p class="text-xs text-ink-subtle">
        PostgreSQL 事务 + SELECT ... FOR UPDATE 行锁防止并发冲突。
        <span v-if="selectedMissionId">当前选中：任务 #{{ selectedMissionId }}</span>
      </p>
    </div>

    <div class="bg-surface-1 border border-hairline rounded-lg">
      <div class="px-4 py-3 border-b border-hairline flex items-center justify-between">
        <h2 class="text-sm font-medium text-ink-muted">任务完成排行榜</h2>
        <span class="text-xs text-ink-subtle">RANK() OVER (ORDER BY COUNT) 窗口函数</span>
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
          <tr v-for="r in ranking" :key="r.drone_id" class="mission-row" style="opacity:0">
            <td class="px-4 py-2 font-mono text-primary">#{{ r.rank }}</td>
            <td class="px-4 py-2">{{ r.serial_no }}</td>
            <td class="px-4 py-2 font-mono">{{ r.completed_missions }}</td>
            <td class="px-4 py-2 font-mono">{{ r.avg_battery }}%</td>
            <td class="px-4 py-2 flex gap-2">
              <button
                @click="assignDrone(r.drone_id)"
                class="px-2 py-1 text-xs bg-primary/10 text-primary rounded hover:bg-primary/20 cursor-pointer transition-colors"
              >
                分配任务
              </button>
              <button
                @click="resetDrone(r.drone_id)"
                class="px-2 py-1 text-xs bg-surface-2 text-ink-subtle rounded hover:bg-surface-3 cursor-pointer transition-colors"
              >
                重置
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
