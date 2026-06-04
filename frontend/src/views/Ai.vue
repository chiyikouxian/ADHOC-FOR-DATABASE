<script setup>
import { ref, onMounted, nextTick } from 'vue'
import api from '../api'
import { createTimeline, stagger } from '../composables/useAnime'

const question = ref('')
const loading = ref(false)
const history = ref([])

onMounted(async () => {
  await nextTick()
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  tl.add('.ai-title', { opacity: [0, 1], translateY: [-15, 0] })
    .add('.ai-input-area', { opacity: [0, 1], translateY: [20, 0] }, '-=300')
  setTimeout(() => document.querySelectorAll('.ai-title,.ai-input-area').forEach(el => el.style.opacity = '1'), 2000)
})

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
      time: new Date().toLocaleTimeString()
    })
    question.value = ''
  } catch (e) {
    history.value.unshift({
      question: q,
      error: e.response?.data?.error || '请求失败',
      time: new Date().toLocaleTimeString()
    })
  }
  loading.value = false
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
    <h1 class="ai-title text-lg font-semibold" style="opacity:0">AI 智能问答</h1>
    <p class="text-xs text-ink-subtle -mt-4">用自然语言查询数据库，AI 自动生成 SQL 并执行</p>

    <div class="ai-input-area bg-surface-1 border border-hairline rounded-lg p-4 space-y-3" style="opacity:0">
      <div class="flex gap-2">
        <input v-model="question" @keyup.enter="ask"
          class="flex-1 h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm text-ink placeholder:text-ink-subtle focus:outline-none focus:border-primary"
          placeholder="例如：每架无人机完成了多少个任务？" />
        <button @click="ask" :disabled="loading"
          class="h-9 px-4 bg-primary hover:bg-primary-hover text-white text-sm rounded-md disabled:opacity-50 transition-colors cursor-pointer">
          {{ loading ? '查询中...' : '提问' }}
        </button>
      </div>
      <div class="flex flex-wrap gap-1.5">
        <span class="text-xs text-ink-subtle">试试：</span>
        <button v-for="e in examples" :key="e" @click="question = e; ask()"
          class="text-xs px-2 py-0.5 bg-surface-2 border border-hairline rounded text-ink-muted hover:text-ink hover:border-primary/50 transition-colors cursor-pointer">
          {{ e }}
        </button>
      </div>
    </div>

    <div v-if="history.length" class="space-y-4">
      <div v-for="(item, i) in history" :key="i"
        class="bg-surface-1 border border-hairline rounded-lg p-4 space-y-3">
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
