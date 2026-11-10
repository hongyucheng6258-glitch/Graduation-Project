import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeImages, firstValidImage, resolveAssetUrl } from './image.js'

test('管理端图片工具兼容数组、JSON 字符串、单 URL 和逗号分隔', () => {
  assert.deepEqual(normalizeImages({ imageList: ['https://img.test/a.jpg', ''] }), ['https://img.test/a.jpg'])
  assert.deepEqual(normalizeImages({ images: '["/uploads/b.jpg", "invalid"]' }), ['/uploads/b.jpg'])
  assert.deepEqual(normalizeImages({ images: 'https://img.test/c.jpg, /uploads/d.jpg' }), [
    'https://img.test/c.jpg',
    '/uploads/d.jpg'
  ])
})

test('管理端图片工具保留数据库中的图片 Data URI', () => {
  assert.deepEqual(normalizeImages({ images: 'data:image/png;base64,AA==' }), ['data:image/png;base64,AA=='])
})

test('管理端图片工具忽略非法输入并提供首图', () => {
  assert.equal(firstValidImage({ images: 'not-an-image' }), '')
  assert.equal(firstValidImage({ imageList: 'blob:test' }), 'blob:test')
  assert.deepEqual(normalizeImages(null), [])
})

test('管理端静态资源使用应用前缀，上传图片使用同源接口路径', () => {
  assert.equal(resolveAssetUrl('/images/a.jpg', '/admin/'), '/admin/images/a.jpg')
  assert.equal(resolveAssetUrl('/admin/images/a.jpg', '/admin/'), '/admin/images/a.jpg')
  assert.equal(resolveAssetUrl('/api/assets/campus/avatar.png', '/admin/'), '/api/assets/campus/avatar.png')
})
