import axios from 'axios'
import { clearAuthStorage, getToken } from '@/utils/authStorage'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,  // 60秒超时，支持RAG查询等耗时操作
})

request.interceptors.request.use((config) => {
  const t = getToken()
  if (t) {
    config.headers.Authorization = `Bearer ${t}`
  }
  return config
})

request.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status
    if (status === 401 || status === 403) {
      // 排除不需要跳转的路径：分享会话、登录/注册
      const url = err.config?.url || ''
      const isPublicPath = url.includes('/rag/shared/') || url.includes('/auth/')
      if (!isPublicPath) {
        clearAuthStorage()
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(err)
  },
)

export default request
