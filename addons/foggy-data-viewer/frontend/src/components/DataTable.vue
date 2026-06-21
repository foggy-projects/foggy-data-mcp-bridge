<script setup lang="ts">
import { ref, computed, watch, provide, h, useAttrs } from 'vue'
import type { VxeGridInstance, VxeGridProps, VxeGridListeners } from 'vxe-table'
import { ElMessage, ElTooltip } from 'element-plus'
import type { EnhancedColumnSchema, PaginationState, SortState, SliceRequestDef, FilterOption, CellCopyConfig, MemberQueryRequest, MemberQueryResponse, TableDensity } from '@/types'
import { TextFilter, NumberRangeFilter, DateRangeFilter, SelectFilter, BoolFilter } from './filters'
import { useDeferredVisibility, useTableSelection, useTableSummary } from './composables'

// 禁用自动继承属性，手动控制透传到 vxe-grid
defineOptions({
  inheritAttrs: false
})

// 获取透传的属性和事件（用于传递给 vxe-grid）
const attrs = useAttrs()

/**
 * DataTable 组件属性
 *
 * 该组件设计为可扩展的业务基础组件，支持：
 * - 内嵌表头过滤器
 * - 自定义列渲染
 * - 自定义过滤器
 * - 多种插槽扩展点
 * - DSL 格式的过滤条件
 * - 透传 vxe-table 的所有属性和事件
 */
interface Props {
  /** 列配置（增强的列配置，包含前端定制） */
  columns: EnhancedColumnSchema[]
  /** 数据 */
  data: Record<string, unknown>[]
  /** 总行数 */
  total: number
  /** 加载状态 */
  loading: boolean
  /** 已有数据上的后台刷新状态 */
  backgroundLoading?: boolean
  /** 后台刷新提示文案 */
  backgroundLoadingText?: string
  /** 后台刷新失败提示 */
  backgroundLoadingError?: string | null
  /** 每页大小 */
  pageSize?: number
  /** 是否显示过滤行 */
  showFilters?: boolean
  /** 是否显示分页栏 */
  showPager?: boolean
  /** 初始过滤条件（来自后端缓存） */
  initialSlice?: SliceRequestDef[]
  /** 后端返回的全量汇总数据 */
  serverSummary?: Record<string, unknown> | null
  /** 过滤选项加载器（用于维度列） */
  filterOptionsLoader?: (columnName: string) => Promise<FilterOption[]>
  /** 远程维度成员加载器（优先于 filterOptionsLoader） */
  filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
  /** QM 模型名称（远程成员加载所需） */
  qmModel?: string
  /** 自定义过滤器组件映射 */
  customFilterComponents?: Record<string, unknown>
  /** 普通单元格悬浮复制配置 */
  cellCopy?: CellCopyConfig
  /** 表格视觉密度 */
  density?: TableDensity
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50,
  showFilters: true,
  showPager: true,
  backgroundLoading: false,
  backgroundLoadingText: '',
  backgroundLoadingError: null,
  density: 'default'
})

const emit = defineEmits<{
  (e: 'page-change', page: number, size: number): void
  (e: 'sort-change', field: string | null, order: 'asc' | 'desc' | null): void
  /** 过滤条件变更，使用 DSL slice 格式 */
  (e: 'filter-change', slices: SliceRequestDef[]): void
  /** 过滤条件提交，使用 DSL slice 格式 */
  (e: 'filter-commit', slices: SliceRequestDef[]): void
  /** 行点击事件 */
  (e: 'row-click', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  /** 行双击事件 */
  (e: 'row-dblclick', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  /** 行选中变化 */
  (e: 'checkbox-change', rows: Record<string, unknown>[]): void
  /** 全选变化 */
  (e: 'checkbox-all', rows: Record<string, unknown>[]): void
}>()

// 暴露给插槽使用的上下文
const slots = defineSlots<{
  /** 表格上方工具栏 */
  toolbar?: () => unknown
  /** 表格上方右侧工具栏，显示在分页器之前 */
  'toolbar-right'?: () => unknown
  /** 表格下方区域 */
  footer?: () => unknown
  /** 空数据提示 */
  empty?: () => unknown
  /** 自定义列内容 */
  [key: `column-${string}`]: (props: { row: Record<string, unknown>; column: EnhancedColumnSchema; value: unknown }) => unknown
  /** 自定义过滤器 */
  [key: `filter-${string}`]: (props: { column: EnhancedColumnSchema; field: string; modelValue: SliceRequestDef[] | null; onChange: (val: SliceRequestDef[] | null) => void; onCommit: (val: SliceRequestDef[] | null) => void }) => unknown
}>()

const gridRef = ref<VxeGridInstance>()

const hoveredCopyCell = ref<{ row: Record<string, unknown>; field: string } | null>(null)

const tableClass = computed(() => [
  'data-table',
  props.density === 'compact' ? 'data-table--compact' : ''
])

const overlayLoadingVisibility = useDeferredVisibility(computed(() => props.loading))
const backgroundLoadingVisibility = useDeferredVisibility(computed(() => props.backgroundLoading ?? false))

const queryStatusText = computed(() => {
  if (props.backgroundLoadingError) {
    return props.backgroundLoadingError
  }
  return props.backgroundLoadingText || ''
})

const queryStatusIsError = computed(() => !!props.backgroundLoadingError && !props.backgroundLoading)

const hasQueryStatusAnchor = computed(() => props.showPager || !!slots['toolbar-right'])

const queryStatusShouldRender = computed(() => {
  if (!queryStatusText.value || !hasQueryStatusAnchor.value) return false
  return backgroundLoadingVisibility.shouldRender.value || queryStatusIsError.value
})

const queryStatusVisible = computed(() => {
  if (queryStatusIsError.value) return true
  return backgroundLoadingVisibility.visible.value
})

const backgroundProgressShouldRender = computed(() => backgroundLoadingVisibility.shouldRender.value)
const backgroundProgressVisible = computed(() => backgroundLoadingVisibility.visible.value)

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined
}

function pickNonEmptyString(record: Record<string, unknown> | undefined, keys: string[]): string | null {
  if (!record) {
    return null
  }

  for (const key of keys) {
    const value = record[key]
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }

  return null
}

function getColumnDescription(col: EnhancedColumnSchema): string | null {
  const columnRecord = col as EnhancedColumnSchema & Record<string, unknown>
  const directDescription = pickNonEmptyString(columnRecord, [
    'description',
    'descrip',
    'describe',
    'desc',
    'comment',
    'remark',
    'tooltip',
    'helpText'
  ])

  if (directDescription) {
    return directDescription
  }

  const uiConfigDescription = pickNonEmptyString(asRecord(col.uiConfig), [
    'description',
    'descrip',
    'describe',
    'desc',
    'comment',
    'remark',
    'tooltip',
    'helpText'
  ])

  if (uiConfigDescription) {
    return uiConfigDescription
  }

  return pickNonEmptyString(asRecord(columnRecord.uiHints), [
    'description',
    'descrip',
    'describe',
    'desc',
    'comment',
    'remark',
    'tooltip',
    'helpText'
  ])
}

// 分页状态
const pagination = ref<PaginationState>({
  currentPage: 1,
  pageSize: props.pageSize,
  total: props.total
})

// 排序状态
const sortState = ref<SortState>({
  field: null,
  order: null
})

// 过滤状态：每个字段对应一组 SliceRequestDef
const filterValues = ref<Record<string, SliceRequestDef[] | null>>({})

// 维度选项缓存
const dimensionOptionsCache = ref<Record<string, FilterOption[]>>({})
const dimensionOptionsLoading = ref<Record<string, boolean>>({})

// 使用 composables
const columnsRef = computed(() => props.columns)
const { selectedRows, onCheckboxChange, onCheckboxAll, clearSelection, getSelectedCount } = useTableSelection()
const { serverSummary, calculateSelectedSummary, generateFooterData, setServerSummary } = useTableSummary(columnsRef)

// 监听 serverSummary prop 变化
watch(() => props.serverSummary, (newVal) => {
  setServerSummary(newVal ?? null)
}, { immediate: true })

// 计算 footer 数据
const footerData = computed(() => {
  const selectedSummary = calculateSelectedSummary(selectedRows.value as Record<string, unknown>[])
  // 需要获取实际的列配置（包含 checkbox 列）
  const visibleCols = tableColumns.value?.map(col => ({
    field: col.field as string | undefined,
    type: props.columns.find(c => c.name === col.field)?.type
  })) || []
  return generateFooterData(visibleCols, selectedSummary)
})

// 监听 total 变化
watch(() => props.total, (newTotal) => {
  pagination.value.total = newTotal
})

// 监听 pageSize 变化，用于应用自定义列表或外部配置时同步分页器
watch(() => props.pageSize, (newPageSize) => {
  if (newPageSize && newPageSize > 0) {
    pagination.value.pageSize = newPageSize
  }
})

// 监听 initialSlice 变化，初始化过滤器显示
watch(() => props.initialSlice, (slices) => {
  if (!slices || slices.length === 0) {
    filterValues.value = {}
    return
  }

  // 按 field 分组 slices
  const grouped: Record<string, SliceRequestDef[]> = {}
  for (const slice of slices) {
    if (!slice.field) continue
    if (!grouped[slice.field]) {
      grouped[slice.field] = []
    }
    grouped[slice.field].push(slice)
  }

  // 设置到 filterValues
  filterValues.value = grouped
}, { immediate: true })

// 加载维度选项
async function loadDimensionOptions(columnName: string): Promise<FilterOption[]> {
  if (dimensionOptionsCache.value[columnName]) {
    return dimensionOptionsCache.value[columnName]
  }

  if (!props.filterOptionsLoader) {
    return []
  }

  dimensionOptionsLoading.value[columnName] = true
  try {
    const options = await props.filterOptionsLoader(columnName)
    dimensionOptionsCache.value[columnName] = options
    return options
  } catch (e) {
    console.error('Failed to load dimension options:', e)
    return []
  } finally {
    dimensionOptionsLoading.value[columnName] = false
  }
}

// 推断过滤器类型（根据 filterType 或 type 回退）
function inferFilterType(col: EnhancedColumnSchema): string {
  const type = col.type?.toUpperCase()

  // 日期类型优先使用日期组件，即使 filterType 是 dimension
  switch (type) {
    case 'DAY':
    case 'DATE':
      return 'date'
    case 'DATETIME':
      return 'datetime'
  }

  // dictId 优先级高于 filterType：只要列声明了 dictId，就渲染为字典下拉
  if (col.dictId) {
    return 'dict'
  }

  // 其次使用后端返回的 filterType
  if (col.filterType) {
    return col.filterType
  }

  // 回退：根据 type 推断
  switch (type) {
    case 'NUMBER':
    case 'MONEY':
    case 'BIGDECIMAL':
    case 'INTEGER':
    case 'BIGINT':
    case 'LONG':
      return 'number'
    case 'BOOL':
    case 'BOOLEAN':
      return 'bool'
    case 'DICT':
      return 'dict'
    default:
      return 'text'
  }
}

// 根据列配置获取过滤器组件
function getFilterComponent(col: EnhancedColumnSchema) {
  // 优先使用自定义过滤器组件
  if (col.customFilterComponent) {
    return col.customFilterComponent
  }

  const filterType = inferFilterType(col)

  // 检查自定义过滤器组件
  if (props.customFilterComponents && props.customFilterComponents[filterType]) {
    return props.customFilterComponents[filterType]
  }

  switch (filterType) {
    case 'text':
      return TextFilter
    case 'number':
      return NumberRangeFilter
    case 'date':
      return DateRangeFilter
    case 'datetime':
      return DateRangeFilter
    case 'dict':
      return SelectFilter
    case 'dimension':
      return SelectFilter
    case 'bool':
      return BoolFilter
    default:
      return TextFilter
  }
}

// 获取维度过滤的正确字段名
// 维度列选择的是 id 值，所以过滤字段应该用 $id 后缀
function getDimensionFilterField(columnName: string): string {
  // 如果已经是 $id 结尾，直接返回
  if (columnName.endsWith('$id')) {
    return columnName
  }
  // 如果是 $caption 或其他后缀，替换为 $id
  if (columnName.includes('$')) {
    const baseName = columnName.substring(0, columnName.indexOf('$'))
    return `${baseName}$id`
  }
  // 没有后缀的维度列，添加 $id
  return `${columnName}$id`
}

// 获取过滤器属性
function getFilterProps(col: EnhancedColumnSchema) {
  const filterType = inferFilterType(col)
  const baseProps: Record<string, unknown> = {
    field: col.name,
    modelValue: filterValues.value[col.name] || null,
    'onUpdate:modelValue': (val: SliceRequestDef[] | null) => updateFilter(col.name, val),
    onCommit: (val: SliceRequestDef[] | null) => commitFilter(col.name, val)
  }

  switch (filterType) {
    case 'datetime':
      return { ...baseProps, showTime: true, format: col.format }
    case 'date':
      return { ...baseProps, format: col.format }
    case 'dict':
      return {
        ...baseProps,
        options: col.dictItems || [],
        placeholder: col.title || '请选择'
      }
    case 'dimension': {
      const memberLookup = col.memberLookup
      const hasMemberLoader = !!props.filterMemberLoader && !!props.qmModel && memberLookup?.enabled
      const filterField = memberLookup?.selectionFieldName || getDimensionFilterField(col.name)

      if (hasMemberLoader) {
        return {
          ...baseProps,
          field: col.name,
          selectionField: filterField,
          modelValue: filterValues.value[filterField] || filterValues.value[col.name] || null,
          'onUpdate:modelValue': (val: SliceRequestDef[] | null) => updateFilter(filterField, val),
          onCommit: (val: SliceRequestDef[] | null) => commitFilter(filterField, val),
          remoteLoader: props.filterMemberLoader,
          qmModel: props.qmModel,
          options: [],
          loading: false,
          placeholder: col.title || '请选择'
        }
      }

      if (!dimensionOptionsCache.value[col.name]) {
        loadDimensionOptions(col.name)
      }

      return {
        ...baseProps,
        field: filterField,
        modelValue: filterValues.value[filterField] || filterValues.value[col.name] || null,
        'onUpdate:modelValue': (val: SliceRequestDef[] | null) => updateFilter(filterField, val),
        onCommit: (val: SliceRequestDef[] | null) => commitFilter(filterField, val),
        options: dimensionOptionsCache.value[col.name] || [],
        loading: dimensionOptionsLoading.value[col.name],
        placeholder: col.title || '请选择'
      }
    }
    default:
      return baseProps
  }
}

function getAllFilterSlices(): SliceRequestDef[] {
  const allSlices: SliceRequestDef[] = []
  for (const slices of Object.values(filterValues.value)) {
    if (slices && slices.length > 0) {
      allSlices.push(...slices)
    }
  }
  return allSlices
}

function normalizeFilterValue(value: SliceRequestDef[] | null): SliceRequestDef[] | null {
  return value && value.length > 0 ? value : null
}

function areSlicesEqual(left: SliceRequestDef[] | null | undefined, right: SliceRequestDef[] | null | undefined): boolean {
  return JSON.stringify(left ?? null) === JSON.stringify(right ?? null)
}

function setFilterValue(columnName: string, value: SliceRequestDef[] | null): boolean {
  const nextValue = normalizeFilterValue(value)
  const prevValue = filterValues.value[columnName] ?? null
  if (areSlicesEqual(prevValue, nextValue)) {
    return false
  }

  if (nextValue === null) {
    delete filterValues.value[columnName]
  } else {
    filterValues.value[columnName] = nextValue
  }
  return true
}

// 更新过滤值
function updateFilter(columnName: string, value: SliceRequestDef[] | null) {
  if (setFilterValue(columnName, value)) {
    emitFilterChange()
  }
}

function commitFilter(columnName: string, value: SliceRequestDef[] | null) {
  if (setFilterValue(columnName, value)) {
    emitFilterChange()
  }
  emitFilterCommit()
}

// 发送过滤变更事件 - 合并所有字段的 slice
function emitFilterChange() {
  emit('filter-change', getAllFilterSlices())
}

function emitFilterCommit() {
  emit('filter-commit', getAllFilterSlices())
}

function isEmptyFilterValue(value: unknown): boolean {
  return value === null || value === undefined || value === ''
}

function normalizeFilterText(value: unknown): string {
  return String(value ?? '').trim().toLowerCase()
}

function stripLikeWildcard(value: unknown): string {
  return normalizeFilterText(value).replace(/^%+|%+$/g, '')
}

function parseFilterNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string') {
    const normalized = value.replace(/,/g, '').trim()
    if (!normalized) return null
    const numberValue = Number(normalized)
    return Number.isFinite(numberValue) ? numberValue : null
  }
  return null
}

function parseFilterTime(value: unknown): number | null {
  if (value instanceof Date) {
    const time = value.getTime()
    return Number.isFinite(time) ? time : null
  }
  if (typeof value === 'string' && /\d{4}-\d{1,2}-\d{1,2}/.test(value)) {
    const time = Date.parse(value.replace(' ', 'T'))
    return Number.isFinite(time) ? time : null
  }
  return null
}

function compareFilterValues(left: unknown, right: unknown): number {
  const leftNumber = parseFilterNumber(left)
  const rightNumber = parseFilterNumber(right)
  if (leftNumber !== null && rightNumber !== null) {
    return leftNumber - rightNumber
  }

  const leftTime = parseFilterTime(left)
  const rightTime = parseFilterTime(right)
  if (leftTime !== null && rightTime !== null) {
    return leftTime - rightTime
  }

  return normalizeFilterText(left).localeCompare(normalizeFilterText(right), 'zh-CN', {
    numeric: true,
    sensitivity: 'base'
  })
}

function matchesRangeSlice(fieldValue: unknown, slice: SliceRequestDef): boolean {
  const range = Array.isArray(slice.value) ? slice.value : []
  const [start, end] = range
  const op = slice.op

  if (!isEmptyFilterValue(start)) {
    const leftCompare = compareFilterValues(fieldValue, start)
    if ((op === '()' || op === '(]') ? leftCompare <= 0 : leftCompare < 0) {
      return false
    }
  }

  if (!isEmptyFilterValue(end)) {
    const rightCompare = compareFilterValues(fieldValue, end)
    if ((op === '[]' || op === '(]') ? rightCompare > 0 : rightCompare >= 0) {
      return false
    }
  }

  return true
}

function matchesFilterSlice(row: Record<string, unknown>, slice: SliceRequestDef): boolean {
  if (!slice.field || isEmptyFilterValue(slice.value)) {
    return true
  }

  if (!(slice.field in row)) {
    return true
  }

  const fieldValue = row[slice.field]
  if (isEmptyFilterValue(fieldValue)) {
    return false
  }

  switch (slice.op) {
    case '=':
      return compareFilterValues(fieldValue, slice.value) === 0
    case '!=':
    case '<>':
      return compareFilterValues(fieldValue, slice.value) !== 0
    case '>':
      return compareFilterValues(fieldValue, slice.value) > 0
    case '>=':
      return compareFilterValues(fieldValue, slice.value) >= 0
    case '<':
      return compareFilterValues(fieldValue, slice.value) < 0
    case '<=':
      return compareFilterValues(fieldValue, slice.value) <= 0
    case '[]':
    case '[)':
    case '(]':
    case '()':
      return matchesRangeSlice(fieldValue, slice)
    case 'in': {
      const values = Array.isArray(slice.value) ? slice.value : [slice.value]
      return values.some(value => compareFilterValues(fieldValue, value) === 0)
    }
    case 'right_like':
      return normalizeFilterText(fieldValue).startsWith(stripLikeWildcard(slice.value))
    case 'left_like':
      return normalizeFilterText(fieldValue).endsWith(stripLikeWildcard(slice.value))
    case 'like':
    default:
      return normalizeFilterText(fieldValue).includes(stripLikeWildcard(slice.value))
  }
}

const filteredData = computed(() => {
  const slices = getAllFilterSlices()
  if (slices.length === 0) {
    return props.data
  }
  return props.data.filter(row => slices.every(slice => matchesFilterSlice(row, slice)))
})

function formatCellDisplayValue(col: EnhancedColumnSchema, cellValue: unknown): string {
  if (col.customFormatter) {
    return col.customFormatter(cellValue)
  }

  if (col.dictItems && col.dictItems.length > 0) {
    const labelMap = new Map(col.dictItems.map(item => [String(item.value), item.label]))
    if (cellValue == null) return ''
    return labelMap.get(String(cellValue)) ?? String(cellValue)
  }

  const type = col.type?.toUpperCase()
  switch (type) {
    case 'MONEY':
    case 'NUMBER':
    case 'BIGDECIMAL':
      if (cellValue == null) return ''
      return typeof cellValue === 'number'
        ? cellValue.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        : String(cellValue)
    case 'INTEGER':
    case 'BIGINT':
    case 'LONG':
      if (cellValue == null) return ''
      return typeof cellValue === 'number'
        ? cellValue.toLocaleString('zh-CN')
        : String(cellValue)
    case 'DAY':
    case 'DATE':
      if (!cellValue) return ''
      return String(cellValue).split('T')[0]
    case 'DATETIME':
      if (!cellValue) return ''
      return String(cellValue).replace('T', ' ').substring(0, 19)
    case 'BOOL':
    case 'BOOLEAN':
      return cellValue === true ? '是' : cellValue === false ? '否' : ''
    default:
      return cellValue == null ? '' : String(cellValue)
  }
}

// 根据字段类型获取格式化配置
function getColumnFormatter(col: EnhancedColumnSchema): Partial<VxeGridProps['columns']>[number] {
  const formatter = ({ cellValue }: { cellValue: unknown }) => formatCellDisplayValue(col, cellValue)
  const type = col.type?.toUpperCase()

  if (type === 'DATETIME') {
    return {
      minWidth: 160,
      formatter
    }
  }

  if (col.customFormatter || (col.dictItems && col.dictItems.length > 0)) {
    return { formatter }
  }

  switch (type) {
    case 'MONEY':
    case 'NUMBER':
    case 'BIGDECIMAL':
    case 'INTEGER':
    case 'BIGINT':
    case 'LONG':
    case 'DAY':
    case 'DATE':
    case 'BOOL':
    case 'BOOLEAN':
      return {
        formatter
      }
    default:
      return {}
  }
}

function stringifyCopyValue(value: unknown): string {
  if (value == null) return ''
  if (['string', 'number', 'boolean', 'bigint'].includes(typeof value)) {
    return String(value)
  }
  return ''
}

function isCellCopyEnabled(col: EnhancedColumnSchema, value: unknown): boolean {
  if (col.name === '_actions') return false
  const copyable = col.copyable ?? (props.cellCopy?.enabled !== false)
  if (!copyable) return false
  return stringifyCopyValue(value) !== ''
}

function isCopyCellActive(row: Record<string, unknown>, field: string): boolean {
  return hoveredCopyCell.value?.row === row && hoveredCopyCell.value.field === field
}

function stopCopyEvent(event: Event) {
  event.preventDefault()
  event.stopPropagation()
}

async function writeClipboardText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.top = '0'
  textarea.style.left = '-9999px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()

  try {
    const copied = document.execCommand('copy')
    if (!copied) {
      throw new Error('document.execCommand copy returned false')
    }
  } finally {
    document.body.removeChild(textarea)
  }
}

async function copyCellText(event: Event, value: unknown) {
  stopCopyEvent(event)
  const text = stringifyCopyValue(value)
  if (!text) return

  try {
    await writeClipboardText(text)
  } catch (error) {
    console.warn('Failed to copy cell value:', error)
    ElMessage.warning('复制失败，请手动复制')
  }
}

function renderCopyIcon() {
  return h('svg', {
    class: 'cell-copy-icon',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    'stroke-width': '2',
    'stroke-linecap': 'round',
    'stroke-linejoin': 'round',
    'aria-hidden': 'true'
  }, [
    h('rect', { x: '9', y: '9', width: '13', height: '13', rx: '2', ry: '2' }),
    h('path', { d: 'M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1' })
  ])
}

function renderHeaderHelpIcon() {
  return h('svg', {
    class: 'column-help-svg',
    style: {
      display: 'block',
      width: '14px',
      height: '14px'
    },
    viewBox: '0 0 16 16',
    width: '14',
    height: '14',
    fill: 'none',
    xmlns: 'http://www.w3.org/2000/svg',
    'aria-hidden': 'true'
  }, [
    h('circle', { cx: '8', cy: '8', r: '7', fill: '#18a058' }),
    h('path', {
      d: 'M5.85 6.15A2.2 2.2 0 0 1 8.1 4.2c1.25 0 2.15.76 2.15 1.86 0 .8-.43 1.25-1.14 1.78-.65.48-.86.77-.86 1.46',
      stroke: '#fff',
      'stroke-width': '1.35',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round'
    }),
    h('circle', { cx: '8.25', cy: '11.55', r: '0.72', fill: '#fff' })
  ])
}

function stopSortEvent(event: Event) {
  event.preventDefault()
  event.stopPropagation()
}

function applySort(field: string, order: 'asc' | 'desc') {
  const isSameSort = sortState.value.field === field && sortState.value.order === order
  sortState.value.field = isSameSort ? null : field
  sortState.value.order = isSameSort ? null : order
  emit('sort-change', sortState.value.field, sortState.value.order)
}

function renderSortArrow(field: string, title: string, sortOrder: 'asc' | 'desc' | null, order: 'asc' | 'desc') {
  const activeColor = '#409eff'
  const idleColor = '#909399'
  const isAsc = order === 'asc'
  const isActive = sortOrder === order
  const hitboxX = isAsc ? 0 : 12
  const pathD = isAsc
    ? 'M6 4 1 10h10L6 4z'
    : 'M18 10 13 4h10L18 10z'

  return h('g', {
    class: ['sort-arrow-control', `sort-arrow-control-${order}`, isActive ? 'active' : ''],
    role: 'button',
    tabindex: 0,
    'aria-label': `${title}${isAsc ? '升序' : '降序'}排序`,
    style: { cursor: 'pointer' },
    onClick: (event: MouseEvent) => {
      stopSortEvent(event)
      applySort(field, order)
    },
    onMousedown: stopSortEvent,
    onKeydown: (event: KeyboardEvent) => {
      if (event.key === 'Enter' || event.key === ' ') {
        stopSortEvent(event)
        applySort(field, order)
      }
    }
  }, [
    h('rect', {
      class: ['sort-arrow-hitbox', `sort-arrow-hitbox-${order}`],
      x: hitboxX,
      y: 0,
      width: 12,
      height: 14,
      fill: 'transparent'
    }),
    h('path', {
      class: ['sort-arrow', `sort-arrow-${order}`, isActive ? 'active' : ''],
      d: pathD,
      fill: isActive ? activeColor : idleColor,
      'pointer-events': 'none'
    })
  ])
}

function renderSortIcon(field: string, title: string, sortOrder: 'asc' | 'desc' | null) {
  return h('span', {
    class: ['sort-icon', 'sort-icon-horizontal', sortOrder ? `sort-${sortOrder}` : 'sort-none'],
    style: {
      display: 'inline-flex',
      flex: '0 0 auto',
      alignItems: 'center',
      justifyContent: 'center',
      width: '26px',
      height: '16px',
      marginLeft: 'auto',
      lineHeight: '1'
    },
    'aria-hidden': 'true'
  }, [
    h('svg', {
      class: 'sort-icon-svg',
      style: {
        display: 'block',
        width: '24px',
        height: '14px',
        overflow: 'visible'
      },
      viewBox: '0 0 24 14',
      width: '24',
      height: '14',
      xmlns: 'http://www.w3.org/2000/svg',
      focusable: 'false'
    }, [
      renderSortArrow(field, title, sortOrder, 'asc'),
      renderSortArrow(field, title, sortOrder, 'desc')
    ])
  ])
}

function renderDefaultCell(col: EnhancedColumnSchema, row: Record<string, unknown>) {
  const rawValue = row[col.name]
  const displayValue = formatCellDisplayValue(col, rawValue)
  const copyEnabled = isCellCopyEnabled(col, rawValue)

  if (!copyEnabled) {
    return h('span', { class: 'data-table-cell-text' }, displayValue)
  }

  return h('div', {
    class: 'data-table-copyable-cell',
    style: {
      position: 'relative',
      display: 'flex',
      alignItems: 'center',
      width: '100%',
      minWidth: '0',
      maxWidth: '100%',
      boxSizing: 'border-box',
      paddingRight: '24px'
    },
    onMouseenter: () => {
      hoveredCopyCell.value = { row, field: col.name }
    },
    onMouseleave: () => {
      if (isCopyCellActive(row, col.name)) {
        hoveredCopyCell.value = null
      }
    }
  }, [
    h('span', { class: 'data-table-cell-text' }, displayValue),
    isCopyCellActive(row, col.name) && h('button', {
      type: 'button',
      class: 'cell-copy-button',
      title: '复制',
      'aria-label': '复制单元格内容',
      style: {
        position: 'absolute',
        top: '50%',
        right: '2px',
        zIndex: '2',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: '18px',
        height: '18px',
        minWidth: '18px',
        maxWidth: '18px',
        padding: '0',
        lineHeight: '1',
        boxSizing: 'border-box',
        transform: 'translateY(-50%)'
      },
      onMousedown: stopCopyEvent,
      onPointerdown: stopCopyEvent,
      onClick: (event: Event) => copyCellText(event, rawValue)
    }, [
      renderCopyIcon()
    ])
  ])
}

// 生成 vxe-table 列配置
const tableColumns = computed<VxeGridProps['columns']>(() => {
  // 依赖 sortState 确保排序变化时重新计算
  const currentSort = sortState.value

  // checkbox 列
  const checkboxColumn = {
    type: 'checkbox' as const,
    width: 50,
    fixed: 'left' as const
  }

  const dataColumns = props.columns.map(col => {
    const colConfig: Record<string, unknown> = {
      field: col.name,
      title: col.title || col.name,
      width: col.width,
      minWidth: col.minWidth ?? 120,
      fixed: col.fixed,
      sortable: false, // 禁用 vxe-table 内置排序，我们自己处理
      ...getColumnFormatter(col),
      // 使用 slots 在表头渲染过滤器
      slots: {
        header: () => {
          // 在渲染时获取排序状态
          const sortOrder = currentSort.field === col.name ? currentSort.order : null
          const description = getColumnDescription(col)
          return h('div', {
            class: 'column-header-wrapper',
            style: {
              display: 'flex',
              flexDirection: 'column',
              width: '100%',
              padding: props.density === 'compact' ? '2px 0' : '4px 0',
              height: props.density === 'compact' ? '48px' : '60px',
              maxHeight: props.density === 'compact' ? '48px' : '60px',
              overflow: 'hidden'
            }
          }, [
            h('div', {
              class: 'column-title',
              style: {
                display: 'flex',
                alignItems: 'center',
                width: '100%',
                boxSizing: 'border-box',
                fontWeight: '600',
                marginBottom: props.density === 'compact' ? '3px' : '6px',
                cursor: 'default',
                userSelect: 'none',
                lineHeight: '1.2',
                minHeight: props.density === 'compact' ? '16px' : '18px',
                minWidth: '0',
                gap: '3px'
              }
            }, [
              h('span', {
                class: 'title-text',
                style: {
                  flex: '0 1 auto',
                  minWidth: '0',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  fontSize: '13px'
                }
              }, col.title || col.name),
              description && h(ElTooltip, {
                content: description,
                placement: 'top',
                showAfter: 120,
                teleported: true
              }, {
                default: () => h('span', {
                  class: 'column-help-icon',
                  style: {
                    display: 'inline-flex',
                    flex: '0 0 auto',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: '14px',
                    height: '14px',
                    lineHeight: '1',
                    cursor: 'help'
                  },
                  title: description,
                  'aria-label': description,
                  role: 'img',
                  onClick: (event: Event) => event.stopPropagation(),
                  onMousedown: (event: Event) => event.stopPropagation()
                }, [
                  renderHeaderHelpIcon()
                ])
              }),
              renderSortIcon(col.name, col.title || col.name, sortOrder)
            ]),
            props.showFilters && h('div', { class: 'column-filter' }, [
              renderFilterComponent(col)
            ])
          ])
        }
      }
    }

    // 检查是否有自定义列插槽
    const slotName = `column-${col.name}`
    if (slots[slotName]) {
      colConfig.slots = {
        ...colConfig.slots as object,
        default: ({ row }: { row: Record<string, unknown> }) => {
          return slots[slotName]!({ row, column: col, value: row[col.name] })
        }
      }
    } else if (col.customRender) {
      // 使用自定义渲染函数
      colConfig.slots = {
        ...colConfig.slots as object,
        default: ({ row }: { row: Record<string, unknown> }) => {
          return col.customRender!({ row, value: row[col.name] })
        }
      }
    } else {
      colConfig.slots = {
        ...colConfig.slots as object,
        default: ({ row }: { row: Record<string, unknown> }) => renderDefaultCell(col, row)
      }
    }

    return colConfig
  })

  // 返回 checkbox 列 + 数据列
  return [checkboxColumn, ...dataColumns]
})

// 渲染过滤器组件
function renderFilterComponent(col: EnhancedColumnSchema) {
  // 检查自定义过滤器插槽
  const filterSlotName = `filter-${col.name}`
  if (slots[filterSlotName]) {
    return slots[filterSlotName]!({
      column: col,
      field: col.name,
      modelValue: filterValues.value[col.name] || null,
      onChange: (val: SliceRequestDef[] | null) => updateFilter(col.name, val),
      onCommit: (val: SliceRequestDef[] | null) => commitFilter(col.name, val)
    })
  }

  // 使用内置过滤器
  const FilterComponent = getFilterComponent(col)
  const filterProps = getFilterProps(col)
  return h(FilterComponent, filterProps)
}

// Grid 配置（合并默认配置和用户传入的属性）
const gridOptions = computed<VxeGridProps>(() => {
  // 提取事件监听器（on 开头的属性）
  const userProps = Object.keys(attrs)
    .filter(key => !key.startsWith('on'))
    .reduce((acc, key) => ({ ...acc, [key]: attrs[key] }), {})

  // 默认配置
  const defaultOptions: VxeGridProps = {
    size: 'small',
    border: true,
    stripe: true,
    showOverflow: true,
    height: '100%',
    loading: overlayLoadingVisibility.visible.value,
    columnConfig: {
      resizable: true
    },
    rowConfig: {
      isHover: true
    },
    // checkbox 配置
    checkboxConfig: {
      highlight: true,
      trigger: 'cell'
    },
    // footer 配置
    showFooter: true,
    footerData: footerData.value,
    // 表头行高需要容纳过滤器
    headerRowClassName: props.showFilters ? 'header-with-filter' : '',
    // 禁用内置分页，使用自定义 toolbar 中的分页组件
    pagerConfig: {
      enabled: false
    },
    // 禁用 vxe-table 内置排序，我们自己处理
    sortConfig: {
      remote: true
    },
    columns: tableColumns.value,
    data: filteredData.value
  }

  // 合并：用户传入的属性覆盖默认值
  return { ...defaultOptions, ...userProps }
})

// 事件处理（合并默认事件和用户传入的事件）
const gridEvents = computed<VxeGridListeners>(() => {
  // 提取用户传入的事件监听器（on 开头的属性，转换为驼峰）
  const userEvents: Record<string, Function> = {}
  Object.keys(attrs).forEach(key => {
    if (key.startsWith('on')) {
      // onPageChange -> pageChange
      const eventName = key.slice(2, 3).toLowerCase() + key.slice(3)
      userEvents[eventName] = attrs[key] as Function
    }
  })

  // 创建事件包装函数：先执行默认逻辑，再执行用户监听器
  const wrapEvent = (defaultHandler: Function, eventName: string) => {
    return (...args: any[]) => {
      // 先执行默认逻辑
      defaultHandler(...args)
      // 再执行用户传入的监听器
      if (userEvents[eventName]) {
        userEvents[eventName](...args)
      }
    }
  }

  // 默认事件处理器
  const defaultEvents: VxeGridListeners = {
    pageChange: wrapEvent(({ currentPage, pageSize }) => {
      pagination.value.currentPage = currentPage
      pagination.value.pageSize = pageSize
      emit('page-change', currentPage, pageSize)
    }, 'pageChange'),
    cellClick: wrapEvent(({ row, column }) => {
      const col = props.columns.find(c => c.name === column.field)
      if (col) {
        emit('row-click', row, col)
      }
    }, 'cellClick'),
    cellDblclick: wrapEvent(({ row, column }) => {
      const col = props.columns.find(c => c.name === column.field)
      if (col) {
        emit('row-dblclick', row, col)
      }
    }, 'cellDblclick'),
    checkboxChange: wrapEvent((params: { records: Record<string, unknown>[] }) => {
      onCheckboxChange(params)
      emit('checkbox-change', params.records)
    }, 'checkboxChange'),
    checkboxAll: wrapEvent((params: { records: Record<string, unknown>[] }) => {
      onCheckboxAll(params)
      emit('checkbox-all', params.records)
    }, 'checkboxAll')
  }

  // 合并：添加用户定义但我们没有默认处理的事件
  const mergedEvents = { ...defaultEvents }
  Object.keys(userEvents).forEach(eventName => {
    if (!mergedEvents[eventName as keyof VxeGridListeners]) {
      mergedEvents[eventName as keyof VxeGridListeners] = userEvents[eventName] as any
    }
  })

  return mergedEvents
})

// 处理顶部分页组件的变化
function handlePagerChange({ currentPage, pageSize }: { currentPage: number; pageSize: number }) {
  pagination.value.currentPage = currentPage
  pagination.value.pageSize = pageSize
  emit('page-change', currentPage, pageSize)
}

// 重置分页
function resetPagination() {
  pagination.value.currentPage = 1
}

// 清除所有过滤
function clearFilters() {
  filterValues.value = {}
  emitFilterChange()
  emitFilterCommit()
}

// 暴露方法给父组件
defineExpose({
  resetPagination,
  clearFilters,
  /** 获取当前过滤状态 (DSL slices) */
  getFilters: getAllFilterSlices,
  /** 设置过滤值 */
  setFilter: updateFilter,
  /** 获取 vxe-grid 实例 */
  getGridInstance: () => gridRef.value,
  /** 获取当前选中行数组 */
  getSelectedRows: (): Record<string, unknown>[] => selectedRows.value as Record<string, unknown>[],
  /** 获取当前选中行数量 */
  getSelectedCount: (): number => getSelectedCount(),
  /** 清空选中行 */
  clearSelection
})

// 提供上下文给子组件
provide('dataTableContext', {
  columns: computed(() => props.columns),
  filters: filterValues,
  updateFilter,
  commitFilter
})
</script>

<template>
  <div :class="tableClass">
    <!-- 工具栏：左侧插槽 + 右侧分页 -->
    <div v-if="props.showPager || $slots.toolbar || $slots['toolbar-right']" class="data-table-toolbar">
      <div class="toolbar-left">
        <slot name="toolbar" />
      </div>
      <div v-if="props.showPager || $slots['toolbar-right']" class="toolbar-right">
        <slot name="toolbar-right" />
        <div
          v-if="queryStatusShouldRender"
          class="data-table-query-status"
          :class="{ 'is-visible': queryStatusVisible, 'is-error': queryStatusIsError }"
          aria-live="polite"
        >
          <span
            v-if="!queryStatusIsError"
            class="data-table-query-spinner"
            aria-hidden="true"
          />
          <span class="data-table-query-status-text">{{ queryStatusText }}</span>
        </div>
        <vxe-pager
          v-if="props.showPager"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[20, 50, 100, 200]"
          :layouts="['PrevPage', 'JumpNumber', 'NextPage', 'Sizes', 'Total']"
          @page-change="handlePagerChange"
        />
      </div>
    </div>

    <!-- 表格主体（过滤器已移入表头） -->
    <div class="table-wrapper">
      <div
        v-if="backgroundProgressShouldRender"
        class="data-table-progress-line"
        :class="{ 'is-visible': backgroundProgressVisible }"
        aria-hidden="true"
      />
      <vxe-grid
        ref="gridRef"
        v-bind="gridOptions"
        v-on="gridEvents"
      >
        <!-- 空数据插槽 -->
        <template #empty>
          <slot name="empty">
            <div class="empty-data">暂无数据</div>
          </slot>
        </template>
      </vxe-grid>
    </div>

    <!-- 底部插槽 -->
    <div v-if="$slots.footer" class="data-table-footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<style scoped>
.data-table {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: white;
  border-radius: 4px;
  overflow: hidden;
}

.data-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
  gap: 16px;
}

.data-table--compact .data-table-toolbar {
  padding: 6px 12px;
}

.data-table--compact :deep(.vxe-table--render-default.size--small .vxe-body--column.is--padding > .vxe-cell),
.data-table--compact :deep(.vxe-table--render-default.size--small .vxe-footer--column.is--padding > .vxe-cell) {
  padding-top: 4px;
  padding-bottom: 4px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 1;
  gap: 8px;
}

.toolbar-right :deep(.vxe-pager) {
  padding: 0;
  background: transparent;
}

.data-table-query-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  max-width: 160px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 1;
  color: #606266;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translateY(-1px);
  transition: opacity 160ms ease, transform 160ms ease;
}

.data-table-query-status.is-visible {
  opacity: 1;
  transform: translateY(0);
}

.data-table-query-status.is-error {
  color: #c45656;
}

.data-table-query-spinner {
  flex: 0 0 auto;
  width: 12px;
  height: 12px;
  border: 2px solid #dcdfe6;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: data-table-query-spin 700ms linear infinite;
}

.data-table-query-status-text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-wrapper {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.data-table-progress-line {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 5;
  width: 100%;
  height: 2px;
  overflow: hidden;
  pointer-events: none;
  opacity: 0;
  transition: opacity 160ms ease;
}

.data-table-progress-line.is-visible {
  opacity: 1;
}

.data-table-progress-line::before {
  position: absolute;
  top: 0;
  left: -40%;
  width: 40%;
  height: 100%;
  content: "";
  background: linear-gradient(90deg, transparent, #409eff, transparent);
  animation: data-table-progress-slide 1.1s ease-in-out infinite;
}

.data-table :deep(.data-table-cell-text) {
  display: block;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-table :deep(.vxe-body-cell--wrapper) {
  width: 100%;
  min-width: 0;
}

.data-table :deep(.data-table-copyable-cell) {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  padding-right: 24px;
}

.data-table :deep(.cell-copy-button) {
  position: absolute;
  top: 50%;
  right: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  width: 18px;
  min-width: 18px;
  max-width: 18px;
  height: 18px;
  padding: 0;
  line-height: 1;
  color: #b8bcc4;
  cursor: pointer;
  background: transparent;
  border: 0;
  outline: none;
  border-radius: 3px;
  box-sizing: border-box;
  transform: translateY(-50%);
  opacity: 0.78;
  transition:
    color 120ms ease,
    opacity 120ms ease,
    background-color 120ms ease;
}

.data-table :deep(.cell-copy-button:hover) {
  color: #8d939d;
  background: rgba(144, 147, 153, 0.08);
  opacity: 1;
}

.data-table :deep(.cell-copy-button:active) {
  color: #6b7280;
  background: rgba(144, 147, 153, 0.12);
}

.data-table :deep(.cell-copy-icon) {
  display: block;
  flex: 0 0 auto;
  width: 13px;
  height: 13px;
  pointer-events: none;
}

/* 表头内嵌过滤器样式 */
.column-header-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 4px 0;
  height: 60px;
  max-height: 60px;
  overflow: hidden;
}

.column-title {
  display: flex;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
  font-weight: 600;
  margin-bottom: 6px;
  cursor: pointer;
  user-select: none;
  line-height: 1.2;
  min-height: 18px;
  min-width: 0;
  gap: 3px;
}

.data-table--compact .column-header-wrapper {
  padding: 2px 0;
  height: 48px;
  max-height: 48px;
}

.data-table--compact .column-title {
  margin-bottom: 3px;
  min-height: 16px;
}

.column-title:hover {
  color: #409eff;
}

.title-text {
  flex: 0 1 auto;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
}

.column-help-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  line-height: 1;
  cursor: help;
}

.column-help-svg {
  display: block;
  width: 14px;
  height: 14px;
}

.sort-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 16px;
  margin-left: auto;
  line-height: 1;
}

.sort-icon-svg {
  display: block;
  width: 24px;
  height: 14px;
  overflow: visible;
}

.column-title:hover .sort-arrow:not(.active) {
  fill: #606266;
}

.column-filter {
  width: 100%;
  min-height: 26px;
  min-width: 0;
  overflow: hidden;
}

.data-table--compact .column-filter {
  min-height: 22px;
}

/* 过滤器组件通用样式 */
.column-filter :deep(input:not(.el-range-input):not(.el-input__inner)),
.column-filter :deep(select) {
  width: 100%;
  height: 24px;
  padding: 0 6px;
  font-size: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
}

.data-table--compact .column-filter :deep(input:not(.el-range-input):not(.el-input__inner)),
.data-table--compact .column-filter :deep(select) {
  height: 22px;
  padding: 0 5px;
  font-size: 12px;
}

.column-filter :deep(input:not(.el-range-input):not(.el-input__inner):focus),
.column-filter :deep(select:focus) {
  border-color: #409eff;
  outline: none;
}

.column-filter :deep(.filter-wrapper) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.column-filter :deep(.filter-text),
.column-filter :deep(.filter-select),
.column-filter :deep(.input-wrapper),
.column-filter :deep(.select-input) {
  min-width: 0;
}

.column-filter :deep(.placeholder-text),
.column-filter :deep(.selected-text) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* vxe-table 表头样式（需要 :deep 穿透 scoped） */
:deep(.header-with-filter) {
  height: auto !important;
}

:deep(.header-with-filter .vxe-header--column) {
  padding: 8px 4px !important;
  vertical-align: top;
}

.data-table--compact :deep(.header-with-filter .vxe-header--column) {
  padding: 5px 4px !important;
}

:deep(.vxe-header--column .vxe-cell) {
  overflow: hidden !important;
}

/* 下拉框已通过 Teleport 渲染到 body，不再受表头 overflow 裁切 */

.data-table-footer {
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
}

.empty-data {
  padding: 40px;
  text-align: center;
  color: #909399;
}

@keyframes data-table-query-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes data-table-progress-slide {
  0% {
    transform: translateX(0);
  }

  100% {
    transform: translateX(350%);
  }
}
</style>

<style>
/*
 * Header slot content is rendered inside vxe-grid. In some host apps Vue scoped
 * attributes are not preserved on that slot subtree, so these styles must not
 * rely on DataTable.vue's scoped data-v attribute.
 */
.data-table .column-filter {
  width: 100%;
  min-height: 26px;
  min-width: 0;
  overflow: hidden;
}

.data-table.data-table--compact .column-filter {
  min-height: 22px;
}

.data-table .column-filter input:not(.el-range-input):not(.el-input__inner),
.data-table .column-filter select {
  width: 100%;
  height: 24px;
  padding: 0 6px;
  font-size: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
  box-sizing: border-box;
}

.data-table.data-table--compact .column-filter input:not(.el-range-input):not(.el-input__inner),
.data-table.data-table--compact .column-filter select {
  height: 22px;
  padding: 0 5px;
  font-size: 12px;
}

.data-table .column-filter input:not(.el-range-input):not(.el-input__inner):focus,
.data-table .column-filter select:focus {
  border-color: #409eff;
  outline: none;
}

.data-table .column-filter .filter-text,
.data-table .column-filter .filter-select,
.data-table .column-filter .filter-date-range {
  width: 100%;
  min-width: 0;
}

.data-table .column-filter .input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
}

.data-table .column-filter .input-wrapper input {
  padding-right: 24px;
}

.data-table .column-filter .filter-number-range,
.data-table .column-filter .filter-bool {
  display: flex;
  align-items: center;
  gap: 4px;
}

.data-table .column-filter .filter-number-range {
  position: relative;
}

.data-table .column-filter .filter-number-range input {
  flex: 1;
  min-width: 0;
  width: 50px;
  text-align: right;
}

.data-table .column-filter .filter-number-range .separator {
  flex-shrink: 0;
  color: #909399;
  font-size: 12px;
}

.data-table .column-filter .filter-bool button {
  height: 24px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
  font-size: 11px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  box-sizing: border-box;
}

.data-table.data-table--compact .column-filter .filter-bool button {
  height: 22px;
}

.data-table .column-filter .filter-bool button:hover {
  border-color: #409eff;
  color: #409eff;
}

.data-table .column-filter .filter-bool button.active {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}

.data-table .column-filter .select-input {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
  cursor: pointer;
  gap: 4px;
  box-sizing: border-box;
}

.data-table.data-table--compact .column-filter .select-input {
  height: 22px;
  padding: 0 5px;
}

.data-table .column-filter .select-input:hover {
  border-color: #c0c4cc;
}

.data-table .column-filter .placeholder-text,
.data-table .column-filter .selected-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-table .column-filter .placeholder-text {
  color: #c0c4cc;
}

.data-table .column-filter .selected-text {
  color: #606266;
}

.data-table .column-filter .toggle-multi {
  padding: 1px 4px;
  font-size: 9px;
  color: #fff;
  background: #409eff;
  border-radius: 2px;
  cursor: pointer;
  line-height: 1.2;
}

.data-table .column-filter .clear-btn {
  color: #c0c4cc;
  cursor: pointer;
  font-size: 12px;
}

.data-table .column-filter .clear-btn:hover {
  color: #909399;
}

.data-table .column-filter .input-wrapper .clear-btn {
  position: absolute;
  right: 6px;
  font-size: 14px;
}

.data-table .column-filter .filter-number-range .clear-btn {
  position: absolute;
  right: -16px;
  font-size: 14px;
}

.data-table .column-filter .filter-date-range .el-date-editor {
  width: 100% !important;
  max-width: 100%;
  height: 24px;
}

.data-table.data-table--compact .column-filter .filter-date-range .el-date-editor {
  height: 22px;
}

.data-table .column-filter .filter-date-range .el-input__wrapper {
  height: 24px;
  padding: 0 4px;
  box-sizing: border-box;
}

.data-table.data-table--compact .column-filter .filter-date-range .el-input__wrapper {
  height: 22px;
}

.data-table .column-filter .filter-date-range .el-range-input,
.data-table .column-filter .filter-date-range .el-range-separator {
  font-size: 11px;
}

.data-table .column-filter .filter-date-range .el-range-separator {
  flex-shrink: 0;
  padding: 0 2px;
  line-height: 22px;
}
</style>
