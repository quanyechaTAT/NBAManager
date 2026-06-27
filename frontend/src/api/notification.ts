import request from '@/utils/request'
import type { PageResponse } from '@/api/types'

export interface Notification {
  id: number
  type: string
  title: string
  content: string
  relatedId: number | null
  isRead: boolean
  createTime: string
}

export function fetchNotifications(params: { page: number; size: number }) {
  return request.get<PageResponse<Notification>>('/notifications', { params })
}

export function fetchUnreadCount() {
  return request.get<{ count: number }>('/notifications/unread-count')
}

export function markAsRead(id: number) {
  return request.put(`/notifications/${id}/read`)
}

export function markAllAsRead() {
  return request.put('/notifications/read-all')
}

export function deleteNotification(id: number) {
  return request.delete(`/notifications/${id}`)
}

export function clearReadNotifications() {
  return request.delete('/notifications/clear-read')
}
