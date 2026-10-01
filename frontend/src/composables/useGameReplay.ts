import {
  computed,
  getCurrentScope,
  onScopeDispose,
  ref,
  shallowRef,
  toValue,
  watch,
  type MaybeRefOrGetter,
} from 'vue'
import { useGameReplayStore } from '@/stores/gameReplay'
import type { ReplayBootstrap, ReplayError, ReplayPageState } from '@/api/types'

export const DEFAULT_REPLAY_POLL_DELAYS_MS = [500, 1000, 2000, 4000] as const

export type GameReplayUiState =
  | 'idle'
  | 'loading'
  | 'not-started'
  | 'ready'
  | 'partial'
  | 'preparing'
  | 'polling'
  | 'unavailable'
  | 'error'
  | 'retrying'

export interface GameReplayClientError {
  code: string
  message: string
  retryable: boolean
  cause?: unknown
}

export interface UseGameReplayOptions {
  autoLoad?: boolean
  autoPrepare?: boolean
  cacheTtlMs?: number
  pollDelaysMs?: readonly number[]
}

function normalizeGameId(gameId: string | null | undefined): string {
  return gameId?.trim() ?? ''
}

function uiStateFor(pageState: ReplayPageState): GameReplayUiState {
  switch (pageState) {
    case 'NOT_STARTED': return 'not-started'
    case 'READY': return 'ready'
    case 'PARTIAL': return 'partial'
    case 'PREPARING': return 'preparing'
    case 'UNAVAILABLE': return 'unavailable'
  }
}

function isAbortError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === 'AbortError') return true
  if (!error || typeof error !== 'object') return false
  const candidate = error as { code?: unknown; name?: unknown }
  return candidate.code === 'ERR_CANCELED' || candidate.name === 'CanceledError' || candidate.name === 'AbortError'
}

function toClientError(error: unknown): GameReplayClientError {
  if (error && typeof error === 'object') {
    const candidate = error as {
      code?: unknown
      message?: unknown
      retryable?: unknown
      response?: { data?: { code?: unknown; message?: unknown; retryable?: unknown } }
    }
    const responseError = candidate.response?.data
    const message = typeof responseError?.message === 'string'
      ? responseError.message
      : typeof candidate.message === 'string' ? candidate.message : '比赛回放加载失败'
    const code = typeof responseError?.code === 'string'
      ? responseError.code
      : typeof candidate.code === 'string' ? candidate.code : 'REPLAY_REQUEST_FAILED'
    const retryable = typeof responseError?.retryable === 'boolean'
      ? responseError.retryable
      : typeof candidate.retryable === 'boolean' ? candidate.retryable : true
    return { code, message, retryable, cause: error }
  }
  return { code: 'REPLAY_REQUEST_FAILED', message: '比赛回放加载失败', retryable: true, cause: error }
}

function fromReplayError(error: ReplayError): GameReplayClientError {
  return { code: error.code, message: error.message, retryable: error.retryable }
}

export function useGameReplay(
  gameIdSource: MaybeRefOrGetter<string | null | undefined>,
  options: UseGameReplayOptions = {},
) {
  const store = useGameReplayStore()
  const state = ref<GameReplayUiState>('idle')
  const data = shallowRef<ReplayBootstrap | null>(null)
  const error = shallowRef<GameReplayClientError | null>(null)
  const currentGameId = ref('')
  const operationId = ref<string | null>(null)
  const prepareReused = ref<boolean | null>(null)
  const retryAfterSeconds = ref<number | null>(null)

  const pollDelays = (options.pollDelaysMs?.length ? options.pollDelaysMs : DEFAULT_REPLAY_POLL_DELAYS_MS)
    .map(delay => Math.max(0, Math.trunc(delay)))
  const autoLoad = options.autoLoad !== false
  const autoPrepare = options.autoPrepare !== false

  let generation = 0
  let controller: AbortController | null = null
  let pollTimer: ReturnType<typeof setTimeout> | null = null
  let pollCount = 0
  let disposed = false

  function isCurrent(expectedGeneration: number, expectedGameId: string): boolean {
    return !disposed
      && generation === expectedGeneration
      && currentGameId.value === expectedGameId
      && !controller?.signal.aborted
  }

  function clearPollTimer() {
    if (pollTimer !== null) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  function beginGeneration(gameId: string, reset: boolean): number {
    generation += 1
    controller?.abort()
    controller = new AbortController()
    clearPollTimer()
    pollCount = 0
    currentGameId.value = gameId
    error.value = null
    if (reset) {
      data.value = null
      operationId.value = null
      prepareReused.value = null
      retryAfterSeconds.value = null
    }
    return generation
  }

  function markFailure(cause: unknown, expectedGeneration: number, expectedGameId: string) {
    if (!isCurrent(expectedGeneration, expectedGameId) || isAbortError(cause)) return
    error.value = toClientError(cause)
    state.value = 'error'
  }

  function markPollTimeout(expectedGeneration: number, expectedGameId: string) {
    if (!isCurrent(expectedGeneration, expectedGameId)) return
    error.value = {
      code: 'REPLAY_POLL_TIMEOUT',
      message: '比赛回放准备超时，请稍后重试',
      retryable: true,
    }
    state.value = 'error'
  }

  function applyBootstrap(bundle: ReplayBootstrap) {
    data.value = bundle
    error.value = bundle.error ? fromReplayError(bundle.error) : null
    state.value = uiStateFor(bundle.state)
  }

  function nextPollDelay(serverSeconds?: number | null): number | null {
    if (pollCount >= pollDelays.length) return null
    if (serverSeconds !== null && serverSeconds !== undefined && Number.isFinite(serverSeconds)) {
      return Math.min(10_000, Math.max(500, Math.round(serverSeconds * 1000)))
    }
    return pollDelays[pollCount]
  }

  function schedulePoll(expectedGeneration: number, expectedGameId: string, serverSeconds?: number | null) {
    const delay = nextPollDelay(serverSeconds)
    if (delay === null || delay === undefined) {
      markPollTimeout(expectedGeneration, expectedGameId)
      return
    }

    clearPollTimer()
    pollTimer = setTimeout(async () => {
      pollTimer = null
      if (!isCurrent(expectedGeneration, expectedGameId)) return
      pollCount += 1
      state.value = 'polling'
      try {
        const bundle = await store.loadBootstrap(expectedGameId, {
          force: true,
          signal: controller?.signal,
          ttlMs: options.cacheTtlMs,
        })
        if (!isCurrent(expectedGeneration, expectedGameId)) return
        applyBootstrap(bundle)
        if (bundle.state === 'PREPARING') {
          schedulePoll(expectedGeneration, expectedGameId)
        }
      } catch (cause) {
        markFailure(cause, expectedGeneration, expectedGameId)
      }
    }, delay)
  }

  async function runPreparation(expectedGeneration: number, expectedGameId: string) {
    if (!isCurrent(expectedGeneration, expectedGameId)) return
    state.value = 'preparing'
    try {
      const preparation = await store.startPreparation(expectedGameId, controller?.signal)
      if (!isCurrent(expectedGeneration, expectedGameId)) return
      operationId.value = preparation.operationId
      prepareReused.value = preparation.reused
      retryAfterSeconds.value = preparation.retryAfterSeconds
      error.value = preparation.error ? fromReplayError(preparation.error) : null

      if (preparation.state === 'NOT_STARTED') {
        state.value = 'not-started'
        return
      }
      if (preparation.state === 'PREPARING') {
        state.value = 'preparing'
        schedulePoll(expectedGeneration, expectedGameId, preparation.retryAfterSeconds)
        return
      }

      const bundle = await store.loadBootstrap(expectedGameId, {
        force: true,
        signal: controller?.signal,
        ttlMs: options.cacheTtlMs,
      })
      if (isCurrent(expectedGeneration, expectedGameId)) applyBootstrap(bundle)
    } catch (cause) {
      markFailure(cause, expectedGeneration, expectedGameId)
    }
  }

  async function fetchAndApply(
    expectedGeneration: number,
    expectedGameId: string,
    force: boolean,
    shouldAutoPrepare: boolean,
  ) {
    try {
      const bundle = await store.loadBootstrap(expectedGameId, {
        force,
        signal: controller?.signal,
        ttlMs: options.cacheTtlMs,
      })
      if (!isCurrent(expectedGeneration, expectedGameId)) return
      applyBootstrap(bundle)

      if (bundle.state === 'PREPARING') {
        schedulePoll(expectedGeneration, expectedGameId)
      } else if (
        bundle.state === 'UNAVAILABLE'
        && shouldAutoPrepare
        && bundle.error?.retryable !== false
      ) {
        await runPreparation(expectedGeneration, expectedGameId)
      }
    } catch (cause) {
      markFailure(cause, expectedGeneration, expectedGameId)
    }
  }

  async function switchGame(gameId: string) {
    const expectedGeneration = beginGeneration(gameId, true)
    if (!gameId) {
      state.value = 'idle'
      return
    }
    state.value = 'loading'
    await fetchAndApply(expectedGeneration, gameId, false, autoPrepare)
  }

  async function load() {
    await switchGame(normalizeGameId(toValue(gameIdSource)))
  }

  async function refresh() {
    const gameId = currentGameId.value
    if (!gameId || disposed) return
    const expectedGeneration = beginGeneration(gameId, false)
    state.value = data.value ? 'retrying' : 'loading'
    await fetchAndApply(expectedGeneration, gameId, true, false)
  }

  async function prepare() {
    const gameId = currentGameId.value
    if (!gameId || disposed || data.value?.state === 'NOT_STARTED') return
    const expectedGeneration = beginGeneration(gameId, false)
    await runPreparation(expectedGeneration, gameId)
  }

  async function retry() {
    if (data.value?.state === 'UNAVAILABLE' || error.value?.code === 'REPLAY_POLL_TIMEOUT') {
      await prepare()
    } else {
      await refresh()
    }
  }

  const stopSourceWatch = watch(
    () => normalizeGameId(toValue(gameIdSource)),
    gameId => { if (autoLoad) void switchGame(gameId) },
    { immediate: autoLoad },
  )

  function dispose() {
    if (disposed) return
    disposed = true
    generation += 1
    controller?.abort()
    controller = null
    clearPollTimer()
    stopSourceWatch()
  }

  if (getCurrentScope()) onScopeDispose(dispose)

  return {
    state,
    data,
    error,
    currentGameId,
    operationId,
    prepareReused,
    retryAfterSeconds,
    quality: computed(() => data.value?.quality ?? null),
    isBusy: computed(() => ['loading', 'preparing', 'polling', 'retrying'].includes(state.value)),
    load,
    refresh,
    prepare,
    retry,
    dispose,
  }
}
