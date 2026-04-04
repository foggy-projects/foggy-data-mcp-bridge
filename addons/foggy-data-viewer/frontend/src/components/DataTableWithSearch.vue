<script setup lang="ts">
import { ref, computed, onMounted, useAttrs } from 'vue'
import type { EnhancedColumnSchema, SliceRequestDef, FilterOption, TableSchema, FetchDataParams, FetchDataResult, OrderRequestDef, QueryHooks, MemberQueryRequest, MemberQueryResponse } from '@/types'
import SearchToolbar from './SearchToolbar.vue'
import QueryPanel from './QueryPanel.vue'
import type { QuerySchema } from './QueryPanel.vue'
import DataTable from './DataTable.vue'
import { useTableQuery } from './composables/useTableQuery'

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
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50,
  showFilters: true,
  showPager: true,
  showSearchToolbar: true,
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

// ========== useTableQuery（Schema 模式） ==========
// 始终创建 query 对象，但只在 Schema 模式下调用 loadData
const query = useTableQuery(
  props.fetchData ?? (async () => ({ items: [], total: 0 })),
  {
    pageSize: props.schema?.pageSize ?? props.pageSize,
    hooks: props.queryHooks
  }
)

// ========== 计算属性：根据模式选择数据源 ==========
const effectiveColumns = computed(() => {
  if (isSchemaMode.value && props.schema) {
    return props.schema.columns
  }
  return props.columns || []
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
    return query.loading.value
  }
  return props.loading || false
})

const effectiveServerSummary = computed(() => {
  if (isSchemaMode.value) {
    return query.serverSummary.value
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
const queryPanelRef = ref<InstanceType<typeof QueryPanel>>()
const dataTableRef = ref<InstanceType<typeof DataTable>>()

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

// Schema 模式下，初始化时加载数据
onMounted(() => {
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
    query.currentPage.value = 1
    loadData('filter')
  }
}

// 处理分页变化
function handlePageChange(page: number, size: number) {
  emit('page-change', page, size)

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
  /** 获取 useTableQuery 实例（高级用法） */
  getQuery: () => query,

  // ========== 保存查询功能方法 ==========
  /** 获取当前查询状态（用于保存查询） */
  getQueryState: () => ({
    columns: effectiveColumns.value.map(c => c.name),
    slice: mergedSlices.value,
    orderBy: query.currentOrderBy.value
  }),
  /** 应用查询状态（用于加载保存的查询） */
  applyQueryState: (state: { columns: string[]; slice: SliceRequestDef[]; orderBy: OrderRequestDef[] }) => {
    // 应用筛选条件
    searchSlices.value = []
    tableSlices.value = state.slice || []

    // 应用排序
    query.setSort(state.orderBy || [])

    // 注意：列的应用需要由父组件处理，因为 schema 是从 props 传入的
    // 这里只是更新查询状态
  },
  /** 获取列 Schema（用于保存查询对话框） */
  getSchema: () => effectiveColumns.value
})
</script>

<template>
  <div class="data-table-with-search">
    <!-- 传统查询区 -->
    <div v-if="showQueryPanel && querySchema" class="query-panel-wrapper">
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

.query-panel-wrapper {
  flex-shrink: 0;
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
