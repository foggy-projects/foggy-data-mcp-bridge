import { describe, it, expect } from 'vitest'
import { shallowMount, config } from '@vue/test-utils'
import Editor from './ListPresetConditionEditor.vue'
import SelectFilter from '../filters/SelectFilter.vue'

for (const name of ['el-button', 'el-alert', 'el-option', 'el-select', 'el-input']) config.global.stubs[name] = true

describe('preset condition wire contract', () => {
  const columns = [{ name: 'customerName', title: '客户', type: 'TEXT', memberLookup: {
    enabled: true, selectionFieldName: 'customer$id', displayFieldName: 'customerName'
  } }]
  it('emits the dollar-prefixed JSON group recognized by Jackson and retains selection fields', () => {
    const wrapper = shallowMount(Editor, { props: { columns } })
    ;(wrapper.vm as any).addGroup('$or')
    const json = JSON.parse(JSON.stringify(wrapper.emitted('update:modelValue')![0][0]))
    expect(json[0]).toEqual({ $or: [
      { field: 'customer$id', op: '=', value: '' },
      { field: 'customer$id', op: '=', value: '' }
    ] })
    expect(json[0]).not.toHaveProperty('or')
  })
  it('uses wire keys in the group selector options', () => {
    const wrapper = shallowMount(Editor, { global: { renderStubDefaultSlot: true }, props: { columns,
      modelValue: [{ $or: [{ field: 'customer$id', op: '=', value: 'id-1' }] }] as any
    } })
    const values = wrapper.findAll('el-option-stub').map(option => option.attributes('value'))
    expect(values).toContain('$and')
    expect(values).toContain('$or')
    expect(values).not.toContain('or')
  })
  it('passes the metadata and loader to the name-based member selector', () => {
    const loader = async () => ({ items: [], total: 0, hasMore: false })
    const wrapper = shallowMount(Editor, { props: {
      columns, qmModel: 'Orders', filterMemberLoader: loader,
      modelValue: [{ field: 'customer$id', op: '=', value: 'id-1' }]
    } })
    const select = wrapper.findComponent(SelectFilter)
    expect(select.props('field')).toBe('customerName')
    expect(select.props('selectionField')).toBe('customer$id')
    expect(select.props('remoteLoader')).toBe(loader)
  })
  it('counts sibling leaves when adding within a nested group', () => {
    const wrapper = shallowMount(Editor, { props: { columns, conditionOffset: 19,
      modelValue: [{ field: 'customer$id', op: '=', value: 'id-1' }] } })
    ;(wrapper.vm as any).addLeaf()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
  it('uses the checkbox selector for dictionary codes and preserves multi-value IN', () => {
    const wrapper = shallowMount(Editor, { props: {
      columns: [{ name: 'status', type: 'TEXT', dictItems: [{ value: 0, label: '待处理' }] }],
      modelValue: [{ field: 'status', op: '=', value: undefined }]
    } })
    const select = wrapper.findComponent(SelectFilter)
    expect(select.props('options')).toEqual([{ value: 0, label: '待处理' }])
    select.vm.$emit('update:modelValue', [{ field: 'status', op: 'in', value: [0, 1] }])
    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual([{ field: 'status', op: 'in', value: [0, 1] }])
  })
})
