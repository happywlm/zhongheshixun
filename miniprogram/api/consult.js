// miniprogram/api/consult.js
const { request } = require('./request')

module.exports = {
  /** 发起咨询（智能问答 / 人工工单） */
  ask(question) {
    return request('/consult/ask', 'POST', { question })
  },

  /** 我的咨询列表 */
  myList(pageNum = 1, pageSize = 10) {
    return request(`/consult/my?pageNum=${pageNum}&pageSize=${pageSize}`)
  },

  /** 学员主动转人工：将已自动回复的咨询转为人工工单 */
  transferHuman(consultId) {
    return request('/consult/transfer-human', 'POST', { consultId })
  }
}
