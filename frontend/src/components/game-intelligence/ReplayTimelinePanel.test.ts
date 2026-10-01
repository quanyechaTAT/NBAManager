import { mount } from '@vue/test-utils'
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
