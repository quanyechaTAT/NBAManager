import { describe, expect, it } from 'vitest'
import { buildRagConversationHistory } from './ragConversation'

describe('buildRagConversationHistory', () => {
  it('keeps only the most recent valid bounded turns', () => {
    const messages = [
      ...Array.from({ length: 8 }, (_, index) => ({
        role: index % 2 === 0 ? 'user' as const : 'assistant' as const,
        content: `turn-${index}`,
      })),
      { role: 'assistant' as const, content: '', error: 'request failed' },
      { role: 'user' as const, content: 'x'.repeat(1500) },
      { role: 'assistant' as const, content: 'y'.repeat(1500) },
    ]

    const history = buildRagConversationHistory(messages)

    expect(history).toHaveLength(8)
    expect(history[0].content).toBe('turn-2')
    expect(history[history.length - 1]?.content).toHaveLength(1000)
    expect(history.some(turn => turn.content === '')).toBe(false)
  })
})
