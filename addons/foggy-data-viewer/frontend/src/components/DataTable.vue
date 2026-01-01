<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { VxeGridInstance, VxeGridProps, VxeGridListeners } from 'vxe-table'
import type { ColumnSchema, PaginationState, SortState, FilterValue } from '@/types'

interface Props {
  columns: ColumnSchema[]
  data: Record<string, unknown>[]
  total: number
  loading: boolean
  pageSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 50
})

const emit = defineEmits<{
  (e: 'page-change', page: number, size: number): void
  (e: 'sort-change', field: string | null, order: 'asc' | 'desc' | null): void
  (e: 'filter-change', filters: Record<string, FilterValue>): void
}>()

const gridRef = ref<VxeGridInstance>()

const pagination = ref<PaginationState>({
  currentPage: 1,
  pageSize: props.pageSize,
  total: props.total
})

const sortState = ref<SortState>({
  field: null,
  order: null
})

// 监听 total 变化
watch(() => props.total, (newTotal) => {
  pagination.value.total = newTotal
})

// 生成 vxe-table 列配置
const tableColumns = computed<VxeGridProps['columns']>(() => {
  return props.columns.map(col => ({
    field: col.name,
    title: col.label || col.name,
    minWidth: 120,
    sortable: true,
    filters: [{ data: '' }],
    filterRender: {
      name: 'VxeInput',
      props: { placeholder: '搜索...' }
    },
    // 根据类型设置格式化
    ...(getColumnFormatter(col.type))
  }))
})

// 根据字段类型获取格式化配置
function getColumnFormatter(type: string): Partial<VxeGridProps['columns']>[number] {
  switch (type?.toUpperCase()) {
    case 'MONEY':
    case 'NUMBER':
    case 'BIGDECIMAL':
      return {
        align: 'right',
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
        align: 'right',
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
        align: 'center',
        formatter: ({ cellValue }) => {
          return cellValue === true ? '是' : cellValue === false ? '否' : ''
        }
      }
    default:
      return {}
  }
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
  pagerConfig: {
    enabled: true,
    currentPage: pagination.value.currentPage,
    pageSize: pagination.value.pageSize,
    total: pagination.value.total,
    pageSizes: [20, 50, 100, 200],
    layouts: ['PrevPage', 'JumpNumber', 'NextPage', 'Sizes', 'FullJump', 'Total']
  },
  sortConfig: {
    trigger: 'cell',
    remote: true,
    defaultSort: sortState.value.field
      ? { field: sortState.value.field, order: sortState.value.order }
      : undefined
  },
  filterConfig: {
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
  sortChange: ({ field, order }) => {
    sortState.value.field = field as string
    sortState.value.order = order as 'asc' | 'desc' | null
    emit('sort-change', field as string, order as 'asc' | 'desc' | null)
  },
  filterChange: ({ filters }) => {
    const filterMap: Record<string, FilterValue> = {}
    for (const filter of filters) {
      if (filter.values && filter.values.length > 0) {
        filterMap[filter.field] = filter.values[0]
      }
    }
    emit('filter-change', filterMap)
  }
}

// 重置分页
function resetPagination() {
  pagination.value.currentPage = 1
}

defineExpose({
  resetPagination
})
</script>

<template>
  <div class="data-table">
    <vxe-grid
      ref="gridRef"
      v-bind="gridOptions"
      v-on="gridEvents"
    />
  </div>
</template>

<style scoped>
.data-table {
  width: 100%;
  height: 100%;
}
</style>
