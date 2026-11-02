import request, { http } from './request'
import type {
  DailyReport,
  DashboardData,
  DebtItem,
  Fruit,
  FruitForm,
  FruitQuery,
  LoginResult,
  Order,
  OrderForm,
  OrderPage,
  OrderQuery,
  UserForm,
  UserItem,
  UserPage,
  UserQuery
} from '@/types'

// 后端接口统一入口（按 API 契约分组）
export const api = {
  // 登录
  auth: {
    login(data: { username: string; password: string }) {
      return http.post<LoginResult>('/auth/login', data)
    }
  },

  // 果品档案
  fruits: {
    list(params: FruitQuery = {}) {
      return http.get<Fruit[]>('/fruits', { ...params })
    },
    create(data: FruitForm) {
      return http.post<Fruit>('/fruits', data)
    },
    update(id: number, data: FruitForm) {
      return http.put<Fruit>(`/fruits/${id}`, data)
    },
    remove(id: number) {
      return http.del(`/fruits/${id}`)
    }
  },

  // 收购流水
  orders: {
    list(params: OrderQuery) {
      return http.get<OrderPage>('/orders', { ...params })
    },
    detail(id: number) {
      return http.get<Order>(`/orders/${id}`)
    },
    create(data: OrderForm) {
      return http.post<Order>('/orders', data)
    },
    update(id: number, data: OrderForm) {
      return http.put<Order>(`/orders/${id}`, data)
    },
    remove(id: number) {
      return http.del(`/orders/${id}`)
    }
  },

  // 工作台看板
  dashboard: {
    get() {
      return http.get<DashboardData>('/dashboard')
    }
  },

  // 日结报表
  reports: {
    daily(date: string) {
      return http.get<DailyReport>('/reports/daily', { date })
    }
  },

  // 欠款汇总
  debts: {
    list(keyword?: string) {
      return http.get<DebtItem[]>('/debts', { keyword: keyword || '' })
    }
  },

  // 用户管理
  users: {
    list(params: UserQuery = {}) {
      return http.get<UserPage>('/users', { ...params })
    },
    create(data: UserForm) {
      return http.post<UserItem>('/users', data)
    },
    update(id: number, data: UserForm) {
      return http.put<UserItem>(`/users/${id}`, data)
    },
    remove(id: number) {
      return http.del(`/users/${id}`)
    },
    resetPassword(id: number, password: string) {
      return http.post(`/users/${id}/reset-password`, { password })
    }
  },

  // 导出（xlsx 文件流）
  export: {
    orders(params: { dateFrom?: string; dateTo?: string }) {
      return request.get<any, Blob>('/export/orders', { params: { ...params }, responseType: 'blob' })
    }
  }
}

export default api
