import { describe, expect, it, vi } from 'vitest'
import { QueryCache } from './queryCache'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe('QueryCache', () => {
  it('shares a loader and delivers its result to every consumer', async () => {
    const cache = new QueryCache()
    const result = deferred<string>()
    const loader = vi.fn(() => result.promise)
    const first = cache.fetch('game:1', loader, { ttlMs: 1000 })
    const second = cache.fetch('game:1', loader, { ttlMs: 1000 })
    await Promise.resolve()
    expect(loader).toHaveBeenCalledTimes(1)
    result.resolve('verified')
    expect(await Promise.all([first, second])).toEqual(['verified', 'verified'])
  })

  it('cancels one subscriber without interrupting another', async () => {
    const cache = new QueryCache()
    const result = deferred<string>()
    let networkSignal!: AbortSignal
    const loader = (signal: AbortSignal) => { networkSignal = signal; return result.promise }
    const leaving = new AbortController()
    const first = cache.fetch('game:1', loader, { ttlMs: 1000, signal: leaving.signal })
    const second = cache.fetch('game:1', loader, { ttlMs: 1000 })
    const cancelled = expect(first).rejects.toMatchObject({ name: 'AbortError' })
    await Promise.resolve()
    leaving.abort()
    expect(networkSignal.aborted).toBe(false)
    result.resolve('verified')
    await cancelled
    expect(await second).toBe('verified')
  })

  it('aborts the loader when its last subscriber leaves and never caches a late result', async () => {
    const cache = new QueryCache()
    const result = deferred<string>()
    const leaving = new AbortController()
    let networkSignal!: AbortSignal
    const first = cache.fetch('game:1', signal => { networkSignal = signal; return result.promise },
      { ttlMs: 1000, signal: leaving.signal })
    const cancelled = expect(first).rejects.toMatchObject({ name: 'AbortError' })
    await Promise.resolve()
    leaving.abort()
    expect(networkSignal.aborted).toBe(true)
    result.resolve('outdated')
    await cancelled
    await Promise.resolve()
    expect(cache.snapshot('game:1')).toBeUndefined()
    expect(await cache.fetch('game:1', async () => 'new', { ttlMs: 1000 })).toBe('new')
  })

  it('does not extend freshness when cached data is read', async () => {
    let now = 1000
    const cache = new QueryCache(4, () => now)
    const loader = vi.fn(async () => 'data')
    await cache.fetch('rankings', loader, { ttlMs: 100 })
    now = 1090
    await cache.fetch('rankings', loader, { ttlMs: 100 })
    expect(cache.snapshot('rankings')?.updatedAt).toBe(1000)
    now = 1101
    await cache.fetch('rankings', loader, { ttlMs: 100 })
    expect(loader).toHaveBeenCalledTimes(2)
  })

  it('keeps a usable snapshot on refresh failure and retries later', async () => {
    const cache = new QueryCache()
    cache.seed('rankings', 'old', 0)
    await expect(cache.fetch('rankings', async () => { throw new Error('offline') }, { ttlMs: 1000 })).rejects.toThrow('offline')
    expect(cache.snapshot('rankings')?.data).toBe('old')
    expect(await cache.fetch('rankings', async () => 'new', { ttlMs: 1000 })).toBe('new')
  })

  it('cannot republish invalidated data over a newer request', async () => {
    const cache = new QueryCache()
    const old = deferred<string>()
    const first = cache.fetch('game:1', () => old.promise, { ttlMs: 1000 })
    const cancelled = expect(first).rejects.toMatchObject({ name: 'AbortError' })
    await Promise.resolve()
    cache.invalidate('game:1')
    await cache.fetch('game:1', async () => 'new', { ttlMs: 1000 })
    old.resolve('old')
    await cancelled
    expect(cache.snapshot('game:1')?.data).toBe('new')
  })

  it('evicts the least recently used value when capacity is reached', () => {
    const cache = new QueryCache(2)
    cache.seed('a', 1, 1000)
    cache.seed('b', 2, 1000)
    cache.snapshot('a')
    cache.seed('c', 3, 1000)
    expect(cache.snapshot('b')).toBeUndefined()
    expect(cache.snapshot('a')?.data).toBe(1)
  })
})
