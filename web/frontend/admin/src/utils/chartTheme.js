// 梧桐校园 · ECharts 配色（sRGB 近似 Token，canvas 不支持 OKLCH）
// 与 src/styles/tokens.css 的校园蓝/杏色语义一致，保证图表与大屏协调。
export const chartPalette = {
  brand:  '#315cf5', // ≈ 校园蓝 oklch(56% 0.19 265)
  accent: '#f2a15c', // ≈ 杏色 oklch(76% 0.06 60)
  success:'#4ca46a',
  warning:'#e0a93f',
  error:  '#d9543f',
  info:   '#6b8f9c',
  ink:    '#43474d',
  ink2:   '#8b9099',
  line:   '#e6e2d8',
}

export const seriesColors = [
  chartPalette.brand, chartPalette.accent, chartPalette.success,
  chartPalette.warning, chartPalette.error, chartPalette.info,
]

// 图表通用基底（文字/图例/提示框/坐标色），与各图 option 合并即可统一观感
export function chartBase() {
  return {
    color: seriesColors,
    textStyle: { color: chartPalette.ink, fontFamily: 'Plus Jakarta Sans, PingFang SC, sans-serif' },
    legend: { textStyle: { color: chartPalette.ink2 } },
    tooltip: {
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: chartPalette.line,
      textStyle: { color: chartPalette.ink },
    },
    // 注：categoryAxis/valueAxis 非 ECharts 顶层字段，仅作占位；坐标轴色在各图显式设置
    _axis: {
      line: chartPalette.line,
      label: chartPalette.ink2,
    },
  }
}
