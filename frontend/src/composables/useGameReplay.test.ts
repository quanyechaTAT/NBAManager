import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReplayBootstrap, ReplayPageState, ReplayPreparation } from '@/api/types'

const replayApi = vi.hoisted(() => ({
  fetchReplayBootstrap: vi.fn(),
  prepareReplay: vi.fn(),
}))

vi.mock('@/api/aiReplay', () => replayApi)

import { useGameReplay } from './useGameReplay'

function bootstrap(gameId: string, state: ReplayPageState): ReplayBootstrap {
  return {
    gameId,
    state,
    quality: {
      level: state === 'READY' ? 'FULL' : state === 'PARTIAL' ? 'SUMMARY_ONLY' : 'UNAVAILABLE',
      eventCount: state === 'READY' ? 1 : 0,
      scoringEventCount: state === 'READY' ? 1 : 0,
      periodsCovered: state === 'READY' ? [1] : [],
      source: state === 'READY' ? 'NBA_PLAY_BY_PLAY' : null,
      sourceLabel: state === 'READY' ? 'NBA official play-by-play' : null,
      synthetic: false,
      finalScoreVerified: state === 'READY',
      monotonicScores: true,
      lastSyncedAt: null,
      warnings: [],
      factsHash: state === 'READY' ? `facts-${gameId}` : null,
    },
    timeline: [],
    momentum: [],
    highlights: [],
    turningPoints: [],
    cachedSummary: null,
    error: state === 'UNAVAILABLE'
      ? { code: 'REPLAY_SOURCE_UNAVAILABLE', message: 'No replay source', retryable: true, lastAttemptAt: null, nextRetryAt: null }
      : null,
  }
}

function preparing(operationId = 'operation-1', reused = false): ReplayPreparation {
  return {
    gameId: 'game-1',
    state: 'PREPARING',
    operationId,
    reused,
    retryAfterSeconds: null,
    startedAt: null,
    leaseExpiresAt: null,
    error: null,
  }
}

function mountReplay(gameId: ReturnType<typeof ref<string>>, options = {}) {
  const scope = effectScope()
  let replay!: ReturnType<typeof useGameReplay>
  scope.run(() => { replay = useGameReplay(gameId, options) })
  return { replay, scope }
}

async function flushAsync() {
  for (let index = 0; index < 30; index += 1) await Promise.resolve()
  await nextTick()
}

describe('useGameReplay', () => {
  let scopes: EffectScope[]

  beforeEach(() => {
    setActivePinia(createPinia())
    replayApi.fetchReplayBootstrap.mockReset()
    replayApi.prepareReplay.mockReset()
    vi.useRealTimers()
    scopes = []
  })

  afterEach(() => {
    scopes.forEach(scope => scope.stop())
    vi.useRealTimers()
  })

  it.each([
    ['READY', 'ready'],
    ['PARTIAL', 'partial'],
    ['NOT_STARTED', 'not-started'],
    ['UNAVAILABLE', 'unavailable'],
  ] as const)('maps %s Bootstrap responses to an honest %s UI state', async (pageState, uiState) => {
    replayApi.fetchReplayBootstrap.mockResolvedValue(bootstrap('game-1', pageState))
    const mounted = mountReplay(ref('game-1'), { autoPrepare: false })
    scopes.push(mounted.scope)

    await flushAsync()

    expect(mounted.replay.state.value).toBe(uiState)
    expect(mounted.replay.data.value?.state).toBe(pageState)
  })

  it('never prepares a game that has not started', async () => {
    replayApi.fetchReplayBootstrap.mockResolvedValue(bootstrap('game-1', 'NOT_STARTED'))
    const mounted = mountReplay(ref('game-1'))
    scopes.push(mounted.scope)

    await flushAsync()

    expect(mounted.replay.state.value).toBe('not-started')
    expect(replayApi.prepareReplay).not.toHaveBeenCalled()
  })

  it('can defer the first Bootstrap request until load is called', async () => {
    replayApi.fetchReplayBootstrap.mockResolvedValue(bootstrap('game-1', 'READY'))
    const mounted = mountReplay(ref('game-1'), { autoLoad: false })
    scopes.push(mounted.scope)

    await flushAsync()
    expect(replayApi.fetchReplayBootstrap).not.toHaveBeenCalled()

    await mounted.replay.load()
    await flushAsync()

    expect(replayApi.fetchReplayBootstrap).toHaveBeenCalledTimes(1)
    expect(mounted.replay.state.value).toBe('ready')
  })

  it('prepares once and polls with bounded 500/1000/2000/4000ms backoff', async () => {
    vi.useFakeTimers()
    replayApi.fetchReplayBootstrap
      .mockResolvedValueOnce(bootstrap('game-1', 'UNAVAILABLE'))
      .mockResolvedValue(bootstrap('game-1', 'PREPARING'))
    replayApi.prepareReplay.mockResolvedValue(preparing())
    const mounted = mountReplay(ref('game-1'))
    scopes.push(mounted.scope)
    await flushAsync()

    expect(mounted.replay.state.value).toBe('preparing')
    expect(mounted.replay.operationId.value).toBe('operation-1')
    expect(mounted.replay.prepareReused.value).toBe(false)
    expect(replayApi.prepareReplay).toHaveBeenCalledTimes(1)

    for (const delay of [500, 1000, 2000, 4000]) {
      await vi.advanceTimersByTimeAsync(delay)
      await flushAsync()
    }

    expect(replayApi.fetchReplayBootstrap).toHaveBeenCalledTimes(5)
    expect(mounted.replay.state.value).toBe('error')
    expect(mounted.replay.error.value?.code).toBe('REPLAY_POLL_TIMEOUT')
    await vi.runAllTimersAsync()
    expect(replayApi.fetchReplayBootstrap).toHaveBeenCalledTimes(5)
  })

  it('reaches ready after preparation and records a reused server operation', async () => {
    vi.useFakeTimers()
    replayApi.fetchReplayBootstrap
      .mockResolvedValueOnce(bootstrap('game-1', 'UNAVAILABLE'))
      .mockResolvedValueOnce(bootstrap('game-1', 'READY'))
    replayApi.prepareReplay.mockResolvedValue(preparing('operation-shared', true))
    const mounted = mountReplay(ref('game-1'))
    scopes.push(mounted.scope)
    await flushAsync()

    expect(mounted.replay.operationId.value).toBe('operation-shared')
    expect(mounted.replay.prepareReused.value).toBe(true)
    await vi.advanceTimersByTimeAsync(500)
    await flushAsync()

    expect(mounted.replay.state.value).toBe('ready')
    expect(mounted.replay.data.value?.gameId).toBe('game-1')
  })

  it('ignores an old game response after a fast route switch and aborts its signal', async () => {
    let resolveA!: (value: ReplayBootstrap) => void
    let signalA: AbortSignal | undefined
    replayApi.fetchReplayBootstrap.mockImplementation((gameId: string, signal?: AbortSignal) => {
      if (gameId === 'game-a') {
        signalA = signal
        return new Promise(resolve => { resolveA = resolve })
      }
      return Promise.resolve(bootstrap('game-b', 'READY'))
    })
    const gameId = ref('game-a')
    const mounted = mountReplay(gameId)
    scopes.push(mounted.scope)
    await flushAsync()

    gameId.value = 'game-b'
    await flushAsync()
    expect(signalA?.aborted).toBe(true)
    expect(mounted.replay.data.value?.gameId).toBe('game-b')

    resolveA(bootstrap('game-a', 'READY'))
    await flushAsync()
    expect(mounted.replay.data.value?.gameId).toBe('game-b')
    expect(mounted.replay.state.value).toBe('ready')
  })

  it('stops pending polling when disposed', async () => {
    vi.useFakeTimers()
    replayApi.fetchReplayBootstrap.mockResolvedValue(bootstrap('game-1', 'UNAVAILABLE'))
    replayApi.prepareReplay.mockResolvedValue(preparing())
    const mounted = mountReplay(ref('game-1'))
    await flushAsync()

    mounted.replay.dispose()
    await vi.runAllTimersAsync()

    expect(replayApi.fetchReplayBootstrap).toHaveBeenCalledTimes(1)
  })

  it('keeps a usable snapshot visible when a refresh fails', async () => {
    replayApi.fetchReplayBootstrap
      .mockResolvedValueOnce(bootstrap('game-1', 'READY'))
      .mockRejectedValueOnce(new Error('network down'))
    const mounted = mountReplay(ref('game-1'))
    scopes.push(mounted.scope)
    await flushAsync()

    await mounted.replay.refresh()

    expect(mounted.replay.state.value).toBe('error')
    expect(mounted.replay.error.value?.message).toBe('network down')
    expect(mounted.replay.data.value?.gameId).toBe('game-1')
  })
})
