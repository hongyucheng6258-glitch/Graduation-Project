const labels = {
  WAITING: '候补中',
  OFFERED: '待确认递补',
  CLAIMED: '递补报名待审批',
  APPROVED: '递补报名已通过',
  REJECTED: '递补报名未通过',
  EXPIRED: '候补已失效',
  CANCELLED: '已退出候补'
}

export function waitlistState(entry) {
  if (entry?.state === 'OFFERED' && Date.parse(entry.offerUntil.replace(' ', 'T') + '+08:00') <= Date.now()) {
    return 'EXPIRED'
  }
  return entry?.state
}

export function waitlistLabel(entry) {
  return labels[waitlistState(entry)] || ''
}
