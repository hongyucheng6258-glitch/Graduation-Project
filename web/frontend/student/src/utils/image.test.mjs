import test from 'node:test'
import assert from 'node:assert/strict'

const imageUtils = await import('./image.mjs').catch(() => ({
  normalizeImages: () => [],
  firstValidImage: () => ''
}))
const { firstValidImage, normalizeImages } = imageUtils

test('normalizeImages 兼容 imageList 数组并过滤无效项', () => {
  assert.deepEqual(normalizeImages({ imageList: ['', '  ', 'https://img.test/first.jpg', null] }), [
    'https://img.test/first.jpg'
  ])
})

test('normalizeImages 保留数据库中的图片 Data URI', () => {
  assert.deepEqual(normalizeImages({ images: 'data:image/png;base64,AA==' }), [
    'data:image/png;base64,AA=='
  ])
})

test('normalizeImages 兼容 images JSON 数组字符串', () => {
  assert.deepEqual(normalizeImages({ images: '["", "https://img.test/json.png"]' }), [
    'https://img.test/json.png'
  ])
})

test('normalizeImages 兼容 images 普通 URL 和逗号分隔字符串', () => {
  assert.deepEqual(normalizeImages({ images: 'https://img.test/plain.webp' }), [
    'https://img.test/plain.webp'
  ])
  assert.deepEqual(normalizeImages({ images: ' , https://img.test/a.jpg, https://img.test/b.jpg ' }), [
    'https://img.test/a.jpg',
    'https://img.test/b.jpg'
  ])
})

test('firstValidImage 优先 imageList 的首张有效图，无有效图返回空字符串', () => {
  assert.equal(firstValidImage({ imageList: [' ', 'https://img.test/list.jpg'], images: 'https://img.test/fallback.jpg' }), 'https://img.test/list.jpg')
  assert.equal(firstValidImage({ imageList: [], images: 'not-json-without-url' }), '')
  assert.equal(firstValidImage(null), '')
})
