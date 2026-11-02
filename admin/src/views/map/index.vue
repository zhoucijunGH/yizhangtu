<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, ArrowUp, Picture, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import html2canvas from 'html2canvas'
import api from '@/api'
import { useUiStore } from '@/stores/ui'
import { useUserStore } from '@/stores/user'
import type { AlarmItem, DashboardCards, MapLayer, PointItem, RegionItem, RegionStat } from '@/types'
import { alarmStatusLabel, alarmTagType, alarmTypeLabel, categoryColor, categoryLabel, categoryTagType, fmtRate, pointStatusLabel } from '@/utils/format'

const userStore = useUserStore()
const uiStore = useUiStore()

const roleLabel = computed(() => {
  if (userStore.role === 'ADMIN') return '管理员'
  if (userStore.role === 'OPERATOR') return '操作员'
  return '访客'
})

// ==================== 地图初始化 ====================
const pageEl = ref<HTMLElement>()
const mapEl = ref<HTMLElement>()
let map: L.Map | null = null

// 底图（免 key，国内可达）
const BASEMAPS: Record<string, { label: string; url: string; options: L.TileLayerOptions }> = {
  gaode: {
    label: '标准地图',
    url: 'https://webrd0{s}.is.autonavi.com/appmaptile?x={x}&y={y}&z={z}&lang=zh_cn&size=1&scale=1&style=8',
    options: { maxZoom: 18, subdomains: '1234', attribution: '© 高德地图' }
  },
  esri: {
    label: '卫星影像',
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    options: { maxZoom: 19, attribution: '© Esri' }
  }
}
const basemap = ref<keyof typeof BASEMAPS>('gaode')
let baseTile: L.TileLayer | null = null

function applyBasemap() {
  if (!map) return
  if (baseTile) {
    map.removeLayer(baseTile)
    baseTile = null
  }
  const cfg = BASEMAPS[basemap.value]
  baseTile = L.tileLayer(cfg.url, cfg.options)
  baseTile.addTo(map)
}

function initMap() {
  if (!mapEl.value || map) return
  map = L.map(mapEl.value, { center: [22.8, 108.3], zoom: 10, zoomControl: false, attributionControl: true })
  L.control.zoom({ position: 'bottomleft' }).addTo(map)
  regionLayer.addTo(map)
  applyBasemap()
  map.on('click', () => {
    // 点击空白处收起搜索选中态
    searchId.value = null
  })
}

// ==================== 图层（面板：显隐 / 排序 / 透明度） ====================
interface LayerUi {
  visible: boolean
  opacity: number
}
const layers = ref<MapLayer[]>([])
const layerState = reactive<Record<number, LayerUi>>({})

function layerCategories(layer: MapLayer): string[] {
  if (!layer.styleJson) return []
  try {
    const obj = JSON.parse(layer.styleJson)
    return Array.isArray(obj.categories) ? (obj.categories as string[]) : []
  } catch {
    return []
  }
}

function initLayerState() {
  layers.value.forEach((l) => {
    if (!layerState[l.id]) {
      layerState[l.id] = { visible: l.defaultVisible === 1 && l.status === 1, opacity: 100 }
    }
  })
}

/** 点位所属图层 id（按 style_json.categories 映射） */
function layerIdOfCategory(category: string): number | null {
  for (const l of layers.value) {
    if (l.layerType !== 'POINT' || l.status !== 1) continue
    if (layerCategories(l).includes(category)) return l.id
  }
  return null
}

function isPointVisible(p: PointItem): boolean {
  const lid = layerIdOfCategory(p.category)
  if (lid == null) return true // 未映射到任何图层的点位始终显示
  const st = layerState[lid]
  return st ? st.visible : true
}

const polygonLayerVisible = computed(() =>
  layers.value.some((l) => l.layerType === 'POLYGON' && l.status === 1 && layerState[l.id]?.visible)
)

function onLayerVisible() {
  renderPoints()
  renderRegions()
}

function onLayerOpacity() {
  // 点位：直接改 marker DOM 透明度（避免拖动滑杆频繁重建）
  markers.forEach((m, id) => {
    const p = pointMap.value.get(id)
    if (!p) return
    const lid = layerIdOfCategory(p.category)
    const op = lid != null && layerState[lid] ? layerState[lid].opacity / 100 : 1
    const el = m.getElement()
    if (el) el.style.opacity = String(op)
  })
  // 区域面：重建（数量少）
  renderRegions()
}

async function moveLayer(layer: MapLayer, dir: -1 | 1) {
  const idx = layers.value.findIndex((l) => l.id === layer.id)
  const target = idx + dir
  if (idx < 0 || target < 0 || target >= layers.value.length) return
  const other = layers.value[target]
  const a = { ...layer }
  const b = { ...other }
  try {
    await Promise.all([
      api.layers.update(a.id, { ...a, sortOrder: b.sortOrder }),
      api.layers.update(b.id, { ...b, sortOrder: a.sortOrder })
    ])
    ElMessage.success('排序已更新')
    await loadLayers()
    renderPoints()
    renderRegions()
  } catch {
    // 拦截器已提示
  }
}

async function loadLayers() {
  layers.value = (await api.layers.list()) || []
  initLayerState()
}

// ==================== 点位渲染 ====================
const points = ref<PointItem[]>([])
const pointMap = computed(() => {
  const m = new Map<number, PointItem>()
  points.value.forEach((p) => m.set(p.id, p))
  return m
})
const markers = new Map<number, L.Marker>()
let pointContainer: L.LayerGroup | L.MarkerClusterGroup | null = null

const categoryFilter = ref('')
const categoryOptions = [
  { label: '全部分类', value: '' },
  { label: '监测', value: 'MONITOR' },
  { label: '隐患', value: 'HAZARD' },
  { label: '工程', value: 'PROJECT' },
  { label: '巡查', value: 'PATROL' }
]

function filteredPoints(): PointItem[] {
  return points.value.filter((p) => (categoryFilter.value ? p.category === categoryFilter.value : true) && isPointVisible(p))
}

function makeMarker(p: PointItem): L.Marker {
  const color = categoryColor(p.category)
  const off = p.status === 'DISABLED'
  const icon = L.divIcon({
    className: 'pt-div',
    html: `<div class="pt-dot${off ? ' is-off' : ''}" style="--ptc:${color}"><i></i></div>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10]
  })
  const marker = L.marker([Number(p.lat), Number(p.lng)], { icon, title: p.name })
  marker.bindTooltip(p.name, { direction: 'top', offset: [0, -9], opacity: 0.92 })
  marker.on('click', () => openDetail(p.id))
  return marker
}

function ensurePointContainer(count: number) {
  if (!map) return
  const wantCluster = count > 200
  const isCluster = pointContainer instanceof L.MarkerClusterGroup
  if (pointContainer && wantCluster === isCluster) return
  if (pointContainer) map.removeLayer(pointContainer)
  pointContainer = wantCluster
    ? L.markerClusterGroup({ maxClusterRadius: 48, showCoverageOnHover: false, spiderfyOnMaxZoom: true })
    : L.layerGroup()
  pointContainer.addTo(map)
}

function renderPoints() {
  if (!map) return
  const list = filteredPoints()
  ensurePointContainer(list.length)
  if (!pointContainer) return
  pointContainer.clearLayers()
  markers.clear()
  list.forEach((p) => {
    const m = makeMarker(p)
    markers.set(p.id, m)
    pointContainer!.addLayer(m)
  })
  // 应用图层透明度
  markers.forEach((m, id) => {
    const p = pointMap.value.get(id)
    if (!p) return
    const lid = layerIdOfCategory(p.category)
    const op = lid != null && layerState[lid] ? layerState[lid].opacity / 100 : 1
    const el = m.getElement()
    if (el) el.style.opacity = String(op)
  })
}

// ==================== 区域专题图（5 档蓝色分级） ====================
const REGION_SCALE = ['#eff6ff', '#bfdbfe', '#93c5fd', '#60a5fa', '#2563eb']
const REGION_LABELS = ['无告警', '低', '中', '较高', '很高']
const regions = ref<RegionItem[]>([])
const regionStats = ref<RegionStat[]>([])
const regionLayer = L.layerGroup()

const statMap = computed(() => {
  const m = new Map<string, RegionStat>()
  regionStats.value.forEach((s) => m.set(s.region, s))
  return m
})

const maxAlarm = computed(() => regionStats.value.reduce((max, s) => Math.max(max, Number(s.alarmCount || 0)), 0))

function regionClass(count: number): number {
  if (count <= 0) return 0
  const max = maxAlarm.value
  if (max <= 0) return 0
  const r = count / max
  if (r <= 0.25) return 1
  if (r <= 0.5) return 2
  if (r <= 0.75) return 3
  return 4
}

function polygonStyle(layer: MapLayer | undefined): { color: string; fillColor: string; fillOpacity: number; weight: number } {
  let fill = 0.18
  if (layer?.styleJson) {
    try {
      const o = JSON.parse(layer.styleJson)
      if (typeof o.fillOpacity === 'number') fill = o.fillOpacity
    } catch {
      /* 使用默认值 */
    }
  }
  const op = layer && layerState[layer.id] ? layerState[layer.id].opacity / 100 : 1
  return { color: '#3b82f6', fillColor: '#60a5fa', fillOpacity: fill * op, weight: 1.5 }
}

function toLatLngs(geo: any): L.LatLngTuple[] {
  // Polygon: coordinates = [ring[][], ...]
  const rings: number[][][] = geo.coordinates
  return rings[0].map((c: number[]) => [c[1], c[0]] as L.LatLngTuple)
}

function renderRegions() {
  if (!map) return
  regionLayer.clearLayers()
  const polyLayer = layers.value.find((l) => l.layerType === 'POLYGON' && l.status === 1)
  if (!polygonLayerVisible.value || !polyLayer) return

  regions.value.forEach((r) => {
    let geo: any
    try {
      geo = JSON.parse(r.geojson)
    } catch {
      return
    }
    const stat = statMap.value.get(r.name)
    const cls = regionClass(Number(stat?.alarmCount || 0))
    const style = { ...polygonStyle(polyLayer), fillColor: REGION_SCALE[cls] }
    let polygon: L.Polygon
    try {
      polygon = L.polygon(toLatLngs(geo), style)
    } catch {
      return
    }
    const html = `
      <div class="rg-pop">
        <div class="rg-title">${r.name}</div>
        <div class="rg-sub">${r.parentName || ''}</div>
        <div class="rg-grid">
          <span>点位数</span><b>${stat?.pointCount ?? 0}</b>
          <span>告警数</span><b>${stat?.alarmCount ?? 0}</b>
          <span>待处理</span><b class="warn">${stat?.unprocessed ?? 0}</b>
          <span>办结率</span><b>${fmtRate(stat?.closeRate)}</b>
        </div>
      </div>`
    polygon.bindPopup(html, { closeButton: true, className: 'rg-popup' })
    polygon.on('mouseover', () => polygon.setStyle({ weight: 3 }))
    polygon.on('mouseout', () => polygon.setStyle({ weight: style.weight }))
    polygon.addTo(regionLayer)
  })
}

// ==================== 指标卡 / 告警数据 ====================
const cards = ref<DashboardCards>({ monitorTotal: 0, todayAlarm: 0, closeRate: 0, pending: 0 })
const alarms = ref<AlarmItem[]>([])
const alarmTotal = ref(0)
const alarmLoading = ref(false)

async function loadCards() {
  cards.value = (await api.dashboard.cards()) || { monitorTotal: 0, todayAlarm: 0, closeRate: 0, pending: 0 }
}

async function loadAlarms() {
  alarmLoading.value = true
  try {
    const res = await api.alarms.list({ page: 1, size: 50, status: '' })
    alarms.value = res.list || []
    alarmTotal.value = res.total || 0
  } finally {
    alarmLoading.value = false
  }
}

async function loadStats() {
  regionStats.value = (await api.regions.stats()) || []
  renderRegions()
}

async function loadPoints() {
  const res = await api.points.list({ page: 1, size: 1000 })
  points.value = res.list || []
  renderPoints()
}

// ==================== 顶栏：搜索定位 ====================
const searchId = ref<number | null>(null)
const searchOptions = computed(() => points.value)

function onSearchSelect(id: number | null) {
  if (id == null) return
  const p = pointMap.value.get(id)
  if (!p) return
  flyTo(p)
  openDetail(p.id)
}

function flyTo(p: PointItem) {
  if (!map) return
  map.flyTo([Number(p.lat), Number(p.lng)], Math.max(map.getZoom(), 14), { duration: 1.1 })
}

// ==================== 点位详情弹窗 ====================
const detailVisible = ref(false)
const detailId = ref<number | null>(null)
const detailPoint = computed(() => (detailId.value != null ? pointMap.value.get(detailId.value) || null : null))
const detailAlarms = ref<AlarmItem[]>([])
const detailLoading = ref(false)

function attrRows(row?: PointItem | null): { key: string; value: string }[] {
  if (!row?.attrsJson) return []
  try {
    return Object.entries(JSON.parse(row.attrsJson)).map(([key, value]) => ({ key, value: String(value) }))
  } catch {
    return []
  }
}

async function openDetail(id: number) {
  detailId.value = id
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await api.alarms.list({ pointId: id, page: 1, size: 5 })
    detailAlarms.value = res.list || []
  } finally {
    detailLoading.value = false
  }
}

// ==================== 底部告警抽屉 + 处理 ====================
const drawerOpen = ref(false)

const handleVisible = ref(false)
const handleTitle = ref('处理告警')
const handleSaving = ref(false)
const handleRef = ref<FormInstance>()
const handleForm = reactive({ id: 0, pointName: '', handleNote: '', isClose: false })
const handleRules: FormRules = { handleNote: [{ required: true, message: '请填写处理说明', trigger: 'blur' }] }

function openHandle(row: AlarmItem, isClose = false) {
  handleForm.id = row.id
  handleForm.pointName = row.pointName
  handleForm.handleNote = ''
  handleForm.isClose = isClose
  handleTitle.value = isClose ? '关闭告警' : '处理告警'
  handleVisible.value = true
}

async function submitHandle() {
  if (!handleRef.value) return
  try {
    await handleRef.value.validate()
  } catch {
    return
  }
  handleSaving.value = true
  try {
    if (handleForm.isClose) {
      await api.alarms.close(handleForm.id, handleForm.handleNote)
      ElMessage.success('告警已关闭')
    } else {
      await api.alarms.handle(handleForm.id, handleForm.handleNote)
      ElMessage.success('处理成功')
    }
    handleVisible.value = false
    await Promise.all([loadAlarms(), loadCards(), loadStats()])
    if (detailVisible.value) openDetail(detailId.value!)
  } finally {
    handleSaving.value = false
  }
}

const unprocessedCount = computed(() => alarms.value.filter((a) => a.status === 'UNPROCESSED').length)
const tickerItems = computed(() => alarms.value.slice(0, 10))

function shortTime(s?: string): string {
  if (!s) return '-'
  const t = s.replace('T', ' ')
  return t.length >= 16 ? t.slice(5, 16) : t
}

// ==================== 截图导出 ====================
const capturing = ref(false)

async function capture() {
  if (capturing.value || !pageEl.value) return
  capturing.value = true
  try {
    const canvas = await html2canvas(pageEl.value, { useCORS: true, backgroundColor: '#0b1220', logging: false })
    const url = canvas.toDataURL('image/png')
    const a = document.createElement('a')
    const d = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    a.href = url
    a.download = `一张图_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}.png`
    a.click()
    ElMessage.success('截图已导出')
  } catch {
    ElMessage.info('截图导出受底图跨域限制，可使用系统截图工具代替')
  } finally {
    capturing.value = false
  }
}

// ==================== 大屏模式：点位轮播 + 告警滚动 ====================
let carouselTimer: number | null = null
const carouselIndex = ref(0)
const carouselPaused = ref(false)
const carouselPointId = ref<number | null>(null)
const carouselPoint = computed(() =>
  carouselPointId.value != null ? pointMap.value.get(carouselPointId.value) || null : null
)

function carouselStep() {
  const list = filteredPoints()
  if (!list.length) {
    carouselPointId.value = null
    return
  }
  carouselIndex.value = (carouselIndex.value + 1) % list.length
  const p = list[carouselIndex.value]
  carouselPointId.value = p.id
  flyTo(p)
}

function startCarousel() {
  stopCarousel()
  carouselStep()
  carouselTimer = window.setInterval(() => {
    if (!carouselPaused.value) carouselStep()
  }, 5000)
}

function stopCarousel() {
  if (carouselTimer != null) {
    window.clearInterval(carouselTimer)
    carouselTimer = null
  }
}

async function toggleBigscreen() {
  const next = !uiStore.bigscreen
  uiStore.setBigscreen(next)
  if (next) {
    carouselPaused.value = false
    startCarousel()
  } else {
    stopCarousel()
    carouselPointId.value = null
  }
  await nextTick()
  window.setTimeout(() => map?.invalidateSize(), 80)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  if (detailVisible.value || handleVisible.value) return
  if (uiStore.bigscreen) toggleBigscreen()
}

// ==================== 自动刷新（与后端 30s 模拟数据流同步） ====================
let refreshTimer: number | null = null

async function refreshAll() {
  try {
    await Promise.all([loadCards(), loadAlarms(), loadStats()])
  } catch {
    /* 网络异常忽略，下轮重试 */
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  initMap()
  window.addEventListener('keydown', onKeydown)
  try {
    await Promise.all([loadLayers(), loadPoints(), api.regions.list().then((r) => (regions.value = r || []))])
    renderRegions()
    await refreshAll()
  } catch {
    // 拦截器已提示
  }
  refreshTimer = window.setInterval(refreshAll, 30000)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  stopCarousel()
  if (refreshTimer != null) window.clearInterval(refreshTimer)
  if (uiStore.bigscreen) uiStore.setBigscreen(false)
  map?.remove()
  map = null
  markers.clear()
  pointContainer = null
})
</script>

<template>
  <div ref="pageEl" class="map-page" :class="{ 'is-big': uiStore.bigscreen }">
    <!-- 地图容器 -->
    <div ref="mapEl" class="map-canvas"></div>

    <!-- 顶栏 -->
    <div class="map-topbar">
      <div class="tb-brand">
        <span class="tb-logo">🗺️</span>
        <span class="tb-title">轻量行业一张图</span>
        <span class="tb-sub">水利 · 河长监管</span>
      </div>

      <div class="tb-center">
        <el-select
          v-model="searchId"
          filterable
          clearable
          placeholder="搜索点位定位"
          size="small"
          class="tb-select"
          @change="onSearchSelect"
        >
          <el-option v-for="p in searchOptions" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-select v-model="categoryFilter" size="small" class="tb-select tb-narrow" placeholder="全部分类" @change="renderPoints">
          <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="basemap" size="small" class="tb-select tb-narrow" @change="applyBasemap">
          <el-option v-for="(cfg, key) in BASEMAPS" :key="key" :label="cfg.label" :value="key" />
        </el-select>
      </div>

      <div class="tb-right">
        <el-button size="small" class="tb-btn" :loading="capturing" :icon="Picture" @click="capture">截图</el-button>
        <el-button size="small" class="tb-btn tb-primary" @click="toggleBigscreen">
          {{ uiStore.bigscreen ? '退出大屏 (Esc)' : '大屏模式' }}
        </el-button>
        <div class="tb-user">
          {{ userStore.nickname || userStore.username }}
          <span class="tb-role">{{ roleLabel }}</span>
        </div>
      </div>
    </div>

    <!-- 4 指标卡 -->
    <div class="metric-row">
      <div class="metric-card">
        <div class="m-label">监测点总数</div>
        <div class="m-value cyan">{{ cards.monitorTotal }}</div>
      </div>
      <div class="metric-card">
        <div class="m-label">今日告警数</div>
        <div class="m-value orange">{{ cards.todayAlarm }}</div>
      </div>
      <div class="metric-card">
        <div class="m-label">隐患办结率</div>
        <div class="m-value green">{{ fmtRate(cards.closeRate) }}</div>
      </div>
      <div class="metric-card">
        <div class="m-label">在办数</div>
        <div class="m-value red">{{ cards.pending }}</div>
      </div>
    </div>

    <!-- 左侧图层面板 -->
    <div class="layer-panel">
      <div class="lp-title">图层控制</div>
      <div v-for="(l, idx) in layers" :key="l.id" class="lp-item">
        <div class="lp-head">
          <el-checkbox
            v-model="layerState[l.id].visible"
            :disabled="l.status !== 1"
            @change="onLayerVisible"
          >{{ l.name }}</el-checkbox>
          <span class="lp-group">{{ l.groupName }}</span>
          <span v-if="userStore.isAdmin" class="lp-ops">
            <el-button
              link
              size="small"
              :disabled="idx === 0"
              :icon="ArrowUp"
              @click="moveLayer(l, -1)"
            />
            <el-button
              link
              size="small"
              :disabled="idx === layers.length - 1"
              :icon="ArrowDown"
              @click="moveLayer(l, 1)"
            />
          </span>
        </div>
        <div v-if="layerState[l.id].visible" class="lp-opacity">
          <span>透明度</span>
          <el-slider v-model="layerState[l.id].opacity" :min="10" :max="100" :show-tooltip="false" size="small" @input="onLayerOpacity" />
          <span class="lp-num">{{ layerState[l.id].opacity }}%</span>
        </div>
      </div>
      <el-empty v-if="!layers.length" description="暂无图层" :image-size="48" />
    </div>

    <!-- 右侧图例 -->
    <div class="legend-panel">
      <div class="lg-title">点位分类</div>
      <div class="lg-row" v-for="c in ['MONITOR', 'HAZARD', 'PROJECT', 'PATROL']" :key="c">
        <i class="lg-dot" :style="{ background: categoryColor(c) }"></i>{{ categoryLabel(c) }}
      </div>
      <div class="lg-title lg-gap">区域告警分级</div>
      <div class="lg-row" v-for="(color, i) in REGION_SCALE" :key="color">
        <i class="lg-box" :style="{ background: color }"></i>{{ REGION_LABELS[i] }}
      </div>
    </div>

    <!-- 底部告警抽屉（常规模式） -->
    <template v-if="!uiStore.bigscreen">
      <div class="alarm-bar">
        <div class="ab-title">
          <span class="ab-dot"></span>告警动态
          <el-tag v-if="unprocessedCount" type="danger" size="small" effect="dark" class="ab-badge">{{ unprocessedCount }} 待处理</el-tag>
          <el-tag v-else type="success" size="small" effect="plain" class="ab-badge">全部已办</el-tag>
        </div>
        <div class="ab-latest">
          <template v-if="alarms.length">
            {{ shortTime(alarms[0].createdAt) }} · {{ alarms[0].pointName }} ·
            {{ alarmTypeLabel(alarms[0]) }} 实测 {{ alarms[0].valueStr }}（阈值 {{ alarms[0].thresholdStr }}）
          </template>
          <template v-else>暂无告警记录</template>
        </div>
        <div class="ab-actions">
          <el-button size="small" text class="ab-link" @click="drawerOpen = !drawerOpen">
            {{ drawerOpen ? '收起' : `展开 (${alarmTotal})` }}
          </el-button>
        </div>
      </div>

      <div v-show="drawerOpen" class="alarm-drawer" v-loading="alarmLoading">
        <div class="ad-head">
          <span>最近告警（{{ alarmTotal }} 条）</span>
          <el-button size="small" text @click="drawerOpen = false">收起</el-button>
        </div>
        <div class="ad-list">
          <div v-for="a in alarms" :key="a.id" class="ad-item">
            <span class="ad-time">{{ shortTime(a.createdAt) }}</span>
            <span class="ad-point">{{ a.pointName }}</span>
            <span class="ad-type">{{ alarmTypeLabel(a) }}</span>
            <span class="ad-val">{{ a.valueStr }} / {{ a.thresholdStr }}</span>
            <el-tag :type="alarmTagType(a.status)" size="small">{{ alarmStatusLabel(a.status) }}</el-tag>
            <span class="ad-note">{{ a.handleNote || '-' }}</span>
            <div class="ad-ops">
              <el-button
                v-if="a.status !== 'CLOSED' && userStore.isOperator"
                link
                type="primary"
                size="small"
                @click="openHandle(a)"
              >{{ a.status === 'UNPROCESSED' ? '处理' : '重新处理' }}</el-button>
              <el-button
                v-if="a.status !== 'CLOSED' && userStore.isAdmin"
                link
                type="danger"
                size="small"
                @click="openHandle(a, true)"
              >关闭</el-button>
            </div>
          </div>
          <el-empty v-if="!alarms.length" description="暂无告警" :image-size="60" />
        </div>
      </div>
    </template>

    <!-- 大屏模式：底部告警滚动条 + 点位轮播 -->
    <template v-else>
      <div class="bs-ticker">
        <div class="bs-ticker-label">告警滚动</div>
        <div class="bs-ticker-view">
          <div class="bs-ticker-track">
            <template v-for="dup in [0, 1]" :key="dup">
              <span v-for="a in tickerItems" :key="dup + '-' + a.id" class="bs-item">
                <i class="bs-dot"></i>
                {{ shortTime(a.createdAt) }} · {{ a.pointName }} · {{ alarmTypeLabel(a) }} · 实测 {{ a.valueStr }}（阈值 {{ a.thresholdStr }}）· {{ alarmStatusLabel(a.status) }}
              </span>
              <span v-if="!tickerItems.length" class="bs-item" :key="'empty-' + dup">暂无告警记录</span>
            </template>
          </div>
        </div>
      </div>

      <div v-if="carouselPoint" class="bs-card">
        <div class="bs-card-head">
          <el-tag :type="categoryTagType(carouselPoint.category)" size="small" effect="dark">
            {{ categoryLabel(carouselPoint.category) }}
          </el-tag>
          <span class="bs-name">{{ carouselPoint.name }}</span>
        </div>
        <div class="bs-meta">{{ carouselPoint.region || '-' }} · {{ carouselPoint.riverSegment || '-' }}</div>
        <div class="bs-attrs">
          <span v-for="a in attrRows(carouselPoint)" :key="a.key" class="bs-attr">{{ a.key }}: {{ a.value }}</span>
          <span v-if="!attrRows(carouselPoint).length" class="bs-attr">状态 {{ pointStatusLabel(carouselPoint.status) }}</span>
        </div>
        <div class="bs-card-ops">
          <el-button size="small" class="tb-btn" :icon="carouselPaused ? VideoPlay : VideoPause" @click="carouselPaused = !carouselPaused">
            {{ carouselPaused ? '继续轮播' : '暂停轮播' }}
          </el-button>
          <el-button size="small" class="tb-btn" @click="openDetail(carouselPoint.id)">详情</el-button>
        </div>
      </div>
    </template>

    <!-- 点位详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailPoint ? `点位详情 · ${detailPoint.name}` : '点位详情'" width="620px" destroy-on-close>
      <template v-if="detailPoint">
        <div class="detail-grid">
          <div class="dg-item"><label>分类</label><el-tag :type="categoryTagType(detailPoint.category)" size="small">{{ categoryLabel(detailPoint.category) }}</el-tag></div>
          <div class="dg-item"><label>状态</label><el-tag :type="detailPoint.status === 'NORMAL' ? 'success' : 'info'" size="small">{{ pointStatusLabel(detailPoint.status) }}</el-tag></div>
          <div class="dg-item"><label>区域</label><span>{{ detailPoint.region || '-' }}</span></div>
          <div class="dg-item"><label>河段</label><span>{{ detailPoint.riverSegment || '-' }}</span></div>
          <div class="dg-item"><label>坐标</label><span>{{ Number(detailPoint.lng).toFixed(5) }}, {{ Number(detailPoint.lat).toFixed(5) }}</span></div>
          <div class="dg-item dg-wide"><label>属性</label>
            <span>
              <el-tag v-for="a in attrRows(detailPoint)" :key="a.key" size="small" effect="plain" class="dg-attr">{{ a.key }}: {{ a.value }}</el-tag>
              <span v-if="!attrRows(detailPoint).length">无</span>
            </span>
          </div>
        </div>

        <div class="detail-alarms">
          <div class="da-title">最近告警</div>
          <div v-loading="detailLoading" class="da-list">
            <div v-for="a in detailAlarms" :key="a.id" class="da-item">
              <span class="da-time">{{ shortTime(a.createdAt) }}</span>
              <span>{{ alarmTypeLabel(a) }}</span>
              <span class="da-val">{{ a.valueStr }} / {{ a.thresholdStr }}</span>
              <el-tag :type="alarmTagType(a.status)" size="small">{{ alarmStatusLabel(a.status) }}</el-tag>
            </div>
            <el-empty v-if="!detailAlarms.length && !detailLoading" description="该点位暂无告警" :image-size="54" />
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailPoint"
          type="primary"
          @click="detailVisible = false; $router.push('/points')"
        >前往点位管理</el-button>
      </template>
    </el-dialog>

    <!-- 处理 / 关闭告警弹窗 -->
    <el-dialog v-model="handleVisible" :title="`${handleTitle} · ${handleForm.pointName}`" width="460px" destroy-on-close>
      <el-form ref="handleRef" :model="handleForm" :rules="handleRules" label-width="90px">
        <el-form-item label="处理说明" prop="handleNote">
          <el-input v-model="handleForm.handleNote" type="textarea" :rows="4" placeholder="必填：写明处置措施与结果，留痕可查" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handleSaving" @click="submitHandle">
          {{ handleForm.isClose ? '确认关闭' : '确认处理' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ==================== 页面容器（常规模式贴边铺满内容区，大屏模式整屏） ==================== */
.map-page {
  position: relative;
  height: calc(100vh - 96px);
  margin: -20px -24px;
  overflow: hidden;
  background: #0b1220;
}

.map-page.is-big {
  height: 100vh;
  margin: 0;
}

.map-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
}

/* 缩放控件：避开左侧图层面板与底部告警条 */
.map-page :deep(.leaflet-bottom.leaflet-left) {
  left: 264px;
  bottom: 52px;
}

/* ==================== 顶栏 ==================== */
.map-topbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 48px;
  z-index: 1200;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 14px;
  background: linear-gradient(180deg, rgba(6, 14, 26, 0.92), rgba(6, 14, 26, 0.72));
  border-bottom: 1px solid rgba(56, 189, 248, 0.28);
  backdrop-filter: blur(8px);
}

.tb-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.tb-logo {
  font-size: 18px;
}

.tb-title {
  color: #e2f3ff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 2px;
}

.tb-sub {
  color: #38bdf8;
  font-size: 11px;
  letter-spacing: 1px;
  padding: 2px 8px;
  border: 1px solid rgba(56, 189, 248, 0.4);
  border-radius: 10px;
}

.tb-center {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.tb-select {
  width: 190px;
}

.tb-select.tb-narrow {
  width: 118px;
}

.map-topbar :deep(.el-input__wrapper) {
  background: rgba(15, 30, 48, 0.85);
  box-shadow: 0 0 0 1px rgba(56, 189, 248, 0.3) inset;
}

.map-topbar :deep(.el-input__inner) {
  color: #dbeafe;
}

.map-topbar :deep(.el-input__inner::placeholder) {
  color: rgba(148, 197, 237, 0.55);
}

.tb-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.tb-btn {
  background: rgba(15, 30, 48, 0.85) !important;
  border: 1px solid rgba(56, 189, 248, 0.4) !important;
  color: #bfe6ff !important;
}

.tb-btn:hover {
  border-color: #38bdf8 !important;
  color: #fff !important;
}

.tb-primary {
  background: rgba(37, 99, 235, 0.85) !important;
  border-color: rgba(96, 165, 250, 0.7) !important;
  color: #fff !important;
}

.tb-user {
  color: #cfe6fb;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.tb-role {
  font-size: 11px;
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.45);
  padding: 1px 7px;
  border-radius: 9px;
}

/* ==================== 指标卡 ==================== */
.metric-row {
  position: absolute;
  top: 60px;
  right: 14px;
  z-index: 1100;
  display: flex;
  gap: 10px;
}

.metric-card {
  min-width: 116px;
  padding: 9px 14px 10px;
  border-radius: 10px;
  background: rgba(6, 14, 26, 0.82);
  border: 1px solid rgba(56, 189, 248, 0.28);
  backdrop-filter: blur(6px);
  box-shadow: 0 4px 18px rgba(2, 8, 20, 0.45);
}

.m-label {
  font-size: 11px;
  color: #8fb8d8;
  letter-spacing: 1px;
}

.m-value {
  margin-top: 3px;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.m-value.cyan { color: #22d3ee; }
.m-value.orange { color: #f59e0b; }
.m-value.green { color: #34d399; }
.m-value.red { color: #f87171; }

/* ==================== 左侧图层面板 ==================== */
.layer-panel {
  position: absolute;
  left: 14px;
  top: 60px;
  bottom: 56px;
  width: 244px;
  z-index: 1100;
  padding: 12px 12px 8px;
  border-radius: 12px;
  background: rgba(6, 14, 26, 0.84);
  border: 1px solid rgba(56, 189, 248, 0.28);
  backdrop-filter: blur(8px);
  overflow-y: auto;
}

.lp-title {
  color: #e2f3ff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 3px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.2);
  margin-bottom: 6px;
}

.lp-item {
  padding: 6px 0;
  border-bottom: 1px dashed rgba(56, 189, 248, 0.14);
}

.lp-head {
  display: flex;
  align-items: center;
  gap: 4px;
}

.lp-head :deep(.el-checkbox__label) {
  color: #d5e9fb;
  font-size: 13px;
}

.lp-group {
  margin-left: auto;
  font-size: 10px;
  color: #6f94b3;
}

.lp-ops {
  display: inline-flex;
  margin-left: 4px;
}

.lp-ops :deep(.el-button) {
  color: #7fb6dd;
  padding: 2px 4px;
}

.lp-opacity {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 4px 0 24px;
  font-size: 11px;
  color: #7fa8c8;
}

.lp-opacity :deep(.el-slider) {
  flex: 1;
}

.lp-opacity :deep(.el-slider__runway) {
  background: rgba(56, 189, 248, 0.18);
}

.lp-opacity :deep(.el-slider__bar) {
  background: #38bdf8;
}

.lp-opacity :deep(.el-slider__button) {
  border-color: #38bdf8;
}

.lp-num {
  width: 34px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.layer-panel :deep(.el-empty__description p) {
  color: #6f94b3;
  font-size: 12px;
}

/* ==================== 右侧图例 ==================== */
.legend-panel {
  position: absolute;
  right: 14px;
  top: 142px;
  z-index: 1100;
  width: 138px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(6, 14, 26, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.25);
  backdrop-filter: blur(6px);
}

.lg-title {
  color: #8fd0f5;
  font-size: 11px;
  letter-spacing: 2px;
  margin-bottom: 6px;
}

.lg-gap {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(56, 189, 248, 0.18);
}

.lg-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #cfe6fb;
  font-size: 12px;
  line-height: 20px;
}

.lg-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.6);
  flex-shrink: 0;
}

.lg-box {
  width: 14px;
  height: 10px;
  border-radius: 2px;
  border: 1px solid rgba(59, 130, 246, 0.6);
  flex-shrink: 0;
}

/* ==================== 底部告警抽屉 ==================== */
.alarm-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 44px;
  z-index: 1150;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px;
  background: rgba(6, 14, 26, 0.9);
  border-top: 1px solid rgba(56, 189, 248, 0.3);
  backdrop-filter: blur(6px);
}

.ab-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #e2f3ff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 2px;
  flex-shrink: 0;
}

.ab-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f87171;
  box-shadow: 0 0 8px #f87171;
  animation: abBlink 1.6s infinite;
}

@keyframes abBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

.ab-badge {
  margin-left: 2px;
}

.ab-latest {
  flex: 1;
  min-width: 0;
  color: #a9cbe6;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ab-actions {
  flex-shrink: 0;
}

.ab-link {
  color: #38bdf8 !important;
}

.alarm-drawer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 44px;
  height: 300px;
  z-index: 1140;
  background: rgba(6, 14, 26, 0.96);
  border-top: 1px solid rgba(56, 189, 248, 0.35);
  display: flex;
  flex-direction: column;
}

.ad-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  color: #e2f3ff;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid rgba(56, 189, 248, 0.18);
}

.ad-head :deep(.el-button) {
  color: #38bdf8;
}

.ad-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 16px 10px;
}

.ad-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  border-bottom: 1px dashed rgba(56, 189, 248, 0.14);
  color: #cfe6fb;
  font-size: 12px;
}

.ad-time {
  color: #7fa8c8;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.ad-point {
  width: 130px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ad-type {
  width: 110px;
  flex-shrink: 0;
}

.ad-val {
  width: 150px;
  flex-shrink: 0;
  color: #93c5fd;
  font-variant-numeric: tabular-nums;
}

.ad-note {
  flex: 1;
  min-width: 0;
  color: #7fa8c8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ad-ops {
  flex-shrink: 0;
  display: flex;
  gap: 4px;
}

.ad-ops :deep(.el-button) {
  color: #38bdf8;
}

.alarm-drawer :deep(.el-empty__description p) {
  color: #6f94b3;
}

/* ==================== 大屏：告警滚动条 ==================== */
.bs-ticker {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 44px;
  z-index: 1200;
  display: flex;
  align-items: center;
  background: rgba(4, 10, 20, 0.95);
  border-top: 1px solid rgba(56, 189, 248, 0.4);
  overflow: hidden;
}

.bs-ticker-label {
  flex-shrink: 0;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 16px;
  color: #38bdf8;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 3px;
  border-right: 1px solid rgba(56, 189, 248, 0.3);
  background: rgba(37, 99, 235, 0.18);
}

.bs-ticker-view {
  flex: 1;
  height: 100%;
  overflow: hidden;
}

.bs-ticker-track {
  display: flex;
  align-items: center;
  gap: 40px;
  height: 100%;
  width: max-content;
  white-space: nowrap;
  padding-left: 24px;
  animation: bsScroll 42s linear infinite;
}

.bs-ticker-track:hover {
  animation-play-state: paused;
}

@keyframes bsScroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

.bs-item {
  color: #bfe6ff;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.bs-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f87171;
  box-shadow: 0 0 6px #f87171;
}

/* ==================== 大屏：点位轮播卡 ==================== */
.bs-card {
  position: absolute;
  left: 14px;
  bottom: 60px;
  width: 306px;
  z-index: 1200;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(6, 14, 26, 0.9);
  border: 1px solid rgba(56, 189, 248, 0.4);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 26px rgba(2, 8, 20, 0.6);
}

.bs-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bs-name {
  color: #e2f3ff;
  font-size: 15px;
  font-weight: 700;
}

.bs-meta {
  margin-top: 6px;
  color: #8fb8d8;
  font-size: 12px;
}

.bs-attrs {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.bs-attr {
  font-size: 11px;
  color: #a5f3d0;
  background: rgba(52, 211, 153, 0.12);
  border: 1px solid rgba(52, 211, 153, 0.32);
  border-radius: 6px;
  padding: 2px 7px;
}

.bs-card-ops {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

/* ==================== 点位标记（Leaflet divIcon） ==================== */
.map-page :deep(.pt-div) {
  background: transparent;
  border: none;
}

.map-page :deep(.pt-dot) {
  position: relative;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--ptc);
  border: 2px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 10px var(--ptc);
  display: flex;
  align-items: center;
  justify-content: center;
}

.map-page :deep(.pt-dot i) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.95);
}

.map-page :deep(.pt-dot::after) {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 1px solid var(--ptc);
  opacity: 0.55;
  animation: ptPulse 2.4s ease-out infinite;
}

@keyframes ptPulse {
  0% { transform: scale(0.7); opacity: 0.7; }
  100% { transform: scale(1.5); opacity: 0; }
}

.map-page :deep(.pt-dot.is-off) {
  background: #64748b;
  box-shadow: none;
  opacity: 0.7;
}

.map-page :deep(.pt-dot.is-off::after) {
  display: none;
}

/* Leaflet 控件避让底部告警条 */
.map-page :deep(.leaflet-bottom) {
  bottom: 48px;
}

.map-page :deep(.leaflet-control-attribution) {
  background: rgba(6, 14, 26, 0.6);
  color: #7fa8c8;
  font-size: 10px;
}

.map-page :deep(.leaflet-control-attribution a) {
  color: #38bdf8;
}

.map-page :deep(.leaflet-bar a) {
  background: rgba(6, 14, 26, 0.88);
  color: #bfe6ff;
  border-bottom-color: rgba(56, 189, 248, 0.3);
}

.map-page :deep(.leaflet-bar a:hover) {
  background: rgba(37, 99, 235, 0.85);
  color: #fff;
}

.map-page :deep(.leaflet-tooltip) {
  background: rgba(6, 14, 26, 0.92);
  border: 1px solid rgba(56, 189, 248, 0.4);
  color: #e2f3ff;
  font-size: 12px;
  box-shadow: 0 4px 14px rgba(2, 8, 20, 0.5);
}

.map-page :deep(.leaflet-tooltip-top::before) {
  border-top-color: rgba(56, 189, 248, 0.4);
}

/* 区域弹窗 */
.map-page :deep(.rg-popup .rg-pop) {
  min-width: 190px;
  color: #26322c;
}

.map-page :deep(.rg-popup .rg-title) {
  font-size: 14px;
  font-weight: 700;
  color: #1d4ed8;
}

.map-page :deep(.rg-popup .rg-sub) {
  font-size: 12px;
  color: #8b968f;
  margin-bottom: 6px;
}

.map-page :deep(.rg-popup .rg-grid) {
  display: grid;
  grid-template-columns: auto auto;
  gap: 4px 16px;
  font-size: 12px;
}

.map-page :deep(.rg-popup .rg-grid b) {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.map-page :deep(.rg-popup .rg-grid b.warn) {
  color: #dc2626;
}

/* ==================== 点位详情弹窗 ==================== */
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 18px;
  padding: 4px 2px 12px;
  border-bottom: 1px solid var(--yzt-border);
}

.dg-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--yzt-text);
}

.dg-item label {
  color: var(--yzt-text-sub);
  flex-shrink: 0;
  font-size: 12px;
}

.dg-wide {
  grid-column: 1 / -1;
  align-items: flex-start;
}

.dg-attr {
  margin-right: 6px;
  margin-bottom: 4px;
}

.detail-alarms {
  margin-top: 12px;
}

.da-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--yzt-text);
  margin-bottom: 8px;
}

.da-list {
  min-height: 60px;
}

.da-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--yzt-text);
  padding: 7px 4px;
  border-bottom: 1px dashed var(--yzt-border);
}

.da-time {
  color: var(--yzt-text-sub);
  font-variant-numeric: tabular-nums;
}

.da-val {
  color: #2563eb;
  font-variant-numeric: tabular-nums;
  margin-left: auto;
}
</style>
