<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import api from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 登录：成功后存 token 并跳工作台（或重定向回原页面）
async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await api.auth.login({ username: form.username, password: form.password })
    userStore.setLogin(data)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <div class="login-logo">🍊</div>
        <h1>果账通</h1>
        <p>水果收购 · 开单记账系统</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button class="btn-gradient login-btn" type="primary" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 20% 20%, rgba(67, 185, 122, 0.18), transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(47, 158, 98, 0.14), transparent 45%),
    var(--yzt-bg);
}

.login-card {
  width: 380px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px 32px;
  box-shadow: 0 12px 40px rgba(38, 50, 44, 0.1);
  border: 1px solid var(--yzt-border);
}

.login-brand {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  font-size: 40px;
  margin-bottom: 8px;
}

.login-brand h1 {
  margin: 0;
  font-size: 26px;
  letter-spacing: 6px;
  color: var(--yzt-text);
}

.login-brand p {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--yzt-text-sub);
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-top: 4px;
}
</style>
