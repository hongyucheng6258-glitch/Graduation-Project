import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn.js'

dayjs.locale('zh-cn')

export function formatTime(value) {
  if (!value) return ''
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

export function formatDate(value) {
  if (!value) return ''
  return dayjs(value).format('YYYY-MM-DD')
}
