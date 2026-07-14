import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 创建 axios 实例，baseURL 走 /api 代理到 training-api 后端（9899）
const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  // 兜底：当响应体为空或非 JSON 时，不抛 JSON.parse 异常，返回原始文本
  transformResponse: [
    (data) => {
      if (typeof data === 'string') {
        try {
          return JSON.parse(data)
        } catch (e) {
          // 空响应体或非 JSON，返回原始值，让后续拦截器处理
          return data
        }
      }
      return data
    },
  ],
})

// 请求拦截器：自动携带 JWT token
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理 code 与 401
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401) {
      // token 失效，清理并跳登录
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      router.push('/login')
      return Promise.reject(res)
    }
    // 其他业务错误
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(res)
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      router.push('/login')
    } else {
      ElMessage.error(error.message || '网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default service
