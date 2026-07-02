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
  Delete: {},
  Finished: {},
  Loading: {},
  Lock: {},
  MoreFilled: {},
  Operation: {},
  Rank: {},
  Refresh: {},
  Search: {},
  Star: {},
  Top: {}
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
  const wrapper = shallowMount(ListPresetManager, {
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
      stubs: [
        'el-button',
        'el-checkbox',
        'el-descriptions',
        'el-descriptions-item',
        'el-dialog',
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
        'el-select',
        'el-scrollbar',
        'el-tag',
        'el-tooltip'
      ]
    }
  })
  return wrapper.vm as unknown as ExposedManager
}

describe('ListPresetManager', () => {
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
        expect.objectContaining({ name: 'orderNo', visible: false, order: 0 }),
        expect.objectContaining({ name: 'status', visible: true, width: 120, order: 1 })
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

  it('keeps locked columns visible and excludes runtime columns from saved presets', async () => {
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
        expect.objectContaining({ name: 'orderNo', visible: true, order: 0 }),
        expect.objectContaining({ name: 'status', visible: false, order: 1 })
      ]
    }))
    const request = vi.mocked(createListPreset).mock.calls[0]?.[1]
    expect(request?.columnSettings?.some(setting => setting.name === 'runtimeToken')).toBe(false)
  })

  it('calls the clear conditions callback', async () => {
    const clearConditions = vi.fn().mockResolvedValue(undefined)
    const manager = mountManager({ clearConditions })

    await manager.clearCurrentConditions()

    expect(clearConditions).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('已清空查询条件')
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

  it('edits an existing preset with current table state', async () => {
    const preset = makePreset()
    const updated = makePreset({ title: '更新后的视图', columns: currentState.columns })
    vi.mocked(updateListPreset).mockResolvedValue(updated)

    const manager = mountManager()
    manager.startEditPreset(preset)
    manager.setDraft({ title: '更新后的视图' })
    await manager.saveCurrentPreset()

    expect(updateListPreset).toHaveBeenCalledWith(config.userId, preset.id, expect.objectContaining({
      title: '更新后的视图',
      columns: currentState.columns,
      query: {
        slice: currentState.slice,
        orderBy: currentState.orderBy
      }
    }))
    expect(manager.getPresets()[0]).toEqual(updated)
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
