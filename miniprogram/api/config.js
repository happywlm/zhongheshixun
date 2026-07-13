// miniprogram/api/config.js
module.exports = {
  // 小程序 API 地址（training-api 端口 9899）
  // ⚠️ 为避开 8081 僵尸死锁，已迁移至 9899（2026-07-09）
  // 真机联调：手机无法访问 localhost，必须填电脑局域网 IP（当前 192.168.70.227）。
  // 模拟器(devtools)仍走 localhost，二者互不干扰。
  // ⚠️ 若换了 WiFi / 重启路由器导致 IP 变化，请同步修改此处 IP。
  baseURL: (() => {
    let host = '192.168.70.227'
    try { if (wx.getSystemInfoSync().platform === 'devtools') host = 'localhost' } catch (e) {}
    return `http://${host}:9899/api`
  })(),
  // 请求超时 10 秒（弱网环境）
  timeout: 10000,
  // 离线包本地存储目录名
  offlineDir: 'offline_courses'
}
