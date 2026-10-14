import { afterEach, describe, expect, it, vi } from 'vitest'
import { createTextBatcher } from './textBatcher'

afterEach(() => { vi.useRealTimers() })
describe('stream text batching', () => {
  it('shows the first text immediately and combines the next hundred updates without losing text', () => {
    vi.useFakeTimers()
    const apply = vi.fn()
    const batch = createTextBatcher(apply)
    batch.push('首段')
    expect(apply).toHaveBeenCalledWith('首段')
    for (let i = 0; i < 100; i++) batch.push(String(i))
    expect(apply).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(40)
    expect(apply).toHaveBeenCalledTimes(2)
    expect(apply.mock.calls[1][0]).toBe(Array.from({ length: 100 }, (_, i) => String(i)).join(''))
  })
  it('flushes remaining text before terminal handling and clears its scheduled update', () => {
    vi.useFakeTimers()
    const apply = vi.fn()
    const batch = createTextBatcher(apply)
    batch.push('a'); batch.push('b'); batch.flush(); batch.dispose()
    vi.advanceTimersByTime(100)
    expect(apply.mock.calls).toEqual([['a'], ['b']])
  })
  it('never writes late text after disposal', () => {
    vi.useFakeTimers()
    const apply = vi.fn()
    const batch = createTextBatcher(apply)
    batch.push('a'); batch.push('b'); batch.dispose(); batch.push('late')
    vi.advanceTimersByTime(100)
    expect(apply.mock.calls).toEqual([['a']])
  })
})
