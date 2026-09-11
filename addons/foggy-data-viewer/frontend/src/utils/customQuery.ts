import type { ListViewState, SliceRequestDef } from '@/types'
import { cloneSliceTree, validateListPresetLimits } from './listPreset'

export const relativeDateOptions = [
  { value: 'today', label: '今天' },
  { value: 'yesterday', label: '昨天' },
  { value: 'last3days', label: '近三天' },
  { value: 'last7days', label: '近一周' },
  { value: 'thisweek', label: '本周' },
  { value: 'lastweek', label: '上周' },
  { value: 'last2weeks', label: '近两周' },
  { value: 'thismonth', label: '本月' },
  { value: 'lastmonth', label: '上月' },
  { value: 'last1month', label: '近一个月' }
] as const

export type RelativeDateRange = typeof relativeDateOptions[number]['value']
/** UI-only value: persisted as configuration, never sent to the query engine. */
export interface RelativeDateValue {
  $relativeDate: RelativeDateRange
  dateTime?: boolean
}

export interface CustomQueryExecutionOptions {
  now?: Date
  /** Business calendar timezone; defaults to the browser timezone. */
  timeZone?: string
}

export function isRelativeDateValue(value: unknown): value is RelativeDateValue {
  return !!value && typeof value === 'object' && '$relativeDate' in value
}

export function resolveRelativeDate(value: RelativeDateValue, options: CustomQueryExecutionOptions = {}): [string, string] {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: options.timeZone, year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(options.now ?? new Date())
  const part = (key: string) => Number(parts.find(p => p.type === key)!.value)
  // UTC is used only for calendar arithmetic; output is a business-local date.
  const today = new Date(Date.UTC(part('year'), part('month') - 1, part('day')))
  const day = (date: Date, offset: number) => new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() + offset))
  const monthStart = (offset: number) => new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() + offset, 1))
  let start = today
  let end = day(today, 1)
  switch (value.$relativeDate) {
    case 'today': break
    case 'yesterday': start = day(today, -1); end = today; break
    case 'last3days': start = day(today, -2); break
    case 'last7days': start = day(today, -6); break
    case 'last2weeks': start = day(today, -13); break
    case 'thisweek':
    case 'lastweek': {
      start = day(today, -((today.getUTCDay() + 6) % 7))
      if (value.$relativeDate === 'lastweek') start = day(start, -7)
      end = day(start, 7)
      break
    }
    case 'thismonth': start = monthStart(0); end = monthStart(1); break
    case 'lastmonth': start = monthStart(-1); end = monthStart(0); break
    case 'last1month': {
      const lastDay = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), 0)).getUTCDate()
      start = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth() - 1, Math.min(end.getUTCDate(), lastDay)))
      break
    }
    default: throw new Error('不支持的相对日期范围')
  }
  const format = (date: Date) => date.toISOString().slice(0, 10) + (value.dateTime ? ' 00:00:00' : '')
  return [format(start), format(end)]
}

/** Resolve relative date tokens only for controls that need concrete display values. */
export function resolveRelativeDatesForDisplay(
  conditions: readonly SliceRequestDef[] | null | undefined,
  options: CustomQueryExecutionOptions = {}
): SliceRequestDef[] {
  return cloneSliceTree(conditions).map(condition => {
    const next = {
      ...condition,
      ...(condition.$or ? { $or: resolveRelativeDatesForDisplay(condition.$or, options) } : {}),
      ...(condition.$and ? { $and: resolveRelativeDatesForDisplay(condition.$and, options) } : {}),
      ...(condition.children ? { children: resolveRelativeDatesForDisplay(condition.children, options) } : {})
    }

    if (!isRelativeDateValue(condition.value)) return next
    return {
      ...next,
      op: '[)',
      value: resolveRelativeDate(condition.value, options)
    }
  })
}

function isBlank(value: unknown): boolean {
  return value == null || (typeof value === 'string' && value.trim() === '')
}

/** Normalize a copy of USER conditions before business hooks. Never cleans fixed business conditions. */
export function normalizeUserSlice(conditions: readonly SliceRequestDef[] | null | undefined, options: CustomQueryExecutionOptions = {}): SliceRequestDef[] {
  return cloneSliceTree(conditions).flatMap(condition => {
    const groups = (['$and', '$or', 'children'] as const).filter(key => Array.isArray(condition[key]))
    if (groups.length > 1) throw new Error('一个条件组只能选择一种 AND/OR 关系')
    if (groups.length) {
      const key = groups[0]
      const children = normalizeUserSlice(condition[key], options)
      return children.length ? [{ ...condition, [key]: children }] : []
    }
    if (condition.$expr) return [condition]
    if (condition.op === 'is null' || condition.op === 'is not null') {
      delete condition.value
      return [condition]
    }
    if (isRelativeDateValue(condition.value)) {
      return [{ ...condition, op: '[)', value: resolveRelativeDate(condition.value, options) }]
    }
    if (isBlank(condition.value)) return []
    if (Array.isArray(condition.value)) {
      if (condition.value.length === 0 || condition.value.every(isBlank)) return []
      if (['[]', '[)', '(]', '()'].includes(condition.op)) {
        if (condition.value.length !== 2 || condition.value.some(isBlank)) {
          throw new Error(`${condition.field}：请填写完整的范围起止值`)
        }
      } else {
        condition.value = condition.value.filter(value => !isBlank(value))
      }
    }
    return [condition]
  })
}

/** Also available to controlled tables whose fetchData is owned by the page. */
export function prepareCustomQuery(state: ListViewState, options: CustomQueryExecutionOptions = {}): ListViewState {
  validateListPresetLimits(state)
  return {
    ...state,
    columns: [...state.columns],
    columnSettings: state.columnSettings?.map(column => ({ ...column })),
    orderBy: state.orderBy.map(order => ({ ...order })),
    slice: normalizeUserSlice(state.slice, options)
  }
}
