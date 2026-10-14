import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, it, expect, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import DiscoveryPreferences from './DiscoveryPreferences.vue'
import { discoveryPreferences, updateDiscoveryPreferences } from '@/api/discovery'

vi.mock('@/api/discovery', () => ({ discoveryPreferences: vi.fn(), updateDiscoveryPreferences: vi.fn(), clearDiscoveryPreferences: vi.fn() }))

describe('discovery preference account isolation', () => {
  it('does not apply a previous account response after switching accounts', async () => {
    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(); auth.token = 'first-account'
    vi.mocked(discoveryPreferences).mockResolvedValue({ data: { personalized: true, revision: 0 } } as Awaited<ReturnType<typeof discoveryPreferences>>)
    let finish!: (value: Awaited<ReturnType<typeof updateDiscoveryPreferences>>) => void
    vi.mocked(updateDiscoveryPreferences).mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const wrapper = mount(DiscoveryPreferences, { global: { plugins: [pinia], stubs: { ElSwitch: { props: ['modelValue'], template: '<button class="switch" @click="$emit(\'change\', false)">{{ modelValue }}</button>' } } } })
    await flushPromises()
    await wrapper.get('.switch').trigger('click')
    auth.token = 'second-account'; await flushPromises()
    finish({ data: { personalized: false, revision: 1 } } as Awaited<ReturnType<typeof updateDiscoveryPreferences>>)
    await flushPromises()
    expect(wrapper.get('.switch').text()).toBe('true')
    expect(wrapper.text()).not.toContain('已关闭个性化推荐')
    wrapper.unmount()
  })
})
