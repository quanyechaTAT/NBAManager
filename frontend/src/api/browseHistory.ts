import request from '@/utils/request'

export function recordBrowse(targetType: string, targetId: number) {
  return request.post('/browse-history', null, { params: { targetType, targetId } })
}

export function fetchBrowsedNewsIds() {
  return request.get<number[]>('/browse-history/news')
}

export function fetchBrowsedPostIds() {
  return request.get<number[]>('/browse-history/posts')
}

export function deleteBrowseRecord(targetType: string, targetId: number) {
  return request.delete('/browse-history', { params: { targetType, targetId } })
}

export function clearBrowseHistory(targetType: string) {
  return request.delete('/browse-history/clear', { params: { targetType } })
}
