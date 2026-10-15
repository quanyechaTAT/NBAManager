import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GlobalSearch from './GlobalSearch.vue'
import { discover, type DiscoveryResponse } from '@/api/discovery'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/discovery', () => ({ discover: vi.fn(), discoveryLabels: { PLAYER: '球员' } }))
vi.mock('@/utils/discoveryTelemetry', () => ({
  attribution: vi.fn(), sendDiscoveryEvent: vi.fn().mockResolvedValue(undefined), resetDiscoveryTelemetry: vi.fn(),
}))

const result: DiscoveryResponse = {
  requestId: 'suggestion-request', nextCursor: null, strategyVersion: 'bm25-v1', degraded: false,
  items: [{ itemKey: 'PLAYER:1', type: 'PLAYER', id: 1, title: '库里', summary: '', imageUrl: null,
    publishedAt: null, reason: null, target: { path: '/players/1', query: {} } }],
}
const mountSearch = () => mount(GlobalSearch, { attachTo: document.body, global: { plugins: [createPinia()] } })
let wrapper: ReturnType<typeof mountSearch>
let outside: HTMLButtonElement

beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(discover).mockResolvedValue({ data: result } as Awaited<ReturnType<typeof discover>>)
  wrapper = mountSearch()
  outside = document.createElement('button'); document.body.append(outside)
})
afterEach(() => {
  wrapper.unmount(); outside.remove(); vi.clearAllTimers(); vi.useRealTimers()
})
async function showSuggestions() {
  const input = wrapper.get('input')
  input.element.focus(); await input.setValue('库里')
  await vi.advanceTimersByTimeAsync(200); await flushPromises()
  return input
}

describe('global search focus and submission', () => {
  it('keeps suggestions available after keyboard focus moves from input to a result', async () => {
    await showSuggestions()
    wrapper.get<HTMLButtonElement>('.suggestions button').element.focus()
    await vi.advanceTimersByTimeAsync(200)
    expect(wrapper.find('.suggestions').exists()).toBe(true)
    await wrapper.get('.suggestions button').trigger('click')
    expect(push).toHaveBeenCalledWith(result.items[0].target)
  })

  it('cancels an old close timer on refocus and closes after focus leaves the whole control', async () => {
    const input = await showSuggestions()
    outside.focus(); await vi.advanceTimersByTimeAsync(100)
    input.element.focus(); await vi.advanceTimersByTimeAsync(100)
    expect(wrapper.find('.suggestions').exists()).toBe(true)
    outside.focus(); await vi.advanceTimersByTimeAsync(200)
    expect(wrapper.find('.suggestions').exists()).toBe(false)
  })

  it('allows Escape to close the menu from a focused result', async () => {
    await showSuggestions()
    const button = wrapper.get<HTMLButtonElement>('.suggestions button'); button.element.focus()
    await button.trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('.suggestions').exists()).toBe(false)
  })

  it('does not start a pending suggestion request after Enter opens the full results', async () => {
    const input = wrapper.get('input'); input.element.focus(); await input.setValue('库里')
    await wrapper.get('form').trigger('submit')
    expect(push).toHaveBeenCalledWith({ path: '/search', query: { q: '库里' } })
    await vi.advanceTimersByTimeAsync(300); await flushPromises()
    expect(discover).not.toHaveBeenCalled()
  })
})
