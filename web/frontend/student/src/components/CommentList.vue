<template>
  <!-- 评论列表 + 发表评论（动态广场用；第8项修复：组件内自行分页，不再一次性拉 50 条） -->
  <div class="comment-list">
    <div class="input-row">
      <el-input v-model="content" placeholder="友善评论，温暖校园" maxlength="500" @keyup.enter="submit">
        <template #append>
          <el-button :loading="submitting" @click="submit">发表</el-button>
        </template>
      </el-input>
    </div>
    <div v-loading="loading" class="comment-body">
      <div v-for="c in comments" :key="c.id" class="comment-item">
        <el-avatar :size="28" :src="c.avatar" style="cursor:pointer" @click="goUser(c.userId)">{{ c.nickname?.charAt(0) }}</el-avatar>
        <div class="c-body">
          <div class="c-head">
            <span class="c-nick" style="cursor:pointer;color:var(--brand-strong)" @click="goUser(c.userId)">{{ c.nickname }}</span>
            <span class="c-time">{{ fromNow(c.createTime) }}</span>
          </div>
          <div class="c-content">{{ c.content }}</div>
        </div>
      </div>
      <el-empty v-if="!loading && comments.length === 0" description="暂无评论，来抢沙发" :image-size="60" />
    </div>
    <div v-if="total > pageSize" class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        background
        small
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { commentPost, listComments } from '../api/post'
import { useUserStore } from '../store/user'
import { fromNow } from '../utils/date'
import { useRouter } from 'vue-router'

const props = defineProps({
  postId: { type: Number, required: true }
})
const emit = defineEmits(['count-changed'])

const userStore = useUserStore()
const router = useRouter()
const content = ref('')
const submitting = ref(false)
const loading = ref(false)
const comments = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10

watch(() => props.postId, () => load(1), { immediate: true })

function goUser(id) {
  if (!id) return
  if (Number(id) === Number(userStore.userInfo?.id)) router.push('/profile')
  else router.push(`/user/${id}`)
}

async function load(page) {
  if (!props.postId) return
  loading.value = true
  try {
    const res = await listComments(props.postId, { pageNum: page, pageSize })
    comments.value = res?.list || []
    total.value = res?.total || 0
    pageNum.value = page
    emit('count-changed', total.value)
  } catch (e) {
    comments.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onPageChange(page) {
  load(page)
}

/** 外部（如切换展开的动态）可强制刷新第一页 */
function reload() {
  load(1)
}

defineExpose({ reload })

async function submit() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  submitting.value = true
  try {
    await commentPost(props.postId, content.value.trim())
    content.value = ''
    ElMessage.success('评论成功')
    await load(1)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.input-row {
  margin-bottom: 16px;
}
.comment-body {
  min-height: 40px;
}
.comment-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.c-head {
  display: flex;
  gap: 10px;
  align-items: center;
}
.c-nick {
  font-size: 13px;
  font-weight: 600;
}
.c-time {
  font-size: 12px;
  color: var(--ink-3);
}
.c-content {
  font-size: 14px;
  color: var(--ink);
  margin-top: 4px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
