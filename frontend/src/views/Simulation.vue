<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const scenarios = ref([])
const selectedScenarioId = ref(null)
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const runtimeBusy = ref(false)
const runtimeStatus = ref({ running: false })
const runtimePreview = ref({ nodes: [], edges: [], metrics: { running: false } })
const message = ref('')
const messageType = ref('info')
const chartRef = ref(null)

const form = reactive(createEmptyScenario())
const selectedScenario = computed(() =>
  scenarios.value.find(item => item.scenarioId === selectedScenarioId.value) || null
)
const droneOverrideCount = computed(() => form.drones.length)
const linkOverrideCount = computed(() => form.links.length)
const activePreview = computed(() => {
  const hasRuntimeGraph = runtimeStatus.value.running
    && Array.isArray(runtimePreview.value.nodes)
    && runtimePreview.value.nodes.length > 0
  return hasRuntimeGraph ? runtimePreview.value : buildDraftPreview()
})

let chart = null
let refreshTimer = null
let messageTimer = null
let lastRuntimeStatusSignature = ''
let lastRuntimePreviewSignature = ''

onMounted(async () => {
  chart = echarts.init(chartRef.value)
  await loadScenarios()
  await refreshRuntime(false)
  if (runtimeStatus.value.running && runtimeStatus.value.scenarioId) {
    await selectScenario(runtimeStatus.value.scenarioId)
  }
  renderChart()
  refreshTimer = window.setInterval(() => {
    if (runtimeStatus.value.running) {
      refreshRuntime(false)
    }
  }, 3000)
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  if (messageTimer) {
    clearTimeout(messageTimer)
  }
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

function resizeChart() {
  chart?.resize()
}

function createEmptyScenario() {
  return {
    scenarioId: null,
    name: '',
    description: '',
    status: 'draft',
    droneCount: 7,
    publishIntervalMs: 1000,
    areaCenterLat: 31.23,
    areaCenterLon: 121.47,
    areaRadiusM: 800,
    batteryMin: 70,
    batteryMax: 100,
    rssiMin: -90,
    rssiMax: -45,
    altMin: 100,
    altMax: 220,
    topologyMode: 'chain',
    motionMode: 'random-walk',
    drones: [],
    links: [],
  }
}

function createEmptyDrone(droneNo = 1, lat = 31.23, lon = 121.47) {
  return {
    droneNo,
    modelId: 1,
    serialNo: `SIM-${String(droneNo).padStart(4, '0')}`,
    initialLat: Number(lat.toFixed(6)),
    initialLon: Number(lon.toFixed(6)),
    initialAlt: 120,
    initialBatteryPct: 90,
    initialRssi: -65,
    roleTag: droneNo === 1 ? 'gateway' : 'relay',
  }
}

function createEmptyLink(srcDroneNo = 1, dstDroneNo = 2) {
  return {
    srcDroneNo,
    dstDroneNo,
    initialQuality: 80,
    isEnabled: true,
  }
}

function resetForm() {
  Object.assign(form, createEmptyScenario())
}

function applyScenarioToForm(data) {
  Object.assign(form, createEmptyScenario(), {
    scenarioId: data.scenarioId ?? null,
    name: data.name ?? '',
    description: data.description ?? '',
    status: data.status ?? 'draft',
    droneCount: data.droneCount ?? 6,
    publishIntervalMs: data.publishIntervalMs ?? 1000,
    areaCenterLat: data.areaCenterLat ?? 31.23,
    areaCenterLon: data.areaCenterLon ?? 121.47,
    areaRadiusM: data.areaRadiusM ?? 800,
    batteryMin: data.batteryMin ?? 70,
    batteryMax: data.batteryMax ?? 100,
    rssiMin: data.rssiMin ?? -90,
    rssiMax: data.rssiMax ?? -45,
    altMin: data.altMin ?? 100,
    altMax: data.altMax ?? 220,
    topologyMode: data.topologyMode ?? 'chain',
    motionMode: data.motionMode ?? 'random-walk',
    drones: (data.drones || []).map(drone => ({
      droneNo: drone.droneNo ?? 1,
      modelId: drone.modelId ?? 1,
      serialNo: drone.serialNo ?? '',
      initialLat: drone.initialLat ?? form.areaCenterLat,
      initialLon: drone.initialLon ?? form.areaCenterLon,
      initialAlt: drone.initialAlt ?? 120,
      initialBatteryPct: drone.initialBatteryPct ?? 90,
      initialRssi: drone.initialRssi ?? -65,
      roleTag: drone.roleTag ?? '',
    })),
    links: (data.links || []).map(link => ({
      srcDroneNo: link.srcDroneNo ?? 1,
      dstDroneNo: link.dstDroneNo >= 0 ? link.dstDroneNo : null,
      initialQuality: link.initialQuality ?? 80,
      isEnabled: link.isEnabled ?? true,
    })),
  })
  renderChart()
}

function showMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
  if (messageTimer) {
    clearTimeout(messageTimer)
  }
  messageTimer = window.setTimeout(() => {
    if (message.value === text) {
      message.value = ''
    }
  }, 3000)
}

async function loadScenarios() {
  loading.value = true
  try {
    const { data } = await api.get('/simulation/scenarios')
    scenarios.value = data
    if (selectedScenarioId.value) {
      const exists = scenarios.value.some(item => item.scenarioId === selectedScenarioId.value)
      if (!exists) {
        selectedScenarioId.value = null
      }
    }
    if (!selectedScenarioId.value && scenarios.value.length > 0) {
      await selectScenario(scenarios.value[0].scenarioId)
    } else if (!selectedScenarioId.value) {
      resetForm()
      renderChart()
    }
  } catch (error) {
    showMessage(error.response?.data?.error || '场景列表加载失败', 'error')
  }
  loading.value = false
}

async function selectScenario(scenarioId) {
  selectedScenarioId.value = scenarioId
  try {
    const { data } = await api.get(`/simulation/scenarios/${scenarioId}`)
    applyScenarioToForm(data)
  } catch (error) {
    showMessage(error.response?.data?.error || '场景详情加载失败', 'error')
  }
}

function createNewScenario() {
  selectedScenarioId.value = null
  resetForm()
  renderChart()
}

function sanitizeNumber(value, fallback = null) {
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

function buildPayload() {
  return {
    name: form.name?.trim(),
    description: form.description?.trim() || null,
    status: form.status,
    droneCount: sanitizeNumber(form.droneCount, 1),
    publishIntervalMs: sanitizeNumber(form.publishIntervalMs, 1000),
    areaCenterLat: sanitizeNumber(form.areaCenterLat, 31.23),
    areaCenterLon: sanitizeNumber(form.areaCenterLon, 121.47),
    areaRadiusM: sanitizeNumber(form.areaRadiusM, 800),
    batteryMin: sanitizeNumber(form.batteryMin, 70),
    batteryMax: sanitizeNumber(form.batteryMax, 100),
    rssiMin: sanitizeNumber(form.rssiMin, -90),
    rssiMax: sanitizeNumber(form.rssiMax, -45),
    altMin: sanitizeNumber(form.altMin, 100),
    altMax: sanitizeNumber(form.altMax, 220),
    topologyMode: form.topologyMode,
    motionMode: form.motionMode,
    drones: form.drones
      .filter(drone => sanitizeNumber(drone.droneNo, 0) > 0)
      .map(drone => ({
        droneNo: sanitizeNumber(drone.droneNo, 1),
        modelId: sanitizeNumber(drone.modelId, 1),
        serialNo: drone.serialNo?.trim() || null,
        initialLat: sanitizeNumber(drone.initialLat, null),
        initialLon: sanitizeNumber(drone.initialLon, null),
        initialAlt: sanitizeNumber(drone.initialAlt, null),
        initialBatteryPct: sanitizeNumber(drone.initialBatteryPct, null),
        initialRssi: sanitizeNumber(drone.initialRssi, null),
        roleTag: drone.roleTag?.trim() || null,
      })),
    links: form.links
      .filter(link => {
        const src = sanitizeNumber(link.srcDroneNo, 0)
        const dst = sanitizeNumber(link.dstDroneNo, 0)
        return src > 0 && dst >= 0 && src !== dst
      })
      .map(link => ({
        srcDroneNo: sanitizeNumber(link.srcDroneNo, 1),
        dstDroneNo: sanitizeNumber(link.dstDroneNo, 0),
        initialQuality: sanitizeNumber(link.initialQuality, 80),
        isEnabled: Boolean(link.isEnabled),
      })),
  }
}

function pruneOutOfRangeChildren(showNotice = false) {
  const droneCount = Math.max(1, sanitizeNumber(form.droneCount, 1))
  const beforeDroneCount = form.drones.length
  const beforeLinkCount = form.links.length

  form.drones = form.drones.filter(drone => {
    const droneNo = sanitizeNumber(drone.droneNo, 0)
    return droneNo > 0 && droneNo <= droneCount
  })

  form.links = form.links.filter(link => {
    const src = sanitizeNumber(link.srcDroneNo, 0)
    const dst = sanitizeNumber(link.dstDroneNo, -1)
    const srcValid = src > 0 && src <= droneCount
    const dstValid = dst >= 0 && dst <= droneCount
    return srcValid && dstValid && src !== dst
  })

  const removedDrones = beforeDroneCount - form.drones.length
  const removedLinks = beforeLinkCount - form.links.length

  if (showNotice && (removedDrones > 0 || removedLinks > 0)) {
    showMessage(`已自动清理超范围配置：删除 ${removedDrones} 个节点、${removedLinks} 条链路`, 'info')
  }
}

function onDroneCountChange() {
  pruneOutOfRangeChildren(true)
  renderChart()
}

async function saveScenario() {
  saving.value = true
  try {
    pruneOutOfRangeChildren(false)
    const payload = buildPayload()
    if (form.scenarioId) {
      const { data } = await api.put(`/simulation/scenarios/${form.scenarioId}`, payload)
      showMessage(data.message || '场景已更新', 'success')
      await loadScenarios()
      await selectScenario(form.scenarioId)
    } else {
      const { data } = await api.post('/simulation/scenarios', payload)
      showMessage(data.message || '场景已创建', 'success')
      await loadScenarios()
      if (data.scenarioId) {
        await selectScenario(data.scenarioId)
      }
    }
  } catch (error) {
    showMessage(error.response?.data?.error || '保存失败', 'error')
  }
  saving.value = false
}

async function saveScenarioSection(sectionName) {
  const activeScenarioId = runtimeStatus.value.scenarioId
  const isCurrentRuntimeScenario = runtimeStatus.value.running
    && activeScenarioId
    && Number(activeScenarioId) === Number(form.scenarioId || selectedScenarioId.value)

  await saveScenario()

  if (!isCurrentRuntimeScenario || !form.scenarioId) {
    return
  }

  runtimeBusy.value = true
  try {
    const { data } = await api.post('/simulation/runtime/start', {
      scenarioId: form.scenarioId,
      overrides: {
        droneCount: sanitizeNumber(form.droneCount, 1),
        publishIntervalMs: sanitizeNumber(form.publishIntervalMs, 1000),
        motionMode: form.motionMode,
      },
    })
    showMessage(data.message || `${sectionName}已保存并同步到运行中的模拟`, 'success')
    await refreshRuntime(false)
  } catch (error) {
    showMessage(error.response?.data?.error || `${sectionName}已保存，但运行态同步失败`, 'error')
  }
  runtimeBusy.value = false
}

async function saveDroneSection() {
  await saveScenarioSection('节点配置')
}

async function saveLinkSection() {
  form.topologyMode = 'custom'
  await saveScenarioSection('链路配置')
}

async function removeScenario() {
  if (!form.scenarioId) {
    showMessage('当前是未保存的新场景', 'info')
    return
  }
  if (!window.confirm(`确认删除场景“${form.name}”吗？`)) {
    return
  }
  deleting.value = true
  try {
    const { data } = await api.delete(`/simulation/scenarios/${form.scenarioId}`)
    showMessage(data.message || '场景已删除', 'success')
    selectedScenarioId.value = null
    resetForm()
    await loadScenarios()
  } catch (error) {
    showMessage(error.response?.data?.error || '删除失败', 'error')
  }
  deleting.value = false
}

async function refreshRuntime(showError = true) {
  try {
    const [statusRes, previewRes] = await Promise.all([
      api.get('/simulation/runtime/status'),
      api.get('/simulation/runtime/preview'),
    ])
    const nextStatus = statusRes.data || { running: false }
    const nextPreview = previewRes.data || { nodes: [], edges: [], metrics: { running: false } }
    const nextStatusSignature = JSON.stringify(nextStatus)
    const nextPreviewSignature = JSON.stringify(nextPreview)
    const changed = nextStatusSignature !== lastRuntimeStatusSignature
      || nextPreviewSignature !== lastRuntimePreviewSignature

    runtimeStatus.value = nextStatus
    runtimePreview.value = nextPreview
    lastRuntimeStatusSignature = nextStatusSignature
    lastRuntimePreviewSignature = nextPreviewSignature

    if (nextStatus.running && nextStatus.scenarioId && selectedScenarioId.value !== nextStatus.scenarioId) {
      selectedScenarioId.value = nextStatus.scenarioId
    }

    if (changed) {
      renderChart()
    }
  } catch (error) {
    if (showError) {
      showMessage(error.response?.data?.error || '运行状态加载失败', 'error')
    }
  }
}

async function startRuntime() {
  const scenarioId = form.scenarioId || selectedScenarioId.value
  if (!scenarioId) {
    showMessage('请先保存场景，再启动仿真', 'info')
    return
  }
  runtimeBusy.value = true
  try {
    const { data } = await api.post('/simulation/runtime/start', {
      scenarioId,
      overrides: {
        droneCount: sanitizeNumber(form.droneCount, 1),
        publishIntervalMs: sanitizeNumber(form.publishIntervalMs, 1000),
        motionMode: form.motionMode,
      },
    })
    showMessage(data.message || '仿真已启动', 'success')
    await refreshRuntime()
  } catch (error) {
    showMessage(error.response?.data?.error || '启动失败', 'error')
  }
  runtimeBusy.value = false
}

async function stopRuntime() {
  runtimeBusy.value = true
  try {
    const { data } = await api.post('/simulation/runtime/stop')
    showMessage(data.message || '仿真已停止', 'success')
    await refreshRuntime()
  } catch (error) {
    showMessage(error.response?.data?.error || '停止失败', 'error')
  }
  runtimeBusy.value = false
}

async function applyRuntime() {
  if (!runtimeStatus.value.running) {
    showMessage('当前没有正在运行的仿真会话', 'info')
    return
  }
  runtimeBusy.value = true
  try {
    const { data } = await api.post('/simulation/runtime/apply', {
      publishIntervalMs: sanitizeNumber(form.publishIntervalMs, 1000),
      motionMode: form.motionMode,
      droneCount: sanitizeNumber(form.droneCount, 1),
    })
    showMessage(data.message || '运行参数已应用', 'success')
    await refreshRuntime()
  } catch (error) {
    showMessage(error.response?.data?.error || '参数应用失败', 'error')
  }
  runtimeBusy.value = false
}

function addDrone() {
  const nextDroneNo = form.drones.length
    ? Math.max(...form.drones.map(drone => sanitizeNumber(drone.droneNo, 0))) + 1
    : 1
  form.drones.push(createEmptyDrone(nextDroneNo, form.areaCenterLat, form.areaCenterLon))
  form.droneCount = Math.max(form.droneCount, form.drones.length)
  renderChart()
}

function removeDrone(index) {
  form.drones.splice(index, 1)
  renderChart()
}

function generateDroneOverrides() {
  const count = Math.max(1, sanitizeNumber(form.droneCount, 1))
  const radius = Math.max(80, sanitizeNumber(form.areaRadiusM, 800) / 3)
  const centerLat = sanitizeNumber(form.areaCenterLat, 31.23)
  const centerLon = sanitizeNumber(form.areaCenterLon, 121.47)
  const batterySpan = Math.max(0, sanitizeNumber(form.batteryMax, 100) - sanitizeNumber(form.batteryMin, 70))
  const altSpan = Math.max(0, sanitizeNumber(form.altMax, 220) - sanitizeNumber(form.altMin, 100))
  const rssiSpan = Math.max(0, sanitizeNumber(form.rssiMax, -45) - sanitizeNumber(form.rssiMin, -90))

  form.drones = Array.from({ length: count }, (_, index) => {
    const droneNo = index + 1
    const angle = (Math.PI * 2 * index) / count
    const latOffset = (Math.cos(angle) * radius) / 111111
    const lonOffset = (Math.sin(angle) * radius) / (111111 * Math.cos((centerLat * Math.PI) / 180))
    return {
      droneNo,
      modelId: 1,
      serialNo: `SIM-${String(droneNo).padStart(4, '0')}`,
      initialLat: Number((centerLat + latOffset).toFixed(6)),
      initialLon: Number((centerLon + lonOffset).toFixed(6)),
      initialAlt: Number((sanitizeNumber(form.altMin, 100) + (altSpan * ((index % 5) / 4 || 0))).toFixed(1)),
      initialBatteryPct: Number((sanitizeNumber(form.batteryMax, 100) - (batterySpan * (index / Math.max(1, count - 1)))).toFixed(1)),
      initialRssi: Math.round(sanitizeNumber(form.rssiMin, -90) + (rssiSpan * (index / Math.max(1, count - 1)))),
      roleTag: index === 0 ? 'gateway' : index % 3 === 0 ? 'scout' : 'relay',
    }
  })
  renderChart()
}

function addLink() {
  const count = Math.max(1, sanitizeNumber(form.droneCount, 1))
  if (count < 2) {
    showMessage('至少需要 2 个节点才能配置链路', 'info')
    return
  }
  form.links.push(createEmptyLink(1, 2))
  renderChart()
}

function removeLink(index) {
  form.links.splice(index, 1)
  renderChart()
}

function generateLinksFromTopology() {
  const count = Math.max(1, sanitizeNumber(form.droneCount, 1))
  const links = []

  if (form.topologyMode === 'chain') {
    for (let droneNo = 2; droneNo <= count; droneNo += 1) {
      links.push(createEmptyLink(droneNo, droneNo - 1))
    }
  } else if (form.topologyMode === 'star') {
    for (let droneNo = 2; droneNo <= count; droneNo += 1) {
      links.push(createEmptyLink(droneNo, 1))
    }
  } else if (form.topologyMode === 'mesh') {
    for (let droneNo = 1; droneNo <= count; droneNo += 1) {
      for (let peer = droneNo + 1; peer <= Math.min(count, droneNo + 2); peer += 1) {
        links.push(createEmptyLink(droneNo, peer))
      }
    }
  } else {
    showMessage('当前为自定义拓扑，保留现有链路配置', 'info')
    return
  }

  form.links = links
  renderChart()
}

function availableTargetNodes(link) {
  const count = Math.max(1, sanitizeNumber(form.droneCount, 1))
  const source = sanitizeNumber(link.srcDroneNo, 0)
  return Array.from({ length: count }, (_, index) => index + 1)
    .filter(droneNo => droneNo !== source)
}

function buildDraftPreview() {
  const centerLat = sanitizeNumber(form.areaCenterLat, 31.23)
  const centerLon = sanitizeNumber(form.areaCenterLon, 121.47)
  const drones = form.drones.length > 0
    ? form.drones
    : Array.from({ length: Math.max(1, sanitizeNumber(form.droneCount, 1)) }, (_, index) => {
        const angle = (Math.PI * 2 * index) / Math.max(1, form.droneCount)
        return createEmptyDrone(
          index + 1,
          centerLat + (Math.cos(angle) * 0.004),
          centerLon + (Math.sin(angle) * 0.004),
        )
      })

  const hasGroundStation = form.links.some(link => sanitizeNumber(link.dstDroneNo, -1) === 0)
  const nodes = [
    ...(hasGroundStation ? [{
      droneNo: 0,
      name: '地面站',
      lat: centerLat,
      lon: centerLon,
      alt: 0,
      batteryPct: 100,
      rssi: 0,
    }] : []),
    ...drones.map(drone => ({
      droneNo: sanitizeNumber(drone.droneNo, 1),
      name: drone.serialNo || `SN-${String(drone.droneNo).padStart(4, '0')}`,
      lat: sanitizeNumber(drone.initialLat, centerLat),
      lon: sanitizeNumber(drone.initialLon, centerLon),
      alt: sanitizeNumber(drone.initialAlt, 120),
      batteryPct: sanitizeNumber(drone.initialBatteryPct, 90),
      rssi: sanitizeNumber(drone.initialRssi, -65),
    })),
  ]

  const edges = (form.links.length > 0 ? form.links : []).map(link => ({
    source: sanitizeNumber(link.srcDroneNo, 1),
    target: sanitizeNumber(link.dstDroneNo, 0),
    quality: sanitizeNumber(link.initialQuality, 80),
    active: link.isEnabled !== false,
  }))

  return {
    nodes,
    edges,
    metrics: {
      running: false,
      draft: true,
    },
  }
}

function renderChart() {
  if (!chart) {
    return
  }

  const preview = activePreview.value
  const nodes = (preview.nodes || []).map(node => ({
    id: String(node.droneNo),
    name: node.name,
    symbolSize: node.droneNo === 0 ? 42 : 28,
    itemStyle: {
      color: node.droneNo === 0
        ? '#0b57d0'
        : node.batteryPct >= 60
          ? '#1f8f4f'
          : node.batteryPct >= 30
            ? '#ca8a04'
            : '#d93025',
    },
    value: [
      node.lat,
      node.lon,
      node.alt,
      node.batteryPct,
      node.rssi,
    ],
  }))

  const links = (preview.edges || []).map(edge => ({
    source: String(edge.source),
    target: String(edge.target),
    lineStyle: {
      width: Math.max(1, sanitizeNumber(edge.quality, 60) / 25),
      color: edge.active ? '#334155' : '#94a3b8',
      opacity: edge.active ? 0.85 : 0.35,
    },
  }))

  chart.setOption({
    backgroundColor: 'transparent',
    animationDuration: 500,
    tooltip: {
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: { color: '#f8fafc' },
      formatter: params => {
        if (params.dataType === 'node') {
          const [lat, lon, alt, batteryPct, rssi] = params.data.value || []
          return `${params.data.name}<br/>电量: ${batteryPct}%<br/>RSSI: ${rssi}<br/>位置: ${lat}, ${lon}<br/>高度: ${alt}m`
        }
        return `${params.data.source} -> ${params.data.target}`
      },
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: {
        repulsion: 240,
        edgeLength: [90, 220],
        gravity: 0.08,
      },
      label: {
        show: true,
        color: '#dbe4ee',
        fontSize: 11,
      },
      lineStyle: {
        curveness: 0.12,
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 4,
          color: '#0b57d0',
        },
      },
      data: nodes,
      links,
    }],
  }, {
    notMerge: false,
    lazyUpdate: true,
  })
}

const statusOptions = [
  { value: 'draft', label: '草稿' },
  { value: 'ready', label: '可运行' },
  { value: 'archived', label: '已归档' },
]

const topologyOptions = [
  { value: 'chain', label: '链式' },
  { value: 'star', label: '星型' },
  { value: 'mesh', label: '网状' },
  { value: 'custom', label: '自定义' },
]

const motionOptions = [
  { value: 'random-walk', label: '随机游走' },
  { value: 'patrol', label: '巡航' },
  { value: 'orbit', label: '环绕' },
  { value: 'hover', label: '悬停' },
]
</script>

<template>
  <div class="simulation-page p-6 space-y-6">
    <div class="simulation-toolbar flex items-center justify-between gap-4">
      <div>
        <h1 class="text-lg font-semibold">无人机群模拟</h1>
        <p class="text-xs text-ink-subtle mt-1">
          把原来的脚本模拟器升级成可视化工作台：既能做场景 CRUD，也能直接控制仿真和预览拓扑。
        </p>
      </div>
      <button
        @click="createNewScenario"
        class="h-9 px-4 bg-primary text-white text-sm rounded-md hover:bg-primary-hover transition-colors cursor-pointer"
      >
        新建场景
      </button>
    </div>

    <div
      v-if="message"
      :class="[
        'px-4 py-3 rounded-md border text-sm',
        messageType === 'success' ? 'bg-success/10 border-success/20 text-success' :
        messageType === 'error' ? 'bg-danger/10 border-danger/20 text-danger' :
        'bg-info/10 border-info/20 text-info'
      ]"
    >
      {{ message }}
    </div>

    <div class="grid gap-6 xl:grid-cols-[320px,1fr,340px]">
      <section class="simulation-card bg-surface-1 border border-hairline rounded-lg overflow-hidden">
        <div class="px-4 py-3 border-b border-hairline flex items-center justify-between">
          <h2 class="text-sm font-medium text-ink-muted">场景列表</h2>
          <span class="text-xs text-ink-subtle">{{ scenarios.length }} 个</span>
        </div>
        <div v-if="loading" class="p-4 text-sm text-ink-subtle">加载中...</div>
        <div v-else-if="!scenarios.length" class="p-4 text-sm text-ink-subtle">暂无场景，先创建一个吧。</div>
        <div v-else class="divide-y divide-hairline">
          <button
            v-for="item in scenarios"
            :key="item.scenarioId"
            @click="selectScenario(item.scenarioId)"
            :class="[
              'w-full text-left px-4 py-3 transition-colors cursor-pointer',
              selectedScenarioId === item.scenarioId ? 'bg-primary/8' : 'hover:bg-surface-2'
            ]"
          >
            <div class="flex items-center justify-between gap-2">
              <p class="text-sm font-medium text-ink">{{ item.name }}</p>
              <span class="text-[11px] px-1.5 py-0.5 rounded bg-surface-2 text-ink-subtle">
                {{ item.status }}
              </span>
            </div>
            <div class="mt-1 flex flex-wrap gap-2 text-xs text-ink-subtle">
              <span>{{ item.droneCount }} 架</span>
              <span>{{ item.topologyMode }}</span>
              <span>{{ item.motionMode }}</span>
            </div>
          </button>
        </div>
      </section>

      <section class="space-y-6">
        <div class="simulation-card bg-surface-1 border border-hairline rounded-lg">
          <div class="px-4 py-3 border-b border-hairline">
            <h2 class="text-sm font-medium text-ink-muted">场景编辑</h2>
            <p class="text-xs text-ink-subtle mt-1">
              这里补齐了课程设计需要的基础增删改查，还能把无人机节点和链路一起持久化。
            </p>
          </div>

          <div class="p-4 grid gap-4 md:grid-cols-2">
            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">场景名称</span>
              <input v-model="form.name" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">状态</span>
              <select v-model="form.status" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm">
                <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>

            <label class="space-y-1 md:col-span-2">
              <span class="text-xs text-ink-subtle">描述</span>
              <textarea v-model="form.description" rows="3" class="w-full px-3 py-2 bg-surface-2 border border-hairline rounded-md text-sm resize-none" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">无人机数量</span>
              <input v-model.number="form.droneCount" @change="onDroneCountChange" type="number" min="1" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">发布间隔(ms)</span>
              <input v-model.number="form.publishIntervalMs" type="number" min="100" step="100" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">中心纬度</span>
              <input v-model.number="form.areaCenterLat" type="number" step="0.000001" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">中心经度</span>
              <input v-model.number="form.areaCenterLon" type="number" step="0.000001" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">场景半径(m)</span>
              <input v-model.number="form.areaRadiusM" type="number" min="1" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">拓扑模式</span>
              <select v-model="form.topologyMode" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm">
                <option v-for="item in topologyOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">运动模式</span>
              <select v-model="form.motionMode" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm">
                <option v-for="item in motionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">最低电量(%)</span>
              <input v-model.number="form.batteryMin" type="number" min="0" max="100" step="0.1" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">最高电量(%)</span>
              <input v-model.number="form.batteryMax" type="number" min="0" max="100" step="0.1" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">最低 RSSI</span>
              <input v-model.number="form.rssiMin" type="number" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">最高 RSSI</span>
              <input v-model.number="form.rssiMax" type="number" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">最低高度(m)</span>
              <input v-model.number="form.altMin" type="number" min="0" step="0.1" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>

            <label class="space-y-1">
              <span class="text-xs text-ink-subtle">最高高度(m)</span>
              <input v-model.number="form.altMax" type="number" min="0" step="0.1" class="w-full h-9 px-3 bg-surface-2 border border-hairline rounded-md text-sm" />
            </label>
          </div>

          <div class="simulation-toolbar px-4 pb-4 flex gap-3 flex-wrap">
            <button
              @click="saveScenario"
              :disabled="saving"
              class="h-9 px-4 bg-primary text-white text-sm rounded-md hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
            >
              {{ saving ? '保存中...' : form.scenarioId ? '更新场景' : '创建场景' }}
            </button>
            <button
              @click="removeScenario"
              :disabled="deleting || !form.scenarioId"
              class="h-9 px-4 bg-danger/10 text-danger text-sm rounded-md hover:bg-danger/20 disabled:opacity-50 transition-colors cursor-pointer"
            >
              {{ deleting ? '删除中...' : '删除场景' }}
            </button>
            <button
              @click="createNewScenario"
              class="h-9 px-4 bg-surface-2 text-ink-muted text-sm rounded-md hover:bg-surface-3 transition-colors cursor-pointer"
            >
              重置表单
            </button>
          </div>
        </div>

        <div class="grid gap-6 xl:grid-cols-2">
          <div class="simulation-card bg-surface-1 border border-hairline rounded-lg">
            <div class="px-4 py-3 border-b border-hairline flex items-center justify-between">
              <div>
                <h2 class="text-sm font-medium text-ink-muted">无人机节点</h2>
                <p class="text-xs text-ink-subtle mt-1">支持自定义每架无人机的编号、位置、电量、RSSI 和角色标签。</p>
              </div>
              <div class="flex gap-2">
                <button @click="generateDroneOverrides" class="h-8 px-3 bg-surface-2 text-xs rounded-md hover:bg-surface-3 transition-colors cursor-pointer">
                  自动生成
                </button>
                <button
                  @click="saveDroneSection"
                  :disabled="saving || runtimeBusy"
                  class="h-8 px-3 bg-primary text-white text-xs rounded-md hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
                >
                  {{ saving ? '保存中...' : '保存节点' }}
                </button>
                <button @click="addDrone" class="h-8 px-3 bg-primary/10 text-primary text-xs rounded-md hover:bg-primary/15 transition-colors cursor-pointer">
                  新增节点
                </button>
              </div>
            </div>
            <div class="p-4 space-y-3 max-h-[420px] overflow-auto">
              <div v-if="!form.drones.length" class="text-sm text-ink-subtle">
                还没有节点覆盖配置。可以先点“自动生成”，再按需要微调。
              </div>
              <div v-for="(drone, index) in form.drones" :key="`${drone.droneNo}-${index}`" class="rounded-lg border border-hairline bg-surface-2 p-3 space-y-3">
                <div class="flex items-center justify-between">
                  <p class="text-sm font-medium text-ink">节点 {{ index + 1 }}</p>
                  <button @click="removeDrone(index)" class="text-xs text-danger hover:text-danger/80 transition-colors cursor-pointer">
                    删除
                  </button>
                </div>
                <div class="grid gap-3 md:grid-cols-2">
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">编号</span>
                    <input v-model.number="drone.droneNo" type="number" min="1" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">型号 ID</span>
                    <input v-model.number="drone.modelId" type="number" min="1" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1 md:col-span-2">
                    <span class="text-xs text-ink-subtle">序列号</span>
                    <input v-model="drone.serialNo" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">纬度</span>
                    <input v-model.number="drone.initialLat" type="number" step="0.000001" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">经度</span>
                    <input v-model.number="drone.initialLon" type="number" step="0.000001" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">高度(m)</span>
                    <input v-model.number="drone.initialAlt" type="number" step="0.1" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">电量(%)</span>
                    <input v-model.number="drone.initialBatteryPct" type="number" min="0" max="100" step="0.1" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">RSSI</span>
                    <input v-model.number="drone.initialRssi" type="number" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">角色标签</span>
                    <input v-model="drone.roleTag" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                </div>
              </div>
            </div>
            <div class="px-4 pb-4 text-xs text-ink-subtle">
              已配置 {{ droneOverrideCount }} 个节点覆盖项，保存后会一起写入 PostgreSQL。
            </div>
          </div>

          <div class="simulation-card bg-surface-1 border border-hairline rounded-lg">
            <div class="px-4 py-3 border-b border-hairline flex items-center justify-between">
              <div>
                <h2 class="text-sm font-medium text-ink-muted">链路配置</h2>
                <p class="text-xs text-ink-subtle mt-1">可快速按拓扑自动生成，也能逐条修改链路质量和启用状态。</p>
              </div>
              <div class="flex gap-2">
                <button @click="generateLinksFromTopology" class="h-8 px-3 bg-surface-2 text-xs rounded-md hover:bg-surface-3 transition-colors cursor-pointer">
                  按拓扑生成
                </button>
                <button
                  @click="saveLinkSection"
                  :disabled="saving || runtimeBusy"
                  class="h-8 px-3 bg-primary text-white text-xs rounded-md hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
                >
                  {{ saving ? '保存中...' : '保存链路' }}
                </button>
                <button @click="addLink" class="h-8 px-3 bg-primary/10 text-primary text-xs rounded-md hover:bg-primary/15 transition-colors cursor-pointer">
                  新增链路
                </button>
              </div>
            </div>
            <div class="p-4 space-y-3 max-h-[420px] overflow-auto">
              <div v-if="!form.links.length" class="text-sm text-ink-subtle">
                还没有链路配置。链式、星型、网状场景可以一键生成默认链路。
              </div>
              <div v-for="(link, index) in form.links" :key="`${link.srcDroneNo}-${link.dstDroneNo}-${index}`" class="rounded-lg border border-hairline bg-surface-2 p-3 space-y-3">
                <div class="flex items-center justify-between">
                  <p class="text-sm font-medium text-ink">链路 {{ index + 1 }}</p>
                  <button @click="removeLink(index)" class="text-xs text-danger hover:text-danger/80 transition-colors cursor-pointer">
                    删除
                  </button>
                </div>
                <div class="grid gap-3 md:grid-cols-2">
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">源节点</span>
                    <input v-model.number="link.srcDroneNo" type="number" min="1" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">目标节点</span>
                    <select v-model.number="link.dstDroneNo" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm">
                      <option :value="0">地面站</option>
                      <option v-for="droneNo in availableTargetNodes(link)" :key="droneNo" :value="droneNo">
                        节点 {{ droneNo }}
                      </option>
                    </select>
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">初始质量</span>
                    <input v-model.number="link.initialQuality" type="number" min="0" max="100" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-xs text-ink-subtle">启用状态</span>
                    <select v-model="link.isEnabled" class="w-full h-9 px-3 rounded-md border border-hairline bg-white/70 text-sm">
                      <option :value="true">启用</option>
                      <option :value="false">禁用</option>
                    </select>
                  </label>
                </div>
              </div>
            </div>
            <div class="px-4 pb-4 text-xs text-ink-subtle">
              已配置 {{ linkOverrideCount }} 条链路，目标节点可选择已生成的无人机节点或地面站。
            </div>
          </div>
        </div>

        <div class="simulation-card bg-surface-1 border border-hairline rounded-lg p-4">
          <div class="simulation-toolbar flex items-center justify-between">
            <div>
              <h2 class="text-sm font-medium text-ink-muted">可视化预览</h2>
              <p class="text-xs text-ink-subtle mt-1">
                运行仿真时显示实时网络，未运行时显示当前表单草稿预览，方便先调场景再启动。
              </p>
            </div>
            <button
              @click="refreshRuntime"
              class="text-xs text-primary hover:text-primary-hover transition-colors cursor-pointer"
            >
              刷新
            </button>
          </div>
          <div ref="chartRef" class="simulation-chart mt-4 h-[380px] w-full"></div>
          <div class="mt-3 flex flex-wrap gap-4 text-xs text-ink-subtle">
            <span>节点 {{ activePreview.nodes?.length || 0 }}</span>
            <span>链路 {{ activePreview.edges?.length || 0 }}</span>
            <span>{{ runtimeStatus.running ? '实时仿真预览' : '场景草稿预览' }}</span>
          </div>
        </div>
      </section>

      <section class="space-y-4">
        <div class="simulation-card bg-surface-1 border border-hairline rounded-lg p-4">
          <div class="simulation-toolbar flex items-center justify-between">
            <h2 class="text-sm font-medium text-ink-muted">运行控制</h2>
            <button
              @click="refreshRuntime"
              class="text-xs text-primary hover:text-primary-hover transition-colors cursor-pointer"
            >
              刷新
            </button>
          </div>
          <div class="mt-3 space-y-3">
            <div class="flex items-center gap-2">
              <span :class="[
                'inline-flex h-2.5 w-2.5 rounded-full',
                runtimeStatus.running ? 'bg-success' : 'bg-ink-subtle'
              ]"></span>
              <span class="text-sm text-ink">{{ runtimeStatus.running ? '仿真运行中' : '当前未运行' }}</span>
            </div>
            <div class="grid grid-cols-2 gap-2 text-xs">
              <div class="bg-surface-2 rounded p-2">
                <p class="text-ink-subtle">场景</p>
                <p class="text-ink font-medium">{{ runtimeStatus.scenarioName || '—' }}</p>
              </div>
              <div class="bg-surface-2 rounded p-2">
                <p class="text-ink-subtle">Tick</p>
                <p class="text-ink font-medium">{{ runtimeStatus.tickCount ?? 0 }}</p>
              </div>
              <div class="bg-surface-2 rounded p-2">
                <p class="text-ink-subtle">平均电量</p>
                <p class="text-ink font-medium">{{ runtimeStatus.avgBattery ?? '—' }}</p>
              </div>
              <div class="bg-surface-2 rounded p-2">
                <p class="text-ink-subtle">活跃链路</p>
                <p class="text-ink font-medium">{{ runtimeStatus.activeLinks ?? 0 }}</p>
              </div>
            </div>
            <div class="flex gap-2">
              <button
                @click="startRuntime"
                :disabled="runtimeBusy || runtimeStatus.running"
                class="flex-1 h-9 px-3 bg-primary text-white text-sm rounded-md hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
              >
                启动
              </button>
              <button
                @click="stopRuntime"
                :disabled="runtimeBusy || !runtimeStatus.running"
                class="flex-1 h-9 px-3 bg-danger/10 text-danger text-sm rounded-md hover:bg-danger/20 disabled:opacity-50 transition-colors cursor-pointer"
              >
                停止
              </button>
            </div>
            <button
              @click="applyRuntime"
              :disabled="runtimeBusy || !runtimeStatus.running"
              class="w-full h-9 px-3 bg-surface-2 text-ink text-sm rounded-md hover:bg-surface-3 disabled:opacity-50 transition-colors cursor-pointer"
            >
              应用当前参数到运行会话
            </button>
          </div>
        </div>

        <div class="simulation-card bg-surface-1 border border-hairline rounded-lg p-4">
          <h2 class="text-sm font-medium text-ink-muted">当前选中</h2>
          <div v-if="selectedScenario" class="mt-3 space-y-2 text-sm">
            <div class="flex items-center justify-between">
              <span class="text-ink-subtle">名称</span>
              <span class="text-ink">{{ selectedScenario.name }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-subtle">状态</span>
              <span class="text-ink">{{ selectedScenario.status }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-subtle">无人机数量</span>
              <span class="text-ink">{{ selectedScenario.droneCount }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-subtle">拓扑</span>
              <span class="text-ink">{{ selectedScenario.topologyMode }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-subtle">运动</span>
              <span class="text-ink">{{ selectedScenario.motionMode }}</span>
            </div>
          </div>
          <p v-else class="mt-3 text-sm text-ink-subtle">当前没有选中场景。</p>
        </div>

        <div class="simulation-card bg-surface-1 border border-hairline rounded-lg p-4">
          <h2 class="text-sm font-medium text-ink-muted">课程设计亮点</h2>
          <ul class="mt-3 space-y-2 text-sm text-ink-subtle">
            <li>前端直接完成场景级和子项级 CRUD，补齐基础数据库功能展示。</li>
            <li>一页同时展示“配置、运行、可视化”，演示时不用再切多个终端。</li>
            <li>支持可控参数和自定义链路，能快速构造不同自组网场景。</li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.simulation-page {
  position: relative;
  isolation: isolate;
}

.simulation-card {
  position: relative;
  z-index: 1;
}

.simulation-toolbar {
  position: relative;
  z-index: 20;
}

.simulation-chart {
  position: relative;
  z-index: 0;
  overflow: hidden;
}

.simulation-chart :deep(canvas) {
  pointer-events: none;
}

.simulation-page select {
  background-color: #1e293b;
  color: #f8fafc;
  color-scheme: dark;
}

.simulation-page select option {
  background-color: #1e293b;
  color: #f8fafc;
}
</style>
