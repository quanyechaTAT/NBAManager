import { computed, ref, onMounted, onUnmounted } from 'vue'

/**
 * 提供主题感知的 ECharts 颜色配置
 * 监听 data-theme 属性变化，自动切换深色/浅色配色
 */
export function useChartTheme() {
  const isLight = ref(false)

  function update() {
    isLight.value = document.documentElement.getAttribute('data-theme') === 'light'
  }

  let observer: MutationObserver | null = null

  onMounted(() => {
    update()
    observer = new MutationObserver(update)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  })

  onUnmounted(() => {
    observer?.disconnect()
  })

  const colors = computed(() => {
    if (isLight.value) {
      return {
        // 背景
        tooltipBg: '#FFFFFF',
        tooltipBorder: '#E8E5E0',
        // 文字
        textPrimary: '#1A1A1A',
        textSecondary: '#4A4540',
        textMuted: '#78716C',
        textDim: '#A8A29E',
        // 图表轴线/网格
        axisLine: '#E8E5E0',
        splitLine: '#F1F0ED',
        splitArea1: 'rgba(232, 93, 38, 0.02)',
        splitArea2: 'rgba(232, 93, 38, 0.04)',
        // 主色
        accent: '#E85D26',
        accentLight: '#F97316',
        purple: '#7C3AED',
        purpleLight: '#8B5CF6',
        cyan: '#0891B2',
        // 渐变色（柱状图）
        barWin1: '#E85D26',
        barWin2: '#F97316',
        barLose1: '#D4CFC8',
        barLose2: '#B8B0A6',
        // 雷达图
        radarArea1: 'rgba(232, 93, 38, 0.06)',
        radarArea2: 'rgba(232, 93, 38, 0.10)',
      }
    }
    return {
      tooltipBg: '#1C2333',
      tooltipBorder: '#30363D',
      textPrimary: '#E6EDF3',
      textSecondary: '#CBD5E1',
      textMuted: '#8B949E',
      textDim: '#6E7681',
      axisLine: '#30363D',
      splitLine: '#1C2333',
      splitArea1: 'rgba(108, 92, 231, 0.02)',
      splitArea2: 'rgba(108, 92, 231, 0.05)',
      accent: '#E85D26',
      accentLight: '#F97316',
      purple: '#6C5CE7',
      purpleLight: '#A29BFE',
      cyan: '#00D2FF',
      barWin1: '#E85D26',
      barWin2: '#F97316',
      barLose1: '#3D444D',
      barLose2: '#2D333B',
      radarArea1: 'rgba(108, 92, 231, 0.02)',
      radarArea2: 'rgba(108, 92, 231, 0.05)',
    }
  })

  /** 通用 tooltip 配置 */
  const tooltipStyle = computed(() => ({
    backgroundColor: colors.value.tooltipBg,
    borderColor: colors.value.tooltipBorder,
    textStyle: { color: colors.value.textPrimary, fontSize: 12 },
  }))

  /** 通用 legend 文字样式 */
  const legendStyle = computed(() => ({
    textStyle: { color: colors.value.textMuted, fontSize: 12 },
  }))

  /** 通用 xAxis 配置 */
  const xAxisStyle = computed(() => ({
    axisLabel: { color: colors.value.textDim, fontSize: 11 },
    axisLine: { lineStyle: { color: colors.value.axisLine } },
    axisTick: { show: false },
  }))

  /** 通用 yAxis 配置 */
  const yAxisStyle = computed(() => ({
    axisLabel: { color: colors.value.textDim, fontSize: 11 },
    splitLine: { lineStyle: { color: colors.value.splitLine, type: 'dashed' as const } },
    axisLine: { show: false },
    axisTick: { show: false },
  }))

  return { isLight, colors, tooltipStyle, legendStyle, xAxisStyle, yAxisStyle }
}
