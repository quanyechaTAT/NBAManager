import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { fetchReplayBootstrap, prepareReplay } from '@/api/aiReplay'
import type { ReplayBootstrap, ReplayPreparation } from '@/api/types'

export const REPLAY_CACHE_TTL_MS = 30_000

export interface ReplayLoadOptions {
  force?: boolean
  signal?: AbortSignal
  ttlMs?: number
}

interface ReplayCacheEntry {
  data: ReplayBootstrap | null
  fetchedAt: number
  expiresAt: number
  preparation: ReplayPreparation | null
}

function requireGameId(gameId: string): string {
  const normalized = gameId.trim()
  if (!normalized) throw new Error('gameId is required')
  return normalized
}

export const useGameReplayStore = defineStore('gameReplay', () => {
  const entries = reactive<Record<string, ReplayCacheEntry>>({})
  const bootstrapInFlight = new Map<string, Promise<ReplayBootstrap>>()
  const preparationInFlight = new Map<string, Promise<ReplayPreparation>>()
  const generations = new Map<string, number>()

  function generationFor(gameId: string): number {
    return generations.get(gameId) ?? 0
  }

  function getCached(gameId: string, now = Date.now()): ReplayBootstrap | null {
    const entry = entries[gameId]
    if (!entry || entry.expiresAt <= now) return null
    return entry.data
  }

  function getPreparation(gameId: string): ReplayPreparation | null {
    return entries[gameId]?.preparation ?? null
  }

  function loadBootstrap(gameId: string, options: ReplayLoadOptions = {}): Promise<ReplayBootstrap> {
    let normalized: string
    try {
      normalized = requireGameId(gameId)
    } catch (error) {
      return Promise.reject(error)
    }

    const cached = options.force ? null : getCached(normalized)
    if (cached) return Promise.resolve(cached)

    const existing = bootstrapInFlight.get(normalized)
    if (existing) return existing

    const requestGeneration = generationFor(normalized)
    const ttlMs = Math.max(0, options.ttlMs ?? REPLAY_CACHE_TTL_MS)
    const promise = fetchReplayBootstrap(normalized, options.signal)
      .then((data) => {
        if (generationFor(normalized) === requestGeneration) {
          const now = Date.now()
          const previous = entries[normalized]
          entries[normalized] = {
            data,
            fetchedAt: now,
            expiresAt: now + ttlMs,
            preparation: previous?.preparation ?? null,
          }
        }
        return data
      })
      .finally(() => {
        if (bootstrapInFlight.get(normalized) === promise) bootstrapInFlight.delete(normalized)
      })

    bootstrapInFlight.set(normalized, promise)
    return promise
  }

  function startPreparation(gameId: string, signal?: AbortSignal): Promise<ReplayPreparation> {
    let normalized: string
    try {
      normalized = requireGameId(gameId)
    } catch (error) {
      return Promise.reject(error)
    }

    const existing = preparationInFlight.get(normalized)
    if (existing) return existing

    const requestGeneration = generationFor(normalized)
    const promise = prepareReplay(normalized, signal)
      .then((preparation) => {
        if (generationFor(normalized) === requestGeneration) {
          const previous = entries[normalized]
          entries[normalized] = {
            data: previous?.data ?? null,
            fetchedAt: previous?.fetchedAt ?? 0,
            expiresAt: previous?.expiresAt ?? 0,
            preparation,
          }
        }
        return preparation
      })
      .finally(() => {
        if (preparationInFlight.get(normalized) === promise) preparationInFlight.delete(normalized)
      })

    preparationInFlight.set(normalized, promise)
    return promise
  }

  function invalidate(gameId: string) {
    const normalized = gameId.trim()
    if (!normalized) return
    generations.set(normalized, generationFor(normalized) + 1)
    delete entries[normalized]
  }

  function clear() {
    for (const gameId of Object.keys(entries)) invalidate(gameId)
  }

  return {
    entries,
    getCached,
    getPreparation,
    loadBootstrap,
    startPreparation,
    invalidate,
    clear,
  }
})
