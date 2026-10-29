export function shouldUseChatSocket(messageType) {
  return messageType !== 'image'
}
