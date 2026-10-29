<template>
  <WtPageHeader title="发布搭子信息" subtitle="写下你的学习需求，AI 帮你找搭子" eyebrow="校园服务" />

  <div class="publish">
    <el-card>
      <template #header><h3>学习搭子信息</h3></template>
      <el-alert type="info" :closable="false" title="发布后需管理员审核通过才会公开展示" style="margin-bottom: 16px" />
      <el-form :model="form" label-width="90px" style="max-width: 640px">
        <el-form-item label="科目/领域" required>
          <el-input v-model="form.subject" maxlength="32" show-word-limit placeholder="如：高等数学 / 英语四六级 / Java" />
        </el-form-item>
        <el-form-item label="目标">
          <el-input v-model="form.goal" maxlength="64" placeholder="如：考研 / 过六级 / 组队打比赛" />
        </el-form-item>
        <el-form-item label="可搭时间">
          <el-input v-model="form.schedule" maxlength="64" placeholder="如：工作日晚上 / 周末全天" />
        </el-form-item>
        <el-form-item label="自我介绍">
          <el-input v-model="form.intro" type="textarea" :rows="3" placeholder="性格、水平、想找什么样的搭子…" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="form.contact" placeholder="手机号/微信号/QQ" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交审核</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>

      <el-divider />
      <div class="match-head">
        <h3>🤖 AI 智能匹配</h3>
        <span class="match-tip">填写科目和目标后，AI 从校园里挑选最合适的搭子</span>
      </div>
      <el-button
        type="success"
        plain
        :loading="matching"
        :disabled="!form.subject.trim()"
        @click="doMatch"
      >{{ matching ? 'AI 匹配中…' : '帮我找搭子（AI 匹配）' }}</el-button>

      <div v-if="matches.length" class="match-list">
        <div v-for="m in matches" :key="m.id" class="match-card">
          <div class="match-card__main">
            <b>🎯 推荐搭子：{{ m.subject }}</b>
            <span v-if="m.reason" class="match-reason">{{ m.reason }}</span>
            <div class="match-meta">
              <span v-if="m.goal">🎯 {{ m.goal }}</span>
              <span v-if="m.schedule">🕐 {{ m.schedule }}</span>
              <span v-if="m.contact">📞 {{ m.contact }}</span>
              <span v-if="m.publisherNickname">👤 {{ m.publisherNickname }}</span>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else-if="matchedDone" description="暂未找到合适的搭子，可稍后再试或发布信息等大家来找你" :image-size="70" />
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { publishPartner, partnerMatch } from '../../api/partner'

const router = useRouter()
const form = reactive({ subject: '', goal: '', schedule: '', intro: '', contact: '' })
const submitting = ref(false)
const matching = ref(false)
const matchedDone = ref(false)
const matches = ref([])

async function submit() {
  if (!form.subject.trim()) {
    ElMessage.warning('请填写科目/领域')
    return
  }
  submitting.value = true
  try {
    await publishPartner({
      subject: form.subject.trim(),
      goal: form.goal.trim(),
      schedule: form.schedule.trim(),
      intro: form.intro.trim(),
      contact: form.contact.trim()
    })
    ElMessage.success('已提交，待管理员审核')
    router.push('/partner')
  } finally {
    submitting.value = false
  }
}

async function doMatch() {
  if (!form.subject.trim()) {
    ElMessage.warning('请先填写科目/领域')
    return
  }
  matching.value = true
  matches.value = []
  matchedDone.value = false
  try {
    const res = await partnerMatch({
      subject: form.subject.trim(),
      goal: form.goal.trim(),
      schedule: form.schedule.trim(),
      intro: form.intro.trim()
    })
    matches.value = Array.isArray(res) ? res : (res.list || [])
    if (!matches.value.length) ElMessage.info('暂未找到合适的搭子')
  } catch (e) {
    ElMessage.error(e.message || 'AI 匹配失败，请稍后重试')
  } finally {
    matching.value = false
    matchedDone.value = true
  }
}
</script>

<style scoped>
.publish {
  max-width: 760px;
  margin: 0 auto;
}
.match-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.match-head h3 { margin: 0; font-size: var(--fs-body); }
.match-tip { font-size: var(--fs-cap); color: var(--ink-3); }
.match-list { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
.match-card {
  padding: 14px 16px; border: 1px solid var(--success-line, #b3e6d0);
  border-radius: var(--r-md); background: var(--success-soft, #f0faf5);
}
.match-card__main b { display: block; font-size: var(--fs-sm); margin-bottom: 4px; }
.match-reason { display: block; font-size: var(--fs-cap); color: var(--success-strong, #008a5c); margin-bottom: 6px; }
.match-meta { display: flex; flex-wrap: wrap; gap: 12px; font-size: var(--fs-cap); color: var(--ink-2); }
</style>
