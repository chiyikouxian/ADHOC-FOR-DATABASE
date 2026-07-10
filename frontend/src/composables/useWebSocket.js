import { ref, onUnmounted } from 'vue'

export function useWebSocket(path) {
  const data = ref(null)
  const connected = ref(false)
  let ws = null
  let reconnectTimer = null

  function connect() {
    const token = localStorage.getItem('token')
    if (!token) return
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const separator = path.includes('?') ? '&' : '?'
    const url = `${protocol}://${window.location.host}${path}${separator}token=${encodeURIComponent(token)}`
    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
    }

    ws.onmessage = (event) => {
      try {
        data.value = JSON.parse(event.data)
      } catch (e) { /* ignore malformed messages */ }
    }

    ws.onclose = () => {
      connected.value = false
      reconnectTimer = setTimeout(connect, 3000)
    }

    ws.onerror = () => ws.close()
  }

  function close() {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (ws) { ws.onclose = null; ws.close() }
  }

  connect()
  onUnmounted(close)

  return { data, connected }
}
