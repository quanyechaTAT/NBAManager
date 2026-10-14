import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, reactive, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { useCommentRealtime } from './useCommentRealtime'

const fixtures = vi.hoisted(() => ({
  auth: { token: 'test-token' },
  connections: [] as {
    options: { onConnect: () => void },
    activate: ReturnType<typeof vi.fn>,
    deactivate: Mock<[], Promise<void>>,
    subscribe: ReturnType<typeof vi.fn>,
  }[],
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => reactive(fixtures.auth) }))
vi.mock('sockjs-client/dist/sockjs.min.js', () => ({ default: vi.fn() }))
vi.mock('@stomp/stompjs', () => ({
  Client: class {
    activate = vi.fn()
    deactivate = vi.fn(() => Promise.resolve())
    subscribe = vi.fn()
    constructor(public options: { onConnect: () => void }) { fixtures.connections.push(this) }
  },
}))

const wrappers: ReturnType<typeof mount>[] = []
function consumer() {
  const destination = ref<string | null>(null)
  const onUpdate = vi.fn()
  const wrapper = mount(defineComponent({
    setup() { useCommentRealtime(() => destination.value, onUpdate) },
    template: '<div />',
  }))
  wrappers.push(wrapper)
  return { wrapper, destination, onUpdate }
}

beforeEach(() => { vi.useFakeTimers(); fixtures.connections.length = 0; fixtures.auth.token = 'test-token' })
afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); vi.useRealTimers() })

describe('comment realtime lifecycle', () => {
  it('connects only for an active topic and cancels pending updates on leaving it', async () => {
    const { destination, onUpdate } = consumer()
    expect(fixtures.connections).toHaveLength(0)
    destination.value = '/topic/comments/game/1'; await nextTick()
    const connection = fixtures.connections[0]!
    expect(connection.activate).toHaveBeenCalledTimes(1)
    connection.options.onConnect()
    const update = connection.subscribe.mock.calls[0]![1] as () => void
    update()
    destination.value = null; await nextTick()
    expect(connection.deactivate).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(50)
    expect(onUpdate).not.toHaveBeenCalled()
    update(); await vi.advanceTimersByTimeAsync(50)
    expect(onUpdate).not.toHaveBeenCalled()
  })

  it('does not subscribe when a replaced connection finishes connecting late', async () => {
    const { destination } = consumer()
    destination.value = '/topic/old'; await nextTick()
    const old = fixtures.connections[0]!
    destination.value = '/topic/new'; await nextTick()
    const current = fixtures.connections[1]!
    old.options.onConnect()
    expect(old.subscribe).not.toHaveBeenCalled()
    current.options.onConnect()
    expect(current.subscribe).toHaveBeenCalledWith('/topic/new', expect.any(Function))
  })

  it('disconnects on logout and reconnects with a new token', async () => {
    const { destination } = consumer()
    destination.value = '/topic/game'; await nextTick()
    reactive(fixtures.auth).token = ''; await nextTick()
    expect(fixtures.connections[0]!.deactivate).toHaveBeenCalledTimes(1)
    expect(fixtures.connections).toHaveLength(1)
    reactive(fixtures.auth).token = 'replacement-token'; await nextTick()
    expect(fixtures.connections).toHaveLength(2)
    expect(fixtures.connections[1]!.activate).toHaveBeenCalledTimes(1)
  })
})
