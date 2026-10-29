<template>
  <WtPageHeader :title="editId ? '编辑信息' : '发布招领'" :subtitle="editId ? '修改信息，保存后重新进入审核' : '帮物品找到它的主人'" eyebrow="校园服务" />

  <div class="publish">
    <el-card>
      <template #header><h3>发布失物/招领信息</h3></template>
      <el-alert type="info" :closable="false" title="发布后需管理员审核通过才会公开展示" style="margin-bottom: 16px" />
      <AiAssistPanel :type="'lostfound'" type-name="失物招领" @fill="applyAssist" />
      <el-form :model="form" label-width="90px" style="max-width: 640px">
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.type">
            <el-radio :value="0">我丢了东西（失物）</el-radio>
            <el-radio :value="1">我捡到东西（招领）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="64" show-word-limit placeholder="如：图书馆丢失黑色钱包" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="物品特征、时间地点等" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker v-model="form.happenTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="form.contact" placeholder="手机号/微信号/QQ" />
        </el-form-item>
        <el-form-item label="图片">
          <UploadImg v-model="form.images" :max="6" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交审核</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>

      <!-- AI 智能匹配：仅"我丢了东西"时显示 -->
      <template v-if="form.type === 0 && !editId">
        <el-divider />
        <div class="match-head">
          <h3>🤖 AI 智能匹配</h3>
          <span class="match-tip">填写标题和特征后，AI 自动比对校园里登记过的拾到信息，命中会直接告诉你"可能找到了！"</span>
        </div>
        <el-button
          type="success"
          plain
          :loading="matching"
          :disabled="!form.title.trim()"
          @click="doMatch"
        >{{ matching ? 'AI 匹配中…' : '帮我找找（AI 匹配）' }}</el-button>

        <div v-if="matches.length" class="match-list">
          <div v-for="m in matches" :key="m.id" class="match-card">
            <div class="match-card__main">
              <b>🎯 可能找到了！{{ m.title }}</b>
              <span class="match-reason">{{ m.reason }}</span>
              <div class="match-meta">
                <span v-if="m.location">📍 {{ m.location }}</span>
                <span v-if="m.happenTime">🕐 {{ m.happenTime.replace('T', ' ') }}</span>
                <span v-if="m.publisherNickname">👤 {{ m.publisherNickname }}</span>
              </div>
            </div>
            <el-button size="small" type="primary" @click="router.push(`/lostfound/detail/${m.id}`)">去看看</el-button>
          </div>
        </div>
        <el-empty v-else-if="matchedDone" description="暂未匹配到高度相似的拾到信息，发布后大家会看到你的信息" :image-size="70" />
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import UploadImg from '../../components/UploadImg.vue'
import AiAssistPanel from '../../components/AiAssistPanel.vue'
import { publishLostFound, updateLostFound, lostFoundDetail, lostMatch } from '../../api/lostfound'

const router = useRouter()
const route = useRoute()
const editId = route.query.id ? Number(route.query.id) : 0
const form = reactive({ type: 0, title: '', description: '', location: '', happenTime: '', contact: '', images: [] })
const submitting = ref(false)
const matching = ref(false)
const matchedDone = ref(false)
const matches = ref([])

onMounted(async () => {
  if (!editId) return
  try {
    const d = await lostFoundDetail(editId)
    form.type = d.type ?? 0
    form.title = d.title || ''
    form.description = d.description || ''
    form.location = d.location || ''
    form.happenTime = (d.happenTime || '').replace('T', ' ')
    form.contact = d.contact || ''
    form.images = d.imageList || []
  } catch (e) {
    ElMessage.error('加载信息失败')
    router.back()
  }
})

function applyAssist(data) {
  if (data.title) form.title = data.title
  if (data.content) form.description = data.content
}

/** AI 智能匹配：比对库中拾到记录 */
async function doMatch() {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写丢失物品标题')
    return
  }
  matching.value = true
  matches.value = []
  matchedDone.value = false
  try {
    const res = await lostMatch({ title: form.title.trim(), description: form.description.trim() })
    matches.value = Array.isArray(res) ? res : (res.list || [])
    if (!matches.value.length) ElMessage.info('暂未匹配到相似拾到信息')
  } catch (e) {
    ElMessage.error(e.message || 'AI 匹配失败，请稍后重试')
  } finally {
    matching.value = false
    matchedDone.value = true
  }
}
async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  submitting.value = true
  try {
    if (editId) {
      await updateLostFound(editId, form)
      ElMessage.success('已保存，重新进入审核')
    } else {
      await publishLostFound(form)
      ElMessage.success('已提交，待管理员审核')
    }
    router.push('/lostfound')
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
.match-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.match-head h3 { margin: 0; font-size: var(--fs-body); }
.match-tip { font-size: var(--fs-cap); color: var(--ink-3); }
.match-list { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
.match-card {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 14px 16px; border: 1px solid var(--success-line, #b3e6d0);
  border-radius: var(--r-md); background: var(--success-soft, #f0faf5);
}
.match-card__main b { display: block; font-size: var(--fs-sm); margin-bottom: 4px; }
.match-reason { display: block; font-size: var(--fs-cap); color: var(--success-strong, #008a5c); margin-bottom: 6px; }
.match-meta { display: flex; flex-wrap: wrap; gap: 12px; font-size: var(--fs-cap); color: var(--ink-2); }
</style>
