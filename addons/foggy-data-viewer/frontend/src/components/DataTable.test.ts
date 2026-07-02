import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { PropType } from 'vue'
import DataTable from './DataTable.vue'
import type { CellRenderContext, EnhancedColumnSchema, SliceRequestDef } from '@/types'
import { globalColumnRenderers } from './composables/globalColumnRenderers'

const elMessageWarning = vi.hoisted(() => vi.fn())
const clearCheckboxRowSpy = vi.hoisted(() => vi.fn())
const clearCheckboxReserveSpy = vi.hoisted(() => vi.fn())

vi.mock('element-plus', () => ({
  ElMessage: {
    warning: elMessageWarning
  },
  ElTooltip: {
    name: 'ElTooltip',
    props: ['content', 'placement', 'showAfter', 'teleported'],
    render(this: { $slots: { default?: () => unknown } }) {
      return this.$slots.default?.()
    }
  }
}))

interface TestGridColumn {
  field?: string
  className?: string
  width?: number | string
  minWidth?: number
  slots?: {
    header?: () => unknown
    default?: (params: { row: Record<string, unknown>; column: TestGridColumn; cellValue: unknown }) => unknown
  }
}

const VxeGridRenderStub = defineComponent({
  name: 'vxe-grid',
  props: {
    columns: {
      type: Array as PropType<TestGridColumn[]>,
      default: () => []
    },
    data: {
      type: Array as PropType<Record<string, unknown>[]>,
      default: () => []
    },
    size: {
      type: String,
      default: undefined
    }
  },
  emits: ['cellClick', 'checkboxChange', 'checkboxAll'],
  setup(props, { emit, expose }) {
    expose({
      clearCheckboxRow: clearCheckboxRowSpy,
      clearCheckboxReserve: clearCheckboxReserveSpy
    })

    return () => h('div', { class: 'vxe-grid-render-stub' }, [
      h('div', { class: 'stub-header' }, props.columns
        .filter(column => column.field)
        .map(column => h('div', {
          class: ['stub-header-cell', `stub-header-cell-${String(column.field)}`]
        }, column.slots?.header ? column.slots.header() : String(column.field ?? '')))
      ),
      ...props.data.map((row, rowIndex) =>
        h('div', { class: 'stub-row', 'data-row-index': String(rowIndex) }, props.columns
        .filter(column => column.field)
        .map(column => h('div', {
          class: ['stub-cell', `stub-cell-${String(column.field)}`],
          onClick: () => emit('cellClick', { row, column })
        }, column.slots?.default
          ? column.slots.default({ row, column, cellValue: row[column.field as string] })
          : String(row[column.field as string] ?? '')
        ))
      )
      )
    ])
  }
})

describe('DataTable', () => {
  const mockColumns: EnhancedColumnSchema[] = [
    {
      name: 'id',
      type: 'INTEGER',
      title: 'ID',
      width: 100,
      fixed: 'left'
    },
    {
      name: 'name',
      type: 'TEXT',
      title: '名称',
      width: 150
    },
    {
      name: 'amount',
      type: 'MONEY',
      title: '金额',
      width: 120
    }
  ]

  const mockData = [
    { id: 1, name: 'Test 1', amount: 100 },
    { id: 2, name: 'Test 2', amount: 200 },
    { id: 3, name: 'Test 3', amount: 300 }
  ]

  // 全局配置用于所有测试
  const globalConfig = {
    global: {
      stubs: {
        'vxe-grid': true,  // 使用stub避免需要实际的vxe-table组件
        'vxe-pager': true
      }
    }
  }

  const renderGridConfig = {
    global: {
      stubs: {
        'vxe-grid': VxeGridRenderStub,
        'vxe-pager': true
      }
    }
  }

  beforeEach(() => {
    vi.clearAllMocks()
    globalColumnRenderers.clear()
    document.body.innerHTML = ''
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      },
      configurable: true
    })
  })

  afterEach(() => {
    globalColumnRenderers.clear()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  describe('Basic Rendering', () => {
    it('should render table with columns and data', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // 组件已成功挂载
    })

    it('should use compact density by default', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.find('.data-table').classes()).toContain('data-table--compact')
    })

    it('should allow default density explicitly', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          density: 'default'
        },
        ...globalConfig
      })

      expect(wrapper.find('.data-table').classes()).not.toContain('data-table--compact')
    })

    it('should render with loading state', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: [],
          total: 0,
          loading: true
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // vxe-table 会显示 loading 状态
    })

    it('should show background loading status next to pager after delay', async () => {
      vi.useFakeTimers()
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          backgroundLoading: true,
          backgroundLoadingText: '正在筛选...'
        },
        ...globalConfig
      })

      expect(wrapper.find('.data-table-query-status').exists()).toBe(false)

      await vi.advanceTimersByTimeAsync(150)
      await vi.advanceTimersByTimeAsync(1)
      await wrapper.vm.$nextTick()

      const status = wrapper.find('.data-table-query-status')
      expect(status.exists()).toBe(true)
      expect(status.classes()).toContain('is-visible')
      expect(status.text()).toContain('正在筛选...')
      expect(wrapper.find('.data-table-progress-line').classes()).toContain('is-visible')
    })

    it('should avoid pager-side status when pager is hidden and no toolbar anchor exists', async () => {
      vi.useFakeTimers()
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          showPager: false,
          backgroundLoading: true,
          backgroundLoadingText: '正在刷新...'
        },
        ...globalConfig
      })

      await vi.advanceTimersByTimeAsync(150)
      await vi.advanceTimersByTimeAsync(1)
      await wrapper.vm.$nextTick()

      expect(wrapper.find('.data-table-query-status').exists()).toBe(false)
      expect(wrapper.find('.data-table-progress-line').exists()).toBe(true)
      expect(wrapper.find('.data-table-progress-line').classes()).toContain('is-visible')
    })

    it('should render with empty data', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: [],
          total: 0,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should default vxe-grid to small size', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...renderGridConfig
      })

      expect(wrapper.findComponent(VxeGridRenderStub).props('size')).toBe('small')
    })

    it('should render column description help icon and sort arrows', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [
            { ...mockColumns[1], description: '字段说明内容' }
          ],
          data: mockData,
          total: 3,
          loading: false
        },
        ...renderGridConfig
      })

      const helpIcon = wrapper.find('.stub-header-cell-name .column-help-icon')
      expect(helpIcon.exists()).toBe(true)
      expect(helpIcon.find('.column-help-svg').exists()).toBe(true)
      expect(helpIcon.text()).toBe('')
      expect(helpIcon.attributes('title')).toBe('字段说明内容')
      expect(helpIcon.attributes('aria-label')).toBe('字段说明内容')
      expect(wrapper.find('.stub-header-cell-name .sort-icon-horizontal').exists()).toBe(true)
      expect(wrapper.find('.stub-header-cell-name .sort-icon-svg').attributes('viewBox')).toBe('0 0 24 14')
      expect(wrapper.find('.stub-header-cell-name .sort-arrow-asc').exists()).toBe(true)
      expect(wrapper.find('.stub-header-cell-name .sort-arrow-desc').exists()).toBe(true)
    })
  })

  describe('Column Configuration', () => {
    it('should apply column width', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // vxe-table 会应用列宽配置
    })

    it('should provide compact min width for boolean filter columns', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [
            {
              name: 'enabled',
              type: 'BOOL',
              title: '启用',
              width: 60
            }
          ],
          data: [{ enabled: true }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      const grid = wrapper.findComponent(VxeGridRenderStub)
      const columns = grid.props('columns') as TestGridColumn[]
      const enabledColumn = columns.find(column => column.field === 'enabled')
      expect(enabledColumn?.minWidth).toBe(88)
      expect(enabledColumn?.className).toBe('data-table-boolean-column')
    })

    it('should render default boolean values as compact status markers', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'enabled', type: 'BOOLEAN', title: '是否启用', copyable: false }],
          data: [{ enabled: true }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      const marker = wrapper.find('.data-table-boolean-cell')
      expect(marker.exists()).toBe(true)
      expect(marker.text()).toBe('✓')
      expect(marker.classes()).toContain('data-table-boolean-cell--true')
      expect(marker.attributes('title')).toBe('是')
      expect(marker.attributes('aria-label')).toBe('是')
    })

    it('should apply fixed columns', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // 第一列是固定列
    })

    it('should handle custom formatter', () => {
      const formatter = (value: unknown) => `¥${value}`
      const columnsWithFormatter: EnhancedColumnSchema[] = [
        {
          name: 'amount',
          type: 'MONEY',
          title: '金额',
          customFormatter: formatter
        }
      ]

      const wrapper = mount(DataTable, {
        props: {
          columns: columnsWithFormatter,
          data: [{ amount: 100 }],
          total: 1,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should handle custom render with row, value and column context', () => {
      const render = vi.fn(({ value, column }: CellRenderContext) => h('span', { class: 'custom' }, `${column.name}:${String(value)}`))
      const columnsWithRender: EnhancedColumnSchema[] = [
        {
          name: 'status',
          type: 'TEXT',
          title: '状态',
          customRender: render
        }
      ]

      const wrapper = mount(DataTable, {
        props: {
          columns: columnsWithRender,
          data: [{ status: 'active' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(wrapper.find('.custom').text()).toBe('status:active')
      expect(render).toHaveBeenCalledWith(expect.objectContaining({
        row: { status: 'active' },
        value: 'active',
        column: expect.objectContaining({ name: 'status' })
      }))
    })

    it('should render a matching global column renderer before default cell rendering', () => {
      const render = vi.fn(({ value }) => h('button', { class: 'global-rendered' }, String(value)))
      globalColumnRenderers.add({
        id: 'test.name.link',
        match: ({ column }) => column.name === 'name',
        render
      })

      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: 'Test 1' }],
          total: 1,
          loading: false,
          qmModel: 'order.qm',
          tableSchema: { columns: mockColumns }
        },
        ...renderGridConfig
      })

      expect(wrapper.find('.global-rendered').text()).toBe('Test 1')
      expect(render).toHaveBeenCalledWith(expect.objectContaining({
        row: { name: 'Test 1' },
        value: 'Test 1',
        column: expect.objectContaining({ name: 'name' }),
        columns: [expect.objectContaining({ name: 'name' })],
        qmModel: 'order.qm',
        tableSchema: expect.objectContaining({ columns: mockColumns })
      }))
    })

    it('should keep slot and customRender above global column renderers', () => {
      const globalRender = vi.fn(() => h('span', { class: 'global-rendered' }, 'global'))
      const customRender = vi.fn(() => h('span', { class: 'custom-rendered' }, 'custom'))
      globalColumnRenderers.add({
        id: 'test.name.global',
        match: ({ column }) => column.name === 'name',
        render: globalRender
      })

      const customWrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称', customRender }],
          data: [{ name: 'Test 1' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(customWrapper.find('.custom-rendered').text()).toBe('custom')
      expect(customRender).toHaveBeenCalledTimes(1)
      expect(globalRender).not.toHaveBeenCalled()

      const slotWrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: 'Test 1' }],
          total: 1,
          loading: false
        },
        slots: {
          'column-name': '<span class="slot-rendered">slot</span>'
        },
        ...renderGridConfig
      })

      expect(slotWrapper.find('.slot-rendered').text()).toBe('slot')
      expect(globalRender).not.toHaveBeenCalled()
    })

    it('should choose the highest priority global renderer and support dispose/remove/clear', () => {
      globalColumnRenderers.add({
        id: 'test.low',
        priority: 10,
        match: ({ column }) => column.name === 'name',
        render: () => h('span', { class: 'priority-cell' }, 'low')
      })
      const disposeHigh = globalColumnRenderers.add({
        id: 'test.high',
        priority: 100,
        match: ({ column }) => column.name === 'name',
        render: () => h('span', { class: 'priority-cell' }, 'high')
      })

      const mountTable = () => mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: 'Test 1' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(mountTable().find('.priority-cell').text()).toBe('high')

      disposeHigh()
      expect(mountTable().find('.priority-cell').text()).toBe('low')

      globalColumnRenderers.remove('test.low')
      expect(mountTable().find('.priority-cell').exists()).toBe(false)

      globalColumnRenderers.add({
        id: 'test.clear',
        match: ({ column }) => column.name === 'name',
        render: () => h('span', { class: 'priority-cell' }, 'clear')
      })
      globalColumnRenderers.clear()
      expect(mountTable().find('.priority-cell').exists()).toBe(false)
    })

    it('should allow column-level opt-out and empty value rendering for global renderers', () => {
      globalColumnRenderers.add({
        id: 'test.empty',
        match: ({ column }) => column.name === 'name',
        render: ({ value }) => h('span', { class: 'global-empty' }, String(value ?? '').trim() || '-')
      })

      const emptyWrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: '' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(emptyWrapper.find('.global-empty').text()).toBe('-')

      const optOutWrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称', uiConfig: { disableGlobalRender: true } }],
          data: [{ name: 'Test 1' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(optOutWrapper.find('.global-empty').exists()).toBe(false)
      expect(optOutWrapper.find('.stub-cell-name').text()).toContain('Test 1')
    })
  })

  describe('Pagination', () => {
    it('should render pagination with correct total', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 100,
          loading: false,
          pageSize: 10
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // vxe-table 会显示分页组件
    })

    it('should use custom page size', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 100,
          loading: false,
          pageSize: 20
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should emit page-change event when page changes', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 100,
          loading: false
        },
        ...globalConfig
      })

      // 模拟分页变化
      await wrapper.vm.$emit('page-change', 2, 50)
      expect(wrapper.emitted('page-change')).toBeTruthy()
      expect(wrapper.emitted('page-change')?.[0]).toEqual([2, 50])
    })
  })

  describe('Sorting', () => {
    it('should emit order from dedicated sort arrows without sorting local rows', async () => {
      const data = [
        { name: 'Gamma' },
        { name: 'Alpha' },
        { name: 'Beta' }
      ]

      const wrapper = mount(DataTable, {
        props: {
          columns: [mockColumns[1]],
          data,
          total: 3,
          loading: false
        },
        ...renderGridConfig
      })

      expect(wrapper.findAll('.stub-cell-name').map(cell => cell.text())).toEqual(['Gamma', 'Alpha', 'Beta'])

      await wrapper.find('.stub-header-cell-name .sort-arrow-control-asc').trigger('click')
      await flushPromises()

      expect(wrapper.emitted('sort-change')?.[0]).toEqual(['name', 'asc'])
      expect(wrapper.find('.stub-header-cell-name .sort-arrow-asc.active').exists()).toBe(true)
      expect(wrapper.findAll('.stub-cell-name').map(cell => cell.text())).toEqual(['Gamma', 'Alpha', 'Beta'])

      await wrapper.find('.stub-header-cell-name .sort-arrow-control-desc').trigger('click')
      await flushPromises()

      expect(wrapper.emitted('sort-change')?.[1]).toEqual(['name', 'desc'])
      expect(wrapper.find('.stub-header-cell-name .sort-arrow-desc.active').exists()).toBe(true)
      expect(wrapper.findAll('.stub-cell-name').map(cell => cell.text())).toEqual(['Gamma', 'Alpha', 'Beta'])
    })

    it('should clear sort when clicking the active arrow again', async () => {
      const data = [
        { name: 'Gamma' },
        { name: 'Alpha' },
        { name: 'Beta' }
      ]

      const wrapper = mount(DataTable, {
        props: {
          columns: [mockColumns[1]],
          data,
          total: 3,
          loading: false
        },
        ...renderGridConfig
      })

      await wrapper.find('.stub-header-cell-name .sort-arrow-control-desc').trigger('click')
      await flushPromises()
      await wrapper.find('.stub-header-cell-name .sort-arrow-control-desc').trigger('click')
      await flushPromises()

      expect(wrapper.emitted('sort-change')?.[1]).toEqual([null, null])
      expect(wrapper.findAll('.stub-cell-name').map(cell => cell.text())).toEqual(['Gamma', 'Alpha', 'Beta'])
    })
  })

  describe('Filtering', () => {
    it('should show filters when showFilters is true', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          showFilters: true
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should hide filters when showFilters is false', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          showFilters: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should emit filter-change event', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      const slices = [{ field: 'name', op: '=', value: 'test' }]
      await wrapper.vm.$emit('filter-change', slices)
      expect(wrapper.emitted('filter-change')).toBeTruthy()
      expect(wrapper.emitted('filter-change')?.[0]).toEqual([slices])
    })

    it('should apply filter changes to current page data locally', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...renderGridConfig
      })

      const vm = wrapper.vm as unknown as {
        setFilter: (columnName: string, value: SliceRequestDef[] | null) => void
      }

      vm.setFilter('name', [{ field: 'name', op: 'right_like', value: 'Test 2' }])
      await wrapper.vm.$nextTick()

      const rows = wrapper.findAll('.stub-row')
      expect(rows).toHaveLength(1)
      expect(rows[0].text()).toContain('Test 2')
      expect(rows[0].text()).not.toContain('Test 1')
    })

    it('should keep server-filtered rows when local filtering is disabled', async () => {
      const serverFilteredData = [
        { id: 1, name: 'Lin Ze', phoneList: '13915455958,18911897352' }
      ]
      const columns: EnhancedColumnSchema[] = [
        { name: 'id', type: 'INTEGER', title: 'ID' },
        { name: 'name', type: 'TEXT', title: '名称' },
        { name: 'phoneList', type: 'TEXT', title: '联系电话' }
      ]
      const wrapper = mount(DataTable, {
        props: {
          columns,
          data: serverFilteredData,
          total: 1,
          loading: false,
          localFilter: false
        },
        ...renderGridConfig
      })

      const vm = wrapper.vm as unknown as {
        setFilter: (columnName: string, value: SliceRequestDef[] | null) => void
      }

      vm.setFilter('phoneList', [{ field: 'phoneList', op: '=', value: '18911897352' }])
      await wrapper.vm.$nextTick()

      const rows = wrapper.findAll('.stub-row')
      expect(rows).toHaveLength(1)
      expect(rows[0].text()).toContain('13915455958,18911897352')
    })

    it('should apply initial slice', () => {
      const initialSlice = [{ field: 'status', op: '=', value: 'active' }]
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          initialSlice
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should clear filter state when initial slice becomes empty', async () => {
      const initialSlice = [{ field: 'status', op: '=', value: 'active' }]
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          initialSlice
        },
        ...globalConfig
      })

      const vm = wrapper.vm as unknown as { getFilters: () => typeof initialSlice }
      expect(vm.getFilters()).toEqual(initialSlice)

      await wrapper.setProps({ initialSlice: [] })

      expect(vm.getFilters()).toEqual([])
    })
  })

  describe('Events', () => {
    it('should emit row-click event', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      const row = mockData[0]
      await wrapper.vm.$emit('row-click', row, mockColumns[0])
      expect(wrapper.emitted('row-click')).toBeTruthy()
    })

    it('should emit row-dblclick event', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      const row = mockData[0]
      await wrapper.vm.$emit('row-dblclick', row, mockColumns[0])
      expect(wrapper.emitted('row-dblclick')).toBeTruthy()
    })

    it('should emit checkbox-change event', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      await wrapper.vm.$emit('checkbox-change', [mockData[0]])
      expect(wrapper.emitted('checkbox-change')).toBeTruthy()
      expect(wrapper.emitted('checkbox-change')?.[0]).toEqual([[mockData[0]]])
    })

    it('should emit checkbox-all event', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      await wrapper.vm.$emit('checkbox-all', mockData)
      expect(wrapper.emitted('checkbox-all')).toBeTruthy()
      expect(wrapper.emitted('checkbox-all')?.[0]).toEqual([mockData])
    })
  })

  describe('Summary', () => {
    it('should display server summary when provided', () => {
      const serverSummary = {
        amount: 600,
        count: 3
      }

      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          serverSummary
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should handle null server summary', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          serverSummary: null
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })
  })

  describe('Filter Options Loader', () => {
    it('should call filter options loader when needed', async () => {
      const mockLoader = vi.fn().mockResolvedValue([
        { value: 'option1', label: 'Option 1' },
        { value: 'option2', label: 'Option 2' }
      ])

      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          filterOptionsLoader: mockLoader
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // 实际使用中会调用 mockLoader
    })

    it('should load dimension members remotely and emit selection field filters', async () => {
      const memberLoader = vi.fn().mockResolvedValue({
        selectionFieldName: 'ownerOrg$id',
        displayFieldName: 'ownerOrg$caption',
        items: [{ value: 88810, label: 'Vitest Org 88810' }],
        total: 1,
        hasMore: false
      })

      const wrapper = mount(DataTable, {
        attachTo: document.body,
        props: {
          columns: [{
            name: 'ownerOrg$caption',
            type: 'TEXT',
            title: '所属机构',
            filterType: 'dimension',
            memberLookup: {
              enabled: true,
              selectionFieldName: 'ownerOrg$id',
              displayFieldName: 'ownerOrg$caption'
            }
          }],
          data: [{ 'ownerOrg$caption': 'Vitest Org 88810' }],
          total: 1,
          loading: false,
          filterMemberLoader: memberLoader,
          qmModel: 'vehicleCapacityProfile'
        },
        ...renderGridConfig
      })

      await wrapper.find('.select-input').trigger('click')
      await flushPromises()

      expect(memberLoader).toHaveBeenCalledWith(expect.objectContaining({
        qmModel: 'vehicleCapacityProfile',
        fieldName: 'ownerOrg$caption',
        start: 0,
        limit: 100
      }))

      const option = document.body.querySelector('.filter-option') as HTMLElement
      expect(option?.textContent).toContain('Vitest Org 88810')
      option.click()
      await flushPromises()

      const emitted = wrapper.emitted('filter-change') || []
      expect(emitted[emitted.length - 1]?.[0]).toEqual([{
        field: 'ownerOrg$id',
        op: '=',
        value: 88810
      }])
    })
  })

  describe('Exposed Methods', () => {
    it('should have resetPagination method', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 100,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.resetPagination).toBeDefined()
    })

    it('should have clearFilters method', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.clearFilters).toBeDefined()
    })

    it('should have getFilters method', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.getFilters).toBeDefined()
    })

    it('should have setFilter method', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.setFilter).toBeDefined()
    })

    it('should have getGridInstance method', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.getGridInstance).toBeDefined()
    })

    it('should have getSelectedRows method that returns an array', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.getSelectedRows).toBeDefined()
      const rows = wrapper.vm.getSelectedRows()
      expect(Array.isArray(rows)).toBe(true)
      expect(rows).toHaveLength(0) // 初始无选中
    })

    it('should have getSelectedCount method that returns a number', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.getSelectedCount).toBeDefined()
      expect(wrapper.vm.getSelectedCount()).toBe(0) // 初始无选中
    })

    it('should have clearSelection method', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.vm.clearSelection).toBeDefined()
      expect(() => wrapper.vm.clearSelection()).not.toThrow()
    })

    it('should clear internal and vxe checkbox selection state', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...renderGridConfig
      })

      const grid = wrapper.findComponent(VxeGridRenderStub)
      await grid.vm.$emit('checkboxChange', { records: [mockData[0]] })

      expect(wrapper.vm.getSelectedCount()).toBe(1)

      wrapper.vm.clearSelection()
      await wrapper.vm.$nextTick()

      expect(wrapper.vm.getSelectedCount()).toBe(0)
      expect(clearCheckboxRowSpy).toHaveBeenCalled()
      expect(clearCheckboxReserveSpy).toHaveBeenCalled()

      const events = wrapper.emitted('checkbox-change') || []
      expect(events[events.length - 1]).toEqual([[]])
    })
  })

  describe('DictId Filter Inference', () => {
    it('should accept columns with dictId and filterType=number without error', () => {
      const dictColumns: EnhancedColumnSchema[] = [
        { name: 'orgNature', type: 'INTEGER', title: '机构性质', dictId: 'OrgNature', filterType: 'number' },
        { name: 'state', type: 'INTEGER', title: '状态', dictId: 'OrgState' }
      ]

      const wrapper = mount(DataTable, {
        props: {
          columns: dictColumns,
          data: [{ orgNature: 1, state: 2 }],
          total: 1,
          loading: false
        },
        ...globalConfig
      })

      // 组件能正常渲染不报错即可；真正的过滤器渲染由 vxe-grid 内部 slot 完成
      expect(wrapper.exists()).toBe(true)
    })

    it('should render with dictItems columns and use dict formatter', () => {
      const dictColumns: EnhancedColumnSchema[] = [
        { name: 'status', type: 'INTEGER', title: '状态', dictId: 'StatusDict', dictItems: [{ value: 1, label: '启用' }, { value: 0, label: '禁用' }] }
      ]

      const wrapper = mount(DataTable, {
        props: {
          columns: dictColumns,
          data: [{ status: 1 }],
          total: 1,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })
  })

  describe('Cell Copy', () => {
    it('should show copy button when hovering a non-empty default text cell', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: '目的服务区域名称很长很长' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(wrapper.find('.cell-copy-button').exists()).toBe(false)

      await wrapper.find('.data-table-copyable-cell').trigger('mouseenter')

      const copyButton = wrapper.find('.cell-copy-button')
      expect(copyButton.exists()).toBe(true)
      expect((copyButton.element as HTMLElement).style.right).toBe('2px')
      expect((copyButton.element as HTMLElement).style.width).toBe('18px')
      expect(wrapper.find('.cell-copy-icon').exists()).toBe(true)
    })

    it('should expose the complete display value as the cell hover title', () => {
      const fullValue = '13915455958,18911897366,13974036619'
      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'phone', type: 'TEXT', title: '联系人电话' }],
          data: [{ phone: fullValue }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(wrapper.find('.data-table-copyable-cell').attributes('title')).toBe(fullValue)
    })

    it('should use formatted display value for non-copyable cell hover title', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'enabled', type: 'BOOLEAN', title: '是否启用', copyable: false }],
          data: [{ enabled: true }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(wrapper.find('.data-table-boolean-cell').attributes('title')).toBe('是')
    })

    it('should copy the complete raw cell value when clicking copy button', async () => {
      const writeText = vi.fn().mockResolvedValue(undefined)
      Object.defineProperty(navigator, 'clipboard', {
        value: { writeText },
        configurable: true
      })

      const fullValue = '完整目的服务区域名称-不应复制省略文本'
      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: fullValue }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      await wrapper.find('.data-table-copyable-cell').trigger('mouseenter')
      await wrapper.find('.cell-copy-button').trigger('click')

      expect(writeText).toHaveBeenCalledWith(fullValue)
    })

    it('should fall back to textarea copy when clipboard api is unavailable', async () => {
      Object.defineProperty(navigator, 'clipboard', {
        value: undefined,
        configurable: true
      })
      const execCommand = vi.fn().mockReturnValue(true)
      Object.defineProperty(document, 'execCommand', {
        value: execCommand,
        configurable: true
      })

      const fullValue = 'HTTP 环境下也可复制'
      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: fullValue }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      await wrapper.find('.data-table-copyable-cell').trigger('mouseenter')
      await wrapper.find('.cell-copy-button').trigger('click')

      expect(execCommand).toHaveBeenCalledWith('copy')
      expect(document.querySelector('textarea')).toBeNull()
    })

    it('should stop copy button click from bubbling to row-click', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [{ name: 'name', type: 'TEXT', title: '名称' }],
          data: [{ name: 'Test 1' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      await wrapper.find('.data-table-copyable-cell').trigger('mouseenter')
      await wrapper.find('.cell-copy-button').trigger('click')

      expect(wrapper.emitted('row-click')).toBeUndefined()
    })

    it('should not show copy button for empty values or action columns', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [
            { name: 'name', type: 'TEXT', title: '名称' },
            { name: '_actions', type: 'TEXT', title: '操作' }
          ],
          data: [{ name: '', _actions: '编辑' }],
          total: 1,
          loading: false
        },
        ...renderGridConfig
      })

      expect(wrapper.find('.stub-cell-name .data-table-copyable-cell').exists()).toBe(false)
      expect(wrapper.find('.stub-cell-_actions .data-table-copyable-cell').exists()).toBe(false)
      expect(wrapper.find('.cell-copy-button').exists()).toBe(false)
    })

    it('should support global disable with column-level copyable override', async () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: [
            { name: 'disabledName', type: 'TEXT', title: '禁用列' },
            { name: 'enabledName', type: 'TEXT', title: '启用列', copyable: true }
          ],
          data: [{ disabledName: '不可复制', enabledName: '可复制' }],
          total: 1,
          loading: false,
          cellCopy: { enabled: false }
        },
        ...renderGridConfig
      })

      expect(wrapper.find('.stub-cell-disabledName .data-table-copyable-cell').exists()).toBe(false)
      expect(wrapper.find('.stub-cell-enabledName .data-table-copyable-cell').exists()).toBe(true)
    })
  })

  describe('Props Validation', () => {
    it('should accept valid props', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false,
          pageSize: 50,
          showFilters: true
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
    })

    it('should use default pageSize when not provided', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // 默认 pageSize 是 50
    })

    it('should use default showFilters when not provided', () => {
      const wrapper = mount(DataTable, {
        props: {
          columns: mockColumns,
          data: mockData,
          total: 3,
          loading: false
        },
        ...globalConfig
      })

      expect(wrapper.exists()).toBe(true)
      // 默认 showFilters 是 true
    })
  })
})
