/**
 * 字典映射工具
 *
 * 把散落在 7 个 view 里的 typeText / typeColor / difficultyText / statusText /
 * questionTypeText / contentTypeName 等纯函数统一收口，避免复制粘贴带来的
 * 不一致（论文级代码要求：单一事实源）。
 */

// 课程类型：1-必修 2-选修 3-计划
export function typeText(type) {
  return { 1: '必修', 2: '选修', 3: '计划' }[type] || '其他'
}
export function typeColor(type) {
  return { 1: 'danger', 2: 'success', 3: 'warning' }[type] || ''
}

// 难度等级：1-初级 2-中级 3-高级
export function difficultyText(d) {
  return { 1: '初级', 2: '中级', 3: '高级' }[d] || '-'
}

// 考试记录状态：0-未开始 1-进行中 2-已批阅
export function statusText(s) {
  return { 0: '未开始', 1: '进行中', 2: '已批阅' }[s] ?? '未知'
}
export function statusColor(s) {
  return { 0: 'info', 1: 'warning', 2: 'primary' }[s] ?? ''
}

// 题目类型：1-单选 2-多选 3-判断 4-填空
export function questionTypeText(t) {
  return { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题' }[t] || '未知'
}
export function questionTypeColor(t) {
  return { 1: 'primary', 2: 'success', 3: 'warning', 4: 'info' }[t] || ''
}

// 章节内容类型：1-视频 2-PDF 3-文本
export function contentTypeName(t) {
  return { 1: '视频', 2: 'PDF', 3: '文本' }[t] || '未知'
}

/**
 * 解析题目选项
 *
 * 同时兼容两种后端历史格式：
 *   - JSON 数组：["A.文本","B.文本"]
 *   - 拼合格式：A.文本|B.文本
 * 统一输出 [{label, text}]，供 el-radio / el-checkbox 渲染。
 */
export function parseOptions(options) {
  if (!options) return []
  // 先尝试 JSON 数组
  try {
    const parsed = JSON.parse(options)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => {
        const m = String(item).match(/^([A-D])[.、\s]*(.+)$/)
        return m ? { label: m[1], text: m[2] } : { label: item, text: '' }
      })
    }
  } catch (e) {}
  // 拼合格式：A.文本|B.文本
  return String(options).split('|').map((item) => {
    const m = item.match(/^([A-D])[.、\s]*(.+)$/)
    return m ? { label: m[1], text: m[2] } : { label: item, text: '' }
  })
}
