<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import api from '@/api'
import { useUserStore } from '@/stores/user'
import type { AlarmItem } from '@/types'
import { alarmStatusLabel, alarmTagType, alarmTypeLabel, fmtDateTime } from '@/utils/format'

const userStore = useUserStore()
const canHandle = computed(() => userStore.isOperator)

// ========== 查询与列表 ==========
const status = ref('')
const keyword = ref('')
const dateRange = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<AlarmItem[]>([])
const loading = ref(false)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '未处理', value: 'UNPROCESSED' },
  { label: '已处理', value: 'PROCESSED' },
  { label: '已关闭', value: 'CLOSED' }
]

function queryParams() {
  return {
    status: status.value,
    keyword: keyword.value,
    dateFrom: dateRange.value?.[0] || '',
    dateTo: dateRange.value?.[1] || ''
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.alarms.list({ ...queryParams(), page: page.value, size: size.value })
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
  status.value = ''
  keyword.value = ''
  dateRange.value = null
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

async function exportExcel() {
  const blob = await api.alarms.export(queryParams())
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '告警记录.xlsx'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

// ========== 处理 / 关闭 ==========
const handleVisible = ref(false)
const handleTitle = ref('处理告警')
const handleSaving = ref(false)
const handleRef = ref<FormInstance>()
const handleForm = reactive({ id: 0, pointName: '', handleNote: '', isClose: false })

const handleRules: FormRules = {
  handleNote: [{ required: true, message: '请填写处理说明', trigger: 'blur' }]
}

function openHandle(row: AlarmItem) {
  handleForm.id = row.id
  handleForm.pointName = row.pointName
  handleForm.handleNote = ''
  handleForm.isClose = false
  handleTitle.value = '处理告警'
  handleVisible.value = true
}

function openClose(row: AlarmItem) {
  handleForm.id = row.id
  handleForm.pointName = row.pointName
  handleForm.handleNote = ''
  handleForm.isClose = true
  handleTitle.value = '关闭告警'
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
    load()
  } finally {
    handleSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>告警中心</h3>
      <div class="page-header-desc">阈值越限告警的查看、处理与关闭，处理说明必填并留痕</div>
    </div>

    <!-- 筛选栏 -->
    <div class="page-card query-bar">
      <el-select v-model="status" placeholder="全部状态" style="width: 140px">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-input v-model="keyword" placeholder="点位名称" clearable style="width: 200px" @keyup.enter="search" />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button :icon="Download" @click="exportExcel">导出 Excel</el-button>
    </div>

    <!-- 告警表格 -->
    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column label="告警时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="pointName" label="点位" min-width="150" />
        <el-table-column label="告警类型" min-width="130">
          <template #default="{ row }">{{ alarmTypeLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="valueStr" label="实测值" width="100" align="right" />
        <el-table-column prop="thresholdStr" label="阈值" width="110" align="right" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="alarmTagType(row.status)" size="small">{{ alarmStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理说明" min-width="180">
          <template #default="{ row }">{{ row.handleNote || '-' }}</template>
        </el-table-column>
        <el-table-column label="处理人" width="110">
          <template #default="{ row }">{{ row.handler || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <template v-if="row.status !== 'CLOSED'">
              <el-button
                v-if="canHandle"
                link
                type="primary"
                :disabled="row.status === 'PROCESSED' && !userStore.isAdmin"
                @click="openHandle(row)"
              >
                {{ row.status === 'UNPROCESSED' ? '处理' : '重新处理' }}
              </el-button>
              <el-button v-if="userStore.isAdmin" link type="danger" @click="openClose(row)">关闭</el-button>
            </template>
            <span v-else style="color: var(--yzt-text-sub); font-size: 12px">已归档</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无告警记录" />
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
        />
      </div>
    </div>

    <!-- 处理 / 关闭对话框 -->
    <el-dialog v-model="handleVisible" :title="`${handleTitle} · ${handleForm.pointName}`" width="480px" destroy-on-close>
      <el-form ref="handleRef" :model="handleForm" :rules="handleRules" label-width="90px">
        <el-form-item label="处理说明" prop="handleNote">
          <el-input
            v-model="handleForm.handleNote"
            type="textarea"
            :rows="4"
            placeholder="必填：写明处置措施与结果，留痕可查"
          />
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

<style scoped></style>
