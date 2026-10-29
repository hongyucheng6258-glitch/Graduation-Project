<template>
  <div class="dashboard">
    <!-- V2 问候头 -->
    <section class="admin-greeting">
      <div>
        <p class="greet-date">{{ dateLabel }}</p>
        <h1>今天的校园，也交给你了。</h1>
        <span class="greet-sub">辛苦啦，这里是今日的运营概览。</span>
      </div>
      <span class="system-pill" :class="{ warn: errors.length }">
        <i></i>
        {{ loading ? '正在更新概览' : errors.length ? '部分数据加载失败' : '概览数据已更新' }}
      </span>
    </section>

    <div v-if="errors.length" class="dashboard-error" role="alert">
      <span>未能加载：{{ errors.join('、') }}。空白区域不代表没有数据。</span>
      <button class="text-btn" :disabled="loading" @click="loadDashboard">重新加载</button>
    </div>
    <!-- 统计卡 -->
    <section class="stat-grid">
      <div v-for="c in cards" :key="c.label" class="stat-card">
        <span class="stat-icon" :style="{ background: c.soft, color: c.color }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" v-html="c.icon"></svg>
        </span>
        <div>
          <b>{{ c.value }}</b>
          <small>{{ c.label }}</small>
        </div>
      </div>
    </section>

    <!-- 图表区 -->
    <section class="admin-charts">
      <div class="chart-panel trend-panel">
        <div class="panel-head">
          <h3>近30天趋势</h3>
          <span>累计用户 / AI 调用量</span>
        </div>
        <div ref="trendRef" class="chart" />
      </div>
      <div class="chart-panel donut-panel">
        <div class="panel-head">
          <h3>失物状态</h3>
          <span>分布一览</span>
        </div>
        <div ref="pieRef" class="chart donut" />
      </div>
    </section>

    <!-- 待办审核 + 模块发布量 -->
    <section class="admin-panels">
      <div class="chart-panel">
        <div class="panel-head">
          <h3>待审核内容</h3>
          <span>内容合规，随时在线。</span>
        </div>
        <div class="audit-table">
          <div v-for="a in pendingList" :key="a.key" class="audit-row">
            <span class="audit-type" :class="typeCls(a.type)">{{ typeText(a.type) }}</span>
            <div>
              <b>{{ a.title }}</b>
              <small>{{ a.submitter }} · {{ formatTime(a.createTime) }}</small>
            </div>
            <button type="button" class="text-btn" @click="goAudit(a.type)">处理 →</button>
          </div>
          <div v-if="loading" class="audit-empty" role="status">正在读取待审核内容…</div>
          <div v-else-if="errors.includes('待审核内容')" class="audit-empty">读取失败，请重试。</div>
          <div v-else-if="!pendingList.length" class="audit-empty">暂无待审核内容</div>
        </div>
      </div>
      <div class="chart-panel">
        <div class="panel-head">
          <h3>各模块发布量</h3>
          <span>内容生态一览</span>
        </div>
        <div ref="moduleRef" class="chart bar-chart" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
])
import { statsOverview, statsTrend, statsModule, statsPie } from '../../api/stats'
import { auditList } from '../../api/audit'
import { chartPalette, seriesColors, chartBase } from '../../utils/chartTheme'

const router = useRouter()
const overview = ref({})
const trendRef = ref()
const moduleRef = ref()
const pieRef = ref()
const pendingList = ref([])
let charts = []
const loading = ref(true)
const errors = ref([])
let disposed = false
const resizeObserver = new ResizeObserver(() => charts.forEach(chart => chart.resize()))
function disposeCharts() { resizeObserver.disconnect(); charts.forEach(chart => chart.dispose()); charts = [] }

const ICONS = {
  users: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
  spark: '<path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/>',
  ai: '<path d="M9 3h6M10 3v3M14 3v3M12 7v2"/><rect x="5" y="12" width="14" height="9" rx="2"/><path d="M8 16h.01M12 16h.01M16 16h.01"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'
}

const dateLabel = (() => {
  const d = new Date()
  const week = '日一二三四五六'[d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 周${week}`
})()

const cards = computed(() => [
  { label: '总用户数', value: overview.value.totalUsers ?? '-', icon: ICONS.users, color: chartPalette.brand, soft: 'rgba(49,92,245,0.1)' },
  { label: '今日活跃', value: overview.value.todayActiveUsers ?? '-', icon: ICONS.spark, color: chartPalette.success, soft: 'rgba(76,164,106,0.12)' },
  { label: '今日AI调用', value: overview.value.todayAiCalls ?? '-', icon: ICONS.ai, color: chartPalette.accent, soft: 'rgba(242,161,92,0.14)' },
  { label: '待审核内容', value: overview.value.pendingAudits ?? '-', icon: ICONS.clock, color: chartPalette.warning, soft: 'rgba(224,169,63,0.13)' }
])

const TYPE_META = {
  activity: { text: '活动', cls: 'blue' },
  idle: { text: '闲置', cls: 'peach' },
  lost: { text: '失物', cls: 'sage' },
  post: { text: '动态', cls: 'purple' },
  partner: { text: '搭子', cls: 'blue' },
  qa: { text: '问答', cls: 'peach' }
}
function typeText(t) { return TYPE_META[t]?.text || t }
function typeCls(t) { return TYPE_META[t]?.cls || 'blue' }

function formatTime(t) {
  if (!t) return ''
  return String(t).slice(0, 16)
}

function goAudit(type) {
  const module = { lost: 'lostfound', lostfound: 'lostfound', activity: 'activity', idle: 'idle', post: 'post', partner: 'partner' }[type]
  router.push(module ? `/audit/${module}` : '/content')
}

async function loadDashboard() {
  loading.value = true
  errors.value = []
  overview.value = {}
  pendingList.value = []
  disposeCharts()
  await Promise.all([ (async () => {
  // 数字卡片
  try { overview.value = await statsOverview() } catch (e) { errors.value.push('数字概览') }
  })(), (async () => {
  // 待审核（前5条）
  try {
    const res = await auditList({ pageNum: 1, pageSize: 5 })
    const arr = Array.isArray(res) ? res : (res.list || [])
    pendingList.value = arr.map((x) => ({ key: `${x.type}-${x.id}`, ...x }))
  } catch (e) { errors.value.push('待审核内容') }
  })(), (async () => {
  // 双折线
  try {
    const trend = await statsTrend()
    renderChart(trendRef.value, {
      tooltip: { trigger: 'axis' },
      legend: { data: ['累计用户', 'AI调用量'], top: 4 },
      xAxis: { type: 'category', data: trend.dates, axisLine: { lineStyle: { color: chartPalette.line } }, axisLabel: { color: chartPalette.ink2 } },
      yAxis: [
        { type: 'value', name: '用户', axisLine: { lineStyle: { color: chartPalette.line } }, axisLabel: { color: chartPalette.ink2 }, splitLine: { lineStyle: { color: chartPalette.line } } },
        { type: 'value', name: '调用', axisLine: { lineStyle: { color: chartPalette.line } }, axisLabel: { color: chartPalette.ink2 }, splitLine: { show: false } }
      ],
      series: [
        { name: '累计用户', type: 'line', smooth: true, data: trend.userGrowth, areaStyle: { color: 'rgba(49,92,245,0.12)' }, color: seriesColors[0] },
        { name: 'AI调用量', type: 'line', smooth: true, yAxisIndex: 1, data: trend.aiCalls, color: seriesColors[1] }
      ],
      grid: { left: 45, right: 45, bottom: 30, top: 38 }
    })
  } catch (e) { errors.value.push('趋势图') }
  })(), (async () => {
  // 模块发布量柱状
  try {
    const moduleData = await statsModule()
    renderChart(moduleRef.value, {
      tooltip: {},
      xAxis: { type: 'category', data: moduleData.map((d) => d.name), axisLine: { lineStyle: { color: chartPalette.line } }, axisLabel: { color: chartPalette.ink2 } },
      yAxis: { type: 'value', axisLine: { lineStyle: { color: chartPalette.line } }, axisLabel: { color: chartPalette.ink2 }, splitLine: { lineStyle: { color: chartPalette.line } } },
      series: [{ type: 'bar', data: moduleData.map((d) => d.value), color: seriesColors[0], barWidth: 34, itemStyle: { borderRadius: [6, 6, 0, 0] } }],
      grid: { left: 35, right: 15, bottom: 25, top: 15 }
    })
  } catch (e) { errors.value.push('模块发布量') }
  })(), (async () => {
  // 双饼图
  try {
    const pie = await statsPie()
    renderChart(pieRef.value, {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: chartPalette.ink2 } },
      series: [
        {
          name: '失物状态', type: 'pie', radius: ['46%', '74%'], center: ['50%', '43%'],
          data: pie.lostStatus, label: { show: false }, emphasis: { label: { show: true, formatter: '{b}: {c}' } }, color: seriesColors
        }
      ]
    })
  } catch (e) { errors.value.push('失物状态') }
  })() ])
  loading.value = false
}
onMounted(loadDashboard)
onBeforeUnmount(() => { disposed = true; disposeCharts() })

// 合并梧桐校园图表基底（文字/提示框/图例色），保证大屏观感统一
function mergeTheme(option) {
  const base = chartBase()
  return {
    ...base,
    ...option,
    textStyle: { ...base.textStyle, ...(option.textStyle || {}) },
    tooltip: option.tooltip ? { ...base.tooltip, ...option.tooltip } : base.tooltip,
    legend: option.legend ? { ...base.legend, ...option.legend } : base.legend,
  }
}

function renderChart(el, option) {
  if (!el || disposed) return
  const chart = echarts.init(el)
  chart.setOption(mergeTheme(option))
  charts.push(chart)
  resizeObserver.observe(el)
}
</script>

<style scoped>
.dashboard-error { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 14px 18px; border: 1px solid var(--brand-line); border-radius: 12px; background: var(--surface); color: var(--ink-2); }
.dashboard { display: flex; flex-direction: column; gap: 20px; }

/* —— V2 问候头 —— */
.admin-greeting {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 15px;
  background: var(--brand-soft);
  border: 1px solid var(--brand-line);
  border-radius: 16px;
  padding: 26px 30px;
  margin-bottom: 4px;
}
.greet-date { font-size: 12px; color: var(--brand-strong); margin: 0 0 7px; }
.admin-greeting h1 { font-size: 25px; font-weight: 700; color: var(--ink); letter-spacing: -.4px; margin: 0; }
.greet-sub { display: block; font-size: 12px; color: var(--ink-3); margin-top: 8px; }
.system-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--brand-line);
  background: var(--surface);
  border-radius: 50px;
  padding: 6px 13px;
  font-size: 11px;
  color: var(--ink-2);
  flex-shrink: 0;
}
.system-pill i { width: 7px; height: 7px; border-radius: 50%; background: var(--success); }
.system-pill.warn { color: #b45309; background: #fffbeb; border-color: #fde68a; }
.system-pill.warn i { background: #f59e0b; box-shadow: 0 0 0 3px rgba(245, 158, 11, .18); }

/* —— 统计卡 —— */
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 15px; }
.stat-card {
  display: flex;
  align-items: center;
  gap: 15px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 19px 21px;
  min-width: 0;
}
.stat-icon {
  width: 45px;
  height: 45px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.stat-icon svg { width: 21px; height: 21px; }
.stat-card b { display: block; font-size: 27px; font-weight: 700; color: var(--ink); letter-spacing: -.6px; line-height: 1.1; }
.stat-card small { display: block; font-size: 11px; color: var(--ink-3); margin-top: 4px; white-space: nowrap; }

/* —— 图表 —— */
.admin-charts { display: grid; grid-template-columns: 1.7fr 1fr; gap: 15px; }
.admin-panels { display: grid; grid-template-columns: 1fr 1.2fr; gap: 15px; }
.chart-panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 19px 21px;
  min-width: 0;
}
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 13px; }
.panel-head h3 { font-size: 15px; font-weight: 650; color: var(--ink); margin: 0; }
.panel-head span { font-size: 11px; color: var(--ink-3); }
.chart { height: 300px; width: 100%; }
.chart.donut { height: 285px; }
.chart.bar-chart { height: 248px; }

/* —— 待办审核 —— */
.audit-table { display: flex; flex-direction: column; }
.audit-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid var(--line);
  min-width: 0;
}
.audit-row:last-child { border-bottom: 0; }
.audit-type {
  font-size: 10px;
  font-weight: 550;
  padding: 4px 9px;
  border-radius: 6px;
  flex-shrink: 0;
  min-width: 42px;
  text-align: center;
}
.audit-type.blue { background: var(--brand-soft); color: var(--brand); }
.audit-type.peach { background: var(--peach); color: var(--accent-strong); }
.audit-type.sage { background: var(--sage); color: var(--success); }
.audit-type.purple { background: var(--purple-soft); color: var(--purple-strong); }
.audit-row > div { flex: 1; min-width: 0; }
.audit-row b { display: block; font-size: 13px; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.audit-row small { display: block; font-size: 11px; color: var(--ink-3); margin-top: 3px; }
.text-btn {
  border: 0;
  background: none;
  color: var(--brand);
  font-size: 12px;
  padding: 3px 0;
  display: inline-flex;
  gap: 7px;
  align-items: center;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}
.text-btn:hover { text-decoration: underline; text-underline-offset: 4px; }
.audit-empty { text-align: center; color: var(--ink-3); font-size: 12px; padding: 30px 0; }

@media (max-width: 1100px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .admin-charts, .admin-panels { grid-template-columns: 1fr; }
  .chart.bar-chart { height: 260px; }
}
@media (max-width: 760px) {
  .admin-greeting { padding: 21px; align-items: flex-start; }
  .admin-greeting h1 { font-size: 21px; }
  .system-pill { display: none; }
  .stat-card { padding: 15px 16px; gap: 12px; }
  .stat-icon { width: 38px; height: 38px; }
  .stat-card b { font-size: 22px; }
  .chart-panel { padding: 16px; }
  .panel-head span { display: none; }
}
</style>
