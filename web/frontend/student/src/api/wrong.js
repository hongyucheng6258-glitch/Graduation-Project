import request from './request'

/** 错题本 API（v2：快速收录 + 复习闭环） */
export function listWrong(params) {
  // params: { subject, status, sort, pageNum, pageSize }
  return request.get('/wrong-question/list', { params })
}

export function wrongSubjects() {
  return request.get('/wrong-question/subjects')
}

export function wrongStats() {
  return request.get('/wrong-question/stats')
}

export function todayWrongs() {
  return request.get('/wrong-question/today')
}

export function submitReview(data) {
  return request.post('/wrong-question/review', data)
}

export function createWrong(data) {
  return request.post('/wrong-question', data)
}

export function updateWrong(id, data) {
  return request.put(`/wrong-question/${id}`, data)
}

export function deleteWrong(id) {
  return request.delete(`/wrong-question/${id}`)
}

export function getWrong(id) {
  return request.get(`/wrong-question/${id}`)
}

// ---------- 第二阶段：智能整理 / 讲解 / 复习计划 / 薄弱点 ----------

/** AI 智能整理（识别题型/学科/章节/难度/知识点/错因 + 摘要） */
export function analyzeWrong(id) {
  return request.post(`/wrong-question/${id}/analyze`, {}, { silent: true, timeout: 120000 })
}

/** AI 讲解这道题（错因分析 + 知识点讲解） */
export function explainWrong(id) {
  return request.post(`/wrong-question/${id}/explain`, {}, { silent: true, timeout: 120000 })
}

/** AI 生成今日复习计划（subject 可空=全部） */
export function reviewPlan(subject) {
  return request.post('/wrong-question/review-plan', { subject: subject || undefined }, { silent: true, timeout: 120000 })
}

/** 薄弱知识点报告（统计型） */
export function weakPoints() {
  return request.get('/wrong-question/weak-points')
}

// ---------- 第三阶段：AI 练习题（练习模式） ----------

/** 生成同类练习题（结构化，落库练习中，不直接入错题本） */
export function generatePractice(id) {
  return request.post(`/wrong-question/${id}/practice`, {}, { silent: true, timeout: 120000 })
}

/** 保存练习题到错题本（幂等） */
export function savePractice(id) {
  return request.post(`/wrong-question/practice/${id}/save`, {}, { silent: true })
}
