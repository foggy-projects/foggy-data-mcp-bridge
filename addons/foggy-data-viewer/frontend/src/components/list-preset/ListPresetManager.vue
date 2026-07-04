<template>
  <div class="list-preset-manager">
    <el-button data-testid="list-preset-open" size="small" :icon="Operation" @click="openDialog">
      {{ buttonText }}
    </el-button>
    <el-tooltip v-if="clearConditionsEnabled" content="清空查询条件" placement="top">
      <el-button
        data-testid="list-preset-clear-conditions"
        size="small"
        :icon="Brush"
        circle
        title="清空查询条件"
        aria-label="清空查询条件"
        :loading="clearing"
        @click="clearCurrentConditions"
      />
    </el-tooltip>

    <el-dialog
      v-model="visible"
      class="list-preset-dialog"
      title="自定义查询"
      width="min(1180px, calc(100vw - 32px))"
      top="4vh"
      :close-on-click-modal="false"
      @open="loadPresets"
    >
      <div class="preset-layout" data-testid="list-preset-dialog">
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
                <el-dropdown trigger="click">
                  <el-button data-testid="list-preset-more" link :icon="MoreFilled" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="startEditPreset(preset)">
                        编辑名称与范围
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

        <section class="field-pool-section">
          <div class="section-header">
            <div>
              <h4>字段池</h4>
              <span>{{ filteredColumnDraft.length }} / {{ columnDraft.length }} 个字段</span>
            </div>
            <el-button link type="primary" @click="syncColumnDraftFromState">恢复当前</el-button>
          </div>

          <div class="field-tools">
            <el-input
              v-model="fieldKeyword"
              placeholder="按字段名或编码搜索"
              :prefix-icon="Search"
              clearable
            />
            <el-select v-model="fieldTypeFilter" placeholder="类型" clearable>
              <el-option
                v-for="type in columnTypeOptions"
                :key="type"
                :label="type"
                :value="type"
              />
            </el-select>
          </div>

          <div class="field-actions">
            <el-button size="small" @click="selectAllColumns">全选</el-button>
            <el-button size="small" @click="clearOptionalColumns">取消可选</el-button>
          </div>

          <el-scrollbar class="field-scroll">
            <div class="field-list">
              <section
                v-for="group in filteredColumnGroups"
                :key="group.key"
                class="field-group"
              >
                <div class="field-group-header">
                  <div class="field-group-title">
                    <span>{{ group.title }}</span>
                    <small>{{ group.selectedCount }} / {{ group.columns.length }} 已选</small>
                  </div>
                  <div class="field-group-actions">
                    <el-button size="small" link type="primary" @click="selectColumnGroup(group.key)">选择本组</el-button>
                    <el-button size="small" link @click="clearColumnGroup(group.key)">取消本组</el-button>
                  </div>
                </div>
                <article
                  v-for="column in group.columns"
                  :key="column.name"
                  class="field-row"
                  :class="{
                    'is-selected': column.visible,
                    'is-locked': isColumnLocked(column.name),
                    'is-runtime': isRuntimeColumn(column.name)
                  }"
                  @click="toggleColumn(column.name, !column.visible)"
                >
                  <el-checkbox
                    :model-value="column.visible"
                    :disabled="isRuntimeColumn(column.name) || isColumnLocked(column.name)"
                    @click.stop
                    @change="value => toggleColumn(column.name, value)"
                  />
                  <div class="field-main">
                    <div class="field-title">
                      <span>{{ column.title || column.name }}</span>
                      <el-tag v-if="isColumnLocked(column.name)" size="small" type="warning">锁定</el-tag>
                      <el-tag v-if="isRuntimeColumn(column.name)" size="small">运行时</el-tag>
                      <el-tag size="small" effect="plain">{{ column.type }}</el-tag>
                    </div>
                    <div class="field-code">{{ column.name }}</div>
                  </div>
                  <el-icon v-if="column.visible" class="field-selected-icon"><Finished /></el-icon>
                </article>
              </section>
            </div>
          </el-scrollbar>
        </section>

        <section class="inspector-section">
          <div class="inspector-header">
            <div class="inspector-title">
              <h4>{{ inspectorTitle }}</h4>
              <span v-if="inspectorTab === 'columns'">{{ visibleColumnDraft.length }} 已选</span>
              <span v-else-if="inspectorTab === 'query'">{{ savedConditionCount }} 条件</span>
            </div>
            <el-radio-group v-model="inspectorTab" size="small">
              <el-radio-button label="columns">字段</el-radio-button>
              <el-radio-button label="query">条件</el-radio-button>
              <el-radio-button data-testid="list-preset-save-tab" label="save">保存</el-radio-button>
            </el-radio-group>
          </div>

          <el-scrollbar class="inspector-body">
            <div v-if="inspectorTab === 'columns'" class="selected-list">
              <el-empty v-if="visibleColumnDraft.length === 0" description="暂无选中字段" />
              <article
                v-for="(column, index) in visibleColumnDraft"
                v-else
                :key="column.name"
                class="selected-row"
                :class="{ 'is-editing': activeColumnEditor === column.name }"
                tabindex="0"
                @click="openColumnEditor(column.name)"
                @keydown.enter="openColumnEditor(column.name)"
                @keydown.space.prevent="openColumnEditor(column.name)"
              >
                <el-icon class="drag-icon"><Rank /></el-icon>
                <div class="selected-main">
                  <div class="selected-name">
                    <span>{{ column.title || column.name }}</span>
                    <el-tag v-if="isColumnLocked(column.name)" size="small" type="warning">锁定</el-tag>
                    <el-tag
                      v-for="tag in getColumnSettingTags(column)"
                      :key="tag"
                      size="small"
                      effect="plain"
                    >
                      {{ tag }}
                    </el-tag>
                  </div>
                  <div class="selected-code">{{ column.name }}</div>
                </div>
                <div class="selected-actions selected-move-actions" @click.stop>
                  <el-tooltip content="移到顶部" placement="top">
                    <el-button
                      :icon="Top"
                      size="small"
                      title="移到顶部"
                      aria-label="移到顶部"
                      :disabled="index === 0"
                      @click="moveVisibleColumnToEdge(index, 'top')"
                    />
                  </el-tooltip>
                  <el-tooltip content="上移" placement="top">
                    <el-button
                      :icon="ArrowUp"
                      size="small"
                      title="上移"
                      aria-label="上移"
                      :disabled="index === 0"
                      @click="moveVisibleColumn(index, -1)"
                    />
                  </el-tooltip>
                  <el-tooltip content="下移" placement="top">
                    <el-button
                      :icon="ArrowDown"
                      size="small"
                      title="下移"
                      aria-label="下移"
                      :disabled="index === visibleColumnDraft.length - 1"
                      @click="moveVisibleColumn(index, 1)"
                    />
                  </el-tooltip>
                  <el-tooltip content="移到底部" placement="top">
                    <el-button
                      :icon="Bottom"
                      size="small"
                      title="移到底部"
                      aria-label="移到底部"
                      :disabled="index === visibleColumnDraft.length - 1"
                      @click="moveVisibleColumnToEdge(index, 'bottom')"
                    />
                  </el-tooltip>
                </div>
                <div class="selected-actions selected-row-actions" @click.stop>
                  <el-tooltip content="编辑字段配置" placement="top">
                    <el-button
                      :icon="Edit"
                      size="small"
                      title="编辑字段配置"
                      aria-label="编辑字段配置"
                      @click="toggleColumnEditor(column.name)"
                    />
                  </el-tooltip>
                  <el-tooltip :content="isColumnLocked(column.name) ? '锁定列不可移除' : '移除字段'" placement="top">
                    <el-button
                      :icon="isColumnLocked(column.name) ? Lock : Delete"
                      size="small"
                      :type="isColumnLocked(column.name) ? 'warning' : 'default'"
                      :title="isColumnLocked(column.name) ? '锁定列不可移除' : '移除字段'"
                      :aria-label="isColumnLocked(column.name) ? '锁定列不可移除' : '移除字段'"
                      :disabled="isColumnLocked(column.name)"
                      @click="removeColumn(column.name)"
                    />
                  </el-tooltip>
                </div>
                <div v-if="activeColumnEditor === column.name" class="selected-editor" @click.stop>
                  <label class="editor-field">
                    <span>列宽</span>
                    <el-input-number
                      v-model="column.width"
                      size="small"
                      :min="40"
                      :step="10"
                      controls-position="right"
                      placeholder="列宽"
                    />
                  </label>
                  <label class="editor-field">
                    <span>固定</span>
                    <el-select v-model="column.fixed" size="small" placeholder="不固定" clearable>
                      <el-option label="左固定" value="left" />
                      <el-option label="右固定" value="right" />
                    </el-select>
                  </label>
                  <div class="editor-actions">
                    <el-button size="small" link @click="resetColumnSettings(column.name)">恢复默认</el-button>
                    <el-button size="small" type="primary" link @click="closeColumnEditor">完成</el-button>
                  </div>
                </div>
              </article>
            </div>

            <div v-else-if="inspectorTab === 'query'" class="query-panel">
              <el-checkbox v-model="form.saveQueryConditions">保存当前筛选和排序</el-checkbox>
              <div class="query-summary-block">
                <div class="summary-title">筛选条件</div>
                <div v-if="!form.saveQueryConditions || conditionSummary.length === 0" class="empty-text">不保存筛选条件</div>
                <div v-else class="query-summary">
                  <span v-for="item in conditionSummary" :key="item" class="query-summary-item">{{ item }}</span>
                </div>
              </div>
              <div class="query-summary-block">
                <div class="summary-title">排序规则</div>
                <div v-if="!form.saveQueryConditions || orderSummary.length === 0" class="empty-text">不保存排序规则</div>
                <div v-else class="query-summary">
                  <span v-for="item in orderSummary" :key="item" class="query-summary-item">{{ item }}</span>
                </div>
              </div>
              <div class="query-summary-block">
                <div class="summary-title">分页大小</div>
                <span>{{ currentState.pageSize || '-' }}</span>
              </div>
            </div>

            <el-form v-else class="save-form" label-position="top" :model="form">
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
              <el-button v-if="editingPresetId" link type="primary" @click="cancelEdit">
                取消编辑
              </el-button>
            </el-form>
          </el-scrollbar>

          <div class="state-summary">
            <div>
              <strong>{{ visibleColumnDraft.length }}</strong>
              <span>展示列</span>
            </div>
            <div>
              <strong>{{ savedConditionCount }}</strong>
              <span>条件</span>
            </div>
            <div>
              <strong>{{ currentState.pageSize || '-' }}</strong>
              <span>分页</span>
            </div>
          </div>
        </section>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="visible = false">关闭</el-button>
          <el-button v-if="editingPresetId" @click="cancelEdit">取消编辑</el-button>
          <el-button data-testid="list-preset-save" type="primary" :loading="saving" @click="saveCurrentPreset">
            {{ editingPresetId ? '更新方案' : '保存当前方案' }}
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
  ArrowDown,
  ArrowUp,
  Bottom,
  Brush,
  Delete,
  Edit,
  Finished,
  Loading,
  Lock,
  MoreFilled,
  Operation,
  Rank,
  Refresh,
  Search,
  Star,
  Top
} from '@element-plus/icons-vue'
import {
  createListPreset,
  deleteListPreset,
  listPresets,
  setDefaultListPreset,
  updateListPreset
} from '@/api/listPreset'
import type {
  ColumnViewSetting,
  EnhancedColumnSchema,
  ListPresetConfig,
  ListPresetDef,
  ListPresetVisibility,
  ListViewState
} from '@/types'

interface Props {
  config: ListPresetConfig
  getState: () => ListViewState
  applyState: (state: ListViewState, options?: { reload?: boolean }) => void
  reload?: () => void | Promise<void>
  clearConditions?: () => void | Promise<void>
  availableColumns?: EnhancedColumnSchema[]
  lockedColumns?: string[]
  requiredRuntimeColumns?: string[]
}

const props = defineProps<Props>()

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
type MoveEdge = 'top' | 'bottom'

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const clearing = ref(false)
const keyword = ref('')
const fieldKeyword = ref('')
const fieldTypeFilter = ref('')
const activeColumnEditor = ref<string | null>(null)
const inspectorTab = ref<InspectorTab>('columns')
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

const buttonText = computed(() => props.config.buttonText || '自定义查询')
const clearConditionsEnabled = computed(() => Boolean(props.clearConditions))
const currentState = computed(() => props.getState())
const lockedColumnNameSet = computed(() => new Set(props.lockedColumns || []))
const runtimeColumnNameSet = computed(() => new Set(props.requiredRuntimeColumns || []))
const availableColumnMap = computed(() => new Map((props.availableColumns || []).map(column => [column.name, column])))
const visibleColumnDraft = computed(() => columnDraft.value.filter(column => column.visible && !isRuntimeColumn(column.name)))
const appliedPreset = computed(() => presets.value.find(preset => preset.id === appliedPresetId.value))
const defaultPreset = computed(() => presets.value.find(preset => preset.isDefault))
const inspectorTitle = computed(() => {
  if (inspectorTab.value === 'query') return '查询条件'
  if (inspectorTab.value === 'save') return editingPresetId.value ? '编辑方案' : '保存方案'
  return '已选字段'
})
const savedConditionCount = computed(() => form.value.saveQueryConditions ? currentState.value.slice.length : 0)
const conditionSummary = computed(() => {
  if (!form.value.saveQueryConditions) return []
  return currentState.value.slice.slice(0, 8).map(slice => {
    const value = Array.isArray(slice.value) ? slice.value.join(',') : slice.value
    return `${slice.field} ${slice.op} ${value ?? ''}`.trim()
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
  const explicitKey = pickText(column.groupKey, column.groupTitle)
  if (explicitKey) {
    return {
      key: `qm:${explicitKey}`,
      title: pickText(column.groupTitle, column.groupKey) || '未分组',
      order: column.groupOrder ?? 10
    }
  }

  const category = column.category || ''
  const categoryGroup = CATEGORY_GROUPS[category]
  if (categoryGroup) {
    return {
      key: `category:${category}`,
      title: categoryGroup.title,
      order: categoryGroup.order
    }
  }

  return {
    key: 'default',
    title: '未分组',
    order: 999
  }
}

function openDialog() {
  syncColumnDraftFromState()
  visible.value = true
}

function syncColumnDraftFromState() {
  columnDraft.value = buildColumnDraft(currentState.value)
  activeColumnEditor.value = null
}

function isColumnLocked(name: string): boolean {
  return lockedColumnNameSet.value.has(name)
}

function isRuntimeColumn(name: string): boolean {
  return runtimeColumnNameSet.value.has(name)
}

function normalizeVisible(name: string, visibleValue: boolean): boolean {
  if (isRuntimeColumn(name)) return false
  if (isColumnLocked(name)) return true
  return visibleValue
}

function buildColumnDraft(state: ListViewState): ColumnDraft[] {
  const sourceColumns: EnhancedColumnSchema[] = props.availableColumns && props.availableColumns.length > 0
    ? props.availableColumns
    : state.columns.map(name => ({ name, type: 'TEXT', title: name }))
  const settingMap = new Map((state.columnSettings || []).map(setting => [setting.name, setting]))
  const visibleNames = new Set(state.columns || [])
  const hasVisibleColumns = visibleNames.size > 0

  return sourceColumns
    .filter(column => column.name !== '_actions')
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
  const persistedColumns = draft.filter(column => !isRuntimeColumn(column.name))

  return {
    columns: persistedColumns.filter(column => column.visible).map(column => column.name),
    columnSettings: persistedColumns.map(toColumnViewSetting),
    slice: saveQueryConditions ? state.slice : [],
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

function toggleColumn(name: string, value: unknown) {
  const column = columnDraft.value.find(item => item.name === name)
  if (!column) return
  const nextVisible = Boolean(value)
  if (isRuntimeColumn(name)) {
    column.visible = false
    ElMessage.warning('运行时字段不作为展示列保存')
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
  columnDraft.value = columnDraft.value.map(column => ({
    ...column,
    visible: !isRuntimeColumn(column.name)
  }))
}

function clearOptionalColumns() {
  columnDraft.value = columnDraft.value.map(column => ({
    ...column,
    visible: isColumnLocked(column.name) && !isRuntimeColumn(column.name)
  }))
}

function getGroupColumnNames(groupKey: string): Set<string> {
  const group = filteredColumnGroups.value.find(item => item.key === groupKey)
  return new Set((group?.columns || []).map(column => column.name))
}

function selectColumnGroup(groupKey: string) {
  const groupColumnNames = getGroupColumnNames(groupKey)
  columnDraft.value = columnDraft.value.map(column => (
    groupColumnNames.has(column.name)
      ? { ...column, visible: !isRuntimeColumn(column.name) }
      : column
  ))
}

function clearColumnGroup(groupKey: string) {
  const groupColumnNames = getGroupColumnNames(groupKey)
  columnDraft.value = columnDraft.value.map(column => (
    groupColumnNames.has(column.name)
      ? { ...column, visible: isColumnLocked(column.name) && !isRuntimeColumn(column.name) }
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
    ElMessage.error(getErrorMessage(error, '加载自定义查询失败'))
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
  inspectorTab.value = 'save'
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
    inspectorTab.value = 'save'
    ElMessage.warning('请输入查询名称')
    return
  }

  const state = buildStateFromDraft(currentState.value)
  if (!ensureHasVisibleColumns(state)) {
    inspectorTab.value = 'columns'
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
    ElMessage.success(wasEditing ? '自定义查询已更新' : '自定义查询已保存')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, editingPresetId.value ? '更新自定义查询失败' : '保存自定义查询失败'))
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
  align-items: center;
  gap: 8px;
}

.preset-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 340px;
  height: min(640px, calc(100vh - 190px));
  min-height: min(520px, calc(100vh - 190px));
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
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
  gap: 10px;
  margin: 10px 10px 0;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
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
  gap: 2px;
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
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
  min-height: 60px;
  padding: 8px;
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
  color: #c0c4cc;
}

.selected-actions {
  display: flex;
  align-items: center;
  gap: 3px;
}

.selected-actions :deep(.el-button) {
  width: 26px;
  height: 26px;
  padding: 0;
}

.selected-move-actions {
  opacity: 0.78;
}

.selected-row-actions {
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

.selected-editor {
  grid-column: 2 / 5;
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
    grid-template-columns: 220px minmax(0, 1fr) 320px;
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
    grid-template-columns: auto minmax(0, 1fr);
  }

  .selected-move-actions,
  .selected-row-actions,
  .selected-editor {
    grid-column: 2 / 3;
  }

  .selected-editor {
    grid-template-columns: 1fr;
  }
}
</style>
