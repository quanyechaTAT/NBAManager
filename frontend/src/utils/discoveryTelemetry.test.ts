import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { setToken, clearAuthStorage } from './authStorage'
import { attribution, clickedAttribution, observeDiscoveryExposure, resetDiscoveryTelemetry, sendDiscoveryEvent } from './discoveryTelemetry'

let callback: IntersectionObserverCallback
const element = document.createElement('article')
function visibility(state: 'visible' | 'hidden') { Object.defineProperty(document, 'visibilityState', { configurable: true, value: state }); document.dispatchEvent(new Event('visibilitychange')) }
function intersect(ratio: number) { callback([{ target: element, isIntersecting: ratio > 0, intersectionRatio: ratio,
  boundingClientRect: new DOMRect(), intersectionRect: new DOMRect(), rootBounds: null, time: performance.now() }], {} as IntersectionObserver) }

beforeEach(() => {
  vi.useFakeTimers(); setToken('user-a'); resetDiscoveryTelemetry(); visibility('visible')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
  vi.stubGlobal('IntersectionObserver', class { constructor(cb: IntersectionObserverCallback) { callback = cb } observe() {} disconnect() {} })
})
afterEach(() => { clearAuthStorage(); resetDiscoveryTelemetry(); vi.unstubAllGlobals(); vi.useRealTimers() })

describe('discovery attribution', () => {
  it('requires 50% visibility for a continuous second and cancels a hidden-tab exposure', async () => {
    const stop = observeDiscoveryExposure(element, attribution('request', 'NEWS:1', 0))
    intersect(.49); await vi.advanceTimersByTimeAsync(1500); expect(fetch).not.toHaveBeenCalled()
    intersect(.5); await vi.advanceTimersByTimeAsync(700); visibility('hidden')
    await vi.advanceTimersByTimeAsync(1500); expect(fetch).not.toHaveBeenCalled()
    visibility('visible'); await vi.advanceTimersByTimeAsync(1000)
    expect(fetch).toHaveBeenCalledTimes(1)
    const body = JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)
    expect(body.events[0]).toMatchObject({ itemKey: 'NEWS:1', requestId: 'request', eventType: 'IMPRESSION', position: 0 })
    stop()
  })
  it('deduplicates repeated exposure and drops an old account context', async () => {
    const context = attribution('request', 'POST:1', 2)
    await sendDiscoveryEvent(context, 'IMPRESSION'); await sendDiscoveryEvent(context, 'IMPRESSION')
    expect(fetch).toHaveBeenCalledTimes(1)
    setToken('user-b'); await sendDiscoveryEvent(context, 'CLICK')
    expect(fetch).toHaveBeenCalledTimes(1); expect(clickedAttribution('POST:1')).toBeUndefined()
  })
  it('does not attribute a read to a rejected click', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({ ok: false } as Response)
    await sendDiscoveryEvent(attribution('request', 'NEWS:1', 0), 'CLICK')
    expect(clickedAttribution('NEWS:1')).toBeUndefined()
  })
})
