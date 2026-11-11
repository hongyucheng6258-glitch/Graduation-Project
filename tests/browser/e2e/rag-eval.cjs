// This evaluation uploads and withdraws fixtures. Run against an isolated backend.
const assert = require('node:assert/strict')
const { randomBytes } = require('node:crypto')
const { readFile } = require('node:fs/promises')
const path = require('node:path')

const base = process.env.E2E_BACKEND_URL?.replace(/\/$/, '')
const password = process.env.E2E_ADMIN_PASSWORD
const assets = path.resolve(__dirname, '../../../docker/demo/campus')
const knowledge = '/ai/knowledge'
const publicLibrary = '/admin/ai/agent/knowledge'
const internalLibrary = '/admin/assistant/rules'
const suffix = randomBytes(3).toString('hex')
const checks = []

async function call(url, { token, body, method = body ? 'POST' : 'GET' } = {}) {
  const multipart = body instanceof FormData
  const response = await fetch(base + '/api' + url, {
    method,
    headers: {
      ...(token && { Authorization: `Bearer ${token}` }),
      ...(body && !multipart && { 'Content-Type': 'application/json' })
    },
    body: multipart ? body : body && JSON.stringify(body),
    signal: AbortSignal.timeout(45000)
  })
  const json = response.headers.get('content-type')?.includes('application/json')
    ? await response.json() : null
  return { response, json }
}

async function ok(url, options) {
  const { json } = await call(url, options)
  assert.equal(json?.code, 200, `${url}: ${json?.message || '接口请求失败'}`)
  return json.data
}

async function register(nickname) {
  return (await ok('/auth/register', { body: {
    studentNo: 'RAG' + randomBytes(5).toString('hex'), password: randomBytes(10).toString('hex'),
    nickname, captchaId: 'isolated-test', captchaCode: 'isolated-test'
  } })).token
}

async function upload(url, token, file, title) {
  const form = new FormData()
  form.append('file', new Blob([await readFile(path.join(assets, file))], { type: 'application/pdf' }), title)
  return ok(url, { token, body: form })
}

async function waitReady(read) {
  for (let attempt = 0; attempt < 25; attempt++) {
    const state = await read()
    if (state?.state === 'READY') return
    assert.notEqual(state?.state, 'FAILED', state?.error)
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  assert.fail('PDF 索引未在规定时间内就绪')
}

async function check(name, test) {
  const start = Date.now()
  try {
    await test()
    checks.push({ name, passed: true, elapsedMs: Date.now() - start })
  } catch (error) {
    checks.push({ name, passed: false, elapsedMs: Date.now() - start, error: error.message })
  }
}

function answerSupports(reply, id, facts) {
  assert.ok(reply.citations.length, '缺少引用')
  assert.ok(reply.citations.every(ref => String(ref.documentId) === String(id) && ref.page === 1), '引用文档或页码错误')
  for (const fact of facts) assert.match(reply.answer, fact)
}

async function denied(url, token, body) {
  const { response, json } = await call(url, { token, body })
  assert.ok([401, 403, 404].includes(response.status) || [401, 403, 404].includes(json?.code),
    '越权或撤回的资料必须被拒绝')
}

async function main() {
  assert.ok(base && password, '需要 E2E_BACKEND_URL 和 E2E_ADMIN_PASSWORD')
  const admin = (await ok('/admin/auth/login', { body: { username: process.env.E2E_ADMIN_USERNAME || 'admin', password } })).token
  const [owner, stranger] = await Promise.all([register('资料验收甲'), register('资料验收乙')])
  const [publicDocs, promptVersions] = await Promise.all([
    ok(publicLibrary, { token: admin }),
    ok('/admin/ai/agent/prompts/knowledge_answer', { token: admin })
  ])
  const activity = publicDocs.find(doc => doc.fileName.includes('校园活动报名与签到指南'))
  const lost = publicDocs.find(doc => doc.fileName.includes('失物登记与认领指南'))
  assert.ok(activity && lost, '需要先导入校园演示知识库')
  const privatePdf = await upload('/pdf/upload', owner, 'library-guide.pdf', `验收图书馆-${suffix}.pdf`)
  const privateIndex = await ok(`${knowledge}/pdf/${privatePdf.docId}/index`, { token: owner, method: 'POST' })
  const internal = await upload(internalLibrary, admin, 'lab-notes.pdf', `验收实验记录-${suffix}.pdf`)
  const withdrawn = await upload(publicLibrary, admin, 'library-guide.pdf', `验收待撤回-${suffix}.pdf`)
  await Promise.all([
    waitReady(() => ok(`${knowledge}/${privateIndex.id}`, { token: owner })),
    waitReady(async () => (await ok(internalLibrary, { token: admin })).find(row => row.id === internal.id)),
    waitReady(async () => (await ok(publicLibrary, { token: admin })).find(row => row.id === withdrawn.id))
  ])
  const ask = (id, question, token = owner) => ok(`${knowledge}/${id}/ask`, { token, body: { question } })
  try {
    await check('公共资料：活动签到', async () => {
      answerSupports(await ask(activity.id, '报名通过后，按什么方式进行活动现场签到？'), activity.id, [/签到二维码|签到内容/])
    })
    await check('公共资料：失物归还', async () => {
      answerSupports(await ask(lost.id, '认领获准后，要等到什么时机才能确认已找回？'), lost.id, [/实际|拿回|交还/])
    })
    await check('个人 PDF：馆藏检索', async () => {
      answerSupports(await ask(privateIndex.id, '检索到馆藏后，记下什么信息才能去对应楼层找书？'), privateIndex.id, [/索书号/])
    })
    await check('个人 PDF：其他学生无法问答或打开文件', async () => {
      await denied(`${knowledge}/${privateIndex.id}/ask`, stranger, { question: '馆藏检索后记下什么？' })
      await denied(`${knowledge}/${privateIndex.id}/file`, stranger)
    })
    await check('管理资料：实验原始数据', async () => {
      const reply = await ok(internalLibrary + '/ask', { token: admin, body: { question: '实验读数需要改正时，应如何保存原始数据？' } })
      answerSupports(reply, internal.id, [/原读数|原始读数/, /原因/])
    })
    await check('管理资料：学生无法检索', async () => {
      await denied(`${knowledge}/${internal.id}/ask`, owner, { question: '实验原始读数改正时怎么处理？' })
    })
    await check('无依据问题：不编造答案或页码', async () => {
      const reply = await ask(privateIndex.id, '这份资料规定图书馆的空调维修合同金额是多少元？')
      assert.equal(reply.citations.length, 0)
      assert.match(reply.answer, /未查到|没有|不足/)
    })
    await check('撤回资料：撤回前可引用，撤回后无法查询', async () => {
      answerSupports(await ask(withdrawn.id, '小组讨论前需要确认研讨室的什么信息？'), withdrawn.id, [/预约时段|可预约/])
      await ok(`${publicLibrary}/${withdrawn.id}`, { token: admin, method: 'DELETE' })
      await denied(`${knowledge}/${withdrawn.id}/ask`, owner, { question: '研讨室如何预约？' })
      await denied(`${publicLibrary}/${withdrawn.id}/file`, admin)
    })
  } finally {
    await Promise.allSettled([
      ok(`${knowledge}/${privateIndex.id}`, { token: owner, method: 'DELETE' }),
      ok(`${internalLibrary}/${internal.id}`, { token: admin, method: 'DELETE' }),
      ok(`${publicLibrary}/${withdrawn.id}`, { token: admin, method: 'DELETE' })
    ])
  }
  const version = promptVersions.find(version => version.active)
  console.log(JSON.stringify({ promptVersion: version?.id ?? 0, checks }, null, 2))
  if (checks.some(item => !item.passed)) process.exitCode = 1
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
