import { describe, it, expect, vi } from 'vitest'
import { useEmojiPicker } from './useEmojiPicker'

describe('useEmojiPicker composable', () => {
  it('initializes with visible false and null anchor', () => {
    const { visible, anchor } = useEmojiPicker()
    expect(visible.value).toBe(false)
    expect(anchor.value).toBe(null)
  })

  it('open sets visible and anchor and callback', () => {
    const { visible, anchor, open, onSelect } = useEmojiPicker()
    const dummyEl = document.createElement('button')
    const onInsert = vi.fn()

    open(dummyEl, onInsert)
    expect(visible.value).toBe(true)
    expect(anchor.value).toBe(dummyEl)

    onSelect('🏀')
    expect(onInsert).toHaveBeenCalledWith('🏀')
    expect(visible.value).toBe(false)
  })

  it('close closes picker without firing callback', () => {
    const { visible, anchor, open, close } = useEmojiPicker()
    const dummyEl = document.createElement('button')
    const onInsert = vi.fn()

    open(dummyEl, onInsert)
    expect(visible.value).toBe(true)
    expect(anchor.value).toBe(dummyEl)

    close()
    expect(visible.value).toBe(false)
    expect(onInsert).not.toHaveBeenCalled()
  })
})
