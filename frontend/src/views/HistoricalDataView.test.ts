import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import HistoricalDataView from './HistoricalDataView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/historical', () => ({
  getAvailableSeasons: vi.fn().mockResolvedValue({
    current: '2023-24',
    available: ['2023-24', '2022-23'],
  }),
  getPlayersBySeason: vi.fn().mockResolvedValue([]),
  getTeamsBySeason: vi.fn().mockResolvedValue([]),
  syncHistoricalSeason: vi.fn().mockResolvedValue({ message: '同步成功' }),
}))

vi.mock('@/api/player', () => ({
  fetchPlayers: vi.fn().mockResolvedValue({ data: { content: [], totalPages: 0 } }),
}))

vi.mock('@/api/team', () => ({
  fetchTeams: vi.fn().mockResolvedValue({ data: [] }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isAdmin: false }),
}))

vi.mock('vue-echarts', () => ({
  default: { template: '<div class="echarts-mock" />' },
}))

describe('HistoricalDataView centering and layout', () => {
  it('renders .page-wrapper, .page, .page-inner and .card container hierarchy', () => {
    const wrapper = mount(HistoricalDataView, {
      global: {
        directives: {
          loading: () => {},
        },
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          VChart: true,
          SportsHeader: { template: '<div class="sports-header-stub"><slot /></div>' },
          AppEmpty: true,
          ElCard: { template: '<div class="el-card"><slot /></div>' },
          ElSelect: { template: '<div class="el-select"><slot /></div>' },
          ElOption: { template: '<div class="el-option"><slot /></div>' },
          ElButton: { template: '<button><slot /></button>' },
          ElTag: { template: '<span><slot /></span>' },
          ElTable: { template: '<div class="el-table"><slot /></div>' },
          ElTableColumn: true,
        },
      },
    })

    const root = wrapper.find('.page-wrapper')
    expect(root.exists()).toBe(true)
    expect(root.classes()).toContain('page')
    expect(root.classes()).toContain('page-wrapper')

    const inner = wrapper.find('.page-inner')
    expect(inner.exists()).toBe(true)
    expect(root.element.contains(inner.element)).toBe(true)

    const card = wrapper.find('.card')
    expect(card.exists()).toBe(true)
    expect(inner.element.contains(card.element)).toBe(true)

    wrapper.unmount()
  })

  it('declares centering rules and responsive paddings in styles', async () => {
    const rawModule = await import('./HistoricalDataView.vue?raw')
    const content = rawModule.default

    expect(content).toMatch(/\.page-wrapper,\s*\.page\s*\{[^}]*max-width:\s*1440px/s)
    expect(content).toMatch(/\.page-wrapper,\s*\.page\s*\{[^}]*margin:\s*0 auto/s)
    expect(content).toMatch(/\.page-wrapper,\s*\.page\s*\{[^}]*padding:\s*20px 24px 32px/s)
    expect(content).toMatch(/\.page-wrapper,\s*\.page\s*\{[^}]*box-sizing:\s*border-box/s)
    expect(content).toMatch(/\.page-inner\s*\{[^}]*max-width:\s*1440px/s)
    expect(content).toMatch(/\.page-inner\s*\{[^}]*margin:\s*0 auto/s)
    expect(content).toMatch(/\.page-inner\s*\{[^}]*box-sizing:\s*border-box/s)
    expect(content).toMatch(/\.card\s*\{[^}]*margin:\s*0 auto/s)
    expect(content).toMatch(/\.card\s*\{[^}]*box-sizing:\s*border-box/s)
    expect(content).toMatch(/@media\s*\(max-width:\s*768px\)\s*\{[^}]*padding:\s*16px 16px 28px/s)
    expect(content).toMatch(/@media\s*\(max-width:\s*480px\)\s*\{[^}]*padding:\s*12px 12px 20px/s)
    expect(content).toMatch(/chart-container\s*\{[^}]*overflow-x:\s*auto/s)
    expect(content).toMatch(/chart\s*\{[^}]*min-width:\s*640px/s)
  })
})
