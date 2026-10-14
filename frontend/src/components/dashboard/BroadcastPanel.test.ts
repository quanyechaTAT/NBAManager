import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BroadcastPanel from './BroadcastPanel.vue'
import type { MatchRecord } from '@/api/types'

function createMatch(overrides: Partial<MatchRecord> = {}): MatchRecord {
  return {
    id: 1,
    homeTeam: '掘金',
    awayTeam: '猛龙',
    homeScore: 119,
    awayScore: 66,
    matchDate: '2026-07-20T19:30:00Z',
    season: '2025-26',
    status: 'FINISHED',
    nbaGameId: '0012600001',
    gameType: 'REGULAR_SEASON',
    ...overrides,
  }
}

describe('BroadcastPanel.vue', () => {
  it('renders headline match scores cleanly with winning state and no broken classes', () => {
    const wrapper = mount(BroadcastPanel, {
      props: {
        headlineMatch: createMatch({ awayScore: 66, homeScore: 119 }),
        feedLabel: '最近赛果',
      },
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })

    const scoreContainer = wrapper.find('.broadcast__score')
    expect(scoreContainer.exists()).toBe(true)

    const pts = wrapper.findAll('.broadcast__pts')
    expect(pts).toHaveLength(2)
    expect(pts[0].text()).toBe('66')
    expect(pts[0].classes()).not.toContain('is-winner')
    expect(pts[1].text()).toBe('119')
    expect(pts[1].classes()).toContain('is-winner')

    const dash = wrapper.find('.broadcast__dash')
    expect(dash.exists()).toBe(true)
    expect(dash.text()).toBe('–')
  })

  it('renders kickoff text when match does not have scores yet', () => {
    const wrapper = mount(BroadcastPanel, {
      props: {
        headlineMatch: createMatch({ homeScore: undefined, awayScore: undefined, status: 'SCHEDULED' }),
      },
    })

    const scoreContainer = wrapper.find('.broadcast__score')
    expect(scoreContainer.exists()).toBe(true)
    const timeEl = scoreContainer.find('.broadcast__time')
    expect(timeEl.exists()).toBe(true)
  })

  it('renders empty state when headlineMatch is null', () => {
    const wrapper = mount(BroadcastPanel, {
      props: {
        headlineMatch: null,
      },
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })

    expect(wrapper.find('.broadcast__empty-title').text()).toContain('今日暂无进行中的赛事')
  })

  it('emits open-detail when action button is clicked', async () => {
    const match = createMatch()
    const wrapper = mount(BroadcastPanel, {
      props: { headlineMatch: match },
    })

    const btn = wrapper.find('.broadcast__btn')
    await btn.trigger('click')

    expect(wrapper.emitted('open-detail')).toBeTruthy()
    expect(wrapper.emitted('open-detail')![0]).toEqual([match])
  })

  it('renders away team as winner when awayScore is greater', () => {
    const wrapper = mount(BroadcastPanel, {
      props: {
        headlineMatch: createMatch({ awayScore: 112, homeScore: 98 }),
      },
    })

    const pts = wrapper.findAll('.broadcast__pts')
    expect(pts[0].text()).toBe('112')
    expect(pts[0].classes()).toContain('is-winner')
    expect(pts[1].text()).toBe('98')
    expect(pts[1].classes()).not.toContain('is-winner')

    const scoreContainer = wrapper.find('.broadcast__score')
    expect(scoreContainer.attributes('aria-label')).toBe('猛龙 112 比 98 掘金')
  })

  it('does not mark either team as winner when scores are tied', () => {
    const wrapper = mount(BroadcastPanel, {
      props: {
        headlineMatch: createMatch({ awayScore: 105, homeScore: 105 }),
      },
    })

    const pts = wrapper.findAll('.broadcast__pts')
    expect(pts[0].text()).toBe('105')
    expect(pts[0].classes()).not.toContain('is-winner')
    expect(pts[1].text()).toBe('105')
    expect(pts[1].classes()).not.toContain('is-winner')
  })

  it('handles zero-zero live or tipoff score properly without treating as empty', () => {
    const wrapper = mount(BroadcastPanel, {
      props: {
        headlineMatch: createMatch({ awayScore: 0, homeScore: 0, status: 'LIVE' }),
      },
    })

    const pts = wrapper.findAll('.broadcast__pts')
    expect(pts).toHaveLength(2)
    expect(pts[0].text()).toBe('0')
    expect(pts[1].text()).toBe('0')
    expect(wrapper.find('.broadcast__time').exists()).toBe(false)
  })
})
