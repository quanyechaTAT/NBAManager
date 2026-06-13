// NBA Manager - Push Notification Service Worker

self.addEventListener('install', (event) => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(clients.claim())
})

self.addEventListener('push', (event) => {
  let data = { title: 'NBA Manager', body: '', icon: '/images/nba-logo.png', url: '/' }

  if (event.data) {
    try {
      const payload = event.data.json()
      data = {
        title: payload.title || 'NBA Manager',
        body: payload.body || '',
        icon: payload.icon || '/images/nba-logo.png',
        url: payload.url || '/',
      }
    } catch {
      data.body = event.data.text()
    }
  }

  const options = {
    body: data.body,
    icon: data.icon,
    badge: '/images/nba-logo.png',
    vibrate: [100, 50, 100],
    data: { url: data.url },
    tag: 'nba-notification',
    renotify: true,
  }

  event.waitUntil(self.registration.showNotification(data.title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const targetUrl = event.notification.data?.url || '/'

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      // If the app is already open, focus it and navigate
      for (const client of windowClients) {
        if (client.url.includes(self.location.origin) && 'focus' in client) {
          client.navigate(targetUrl)
          return client.focus()
        }
      }
      // Otherwise open a new window
      return clients.openWindow(targetUrl)
    })
  )
})
