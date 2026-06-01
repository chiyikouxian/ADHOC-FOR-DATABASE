<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()

const navItems = [
  { path: '/', label: '态势大屏', icon: '◉' },
  { path: '/missions', label: '任务管理', icon: '▶' },
  { path: '/telemetry', label: '遥测曲线', icon: '〜' },
  { path: '/topology', label: '拓扑分析', icon: '⬡' },
  { path: '/alerts', label: '告警中心', icon: '△' },
]

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="flex h-screen overflow-hidden">
    <aside class="w-[200px] bg-surface-2 border-r border-hairline flex flex-col">
      <div class="h-12 flex items-center px-4 border-b border-hairline">
        <span class="text-primary font-semibold text-sm tracking-wide">FANET Platform</span>
      </div>
      <nav class="flex-1 py-2">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-3 px-4 py-2 mx-2 rounded-md text-sm text-ink-muted hover:bg-surface-3 hover:text-ink transition-colors"
          active-class="!bg-primary/10 !text-primary"
        >
          <span class="text-base">{{ item.icon }}</span>
          {{ item.label }}
        </router-link>
      </nav>
      <div class="p-3 border-t border-hairline">
        <div class="flex items-center justify-between">
          <span class="text-xs text-ink-subtle">{{ auth.username }}</span>
          <button @click="logout" class="text-xs text-ink-subtle hover:text-danger transition-colors cursor-pointer">退出</button>
        </div>
      </div>
    </aside>
    <main class="flex-1 overflow-auto bg-canvas">
      <router-view />
    </main>
  </div>
</template>
