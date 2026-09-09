<script setup lang="ts">
import { computed } from 'vue'
import type { ColumnSchema, SliceRequestDef, MemberQueryRequest, MemberQueryResponse } from '@/types'
import SelectFilter from '../filters/SelectFilter.vue'
import {
  countConditionLeaves,
  getDisplayColumnForCondition,
  getQueryFieldForColumn,
  getUserConfigurableColumns
} from '@/utils/listPreset'

defineOptions({ name: 'ListPresetConditionEditor' })

interface Props {
  modelValue?: SliceRequestDef[]
  columns: ColumnSchema[]
  maxConditions?: number
  showToolbar?: boolean
  pathLabel?: string
  /** Leaf conditions outside this recursive subtree. */
  conditionOffset?: number
  qmModel?: string
  filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  maxConditions: 20,
  showToolbar: true,
  pathLabel: '',
  conditionOffset: 0
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: SliceRequestDef[]): void
}>()

const configurableColumns = computed(() => getUserConfigurableColumns(props.columns))
const conditionCount = computed(() => countConditionLeaves(props.modelValue))
const canAddCondition = computed(() => conditionCount.value + props.conditionOffset < props.maxConditions)

const operators = [
  { value: '=', label: '等于' },
  { value: '!=', label: '不等于' },
  { value: 'like', label: '包含' },
  { value: 'right_like', label: '前缀匹配' },
  { value: '>', label: '大于' },
  { value: '>=', label: '大于等于' },
  { value: '<', label: '小于' },
  { value: '<=', label: '小于等于' },
  { value: 'in', label: '属于' },
  { value: 'not in', label: '不属于' },
  { value: '[]', label: '范围（含边界）' },
  { value: '[)', label: '范围（左含右不含）' },
  { value: 'is null', label: '为空' },
  { value: 'is not null', label: '不为空' }
]

function isGroup(node: SliceRequestDef): '$or' | '$and' | 'children' | null {
  if (Array.isArray(node.$or)) return '$or'
  if (Array.isArray(node.$and)) return '$and'
  if (Array.isArray(node.children)) return 'children'
  return null
}

function getChildren(node: SliceRequestDef): SliceRequestDef[] {
  const group = isGroup(node)
  return group ? (node[group] || []) : []
}

function clone(conditions: SliceRequestDef[]): SliceRequestDef[] {
  return conditions.map(condition => ({
    ...condition,
    ...(condition.$or ? { $or: clone(condition.$or) } : {}),
    ...(condition.$and ? { $and: clone(condition.$and) } : {}),
    ...(condition.children ? { children: clone(condition.children) } : {})
  }))
}

function update(conditions: SliceRequestDef[]) {
  emit('update:modelValue', clone(conditions))
}

function getDisplayField(field: string): string {
  return getDisplayColumnForCondition(field, configurableColumns.value)?.name || field
}

function setField(index: number, displayField: string) {
  const next = clone(props.modelValue)
  const column = configurableColumns.value.find(item => item.name === displayField)
  const field = getQueryFieldForColumn(column) || displayField
  next[index] = { field, op: '=', value: undefined }
  update(next)
}

function setOperator(index: number, operator: string) {
  const next = clone(props.modelValue)
  const current = next[index]
  if (!current) return
  next[index] = {
    ...current,
    op: operator,
    ...(operator === 'is null' || operator === 'is not null' ? { value: undefined } : {})
  }
  update(next)
}

function valueToText(value: unknown): string {
  if (Array.isArray(value)) return value.join(', ')
  if (value == null) return ''
  return String(value)
}

function textToValue(operator: string, text: string): unknown {
  if (operator === 'in' || operator === 'not in' || operator === '[]' || operator === '[)') {
    return text.split(/[,，\s]+/).map(value => value.trim()).filter(Boolean)
  }
  return text
}

function setValue(index: number, text: string) {
  const next = clone(props.modelValue)
  const current = next[index]
  if (!current || current.op === 'is null' || current.op === 'is not null') return
  next[index] = { ...current, value: textToValue(current.op, text) }
  update(next)
}

function valueColumn(node: SliceRequestDef) {
  return getDisplayColumnForCondition(node.field, configurableColumns.value)
}

function setSelectedValue(index: number, value: unknown) {
  const next = clone(props.modelValue)
  next[index] = { ...next[index], value }
  update(next)
}

function setMemberValue(index: number, slices: SliceRequestDef[] | null) {
  const next = clone(props.modelValue)
  const first = slices?.[0]
  next[index] = first ? { ...first } : { ...next[index], value: undefined }
  update(next)
}

function remove(index: number) {
  const next = clone(props.modelValue)
  next.splice(index, 1)
  update(next)
}

function addLeaf() {
  if (!canAddCondition.value) return
  const first = configurableColumns.value[0]
  if (!first) return
  const field = getQueryFieldForColumn(first) || first.name
  update([...props.modelValue, { field, op: '=', value: '' }])
}

function addGroup(kind: '$or' | '$and') {
  if (conditionCount.value + props.conditionOffset + 2 > props.maxConditions) return
  const first = configurableColumns.value[0]
  if (!first) return
  const field = getQueryFieldForColumn(first) || first.name
  const leaf = (): SliceRequestDef => ({ field, op: '=', value: '' })
  update([...props.modelValue, { [kind]: [leaf(), leaf()] } as unknown as SliceRequestDef])
}

function addGroupChild(index: number, kind: '$or' | '$and' | 'children') {
  if (!canAddCondition.value) return
  const first = configurableColumns.value[0]
  if (!first) return
  const field = getQueryFieldForColumn(first) || first.name
  const next = clone(props.modelValue)
  const node = next[index]
  if (!node || !isGroup(node)) return
  const children = getChildren(node)
  children.push({ field, op: '=', value: '' })
  node[kind] = children
  update(next)
}

function updateGroupChildren(index: number, children: SliceRequestDef[]) {
  const next = clone(props.modelValue)
  const node = next[index]
  const kind = node && isGroup(node)
  if (!node || !kind) return
  const nextChildren = clone(children)
  node[kind] = nextChildren
  update(next)
}

function setGroupKind(index: number, kind: '$or' | '$and') {
  const next = clone(props.modelValue)
  const node = next[index]
  if (!node) return
  const children = getChildren(node)
  next[index] = { [kind]: children } as unknown as SliceRequestDef
  update(next)
}
</script>

<template>
  <div class="condition-editor">
    <div v-if="showToolbar" class="condition-editor-toolbar">
      <div>
        <span>用户条件</span>
        <small>{{ conditionCount }} / {{ maxConditions }} 条件</small>
      </div>
      <div class="condition-editor-actions">
        <el-button size="small" :disabled="!canAddCondition" @click="addLeaf">添加条件</el-button>
        <el-button size="small" :disabled="conditionCount + conditionOffset + 2 > maxConditions" @click="addGroup('$and')">添加 AND 组</el-button>
        <el-button size="small" :disabled="conditionCount + conditionOffset + 2 > maxConditions" @click="addGroup('$or')">添加 OR 组</el-button>
      </div>
    </div>

    <el-alert
      v-if="conditionCount > maxConditions"
      type="error"
      :closable="false"
      show-icon
      title="当前方案条件已超出上限，请删除条件后再保存或应用"
    />

    <div v-if="modelValue.length === 0" class="condition-empty">暂无用户条件</div>
    <div v-else class="condition-list">
      <template v-for="(condition, index) in modelValue" :key="`${pathLabel || 'root'}-${index}`">
        <div v-if="isGroup(condition)" class="condition-group">
          <div class="condition-group-header">
            <el-select
              :model-value="isGroup(condition) === '$or' ? '$or' : '$and'"
              size="small"
              @change="value => setGroupKind(index, value)"
            >
              <el-option label="AND 组" value="$and" />
              <el-option label="OR 组" value="$or" />
            </el-select>
            <el-button size="small" link type="primary" @click="addGroupChild(index, isGroup(condition)!)">添加子条件</el-button>
            <el-button size="small" link type="danger" @click="remove(index)">删除组</el-button>
          </div>
          <ListPresetConditionEditor
            :model-value="getChildren(condition)"
            :columns="columns"
            :qm-model="qmModel"
            :filter-member-loader="filterMemberLoader"
            :max-conditions="maxConditions"
            :show-toolbar="false"
            :condition-offset="conditionOffset + conditionCount - countConditionLeaves(getChildren(condition))"
            :path-label="`${pathLabel || 'root'}-${index}`"
            @update:model-value="value => updateGroupChildren(index, value)"
          />
        </div>

        <div v-else class="condition-row">
          <el-select
            :model-value="getDisplayField(condition.field)"
            class="condition-field"
            size="small"
            filterable
            @change="value => setField(index, value)"
          >
            <el-option
              v-for="column in configurableColumns"
              :key="column.name"
              :label="column.title || column.name"
              :value="column.name"
            />
          </el-select>
          <el-select
            :model-value="condition.op"
            class="condition-operator"
            size="small"
            @change="value => setOperator(index, value)"
          >
            <el-option v-for="operator in operators" :key="operator.value" :label="operator.label" :value="operator.value" />
          </el-select>
          <el-select
            v-if="valueColumn(condition)?.dictItems?.length && !condition.op.startsWith('is ')"
            :model-value="condition.value"
            :multiple="condition.op === 'in' || condition.op === 'not in'"
            filterable
            @update:model-value="value => setSelectedValue(index, value)"
          >
            <el-option v-for="item in valueColumn(condition)?.dictItems" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <SelectFilter
            v-else-if="valueColumn(condition)?.memberLookup?.enabled && (condition.op === '=' || condition.op === 'in')"
            :field="valueColumn(condition)!.name"
            :selection-field="valueColumn(condition)!.memberLookup!.selectionFieldName"
            :model-value="[condition]"
            :qm-model="qmModel"
            :remote-loader="filterMemberLoader"
            @update:model-value="value => setMemberValue(index, value)"
          />
          <el-input
            v-else-if="condition.op !== 'is null' && condition.op !== 'is not null'"
            class="condition-value"
            size="small"
            :model-value="valueToText(condition.value)"
            placeholder="多个值用逗号分隔"
            @update:model-value="value => setValue(index, value)"
          />
          <span v-else class="condition-no-value">无需填写值</span>
          <el-button size="small" link type="danger" @click="remove(index)">删除</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.condition-editor {
  display: grid;
  gap: 8px;
}

.condition-editor-toolbar,
.condition-group-header,
.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.condition-editor-toolbar {
  justify-content: space-between;
  min-height: 28px;
}

.condition-editor-toolbar small {
  margin-left: 8px;
  color: #909399;
}

.condition-editor-actions {
  display: flex;
  gap: 6px;
}

.condition-list {
  display: grid;
  gap: 8px;
}

.condition-row,
.condition-group {
  padding: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 5px;
  background: #fff;
}

.condition-field {
  width: 34%;
}

.condition-operator {
  width: 28%;
}

.condition-value {
  flex: 1;
  min-width: 80px;
}

.condition-no-value,
.condition-empty {
  color: #909399;
  font-size: 12px;
}

.condition-group {
  display: grid;
  gap: 8px;
  background: #fafafa;
}

.condition-group-header {
  justify-content: flex-start;
}

@media (max-width: 760px) {
  .condition-editor-toolbar,
  .condition-row {
    align-items: stretch;
    flex-direction: column;
  }

  .condition-editor-actions,
  .condition-field,
  .condition-operator,
  .condition-value {
    width: 100%;
  }
}
</style>
