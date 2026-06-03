import request from '@/utils/request'

export interface UserFavorite {
  id: number
  targetType: string
  targetId: number
  createTime: string
}

export function fetchFavorites() {
  return request.get<UserFavorite[]>('/favorites')
}

export function toggleFavorite(targetType: string, targetId: number) {
  return request.post<{ favorited: boolean }>('/favorites', { targetType, targetId })
}

export function fetchFollowedPlayerIds() {
  return request.get<number[]>('/favorites/players')
}

export function fetchFollowedTeamIds() {
  return request.get<number[]>('/favorites/teams')
}

export function fetchFavoritedPostIds() {
  return request.get<number[]>('/favorites/posts')
}

export function fetchFavoritedNewsIds() {
  return request.get<number[]>('/favorites/news')
}
