import { createConversation } from '../api/chat'

export async function startChat(router, userStore, targetUserId, context = {}) {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return null
  }
  if (!targetUserId || Number(targetUserId) === Number(userStore.userInfo?.id)) return null
  const conversation = await createConversation({
    targetUserId: Number(targetUserId),
    contextType: context.type,
    contextId: context.id,
    contextTitle: context.title
  })
  router.push(`/chat/${conversation.id}`)
  return conversation
}
