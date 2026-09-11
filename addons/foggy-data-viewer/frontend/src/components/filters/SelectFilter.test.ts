import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import SelectFilter from './SelectFilter.vue'
import type { SliceRequestDef } from '@/types'

describe('SelectFilter', () => {
  it('keeps the dropdown in its dialog and labels its multi-select checkboxes', async () => {
    const dialog = document.createElement('div')
    dialog.setAttribute('role', 'dialog')
    document.body.appendChild(dialog)
    const wrapper = mount(SelectFilter, { attachTo: dialog, props: {
      field: 'payment', options: [{ value: 'PREPAID', label: '寄付' }]
    } })
    await wrapper.find('.toggle-multi').trigger('click')
    await wrapper.find('.select-input').trigger('click')
    expect(dialog.querySelector('.filter-dropdown')).not.toBeNull()
    expect(dialog.querySelector('input[type="checkbox"]')?.getAttribute('aria-label')).toBe('寄付')
    wrapper.unmount()
  })
  it('hydrates off-page selectedItems without opening, changing DSL types, or emitting queries', async () => {
    const loader = vi.fn().mockResolvedValue({ items: [{ value: 1, label: '其他' }], total: 100,
      selectedItems: [{ value: '80581', label: '郑州分拨中心' }] })
    const slices = [{ field: 'srcNode$id', op: '=', value: 80581 }]
    const wrapper = mount(SelectFilter, { props: { field: 'srcNode', selectionField: 'srcNode$id',
      qmModel: 'Routes', remoteLoader: loader, modelValue: slices } })
    await flushPromises()
    expect(loader).toHaveBeenCalledWith(expect.objectContaining({ selectedValues: [80581] }))
    expect(wrapper.find('.selected-text').text()).toBe('郑州分拨中心')
    expect(slices[0].value).toBe(80581)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('commit')).toBeUndefined()
    loader.mockResolvedValue({ items: [], total: 100, selectedItems: [{ value: '9', label: '页外成员' }] })
    await wrapper.setProps({ modelValue: [{ field: 'srcNode$id', op: 'in', value: ['9'] }] })
    await flushPromises()
    expect(wrapper.find('.selected-text').text()).toBe('页外成员')
    expect(loader.mock.calls.at(-1)?.[0].selectedValues).toEqual(['9'])
    wrapper.unmount()
  })

  it.each(['model', 'field', 'selectionField', 'loader'])('ignores stale hydration and dropdown results after %s changes', async change => {
    const pending: ((value: any) => void)[] = []
    const loader = vi.fn(() => new Promise<any>(resolve => pending.push(resolve)))
    const wrapper = mount(SelectFilter, { props: { field: 'srcNode', selectionField: 'srcNode$id',
      qmModel: 'A', remoteLoader: loader, modelValue: [{ field: 'srcNode$id', op: '=', value: 7 }] } })
    await wrapper.find('.select-input').trigger('click')
    expect(pending).toHaveLength(2)
    await wrapper.setProps(change === 'model' ? { qmModel: 'B' }
      : change === 'field' ? { field: 'dstNode' }
      : change === 'selectionField' ? { selectionField: 'dstNode$id' }
      : { remoteLoader: () => loader() })
    pending[2]({ items: [], total: 0, selectedItems: [{ value: 7, label: 'B名称' }] })
    await flushPromises()
    pending[0]({ items: [], total: 0, selectedItems: [{ value: 7, label: 'A旧名称' }] })
    pending[1]({ items: [{ value: 7, label: 'A旧下拉' }], total: 1 })
    await flushPromises()
    expect(wrapper.find('.selected-text').text()).toBe('B名称')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('commit')).toBeUndefined()
    wrapper.unmount()
  })

  it('ignores earlier selections and recovers labels after a lookup failure', async () => {
    const pending: ((value: any) => void)[] = []
    const loader = vi.fn(() => new Promise<any>(resolve => pending.push(resolve)))
    const wrapper = mount(SelectFilter, { props: { field: 'node', qmModel: 'A', remoteLoader: loader,
      modelValue: [{ field: 'node$id', op: '=', value: 1 }] } })
    await wrapper.setProps({ modelValue: [{ field: 'node$id', op: '=', value: 2 }] })
    pending[1]({ items: [{ value: 2, label: '新' }], total: 1 })
    pending[0]({ items: [{ value: 2, label: '旧' }], total: 1 })
    await flushPromises()
    expect(wrapper.find('.selected-text').text()).toBe('新')
    const errorLog = vi.spyOn(console, 'error').mockImplementation(() => {})
    const retryLoader = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({
      items: [], total: 0, selectedItems: [{ value: 2, label: '恢复' }] })
    await wrapper.setProps({ remoteLoader: retryLoader })
    await flushPromises()
    expect(wrapper.find('.selected-text').text()).toBe('2')
    await wrapper.find('.select-input').trigger('click')
    await flushPromises()
    expect(wrapper.find('.selected-text').text()).toBe('恢复')
    expect(wrapper.emitted('commit')).toBeUndefined()
    errorLog.mockRestore()
    wrapper.unmount()
  })

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

  it('aligns dropdown width with input and keeps a minimum width', async () => {
    const wrapper = mount(SelectFilter, {
      attachTo: document.body,
      props: {
        field: 'status',
        options: [
          { label: '运输中', value: 'transporting' }
        ]
      }
    })

    const input = wrapper.find('.select-input').element as HTMLElement
    input.getBoundingClientRect = () => ({
      width: 86,
      height: 28,
      top: 10,
      right: 106,
      bottom: 38,
      left: 20,
      x: 20,
      y: 10,
      toJSON: () => ({})
    })

    await wrapper.find('.select-input').trigger('click')

    const dropdown = document.body.querySelector<HTMLElement>('.filter-dropdown')
    expect(dropdown?.style.left).toBe('20px')
    expect(dropdown?.style.width).toBe('86px')
    expect(dropdown?.style.minWidth).toBe('160px')
  })
})
