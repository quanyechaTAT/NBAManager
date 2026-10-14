import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore, sanitizeAvatarUrl } from './auth'

describe('auth store - avatar sanitization and lifecycle', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  describe('sanitizeAvatarUrl', () => {
    it('returns null for empty, whitespace, null, or undefined', () => {
      expect(sanitizeAvatarUrl(null)).toBeNull()
      expect(sanitizeAvatarUrl(undefined)).toBeNull()
      expect(sanitizeAvatarUrl('')).toBeNull()
      expect(sanitizeAvatarUrl('   ')).toBeNull()
    })

    it('returns null for dirty strings like "null", "undefined", "[object Object]"', () => {
      expect(sanitizeAvatarUrl('null')).toBeNull()
      expect(sanitizeAvatarUrl('NULL')).toBeNull()
      expect(sanitizeAvatarUrl('undefined')).toBeNull()
      expect(sanitizeAvatarUrl('[object Object]')).toBeNull()
      expect(sanitizeAvatarUrl('false')).toBeNull()
      expect(sanitizeAvatarUrl('none')).toBeNull()
      expect(sanitizeAvatarUrl('/uploads/avatars/null')).toBeNull()
      expect(sanitizeAvatarUrl('/uploads/avatars/undefined')).toBeNull()
    })

    it('returns trimmed string for valid avatar URLs', () => {
      expect(sanitizeAvatarUrl('  /uploads/avatars/avatar.png  ')).toBe('/uploads/avatars/avatar.png')
      expect(sanitizeAvatarUrl('https://example.com/pic.jpg')).toBe('https://example.com/pic.jpg')
    })
  })

  describe('login without resurrecting stale localStorage', () => {
    it('does not resurrect stale broken avatar from localStorage when backend returns null', () => {
      localStorage.setItem('avatarUrl_user2', 'https://broken-domain.com/404.png')
      const auth = useAuthStore()

      auth.login('token-123', {
        username: 'user2',
        role: 'USER',
        avatarUrl: null,
      })

      expect(auth.avatarUrl).toBeNull()
      expect(localStorage.getItem('avatarUrl_user2')).toBeNull()
    })

    it('sets valid avatarUrl when backend provides one', () => {
      const auth = useAuthStore()
      auth.login('token-123', {
        username: 'user2',
        role: 'USER',
        avatarUrl: '/uploads/avatars/valid.png',
      })

      expect(auth.avatarUrl).toBe('/uploads/avatars/valid.png')
      expect(localStorage.getItem('avatarUrl_user2')).toBe('/uploads/avatars/valid.png')
    })
  })

  describe('clearBrokenAvatar', () => {
    it('clears avatarUrl in store and removes it from localStorage', () => {
      const auth = useAuthStore()
      auth.login('token-123', {
        username: 'user2',
        role: 'USER',
        avatarUrl: 'https://example.com/broken.png',
      })

      expect(auth.avatarUrl).toBe('https://example.com/broken.png')

      auth.clearBrokenAvatar()

      expect(auth.avatarUrl).toBeNull()
      expect(auth.profile?.avatarUrl).toBeNull()
      expect(localStorage.getItem('avatarUrl_user2')).toBeNull()
    })
  })
})
