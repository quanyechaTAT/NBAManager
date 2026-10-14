import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { setToken, clearAuthStorage } from '@/utils/authStorage'
import { attribution, resetDiscoveryTelemetry, sendDiscoveryEvent } from '@/utils/discoveryTelemetry'
import { useDiscoveryReading } from './useDiscoveryReading'

function visibility(value: 'visible' | 'hidden') { Object.defineProperty(document, 'visibilityState', { configurable: true, value }); document.dispatchEvent(new Event('visibilitychange')) }
beforeEach(() => { vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'performance', 'Date'] }); setToken('reader'); resetDiscoveryTelemetry(); visibility('visible'); vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true })) })
afterEach(() => { clearAuthStorage(); resetDiscoveryTelemetry(); vi.useRealTimers(); vi.unstubAllGlobals() })

it('counts only visible detail time toward the 15 second read label', async () => {
  await sendDiscoveryEvent(attribution('request', 'NEWS:1', 0), 'CLICK')
  const item = ref<string | null>('NEWS:1')
  const wrapper = mount(defineComponent({ setup() { useDiscoveryReading(item); return () => null } }))
  await vi.advanceTimersByTimeAsync(10000); visibility('hidden')
  await vi.advanceTimersByTimeAsync(30000); expect(fetch).toHaveBeenCalledTimes(1)
  visibility('visible'); await vi.advanceTimersByTimeAsync(5000)
  expect(fetch).toHaveBeenCalledTimes(2)
  const event = JSON.parse(vi.mocked(fetch).mock.calls[1][1]?.body as string).events[0]
  expect(event.eventType).toBe('READ'); expect(event.dwellMs).toBe(15000)
  wrapper.unmount()
})
