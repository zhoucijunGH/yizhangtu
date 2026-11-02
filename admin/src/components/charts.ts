// 通用 ECharts 组件（用 h() 渲染，避免 JSX 依赖）
import { defineComponent, h, onMounted, onBeforeUnmount, ref, watch, type PropType } from 'vue'
import * as echarts from 'echarts'

/** 创建图表实例，统一处理渲染、监听与销毁 */
function useChart(optionFn: () => any) {
  const el = ref<HTMLElement>()
  let chart: echarts.ECharts | null = null
  const render = () => {
    if (!chart) chart = echarts.init(el.value!)
    chart.setOption(optionFn(), true)
  }
  onMounted(() => {
    render()
    window.addEventListener('resize', onResize)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
    chart?.dispose()
  })
  const onResize = () => chart?.resize()
  return { el, render, onResize }
}

/** 收购额趋势折线图：rows = [{ date: 'MM-DD', amount }] */
export const TrendChart = defineComponent({
  name: 'TrendChart',
  props: { rows: { type: Array as PropType<any[]>, default: () => [] } },
  setup(props) {
    const { el, render } = useChart(() => ({
      tooltip: { trigger: 'axis', valueFormatter: (v: any) => '¥' + v },
      grid: { left: 56, right: 16, top: 16, bottom: 28 },
      xAxis: {
        type: 'category',
        data: props.rows.map((r) => String(r.date)),
        axisLabel: { color: '#666' }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#666' },
        splitLine: { lineStyle: { color: '#eee' } }
      },
      series: [{
        type: 'line',
        data: props.rows.map((r) => Number(r.amount || 0)),
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#2f9e62', width: 2.5 },
        itemStyle: { color: '#2f9e62' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(47, 158, 98, 0.22)' },
            { offset: 1, color: 'rgba(47, 158, 98, 0.02)' }
          ])
        }
      }]
    }))
    watch(() => props.rows, render)
    return () => h('div', { ref: el, style: 'height: 300px' })
  }
})
