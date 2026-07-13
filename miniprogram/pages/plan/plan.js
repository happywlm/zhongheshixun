// miniprogram/pages/plan/plan.js
const courseApi = require('../../api/course')
const examApi = require('../../api/exam')
const statsApi = require('../../api/stats')

Page({
  data: {
    planList: [],
    stats: {
      totalCourse: 0,
      finished: 0,
      learning: 0,
      hours: 0
    },
    monthProgress: 0
  },

  onLoad() {
    this.loadPlan()
  },

  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
    }
  },

  onPullDownRefresh() {
    this.loadPlan().then(() => wx.stopPullDownRefresh())
  },

  async loadPlan() {
    wx.showLoading({ title: '加载中' })
    try {
      // 1. 取我报名的课程（真实）
      const courseRes = await courseApi.getMyCourses({ pageNum: 1, pageSize: 50 })
        .catch(() => ({ records: [], total: 0 }))
      const enrolledRecords = courseRes.records || []

      // 2. 取我的考试记录（真实，用于统计已完成）+ 真实学时（/stats/my）
      // 并发：考试记录 + 个人统计
      const [examRes, statsRes] = await Promise.all([
        examApi.getMyRecords().catch(() => []),
        statsApi.getMyStats().catch(() => null)
      ])
      const examRecords = Array.isArray(examRes) ? examRes : []
      const passedExams = examRecords.filter(r => r.passed).length
      // 真实学习时长：从后端 /stats/my 的 totalStudyHours 取（study_record 秒级累加/3600）
      // 旧实现错误地把课程的 totalHours（课程总长度）累加，导致报名多门课就显示几十小时
      const realHours = statsRes ? (statsRes.totalStudyHours ?? 0) : 0

      // 3. 真实学习进度（章节级）
      // 修复：分母必须是课程总章节数（不是已学章节数）
      // 旧实现：3 章只学 1 章时，p.length=1，进度算成 100%，错误
      // 新实现：并发拉学习记录 + 章节列表，用 chapters.length 做分母
      const planList = await Promise.all(
        enrolledRecords.map(async (c) => {
          let progress = 0
          try {
            const [progressList, chapters] = await Promise.all([
              courseApi.getProgress(c.id).catch(() => []),
              courseApi.getChapters(c.id).catch(() => [])
            ])
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
          } catch (e) {
            // 忽略，使用 0
          }

          return {
            id: c.id,
            title: c.title,
            courseType: c.courseType ?? c.course_type,
            teacher: c.teacherName || c.teacher_name || '专家团队',
            description: c.description || '',
            totalHours: c.totalHours ?? (c.total_hours || 0),
            progress
          }
        })
      )

      // 统计：已完成为 progress===100 的课程数 + passedExams
      const finished = planList.filter(p => p.progress >= 100).length + passedExams
      const learning = planList.filter(p => p.progress > 0 && p.progress < 100).length
      const total = planList.length
      const monthProgress = total === 0 ? 0 : Math.round((finished / Math.max(1, total)) * 100)
      // 学习时长使用后端 /stats/my 真实值，不再用课程 totalHours 累加
      const hours = realHours

      this.setData({
        planList,
        stats: {
          totalCourse: total,
          finished,
          learning,
          hours
        },
        monthProgress
      })
    } catch (e) {
      // request 统一 toast
    } finally {
      wx.hideLoading()
    }
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/course/detail/detail?id=${id}` })
  }
})
