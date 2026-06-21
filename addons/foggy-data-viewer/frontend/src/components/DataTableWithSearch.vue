<script setup lang="ts">
import { ref, computed, onMounted, useAttrs, useSlots } from 'vue'
import type { EnhancedColumnSchema, SliceRequestDef, FilterOption, TableSchema, FetchDataParams, FetchDataResult, OrderRequestDef, QueryHooks, MemberQueryRequest, MemberQueryResponse, CellCopyConfig, QueryMode, ListViewState, ColumnViewSetting, ListPresetConfig, ListPresetDef, TableDensity, QueryTrigger } from '@/types'
import SearchToolbar from './SearchToolbar.vue'
import QueryPanel from './QueryPanel.vue'
import type { QueryPanelExpose, QuerySchema } from './QueryPanel.vue'
import DataTable from './DataTable.vue'
import ListPresetManager from './list-preset/ListPresetManager.vue'
import { useTableQuery } from './composables/useTableQuery'
import { getDefaultListPreset } from '@/api/listPreset'

// 禁用自动继承属性
defineOptions({
  inheritAttrs: false
})

// 获取透传的属性和事件
const attrs = useAttrs()

/**
 * DataTableWithSearch 组件
 *
 * 支持两种工作模式：
 * 1. Schema 模式（推荐）：传入 schema + fetchData，组件自动管理数据加载
 * 2. 受控模式：传入 columns + data + total + loading，用户手动管理状态
 */
interface Props {
  // ========== Schema 模式 Props ==========
  /** 表格 Schema 配置（包含 columns 和其他配置） */
  schema?: TableSchema
  /** 数据加载函数 */
  fetchData?: (params: FetchDataParams) => Promise<FetchDataResult>

  // ========== 受控模式 Props（当不使用 schema 时） ==========
  /** 列配置 */
  columns?: EnhancedColumnSchema[]
  /** 表格数据 */
  data?: Record<string, unknown>[]
  /** 总数据量 */
  total?: number
  /** 加载状态 */
  loading?: boolean
  /** 每页大小 */
  pageSize?: number
  /** 是否显示表头过滤器 */
  showFilters?: boolean
  /** 是否显示分页栏 */
  showPager?: boolean
  /** 初始筛选条件 */
  initialSlice?: SliceRequestDef[]
  /** 服务端汇总数据 */
  serverSummary?: Record<string, unknown> | null
  /** 过滤选项加载器 */
  filterOptionsLoader?: (columnName: string) => Promise<FilterOption[]>
  /** 自定义过滤器组件映射 */
  customFilterComponents?: Record<string, unknown>
  /** 普通单元格悬浮复制配置 */
  cellCopy?: CellCopyConfig
  /** 内置查询入口模式；设置后优先于 showQueryPanel/showFilters */
  queryMode?: QueryMode
  /** 表格视觉密度 */
  density?: TableDensity

  // ========== SearchToolbar Props ==========
  /** 搜索工具栏可搜索字段 */
  searchableFields?: string[]
  /** 搜索工具栏布局 */
  searchLayout?: 'horizontal' | 'vertical'
  /** 是否显示搜索按钮 */
  showSearchActions?: boolean

  // ========== 组合配置 ==========
  /** 搜索工具栏和表头过滤器的筛选条件合并模式 */
  filterMergeMode?: 'replace' | 'merge'

  // ========== 查询钩子 ==========
  /** 查询钩子（声明式） */
  queryHooks?: QueryHooks

  // ========== 查询条件区 ==========
  /** 查询 Schema（传统查询区定义） */
  querySchema?: QuerySchema
  /** 是否显示传统查询区 */
  showQueryPanel?: boolean

  // ========== 维度成员远程过滤 ==========
  /** QM 模型名称（远程成员加载所需） */
  qmModel?: string
  /** 远程维度成员加载器 */
  filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>

  // ========== 保存查询功能 ==========
  /** 启用保存查询功能 */
  enableSavedQuery?: boolean
  /** 自定义列表配置 */
  listPreset?: boolean | ListPresetConfig
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50,
  showFilters: true,
  showPager: true,
  searchLayout: 'horizontal',
  showSearchActions: true,
  filterMergeMode: 'merge',
  showQueryPanel: false
})

const emit = defineEmits<{
  // 通用事件
  (e: 'page-change', page: number, size: number): void
  (e: 'sort-change', field: string | null, order: 'asc' | 'desc' | null): void
  (e: 'filter-change', slices: SliceRequestDef[]): void
  (e: 'filter-commit', slices: SliceRequestDef[]): void
  (e: 'row-click', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  (e: 'row-dblclick', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  (e: 'checkbox-change', rows: Record<string, unknown>[]): void
  (e: 'checkbox-all', rows: Record<string, unknown>[]): void
  // SearchToolbar 事件
  (e: 'search', slices: SliceRequestDef[]): void
  (e: 'reset'): void
  // Schema 模式事件
  (e: 'load-success', result: FetchDataResult): void
  (e: 'load-error', error: Error): void
}>()

// 获取插槽（用于检测 row-actions 等标准插槽）
const parentSlots = useSlots()

// ========== 判断工作模式 ==========
const isSchemaMode = computed(() => !!props.schema && !!props.fetchData)

// ========== useTableQuery（Schema 模式） ==========
// 始终创建 query 对象，但只在 Schema 模式下调用 loadData
const query = useTableQuery(
  props.fetchData ?? (async () => ({ items: [], total: 0 })),
  {
    pageSize: props.schema?.pageSize ?? props.pageSize,
    hooks: props.queryHooks
  }
)

const activeListViewState = ref<ListViewState | null>(null)
const activePageSize = ref<number | undefined>(undefined)

const baseColumns = computed(() => {
  if (isSchemaMode.value && props.schema) {
    return props.schema.columns
  }
  return props.columns || []
})

function applyColumnSetting(col: EnhancedColumnSchema, setting?: ColumnViewSetting): EnhancedColumnSchema {
  if (!setting) return col

  return {
    ...col,
    width: setting.width ?? col.width,
    minWidth: setting.minWidth ?? col.minWidth,
    fixed: setting.fixed ?? col.fixed
  }
}

function deriveColumnsByListViewState(columns: EnhancedColumnSchema[], state: ListViewState | null): EnhancedColumnSchema[] {
  if (!state) return columns

  const columnMap = new Map(columns.map(col => [col.name, col]))
  const settingMap = new Map((state.columnSettings || []).map(setting => [setting.name, setting]))
  const visibleNames = new Set(state.columns || [])
  const hasVisibleColumns = visibleNames.size > 0
  const orderedNames: string[] = []

  if (state.columnSettings && state.columnSettings.length > 0) {
    orderedNames.push(...state.columnSettings
      .slice()
      .sort((a, b) => a.order - b.order)
      .map(setting => setting.name))
  }

  for (const name of state.columns || []) {
    if (!orderedNames.includes(name)) {
      orderedNames.push(name)
    }
  }

  const result: EnhancedColumnSchema[] = []
  const usedNames = new Set<string>()

  for (const name of orderedNames) {
    const col = columnMap.get(name)
    if (!col || usedNames.has(name)) {
      if (!col) {
        console.warn(`[DataTableWithSearch] Ignore unknown list preset column: ${name}`)
      }
      continue
    }

    const setting = settingMap.get(name)
    const visible = setting?.visible !== false && (!hasVisibleColumns || visibleNames.has(name))
    if (!visible) continue

    result.push(applyColumnSetting(col, setting))
    usedNames.add(name)
  }

  if (!hasVisibleColumns) {
    for (const col of columns) {
      if (usedNames.has(col.name)) continue
      const setting = settingMap.get(col.name)
      if (setting?.visible === false) continue
      result.push(applyColumnSetting(col, setting))
      usedNames.add(col.name)
    }
  }

  return result.length > 0 ? result : columns
}

// ========== 计算属性：根据模式选择数据源 ==========
const effectiveColumns = computed(() => {
  const cols = deriveColumnsByListViewState(baseColumns.value, activeListViewState.value)

  // 当用户提供 row-actions 插槽时，自动注入一个 actions 列（除非已有同名列）
  if (parentSlots['row-actions'] && !cols.some(c => c.name === '_actions')) {
    const actionsCol: EnhancedColumnSchema = {
      name: '_actions',
      type: 'TEXT',
      title: '操作',
      width: 120,
      fixed: 'right',
      filterable: false
    }
    return [...cols, actionsCol]
  }

  return cols
})

const effectiveData = computed(() => {
  if (isSchemaMode.value) {
    return query.data.value
  }
  return props.data || []
})

const effectiveTotal = computed(() => {
  if (isSchemaMode.value) {
    return query.total.value
  }
  return props.total || 0
})

const effectiveLoading = computed(() => {
  if (isSchemaMode.value) {
    return query.loading.value && query.data.value.length === 0
  }
  return props.loading || false
})

const effectiveBackgroundLoading = computed(() => {
  if (!isSchemaMode.value) return false
  return query.loading.value && query.data.value.length > 0
})

function getBackgroundLoadingText(trigger: QueryTrigger | null): string {
  if (trigger === 'filter') {
    return '正在筛选...'
  }
  if (trigger === 'sort') {
    return '正在排序...'
  }
  if (trigger === 'page') {
    return effectiveShowPager.value ? `正在加载第 ${query.currentPage.value} 页...` : '正在刷新...'
  }
  return '正在刷新...'
}

const effectiveBackgroundLoadingText = computed(() => {
  if (!effectiveBackgroundLoading.value) return ''
  return getBackgroundLoadingText(query.activeTrigger.value)
})

const effectiveBackgroundLoadingError = computed(() => {
  if (!isSchemaMode.value || query.loading.value || query.data.value.length === 0) {
    return null
  }
  return query.lastError.value ? '查询失败' : null
})

const effectiveServerSummary = computed(() => {
  if (isSchemaMode.value) {
    return query.serverSummary.value
  }
  return props.serverSummary || null
})

const effectivePageSize = computed(() => {
  if (activePageSize.value && activePageSize.value > 0) {
    return activePageSize.value
  }
  if (isSchemaMode.value && props.schema?.pageSize) {
    return props.schema.pageSize
  }
  return props.pageSize
})

const effectiveQueryMode = computed<QueryMode | undefined>(() => {
  if (isSchemaMode.value && props.schema?.queryMode !== undefined) {
    return props.schema.queryMode
  }
  return props.queryMode
})

const hasExplicitQueryMode = computed(() => effectiveQueryMode.value !== undefined)

const usesPanelQueryEntrance = computed(() => {
  return effectiveQueryMode.value === 'panel' || effectiveQueryMode.value === 'combined'
})

const effectiveShowQueryPanel = computed(() => {
  if (hasExplicitQueryMode.value) {
    return usesPanelQueryEntrance.value && !!props.querySchema
  }
  return !!props.showQueryPanel && !!props.querySchema
})

const effectiveShowFilters = computed(() => {
  if (hasExplicitQueryMode.value) {
    return effectiveQueryMode.value === 'column' || effectiveQueryMode.value === 'combined'
  }
  if (isSchemaMode.value && props.schema?.showFilters !== undefined) {
    return props.schema.showFilters
  }
  return props.showFilters
})

const effectiveShowPager = computed(() => {
  if (isSchemaMode.value && props.schema?.showPager !== undefined) {
    return props.schema.showPager
  }
  return props.showPager
})

const effectiveShowSearchToolbar = computed(() => {
  if (hasExplicitQueryMode.value) {
    return usesPanelQueryEntrance.value && !props.querySchema
  }
  return false
})

const effectiveCellCopy = computed(() => {
  if (isSchemaMode.value && props.schema?.cellCopy) {
    return props.schema.cellCopy
  }
  return props.cellCopy
})

const effectiveDensity = computed<TableDensity>(() => {
  if (isSchemaMode.value && props.schema?.density) {
    return props.schema.density
  }
  return props.density ?? 'default'
})

const effectiveInitialSlice = computed(() => {
  if (activeListViewState.value) {
    return activeListViewState.value.slice || []
  }
  return props.initialSlice
})

const effectiveSearchLayout = computed(() => {
  if (isSchemaMode.value && props.schema?.searchLayout) {
    return props.schema.searchLayout
  }
  return props.searchLayout
})

const normalizedListPresetConfig = computed<ListPresetConfig | null>(() => {
  const config = props.listPreset
  if (!config) return null

  if (config === true) {
    console.warn('[DataTableWithSearch] listPreset=true requires object config with userId and model')
    return null
  }

  if (config.enabled === false) return null
  if (!config.userId || !config.model) {
    console.warn('[DataTableWithSearch] listPreset requires userId and model')
    return null
  }
  return {
    autoLoadDefault: true,
    placement: 'toolbar-right',
    ...config,
    enabled: true
  }
})

const shouldRenderListPresetManager = computed(() => {
  const config = normalizedListPresetConfig.value
  return !!config && config.placement !== 'external'
})

// 计算搜索工具栏显示的字段
const effectiveSearchableFields = computed(() => {
  // 优先使用 props
  if (props.searchableFields && props.searchableFields.length > 0) {
    return props.searchableFields
  }
  // 其次使用 schema 配置
  if (isSchemaMode.value && props.schema?.searchableFields) {
    return props.schema.searchableFields
  }
  // 最后从 columns 中筛选 uiConfig.showInToolbar=true 的列
  const toolbarFields = effectiveColumns.value
    .filter(col => col.uiConfig?.showInToolbar === true)
    .map(col => col.name)

  return toolbarFields.length > 0 ? toolbarFields : undefined
})

// ========== 动态插槽：仅透传 column-* / filter-* 前缀插槽 ==========
const STANDARD_SLOTS = new Set(['toolbar', 'toolbar-right', 'footer', 'empty', 'row-actions'])
const dynamicSlots = computed(() => {
  const result: Record<string, unknown> = {}
  for (const name of Object.keys(parentSlots)) {
    if (!STANDARD_SLOTS.has(name) && (name.startsWith('column-') || name.startsWith('filter-'))) {
      result[name] = parentSlots[name]
    }
  }
  return result
})

// ========== 组件引用 ==========
interface SearchToolbarExpose {
  clearFilters: () => void
  getFilters: () => SliceRequestDef[]
}

interface DataTableExpose {
  resetPagination: () => void
  clearFilters: () => void
  getGridInstance: () => unknown
  getSelectedRows: () => Record<string, unknown>[]
  getSelectedCount: () => number
  clearSelection: () => void
}

const searchToolbarRef = ref<SearchToolbarExpose>()
const queryPanelRef = ref<QueryPanelExpose>()
const dataTableRef = ref<DataTableExpose>()

// ========== 筛选状态 ==========
const searchSlices = ref<SliceRequestDef[]>([])
const tableSlices = ref<SliceRequestDef[]>([])
const queryPanelSlices = ref<SliceRequestDef[]>([])

// 合并后的筛选条件（QueryPanel + SearchToolbar + DataTable header filters）
const mergedSlices = computed(() => {
  // QueryPanel 条件始终参与
  const allSlices = [...queryPanelSlices.value]
  const usedFields = new Set(allSlices.map(s => s.field))

  if (props.filterMergeMode === 'replace') {
    const source = searchSlices.value.length > 0 ? searchSlices.value : tableSlices.value
    for (const s of source) {
      if (!usedFields.has(s.field)) {
        allSlices.push(s)
        usedFields.add(s.field)
      }
    }
  } else {
    for (const s of searchSlices.value) {
      if (!usedFields.has(s.field)) {
        allSlices.push(s)
        usedFields.add(s.field)
      }
    }
    for (const s of tableSlices.value) {
      if (!usedFields.has(s.field)) {
        allSlices.push(s)
        usedFields.add(s.field)
      }
    }
  }

  return allSlices
})

// ========== Schema 模式的数据加载 ==========
async function loadData(trigger: 'mount' | 'filter' | 'sort' | 'page' | 'refresh' | 'reload' = 'refresh') {
  if (!isSchemaMode.value || !props.fetchData) return

  // 同步 slice 到 query 对象
  query.setSlice(mergedSlices.value)

  try {
    await query.loadData(trigger)
    // 加载成功后发出事件
    emit('load-success', { items: query.data.value, total: query.total.value, totalData: query.serverSummary.value ?? undefined })
  } catch (error) {
    // 未被钩子处理的错误，发出 load-error 事件
    emit('load-error', error as Error)
  }
}

function presetToListViewState(preset: ListPresetDef): ListViewState {
  return {
    columns: preset.columns,
    columnSettings: preset.columnSettings,
    slice: preset.query?.slice || [],
    orderBy: preset.query?.orderBy || [],
    pageSize: preset.pageSize
  }
}

async function applyDefaultListPresetIfNeeded() {
  const config = normalizedListPresetConfig.value
  if (!config || config.autoLoadDefault === false) return

  try {
    const preset = await getDefaultListPreset({
      userId: config.userId,
      model: config.model,
      businessKey: config.businessKey
    })
    if (preset) {
      applyListViewState(presetToListViewState(preset))
    }
  } catch (error) {
    console.warn('[DataTableWithSearch] Failed to load default list preset:', error)
  }
}

// Schema 模式下，初始化时加载数据
onMounted(async () => {
  await applyDefaultListPresetIfNeeded()
  if (isSchemaMode.value) {
    loadData('mount')
  }
})

// ========== 事件处理 ==========

// 处理 QueryPanel 查询提交
function handleQueryPanelChange(slices: SliceRequestDef[]) {
  queryPanelSlices.value = slices
}

function handleQueryPanelSearch() {
  handleFilterChange()
}

function handleQueryPanelReset() {
  queryPanelSlices.value = []
  handleFilterChange()
}

function searchQueryPanel() {
  queryPanelRef.value?.search()
}

function resetQueryPanel() {
  queryPanelRef.value?.reset()
}

// 处理搜索工具栏筛选变化（v-model 更新）
function handleSearchChange(slices: SliceRequestDef[]) {
  searchSlices.value = slices
  // 如果隐藏了搜索按钮，则实时触发筛选
  if (!props.showSearchActions) {
    handleFilterChange()
  }
}

// 处理搜索按钮点击
function handleSearch() {
  emit('search', searchSlices.value)
  handleFilterChange()
}

// 处理重置按钮点击
function handleReset() {
  searchSlices.value = []
  emit('reset')
  handleFilterChange()
}

// 处理表头筛选变化
function handleTableFilterChange(slices: SliceRequestDef[]) {
  tableSlices.value = slices
  emit('filter-change', mergedSlices.value)
}

// 处理表头筛选提交
function handleTableFilterCommit(slices: SliceRequestDef[]) {
  tableSlices.value = slices
  emit('filter-commit', mergedSlices.value)
  handleFilterChange()
}

// 统一的筛选变化处理
function handleFilterChange() {
  emit('filter-change', mergedSlices.value)

  // Schema 模式下，重置到第一页并重新加载
  if (isSchemaMode.value) {
    query.currentPage.value = 1
    loadData('filter')
  }
}

// 处理分页变化
function handlePageChange(page: number, size: number) {
  emit('page-change', page, size)
  activePageSize.value = size

  // Schema 模式下，更新分页并重新加载
  if (isSchemaMode.value) {
    query.setPage(page, size)
    loadData('page')
  }
}

// 处理排序变化
function handleSortChange(field: string | null, order: 'asc' | 'desc' | null) {
  emit('sort-change', field, order)

  // Schema 模式下，更新排序并重新加载
  if (isSchemaMode.value) {
    if (field && order) {
      query.setSort([{ field, order }])
    } else {
      query.setSort([])
    }
    loadData('sort')
  }
}

function getListViewState(): ListViewState {
  const presetColumns = effectiveColumns.value.filter(col => col.name !== '_actions')
  const columns = presetColumns.map(c => c.name)
  const columnSettings = presetColumns.map((col, index) => ({
    name: col.name,
    visible: true,
    width: col.width,
    minWidth: col.minWidth,
    fixed: col.fixed,
    order: index
  }))

  return {
    columns,
    columnSettings,
    slice: mergedSlices.value,
    orderBy: query.currentOrderBy.value ?? [],
    pageSize: effectivePageSize.value
  }
}

function applyListViewState(state: ListViewState, options: { reload?: boolean } = {}) {
  activeListViewState.value = {
    columns: state.columns || [],
    columnSettings: state.columnSettings,
    slice: state.slice || [],
    orderBy: state.orderBy || [],
    pageSize: state.pageSize
  }
  activePageSize.value = state.pageSize && state.pageSize > 0 ? state.pageSize : undefined

  searchSlices.value = []
  queryPanelSlices.value = []
  tableSlices.value = state.slice || []

  query.setSort(state.orderBy || [])

  if (isSchemaMode.value) {
    query.setPage(1, effectivePageSize.value)
    if (options.reload) {
      loadData('reload')
    }
  }
}

function resetListViewState(options: { reload?: boolean } = {}) {
  activeListViewState.value = null
  activePageSize.value = undefined
  searchSlices.value = []
  queryPanelSlices.value = []
  tableSlices.value = []
  query.setSort([])

  if (isSchemaMode.value) {
    query.setPage(1, effectivePageSize.value)
    if (options.reload) {
      loadData('reload')
    }
  }
}

async function reloadAfterListPresetApply() {
  if (isSchemaMode.value) {
    query.currentPage.value = 1
    await loadData('reload')
  }
}

// ========== DataTable props 和 events ==========
const dataTableProps = computed(() => {
  const userProps = Object.keys(attrs)
    .filter(key => !key.startsWith('on'))
    .reduce((acc, key) => ({ ...acc, [key]: attrs[key] }), {})

  return {
    columns: effectiveColumns.value,
    data: effectiveData.value,
    total: effectiveTotal.value,
    loading: effectiveLoading.value,
    backgroundLoading: effectiveBackgroundLoading.value,
    backgroundLoadingText: effectiveBackgroundLoadingText.value,
    backgroundLoadingError: effectiveBackgroundLoadingError.value,
    pageSize: effectivePageSize.value,
    showFilters: effectiveShowFilters.value,
    showPager: effectiveShowPager.value,
    initialSlice: effectiveInitialSlice.value,
    serverSummary: effectiveServerSummary.value,
    filterOptionsLoader: props.filterOptionsLoader,
    filterMemberLoader: props.filterMemberLoader,
    qmModel: props.qmModel,
    customFilterComponents: props.customFilterComponents,
    cellCopy: effectiveCellCopy.value,
    density: effectiveDensity.value,
    ...userProps
  }
})

const dataTableEvents = computed(() => {
  const userEvents: Record<string, Function> = {}
  Object.keys(attrs).forEach(key => {
    if (key.startsWith('on')) {
      const eventName = key.slice(2, 3).toLowerCase() + key.slice(3)
      userEvents[eventName] = attrs[key] as Function
    }
  })

  return {
    'page-change': (page: number, size: number) => {
      handlePageChange(page, size)
      if (userEvents['pageChange']) {
        userEvents['pageChange'](page, size)
      }
    },
    'sort-change': (field: string | null, order: 'asc' | 'desc' | null) => {
      handleSortChange(field, order)
      if (userEvents['sortChange']) {
        userEvents['sortChange'](field, order)
      }
    },
    'filter-change': handleTableFilterChange,
    'filter-commit': handleTableFilterCommit,
    'row-click': (...args: unknown[]) => {
      emit('row-click', ...args as [Record<string, unknown>, EnhancedColumnSchema])
      if (userEvents['rowClick']) {
        userEvents['rowClick'](...args)
      }
    },
    'row-dblclick': (...args: unknown[]) => {
      emit('row-dblclick', ...args as [Record<string, unknown>, EnhancedColumnSchema])
      if (userEvents['rowDblclick']) {
        userEvents['rowDblclick'](...args)
      }
    },
    'checkbox-change': (rows: Record<string, unknown>[]) => {
      emit('checkbox-change', rows)
      if (userEvents['checkboxChange']) {
        userEvents['checkboxChange'](rows)
      }
    },
    'checkbox-all': (rows: Record<string, unknown>[]) => {
      emit('checkbox-all', rows)
      if (userEvents['checkboxAll']) {
        userEvents['checkboxAll'](rows)
      }
    }
  }
})

// ========== 暴露方法 ==========
defineExpose({
  /** 获取 SearchToolbar 实例 */
  getSearchToolbar: () => searchToolbarRef.value,
  /** 获取 QueryPanel 实例 */
  getQueryPanel: () => queryPanelRef.value,
  /** 获取 DataTable 实例 */
  getDataTable: () => dataTableRef.value,
  /** 清空搜索工具栏筛选 */
  clearSearchFilters: () => searchToolbarRef.value?.clearFilters(),
  /** 清空表头筛选 */
  clearTableFilters: () => dataTableRef.value?.clearFilters(),
  /** 触发 QueryPanel 查询，提交当前表单值 */
  searchQueryPanel,
  /** 重置 QueryPanel 并刷新筛选 */
  resetQueryPanel,
  /** 清空所有筛选 */
  clearAllFilters: () => {
    queryPanelSlices.value = []
    resetQueryPanel()
    searchToolbarRef.value?.clearFilters()
    dataTableRef.value?.clearFilters()
  },
  /** 获取合并后的筛选条件 */
  getMergedFilters: () => mergedSlices.value,
  /** 重置分页 */
  resetPagination: () => dataTableRef.value?.resetPagination(),
  /** 刷新数据（仅 Schema 模式） */
  refresh: () => {
    if (isSchemaMode.value) {
      loadData('refresh')
    }
  },
  /** 重新加载（重置分页后刷新，仅 Schema 模式） */
  reload: () => {
    if (isSchemaMode.value) {
      query.currentPage.value = 1
      loadData('reload')
    }
  },
  /** 注册查询钩子（运行时） */
  addQueryHook: query.addHook,
  /** 移除查询钩子（运行时） */
  removeQueryHook: query.removeHook,
  /** 获取当前选中行数组（代理 DataTable） */
  getSelectedRows: (): Record<string, unknown>[] => dataTableRef.value?.getSelectedRows?.() ?? [],
  /** 获取当前选中行数量（代理 DataTable） */
  getSelectedCount: (): number => dataTableRef.value?.getSelectedCount?.() ?? 0,
  /** 清空选中行（代理 DataTable） */
  clearSelection: () => dataTableRef.value?.clearSelection?.(),
  /** 获取 useTableQuery 实例（高级用法） */
  getQuery: () => query,

  // ========== 保存查询功能方法 ==========
  /** 获取当前列表视图状态（用于保存自定义列表） */
  getListViewState,
  /** 应用列表视图状态（用于加载自定义列表） */
  applyListViewState,
  /** 重置列表视图状态 */
  resetListViewState,
  /** 获取当前查询状态（用于保存查询） */
  getQueryState: getListViewState,
  /** 应用查询状态（用于加载保存的查询） */
  applyQueryState: (state: { columns: string[]; slice: SliceRequestDef[]; orderBy: OrderRequestDef[] }) => {
    applyListViewState(state)
  },
  /** 获取列 Schema（用于保存查询对话框） */
  getSchema: () => effectiveColumns.value
})
</script>

<template>
  <div class="data-table-with-search">
    <!-- 传统查询区 -->
    <div v-if="effectiveShowQueryPanel" class="query-panel-wrapper">
      <QueryPanel
        ref="queryPanelRef"
        :schema="querySchema"
        :filter-member-loader="filterMemberLoader"
        :qm-model="qmModel"
        v-model="queryPanelSlices"
        @update:model-value="handleQueryPanelChange"
        @search="handleQueryPanelSearch"
        @reset="handleQueryPanelReset"
      />
    </div>

    <!-- 搜索工具栏 -->
    <div v-if="effectiveShowSearchToolbar" class="search-toolbar-wrapper">
      <SearchToolbar
        ref="searchToolbarRef"
        :columns="effectiveColumns"
        :searchable-fields="effectiveSearchableFields"
        :layout="effectiveSearchLayout"
        :show-actions="showSearchActions"
        :filter-options-loader="filterOptionsLoader"
        :filter-member-loader="filterMemberLoader"
        :qm-model="qmModel"
        v-model="searchSlices"
        @update:model-value="handleSearchChange"
        @search="handleSearch"
        @reset="handleReset"
      />
    </div>

    <!-- 数据表格 -->
    <div class="data-table-wrapper">
      <DataTable
        ref="dataTableRef"
        v-bind="dataTableProps"
        v-on="dataTableEvents"
      >
        <!-- 显式透传标准插槽，保证 HMR 热更新稳定 -->
        <template v-if="$slots.toolbar || (shouldRenderListPresetManager && normalizedListPresetConfig?.placement !== 'toolbar-right')" #toolbar>
          <slot name="toolbar" />
          <ListPresetManager
            v-if="shouldRenderListPresetManager && normalizedListPresetConfig && normalizedListPresetConfig.placement !== 'toolbar-right'"
            :config="normalizedListPresetConfig"
            :get-state="getListViewState"
            :apply-state="applyListViewState"
            :available-columns="baseColumns"
            :reload="reloadAfterListPresetApply"
          />
        </template>
        <template v-if="$slots['toolbar-right'] || (shouldRenderListPresetManager && normalizedListPresetConfig?.placement === 'toolbar-right')" #toolbar-right>
          <slot name="toolbar-right" />
          <ListPresetManager
            v-if="shouldRenderListPresetManager && normalizedListPresetConfig && normalizedListPresetConfig.placement === 'toolbar-right'"
            :config="normalizedListPresetConfig"
            :get-state="getListViewState"
            :apply-state="applyListViewState"
            :available-columns="baseColumns"
            :reload="reloadAfterListPresetApply"
          />
        </template>
        <template v-if="$slots.footer" #footer>
          <slot name="footer" />
        </template>
        <template v-if="$slots.empty" #empty>
          <slot name="empty" />
        </template>

        <!-- row-actions 标准扩展点：映射为 column-_actions cell slot -->
        <template v-if="$slots['row-actions']" #column-_actions="{ row, column, value }">
          <slot name="row-actions" :row="row" :column="column" :value="value" />
        </template>

        <!-- 透传动态前缀插槽（column-* / filter-*），保持兼容 -->
        <template v-for="(_, name) in dynamicSlots" :key="name" #[name]="slotData">
          <slot :name="name" v-bind="slotData || {}" />
        </template>
      </DataTable>
    </div>
  </div>
</template>

<style scoped>
.data-table-with-search {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  height: 100%;
}

.query-panel-wrapper {
  flex-shrink: 0;
}

.query-panel-wrapper :deep(.placeholder-text) {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.search-toolbar-wrapper {
  flex-shrink: 0;
}

.data-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.data-table-wrapper :deep(.data-table) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
