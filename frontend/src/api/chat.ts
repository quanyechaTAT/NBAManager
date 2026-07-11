import request from '@/utils/request'

export interface ChatMessageResponse {
  id: number
  clientMessageId: string
  gameId: string
  userId: number
  username: string
  avatarUrl: string | null
  role: 'ADMIN' | 'USER'
  type: 'TEXT' | 'IMAGE' | 'SYSTEM'
  content: string | null
  imageUrl: string | null
  createdAt: string
  deleted: boolean
}

export interface ChatPresenceResponse {
  gameId: string
  onlineCount: number
  members: ChatPresenceMember[]
  updatedAt: string
}

export interface ChatPresenceMember {
  userId: number
  username: string
  avatarUrl: string | null
  role: 'ADMIN' | 'USER'
}

export interface ChatHistoryResult {
  messages: ChatMessageResponse[]
  hasMore: boolean
}

export function fetchChatHistory(gameId: string, beforeId?: number, limit = 50) {
  return request.get<ChatHistoryResult>(`/chat/games/${gameId}/messages`, {
    params: { beforeId, limit },
  })
}

export function fetchChatPresence(gameId: string) {
  return request.get<ChatPresenceResponse>(`/chat/games/${gameId}/presence`)
}

export function deleteChatMessage(messageId: number) {
  return request.delete<ChatMessageResponse>(`/chat/messages/${messageId}`)
}

export function uploadChatImage(gameId: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  form.append('gameId', gameId)
  return request.post<{ url: string }>('/chat/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
