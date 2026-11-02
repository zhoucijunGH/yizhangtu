import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { ApiResult } from '@/env'

// axios 实例：统一 baseURL、超时
const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// localStorage 里的登录态 key
const TOKEN_KEY = 'yzt_token'
const USER_KEYS = ['yzt_token', 'yzt_userId', 'yzt_username', 'yzt_nickname', 'yzt_role']

// 清理登录态并跳登录页
function goLogin(msg: string) {
  USER_KEYS.forEach((k) => localStorage.removeItem(k))
  ElMessage.error(msg)
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

// 请求拦截：自动带上 JWT
request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一解包 {code,msg,data}，code=0 才算成功
request.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    // 文件流（导出 Excel）直接返回 Blob，不走业务解包
    if (response.config.responseType === 'blob') {
      const blob = response.data as unknown as Blob
      // 后端出错时可能返回 JSON 而非 xlsx，这里解析出来提示
      if (blob && blob.type && blob.type.includes('json')) {
        return blob.text().then((txt) => {
          let msg = '导出失败'
          try {
            msg = JSON.parse(txt).msg || msg
          } catch {
            // 解析失败则用默认提示
          }
          ElMessage.error(msg)
          return Promise.reject(new Error(msg))
        })
      }
      return blob as any
    }

    const res = response.data
    if (res.code !== 0) {
      // 业务错误统一提示
      if (res.code === 401) {
        goLogin('登录已过期，请重新登录')
      } else {
        ElMessage.error(res.msg || '请求失败')
      }
      return Promise.reject(new Error(res.msg))
    }
    return res.data as any
  },
  (error) => {
    if (error.response?.status === 401) {
      goLogin('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

// 简化的 GET/POST/PUT/DELETE 封装（解包后的 data 就是返回值）
export const http = {
  get<T = any>(url: string, params?: object, config?: AxiosRequestConfig) {
    return request.get<any, T>(url, { params, ...config })
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig) {
    return request.post<any, T>(url, data, config)
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig) {
    return request.put<any, T>(url, data, config)
  },
  del<T = any>(url: string, params?: object) {
    return request.delete<any, T>(url, { params })
  }
}

export default request
