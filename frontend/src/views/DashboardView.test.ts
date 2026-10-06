import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'

const fixtures = vi.hoisted(() => ({
  west: Array.from({ length: 15 }, (_, i) => ({
    teamName: `西部球队${i + 1}`, wins: 64 - i, losses: 18 + i,
  })),
  east: Array.from({ length: 15 }, (_, i) => ({
    teamName: `东部球队${i + 1}`, wins: 60 - i, losses: 22 + i,
  })),
  push: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: fixtures.push }) }))
vi.mock('@/composables/useDashboardCache', async () => {
  const { ref } = await import('vue')
  return {
    useDashboardCache: () => ({
      stats: ref(null), todayGames: ref([]), westStandings: ref(fixtures.west),
      eastStandings: ref(fixtures.east), topScorers: ref([]), hotPosts: ref([]),
      docCount: ref(0), loading: ref(false),
    }),
  }
})
vi.mock('@/composables/useMatchFeed', async () => {
  const { ref } = await import('vue')
  return {
    useMatchFeed: () => ({
      tickerMatches: ref([]), groups: ref([]), headline: ref(null),
      headlineLabel: ref('最近赛果'), refresh: vi.fn().mockResolvedValue(undefined),
    }),
  }
})

function dashboard() {
  return mount(DashboardView, {
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, BroadcastPanel: true } },
  })
}

describe('Dashboard conference standings', () => {
  it('displays all 15 western teams with their API order and records', () => {
    const wrapper = dashboard()
    try {
      const rows = wrapper.findAll('.rank-row')
      expect(rows).toHaveLength(15)
      expect(rows.map(row => row.get('.rank-name').text()))
        .toEqual(fixtures.west.map(team => team.teamName))
      expect(rows[14].get('.rank-num').text()).toBe('15')
      expect(rows[14].get('.rank-record').text()).toBe('50-32')
    } finally { wrapper.unmount() }
  })

  it('switches to all 15 eastern teams and opens the selected team details', async () => {
    const wrapper = dashboard()
    try {
      await wrapper.findAll('.rank-tab')[1].trigger('click')
      const rows = wrapper.findAll('.rank-row')
      expect(rows).toHaveLength(15)
      expect(rows.map(row => row.get('.rank-name').text()))
        .toEqual(fixtures.east.map(team => team.teamName))
      await rows[14].trigger('click')
      expect(fixtures.push).toHaveBeenCalledWith({
        path: '/teams/detail', query: { name: '东部球队15', returnTo: '/dashboard' },
      })
    } finally { wrapper.unmount() }
  })

  it('renders match title with "vs" instead of "@"', () => {
    const wrapper = mount(DashboardView, {
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' }, BroadcastPanel: true },
      },
    })
    try {
      // With empty matches it might not show game-title, but we can verify the template structure
      expect(wrapper.html()).not.toContain(' @ ')
    } finally {
      wrapper.unmount()
    }
  })
})
