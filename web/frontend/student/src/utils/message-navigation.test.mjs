import test from 'node:test'
import assert from 'node:assert/strict'
import { messageTarget } from './message-navigation.mjs'

test('notification destinations cover every business module', () => {
  const cases = [
    [{ bizType: 'conversation', bizId: 8 }, '/chat/8'],
    [{ bizType: 'idle', bizId: 9, type: 'interact' }, '/idle/detail/9'],
    [{ bizType: 'activity', bizId: 12, type: 'interact' }, '/activity/detail/12'],
    [{ bizType: 'lostfound', bizId: 5, type: 'interact' }, '/lostfound/detail/5'],
    [{ bizType: 'qa', bizId: 3, type: 'interact' }, '/qa/detail/3'],
    [{ bizType: 'post', bizId: 7, type: 'interact' }, '/social?post=7'],
    [{ bizType: 'partner', bizId: 4, type: 'interact' }, '/partner'],
    [{ bizType: 'notice', bizId: 2 }, '/notice/detail/2'],
    [{ bizType: 'user', bizId: 6 }, '/user/6']
  ]
  for (const [message, expected] of cases) assert.equal(messageTarget(message), expected)
})

test('audit notifications route to my-content pages instead of business detail', () => {
  assert.equal(messageTarget({ bizType: 'idle', bizId: 9, type: 'audit' }), '/profile?tab=idle')
  assert.equal(messageTarget({ bizType: 'activity', bizId: 12, type: 'audit' }), '/activity/my-signup')
  assert.equal(messageTarget({ bizType: 'lostfound', bizId: 5, type: 'audit' }), '/lostfound')
  assert.equal(messageTarget({ bizType: 'post', bizId: 7, type: 'audit' }), '/social')
  assert.equal(messageTarget({ bizType: 'partner', bizId: 4, type: 'audit' }), '/partner')
  assert.equal(messageTarget({ bizType: 'qa', bizId: 3, type: 'audit' }), '/qa/my')
  assert.equal(messageTarget({ bizType: 'report', bizId: 9, type: 'audit' }), '/profile?tab=report')
})

test('missing identifiers fall back to the module list and unknown types stay in the inbox', () => {
  assert.equal(messageTarget({ bizType: 'activity' }), '/activity')
  assert.equal(messageTarget({ bizType: 'lostfound' }), '/lostfound')
  assert.equal(messageTarget({ bizType: 'post' }), '/social')
  assert.equal(messageTarget({ bizType: 'system' }), null)
  assert.equal(messageTarget({ bizType: 'audit' }), null)
})
