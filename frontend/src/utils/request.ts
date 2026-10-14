import axios from 'axios'
import { clearAuthStorage, getToken } from '@/utils/authStorage'

const configuredTimeout = Number(import.meta.env.VITE_API_TIMEOUT_MS)
export const DEFAULT_REQUEST_TIMEOUT_MS = Number.isFinite(configuredTimeout) && configuredTimeout > 0
  ? configuredTimeout
  : 15_000

const request = axios.create({
  baseURL: '/api',
  timeout: DEFAULT_REQUEST_TIMEOUT_MS,
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
    if (status === 401) {
      // 排除不需要跳转的路径：分享会话、登录/注册
      const url = err.config?.url || ''
      const isPublicPath = url.includes('/rag/shared/') || url.includes('/auth/')
      if (!isPublicPath) {
        clearAuthStorage()
        if (!window.location.hash.startsWith('#/login')) {
          // Vue uses hash history, including legacy URLs such as /login#/admin/sync.
          window.location.href = '/#/login'
        }
      }
    }
    return Promise.reject(err)
  },
)

export default request
