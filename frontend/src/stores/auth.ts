import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  clearAuthStorage,
  getProfile,
  getToken,
  setProfile,
  setToken as saveToken,
  type StoredProfile,
} from '@/utils/authStorage'
import { uploadAvatarApi } from '@/api/auth'

export function sanitizeAvatarUrl(url?: string | null): string | null {
  if (!url || typeof url !== 'string') return null
  const trimmed = url.trim()
  const lower = trimmed.toLowerCase()
  if (
    !trimmed ||
    lower === 'null' ||
    lower === 'undefined' ||
    lower === '[object object]' ||
    lower === 'false' ||
    lower === 'none' ||
    lower.endsWith('/null') ||
    lower.endsWith('/undefined')
  ) {
    return null
  }
  return trimmed
}

function getAvatarForUser(username?: string): string | null {
  if (!username) return null
  return sanitizeAvatarUrl(localStorage.getItem(`avatarUrl_${username}`))
}

function setAvatarForUser(username: string, url: string | null) {
  const sanitized = sanitizeAvatarUrl(url)
  if (sanitized) {
    localStorage.setItem(`avatarUrl_${username}`, sanitized)
  } else {
    localStorage.removeItem(`avatarUrl_${username}`)
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const profile = ref<StoredProfile | null>(getProfile())
  const initialAvatar = profile.value
    ? (profile.value.avatarUrl !== undefined
        ? sanitizeAvatarUrl(profile.value.avatarUrl)
        : getAvatarForUser(profile.value.username))
    : null
  const avatarUrl = ref<string | null>(initialAvatar)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => profile.value?.role === 'ADMIN')
  const username = computed(() => profile.value?.username ?? '')

  function login(jwt: string, p: StoredProfile) {
    // 若后端明确返回了 avatarUrl（含 null），以此为准，杜绝从 localStorage 复活过期脏数据
    const cleanAvatar = p.avatarUrl !== undefined
      ? sanitizeAvatarUrl(p.avatarUrl)
      : (sanitizeAvatarUrl(p.avatarUrl) || getAvatarForUser(p.username))
    const cleanProfile: StoredProfile = { ...p, avatarUrl: cleanAvatar }
    saveToken(jwt)
    setProfile(cleanProfile)
    token.value = jwt
    profile.value = cleanProfile
    avatarUrl.value = cleanAvatar
    setAvatarForUser(p.username, cleanAvatar)
  }

  function logout() {
    clearAuthStorage()
    token.value = null
    profile.value = null
    avatarUrl.value = null
  }

  function hydrate() {
    token.value = getToken()
    const p = getProfile()
    if (p) {
      const cleanAvatar = p.avatarUrl !== undefined
        ? sanitizeAvatarUrl(p.avatarUrl)
        : getAvatarForUser(p.username)
      profile.value = { ...p, avatarUrl: cleanAvatar }
      avatarUrl.value = cleanAvatar
    } else {
      profile.value = null
      avatarUrl.value = null
    }
  }

  function setAvatar(url: string | null) {
    const cleanUrl = sanitizeAvatarUrl(url)
    avatarUrl.value = cleanUrl
    if (profile.value) {
      profile.value = { ...profile.value, avatarUrl: cleanUrl }
      setProfile(profile.value)
      setAvatarForUser(profile.value.username, cleanUrl)
    }
  }

  function clearBrokenAvatar() {
    avatarUrl.value = null
    if (profile.value) {
      profile.value = { ...profile.value, avatarUrl: null }
      setProfile(profile.value)
      if (profile.value.username) {
        setAvatarForUser(profile.value.username, null)
      }
    }
  }

  async function syncLegacyAvatar() {
    if (!token.value || profile.value?.avatarUrl || !avatarUrl.value?.startsWith('data:image/')) return
    try {
      const response = await fetch(avatarUrl.value)
      const blob = await response.blob()
      const extension = blob.type.split('/')[1]?.replace('jpeg', 'jpg') || 'png'
      const file = new File([blob], `legacy-avatar.${extension}`, { type: blob.type || 'image/png' })
      const { data } = await uploadAvatarApi(file)
      setAvatar(data.avatarUrl)
    } catch (error) {
      console.error('旧头像同步失败:', error)
    }
  }

  function updateToken(jwt: string) {
    saveToken(jwt)
    token.value = jwt
  }

  function setUsername(name: string) {
    if (profile.value) {
      const oldUsername = profile.value.username
      profile.value = { ...profile.value, username: name }
      setProfile(profile.value)
      // 迁移头像
      const oldAvatar = getAvatarForUser(oldUsername)
      if (oldAvatar) {
        setAvatarForUser(name, oldAvatar)
        avatarUrl.value = oldAvatar
      }
    }
  }

  return { token, profile, avatarUrl, isAuthenticated, isAdmin, username, login, logout, hydrate, setAvatar, clearBrokenAvatar, syncLegacyAvatar, updateToken, setUsername }
})
