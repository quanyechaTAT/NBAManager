import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import GameIntelligenceCenter from './GameIntelligenceCenter.vue'
import type { MomentumSnapshot, TimelineEvent, TurningPoint } from '@/api/types'

function event(overrides: Partial<TimelineEvent> = {}): TimelineEvent {
  return {
    id: overrides.id ?? 1,
    period: overrides.period ?? 2,
    gameClock: overrides.gameClock ?? '08:00',
    eventType: overrides.eventType ?? 'SHOT',
    actionType: overrides.actionType ?? 'Made Shot',
    description: overrides.description ?? 'Made shot',
    playerName: overrides.playerName ?? 'Player One',
    playerId: overrides.playerId ?? 1,
    teamName: overrides.teamName ?? 'Home',
    teamId: overrides.teamId ?? 1,
    homeScore: overrides.homeScore ?? 40,
    awayScore: overrides.awayScore ?? 38,
    scoreMargin: overrides.scoreMargin ?? 2,
    isScoringPlay: overrides.isScoringPlay ?? true,
    isHighlightCandidate: overrides.isHighlightCandidate ?? false,
    eventOrder: overrides.eventOrder ?? 12,
    source: overrides.source ?? 'NBA_PLAY_BY_PLAY',
    ...overrides,
  }
}

function momentum(overrides: Partial<MomentumSnapshot> = {}): MomentumSnapshot {
  return {
    id: overrides.id ?? 1,
    eventOrder: overrides.eventOrder ?? 12,
    period: overrides.period ?? 2,
    gameClock: overrides.gameClock ?? '08:00',
    homeScore: overrides.homeScore ?? 40,
    awayScore: overrides.awayScore ?? 38,
    scoreMargin: overrides.scoreMargin ?? 2,
    ...overrides,
  }
}

function turningPoint(overrides: Partial<TurningPoint> = {}): TurningPoint {
  return {
    id: overrides.id ?? 7,
    period: overrides.period ?? 2,
    gameClock: overrides.gameClock ?? '08:00',
    type: overrides.type ?? 'RUN',
    confidence: overrides.confidence ?? 0.9,
    factualReason: overrides.factualReason ?? '8-0 run',
    aiExplanation: overrides.aiExplanation ?? null,
    citedEventIdsJson: overrides.citedEventIdsJson ?? '[]',
    startEventOrder: overrides.startEventOrder ?? 10,
    endEventOrder: overrides.endEventOrder ?? 15,
    ...overrides,
  }
}

describe('GameIntelligenceCenter', () => {
  it('highlights matching turning point without using scrollIntoView', async () => {
    const scrollIntoView = vi.fn()
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    })
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)

    const wrapper = mount(GameIntelligenceCenter, {
      props: {
        gameId: 'game-1',
        homeTeam: 'Home',
        awayTeam: 'Away',
        timelineEvents: [event()],
        momentumData: [momentum()],
        highlights: [],
        turningPoints: [turningPoint()],
        summary: null,
        comparisonItems: [],
        homePlayers: [],
        awayPlayers: [],
        selectedEvent: null,
      },
      global: {
        stubs: {
          GameTrendCard: { template: '<button data-test="run" @click="$emit(`runClick`, 10, 15)">run</button>' },
          AISummaryCard: true,
          ReplayTimelinePanel: true,
          AIExplainPanel: true,
          TurningPointStoryRail: {
            template: '<section><article data-tp-id="7" class="tp-card">turning point</article></section>',
          },
          StatComparisonPanel: true,
          PlayerPerformanceRail: true,
        },
      },
    })

    await wrapper.get('[data-test="run"]').trigger('click')

    expect(scrollIntoView).not.toHaveBeenCalled()
    expect(scrollTo).toHaveBeenCalled()
    expect(wrapper.get('[data-tp-id="7"]').classes()).toContain('tp-highlight')
  })
})
