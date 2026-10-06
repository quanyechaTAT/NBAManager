import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import GameChatHeader from './GameChatHeader.vue'

describe('GameChatHeader.vue', () => {
  it('renders online count and connection state', () => {
    const wrapper = mount(GameChatHeader, {
      props: {
        connected: true,
        reconnecting: false,
        onlineCount: 42,
        homeTeam: '猛龙',
        awayTeam: '热火',
      },
    })

    expect(wrapper.text()).toContain('42')
    expect(wrapper.text()).toContain('已连线')
    expect(wrapper.text()).toContain('LIVE 实况')
    expect(wrapper.text()).toContain('猛龙')
    expect(wrapper.text()).toContain('热火')

    wrapper.unmount()
  })

  it('emits cheer and selectCamp events on button clicks', async () => {
    const wrapper = mount(GameChatHeader, {
      props: {
        connected: true,
        reconnecting: false,
        onlineCount: 10,
        homeTeam: '猛龙',
        awayTeam: '热火',
        selectedCamp: 'ALL',
        homeCheerCount: 20,
        awayCheerCount: 15,
      },
    })

    // Click away cheer button
    const awayBtn = wrapper.find('.away-btn')
    await awayBtn.trigger('click')
    expect(wrapper.emitted('cheer')).toBeTruthy()
    expect(wrapper.emitted('cheer')![0]).toEqual(['AWAY'])

    // Click home chip
    const homeChip = wrapper.find('.home-chip')
    await homeChip.trigger('click')
    expect(wrapper.emitted('selectCamp')).toBeTruthy()
    expect(wrapper.emitted('selectCamp')![0]).toEqual(['HOME'])

    wrapper.unmount()
  })

  it('renders 50% vs 50% neutral cheer meter when both cheer counts are 0', () => {
    const wrapper = mount(GameChatHeader, {
      props: {
        connected: true,
        reconnecting: false,
        onlineCount: 5,
        homeTeam: '猛龙',
        awayTeam: '热火',
        homeCheerCount: 0,
        awayCheerCount: 0,
      },
    })

    const awayPct = wrapper.find('.away-pct')
    const homePct = wrapper.find('.home-pct')
    expect(awayPct.text()).toBe('50%')
    expect(homePct.text()).toBe('50%')

    wrapper.unmount()
  })
})
