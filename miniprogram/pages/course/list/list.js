// pages/course/list/list.js
const courseApi = require('../../../api/course')

Page({
  data: {
    list: [],
    pageNum: 1,
    pageSize: 10,
    keyword: '',
    courseType: '',
    hasMore: true,
    loading: false,
    mode: 'all',          // all=全部课程，mine=我的课程
    filters: [
      { label: '全部', value: '' },
      { label: '必修', value: '2' },
      { label: '公开', value: '1' }
    ]
  },

  onLoad(options) {
    // 支持 ?mode=mine 从 profile/plan 跳转进入"我的课程"
    const mode = options.mode || 'all'
    this.setData({ mode })
    this.loadData()
  },

  onShow() {
    // 登录态检查
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
    }
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, list: [], hasMore: true })
    this.loadData().then(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loading) return
    this.setData({ pageNum: this.data.pageNum + 1 })
    this.loadData(true)
  },

  // 实时记录输入框值（不立即搜索，避免每次输入都请求）
  onSearchInput(e) {
    this.setData({ keyword: (e.detail.value || '').trim() })
  },

  // 点击"搜索"按钮触发
  onSearchClick() {
    this.setData({ pageNum: 1, list: [], hasMore: true })
    this.loadData()
  },

  // 键盘"搜索"键触发
  onSearch(e) {
    this.setData({
      keyword: (e.detail.value || '').trim(),
      pageNum: 1,
      list: [],
      hasMore: true
    })
    this.loadData()
  },

  // 清空搜索关键词
  onClearKeyword() {
    this.setData({
      keyword: '',
      pageNum: 1,
      list: [],
      hasMore: true
    })
    this.loadData()
  },

  onFilter(e) {
    const type = e.currentTarget.dataset.type
    if (type === this.data.courseType) return
    this.setData({
      courseType: type,
      pageNum: 1,
      list: [],
      hasMore: true
    })
    this.loadData()
  },

  // mode=all 用 getList，mode=mine 用 getMyCourses
  async loadData(append = false) {
    if (this.data.loading) return
    wx.showLoading({ title: '加载中' })
    this.setData({ loading: true })
    try {
      const params = {
        pageNum: this.data.pageNum,
        pageSize: this.data.pageSize,
        title: this.data.keyword,
        courseType: this.data.courseType
      }
      const api = this.data.mode === 'mine'
        ? courseApi.getMyCourses(params)
        : courseApi.getList(params)
      const res = await api
      const newList = append ? this.data.list.concat(res.records) : res.records
      this.setData({
        list: newList,
        hasMore: res.hasMore !== undefined ? res.hasMore : newList.length < res.total
      })
    } catch (err) {
      console.error('加载课程列表失败', err)
    } finally {
      wx.hideLoading()
      this.setData({ loading: false })
    }
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/course/detail/detail?id=${id}` })
  }
})
