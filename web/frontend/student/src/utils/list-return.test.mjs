import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

test('room list cache separates search queries and restores page and edited filters', () => {
  const cache = new Map()
  let query, cleanup
  const context = vm.createContext({
    inject: () => cache, useRoute: () => ({ query }), onBeforeUnmount: fn => { cleanup = fn },
  })
  const source = readFileSync(new URL('./list-state.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '').replace('export function', 'function')
  vm.runInContext(source, context)
  query = { q: 'campus' }
  context.useListState('activity', { pageNum: { value: 3 }, keyword: { value: 'edited' } })
  cleanup()
  const fields = { pageNum: { value: 1 }, keyword: { value: '' } }
  assert.equal(context.useListState('activity', fields), true)
  assert.equal(fields.pageNum.value, 3)
  assert.equal(fields.keyword.value, 'edited')
  query = { q: 'different' }
  assert.equal(context.useListState('activity', { pageNum: { value: 1 } }), false)
})

for (const file of ['activity/List.vue', 'idle/List.vue', 'lostfound/List.vue', 'social/PostSquare.vue']) {
  test(`${file}: initial search preserves restored page, changed search resets it`, () => {
    const source = readFileSync(new URL(`../views/${file}`, import.meta.url), 'utf8')
    const watcher = source.slice(source.indexOf('let initialSearchWatch'), source.indexOf('</script>'))
    const pageNum = { value: 3 }, keyword = { value: 'edited' }
    let callback, loads = 0
    vm.runInNewContext(watcher, {
      restoredListState: true, pageNum, keyword, route: { query: { q: 'campus' } },
      watch: (getter, cb) => { callback = cb; cb(getter()) }, load: () => loads++,
      // PostSquare 分享直达相关状态/函数（watch 片段内 loadShareTarget 会引用）
      targetPost: { value: null }, targetError: { value: '' },
      postDetail: async () => null,
    })
    assert.equal(pageNum.value, 3)
    assert.equal(keyword.value, 'edited')
    callback('new search')
    assert.equal(pageNum.value, 1)
    assert.equal(keyword.value, 'new search')
    assert.equal(loads, 2)
  })
}
