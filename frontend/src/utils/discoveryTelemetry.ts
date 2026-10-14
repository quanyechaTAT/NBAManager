import { getToken } from '@/utils/authStorage'

export interface Attribution { requestId: string; itemKey: string; position: number; owner: string }
type EventKind = 'IMPRESSION' | 'CLICK' | 'READ'
const clicks = new Map<string, Attribution>()
const sent = new Set<string>()
const controllers = new Set<AbortController>()
let identity = getToken()

export function resetDiscoveryTelemetry() {
  controllers.forEach(controller => controller.abort())
  controllers.clear(); clicks.clear(); sent.clear(); identity = getToken()
}
function current() {
  if (identity !== getToken()) resetDiscoveryTelemetry()
  return identity
}
export function attribution(requestId: string, itemKey: string, position: number): Attribution {
  return { requestId, itemKey, position, owner: current() || '' }
}
export function clickedAttribution(itemKey: string): Attribution | undefined {
  current()
  return clicks.get(itemKey)
}
export async function sendDiscoveryEvent(context: Attribution, eventType: EventKind, dwellMs = 0) {
  if (!context.owner || context.owner !== current()) return
  const key = `${context.requestId}:${context.itemKey}:${eventType}`
  if (sent.has(key)) return
  sent.add(key)
  const controller = new AbortController()
  controllers.add(controller)
  const timeout = setTimeout(() => controller.abort(), 2000)
  if (eventType === 'CLICK') clicks.set(context.itemKey, context)
  const event = { eventId: crypto.randomUUID(), requestId: context.requestId, itemKey: context.itemKey,
    position: context.position, eventType, dwellMs: Math.min(1_800_000, Math.max(0, Math.floor(dwellMs))), occurredAt: new Date().toISOString() }
  try {
    // Capture the originating credential so an account switch cannot relabel an in-flight event.
    const response = await fetch('/api/discovery/events', { method: 'POST', signal: controller.signal,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${context.owner}` },
      body: JSON.stringify({ events: [event] }), keepalive: true })
    if (!response.ok) throw new Error('Telemetry rejected')
    if (eventType === 'CLICK' && context.owner === current()) clicks.set(context.itemKey, context)
  } catch {
    sent.delete(key)
    if (eventType === 'CLICK' && clicks.get(context.itemKey) === context) clicks.delete(context.itemKey)
  }
  finally { clearTimeout(timeout); controllers.delete(controller) }
}

/** Exposure must remain at least half visible for one uninterrupted second in a visible document. */
export function observeDiscoveryExposure(element: HTMLElement, context: Attribution) {
  let visible = false
  let timer: ReturnType<typeof setTimeout> | undefined
  const cancel = () => { if (timer) clearTimeout(timer); timer = undefined }
  const update = () => {
    cancel()
    if (visible && document.visibilityState === 'visible') timer = setTimeout(() => {
      if (visible && document.visibilityState === 'visible') void sendDiscoveryEvent(context, 'IMPRESSION')
    }, 1000)
  }
  if (typeof IntersectionObserver === 'undefined') return () => {}
  const observer = new IntersectionObserver(entries => {
    visible = entries.some(entry => entry.target === element && entry.isIntersecting && entry.intersectionRatio >= .5)
    update()
  }, { threshold: [0, .5, 1] })
  observer.observe(element)
  document.addEventListener('visibilitychange', update)
  return () => { cancel(); observer.disconnect(); document.removeEventListener('visibilitychange', update) }
}
