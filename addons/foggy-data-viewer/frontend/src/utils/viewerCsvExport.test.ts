import { describe, expect, it } from 'vitest'
import { buildViewerCsv, collectViewerExportRows, MAX_VIEWER_EXPORT_ROWS } from './viewerCsvExport'
import type { EnhancedColumnSchema, ViewerDataResponse } from '@/types'

const columns = [
  { name: 'status', title: '状态', type: 'STRING' },
  { name: 'amount', title: '金额', type: 'NUMBER' }
] as EnhancedColumnSchema[]

describe('viewer CSV export', () => {
  it('escapes CSV and neutralizes spreadsheet formulas', () => {
    const csv = buildViewerCsv(columns, [{ status: '=HYPERLINK("bad")', amount: 12.5 }])
    expect(csv).toBe('"状态","金额"\r\n"\'=HYPERLINK(""bad"")","12.50"')
  })

  it('paginates the current query without retaining the visible page start/limit', async () => {
    const requests: Array<{ start?: number; limit?: number }> = []
    const rows = await collectViewerExportRows({ start: 50, limit: 50, slice: [{ field: 'status', op: '=', value: 'ok' }] }, async request => {
      requests.push(request)
      return { success: true, items: request.start === 0 ? [{ status: 'ok' }] : [{ status: 'done' }], total: 2 } as ViewerDataResponse
    })
    expect(requests.map(request => [request.start, request.limit])).toEqual([[0, 500], [1, 500]])
    expect(requests[0]).toMatchObject({ slice: [{ field: 'status', op: '=', value: 'ok' }] })
    expect(rows).toHaveLength(2)
  })

  it('refuses an oversized export before downloading', async () => {
    await expect(collectViewerExportRows({}, async () => ({ success: true, items: [{ status: 'ok' }], total: MAX_VIEWER_EXPORT_ROWS + 1 } as ViewerDataResponse)))
      .rejects.toThrow('请缩小筛选范围')
  })
})
