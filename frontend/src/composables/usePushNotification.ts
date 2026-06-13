import { ref, computed } from 'vue'

const SUBSCRIPTION_KEY = 'nba_push_subscription'

export function usePushNotification() {
  const isSupported = computed(() => {
    return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
  })

  const permissionStatus = ref<NotificationPermission>('default')

  function updatePermission() {
    if (isSupported.value) {
      permissionStatus.value = Notification.permission
    }
  }

  async function registerServiceWorker(): Promise<ServiceWorkerRegistration | null> {
    if (!isSupported.value) return null
    try {
      const registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' })
      return registration
    } catch {
      return null
    }
  }

  async function requestPermission(): Promise<boolean> {
    if (!isSupported.value) return false

    // Already granted
    if (Notification.permission === 'granted') {
      updatePermission()
      return true
    }

    // Already denied
    if (Notification.permission === 'denied') {
      updatePermission()
      return false
    }

    // Request permission
    const result = await Notification.requestPermission()
    updatePermission()
    return result === 'granted'
  }

  async function subscribe(): Promise<PushSubscription | null> {
    if (!isSupported.value) return null

    const granted = await requestPermission()
    if (!granted) return null

    const registration = await registerServiceWorker()
    if (!registration) return null

    try {
      const key = urlBase64ToUint8Array(getVapidPublicKey())
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: key.buffer as ArrayBuffer,
      })

      // Store subscription in localStorage
      localStorage.setItem(SUBSCRIPTION_KEY, JSON.stringify(subscription))
      return subscription
    } catch {
      return null
    }
  }

  function getStoredSubscription(): PushSubscription | null {
    try {
      const stored = localStorage.getItem(SUBSCRIPTION_KEY)
      if (stored) return JSON.parse(stored)
    } catch { /* ignore */ }
    return null
  }

  function clearSubscription() {
    localStorage.removeItem(SUBSCRIPTION_KEY)
  }

  // Vapid key placeholder - replace with actual public key from backend
  function getVapidPublicKey(): string {
    // This is a placeholder - in production, fetch from backend /api/vapid-key
    return 'BEl62iUYgUivxVkvSC9LIC-fnWdHsUJr1mHcHmHdHkFAHhIaKz2GZQhYd5GZr3xR7q8Kd9j0mLpO1iU'
  }

  function urlBase64ToUint8Array(base64String: string): Uint8Array {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
    const rawData = window.atob(base64)
    const outputArray = new Uint8Array(rawData.length)
    for (let i = 0; i < rawData.length; i++) {
      outputArray[i] = rawData.charCodeAt(i)
    }
    return outputArray
  }

  // Initialize permission status
  updatePermission()

  return {
    isSupported,
    permissionStatus,
    requestPermission,
    subscribe,
    registerServiceWorker,
    getStoredSubscription,
    clearSubscription,
  }
}
