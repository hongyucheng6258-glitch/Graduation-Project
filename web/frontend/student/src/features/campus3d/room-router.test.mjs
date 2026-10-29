import test from 'node:test'
import assert from 'node:assert/strict'
import { createRoomRouter, SERVICE_PATHS } from './room-router.mjs'
import { getRoomProfile, SERVICES } from './campus-data.js'

const page = { render() {} }
const records = [{ path: '/', children: [
  { path: '', component: page, meta: { public: true } },
  ...Object.entries(SERVICE_PATHS).filter(([id, path]) => id !== 'drawgame' && path !== '/').map(([, path]) => ({ path: path.slice(1), component: page, meta: { public: true } })),
  { path: 'activity/detail/:id', component: page, meta: { public: true } },
  { path: 'activity/publish', component: page },
  { path: 'draw-guess', component: page },
  { path: 'draw-guess/room/:roomId', component: page },
  { path: 'chat/:conversationId', component: page }
] }, { path: '/login', component: page, meta: { public: true } }]
const tick = () => new Promise(resolve => setTimeout(resolve, 20))

test('all rooms reuse the student component records', async () => {
  for (const id of Object.keys(SERVICE_PATHS)) {
    const router = createRoomRouter(records, id, () => true)
    await router.replace(SERVICE_PATHS[id])
    assert.equal(router.currentRoute.value.matched[0].components.default, page)
  }
})

test('draw and guess is registered as an isolated student service room', () => {
  assert.equal(SERVICE_PATHS.drawgame, '/draw-guess')
  const service = SERVICES.find(item => item.id === 'drawgame')
  assert.equal(service?.name, '你画我猜')
  assert.equal(service?.group, 'social')
  assert.equal(getRoomProfile('drawgame').type, '你画我猜游戏室')
})

test('unauthenticated game room entry preserves its exact return target', async () => {
  const router = createRoomRouter(records, 'drawgame', () => false)
  await router.push('/draw-guess/room/room-42')
  assert.equal(router.currentRoute.value.path, '/login')
  assert.equal(router.currentRoute.value.query.redirect, '/draw-guess/room/room-42')
})
test('detail and private chat remain in room history and back preserves the detail target', async () => {
  const router = createRoomRouter(records, 'activity', () => true)
  await router.replace('/activity')
  await router.push('/activity/detail/42')
  await router.push('/chat/8')
  router.back(); await tick()
  assert.equal(router.currentRoute.value.fullPath, '/activity/detail/42')
  router.back(); await tick()
  assert.equal(router.currentRoute.value.fullPath, '/activity')
  router.back(); await tick()
  assert.equal(router.currentRoute.value.fullPath, '/activity')
})
test('unauthenticated publishing retains its return target', async () => {
  const router = createRoomRouter(records, 'activity', () => false)
  await router.push('/activity/publish')
  assert.equal(router.currentRoute.value.path, '/login')
  assert.equal(router.currentRoute.value.query.redirect, '/activity/publish')
})
