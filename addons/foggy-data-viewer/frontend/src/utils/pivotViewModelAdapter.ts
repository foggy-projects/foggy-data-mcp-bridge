import type {
  PivotHeaderNode,
  PivotRawAxisMember,
  PivotRawCell,
  PivotRawPayload,
  PivotViewModel
} from '@/types/pivot'

const CELL_FIELD_SEPARATOR = '__'

export function toPivotViewModel(payload: PivotRawPayload): PivotViewModel {
  validatePayloadShape(payload)

  const rowMemberByKey = new Map(payload.rowMembers.map(member => [member.key, member]))
  const columnMemberByKey = new Map(payload.columnMembers.map(member => [member.key, member]))
  const metricFields = new Set(payload.metrics.map(metric => metric.field))
  const itemByRowKey = new Map(
    payload.rowMembers.map(member => [member.key, { ...member.values }])
  )

  const headerTree = [
    ...buildRowHeaderNodes(payload),
    ...buildColumnHeaderNodes(payload)
  ]

  for (const cell of payload.cells) {
    validateCell(cell, rowMemberByKey, columnMemberByKey, metricFields)
    const row = itemByRowKey.get(cell.rowKey)
    if (row) {
      row[buildPivotCellField(cell.columnKey, cell.metricField)] = cell.value
    }
  }

  return {
    viewMode: payload.viewMode,
    shape: payload.shape,
    rowAxes: payload.rowAxes,
    columnAxes: payload.columnAxes,
    metrics: payload.metrics,
    headerTree,
    items: payload.rowMembers.map(member => itemByRowKey.get(member.key) ?? { ...member.values }),
    axisPages: payload.axisPages,
    evidence: payload.evidence
  }
}

export function buildPivotCellField(columnKey: string, metricField: string): string {
  return [
    sanitizeFieldSegment(columnKey),
    sanitizeFieldSegment(metricField)
  ].join(CELL_FIELD_SEPARATOR)
}

function buildRowHeaderNodes(payload: PivotRawPayload): PivotHeaderNode[] {
  return payload.rowAxes.map(axis => ({
    field: axis.field,
    title: axis.title ?? axis.field,
    role: 'rowAxis'
  }))
}

function buildColumnHeaderNodes(payload: PivotRawPayload): PivotHeaderNode[] {
  const usedLeafFields = new Set<string>()

  return payload.columnMembers.map(member => ({
    key: member.key,
    title: resolveMemberTitle(member),
    role: 'columnAxisMember',
    axisValue: member.axisValue,
    children: payload.metrics.map(metric => {
      const field = buildPivotCellField(member.key, metric.field)
      if (usedLeafFields.has(field)) {
        throw new Error(`Duplicate pivot cell field "${field}"`)
      }
      usedLeafFields.add(field)

      return {
        field,
        title: metric.title ?? metric.field,
        role: 'metric',
        metricField: metric.field
      }
    })
  }))
}

function validatePayloadShape(payload: PivotRawPayload): void {
  if (payload.viewMode !== 'pivotTable') {
    throw new Error(`Unsupported pivot viewMode "${payload.viewMode}"`)
  }

  if (payload.shape !== 'grid') {
    throw new Error(`Unsupported pivot shape "${payload.shape}"`)
  }
}

function validateCell(
  cell: PivotRawCell,
  rowMemberByKey: Map<string, PivotRawAxisMember>,
  columnMemberByKey: Map<string, PivotRawAxisMember>,
  metricFields: Set<string>
): void {
  if (!rowMemberByKey.has(cell.rowKey)) {
    throw new Error(`Pivot cell references unknown row member "${cell.rowKey}"`)
  }

  if (!columnMemberByKey.has(cell.columnKey)) {
    throw new Error(`Pivot cell references unknown column member "${cell.columnKey}"`)
  }

  if (!metricFields.has(cell.metricField)) {
    throw new Error(`Pivot cell references unknown metric "${cell.metricField}"`)
  }
}

function resolveMemberTitle(member: PivotRawAxisMember): string {
  if (member.title) {
    return member.title
  }

  if (member.axisValue !== undefined && member.axisValue !== null) {
    return String(member.axisValue)
  }

  return member.key
}

function sanitizeFieldSegment(value: string): string {
  const normalized = value.trim().replace(/[^\w]+/g, '_').replace(/^_+|_+$/g, '')
  return normalized || 'empty'
}
