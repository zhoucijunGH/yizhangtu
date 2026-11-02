<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '@/api'
import type { UserForm, UserItem } from '@/types'

const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<UserItem[]>([])
const loading = ref(false)

const roleOptions = [
  { label: '管理员', value: 'ADMIN' },
  { label: '店员', value: 'STAFF' }
]

function roleLabel(role?: string) {
  return role === 'ADMIN' ? '管理员' : '店员'
}

async function load() {
  loading.value = true
  try {
    const res = await api.users.list({ page: page.value, size: size.value })
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
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

// ========== 新增 / 编辑弹窗 ==========
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

function defaultForm(): UserForm {
  return { username: '', password: '', nickname: '', phone: '', role: 'STAFF' }
}

const form = reactive<UserForm>(defaultForm())

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增用户'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row: UserItem) {
  editingId.value = row.id
  dialogTitle.value = '编辑用户'
  Object.assign(form, {
    username: row.username,
    password: '',
    nickname: row.nickname,
    phone: row.phone,
    role: row.role
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
      // 编辑不改密码
      await api.users.update(editingId.value, {
        username: form.username,
        nickname: form.nickname,
        phone: form.phone,
        role: form.role
      })
      ElMessage.success('修改成功')
    } else {
      await api.users.create({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ========== 重置密码弹窗 ==========
const resetVisible = ref(false)
const resetSaving = ref(false)
const resetRef = ref<FormInstance>()
const resetForm = reactive({ userId: 0, username: '', password: '' })

const resetRules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

function openReset(row: UserItem) {
  resetForm.userId = row.id
  resetForm.username = row.username
  resetForm.password = ''
  resetVisible.value = true
}

async function doReset() {
  if (!resetRef.value) return
  try {
    await resetRef.value.validate()
  } catch {
    return
  }
  resetSaving.value = true
  try {
    await api.users.resetPassword(resetForm.userId, resetForm.password)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } finally {
    resetSaving.value = false
  }
}

// ========== 删除 ==========
function remove(row: UserItem) {
  ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.users.remove(row.id)
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
      <h3>用户管理</h3>
      <div class="page-header-desc">维护系统账号（仅管理员可操作）</div>
    </div>

    <div class="page-card">
      <div class="query-bar">
        <div style="flex: 1"></div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="username" label="账号" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="120">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="电话" min-width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="openReset(row)">重置密码</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户" />
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="登录账号" :disabled="editingId != null" clearable />
        </el-form-item>
        <el-form-item v-if="editingId == null" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="至少 6 位" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="显示昵称" clearable />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" placeholder="联系电话（可选）" clearable />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="o in roleOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetVisible" :title="`重置密码 · ${resetForm.username}`" width="420px" destroy-on-close>
      <el-form ref="resetRef" :model="resetForm" :rules="resetRules" label-width="80px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" placeholder="至少 6 位" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSaving" @click="doReset">确定重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped></style>
