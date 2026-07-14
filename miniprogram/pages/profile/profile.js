// miniprogram/pages/profile/profile.js
const userApi = require('../../api/user')
const courseApi = require('../../api/course')
const examApi = require('../../api/exam')
const consultApi = require('../../api/consult')
const statsApi = require('../../api/stats')

Page({
  data: {
    profile: {},
    nicknameFirst: '学',
    stats: {
      courseCount: '-',
      studyHours: '-',
      examCount: '-',
      consultCount: 0
    },
    editVisible: false,
    editForm: {
      realName: '',
      phone: '',
      orgName: '',
      jobType: ''
    },
    jobTypes: [
      { label: '临床', value: '临床' },
      { label: '公卫', value: '公卫' },
      { label: '护理', value: '护理' },
      { label: '医技', value: '医技' },
      { label: '其他', value: '其他' }
    ]
  },

  onLoad() {
    this.loadProfile()
    this.loadStats()
  },

  // 下拉刷新
  onPullDownRefresh() {
    Promise.all([this.loadProfile(), this.loadStats()]).then(() => {
      wx.stopPullDownRefresh()
    }).catch(() => {
      wx.stopPullDownRefresh()
    })
  },

  onShow() {
    // 所有 tabBar 页统一登录态检查
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 修复 #3：检测 settings 页设置的编辑标记，自动打开编辑弹窗
    if (wx.getStorageSync('openEditFlag')) {
      wx.removeStorageSync('openEditFlag')
      // 延迟执行，确保页面已渲染
      setTimeout(() => this.onGoEdit(), 100)
    }
  },

  // 加载个人信息
  async loadProfile() {
    try {
      const res = await userApi.getProfile()
      const profile = res || {}
      const realName = profile.realName || '学员'
      this.setData({
        profile,
        nicknameFirst: realName.charAt(0)
      })
    } catch (e) {
      // request 已统一 toast；失败时使用本地缓存兜底
      const local = wx.getStorageSync('userInfo') || {}
      this.setData({
        profile: local,
        nicknameFirst: (local.realName || '学').charAt(0)
      })
    }
  },

  // 加载学习数据（并发请求课程/考试/咨询/统计真实接口）
  async loadStats() {
    const results = await Promise.allSettled([
      courseApi.getMyCourses({ pageNum: 1, pageSize: 50 }),
      examApi.getMyRecords(),
      consultApi.myList(1, 1),
      statsApi.getMyStats()
    ])

    const courseRes = results[0].status === 'fulfilled' ? results[0].value : null
    const examRes = results[1].status === 'fulfilled' ? results[1].value : null
    const consultRes = results[2].status === 'fulfilled' ? results[2].value : null
    const statsRes = results[3].status === 'fulfilled' ? results[3].value : null

    const courseTotal = courseRes ? (courseRes.total || (courseRes.records || []).length) : 0
    // 修复 #5：examApi.getMyRecords 经 api/exam.js 的 .map() 返回数组（后端 /exam/my-records 返回数组）
    // 兼容写法：数组取 length，PageResult 取 total/records.length
    const examTotal = Array.isArray(examRes) ? examRes.length
      : (examRes ? (examRes.total || (examRes.records || []).length) : 0)
    // 修复 #6：后端 consult/my 的 total 可能返回 0（count SQL bug），用 records.length 兜底
    const consultTotal = consultRes ? (consultRes.total || (consultRes.records || []).length) : 0

    // 学习时长：必须从后端 /stats/my 的 totalStudyHours 取真实值（study_record 秒级累加/3600）
    // 旧实现错误地把已报名课程的 totalHours（课程长度）累加，导致报名多门课就显示几十小时
    const hours = statsRes ? (statsRes.totalStudyHours ?? 0) : 0

    this.setData({
      stats: {
        courseCount: courseTotal,
        examCount: examTotal,
        consultCount: consultTotal,
        studyHours: hours
      }
    })
  },

  // 跳转 - 我的课程子页
  // 修复：my-courses 是 tabBar 页面，必须用 switchTab（navigateTo 跳 tabBar 页会失败）
  onGoMyCourse() {
    wx.switchTab({ url: '/pages/course/my-courses/my-courses' })
  },

  // 跳转 - 学习记录（考试记录时间线）
  onGoExam() {
    wx.navigateTo({ url: '/pages/profile/records/records' })
  },

  // 跳转 - 我的咨询
  onGoMyConsult() {
    wx.switchTab({ url: '/pages/consult/index/index' })
  },

  // 跳转 - 设置
  onGoSettings() {
    wx.navigateTo({ url: '/pages/profile/settings/settings' })
  },

  // 跳转 - 学习中心首页
  onGoStudy() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  // 编辑资料
  onGoEdit() {
    const p = this.data.profile || {}
    this.setData({
      editVisible: true,
      editForm: {
        realName: p.realName || '',
        phone: p.phone || '',
        orgName: p.orgName || '',
        jobType: p.jobType || ''
      }
    })
  },

  onCloseEdit() {
    this.setData({ editVisible: false })
  },

  onJobChange(e) {
    const idx = e.detail.value
    const job = this.data.jobTypes[idx]
    this.setData({ 'editForm.jobType': job ? job.value : '' })
  },

  // 显式 input 处理（替代 model:value，确保双向绑定可靠）
  onInputRealName(e) {
    this.setData({ 'editForm.realName': e.detail.value })
  },
  onInputPhone(e) {
    this.setData({ 'editForm.phone': e.detail.value })
  },
  onInputOrgName(e) {
    this.setData({ 'editForm.orgName': e.detail.value })
  },

  // 保存资料
  async onSaveProfile() {
    const form = this.data.editForm
    console.log('[onSaveProfile] editForm:', JSON.stringify(form))
    if (!form.realName || !form.realName.trim()) {
      wx.showToast({ title: '请输入姓名', icon: 'none' })
      return
    }
    try {
      const updated = await userApi.updateProfile({
        realName: form.realName.trim(),
        phone: form.phone,
        orgName: form.orgName,
        jobType: form.jobType
      })
      console.log('[onSaveProfile] 后端返回 updated:', JSON.stringify(updated))
      wx.showToast({ title: '保存成功', icon: 'success' })
      this.setData({ editVisible: false })

      // 修复"保存后没变化"：后端 updateProfile 返回的 data 只有部分字段
      // （realName/orgName/phone/id/avatar/jobType/email），缺少 role/roleName/username
      // 不能直接用返回值覆盖 profile，应合并到现有 profile 上
      const mergedProfile = Object.assign({}, this.data.profile, updated || {})
      console.log('[onSaveProfile] 合并后 mergedProfile:', JSON.stringify(mergedProfile))
      this.setData({
        profile: mergedProfile,
        nicknameFirst: (mergedProfile.realName || '学').charAt(0)
      })

      // 同步更新本地缓存
      const local = wx.getStorageSync('userInfo') || {}
      local.realName = mergedProfile.realName
      local.orgName = mergedProfile.orgName
      local.phone = mergedProfile.phone
      local.jobType = mergedProfile.jobType
      wx.setStorageSync('userInfo', local)

      // 重新从后端拉取 profile，确保 UI 与后端完全一致（兜底）
      setTimeout(() => this.loadProfile(), 500)
    } catch (e) {
      console.error('[onSaveProfile] 保存失败:', e)
      // 错误由 request 统一 toast
    }
  },

  // 离线学习（待功能）
  onGoOffline() {
    wx.showToast({ title: '离线学习开发中', icon: 'none' })
  },

  // 退出登录
  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('userInfo')
          wx.reLaunch({ url: '/pages/login/login' })
        }
      }
    })
  }
})
