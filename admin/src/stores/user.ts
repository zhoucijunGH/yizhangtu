import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { LoginUser } from '@/types'

// localStorage key
const KEY = {
  token: 'yzt_token',
  userId: 'yzt_userId',
  username: 'yzt_username',
  nickname: 'yzt_nickname',
  role: 'yzt_role'
}

// 登录态 Store：token / 用户信息持久化到 localStorage
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(KEY.token) || '')
  const userId = ref<number>(Number(localStorage.getItem(KEY.userId) || 0))
  const username = ref<string>(localStorage.getItem(KEY.username) || '')
  const nickname = ref<string>(localStorage.getItem(KEY.nickname) || '')
  const role = ref<string>(localStorage.getItem(KEY.role) || '')

  // 是否管理员（只有 ADMIN 才显示用户管理、流水编辑/删除入口）
  const isAdmin = computed(() => role.value === 'ADMIN')

  // 登录成功后的信息落地
  function setLogin(data: { token: string; user: LoginUser }) {
    token.value = data.token
    userId.value = data.user.id
    username.value = data.user.username
    nickname.value = data.user.nickname
    role.value = data.user.role
    localStorage.setItem(KEY.token, data.token)
    localStorage.setItem(KEY.userId, String(data.user.id))
    localStorage.setItem(KEY.username, data.user.username)
    localStorage.setItem(KEY.nickname, data.user.nickname)
    localStorage.setItem(KEY.role, data.user.role)
  }

  // 退出登录
  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    nickname.value = ''
    role.value = ''
    Object.values(KEY).forEach((k) => localStorage.removeItem(k))
  }

  return { token, userId, username, nickname, role, isAdmin, setLogin, logout }
})
