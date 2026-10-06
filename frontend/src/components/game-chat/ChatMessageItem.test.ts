import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import ChatMessageItem from './ChatMessageItem.vue'
import type { LocalChatMessage } from '@/composables/useGameChat'

describe('ChatMessageItem.vue', () => {
  const baseMessage: LocalChatMessage = {
    id: 1,
    clientMessageId: 'c-1',
    gameId: '0012600009',
    userId: 10,
    username: 'LeBronFan',
    avatarUrl: null,
    role: 'USER',
    type: 'TEXT',
    content: '猛龙这一攻战术打得真漂亮！',
    imageUrl: null,
    createdAt: '2026-10-05T12:00:00Z',
    deleted: false,
    status: 'sent',
  }

  it('renders username, content, and matched home team badge', () => {
    const wrapper = mount(ChatMessageItem, {
      props: {
        message: baseMessage,
        isSelf: false,
        homeTeam: '猛龙',
        awayTeam: '热火',
      },
    })

    expect(wrapper.text()).toContain('LeBronFan')
    expect(wrapper.text()).toContain('猛龙这一攻战术打得真漂亮！')
    expect(wrapper.text()).toContain('猛龙 拥趸')

    wrapper.unmount()
  })

  it('renders admin badge for admin user', () => {
    const adminMessage: LocalChatMessage = {
      ...baseMessage,
      role: 'ADMIN',
      content: '欢迎进入官方直播间',
    }

    const wrapper = mount(ChatMessageItem, {
      props: {
        message: adminMessage,
        isSelf: true,
      },
    })

    expect(wrapper.text()).toContain('官方解说')
    expect(wrapper.text()).not.toContain('👑')
    expect(wrapper.classes()).toContain('is-self')

    wrapper.unmount()
  })
})
