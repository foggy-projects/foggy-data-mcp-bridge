<script setup lang="ts">
import { ref, computed, watch, onMounted, useAttrs } from 'vue'
import type { EnhancedColumnSchema, SliceRequestDef, FilterOption, TableSchema, FetchDataParams, FetchDataResult, OrderRequestDef } from '@/types'
import SearchToolbar from './SearchToolbar.vue'
import DataTable from './DataTable.vue'

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

  // ========== SearchToolbar Props ==========
  /** 是否显示搜索工具栏 */
  showSearchToolbar?: boolean
  /** 搜索工具栏可搜索字段 */
  searchableFields?: string[]
  /** 搜索工具栏布局 */
  searchLayout?: 'horizontal' | 'vertical'
  /** 是否显示搜索按钮 */
  showSearchActions?: boolean

  // ========== 组合配置 ==========
  /** 搜索工具栏和表头过滤器的筛选条件合并模式 */
  filterMergeMode?: 'replace' | 'merge'
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50,
  showFilters: true,
  showPager: true,
  showSearchToolbar: true,
  searchLayout: 'horizontal',
  showSearchActions: true,
  filterMergeMode: 'merge'
})

const emit = defineEmits<{
  // 通用事件
  (e: 'page-change', page: number, size: number): void
  (e: 'sort-change', field: string | null, order: 'asc' | 'desc' | null): void
  (e: 'filter-change', slices: SliceRequestDef[]): void
  (e: 'row-click', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  (e: 'row-dblclick', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  // SearchToolbar 事件
  (e: 'search', slices: SliceRequestDef[]): void
  (e: 'reset'): void
  // Schema 模式事件
  (e: 'load-success', result: FetchDataResult): void
  (e: 'load-error', error: Error): void
}>()

// ========== 判断工作模式 ==========
const isSchemaMode = computed(() => !!props.schema && !!props.fetchData)

// ========== Schema 模式的内部状态 ==========
const internalData = ref<Record<string, unknown>[]>([])
const internalTotal = ref(0)
const internalLoading = ref(false)
const internalServerSummary = ref<Record<string, unknown> | null>(null)
const currentPage = ref(1)
const currentPageSize = ref(props.schema?.pageSize ?? props.pageSize)
const currentOrderBy = ref<OrderRequestDef[]>([])

// ========== 计算属性：根据模式选择数据源 ==========
const effectiveColumns = computed(() => {
  if (isSchemaMode.value && props.schema) {
    return props.schema.columns
  }
  return props.columns || []
})

const effectiveData = computed(() => {
  if (isSchemaMode.value) {
    return internalData.value
  }
  return props.data || []
})

const effectiveTotal = computed(() => {
  if (isSchemaMode.value) {
    return internalTotal.value
  }
  return props.total || 0
})

const effectiveLoading = computed(() => {
  if (isSchemaMode.value) {
    return internalLoading.value
  }
  return props.loading || false
})

const effectiveServerSummary = computed(() => {
  if (isSchemaMode.value) {
    return internalServerSummary.value
  }
  return props.serverSummary || null
})

const effectivePageSize = computed(() => {
  if (isSchemaMode.value && props.schema?.pageSize) {
    return props.schema.pageSize
  }
  return props.pageSize
})

const effectiveShowFilters = computed(() => {
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
  if (isSchemaMode.value && props.schema?.showSearchToolbar !== undefined) {
    return props.schema.showSearchToolbar
  }
  return props.showSearchToolbar
})

const effectiveSearchLayout = computed(() => {
  if (isSchemaMode.value && props.schema?.searchLayout) {
    return props.schema.searchLayout
  }
  return props.searchLayout
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

// ========== 组件引用 ==========
const searchToolbarRef = ref<InstanceType<typeof SearchToolbar>>()
const dataTableRef = ref<InstanceType<typeof DataTable>>()

// ========== 筛选状态 ==========
const searchSlices = ref<SliceRequestDef[]>([])
const tableSlices = ref<SliceRequestDef[]>([])

// 合并后的筛选条件
const mergedSlices = computed(() => {
  if (props.filterMergeMode === 'replace') {
    return searchSlices.value.length > 0 ? searchSlices.value : tableSlices.value
  } else {
    const merged = [...searchSlices.value]
    const searchFields = new Set(searchSlices.value.map(s => s.field))
    for (const slice of tableSlices.value) {
      if (!searchFields.has(slice.field)) {
        merged.push(slice)
      }
    }
    return merged
  }
})

// ========== Schema 模式的数据加载 ==========
async function loadData() {
  if (!isSchemaMode.value || !props.fetchData) return

  internalLoading.value = true
  try {
    const result = await props.fetchData({
      page: currentPage.value,
      pageSize: currentPageSize.value,
      slice: mergedSlices.value,
      orderBy: currentOrderBy.value
    })

    internalData.value = result.items
    internalTotal.value = result.total
    internalServerSummary.value = result.totalData || null

    emit('load-success', result)
  } catch (error) {
    console.error('数据加载失败:', error)
    emit('load-error', error as Error)
  } finally {
    internalLoading.value = false
  }
}

// Schema 模式下，初始化时加载数据
onMounted(() => {
  if (isSchemaMode.value) {
    loadData()
  }
})

// ========== 事件处理 ==========

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
  handleFilterChange()
}

// 统一的筛选变化处理
function handleFilterChange() {
  emit('filter-change', mergedSlices.value)

  // Schema 模式下，重置到第一页并重新加载
  if (isSchemaMode.value) {
    currentPage.value = 1
    loadData()
  }
}

// 处理分页变化
function handlePageChange(page: number, size: number) {
  emit('page-change', page, size)

  // Schema 模式下，更新分页并重新加载
  if (isSchemaMode.value) {
    currentPage.value = page
    currentPageSize.value = size
    loadData()
  }
}

// 处理排序变化
function handleSortChange(field: string | null, order: 'asc' | 'desc' | null) {
  emit('sort-change', field, order)

  // Schema 模式下，更新排序并重新加载
  if (isSchemaMode.value) {
    if (field && order) {
      currentOrderBy.value = [{ field, order }]
    } else {
      currentOrderBy.value = []
    }
    loadData()
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
    pageSize: effectivePageSize.value,
    showFilters: effectiveShowFilters.value,
    showPager: effectiveShowPager.value,
    initialSlice: props.initialSlice,
    serverSummary: effectiveServerSummary.value,
    filterOptionsLoader: props.filterOptionsLoader,
    customFilterComponents: props.customFilterComponents,
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
    'row-click': (...args: any[]) => {
      emit('row-click', ...args as [Record<string, unknown>, EnhancedColumnSchema])
      if (userEvents['rowClick']) {
        userEvents['rowClick'](...args)
      }
    },
    'row-dblclick': (...args: any[]) => {
      emit('row-dblclick', ...args as [Record<string, unknown>, EnhancedColumnSchema])
      if (userEvents['rowDblclick']) {
        userEvents['rowDblclick'](...args)
      }
    }
  }
})

// ========== 暴露方法 ==========
defineExpose({
  /** 获取 SearchToolbar 实例 */
  getSearchToolbar: () => searchToolbarRef.value,
  /** 获取 DataTable 实例 */
  getDataTable: () => dataTableRef.value,
  /** 清空搜索工具栏筛选 */
  clearSearchFilters: () => searchToolbarRef.value?.clearFilters(),
  /** 清空表头筛选 */
  clearTableFilters: () => dataTableRef.value?.clearFilters(),
  /** 清空所有筛选 */
  clearAllFilters: () => {
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
      loadData()
    }
  },
  /** 重新加载（重置分页后刷新，仅 Schema 模式） */
  reload: () => {
    if (isSchemaMode.value) {
      currentPage.value = 1
      loadData()
    }
  }
})
</script>

<template>
  <div class="data-table-with-search">
    <!-- 搜索工具栏 -->
    <div v-if="effectiveShowSearchToolbar" class="search-toolbar-wrapper">
      <SearchToolbar
        ref="searchToolbarRef"
        :columns="effectiveColumns"
        :searchable-fields="effectiveSearchableFields"
        :layout="effectiveSearchLayout"
        :show-actions="showSearchActions"
        :filter-options-loader="filterOptionsLoader"
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
        <!-- 透传所有插槽 -->
        <template v-for="(_, name) in $slots" #[name]="slotData">
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
