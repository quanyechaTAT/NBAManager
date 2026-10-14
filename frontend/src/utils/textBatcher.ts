/** Show first text immediately, then apply small stream chunks in bounded batches. */
export function createTextBatcher(apply: (text: string) => void, intervalMs = 40) {
  let pending = ''
  let timer: ReturnType<typeof setTimeout> | null = null
  let started = false
  let disposed = false
  function flush() {
    if (timer !== null) clearTimeout(timer)
    timer = null
    if (!pending || disposed) return
    const text = pending
    pending = ''
    apply(text)
  }
  return {
    push(text: string) {
      if (disposed || !text) return
      pending += text
      if (!started) { started = true; flush() }
      else if (timer === null) timer = setTimeout(flush, intervalMs)
    },
    flush,
    dispose() {
      disposed = true
      pending = ''
      if (timer !== null) clearTimeout(timer)
      timer = null
    },
  }
}
