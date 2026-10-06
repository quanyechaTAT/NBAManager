import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import LiveScoreTicker from './LiveScoreTicker.vue'
import type { MatchRecord } from '@/api/types'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}))

function createMatch(overrides: Partial<MatchRecord> = {}): MatchRecord {
  return {
    id: 1,
    homeTeam: '猛龙',
    awayTeam: '掘金',
    homeScore: 105,
    awayScore: 129,
    matchDate: '2026-07-20',
    season: '2025-26',
    status: 'FINISHED',
    nbaGameId: '0012600001',
    gameType: 'REGULAR_SEASON',
    ...overrides,
  }
}

describe('LiveScoreTicker.vue', () => {
  it('renders finished match with scores: shows scores and "-" connector without "FINAL" between teams', () => {
    const wrapper = mount(LiveScoreTicker, {
      props: {
        matches: [createMatch({ homeScore: 105, awayScore: 129, status: 'FINISHED' })],
      },
    })

    const item = wrapper.find('.live-ticker__item')
    expect(item.exists()).toBe(true)

    // 卡片左侧有且仅有状态徽章 FINAL
    const finalMark = item.find('.live-ticker__final-mark')
    expect(finalMark.exists()).toBe(true)
    expect(finalMark.text()).toBe('FINAL')

    // 队名正确展示
    const teamNames = item.findAll('.live-ticker__team-name').map((el) => el.text())
    expect(teamNames).toEqual(['猛龙', '掘金'])

    // 比分正确展示，胜方与负方样式类匹配
    const scores = item.findAll('.live-ticker__score')
    expect(scores).toHaveLength(2)
    expect(scores[0].text()).toBe('105')
    expect(scores[0].classes()).toContain('is-loser')
    expect(scores[1].text()).toBe('129')
    expect(scores[1].classes()).toContain('is-winner')

    // 两队之间连接符为 '-'，绝不出现 'FINAL' 夹在队名中间
    const vs = item.find('.live-ticker__vs')
    expect(vs.text()).toBe('-')
    expect(vs.classes()).toContain('live-ticker__vs--score')
    expect(vs.text()).not.toBe('FINAL')

    // 完整文本绝不包含 "猛龙 FINAL 掘金"
    expect(item.text()).not.toContain('猛龙 FINAL 掘金')
  })

  it('renders finished match without scores: shows "vs" connector without empty score span or FINAL sandwiched between teams', () => {
    const wrapper = mount(LiveScoreTicker, {
      props: {
        matches: [
          createMatch({
            homeScore: null as unknown as number,
            awayScore: null as unknown as number,
            status: 'FINISHED',
          }),
        ],
      },
    })

    const item = wrapper.find('.live-ticker__item')

    // 左侧仍有 FINAL 徽章
    expect(item.find('.live-ticker__final-mark').text()).toBe('FINAL')

    // 不渲染空比分 span
    expect(item.findAll('.live-ticker__score')).toHaveLength(0)

    // 两队之间使用标准的 'vs' 连接符，不夹带 'FINAL'
    const vs = item.find('.live-ticker__vs')
    expect(vs.text()).toBe('vs')
    expect(vs.classes()).not.toContain('live-ticker__vs--score')

    // 确认文本绝不出现 "猛龙 FINAL 掘金"
    expect(item.text()).not.toContain('猛龙 FINAL 掘金')
    expect(item.text()).toContain('猛龙')
    expect(item.text()).toContain('掘金')
  })

  it('renders live match with scores: shows scores and "-" connector without "LIVE" sandwiched between teams', () => {
    const wrapper = mount(LiveScoreTicker, {
      props: {
        matches: [createMatch({ homeScore: 88, awayScore: 92, status: 'LIVE' })],
      },
    })

    const item = wrapper.find('.live-ticker__item')

    // 直播脉冲点存在，无 FINAL 徽章
    expect(item.find('.live-ticker__live-dot').exists()).toBe(true)
    expect(item.find('.live-ticker__final-mark').exists()).toBe(false)

    // 比分正确
    const scores = item.findAll('.live-ticker__score').map((el) => el.text())
    expect(scores).toEqual(['88', '92'])

    // 中间连接符为 '-'，绝非 'LIVE'
    const vs = item.find('.live-ticker__vs')
    expect(vs.text()).toBe('-')
    expect(item.text()).not.toContain('猛龙 LIVE 掘金')
  })

  it('renders scheduled match: shows "vs" connector and date', () => {
    const wrapper = mount(LiveScoreTicker, {
      props: {
        matches: [createMatch({ homeScore: 0, awayScore: 0, status: 'SCHEDULED', matchDate: '2026-07-20' })],
      },
    })

    const item = wrapper.find('.live-ticker__item')

    // 未开赛不渲染比分
    expect(item.findAll('.live-ticker__score')).toHaveLength(0)

    // 中间连接符为 'vs'
    const vs = item.find('.live-ticker__vs')
    expect(vs.text()).toBe('vs')

    // 日期正确展示
    const date = item.find('.live-ticker__date')
    expect(date.exists()).toBe(true)
    expect(date.text()).toBe('07/20')
  })

  it('navigates to match detail on clicking an item with nbaGameId', async () => {
    pushMock.mockClear()
    const wrapper = mount(LiveScoreTicker, {
      props: {
        matches: [createMatch({ nbaGameId: '0012600001' })],
      },
    })

    const item = wrapper.find('.live-ticker__item')
    expect(item.classes()).toContain('is-clickable')

    const expectedQuery = {
      gameId: '0012600001',
      status: 'FINISHED',
      homeTeam: '猛龙',
      awayTeam: '掘金',
      homeScore: '105',
      awayScore: '129',
    }

    await item.trigger('click')
    expect(pushMock).toHaveBeenCalledWith({
      path: '/match-detail',
      query: expectedQuery,
    })

    // Keyboard Enter navigation
    pushMock.mockClear()
    await item.trigger('keydown.enter')
    expect(pushMock).toHaveBeenCalledWith({
      path: '/match-detail',
      query: expectedQuery,
    })

    // Keyboard Space navigation
    pushMock.mockClear()
    await item.trigger('keydown.space')
    expect(pushMock).toHaveBeenCalledWith({
      path: '/match-detail',
      query: expectedQuery,
    })
  })

  it('renders informative tooltip and aria-label for accessibility', () => {
    const wrapper = mount(LiveScoreTicker, {
      props: {
        matches: [
          createMatch({
            homeTeam: '猛龙',
            awayTeam: '掘金',
            homeScore: 105,
            awayScore: 129,
            status: 'FINISHED',
            nbaGameId: '0012600001',
          }),
        ],
      },
    })

    const item = wrapper.find('.live-ticker__item')
    expect(item.attributes('title')).toBe('查看比赛详情：猛龙 105 - 129 掘金')
    expect(item.attributes('aria-label')).toBe('已结束，07/20 猛龙 105 比 129 掘金，回车可查看比赛详情')
    expect(item.attributes('role')).toBe('button')
    expect(item.attributes('tabindex')).toBe('0')
  })

  it('handles matches without nbaGameId gracefully without clickable button role', () => {
    const wrapper = mount(LiveScoreTicker, {
      props: {
        matches: [createMatch({ nbaGameId: null })],
      },
    })

    const item = wrapper.find('.live-ticker__item')
    expect(item.classes()).not.toContain('is-clickable')
    expect(item.attributes('role')).toBeUndefined()
    expect(item.attributes('tabindex')).toBeUndefined()
    expect(item.attributes('title')).toBe('猛龙 105 - 129 掘金')
    expect(item.attributes('aria-label')).toBe('已结束，07/20 猛龙 105 比 129 掘金')
  })
})
