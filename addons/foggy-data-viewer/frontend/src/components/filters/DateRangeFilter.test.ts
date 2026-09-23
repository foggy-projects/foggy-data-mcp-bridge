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

  it('defaults the datetime end to 23:59 and serializes the next midnight as an exclusive bound', () => {
    const wrapper = shallowMount(DateRangeFilter, {
      props: { field: 'createdDate', showTime: true, endOfDay: true },
      global: {
        stubs: {
          'el-date-picker': {
            name: 'ElDatePickerStub',
            props: ['defaultTime'],
            template: '<div />'
          }
        }
      }
    })
    const picker = wrapper.findComponent({ name: 'ElDatePickerStub' })
    const [startDefault, endDefault] = picker.props('defaultTime') as Date[]

    expect(startDefault.getHours()).toBe(0)
    expect(startDefault.getMinutes()).toBe(0)
    expect(endDefault.getHours()).toBe(23)
    expect(endDefault.getMinutes()).toBe(59)
    expect(endDefault.getSeconds()).toBe(59)

    ;(wrapper.vm as any).handleChange([
      new Date(2026, 8, 22, 0, 0, 0),
      new Date(2026, 8, 22, 23, 59, 59)
    ])

    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual([{
      field: 'createdDate',
      op: '[)',
      value: ['2026-09-22 00:00:00', '2026-09-23 00:00:00']
    }])
  })

  it('keeps a custom end minute and includes its final second', () => {
    const wrapper = shallowMount(DateRangeFilter, {
      props: { field: 'createdDate', showTime: true, endOfDay: true },
      global: { stubs: { 'el-date-picker': true } }
    })

    ;(wrapper.vm as any).handleChange([
      new Date(2026, 8, 8, 0, 0, 0),
      new Date(2026, 8, 8, 13, 59, 59)
    ])

    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual([{
      field: 'createdDate',
      op: '[)',
      value: ['2026-09-08 00:00:00', '2026-09-08 14:00:00']
    }])
  })

  it('displays an exclusive next-minute bound as the selected end minute', () => {
    const wrapper = shallowMount(DateRangeFilter, {
      props: {
        field: 'createdDate',
        showTime: true,
        endOfDay: true,
        modelValue: [{
          field: 'createdDate',
          op: '[)',
          value: ['2026-09-08 00:00:00', '2026-09-08 14:00:00']
        }]
      },
      global: { stubs: { 'el-date-picker': true } }
    })
    const vm = wrapper.vm as any
    const end = vm.dateRange[1] as Date

    expect(end.getDate()).toBe(8)
    expect(end.getHours()).toBe(13)
    expect(end.getMinutes()).toBe(59)
    expect(end.getSeconds()).toBe(59)
    vm.handleChange(vm.dateRange)
    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual([{
      field: 'createdDate',
      op: '[)',
      value: ['2026-09-08 00:00:00', '2026-09-08 14:00:00']
    }])
  })

  it('displays an exclusive next-midnight bound as 23:59 on the selected end date', () => {
    const wrapper = shallowMount(DateRangeFilter, {
      props: {
        field: 'createdDate',
        showTime: true,
        endOfDay: true,
        modelValue: [{
          field: 'createdDate',
          op: '[)',
          value: ['2026-09-22 00:00:00', '2026-09-23 00:00:00']
        }]
      },
      global: { stubs: { 'el-date-picker': true } }
    })
    const end = (wrapper.vm as any).dateRange[1] as Date

    expect(end.getFullYear()).toBe(2026)
    expect(end.getMonth()).toBe(8)
    expect(end.getDate()).toBe(22)
    expect(end.getHours()).toBe(23)
    expect(end.getMinutes()).toBe(59)
    expect(end.getSeconds()).toBe(59)
  })

  it('displays an existing inclusive midnight end as 23:59 and normalizes it to the next midnight', () => {
    const wrapper = shallowMount(DateRangeFilter, {
      props: {
        field: 'createdDate',
        showTime: true,
        endOfDay: true,
        modelValue: [{
          field: 'createdDate',
          op: '[]',
          value: ['2026-09-22 00:00:00', '2026-09-22 00:00:00']
        }]
      },
      global: { stubs: { 'el-date-picker': true } }
    })
    const vm = wrapper.vm as any
    const end = vm.dateRange[1] as Date

    expect(end.getFullYear()).toBe(2026)
    expect(end.getMonth()).toBe(8)
    expect(end.getDate()).toBe(22)
    expect(end.getHours()).toBe(23)
    expect(end.getMinutes()).toBe(59)
    expect(end.getSeconds()).toBe(59)

    vm.handleChange(vm.dateRange)

    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual([{
      field: 'createdDate',
      op: '[)',
      value: ['2026-09-22 00:00:00', '2026-09-23 00:00:00']
    }])
  })

  it('keeps a legacy same-day empty half-open range on its selected date', () => {
    const wrapper = shallowMount(DateRangeFilter, {
      props: {
        field: 'actualDepartureTime',
        showTime: true,
        endOfDay: true,
        modelValue: [{
          field: 'actualDepartureTime',
          op: '[)',
          value: ['2026-09-16 00:00:00', '2026-09-16 00:00:00']
        }]
      },
      global: { stubs: { 'el-date-picker': true } }
    })
    const vm = wrapper.vm as any
    const end = vm.dateRange[1] as Date

    expect(end.getDate()).toBe(16)
    expect(end.getHours()).toBe(23)
    expect(end.getMinutes()).toBe(59)

    vm.handleChange(vm.dateRange)
    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual([{
      field: 'actualDepartureTime',
      op: '[)',
      value: ['2026-09-16 00:00:00', '2026-09-17 00:00:00']
    }])
  })
})
