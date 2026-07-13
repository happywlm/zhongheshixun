// pages/profile/settings/settings.js
// 设置：静态菜单（我的资料 / 清理缓存 / 关于 / 退出登录）
// 注：不调用 api，菜单数据静态定义
Page({
  data: {
    version: '1.0.0',
    cacheSize: 0,         // KB
    userInfo: null,       // 从 storage 取
    menuItems: [
      {
        icon: '👤',
        title: '我的资料',
        url: '/pages/profile/edit/edit',
        handler: 'goProfile'
      },
      {
        icon: '🧹',
        title: '清理缓存',
        url: '',
        handler: 'onClearCache'
      },
      {
        icon: 'ℹ️',
        title: '关于',
        url: '',
        handler: 'onAbout'
      }
    ]
  },

  onLoad() {
    this.refreshCacheSize()
  },

  onShow() {
    // 刷新用户信息
    const userInfo = wx.getStorageSync('userInfo') || {}
    this.setData({ userInfo })
    this.refreshCacheSize()
  },

  // 刷新缓存大小（KB）
  refreshCacheSize() {
    try {
      const info = wx.getStorageInfoSync()
      this.setData({ cacheSize: info.currentSize || 0 })
    } catch (e) {
      this.setData({ cacheSize: 0 })
    }
  },

  // 菜单点击分发
  onMenuTap(e) {
    const { handler } = e.currentTarget.dataset
    if (handler && typeof this[handler] === 'function') {
      this[handler]()
    }
  },

  // 跳转我的资料 - 复用 profile 页已有的编辑表单
  // 修复 #3：旧实现只显示"开发中"toast，edit 页面未注册无法跳转
  // 方案：设置标记后 navigateBack 回 profile 页，profile.onShow 检测标记并自动打开编辑弹窗
  goProfile() {
    wx.setStorageSync('openEditFlag', true)
    wx.navigateBack()
  },

  // 清理缓存
  onClearCache() {
    wx.showModal({
      title: '清理缓存',
      content: '确定要清除本地缓存数据吗？',
      success: (res) => {
        if (res.confirm) {
          try {
            wx.clearStorageSync()
            this.setData({ cacheSize: 0 })
            wx.showToast({ title: '已清理', icon: 'success' })
          } catch (e) {
            wx.showToast({ title: '清理失败', icon: 'none' })
          }
        }
      }
    })
  },

  // 关于
  onAbout() {
    wx.showModal({
      title: '关于',
      content: `四川省基层卫生人员网络培训平台\n版本：${this.data.version}\n© 2026 培训平台团队`,
      showCancel: false,
      confirmButtonText: '确定'
    })
  },

  // 退出登录
  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: (res) => {
        if (res.confirm) {
          try {
            wx.removeStorageSync('token')
            wx.removeStorageSync('userInfo')
          } catch (e) {}
          wx.reLaunch({ url: '/pages/login/login' })
        }
      }
    })
  }
})
