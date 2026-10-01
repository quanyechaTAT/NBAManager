export interface DataRefreshSignal {
  module: string
  jobId: number
  status: string
  timestamp: number
}

const STORAGE_KEY = 'nba:data-refresh'
const EVENT_NAME = 'nba:data-refresh'

export function publishDataRefresh(signal: Omit<DataRefreshSignal, 'timestamp'>) {
  const payload: DataRefreshSignal = { ...signal, timestamp: Date.now() }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  window.dispatchEvent(new CustomEvent<DataRefreshSignal>(EVENT_NAME, { detail: payload }))
}

export function subscribeDataRefresh(listener: (signal: DataRefreshSignal) => void) {
  const onCustomEvent = (event: Event) => {
    listener((event as CustomEvent<DataRefreshSignal>).detail)
  }
  const onStorage = (event: StorageEvent) => {
    if (event.key !== STORAGE_KEY || !event.newValue) return
    try {
      listener(JSON.parse(event.newValue) as DataRefreshSignal)
    } catch {
      // Ignore malformed data left by an older client.
    }
  }

  window.addEventListener(EVENT_NAME, onCustomEvent)
  window.addEventListener('storage', onStorage)
  return () => {
    window.removeEventListener(EVENT_NAME, onCustomEvent)
    window.removeEventListener('storage', onStorage)
  }
}
