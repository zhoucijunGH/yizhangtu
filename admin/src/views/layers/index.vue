<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '@/api'
import type { LayerForm, MapLayer } from '@/types'

// ========== 列表 ==========
const list = ref<MapLayer[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = (await api.layers.list()) || []
  } finally {
    loading.value = false
  }
}

const layerTypeLabel = (t?: string) => (t === 'POLYGON' ? '面图层' : '点图层')

/** style_json 预览（点位分类映射等） */
function styleSummary(row: MapLayer): string {
  if (!row.styleJson) return '-'
  try {
    const obj = JSON.parse(row.styleJson)
    const cats: string[] = obj.categories || []
    const bits: string[] = []
    if (obj.color) bits.push(String(obj.color))
    if (obj.radius) bits.push(`半径 ${obj.radius}`)
    if (cats.length) bits.push(`分类 ${cats.join('/')}`)
    return bits.length ? bits.join(' · ') : '已配置'
  } catch {
    return '格式异常'
  }
}

// ========== 新增 / 编辑弹窗 ==========
const dialogVisible = ref(false)
const dialogTitle = ref('新增图层')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

function defaultForm(): LayerForm {
  return {
    name: '',
    groupName: '基础点位',
    layerType: 'POINT',
    defaultVisible: 1,
    sortOrder: 0,
    styleJson: '',
    status: 1
  }
}

const form = reactive<LayerForm>(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入图层名称', trigger: 'blur' }],
  groupName: [{ required: true, message: '请输入分组名', trigger: 'blur' }],
  layerType: [{ required: true, message: '请选择图层类型', trigger: 'change' }],
  styleJson: [
    {
      validator: (_r, value, cb) => {
        if (!value) return cb()
        try {
          JSON.parse(String(value))
          cb()
        } catch {
          cb(new Error('样式必须是合法 JSON'))
        }
      },
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增图层'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row: MapLayer) {
  editingId.value = row.id
  dialogTitle.value = '编辑图层'
  Object.assign(form, {
    name: row.name,
    groupName: row.groupName,
    layerType: row.layerType,
    defaultVisible: row.defaultVisible,
    sortOrder: row.sortOrder,
    styleJson: row.styleJson,
    status: row.status
  })
  dialogVisible.value = true
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
    if (editingId.value != null) {
      await api.layers.update(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await api.layers.create({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function remove(row: MapLayer) {
  ElMessageBox.confirm(`确认删除图层「${row.name}」？地图上对应要素将不再显示`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.layers.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>图层配置</h3>
      <div class="page-header-desc">维护一张图左侧图层树（仅管理员可操作），支持分组、排序与样式映射</div>
    </div>

    <div class="page-card">
      <div class="query-bar">
        <div style="flex: 1"></div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增图层</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="name" label="图层名称" min-width="160" />
        <el-table-column prop="groupName" label="分组" min-width="120">
          <template #default="{ row }">{{ row.groupName || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.layerType === 'POLYGON' ? 'success' : 'primary'" effect="plain">
              {{ layerTypeLabel(row.layerType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="默认显示" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.defaultVisible === 1 ? 'success' : 'info'">
              {{ row.defaultVisible === 1 ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="样式" min-width="200">
          <template #default="{ row }">{{ styleSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无图层" />
        </template>
      </el-table>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="图层名称" prop="name">
          <el-input v-model="form.name" placeholder="如：监测点位" clearable />
        </el-form-item>
        <el-form-item label="分组" prop="groupName">
          <el-input v-model="form.groupName" placeholder="如：基础点位 / 专题图层" clearable />
        </el-form-item>
        <el-form-item label="图层类型" prop="layerType">
          <el-radio-group v-model="form.layerType">
            <el-radio value="POINT">点图层</el-radio>
            <el-radio value="POLYGON">面图层（区域专题）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width: 160px" />
          <span class="form-tip">值越小越靠前</span>
        </el-form-item>
        <el-form-item label="默认显示">
          <el-radio-group v-model="form.defaultVisible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="样式 JSON" prop="styleJson">
          <el-input
            v-model="form.styleJson"
            type="textarea"
            :rows="4"
            placeholder='如：{"color":"#22d3ee","radius":8,"categories":["MONITOR"]}'
          />
          <div class="form-tip">categories 用于把点位分类映射到该图层；面图层可写 color 表示专题底色</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-tip {
  font-size: 12px;
  color: var(--yzt-text-sub);
  margin-left: 8px;
}
</style>
