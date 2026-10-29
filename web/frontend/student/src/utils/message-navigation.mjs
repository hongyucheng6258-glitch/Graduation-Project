/**
 * Resolve the in-app destination for a platform notification.
 * Keeping this mapping in one place makes the normal student shell and the
 * 3D room use exactly the same message behaviour.
 */
export function messageTarget(message) {
  const type = String(message?.bizType || '').trim().toLowerCase()
  const msgType = String(message?.type || '').trim().toLowerCase()
  const rawId = message?.bizId
  const hasId = rawId !== null && rawId !== undefined && String(rawId).trim() !== ''
  const id = hasId ? encodeURIComponent(String(rawId)) : ''

  // 审核结果通知：跳转到"我的发布"页，让作者看到审核结果和修改入口。
  if (msgType === 'audit') {
    switch (type) {
      case 'idle':
        return '/profile?tab=idle'
      case 'activity':
        return '/activity/my-signup'
      case 'lostfound':
        return '/lostfound'
      case 'post':
        return '/social'
      case 'partner':
        return '/partner'
      case 'qa':
        return '/qa/my'
      case 'report':
        return '/profile?tab=report'
      default:
        return null
    }
  }

  switch (type) {
    case 'conversation':
      return id ? `/chat/${id}` : '/chat'
    case 'idle':
      // 闲置互动通知（新预约/接受/拒绝/完成/互评）跳转到物品详情页，
      // 详情页已直接展示该物品的进行中预约与评价入口，无需翻页查找。
      return id ? `/idle/detail/${id}` : '/idle'
    case 'activity':
      return id ? `/activity/detail/${id}` : '/activity'
    case 'lostfound':
      return id ? `/lostfound/detail/${id}` : '/lostfound'
    case 'qa':
      return id ? `/qa/detail/${id}` : '/qa'
    case 'post':
      return id ? `/social?post=${id}` : '/social'
    case 'partner':
      return '/partner'
    case 'notice':
      return id ? `/notice/detail/${id}` : '/notice'
    case 'user':
      return id ? `/user/${id}` : '/profile'
    default:
      return null
  }
}
