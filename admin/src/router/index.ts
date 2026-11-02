import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// 布局壳（登录页之外的所有页面共用）
const Layout = () => import('@/layout/index.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '工作台', icon: 'Odometer' } },
      { path: 'orders', name: 'orders', component: () => import('@/views/orders/index.vue'), meta: { title: '收购流水', icon: 'Tickets' } },
      { path: 'daily', name: 'daily', component: () => import('@/views/daily/index.vue'), meta: { title: '日结报表', icon: 'Calendar' } },
      { path: 'debts', name: 'debts', component: () => import('@/views/debts/index.vue'), meta: { title: '欠款汇总', icon: 'Money' } },
      { path: 'fruits', name: 'fruits', component: () => import('@/views/fruits/index.vue'), meta: { title: '果品档案', icon: 'Cherry' } },
      { path: 'users', name: 'users', component: () => import('@/views/users/index.vue'), meta: { title: '用户管理', icon: 'User', admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导出路由表（布局侧边栏菜单用）
const layoutRoute = routes.find((r) => r.path === '/')
export const appRoutes = layoutRoute?.children || []

// 全局前置守卫：未登录跳登录页；用户管理仅 ADMIN 可进
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.token) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined }
  }
  if (to.meta.admin && !userStore.isAdmin) {
    ElMessage.warning('仅管理员可访问该页面')
    return { path: '/dashboard' }
  }
  document.title = to.meta.title ? `${to.meta.title} · 果账通` : '果账通'
})

export default router
