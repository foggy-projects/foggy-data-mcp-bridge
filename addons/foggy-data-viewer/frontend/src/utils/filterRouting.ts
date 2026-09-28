import type { ColumnSchema, SliceRequestDef } from '@/types'

export interface RoutedMeasureFilters {
  slice: SliceRequestDef[]
  having: SliceRequestDef[]
}

type FilterPhase = 'slice' | 'having' | 'mixed'

function childrenOf(filter: SliceRequestDef): SliceRequestDef[] {
  return [
    ...(filter.$and || []),
    ...(filter.$or || []),
    ...(filter.children || [])
  ]
}

function classifyFilter(filter: SliceRequestDef, measureFields: ReadonlySet<string>): FilterPhase {
  if (filter.$expr) return 'slice'

  const children = childrenOf(filter)
  const phases = new Set<FilterPhase>()
  if (filter.field) phases.add(measureFields.has(filter.field) ? 'having' : 'slice')
  for (const child of children) phases.add(classifyFilter(child, measureFields))

  if (phases.has('mixed') || (phases.has('slice') && phases.has('having'))) return 'mixed'
  return phases.values().next().value || 'slice'
}

/** Route metric filters to HAVING while preserving detail filters as WHERE/slice. */
export function routeMeasureFilters(
  filters: readonly SliceRequestDef[],
  columns: readonly ColumnSchema[]
): RoutedMeasureFilters {
  const measureFields = new Set(columns
    .filter(column => column.measure === true || column.category === 'measure')
    .map(column => column.name))
  const result: RoutedMeasureFilters = { slice: [], having: [] }

  for (const filter of filters) {
    const phase = classifyFilter(filter, measureFields)
    if (phase === 'mixed') {
      throw new Error('同一个逻辑筛选组不能混合明细字段和聚合指标；请分别设置筛选条件。')
    }
    result[phase].push(filter)
  }

  return result
}
