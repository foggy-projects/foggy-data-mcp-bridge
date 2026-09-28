import { formatCellDisplayValue } from './displayValue'
import type { EnhancedColumnSchema, ViewerDataResponse, ViewerQueryRequest } from '@/types'

export const MAX_VIEWER_EXPORT_ROWS = 10_000
const EXPORT_PAGE_SIZE = 500

function csvCell(value: string): string {
  // Spreadsheet programs may interpret leading formula operators as executable formulas.
  const safe = /^[\s\u0000-\u001f]*[=+\-@]/.test(value) ? `'${value}` : value
  return `"${safe.replace(/"/g, '""')}"`
}

export function buildViewerCsv(columns: EnhancedColumnSchema[], rows: Record<string, unknown>[]): string {
  const header = columns.map(column => csvCell(column.title || column.name)).join(',')
  const body = rows.map(row => columns.map(column =>
    csvCell(formatCellDisplayValue(column, row[column.name]))
  ).join(','))
  return [header, ...body].join('\r\n')
}

/** Re-run the same viewer query in bounded pages, without altering the visible table. */
export async function collectViewerExportRows(
  request: ViewerQueryRequest,
  fetchPage: (request: ViewerQueryRequest) => Promise<ViewerDataResponse>
): Promise<Record<string, unknown>[]> {
  const rows: Record<string, unknown>[] = []
  for (;;) {
    const response = await fetchPage({ ...request, start: rows.length, limit: EXPORT_PAGE_SIZE })
    if (!response.success || response.expired) throw new Error(response.errorMessage || '查询失败，无法导出')
    if (response.total > MAX_VIEWER_EXPORT_ROWS) {
      throw new Error(`结果超过 ${MAX_VIEWER_EXPORT_ROWS.toLocaleString('zh-CN')} 行，请缩小筛选范围后导出`)
    }
    rows.push(...response.items)
    if (rows.length > MAX_VIEWER_EXPORT_ROWS) throw new Error('导出行数超过上限，请缩小筛选范围')
    if (rows.length >= response.total) return rows
    if (!response.items.length) throw new Error('分页查询未返回后续数据，导出已取消')
  }
}
