import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosHeaders, type AxiosResponse } from 'axios'
import { useDashboardCache } from './useDashboardCache'
import { queryCache } from '@/utils/queryCache'
import { fetchDashboardOverview } from '@/api/dashboard'
import { fetchTodayNews } from '@/api/news'
import { setProfile, setToken } from '@/utils/authStorage'
import type { DashboardOverview } from '@/api/types'

const overview: DashboardOverview = {
  stats: { teamCount: 30, playerCount: 450, teamWinRows: [], topScorers: [] },
  todayGames: [], westStandings: [], eastStandings: [], topScorers: [], hotPosts: [], docCount: 8,
  generatedAt: '2026-10-06T12:00:00', partialErrors: [],
}

function response<T>(data: T): AxiosResponse<T> {
  return { data, status: 200, statusText: 'OK', headers: {}, config: { headers: new AxiosHeaders() } }
}

vi.mock('@/api/dashboard', () => ({ fetchDashboardOverview: vi.fn(async () => response(overview)) }))
vi.mock('@/api/news', () => ({ fetchTodayNews: vi.fn(async () => response([])) }))

const Consumer = defineComponent({
  setup() { return useDashboardCache() },
  template: '<div>{{ stats?.teamCount }}<span v-if="failed">failed</span></div>',
})
const wrappers: ReturnType<typeof mount>[] = []
function consumer() {
  const wrapper = mount(Consumer)
  wrappers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  queryCache.clear(); localStorage.clear(); sessionStorage.clear()
  vi.mocked(fetchDashboardOverview).mockImplementation(async () => response(overview))
  vi.mocked(fetchTodayNews).mockImplementation(async () => response([]))
})
afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); queryCache.clear() })

describe('dashboard query consumers', () => {
  it('updates every mounted consumer using one overview and one today request', async () => {
    const first = consumer()
    const second = consumer()
    await flushPromises()
    expect(fetchDashboardOverview).toHaveBeenCalledTimes(1)
    expect(fetchTodayNews).toHaveBeenCalledTimes(1)
    expect(first.text()).toContain('30')
    expect(second.text()).toContain('30')
  })

  it('does not cancel the shared request when only one consumer leaves', async () => {
    let resolve!: (value: AxiosResponse<DashboardOverview>) => void
    vi.mocked(fetchDashboardOverview).mockImplementation(() => new Promise(yes => { resolve = yes }))
    const first = consumer()
    const second = consumer()
    await flushPromises()
    first.unmount(); wrappers.splice(wrappers.indexOf(first), 1)
    expect(vi.mocked(fetchDashboardOverview).mock.calls[0][0]?.aborted).toBe(false)
    resolve(response(overview))
    await flushPromises()
    expect(second.text()).toContain('30')
  })

  it('retains the original cache timestamp on a fresh revisit', async () => {
    let now = Date.now()
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    const first = consumer()
    await flushPromises()
    const initial = JSON.parse(sessionStorage.getItem('nba_dashboard_cache_v3')!).timestamp
    first.unmount(); wrappers.splice(wrappers.indexOf(first), 1)
    now += 20_000
    consumer()
    await flushPromises()
    expect(fetchDashboardOverview).toHaveBeenCalledTimes(1)
    expect(JSON.parse(sessionStorage.getItem('nba_dashboard_cache_v3')!).timestamp).toBe(initial)
  })

  it('keeps the previous snapshot when stale-data refresh fails', async () => {
    let now = Date.now()
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    const first = consumer()
    await flushPromises()
    first.unmount(); wrappers.splice(wrappers.indexOf(first), 1)
    now += 61_000
    vi.mocked(fetchDashboardOverview).mockRejectedValueOnce(new Error('offline'))
    const second = consumer()
    await flushPromises()
    expect(second.text()).toContain('30')
    expect(second.text()).toContain('failed')
  })

  it('does not reuse an overview from a different signed-in account', async () => {
    setToken('test'); setProfile({ username: 'alice', role: 'USER' })
    const first = consumer()
    await flushPromises()
    first.unmount(); wrappers.splice(wrappers.indexOf(first), 1)
    setProfile({ username: 'bob', role: 'USER' })
    consumer()
    await flushPromises()
    expect(fetchDashboardOverview).toHaveBeenCalledTimes(2)
    expect(JSON.parse(sessionStorage.getItem('nba_dashboard_cache_v3')!).scope).toBe('user:bob')
  })
})
