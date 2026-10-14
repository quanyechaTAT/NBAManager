export interface QuerySnapshot<T> {
  data: T
  updatedAt: number
  expiresAt: number
}

interface PendingQuery {
  controller: AbortController
  promise: Promise<unknown>
  consumers: number
}

interface QueryOptions<T> {
  ttlMs: number | ((data: T) => number)
  signal?: AbortSignal
  force?: boolean
}

function aborted() {
  return new DOMException('请求已取消', 'AbortError')
}

/** Bounded GET-result cache with one loader per key and cancellation per consumer. */
export class QueryCache {
  private readonly values = new Map<string, QuerySnapshot<unknown>>()
  private readonly pending = new Map<string, PendingQuery>()

  constructor(private readonly capacity = 64, private readonly now: () => number = () => Date.now()) {
    if (!Number.isInteger(capacity) || capacity < 1) throw new Error('缓存容量必须为正整数')
  }

  snapshot<T>(key: string): QuerySnapshot<T> | undefined {
    const value = this.values.get(key)
    if (value) {
      this.values.delete(key)
      this.values.set(key, value)
    }
    return value as QuerySnapshot<T> | undefined
  }

  seed<T>(key: string, data: T, ttlMs: number, updatedAt = this.now()): void {
    this.values.delete(key)
    this.values.set(key, { data, updatedAt, expiresAt: updatedAt + Math.max(0, ttlMs) })
    while (this.values.size > this.capacity) this.values.delete(this.values.keys().next().value!)
  }

  fetch<T>(key: string, loader: (signal: AbortSignal) => Promise<T>, options: QueryOptions<T>): Promise<T> {
    if (options.signal?.aborted) return Promise.reject(aborted())
    const cached = this.snapshot<T>(key)
    if (!options.force && cached && cached.expiresAt > this.now()) return Promise.resolve(cached.data)

    let task = this.pending.get(key)
    if (!task) {
      if (this.pending.size >= this.capacity) return Promise.reject(new Error('正在处理的查询过多，请稍后重试'))
      const controller = new AbortController()
      task = { controller, consumers: 0, promise: Promise.resolve() }
      const created = task
      created.promise = Promise.resolve().then(() => loader(controller.signal)).then(data => {
        if (controller.signal.aborted) throw aborted()
        if (this.pending.get(key) === created) {
          const ttl = typeof options.ttlMs === 'function' ? options.ttlMs(data) : options.ttlMs
          this.seed(key, data, ttl)
        }
        return data
      }).finally(() => {
        if (this.pending.get(key) === created) this.pending.delete(key)
      })
      this.pending.set(key, created)
    }
    return this.join<T>(key, task, options.signal)
  }

  invalidate(key: string): void {
    this.values.delete(key)
    const task = this.pending.get(key)
    this.pending.delete(key)
    task?.controller.abort()
  }

  clear(): void {
    for (const key of this.pending.keys()) this.invalidate(key)
    this.values.clear()
  }

  private join<T>(key: string, task: PendingQuery, signal?: AbortSignal): Promise<T> {
    task.consumers++
    return new Promise<T>((resolve, reject) => {
      let finished = false
      const finish = () => {
        if (finished) return false
        finished = true
        signal?.removeEventListener('abort', cancel)
        task.consumers--
        return true
      }
      const cancel = () => {
        if (!finish()) return
        if (task.consumers === 0 && this.pending.get(key) === task) {
          this.pending.delete(key)
          task.controller.abort()
        }
        reject(aborted())
      }
      signal?.addEventListener('abort', cancel, { once: true })
      task.promise.then(data => {
        if (finish()) resolve(data as T)
      }, error => {
        if (finish()) reject(error)
      })
    })
  }
}

// Used only by read queries; personal data must include its account scope in the key.
export const queryCache = new QueryCache()
