<template>
  <section aria-label="校园日程" class="campus-schedule">
    <RouterLink :to="{ name: 'WrongBook' }" class="review-link">
      {{ reviewCount == null ? '查看错题复习' : `今日待复习 ${reviewCount} 题` }}
    </RouterLink>
    <div v-loading="loading" class="record-list" aria-live="polite">
      <div v-if="failed">
        <p>暂时无法加载日程。</p>
        <el-button size="small" @click="loadSchedule">重试</el-button>
      </div>
      <template v-else-if="schedule">
        <p class="record-meta">{{ schedule.from }} 至 {{ schedule.through }}（北京时间）</p>
        <section v-for="[day, entries] in days" :key="day">
          <h3 class="schedule-day">{{ day }}</h3>
          <article v-for="entry in entries" :key="entry.activityId" class="record-row">
            <header class="record-head">
              <RouterLink :to="{ name: 'ActivityDetail', params: { id: entry.activityId } }">{{ entry.title }}</RouterLink>
              <el-tag :type="entry.signupStatus === 1 ? 'success' : 'warning'" size="small">
                {{ entry.signupStatus === 1 ? '已通过' : '待审批' }}
              </el-tag>
            </header>
            <div class="record-meta">
              <span>{{ timeRange(entry) }}</span>
              <span>{{ entry.location || '地点待定' }}</span>
            </div>
          </article>
        </section>
        <EmptyBox v-if="!days.size" description="这七天暂无活动安排" />
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import EmptyBox from '../../../components/EmptyBox.vue'
import { myActivitySchedule } from '../../../api/activity'
import { wrongStats } from '../../../api/wrong'

const schedule = ref(null)
const reviewCount = ref(null)
const loading = ref(true)
const failed = ref(false)

const days = computed(() => {
  const groups = new Map()
  if (!schedule.value) return groups
  for (const entry of schedule.value.entries) {
    const start = entry.startTime?.slice(0, 10)
    const day = start ? (start < schedule.value.from ? schedule.value.from : start) : '日期待定'
    if (!groups.has(day)) groups.set(day, [])
    groups.get(day).push(entry)
  }
  return groups
})

function timeRange(entry) {
  if (!entry.startTime) return '开始时间待定'
  const start = entry.startTime.slice(0, 16).replace('T', ' ')
  if (!entry.endTime) return `${start} 开始，结束时间待定`
  const sameDay = entry.endTime.slice(0, 10) === entry.startTime.slice(0, 10)
  const end = sameDay ? entry.endTime.slice(11, 16) : entry.endTime.slice(0, 16).replace('T', ' ')
  return `${start} 至 ${end}`
}

async function loadSchedule() {
  loading.value = true
  failed.value = false
  try {
    schedule.value = await myActivitySchedule()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSchedule()
  wrongStats({ silent: true, skipMaintenanceRedirect: true }).then(stats => { reviewCount.value = stats.todayPending }).catch(() => {})
})
</script>

<style scoped src="../../../styles/records.css" />
<style scoped>
.campus-schedule .record-list { min-height: 64px; }
.review-link { display: inline-block; margin-bottom: 12px; }
.schedule-day { font-size: 1rem; margin: 12px 0 6px; }
</style>
