// pages/profile/records/records.js
// 考试记录：我的考试记录时间线
const examApi = require('../../../api/exam')

Page({
  data: {
    records: [],   // 考试记录时间线
    loading: false
  },

  onLoad() {
    this.loadData()
  },

  async loadData() {
    this.setData({ loading: true })
    try {
      // 第一步：用 /exam/list 拿考试列表（含 title/score/passed/recordId）
      const list = await examApi.getList()
      // 只保留已考过的（score != null 表示有考试记录）
      const done = (list || []).filter(e => e.score != null && e.score !== undefined)

      // 第二步：用每个 exam 的 recordId 调 /exam/result 补全对错统计和提交时间
      // （ExamListVO 的 correctCount/wrongCount 置 null，submitTime 不返回）
      const records = await Promise.all(done.map(async (e) => {
        let correctCount = 0, wrongCount = 0, unansweredCount = 0, totalCount = 0, submitTime = ''
        if (e.recordId) {
          try {
            const detail = await examApi.getResult(e.id)
            correctCount = detail.correctCount ?? 0
            wrongCount = detail.wrongCount ?? 0
            unansweredCount = detail.unansweredCount ?? 0
            totalCount = correctCount + wrongCount + unansweredCount
          } catch (err) {
            // 单条失败用 questionCount 兜底
          }
        }
        if (totalCount === 0 && e.questionCount) totalCount = e.questionCount
        return {
          id: e.recordId || e.id,
          examId: e.id,
          examTitle: e.title || '未命名考试',
          score: e.score ?? 0,
          totalScore: e.totalScore ?? 0,
          passed: e.passed === true,
          correctCount,
          wrongCount,
          unansweredCount,
          totalCount,
          submitTime
        }
      }))

      this.setData({ records, loading: false })
    } catch (e) {
      console.error('[records] load error', e)
      this.setData({ loading: false })
      wx.showToast({ title: '加载失败', icon: 'none' })
    }
  },

  // 跳转到考试结果页
  goRecord(e) {
    const { id, examId } = e.currentTarget.dataset
    wx.navigateTo({
      url: `/pages/exam/result/result?id=${id}&examId=${examId}&from=records`
    })
  }
})
