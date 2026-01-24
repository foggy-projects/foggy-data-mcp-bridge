import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchToolbar from './SearchToolbar.vue'
import type { EnhancedColumnSchema, SliceRequestDef } from '@/types'

// Mock filter components
vi.mock('./filters', () => ({
  TextFilter: { name: 'TextFilter', template: '<div class="text-filter"></div>' },
  NumberRangeFilter: { name: 'NumberRangeFilter', template: '<div class="number-filter"></div>' },
  DateRangeFilter: { name: 'DateRangeFilter', template: '<div class="date-filter"></div>' },
  SelectFilter: { name: 'SelectFilter', template: '<div class="select-filter"></div>' },
  BoolFilter: { name: 'BoolFilter', template: '<div class="bool-filter"></div>' }
}))

describe('SearchToolbar', () => {
  const mockColumns: EnhancedColumnSchema[] = [
    { name: 'id', type: 'INTEGER', title: 'ID', filterable: true, filterType: 'number' },
    { name: 'name', type: 'TEXT', title: '名称', filterable: true, filterType: 'text' },
    { name: 'amount', type: 'MONEY', title: '金额', filterable: true, filterType: 'number' },
    { name: 'date', type: 'DAY', title: '日期', filterable: true, filterType: 'date' },
    { name: 'status', type: 'BOOL', title: '状态', filterable: true, filterType: 'bool' },
    { name: 'category', type: 'TEXT', title: '分类', filterable: false } // 不可筛选
  ]

  describe('Rendering', () => {
    it('should render successfully', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.search-toolbar').exists()).toBe(true)
    })

    it('should render only filterable columns', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      const fields = wrapper.findAll('.search-field-item')
      // 5个可筛选字段
      expect(fields).toHaveLength(5)
    })

    it('should render only searchable fields when specified', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['name', 'amount']
        }
      })

      const fields = wrapper.findAll('.search-field-item')
      expect(fields).toHaveLength(2)
    })

    it('should filter out non-existent fields from searchableFields', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['name', 'nonexistent', 'amount']
        }
      })

      const fields = wrapper.findAll('.search-field-item')
      expect(fields).toHaveLength(2)
    })

    it('should render field labels correctly', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['name', 'amount']
        }
      })

      const labels = wrapper.findAll('.field-label')
      expect(labels[0].text()).toBe('名称')
      expect(labels[1].text()).toBe('金额')
    })
  })

  describe('Layout', () => {
    it('should render horizontal layout by default', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      expect(wrapper.find('.layout-horizontal').exists()).toBe(true)
      expect(wrapper.find('.layout-vertical').exists()).toBe(false)
    })

    it('should render vertical layout when specified', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          layout: 'vertical'
        }
      })

      expect(wrapper.find('.layout-vertical').exists()).toBe(true)
      expect(wrapper.find('.layout-horizontal').exists()).toBe(false)
    })
  })

  describe('Actions', () => {
    it('should show action buttons by default', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      expect(wrapper.find('.search-actions').exists()).toBe(true)
      expect(wrapper.findAll('.btn')).toHaveLength(2)
    })

    it('should hide action buttons when showActions is false', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          showActions: false
        }
      })

      expect(wrapper.find('.search-actions').exists()).toBe(false)
    })

    it('should emit search event when search button clicked', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      const searchBtn = wrapper.find('.btn-primary')
      await searchBtn.trigger('click')

      expect(wrapper.emitted('search')).toBeTruthy()
      expect(wrapper.emitted('search')).toHaveLength(1)
    })

    it('should emit reset event when reset button clicked', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      const resetBtn = wrapper.find('.btn-default')
      await resetBtn.trigger('click')

      expect(wrapper.emitted('reset')).toBeTruthy()
      expect(wrapper.emitted('reset')).toHaveLength(1)
    })

    it('should clear filters when reset clicked', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: [{ field: 'name', op: '=', value: 'test' }]
        }
      })

      const resetBtn = wrapper.find('.btn-default')
      await resetBtn.trigger('click')

      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted![emitted!.length - 1]).toEqual([[]])
    })
  })

  describe('Filter Updates', () => {
    it('should emit update:modelValue when filter changes', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      // 模拟内部更新过滤器
      const vm = wrapper.vm as any
      vm.updateFilter('name', [{ field: 'name', op: '=', value: 'test' }])

      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    })

    it('should merge multiple filter fields', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      const vm = wrapper.vm as any
      vm.updateFilter('name', [{ field: 'name', op: '=', value: 'test' }])
      vm.updateFilter('amount', [{ field: 'amount', op: '>=', value: 100 }])

      const emitted = wrapper.emitted('update:modelValue')
      const lastEmit = emitted![emitted!.length - 1][0] as SliceRequestDef[]

      expect(lastEmit).toHaveLength(2)
      expect(lastEmit.find(s => s.field === 'name')).toBeTruthy()
      expect(lastEmit.find(s => s.field === 'amount')).toBeTruthy()
    })

    it('should remove filter when value is null', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: [
            { field: 'name', op: '=', value: 'test' },
            { field: 'amount', op: '>=', value: 100 }
          ]
        }
      })

      const vm = wrapper.vm as any
      vm.updateFilter('name', null)

      const emitted = wrapper.emitted('update:modelValue')
      const lastEmit = emitted![emitted!.length - 1][0] as SliceRequestDef[]

      expect(lastEmit).toHaveLength(1)
      expect(lastEmit[0].field).toBe('amount')
    })

    it('should emit search event after filter update when search button clicked', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      const vm = wrapper.vm as any
      vm.updateFilter('name', [{ field: 'name', op: '=', value: 'test' }])

      const searchBtn = wrapper.find('.btn-primary')
      await searchBtn.trigger('click')

      expect(wrapper.emitted('search')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    })
  })

  describe('ModelValue Sync', () => {
    it('should initialize with modelValue prop', () => {
      const initialSlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'test' }
      ]

      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: initialSlices
        }
      })

      const vm = wrapper.vm as any
      expect(vm.filterValues).toBeDefined()
      expect(vm.filterValues.name).toEqual(initialSlices)
    })

    it('should update when modelValue prop changes', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: []
        }
      })

      const newSlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'updated' }
      ]

      await wrapper.setProps({ modelValue: newSlices })

      const vm = wrapper.vm as any
      expect(vm.filterValues.name).toEqual(newSlices)
    })

    it('should clear internal state when modelValue is empty', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: [{ field: 'name', op: '=', value: 'test' }]
        }
      })

      await wrapper.setProps({ modelValue: [] })

      const vm = wrapper.vm as any
      expect(Object.keys(vm.filterValues)).toHaveLength(0)
    })
  })

  describe('Exposed Methods', () => {
    it('should expose clearFilters method', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: [{ field: 'name', op: '=', value: 'test' }]
        }
      })

      const vm = wrapper.vm as any
      expect(vm.clearFilters).toBeDefined()

      vm.clearFilters()

      const emitted = wrapper.emitted('update:modelValue')
      const lastEmit = emitted![emitted!.length - 1][0] as SliceRequestDef[]
      expect(lastEmit).toEqual([])
    })

    it('should expose getFilters method', () => {
      const slices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'test' },
        { field: 'amount', op: '>=', value: 100 }
      ]

      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: slices
        }
      })

      const vm = wrapper.vm as any
      expect(vm.getFilters).toBeDefined()

      const filters = vm.getFilters()
      expect(filters).toHaveLength(2)
      expect(filters).toEqual(expect.arrayContaining(slices))
    })
  })

  describe('Filter Component Rendering', () => {
    it('should render correct filter component for text type', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['name']
        }
      })

      expect(wrapper.find('.text-filter').exists()).toBe(true)
    })

    it('should render correct filter component for number type', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['amount']
        }
      })

      expect(wrapper.find('.number-filter').exists()).toBe(true)
    })

    it('should render correct filter component for date type', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['date']
        }
      })

      expect(wrapper.find('.date-filter').exists()).toBe(true)
    })

    it('should render correct filter component for bool type', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['status']
        }
      })

      expect(wrapper.find('.bool-filter').exists()).toBe(true)
    })
  })

  describe('Integration', () => {
    it('should work in complete workflow', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: ['name', 'amount']
        }
      })

      const vm = wrapper.vm as any

      // 1. 初始状态
      expect(vm.getFilters()).toEqual([])

      // 2. 更新第一个过滤器
      vm.updateFilter('name', [{ field: 'name', op: '=', value: 'test' }])
      expect(vm.getFilters()).toHaveLength(1)

      // 3. 更新第二个过滤器
      vm.updateFilter('amount', [{ field: 'amount', op: '>=', value: 100 }])
      expect(vm.getFilters()).toHaveLength(2)

      // 4. 点击搜索按钮
      const searchBtn = wrapper.find('.btn-primary')
      await searchBtn.trigger('click')
      expect(wrapper.emitted('search')).toBeTruthy()

      // 5. 点击重置按钮
      const resetBtn = wrapper.find('.btn-default')
      await resetBtn.trigger('click')
      expect(wrapper.emitted('reset')).toBeTruthy()

      const emitted = wrapper.emitted('update:modelValue')
      const lastEmit = emitted![emitted!.length - 1][0] as SliceRequestDef[]
      expect(lastEmit).toEqual([])
    })

    it('should handle rapid filter changes', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns
        }
      })

      const vm = wrapper.vm as any

      // 快速连续更新
      for (let i = 1; i <= 5; i++) {
        vm.updateFilter('name', [{ field: 'name', op: '=', value: `test${i}` }])
      }

      const filters = vm.getFilters()
      expect(filters).toHaveLength(1)
      expect(filters[0].value).toBe('test5')
    })

    it('should maintain other filters when one is cleared', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: [
            { field: 'name', op: '=', value: 'test' },
            { field: 'amount', op: '>=', value: 100 }
          ]
        }
      })

      const vm = wrapper.vm as any
      vm.updateFilter('name', null)

      const filters = vm.getFilters()
      expect(filters).toHaveLength(1)
      expect(filters[0].field).toBe('amount')
    })
  })

  describe('Edge Cases', () => {
    it('should handle empty columns array', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: []
        }
      })

      expect(wrapper.find('.search-fields').exists()).toBe(true)
      expect(wrapper.findAll('.search-field-item')).toHaveLength(0)
    })

    it('should handle columns without filterable flag', () => {
      const columnsWithoutFilterable: EnhancedColumnSchema[] = [
        { name: 'id', type: 'INTEGER', title: 'ID' }
      ]

      const wrapper = mount(SearchToolbar, {
        props: {
          columns: columnsWithoutFilterable
        }
      })

      // 没有 filterable: false 的列应该被视为可筛选
      expect(wrapper.findAll('.search-field-item')).toHaveLength(1)
    })

    it('should handle searchableFields with empty array', () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          searchableFields: []
        }
      })

      // 空数组时，length === 0，不进行过滤，显示所有可筛选列
      expect(wrapper.findAll('.search-field-item')).toHaveLength(5)
    })

    it('should handle null/undefined in modelValue gracefully', async () => {
      const wrapper = mount(SearchToolbar, {
        props: {
          columns: mockColumns,
          modelValue: undefined
        }
      })

      const vm = wrapper.vm as any
      expect(vm.getFilters()).toEqual([])

      await wrapper.setProps({ modelValue: null as any })
      expect(vm.getFilters()).toEqual([])
    })
  })
})
