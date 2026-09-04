import { describe, expect, it } from 'vitest'
import type { ColumnSchema } from '@/types'
import {
  MONEY_VIEWER,
  formatViewerValue,
  getColumnMoneyViewer,
  viewerSlicesToDisplay,
  viewerSlicesToRaw
} from './viewer'

const moneyColumn: ColumnSchema = {
  name: 'totalTransportFee',
  title: '运输费',
  type: 'MONEY',
  extData: { viewer: MONEY_VIEWER }
}

describe('money viewer extension', () => {
  it('formats minor-unit values without changing empty or zero semantics', () => {
    expect(formatViewerValue(3400, moneyColumn)).toBe('34.00')
    expect(formatViewerValue(2200, moneyColumn)).toBe('22.00')
    expect(formatViewerValue(0, moneyColumn)).toBe('0.00')
    expect(formatViewerValue(null, moneyColumn)).toBe('')
    expect(formatViewerValue(undefined, moneyColumn)).toBe('')
  })

  it('uses decimal arithmetic for exact display-to-raw filtering', () => {
    const displaySlices = [{
      field: 'totalTransportFee',
      op: '[]',
      value: [34, 34.01]
    }]

    expect(viewerSlicesToRaw(displaySlices, [moneyColumn])).toEqual([{
      field: 'totalTransportFee',
      op: '[]',
      value: [3400, 3401]
    }])
  })

  it('round-trips raw filters into display inputs without repeated conversion', () => {
    const rawSlices = [{ field: 'totalTransportFee', op: '>=', value: 3400 }]
    const displaySlices = viewerSlicesToDisplay(rawSlices, [moneyColumn])

    expect(displaySlices).toEqual([{ field: 'totalTransportFee', op: '>=', value: 34 }])
    expect(viewerSlicesToRaw(displaySlices, [moneyColumn])).toEqual(rawSlices)
  })

  it('does not infer viewer behavior from MONEY type or semanticScaleFactor', () => {
    const aiColumn = {
      name: 'amountYuan',
      title: '金额',
      type: 'MONEY',
      semanticScaleFactor: 100
    } as ColumnSchema & { semanticScaleFactor: number }

    expect(getColumnMoneyViewer(aiColumn)).toBeNull()
    expect(formatViewerValue(34, aiColumn)).toBeNull()
    expect(viewerSlicesToRaw(
      [{ field: 'amountYuan', op: '>=', value: 34 }],
      [aiColumn]
    )).toEqual([{ field: 'amountYuan', op: '>=', value: 34 }])
  })

  it('rejects invalid scaleFactor values', () => {
    const invalidColumn: ColumnSchema = {
      ...moneyColumn,
      extData: { viewer: { ...MONEY_VIEWER, scaleFactor: 0 } }
    }

    expect(() => formatViewerValue(3400, invalidColumn)).toThrow(/scaleFactor/)
  })

  it('honors configured precision', () => {
    const precisionColumn: ColumnSchema = {
      ...moneyColumn,
      extData: { viewer: { ...MONEY_VIEWER, precision: 3 } }
    }

    expect(formatViewerValue(3400, precisionColumn)).toBe('34.000')
  })
})
