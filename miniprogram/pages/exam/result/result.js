// pages/exam/result/result.js
const examApi = require('../../../api/exam')

Page({
  data: {
    id: null,            // 记录 id（优先）
    examId: null,        // 考试 id（用于拉详情）
    score: 0,
    totalScore: 0,
    passed: false,
    correctCount: 0,
    wrongCount: 0,
    totalCount: 0,
    unansweredCount: 0,
    correctRate: 0,
    from: 'do'
  },

  onLoad(options) {
    // 严格校验 examId：必须是有效数字，否则后端 @RequestParam Long examId 转换失败 → 500 "系统繁忙"
    const rawExamId = options.examId
    const parsedExamId = rawExamId != null && rawExamId !== '' && rawExamId !== 'undefined'
      ? Number(rawExamId) : NaN
    const rawId = options.id
    const parsedId = rawId != null && rawId !== '' && rawId !== 'undefined'
      ? Number(rawId) : NaN

    this.setData({
      id: isNaN(parsedId) ? null : parsedId,
      examId: isNaN(parsedExamId) ? null : parsedExamId,
      score: Number(options.score) || 0,
      passed: options.passed === 'true',
      correctCount: Number(options.correct) || 0,
      totalCount: Number(options.total) || 0,
      from: options.from || 'do'
    })

    console.log('[result.onLoad] options:', options, 'parsed examId:', parsedExamId, 'id:', parsedId)

    // 修复：只要有有效 examId，就调 /exam/result 拉完整对错统计
    if (!isNaN(parsedExamId) && parsedExamId > 0) {
      this.loadResult(parsedExamId)
    } else if (!isNaN(parsedId) && !options.score) {
      // 兜底：只有 recordId 没 examId 也没 score，用旧逻辑查记录
      this.loadRecord(parsedId)
    } else if (isNaN(parsedExamId) && isNaN(parsedId)) {
      // 两个 id 都没有，提示错误
      wx.showToast({ title: '参数错误，缺少考试ID', icon: 'none', duration: 2500 })
    }
  },

  // 从 examId 拉完整结果（含对错统计）
  async loadResult(examId) {
    try {
      console.log('[loadResult] 调用 /exam/result?examId=', examId)
      const data = await examApi.getResult(examId)
      console.log('[loadResult] 返回数据:', data)
      if (!data) return
      // 总题数 = 答对 + 答错 + 未作答
      const correct = data.correctCount || 0
      const wrong = data.wrongCount || 0
      const unanswered = data.unansweredCount || 0
      const total = correct + wrong + unanswered
      this.setData({
        score: data.score ?? 0,
        totalScore: data.totalScore ?? 0,
        passed: !!data.passed,
        correctCount: correct,
        wrongCount: wrong,
        totalCount: total,
        unansweredCount: unanswered,
        correctRate: data.correctRate ? Math.round(data.correctRate * 100) : 0
      })
    } catch (e) {
      console.warn('[loadResult] 拉取考试结果失败', e)
      // 失败时保留 query 传入的 score，对错统计保持 0
      wx.showToast({ title: '加载考试详情失败', icon: 'none', duration: 2000 })
    }
  },

  // 旧兜底：从 recordId 查记录（仅当无 examId 时使用）
  async loadRecord(recordId) {
    try {
      const list = await examApi.getMyRecords()
      const found = (list || []).find(r => r.id === recordId || r.examId === recordId)
      if (found) {
        this.setData({
          score: found.score || 0,
          passed: !!found.passed,
          correctCount: found.correctCount || 0,
          totalCount: found.totalCount || 0
        })
      }
    } catch (e) {
      // 忽略
    }
  },

  // 返回考试列表
  goBack() {
    wx.redirectTo({ url: '/pages/exam/list/list' })
  },

  // 返回首页
  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  // 分享
  onShareAppMessage() {
    return {
      title: `我考了 ${this.data.score} 分，一起来学习吧！`,
      path: '/pages/index/index'
    }
  }
})
