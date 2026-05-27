import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  clearAuthStorage,
  getProfile,
  getToken,
  setProfile,
  setToken,
  type Role,
  type StoredProfile,
} from '@/utils/authStorage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const profile = ref<StoredProfile | null>(getProfile())

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => profile.value?.role === 'ADMIN')
  const username = computed(() => profile.value?.username ?? '')

  function login(jwt: string, p: StoredProfile) {
    setToken(jwt)
    setProfile(p)
    token.value = jwt
    profile.value = p
  }

  function logout() {
    clearAuthStorage()
    token.value = null
    profile.value = null
  }

  function hydrate() {
    token.value = getToken()
    profile.value = getProfile()
  }

  return { token, profile, isAuthenticated, isAdmin, username, login, logout, hydrate }
})
