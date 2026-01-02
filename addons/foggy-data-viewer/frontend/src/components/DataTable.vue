<script setup lang="ts">
import { ref, computed, watch, provide, h } from 'vue'
import type { VxeGridInstance, VxeGridProps, VxeGridListeners } from 'vxe-table'
import type { ColumnSchema, PaginationState, SortState, SliceRequestDef, FilterOption } from '@/types'
import { TextFilter, NumberRangeFilter, DateRangeFilter, SelectFilter, BoolFilter } from './filters'

/**
 * DataTable 组件属性
 *
 * 该组件设计为可扩展的业务基础组件，支持：
 * - 内嵌表头过滤器
 * - 自定义列渲染
 * - 自定义过滤器
 * - 多种插槽扩展点
 * - DSL 格式的过滤条件
 */
interface Props {
  /** 列配置 */
  columns: ColumnSchema[]
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
  /** 初始过滤条件（来自后端缓存） */
  initialSlice?: SliceRequestDef[]
  /** 过滤选项加载器（用于维度列） */
  filterOptionsLoader?: (columnName: string) => Promise<FilterOption[]>
  /** 自定义过滤器组件映射 */
  customFilterComponents?: Record<string, unknown>
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50,
  showFilters: true
})

const emit = defineEmits<{
  (e: 'page-change', page: number, size: number): void
  (e: 'sort-change', field: string | null, order: 'asc' | 'desc' | null): void
  /** 过滤条件变更，使用 DSL slice 格式 */
  (e: 'filter-change', slices: SliceRequestDef[]): void
  /** 行点击事件 */
  (e: 'row-click', row: Record<string, unknown>, column: ColumnSchema): void
  /** 行双击事件 */
  (e: 'row-dblclick', row: Record<string, unknown>, column: ColumnSchema): void
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
  [key: `column-${string}`]: (props: { row: Record<string, unknown>; column: ColumnSchema; value: unknown }) => unknown
  /** 自定义过滤器 */
  [key: `filter-${string}`]: (props: { column: ColumnSchema; field: string; modelValue: SliceRequestDef[] | null; onChange: (val: SliceRequestDef[] | null) => void }) => unknown
}>()

const gridRef = ref<VxeGridInstance>()

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
function inferFilterType(col: ColumnSchema): string {
  const type = col.type?.toUpperCase()

  // 日期类型优先使用日期组件，即使 filterType 是 dimension
  switch (type) {
    case 'DAY':
    case 'DATE':
      return 'date'
    case 'DATETIME':
      return 'datetime'
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
function getFilterComponent(col: ColumnSchema) {
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

// 获取过滤器属性
function getFilterProps(col: ColumnSchema) {
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
    case 'dimension':
      // 维度需要异步加载选项
      if (!dimensionOptionsCache.value[col.name]) {
        loadDimensionOptions(col.name)
      }
      return {
        ...baseProps,
        options: dimensionOptionsCache.value[col.name] || [],
        loading: dimensionOptionsLoading.value[col.name],
        placeholder: col.title || '请选择'
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

// 根据字段类型获取格式化配置
function getColumnFormatter(type: string): Partial<VxeGridProps['columns']>[number] {
  switch (type?.toUpperCase()) {
    case 'MONEY':
    case 'NUMBER':
    case 'BIGDECIMAL':
      return {
        formatter: ({ cellValue }) => {
          if (cellValue == null) return ''
          return typeof cellValue === 'number'
            ? cellValue.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
            : cellValue
        }
      }
    case 'INTEGER':
    case 'BIGINT':
    case 'LONG':
      return {
        formatter: ({ cellValue }) => {
          if (cellValue == null) return ''
          return typeof cellValue === 'number'
            ? cellValue.toLocaleString('zh-CN')
            : cellValue
        }
      }
    case 'DAY':
    case 'DATE':
      return {
        formatter: ({ cellValue }) => {
          if (!cellValue) return ''
          return String(cellValue).split('T')[0]
        }
      }
    case 'DATETIME':
      return {
        minWidth: 160,
        formatter: ({ cellValue }) => {
          if (!cellValue) return ''
          return String(cellValue).replace('T', ' ').substring(0, 19)
        }
      }
    case 'BOOL':
    case 'BOOLEAN':
      return {
        formatter: ({ cellValue }) => {
          return cellValue === true ? '是' : cellValue === false ? '否' : ''
        }
      }
    default:
      return {}
  }
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

  return props.columns.map(col => {
    const colConfig: Record<string, unknown> = {
      field: col.name,
      title: col.title || col.name,
      minWidth: 120,
      sortable: false, // 禁用 vxe-table 内置排序，我们自己处理
      ...getColumnFormatter(col.type),
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
    }

    return colConfig
  })
})

// 渲染过滤器组件
function renderFilterComponent(col: ColumnSchema) {
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

// Grid 配置
const gridOptions = computed<VxeGridProps>(() => ({
  border: true,
  stripe: true,
  showOverflow: true,
  height: 'auto',
  loading: props.loading,
  columnConfig: {
    resizable: true
  },
  rowConfig: {
    isHover: true
  },
  // 表头行高需要容纳过滤器
  headerRowClassName: props.showFilters ? 'header-with-filter' : '',
  pagerConfig: {
    enabled: true,
    currentPage: pagination.value.currentPage,
    pageSize: pagination.value.pageSize,
    total: pagination.value.total,
    pageSizes: [20, 50, 100, 200],
    layouts: ['PrevPage', 'JumpNumber', 'NextPage', 'Sizes', 'FullJump', 'Total']
  },
  // 禁用 vxe-table 内置排序，我们自己处理
  sortConfig: {
    remote: true
  },
  columns: tableColumns.value,
  data: props.data
}))

// 事件处理
const gridEvents: VxeGridListeners = {
  pageChange: ({ currentPage, pageSize }) => {
    pagination.value.currentPage = currentPage
    pagination.value.pageSize = pageSize
    emit('page-change', currentPage, pageSize)
  },
  cellClick: ({ row, column }) => {
    const col = props.columns.find(c => c.name === column.field)
    if (col) {
      emit('row-click', row, col)
    }
  },
  cellDblclick: ({ row, column }) => {
    const col = props.columns.find(c => c.name === column.field)
    if (col) {
      emit('row-dblclick', row, col)
    }
  }
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
  getGridInstance: () => gridRef.value
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
    <!-- 工具栏插槽 -->
    <div v-if="$slots.toolbar" class="data-table-toolbar">
      <slot name="toolbar" />
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
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}

.table-wrapper {
  flex: 1;
  overflow: hidden;
}

/* 表头内嵌过滤器样式 */
.column-header-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 4px 0;
}

.column-title {
  display: flex;
  align-items: center;
  font-weight: 600;
  margin-bottom: 6px;
  cursor: pointer;
  user-select: none;
}

.column-title:hover {
  color: #409eff;
}

.title-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sort-icon {
  flex-shrink: 0;
  font-size: 12px;
  color: #c0c4cc;
  margin-left: 4px;
}

.sort-icon.sort-asc,
.sort-icon.sort-desc {
  color: #409eff;
}

.column-filter {
  width: 100%;
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

/* 确保下拉框不被表头裁剪 */
:deep(.vxe-table--header-wrapper) {
  overflow: visible !important;
}

:deep(.vxe-table--header-inner-wrapper) {
  overflow: visible !important;
}

:deep(.vxe-table--header) {
  overflow: visible !important;
}

:deep(.vxe-header--row) {
  overflow: visible !important;
}

/* 提高下拉框 z-index，确保在表格内容之上 */
.column-filter :deep(.filter-dropdown),
.column-filter :deep(.filter-select .filter-dropdown),
.column-filter :deep(.filter-text .filter-dropdown) {
  z-index: 2000 !important;
}

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
