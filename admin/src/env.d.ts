/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 统一后端响应结构：code = 0 表示成功，非 0 为业务错误
export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
}
