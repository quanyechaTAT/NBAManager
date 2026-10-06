import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ReplayTimelinePanel from './ReplayTimelinePanel.vue'
import type { TimelineEvent } from '@/api/types'

function event(overrides: Partial<TimelineEvent> = {}): TimelineEvent {
  return {
    id: overrides.id ?? 1,
    period: overrides.period ?? 1,
    gameClock: overrides.gameClock ?? '10:00',
    eventType: overrides.eventType ?? 'SHOT',
    actionType: overrides.actionType ?? 'Made Shot',
    description: overrides.description ?? 'Made jump shot',
    playerName: overrides.playerName ?? 'Player One',
    playerId: overrides.playerId ?? 10,
    teamName: overrides.teamName ?? 'Home',
    teamId: overrides.teamId ?? 100,
    homeScore: overrides.homeScore ?? 2,
    awayScore: overrides.awayScore ?? 0,
    scoreMargin: overrides.scoreMargin ?? 2,
    isScoringPlay: overrides.isScoringPlay ?? true,
    isHighlightCandidate: overrides.isHighlightCandidate ?? false,
    eventOrder: overrides.eventOrder ?? 1,
    source: overrides.source ?? 'NBA_PLAY_BY_PLAY',
    ...overrides,
  }
}

describe('ReplayTimelinePanel', () => {
  it('centers the selected event in the list when its offset parent is outside the scroller', async () => {
    const events = [event(), event({ id: 2, eventOrder: 2 })]
    const wrapper = mount(ReplayTimelinePanel, { props: { events, selectedEvent: null } })
    const scroller = wrapper.get('.timeline-scroll').element as HTMLElement
    const row = wrapper.findAll('.timeline-row')[1].element as HTMLElement
    Object.defineProperties(scroller, {
      clientHeight: { value: 200 }, scrollHeight: { value: 1000 },
      getBoundingClientRect: { value: () => ({ top: 400, height: 200 }) },
    })
    Object.defineProperties(row, {
      offsetTop: { value: 650 }, clientHeight: { value: 80 },
      getBoundingClientRect: { value: () => ({ top: 650, height: 80 }) },
    })
    scroller.scrollTop = 100
    await wrapper.setProps({ selectedEvent: events[1] })
    await flushPromises()
    const selectedCenter = 650 + 40 - (scroller.scrollTop - 100)
    expect(selectedCenter).toBe(400 + 100)
    wrapper.unmount()
  })

  it('renders the Chinese description and keeps translated misses out of scoring results', async () => {
    const wrapper = mount(ReplayTimelinePanel, {
      props: {
        selectedEvent: null,
        events: [event({
          playerName: '约什·哈特',
          description: '哈特 19英尺 转身打板跳投 未命中',
          descriptionCn: '哈特 19英尺 转身打板跳投 未命中',
          descriptionEn: "MISS Hart 19' Turnaround Bank Shot",
          actionType: '2pt',
          isScoringPlay: false,
        })],
      },
    })
    try {
      expect(wrapper.get('.timeline-row').text()).toContain('约什·哈特')
      expect(wrapper.get('.timeline-row').text()).toContain('转身打板跳投 未命中')
      const scoringFilter = wrapper.findAll('button').find(button => button.text() === '得分')!
      await scoringFilter.trigger('click')
      expect(wrapper.findAll('.timeline-row')).toHaveLength(0)
    } finally {
      wrapper.unmount()
    }
  })

  it('makes timeline rows keyboard selectable', async () => {
    const events = [event()]
    const wrapper = mount(ReplayTimelinePanel, {
      props: { events, selectedEvent: null },
    })

    const row = wrapper.get('.timeline-row')

    expect(row.attributes('role')).toBe('button')
    expect(row.attributes('tabindex')).toBe('0')

    await row.trigger('keydown.enter')

    expect(wrapper.emitted('select')).toEqual([[events[0]]])
  })

  it('keeps selection inside the timeline scroller without calling scrollIntoView', async () => {
    const scrollIntoView = vi.fn()
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    })

    const events = [
      event({ id: 1, eventOrder: 1 }),
      event({ id: 2, eventOrder: 2, playerName: 'Player Two' }),
    ]
    const wrapper = mount(ReplayTimelinePanel, {
      props: { events, selectedEvent: null },
    })

    await wrapper.setProps({ selectedEvent: events[1] })

    expect(scrollIntoView).not.toHaveBeenCalled()
  })
})
