<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import api from '@/api'
import type { PointItem, RuleForm, RuleItem } from '@/types'

// ========== 列表 ==========
const list = ref<RuleItem[]>([])
const loading = ref(false)
const pointId = ref<number | undefined>(undefined)
const points = ref<PointItem[]>([])

const pointNameMap = computed(() => {
  const m = new Map<number, string>()
  points.value.forEach((p) => m.set(p.id, p.name))
  return m
})

function pointName(id?: number): string {
  if (id == null) return '-'
  return pointNameMap.value.get(id) || `#${id}`
}

function limitText(row: RuleItem): string {
  const up = row.upperLimit != null ? `上限 ${row.upperLimit}` : ''
  const low = row.lowerLimit != null ? `下限 ${row.lowerLimit}` : ''
  return [up, low].filter(Boolean).join(' / ') || '-'
}

async function load() {
  loading.value = true
  try {
    list.value = (await api.rules.list(pointId.value)) || []
  } finally {
    loading.value = false
  }
}

async function loadPoints() {
  try {
    const res = await api.points.list({ page: 1, size: 500 })
    points.value = res.list || []
  } catch {
    // 拉取失败仅影响下拉展示
  }
}

function search() {
  load()
}

function reset() {
  pointId.value = undefined
  load()
}

// ========== 新增 / 编辑弹窗 ==========
const dialogVisible = ref(false)
const dialogTitle = ref('新增规则')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

function defaultForm(): RuleForm {
  return { pointId: undefined, attrKey: '', attrLabel: '', upperLimit: undefined, lowerLimit: undefined, enabled: 1, remark: '' }
}

const form = reactive<RuleForm>(defaultForm())

const rules: FormRules = {
  pointId: [{ required: true, message: '请选择点位', trigger: 'change' }],
  attrKey: [{ required: true, message: '请输入属性键', trigger: 'blur' }],
  attrLabel: [{ required: true, message: '请输入属性名', trigger: 'blur' }],
  upperLimit: [
    {
      validator: (_r, value, cb) => {
        if (value == null) return cb()
        Number.isFinite(Number(value)) ? cb() : cb(new Error('上限须为数字'))
      },
      trigger: 'change'
    }
  ],
  lowerLimit: [
    {
      validator: (_r, value, cb) => {
        if (value == null) return cb()
        Number.isFinite(Number(value)) ? cb() : cb(new Error('下限须为数字'))
      },
      trigger: 'change'
    }
  ]
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增规则'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row: RuleItem) {
  editingId.value = row.id
  dialogTitle.value = '编辑规则'
  Object.assign(form, {
    pointId: row.pointId,
    attrKey: row.attrKey,
    attrLabel: row.attrLabel,
    upperLimit: row.upperLimit,
    lowerLimit: row.lowerLimit,
    enabled: row.enabled,
    remark: row.remark
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
  if (form.upperLimit == null && form.lowerLimit == null) {
    ElMessage.warning('上限与下限至少填写一项')
    return
  }
  if (form.upperLimit != null && form.lowerLimit != null && Number(form.upperLimit) <= Number(form.lowerLimit)) {
    ElMessage.warning('上限必须大于下限')
    return
  }
  saving.value = true
  try {
    if (editingId.value != null) {
      await api.rules.update(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await api.rules.create({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: RuleItem) {
  const next = row.enabled === 1 ? 0 : 1
  const text = next === 0 ? '停用' : '启用'
  ElMessageBox.confirm(`确认${text}「${pointName(row.pointId)} · ${row.attrLabel}」规则？`, '操作确认', {
    type: 'warning'
  })
    .then(async () => {
      await api.rules.update(row.id, {
        pointId: row.pointId,
        attrKey: row.attrKey,
        attrLabel: row.attrLabel,
        upperLimit: row.upperLimit,
        lowerLimit: row.lowerLimit,
        enabled: next,
        remark: row.remark
      })
      ElMessage.success(`已${text}`)
      load()
    })
    .catch(() => {})
}

function remove(row: RuleItem) {
  ElMessageBox.confirm(`确认删除「${pointName(row.pointId)} · ${row.attrLabel}」规则？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.rules.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  loadPoints()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h3>阈值规则</h3>
      <div class="page-header-desc">配置点位属性的上 / 下限，模拟数据流越限时自动生成告警（仅管理员可维护）</div>
    </div>

    <!-- 筛选栏 -->
    <div class="page-card query-bar">
      <el-select v-model="pointId" filterable clearable placeholder="全部点位" style="width: 220px">
        <el-option v-for="p in points" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
    </div>

    <!-- 规则表格 -->
    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column label="点位" min-width="160">
          <template #default="{ row }">{{ pointName(row.pointId) }}</template>
        </el-table-column>
        <el-table-column prop="attrLabel" label="属性" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.attrLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="attrKey" label="属性键" min-width="130">
          <template #default="{ row }"><code class="attr-key">{{ row.attrKey }}</code></template>
        </el-table-column>
        <el-table-column label="阈值" min-width="180">
          <template #default="{ row }">{{ limitText(row) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled === 1 ? 'warning' : 'success'" @click="toggleEnabled(row)">
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无阈值规则" />
        </template>
      </el-table>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="点位" prop="pointId">
          <el-select v-model="form.pointId" filterable placeholder="选择点位" style="width: 100%">
            <el-option v-for="p in points" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="属性键" prop="attrKey">
          <el-input v-model="form.attrKey" placeholder="如：water_level / rainfall" clearable />
        </el-form-item>
        <el-form-item label="属性名" prop="attrLabel">
          <el-input v-model="form.attrLabel" placeholder="如：水位 / 雨量" clearable />
        </el-form-item>
        <el-form-item label="上限">
          <el-input-number v-model="form.upperLimit" :controls="false" style="width: 100%" placeholder="超过该值告警（可空）" />
        </el-form-item>
        <el-form-item label="下限">
          <el-input-number v-model="form.lowerLimit" :controls="false" style="width: 100%" placeholder="低于该值告警（可空）" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="可空" clearable />
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
.attr-key {
  font-size: 12px;
  background: #f2f4f3;
  padding: 2px 6px;
  border-radius: 4px;
  color: #5f6b64;
}
</style>
