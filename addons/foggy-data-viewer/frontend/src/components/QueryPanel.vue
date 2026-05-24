<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { SliceRequestDef, MemberQueryRequest, MemberQueryResponse } from '@/types'
import { TextFilter, NumberRangeFilter, DateRangeFilter, SelectFilter, BoolFilter } from './filters'

/**
 * QueryPanel - 传统查询区组件
 *
 * 根据 QuerySchema 渲染表格上方的查询表单区域。
 * 支持响应式 grid 布局、折叠/展开、查询/重置。
 * 只处理 placement='form' 的查询字段。
 */

// ── 查询 Schema 类型（内联，避免循环依赖） ──

export interface QueryFieldSchema {
  key: string
  label: string
  sourceField?: string
  placement: 'form' | 'column' | 'hidden'
  component: 'text' | 'numberRange' | 'dateRange' | 'dictSelect' | 'qmLookupSelect' | 'memberSelect' | 'bool' | 'custom'
  defaultOperator?: string
  defaultValue?: unknown
  columnRef?: string
  dictId?: string
  /** 远程成员查询使用的展示/lookup 字段，如 customer$caption */
  lookupRef?: string
  order?: number
  span?: number
  visible?: boolean
}

export interface QueryPanelLayoutSchema {
  mode?: 'grid'
  columns?: { xs?: number; sm?: number; md?: number; lg?: number; xl?: number }
  labelWidth?: number
  actionAlign?: 'left' | 'right'
  collapsedRows?: number
  gutter?: number
}

export interface QuerySchema {
  fields: QueryFieldSchema[]
  submitMode?: 'manual'
  collapsible?: boolean
  defaultExpanded?: boolean
  layout?: QueryPanelLayoutSchema
}

interface Props {
  /** 查询 Schema */
  schema: QuerySchema
  /** 当前查询条件 (v-model) */
  modelValue?: SliceRequestDef[]
  /** 远程维度成员加载器 */
  filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
  /** QM 模型名称 */
  qmModel?: string
}

const props = withDefaults(defineProps<Props>(), {})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[]): void
  (e: 'search'): void
  (e: 'reset'): void
}>()

// ── 状态 ──
const expanded = ref(props.schema.defaultExpanded !== false)
const fieldValues = ref<Record<string, SliceRequestDef[] | null>>({})

// 只处理 placement = 'form' 的字段
const formFields = computed(() => {
  return props.schema.fields
    .filter(f => f.placement === 'form' && f.visible !== false)
    .sort((a, b) => (a.order ?? 99) - (b.order ?? 99))
})

// 布局配置
const layout = computed(() => props.schema.layout ?? {})
const gridColumns = computed(() => layout.value.columns ?? { xs: 1, sm: 2, md: 3, lg: 4, xl: 4 })
const collapsedRows = computed(() => layout.value.collapsedRows ?? 1)
const labelWidth = computed(() => layout.value.labelWidth ?? 100)
const gutter = computed(() => layout.value.gutter ?? 16)

// 折叠时显示的字段数
const visibleFieldCount = computed(() => {
  if (expanded.value || !props.schema.collapsible) return formFields.value.length
  // 折叠行数 × 当前列数（用 lg 作为默认）
  return collapsedRows.value * (gridColumns.value.lg ?? 4)
})

const visibleFields = computed(() => {
  return formFields.value.slice(0, visibleFieldCount.value)
})

const canCollapse = computed(() => {
  return props.schema.collapsible !== false && formFields.value.length > visibleFieldCount.value
})

// ── 从 modelValue 初始化 ──
watch(() => props.modelValue, (slices) => {
  if (!slices || slices.length === 0) {
    fieldValues.value = {}
    return
  }
  const grouped: Record<string, SliceRequestDef[]> = {}
  for (const slice of slices) {
    if (!slice.field) continue
    // 匹配字段：按 sourceField 或 key 找
    const matchField = formFields.value.find(
      f => f.sourceField === slice.field || f.key === slice.field
    )
    if (matchField) {
      if (!grouped[matchField.key]) grouped[matchField.key] = []
      grouped[matchField.key].push(slice)
    }
  }
  fieldValues.value = grouped
}, { immediate: true })

// ── 组件映射 ──
function getFilterComponent(field: QueryFieldSchema) {
  switch (field.component) {
    case 'text': return TextFilter
    case 'numberRange': return NumberRangeFilter
    case 'dateRange': return DateRangeFilter
    case 'dictSelect': return SelectFilter
    case 'memberSelect': return SelectFilter
    case 'qmLookupSelect': return SelectFilter
    case 'bool': return BoolFilter
    default: return TextFilter
  }
}

function getSliceField(field: QueryFieldSchema): string {
  return field.sourceField || field.key
}

function getMemberLookupField(field: QueryFieldSchema): string {
  return field.lookupRef || field.key
}

function getFilterProps(field: QueryFieldSchema): Record<string, unknown> {
  const baseProps: Record<string, unknown> = {
    field: getSliceField(field),
    modelValue: fieldValues.value[field.key] || null,
    'onUpdate:modelValue': (val: SliceRequestDef[] | null) => updateField(field.key, val)
  }

  switch (field.component) {
    case 'dateRange':
      return { ...baseProps, showTime: true }
    case 'dictSelect':
      return {
        ...baseProps,
        options: [],
        dictId: field.dictId,
        placeholder: `请选择${field.label}`
      }
    case 'memberSelect':
    case 'qmLookupSelect':
      return {
        ...baseProps,
        field: getMemberLookupField(field),
        selectionField: getSliceField(field),
        remoteLoader: props.filterMemberLoader,
        qmModel: props.qmModel,
        options: [],
        placeholder: `请选择${field.label}`
      }
    default:
      return { ...baseProps, placeholder: `请输入${field.label}` }
  }
}

// ── 事件 ──
function updateField(key: string, value: SliceRequestDef[] | null) {
  if (value === null || value.length === 0) {
    delete fieldValues.value[key]
  } else {
    fieldValues.value[key] = value
  }
  // manual 模式：不立即触发服务端查询，等点"查询"
}

function handleSearch() {
  const allSlices: SliceRequestDef[] = []
  for (const slices of Object.values(fieldValues.value)) {
    if (slices && slices.length > 0) allSlices.push(...slices)
  }
  emit('update:modelValue', allSlices)
  emit('search')
}

function handleReset() {
  fieldValues.value = {}
  emit('update:modelValue', [])
  emit('reset')
}

function toggleExpand() {
  expanded.value = !expanded.value
}

defineExpose({
  search: handleSearch,
  reset: handleReset,
  getSlices: (): SliceRequestDef[] => {
    const allSlices: SliceRequestDef[] = []
    for (const slices of Object.values(fieldValues.value)) {
      if (slices && slices.length > 0) allSlices.push(...slices)
    }
    return allSlices
  }
})
</script>

<template>
  <div class="query-panel">
    <div
      class="query-fields"
      :style="{
        display: 'grid',
        gridTemplateColumns: `repeat(${gridColumns.lg}, 1fr)`,
        gap: `${gutter}px`,
      }"
    >
      <div
        v-for="field in visibleFields"
        :key="field.key"
        class="query-field-item"
        :style="{ gridColumn: `span ${field.span || 1}` }"
      >
        <label class="field-label" :style="{ width: `${labelWidth}px` }">
          {{ field.label }}
        </label>
        <div class="field-control">
          <component
            :is="getFilterComponent(field)"
            v-bind="getFilterProps(field) as any"
          />
        </div>
      </div>
    </div>

    <div class="query-actions" :class="`align-${layout.actionAlign || 'right'}`">
      <button class="btn btn-primary" @click="handleSearch">查询</button>
      <button class="btn btn-default" @click="handleReset">重置</button>
      <button
        v-if="canCollapse || expanded"
        class="btn btn-link"
        @click="toggleExpand"
      >
        {{ expanded ? '收起' : '展开' }}
        <span class="expand-icon">{{ expanded ? '▲' : '▼' }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.query-panel {
  padding: 16px;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.query-field-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-label {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  text-align: right;
  white-space: nowrap;
}

.field-control {
  flex: 1;
  min-width: 0;
}

.query-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.query-actions.align-right {
  justify-content: flex-end;
}

.query-actions.align-left {
  justify-content: flex-start;
}

.btn {
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-primary {
  background: #409eff;
  color: white;
  border-color: #409eff;
}

.btn-primary:hover {
  background: #66b1ff;
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

.btn-link {
  background: none;
  border: none;
  color: #409eff;
  padding: 8px 4px;
}

.btn-link:hover {
  color: #66b1ff;
}

.expand-icon {
  font-size: 10px;
}

/* 响应式 */
@media (max-width: 1200px) {
  .query-fields { grid-template-columns: repeat(3, 1fr) !important; }
}
@media (max-width: 992px) {
  .query-fields { grid-template-columns: repeat(2, 1fr) !important; }
}
@media (max-width: 576px) {
  .query-fields { grid-template-columns: 1fr !important; }
}
</style>
