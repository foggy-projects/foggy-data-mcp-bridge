<script setup lang="ts">
import { ref, computed, watch, provide, h, useAttrs } from 'vue'
import type { VxeGridInstance, VxeGridProps, VxeGridListeners } from 'vxe-table'
import { ElMessage } from 'element-plus'
import type { EnhancedColumnSchema, PaginationState, SortState, SliceRequestDef, FilterOption, CellCopyConfig } from '@/types'
import { TextFilter, NumberRangeFilter, DateRangeFilter, SelectFilter, BoolFilter } from './filters'
import { useTableSelection, useTableSummary } from './composables'

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
  /** 自定义过滤器组件映射 */
  customFilterComponents?: Record<string, unknown>
  /** 普通单元格悬浮复制配置 */
  cellCopy?: CellCopyConfig
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50,
  showFilters: true,
  showPager: true
})

const emit = defineEmits<{
  (e: 'page-change', page: number, size: number): void
  (e: 'sort-change', field: string | null, order: 'asc' | 'desc' | null): void
  /** 过滤条件变更，使用 DSL slice 格式 */
  (e: 'filter-change', slices: SliceRequestDef[]): void
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
  /** 表格下方区域 */
  footer?: () => unknown
  /** 空数据提示 */
  empty?: () => unknown
  /** 自定义列内容 */
  [key: `column-${string}`]: (props: { row: Record<string, unknown>; column: EnhancedColumnSchema; value: unknown }) => unknown
  /** 自定义过滤器 */
  [key: `filter-${string}`]: (props: { column: EnhancedColumnSchema; field: string; modelValue: SliceRequestDef[] | null; onChange: (val: SliceRequestDef[] | null) => void }) => unknown
}>()

const gridRef = ref<VxeGridInstance>()

const hoveredCopyCell = ref<{ row: Record<string, unknown>; field: string } | null>(null)

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

// 监听 initialSlice 变化，初始化过滤器显示
watch(() => props.initialSlice, (slices) => {
  if (!slices || slices.length === 0) return

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
    'onUpdate:modelValue': (val: SliceRequestDef[] | null) => updateFilter(col.name, val)
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
      // 维度需要异步加载选项
      if (!dimensionOptionsCache.value[col.name]) {
        loadDimensionOptions(col.name)
      }
      // 维度过滤使用 $id 字段
      const filterField = getDimensionFilterField(col.name)
      return {
        ...baseProps,
        field: filterField,  // 使用正确的过滤字段（$id）
        modelValue: filterValues.value[filterField] || filterValues.value[col.name] || null,
        'onUpdate:modelValue': (val: SliceRequestDef[] | null) => updateFilter(filterField, val),
        options: dimensionOptionsCache.value[col.name] || [],
        loading: dimensionOptionsLoading.value[col.name],
        placeholder: col.title || '请选择'
      }
    }
    default:
      return baseProps
  }
}

// 更新过滤值
function updateFilter(columnName: string, value: SliceRequestDef[] | null) {
  if (value === null || value.length === 0) {
    delete filterValues.value[columnName]
  } else {
    filterValues.value[columnName] = value
  }
  emitFilterChange()
}

// 发送过滤变更事件 - 合并所有字段的 slice
function emitFilterChange() {
  const allSlices: SliceRequestDef[] = []
  for (const slices of Object.values(filterValues.value)) {
    if (slices && slices.length > 0) {
      allSlices.push(...slices)
    }
  }
  emit('filter-change', allSlices)
}

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

async function copyCellText(event: Event, value: unknown) {
  stopCopyEvent(event)
  const text = stringifyCopyValue(value)
  if (!text) return

  try {
    await navigator.clipboard.writeText(text)
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

function renderDefaultCell(col: EnhancedColumnSchema, row: Record<string, unknown>) {
  const rawValue = row[col.name]
  const displayValue = formatCellDisplayValue(col, rawValue)
  const copyEnabled = isCellCopyEnabled(col, rawValue)

  if (!copyEnabled) {
    return h('span', { class: 'data-table-cell-text' }, displayValue)
  }

  return h('div', {
    class: 'data-table-copyable-cell',
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
      onMousedown: stopCopyEvent,
      onPointerdown: stopCopyEvent,
      onClick: (event: Event) => copyCellText(event, rawValue)
    }, [
      renderCopyIcon()
    ])
  ])
}

// 切换排序
function toggleSort(field: string) {
  const current = sortState.value.field === field ? sortState.value.order : null
  let newOrder: 'asc' | 'desc' | null
  if (current === null) {
    newOrder = 'asc'
  } else if (current === 'asc') {
    newOrder = 'desc'
  } else {
    newOrder = null
  }
  sortState.value.field = newOrder ? field : null
  sortState.value.order = newOrder
  emit('sort-change', sortState.value.field, sortState.value.order)
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
          return h('div', { class: 'column-header-wrapper' }, [
            h('div', {
              class: 'column-title',
              onClick: () => toggleSort(col.name)
            }, [
              h('span', { class: 'title-text' }, col.title || col.name),
              h('span', { class: ['sort-icon', sortOrder ? `sort-${sortOrder}` : ''] },
                sortOrder === 'asc' ? ' ↑' : sortOrder === 'desc' ? ' ↓' : ' ↕'
              )
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
      onChange: (val: SliceRequestDef[] | null) => updateFilter(col.name, val)
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
    border: true,
    stripe: true,
    showOverflow: true,
    height: '100%',
    loading: props.loading,
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
    data: props.data
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
}

// 暴露方法给父组件
defineExpose({
  resetPagination,
  clearFilters,
  /** 获取当前过滤状态 (DSL slices) */
  getFilters: (): SliceRequestDef[] => {
    const allSlices: SliceRequestDef[] = []
    for (const slices of Object.values(filterValues.value)) {
      if (slices && slices.length > 0) {
        allSlices.push(...slices)
      }
    }
    return allSlices
  },
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
  updateFilter
})
</script>

<template>
  <div class="data-table">
    <!-- 工具栏：左侧插槽 + 右侧分页 -->
    <div v-if="props.showPager || $slots.toolbar" class="data-table-toolbar">
      <div class="toolbar-left">
        <slot name="toolbar" />
      </div>
      <div v-if="props.showPager" class="toolbar-right">
        <vxe-pager
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
}

.toolbar-right :deep(.vxe-pager) {
  padding: 0;
  background: transparent;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.data-table-cell-text {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-table-copyable-cell {
  position: relative;
  display: block;
  width: 100%;
  min-width: 0;
  padding-right: 24px;
}

.cell-copy-button {
  position: absolute;
  top: 50%;
  right: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  color: #606266;
  cursor: pointer;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  transform: translateY(-50%);
}

.cell-copy-button:hover {
  color: #409eff;
  border-color: #409eff;
}

.cell-copy-icon {
  width: 13px;
  height: 13px;
}

/* 表头内嵌过滤器样式 */
.column-header-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 4px 0;
  height: 60px;
  max-height: 60px;
  overflow: visible;
}

.column-title {
  display: flex;
  align-items: center;
  font-weight: 600;
  margin-bottom: 6px;
  cursor: pointer;
  user-select: none;
  line-height: 1.2;
  min-height: 18px;
}

.column-title:hover {
  color: #409eff;
}

.title-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
}

.sort-icon {
  flex-shrink: 0;
  font-size: 11px;
  color: #c0c4cc;
  margin-left: 2px;
  width: 12px;
  text-align: center;
}

.sort-icon.sort-asc,
.sort-icon.sort-desc {
  color: #409eff;
  font-weight: bold;
}

.column-filter {
  width: 100%;
  min-height: 26px;
  overflow: visible;
}

/* 过滤器组件通用样式 */
.column-filter :deep(input),
.column-filter :deep(select) {
  width: 100%;
  height: 24px;
  padding: 0 6px;
  font-size: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
}

.column-filter :deep(input:focus),
.column-filter :deep(select:focus) {
  border-color: #409eff;
  outline: none;
}

.column-filter :deep(.filter-wrapper) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* vxe-table 表头样式（需要 :deep 穿透 scoped） */
:deep(.header-with-filter) {
  height: auto !important;
}

:deep(.header-with-filter .vxe-header--column) {
  padding: 8px 4px !important;
  vertical-align: top;
}

:deep(.vxe-header--column .vxe-cell) {
  overflow: visible !important;
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
</style>
