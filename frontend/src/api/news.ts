import request from '@/utils/request'
import type { PageResponse, GameNews } from '@/api/types'

export interface GameNewsPayload {
  title: string
  summary: string
  content: string
  homeTeam: string
  awayTeam: string
  homeScore: number | null
  awayScore: number | null
  gameStartTime: string
  gameEndTime: string
  nbaGameId?: string | null
  category?: string
  sourceUrl?: string | null
  imageUrl?: string | null
}

/** 分页获取赛事资讯 */
export function fetchNews(params: { q?: string; page: number; size: number }) {
  return request.get<PageResponse<GameNews>>('/news', { params })
}

/** 获取单条资讯详情 */
export function fetchNewsDetail(id: number) {
  return request.get<GameNews>(`/news/${id}`)
}

/** 获取今日赛事 */
export function fetchTodayNews() {
  return request.get<GameNews[]>('/news/today')
}

/** 新增赛事资讯（管理员） */
export function createNews(data: GameNewsPayload) {
  return request.post<GameNews>('/news', data)
}

/** 修改赛事资讯（管理员） */
export function updateNews(id: number, data: GameNewsPayload) {
  return request.put<GameNews>(`/news/${id}`, data)
}

/** 删除赛事资讯（管理员） */
export function deleteNews(id: number) {
  return request.delete(`/news/${id}`)
}

/** 收藏/取消收藏资讯 */
export function toggleNewsFavorite(id: number) {
  return request.post<{ favorited: boolean }>(`/news/${id}/favorite`)
}
