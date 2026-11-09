<template>
  <WtPageHeader :title="editId ? '编辑活动' : '发布活动'" :subtitle="editId ? '修改活动信息，保存后重新进入审核' : '发起一场属于同学们的聚会'" eyebrow="校园服务" />

  <div class="publish">
    <el-card>
      <template #header><h3>发布活动</h3></template>
      <el-alert :type="draftError ? 'warning' : 'info'" :closable="false" :title="draftNotice" style="margin-bottom: 16px" />
      <RouterLink v-if="draftState === 'SUBMITTED'" :to="{ name: 'MySignup', query: { tab: 'published' } }">查看我的活动发布</RouterLink>
      <RouterLink v-else-if="draftError" to="/ai/chat">返回校园向导</RouterLink>
      <AiAssistPanel v-if="draftReady" type="activity" type-name="活动" @fill="applyAssist" />
      <el-form v-if="draftReady" :model="form" label-width="90px" style="max-width: 640px">
        <el-form-item label="活动标题" required>
          <el-input v-model="form.title" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动描述">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="如：东区操场 / 图书馆三楼" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="报名截止">
          <el-date-picker v-model="form.signupDeadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="人数上限">
          <el-input-number v-model="form.maxMembers" :min="0" :max="500" />
          <span class="tip">（0 表示不限）</span>
        </el-form-item>
        <el-form-item label="图片">
          <UploadImg v-model="form.images" :max="3" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交审核</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import UploadImg from '../../components/UploadImg.vue'
import AiAssistPanel from '../../components/AiAssistPanel.vue'
import { publishActivity, updateActivity, activityDetail } from '../../api/activity'
import { loadActivityDraft, confirmActivityDraft } from '../../api/ai'

const router = useRouter()
const route = useRoute()
const editId = computed(() => typeof route.query.id === 'string' && /^[1-9]\d*$/.test(route.query.id) ? route.query.id : '')
const draftId = computed(() => typeof route.query.draft === 'string' ? route.query.draft : '')
const draftState = ref('')
const draftError = ref('')
const draftReady = computed(() => !draftError.value && draftState.value !== 'LOADING' && (!draftId.value || draftState.value === 'DRAFT'))
const draftNotice = computed(() => draftError.value || ({
  LOADING: draftId.value ? '正在加载活动草稿…' : '正在加载活动信息…',
  DRAFT: '请核对活动草稿，可以修改内容或补充图片，确认后提交审核。',
  SUBMITTED: '这份活动草稿已提交，可在我的发布查看审核进度。'
})[draftState.value] || '审核通过后，活动才会公开展示。')
const categories = ['学习交流', '体育运动', '文艺娱乐', '志愿服务', '竞赛组队', '其他']
const emptyForm = () => ({
  title: '', category: '', description: '', location: '',
  startTime: '', endTime: '', signupDeadline: '', maxMembers: 0, images: []
})
const form = reactive(emptyForm())
const submitting = ref(false)
let loadVersion = 0
onBeforeUnmount(() => { loadVersion += 1 })

watch([() => route.query.id, () => route.query.draft], async ([rawId, rawDraft]) => {
  const version = ++loadVersion
  draftState.value = ''
  draftError.value = ''
  Object.assign(form, emptyForm())
  if ((rawId !== undefined && (!editId.value || rawDraft !== undefined)) || (rawDraft !== undefined && !draftId.value)) {
    draftError.value = '活动链接无效，请从活动列表或校园向导重新打开。'
    return
  }
  if (!draftId.value && !editId.value) return
  draftState.value = 'LOADING'
  try {
    if (draftId.value) {
      const draft = await loadActivityDraft(draftId.value)
      if (version !== loadVersion) return
      draftState.value = draft.state
      if (draft.state === 'EXPIRED') draftError.value = '活动草稿已过期，请回到校园向导重新生成。'
      if (draft.state === 'DRAFT') fillForm(draft)
    } else {
      const activity = await activityDetail(editId.value)
      if (version !== loadVersion) return
      fillForm(activity)
      form.images = activity.imageList || []
      draftState.value = ''
    }
  } catch (e) {
    if (version !== loadVersion) return
    draftError.value = e.message || '加载活动信息失败'
  }
}, { immediate: true })

function fillForm(activity) {
  for (const field of ['title', 'category', 'description', 'location']) form[field] = activity[field] || ''
  for (const field of ['startTime', 'endTime', 'signupDeadline']) {
    const value = (activity[field] || '').replace('T', ' ')
    form[field] = value.length === 16 ? `${value}:00` : value
  }
  form.maxMembers = activity.maxMembers ?? (draftId.value ? undefined : 0)
}

function applyAssist(data) {
  if (data.title) form.title = data.title
  if (data.content) form.description = data.content
}
async function submit() {
  if (submitting.value || !draftReady.value) return
  if (!form.title.trim()) {
    ElMessage.warning('请填写活动标题')
    return
  }
  submitting.value = true
  const version = loadVersion
  const currentDraft = draftId.value
  const currentEdit = editId.value
  try {
    if (currentDraft) {
      await confirmActivityDraft(currentDraft, { ...form })
      if (version !== loadVersion) return
      draftState.value = 'SUBMITTED'
      ElMessage.success('活动已提交，可在我的发布查看审核进度')
      await router.push({ name: 'MySignup', query: { tab: 'published' } })
      return
    }
    if (currentEdit) {
      await updateActivity(currentEdit, { ...form })
      if (version !== loadVersion) return
      ElMessage.success('已保存，重新进入审核')
    } else {
      await publishActivity({ ...form })
      if (version !== loadVersion) return
      ElMessage.success('已提交，待管理员审核')
    }
    router.push('/activity/my-signup')
  } catch (e) {
    if (currentDraft && version === loadVersion) ElMessage.error(e.message || '活动提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.publish {
  max-width: 760px;
  margin: 0 auto;
}
.tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
