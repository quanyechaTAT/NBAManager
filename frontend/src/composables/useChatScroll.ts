import { ref, nextTick, watch, type Ref } from 'vue'

export function useChatScroll(messages: Ref<any[]>) {
  const container = ref<HTMLElement | null>(null)
  const isAtBottom = ref(true)
  const hasNewMessages = ref(false)

  function checkIfAtBottom() {
    const el = container.value
    if (!el) return true
    const threshold = 60
    return el.scrollHeight - el.scrollTop - el.clientHeight < threshold
  }

  function scrollToBottom(smooth = false) {
    const el = container.value
    if (!el) return
    if (smooth) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    } else {
      el.scrollTop = el.scrollHeight
    }
    hasNewMessages.value = false
  }

  function onScroll() {
    isAtBottom.value = checkIfAtBottom()
    if (isAtBottom.value) {
      hasNewMessages.value = false
    }
  }

  watch(() => messages.value.length, () => {
    nextTick(() => {
      if (isAtBottom.value) {
        scrollToBottom()
      } else {
        hasNewMessages.value = true
      }
    })
  })

  return {
    container,
    isAtBottom,
    hasNewMessages,
    scrollToBottom,
    onScroll,
  }
}
