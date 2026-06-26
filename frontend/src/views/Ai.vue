<script setup>
import { ref, onMounted, nextTick } from 'vue'
import api from '../api'
import { createTimeline } from '../composables/useAnime'

const question = ref('')
const loading = ref(false)
const history = ref([])

const predDroneId = ref(1)
const predMinutes = ref(60)
const predUseAI = ref(false)
const predLoading = ref(false)
const prediction = ref(null)

const reportMissionId = ref(1)
const reportUseAI = ref(true)
const reportLoading = ref(false)
const reportData = ref(null)
const reportMissions = ref([])

onMounted(async () => {
  await fetchReportMissions()
  await nextTick()
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.ai-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.ai-input-area', { opacity: [0, 1], translateY: [20, 0] }, '-=300')
    .add('.ai-endurance', { opacity: [0, 1], translateY: [20, 0] }, '-=300')
    .add('.ai-report', { opacity: [0, 1], translateY: [20, 0] }, '-=300')
  setTimeout(() => {
    document.querySelectorAll('.ai-title,.ai-input-area,.ai-endurance,.ai-report').forEach(el => {
      el.style.opacity = '1'
    })
  }, 2000)
})

async function fetchReportMissions() {
  try {
    const { data } = await api.get('/missions')
    reportMissions.value = data.map(m => ({
      missionId: m.mission_id ?? m.missionId,
      title: m.title,
      status: m.status,
    }))
    if (reportMissions.value.length > 0 && !reportMissions.value.find(m => m.missionId === reportMissionId.value)) {
      reportMissionId.value = reportMissions.value[0].missionId
    }
  } catch (_) {
    reportMissions.value = []
  }
}

async function ask() {
  const q = question.value.trim()
  if (!q || loading.value) return
  loading.value = true
  try {
    const { data } = await api.post('/ai/ask', { question: q })
    history.value.unshift({
      question: q,
      sql: data.sql || 'N/A',
      targetDb: data.target_db || 'unknown',
      rows: data.rows || [],
      error: data.error || null,
      time: new Date().toLocaleTimeString(),
    })
    question.value = ''
  } catch (e) {
    history.value.unshift({
      question: q,
      error: e.response?.data?.error || '请求失败',
      time: new Date().toLocaleTimeString(),
    })
  }
  loading.value = false
}

async function predictEndurance() {
  if (predLoading.value) return
  predLoading.value = true
  prediction.value = null
  try {
    const { data } = await api.post('/ai/endurance', {
      droneId: predDroneId.value,
      historyMinutes: predMinutes.value,
      useAI: predUseAI.value,
    })
    prediction.value = data
  } catch (e) {
    prediction.value = { error: e.response?.data?.error || '预测失败' }
  }
  predLoading.value = false
}

async function generateReport() {
  if (reportLoading.value) return
  reportLoading.value = true
  reportData.value = null
  try {
    const { data } = await api.post('/ai/report', {
      missionId: reportMissionId.value,
      useAI: reportUseAI.value,
    })
    reportData.value = data
  } catch (e) {
    reportData.value = { error: e.response?.data?.error || '报告生成失败' }
  }
  reportLoading.value = false
}

const examples = [
  '每架无人机完成了多少个任务？按排名显示',
  'Drone-1 最近 5 条遥测的电量和信号',
  '按电量从低到高排列所有无人机',
  '地面站（drone_id=0）的位置坐标是什么',
]
</script>

<template>
  <div class="p-6 space-y-6">
    <h1 class="ai-title text-lg font-semibold" style="opacity:0">AI 智能分析</h1>
    <p class="text-xs text-ink-subtle -mt-4">自然语言查询 + 续航预测</p>

    <div class="ai-input-area bg-surface-1 border border-hairline rounded-lg p-4 space-y-3" style="opacity:0">
      <h2 class="text-sm font-medium text-ink-muted">NL2SQL 自然语言查询</h2>
      <div class="flex gap-2">
        <input
          v-model="question"
          @keyup.enter="ask"
          class="flex-1 h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm text-ink placeholder:text-ink-subtle focus:outline-none focus:border-primary"
          placeholder="例如：每架无人机完成了多少个任务？"
        />
        <button
          @click="ask"
          :disabled="loading"
          class="h-9 px-4 bg-primary hover:bg-primary-hover text-white text-sm rounded-md disabled:opacity-50 transition-colors cursor-pointer"
        >
          {{ loading ? '查询中...' : '提问' }}
        </button>
      </div>
      <div class="flex flex-wrap gap-1.5">
        <span class="text-xs text-ink-subtle">试试：</span>
        <button
          v-for="e in examples"
          :key="e"
          @click="question = e; ask()"
          class="text-xs px-2 py-0.5 bg-surface-2 border border-hairline rounded text-ink-muted hover:text-ink hover:border-primary/50 transition-colors cursor-pointer"
        >
          {{ e }}
        </button>
      </div>
    </div>

    <div class="ai-endurance bg-surface-1 border border-hairline rounded-lg p-4 space-y-3" style="opacity:0">
      <h2 class="text-sm font-medium text-ink-muted">续航预测</h2>
      <p class="text-xs text-ink-subtle">基于 TDengine 历史电量放电曲线做线性回归，估算剩余飞行时间</p>
      <div class="flex gap-3 items-end flex-wrap">
        <div>
          <label class="block text-xs text-ink-subtle mb-1">无人机 ID</label>
          <input
            v-model.number="predDroneId"
            type="number"
            min="1"
            max="7"
            class="w-20 h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm text-ink focus:outline-none focus:border-primary"
          />
        </div>
        <div>
          <label class="block text-xs text-ink-subtle mb-1">历史数据 (分钟)</label>
          <select
            v-model.number="predMinutes"
            class="h-9 px-2 bg-surface-2 border border-hairline rounded-md text-sm text-ink focus:outline-none focus:border-primary"
          >
            <option :value="30">30 分钟</option>
            <option :value="60">60 分钟</option>
            <option :value="120">120 分钟</option>
          </select>
        </div>
        <div class="flex items-center gap-2 h-9">
          <input v-model="predUseAI" type="checkbox" id="useAi" class="accent-primary" />
          <label for="useAi" class="text-xs text-ink-muted cursor-pointer">AI 增强分析</label>
        </div>
        <button
          @click="predictEndurance"
          :disabled="predLoading"
          class="h-9 px-4 bg-primary hover:bg-primary-hover text-white text-sm rounded-md disabled:opacity-50 transition-colors cursor-pointer"
        >
          {{ predLoading ? '预测中...' : '预测' }}
        </button>
      </div>

      <div v-if="prediction" class="p-3 bg-surface-2 rounded border border-hairline space-y-2">
        <div v-if="prediction.error" class="text-xs text-danger">{{ prediction.error }}</div>
        <template v-else>
          <div class="grid grid-cols-3 gap-3 text-xs">
            <div>
              <span class="text-ink-subtle">当前电量</span>
              <p class="text-ink font-semibold">{{ prediction.currentBatteryPct }}%</p>
            </div>
            <div>
              <span class="text-ink-subtle">放电速率</span>
              <p class="text-ink font-semibold">{{ prediction.dischargeRatePctPerMin }}%/min</p>
            </div>
            <div>
              <span class="text-ink-subtle">预估剩余</span>
              <p class="text-ink font-semibold" :class="{ 'text-danger': prediction.estimatedMinutes > 0 && prediction.estimatedMinutes < 15, 'text-warning': prediction.estimatedMinutes >= 15 && prediction.estimatedMinutes < 30 }">
                {{ prediction.estimatedMinutes > 0 ? prediction.estimatedMinutes + ' 分钟' : '无法估算' }}
              </p>
            </div>
          </div>
          <div class="flex gap-3 text-xs">
            <span class="text-ink-subtle">拟合度 R²={{ prediction.rSquared }}</span>
            <span :class="['px-1 rounded', prediction.confidence === 'high' ? 'bg-success/20 text-success' : prediction.confidence === 'medium' ? 'bg-warning/20 text-warning' : 'bg-danger/20 text-danger']">
              置信度: {{ prediction.confidence === 'high' ? '高' : prediction.confidence === 'medium' ? '中' : '低' }}
            </span>
            <span class="text-ink-subtle">数据点: {{ prediction.dataPoints }}</span>
          </div>
          <div v-if="prediction.warning" class="text-xs text-danger font-medium">⚠ {{ prediction.warning }}</div>
          <div v-if="prediction.aiAnalysis" class="text-xs text-ink-muted italic border-t border-hairline pt-2 mt-2">
            {{ prediction.aiAnalysis }}
          </div>
        </template>
      </div>
    </div>

    <div class="ai-report bg-surface-1 border border-hairline rounded-lg p-4 space-y-3" style="opacity:0">
      <h2 class="text-sm font-medium text-ink-muted">飞行复盘报告</h2>
      <p class="text-xs text-ink-subtle">聚合 TDengine 遥测摘要 + PG 告警记录，可选 AI 生成人话版复盘总结</p>
      <div class="flex gap-3 items-end flex-wrap">
        <div>
          <label class="block text-xs text-ink-subtle mb-1">任务 ID</label>
          <select
            v-model.number="reportMissionId"
            class="h-9 px-2 bg-surface-2 border border-hairline rounded-md text-sm text-ink focus:outline-none focus:border-primary"
          >
            <option v-for="mission in reportMissions" :key="mission.missionId" :value="mission.missionId">
              #{{ mission.missionId }} {{ mission.title }}
            </option>
          </select>
        </div>
        <div class="flex items-center gap-2 h-9">
          <input v-model="reportUseAI" type="checkbox" id="reportUseAi" class="accent-primary" />
          <label for="reportUseAi" class="text-xs text-ink-muted cursor-pointer">AI 生成总结</label>
        </div>
        <button
          @click="generateReport"
          :disabled="reportLoading"
          class="h-9 px-4 bg-primary hover:bg-primary-hover text-white text-sm rounded-md disabled:opacity-50 transition-colors cursor-pointer"
        >
          {{ reportLoading ? '生成中...' : '生成报告' }}
        </button>
      </div>

      <div v-if="reportData" class="space-y-3">
        <div v-if="reportData.error" class="p-3 bg-danger/10 border border-danger/20 rounded text-xs text-danger">
          {{ reportData.error }}
        </div>
        <template v-else>
          <div v-if="reportData.aiSummary" class="p-3 bg-primary/5 border border-primary/20 rounded">
            <p class="text-xs text-ink-subtle mb-1">AI 总结</p>
            <p class="text-sm text-ink">{{ reportData.aiSummary }}</p>
          </div>
          <div class="grid grid-cols-4 gap-3">
            <div class="bg-surface-2 rounded p-2 text-center">
              <p class="text-lg font-semibold text-ink">{{ reportData.stats?.totalDrones }}</p>
              <p class="text-xs text-ink-subtle">参与无人机</p>
            </div>
            <div class="bg-surface-2 rounded p-2 text-center">
              <p class="text-lg font-semibold text-success">{{ reportData.stats?.completed }}</p>
              <p class="text-xs text-ink-subtle">已完成</p>
            </div>
            <div class="bg-surface-2 rounded p-2 text-center">
              <p class="text-lg font-semibold text-warning">{{ reportData.stats?.totalAlerts }}</p>
              <p class="text-xs text-ink-subtle">告警总数</p>
            </div>
            <div class="bg-surface-2 rounded p-2 text-center">
              <p class="text-lg font-semibold text-danger">{{ reportData.stats?.criticalAlerts }}</p>
              <p class="text-xs text-ink-subtle">严重告警</p>
            </div>
          </div>
          <div v-if="reportData.droneSummaries?.length" class="space-y-1">
            <p class="text-xs text-ink-subtle">无人机遥测摘要</p>
            <div
              v-for="ds in reportData.droneSummaries"
              :key="ds.droneId"
              class="flex gap-3 text-xs bg-surface-2 rounded p-2"
            >
              <span class="text-ink font-medium">{{ ds.serialNo }}</span>
              <span class="text-ink-muted">均电: {{ ds.avgBattery }}%</span>
              <span class="text-ink-muted">均信号: {{ ds.avgRssi }}dBm</span>
              <span class="text-ink-subtle">{{ ds.dataPoints }} 个数据点</span>
            </div>
          </div>
          <div v-if="reportData.alerts?.length" class="space-y-1">
            <p class="text-xs text-ink-subtle">相关告警 ({{ reportData.alerts.length }} 条)</p>
            <div
              v-for="a in reportData.alerts.slice(0, 5)"
              :key="a.alertId"
              class="text-xs flex gap-2 bg-surface-2 rounded p-1.5"
            >
              <span :class="a.severity === 'critical' ? 'text-danger' : 'text-warning'">[{{ a.severity }}]</span>
              <span class="text-ink-muted">{{ a.detail }}</span>
              <span v-if="a.resolved" class="text-success">✓</span>
            </div>
          </div>
          <p class="text-xs text-ink-subtle">生成时间: {{ reportData.generatedAt }}</p>
        </template>
      </div>
    </div>

    <div v-if="history.length" class="space-y-4">
      <div
        v-for="(item, i) in history"
        :key="i"
        class="bg-surface-1 border border-hairline rounded-lg p-4 space-y-3"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-ink">Q: {{ item.question }}</span>
          <span class="text-xs text-ink-subtle">{{ item.time }}</span>
        </div>

        <div v-if="item.error" class="p-3 bg-danger/10 border border-danger/30 rounded text-xs text-danger">
          {{ item.error }}
        </div>

        <template v-else>
          <div class="flex items-center gap-3 text-xs">
            <span :class="['px-1.5 py-0.5 rounded font-mono', item.targetDb === 'tdengine' ? 'text-warning bg-warning/10' : 'text-primary bg-primary/10']">
              {{ item.targetDb === 'tdengine' ? 'TDengine' : 'PostgreSQL' }}
            </span>
            <code class="text-ink-muted text-xs bg-surface-2 px-2 py-1 rounded">{{ item.sql }}</code>
          </div>

          <div v-if="item.rows.length" class="overflow-x-auto">
            <table class="w-full text-xs">
              <thead>
                <tr class="text-ink-subtle border-b border-hairline">
                  <th v-for="col in Object.keys(item.rows[0])" :key="col" class="px-2 py-1.5 text-left font-medium">
                    {{ col }}
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-hairline">
                <tr v-for="(row, ri) in item.rows" :key="ri">
                  <td v-for="col in Object.keys(item.rows[0])" :key="col" class="px-2 py-1.5 font-mono text-ink-muted">
                    {{ row[col] ?? '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="text-xs text-ink-subtle">（无结果）</p>
        </template>
      </div>
    </div>
  </div>
</template>
