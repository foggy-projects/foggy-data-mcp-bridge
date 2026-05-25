<template>
  <div class="list-preset-manager">
    <el-button data-testid="list-preset-open" size="small" :icon="Operation" @click="openDialog">
      {{ buttonText }}
    </el-button>

    <el-dialog
      v-model="visible"
      title="自定义列表"
      width="920px"
      :close-on-click-modal="false"
      @open="loadPresets"
    >
      <div class="preset-layout" data-testid="list-preset-dialog">
        <section class="preset-list-section">
          <div class="preset-toolbar">
            <el-input
              v-model="keyword"
              placeholder="搜索列表"
              :prefix-icon="Search"
              clearable
            />
            <el-button :icon="Refresh" :loading="loading" @click="loadPresets" />
          </div>

          <div v-if="appliedPreset || defaultPreset" class="preset-status-bar">
            <span v-if="appliedPreset">当前已应用：{{ appliedPreset.title }}</span>
            <span v-if="defaultPreset">默认方案：{{ defaultPreset.title }}</span>
          </div>

          <el-scrollbar height="430px">
            <div v-if="loading" class="preset-state">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>加载中</span>
            </div>
            <el-empty v-else-if="filteredPresets.length === 0" description="暂无自定义列表" />

            <article
              v-for="preset in filteredPresets"
              v-else
              :key="preset.id"
              class="preset-item"
              data-testid="list-preset-item"
              :class="{ 'is-active': preset.id === appliedPresetId }"
            >
              <div class="preset-item-main">
                <div class="preset-title-row">
                  <span class="preset-title">{{ preset.title }}</span>
                  <el-tag v-if="preset.isDefault" size="small" type="success">默认</el-tag>
                  <el-tag v-if="preset.id === appliedPresetId" size="small">已应用</el-tag>
                  <el-tag size="small" :type="getVisibilityTagType(preset.visibility)">
                    {{ getVisibilityLabel(preset.visibility) }}
                  </el-tag>
                </div>
                <p v-if="preset.description" class="preset-description">{{ preset.description }}</p>
                <div class="preset-meta">
                  <span>{{ preset.columns.length }} 列</span>
                  <span>{{ preset.query?.slice?.length || 0 }} 条件</span>
                  <span>{{ preset.query?.orderBy?.length || 0 }} 排序</span>
                  <span>{{ formatDate(preset.updatedAt) }}</span>
                </div>
              </div>

              <div class="preset-actions">
                <el-button data-testid="list-preset-apply" link type="primary" @click="applyPreset(preset)">应用</el-button>
                <el-dropdown trigger="click">
                  <el-button data-testid="list-preset-more" link :icon="MoreFilled" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="startEditPreset(preset)">
                        编辑信息
                      </el-dropdown-item>
                      <el-dropdown-item @click="overwritePreset(preset)">
                        覆盖当前
                      </el-dropdown-item>
                      <el-dropdown-item @click="markAsDefault(preset)">
                        <el-icon><Star /></el-icon>
                        设为默认
                      </el-dropdown-item>
                      <el-dropdown-item divided class="danger-item" @click="removePreset(preset)">
                        <el-icon><Delete /></el-icon>
                        删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </article>
          </el-scrollbar>
        </section>

        <section class="preset-save-section">
          <div class="save-section-header">
            <h4>{{ editingPresetId ? '编辑当前方案' : '保存当前列表' }}</h4>
            <el-button v-if="editingPresetId" link type="primary" @click="cancelEdit">
              取消编辑
            </el-button>
          </div>
          <el-form label-position="top" :model="form">
            <el-form-item label="名称" required>
              <div data-testid="list-preset-title" class="preset-input-wrapper">
                <el-input v-model="form.title" maxlength="50" show-word-limit />
              </div>
            </el-form-item>
            <el-form-item label="描述">
              <div data-testid="list-preset-description" class="preset-input-wrapper">
                <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
              </div>
            </el-form-item>
            <el-form-item label="可见范围">
              <el-radio-group v-model="form.visibility">
                <el-radio label="PRIVATE">仅自己</el-radio>
                <el-radio v-if="config.allowShared" label="DEPARTMENT">部门</el-radio>
                <el-radio v-if="config.allowTenantShared" label="TENANT">租户</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="form.isDefault">设为默认</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="form.saveQueryConditions">保存当前筛选和排序</el-checkbox>
            </el-form-item>
          </el-form>

          <div class="column-config-panel">
            <div class="column-config-header">
              <span>字段配置</span>
              <el-button link type="primary" @click="syncColumnDraftFromState">恢复当前</el-button>
            </div>
            <el-scrollbar height="220px">
              <div
                v-for="(column, index) in columnDraft"
                :key="column.name"
                class="column-config-row"
              >
                <el-checkbox v-model="column.visible" class="column-visible" />
                <span class="column-name" :title="column.name">{{ column.title || column.name }}</span>
                <el-input-number
                  v-model="column.width"
                  size="small"
                  :min="40"
                  :step="10"
                  controls-position="right"
                  placeholder="宽"
                />
                <el-select v-model="column.fixed" size="small" placeholder="固定" clearable>
                  <el-option label="左" value="left" />
                  <el-option label="右" value="right" />
                </el-select>
                <div class="column-order-actions">
                  <el-button link :disabled="index === 0" @click="moveColumn(index, -1)">上</el-button>
                  <el-button link :disabled="index === columnDraft.length - 1" @click="moveColumn(index, 1)">下</el-button>
                </div>
              </div>
            </el-scrollbar>
          </div>

          <div class="current-state-summary">
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="当前列数">{{ visibleColumnDraft.length }}</el-descriptions-item>
              <el-descriptions-item label="筛选条件">
                <div class="query-summary">
                  <span>{{ form.saveQueryConditions ? currentState.slice.length : 0 }} 条</span>
                  <span v-for="item in conditionSummary" :key="item" class="query-summary-item">{{ item }}</span>
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="排序规则">
                <div class="query-summary">
                  <span>{{ form.saveQueryConditions ? currentState.orderBy.length : 0 }} 条</span>
                  <span v-for="item in orderSummary" :key="item" class="query-summary-item">{{ item }}</span>
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="分页大小">{{ currentState.pageSize || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </section>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="visible = false">关闭</el-button>
          <el-button v-if="editingPresetId" @click="cancelEdit">取消编辑</el-button>
          <el-button data-testid="list-preset-save" type="primary" :loading="saving" @click="saveCurrentPreset">
            {{ editingPresetId ? '更新方案' : '保存当前' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Operation,
  Search,
  Refresh,
  Loading,
  MoreFilled,
  Delete,
  Star
} from '@element-plus/icons-vue'
import {
  createListPreset,
  deleteListPreset,
  listPresets,
  setDefaultListPreset,
  updateListPreset
} from '@/api/listPreset'
import type { EnhancedColumnSchema, ListPresetConfig, ListPresetDef, ListPresetVisibility, ListViewState } from '@/types'

interface Props {
  config: ListPresetConfig
  getState: () => ListViewState
  applyState: (state: ListViewState) => void
  reload?: () => void | Promise<void>
  availableColumns?: EnhancedColumnSchema[]
}

const props = defineProps<Props>()

interface ColumnDraft {
  name: string
  title?: string
  visible: boolean
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
}

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const presets = ref<ListPresetDef[]>([])
const columnDraft = ref<ColumnDraft[]>([])
const editingPresetId = ref<string | null>(null)
const appliedPresetId = ref<string | null>(null)
const form = ref({
  title: '',
  description: '',
  visibility: 'PRIVATE' as ListPresetVisibility,
  isDefault: false,
  saveQueryConditions: true
})

const buttonText = computed(() => props.config.buttonText || '自定义列表')
const currentState = computed(() => props.getState())
const visibleColumnDraft = computed(() => columnDraft.value.filter(column => column.visible))
const appliedPreset = computed(() => presets.value.find(preset => preset.id === appliedPresetId.value))
const defaultPreset = computed(() => presets.value.find(preset => preset.isDefault))
const conditionSummary = computed(() => {
  if (!form.value.saveQueryConditions) return []
  return currentState.value.slice.slice(0, 3).map(slice => {
    const value = Array.isArray(slice.value) ? slice.value.join(',') : slice.value
    return `${slice.field} ${slice.op} ${value ?? ''}`.trim()
  })
})
const orderSummary = computed(() => {
  if (!form.value.saveQueryConditions) return []
  return currentState.value.orderBy.slice(0, 3).map(order => `${order.field} ${order.order}`)
})
const filteredPresets = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return presets.value
  return presets.value.filter(preset =>
    preset.title.toLowerCase().includes(kw) ||
    preset.description?.toLowerCase().includes(kw)
  )
})

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

function openDialog() {
  syncColumnDraftFromState()
  visible.value = true
}

function syncColumnDraftFromState() {
  columnDraft.value = buildColumnDraft(currentState.value)
}

function buildColumnDraft(state: ListViewState): ColumnDraft[] {
  const sourceColumns: EnhancedColumnSchema[] = props.availableColumns && props.availableColumns.length > 0
    ? props.availableColumns
    : state.columns.map(name => ({ name, type: 'TEXT', title: name }))
  const settingMap = new Map((state.columnSettings || []).map(setting => [setting.name, setting]))
  const visibleNames = new Set(state.columns || [])
  const hasVisibleColumns = visibleNames.size > 0

  return sourceColumns
    .map((column, sourceIndex) => {
      const setting = settingMap.get(column.name)
      return {
        name: column.name,
        title: column.title,
        visible: setting?.visible ?? (!hasVisibleColumns || visibleNames.has(column.name)),
        width: setting?.width ?? column.width,
        minWidth: setting?.minWidth ?? column.minWidth,
        fixed: setting?.fixed ?? column.fixed,
        order: setting?.order ?? sourceIndex
      }
    })
    .sort((left, right) => left.order - right.order)
    .map(column => ({
      name: column.name,
      title: column.title,
      visible: column.visible,
      width: column.width,
      minWidth: column.minWidth,
      fixed: column.fixed
    }))
}

function buildStateFromDraft(state: ListViewState): ListViewState {
  const draft = columnDraft.value.length > 0 ? columnDraft.value : buildColumnDraft(state)
  const saveQueryConditions = form.value.saveQueryConditions
  return {
    columns: draft.filter(column => column.visible).map(column => column.name),
    columnSettings: draft.map((column, index) => ({
      name: column.name,
      visible: column.visible,
      width: column.width,
      minWidth: column.minWidth,
      fixed: column.fixed,
      order: index
    })),
    slice: saveQueryConditions ? state.slice : [],
    orderBy: saveQueryConditions ? state.orderBy : [],
    pageSize: state.pageSize
  }
}

function getAvailableColumnNameSet(): Set<string> | null {
  if (!props.availableColumns || props.availableColumns.length === 0) return null
  return new Set(props.availableColumns.map(column => column.name))
}

function getUnavailableFields(preset: ListPresetDef): string[] {
  const availableNames = getAvailableColumnNameSet()
  if (!availableNames) return []
  const presetFields = new Set([
    ...preset.columns,
    ...(preset.columnSettings || []).map(setting => setting.name)
  ])
  return [...presetFields].filter(field => !availableNames.has(field))
}

function ensureHasVisibleColumns(state: ListViewState): boolean {
  if (state.columns.length > 0) return true
  ElMessage.warning('请至少保留一个显示字段')
  return false
}

function moveColumn(index: number, direction: -1 | 1) {
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= columnDraft.value.length) return
  const next = columnDraft.value.slice()
  const current = next[index]
  const target = next[nextIndex]
  if (!current || !target) return
  next[index] = target
  next[nextIndex] = current
  columnDraft.value = next
}

function resetForm() {
  editingPresetId.value = null
  form.value = {
    title: '',
    description: '',
    visibility: 'PRIVATE',
    isDefault: false,
    saveQueryConditions: true
  }
}

async function loadPresets() {
  loading.value = true
  try {
    presets.value = await listPresets({
      userId: props.config.userId,
      model: props.config.model,
      businessKey: props.config.businessKey
    })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '加载自定义列表失败'))
  } finally {
    loading.value = false
  }
}

async function applyPreset(preset: ListPresetDef) {
  const unavailableFields = getUnavailableFields(preset)
  appliedPresetId.value = preset.id
  props.applyState({
    columns: preset.columns,
    columnSettings: preset.columnSettings,
    slice: preset.query?.slice || [],
    orderBy: preset.query?.orderBy || [],
    pageSize: preset.pageSize
  })
  await props.reload?.()
  visible.value = false
  if (unavailableFields.length > 0) {
    ElMessage.warning(`已忽略失效字段: ${unavailableFields.slice(0, 3).join('、')}${unavailableFields.length > 3 ? '...' : ''}`)
  }
  ElMessage.success(`已应用: ${preset.title}`)
}

function startEditPreset(preset: ListPresetDef) {
  editingPresetId.value = preset.id
  form.value = {
    title: preset.title,
    description: preset.description || '',
    visibility: preset.visibility,
    isDefault: Boolean(preset.isDefault),
    saveQueryConditions: true
  }
}

function cancelEdit() {
  resetForm()
}

async function saveCurrentPreset() {
  const title = form.value.title.trim()
  if (!title) {
    ElMessage.warning('请输入列表名称')
    return
  }

  const state = buildStateFromDraft(currentState.value)
  if (!ensureHasVisibleColumns(state)) return
  saving.value = true
  try {
    const wasEditing = Boolean(editingPresetId.value)
    const request = {
      title,
      description: form.value.description.trim() || undefined,
      columns: state.columns,
      columnSettings: state.columnSettings,
      query: {
        slice: state.slice,
        orderBy: state.orderBy
      },
      pageSize: state.pageSize,
      visibility: form.value.visibility,
      isDefault: form.value.isDefault
    }

    const saved = wasEditing && editingPresetId.value
      ? await updateListPreset(props.config.userId, editingPresetId.value, request)
      : await createListPreset({
        userId: props.config.userId,
        model: props.config.model,
        businessKey: props.config.businessKey
      }, request)

    presets.value = [saved, ...presets.value.filter(preset => preset.id !== saved.id)]
    if (saved.isDefault) {
      presets.value = presets.value.map(preset => ({
        ...preset,
        isDefault: preset.id === saved.id
      }))
    }
    resetForm()
    ElMessage.success(wasEditing ? '自定义列表已更新' : '自定义列表已保存')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, editingPresetId.value ? '更新自定义列表失败' : '保存自定义列表失败'))
  } finally {
    saving.value = false
  }
}

async function overwritePreset(preset: ListPresetDef) {
  const state = buildStateFromDraft(currentState.value)
  if (!ensureHasVisibleColumns(state)) return
  saving.value = true
  try {
    const saved = await updateListPreset(props.config.userId, preset.id, {
      title: preset.title,
      description: preset.description,
      columns: state.columns,
      columnSettings: state.columnSettings,
      query: {
        slice: state.slice,
        orderBy: state.orderBy
      },
      pageSize: state.pageSize,
      visibility: preset.visibility,
      isDefault: preset.isDefault
    })
    presets.value = presets.value.map(item => (item.id === saved.id ? saved : item))
    if (saved.isDefault) {
      presets.value = presets.value.map(item => ({
        ...item,
        isDefault: item.id === saved.id
      }))
    }
    ElMessage.success('已覆盖自定义列表')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '覆盖自定义列表失败'))
  } finally {
    saving.value = false
  }
}

async function markAsDefault(preset: ListPresetDef) {
  try {
    const updated = await setDefaultListPreset(props.config.userId, preset.id)
    presets.value = presets.value.map(item => ({
      ...item,
      isDefault: item.id === updated.id
    }))
    ElMessage.success('默认列表已更新')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '设置默认列表失败'))
  }
}

async function removePreset(preset: ListPresetDef) {
  try {
    await ElMessageBox.confirm(`确定删除"${preset.title}"吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    await deleteListPreset(props.config.userId, preset.id)
    presets.value = presets.value.filter(item => item.id !== preset.id)
    if (editingPresetId.value === preset.id) {
      resetForm()
    }
    if (appliedPresetId.value === preset.id) {
      appliedPresetId.value = null
    }
    ElMessage.success('自定义列表已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '删除自定义列表失败'))
    }
  }
}

function getVisibilityLabel(visibility: ListPresetVisibility): string {
  switch (visibility) {
    case 'DEPARTMENT':
      return '部门'
    case 'TENANT':
      return '租户'
    case 'PRIVATE':
    default:
      return '仅自己'
  }
}

function getVisibilityTagType(visibility: ListPresetVisibility): 'info' | 'warning' | 'success' {
  switch (visibility) {
    case 'DEPARTMENT':
      return 'warning'
    case 'TENANT':
      return 'success'
    case 'PRIVATE':
    default:
      return 'info'
  }
}

function formatDate(value: string): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

defineExpose({
  applyPreset,
  cancelEdit,
  loadPresets,
  openDialog,
  overwritePreset,
  saveCurrentPreset,
  startEditPreset,
  setDraft: (draft: Partial<typeof form.value>) => {
    form.value = {
      ...form.value,
      ...draft
    }
  },
  getDraft: () => form.value,
  getPresets: () => presets.value,
  getColumnDraft: () => columnDraft.value,
  syncColumnDraftFromState
})
</script>

<style scoped>
.list-preset-manager {
  display: inline-flex;
}

.preset-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
}

.preset-list-section,
.preset-save-section {
  min-width: 0;
}

.preset-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.preset-status-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #606266;
}

.preset-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 160px;
  color: #909399;
}

.preset-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.preset-item.is-active {
  border-color: #409eff;
  background: #ecf5ff;
}

.preset-item-main {
  min-width: 0;
}

.preset-title-row,
.preset-meta,
.preset-actions {
  display: flex;
  align-items: center;
}

.preset-title-row {
  gap: 6px;
}

.preset-title {
  max-width: 240px;
  overflow: hidden;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset-description {
  margin: 8px 0;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.preset-meta {
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #606266;
}

.preset-actions {
  flex-shrink: 0;
  gap: 4px;
}

.save-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
}

.save-section-header h4 {
  margin: 0;
  font-size: 14px;
  color: #303133;
}

.preset-input-wrapper {
  width: 100%;
}

.current-state-summary {
  margin-top: 12px;
}

.query-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.query-summary-item {
  max-width: 180px;
  overflow: hidden;
  border-radius: 3px;
  background: #f5f7fa;
  padding: 1px 5px;
  color: #606266;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-config-panel {
  margin-top: 14px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}

.column-config-header,
.column-config-row,
.column-order-actions {
  display: flex;
  align-items: center;
}

.column-config-header {
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.column-config-row {
  gap: 6px;
  padding: 5px 0;
}

.column-visible {
  flex-shrink: 0;
}

.column-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-config-row :deep(.el-input-number) {
  width: 82px;
}

.column-config-row :deep(.el-select) {
  width: 74px;
}

.column-order-actions {
  flex-shrink: 0;
  gap: 2px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.danger-item {
  color: #f56c6c;
}
</style>
