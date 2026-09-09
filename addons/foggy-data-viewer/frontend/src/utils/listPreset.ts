import type { ColumnSchema, ListViewState, SliceRequestDef } from '@/types'

export const MAX_LIST_PRESET_FIELDS = 50
export const MAX_LIST_PRESET_CONDITIONS = 20

export interface ListPresetLimitOptions {
  maxFields?: number
  maxConditions?: number
  /** @deprecated Ignored: execution dependencies never exempt user-authored fields. */
  internalFields?: readonly string[]
}

export interface ListPresetLimitResult {
  fieldCount: number
  conditionCount: number
  maxFields: number
  maxConditions: number
}

function distinctNonBlank(values: readonly (string | null | undefined)[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const value of values) {
    const normalized = value?.trim()
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }
  return result
}

/**
 * Count only user-authored leaf conditions. Logical groups do not consume a
 * slot, while every nested expression does. Empty groups count as zero so
 * legacy payloads can still be inspected and rejected by the normal DSL
 * validation layer.
 */
export function countConditionLeaves(conditions: readonly SliceRequestDef[] | null | undefined): number {
  if (!conditions) return 0

  return conditions.reduce((total, condition) => {
    const groups = [condition.$or, condition.$and, condition.children]
      .filter((children): children is SliceRequestDef[] => Array.isArray(children))
    if (groups.length > 0) {
      return total + groups.reduce((groupTotal, children) => groupTotal + countConditionLeaves(children), 0)
    }
    return total + 1
  }, 0)
}

export function cloneSliceTree(conditions: readonly SliceRequestDef[] | null | undefined): SliceRequestDef[] {
  if (!conditions) return []
  return conditions.map(condition => ({
    ...condition,
    ...(condition.value === undefined ? {} : { value: cloneConditionValue(condition.value) }),
    ...(condition.$or ? { $or: cloneSliceTree(condition.$or) } : {}),
    ...(condition.$and ? { $and: cloneSliceTree(condition.$and) } : {}),
    ...(condition.children ? { children: cloneSliceTree(condition.children) } : {})
  }))
}

function cloneConditionValue(value: unknown): unknown {
  if (value instanceof Date) return new Date(value.getTime())
  if (Array.isArray(value)) return value.map(cloneConditionValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneConditionValue(item)]))
  }
  return value
}

export function getConditionFields(conditions: readonly SliceRequestDef[] | null | undefined): string[] {
  if (!conditions) return []
  const fields: string[] = []
  for (const condition of conditions) {
    if (condition.field?.trim()) fields.push(condition.field)
    fields.push(...getConditionFields(condition.$or))
    fields.push(...getConditionFields(condition.$and))
    fields.push(...getConditionFields(condition.children))
  }
  return distinctNonBlank(fields)
}

export function getListPresetFieldCount(
  state: Pick<ListViewState, 'columns' | 'columnSettings'>,
  _options: Pick<ListPresetLimitOptions, 'internalFields'> = {}
): number {
  const configured = [
    ...(state.columns || []),
    ...(state.columnSettings || []).map(setting => setting.name)
  ]
  return distinctNonBlank(configured).length
}

export function getListPresetLimitResult(
  state: Pick<ListViewState, 'columns' | 'columnSettings' | 'slice'>,
  options: ListPresetLimitOptions = {}
): ListPresetLimitResult {
  const maxFields = options.maxFields ?? MAX_LIST_PRESET_FIELDS
  const maxConditions = options.maxConditions ?? MAX_LIST_PRESET_CONDITIONS
  return {
    fieldCount: getListPresetFieldCount(state, options),
    conditionCount: countConditionLeaves(state.slice),
    maxFields,
    maxConditions
  }
}

export function validateListPresetLimits(
  state: Pick<ListViewState, 'columns' | 'columnSettings' | 'slice'>,
  options: ListPresetLimitOptions = {}
): ListPresetLimitResult {
  const result = getListPresetLimitResult(state, options)
  if (result.fieldCount > result.maxFields) {
    throw new Error(`自定义查询最多配置 ${result.maxFields} 个字段，当前 ${result.fieldCount} 个`)
  }
  if (result.conditionCount > result.maxConditions) {
    throw new Error(`自定义查询最多配置 ${result.maxConditions} 个条件，当前 ${result.conditionCount} 个`)
  }
  return result
}

/** Fields shown to a user in the field/condition editor. */
export function getUserConfigurableColumns(columns: readonly ColumnSchema[]): ColumnSchema[] {
  return columns.filter(column => {
    if (column.name === '_actions') return false
    if (column.name.endsWith('$id')) return false
    if (column.category === 'dimension-id') return false
    return true
  })
}

/** Resolve a raw dimension-id condition to its caption field for editing. */
export function getDisplayColumnForCondition(
  field: string,
  columns: readonly ColumnSchema[]
): ColumnSchema | undefined {
  const direct = columns.find(column => column.name === field)
  if (direct && direct.category !== 'dimension-id' && !direct.name.endsWith('$id')) return direct
  return columns.find(column => column.memberLookup?.selectionFieldName === field)
}

/** Convert a user-facing caption field back to the metadata-declared value field. */
export function getQueryFieldForColumn(column: ColumnSchema | undefined): string | undefined {
  if (!column) return undefined
  return column.memberLookup?.enabled
    ? column.memberLookup.selectionFieldName
    : column.name
}
