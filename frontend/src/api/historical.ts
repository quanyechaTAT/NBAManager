import request from '@/utils/request'

/**
 * 获取可用赛季列表
 */
export function getAvailableSeasons() {
  return request.get('/historical/seasons').then(res => res.data)
}

/**
 * 获取指定赛季球员数据
 */
export function getPlayersBySeason(season: string, sortBy = 'pointsPerGame', limit = 500) {
  return request.get('/historical/players', {
    params: { season, sortBy, limit }
  }).then(res => res.data)
}

/**
 * 获取指定赛季球队数据
 */
export function getTeamsBySeason(season: string) {
  return request.get('/historical/teams', {
    params: { season }
  }).then(res => res.data)
}

/**
 * 获取指定赛季联盟排名
 */
export function getHistoricalRankings(season: string) {
  return request.get('/historical/rankings', {
    params: { season }
  }).then(res => res.data)
}

/**
 * 获取指定赛季分区排名
 */
export function getHistoricalDivisionRankings(season: string) {
  return request.get('/historical/division-rankings', {
    params: { season }
  }).then(res => res.data)
}

/**
 * 获取指定赛季数据领袖
 */
export function getSeasonLeaders(season: string) {
  return request.get('/historical/leaders', {
    params: { season }
  }).then(res => res.data)
}

/**
 * 获取球员历史赛季列表
 */
export function getPlayerSeasons(nbaPlayerId: number) {
  return request.get(`/historical/player/${nbaPlayerId}/seasons`)
    .then(res => res.data)
}

/**
 * 获取同步状态
 */
export function getHistoricalSyncStatus() {
  return request.get('/historical/sync-status').then(res => res.data)
}

/**
 * 管理员：同步指定赛季数据
 */
export function syncHistoricalSeason(season: string) {
  return request.post(`/historical/sync/${season}`).then(res => res.data)
}

/**
 * 管理员：同步所有历史赛季
 */
export function syncAllHistoricalSeasons() {
  return request.post('/historical/sync-all').then(res => res.data)
}

/**
 * 管理员：清理旧赛季数据
 */
export function cleanupOldSeasons() {
  return request.post('/historical/cleanup').then(res => res.data)
}
