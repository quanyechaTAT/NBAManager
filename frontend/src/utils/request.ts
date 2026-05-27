import axios from 'axios'
import { clearAuthStorage, getToken } from '@/utils/authStorage'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
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
    if (err.response?.status === 401) {
      clearAuthStorage()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  },
)

export default request
