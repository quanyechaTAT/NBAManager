import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { reactive, ref } from 'vue'
import SmartSearchView from './SmartSearchView.vue'

const routerPush = vi.fn()
const mockRoute = reactive({
  query: {},
  fullPath: '/smart-search',
})

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({
    push: routerPush,
    resolve: vi.fn((to) => ({ href: `http://localhost:5173/#${to.name}?share=${to.query?.share}` })),
  }),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url === '/rag/stats') {
        return Promise.resolve({ data: { documentCount: 1250 } })
      }
      if (url === '/rag/conversations') {
        return Promise.resolve({
          data: [
            {
              id: 'c_1',
              title: '凯尔特人五外战术剖析',
              time: Date.now() - 3600000,
              messages: [
                { role: 'user', content: '分析凯尔特人' },
                { role: 'assistant', content: '凯尔特人进攻效率联盟第一' },
              ],
            },
          ],
        })
      }
      return Promise.resolve({ data: null })
    }),
    post: vi.fn(() => Promise.resolve({ data: { shareId: 'test-share-123' } })),
    delete: vi.fn(() => Promise.resolve({ data: { success: true } })),
  },
}))

vi.mock('@/composables/useMatchFeed', () => ({
  useMatchFeed: () => ({
    matches: ref([]),
    tickerMatches: ref([]),
    live: ref([]),
    groups: ref([]),
    loading: ref(false),
    failed: ref(false),
    refresh: vi.fn(),
  }),
}))

describe('SmartSearchView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  afterEach(() => { vi.unstubAllGlobals() })

  it('renders incremental chunks and uses server follow-ups after completion', async () => {
    let controller!: ReadableStreamDefaultController<Uint8Array>
    const stream = new ReadableStream<Uint8Array>({ start(value) { controller = value } })
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, body: stream })))
    const wrapper = mount(SmartSearchView)
    await flushPromises()
    await wrapper.find('textarea').setValue('分析马刺和尼克斯的比赛')
    await wrapper.find('.send-btn').trigger('click')
    await flushPromises()
    const emit = (event: object) => controller.enqueue(new TextEncoder().encode(`data: ${JSON.stringify(event)}\n\n`))
    emit({ type: 'chunk', content: '2024-12-25 比赛的首段分析' })
    await flushPromises()
    expect(wrapper.text()).toContain('比赛的首段分析')
    expect(wrapper.find('.follow-up-btn').exists()).toBe(false)
    const suggestions = ['2024-12-25马刺对尼克斯的比赛：双方篮板和失误有哪些差异？', '2024-12-25马刺对尼克斯的比赛：请列出数据来源。']
    emit({ type: 'done', response_time: 100, follow_ups: suggestions })
    controller.close()
    await flushPromises()
    expect(wrapper.findAll('.follow-up-btn').map(button => button.text())).toEqual(suggestions)
    expect(wrapper.text()).not.toContain('季后赛战术对位胜率')
    wrapper.unmount()
  })

  it('renders welcome screen with hero, capabilities, and prompt chips when messages are empty', async () => {
    const wrapper = mount(SmartSearchView)
    await flushPromises()

    expect(wrapper.find('.welcome-screen').exists()).toBe(true)
    expect(wrapper.find('.hero-title').text()).toContain('把比赛读得')
    expect(wrapper.find('.hero-title').text()).toContain('更深')
    expect(wrapper.find('.cap-main').exists()).toBe(true)
    expect(wrapper.findAll('.cap-card')).toHaveLength(4)
    expect(wrapper.findAll('.chip')).toHaveLength(5)
    expect(wrapper.find('.top-doc-badge').text()).toContain('已索引 1,250 篇')
  })

  it('toggles sidebar and displays conversation history with count badge', async () => {
    const wrapper = mount(SmartSearchView)
    await flushPromises()

    const sidebar = wrapper.find('.sidebar')
    expect(sidebar.exists()).toBe(true)
    expect(wrapper.find('.conv-count-badge').text()).toBe('1')
    expect(wrapper.find('.sidebar-item-title').text()).toBe('凯尔特人五外战术剖析')

    // Click delete icon triggers confirmation pill
    const delBtn = wrapper.find('.sidebar-item-del')
    expect(delBtn.exists()).toBe(true)
    await delBtn.trigger('click')

    expect(wrapper.find('.del-confirm-pill').exists()).toBe(true)
    expect(wrapper.find('.del-confirm-text').text()).toBe('确定删除？')

    // Click cancel button dismisses confirmation pill
    const cancelBtn = wrapper.find('.del-confirm-btn.cancel')
    await cancelBtn.trigger('click')
    expect(wrapper.find('.del-confirm-pill').exists()).toBe(false)
  })

  it('filters history conversations via the search input', async () => {
    const wrapper = mount(SmartSearchView)
    await flushPromises()

    const searchInput = wrapper.find('.sidebar-search-input')
    expect(searchInput.exists()).toBe(true)

    // Type matching query
    await searchInput.setValue('凯尔特人')
    expect(wrapper.findAll('.sidebar-item')).toHaveLength(1)

    // Type non-matching query
    await searchInput.setValue('不存在的球队')
    expect(wrapper.findAll('.sidebar-item')).toHaveLength(0)
    expect(wrapper.text()).toContain('未找到匹配对话')
  })

  it('shows character count warning on large input and controls send button state', async () => {
    const wrapper = mount(SmartSearchView)
    await flushPromises()

    const textarea = wrapper.find('.composer-wrap textarea')
    const sendBtn = wrapper.find('.send-btn')

    // Initial state: empty input, send disabled
    expect((sendBtn.element as HTMLButtonElement).disabled).toBe(true)

    // Enter valid text
    await textarea.setValue('分析湖人队战术')
    expect(wrapper.find('.char-count').text()).toBe('7/500')
    expect((sendBtn.element as HTMLButtonElement).disabled).toBe(false)

    // Exceed warning threshold (>450)
    await textarea.setValue('A'.repeat(460))
    expect(wrapper.find('.char-count').classes()).toContain('warn')
  })
})
