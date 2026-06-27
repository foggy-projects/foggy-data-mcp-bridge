import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SelectFilter from './SelectFilter.vue'
import type { SliceRequestDef } from '@/types'

describe('SelectFilter', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('keeps checkbox clicks in sync with multi-select state', async () => {
    const wrapper = mount(SelectFilter, {
      attachTo: document.body,
      props: {
        field: 'customerType',
        options: [
          { label: '企业', value: 10 },
          { label: '个人', value: 20 }
        ]
      }
    })

    await wrapper.find('.toggle-multi').trigger('click')
    await wrapper.find('.select-input').trigger('click')

    const options = document.body.querySelectorAll<HTMLElement>('.filter-option')
    options[0].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(document.body.querySelector('.selected-count')?.textContent).toContain('已选 1 项')

    const checkboxes = document.body.querySelectorAll<HTMLInputElement>('.filter-option input[type="checkbox"]')
    checkboxes[1].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(document.body.querySelector('.selected-count')?.textContent).toContain('已选 2 项')

    document.body.querySelector<HTMLButtonElement>('.confirm-btn')?.click()
    await wrapper.vm.$nextTick()

    const commitEvents = wrapper.emitted('commit') as [SliceRequestDef[] | null][]
    expect(commitEvents.at(-1)?.[0]).toEqual([
      { field: 'customerType', op: 'in', value: [10, 20] }
    ])
  })
})
