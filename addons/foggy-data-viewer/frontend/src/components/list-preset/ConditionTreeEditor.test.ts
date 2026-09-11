import { describe, it, expect, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import Editor from './ConditionTreeEditor.vue'

vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn() } }))
const field = { name: 'orderNo', title: '运单号', type: 'TEXT' }
const stubs = Object.fromEntries(['el-button', 'el-tree', 'el-dropdown', 'el-dropdown-menu', 'el-dropdown-item', 'el-dialog', 'el-input'].map(name => [name, true]))
function mountEditor(modelValue: any[] = []) {
  return shallowMount(Editor, { props: { columns: [field], modelValue }, global: { stubs } })
}
describe('condition tree wizard', () => {
  it('targets node menu actions at the clicked node and cleans up menu refs', () => {
    const wrapper = mountEditor([{ $or: [] }])
    const vm = wrapper.vm as any
    const handleOpen = vi.fn()
    vm.setNodeMenu('0', { handleOpen })
    vm.openNodeMenu({ key: '0', path: [0] })
    expect(handleOpen).toHaveBeenCalledOnce()
    vm.pickField(field)
    expect(wrapper.emitted('update:modelValue')!.at(-1)![0]).toEqual([{ $or: [{ field: 'orderNo', op: '=', value: undefined }] }])
    vm.setNodeMenu('0', null)
    vm.openNodeMenu({ key: '0', path: [0] })
    expect(handleOpen).toHaveBeenCalledOnce()
    expect(wrapper.find('.tree-actions').exists()).toBe(false)
  })
  it('allows repeated fields without modifying the input tree', () => {
    const original = [{ field: 'orderNo', op: '=', value: '' }]
    const wrapper = mountEditor(original)
    ;(wrapper.vm as any).pickField(field)
    expect(wrapper.emitted('update:modelValue')![0][0]).toEqual([...original, { field: 'orderNo', op: '=', value: undefined }])
    expect(original).toHaveLength(1)
  })
  it('blocks the 21st leaf even when all fields are identical', () => {
    const wrapper = mountEditor(Array.from({ length: 20 }, () => ({ field: 'orderNo', op: '=', value: '' })))
    ;(wrapper.vm as any).pickField(field)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
  it('adds into the selected group, switches logic and deletes a group without mutating props', async () => {
    const wrapper = mountEditor()
    const vm = wrapper.vm as any
    vm.addGroup('$or')
    await wrapper.setProps({ modelValue: wrapper.emitted('update:modelValue')!.at(-1)![0] as any })
    vm.pickField(field)
    const next = wrapper.emitted('update:modelValue')!.at(-1)![0] as any
    expect(next).toEqual([{ $or: [{ field: 'orderNo', op: '=', value: undefined }] }])
    await wrapper.setProps({ modelValue: next })
    vm.removeSelected()
    await wrapper.setProps({ modelValue: wrapper.emitted('update:modelValue')!.at(-1)![0] as any })
    vm.toggleGroup()
    expect(wrapper.emitted('update:modelValue')!.at(-1)![0]).toEqual([{ $and: [] }])
    vm.removeSelected()
    expect(wrapper.emitted('update:modelValue')!.at(-1)![0]).toEqual([])
    expect(next[0].$or).toHaveLength(1)
  })
  it('switches only the selected field and uses dimension selection keys', async () => {
    const wrapper = mountEditor()
    const vm = wrapper.vm as any
    vm.pickField(field)
    await wrapper.setProps({ modelValue: wrapper.emitted('update:modelValue')!.at(-1)![0] as any })
    vm.openPicker('switch')
    vm.pickField({ name: 'origin$caption', type: 'TEXT', memberLookup: { enabled: true, selectionFieldName: 'origin$id' } })
    expect(wrapper.emitted('update:modelValue')!.at(-1)![0]).toEqual([{ field: 'origin$id', op: '=', value: undefined }])
  })
})
