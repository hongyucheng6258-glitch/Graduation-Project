<template>
  <!-- 通用内容卡片：封面图 + 标题 + 摘要 + 底部信息（闲置/活动/失物列表复用）
       第9项修复：module/category 传入兜底语义；加载失败换兜底图（防递归）；兜底时标注"示意图"；懒加载 -->
  <el-card class="item-card" :class="{ 'item-card--feed': feed }" :shadow="feed ? 'never' : 'hover'" @click="$emit('click')">
    <div class="content-photo" v-if="resolvedCover">
      <el-image :src="resolvedCover" fit="cover" class="content-photo__image" lazy @error="onError" />
      <span v-if="isSchematic" class="schematic-tag">示意图</span>
      <span v-if="$slots.badge" class="content-photo__badge"><slot name="badge" /></span>
    </div>
    <div class="content-photo placeholder" v-else>
      <el-icon :size="36" color="var(--ink-3)"><Picture /></el-icon>
      <span v-if="$slots.badge" class="content-photo__badge"><slot name="badge" /></span>
    </div>
    <div class="body">
      <slot name="labels" />
      <div class="title">{{ title }}</div>
      <div class="desc">{{ desc }}</div>
      <div class="footer">
        <slot name="footer">
          <span class="time">{{ timeText }}</span>
        </slot>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { fromNow } from '../utils/date'
import { fallbackFor, onImageError, isFallbackSrc } from '../utils/content-assets.mjs'

const props = defineProps({
  feed: Boolean,
  cover: { type: String, default: '' },
  title: { type: String, default: '' },
  desc: { type: String, default: '' },
  time: { type: String, default: '' },
  module: { type: String, default: '' },
  category: { type: String, default: '' }
})
defineEmits(['click'])

const timeText = computed(() => fromNow(props.time))
const displayCover = ref(props.cover || '')
watch(() => props.cover, (v) => { displayCover.value = v || '' })

const resolvedCover = computed(() => displayCover.value || fallbackFor(props.module, props.category))
const isSchematic = computed(() => isFallbackSrc(resolvedCover.value, props.module, props.category))

function onError(event) {
  // 真实地址加载失败：响应式切到兜底图（只切一次，防递归），并同步"示意图"标注
  const fallback = fallbackFor(props.module, props.category)
  if (props.cover && fallback && displayCover.value !== fallback) {
    displayCover.value = fallback
    return
  }
  onImageError(event, props.module, props.category)
}
</script>

<style scoped>
.item-card {
  cursor: pointer;
  overflow: hidden;
}
.item-card :deep(.el-card__body) {
  padding: 0;
}
.placeholder { display: grid; place-items: center; }
.schematic-tag {
  position: absolute;
  left: 10px;
  bottom: 10px;
  z-index: 2;
  font-size: 11px;
  line-height: 1;
  padding: 3px 7px;
  border-radius: 4px;
  color: #fff;
  background: rgba(15, 23, 42, 0.62);
}
.body {
  padding: 12px;
}
.title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
  line-height: 1.3;
}
.desc {
  font-size: 13px;
  color: var(--ink-3);
  margin-top: 6px;
  height: 36px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.footer {
  margin-top: 8px;
}
.time {
  font-size: 12px;
  color: var(--ink-3);
}
.item-card--feed {
  background: transparent;
  border: 0;
  border-radius: 0;
}
.item-card--feed .body { padding: 9px 0 0; }
.item-card--feed .desc:empty { display: none; }
.item-card--feed .title { min-height: 0; overflow-wrap: anywhere; line-height: 1.45; }
.item-card--feed .footer { margin-top: 4px; }
</style>
