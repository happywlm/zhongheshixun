// miniprogram/pages/consult/index/index.js
const consultApi = require('../../../api/consult')

Page({
  data: {
    // 输入的问题
    question: '',
    // 咨询记录列表（按时间倒序）
    consults: [],
    // 提交中状态
    submitting: false,
    // 分页
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loadingList: false
  },

  onLoad() {
    this.loadList()
  },

  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
    }
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, consults: [], hasMore: true })
    this.loadList(true)
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingList) {
      this.setData({ pageNum: this.data.pageNum + 1 })
      this.loadList()
    }
  },

  // 输入
  onInput(e) {
    this.setData({ question: e.detail.value })
  },

  // 提交问题
  async onSubmit() {
    const q = (this.data.question || '').trim()
    if (!q) {
      wx.showToast({ title: '请输入问题', icon: 'none' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })

    try {
      const res = await consultApi.ask(q)
      // 顶部插入新记录
      const newItem = {
        id: res.consultId,
        question: q,
        answer: res.autoReply || null,
        isAuto: res.matched ? 1 : 0,
        createTime: '刚刚',
        replyTime: res.matched ? '刚刚' : null,
        matched: res.matched
      }
      this.setData({
        consults: [newItem, ...this.data.consults],
        question: ''
      })
      if (res.matched) {
        wx.showToast({ title: '已智能回复', icon: 'success' })
      } else {
        wx.showToast({ title: '已提交，请等待人工回复', icon: 'none' })
      }
    } catch (e) {
      // 错误由 request 统一 toast
    } finally {
      this.setData({ submitting: false })
    }
  },

  // 加载咨询列表
  async loadList(refresh = false) {
    if (this.data.loadingList) return
    this.setData({ loadingList: true })
    try {
      const res = await consultApi.myList(this.data.pageNum, this.data.pageSize)
      // 后端返回 PageResult = { records, total, pageNum, pageSize }
      const list = (res.records || []).map(item => ({
        ...item,
        createTime: this.formatTime(item.createTime),
        replyTime: item.replyTime ? this.formatTime(item.replyTime) : null
      }))
      const consults = refresh ? list : [...this.data.consults, ...list]
      this.setData({
        consults,
        hasMore: list.length >= this.data.pageSize
      })
    } catch (e) {
      // error handled by request
    } finally {
      this.setData({ loadingList: false })
      wx.stopPullDownRefresh()
    }
  },

  // 转人工：将已自动回复的咨询转为人工工单
  async onTransferHuman(e) {
    const consultId = e.currentTarget.dataset.id
    if (!consultId) return
    wx.showModal({
      title: '转人工客服',
      content: '确认将此咨询转为人工处理？老师将尽快为您回复。',
      confirmText: '确认转人工',
      cancelText: '取消',
      success: async (res) => {
        if (!res.confirm) return
        try {
          wx.showLoading({ title: '处理中...' })
          await consultApi.transferHuman(consultId)
          wx.hideLoading()
          // 更新本地列表：清空答案、标记为等待人工回复
          const consults = this.data.consults.map(item => {
            if (item.id === consultId) {
              return {
                ...item,
                answer: null,
                isAuto: 0,
                replyTime: null,
                matched: false
              }
            }
            return item
          })
          this.setData({ consults })
          wx.showToast({ title: '已转人工，等待回复', icon: 'none' })
        } catch (e) {
          wx.hideLoading()
          // 错误由 request 统一 toast
        }
      }
    })
  },

  formatTime(t) {
    if (!t) return ''
    // 2026-07-08 10:15:30 → 07-08 10:15
    const d = new Date(t.replace(/-/g, '/'))
    if (isNaN(d.getTime())) return t
    const mo = (d.getMonth() + 1).toString().padStart(2, '0')
    const day = d.getDate().toString().padStart(2, '0')
    const h = d.getHours().toString().padStart(2, '0')
    const mi = d.getMinutes().toString().padStart(2, '0')
    return `${mo}-${day} ${h}:${mi}`
  }
})
