// pages/exam/list/list.js
const examApi = require('../../../api/exam')

// 网页端考试地址（生产环境请替换为真实域名）
// 开发环境: http://localhost:5174/exam
// 生产环境: https://你的域名/exam
const WEB_EXAM_URL = 'http://localhost:5174/exam'

Page({
  data: {
    allExams: [],         // exam/list
    list: [],             // 前端 UI 用（已合并字段）
    filteredList: [],     // 修复 #1：WXML 不支持函数调用，预计算过滤结果存 data
    tab: 'all',
    loading: false
  },

  onLoad() {
    this.loadData()
  },

  onShow() {
    // 已登录才拉真实数据
    if (wx.getStorageSync('token')) {
      this.loadData()
    } else {
      wx.reLaunch({ url: '/pages/login/login' })
    }
  },

  // Tab 切换
  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab) return
    this.setData({ tab })
    // 修复 #1：切换 tab 后立即预计算过滤列表
    this.updateFilteredList()
  },

  // 修复 #1：预计算过滤列表（WXML 不支持函数调用，必须存 data）
  updateFilteredList() {
    const { list, tab } = this.data
    let filtered = list
    if (tab === 'pending') filtered = list.filter(i => i.status === 0)
    else if (tab === 'done') filtered = list.filter(i => i.status === 1)
    this.setData({ filteredList: filtered })
  },

  // 将后端 ExamListVO 转为 list UI 项
  // 移除重考机制：本项目考试不通过 → 联系老师开发新考试，不再有"重考次数"概念
  mapExamItem(e) {
    // 后端 status 语义：0未开始 1已提交 2已批阅；前端归一化为 0/1（有记录即 1）
    const hasRecord = e.status != null && e.status >= 1
    return {
      id: e.id,
      title: e.title,
      duration: e.duration || 0,
      questionCount: e.questionCount || 0,
      maxRetry: e.maxRetry ?? 1,
      examType: e.examType,
      courseId: e.courseId,
      status: hasRecord ? 1 : 0,
      score: e.score,
      recordId: e.recordId,
      passed: e.passed
    }
  },

  async loadData() {
    if (this.data.loading) return
    this.setData({ loading: true })

    wx.showLoading({ title: '加载中' })
    try {
      // 修复 Bug #5：exam/list 返回的 ExamListVO 已含学员维度字段，
      // 无需再调 /exam/my-records 拼接（旧实现字段不匹配导致状态/成绩/重考次数全错）
      const exams = await examApi.getList().catch(() => [])
      const list = exams.map(e => this.mapExamItem(e))

      this.setData({
        allExams: exams,
        list
      })
      // 修复 #1：数据加载后预计算过滤列表
      this.updateFilteredList()
    } catch (e) {
      // 错误由 request 统一 toast
    } finally {
      wx.hideLoading()
      this.setData({ loading: false })
    }
  },

  goExam(e) {
    const { id, score, recordId, title } = e.currentTarget.dataset
    // Number() 归一化：dataset 传递的值可能为字符串或数字，统一转数字比较
    const status = Number(e.currentTarget.dataset.status)
    console.log('[goExam] 触发！dataset:', e.currentTarget.dataset, 'status:', status, 'id:', id, 'title:', title)
    // 已考：跳转成绩页
    // 修复：补传 examId，result 页用它调 /exam/result 拉对错统计
    if (status === 1 && recordId) {
      wx.navigateTo({
        url: `/pages/exam/result/result?id=${recordId}&examId=${id}&score=${score}&from=list`
      })
      return
    }
    if (status === 1) {
      // 兜底：没记录 id 用 examId
      wx.navigateTo({
        url: `/pages/exam/result/result?id=${id}&examId=${id}&score=${score}&from=list`
      })
      return
    }
    // 待考：弹框引导至网页端考试
    console.log('[goExam] 待考，调用 showWebTip')
    this.showWebTip(id, title)
  },

  // 弹框提示：请使用网页端参加考试
  showWebTip(examId, examTitle) {
    console.log('[showWebTip] 开始弹框 examId:', examId, 'title:', examTitle)
    wx.showModal({
      title: '请使用网页端参加考试',
      content: '小程序端暂不支持在线考试，请在电脑或手机浏览器中打开网页端完成考试。',
      showCancel: true,
      cancelText: '取消',
      confirmText: '复制链接',
      success: (res) => {
        console.log('[showWebTip] modal success:', res)
        if (res.confirm) {
          this.copyWebUrl(examId, examTitle)
        }
      },
      fail: (err) => {
        console.error('[showWebTip] modal fail:', err)
        // 兜底：modal 调用失败时用 toast 提示
        wx.showToast({
          title: '请使用网页端参加考试',
          icon: 'none',
          duration: 3000
        })
      }
    })
  },

  // 复制网页端考试链接到剪贴板
  copyWebUrl(examId, examTitle) {
    const url = `${WEB_EXAM_URL}?examId=${examId}`
    wx.setClipboardData({
      data: url,
      success: () => {
        wx.showToast({ title: '链接已复制，请在浏览器打开', icon: 'none', duration: 2500 })
      },
      fail: () => {
        wx.showToast({ title: '复制失败，请手动访问网页端', icon: 'none', duration: 2500 })
      }
    })
  },

  onPullDownRefresh() {
    this.loadData().then(() => wx.stopPullDownRefresh())
  }
})
