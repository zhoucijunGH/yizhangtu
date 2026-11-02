import { defineStore } from 'pinia'
import { ref } from 'vue'

// 全局 UI 状态：地图大屏模式（隐藏侧边栏与顶栏）
export const useUiStore = defineStore('ui', () => {
  const bigscreen = ref(false)

  function setBigscreen(v: boolean) {
    bigscreen.value = v
  }

  return { bigscreen, setBigscreen }
})
