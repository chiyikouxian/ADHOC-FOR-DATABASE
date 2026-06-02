<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { createTimeline, stagger } from '../composables/useAnime'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

onMounted(() => {
  const tl = createTimeline({ defaults: { duration: 600, ease: 'outExpo' } })
  tl.add('.login-card', { opacity: [0, 1], scale: [0.95, 1], translateY: [30, 0] })
    .add('.login-title', { opacity: [0, 1], translateY: [-10, 0] }, '-=400')
    .add('.login-field', { opacity: [0, 1], translateY: [15, 0], delay: stagger(80) }, '-=300')
    .add('.login-btn', { opacity: [0, 1], translateY: [10, 0] }, '-=200')
})

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    router.push('/')
  } catch (e) {
    error.value = e.response?.data?.error || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-canvas flex items-center justify-center">
    <div class="login-card w-[360px] bg-surface-1 border border-hairline rounded-lg p-8" style="opacity:0">
      <h1 class="login-title text-xl font-semibold text-ink mb-1" style="opacity:0">FANET Platform</h1>
      <p class="login-title text-sm text-ink-subtle mb-6" style="opacity:0">无人机自组网集群管理平台</p>
      <form @submit.prevent="handleLogin" class="space-y-4">
        <div class="login-field" style="opacity:0">
          <label class="block text-xs text-ink-subtle mb-1.5">用户名</label>
          <input
            v-model="username"
            type="text"
            class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm text-ink placeholder:text-ink-subtle focus:outline-none focus:border-primary transition-colors"
            placeholder="admin"
          />
        </div>
        <div class="login-field" style="opacity:0">
          <label class="block text-xs text-ink-subtle mb-1.5">密码</label>
          <input
            v-model="password"
            type="password"
            class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm text-ink placeholder:text-ink-subtle focus:outline-none focus:border-primary transition-colors"
            placeholder="password"
          />
        </div>
        <p v-if="error" class="text-xs text-danger">{{ error }}</p>
        <button
          type="submit"
          :disabled="loading"
          class="login-btn w-full h-9 bg-primary hover:bg-primary-hover text-white text-sm font-medium rounded-md transition-colors disabled:opacity-50 cursor-pointer"
          style="opacity:0"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
    </div>
  </div>
</template>
