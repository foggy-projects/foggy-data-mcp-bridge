<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ColumnSchema, SliceRequestDef, MemberQueryRequest, MemberQueryResponse } from '@/types'
import { cloneSliceTree, countConditionLeaves, getDisplayColumnForCondition, getQueryFieldForColumn, getUserConfigurableColumns } from '@/utils/listPreset'
import { isRelativeDateValue, relativeDateOptions } from '@/utils/customQuery'
import ListPresetConditionEditor from './ListPresetConditionEditor.vue'

const props = defineProps<{
  modelValue: SliceRequestDef[]
  columns: ColumnSchema[]
  qmModel?: string
  filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
}>()
const emit = defineEmits<{ (event: 'update:modelValue', value: SliceRequestDef[]): void }>()
const selectedPath = ref<number[]>([])
const nodeMenus = new Map<string, { handleOpen: () => void }>()
function setNodeMenu(key: string, menu: unknown) {
  if (menu) nodeMenus.set(key, menu as { handleOpen: () => void })
  else nodeMenus.delete(key)
}
function openNodeMenu(node: { key: string; path: number[] }) {
  selectedPath.value = node.path
  nodeMenus.get(node.key)?.handleOpen()
}
const pickerVisible = ref(false)
const pickerMode = ref<'add' | 'switch'>('add')
const keyword = ref('')
const typeFilter = ref('全部')
const types = ['全部', '文本', '选项', '日期', '日期时间', '数值']
const fields = computed(() => getUserConfigurableColumns(props.columns))
const count = computed(() => countConditionLeaves(props.modelValue))
function groupKey(node: SliceRequestDef) {
  return (['$and', '$or', 'children'] as const).find(key => Array.isArray(node[key]))
}
function children(node: SliceRequestDef): SliceRequestDef[] { const key = groupKey(node); return key ? node[key]! : [] }
function nodeAt(tree: SliceRequestDef[], path: number[]): SliceRequestDef | undefined {
  let nodes = tree
  let node: SliceRequestDef | undefined
  for (const index of path) { node = nodes[index]; if (!node) return; nodes = children(node) }
  return node
}
const selected = computed(() => nodeAt(props.modelValue, selectedPath.value))
const isRoot = computed(() => !selectedPath.value.length || !selected.value)
const isGroup = computed(() => isRoot.value || !!groupKey(selected.value!))
const selectedChildren = computed(() => isRoot.value ? props.modelValue : children(selected.value!))
function fieldFor(node: SliceRequestDef) { return getDisplayColumnForCondition(node.field, fields.value) }
function label(node: SliceRequestDef) {
  const key = groupKey(node)
  return key ? `${key === '$or' ? 'OR' : 'AND'} 条件组` : fieldFor(node)?.title || node.field
}
function summary(node: SliceRequestDef): string {
  if (groupKey(node)) return `${countConditionLeaves([node])} 条用户条件`
  if (node.op.startsWith('is ')) return node.op === 'is null' ? '为空' : '不为空'
  if (isRelativeDateValue(node.value)) { const value = node.value.$relativeDate; return relativeDateOptions.find(option => option.value === value)?.label || '' }
  const values = (Array.isArray(node.value) ? node.value : [node.value]).filter(value => value != null && value !== '')
  if (!values.length) return '待填写取值'
  const field = fieldFor(node)
  if (field?.dictItems?.length) return values.map(value => field.dictItems!.find(item => item.value === value)?.label || '已选选项').join('、')
  if (field?.memberLookup?.enabled) return `已选 ${values.length} 个成员`
  return values.join('、')
}
function fieldType(field: ColumnSchema) {
  if (field.dictId || field.dictItems?.length || field.memberLookup?.enabled) return '选项'
  if (field.filterType === 'datetime' || field.type.toUpperCase() === 'DATETIME') return '日期时间'
  if (field.filterType === 'date' || ['DATE', 'DAY'].includes(field.type.toUpperCase())) return '日期'
  return ['NUMBER', 'INTEGER', 'LONG', 'DOUBLE', 'BIGDECIMAL', 'MONEY'].includes(field.type.toUpperCase()) ? '数值' : '文本'
}
const filteredFields = computed(() => fields.value.filter(field =>
  (typeFilter.value === '全部' || fieldType(field) === typeFilter.value) &&
  `${field.title || ''} ${field.name}`.toLowerCase().includes(keyword.value.trim().toLowerCase())))
interface TreeNode { key: string; path: number[]; label: string; summary: string; group: boolean; children?: TreeNode[] }
function treeNodes(nodes: SliceRequestDef[], path: number[] = []): TreeNode[] {
  return nodes.map((node, index) => {
    const nextPath = [...path, index]
    return { key: nextPath.join('.'), path: nextPath, label: label(node), summary: summary(node), group: !!groupKey(node), children: treeNodes(children(node), nextPath) }
  })
}
const tree = computed(() => [{ key: 'root', path: [], label: '全部同时满足', summary: `${count.value} 条用户条件`, group: true, children: treeNodes(props.modelValue) }])
const breadcrumb = computed(() => ['全部同时满足', ...selectedPath.value.map((_, index) => {
  const node = nodeAt(props.modelValue, selectedPath.value.slice(0, index + 1)); return node ? label(node) : ''
}).filter(Boolean)])
function targetPath() { return isGroup.value ? (isRoot.value ? [] : selectedPath.value) : selectedPath.value.slice(0, -1) }
function openPicker(mode: 'add' | 'switch' = 'add') {
  if (mode === 'add' && count.value >= 20) { ElMessage.warning('一个方案最多可添加 20 条用户条件'); return }
  pickerMode.value = mode; keyword.value = ''; typeFilter.value = '全部'; pickerVisible.value = true
}
function pickField(field: ColumnSchema) {
  if (pickerMode.value === 'add' && count.value >= 20) return
  const next = cloneSliceTree(props.modelValue)
  const kind = fieldType(field)
  const leaf = { field: getQueryFieldForColumn(field)!, op: kind === '日期' || kind === '日期时间' ? '[)' : '=', value: undefined }
  if (pickerMode.value === 'switch' && !isRoot.value) {
    const path = selectedPath.value
    const parent = path.length === 1 ? next : children(nodeAt(next, path.slice(0, -1))!)
    parent[path[path.length - 1]] = leaf
  } else {
    const path = targetPath()
    const parent = path.length ? children(nodeAt(next, path)!) : next
    selectedPath.value = [...path, parent.length]; parent.push(leaf)
  }
  emit('update:modelValue', next); pickerVisible.value = false
}
function addGroup(key: '$and' | '$or') {
  if (selectedPath.value.length >= 8) { ElMessage.warning('条件组最多嵌套 8 层'); return }
  const next = cloneSliceTree(props.modelValue)
  const path = targetPath()
  const parent = path.length ? children(nodeAt(next, path)!) : next
  selectedPath.value = [...path, parent.length]
  parent.push({ [key]: [] } as unknown as SliceRequestDef)
  emit('update:modelValue', next)
}
function removeSelected() {
  if (isRoot.value) return
  const next = cloneSliceTree(props.modelValue)
  const path = selectedPath.value
  const parentPath = path.slice(0, -1)
  const parent = parentPath.length ? children(nodeAt(next, parentPath)!) : next
  parent.splice(path[path.length - 1], 1)
  selectedPath.value = parentPath; emit('update:modelValue', next)
}
function updateSelected(value: SliceRequestDef[]) {
  if (!value.length) { removeSelected(); return }
  const next = cloneSliceTree(props.modelValue)
  const path = selectedPath.value
  const parent = path.length === 1 ? next : children(nodeAt(next, path.slice(0, -1))!)
  parent[path[path.length - 1]] = value[0]
  emit('update:modelValue', next)
}
function toggleGroup() {
  if (isRoot.value) return
  updateSelected([{ [groupKey(selected.value!) === '$or' ? '$and' : '$or']: cloneSliceTree(selectedChildren.value) } as unknown as SliceRequestDef])
}
</script>

<template>
  <div class="condition-workbench" data-testid="condition-workbench">
    <aside class="condition-tree-panel" aria-label="条件结构树">
      <header><div><h3>条件结构</h3><p>{{ count }} / 20 条用户条件</p></div><el-button aria-label="新增条件到第一层" circle @click="selectedPath = []; openPicker()">＋</el-button></header>
      <el-tree :data="tree" node-key="key" default-expand-all :expand-on-click-node="false" highlight-current
        :current-node-key="selectedPath.join('.') || 'root'" @node-click="node => selectedPath = node.path">
        <template #default="{ data }">
          <div class="condition-tree-row" :data-testid="`condition-node-${data.key}`" @contextmenu.prevent.stop="openNodeMenu(data)">
            <span class="node-symbol" :class="{ group: data.group }">{{ data.group ? (data.label.startsWith('OR') ? '∨' : '∧') : 'ƒ' }}</span>
            <span class="tree-node-copy"><b>{{ data.label }}</b><small>{{ data.summary }}</small></span>
            <button class="tree-node-add" aria-label="新增条件" title="新增条件" @click.stop="selectedPath = data.path; openPicker()">＋</button>
            <el-dropdown :ref="menu => setNodeMenu(data.key, menu)" trigger="click" @visible-change="open => { if (open) selectedPath = data.path }">
              <button class="tree-node-menu" aria-label="打开操作菜单" title="打开操作菜单" @click.stop="selectedPath = data.path">⋯</button>
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item @click="openPicker()">新增条件</el-dropdown-item>
                <el-dropdown-item @click="addGroup('$and')">新增 AND 组</el-dropdown-item>
                <el-dropdown-item @click="addGroup('$or')">新增 OR 组</el-dropdown-item>
                <el-dropdown-item divided :disabled="!data.path.length" @click="removeSelected()">{{ !data.path.length ? '根节点不可删除' : data.group ? '删除当前条件组' : '删除当前条件' }}</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
          </div>
        </template>
      </el-tree>
    </aside>
    <section class="condition-workspace" aria-label="条件编辑区">
      <div class="condition-breadcrumb">{{ breadcrumb.join('  /  ') }}</div>
      <div class="condition-canvas" :class="{ 'is-leaf': !isGroup }">
        <template v-if="isGroup">
          <header class="condition-editor-head"><div><span class="logic-token">{{ !isRoot && groupKey(selected!) === '$or' ? 'OR' : 'AND' }}</span><b>{{ !isRoot && groupKey(selected!) === '$or' ? '满足任一条件' : '全部同时满足' }}</b></div>
            <el-button v-if="!isRoot" link type="primary" @click="toggleGroup">切换为 {{ groupKey(selected!) === '$or' ? 'AND' : 'OR' }}</el-button><small v-else>根节点逻辑固定为 AND</small>
          </header>
          <div class="children-heading"><b>组内节点</b><span>{{ selectedChildren.length }} 个直接节点</span></div>
          <button v-for="(child, index) in selectedChildren" :key="index" class="condition-child" @click="selectedPath = [...(isRoot ? [] : selectedPath), index]">
            <span class="node-symbol" :class="{ group: !!groupKey(child) }">{{ groupKey(child) === '$or' ? '∨' : groupKey(child) ? '∧' : 'ƒ' }}</span>
            <span><b>{{ label(child) }}</b><small>{{ summary(child) }}</small></span><span class="edit-child">编辑 ›</span>
          </button>
          <div v-if="!selectedChildren.length" class="condition-empty-state"><p>这个条件组还没有条件</p><el-button type="primary" plain @click="openPicker()">添加第一个条件</el-button></div>
          <p class="condition-note">{{ !isRoot && groupKey(selected!) === '$or' ? '组内任一条件满足即可。' : '组内所有条件都需要满足。' }} 条件组可以继续嵌套。页面固定规则会在查询时自动加入。</p>
          <el-button v-if="!isRoot" link type="danger" @click="removeSelected">删除此条件组</el-button>
        </template>
        <template v-else>
          <header class="condition-editor-head"><div class="leaf-heading"><span class="node-symbol">ƒ</span><div><h3>编辑条件：{{ label(selected!) }}</h3><small>{{ fieldFor(selected!) ? fieldType(fieldFor(selected!)!) : '条件' }}</small></div></div><el-button link type="primary" @click="openPicker('switch')">切换字段</el-button></header>
          <div class="leaf-labels"><span>匹配方式</span><span>取值</span></div>
          <ListPresetConditionEditor class="focused-condition-editor" :model-value="[selected!]" :columns="columns" :show-toolbar="false" :qm-model="qmModel" :filter-member-loader="filterMemberLoader" @update:model-value="updateSelected" />
          <p class="condition-note">空条件可以保留在方案中，查询时自动忽略。选项显示名称，查询使用对应值；多选按 IN 执行。</p>
        </template>
      </div>
    </section>
    <el-dialog v-model="pickerVisible" :title="pickerMode === 'switch' ? '切换条件字段' : '选择条件字段'" width="min(860px, calc(100vw - 40px))" append-to-body class="condition-field-picker">
      <p>字段来自当前页面可用范围，不要求先作为表格展示列。</p>
      <el-input v-model="keyword" placeholder="搜索字段名称" clearable aria-label="搜索条件字段" />
      <div class="picker-types"><button v-for="type in types" :key="type" :class="{ active: typeFilter === type }" @click="typeFilter = type">{{ type }}</button></div>
      <div class="condition-field-grid"><button v-for="field in filteredFields" :key="field.name" class="condition-field-card" @click="pickField(field)"><b>{{ field.title || field.name }}</b><span>{{ fieldType(field) }}</span></button></div>
      <p v-if="!filteredFields.length">没有符合条件的字段</p>
      <template #footer><span>已添加 {{ count }} / 20 条用户条件</span></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.condition-workbench { display: grid; grid-template-columns: 330px minmax(0, 1fr); min-height: 540px; color: #172033; }
.condition-tree-panel { background: #f8fafc; border-right: 1px solid #e3e8f0; padding: 22px 16px; min-width: 0; }
.condition-tree-panel header, .condition-editor-head, .children-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h3 { margin: 0; font-size: 15px; } p, small { color: #7c889a; font-size: 12px; }
.condition-tree-panel :deep(.el-tree) { background: transparent; max-height: 400px; overflow: auto; margin: 16px 0; }
.condition-tree-panel :deep(.el-tree-node__content) { height: 56px; border-radius: 7px; }
.condition-tree-row, .leaf-heading { display: flex; align-items: center; gap: 10px; min-width: 0; }
.condition-tree-row b, .condition-tree-row small, .condition-child b, .condition-child small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; }
.condition-tree-row b, .condition-child b { font-size: 12px; }
.condition-tree-row small, .condition-child small { margin-top: 5px; font-size: 11px; }
.node-symbol { display: grid; place-items: center; flex: 0 0 28px; height: 28px; border-radius: 7px; background: #eaf3ff; color: #1867d5; font-weight: 700; }
.node-symbol.group { background: #edf8f2; color: #237a54; }
.condition-tree-row { width: 100%; gap: 6px; padding-right: 6px; }
.tree-node-copy { flex: 1; min-width: 0; }
.tree-node-add, .tree-node-menu { flex-shrink: 0; border: 0; border-radius: 4px; background: transparent; color: #6b809b; width: 20px; height: 26px; padding: 0; cursor: pointer; }
.tree-node-add { opacity: 0; color: #1867d5; }
.condition-tree-row:hover .tree-node-add, .condition-tree-row:focus-within .tree-node-add, :deep(.is-current > .el-tree-node__content) .tree-node-add { opacity: 1; }
.tree-node-add:hover, .tree-node-menu:hover { background: #e7f1ff; color: #1867d5; }
.tree-node-add:focus-visible, .tree-node-menu:focus-visible { outline: 2px solid #72a9ed; outline-offset: 1px; }
.condition-canvas.is-leaf { box-sizing: border-box; width: 100%; max-width: 768px; }
.condition-workspace { padding: 26px 30px; min-width: 0; }
.condition-breadcrumb { color: #7c889a; font-size: 12px; margin-bottom: 20px; }
.condition-canvas { border: 1px solid #dce5f0; border-radius: 10px; background: white; padding: 24px; }
.condition-editor-head { min-height: 42px; padding-bottom: 20px; border-bottom: 1px solid #eaf0f7; }
.condition-editor-head > div { display: flex; align-items: center; gap: 10px; }
.logic-token { padding: 6px 9px; background: #eaf3ff; border-radius: 5px; font-size: 12px; color: #1867d5; font-weight: 700; }
.children-heading { margin: 20px 0 12px; font-size: 12px; }.children-heading span { color: #7c889a; }
.condition-child { width: 100%; display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid #e0e7f0; border-radius: 8px; background: white; margin-bottom: 9px; text-align: left; cursor: pointer; }
.condition-child:hover { border-color: #80afea; background: #f8fbff; }.edit-child { margin-left: auto; color: #1867d5; font-size: 12px; }
.condition-note { padding: 12px; background: #f7f9fc; border-radius: 7px; line-height: 1.7; margin-top: 20px; }
.condition-empty-state { text-align: center; padding: 35px; }.leaf-labels { display: grid; grid-template-columns: 150px 1fr; gap: 12px; margin: 24px 0 10px; font-size: 12px; color: #53647b; }
.focused-condition-editor :deep(.condition-field) { display: none; }.focused-condition-editor :deep(.condition-row) { border: 0; padding: 0; align-items: start; flex-wrap: wrap; gap: 12px; }
.focused-condition-editor :deep(.condition-operator) { width: 150px; flex: 0 0 150px; }.focused-condition-editor :deep(.condition-row > .el-button) { flex-basis: 100%; margin: 16px 0 0; justify-content: start; }
.focused-condition-editor :deep(.filter-select), .focused-condition-editor :deep(.condition-value) { flex: 1; min-width: 180px; }
.picker-types { display: flex; gap: 6px; margin: 14px 0; }.picker-types button { border: 1px solid #d9e1eb; border-radius: 6px; background: white; padding: 7px 12px; color: #6e7c91; cursor: pointer; }.picker-types button.active { color: #0c4fae; border-color: #91b9eb; background: #eaf3ff; }
.condition-field-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; max-height: 370px; overflow: auto; }
.condition-field-card { min-height: 72px; border: 1px solid #dfe6ef; border-radius: 8px; padding: 12px; background: white; display: flex; align-items: center; justify-content: space-between; gap: 8px; cursor: pointer; color: #172033; }.condition-field-card:hover { border-color: #80afea; background: #f8fbff; }.condition-field-card span { color: #6b7689; font-size: 11px; }
@media (max-width: 900px) { .condition-workbench { grid-template-columns: 260px minmax(0, 1fr); } .condition-workspace { padding: 20px 16px; } .condition-canvas { padding: 16px; } .condition-field-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
