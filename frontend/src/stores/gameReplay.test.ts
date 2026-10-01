import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { ReplayBootstrap, ReplayPreparation } from '@/api/types'

const replayApi = vi.hoisted(() => ({
  fetchReplayBootstrap: vi.fn(),
  prepareReplay: vi.fn(),
}))

vi.mock('@/api/aiReplay', () => replayApi)

import { REPLAY_CACHE_TTL_MS, useGameReplayStore } from './gameReplay'

function bootstrap(gameId: string): ReplayBootstrap {
  return {
    gameId,
    state: 'READY',
    quality: {
      level: 'FULL',
      eventCount: 1,
      scoringEventCount: 1,
      periodsCovered: [1],
      source: 'NBA_PLAY_BY_PLAY',
      sourceLabel: 'NBA official play-by-play',
      synthetic: false,
      finalScoreVerified: true,
      monotonicScores: true,
      lastSyncedAt: null,
      warnings: [],
      factsHash: `facts-${gameId}`,
    },
    timeline: [],
    momentum: [],
    highlights: [],
    turningPoints: [],
    cachedSummary: null,
    error: null,
  }
}

describe('game replay store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replayApi.fetchReplayBootstrap.mockReset()
    replayApi.prepareReplay.mockReset()
    vi.useRealTimers()
  })

  it('deduplicates concurrent Bootstrap and prepare calls for one game', async () => {
    let resolveBootstrap!: (value: ReplayBootstrap) => void
    replayApi.fetchReplayBootstrap.mockReturnValue(new Promise(resolve => { resolveBootstrap = resolve }))
    const preparation: ReplayPreparation = {
      gameId: 'game-1',
      state: 'PREPARING',
      operationId: 'operation-1',
      reused: false,
      retryAfterSeconds: 2,
      startedAt: null,
      leaseExpiresAt: null,
      error: null,
    }
    replayApi.prepareReplay.mockResolvedValue(preparation)
    const store = useGameReplayStore()

    const firstLoad = store.loadBootstrap('game-1')
    const secondLoad = store.loadBootstrap('game-1')
    expect(replayApi.fetchReplayBootstrap).toHaveBeenCalledTimes(1)
    resolveBootstrap(bootstrap('game-1'))
    await expect(Promise.all([firstLoad, secondLoad])).resolves.toEqual([
      bootstrap('game-1'),
      bootstrap('game-1'),
    ])

    const firstPrepare = store.startPreparation('game-1')
    const secondPrepare = store.startPreparation('game-1')
    await Promise.all([firstPrepare, secondPrepare])
    expect(replayApi.prepareReplay).toHaveBeenCalledTimes(1)
    expect(store.getPreparation('game-1')).toEqual(preparation)
  })

  it('returns a fresh per-game cache entry and refreshes after TTL', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-20T10:00:00Z'))
    replayApi.fetchReplayBootstrap.mockResolvedValue(bootstrap('game-1'))
    const store = useGameReplayStore()

    await store.loadBootstrap('game-1')
    await store.loadBootstrap('game-1')
    expect(replayApi.fetchReplayBootstrap).toHaveBeenCalledTimes(1)

    vi.setSystemTime(Date.now() + REPLAY_CACHE_TTL_MS + 1)
    await store.loadBootstrap('game-1')
    expect(replayApi.fetchReplayBootstrap).toHaveBeenCalledTimes(2)
  })

  it('keeps game caches isolated', async () => {
    replayApi.fetchReplayBootstrap.mockImplementation(async (gameId: string) => bootstrap(gameId))
    const store = useGameReplayStore()

    await Promise.all([store.loadBootstrap('game-a'), store.loadBootstrap('game-b')])

    expect(store.getCached('game-a')?.gameId).toBe('game-a')
    expect(store.getCached('game-b')?.gameId).toBe('game-b')
  })
})
