import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosHeaders, type AxiosResponse } from 'axios'
import { useMatchFeed } from './useMatchFeed'
import { queryCache } from '@/utils/queryCache'
import { fetchMatchRecords } from '@/api/matchRecord'
import type { MatchRecord } from '@/api/types'

vi.mock('@/api/matchRecord', () => ({ fetchMatchRecords: vi.fn() }))
const Consumer = defineComponent({
  setup() { return useMatchFeed() },
  template: '<div>{{ headline?.homeTeam }}<span v-if="failed">offline</span></div>',
})
const wrappers: ReturnType<typeof mount>[] = []
function consumer() { const wrapper = mount(Consumer); wrappers.push(wrapper); return wrapper }
function response(data: MatchRecord[]): AxiosResponse<MatchRecord[]> {
  return { data, status: 200, statusText: 'OK', headers: {}, config: { headers: new AxiosHeaders() } }
}
const live: MatchRecord = { id: 1, nbaGameId: '0012600009', homeTeam: '马刺', awayTeam: '尼克斯',
  homeScore: 1, awayScore: 2, matchDate: '2026-10-06', season: '2026-27', status: 'LIVE', gameType: 'REGULAR_SEASON' }

beforeEach(() => {
  vi.useFakeTimers(); queryCache.clear()
  vi.mocked(fetchMatchRecords).mockResolvedValue(response([]))
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
})
afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount()
  queryCache.clear(); vi.useRealTimers()
})

describe('shared match feed polling', () => {
  it('owns one request and one timer for multiple views, stopping after the last view leaves', async () => {
    const first = consumer(); const second = consumer()
    await flushPromises()
    // Vue's first renderer schedules a 3 s devtools discovery timer.
    // Let it finish before counting application polling timers.
    await vi.advanceTimersByTimeAsync(3_000)
    expect(fetchMatchRecords).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(1)
    first.unmount(); wrappers.splice(wrappers.indexOf(first), 1)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(fetchMatchRecords).toHaveBeenCalledTimes(2)
    second.unmount(); wrappers.splice(wrappers.indexOf(second), 1)
    expect(vi.getTimerCount()).toBe(0)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(fetchMatchRecords).toHaveBeenCalledTimes(2)
  })

  it('refreshes live games every ten seconds and preserves verified results on failure', async () => {
    vi.mocked(fetchMatchRecords).mockResolvedValueOnce(response([live]))
    const wrapper = consumer()
    await flushPromises()
    vi.mocked(fetchMatchRecords).mockRejectedValueOnce(new Error('offline'))
    await vi.advanceTimersByTimeAsync(10_000)
    expect(fetchMatchRecords).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('马刺')
    expect(wrapper.text()).toContain('offline')
  })

  it('pauses while hidden and resumes with exactly one refresh cycle', async () => {
    consumer()
    await flushPromises()
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(120_000)
    expect(fetchMatchRecords).toHaveBeenCalledTimes(1)
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(fetchMatchRecords).toHaveBeenCalledTimes(2)
    expect(vi.getTimerCount()).toBe(1)
  })
})
