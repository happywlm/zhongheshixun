// pages/profile/records/records.js
// 学习记录：我的考试记录时间线
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
      const list = await examApi.getMyRecords()
      // 按提交时间倒序
      const sorted = (list || []).sort((a, b) => {
        const ta = new Date(a.submitTime).getTime() || 0
        const tb = new Date(b.submitTime).getTime() || 0
        return tb - ta
      })

      // 修复：ExamRecord 实体只有 examId/score/submitTime，缺 examTitle/correctCount/totalCount/passed
      // 用每个 record 的 examId 调 /exam/result 拉完整数据（并发）
      const enriched = await Promise.all(sorted.map(async (r) => {
        if (!r.examId) return r
        try {
          const detail = await examApi.getResult(r.examId)
          return {
            ...r,
            examTitle: detail.examTitle || r.examTitle || `考试 #${r.examId}`,
            score: detail.score ?? r.score ?? 0,
            totalScore: detail.totalScore ?? 0,
            passed: detail.passed ?? r.passed ?? false,
            correctCount: detail.correctCount ?? 0,
            wrongCount: detail.wrongCount ?? 0,
            unansweredCount: detail.unansweredCount ?? 0,
            totalCount: (detail.correctCount || 0) + (detail.wrongCount || 0) + (detail.unansweredCount || 0)
          }
        } catch (e) {
          // 单条失败不影响其他，返回原始数据
          return { ...r, examTitle: r.examTitle || `考试 #${r.examId}` }
        }
      }))

      this.setData({ records: enriched, loading: false })
    } catch (e) {
      console.error('[records] load error', e)
      this.setData({ loading: false })
      wx.showToast({ title: '加载失败', icon: 'none' })
    }
  },

  // 跳转到考试结果页
  // 修复：补传 examId，result 页用它调 /exam/result 拉完整对错统计
  // 注意：WXML 里 data-exam-id 会自动转成 dataset.examId（驼峰），不是 examid
  goRecord(e) {
    const { id, examId } = e.currentTarget.dataset
    console.log('[goRecord] dataset:', e.currentTarget.dataset, 'id:', id, 'examId:', examId)
    wx.navigateTo({
      url: `/pages/exam/result/result?id=${id}&examId=${examId}&from=records`
    })
  }
})
