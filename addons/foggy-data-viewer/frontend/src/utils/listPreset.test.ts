import { describe, expect, it } from 'vitest'
import type { EnhancedColumnSchema, SliceRequestDef } from '@/types'
import {
  countConditionLeaves,
  getDisplayColumnForCondition,
  getListPresetFieldCount,
  getQueryFieldForColumn,
  getUserConfigurableColumns,
  validateListPresetLimits
} from './listPreset'

describe('listPreset limits and dimension metadata', () => {
  it('counts nested leaves and enforces 50 fields and 20 conditions', () => {
    const leaves = Array.from({ length: 20 }, (_, index) => ({
      field: `field${index}`,
      op: '=',
      value: index
    }))
    const nested: SliceRequestDef[] = [{
      field: '',
      op: '',
      and: [{
        field: '',
        op: '',
        or: leaves
      }]
    }]

    expect(countConditionLeaves(nested)).toBe(20)
    expect(() => validateListPresetLimits({
      columns: Array.from({ length: 50 }, (_, index) => `field${index}`),
      columnSettings: [],
      slice: nested
    })).not.toThrow()
    expect(() => validateListPresetLimits({
      columns: Array.from({ length: 51 }, (_, index) => `field${index}`),
      columnSettings: [],
      slice: []
    })).toThrow('最多配置 50 个字段')
    expect(() => validateListPresetLimits({
      columns: [],
      columnSettings: [],
      slice: [{ field: '', op: '', or: [...leaves, { field: 'overflow', op: '=' }] }]
    })).toThrow('最多配置 20 个条件')
  })

  it('does not count internal required fields toward the user field limit', () => {
    const state = {
      columns: ['visibleA', 'businessToken'],
      columnSettings: [
        { name: 'visibleA', visible: true, order: 0 },
        { name: 'businessToken', visible: false, order: 1 }
      ]
    }

    expect(getListPresetFieldCount(state, { internalFields: ['businessToken'] })).toBe(1)
  })

  it('shows dimension captions while serializing the metadata selection field', () => {
    const columns: EnhancedColumnSchema[] = [
      {
        name: 'customerName',
        title: '客户',
        type: 'TEXT',
        category: 'dimension-caption',
        memberLookup: {
          enabled: true,
          selectionFieldName: 'customer$id',
          displayFieldName: 'customerName'
        }
      },
      {
        name: 'customer$id',
        title: '客户 ID',
        type: 'INTEGER',
        category: 'dimension-id'
      }
    ]

    expect(getUserConfigurableColumns(columns).map(column => column.name)).toEqual(['customerName'])
    expect(getDisplayColumnForCondition('customer$id', columns)?.name).toBe('customerName')
    expect(getQueryFieldForColumn(columns[0])).toBe('customer$id')
  })
})
