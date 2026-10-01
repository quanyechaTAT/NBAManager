import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ReplayAvailabilityPanel from './ReplayAvailabilityPanel.vue'
import type { ReplayQuality } from '@/api/types'

function quality(level: ReplayQuality['level']): ReplayQuality {
  return {
    level,
    eventCount: level === 'FULL' ? 120 : 4,
    scoringEventCount: 20,
    periodsCovered: [1, 2, 3, 4],
    source: 'NBA_PLAY_BY_PLAY',
    sourceLabel: 'NBA official play-by-play',
    synthetic: false,
    finalScoreVerified: true,
    monotonicScores: true,
    lastSyncedAt: null,
    warnings: [],
    factsHash: 'facts-1',
  }
}

describe('ReplayAvailabilityPanel', () => {
  it('communicates summary-only data without pretending full replay is available', () => {
    const wrapper = mount(ReplayAvailabilityPanel, {
      props: {
        state: 'partial',
        quality: quality('SUMMARY_ONLY'),
        error: null,
        operationId: null,
        retryAfterSeconds: null,
        busy: false,
      },
    })

    expect(wrapper.text()).toContain('\u4ec5\u6709\u8282\u672b\u6bd4\u5206\u6458\u8981')
    expect(wrapper.text()).toContain('\u4e0d\u4f1a\u4f2a\u88c5\u6210\u9010\u56de\u5408\u56de\u653e')
  })

  it('shows a single prepare action when unavailable data can be prepared', async () => {
    const wrapper = mount(ReplayAvailabilityPanel, {
      props: {
        state: 'unavailable',
        quality: quality('UNAVAILABLE'),
        error: { code: 'REPLAY_SOURCE_UNAVAILABLE', message: 'source down', retryable: true },
        operationId: null,
        retryAfterSeconds: null,
        busy: false,
      },
    })

    const buttons = wrapper.findAll('button')
    expect(buttons.map(button => button.text())).toEqual(['准备回放', '刷新'])

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('prepare')).toHaveLength(1)
    expect(wrapper.emitted('retry')).toBeUndefined()
  })
})
