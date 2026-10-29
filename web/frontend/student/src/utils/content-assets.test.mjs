import test from 'node:test'
import assert from 'node:assert/strict'
import {
  firstContentImage,
  fallbackImageFor,
  idleCategoryImage,
  itemImages,
  isFallbackSrc
} from './content-assets.mjs'

test('真实 imageList 优先于模块回退图', () => {
  assert.equal(firstContentImage({ imageList: ['https://cdn.example/a.png'] }, 'idle', '其他'), 'https://cdn.example/a.png')
})

test('多图完整保留（不丢图）', () => {
  const item = { imageList: ['https://cdn.example/a.png', '/uploads/b.jpg'] }
  assert.deepEqual(itemImages(item, 'activity'), ['https://cdn.example/a.png', '/uploads/b.jpg'])
  // 序列化形式兼容
  const serialized = { imageList: '["https://cdn.example/a.png","/uploads/b.jpg"]' }
  assert.deepEqual(itemImages(serialized, 'activity'), ['https://cdn.example/a.png', '/uploads/b.jpg'])
})

test('imageList 和 images 都为空时按模块返回回退图（WebP）', () => {
  assert.equal(firstContentImage({ imageList: [], images: '' }, 'activity'), '/images/generated-activity-campus.webp')
  assert.equal(fallbackImageFor('activity'), '/images/generated-activity-campus.webp')
})

test('闲置无图按物品类别选择对应示意图', () => {
  assert.equal(idleCategoryImage('教材书籍'), '/images/idle-schematic-book.jpg')
  assert.equal(idleCategoryImage('数码电子'), '/images/idle-calculator.jpg')
  assert.equal(idleCategoryImage('运动器材'), '/images/idle-badminton.jpg')
  assert.equal(idleCategoryImage('服饰鞋包'), '/images/idle-schematic-clothes.jpg')
  assert.equal(firstContentImage({ imageList: [] }, 'idle', '生活用品'), '/images/idle-schematic-life.jpg')
})

test('闲置未知分类不伪造实物图（返回空，显示占位）', () => {
  assert.equal(firstContentImage({ imageList: [] }, 'idle', '未知名目'), '')
  assert.equal(firstContentImage({ imageList: [] }, 'idle'), '')
})

test('itemImages 无图时返回兜底数组，且不伪造未知分类', () => {
  assert.deepEqual(itemImages({ imageList: [] }, 'idle', '运动器材'), ['/images/idle-badminton.jpg'])
  assert.deepEqual(itemImages({ imageList: [] }, 'idle', '未知'), [])
})

test('isFallbackSrc 能识别示意图地址', () => {
  assert.equal(isFallbackSrc('/images/idle-schematic-book.jpg', 'idle', '教材书籍'), true)
  assert.equal(isFallbackSrc('https://cdn.example/real.png', 'idle', '教材书籍'), false)
})

test('未知模块不伪造接口图片', () => {
  assert.equal(firstContentImage({}, 'unknown'), '')
  assert.equal(fallbackImageFor('unknown'), '')
})
