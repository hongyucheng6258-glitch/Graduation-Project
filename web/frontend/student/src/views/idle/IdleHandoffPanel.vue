<template>
  <section class="handoff-panel" aria-label="交接安排">
    <p v-if="loading">正在加载交接安排…</p>
    <template v-else-if="failed">
      <p>暂时无法加载交接安排。</p>
      <el-button size="small" @click="load">重试</el-button>
    </template>
    <template v-else>
      <p v-if="plan?.meetingAt">
        {{ formatTime(plan.meetingAt) }}（北京时间） · {{ plan.location }}
      </p>
      <p v-if="plan?.confirmed">{{ myDone ? '你已确认交接，等待对方确认' : '交接安排已确认' }}</p>
      <p v-else-if="plan?.meetingAt">{{ mine ? '等待对方确认安排' : '对方提出了交接安排' }}</p>
      <p v-else>尚未约定交接时间与地点。</p>

      <form v-if="editing" class="handoff-form" @submit.prevent="propose">
        <label>交接时间（北京时间）
          <input v-model="draft.meetingAt" type="datetime-local" required />
        </label>
        <label>交接地点
          <input v-model.trim="draft.location" maxlength="128" required placeholder="如图书馆一楼服务台" />
        </label>
        <div class="handoff-actions">
          <el-button @click="editing = false">取消</el-button>
          <el-button native-type="submit" type="primary" :loading="busy">发送交接安排</el-button>
        </div>
      </form>
      <div v-else class="handoff-actions">
        <el-button v-if="!plan?.buyerDone && !plan?.sellerDone" size="small" :disabled="busy" @click="edit">
          {{ plan?.meetingAt ? '提出改约' : '提出交接安排' }}
        </el-button>
        <el-button v-if="plan?.meetingAt && !plan.confirmed && !mine" type="primary" size="small"
          :loading="busy" @click="confirm">同意交接安排</el-button>
        <el-button v-if="plan?.confirmed && !myDone" type="primary" size="small" :loading="busy" @click="finish">
          确认已交接
        </el-button>
        <el-button v-if="!plan" size="small" :loading="busy" @click="finish">确认完成</el-button>
        <el-button v-if="plan" text size="small" :disabled="busy" @click="load">刷新交接状态</el-button>
      </div>
      <p v-if="plan?.confirmed && !myDone" class="handoff-hint">到约定时间并完成互换后，双方分别确认即可评价。</p>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmIdleHandoff, finishAppoint, getIdleHandoff, proposeIdleHandoff } from '../../api/idle'
import { useUserStore } from '../../store/user'
import { formatTime } from '../../utils/date'

const props = defineProps({ appointmentId: { type: [Number, String], required: true }, role: { type: String, required: true } })
const emit = defineEmits(['updated'])
const user = useUserStore()
const plan = ref(null)
const loading = ref(true)
const failed = ref(false)
const busy = ref(false)
const editing = ref(false)
const draft = reactive({ meetingAt: '', location: '' })
const mine = computed(() => String(plan.value?.proposedBy) === String(user.userInfo?.id))
const myDone = computed(() => props.role === 'buyer' ? plan.value?.buyerDone : plan.value?.sellerDone)

async function load() {
  loading.value = true
  failed.value = false
  try {
    plan.value = await getIdleHandoff(props.appointmentId)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function edit() {
  draft.meetingAt = plan.value?.meetingAt?.slice(0, 16).replace(' ', 'T') || ''
  draft.location = plan.value?.location || ''
  editing.value = true
}

async function update(action, success) {
  if (busy.value) return
  busy.value = true
  try {
    await action()
    ElMessage.success(success)
  } catch {
    // The request wrapper presents the failure.
  } finally {
    busy.value = false
  }
}

function propose() {
  return update(async () => {
    plan.value = await proposeIdleHandoff(props.appointmentId, { ...draft, version: plan.value?.version ?? 0 })
    editing.value = false
  }, '交接安排已发送')
}

function confirm() {
  return update(async () => {
    plan.value = await confirmIdleHandoff(props.appointmentId, plan.value.version)
  }, '已同意交接安排')
}

async function finish() {
  try {
    await ElMessageBox.confirm('确定已实际完成这次互换？', '确认交接', { type: 'warning' })
  } catch {
    return
  }
  await update(async () => {
    await finishAppoint(props.appointmentId)
    await load()
    emit('updated')
  }, '已确认交接')
}

onMounted(load)
</script>

<style scoped>
.handoff-panel { display: grid; gap: 8px; margin-top: 10px; padding: 10px; background: var(--surface-2); border-radius: 8px; }
.handoff-panel p { margin: 0; }
.handoff-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.handoff-actions .el-button { margin: 0; }
.handoff-form, .handoff-form label { display: grid; gap: 6px; }
.handoff-form input { width: 100%; min-width: 0; padding: 6px; font: inherit; border: 1px solid var(--line); border-radius: 6px; }
.handoff-hint { font-size: .875rem; color: var(--ink-3); }
</style>
