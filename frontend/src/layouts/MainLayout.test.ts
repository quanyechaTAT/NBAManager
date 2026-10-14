import { mount } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import MainLayout from './MainLayout.vue'

const mockAuth = {
  token: 'mock-token',
  username: 'user2',
  avatarUrl: null as string | null,
  isAuthenticated: true,
  isAdmin: false,
  logout: vi.fn(),
  syncLegacyAvatar: vi.fn(),
  clearBrokenAvatar: vi.fn(),
}

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/dashboard', fullPath: '/dashboard' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockAuth,
}))

vi.mock('@/stores/identity', () => ({
  useIdentityStore: () => ({
    updateIdentity: vi.fn(),
  }),
}))

vi.mock('@/stores/notification', () => ({
  useNotificationStore: () => ({
    startPolling: vi.fn(),
    stopPolling: vi.fn(),
  }),
}))

vi.mock('@/composables/useCurrentSeason', () => ({
  useCurrentSeason: () => ({
    currentSeason: ref('2025-26'),
    loadSeason: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@/composables/useMatchFeed', () => ({
  useMatchFeed: () => ({
    tickerMatches: ref([]),
    live: ref([]),
    refresh: vi.fn().mockResolvedValue(undefined),
  }),
}))

function mountLayout() {
  return mount(MainLayout, {
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        RouterView: { template: '<div class="router-view-stub" />' },
        ThemeToggle: { template: '<div />' },
        GlobalSearch: { template: '<div />' },
        NotificationBell: { template: '<div />' },
        LiveScoreTicker: { template: '<div />' },
        ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
        ElDropdownMenu: { template: '<div><slot /></div>' },
        ElDropdownItem: { template: '<div><slot /></div>' },
        ElDialog: { template: '<div><slot /></div>' },
        ElForm: { template: '<form><slot /></form>' },
        ElFormItem: { template: '<div><slot /></div>' },
        ElInput: { template: '<input />' },
        ElButton: { template: '<button><slot /></button>' },
      },
    },
  })
}

describe('MainLayout.vue', () => {
  beforeEach(() => {
    mockAuth.username = 'user2'
    mockAuth.avatarUrl = null
  })

  it('renders "赛事总览" in navigation items', () => {
    const wrapper = mountLayout()
    expect(wrapper.text()).toContain('赛事总览')
    wrapper.unmount()
  })

  it('renders user initial letter U when avatarUrl is null', () => {
    mockAuth.avatarUrl = null
    const wrapper = mountLayout()
    const avatar = wrapper.find('.user-avatar')
    expect(avatar.exists()).toBe(true)
    expect(avatar.find('img').exists()).toBe(false)
    expect(avatar.text()).toBe('U')
    expect(wrapper.find('.user-name').text()).toBe('user2')
    wrapper.unmount()
  })

  it('falls back to initial letter U when avatar image encounters error', async () => {
    mockAuth.avatarUrl = 'https://example.com/broken-user2.png'
    const wrapper = mountLayout()
    const img = wrapper.find('.user-avatar img')
    expect(img.exists()).toBe(true)

    // Trigger broken image @error
    await img.trigger('error')

    // Image must disappear and fallback letter U must appear
    expect(wrapper.find('.user-avatar img').exists()).toBe(false)
    const fallback = wrapper.find('.user-avatar .avatar-fallback')
    expect(fallback.exists()).toBe(true)
    expect(fallback.text()).toBe('U')
    expect(mockAuth.clearBrokenAvatar).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('correctly trims username and renders initial for leading spaces', () => {
    mockAuth.username = '  lebron'
    mockAuth.avatarUrl = null
    const wrapper = mountLayout()
    const avatar = wrapper.find('.user-avatar')
    expect(avatar.text()).toBe('L')
    wrapper.unmount()
  })
})
