<template>
  <div class="square">
    <header class="square-head">
      <div>
        <h1>动态广场</h1>
        <p>记录今天，也认识身边有趣的人。</p>
      </div>
      <button v-if="!composerOpen" type="button" class="btn primary" aria-controls="post-composer"
        aria-expanded="false" @click="toggleComposer">发布动态</button>
      <button v-if="composerOpen" type="button" class="square-button" aria-controls="post-composer"
        aria-expanded="true" @click="toggleComposer">收起发布框</button>
    </header>

    <WtFeature class="square-feature" image="/images/generated-social-campus.webp" title="有些小美好，值得一起看见。"
      description="分享校园生活，参与评论与点赞，也可以把喜欢的内容收藏起来。">
      <button class="btn primary" type="button" @click="toggleComposer">分享此刻</button>
      <RouterLink class="btn" to="/draw-guess">你画我猜</RouterLink>
    </WtFeature>

    <div class="square-rail">
      <form class="post-search" role="search" aria-label="搜索校园动态" @submit.prevent="search">
        <el-input v-model="keyword" aria-label="搜索校园动态" placeholder="搜索校园动态" clearable @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
          <template #append><el-button native-type="submit">搜索</el-button></template>
        </el-input>
      </form>

      <aside v-if="hotTopics.length || hotPosts.length" class="square-discover" aria-label="校园发现">
        <button type="button" class="discover-toggle" aria-controls="discover-panels"
          :aria-expanded="discoverOpen" @click="discoverOpen = !discoverOpen">
          热门话题与动态 <el-icon><ArrowDown /></el-icon>
        </button>
        <div id="discover-panels" class="discover-panels" :class="{ 'is-expanded': discoverOpen }">
          <section v-if="hotTopics.length" class="discover-panel">
            <h2>热门话题</h2>
            <button v-for="t in hotTopics" :key="t.tag" type="button" class="discover-row topic-row" @click="searchTopic(t.tag)">
              <strong>#{{ t.tag }}</strong>
              <span>{{ t.count }} 条讨论</span>
            </button>
          </section>
          <section v-if="hotPosts.length" class="discover-panel">
            <h2>热门动态</h2>
            <button v-for="p in hotPosts" :key="p.id" type="button" class="discover-row hot-row" @click="openPost(p)">
              <strong>{{ p.nickname }}</strong>
              <span class="hot-text">{{ p.content }}</span>
              <span>{{ p.likeCount }} 赞 · {{ p.commentCount }} 评论</span>
            </button>
          </section>
        </div>
      </aside>
    </div>

    <section class="timeline" aria-label="动态时间线">
      <div class="compose-row">
        <el-avatar class="timeline-avatar" :src="userStore.userInfo?.avatar">
          {{ userStore.userInfo?.nickname?.charAt(0) || '梧' }}
        </el-avatar>
        <button v-if="!composerOpen" type="button" class="compose-trigger" aria-controls="post-composer"
          aria-expanded="false" @click="toggleComposer">
          <span>分享校园生活…</span>
          <span class="publish-label">发动态</span>
        </button>
        <div id="post-composer" v-show="composerOpen" class="compose-editor">
          <el-input ref="composerInput" v-model="newPost" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }"
            aria-label="动态正文" placeholder="分享校园生活…" maxlength="2000" show-word-limit />
          <AiAssistPanel type="post" type-name="动态" @fill="applyAssist" />
          <div class="publish-ops">
            <UploadImg v-model="newImages" :max="9" />
            <el-button class="publish-button" type="primary" :loading="publishing" @click="publish">发布动态</el-button>
          </div>
        </div>
      </div>

      <div v-if="targetPost" class="share-banner">
        <span>正在查看分享的动态</span>
        <button type="button" class="square-button" @click="closeShare">返回动态列表</button>
      </div>
      <el-alert v-else-if="targetError" type="error" :closable="false" show-icon class="share-error">
        <template #title>{{ targetError }}</template>
        <button type="button" class="square-button" @click="closeShare">返回动态列表</button>
      </el-alert>
      <div v-if="loadError" class="load-error" role="alert">
        <span>{{ loadError }}</span><button type="button" class="square-button" @click="load">重新加载</button>
      </div>

      <div v-loading="loading" class="timeline-posts">
        <article v-for="p in displayed" :id="'square-post-' + p.id" :key="p.id" :data-post-id="p.id" class="post-card">
          <button type="button" class="author-avatar" :aria-label="'查看' + (p.nickname || '同学') + '的主页'" @click="goUser(p.userId)">
            <el-avatar class="timeline-avatar" :src="p.avatar">{{ p.nickname?.charAt(0) || '梧' }}</el-avatar>
          </button>
          <div class="post-main">
            <header class="post-head">
              <div class="post-byline">
                <button type="button" class="nick" @click="goUser(p.userId)">{{ p.nickname || '校园同学' }}</button>
                <time class="time" :datetime="p.createTime">{{ fromNow(p.createTime) }}</time>
              </div>
              <el-dropdown trigger="click" @command="command => command === 'message' ? contactAuthor(p) : openReport(p)">
                <button type="button" class="post-more" aria-label="动态更多操作"><el-icon><MoreFilled /></el-icon></button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="Number(p.userId) !== Number(userStore.userInfo?.id)" command="message">私信</el-dropdown-item>
                    <el-dropdown-item command="report">举报</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </header>
            <p class="post-content">{{ p.content }}</p>
            <div v-if="postImages(p).length" class="post-images" :class="{ 'post-images--single': postImages(p).length === 1 }">
              <el-image v-for="(img, index) in postImages(p)" :key="index" :src="img" fit="cover" class="post-img content-photo"
                :alt="'动态图片 ' + (index + 1)" :preview-src-list="postImages(p)" :initial-index="index" preview-teleported
                loading="lazy" @error="onImgError" />
            </div>

            <div class="post-ops" aria-label="动态互动">
              <button type="button" class="op" :aria-expanded="expandedPostId === p.id"
                :aria-label="expandedPostId === p.id ? '收起评论' : '查看评论'" @click="toggleComments(p)">
                <el-icon><ChatRound /></el-icon><span>{{ p.commentCount || 0 }}</span>
              </button>
              <button type="button" class="op like-op" :aria-pressed="!!p.liked"
                :aria-label="p.liked ? '取消点赞' : '点赞'" @click="toggleLike(p)">
                <svg viewBox="0 0 24 24" :fill="p.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
                  <path d="M12 20.5S3 15.4 3 9.6a4.6 4.6 0 0 1 9-1.2 4.6 4.6 0 0 1 9 1.2c0 5.8-9 10.9-9 10.9Z" />
                </svg><span>{{ p.likeCount || 0 }}</span>
              </button>
              <button type="button" class="op" :aria-pressed="!!p.favorited"
                :aria-label="p.favorited ? '取消收藏' : '收藏'" @click="toggleFavorite(p)">
                <el-icon><StarFilled v-if="p.favorited" /><Star v-else /></el-icon>
              </button>
              <button type="button" class="op" aria-label="分享" @click="sharePost(p)"><el-icon><Share /></el-icon></button>
            </div>
            <div v-if="expandedPostId === p.id" class="comment-area">
              <CommentList :post-id="p.id" @count-changed="(t) => (p.commentCount = t)" />
            </div>
          </div>
        </article>
        <EmptyBox v-if="!loadError && !loading && !displayed.length" :description="keyword ? '没有找到相关动态' : '还没有动态，来发第一条吧'" />
      </div>
      <el-pagination v-if="total > 10" v-model:current-page="pageNum" :total="total" :page-size="10"
        class="timeline-pagination" layout="prev, pager, next" @current-change="load" />
    </section>

    <el-dialog v-model="reportVisible" title="举报该动态" width="min(440px, calc(100vw - 32px))">
      <el-select v-model="reportReasonType" style="width: 100%; margin-bottom: 10px">
        <el-option label="违规内容" value="违规" />
        <el-option label="辱骂引战" value="辱骂" />
        <el-option label="广告骚扰" value="广告" />
        <el-option label="其他" value="其他" />
      </el-select>
      <el-input v-model="reportReason" type="textarea" :rows="3" maxlength="500" aria-label="举报理由" />
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="doReport">提交举报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import WtFeature from '../../components/wt/WtFeature.vue'
import { useListState } from '../../utils/list-state'
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowDown, ChatRound, MoreFilled, Search, Share, Star, StarFilled } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import UploadImg from '../../components/UploadImg.vue'
import AiAssistPanel from '../../components/AiAssistPanel.vue'
import CommentList from '../../components/CommentList.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { listPost, postDetail, publishPost, likePost, unlikePost } from '../../api/post'
import { submitReport } from '../../api/report'
import { favorite, unfavorite } from '../../api/favorite'
import { useUserStore } from '../../store/user'
import { fromNow } from '../../utils/date'
import { startChat } from '../../utils/startChat'
import { onImageError } from '../../utils/content-assets.mjs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const restoredListState = useListState('social', { pageNum, keyword })
const composerOpen = ref(false)
const composerInput = ref(null)
const discoverOpen = ref(false)
const newPost = ref('')
const newImages = ref([])
const publishing = ref(false)
const expandedPostId = ref(null)
const targetPost = ref(null)
const targetError = ref('')
const displayed = computed(() => targetPost.value
  ? [targetPost.value, ...list.value.filter(p => String(p.id) !== String(targetPost.value.id))]
  : list.value)
const reportVisible = ref(false)
const reportReasonType = ref('违规')
const reportReason = ref('')
const reportTarget = ref(null)


function onImgError(event) {
  onImageError(event, 'square')
}
function postImages(post) {
  // 纯文字动态不强行补校园照片；有图才展示，加载失败时由 onImgError 换兜底图
  return post?.imageList?.length ? post.imageList : []
}

/** 热门话题：从动态内容提取 #标签 并按出现次数聚合 */
const hotTopics = computed(() => {
  const counter = new Map()
  for (const p of list.value) {
    const tags = String(p.content || '').match(/#([\u4e00-\u9fa5A-Za-z0-9]+)/g) || []
    for (const raw of tags) {
      const tag = raw.slice(1)
      counter.set(tag, (counter.get(tag) || 0) + 1)
    }
  }
  return [...counter.entries()]
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)
})

/** 热门动态：按互动量（赞+评论）排序 Top3 */
const hotPosts = computed(() =>
  [...list.value]
    .sort((a, b) => (b.likeCount || 0) + (b.commentCount || 0) - (a.likeCount || 0) - (a.commentCount || 0))
    .slice(0, 3)
)

function searchTopic(tag) {
  keyword.value = tag
  search()
}

function toggleComments(post) {
  expandedPostId.value = expandedPostId.value === post.id ? null : post.id
}

async function openPost(p) {
  expandedPostId.value = p.id
  await nextTick()
  document.getElementById('square-post-' + p.id)?.scrollIntoView({ block: 'start' })
}
async function sharePost(p) {
  const url = `${location.origin}/social?post=${encodeURIComponent(p.id)}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制，快去分享吧')
  } catch {
    ElMessage.info(`动态链接：${url}`)
  }
}

function search() {
  pageNum.value = 1
  load()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listPost({ keyword: keyword.value || undefined, pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } catch (error) {
    loadError.value = error.message || '内容加载失败，请重试'
  } finally {
    loading.value = false
  }
}

async function toggleComposer() {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  composerOpen.value = !composerOpen.value
  if (composerOpen.value) {
    await nextTick()
    composerInput.value?.focus()
  }
}

function applyAssist(data) {
  if (data.content) newPost.value = data.content
}

async function publish() {
  if (!newPost.value.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  publishing.value = true
  try {
    await publishPost({ content: newPost.value, images: newImages.value })
    ElMessage.success('已提交，待管理员审核后公开')
    newPost.value = ''
    newImages.value = []
    composerOpen.value = false
  } finally {
    publishing.value = false
  }
}

async function toggleFavorite(p) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    if (p.favorited) {
      await unfavorite('post', p.id)
      p.favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await favorite('post', p.id)
      p.favorited = true
      ElMessage.success('收藏成功')
    }
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function toggleLike(p) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  if (p.liked) {
    await unlikePost(p.id)
    p.liked = false
    p.likeCount--
  } else {
    await likePost(p.id)
    p.liked = true
    p.likeCount++
  }
}

function goUser(id) {
  if (!id) return
  if (Number(id) === Number(userStore.userInfo?.id)) router.push('/profile')
  else router.push(`/user/${id}`)
}

async function contactAuthor(p) {
  const title = String(p.content || '').slice(0, 60) || '校园动态'
  await startChat(router, userStore, p.userId, { type: 'post', id: p.id, title })
}

function openReport(p) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  reportTarget.value = p
  reportVisible.value = true
}

async function doReport() {
  await submitReport({ targetType: 'post', targetId: reportTarget.value.id, reasonType: reportReasonType.value, reason: reportReason.value })
  ElMessage.success('举报已提交')
  reportVisible.value = false
}

watch(() => route.query.post, loadShareTarget, { immediate: true })

let initialSearchWatch = true
watch(
  () => route.query.q,
  (q) => {
    if (!initialSearchWatch || !restoredListState) {
      keyword.value = String(q || '')
      pageNum.value = 1
    }
    initialSearchWatch = false
    load()
  },
  { immediate: true }
)

async function loadShareTarget() {
  const id = Number(route.query.post)
  if (!id) {
    targetPost.value = null
    targetError.value = ''
    return
  }
  try {
    const post = await postDetail(id)
    if (id !== Number(route.query.post)) return
    targetPost.value = post
    targetError.value = ''
  } catch (e) {
    if (id !== Number(route.query.post)) return
    targetPost.value = null
    targetError.value = e?.message || '动态不存在或未通过审核'
  }
}

function closeShare() {
  targetPost.value = null
  targetError.value = ''
  // 保留搜索词 q，仅移除 post 参数，不重载列表（返回列表状态与普通入口一致）
  router.replace({ query: route.query.q ? { q: route.query.q } : {} })
}
</script>

<style scoped>
.square-head p { margin-top: 6px; color: var(--ink-3); font-size: 13px; }
.square {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(235px, 28vw, 286px);
  grid-template-areas: 'head head' 'feature feature' 'timeline rail';
  column-gap: 24px;
  align-items: start;
  max-width: 1200px;
  margin-inline: auto;
}
.square-feature { grid-area: feature; }
.square-head, .timeline {
  min-width: 0;
  background: var(--surface);
  border-inline: 1px solid var(--line);
}
.square-head {
  grid-area: head;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 24px;
  background: transparent;
  border: 0;
}
.square-head h1 { margin: 0; font-size: 34px; line-height: 1.25; color: var(--ink); }
.timeline { grid-area: timeline; width: min(100%, 720px); min-height: 70vh; border-bottom: 1px solid var(--line); border-radius: 0 0 12px 12px; }
.square-rail { grid-area: rail; min-width: 0; }
.post-search { padding-block: 6px 10px; min-width: 0; }
.post-search :deep(.el-input__wrapper) { border-radius: 999px 0 0 999px; background: var(--surface); }
.post-search :deep(.el-input-group__append) { border-radius: 0 999px 999px 0; }
.square-discover { min-width: 0; }
.discover-toggle { display: none; }
.discover-panels { display: grid; gap: 12px; }
.discover-panel { min-width: 0; padding: 4px 0; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); }
.discover-panel h2 { margin: 0; padding: 12px 14px 8px; color: var(--ink); font-size: 17px; }
.discover-row {
  display: grid;
  width: 100%;
  gap: 3px;
  border: 0;
  padding: 8px 14px;
  background: transparent;
  text-align: start;
  color: var(--ink-3);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  overflow-wrap: anywhere;
}
.discover-row:hover { background: var(--surface-2); }
.discover-row strong { color: var(--ink); font-size: 13px; }
.hot-text { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; color: var(--ink-2); }

.compose-row, .post-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: start;
  gap: 11px;
  padding: 13px 17px;
  border-bottom: 1px solid var(--line);
}
.timeline-avatar { --el-avatar-size: 42px; color: var(--brand-strong); background: var(--brand-soft); font-weight: 650; }
.compose-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 42px;
  padding: 0;
  border: 0;
  color: var(--ink-3);
  background: transparent;
  text-align: start;
  font: inherit;
  cursor: pointer;
}
.publish-label {
  flex: none;
  padding: 7px 13px;
  border-radius: 999px;
  color: var(--brand-ink);
  background: var(--brand);
  font-size: 13px;
  font-weight: 650;
}
.compose-editor { min-width: 0; display: grid; gap: 10px; }
.compose-editor :deep(.el-textarea__inner) { padding: 9px 9px 23px; font-size: 15px; }
.publish-ops { display: flex; flex-wrap: wrap; align-items: start; justify-content: space-between; gap: 8px; }
.publish-ops > * { min-width: 0; max-width: 100%; }
.publish-button { border-radius: 999px; margin-inline-start: auto; }

.post-card { scroll-margin-top: 82px; }
.author-avatar, .nick, .post-more, .op, .square-button {
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.author-avatar { border-radius: 50%; line-height: 0; }
.post-main, .post-byline { min-width: 0; }
.post-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 4px; }
.post-byline { display: flex; align-items: baseline; flex-wrap: wrap; gap: 0 7px; }
.nick { max-width: 100%; color: var(--ink); font-size: 14px; font-weight: 700; text-align: start; overflow-wrap: anywhere; }
.nick:hover { text-decoration: underline; }
.time { color: var(--ink-3); font-size: 12px; white-space: nowrap; }
.time::before { content: '·'; margin-inline-end: 6px; }
.post-more { display: grid; place-items: center; width: 32px; height: 28px; border-radius: 999px; color: var(--ink-3); }
.post-more:hover { color: var(--brand-strong); background: var(--brand-soft); }
.post-content { margin: 1px 0 0; color: var(--ink); font-size: 15px; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.post-images {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 9px;
  width: min(100%, 440px);
}
.post-images--single { grid-template-columns: minmax(0, 1fr); }
.post-img { cursor: zoom-in; }
.post-img :deep(.el-image__inner) { position: absolute; inset: 0; display: block; width: 100%; height: 100%; }
.post-ops { display: flex; justify-content: space-between; align-items: center; max-width: 440px; margin: 5px 0 -3px; }
.op { display: inline-flex; align-items: center; gap: 4px; min-width: 42px; min-height: 38px; color: var(--ink-3); font-size: 12px; }
.op > :is(svg, .el-icon) { width: 18px; height: 18px; font-size: 18px; }
.op:hover, .op[aria-expanded='true'], .op[aria-pressed='true'] { color: var(--brand-strong); }
.like-op:hover, .like-op[aria-pressed='true'] { color: var(--error); }
.comment-area { border-top: 1px solid var(--line); margin-top: 6px; padding-top: 11px; }
.timeline-pagination { justify-content: center; margin: 12px 0; }
.share-banner, .load-error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line);
  background: var(--brand-soft);
  color: var(--ink-2);
  font-size: 13px;
}
.share-error { margin: 8px 0; }
.square-button { color: var(--brand-strong); font-size: 13px; }
.square :is(button, .post-img):focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }

@media (width >= 850px) {
  .square-rail {
    position: sticky;
    inset-block-start: 16px;
    max-height: calc(100dvh - 32px);
    display: flex;
    flex-direction: column;
  }
  .post-search { flex: none; }
  .square-discover { min-height: 0; overflow-y: auto; scrollbar-width: thin; }
  .square-discover .discover-row:focus-visible { outline-offset: -2px; }
}
@media (width < 850px) {
  .square { grid-template-columns: minmax(0, 1fr); grid-template-areas: 'head' 'feature' 'rail' 'timeline'; }
  .post-search, .square-discover, .timeline { border-inline: 1px solid var(--line); background: var(--surface); }
  .post-search { padding: 8px 12px; }
  .discover-toggle {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
    min-height: 38px;
    padding: 7px 12px;
    border: 0;
    border-bottom: 1px solid var(--line);
    background: transparent;
    color: var(--ink-2);
    font: inherit;
    font-size: 13px;
    cursor: pointer;
  }
  .discover-panels { display: none; }
  .discover-panels.is-expanded { display: grid; padding: 8px 12px; }
}
@media (max-width: 480px) {
  .square-head { align-items: flex-start; }
  .square-head h1 { font-size: 27px; }
  .compose-row, .post-card { grid-template-columns: 34px minmax(0, 1fr); gap: 8px; padding: 10px 9px; }
  .timeline-avatar { --el-avatar-size: 34px; }
  .post-byline { column-gap: 4px; }
  .post-content { font-size: 14px; }
  .op { min-width: 34px; }
}
</style>
