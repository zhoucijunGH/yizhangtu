<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Download, Plus, Upload } from '@element-plus/icons-vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import api from '@/api'
import { useUserStore } from '@/stores/user'
import type { PointForm, PointItem, RegionItem } from '@/types'
import { categoryLabel, categoryTagType, pointStatusLabel } from '@/utils/format'

const userStore = useUserStore()
const canEdit = computed(() => userStore.isOperator)

// ========== 查询与列表 ==========
const keyword = ref('')
const category = ref('')
const region = ref('')
const status = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<PointItem[]>([])
const loading = ref(false)
const regions = ref<RegionItem[]>([])

const categoryOptions = [
  { label: '全部分类', value: '' },
  { label: '监测', value: 'MONITOR' },
  { label: '隐患', value: 'HAZARD' },
  { label: '工程', value: 'PROJECT' },
  { label: '巡查', value: 'PATROL' }
]
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 'NORMAL' },
  { label: '停用', value: 'DISABLED' }
]

function queryParams() {
  return {
    keyword: keyword.value,
    category: category.value,
    region: region.value,
    status: status.value
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.points.list({ ...queryParams(), page: page.value, size: size.value })
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  category.value = ''
  region.value = ''
  status.value = ''
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  load()
}

/** 属性 JSON → 可读键值 */
function attrRows(row: PointItem): { key: string; value: string }[] {
  if (!row.attrsJson) return []
  try {
    const obj = JSON.parse(row.attrsJson)
    return Object.entries(obj).map(([key, value]) => ({ key, value: String(value) }))
  } catch {
    return []
  }
}

// ========== 导入导出 ==========
function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function exportExcel() {
  const blob = await api.points.export(queryParams())
  downloadBlob(blob, '点位台账.xlsx')
  ElMessage.success('导出成功')
}

async function exportGeoJson() {
  const data = await api.points.geojson(queryParams())
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/geo+json' })
  downloadBlob(blob, '点位.geojson')
  ElMessage.success('GeoJSON 导出成功')
}

async function downloadTemplate() {
  const blob = await api.points.importTemplate()
  downloadBlob(blob, '点位导入模板.xlsx')
}

const fileRef = ref<HTMLInputElement>()
function pickFile() {
  fileRef.value?.click()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const res = await api.points.importExcel(file)
    const failCount = res.failed?.length || 0
    if (failCount === 0) {
      ElMessage.success(`导入成功，共 ${res.imported} 条`)
    } else {
      const detail = res.failed.map((f) => `第${f.row}行：${f.reason}`).join('；')
      ElMessageBox.alert(`成功 ${res.imported} 条，失败 ${failCount} 条。${detail}`, '导入结果', {
        type: 'warning'
      })
    }
    load()
  } catch {
    // request 拦截器已提示
  }
}

// ========== 新增 / 编辑弹窗（含地图选点） ==========
const dialogVisible = ref(false)
const dialogTitle = ref('新增点位')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

function defaultForm(): PointForm {
  return { name: '', category: 'MONITOR', region: '', riverSegment: '', lng: undefined, lat: undefined, attrsJson: '', photo: '', status: 'NORMAL' }
}

const form = reactive<PointForm>(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入点位名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  lng: [
    { required: true, message: '请在地图上点击选点或输入经度', trigger: 'change' },
    {
      validator: (_r, value, cb) => {
        const n = Number(value)
        if (!Number.isFinite(n) || n < -180 || n > 180) cb(new Error('经度须在 -180 ~ 180'))
        else cb()
      },
      trigger: 'change'
    }
  ],
  lat: [
    { required: true, message: '请在地图上点击选点或输入纬度', trigger: 'change' },
    {
      validator: (_r, value, cb) => {
        const n = Number(value)
        if (!Number.isFinite(n) || n < -90 || n > 90) cb(new Error('纬度须在 -90 ~ 90'))
        else cb()
      },
      trigger: 'change'
    }
  ]
}

// 对话框内嵌小地图
const pickerEl = ref<HTMLElement>()
let pickerMap: L.Map | null = null
let pickerMarker: L.Marker | null = null

function ensurePickerMap() {
  nextTick(() => {
    if (!pickerEl.value || pickerMap) return
    pickerMap = L.map(pickerEl.value, {
      center: [22.8, 108.3],
      zoom: 10,
      zoomControl: true
    })
    L.tileLayer(
      'https://webrd0{s}.is.autonavi.com/appmaptile?x={x}&y={y}&z={z}&lang=zh_cn&size=1&scale=1&style=8',
      { maxZoom: 18, subdomains: '1234', attribution: '© 高德地图' }
    ).addTo(pickerMap)
    pickerMap.on('click', (e: L.LeafletMouseEvent) => {
      const { lat, lng } = e.latlng
      form.lng = Number(lng.toFixed(6))
      form.lat = Number(lat.toFixed(6))
      if (pickerMarker) {
        pickerMarker.setLatLng(e.latlng)
      } else {
        pickerMarker = L.marker(e.latlng).addTo(pickerMap!)
      }
    })
    syncPickerMarker()
  })
}

function syncPickerMarker() {
  if (!pickerMap) return
  if (form.lng != null && form.lat != null) {
    const ll = L.latLng(Number(form.lat), Number(form.lng))
    if (pickerMarker) pickerMarker.setLatLng(ll)
    else pickerMarker = L.marker(ll).addTo(pickerMap)
    pickerMap.setView(ll, Math.max(pickerMap.getZoom(), 12))
  }
}

function destroyPickerMap() {
  pickerMap?.remove()
  pickerMap = null
  pickerMarker = null
}

function onDialogClosed() {
  destroyPickerMap()
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增点位'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
  ensurePickerMap()
}

function openEdit(row: PointItem) {
  editingId.value = row.id
  dialogTitle.value = '编辑点位'
  Object.assign(form, {
    name: row.name,
    category: row.category,
    region: row.region,
    riverSegment: row.riverSegment,
    lng: row.lng,
    lat: row.lat,
    attrsJson: row.attrsJson,
    photo: row.photo,
    status: row.status
  })
  dialogVisible.value = true
  ensurePickerMap()
}

async function save() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload: PointForm = { ...form, lng: Number(form.lng), lat: Number(form.lat) }
    if (editingId.value != null) {
      await api.points.update(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await api.points.create(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ========== 停用 / 恢复 / 删除 ==========
async function toggleStatus(row: PointItem) {
  const next = row.status === 'DISABLED' ? 'NORMAL' : 'DISABLED'
  const text = next === 'DISABLED' ? '停用' : '恢复'
  ElMessageBox.confirm(`确认${text}点位「${row.name}」？`, '操作确认', { type: 'warning' })
    .then(async () => {
      await api.points.updateStatus(row.id, next)
      ElMessage.success(`已${text}`)
      load()
    })
    .catch(() => {})
}

function remove(row: PointItem) {
  ElMessageBox.confirm(`确认删除点位「${row.name}」？删除后不可恢复`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.points.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  api.regions.list().then((r) => (regions.value = r)).catch(() => {})
})

onBeforeUnmount(destroyPickerMap)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>点位管理</h3>
      <div class="page-header-desc">维护监测 / 隐患 / 工程 / 巡查点位，支持地图选点与 Excel 导入导出</div>
    </div>

    <!-- 筛选栏 -->
    <div class="page-card query-bar">
      <el-input
        v-model="keyword"
        placeholder="点位名称 / 河段"
        clearable
        style="width: 200px"
        @keyup.enter="search"
      />
      <el-select v-model="category" placeholder="全部分类" style="width: 130px">
        <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="region" filterable clearable placeholder="全部区域" style="width: 180px">
        <el-option v-for="r in regions" :key="r.id" :label="r.name" :value="r.name" />
      </el-select>
      <el-select v-model="status" placeholder="全部状态" style="width: 130px">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button @click="exportExcel">导出 Excel</el-button>
      <el-button @click="exportGeoJson">导出 GeoJSON</el-button>
      <template v-if="canEdit">
        <el-button :icon="Download" @click="downloadTemplate">导入模板</el-button>
        <el-button :icon="Upload" @click="pickFile">导入</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增点位</el-button>
      </template>
      <input ref="fileRef" type="file" accept=".xlsx,.xls" style="display: none" @change="onFileChange" />
    </div>

    <!-- 点位表格 -->
    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="name" label="点位名称" min-width="150" />
        <el-table-column label="分类" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="categoryTagType(row.category)" size="small">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="region" label="区域" min-width="140">
          <template #default="{ row }">{{ row.region || '-' }}</template>
        </el-table-column>
        <el-table-column prop="riverSegment" label="河段" min-width="130">
          <template #default="{ row }">{{ row.riverSegment || '-' }}</template>
        </el-table-column>
        <el-table-column label="坐标" width="170">
          <template #default="{ row }">{{ Number(row.lng).toFixed(5) }}, {{ Number(row.lat).toFixed(5) }}</template>
        </el-table-column>
        <el-table-column label="属性" min-width="160">
          <template #default="{ row }">
            <template v-if="attrRows(row).length">
              <el-tag v-for="a in attrRows(row)" :key="a.key" size="small" effect="plain" class="attr-tag">
                {{ a.key }}: {{ a.value }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'" size="small">
              {{ pointStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <template v-if="canEdit">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link :type="row.status === 'DISABLED' ? 'success' : 'warning'" @click="toggleStatus(row)">
                {{ row.status === 'DISABLED' ? '恢复' : '停用' }}
              </el-button>
            </template>
            <el-button v-if="userStore.isAdmin" link type="danger" @click="remove(row)">删除</el-button>
            <span v-if="!canEdit && !userStore.isAdmin" style="color: var(--yzt-text-sub); font-size: 12px">只读</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无点位数据" />
        </template>
      </el-table>

      <div class="table-toolbar">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="760px"
      destroy-on-close
      @closed="onDialogClosed"
    >
      <div class="point-dialog">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
          <el-form-item label="点位名称" prop="name">
            <el-input v-model="form.name" placeholder="如：邕江口监测站" clearable />
          </el-form-item>
          <el-form-item label="分类" prop="category">
            <el-select v-model="form.category" style="width: 100%">
              <el-option label="监测" value="MONITOR" />
              <el-option label="隐患" value="HAZARD" />
              <el-option label="工程" value="PROJECT" />
              <el-option label="巡查" value="PATROL" />
            </el-select>
          </el-form-item>
          <el-form-item label="区域">
            <el-select v-model="form.region" filterable clearable placeholder="选择区域" style="width: 100%">
              <el-option v-for="r in regions" :key="r.id" :label="r.name" :value="r.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="河段">
            <el-input v-model="form.riverSegment" placeholder="如：邕江左岸 K3+200" clearable />
          </el-form-item>
          <el-form-item label="地图选点">
            <div class="picker-wrap">
              <div ref="pickerEl" class="picker-map"></div>
              <div class="picker-tip">在地图上点击选择点位坐标</div>
            </div>
          </el-form-item>
          <el-form-item label="经度" prop="lng">
            <el-input-number v-model="form.lng" :precision="6" :controls="false" style="width: 100%" placeholder="点击地图或手动输入" />
          </el-form-item>
          <el-form-item label="纬度" prop="lat">
            <el-input-number v-model="form.lat" :precision="6" :controls="false" style="width: 100%" placeholder="点击地图或手动输入" />
          </el-form-item>
          <el-form-item label="属性 JSON">
            <el-input
              v-model="form.attrsJson"
              type="textarea"
              :rows="3"
              placeholder='如：{"water_level":6.8,"rainfall":32}'
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio value="NORMAL">正常</el-radio>
              <el-radio value="DISABLED">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.attr-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.point-dialog {
  display: flex;
}

.point-dialog .el-form {
  flex: 1;
}

.picker-wrap {
  width: 100%;
}

.picker-map {
  width: 100%;
  height: 240px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--yzt-border);
}

.picker-tip {
  font-size: 12px;
  color: var(--yzt-text-sub);
  margin-top: 4px;
}
</style>
