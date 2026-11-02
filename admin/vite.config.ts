import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    // @ 别名指向 src，现代 Vue3 工程标准写法
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 固定 5273，避开 5173/5174 被其他应用占用
    port: 5273,
    strictPort: true,
    // 开发代理：接口与上传的磅单照片都转发到后端（契约端口 8090）
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true
      }
    }
  }
})
