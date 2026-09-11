import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import ListPresetManager from './ListPresetManager.vue'
import type { EnhancedColumnSchema, ListPresetConfig, ListPresetDef, ListPresetVisibility, ListViewState } from '@/types'
import {
  createListPreset,
  listPresets,
  updateListPreset
} from '@/api/listPreset'

vi.mock('@element-plus/icons-vue', () => ({
  ArrowDown: {},
  ArrowUp: {},
  Bottom: {},
  Brush: {},
  CopyDocument: {},
  Delete: {},
  Download: {},
  Edit: {},
  Finished: {},
  Loading: {},
  Lock: {},
  MoreFilled: {},
  Operation: {},
  Rank: {},
  Refresh: {},
  Search: {},
  Share: {},
  Star: {},
  Top: {},
  Upload: {}
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue(undefined)
  }
}))

vi.mock('@/api/listPreset', () => ({
  createListPreset: vi.fn(),
  deleteListPreset: vi.fn(),
  listPresets: vi.fn(),
  setDefaultListPreset: vi.fn(),
  updateListPreset: vi.fn()
}))

type ExposedManager = {
  applyPreset: (preset: ListPresetDef) => Promise<void>
  clearCurrentConditions: () => Promise<void>
  getColumnDraft: () => Array<{
    name: string
    visible: boolean
    width?: number
    fixed?: 'left' | 'right'
  }>
  startColumnDrag: (name: string, event: DragEvent) => void
  startAvailableColumnDrag: (name: string, event: DragEvent) => void
  onColumnDragOver: (name: string, event: DragEvent) => void
  dropColumn: (name: string, event?: DragEvent) => void
  dropSelectedList: (event: DragEvent) => void
  getDraft: () => {
    title: string
    description: string
    visibility: ListPresetVisibility
    isDefault: boolean
    saveQueryConditions: boolean
  }
  getPresets: () => ListPresetDef[]
  loadPresets: () => Promise<void>
  moveVisibleColumn: (index: number, direction: -1 | 1) => void
  moveVisibleColumnToEdge: (index: number, edge: 'top' | 'bottom') => void
  openDialog: () => void
  openLoadDialog: () => void
  openSaveDialog: () => void
  overwritePreset: (preset: ListPresetDef) => Promise<void>
  saveCurrentPreset: () => Promise<void>
  setDraft: (draft: Partial<ReturnType<ExposedManager['getDraft']>>) => void
  startEditPreset: (preset: ListPresetDef) => void
  syncColumnDraftFromState: () => void
}

const config: ListPresetConfig = {
  userId: 'user_001',
  model: 'FactOrderQueryModel',
  businessKey: 'orders'
}

const currentState: ListViewState = {
  columns: ['orderNo', 'status'],
  columnSettings: [
    { name: 'orderNo', visible: true, order: 0, width: 160 },
    { name: 'status', visible: true, order: 1 }
  ],
  slice: [{ field: 'status', op: '=', value: 'SUBMITTED' }],
  orderBy: [{ field: 'orderNo', order: 'desc' }],
  pageSize: 20
}

function makePreset(overrides: Partial<ListPresetDef> = {}): ListPresetDef {
  return {
    id: 'preset_001',
    model: config.model,
    businessKey: config.businessKey,
    title: '默认工单视图',
    description: '常用字段',
    columns: ['orderNo'],
    columnSettings: [{ name: 'orderNo', visible: true, order: 0 }],
    query: {
      slice: [],
      orderBy: []
    },
    pageSize: 50,
    visibility: 'PRIVATE',
    ownerId: config.userId,
    isDefault: false,
    version: 1,
    createdAt: '2026-05-24T00:00:00Z',
    updatedAt: '2026-05-24T00:00:00Z',
    ...overrides
  }
}

function mountManager(options: {
  getState?: () => ListViewState
  applyState?: (state: ListViewState) => void
  reload?: () => Promise<void>
  clearConditions?: () => Promise<void>
  availableColumns?: EnhancedColumnSchema[]
  lockedColumns?: string[]
  requiredRuntimeColumns?: string[]
} = {}) {
  const wrapper = mountManagerWrapper(options)
  return wrapper.vm as unknown as ExposedManager
}

function mountManagerWrapper(options: {
  getState?: () => ListViewState
  applyState?: (state: ListViewState) => void
  reload?: () => Promise<void>
  clearConditions?: () => Promise<void>
  availableColumns?: EnhancedColumnSchema[]
  lockedColumns?: string[]
  requiredRuntimeColumns?: string[]
} = {}) {
  const componentStubs = Object.fromEntries([
    'el-button',
    'el-checkbox',
    'el-descriptions',
    'el-descriptions-item',
    'el-dropdown',
    'el-dropdown-item',
    'el-dropdown-menu',
    'el-empty',
    'el-form',
    'el-form-item',
    'el-icon',
    'el-input',
    'el-input-number',
    'el-option',
    'el-radio',
    'el-radio-button',
    'el-radio-group',
    'el-select'
  ].map(name => [name, true]))

  return shallowMount(ListPresetManager, {
    props: {
      config,
      getState: options.getState || (() => currentState),
      applyState: options.applyState || vi.fn(),
      reload: options.reload,
      clearConditions: options.clearConditions,
      availableColumns: options.availableColumns,
      lockedColumns: options.lockedColumns,
      requiredRuntimeColumns: options.requiredRuntimeColumns
    },
    global: {
      stubs: {
        ...componentStubs,
        'el-dialog': {
          template: '<div><slot /><slot name="footer" /></div>'
        },
        'el-scrollbar': {
          template: '<div><slot /></div>'
        },
        'el-tag': {
          template: '<span><slot /></span>'
        },
        'el-tooltip': {
          template: '<div><slot /></div>'
        }
      }
    }
  })
}

describe('ListPresetManager', () => {
  it('persists empty conditions and relative tokens and immediately applies the saved configuration', async () => {
    const slice = [
      { field: 'customer$id', op: '=', value: '' },
      { field: 'date', op: '[)', value: { $relativeDate: 'thismonth', dateTime: false } }
    ]
    vi.mocked(createListPreset).mockImplementation(async (_scope, request) => makePreset({
      ...request, query: request.query!, visibility: request.visibility || 'PRIVATE'
    }))
    const applyState = vi.fn()
    const reload = vi.fn().mockResolvedValue(undefined)
    const manager = mountManager({ applyState, reload, getState: () => ({ ...currentState, slice }) })
    manager.openSaveDialog()
    manager.setDraft({ title: '本月客户查询' })
    await manager.saveCurrentPreset()
    expect(createListPreset).toHaveBeenCalledWith(config, expect.objectContaining({ query: { slice, orderBy: currentState.orderBy } }))
    expect(applyState).toHaveBeenCalledWith(expect.objectContaining({ slice }))
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('can save without applying the scheme', async () => {
    vi.mocked(createListPreset).mockResolvedValue(makePreset())
    const applyState = vi.fn()
    const wrapper = mountManagerWrapper({ applyState })
    const manager = wrapper.vm as unknown as ExposedManager
    manager.openSaveDialog()
    manager.setDraft({ title: '稍后使用' })
    ;(wrapper.vm as any).applyAfterSave = false
    await manager.saveCurrentPreset()
    expect(createListPreset).toHaveBeenCalledTimes(1)
    expect(applyState).not.toHaveBeenCalled()
  })

  it('saves and restores explicitly selected dependencies without implicitly adding others', async () => {
    vi.mocked(createListPreset).mockResolvedValue(makePreset())
    const applyState = vi.fn()
    const manager = mountManager({ applyState,
      availableColumns: ['wayType', 'srcNode', 'implicit'].map(name => ({ name, type: 'TEXT' })),
      requiredRuntimeColumns: ['wayType', 'implicit'],
      getState: () => ({ columns: ['wayType', 'srcNode'], slice: [], orderBy: [] })
    })
    manager.openSaveDialog()
    manager.setDraft({ title: 'explicit dependencies' })
    await manager.saveCurrentPreset()
    const request = vi.mocked(createListPreset).mock.calls[0][1]
    expect(request.columns).toEqual(['wayType', 'srcNode'])
    expect(request.columnSettings?.map(item => item.name)).toEqual(['wayType', 'srcNode'])
    await manager.applyPreset(makePreset({ columns: request.columns, columnSettings: request.columnSettings }))
    expect(applyState).toHaveBeenCalledWith(expect.objectContaining({ columns: ['wayType', 'srcNode'] }))
  })

  it('saves a small selection from a field pool larger than fifty', async () => {
    vi.mocked(createListPreset).mockResolvedValue(makePreset())
    const manager = mountManager({ availableColumns: Array.from({ length: 70 }, (_, i) => ({ name: `f${i}`, type: 'TEXT' })),
      getState: () => ({ columns: ['f0'], slice: [], orderBy: [] }) })
    manager.openSaveDialog()
    manager.setDraft({ title: 'small selection' })
    await manager.saveCurrentPreset()
    expect(createListPreset).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      columns: ['f0'], columnSettings: [{ name: 'f0', visible: true, order: 0 }]
    }))
  })

  it('rejects an oversized loaded preset before dropping unavailable fields', async () => {
    const applyState = vi.fn()
    const manager = mountManager({ applyState, availableColumns: [{ name: 'f0', type: 'TEXT' }] })
    await manager.applyPreset(makePreset({ columns: Array.from({ length: 51 }, (_, i) => `f${i}`), columnSettings: [] }))
    expect(applyState).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('50'))
  })
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads presets by user/model/businessKey', async () => {
    const preset = makePreset()
    vi.mocked(listPresets).mockResolvedValue([preset])

    const manager = mountManager()
    await manager.loadPresets()

    expect(listPresets).toHaveBeenCalledWith({
      userId: config.userId,
      model: config.model,
      businessKey: config.businessKey
    })
    expect(manager.getPresets()).toEqual([preset])
  })

  it('creates a preset from the current table state', async () => {
    const saved = makePreset({ columns: currentState.columns, query: { slice: currentState.slice, orderBy: currentState.orderBy } })
    vi.mocked(createListPreset).mockResolvedValue(saved)

    const manager = mountManager()
    manager.setDraft({
      title: '我的视图',
      description: '个人常用',
      isDefault: true
    })
    await manager.saveCurrentPreset()

    expect(createListPreset).toHaveBeenCalledWith({
      userId: config.userId,
      model: config.model,
      businessKey: config.businessKey
    }, {
      title: '我的视图',
      description: '个人常用',
      columns: currentState.columns,
      columnSettings: currentState.columnSettings,
      query: {
        slice: currentState.slice,
        orderBy: currentState.orderBy
      },
      pageSize: currentState.pageSize,
      visibility: 'PRIVATE',
      isDefault: true
    })
    expect(manager.getDraft().title).toBe('')
  })

  it('opens the save flow with the current table state', () => {
    const manager = mountManager()
    manager.setDraft({
      title: '旧标题',
      saveQueryConditions: false
    })

    manager.openSaveDialog()

    expect(manager.getDraft()).toMatchObject({
      title: '',
      saveQueryConditions: true
    })
    expect(manager.getColumnDraft().filter(column => column.visible).map(column => column.name)).toEqual(currentState.columns)
  })

  it('opens the load flow as a focused preset list', async () => {
    const wrapper = mountManagerWrapper()
    const manager = wrapper.vm as unknown as ExposedManager

    manager.openLoadDialog()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.query-wizard.is-load-mode').exists()).toBe(true)
    expect(wrapper.find('.preset-list-section').exists()).toBe(true)
    expect(wrapper.find('.field-pool-section').exists()).toBe(false)
    expect(wrapper.find('.inspector-section').exists()).toBe(false)
  })

  it('opens the custom flow with field configuration visible', async () => {
    const wrapper = mountManagerWrapper()
    const manager = wrapper.vm as unknown as ExposedManager

    manager.openDialog()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.query-wizard.is-customize-mode').exists()).toBe(true)
    expect(wrapper.find('.wizard-selected').exists()).toBe(true)
    expect(wrapper.find('.wizard-field-workspace').exists()).toBe(true)
    expect(wrapper.find('.preset-list-section').exists()).toBe(false)
  })

  it('saves column visibility and order from available columns', async () => {
    const saved = makePreset({ columns: ['status'] })
    vi.mocked(createListPreset).mockResolvedValue(saved)

    const manager = mountManager()
    manager.syncColumnDraftFromState()
    const draft = manager.getColumnDraft()
    const firstColumn = draft[0]
    const secondColumn = draft[1]
    if (!firstColumn || !secondColumn) {
      throw new Error('column draft should contain two columns')
    }
    firstColumn.visible = false
    secondColumn.width = 120
    manager.setDraft({ title: '只看状态' })

    await manager.saveCurrentPreset()

    expect(createListPreset).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      columns: ['status'],
      columnSettings: [
        expect.objectContaining({ name: 'status', visible: true, width: 120, order: 0 })
      ]
    }))
  })

  it('can save a list view without current query conditions', async () => {
    const saved = makePreset({ query: { slice: [], orderBy: [] } })
    vi.mocked(createListPreset).mockResolvedValue(saved)

    const manager = mountManager()
    manager.setDraft({
      title: '空条件视图',
      saveQueryConditions: false
    })
    await manager.saveCurrentPreset()

    expect(createListPreset).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      query: {
        slice: [],
        orderBy: []
      }
    }))
  })

  it('moves selected columns to the top and bottom', () => {
    const manager = mountManager({
      getState: () => ({
        columns: ['orderNo', 'status', 'customerName', 'amount'],
        columnSettings: [
          { name: 'orderNo', visible: true, order: 0 },
          { name: 'status', visible: true, order: 1 },
          { name: 'customerName', visible: true, order: 2 },
          { name: 'amount', visible: true, order: 3 }
        ],
        slice: [],
        orderBy: []
      })
    })

    manager.syncColumnDraftFromState()
    manager.moveVisibleColumnToEdge(2, 'top')

    expect(manager.getColumnDraft().map(column => column.name)).toEqual([
      'customerName',
      'orderNo',
      'status',
      'amount'
    ])

    manager.moveVisibleColumnToEdge(0, 'bottom')

    expect(manager.getColumnDraft().map(column => column.name)).toEqual([
      'orderNo',
      'status',
      'amount',
      'customerName'
    ])
  })

  it('keeps locked columns visible and excludes unselected dependencies from saved presets', async () => {
    const saved = makePreset({ columns: ['orderNo'] })
    vi.mocked(createListPreset).mockResolvedValue(saved)

    const manager = mountManager({
      getState: () => ({
        columns: ['orderNo', 'status', 'runtimeToken'],
        columnSettings: [
          { name: 'orderNo', visible: true, order: 0 },
          { name: 'status', visible: true, order: 1 },
          { name: 'runtimeToken', visible: true, order: 2 }
        ],
        slice: [],
        orderBy: []
      }),
      availableColumns: [
        { name: 'orderNo', title: '订单号', type: 'TEXT' },
        { name: 'status', title: '状态', type: 'TEXT' },
        { name: 'runtimeToken', title: '运行时令牌', type: 'TEXT' }
      ],
      lockedColumns: ['orderNo'],
      requiredRuntimeColumns: ['runtimeToken']
    })

    manager.syncColumnDraftFromState()
    manager.getColumnDraft().forEach(column => {
      column.visible = false
    })
    manager.setDraft({ title: '锁定列视图' })

    await manager.saveCurrentPreset()

    expect(createListPreset).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      columns: ['orderNo'],
      columnSettings: [
        expect.objectContaining({ name: 'orderNo', visible: true, order: 0 })
      ]
    }))
    const request = vi.mocked(createListPreset).mock.calls[0]?.[1]
    expect(request?.columnSettings?.some(setting => setting.name === 'runtimeToken')).toBe(false)
  })

  it('shows grouped metadata fields as prototype cards while retaining their metadata', async () => {
    const wrapper = mountManagerWrapper({
      getState: () => ({
        columns: ['orderNo', 'customerName', 'amount'],
        columnSettings: [
          { name: 'orderNo', visible: true, order: 0 },
          { name: 'customerName', visible: true, order: 1 },
          { name: 'amount', visible: true, order: 2 }
        ],
        slice: [],
        orderBy: []
      }),
      availableColumns: [
        { name: 'orderNo', title: '订单号', type: 'TEXT', groupKey: 'order', groupTitle: '订单信息', groupOrder: 1 },
        { name: 'customerName', title: '客户', type: 'TEXT', groupKey: 'customer', groupTitle: '客户信息', groupOrder: 2 },
        { name: 'amount', title: '金额', type: 'NUMBER', groupKey: 'order', groupTitle: '订单信息', groupOrder: 1 }
      ]
    })

    const manager = wrapper.vm as unknown as ExposedManager
    manager.syncColumnDraftFromState()
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.wizard-field-card b').map(node => node.text())).toEqual(['订单号', '客户', '金额'])
    expect(manager.getColumnDraft()).toEqual(expect.arrayContaining([expect.objectContaining({ name: 'customerName', groupTitle: '客户信息' })]))
  })

  it('shows fields without group metadata and classifies LONG as numeric', async () => {
    const wrapper = mountManagerWrapper({
      getState: () => ({
        columns: ['longProbeId'],
        columnSettings: [
          { name: 'longProbeId', visible: true, order: 0 }
        ],
        slice: [],
        orderBy: []
      }),
      availableColumns: [
        { name: 'longProbeId', title: 'LONG测试ID', type: 'LONG' }
      ]
    })

    const manager = wrapper.vm as unknown as ExposedManager
    manager.syncColumnDraftFromState()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.wizard-field-card').text()).toContain('数值')
    expect(wrapper.text()).not.toContain('未分组')
  })

  it('includes both explicitly grouped and ungrouped fields in the card picker', async () => {
    const wrapper = mountManagerWrapper({
      getState: () => ({
        columns: ['orderId', 'longProbeId'],
        columnSettings: [
          { name: 'orderId', visible: true, order: 0 },
          { name: 'longProbeId', visible: true, order: 1 }
        ],
        slice: [],
        orderBy: []
      }),
      availableColumns: [
        {
          name: 'orderId',
          title: '订单ID',
          type: 'STRING',
          category: 'attribute',
          groupKey: 'attribute',
          groupTitle: '基础属性'
        },
        { name: 'longProbeId', title: 'LONG测试ID', type: 'LONG' }
      ]
    })

    const manager = wrapper.vm as unknown as ExposedManager
    manager.syncColumnDraftFromState()
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.wizard-field-card b').map(node => node.text())).toEqual(['订单ID', 'LONG测试ID'])
  })

  it('offers draggable fields and opens settings from the edit action', async () => {
    const wrapper = mountManagerWrapper({
      getState: () => ({
        columns: ['orderNo', 'status'],
        columnSettings: [
          { name: 'orderNo', visible: true, order: 0, width: 180, fixed: 'left' },
          { name: 'status', visible: true, order: 1 }
        ],
        slice: [],
        orderBy: []
      }),
      availableColumns: [
        { name: 'orderNo', title: '订单号', type: 'TEXT', width: 160 },
        { name: 'status', title: '状态', type: 'TEXT' }
      ]
    })

    const manager = wrapper.vm as unknown as ExposedManager
    manager.syncColumnDraftFromState()
    await wrapper.vm.$nextTick()

    const firstRow = wrapper.find('.selected-row')
    const actionRail = firstRow.find('.selected-actions')
    expect(actionRail.exists()).toBe(true)
    expect(firstRow.attributes('draggable')).toBe('true')
    expect(actionRail.find('[aria-label="编辑字段配置"]').exists()).toBe(true)
    expect(firstRow.text()).toContain('左固定')
    expect(firstRow.text()).toContain('180 px')
    expect(firstRow.find('.selected-editor').exists()).toBe(false)

    await firstRow.find('[aria-label="编辑字段配置"]').trigger('click')

    expect(wrapper.find('.selected-editor').exists()).toBe(true)
  })

  it('shows insertion lines, supports center swap, and briefly confirms moved fields', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = mountManagerWrapper({
        getState: () => ({ columns: ['a', 'b', 'c'], columnSettings: [], slice: [], orderBy: [] }),
        availableColumns: [
          { name: 'a', title: '字段 A', type: 'TEXT' },
          { name: 'b', title: '字段 B', type: 'TEXT' },
          { name: 'c', title: '字段 C', type: 'TEXT' }
        ]
      })
      const manager = wrapper.vm as unknown as ExposedManager
      manager.syncColumnDraftFromState()
      await wrapper.vm.$nextTick()
      const rect = { top: 0, height: 100 } as DOMRect
      const event = (clientY: number) => ({ currentTarget: { getBoundingClientRect: () => rect }, clientY, dataTransfer: { setData: vi.fn(), effectAllowed: '' } }) as unknown as DragEvent

      manager.startColumnDrag('a', event(0))
      manager.onColumnDragOver('c', event(10))
      await wrapper.vm.$nextTick()
      expect(wrapper.findAll('.selected-row').at(2)?.classes()).toContain('drop-before')
      expect(wrapper.find('.drag-feedback').text()).toContain('之前插入')
      manager.dropColumn('c', event(10))
      await wrapper.vm.$nextTick()
      expect(manager.getColumnDraft().filter(column => column.visible).map(column => column.name)).toEqual(['b', 'a', 'c'])
      expect(wrapper.findAll('.selected-row').at(1)?.classes()).toContain('is-recently-moved')

      manager.startColumnDrag('a', event(0))
      manager.onColumnDragOver('c', event(50))
      await wrapper.vm.$nextTick()
      expect(wrapper.findAll('.selected-row').at(2)?.classes()).toContain('drop-swap')
      manager.dropColumn('c', event(50))
      await wrapper.vm.$nextTick()
      expect(manager.getColumnDraft().filter(column => column.visible).map(column => column.name)).toEqual(['b', 'c', 'a'])
      vi.advanceTimersByTime(1800)
      await wrapper.vm.$nextTick()
      expect(wrapper.findAll('.selected-row').some(row => row.classes().includes('is-recently-moved'))).toBe(false)
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('inserts an unselected field into the selected list without replacing its target', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = mountManagerWrapper({
        getState: () => ({ columns: ['a', 'c'], columnSettings: [], slice: [], orderBy: [] }),
        availableColumns: [
          { name: 'a', title: '字段 A', type: 'TEXT' },
          { name: 'b', title: '字段 B', type: 'TEXT' },
          { name: 'c', title: '字段 C', type: 'TEXT' }
        ]
      })
      const manager = wrapper.vm as unknown as ExposedManager
      manager.syncColumnDraftFromState()
      await wrapper.vm.$nextTick()
      const rect = { top: 0, height: 100 } as DOMRect
      const event = (clientY: number) => ({ currentTarget: { getBoundingClientRect: () => rect }, clientY, dataTransfer: { setData: vi.fn(), effectAllowed: '' } }) as unknown as DragEvent

      manager.startAvailableColumnDrag('b', event(0))
      manager.onColumnDragOver('c', event(10))
      await wrapper.vm.$nextTick()
      expect(wrapper.findAll('.selected-row').at(1)?.classes()).toContain('drop-before')
      expect(wrapper.find('.drag-feedback').text()).toContain('不会替换')
      manager.dropColumn('c', event(10))
      await wrapper.vm.$nextTick()
      expect(manager.getColumnDraft().filter(column => column.visible).map(column => column.name)).toEqual(['a', 'b', 'c'])
      expect(manager.getColumnDraft().find(column => column.name === 'b')?.visible).toBe(true)
      expect(wrapper.findAll('.selected-row').at(1)?.classes()).toContain('is-recently-moved')
      vi.advanceTimersByTime(1800)
      await wrapper.vm.$nextTick()
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('calls the clear conditions callback', async () => {
    const clearConditions = vi.fn().mockResolvedValue(undefined)
    const manager = mountManager({ clearConditions })

    await manager.clearCurrentConditions()

    expect(clearConditions).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('已清空查询条件')
  })

  it('renders clear conditions as an icon-only compact button', () => {
    const wrapper = mountManagerWrapper({ clearConditions: vi.fn() })
    const clearButton = wrapper.find('[data-testid="list-preset-clear-conditions"]')

    expect(clearButton.exists()).toBe(true)
    expect(clearButton.text()).toBe('')
    expect(clearButton.attributes()).toHaveProperty('circle')
    expect(clearButton.attributes('title')).toBe('清空查询条件')
    expect(clearButton.attributes('aria-label')).toBe('清空查询条件')
  })

  it('prevents saving when all columns are hidden', async () => {
    const manager = mountManager()
    manager.syncColumnDraftFromState()
    manager.getColumnDraft().forEach(column => {
      column.visible = false
    })
    manager.setDraft({ title: '无字段视图' })

    await manager.saveCurrentPreset()

    expect(ElMessage.warning).toHaveBeenCalledWith('请至少保留一个显示字段')
    expect(createListPreset).not.toHaveBeenCalled()
  })

  it('edits a saved preset without replacing it with unrelated current table state', async () => {
    const preset = makePreset()
    const updated = makePreset({ title: '更新后的视图' })
    vi.mocked(updateListPreset).mockResolvedValue(updated)

    const manager = mountManager()
    manager.startEditPreset(preset)
    manager.setDraft({ title: '更新后的视图' })
    await manager.saveCurrentPreset()

    expect(updateListPreset).toHaveBeenCalledWith(config.userId, preset.id, expect.objectContaining({
      title: '更新后的视图',
      columns: preset.columns,
      pageSize: preset.pageSize,
      query: {
        slice: preset.query?.slice || [],
        orderBy: preset.query?.orderBy || []
      }
    }))
    expect(manager.getPresets()[0]).toEqual(updated)
  })

  it('opens editing at the first wizard step', async () => {
    const wrapper = mountManagerWrapper()
    const manager = wrapper.vm as unknown as ExposedManager
    manager.openLoadDialog()
    manager.startEditPreset(makePreset())
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="wizard-step-columns"]').classes()).toContain('active')
    expect(wrapper.find('[data-testid="wizard-step-save"]').classes()).not.toContain('active')
  })

  it('overwrites a preset content while keeping its metadata', async () => {
    const preset = makePreset({
      visibility: 'DEPARTMENT',
      isDefault: true
    })
    const updated = makePreset({
      columns: currentState.columns,
      visibility: 'DEPARTMENT',
      isDefault: true
    })
    vi.mocked(updateListPreset).mockResolvedValue(updated)

    const manager = mountManager()
    await manager.overwritePreset(preset)

    expect(updateListPreset).toHaveBeenCalledWith(config.userId, preset.id, {
      title: preset.title,
      description: preset.description,
      columns: currentState.columns,
      columnSettings: currentState.columnSettings,
      query: {
        slice: currentState.slice,
        orderBy: currentState.orderBy
      },
      pageSize: currentState.pageSize,
      visibility: preset.visibility,
      isDefault: preset.isDefault
    })
  })

  it('applies preset state and reloads table data', async () => {
    const preset = makePreset({
      columns: ['orderNo', 'status'],
      query: {
        slice: [{ field: 'status', op: '=', value: 'DONE' }],
        orderBy: [{ field: 'orderNo', order: 'asc' }]
      },
      pageSize: 100
    })
    const applyState = vi.fn()
    const reload = vi.fn().mockResolvedValue(undefined)

    const manager = mountManager({ applyState, reload })
    await manager.applyPreset(preset)

    expect(applyState).toHaveBeenCalledWith({
      columns: preset.columns,
      columnSettings: preset.columnSettings,
      slice: preset.query.slice,
      orderBy: preset.query.orderBy,
      pageSize: preset.pageSize
    })
    expect(reload).toHaveBeenCalled()
  })

  it('warns when applying a preset that contains unavailable fields', async () => {
    const preset = makePreset({
      columns: ['orderNo', 'legacyField'],
      columnSettings: [
        { name: 'orderNo', visible: true, order: 0 },
        { name: 'legacyField', visible: true, order: 1 }
      ]
    })
    const applyState = vi.fn()

    const manager = mountManager({
      applyState,
      availableColumns: [
        { name: 'orderNo', title: '订单号', type: 'TEXT' },
        { name: 'status', title: '状态', type: 'TEXT' }
      ]
    })
    await manager.applyPreset(preset)

    expect(applyState).toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('已忽略失效字段: legacyField')
  })

})
