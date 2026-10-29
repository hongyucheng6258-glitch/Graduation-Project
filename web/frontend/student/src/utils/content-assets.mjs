import { normalizeImages } from './image.mjs'

/**
 * 模块级兜底图：真实上传图片优先，仅在没有任何有效图片时使用。
 * 第9项修复：
 *  - 静态资源统一改用 WebP（体积平均下降 90%+，原 PNG 保留未删除）；
 *  - 闲置模块不再用"包含多种物品"的通用图冒充实物，改为按物品类别选择对应示意图；
 *  - 未知分类不伪造接口图片（返回 ''，UI 显示占位图标，诚实呈现"无图"）。
 */
export const CONTENT_FALLBACK_IMAGES = Object.freeze({
  activity: '/images/generated-activity-campus.webp',
  idle: '',
  lost: '/images/generated-lost-found.webp',
  lostfound: '/images/generated-lost-found.webp',
  square: '/images/generated-social-campus.webp',
  social: '/images/generated-social-campus.webp',
  partner: '/images/generated-study-partner.webp',
  aichat: '/images/generated-ai-study.webp',
  code: '/images/generated-ai-study.webp'
})

/**
 * 闲置物品分类 → 对应示意图（示意而非实物照片，UI 上标注"示意图"）。
 * 与闲置发布页 categories = ['教材书籍','数码电子','生活用品','运动器材','服饰鞋包','其他'] 一一对应。
 */
export const IDLE_CATEGORY_IMAGES = Object.freeze({
  教材书籍: '/images/idle-schematic-book.jpg',
  数码电子: '/images/idle-calculator.jpg',
  生活用品: '/images/idle-schematic-life.jpg',
  运动器材: '/images/idle-badminton.jpg',
  服饰鞋包: '/images/idle-schematic-clothes.jpg',
  其他: '/images/idle-schematic-other.jpg'
})

export function fallbackImageFor(moduleId) {
  return CONTENT_FALLBACK_IMAGES[String(moduleId || '').toLowerCase()] || ''
}

export function idleCategoryImage(category) {
  const key = String(category || '').trim()
  return IDLE_CATEGORY_IMAGES[key] || ''
}

/**
 * 按模块 + 分类取兜底图（闲置优先分类示意图，其余走模块映射）。
 * 列表、详情、预览与 3D 内容共用同一规则。
 */
export function fallbackFor(moduleId, category) {
  if (String(moduleId || '').toLowerCase() === 'idle') {
    return idleCategoryImage(category)
  }
  return fallbackImageFor(moduleId)
}

/**
 * 第一张有效图；无图时回退模块/分类兜底。
 * 需要展示全部有效图片时请用 itemImages()。
 */
export function firstContentImage(item, moduleId, category) {
  return normalizeImages(item)[0] || fallbackFor(moduleId, category)
}

/**
 * 该内容的所有有效图片（数组/序列化形式均兼容）；一张都没有时返回 [兜底图]（可能为空数组）。
 * 不裁剪、不丢弃，完整保留真实上传图片。
 */
export function itemImages(item, moduleId, category) {
  const images = normalizeImages(item)
  if (images.length) return images
  const fallback = fallbackFor(moduleId, category)
  return fallback ? [fallback] : []
}

/**
 * 图片加载失败兜底（防递归）：把失败地址换成兜底图，只换一次；
 * 兜底图也失败时不再递归，保持占位。
 * 用法：<el-image :src="src" @error="onImageError($event, 'activity', item.category)" />
 */
export function onImageError(event, moduleId, category) {
  const img = event?.target
  if (!img || img.dataset?.fallbackApplied === '1') return
  const fallback = fallbackFor(moduleId, category)
  if (fallback && img.src !== fallback) {
    img.dataset.fallbackApplied = '1'
    img.src = fallback
  }
}

/** 判断某地址是否为兜底/示意图（供 UI 标注"示意图"用） */
export function isFallbackSrc(src, moduleId, category) {
  const fallback = fallbackFor(moduleId, category)
  return !!fallback && !!src && src === fallback
}
