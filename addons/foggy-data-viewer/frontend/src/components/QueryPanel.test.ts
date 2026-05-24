import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import QueryPanel from './QueryPanel.vue'
import type { MemberQueryResponse, SliceRequestDef } from '@/types'

vi.mock('./filters', () => ({
  TextFilter: {
    name: 'TextFilter',
    props: ['field', 'modelValue', 'placeholder'],
    template: '<div class="text-filter"></div>'
  },
  NumberRangeFilter: {
    name: 'NumberRangeFilter',
    props: ['field', 'modelValue', 'placeholder'],
    template: '<div class="number-filter"></div>'
  },
  DateRangeFilter: {
    name: 'DateRangeFilter',
    props: ['field', 'modelValue', 'placeholder', 'showTime'],
    template: '<div class="date-filter"></div>'
  },
  SelectFilter: {
    name: 'SelectFilter',
    props: [
      'field',
      'selectionField',
      'modelValue',
      'options',
      'placeholder',
      'remoteLoader',
      'qmModel'
    ],
    emits: ['update:modelValue'],
    template: `
      <button
        class="select-filter"
        @click="$emit('update:modelValue', [{ field: selectionField || field, op: '=', value: 1 }])"
      ></button>
    `
  },
  BoolFilter: {
    name: 'BoolFilter',
    props: ['field', 'modelValue', 'placeholder'],
    template: '<div class="bool-filter"></div>'
  }
}))

describe('QueryPanel', () => {
  const loader = vi.fn<[], Promise<MemberQueryResponse>>()

  it('passes lookup field to member loader and selection field to DSL output', async () => {
    const wrapper = mount(QueryPanel, {
      props: {
        qmModel: 'CurrentStationPickupOrderListQuery',
        filterMemberLoader: loader,
        schema: {
          fields: [
            {
              key: 'destinationServiceArea$caption',
              label: '目的服务区域(名称)',
              sourceField: 'destinationServiceArea$id',
              lookupRef: 'destinationServiceArea$caption',
              placement: 'form',
              component: 'memberSelect'
            }
          ],
          submitMode: 'manual'
        }
      }
    })

    const select = wrapper.findComponent({ name: 'SelectFilter' })

    expect(select.props('field')).toBe('destinationServiceArea$caption')
    expect(select.props('selectionField')).toBe('destinationServiceArea$id')
    expect(select.props('remoteLoader')).toBe(loader)
    expect(select.props('qmModel')).toBe('CurrentStationPickupOrderListQuery')

    await select.trigger('click')
    await wrapper.find('.btn-primary').trigger('click')

    const emitted = wrapper.emitted('update:modelValue')
    const slices = emitted![emitted!.length - 1][0] as SliceRequestDef[]
    expect(slices).toEqual([
      { field: 'destinationServiceArea$id', op: '=', value: 1 }
    ])
  })

  it('uses schema key as member lookup field when lookupRef is absent', () => {
    const wrapper = mount(QueryPanel, {
      props: {
        qmModel: 'OrderQuery',
        filterMemberLoader: loader,
        schema: {
          fields: [
            {
              key: 'customer$caption',
              label: '客户',
              sourceField: 'customer$id',
              placement: 'form',
              component: 'qmLookupSelect'
            }
          ]
        }
      }
    })

    const select = wrapper.findComponent({ name: 'SelectFilter' })
    expect(select.props('field')).toBe('customer$caption')
    expect(select.props('selectionField')).toBe('customer$id')
  })
})
