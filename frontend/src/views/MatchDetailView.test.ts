import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { reactive, ref } from 'vue'
import MatchDetailView from './MatchDetailView.vue'
import { fetchMatchRecord, fetchMatchRecords } from '@/api/matchRecord'
import { fetchBoxScore } from '@/api/matchDetail'
import { fetchGameComments } from '@/api/community'

const routerPush = vi.fn()
const mockRoute = reactive({
  query: {
    gameId: '0012600009',
    homeTeam: '猛龙',
    awayTeam: '热火',
    homeScore: '105',
    awayScore: '129',
    status: 'FINISHED',
  },
  fullPath: '/match-detail?gameId=0012600009',
})

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({
    push: routerPush,
    back: vi.fn(),
  }),
}))

vi.mock('@/api/matchDetail', () => ({
  fetchBoxScore: vi.fn(() =>
    Promise.resolve({
      data: {
        gameId: '0012600009',
        homeTeam: '猛龙',
        awayTeam: '热火',
        homeScore: 105,
        awayScore: 129,
        status: 'FINISHED',
        homePlayers: [],
        awayPlayers: [],
      },
    }),
  ),
  fetchQuarterScores: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/api/matchRecord', () => ({
  fetchMatchRecord: vi.fn(() =>
    Promise.resolve({
      data: {
          id: 101,
          nbaGameId: '0012600009',
          homeTeam: '猛龙',
          awayTeam: '热火',
          homeScore: 105,
          awayScore: 129,
          status: 'FINISHED',
      },
    }),
  ),
  fetchMatchRecords: vi.fn(() => Promise.resolve({ data: [] })),
  fetchHeadToHead: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/api/shotChart', () => ({
  getGameShotChart: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/api/community', () => ({
  fetchGameComments: vi.fn(() => Promise.resolve({ data: [] })),
  createComment: vi.fn(() => Promise.resolve({})),
  deleteComment: vi.fn(() => Promise.resolve({})),
  updateComment: vi.fn(() => Promise.resolve({})),
  toggleCommentLike: vi.fn(() => Promise.resolve({})),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isAdmin: false, user: null }),
}))

vi.mock('@/stores/identity', () => ({
  useIdentityStore: () => ({ id: 'test-user' }),
}))

vi.mock('@/composables/useGameReplay', () => ({
  useGameReplay: () => ({
    state: ref('idle'),
    data: ref(null),
    error: ref(null),
    currentGameId: ref(''),
    operationId: ref(null),
    prepareReused: ref(null),
    retryAfterSeconds: ref(null),
    quality: ref(null),
    isBusy: ref(false),
    load: vi.fn(),
    refresh: vi.fn(),
    prepare: vi.fn(),
    retry: vi.fn(),
    dispose: vi.fn(),
  }),
}))

describe('MatchDetailView team navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reads one game and defers the chat connection and comments until their tabs open', async () => {
    const wrapper = mount(MatchDetailView, { global: { stubs: {
      GameIntelligenceCenter: true, ReplayAvailabilityPanel: true, GameShotChartPanel: true, LiveChat: true,
      ElTag: true, ElButton: true, ElTable: true, ElTableColumn: true, ElDialog: true,
      EmojiPicker: true, ElCard: true, ElInput: true,
    } } })
    try {
      await flushPromises()
      expect(fetchMatchRecord).toHaveBeenCalledWith('0012600009', expect.any(AbortSignal))
      expect(fetchMatchRecords).not.toHaveBeenCalled()
      expect(fetchGameComments).not.toHaveBeenCalled()
      expect(wrapper.find('live-chat-stub').exists()).toBe(false)
      await wrapper.findAll('[role="tab"]').find(tab => tab.text().includes('观赛聊天室'))!.trigger('click')
      await flushPromises()
      expect(wrapper.find('live-chat-stub').exists()).toBe(true)
      await wrapper.findAll('[role="tab"]').find(tab => tab.text().includes('赛后热评'))!.trigger('click')
      await flushPromises()
      expect(fetchGameComments).toHaveBeenCalledTimes(1)
    } finally { wrapper.unmount() }
  })

  it('cancels the detail lookup on leaving and does not start late statistics requests', async () => {
    let resolve!: (value: any) => void
    vi.mocked(fetchMatchRecord).mockReturnValueOnce(new Promise(yes => { resolve = yes }))
    const wrapper = mount(MatchDetailView, { global: { stubs: {
      ElTabs: true, ElTag: true, ElButton: true, ElDialog: true, EmojiPicker: true,
    } } })
    const signal = vi.mocked(fetchMatchRecord).mock.calls[0][1]!
    wrapper.unmount()
    expect(signal.aborted).toBe(true)
    resolve({ data: { nbaGameId: '0012600009', status: 'FINISHED' } })
    await flushPromises()
    expect(fetchBoxScore).not.toHaveBeenCalled()
  })

  it('suppresses stale scores for a scheduled game', async () => {
    const original = { ...mockRoute.query }
    mockRoute.query = { ...original, homeScore: '114', awayScore: '144', status: 'SCHEDULED' }
    vi.mocked(fetchMatchRecord).mockResolvedValueOnce({ data: { id: 101, nbaGameId: '0012600009', homeTeam: '76人', awayTeam: '尼克斯', homeScore: 114, awayScore: 144, status: 'SCHEDULED' } } as any)
    const wrapper = mount(MatchDetailView, { global: { directives: { loading: () => {} }, stubs: {
      GameIntelligenceCenter: true, ReplayAvailabilityPanel: true, GameShotChartPanel: true, LiveChat: true,
      ElTag: { template: '<span><slot /></span>' }, ElButton: true, ElTabs: true, ElTabPane: true,
      ElTable: true, ElTableColumn: true, ElDialog: true, EmojiPicker: true,
      ElCard: true, ElInput: true,
    } } })
    try {
      await flushPromises()
      expect(wrapper.get('.match-hero-center').text()).toContain('未开赛')
      expect(wrapper.findAll('.hero-score').map(score => score.text())).toEqual(['--', '--'])
      expect(fetchBoxScore).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
      mockRoute.query = original
    }
  })

  it.each([
    { status: 'LIVE', homeScore: 0, awayScore: 9, expected: ['0', '9'] },
    { status: 'FINISHED', homeScore: null, awayScore: null, expected: ['--', '--'] },
  ])('preserves zero and unknown scores in $status games', async (record) => {
    vi.mocked(fetchMatchRecord).mockResolvedValueOnce({ data: {
      id: 101, nbaGameId: '0012600009', homeTeam: '猛龙', awayTeam: '热火', ...record,
    } } as any)
    const wrapper = mount(MatchDetailView, { global: { directives: { loading: () => {} }, stubs: {
      GameIntelligenceCenter: true, ReplayAvailabilityPanel: true, GameShotChartPanel: true, LiveChat: true,
      ElTag: { template: '<span><slot /></span>' }, ElButton: true, ElTabs: true, ElTabPane: true,
      ElTable: true, ElTableColumn: true, ElDialog: true, EmojiPicker: true, ElCard: true, ElInput: true,
    } } })
    try {
      await flushPromises()
      expect(wrapper.findAll('.hero-score').map(score => score.text())).toEqual(record.expected)
    } finally {
      wrapper.unmount()
    }
  })

  it('uses the recorded status and scores instead of stale URL values', async () => {
    const original = { ...mockRoute.query }
    mockRoute.query = { ...original, homeScore: '114', awayScore: '144', status: 'SCHEDULED' }
    const wrapper = mount(MatchDetailView, { global: { directives: { loading: () => {} }, stubs: {
      GameIntelligenceCenter: true, ReplayAvailabilityPanel: true, GameShotChartPanel: true, LiveChat: true,
      ElTag: { template: '<span><slot /></span>' }, ElButton: true, ElTabs: true, ElTabPane: true,
      ElTable: true, ElTableColumn: true, ElDialog: true, EmojiPicker: true,
      ElCard: true, ElInput: true,
    } } })
    try {
      await flushPromises()
      expect(wrapper.get('.match-hero-center').text()).toContain('已结束')
      expect(wrapper.findAll('.hero-score').map(score => score.text())).toEqual(['105', '129'])
    } finally {
      wrapper.unmount()
      mockRoute.query = original
    }
  })

  it('renders clickable team headers with buttons and navigates to team detail on click', async () => {
    const wrapper = mount(MatchDetailView, {
      global: {
        directives: {
          loading: () => {},
        },
        stubs: {
          EmojiPicker: true,
          GameShotChartPanel: true,
          LiveChat: true,
          GameIntelligenceCenter: true,
          ReplayAvailabilityPanel: true,
          ElButton: { template: '<button><slot /></button>' },
          ElTag: { template: '<span><slot /></span>' },
          ElInput: { template: '<input />' },
          ElTable: true,
          ElTableColumn: true,
          ElRow: { template: '<div><slot /></div>' },
          ElCol: { template: '<div><slot /></div>' },
          ElCard: { template: '<div><slot /></div>' },
          ElTabs: { template: '<div><slot /></div>' },
          ElTabPane: { template: '<div><slot /></div>' },
          ElProgress: { template: '<div><slot /></div>' },
          ElDialog: { template: '<div><slot /></div>' },
        },
      },
    })

    const clickableTeams = wrapper.findAll('.match-hero-team.is-clickable')
    expect(clickableTeams).toHaveLength(2)

    // Check home team direct click navigation
    const homeTeamCard = clickableTeams[0]
    expect(homeTeamCard.text()).toContain('猛龙')
    expect(homeTeamCard.text()).not.toContain('球队详情')

    await homeTeamCard.trigger('click')
    expect(routerPush).toHaveBeenCalledWith({
      path: '/teams/detail',
      query: { name: '猛龙', returnTo: '/match-detail?gameId=0012600009' },
    })

    // Check away team direct click navigation
    const awayTeamCard = clickableTeams[1]
    expect(awayTeamCard.text()).toContain('热火')
    expect(awayTeamCard.text()).not.toContain('球队详情')

    await awayTeamCard.trigger('click')
    expect(routerPush).toHaveBeenCalledWith({
      path: '/teams/detail',
      query: { name: '热火', returnTo: '/match-detail?gameId=0012600009' },
    })

    wrapper.unmount()
  })

  it('gracefully renders team names and scores from boxScore when query params are absent', async () => {
    const origQuery = { ...mockRoute.query }
    mockRoute.query = { gameId: '0012600009' } as any

    const wrapper = mount(MatchDetailView, {
      global: {
        directives: {
          loading: () => {},
        },
        stubs: {
          EmojiPicker: true,
          GameShotChartPanel: true,
          LiveChat: true,
          GameIntelligenceCenter: true,
          ReplayAvailabilityPanel: true,
          ElButton: { template: '<button><slot /></button>' },
          ElTag: { template: '<span><slot /></span>' },
          ElInput: { template: '<input />' },
          ElTable: true,
          ElTableColumn: true,
          ElRow: { template: '<div><slot /></div>' },
          ElCol: { template: '<div><slot /></div>' },
          ElCard: { template: '<div><slot /></div>' },
          ElTabs: { template: '<div><slot /></div>' },
          ElTabPane: { template: '<div><slot /></div>' },
          ElProgress: { template: '<div><slot /></div>' },
          ElDialog: { template: '<div><slot /></div>' },
        },
      },
    })

    await flushPromises()

    const hero = wrapper.find('.match-hero')
    expect(hero.text()).toContain('猛龙')
    expect(hero.text()).toContain('热火')
    expect(hero.text()).toContain('105')
    expect(hero.text()).toContain('129')

    mockRoute.query = origQuery
    wrapper.unmount()
  })

  it('renders tactical review section with header, quick tags, and sort buttons', async () => {
    const wrapper = mount(MatchDetailView, {
      global: {
        directives: {
          loading: () => {},
        },
        stubs: {
          EmojiPicker: true,
          GameShotChartPanel: true,
          LiveChat: true,
          GameIntelligenceCenter: true,
          ReplayAvailabilityPanel: true,
          ElButton: { template: '<button><slot /></button>' },
          ElTag: { template: '<span><slot /></span>' },
          ElInput: { template: '<input />' },
          ElTable: true,
          ElTableColumn: true,
          ElRow: { template: '<div><slot /></div>' },
          ElCol: { template: '<div><slot /></div>' },
          ElCard: { template: '<div><slot /></div>' },
          ElTabs: { template: '<div><slot /></div>' },
          ElTabPane: { template: '<div><slot /></div>' },
          ElProgress: { template: '<div><slot /></div>' },
          ElDialog: { template: '<div><slot /></div>' },
        },
      },
    })

    await flushPromises()

    const commentSection = wrapper.find('.comment-section')
    expect(commentSection.exists()).toBe(true)
    expect(commentSection.text()).toContain('赛后深度复盘与球迷热评')
    expect(commentSection.text()).toContain('最热复盘')
    expect(commentSection.text()).toContain('最新发布')
    expect(commentSection.text()).toContain('#关键决胜回合#')

    wrapper.unmount()
  })

  it('inserts tactical topic tag when clicked without duplicating it', async () => {
    const wrapper = mount(MatchDetailView, {
      global: {
        directives: { loading: () => {} },
        stubs: {
          EmojiPicker: true,
          GameShotChartPanel: true,
          LiveChat: true,
          GameIntelligenceCenter: true,
          ReplayAvailabilityPanel: true,
          ElButton: { template: '<button><slot /></button>' },
          ElTag: { template: '<span><slot /></span>' },
          ElInput: {
            template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
            props: ['modelValue'],
          },
          ElTable: true,
          ElTableColumn: true,
          ElRow: { template: '<div><slot /></div>' },
          ElCol: { template: '<div><slot /></div>' },
          ElCard: { template: '<div><slot /></div>' },
          ElTabs: { template: '<div><slot /></div>' },
          ElTabPane: { template: '<div><slot /></div>' },
          ElProgress: { template: '<div><slot /></div>' },
          ElDialog: { template: '<div><slot /></div>' },
        },
      },
    })

    await flushPromises()

    const tagBtn = wrapper.find('.tactical-tag-pill')
    expect(tagBtn.exists()).toBe(true)
    expect(tagBtn.text()).toBe('#关键决胜回合#')

    await tagBtn.trigger('click')
    const textarea = wrapper.find('.comment-input textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toContain('#关键决胜回合#')

    // Click again - should not duplicate
    await tagBtn.trigger('click')
    const val = (textarea.element as HTMLTextAreaElement).value
    const occurrences = val.split('#关键决胜回合#').length - 1
    expect(occurrences).toBe(1)

    wrapper.unmount()
  })
})
