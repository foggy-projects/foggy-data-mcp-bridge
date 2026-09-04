<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { EnhancedColumnSchema, SliceRequestDef, MemberQueryRequest, MemberQueryResponse } from '@/types'
import { TextFilter, NumberRangeFilter, DateRangeFilter, SelectFilter, BoolFilter } from './filters'
import { viewerSlicesToDisplay, viewerSlicesToRaw } from '@/utils/viewer'

/**
 * SearchToolbar 组件
 *
 * 独立的搜索工具栏组件，可单独使用或集成到 DataTable 的 toolbar 插槽
 * 支持字段级快速筛选，与 DataTable 的过滤器体系保持一致
 */
interface Props {
  /** 列配置（从 QM Schema 构建） */
  columns: EnhancedColumnSchema[]
  /** 可搜索的字段列表（不指定则显示所有可筛选字段） */
  searchableFields?: string[]
  /** 当前筛选条件（v-model） */
  modelValue?: SliceRequestDef[]
  /** 布局方式 */
  layout?: 'horizontal' | 'vertical'
  /** 是否显示操作按钮（搜索、重置） */
  showActions?: boolean
  /** 过滤选项加载器（用于维度列，兼容旧接口） */
  filterOptionsLoader?: (columnName: string) => Promise<{ label: string; value: string | number }[]>
  /** 远程维度成员加载器（优先于 filterOptionsLoader） */
  filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
  /** QM 模型名称（远程成员加载所需） */
  qmModel?: string
}

const props = withDefaults(defineProps<Props>(), {
  layout: 'horizontal',
  showActions: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[]): void
  (e: 'search'): void
  (e: 'reset'): void
}>()

// 内部筛选状态：按字段存储
const filterValues = ref<Record<string, SliceRequestDef[] | null>>({})

// 根据配置获取可搜索的列
const searchableColumns = computed(() => {
  let cols = props.columns.filter(col => col.filterable !== false)

  if (props.searchableFields && props.searchableFields.length > 0) {
    cols = cols.filter(col => props.searchableFields!.includes(col.name))
  }

  return cols
})

// 从 modelValue 初始化
watch(() => props.modelValue, (slices) => {
  if (!slices || slices.length === 0) {
    filterValues.value = {}
    return
  }

  // 按 field 分组
  const grouped: Record<string, SliceRequestDef[]> = {}
  for (const slice of slices) {
    if (!slice.field) continue
    if (!grouped[slice.field]) {
      grouped[slice.field] = []
    }
    grouped[slice.field].push(...viewerSlicesToDisplay([slice], props.columns))
  }

  filterValues.value = grouped
}, { immediate: true })

// 推断过滤器类型
function inferFilterType(col: EnhancedColumnSchema): string {
  const type = col.type?.toUpperCase()

  // 日期类型优先
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

  // 使用后端返回的 filterType
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

// 获取过滤器组件
function getFilterComponent(col: EnhancedColumnSchema) {
  if (col.customFilterComponent) {
    return col.customFilterComponent
  }

  const filterType = inferFilterType(col)

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
        placeholder: `请选择${col.title || col.name}`
      }
    case 'dimension': {
      // 优先使用远程成员加载器
      const hasMemberLoader = !!props.filterMemberLoader && !!props.qmModel
      const memberLookup = col.memberLookup
      const selectionField = memberLookup?.selectionFieldName

      if (hasMemberLoader && memberLookup?.enabled) {
        return {
          ...baseProps,
          // DSL slice 使用 selectionFieldName（如 customer$id）
          selectionField: selectionField || col.name,
          remoteLoader: props.filterMemberLoader,
          qmModel: props.qmModel,
          options: [],
          placeholder: `请选择${col.title || col.name}`
        }
      }

      // fallback：静态 options
      return {
        ...baseProps,
        options: [],
        loading: false,
        placeholder: `请选择${col.title || col.name}`
      }
    }
    default:
      return {
        ...baseProps,
        placeholder: `搜索${col.title || col.name}...`
      }
  }
}

// 更新过滤值
function updateFilter(columnName: string, value: SliceRequestDef[] | null) {
  if (value === null || value.length === 0) {
    delete filterValues.value[columnName]
  } else {
    filterValues.value[columnName] = value
  }

  // 立即同步到 modelValue（实时搜索）
  emitFilterChange()
}

// 发送过滤变更事件
function emitFilterChange() {
  emit('update:modelValue', getRawFilterSlices())
}

function getRawFilterSlices(): SliceRequestDef[] {
  const allSlices: SliceRequestDef[] = []
  for (const slices of Object.values(filterValues.value)) {
    if (slices && slices.length > 0) {
      allSlices.push(...viewerSlicesToRaw(slices, props.columns))
    }
  }
  return allSlices
}

// 点击搜索按钮
function handleSearch() {
  emitFilterChange()
  emit('search')
}

// 点击重置按钮
function handleReset() {
  filterValues.value = {}
  emit('update:modelValue', [])
  emit('reset')
}

// 暴露方法
defineExpose({
  /** 清空所有筛选 */
  clearFilters: handleReset,
  /** 获取当前筛选条件 */
  getFilters: getRawFilterSlices
})
</script>

<template>
  <div class="search-toolbar" :class="`layout-${layout}`">
    <div class="search-fields">
      <div
        v-for="col in searchableColumns"
        :key="col.name"
        class="search-field-item"
      >
        <label class="field-label">{{ col.title || col.name }}</label>
        <div class="field-filter">
          <component
            :is="getFilterComponent(col)"
            v-bind="getFilterProps(col)"
          />
        </div>
      </div>
    </div>

    <div v-if="showActions" class="search-actions">
      <button class="btn btn-primary" @click="handleSearch">
        <span class="btn-icon">🔍</span>
        搜索
      </button>
      <button class="btn btn-default" @click="handleReset">
        <span class="btn-icon">↻</span>
        重置
      </button>
    </div>
  </div>
</template>

<style scoped>
.search-toolbar {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

/* 水平布局（默认） */
.search-toolbar.layout-horizontal {
  flex-direction: row;
  align-items: flex-end;
}

.search-toolbar.layout-horizontal .search-fields {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.search-toolbar.layout-horizontal .search-field-item {
  min-width: 200px;
  flex: 0 1 auto;
}

/* 垂直布局 */
.search-toolbar.layout-vertical {
  flex-direction: column;
}

.search-toolbar.layout-vertical .search-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-toolbar.layout-vertical .search-field-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-toolbar.layout-vertical .field-label {
  min-width: 100px;
  text-align: right;
}

.search-toolbar.layout-vertical .field-filter {
  flex: 1;
}

/* 字段样式 */
.search-field-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  white-space: nowrap;
}

.field-filter {
  min-width: 0;
}

/* 操作按钮 */
.search-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  height: 32px;
}

.btn-icon {
  font-size: 14px;
}

.btn-primary {
  background: #409eff;
  color: white;
  border-color: #409eff;
}

.btn-primary:hover {
  background: #66b1ff;
  border-color: #66b1ff;
}

.btn-primary:active {
  background: #3a8ee6;
  border-color: #3a8ee6;
}

.btn-default {
  background: white;
  color: #606266;
  border-color: #dcdfe6;
}

.btn-default:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background: #ecf5ff;
}

.btn-default:active {
  color: #3a8ee6;
  border-color: #3a8ee6;
}

/* 响应式 */
@media (max-width: 768px) {
  .search-toolbar.layout-horizontal {
    flex-direction: column;
    align-items: stretch;
  }

  .search-toolbar.layout-horizontal .search-fields {
    flex-direction: column;
  }

  .search-toolbar.layout-horizontal .search-field-item {
    min-width: 0;
  }

  .search-actions {
    justify-content: flex-end;
  }
}
</style>
