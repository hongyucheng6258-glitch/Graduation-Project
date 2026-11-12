<template>
  <div v-if="entry || view?.canJoin" class="waitlist-actions" aria-live="polite">
    <p v-if="entry">{{ waitlistLabel(entry) }}</p>
    <p v-if="state === 'OFFERED'">请在 {{ formatTime(entry.offerUntil) }} 前确认（北京时间）。</p>
    <el-button v-if="view?.canJoin" type="primary" plain :loading="busy" @click="join">加入候补</el-button>
    <el-button v-if="state === 'OFFERED'" type="primary" :loading="busy" @click="accepting = true">确认递补</el-button>
    <el-button v-if="['WAITING','OFFERED'].includes(state)" :disabled="busy" @click="leave">退出候补</el-button>
    <el-button v-if="state === 'WAITING'" text :disabled="busy" @click="load">刷新候补状态</el-button>
  </div>
  <el-dialog v-model="accepting" title="确认递补报名" width="min(440px, calc(100vw - 24px))">
    <SignupConflictNotice v-if="accepting" :activity-id="activityId" @checking="checking = $event" />
    <el-input v-model="remark" type="textarea" maxlength="255" placeholder="报名备注（可选）" />
    <template #footer>
      <el-button @click="accepting = false">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="checking" @click="accept">提交递补报名</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { activityWaitlist, joinActivityWaitlist, leaveActivityWaitlist, acceptActivityWaitlist } from '../../../api/activity'
import { formatTime } from '../../../utils/date'
import SignupConflictNotice from './SignupConflictNotice.vue'
import { waitlistLabel, waitlistState } from './waitlist-state'

const props = defineProps({ activityId: { type: Number, required: true }, signupStatus: Number })
const emit = defineEmits(['changed', 'priority'])
const view = ref(null)
const busy = ref(false)
const accepting = ref(false)
const checking = ref(false)
const remark = ref('')
const entry = computed(() => view.value?.entry)
const state = computed(() => waitlistState(entry.value))

async function load() {
  try {
    view.value = await activityWaitlist(props.activityId)
    emit('priority', view.value.canJoin || ['WAITING', 'OFFERED'].includes(state.value))
  } catch {
    // The ordinary signup flow remains available if this optional query fails.
  }
}

async function change(action, success) {
  if (busy.value) return
  busy.value = true
  try {
    await action()
    ElMessage.success(success)
    emit('changed')
  } catch {
    // The request wrapper shows the reason and refresh below reconciles stale state.
  } finally {
    await load()
    busy.value = false
  }
}

function join() {
  return change(() => joinActivityWaitlist(props.activityId), '已加入候补')
}

function leave() {
  return change(() => leaveActivityWaitlist(props.activityId, entry.value.id), '已退出候补')
}

function accept() {
  return change(async () => {
    await acceptActivityWaitlist(props.activityId, entry.value.id, { remark: remark.value })
    accepting.value = false
  }, '递补报名已提交，等待发布者审批')
}

watch(() => [props.activityId, props.signupStatus], load, { immediate: true })
</script>

<style scoped>
.waitlist-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.waitlist-actions p { width: 100%; margin: 0; }
.waitlist-actions .el-button { margin: 0; }
</style>
