import { onBeforeUnmount, watch, type WatchSource } from 'vue'
import { clickedAttribution, sendDiscoveryEvent } from '@/utils/discoveryTelemetry'

/** Only loaded, visible detail content accumulates reading time. List dwell never counts as a read. */
export function useDiscoveryReading(item: WatchSource<string | null>) {
  let stop = () => {}
  watch(item, key => {
    stop()
    const context = key ? clickedAttribution(key) : undefined
    if (!context) return
    let total = 0, last = performance.now(), visible = document.visibilityState === 'visible', submitted = false
    const tick = () => {
      const now = performance.now()
      if (visible) total += Math.max(0, now - last)
      last = now
      if (total >= 15000 && !submitted) { submitted = true; void sendDiscoveryEvent(context, 'READ', total) }
    }
    const visibility = () => { tick(); visible = document.visibilityState === 'visible' }
    const interval = setInterval(tick, 1000)
    document.addEventListener('visibilitychange', visibility)
    stop = () => {
      tick(); clearInterval(interval); document.removeEventListener('visibilitychange', visibility)
      if (!submitted && total > 0) void sendDiscoveryEvent(context, 'READ', total)
    }
  }, { immediate: true })
  onBeforeUnmount(() => stop())
}
