// miniprogram/pages/course/detail/detail.js
const courseApi = require('../../../api/course')

Page({
  data: {
    courseId: null,
    detail: null,
    loading: true,
    enrolled: false,      // 是否已报名
    progressMap: {}       // 章节级学习进度：chapterId → { progress }
  },

  onLoad(options) {
    const id = options.id
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1000)
      return
    }
    this.setData({ courseId: id })
    this.loadDetail(id)
    // 查询报名与进度
    this.refreshEnrollState(id)
  },

  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 返回时刷新报名/进度
    if (this.data.courseId) {
      this.refreshEnrollState(this.data.courseId)
    }
  },

  async loadDetail(id) {
    wx.showLoading({ title: '加载中' })
    try {
      const detail = await courseApi.getDetail(id)
      this.setData({ detail, loading: false })
    } catch (err) {
      console.error('加载课程详情失败', err)
      wx.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      wx.hideLoading()
    }
  },

  // 查询我是否报名了该课程 + 章节学习进度
  // 修复 #7：旧实现用"有进度"判断"已报名"，刚报名未学的课程会被误判为未报名
  // 新逻辑：优先用 my-courses 判断报名状态，进度只用于显示进度条
  async refreshEnrollState(id) {
    try {
      // 并发：查报名状态 + 查学习进度
      const [my, progress] = await Promise.all([
        courseApi.getMyCourses({ pageNum: 1, pageSize: 100 }).catch(() => ({ records: [] })),
        courseApi.getProgress(id).catch(() => [])
      ])
      // 修复 #2 类似问题：id 类型归一化比较
      const enrolled = (my.records || []).some(c => String(c.id) === String(id))
      // 修复进度错位：progress 是"仅有学习记录的章节"数组，按 chapterId 标识
      // 旧实现直接存数组，WXML 用 progress[index] 下标匹配 → 顺序与章节列表无关，进度全错
      // 新实现转为 progressMap（chapterId → { progress }），WXML 用 progressMap[chapter.id] 精确匹配
      const progressMap = {}
      ;(progress || []).forEach(p => {
        if (p && p.chapterId != null) {
          progressMap[p.chapterId] = { progress: p.progress || 0 }
        }
      })
      this.setData({ enrolled, progressMap })
    } catch (e) {
      // 忽略
    }
  },

  // 报名
  // 修复 #4：后端对"已报名"返回 code=1005，request.js 会 toast + reject，
  // 旧 catch 再显示"报名失败，请重试"造成双重 toast
  // 新 catch 判断 code=1005 时不重复 toast
  onEnroll() {
    if (this.data.enrolled) {
      wx.showToast({ title: '已报名', icon: 'none' })
      return
    }
    wx.showModal({
      title: '提示',
      content: '确定要报名这门课程吗？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await courseApi.enroll(this.data.courseId)
          wx.showToast({ title: '报名成功', icon: 'success' })
          this.setData({ enrolled: true })
        } catch (e) {
          // request.js 已对非 200 code 统一 toast（含"已报名该课程"）
          // 这里只在非业务错误（无 code 字段）时兜底提示
          if (!e || !e.code) {
            wx.showToast({ title: '报名失败，请重试', icon: 'none' })
          }
        }
      }
    })
  },

  onStartLearn() {
    const { detail } = this.data
    if (!detail || !detail.chapters || detail.chapters.length === 0) {
      wx.showToast({ title: '暂无章节', icon: 'none' })
      return
    }
    // 跳转到学习页面
    wx.navigateTo({ url: `/pages/course/study/study?id=${this.data.courseId}` })
  }
})
