import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import PresetDateValue from './PresetDateValue.vue'
import DateRangeFilter from './DateRangeFilter.vue'

describe('preset date controls', () => {
  it('stores a relative token and restores the manual picker on clear', async () => {
    const wrapper = shallowMount(PresetDateValue, {
      props: { condition: { field: 'created', op: '[)' }, showTime: true },
      global: { stubs: { 'el-select': true, 'el-option': true, 'el-date-picker': true } }
    })
    ;(wrapper.vm as any).setRelative('today')
    const condition = (wrapper.emitted('update:condition')![0][0]) as any
    expect(condition).toEqual({ field: 'created', op: '[)', value: { $relativeDate: 'today', dateTime: true } })
    await wrapper.setProps({ condition })
    expect(wrapper.findComponent(DateRangeFilter).exists()).toBe(false)
    ;(wrapper.vm as any).setRelative('')
    await wrapper.setProps({ condition: wrapper.emitted('update:condition')![1][0] as any })
    expect(wrapper.findComponent(DateRangeFilter).exists()).toBe(true)
  })

  it('exposes a clear control for relative date selections', () => {
    const wrapper = shallowMount(PresetDateValue, {
      props: { condition: { field: 'created', op: '[)', value: { $relativeDate: 'yesterday' } } },
      global: { stubs: { 'el-select': true, 'el-option': true, 'el-date-picker': true } }
    })

    expect(wrapper.html()).toContain('clearable')
  })
})
