import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

/** 格式化时间（共享约定：yyyy-MM-dd HH:mm:ss） */
export function formatTime(time) {
  if (!time) return ''
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

/** 相对时间（如"3小时前"，消息/动态列表用） */
export function fromNow(time) {
  if (!time) return ''
  return dayjs(time).fromNow()
}

/** 日期格式化 */
export function formatDate(time) {
  if (!time) return ''
  return dayjs(time).format('YYYY-MM-DD')
}
