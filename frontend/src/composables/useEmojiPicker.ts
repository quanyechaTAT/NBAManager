import { ref } from 'vue'

export function useEmojiPicker() {
  const visible = ref(false)
  const anchor = ref<HTMLElement | null>(null)
  let callback: ((emoji: string) => void) | null = null

  function open(anchorEl: HTMLElement, onInsert: (emoji: string) => void) {
    anchor.value = anchorEl
    callback = onInsert
    visible.value = true
  }

  function onSelect(emoji: string) {
    callback?.(emoji)
    visible.value = false
  }

  function close() {
    visible.value = false
  }

  return { visible, anchor, open, onSelect, close }
}