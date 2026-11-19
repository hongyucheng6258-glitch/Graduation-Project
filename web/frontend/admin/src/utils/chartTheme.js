// 梧桐校园 · ECharts 配色
// canvas 不支持 OKLCH，所以这里维护两套 sRGB 色板（亮 / 暗），
// 数值与 src/styles/tokens.css 的品牌色、语义色保持一致。
// 主题切换后由 Dashboard 重建图表，不需要重新请求数据。

const LIGHT = {
  brand: '#5B4BD1',
  accent: '#D89A3D',
  success: '#2C7A8C',
  warning: '#B77A16',
  error: '#C0453C',
  info: '#7581A8',
  ink: '#252233',
  ink2: '#767187',
  line: '#E8E5F0',
  tooltipBg: 'rgba(255,255,255,0.96)',
  tooltipBorder: '#E8E5F0',
}

const DARK = {
  brand: '#9B85FF',
  accent: '#EEB154',
  success: '#4AB074',
  warning: '#C9892F',
  error: '#EF656B',
  info: '#7A9FDD',
  ink: '#E7ECF0',
  ink2: '#9AA3AF',
  line: '#303B4A',
  tooltipBg: 'rgba(27,34,48,0.96)',
  tooltipBorder: '#3A4658',
}

// 与 --font-display 的字体栈一致（该字体只在本机装了才生效，否则逐级回退）
export const CHART_FONT = '"Avenir Next","PingFang SC","Microsoft YaHei","Segoe UI",system-ui,sans-serif'

export function currentTheme() {
  if (typeof document === 'undefined') return 'light'
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

export function readChartPalette(theme = currentTheme()) {
  return { ...(theme === 'dark' ? DARK : LIGHT) }
}

export function chartSeries(palette = readChartPalette()) {
  return [palette.brand, palette.accent, palette.success, palette.warning, palette.error, palette.info]
}

// 图表通用基底（文字/图例/提示框/坐标色），与各图 option 合并即可统一观感
export function chartBase(palette = readChartPalette()) {
  return {
    color: chartSeries(palette),
    textStyle: { color: palette.ink, fontFamily: CHART_FONT },
    legend: { textStyle: { color: palette.ink2 } },
    tooltip: {
      backgroundColor: palette.tooltipBg,
      borderColor: palette.tooltipBorder,
      textStyle: { color: palette.ink },
    },
  }
}

// 兼容性导出：模块加载时的亮色快照。新代码请直接用 readChartPalette()。
export const chartPalette = { ...LIGHT }
export const seriesColors = chartSeries(chartPalette)
