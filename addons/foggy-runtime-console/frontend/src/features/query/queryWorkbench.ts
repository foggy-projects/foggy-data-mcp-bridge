export interface QueryPayloadSummary {
  columns: number
  slices: number
  groups: number
  ordering: number
  page: string
}

function arrayLength(value: unknown): number {
  return Array.isArray(value) ? value.length : 0
}

function numberValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

export function summarizeQueryPayload(payload: Record<string, unknown>): QueryPayloadSummary {
  const page = payload.page && typeof payload.page === 'object' && !Array.isArray(payload.page)
    ? payload.page as Record<string, unknown>
    : {}
  const start = numberValue(page.start)
  const limit = numberValue(page.limit)
  return {
    columns: arrayLength(payload.columns),
    slices: arrayLength(payload.slice),
    groups: arrayLength(payload.groupBy),
    ordering: arrayLength(payload.orderBy),
    page: start !== null || limit !== null
      ? `${start ?? 0} / ${limit ?? '—'}`
      : '未设置'
  }
}

function csvValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  const serialized = typeof value === 'object' ? JSON.stringify(value) : String(value)
  const protectedValue = /^\s*[=+\-@]/.test(serialized) ? `'${serialized}` : serialized
  return /[",\r\n]/.test(protectedValue)
    ? `"${protectedValue.replaceAll('"', '""')}"`
    : protectedValue
}

export function queryRowsToCsv(rows: Record<string, unknown>[]): string {
  if (!rows.length) return ''
  const columns = [...new Set(rows.flatMap(row => Object.keys(row)))]
  return [
    columns.map(csvValue).join(','),
    ...rows.map(row => columns.map(column => csvValue(row[column])).join(','))
  ].join('\r\n')
}
