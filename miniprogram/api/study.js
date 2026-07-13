// miniprogram/api/study.js
// 学习 / 进度相关接口（我的课程、报名、进度上报、综合详情）
const { request } = require('./request')

// ============= 字段映射 =============
const TYPE_MAP = { 1: '公开课', 2: '必修课' }

// 后端 Course 实体 → 前端 list 卡片期望字段
function mapCourse(c) {
  return {
    id: c.id,
    title: c.title,
    coverUrl: c.coverUrl || c.cover_url || '',
    description: c.description || '',
    courseType: c.courseType ?? c.course_type ?? 1,
    courseTypeText: TYPE_MAP[c.courseType ?? c.course_type] || '公开课',
    teacherName: c.teacherName || c.teacher_name || '',
    totalHours: c.totalHours ?? c.total_hours ?? 0,
    offlineFlag: c.offlineFlag ?? c.offline_flag ?? 0
  }
}

// PageResult → list 期望结构
function wrapPage(res, mapper) {
  const records = (res.records || []).map(mapper)
  return {
    records,
    total: res.total || 0,
    pageNum: res.pageNum,
    pageSize: res.pageSize,
    hasMore: records.length >= (res.pageSize || 10)
  }
}

// chapter 字段映射
function mapChapter(ch) {
  return {
    id: ch.id,
    title: ch.title,
    sortOrder: ch.sortOrder ?? ch.sort_order ?? 0,
    videoUrl: ch.videoUrl || ch.video_url || '',
    duration: ch.duration || 0,
    progress: ch.progress ?? null
  }
}

module.exports = {
  // 我的课程（已报名）— 返回 PageResult
  getMyCourses: (params) => {
    const query = {
      pageNum: params.pageNum || 1,
      pageSize: params.pageSize || 10
    }
    return request('/study/my-courses', 'GET', query).then(res => wrapPage(res, mapCourse))
  },

  // 报名课程
  enroll: (courseId) => request('/study/enroll', 'POST', { courseId }),

  // 上报学习进度
  // data: { courseId, chapterId, progress, studyDuration, lastPosition }
  reportProgress: (data) => request('/study/progress', 'POST', data),

  // 查询某课程学习进度（章节级）
  // 返回 [{ chapterId, progress, studyDuration, lastPosition }]
  getProgress: (courseId) => request(`/study/progress/${courseId}`, 'GET'),

  // 综合详情接口：课程 + 章节 + 进度 + 是否报名
  // 在函数内部 require('./course')，避免循环引用
  getCourseDetail: (courseId) => {
    const courseApi = require('./course')
    return Promise.all([
      courseApi.getDetail(courseId),
      courseApi.getChapters(courseId),
      // getProgress 可能返回空数组，容错
      request(`/study/progress/${courseId}`, 'GET').then(list => list || []),
      // 修复 #8：用轻量 check-enrolled 接口替代 my-courses?pageSize=100
      // 修复 #2：原实现 c.id === courseId 类型不匹配（数字 vs 字符串）永远 false
      request('/study/check-enrolled', 'GET', { courseId })
        .then(res => res === true)
        .catch(() => false)
    ]).then(([detail, chapters, progressList, enrolled]) => {
      // 章节级进度 map: chapterId → progress
      const progressMap = {}
      progressList.forEach(p => { progressMap[p.chapterId] = p })
      // 把进度信息合并到 chapter 上
      const mergedChapters = chapters.map(ch => {
        const p = progressMap[ch.id] || {}
        return {
          ...ch,
          progress: p.progress ?? 0,
          studyDuration: p.studyDuration ?? p.study_duration ?? 0,
          lastPosition: p.lastPosition ?? p.last_position ?? 0
        }
      })
      // 总进度 = 各章节 progress 平均
      const overallProgress = mergedChapters.length > 0
        ? Math.round(mergedChapters.reduce((s, ch) => s + (ch.progress || 0), 0) / mergedChapters.length)
        : 0
      return {
        course: detail,
        chapters: mergedChapters,
        progress: progressList,
        enrolled,
        overallProgress
      }
    })
  }
}
