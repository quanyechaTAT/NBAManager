import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/utils/request', () => ({ default: requestMock }))

import { fetchReplayBootstrap, fetchTimeline, prepareReplay } from './aiReplay'
import type { ReplayBootstrap, ReplayPreparation } from './types'

const bootstrap: ReplayBootstrap = {
  gameId: 'game/1',
  state: 'READY',
  quality: {
    level: 'FULL',
    eventCount: 0,
    scoringEventCount: 0,
    periodsCovered: [],
    source: 'NBA_PLAY_BY_PLAY',
    sourceLabel: 'NBA official play-by-play',
    synthetic: false,
    finalScoreVerified: true,
    monotonicScores: true,
    lastSyncedAt: null,
    warnings: [],
    factsHash: 'facts-1',
  },
  timeline: [],
  momentum: [],
  highlights: [],
  turningPoints: [],
  cachedSummary: null,
  error: null,
}

describe('AI replay API', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
    requestMock.post.mockReset()
  })

  it('passes AbortSignal through Bootstrap and legacy timeline reads', async () => {
    const controller = new AbortController()
    requestMock.get
      .mockResolvedValueOnce({ data: bootstrap })
      .mockResolvedValueOnce({ data: [] })

    await fetchReplayBootstrap('game/1', controller.signal)
    await fetchTimeline('game/1', 6, controller.signal)

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/ai-replay/games/game%2F1/bootstrap', {
      signal: controller.signal,
      timeout: 60_000,
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/ai-replay/games/game%2F1/timeline', {
      params: { period: 6 },
      signal: controller.signal,
      timeout: 60_000,
    })
  })

  it('uses a valid Retry-After header for the preparation polling delay', async () => {
    const body: ReplayPreparation = {
      gameId: '0042500405',
      state: 'PREPARING',
      operationId: 'operation-1',
      reused: false,
      retryAfterSeconds: 4,
      startedAt: '2026-07-20T21:00:00',
      leaseExpiresAt: '2026-07-20T21:02:00',
      error: null,
    }
    requestMock.post.mockResolvedValue({ data: body, headers: { 'retry-after': '2' } })

    const result = await prepareReplay('0042500405')

    expect(requestMock.post).toHaveBeenCalledWith(
      '/ai-replay/games/0042500405/prepare',
      {},
      { signal: undefined, timeout: 60_000 },
    )
    expect(result).toEqual({ ...body, retryAfterSeconds: 2 })
  })

  it('falls back to the body delay when Retry-After is absent or invalid', async () => {
    const body: ReplayPreparation = {
      gameId: '0042500405',
      state: 'PREPARING',
      operationId: 'operation-1',
      reused: true,
      retryAfterSeconds: 3,
      startedAt: null,
      leaseExpiresAt: null,
      error: null,
    }
    requestMock.post.mockResolvedValue({ data: body, headers: { 'retry-after': 'tomorrow' } })

    await expect(prepareReplay('0042500405')).resolves.toMatchObject({ retryAfterSeconds: 3 })
  })
})
