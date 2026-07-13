// pages/course/my-courses/my-courses.js
// 我的学习：已报名课程列表（含学习进度）
const courseApi = require('../../../api/course')

Page({
  data: {
    list: [],         // 课程列表（含 progress 字段）
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false,   // 首次加载显示骨架屏
    appendLoading: false // 底部上拉加载更多
  },

  onLoad() {
    this.loadData(false)
  },

  onShow() {
    // 检查登录态
    const token = wx.getStorageSync('token')
    if (!token) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/login/login' })
      }, 800)
    }
  },

  // 加载数据；append=true 表示上拉追加
  async loadData(append = false) {
    if (append) {
      if (!this.data.hasMore) return
      this.setData({ appendLoading: true })
    } else {
      this.setData({ loading: true })
    }

    try {
      const pageNum = append ? this.data.pageNum + 1 : 1
      const res = await courseApi.getMyCourses({ pageNum, pageSize: this.data.pageSize })

      // 并发拉取每门课的学习进度
      // 修复：分母必须是课程总章节数（不是已学章节数）
      // 旧实现：3 章只学 1 章时，prog.length=1，进度算成 100%，错误
      // 新实现：并发拉学习记录 + 章节列表，用 chapters.length 做分母
      const withProgress = await Promise.all((res.records || []).map(async (c) => {
        try {
          const [progressList, chapters] = await Promise.all([
            courseApi.getProgress(c.id).catch(() => []),
            courseApi.getChapters(c.id).catch(() => [])
          ])
          let progress = 0
          if (chapters && chapters.length > 0) {
            // 已学章节按 chapterId 索引
            const pMap = {}
            ;(progressList || []).forEach(p => {
              if (p && p.chapterId != null) pMap[p.chapterId] = p.progress || 0
            })
            // 累加每章进度（未学章节算 0），除以总章节数
            const sum = chapters.reduce((s, ch) => s + (pMap[ch.id] ?? 0), 0)
            progress = Math.round(sum / chapters.length)
          }
          return { ...c, progress }
        } catch (e) {
          return { ...c, progress: 0 }
        }
      }))

      const list = append ? this.data.list.concat(withProgress) : withProgress
      this.setData({
        list,
        pageNum,
        hasMore: res.hasMore,
        loading: false,
        appendLoading: false
      })
    } catch (e) {
      console.error('[my-courses] load error', e)
      this.setData({ loading: false, appendLoading: false })
      wx.showToast({ title: '加载失败', icon: 'none' })
    }
  },

  // 章节数组 → 完成百分比（已废弃：旧实现分母错误，已改为上方 Promise.all 内联计算）
  // 保留空实现避免外部调用报错
  calcProgress(chapters) {
    return 0
  },

  // 上拉触底 → 加载下一页
  onReachBottom() {
    if (this.data.hasMore && !this.data.appendLoading) {
      this.loadData(true)
    }
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.loadData(false).then(() => {
      wx.stopPullDownRefresh()
    }).catch(() => {
      wx.stopPullDownRefresh()
    })
  },

  // 跳转到学习页
  goStudy(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/course/study/study?id=${id}` })
  },

  // 跳转到课程详情
  goDetail(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/course/detail/detail?id=${id}` })
  }
})
