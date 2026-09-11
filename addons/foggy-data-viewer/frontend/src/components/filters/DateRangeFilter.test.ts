import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import DateRangeFilter from './DateRangeFilter.vue'

describe('date range round trip', () => {
  it('does not add another day when an existing half-open date range is edited', () => {
    const slices = [{ field: 'date', op: '[)', value: ['2026-09-01', '2026-09-11'] }]
    const wrapper = shallowMount(DateRangeFilter, { props: { field: 'date', modelValue: slices }, global: { stubs: { 'el-date-picker': true } } })
    const vm = wrapper.vm as any
    expect(vm.dateRange[1].getDate()).toBe(10)
    vm.handleChange(vm.dateRange)
    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual(slices)
  })
})
