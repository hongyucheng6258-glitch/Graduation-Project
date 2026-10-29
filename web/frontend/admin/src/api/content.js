import request from './request'

/** 内容管理 API */
export function contentOff(type, id) {
  return request.put(`/content/${type}/${id}/off`)
}

export function contentOn(type, id) {
  return request.put(`/content/${type}/${id}/on`)
}

export function signinReport(id) {
  return request.get(`/content/activity/${id}/signin-report`)
}

/** 导出（CSV 流，不走 axios 解包） */
function downloadCsv(url, filename) {
  const token = localStorage.getItem('admin_token')
  return fetch('/api/admin' + url, { headers: { Authorization: `Bearer ${token}` } })
    .then(r => {
      if (!r.ok) throw new Error('导出失败')
      return r.blob()
    })
    .then(blob => {
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = filename
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

export function exportMembers(id) {
  return downloadCsv(`/export/activity/${id}/members`, `活动${id}_报名名单.csv`)
}

export function exportSignins(id) {
  return downloadCsv(`/export/activity/${id}/signins`, `活动${id}_签到名单.csv`)
}
