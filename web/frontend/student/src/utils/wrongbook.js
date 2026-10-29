/** 错题本共享常量与展示映射（v2） */

/** 掌握状态：0待复习 1复习中 2基本掌握 3已掌握 */
export const WQ_STATUS = [
  { value: 0, label: '待复习', type: 'danger' },
  { value: 1, label: '复习中', type: 'warning' },
  { value: 2, label: '基本掌握', type: 'primary' },
  { value: 3, label: '已掌握', type: 'success' }
]

/** 复习反馈掌握程度 */
export const MASTERY_LEVELS = [
  { value: 0, label: '仍然不会', icon: '🔴', desc: '完全想不起来，回到待复习' },
  { value: 1, label: '有点理解', icon: '🟡', desc: '思路不清晰，1 天后复习' },
  { value: 2, label: '基本掌握', icon: '🟢', desc: '能做对，间隔 1/3/7 天递减' },
  { value: 3, label: '已完全掌握', icon: '✅', desc: '很熟练，连续答对后拉长间隔' }
]

/** 错误原因快捷选项 */
export const ERROR_REASONS = [
  '概念不清', '公式记错', '审题错误', '计算错误',
  '粗心大意', '不会解题', '知识点混淆', '其他'
]

/** 题型快捷选项 */
export const QUESTION_TYPES = ['选择', '填空', '简答', '计算', '编程', '判断', '其他']

/** 难度选项 */
export const DIFFICULTIES = ['易', '中', '难']

/** AI 失败原因 → 页面提示映射（后端 code 或前端识别） */
export function aiErrorInfo(err) {
  const code = err?.code
  const map = {
    1010: {
      title: 'AI 服务暂不可用',
      desc: 'AI 服务暂不可用（未配置 API Key）。错题本的基础收录、复习功能不受影响。',
      action: '稍后重试'
    },
    1011: {
      title: '题目信息不足',
      desc: '这道题还缺少答案或解析，AI 生成的同类题质量可能较低。建议先补充。',
      action: '仍然生成'
    },
    429: {
      title: '今日生成次数已用完',
      desc: '今天 AI 生成次数已达上限，可以明天再试，或先用手动方式复习。',
      action: '明天继续'
    },
    1003: {
      title: '内容未通过安全校验',
      desc: '生成请求包含敏感内容，请调整后重试。',
      action: '返回'
    },
    400: {
      title: '参数不完整',
      desc: err?.message || '缺少生成所需的信息，请检查后重试。',
      action: '返回'
    },
    1006: {
      title: 'AI 生成失败',
      desc: err?.message || 'AI 服务暂时不可用，请稍后重试。',
      action: '重新生成'
    }
  }
  const hit = map[code]
  if (hit) return hit
  // 网络异常 / 超时
  if (err?.code === 'ECONNABORTED' || /timeout/i.test(err?.message || '')) {
    return {
      title: '生成时间较长',
      desc: 'AI 生成超过预期时间，可能服务繁忙。可以重试一次。',
      action: '重试'
    }
  }
  return {
    title: '网络异常',
    desc: err?.message || '网络连接失败，请检查网络后重新请求。',
    action: '重新请求'
  }
}

/** 格式化下次复习时间（null → 尽快） */
export function formatDue(time) {
  if (!time) return '尽快复习'
  const diff = new Date(time).getTime() - Date.now()
  const days = Math.ceil(diff / 86400000)
  if (days <= 0) return '今天复习'
  if (days === 1) return '明天复习'
  return `${days} 天后复习`
}

/** 难度 → 排序权重（卡片徽标） */
export function difficultyRank(d) {
  return d === '难' ? 3 : d === '中' ? 2 : 1
}
