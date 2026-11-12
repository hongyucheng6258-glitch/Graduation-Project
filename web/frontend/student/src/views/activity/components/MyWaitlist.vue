<template>
  <div v-loading="loading" class="record-list">
    <div v-if="failed">
      <p>暂时无法加载候补记录。</p>
      <el-button @click="load">重试</el-button>
    </div>
    <article v-for="row in rows" :key="row.id" class="record-row">
      <header class="record-head">
        <RouterLink :to="{ name: 'ActivityDetail', params: { id: row.activityId } }">{{ row.title }}</RouterLink>
        <el-tag size="small">{{ waitlistLabel(row) }}</el-tag>
      </header>
      <p v-if="waitlistState(row) === 'OFFERED'" class="record-meta">确认截止 {{ formatTime(row.offerUntil) }}</p>
    </article>
    <EmptyBox v-if="!loading && !failed && !rows.length" description="暂无候补记录" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import EmptyBox from '../../../components/EmptyBox.vue'
import { myActivityWaitlist } from '../../../api/activity'
import { formatTime } from '../../../utils/date'
import { waitlistLabel, waitlistState } from './waitlist-state'

const rows = ref([])
const loading = ref(true)
const failed = ref(false)

async function load() {
  loading.value = true
  failed.value = false
  try {
    rows.value = await myActivityWaitlist()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped src="../../../styles/records.css" />
