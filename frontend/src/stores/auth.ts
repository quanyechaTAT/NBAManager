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

function getAvatarForUser(username?: string): string | null {
  if (!username) return null
  return localStorage.getItem(`avatarUrl_${username}`)
}

function setAvatarForUser(username: string, url: string) {
  localStorage.setItem(`avatarUrl_${username}`, url)
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const profile = ref<StoredProfile | null>(getProfile())
  const avatarUrl = ref<string | null>(getAvatarForUser(profile.value?.username))

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => profile.value?.role === 'ADMIN')
  const username = computed(() => profile.value?.username ?? '')

  function login(jwt: string, p: StoredProfile) {
    saveToken(jwt)
    setProfile(p)
    token.value = jwt
    profile.value = p
    avatarUrl.value = p.avatarUrl || getAvatarForUser(p.username)
  }

  function logout() {
    clearAuthStorage()
    token.value = null
    profile.value = null
    avatarUrl.value = null
  }

  function hydrate() {
    token.value = getToken()
    profile.value = getProfile()
    avatarUrl.value = getAvatarForUser(profile.value?.username)
  }

  function setAvatar(url: string) {
    avatarUrl.value = url
    if (profile.value) {
      profile.value = { ...profile.value, avatarUrl: url }
      setProfile(profile.value)
      setAvatarForUser(profile.value.username, url)
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

  return { token, profile, avatarUrl, isAuthenticated, isAdmin, username, login, logout, hydrate, setAvatar, syncLegacyAvatar, updateToken, setUsername }
})
