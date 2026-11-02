// 业务类型定义（按后端 API 契约整理）

// 登录返回
export interface LoginUser {
  id: number
  username: string
  nickname: string
  role: string
}

export interface LoginResult {
  token: string
  user: LoginUser
}

// 果品档案
export interface Fruit {
  id: number
  name: string
  spec?: string
  price: number
  unit?: string
  enabled?: number
}

export type FruitForm = {
  name: string
  spec?: string
  price: number
  unit?: string
  enabled: number
}

export type FruitQuery = {
  keyword?: string
  enabled?: string
}

// 收购流水单
export interface Order {
  id: number
  orderNo?: string
  createdAt?: string
  orderDate?: string
  fruitId: number
  fruitName?: string
  spec?: string
  grossWeight: number
  tareWeight: number
  netWeight?: number
  price: number
  amount?: number
  farmerName: string
  farmerPhone?: string
  source?: string
  payStatus: string
  payMethod?: string
  remark?: string
  photoUrl?: string
}

export type OrderForm = {
  fruitId?: number
  grossWeight?: number
  tareWeight?: number
  price?: number
  farmerName?: string
  farmerPhone?: string
  source?: string
  payStatus?: string
  payMethod?: string
  remark?: string
  photoUrl?: string
}

export type OrderQuery = {
  dateFrom?: string
  dateTo?: string
  keyword?: string
  payStatus?: string
  page?: number
  size?: number
}

// 流水分页结果
export interface OrderPage {
  total: number
  list: Order[]
}

// 工作台看板
export interface DashboardData {
  todayAmount: number
  todayCount: number
  todayNetWeight: number
  unpaidAmount: number
  unpaidCount: number
  trend: { date: string; amount: number }[]
}

// 日结报表
export interface DailyReport {
  date: string
  summary: {
    amount: number
    count: number
    netWeight: number
    unpaidAmount: number
  }
  byFruit: { fruitName: string; spec?: string; count: number; netWeight: number; amount: number }[]
  byFarmer: { farmerName: string; phone?: string; count: number; amount: number; unpaidAmount: number }[]
}

// 欠款汇总
export interface DebtItem {
  farmerName: string
  phone?: string
  unpaidCount: number
  unpaidAmount: number
  lastDate?: string
}

// 系统用户
export interface UserItem {
  id: number
  username: string
  nickname?: string
  phone?: string
  role: string
}

export type UserForm = {
  username: string
  password?: string
  nickname?: string
  phone?: string
  role: string
}

export type UserQuery = {
  page?: number
  size?: number
}

export interface UserPage {
  total: number
  list: UserItem[]
}
