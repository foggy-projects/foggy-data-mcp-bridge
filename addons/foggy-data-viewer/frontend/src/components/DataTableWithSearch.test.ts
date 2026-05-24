import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DataTableWithSearch from './DataTableWithSearch.vue'
import type { EnhancedColumnSchema, SliceRequestDef } from '@/types'

// Mock child components
vi.mock('./SearchToolbar.vue', () => ({
  default: {
    name: 'SearchToolbar',
    template: '<div class="search-toolbar-mock"><slot /></div>',
    props: ['columns', 'searchableFields', 'layout', 'showActions', 'modelValue'],
    emits: ['update:modelValue', 'search', 'reset'],
    methods: {
      clearFilters() {
        this.$emit('update:modelValue', [])
      },
      getFilters() {
        return this.modelValue || []
      }
    }
  }
}))

vi.mock('./QueryPanel.vue', () => ({
  default: {
    name: 'QueryPanel',
    template: '<div class="query-panel-mock"><slot /></div>',
    props: ['schema', 'modelValue', 'filterMemberLoader', 'qmModel'],
    emits: ['update:modelValue', 'search', 'reset'],
    data() {
      return {
        pendingSlices: []
      }
    },
    methods: {
      search() {
        this.$emit('update:modelValue', this.pendingSlices)
        this.$emit('search')
      },
      reset() {
        this.pendingSlices = []
        this.$emit('update:modelValue', [])
        this.$emit('reset')
      },
      getSlices() {
        return this.pendingSlices
      }
    }
  }
}))

vi.mock('./DataTable.vue', () => ({
  default: {
    name: 'DataTable',
    template: '<div class="data-table-mock"><slot name="toolbar" /><slot name="footer" /><slot name="empty" /><slot name="column-_actions" :row="{}" :column="{}" :value="null" /><slot /></div>',
    props: ['columns', 'data', 'total', 'loading', 'pageSize', 'showFilters', 'initialSlice', 'serverSummary', 'cellCopy'],
    emits: ['page-change', 'sort-change', 'filter-change', 'row-click', 'row-dblclick', 'checkbox-change', 'checkbox-all'],
    methods: {
      resetPagination() {
        // mock
      },
      clearFilters() {
        this.$emit('filter-change', [])
      },
      getGridInstance() {
        return null
      },
      getSelectedRows() {
        return [{ id: 1, name: 'Test' }]
      },
      getSelectedCount() {
        return 1
      },
      clearSelection() {
        // mock
      }
    }
  }
}))

describe('DataTableWithSearch', () => {
  const mockColumns: EnhancedColumnSchema[] = [
    { name: 'id', type: 'INTEGER', title: 'ID', filterable: true },
    { name: 'name', type: 'TEXT', title: '名称', filterable: true },
    { name: 'amount', type: 'MONEY', title: '金额', filterable: true, measure: true, aggregatable: true }
  ]

  const mockData = [
    { id: 1, name: 'Test 1', amount: 100 },
    { id: 2, name: 'Test 2', amount: 200 }
  ]

  const defaultProps = {
    columns: mockColumns,
    data: mockData,
    total: 100,
    loading: false
  }

  const toolbarProps = {
    ...defaultProps,
    queryMode: 'panel' as const
  }

  const mockQuerySchema = {
    fields: [
      {
        key: 'name',
        label: '名称',
        sourceField: 'name',
        placement: 'form' as const,
        component: 'text' as const
      }
    ],
    submitMode: 'manual' as const
  }

  describe('Rendering', () => {
    it('should render successfully', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.data-table-with-search').exists()).toBe(true)
    })

    it('should not render SearchToolbar without queryMode', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
    })

    it('should render SearchToolbar when queryMode is panel and querySchema is absent', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'panel'
        }
      })

      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(true)
    })

    it('should render legacy QueryPanel only when showQueryPanel is true', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          querySchema: mockQuerySchema,
          showQueryPanel: true
        }
      })

      expect(wrapper.find('.query-panel-mock').exists()).toBe(true)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
    })

    it('should only hide legacy QueryPanel when showQueryPanel is false', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          querySchema: mockQuerySchema,
          showQueryPanel: false,
          showFilters: true
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(false)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
      expect(dataTable.props('showFilters')).toBe(true)
    })

    it('should always render DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      expect(wrapper.find('.data-table-mock').exists()).toBe(true)
    })
  })

  describe('Props Passthrough', () => {
    it('should pass columns to both SearchToolbar and DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: toolbarProps
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      expect(searchToolbar.props('columns')).toEqual(mockColumns)
      expect(dataTable.props('columns')).toEqual(mockColumns)
    })

    it('should pass data to DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('data')).toEqual(mockData)
    })

    it('should pass total to DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          total: 500
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('total')).toBe(500)
    })

    it('should pass loading to DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          loading: true
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('loading')).toBe(true)
    })

    it('should pass pageSize to DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          pageSize: 100
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('pageSize')).toBe(100)
    })

    it('should pass showFilters to DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          showFilters: false
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('showFilters')).toBe(false)
    })

    it('should pass searchableFields to SearchToolbar', () => {
      const searchableFields = ['name', 'amount']

      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          searchableFields
        }
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      expect(searchToolbar.props('searchableFields')).toEqual(searchableFields)
    })

    it('should pass searchLayout to SearchToolbar as layout prop', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          searchLayout: 'vertical'
        }
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      expect(searchToolbar.props('layout')).toBe('vertical')
    })

    it('should pass showSearchActions to SearchToolbar as showActions prop', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          showSearchActions: false
        }
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      expect(searchToolbar.props('showActions')).toBe(false)
    })

    it('should pass serverSummary to DataTable', () => {
      const serverSummary = { total: 100, amount: 30000 }

      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          serverSummary
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('serverSummary')).toEqual(serverSummary)
    })

    it('should pass cellCopy to DataTable', () => {
      const cellCopy = { enabled: false }

      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          cellCopy
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('cellCopy')).toEqual(cellCopy)
    })
  })

  describe('Query Mode', () => {
    it('should keep only column filters when queryMode is column', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'column',
          querySchema: mockQuerySchema
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(false)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
      expect(dataTable.props('showFilters')).toBe(true)
    })

    it('should keep only QueryPanel when queryMode is panel and querySchema exists', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'panel',
          querySchema: mockQuerySchema,
          showFilters: true
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(true)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
      expect(dataTable.props('showFilters')).toBe(false)
    })

    it('should use SearchToolbar as panel entrance when queryMode is panel without querySchema', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'panel',
          showFilters: true
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(false)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(true)
      expect(dataTable.props('showFilters')).toBe(false)
    })

    it('should keep QueryPanel and column filters when queryMode is combined', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'combined',
          querySchema: mockQuerySchema
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(true)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
      expect(dataTable.props('showFilters')).toBe(true)
    })

    it('should keep SearchToolbar and column filters when queryMode is combined without querySchema', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'combined'
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(false)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(true)
      expect(dataTable.props('showFilters')).toBe(true)
    })

    it('should hide all built-in query entrances when queryMode is none', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'none',
          querySchema: mockQuerySchema,
          showFilters: true
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(false)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
      expect(dataTable.props('showFilters')).toBe(false)
    })

    it('should let schema queryMode override prop queryMode and legacy switches', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          schema: {
            columns: mockColumns,
            queryMode: 'none',
            showFilters: true
          },
          fetchData: vi.fn().mockResolvedValue({ items: [], total: 0 }),
          queryMode: 'column',
          showFilters: true
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.find('.query-panel-mock').exists()).toBe(false)
      expect(wrapper.find('.search-toolbar-mock').exists()).toBe(false)
      expect(dataTable.props('showFilters')).toBe(false)
    })

    it('should submit QueryPanel slices in panel mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'panel',
          querySchema: mockQuerySchema
        }
      })

      const queryPanel = wrapper.findComponent({ name: 'QueryPanel' })
      const querySlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'from-panel' }
      ]

      await queryPanel.vm.$emit('update:modelValue', querySlices)
      await queryPanel.vm.$emit('search')

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
      expect(filterChangeEvents![0][0]).toEqual(querySlices)
    })

    it('should submit column slices in column mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'column'
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const tableSlices: SliceRequestDef[] = [
        { field: 'amount', op: '>=', value: 100 }
      ]

      await dataTable.vm.$emit('filter-change', tableSlices)

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
      expect(filterChangeEvents![0][0]).toEqual(tableSlices)
    })

    it('should merge QueryPanel and column slices in combined mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'combined',
          querySchema: mockQuerySchema,
          filterMergeMode: 'merge'
        }
      })

      const queryPanel = wrapper.findComponent({ name: 'QueryPanel' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const querySlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'from-panel' }
      ]
      const tableSlices: SliceRequestDef[] = [
        { field: 'amount', op: '>=', value: 100 }
      ]

      await queryPanel.vm.$emit('update:modelValue', querySlices)
      await queryPanel.vm.$emit('search')
      await dataTable.vm.$emit('filter-change', tableSlices)

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()

      const lastEmit = filterChangeEvents![filterChangeEvents!.length - 1][0] as SliceRequestDef[]
      expect(lastEmit).toHaveLength(2)
      expect(lastEmit.find(s => s.field === 'name')?.value).toBe('from-panel')
      expect(lastEmit.find(s => s.field === 'amount')?.value).toBe(100)
    })
  })

  describe('Events', () => {
    it('should emit page-change event from DataTable', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      await dataTable.vm.$emit('page-change', 2, 50)

      expect(wrapper.emitted('page-change')).toBeTruthy()
      expect(wrapper.emitted('page-change')![0]).toEqual([2, 50])
    })

    it('should emit sort-change event from DataTable', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      await dataTable.vm.$emit('sort-change', 'name', 'asc')

      expect(wrapper.emitted('sort-change')).toBeTruthy()
      expect(wrapper.emitted('sort-change')![0]).toEqual(['name', 'asc'])
    })

    it('should emit row-click event from DataTable', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const mockRow = { id: 1, name: 'Test' }
      const mockColumn = mockColumns[0]

      await dataTable.vm.$emit('row-click', mockRow, mockColumn)

      expect(wrapper.emitted('row-click')).toBeTruthy()
      expect(wrapper.emitted('row-click')![0]).toEqual([mockRow, mockColumn])
    })

    it('should emit row-dblclick event from DataTable', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const mockRow = { id: 1, name: 'Test' }
      const mockColumn = mockColumns[0]

      await dataTable.vm.$emit('row-dblclick', mockRow, mockColumn)

      expect(wrapper.emitted('row-dblclick')).toBeTruthy()
      expect(wrapper.emitted('row-dblclick')![0]).toEqual([mockRow, mockColumn])
    })

    it('should emit checkbox-change event from DataTable', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const selectedRows = [{ id: 1, name: 'Test 1' }]
      await dataTable.vm.$emit('checkbox-change', selectedRows)

      expect(wrapper.emitted('checkbox-change')).toBeTruthy()
      expect(wrapper.emitted('checkbox-change')![0]).toEqual([selectedRows])
    })

    it('should emit checkbox-all event from DataTable', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      await dataTable.vm.$emit('checkbox-all', mockData)

      expect(wrapper.emitted('checkbox-all')).toBeTruthy()
      expect(wrapper.emitted('checkbox-all')![0]).toEqual([mockData])
    })

    it('should emit search event from SearchToolbar', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: toolbarProps
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      await searchToolbar.vm.$emit('search')

      expect(wrapper.emitted('search')).toBeTruthy()
    })

    it('should emit reset event from SearchToolbar', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: toolbarProps
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      await searchToolbar.vm.$emit('reset')

      expect(wrapper.emitted('reset')).toBeTruthy()
    })
  })

  describe('Filter Merging', () => {
    it('should merge search and table filters in merge mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          filterMergeMode: 'merge',
          showSearchActions: false  // 实时筛选模式
        }
      })

      const searchSlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'test' }
      ]

      const tableSlices: SliceRequestDef[] = [
        { field: 'amount', op: '>=', value: 100 }
      ]

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      await searchToolbar.vm.$emit('update:modelValue', searchSlices)
      await dataTable.vm.$emit('filter-change', tableSlices)

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()

      const lastEmit = filterChangeEvents![filterChangeEvents!.length - 1][0] as SliceRequestDef[]
      expect(lastEmit).toHaveLength(2)
      expect(lastEmit.find(s => s.field === 'name')).toBeTruthy()
      expect(lastEmit.find(s => s.field === 'amount')).toBeTruthy()
    })

    it('should replace table filters with search filters in replace mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          filterMergeMode: 'replace',
          showSearchActions: false  // 实时筛选模式
        }
      })

      const searchSlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'test' }
      ]

      const tableSlices: SliceRequestDef[] = [
        { field: 'amount', op: '>=', value: 100 }
      ]

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })

      // 先设置表头筛选
      await dataTable.vm.$emit('filter-change', tableSlices)

      // 再设置搜索工具栏筛选
      await searchToolbar.vm.$emit('update:modelValue', searchSlices)

      const filterChangeEvents = wrapper.emitted('filter-change')
      const lastEmit = filterChangeEvents![filterChangeEvents!.length - 1][0] as SliceRequestDef[]

      // replace 模式下，搜索工具栏筛选替换表头筛选
      expect(lastEmit).toHaveLength(1)
      expect(lastEmit[0].field).toBe('name')
    })

    it('should use table filters when search filters are empty in replace mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          filterMergeMode: 'replace'
        }
      })

      const tableSlices: SliceRequestDef[] = [
        { field: 'amount', op: '>=', value: 100 }
      ]

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      await dataTable.vm.$emit('filter-change', tableSlices)

      const filterChangeEvents = wrapper.emitted('filter-change')
      const lastEmit = filterChangeEvents![filterChangeEvents!.length - 1][0] as SliceRequestDef[]

      expect(lastEmit).toEqual(tableSlices)
    })

    it('should not duplicate filters for same field in merge mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          filterMergeMode: 'merge',
          showSearchActions: false  // 实时筛选模式
        }
      })

      const searchSlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'from-search' }
      ]

      const tableSlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'from-table' }
      ]

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      await searchToolbar.vm.$emit('update:modelValue', searchSlices)
      await dataTable.vm.$emit('filter-change', tableSlices)

      const filterChangeEvents = wrapper.emitted('filter-change')
      const lastEmit = filterChangeEvents![filterChangeEvents!.length - 1][0] as SliceRequestDef[]

      // 同一字段只保留搜索工具栏的筛选
      expect(lastEmit).toHaveLength(1)
      expect(lastEmit[0].value).toBe('from-search')
    })
  })

  describe('Exposed Methods', () => {
    it('should expose getSearchToolbar method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: toolbarProps
      })

      const vm = wrapper.vm as any
      expect(vm.getSearchToolbar).toBeDefined()

      const searchToolbar = vm.getSearchToolbar()
      expect(searchToolbar).toBeDefined()
    })

    it('should expose getDataTable method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.getDataTable).toBeDefined()

      const dataTable = vm.getDataTable()
      expect(dataTable).toBeDefined()
    })

    it('should expose getQueryPanel method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'panel',
          querySchema: mockQuerySchema
        }
      })

      const vm = wrapper.vm as any
      expect(vm.getQueryPanel).toBeDefined()
      expect(vm.getQueryPanel()).toBeDefined()
    })

    it('should expose clearSearchFilters method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.clearSearchFilters).toBeDefined()

      // 调用方法不应抛出错误
      expect(() => vm.clearSearchFilters()).not.toThrow()
    })

    it('should expose clearTableFilters method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.clearTableFilters).toBeDefined()

      vm.clearTableFilters()

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
    })

    it('should expose searchQueryPanel and submit current QueryPanel values', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'panel',
          querySchema: mockQuerySchema
        }
      })

      const vm = wrapper.vm as any
      const queryPanel = vm.getQueryPanel()
      const querySlices: SliceRequestDef[] = [
        { field: 'name', op: '=', value: 'pending-panel-value' }
      ]

      queryPanel.pendingSlices = querySlices
      vm.searchQueryPanel()
      await wrapper.vm.$nextTick()

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
      expect(filterChangeEvents![filterChangeEvents!.length - 1][0]).toEqual(querySlices)
    })

    it('should expose resetQueryPanel and clear QueryPanel filters', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'panel',
          querySchema: mockQuerySchema
        }
      })

      const vm = wrapper.vm as any
      const queryPanel = vm.getQueryPanel()
      queryPanel.pendingSlices = [
        { field: 'name', op: '=', value: 'pending-panel-value' }
      ]

      vm.searchQueryPanel()
      await wrapper.vm.$nextTick()
      vm.resetQueryPanel()
      await wrapper.vm.$nextTick()

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
      expect(filterChangeEvents![filterChangeEvents!.length - 1][0]).toEqual([])
      expect(queryPanel.getSlices()).toEqual([])
    })

    it('should expose clearAllFilters method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.clearAllFilters).toBeDefined()

      vm.clearAllFilters()

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
    })

    it('should reset QueryPanel when clearAllFilters is called', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          queryMode: 'combined',
          querySchema: mockQuerySchema
        }
      })

      const vm = wrapper.vm as any
      const queryPanel = vm.getQueryPanel()
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      queryPanel.pendingSlices = [
        { field: 'name', op: '=', value: 'from-panel' }
      ]
      vm.searchQueryPanel()
      await dataTable.vm.$emit('filter-change', [
        { field: 'amount', op: '>=', value: 100 }
      ])

      vm.clearAllFilters()
      await wrapper.vm.$nextTick()

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
      expect(filterChangeEvents![filterChangeEvents!.length - 1][0]).toEqual([])
      expect(queryPanel.getSlices()).toEqual([])
      expect(vm.getMergedFilters()).toEqual([])
    })

    it('should expose getMergedFilters method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.getMergedFilters).toBeDefined()

      const filters = vm.getMergedFilters()
      expect(Array.isArray(filters)).toBe(true)
    })

    it('should expose resetPagination method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.resetPagination).toBeDefined()

      // 不应该报错
      vm.resetPagination()
    })
  })

  describe('Integration', () => {
    it('should work in complete workflow with button mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          showSearchActions: true  // 按钮模式（默认）
        }
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      // 1. 设置搜索工具栏筛选（按钮模式下不会立即触发 filter-change）
      await searchToolbar.vm.$emit('update:modelValue', [
        { field: 'name', op: '=', value: 'test' }
      ])

      // 2. 设置表头筛选
      await dataTable.vm.$emit('filter-change', [
        { field: 'amount', op: '>=', value: 100 }
      ])

      expect(wrapper.emitted('filter-change')).toBeTruthy()

      // 3. 点击搜索按钮（这时才触发搜索工具栏的 filter-change）
      await searchToolbar.vm.$emit('search')
      expect(wrapper.emitted('search')).toBeTruthy()

      // 4. 点击重置按钮
      await searchToolbar.vm.$emit('reset')
      expect(wrapper.emitted('reset')).toBeTruthy()

      // 5. 分页变化
      await dataTable.vm.$emit('page-change', 2, 50)
      expect(wrapper.emitted('page-change')).toBeTruthy()
    })

    it('should handle rapid filter changes in realtime mode', async () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          showSearchActions: false  // 实时筛选模式
        }
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })

      // 快速连续更新（实时模式下每次都会触发 filter-change）
      for (let i = 1; i <= 5; i++) {
        await searchToolbar.vm.$emit('update:modelValue', [
          { field: 'name', op: '=', value: `test${i}` }
        ])
      }

      const filterChangeEvents = wrapper.emitted('filter-change')
      expect(filterChangeEvents).toBeTruthy()
      expect(filterChangeEvents!.length).toBe(5)
    })
  })

  describe('Edge Cases', () => {
    it('should handle undefined searchableFields', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...toolbarProps,
          searchableFields: undefined
        }
      })

      const searchToolbar = wrapper.findComponent({ name: 'SearchToolbar' })
      expect(searchToolbar.props('searchableFields')).toBeUndefined()
    })

    it('should handle empty data array', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          data: []
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('data')).toEqual([])
    })

    it('should handle zero total', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          total: 0
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('total')).toBe(0)
    })

    it('should handle empty columns array', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: {
          ...defaultProps,
          columns: []
        }
      })

      expect(wrapper.exists()).toBe(true)
    })
  })

  describe('Slot Passthrough', () => {
    it('should have slot passthrough capability for toolbar', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps,
        slots: {
          toolbar: '<div class="custom-toolbar">Custom Toolbar</div>'
        }
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.custom-toolbar').exists()).toBe(true)
    })

    it('should have slot passthrough capability for footer', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps,
        slots: {
          footer: '<div class="custom-footer">Custom Footer</div>'
        }
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.custom-footer').exists()).toBe(true)
    })
  })

  describe('Row Actions', () => {
    it('should inject _actions column when row-actions slot is provided', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps,
        slots: {
          'row-actions': '<button class="row-action-btn">Edit</button>'
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns') as EnhancedColumnSchema[]
      const actionsCol = columns.find(c => c.name === '_actions')
      expect(actionsCol).toBeTruthy()
      expect(actionsCol!.title).toBe('操作')
      expect(actionsCol!.fixed).toBe('right')
    })

    it('should NOT inject _actions column when row-actions slot is absent', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns') as EnhancedColumnSchema[]
      expect(columns.find(c => c.name === '_actions')).toBeUndefined()
    })

    it('should render row-actions slot content', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps,
        slots: {
          'row-actions': '<button class="row-action-btn">Edit</button>'
        }
      })

      expect(wrapper.find('.row-action-btn').exists()).toBe(true)
    })

    it('should not inject duplicate _actions column if already present', () => {
      const columnsWithActions: EnhancedColumnSchema[] = [
        ...mockColumns,
        { name: '_actions', type: 'TEXT', title: '操作', width: 200 }
      ]
      const wrapper = mount(DataTableWithSearch, {
        props: { ...defaultProps, columns: columnsWithActions },
        slots: {
          'row-actions': '<button>Edit</button>'
        }
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns') as EnhancedColumnSchema[]
      const actionsCols = columns.filter(c => c.name === '_actions')
      expect(actionsCols).toHaveLength(1)
      expect(actionsCols[0].width).toBe(200) // 保留原始配置
    })
  })

  describe('Selection API', () => {
    it('should expose getSelectedRows method that delegates to DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.getSelectedRows).toBeDefined()
      const rows = vm.getSelectedRows()
      expect(Array.isArray(rows)).toBe(true)
      expect(rows).toHaveLength(1) // mock returns 1 row
      expect(rows[0].id).toBe(1)
    })

    it('should expose getSelectedCount method that delegates to DataTable', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.getSelectedCount).toBeDefined()
      expect(vm.getSelectedCount()).toBe(1) // mock returns 1
    })

    it('should expose clearSelection method', () => {
      const wrapper = mount(DataTableWithSearch, {
        props: defaultProps
      })

      const vm = wrapper.vm as any
      expect(vm.clearSelection).toBeDefined()
      expect(() => vm.clearSelection()).not.toThrow()
    })
  })
})
