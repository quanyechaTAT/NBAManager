import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import ChatComposer from './ChatComposer.vue'

describe('ChatComposer.vue', () => {
  it('renders quick cheer presets and sends message on click', async () => {
    const wrapper = mount(ChatComposer, {
      props: {
        connected: true,
        sendLoading: false,
        homeTeam: '猛龙',
        awayTeam: '热火',
      },
      global: {
        stubs: {
          EmojiPicker: true,
        },
      },
    })

    expect(wrapper.text()).toContain('快捷喊话')
    expect(wrapper.text()).toContain('热火加油！')
    expect(wrapper.text()).toContain('猛龙必胜！')
    expect(wrapper.text()).not.toContain('🔥')
    expect(wrapper.text()).not.toContain('⚡')

    const firstPill = wrapper.find('.chant-pill')
    await firstPill.trigger('click')

    expect(wrapper.emitted('send')).toBeTruthy()
    expect(wrapper.emitted('send')![0]).toEqual(['热火加油！'])

    wrapper.unmount()
  })

  it('handles user input and emits send on button click', async () => {
    const wrapper = mount(ChatComposer, {
      props: {
        connected: true,
        sendLoading: false,
      },
      global: {
        stubs: {
          EmojiPicker: true,
        },
      },
    })

    const textarea = wrapper.find('.composer-textarea')
    await textarea.setValue('这记三分太关键了！')

    const sendBtn = wrapper.find('.send-btn')
    expect(sendBtn.attributes('disabled')).toBeUndefined()

    await sendBtn.trigger('click')
    expect(wrapper.emitted('send')).toBeTruthy()
    expect(wrapper.emitted('send')![0]).toEqual(['这记三分太关键了！'])

    wrapper.unmount()
  })

  it('inserts emoji at cursor position 0 correctly without appending to end', async () => {
    const wrapper = mount(ChatComposer, {
      props: {
        connected: true,
        sendLoading: false,
      },
      attachTo: document.body,
    })

    const textarea = wrapper.find('.composer-textarea')
    const textareaEl = textarea.element as HTMLTextAreaElement
    textareaEl.value = '好球！'
    textareaEl.selectionStart = 0
    textareaEl.selectionEnd = 0
    await textarea.trigger('input')

    // Find EmojiPicker stub and trigger select
    const picker = wrapper.findComponent({ name: 'EmojiPicker' })
    picker.vm.$emit('select', '🏀')
    await wrapper.vm.$nextTick()

    expect(textareaEl.value).toBe('🏀好球！')
    wrapper.unmount()
  })

  it('does not send chant when sendLoading is true', async () => {
    const wrapper = mount(ChatComposer, {
      props: {
        connected: true,
        sendLoading: true,
        homeTeam: '猛龙',
        awayTeam: '热火',
      },
      global: {
        stubs: { EmojiPicker: true },
      },
    })

    const firstPill = wrapper.find('.chant-pill')
    await firstPill.trigger('click')
    expect(wrapper.emitted('send')).toBeFalsy()

    wrapper.unmount()
  })
})
