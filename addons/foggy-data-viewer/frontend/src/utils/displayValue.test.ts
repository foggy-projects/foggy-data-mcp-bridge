import { describe, it, expect, vi } from 'vitest'
import { formatCellDisplayValue, type DisplayValueColumn } from './displayValue'
import { MONEY_VIEWER } from './viewer'

describe('shared cell display projection', () => {
  const status: DisplayValueColumn = { name: 'orderStatus', type: 'INTEGER',
    dictItems: [{ value: 300, label: '已揽收' }] }
  it.each([300, '300'])('maps dictionary value %s using string keys', value => {
    expect(formatCellDisplayValue(status, value)).toBe('已揽收')
  })
  it('supports string dictionary keys and preserves unknown raw values as text', () => {
    expect(formatCellDisplayValue({ ...status, dictItems: [{ value: '300', label: '已揽收' }] }, 300)).toBe('已揽收')
    expect(formatCellDisplayValue(status, 12345)).toBe('12345')
    expect(formatCellDisplayValue(status, 'unknown')).toBe('unknown')
  })
  it.each([null, undefined, ''])('renders empty %s without calling a formatter', value => {
    const customFormatter = vi.fn(() => 'not empty')
    expect(formatCellDisplayValue({ ...status, customFormatter }, value)).toBe('')
    expect(customFormatter).not.toHaveBeenCalled()
  })
  it('prioritizes custom formatter, dictionary, viewer, then type defaults', () => {
    const col = { ...status, type: 'MONEY', extData: { viewer: MONEY_VIEWER } }
    expect(formatCellDisplayValue({ ...col, customFormatter: value => `custom:${value}` }, 300)).toBe('custom:300')
    expect(formatCellDisplayValue(col, 300)).toBe('已揽收')
    expect(formatCellDisplayValue({ ...col, dictItems: [] }, 300)).toBe('3.00')
    expect(formatCellDisplayValue({ name: 'amount', type: 'MONEY' }, 300)).toBe('300.00')
  })
  it.each([
    ['NUMBER', 1234.5, '1,234.50'], ['INTEGER', 1234, '1,234'],
    ['DATETIME', '2026-09-10T01:02:03Z', '2026-09-10 01:02:03'],
    ['DATE', '2026-09-10T01:02:03Z', '2026-09-10'],
    ['BOOL', true, '是'], ['BOOL', false, '否'], ['TEXT', 0, '0']
  ])('preserves %s defaults', (type, value, expected) => {
    expect(formatCellDisplayValue({ name: 'value', type: type as string }, value)).toBe(expected)
  })
  it('preserves identifier formatting and does not mutate source rows or metadata', () => {
    const row = Object.freeze({ orderStatus: 300 })
    const col = Object.freeze(status)
    expect(formatCellDisplayValue(col, row.orderStatus)).toBe('已揽收')
    expect(row.orderStatus).toBe(300)
    expect(formatCellDisplayValue({ name: 'order_id', type: 'INTEGER' }, 123456)).toBe('123456')
  })
})
