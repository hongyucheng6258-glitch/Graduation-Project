import request from './request'
import { getToken } from '../utils/auth'

/** AI 学习中心 API */

// ---------- 会话 ----------
export function createSession(data) {
  return request.post('/ai/session', data)
}

export function listSessions(scene) {
  return request.get('/ai/sessions', { params: { scene } })
}

export function renameSession(id, title) {
  return request.put(`/ai/session/${id}`, { title })
}

export function deleteSession(id) {
  return request.delete(`/ai/session/${id}`)
}

export function bindPdfDocument(sessionId, docId) {
  return request.put(`/ai/session/${sessionId}/pdf`, { docId })
}

export function listMessages(sessionId, pageNum = 1, pageSize = 20) {
  return request.get(`/ai/session/${sessionId}/messages`, { params: { pageNum, pageSize } })
}

// ---------- 代码纠错 / 提纲 / 习题 / PDF（一次性返回） ----------
export function codeFix(data) {
  return request.post('/ai/code-fix', data)
}

/**
 * 生成复习提纲（v2 三种模式：subject 按学科 / selected 按选中错题 / all 全量薄弱点报告）。
 * AI 场景失败原因由调用方展示，传 silent 关闭全局错误弹窗；生成耗时长，超时放宽到 120s。
 */
export function generateOutline(data) {
  return request.post('/ai/outline', data, { silent: true, timeout: 120000 })
}

/** 基于错题生成同类题（silent + 长超时，失败原因由调用方展示并提供重试；force 可跳过信息不足检查） */
export function generateQuiz(wrongQuestionId, force = false) {
  return request.post('/ai/quiz', { wrongQuestionId, force }, { silent: true, timeout: 120000 })
}

export function pdfUpload(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/pdf/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function pdfDoc(docId) {
  return request.get(`/pdf/${docId}`)
}

export function pdfAsk(data) {
  return request.post('/ai/pdf/ask', data)
}

/** AI 辅助发布：生成草稿（活动/闲置/失物招领/动态） */
export function aiAssistCompose(data) {
  return request.post('/ai/assist/compose', data, { silent: true, timeout: 120000 })
}

/** AI 辅助发布：润色/扩写/精简 */
export function aiAssistPolish(data) {
  return request.post('/ai/assist/polish', data, { silent: true, timeout: 120000 })
}

/** AI 校园向导：基于校园业务数据问答（活动/闲置/失物/动态/公告） */
export function aiGuideAsk(question) {
  return request.post('/ai/guide/ask', { question }, { silent: true, timeout: 120000 })
}

/**
 * Web 端 SSE 流式答疑（POST + fetch 流读取，EventSource 不支持 POST/自定义Header）。
 * 第11项修复：
 *  - 区分 SSE 与 JSON：HTTP 200 但 code≠200（401/403/503 等业务错误）进入 onError 统一错误策略，不能到 EOF 就当成功；
 *  - 支持 AbortController（options.signal）：取消时中断流并释放 reader/事件资源；
 *  - SSE 分片、跨 chunk 行、CRLF、error/done、网络断开与取消分别处理。
 *
 * @param {Object} payload { sessionId, question }
 * @param {Object} handlers { onDelta(text), onDone(), onError(code,message) }
 * @param {Object} options { signal }
 */
export async function chatStream(payload, handlers, options = {}) {
  const { signal } = options
  let resp
  try {
    resp = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${getToken()}`
      },
      body: JSON.stringify(payload),
      signal
    })
  } catch (error) {
    if (error?.name === 'AbortError') return
    handlers.onError(1002, error?.message || '连接AI服务失败')
    return
  }

  const contentType = (resp.headers.get('content-type') || '').toLowerCase()
  const isSse = contentType.includes('text/event-stream')

  // HTTP 层错误：尝试解析 JSON 业务错误码/消息
  if (!resp.ok) {
    let code = resp.status
    let message = 'AI服务调用失败'
    try {
      const j = await resp.json()
      if (j && j.code) code = j.code
      if (j && j.message) message = j.message
    } catch { /* 非 JSON 错误体 */ }
    handlers.onError(code, message)
    return
  }

  // 非流式响应（JSON 兜底）：HTTP 200 但 code≠200 的业务错误必须进入统一错误策略
  if (!isSse) {
    try {
      const j = await resp.json()
      if (j && j.code === 200) {
        const text = typeof j.data === 'string' ? j.data : (j.data?.answer ?? '')
        if (text) handlers.onDelta(text)
        handlers.onDone()
      } else {
        handlers.onError((j && j.code) || 200, (j && j.message) || 'AI服务调用失败')
      }
    } catch (error) {
      handlers.onError(500, 'AI服务返回格式异常')
    }
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let completed = false
  let sawDone = false

  const cleanup = () => {
    try { reader.releaseLock?.() } catch { /* ignore */ }
  }

  const finish = () => {
    if (completed) return
    completed = true
    cleanup()
    handlers.onDone()
  }

  const fail = (code, message) => {
    if (completed) return
    completed = true
    cleanup()
    handlers.onError(code, message)
  }

  const dispatch = (evt) => {
    const lines = evt.split(/\r?\n/)
    let eventName = 'message'
    const dataLines = []
    for (const line of lines) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
    }
    const data = dataLines.join('\n')
    if (eventName === 'delta' || eventName === 'message') {
      if (data) handlers.onDelta(data)
    } else if (eventName === 'done') {
      sawDone = true
      finish()
    } else if (eventName === 'error') {
      try {
        const err = JSON.parse(data)
        fail(err.code || 1002, err.message || 'AI服务调用失败')
      } catch {
        fail(1002, data || 'AI服务调用失败')
      }
    }
  }

  const consume = (flush = false) => {
    const normalized = buffer.replace(/\r\n/g, '\n')
    const events = normalized.split('\n\n')
    buffer = events.pop() || ''
    events.forEach(dispatch)
    if (flush && buffer.trim()) {
      dispatch(buffer)
      buffer = ''
    }
  }

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        buffer += decoder.decode()
        consume(true)
        // R4 修复：EOF 不等于成功——只有收到过 done 事件才 finish()；
        // 中途 EOF（截断流）必须按错误处理，不能把半截回复当完整回答。
        if (!completed) {
          if (sawDone) finish()
          else fail(500, 'AI响应未正常结束（连接中断或流被截断）')
        }
        break
      }
      buffer += decoder.decode(value, { stream: true })
      consume()
    }
  } catch (error) {
    if (error?.name === 'AbortError') {
      try { await reader.cancel() } catch { /* ignore */ }
      cleanup()
      return
    }
    fail(1002, error?.message || 'AI服务连接中断')
  }
}
