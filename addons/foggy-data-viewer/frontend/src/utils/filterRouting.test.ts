import { describe, expect, it } from 'vitest'
import type { ColumnSchema, SliceRequestDef } from '@/types'
import { routeMeasureFilters } from './filterRouting'

const columns: ColumnSchema[] = [
  { name: 'businessDate', type: 'DATE', category: 'dimension-property' },
  { name: 'waybillCount', type: 'INTEGER', category: 'measure', measure: true, aggregation: 'SUM' }
]

describe('routeMeasureFilters', () => {
  it('routes model measures to having and keeps row filters in slice', () => {
    const detail: SliceRequestDef = { field: 'businessDate', op: '=', value: '2026-09-26' }
    const measure: SliceRequestDef = { field: 'waybillCount', op: '[]', value: [1, 2] }

    expect(routeMeasureFilters([detail, measure], columns)).toEqual({
      slice: [detail],
      having: [measure]
    })
  })

  it('keeps an all-measure logical group intact in having', () => {
    const group: SliceRequestDef = {
      $or: [
        { field: 'waybillCount', op: '<', value: 2 },
        { field: 'waybillCount', op: '>', value: 10 }
      ]
    }

    expect(routeMeasureFilters([group], columns).having).toEqual([group])
  })

  it('rejects a logical group that mixes row and aggregate phases', () => {
    expect(() => routeMeasureFilters([{
      $or: [
        { field: 'businessDate', op: '=', value: '2026-09-26' },
        { field: 'waybillCount', op: '>', value: 2 }
      ]
    }], columns)).toThrow(/不能混合明细字段和聚合指标/)
  })
})
