// miniprogram/pages/course/study/study.js
// 课程学习页：视频占位图 + 章节切换 + "标记学完"上报进度
// 本项目不做真实视频播放，用 /images/video-placeholder.png 静态截图代替
const studyApi = require('../../../api/study')

Page({
  data: {
    courseId: null,
    course: null,          // 课程信息
    chapters: [],          // 章节列表（含 progress/lastPosition）
    currentIndex: 0,       // 当前章节下标
    currentChapter: null,  // 当前章节对象
    progressMap: {},       // chapterId → { progress, lastPosition, studyDuration }
    overallProgress: 0,    // 总进度 0-100
    loading: true
  },

  onLoad(options) {
    // 登录态检查
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }

    const courseId = options.id
    if (!courseId) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1000)
      return
    }

    this.setData({ courseId })
    this.loadCourseDetail(courseId)
  },

  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
    }
  },

  // 一次性拉取：课程 + 章节 + 进度 + 是否报名
  async loadCourseDetail(courseId) {
    wx.showLoading({ title: '加载中' })
    try {
      const res = await studyApi.getCourseDetail(courseId)
      const { course, chapters, progress, enrolled, overallProgress } = res

      // 未报名 → 提示并返回
      if (!enrolled) {
        wx.showToast({ title: '请先报名', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 1200)
        return
      }

      // 按 chapterId 索引进度
      const progressMap = {}
      ;(progress || []).forEach(p => {
        progressMap[p.chapterId] = {
          progress: p.progress ?? 0,
          lastPosition: p.lastPosition ?? p.last_position ?? 0,
          studyDuration: p.studyDuration ?? p.study_duration ?? 0
        }
      })

      const idx = this._findInitialIndex(chapters, progressMap)
      const currentChapter = chapters[idx] || null

      this.setData({
        course,
        chapters,
        progressMap,
        overallProgress: overallProgress || 0,
        currentIndex: idx,
        currentChapter,
        loading: false
      })
    } catch (err) {
      console.error('加载课程详情失败', err)
      wx.showToast({ title: '加载失败，请重试', icon: 'none' })
      this.setData({ loading: false })
    } finally {
      wx.hideLoading()
    }
  },

  // 找初始播放章节：优先第一个 progress<100 的章节，否则第 0 章
  _findInitialIndex(chapters, progressMap) {
    if (!chapters || chapters.length === 0) return 0
    for (let i = 0; i < chapters.length; i++) {
      const p = progressMap[chapters[i].id]
      if (!p || p.progress < 100) return i
    }
    return 0
  },

  // 章节切换
  onSwitchChapter(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (index === this.data.currentIndex) return
    const chapter = this.data.chapters[index]
    if (!chapter) return

    this.setData({
      currentIndex: index,
      currentChapter: chapter
    })
  },

  // 计算总进度 = 各章节 progress 平均
  _calcOverall(progressMap) {
    const chapters = this.data.chapters
    if (!chapters || chapters.length === 0) return 0
    const sum = chapters.reduce((s, ch) => s + (progressMap[ch.id]?.progress || 0), 0)
    return Math.round(sum / chapters.length)
  },

  // "标记学完"按钮：把当前章节进度设为 100 并上报
  // 本项目无真实视频，学完判定依赖学员手动点击
  onMarkDone() {
    const chapter = this.data.currentChapter
    if (!chapter) return
    const progressMap = { ...this.data.progressMap }
    progressMap[chapter.id] = {
      ...(progressMap[chapter.id] || {}),
      progress: 100,
      lastPosition: 0,
      studyDuration: progressMap[chapter.id]?.studyDuration || 0
    }
    const overallProgress = this._calcOverall(progressMap)
    this.setData({ progressMap, overallProgress })

    studyApi.reportProgress({
      courseId: this.data.courseId,
      chapterId: chapter.id,
      progress: 100,
      studyDuration: 0,
      lastPosition: 0
    }).then(() => {
      wx.showToast({ title: '已完成本章节', icon: 'success' })
    }).catch(err => {
      console.warn('标记学完上报失败', err)
      wx.showToast({ title: '标记失败', icon: 'none' })
    })
  },

  // 页面卸载时上报最终进度
  onUnload() {
    if (!this.data.currentChapter) return
    const chapterId = this.data.currentChapter.id
    const p = this.data.progressMap[chapterId] || {}
    studyApi.reportProgress({
      courseId: this.data.courseId,
      chapterId,
      progress: p.progress || 0,
      studyDuration: 10,
      lastPosition: p.lastPosition || 0
    }).catch(err => {
      console.warn('最终进度上报失败', err)
    })
  }
})
