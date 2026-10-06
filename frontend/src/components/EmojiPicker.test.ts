import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import EmojiPicker from './EmojiPicker.vue'

describe('EmojiPicker.vue', () => {
  it('does not render picker when visible is false', () => {
    const wrapper = mount(EmojiPicker, {
      props: { visible: false },
    })
    expect(wrapper.find('.emoji-picker').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders quick reactions strip and emits select on click', async () => {
    const wrapper = mount(EmojiPicker, {
      props: { visible: true },
    })

    const picker = document.body.querySelector('.emoji-picker')
    expect(picker).toBeTruthy()

    const quickBtns = document.body.querySelectorAll('.quick-emoji-btn')
    expect(quickBtns.length).toBeGreaterThan(5)

    // Click basketball emoji button
    const firstQuick = quickBtns[0] as HTMLButtonElement
    expect(firstQuick.textContent?.trim()).toBe('🏀')
    firstQuick.click()

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual(['🏀'])

    wrapper.unmount()
  })

  it('filters emojis correctly via search input', async () => {
    const wrapper = mount(EmojiPicker, {
      props: { visible: true },
    })

    const input = document.body.querySelector('.emoji-search-input') as HTMLInputElement
    expect(input).toBeTruthy()

    input.value = 'GOAT'
    input.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    const items = document.body.querySelectorAll('.emoji-item')
    expect(items.length).toBeGreaterThanOrEqual(1)

    wrapper.unmount()
  })

  it('correctly matches basketball terms like 盖帽 and 三分 and 空心铁', async () => {
    const wrapper = mount(EmojiPicker, {
      props: { visible: true },
    })

    const input = document.body.querySelector('.emoji-search-input') as HTMLInputElement
    expect(input).toBeTruthy()

    input.value = '盖帽'
    input.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    const blockItems = document.body.querySelectorAll('.emoji-item')
    expect(blockItems.length).toBeGreaterThanOrEqual(1)

    input.value = '三分'
    input.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    const threeItems = document.body.querySelectorAll('.emoji-item')
    expect(threeItems.length).toBeGreaterThanOrEqual(1)

    wrapper.unmount()
  })

  it('applies emoji-picker--centered when anchor is null and emoji-picker--anchored when anchor is provided', async () => {
    const wrapperCentered = mount(EmojiPicker, {
      props: { visible: true, anchor: null },
    })
    const centeredPicker = document.body.querySelector('.emoji-picker')
    expect(centeredPicker?.classList.contains('emoji-picker--centered')).toBe(true)
    wrapperCentered.unmount()

    const anchorEl = document.createElement('button')
    document.body.appendChild(anchorEl)
    const wrapperAnchored = mount(EmojiPicker, {
      props: { visible: true, anchor: anchorEl },
    })
    const anchoredPicker = document.body.querySelector('.emoji-picker')
    expect(anchoredPicker?.classList.contains('emoji-picker--anchored')).toBe(true)
    wrapperAnchored.unmount()
    document.body.removeChild(anchorEl)
  })
})
