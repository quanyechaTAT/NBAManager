import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AIExplainPanel from './AIExplainPanel.vue'
import { explainEvent } from '@/api/aiReplay'
import type { EventExplain, TimelineEvent } from '@/api/types'

vi.mock('@/api/aiReplay', () => ({
  explainEvent: vi.fn(),
}))

function event(overrides: Partial<TimelineEvent> = {}): TimelineEvent {
  return {
    id: overrides.id ?? 11,
    period: overrides.period ?? 4,
    gameClock: overrides.gameClock ?? '01:24',
    eventType: overrides.eventType ?? 'SHOT',
    actionType: overrides.actionType ?? 'Made Shot',
    description: overrides.description ?? 'Clutch three pointer',
    playerName: overrides.playerName ?? 'Closer',
    playerId: overrides.playerId ?? 23,
    teamName: overrides.teamName ?? 'Home',
    teamId: overrides.teamId ?? 1,
    homeScore: overrides.homeScore ?? 101,
    awayScore: overrides.awayScore ?? 99,
    scoreMargin: overrides.scoreMargin ?? 2,
    isScoringPlay: overrides.isScoringPlay ?? true,
    isHighlightCandidate: overrides.isHighlightCandidate ?? true,
    eventOrder: overrides.eventOrder ?? 88,
    source: overrides.source ?? 'NBA_PLAY_BY_PLAY',
    ...overrides,
  }
}

function explanation(target: TimelineEvent): EventExplain {
  return {
    gameId: 'game-1',
    eventId: target.id,
    event: target,
    explanation: '这次进攻直接改变了收官阶段的防守选择。',
    citedEventIdsJson: `[${target.id}]`,
    citedStatKeysJson: '[]',
    modelProvider: 'DEEPSEEK',
    modelName: 'deepseek-test',
  }
}

describe('AIExplainPanel', () => {
  it('does not generate AI explanation automatically when an event is selected', async () => {
    const selected = event()
    const wrapper = mount(AIExplainPanel, {
      props: { gameId: 'game-1', selectedEvent: null, momentumData: [] },
    })

    await wrapper.setProps({ selectedEvent: selected })

    expect(explainEvent).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('生成 AI 解说')
  })

  it('generates AI explanation only after explicit user action', async () => {
    const selected = event()
    vi.mocked(explainEvent).mockResolvedValueOnce(explanation(selected))
    const wrapper = mount(AIExplainPanel, {
      props: { gameId: 'game-1', selectedEvent: selected, momentumData: [] },
    })

    await wrapper.get('[data-test="generate-explain"]').trigger('click')
    await flushPromises()

    expect(explainEvent).toHaveBeenCalledWith('game-1', selected.id, expect.any(AbortSignal))
    expect(await wrapper.find('.ai-text').text()).toContain('改变了收官阶段')
  })
})
