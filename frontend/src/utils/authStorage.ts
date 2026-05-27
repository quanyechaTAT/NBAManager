const TOKEN_KEY = 'nba_token'
const PROFILE_KEY = 'nba_profile'

export type Role = 'ADMIN' | 'USER'

export interface StoredProfile {
  username: string
  role: Role
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getProfile(): StoredProfile | null {
  const raw = localStorage.getItem(PROFILE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredProfile
  } catch {
    return null
  }
}

export function setProfile(p: StoredProfile) {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(p))
}

export function clearAuthStorage() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(PROFILE_KEY)
}
