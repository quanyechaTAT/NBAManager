import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface UserIdentity {
  userId: number
  username: string
  avatarUrl: string | null
  updatedAt: string
}

export const useIdentityStore = defineStore('identity', () => {
  const identities = ref<Map<number, UserIdentity>>(new Map())
  // 每次更新递增，供 watch 触发（Map.set 不改变 size 时也需要触发）
  const revision = ref(0)

  function updateIdentity(data: UserIdentity) {
    identities.value.set(data.userId, data)
    revision.value++
  }

  function getIdentity(userId: number): UserIdentity | undefined {
    return identities.value.get(userId)
  }

  function getUsername(userId: number): string | undefined {
    return identities.value.get(userId)?.username
  }

  function getAvatarUrl(userId: number): string | null {
    return identities.value.get(userId)?.avatarUrl ?? null
  }

  return { identities, revision, updateIdentity, getIdentity, getUsername, getAvatarUrl }
})
