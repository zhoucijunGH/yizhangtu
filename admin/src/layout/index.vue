<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { appRoutes } from '@/router'

const userStore = useUserStore()
const route = useRoute()
const vueRouter = useRouter()

// 菜单分组（用户管理仅 ADMIN 可见）
const menus = computed(() => {
  const visible = appRoutes.filter((r) => r.meta?.admin !== true || userStore.isAdmin)
  return [
    {
      title: '业务',
      items: visible.filter((r) => ['dashboard', 'orders', 'daily', 'debts'].includes(r.name as string))
    },
    {
      title: '基础数据',
      items: visible.filter((r) => ['fruits', 'users'].includes(r.name as string))
    }
  ].filter((g) => g.items.length > 0)
})

const currentTitle = computed(() => (route.meta.title as string) || '工作台')
const roleLabel = computed(() => (userStore.role === 'ADMIN' ? '管理员' : '店员'))

function go(path: string) {
  vueRouter.push(path)
}

function logout() {
  ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    .then(() => {
      userStore.logout()
      vueRouter.push('/login')
    })
    .catch(() => {})
}
</script>

<template>
  <div class="layout">
    <!-- 侧边栏：品牌卡 + 分组菜单 -->
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">🍊</div>
        <div class="brand-text">
          <div class="brand-name">果账通</div>
          <div class="brand-slogan">水果收购 · 开单记账</div>
        </div>
      </div>

      <el-menu :default-active="route.path" class="sidebar-menu" @select="go">
        <template v-for="group in menus" :key="group.title">
          <div class="menu-group-title">{{ group.title }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="'/' + String(item.path)">
            <el-icon><component :is="item.meta?.icon" /></el-icon>
            <span>{{ item.meta?.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar-footer">果账通 · 收购开单记账系统</div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-trigger">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.nickname || userStore.username || '未登录' }}
              <el-tag size="small" :type="userStore.isAdmin ? 'danger' : 'info'" effect="plain">{{ roleLabel }}</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
}

/* ========== 侧边栏 ========== */
.sidebar {
  width: 220px;
  background: #fff;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid var(--yzt-border);
}

.brand {
  margin: 12px 12px 4px;
  padding: 16px 14px;
  border-radius: 14px;
  background: var(--yzt-gradient);
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 6px 16px rgba(47, 158, 98, 0.25);
}

.brand-logo {
  font-size: 24px;
  width: 42px;
  height: 42px;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-name {
  color: #fff;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 3px;
}

.brand-slogan {
  color: rgba(255, 255, 255, 0.85);
  font-size: 10px;
  margin-top: 3px;
}

.sidebar-menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border-right: none;
  background: transparent;
  padding: 6px 10px;
}

.menu-group-title {
  color: #a7b0aa;
  font-size: 11px;
  padding: 14px 10px 6px;
  letter-spacing: 2px;
  font-weight: 600;
}

.sidebar-menu :deep(.el-menu-item) {
  color: #5f6b64;
  border-radius: 9px;
  margin-bottom: 2px;
  height: 42px;
  font-size: 14px;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: #f1f7f3;
  color: var(--yzt-primary);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--yzt-primary-soft);
  color: var(--yzt-primary);
  font-weight: 600;
}

.sidebar-footer {
  padding: 14px 16px 18px;
  border-top: 1px solid var(--yzt-border);
  font-size: 11px;
  color: #a7b0aa;
}

/* ========== 主区域 ========== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--yzt-border);
  flex-shrink: 0;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--yzt-text);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #5f6b64;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: var(--yzt-bg);
}
</style>
