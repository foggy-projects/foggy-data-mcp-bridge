<script setup lang="ts">
import { ref, computed, useAttrs } from 'vue'
import type { EnhancedColumnSchema, SliceRequestDef, FilterOption } from '@/types'
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
 * 组合了 SearchToolbar 和 DataTable 的高级组件
 * 支持完整的属性和事件透传到底层组件
 */
interface Props {
  // ========== DataTable Props ==========
  /** 列配置（必填） */
  columns: EnhancedColumnSchema[]
  /** 表格数据（必填） */
  data: Record<string, unknown>[]
  /** 总数据量（必填） */
  total: number
  /** 加载状态 */
  loading: boolean
  /** 每页大小 */
  pageSize?: number
  /** 是否显示表头过滤器 */
  showFilters?: boolean
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
  /** 搜索工具栏可搜索字段（不指定则使用所有可筛选字段） */
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
  showSearchToolbar: true,
  searchLayout: 'horizontal',
  showSearchActions: true,
  filterMergeMode: 'merge'
})

const emit = defineEmits<{
  // DataTable 事件
  (e: 'page-change', page: number, size: number): void
  (e: 'sort-change', field: string | null, order: 'asc' | 'desc' | null): void
  (e: 'filter-change', slices: SliceRequestDef[]): void
  (e: 'row-click', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  (e: 'row-dblclick', row: Record<string, unknown>, column: EnhancedColumnSchema): void
  // SearchToolbar 事件
  (e: 'search', slices: SliceRequestDef[]): void
  (e: 'reset'): void
}>()

// 组件引用
const searchToolbarRef = ref<InstanceType<typeof SearchToolbar>>()
const dataTableRef = ref<InstanceType<typeof DataTable>>()

// 搜索工具栏筛选条件
const searchSlices = ref<SliceRequestDef[]>([])
// 表头筛选条件
const tableSlices = ref<SliceRequestDef[]>([])

// 合并后的筛选条件
const mergedSlices = computed(() => {
  if (props.filterMergeMode === 'replace') {
    // 替换模式：搜索工具栏优先，如果为空则使用表头筛选
    return searchSlices.value.length > 0 ? searchSlices.value : tableSlices.value
  } else {
    // 合并模式：两者合并（去重）
    const merged = [...searchSlices.value]
    const searchFields = new Set(searchSlices.value.map(s => s.field))

    // 添加表头筛选中不在搜索工具栏中的字段
    for (const slice of tableSlices.value) {
      if (!searchFields.has(slice.field)) {
        merged.push(slice)
      }
    }

    return merged
  }
})

// 处理搜索工具栏筛选变化
function handleSearchChange(slices: SliceRequestDef[]) {
  searchSlices.value = slices
  emit('filter-change', mergedSlices.value)
}

// 处理搜索按钮点击
function handleSearch() {
  emit('search', searchSlices.value)
  emit('filter-change', mergedSlices.value)
}

// 处理重置按钮点击
function handleReset() {
  searchSlices.value = []
  emit('reset')
  emit('filter-change', mergedSlices.value)
}

// 处理表头筛选变化
function handleTableFilterChange(slices: SliceRequestDef[]) {
  tableSlices.value = slices
  emit('filter-change', mergedSlices.value)
}

// 分离 DataTable 的 props 和用户自定义的 props
const dataTableProps = computed(() => {
  // 从 attrs 中提取非事件属性
  const userProps = Object.keys(attrs)
    .filter(key => !key.startsWith('on'))
    .reduce((acc, key) => ({ ...acc, [key]: attrs[key] }), {})

  return {
    columns: props.columns,
    data: props.data,
    total: props.total,
    loading: props.loading,
    pageSize: props.pageSize,
    showFilters: props.showFilters,
    initialSlice: props.initialSlice,
    serverSummary: props.serverSummary,
    filterOptionsLoader: props.filterOptionsLoader,
    customFilterComponents: props.customFilterComponents,
    ...userProps
  }
})

// 提取用户传入的事件监听器
const dataTableEvents = computed(() => {
  const userEvents: Record<string, Function> = {}
  Object.keys(attrs).forEach(key => {
    if (key.startsWith('on')) {
      const eventName = key.slice(2, 3).toLowerCase() + key.slice(3)
      userEvents[eventName] = attrs[key] as Function
    }
  })

  return {
    'page-change': (...args: any[]) => {
      emit('page-change', ...args as [number, number])
      if (userEvents['pageChange']) {
        userEvents['pageChange'](...args)
      }
    },
    'sort-change': (...args: any[]) => {
      emit('sort-change', ...args as [string | null, 'asc' | 'desc' | null])
      if (userEvents['sortChange']) {
        userEvents['sortChange'](...args)
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

// 暴露方法和子组件引用
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
  resetPagination: () => dataTableRef.value?.resetPagination()
})
</script>

<template>
  <div class="data-table-with-search">
    <!-- 搜索工具栏 -->
    <div v-if="showSearchToolbar" class="search-toolbar-wrapper">
      <SearchToolbar
        ref="searchToolbarRef"
        :columns="columns"
        :searchable-fields="searchableFields"
        :layout="searchLayout"
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
