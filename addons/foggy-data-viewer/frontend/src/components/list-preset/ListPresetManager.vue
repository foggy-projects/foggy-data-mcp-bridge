<template>
  <div class="list-preset-manager">
    <template v-if="triggerMode !== 'none'">
      <el-button data-testid="list-preset-open" size="small" :icon="Operation" @click="openDialog">{{ buttonText }}</el-button>
      <el-button v-if="clearConditionsEnabled" data-testid="list-preset-clear-conditions" size="small" :icon="Brush" circle title="清空查询条件" aria-label="清空查询条件" :loading="clearing" @click="clearCurrentConditions" />
    </template>
    <el-dialog v-model="visible" class="list-preset-dialog query-wizard-dialog" :title="dialogTitle"
      width="min(1450px, calc(100vw - 48px))" top="3vh" :close-on-click-modal="false" @open="loadPresets">
      <template #header><div class="wizard-title"><div><h2>{{ dialogMode === 'load' ? '查询管理' : editingPresetId ? '编辑自定义查询' : '新建自定义查询' }}</h2><p>方案将保存个人展示、条件、排序与分页偏好</p></div><span v-if="dialogMode !== 'load'" class="wizard-draft">草稿</span></div></template>
      <div data-testid="list-preset-dialog" class="query-wizard" :class="`is-${dialogMode}-mode`">
        <nav v-if="dialogMode !== 'load'" class="wizard-progress" aria-label="配置进度">
          <button v-for="(step, index) in wizardSteps" :key="step.key" class="wizard-step" :class="{ active: inspectorTab === step.key, done: wizardStepIndex > index }"
            :aria-current="inspectorTab === step.key ? 'step' : undefined" :data-testid="`wizard-step-${step.key}`" @click="goStep(step.key)">
            <span class="wizard-step-dot">{{ wizardStepIndex > index ? '✓' : index + 1 }}</span><span><b>{{ step.title }}</b><small>{{ step.subtitle }}</small></span>
          </button>
        </nav>
        <div v-if="dialogMode === 'load'" class="wizard-load">
        <section class="preset-list-section">
          <div class="section-header">
            <div>
              <h4>查询方案</h4>
              <span>{{ presets.length }} 个方案</span>
            </div>
            <el-button :icon="Refresh" :loading="loading" circle @click="loadPresets" />
          </div>

          <el-input
            v-model="keyword"
            class="preset-search"
            placeholder="搜索方案"
            :prefix-icon="Search"
            clearable
          />

          <div v-if="appliedPreset || defaultPreset" class="preset-status-bar">
            <span v-if="appliedPreset">当前：{{ appliedPreset.title }}</span>
            <span v-if="defaultPreset">默认：{{ defaultPreset.title }}</span>
          </div>

          <el-scrollbar class="preset-scroll">
            <div v-if="loading" class="preset-state">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>加载中</span>
            </div>
            <el-empty v-else-if="filteredPresets.length === 0" description="暂无自定义查询" />

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
                </div>
                <p v-if="preset.description" class="preset-description">{{ preset.description }}</p>
                <div class="preset-meta">
                  <span>{{ preset.columns.length }} 列</span>
                  <span>{{ preset.query?.slice?.length || 0 }} 条件</span>
                  <span>{{ preset.query?.orderBy?.length || 0 }} 排序</span>
                  <el-tag size="small" :type="getVisibilityTagType(preset.visibility)">
                    {{ getVisibilityLabel(preset.visibility) }}
                  </el-tag>
                </div>
                <div class="preset-time">{{ formatDate(preset.updatedAt) }}</div>
              </div>

              <div class="preset-actions">
                <el-button data-testid="list-preset-apply" link type="primary" @click="applyPreset(preset)">应用</el-button>
                <el-button data-testid="list-preset-edit" link :icon="Edit" title="快捷编辑方案" aria-label="快捷编辑方案" @click="startEditPreset(preset)">编辑方案</el-button>
                <el-button data-testid="list-preset-overwrite" link @click="overwritePreset(preset)">覆盖当前</el-button>
                <el-button data-testid="list-preset-default" link @click="markAsDefault(preset)">设为默认</el-button>
                <el-button data-testid="list-preset-delete" link type="danger" @click="removePreset(preset)">删除</el-button>
              </div>
            </article>
          </el-scrollbar>
        </section>


        </div>
        <div v-else-if="inspectorTab === 'columns'" class="wizard-fields">
          <aside class="wizard-selected" aria-label="已选展示字段">
            <header class="wizard-panel-head"><div><h3>已选展示字段</h3><p>拖动调整表格列顺序</p></div><div class="wizard-count">{{ visibleColumnDraft.length }}<small>/ 50</small></div></header>
            <p class="wizard-note drag-feedback" role="status" aria-live="polite" :class="{ 'is-active': draggedColumn }">
              <span class="drag-feedback-text">
                <template v-if="dragFeedback">↕ {{ dragFeedback }}</template>
                <template v-else>↕ 拖动字段调整顺序：拖到上下边缘可插入，拖到中部可交换位置；点击编辑设置列宽和固定位置。</template>
              </span>
            </p>
            <div class="selected-list" :class="{ 'is-drop-ready': dragOrigin === 'available' && draggedColumn, 'drop-append': dragOver?.target === appendDropTarget }"
              @dragover.prevent="onSelectedListDragOver" @dragleave="onSelectedListDragLeave" @drop.prevent="dropSelectedList">
              <el-empty v-if="!visibleColumnDraft.length" description="从右侧选择展示字段" />
              <article v-for="(column, index) in visibleColumnDraft" :key="column.name" class="selected-row" :class="{
                  'is-editing': activeColumnEditor === column.name,
                  'is-dragging': draggedColumn === column.name,
                  'drop-before': dragOver?.target === column.name && dragOver.mode === 'insert-before',
                  'drop-after': dragOver?.target === column.name && dragOver.mode === 'insert-after',
                  'drop-swap': dragOver?.target === column.name && dragOver.mode === 'swap',
                  'is-recently-moved': recentlyMovedColumns.includes(column.name)
                }"
                draggable="true" tabindex="0" @dragstart="startColumnDrag(column.name, $event)" @dragend="clearColumnDrag" @dragover.prevent.stop="onColumnDragOver(column.name, $event)" @dragleave="onColumnDragLeave(column.name, $event)" @drop.prevent.stop="dropColumn(column.name, $event)"
                @keydown.alt.up.prevent="moveVisibleColumn(index, -1)" @keydown.alt.down.prevent="moveVisibleColumn(index, 1)">
                <span class="drag-icon" title="拖动排序；Alt + 方向键可调整">⠿</span><span class="wizard-field-rank">{{ index + 1 }}</span>
                <div class="selected-main"><b>{{ column.title || column.name }}</b><small>{{ column.width || '自动' }} px · {{ column.fixed === 'left' ? '左固定' : column.fixed === 'right' ? '右固定' : '不固定' }}</small></div>
                <div class="selected-actions" @click.stop>
                  <el-button :icon="Edit" link size="small" title="编辑字段配置" aria-label="编辑字段配置" @click="toggleColumnEditor(column.name)" />
                  <el-button :icon="Delete" link size="small" title="移除字段" aria-label="移除字段" :disabled="isColumnLocked(column.name)" @click="removeColumn(column.name)" />
                </div>
                <div v-if="activeColumnEditor === column.name" class="selected-editor" @click.stop>
                  <label class="editor-field"><span>列宽</span><el-input-number v-model="column.width" size="small" :min="40" :max="1000" :step="10" controls-position="right" /></label>
                  <label class="editor-field"><span>固定位置</span><el-select v-model="column.fixed" size="small" placeholder="不固定" clearable><el-option label="左固定" value="left" /><el-option label="右固定" value="right" /></el-select></label>
                  <div class="editor-actions"><el-button size="small" link @click="moveVisibleColumnToEdge(index, 'top')">移到顶部</el-button><el-button size="small" link @click="moveVisibleColumnToEdge(index, 'bottom')">移到底部</el-button><el-button size="small" link @click="resetColumnSettings(column.name)">恢复默认</el-button><el-button size="small" link type="primary" @click="closeColumnEditor">完成</el-button></div>
                </div>
              </article>
            </div>
          </aside>
          <section class="wizard-field-workspace">
            <header class="wizard-workspace-head"><div><span class="wizard-eyebrow">STEP 01 / 03</span><h2>选择展示字段</h2><p>选择要显示在表格中的列，并调整它们的顺序。</p></div><div class="wizard-field-count"><b>{{ visibleColumnDraft.length }} / 50</b><small>已选展示字段</small></div></header>
            <p class="wizard-scope-note">ⓘ 这里只决定表格展示列。查询条件可以使用其他可用字段，不必先作为展示列。</p>
            <div class="wizard-field-tools"><el-input v-model="fieldKeyword" placeholder="搜索字段名称" :prefix-icon="Search" clearable /><div class="wizard-type-filters"><button v-for="type in wizardFieldTypes" :key="type" :class="{ active: wizardFieldType === type }" @click="wizardFieldType = type">{{ type }}</button></div></div>
            <div class="wizard-field-section-head"><b>可选字段 <small>{{ wizardFilteredColumns.length }} 个</small></b><div><el-button size="small" @click="selectFilteredColumns">选择当前结果</el-button><el-button size="small" @click="clearOptionalColumns">清空已选</el-button></div></div>
            <div class="wizard-field-grid">
              <button v-for="column in wizardFilteredColumns" :key="column.name" class="field-row wizard-field-card" :class="{ 'is-selected': column.visible, 'is-draggable-source': !column.visible }" :aria-pressed="column.visible" :draggable="!column.visible" @dragstart="!column.visible && startAvailableColumnDrag(column.name, $event)" @dragend="clearColumnDrag" @click="toggleColumn(column.name, !column.visible)">
                <span class="wizard-check">{{ column.visible ? '✓' : '' }}</span><span><b>{{ column.title || column.name }}</b><small>{{ column.visible ? '已加入展示' : '点击加入展示' }}</small></span><span class="wizard-type-pill">{{ wizardColumnType(column) }}</span>
              </button>
              <p v-if="!wizardFilteredColumns.length" class="empty-text">没有符合条件的字段</p>
            </div>
            <p class="wizard-scope-note">系统规则需要的字段会自动补充；未选为展示列的运行字段不会出现在表格中。</p>
          </section>
        </div>
        <ConditionTreeEditor v-else-if="inspectorTab === 'query'" v-model="conditionDraft" :columns="configurableAvailableColumns" :qm-model="config.model" :filter-member-loader="filterMemberLoader" />
        <div v-else class="wizard-review">
          <section class="wizard-save-card" aria-label="保存查询方案">
            <header><div><h3>保存查询方案</h3><p>给这次配置起个名字，保存后即可直接使用。</p></div><span class="wizard-personal-tag">个人方案</span></header>
            <el-form class="save-form" label-position="top" :model="form">
              <el-form-item label="方案名称" required><div data-testid="list-preset-title" class="preset-input-wrapper"><el-input v-model="form.title" maxlength="50" show-word-limit placeholder="例如：我的待揽收运单列表" aria-label="方案名称" /></div><small class="wizard-form-hint">建议使用容易识别的名称，方便下次查找。</small></el-form-item>
              <el-form-item label="方案说明（可选）"><div data-testid="list-preset-description" class="preset-input-wrapper"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="例如：每天早上查看待揽收运单" aria-label="方案说明" /></div></el-form-item>
              <div class="wizard-apply-option"><el-checkbox v-model="applyAfterSave" data-testid="list-preset-apply-after-save">保存后立即应用这个方案</el-checkbox></div>
            </el-form>
            <p class="wizard-policy-note">ⓘ 只保存你的字段、条件、排序和分页偏好。数据权限由后台控制，页面固定规则会在查询前自动加入。</p>
            <div class="wizard-save-ready">✓ 配置已检查，可以保存</div>
          </section>
        </div>
      </div>
      <template #footer>
        <div class="wizard-footer"><div v-if="dialogMode !== 'load'" class="wizard-summary"><span><b>{{ visibleColumnDraft.length }}</b> 展示字段</span><span><b>{{ savedConditionCount }}</b> 用户条件</span><span>分页大小 <b>{{ currentState.pageSize || 50 }}</b></span></div><div class="wizard-footer-actions">
          <el-button v-if="dialogMode === 'load' || inspectorTab === 'columns'" @click="visible = false">关闭</el-button>
          <el-button v-if="dialogMode !== 'load' && inspectorTab === 'query'" @click="goStep('columns')">返回字段选择</el-button>
          <el-button v-if="dialogMode !== 'load' && inspectorTab === 'save'" @click="goStep('query')">返回条件设置</el-button>
          <el-button v-if="dialogMode !== 'load' && inspectorTab === 'columns'" type="primary" @click="goStep('query')">下一步：设置查询条件</el-button>
          <el-button v-if="dialogMode !== 'load' && inspectorTab === 'query'" data-testid="list-preset-save-tab" type="primary" @click="goStep('save')">下一步：确认与保存</el-button>
          <el-button v-if="dialogMode !== 'load' && inspectorTab === 'save'" data-testid="list-preset-save" type="primary" :loading="saving" @click="saveCurrentPreset">{{ editingPresetId ? '更新方案' : '保存方案' }}</el-button>
        </div></div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowUp,
  Bottom,
  Brush,
  Delete,
  Edit,
  Finished,
  Loading,
  Lock,
  Operation,
  Rank,
  Refresh,
  Search,
  Top
} from '@element-plus/icons-vue'
import {
  createListPreset,
  deleteListPreset,
  listPresets,
  setDefaultListPreset,
  updateListPreset
} from '@/api/listPreset'
import ListPresetConditionEditor from './ListPresetConditionEditor.vue'
import ConditionTreeEditor from './ConditionTreeEditor.vue'
import {
  cloneSliceTree,
  countConditionLeaves,
  getDisplayColumnForCondition,
  getUserConfigurableColumns,
  validateListPresetLimits
} from '@/utils/listPreset'
import { isRelativeDateValue, relativeDateOptions } from '@/utils/customQuery'
import type {
  ColumnViewSetting,
  EnhancedColumnSchema,
  ListPresetConfig,
  ListPresetDef,
  ListPresetVisibility,
  ListViewState,
  SliceRequestDef,
  MemberQueryRequest,
  MemberQueryResponse
} from '@/types'

interface Props {
  config: ListPresetConfig
  getState: () => ListViewState
  applyState: (state: ListViewState, options?: { reload?: boolean; validateLimits?: boolean }) => void
  reload?: () => void | Promise<void>
  clearConditions?: () => void | Promise<void>
  availableColumns?: EnhancedColumnSchema[]
  lockedColumns?: string[]
  /** @deprecated Execution dependencies belong to the query layer; never filter presets. */
  requiredRuntimeColumns?: string[]
  filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
  triggerMode?: 'button' | 'none'
}

const props = defineProps<Props>()
const triggerMode = computed(() => props.triggerMode ?? 'button')

interface ColumnDraft {
  name: string
  title?: string
  type?: string
  groupKey?: string
  groupTitle?: string
  groupOrder?: number
  category?: string
  visible: boolean
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
}

interface ColumnDraftGroup {
  key: string
  title: string
  order: number
  columns: ColumnDraft[]
  selectedCount: number
}

interface ColumnGroupResolution {
  key: string
  title: string
  order: number
}

type InspectorTab = 'columns' | 'query' | 'save'
type DialogMode = 'customize' | 'load' | 'save'
type MoveEdge = 'top' | 'bottom'
type ColumnDropMode = 'insert-before' | 'insert-after' | 'swap'
type ColumnDragOrigin = 'selected' | 'available'

interface ColumnDragTarget {
  target: string
  mode: ColumnDropMode
}

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const clearing = ref(false)
const keyword = ref('')
const fieldKeyword = ref('')
const fieldTypeFilter = ref('')
const activeColumnEditor = ref<string | null>(null)
const inspectorTab = ref<InspectorTab>('columns')
const dialogMode = ref<DialogMode>('customize')
const presets = ref<ListPresetDef[]>([])
const columnDraft = ref<ColumnDraft[]>([])
const conditionDraft = ref<SliceRequestDef[]>([])
const conditionDraftInitialized = ref(false)
const editingPresetId = ref<string | null>(null)
const appliedPresetId = ref<string | null>(null)
const form = ref({
  title: '',
  description: '',
  visibility: 'PRIVATE' as ListPresetVisibility,
  isDefault: false,
  saveQueryConditions: true
})

const buttonText = computed(() => props.config.buttonText || '自定义查询')
const clearConditionsEnabled = computed(() => Boolean(props.clearConditions))
const editingBaseState = ref<ListViewState | null>(null)
const currentState = computed(() => editingBaseState.value || props.getState())
const lockedColumnNameSet = computed(() => new Set(props.lockedColumns || []))
const availableColumnMap = computed(() => new Map((props.availableColumns || []).map(column => [column.name, column])))
const configurableAvailableColumns = computed(() => {
  const sourceColumns = props.availableColumns && props.availableColumns.length > 0
    ? props.availableColumns
    : currentState.value.columns.map(name => ({ name, title: name, type: 'TEXT' }))
  return getUserConfigurableColumns(sourceColumns)
})

const draggedColumn = ref<string | null>(null)
const dragOrigin = ref<ColumnDragOrigin | null>(null)
const dragOver = ref<ColumnDragTarget | null>(null)
const appendDropTarget = '__append__'
const recentlyMovedColumns = ref<string[]>([])
let recentMoveTimer: ReturnType<typeof setTimeout> | null = null
const visibleColumnDraft = computed(() => columnDraft.value.filter(column => column.visible))
const appliedPreset = computed(() => presets.value.find(preset => preset.id === appliedPresetId.value))
const defaultPreset = computed(() => presets.value.find(preset => preset.isDefault))
const dialogTitle = computed(() => {
  if (dialogMode.value === 'load') return '查询管理'
  if (dialogMode.value === 'save') return '保存查询'
  return '自定义查询'
})
const inspectorTitle = computed(() => {
  if (inspectorTab.value === 'query') return '查询条件'
  if (inspectorTab.value === 'save') return editingPresetId.value ? '编辑方案' : '保存方案'
  return '已选字段'
})
const effectiveConditionDraft = computed(() => conditionDraftInitialized.value
  ? conditionDraft.value
  : currentState.value.slice)
const savedConditionCount = computed(() => form.value.saveQueryConditions ? countConditionLeaves(effectiveConditionDraft.value) : 0)
const conditionSummary = computed(() => {
  if (!form.value.saveQueryConditions) return []
  return effectiveConditionDraft.value.slice(0, 8).map(slice => {
    if (slice.$and || slice.$or || slice.children) return `${slice.$or ? 'OR' : 'AND'} 组（${countConditionLeaves([slice])} 条件）`
    const column = getDisplayColumnForCondition(slice.field, configurableAvailableColumns.value)
    const values = Array.isArray(slice.value) ? slice.value : [slice.value]
    let value = values.filter(item => item != null && item !== '').join(', ')
    if (isRelativeDateValue(slice.value)) {
      const range = slice.value.$relativeDate
      value = relativeDateOptions.find(option => option.value === range)?.label || '未知日期范围'
    } else if (column?.dictItems?.length) {
      value = values.map(item => column.dictItems!.find(option => option.value === item)?.label || '').filter(Boolean).join(', ')
    } else if (column?.memberLookup?.enabled) {
      value = values.filter(item => item != null && item !== '').length ? '已选择成员' : ''
    }
    return `${column?.title || column?.name || slice.field} ${slice.op} ${value}`.trim()
  })
})
const orderSummary = computed(() => {
  if (!form.value.saveQueryConditions) return []
  return currentState.value.orderBy.slice(0, 8).map(order => `${order.field} ${order.dir ?? order.order}`)
})
const filteredPresets = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return presets.value
  return presets.value.filter(preset =>
    preset.title.toLowerCase().includes(kw) ||
    preset.description?.toLowerCase().includes(kw)
  )
})
const columnTypeOptions = computed(() => {
  const types = new Set<string>()
  for (const column of columnDraft.value) {
    if (column.type) types.add(column.type)
  }
  return [...types].sort()
})
const filteredColumnDraft = computed(() => {
  const kw = fieldKeyword.value.trim().toLowerCase()
  return columnDraft.value.filter(column => {
    if (fieldTypeFilter.value && column.type !== fieldTypeFilter.value) return false
    if (!kw) return true
    return (column.title || '').toLowerCase().includes(kw) || column.name.toLowerCase().includes(kw)
  })
})
const filteredColumnGroups = computed<ColumnDraftGroup[]>(() => {
  const groupMap = new Map<string, ColumnDraftGroup>()

  for (const column of filteredColumnDraft.value) {
    const group = resolveColumnGroup(column)
    const existing = groupMap.get(group.key)
    if (existing) {
      existing.columns.push(column)
      if (column.visible) existing.selectedCount += 1
      continue
    }
    groupMap.set(group.key, {
      key: group.key,
      title: group.title,
      order: group.order,
      columns: [column],
      selectedCount: column.visible ? 1 : 0
    })
  }

  return [...groupMap.values()].sort((left, right) => {
    if (left.order !== right.order) return left.order - right.order
    return left.title.localeCompare(right.title, 'zh-Hans-CN')
  })
})

const CATEGORY_GROUPS: Record<string, { title: string, order: number }> = {
  'dimension-caption': { title: '维度', order: 20 },
  'dimension-property': { title: '维度属性', order: 30 },
  'dimension-id': { title: '维度ID', order: 40 },
  attribute: { title: '基础属性', order: 50 },
  measure: { title: '指标', order: 60 },
  calculated: { title: '计算字段', order: 70 }
}

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

function pickText(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return undefined
}

function pickNumber(...values: unknown[]): number | undefined {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value
    }
    if (typeof value === 'string' && value.trim()) {
      const numberValue = Number(value)
      if (Number.isFinite(numberValue)) {
        return numberValue
      }
    }
  }
  return undefined
}

function resolveSourceColumnGroup(column: EnhancedColumnSchema): Pick<ColumnDraft, 'groupKey' | 'groupTitle' | 'groupOrder' | 'category'> {
  const rawGroup = column.group
  const groupRecord = rawGroup && typeof rawGroup === 'object'
    ? rawGroup as unknown as Record<string, unknown>
    : null
  const groupText = typeof rawGroup === 'string' ? rawGroup : undefined
  const groupKey = pickText(
    column.groupKey,
    groupText,
    groupRecord?.key,
    groupRecord?.id,
    groupRecord?.code,
    groupRecord?.name
  )
  const groupTitle = pickText(
    column.groupTitle,
    groupText,
    groupRecord?.title,
    groupRecord?.label,
    groupRecord?.caption,
    groupRecord?.name,
    groupKey
  )
  return {
    groupKey,
    groupTitle,
    groupOrder: pickNumber(column.groupOrder, groupRecord?.order, groupRecord?.sort, groupRecord?.index),
    category: typeof column.category === 'string' ? column.category : undefined
  }
}

function resolveColumnGroup(column: ColumnDraft): ColumnGroupResolution {
  const category = column.category || ''
  const categoryGroup = CATEGORY_GROUPS[category]
  const explicitKey = pickText(column.groupKey, column.groupTitle)
  if (explicitKey) {
    const isCategoryGroup = Boolean(categoryGroup && explicitKey === category)
    return {
      key: isCategoryGroup ? `category:${category}` : `qm:${explicitKey}`,
      title: pickText(column.groupTitle, column.groupKey) || '未分组',
      order: column.groupOrder ?? (isCategoryGroup && categoryGroup ? categoryGroup.order : 10)
    }
  }

  if (categoryGroup) {
    return {
      key: `category:${category}`,
      title: categoryGroup.title,
      order: categoryGroup.order
    }
  }

  return {
    key: 'category:attribute',
    title: CATEGORY_GROUPS.attribute.title,
    order: CATEGORY_GROUPS.attribute.order
  }
}

function openDialog() {
  resetForm()
  dialogMode.value = 'customize'
  inspectorTab.value = 'columns'
  syncColumnDraftFromState()
  syncConditionDraftFromState()
  visible.value = true
}

function openLoadDialog() {
  resetForm()
  dialogMode.value = 'load'
  syncColumnDraftFromState()
  syncConditionDraftFromState()
  visible.value = true
}

function openSaveDialog() {
  dialogMode.value = 'save'
  resetForm()
  syncColumnDraftFromState()
  syncConditionDraftFromState()
  inspectorTab.value = 'save'
  visible.value = true
}

function syncColumnDraftFromState() {
  columnDraft.value = buildColumnDraft(currentState.value)
  activeColumnEditor.value = null
}

function syncConditionDraftFromState() {
  conditionDraft.value = cloneSliceTree(currentState.value.slice)
  conditionDraftInitialized.value = true
}

watch(() => currentState.value.slice, () => {
  // 清空、重置或外部应用查询状态后，打开的编辑器也必须反映当前表格条件。
  if (visible.value) {
    syncConditionDraftFromState()
  }
}, { deep: true })

function isColumnLocked(name: string): boolean {
  return lockedColumnNameSet.value.has(name)
}

function normalizeVisible(name: string, visibleValue: boolean): boolean {
  if (isColumnLocked(name)) return true
  return visibleValue
}

function buildColumnDraft(state: ListViewState): ColumnDraft[] {
  const hasAvailableColumns = Boolean(props.availableColumns && props.availableColumns.length > 0)
  const sourceColumns: EnhancedColumnSchema[] = hasAvailableColumns
    ? props.availableColumns
    : state.columns.map(name => ({ name, type: 'TEXT', title: name }))
  const settingMap = new Map((state.columnSettings || []).map(setting => [setting.name, setting]))
  const visibleNames = new Set(state.columns || [])
  const hasVisibleColumns = visibleNames.size > 0

  return sourceColumns
    .filter(column => hasAvailableColumns
      ? configurableAvailableColumns.value.some(available => available.name === column.name)
      : getUserConfigurableColumns([column]).length > 0)
    .map((column, sourceIndex) => {
      const setting = settingMap.get(column.name)
      const visibleValue = setting?.visible ?? (!hasVisibleColumns || visibleNames.has(column.name))
      const group = resolveSourceColumnGroup(column)
      return {
        name: column.name,
        title: column.title,
        type: column.type,
        groupKey: group.groupKey,
        groupTitle: group.groupTitle,
        groupOrder: group.groupOrder,
        category: group.category,
        visible: normalizeVisible(column.name, visibleValue),
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
      type: column.type,
      groupKey: column.groupKey,
      groupTitle: column.groupTitle,
      groupOrder: column.groupOrder,
      category: column.category,
      visible: column.visible,
      width: column.width,
      minWidth: column.minWidth,
      fixed: column.fixed
    }))
}

function normalizeDraftForSave(): ColumnDraft[] {
  return (columnDraft.value.length > 0 ? columnDraft.value : buildColumnDraft(currentState.value))
    .map(column => ({
      ...column,
      visible: normalizeVisible(column.name, column.visible)
    }))
}

function buildStateFromDraft(state: ListViewState): ListViewState {
  const draft = normalizeDraftForSave()
  const saveQueryConditions = form.value.saveQueryConditions
  const persistedColumns = draft.filter(column => column.visible)

  return {
    columns: persistedColumns.filter(column => column.visible).map(column => column.name),
    columnSettings: persistedColumns.map(toColumnViewSetting),
    slice: saveQueryConditions ? cloneSliceTree(effectiveConditionDraft.value) : [],
    orderBy: saveQueryConditions ? state.orderBy : [],
    pageSize: state.pageSize
  }
}

function toColumnViewSetting(column: ColumnDraft, index: number): ColumnViewSetting {
  const setting: ColumnViewSetting = {
    name: column.name,
    visible: column.visible,
    order: index
  }
  if (column.width !== undefined) setting.width = column.width
  if (column.minWidth !== undefined) setting.minWidth = column.minWidth
  if (column.fixed !== undefined) setting.fixed = column.fixed
  return setting
}

function getAvailableColumnNameSet(): Set<string> | null {
  if (!props.availableColumns || props.availableColumns.length === 0) return null
  return new Set(configurableAvailableColumns.value.map(column => column.name))
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

function toggleColumn(name: string, value: unknown) {
  const column = columnDraft.value.find(item => item.name === name)
  if (!column) return
  const nextVisible = Boolean(value)
  if (nextVisible && !column.visible && visibleColumnDraft.value.length >= 50) {
    ElMessage.warning('自定义查询最多配置 50 个字段')
    return
  }
  if (isColumnLocked(name) && !nextVisible) {
    column.visible = true
    ElMessage.warning('锁定列不可移除')
    return
  }
  column.visible = nextVisible
}

function removeColumn(name: string) {
  toggleColumn(name, false)
  if (activeColumnEditor.value === name) {
    activeColumnEditor.value = null
  }
}

function selectAllColumns() {
  if (columnDraft.value.length > 50) {
    ElMessage.warning('自定义查询最多配置 50 个字段，请分组或逐项选择')
    return
  }
  columnDraft.value = columnDraft.value.map(column => ({
    ...column,
    visible: true
  }))
}

function clearOptionalColumns() {
  columnDraft.value = columnDraft.value.map(column => ({
    ...column,
    visible: isColumnLocked(column.name)
  }))
}

function getGroupColumnNames(groupKey: string): Set<string> {
  const group = filteredColumnGroups.value.find(item => item.key === groupKey)
  return new Set((group?.columns || []).map(column => column.name))
}

function selectColumnGroup(groupKey: string) {
  const groupColumnNames = getGroupColumnNames(groupKey)
  if (columnDraft.value.filter(column => column.visible || groupColumnNames.has(column.name)).length > 50) {
    ElMessage.warning('选择本组将超过 50 个字段上限')
    return
  }
  columnDraft.value = columnDraft.value.map(column => (
    groupColumnNames.has(column.name)
      ? { ...column, visible: true }
      : column
  ))
}

function clearColumnGroup(groupKey: string) {
  const groupColumnNames = getGroupColumnNames(groupKey)
  columnDraft.value = columnDraft.value.map(column => (
    groupColumnNames.has(column.name)
      ? { ...column, visible: isColumnLocked(column.name) }
      : column
  ))
}

function openColumnEditor(name: string) {
  activeColumnEditor.value = name
}

function toggleColumnEditor(name: string) {
  activeColumnEditor.value = activeColumnEditor.value === name ? null : name
}

function closeColumnEditor() {
  activeColumnEditor.value = null
}

function getColumnSettingTags(column: ColumnDraft): string[] {
  const baseline = availableColumnMap.value.get(column.name)
  const tags: string[] = []
  const baselineFixed = baseline?.fixed

  if (column.fixed !== baselineFixed) {
    if (column.fixed === 'left') {
      tags.push('左固定')
    } else if (column.fixed === 'right') {
      tags.push('右固定')
    } else if (baselineFixed) {
      tags.push('不固定')
    }
  }

  if (column.width !== undefined && column.width !== baseline?.width) {
    tags.push(`宽 ${column.width}`)
  }

  return tags
}

function resetColumnSettings(name: string) {
  const column = columnDraft.value.find(item => item.name === name)
  if (!column) return
  const baseline = availableColumnMap.value.get(name)
  column.width = baseline?.width
  column.minWidth = baseline?.minWidth
  column.fixed = baseline?.fixed
}

function applyVisibleOrder(orderedVisibleColumns: ColumnDraft[]) {
  const visibleNames = new Set(orderedVisibleColumns.map(column => column.name))
  const hiddenColumns = columnDraft.value.filter(column => !visibleNames.has(column.name))
  columnDraft.value = [...orderedVisibleColumns, ...hiddenColumns]
}

function moveVisibleColumn(index: number, direction: -1 | 1) {
  const nextIndex = index + direction
  const visibleColumns = visibleColumnDraft.value.slice()
  if (nextIndex < 0 || nextIndex >= visibleColumns.length) return
  const current = visibleColumns[index]
  const target = visibleColumns[nextIndex]
  if (!current || !target) return
  visibleColumns[index] = target
  visibleColumns[nextIndex] = current
  applyVisibleOrder(visibleColumns)
  flashMovedColumns([current.name, target.name])
}

function moveVisibleColumnToEdge(index: number, edge: MoveEdge) {
  const visibleColumns = visibleColumnDraft.value.slice()
  const [current] = visibleColumns.splice(index, 1)
  if (!current) return
  if (edge === 'top') {
    visibleColumns.unshift(current)
  } else {
    visibleColumns.push(current)
  }
  applyVisibleOrder(visibleColumns)
  flashMovedColumns([current.name, visibleColumns[edge === 'top' ? 0 : visibleColumns.length - 1]?.name].filter(Boolean) as string[])
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
  editingBaseState.value = null
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
    ElMessage.error(getErrorMessage(error, '加载自定义查询失败'))
  } finally {
    loading.value = false
  }
}

async function applyPreset(preset: ListPresetDef) {
  try {
    validateListPresetLimits({ ...preset, slice: preset.query?.slice || [] })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '自定义查询超过允许的字段或条件上限'))
    return
  }
  const unavailableFields = getUnavailableFields(preset)
  const availableNames = getAvailableColumnNameSet()
  const presetColumnSettings = preset.columnSettings || []
  const state: ListViewState = {
    columns: availableNames
      ? preset.columns.filter(name => availableNames.has(name))
      : preset.columns,
    columnSettings: availableNames
      ? presetColumnSettings.filter(setting => availableNames.has(setting.name))
      : presetColumnSettings,
    slice: preset.query?.slice || [],
    orderBy: preset.query?.orderBy || [],
    pageSize: preset.pageSize
  }

  try {
    validateListPresetLimits(state)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '自定义查询超过允许的字段或条件上限'))
    return
  }

  appliedPresetId.value = preset.id
  props.applyState(state)
  await props.reload?.()
  visible.value = false
  if (unavailableFields.length > 0) {
    ElMessage.warning(`已忽略失效字段: ${unavailableFields.slice(0, 3).join('、')}${unavailableFields.length > 3 ? '...' : ''}`)
  }
  ElMessage.success(`已应用: ${preset.title}`)
}

function startEditPreset(preset: ListPresetDef) {
  editingBaseState.value = {
    columns: [...preset.columns], columnSettings: preset.columnSettings,
    slice: cloneSliceTree(preset.query?.slice || []),
    orderBy: preset.query?.orderBy || [], pageSize: preset.pageSize
  }
  dialogMode.value = 'customize'
  editingPresetId.value = preset.id
  // Editing should start from the same first step as creating a query. The
  // saved conditions and metadata remain loaded in the draft for later steps.
  inspectorTab.value = 'columns'
  syncColumnDraftFromState()
  syncConditionDraftFromState()
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

const applyAfterSave = ref(true)
const wizardSteps: { key: InspectorTab; title: string; subtitle: string }[] = [
  { key: 'columns', title: '选择展示字段', subtitle: '决定表格显示哪些列' },
  { key: 'query', title: '设置查询条件', subtitle: '最多 20 条用户条件' },
  { key: 'save', title: '确认并保存', subtitle: '保存个人查询方案' }
]
const wizardStepIndex = computed(() => wizardSteps.findIndex(step => step.key === inspectorTab.value))
const wizardFieldTypes = ['全部', '文本', '选项', '日期', '日期时间', '数值']
const wizardFieldType = ref('全部')
function wizardColumnType(column: ColumnDraft) {
  const meta = availableColumnMap.value.get(column.name)
  if (meta?.dictId || meta?.dictItems?.length || meta?.memberLookup?.enabled) return '选项'
  if (meta?.filterType === 'datetime' || column.type.toUpperCase() === 'DATETIME') return '日期时间'
  if (meta?.filterType === 'date' || ['DATE', 'DAY'].includes(column.type.toUpperCase())) return '日期'
  return ['NUMBER', 'INTEGER', 'LONG', 'DOUBLE', 'BIGDECIMAL', 'MONEY'].includes(column.type.toUpperCase()) ? '数值' : '文本'
}
const wizardFilteredColumns = computed(() => columnDraft.value.filter(column =>
  (wizardFieldType.value === '全部' || wizardColumnType(column) === wizardFieldType.value) &&
  `${column.title || ''} ${column.name}`.toLowerCase().includes(fieldKeyword.value.trim().toLowerCase())))
function selectFilteredColumns() {
  const additional = wizardFilteredColumns.value.filter(column => !column.visible)
  if (visibleColumnDraft.value.length + additional.length > 50) { ElMessage.warning('选择当前结果将超过 50 个展示字段上限'); return }
  additional.forEach(column => { column.visible = true })
}
function startColumnDrag(name: string, event: DragEvent) {
  draggedColumn.value = name
  dragOrigin.value = 'selected'
  dragOver.value = null
  event.dataTransfer?.setData('text/plain', name)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function startAvailableColumnDrag(name: string, event: DragEvent) {
  const column = columnDraft.value.find(item => item.name === name)
  if (!column || column.visible) return
  draggedColumn.value = name
  dragOrigin.value = 'available'
  dragOver.value = null
  event.dataTransfer?.setData('text/plain', name)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}

function getColumnTitle(name: string): string {
  if (name === appendDropTarget) return '列表末尾'
  return visibleColumnDraft.value.find(column => column.name === name)?.title || name
}

const dragFeedback = computed(() => {
  if (!draggedColumn.value) return ''
  const sourceTitle = getColumnTitle(draggedColumn.value)
  if (dragOrigin.value === 'available') {
    if (!dragOver.value) return `正在添加「${sourceTitle}」，拖到左侧字段之间即可插入，不会替换已有字段。`
    const targetTitle = getColumnTitle(dragOver.value.target)
    return `松开后将在「${targetTitle}」${dragOver.value.mode === 'insert-before' ? '之前' : '之后'}插入「${sourceTitle}」，不会替换。`
  }
  if (!dragOver.value) return `正在拖动「${sourceTitle}」，移到目标字段上方/下方可插入，中部可交换。`
  const targetTitle = getColumnTitle(dragOver.value.target)
  if (dragOver.value.mode === 'swap') return `松开后「${sourceTitle}」将与「${targetTitle}」交换位置。`
  return `松开后将在「${targetTitle}」${dragOver.value.mode === 'insert-before' ? '之前' : '之后'}插入「${sourceTitle}」。`
})

function getColumnDropMode(target: string, event: DragEvent): ColumnDropMode | null {
  if (!draggedColumn.value || draggedColumn.value === target) return null
  const element = event.currentTarget as HTMLElement | null
  const rect = element?.getBoundingClientRect()
  if (!rect || rect.height <= 0) return 'swap'
  const ratio = (event.clientY - rect.top) / rect.height
  if (dragOrigin.value === 'available') return ratio < 0.5 ? 'insert-before' : 'insert-after'
  if (ratio < 0.28) return 'insert-before'
  if (ratio > 0.72) return 'insert-after'
  return 'swap'
}

function onColumnDragOver(target: string, event: DragEvent) {
  const mode = getColumnDropMode(target, event)
  dragOver.value = mode ? { target, mode } : null
}

function onSelectedListDragOver(event: DragEvent) {
  if (dragOrigin.value !== 'available' || !draggedColumn.value) return
  const target = event.target as HTMLElement | null
  if (target?.closest('.selected-row')) return
  dragOver.value = { target: appendDropTarget, mode: 'insert-after' }
}

function onSelectedListDragLeave(event: DragEvent) {
  const element = event.currentTarget as HTMLElement | null
  const related = event.relatedTarget as Node | null
  if (element && related && element.contains(related)) return
  if (dragOver.value?.target === appendDropTarget) dragOver.value = null
}

function onColumnDragLeave(target: string, event: DragEvent) {
  const element = event.currentTarget as HTMLElement | null
  const related = event.relatedTarget as Node | null
  if (element && related && element.contains(related)) return
  if (dragOver.value?.target === target) dragOver.value = null
}

function clearColumnDrag() {
  draggedColumn.value = null
  dragOrigin.value = null
  dragOver.value = null
}

function flashMovedColumns(names: string[]) {
  if (recentMoveTimer) clearTimeout(recentMoveTimer)
  recentlyMovedColumns.value = [...new Set(names)]
  recentMoveTimer = setTimeout(() => {
    recentlyMovedColumns.value = []
    recentMoveTimer = null
  }, 1800)
}

function dropColumn(target: string, event?: DragEvent) {
  const ordered = visibleColumnDraft.value.slice()
  const sourceName = draggedColumn.value
  const mode = dragOver.value?.target === target
    ? dragOver.value.mode
    : event ? getColumnDropMode(target, event) : null
  const from = ordered.findIndex(column => column.name === sourceName)
  const to = ordered.findIndex(column => column.name === target)

  if (dragOrigin.value === 'available') {
    const source = columnDraft.value.find(column => column.name === sourceName)
    if (!source || source.visible || !sourceName || !mode) {
      clearColumnDrag()
      return
    }
    let insertAt = to < 0 ? ordered.length : to
    if (mode === 'insert-after') insertAt += 1
    ordered.splice(insertAt, 0, { ...source, visible: true })
    applyVisibleOrder(ordered)
    flashMovedColumns([sourceName, target])
    clearColumnDrag()
    return
  }

  if (!sourceName || !mode || from < 0 || to < 0 || from === to) {
    clearColumnDrag()
    return
  }

  const [source] = ordered.splice(from, 1)
  if (!source) {
    clearColumnDrag()
    return
  }

  if (mode === 'swap') {
    const targetIndex = ordered.findIndex(column => column.name === target)
    const targetColumn = ordered[targetIndex]
    if (!targetColumn) {
      clearColumnDrag()
      return
    }
    ordered[targetIndex] = source
    ordered.splice(Math.min(from, ordered.length), 0, targetColumn)
  } else {
    let insertAt = ordered.findIndex(column => column.name === target)
    if (insertAt < 0) insertAt = ordered.length
    if (mode === 'insert-after') insertAt += 1
    ordered.splice(insertAt, 0, source)
  }

  applyVisibleOrder(ordered)
  flashMovedColumns([sourceName, target])
  clearColumnDrag()
}

function dropSelectedList(event: DragEvent) {
  if (dragOrigin.value !== 'available' || !draggedColumn.value) {
    clearColumnDrag()
    return
  }
  const target = dragOver.value?.target || appendDropTarget
  if (target === appendDropTarget) {
    const source = columnDraft.value.find(column => column.name === draggedColumn.value)
    if (!source || source.visible) {
      clearColumnDrag()
      return
    }
    const ordered = [...visibleColumnDraft.value, { ...source, visible: true }]
    applyVisibleOrder(ordered)
    flashMovedColumns([source.name])
    clearColumnDrag()
    return
  }
  dropColumn(target, event)
}

onUnmounted(() => {
  if (recentMoveTimer) clearTimeout(recentMoveTimer)
})
function validateWizardConditions(slices: SliceRequestDef[]) {
  for (const condition of slices) {
    const groups = condition.$and || condition.$or || condition.children
    if (groups) {
      if (!groups.length) throw new Error('空条件组请添加条件或删除后再保存')
      validateWizardConditions(groups)
    }
  }
}
function goStep(step: InspectorTab) {
  if (step !== 'columns') {
    const state = buildStateFromDraft(currentState.value)
    if (!ensureHasVisibleColumns(state)) return
    try {
      validateListPresetLimits(state)
      if (step === 'save') validateWizardConditions(state.slice)
    } catch (error) { ElMessage.warning(getErrorMessage(error, '请检查查询配置')); return }
  }
  inspectorTab.value = step
}

async function saveCurrentPreset() {
  const title = form.value.title.trim()
  if (!title) {
    inspectorTab.value = 'save'
    ElMessage.warning('请输入查询名称')
    return
  }

  const state = buildStateFromDraft(currentState.value)
  if (!ensureHasVisibleColumns(state)) {
    inspectorTab.value = 'columns'
    return
  }
  try {
    validateListPresetLimits(state)
    validateWizardConditions(state.slice)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '自定义查询超过允许的字段或条件上限'))
    return
  }
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
    if (applyAfterSave.value) {
      try {
        await applyPreset(saved)
      } catch (error) {
        ElMessage.error(getErrorMessage(error, '方案已保存，但应用失败，请重新应用'))
      }
    } else {
      ElMessage.success(wasEditing ? '自定义查询已更新' : '自定义查询已保存')
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, editingPresetId.value ? '更新自定义查询失败' : '保存自定义查询失败'))
  } finally {
    saving.value = false
  }
}

async function overwritePreset(preset: ListPresetDef) {
  const state = buildStateFromDraft(currentState.value)
  if (!ensureHasVisibleColumns(state)) return
  try {
    validateListPresetLimits(state)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '自定义查询超过允许的字段或条件上限'))
    return
  }
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
    ElMessage.success('已覆盖自定义查询')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '覆盖自定义查询失败'))
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
    ElMessage.success('默认查询已更新')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '设置默认查询失败'))
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
    ElMessage.success('自定义查询已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '删除自定义查询失败'))
    }
  }
}

async function clearCurrentConditions() {
  if (!props.clearConditions) return
  clearing.value = true
  try {
    await props.clearConditions()
    syncConditionDraftFromState()
    ElMessage.success('已清空查询条件')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '清空查询条件失败'))
  } finally {
    clearing.value = false
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
  clearCurrentConditions,
  loadPresets,
  moveColumn,
  moveVisibleColumn,
  moveVisibleColumnToEdge,
  openDialog,
  openLoadDialog,
  openSaveDialog,
  startColumnDrag,
  startAvailableColumnDrag,
  onColumnDragOver,
  dropColumn,
  dropSelectedList,
  clearColumnDrag,
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

<style>
.query-wizard-dialog.el-dialog { padding: 0; overflow: hidden; max-height: calc(100vh - 48px); display: flex; flex-direction: column; border-radius: 14px; --el-color-primary: #1867d5; font-family: 'HarmonyOS Sans SC', 'Microsoft YaHei UI', 'PingFang SC', sans-serif; }
.query-wizard-dialog .el-dialog__header { margin: 0; padding: 22px 30px; border-bottom: 1px solid #e3e8f0; }
.query-wizard-dialog .el-dialog__headerbtn { top: 12px; right: 12px; }
.query-wizard-dialog .el-dialog__body { padding: 0; overflow: auto; min-height: 0; }
.query-wizard-dialog .el-dialog__footer { padding: 0; flex-shrink: 0; }
</style>
<style scoped>
.query-wizard { color: #172033; }
.wizard-title { display: flex; align-items: center; gap: 16px; }.wizard-title h2 { margin: 0; font-size: 22px; }.wizard-title p { margin: 6px 0 0; font-size: 12px; color: #6b7689; }
.wizard-draft, .wizard-personal-tag { color: #1867d5; background: #eaf3ff; border: 1px solid #c9ddfb; padding: 4px 9px; border-radius: 20px; font-size: 11px; }
.wizard-progress { display: flex; align-items: center; height: 86px; padding: 0 42px; border-bottom: 1px solid #e3e8f0; background: linear-gradient(180deg, #fff, #fbfcfe); }
.wizard-step { display: flex; align-items: center; gap: 10px; border: 0; background: transparent; color: #8290a2; padding: 0; text-align: left; cursor: pointer; }.wizard-step + .wizard-step { flex: 1; }.wizard-step + .wizard-step::before { content: ''; height: 1px; flex: 1; margin: 0 20px; background: #d9e2ee; }.wizard-step b { font-size: 13px; }.wizard-step small { display: block; margin-top: 4px; font-size: 11px; color: #909bac; }
.wizard-step-dot { display: grid; place-items: center; flex: 0 0 30px; height: 30px; border-radius: 50%; border: 1px solid #d6dee9; background: white; font-size: 12px; font-weight: 700; }.wizard-step.active { color: #0c4fae; }.wizard-step.active .wizard-step-dot { background: #1867d5; color: white; border-color: #1867d5; box-shadow: 0 0 0 5px #eaf3ff; }.wizard-step.done .wizard-step-dot { color: #237a54; background: #edf8f2; border-color: #9dddbc; }
.wizard-fields { display: grid; grid-template-columns: 330px minmax(0, 1fr); min-height: 540px; }.wizard-selected { min-width: 0; padding: 22px 16px; background: #f8fafc; border-right: 1px solid #e3e8f0; }.wizard-panel-head { display: flex; justify-content: space-between; padding: 0 7px; }.wizard-panel-head h3 { margin: 0; font-size: 15px; }.wizard-panel-head p { margin: 6px 0 0; color: #8490a2; font-size: 12px; }.wizard-count { font-size: 20px; color: #0c4fae; font-weight: 700; }.wizard-count small { display: block; text-align: right; font-size: 11px; font-weight: 400; color: #8490a2; }
.wizard-note { background: #f1f7ff; border: 1px solid #dce8f6; border-radius: 8px; padding: 10px; margin: 15px 0 12px; font-size: 11px; color: #5c7494; line-height: 1.6; }
.wizard-selected .selected-list { display: grid; gap: 7px; max-height: 395px; overflow: auto; background: white; border: 1px solid #e0e7f0; border-radius: 10px; padding: 8px; transition: border-color .16s ease, background-color .16s ease, box-shadow .16s ease; }.wizard-selected .selected-list.is-drop-ready { border-color: #80afea; background: #f7fbff; box-shadow: inset 0 0 0 2px #dceafd; }.wizard-selected .selected-list.drop-append { border-color: #35a66a; background: #f1fbf5; box-shadow: inset 0 0 0 2px #c8efd8; }
.wizard-selected .selected-row { position: relative; display: grid; grid-template-columns: 14px 18px minmax(0, 1fr) auto; align-items: center; gap: 6px; min-height: 48px; padding: 7px; border: 1px solid #e0e7f0; border-radius: 7px; margin: 0; transition: border-color .16s ease, background-color .16s ease, opacity .16s ease, box-shadow .16s ease, transform .16s ease; }.wizard-selected .selected-row:hover { border-color: #9ec3f7; }.wizard-selected .selected-row.is-dragging { opacity: .42; transform: scale(.985); }.wizard-selected .selected-row.drop-swap { border-color: #e1a93b; background: #fff8e8; box-shadow: 0 0 0 3px #ffedbd; }.wizard-selected .selected-row.drop-before::before, .wizard-selected .selected-row.drop-after::after { content: ''; position: absolute; z-index: 2; left: 6px; right: 6px; height: 3px; border-radius: 999px; background: #1867d5; box-shadow: 0 0 0 3px #dceafd, 0 2px 8px #1867d566; pointer-events: none; }.wizard-selected .selected-row.drop-before::before { top: -6px; }.wizard-selected .selected-row.drop-after::after { bottom: -6px; }.wizard-selected .selected-row.is-recently-moved { animation: column-drop-confirm 1.8s ease-out; }.wizard-selected .selected-main b { font-size: 12px; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }.wizard-selected .selected-main small { display: block; color: #8a96a7; margin-top: 4px; font-size: 10px; }.wizard-field-rank { font-size: 10px; color: #92a0b1; }.wizard-selected .selected-actions { display: flex; gap: 2px; }.wizard-selected .selected-actions .el-button { margin: 0; padding: 2px; }.wizard-selected .selected-editor { grid-column: 1 / -1; display: grid; grid-template-columns: 100px 100px; gap: 8px; padding: 10px 0 0; }.wizard-selected .editor-actions { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 4px; }.wizard-selected .editor-actions .el-button { margin: 0; }.wizard-selected .el-input-number { width: 100px; }
.drag-feedback { height: 58px; box-sizing: border-box; display: flex; align-items: center; overflow: hidden; transition: color .16s ease, border-color .16s ease, background-color .16s ease; }.drag-feedback-text { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.drag-feedback.is-active { color: #1857ae; border-color: #9ec3f7; background: #edf5ff; }
@keyframes column-drop-confirm { 0% { border-color: #35a66a; background: #eaf8f0; box-shadow: 0 0 0 3px #c8efd8; } 70% { border-color: #8fd3b1; background: #f4fcf7; box-shadow: 0 0 0 2px #e1f6e9; } 100% { border-color: #e0e7f0; background: #fff; box-shadow: none; } }
.wizard-field-workspace { min-width: 0; padding: 26px 30px; }.wizard-workspace-head { display: flex; justify-content: space-between; gap: 16px; }.wizard-eyebrow { color: #1867d5; font-size: 11px; font-weight: 700; letter-spacing: .07em; }.wizard-workspace-head h2 { font-size: 21px; margin: 6px 0; }.wizard-workspace-head p { font-size: 13px; color: #6b7689; margin: 0; }.wizard-field-count { padding: 10px 12px; border: 1px solid #dbe6f7; border-radius: 9px; color: #0c4fae; background: #f4f8ff; text-align: right; }.wizard-field-count b { display: block; font-size: 18px; }.wizard-field-count small { font-size: 11px; }
.wizard-scope-note { padding: 11px 13px; border: 1px solid #e2e8f1; border-radius: 8px; background: #f7f9fc; color: #69768a; font-size: 12px; line-height: 1.6; margin: 18px 0 14px; }.wizard-field-tools { display: flex; gap: 10px; }.wizard-field-tools > .el-input { flex: 1; min-width: 150px; }.wizard-type-filters { display: flex; gap: 5px; }.wizard-type-filters button { padding: 7px 10px; border: 1px solid #d9e1eb; border-radius: 6px; color: #6e7c91; background: white; font-size: 12px; cursor: pointer; }.wizard-type-filters button.active { color: #0c4fae; background: #eaf3ff; border-color: #91b9eb; }
.wizard-field-section-head { display: flex; justify-content: space-between; align-items: center; margin: 18px 0 10px; font-size: 13px; }.wizard-field-section-head small { color: #8290a3; font-weight: 400; margin-left: 6px; }
.wizard-field-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; max-height: 305px; overflow: auto; padding: 2px; }.wizard-field-grid .wizard-field-card { display: flex; align-items: center; gap: 9px; min-height: 67px; padding: 10px 12px; border: 1px solid #dfe6ef; border-radius: 8px; color: #172033; background: white; text-align: left; cursor: pointer; }.wizard-field-grid .wizard-field-card.is-draggable-source { cursor: grab; }.wizard-field-grid .wizard-field-card.is-draggable-source:active { cursor: grabbing; }.wizard-field-card b { font-size: 12px; }.wizard-field-card small { display: block; margin-top: 5px; color: #8b98a9; font-size: 10px; }.wizard-field-grid .wizard-field-card:hover { border-color: #80afea; }.wizard-field-grid .wizard-field-card.is-selected { border-color: #8fd3b1; background: #f1fbf5; }.wizard-check { display: grid; place-items: center; flex: 0 0 18px; height: 18px; border: 1px solid #cbd6e3; border-radius: 4px; background: white; }.is-selected .wizard-check { color: white; background: #35a66a; border-color: #35a66a; }.wizard-type-pill { margin-left: auto; padding: 3px 5px; border-radius: 4px; background: #f0f3f7; color: #58728e; font-size: 10px; white-space: nowrap; }
.wizard-review { display: grid; place-items: start center; min-height: 540px; padding: 44px 30px; background: #f8fafc; }.wizard-save-card { width: min(560px, 100%); padding: 25px 28px; border: 1px solid #cfe0f5; border-radius: 11px; background: white; box-shadow: 0 8px 22px #284f8314; }.wizard-save-card header { display: flex; justify-content: space-between; align-items: start; gap: 12px; padding-bottom: 17px; border-bottom: 1px solid #eaf0f7; }.wizard-save-card h3 { margin: 0; font-size: 16px; }.wizard-save-card header p { margin: 6px 0 0; color: #7c889a; font-size: 11px; }.wizard-personal-tag { white-space: nowrap; border-radius: 5px; }.wizard-save-card .save-form { margin-top: 18px; }.wizard-form-hint { color: #9aa5b3; font-size: 10px; }.wizard-apply-option { padding: 7px 11px; border: 1px solid #e4ebf4; border-radius: 7px; background: #f8fbff; }.wizard-policy-note { padding-top: 14px; border-top: 1px solid #edf1f5; color: #7c8999; font-size: 11px; line-height: 1.7; }.wizard-save-ready { padding: 10px; border-radius: 7px; color: #237a54; background: #edf8f2; font-size: 11px; }
.wizard-footer { display: flex; justify-content: space-between; align-items: center; min-height: 74px; padding: 16px 28px; border-top: 1px solid #e3e8f0; gap: 15px; }.wizard-summary { display: flex; gap: 20px; color: #718096; font-size: 12px; }.wizard-summary b { color: #172033; font-size: 17px; }.wizard-footer-actions { display: flex; margin-left: auto; gap: 10px; }.wizard-footer-actions .el-button { margin: 0; }.wizard-load { padding: 12px 24px 18px; }.wizard-load .preset-list-section { border: 0; }.wizard-load .preset-scroll { height: auto; max-height: min(500px, calc(100vh - 280px)); }
@media (max-width: 1180px) { .wizard-field-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.wizard-field-tools { flex-wrap: wrap; }.wizard-fields { grid-template-columns: 300px minmax(0, 1fr); }.wizard-field-workspace { padding: 22px; } }
@media (max-height: 850px) { .wizard-fields, .wizard-review { min-height: 470px; }.wizard-review { padding: 22px; }.wizard-selected .selected-list { max-height: 310px; }.wizard-field-grid { max-height: 235px; } }
</style>
<style scoped>
.list-preset-manager {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.preset-layout {
  display: grid;
  grid-template-columns: 250px minmax(420px, 1fr) minmax(420px, 0.5fr);
  height: min(640px, calc(100vh - 190px));
  min-height: min(520px, calc(100vh - 190px));
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}

.preset-layout.is-load-mode {
  grid-template-columns: minmax(420px, 1fr);
}

.preset-list-section,
.field-pool-section,
.inspector-section {
  display: grid;
  min-width: 0;
  min-height: 0;
}

.preset-list-section {
  grid-template-rows: auto auto auto minmax(0, 1fr);
}

.field-pool-section {
  grid-template-rows: auto auto auto minmax(0, 1fr);
}

.preset-scroll,
.field-scroll,
.inspector-body {
  min-height: 0;
}

.preset-list-section,
.inspector-section {
  background: #fafafa;
}

.preset-list-section,
.field-pool-section {
  border-right: 1px solid #e4e7ed;
}

.preset-layout.is-load-mode .preset-list-section {
  border-right: 0;
  background: #fff;
}

.preset-layout.is-load-mode .preset-title {
  max-width: none;
}

.section-header,
.inspector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 58px;
  padding: 12px 14px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.section-header h4,
.inspector-title h4 {
  margin: 0;
  font-size: 14px;
  color: #303133;
}

.section-header span,
.inspector-title span {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: #909399;
}

.preset-search {
  width: calc(100% - 28px);
  margin: 12px 14px 0;
}

.preset-status-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 14px 0;
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  margin: 10px 10px 0;
  padding: 14px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  align-items: center;
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
  max-width: 130px;
  overflow: hidden;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset-description {
  margin: 7px 0;
  font-size: 12px;
  line-height: 1.45;
  color: #909399;
}

.preset-meta {
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: #606266;
}

.preset-time {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.preset-actions {
  flex-shrink: 0;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.preset-actions .el-button {
  margin: 0;
}

@media (max-width: 900px) {
  .preset-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }

  .preset-actions {
    justify-content: flex-start;
  }
}

.field-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px;
  gap: 8px;
  padding: 12px 14px 8px;
}

.field-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px 12px;
  border-bottom: 1px solid #ebeef5;
}

.field-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  padding: 12px 14px 16px;
}

.field-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(230px, 1fr));
  gap: 8px;
}

.field-group-header {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 34px;
  padding: 0 2px;
  border-bottom: 1px solid #ebeef5;
}

.field-group-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.field-group-title span {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.field-group-title small {
  font-size: 12px;
  color: #909399;
}

.field-group-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8px;
}

.field-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 54px;
  padding: 8px 9px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}

.field-row:hover {
  border-color: #c0c4cc;
}

.field-row.is-selected {
  border-color: #95d5b2;
  background: #f0f9f4;
}

.field-row.is-locked {
  border-color: #e6d3a5;
  background: #fffaf0;
}

.field-row.is-runtime {
  color: #909399;
  background: #f7f8fa;
}

.field-main,
.selected-main {
  min-width: 0;
}

.field-title,
.selected-name {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-weight: 600;
  color: #303133;
}

.field-title > span:first-child,
.selected-name > span:first-child,
.field-code,
.selected-code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-code,
.selected-code {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.field-selected-icon {
  color: #2f8d5b;
}

.inspector-section {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}

.inspector-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: stretch;
}

.inspector-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.inspector-body {
  min-height: 0;
}

.selected-list,
.query-panel,
.save-form {
  padding: 12px;
}

.selected-list {
  display: grid;
  gap: 8px;
}

.selected-row {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  min-height: 80px;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}

.selected-row:hover,
.selected-row:focus-within,
.selected-row.is-editing {
  border-color: #c0c4cc;
}

.selected-row:focus-visible {
  outline: 2px solid #a0cfff;
  outline-offset: 2px;
}

.drag-icon {
  margin-top: 8px;
  color: #c0c4cc;
}

.selected-main {
  padding-top: 2px;
}

.selected-action-rail {
  display: grid;
  grid-template-columns: 1fr;
  justify-items: end;
  align-self: start;
  gap: 2px;
  min-width: 104px;
  min-height: 32px;
  padding-top: 1px;
}

.selected-actions {
  display: flex;
  align-items: center;
  gap: 1px;
}

.selected-actions :deep(.el-button) {
  width: 24px;
  height: 24px;
  padding: 0;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

.selected-actions :deep(.el-button:hover),
.selected-actions :deep(.el-button:focus) {
  border-color: transparent;
  background: #f5f7fa;
}

.selected-move-actions {
  opacity: 0.72;
  transition: opacity 0.16s ease;
}

.selected-row-actions {
  height: 24px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.16s ease;
}

.selected-row:hover .selected-row-actions,
.selected-row:focus-within .selected-row-actions,
.selected-row.is-editing .selected-row-actions {
  opacity: 1;
  pointer-events: auto;
}

.selected-row:hover .selected-move-actions,
.selected-row:focus-within .selected-move-actions,
.selected-row.is-editing .selected-move-actions {
  opacity: 1;
}

.selected-editor {
  grid-column: 2 / 4;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 120px) auto;
  align-items: end;
  gap: 8px;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 5px;
  background: #fafafa;
  cursor: default;
}

.selected-editor :deep(.el-input-number) {
  width: 100%;
}

.editor-field {
  display: grid;
  gap: 4px;
  min-width: 0;
  font-size: 12px;
  color: #606266;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  align-items: center;
  min-height: 28px;
}

.query-panel {
  display: grid;
  gap: 12px;
}

.query-summary-block {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  padding: 10px;
}

.summary-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
}

.query-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.query-summary-item {
  max-width: 250px;
  overflow: hidden;
  border-radius: 4px;
  background: #f5f7fa;
  padding: 2px 6px;
  color: #606266;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-text {
  color: #909399;
  font-size: 12px;
}

.preset-input-wrapper {
  width: 100%;
}

.state-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}

.state-summary > div {
  min-width: 0;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px;
  text-align: center;
}

.state-summary strong {
  display: block;
  font-size: 17px;
  color: #303133;
}

.state-summary span {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #606266;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.danger-item {
  color: #f56c6c;
}

@media (max-width: 1100px) {
  .preset-layout {
    grid-template-columns: 220px minmax(0, 1fr) minmax(360px, 0.42fr);
  }

  .field-list {
    grid-template-columns: 1fr;
  }

  .field-group {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .preset-layout {
    grid-template-columns: 1fr;
    height: calc(100vh - 160px);
    overflow: auto;
  }

  .preset-list-section,
  .field-pool-section {
    border-right: 0;
    border-bottom: 1px solid #e4e7ed;
  }

  .selected-row {
    grid-template-columns: 18px minmax(0, 1fr);
  }

  .selected-action-rail,
  .selected-editor {
    grid-column: 2 / 3;
  }

  .selected-action-rail {
    justify-content: start;
  }

  .selected-row-actions {
    opacity: 1;
    pointer-events: auto;
  }

  .selected-editor {
    grid-template-columns: 1fr;
  }
}
</style>
