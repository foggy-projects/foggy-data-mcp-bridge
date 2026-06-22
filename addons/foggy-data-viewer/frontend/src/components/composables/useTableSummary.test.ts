import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useTableSummary } from './useTableSummary'
import type { ColumnSchema } from '@/types'

describe('useTableSummary', () => {
  const mockColumns = ref<ColumnSchema[]>([
    { name: 'id', type: 'INTEGER', title: 'ID' },
    { name: 'name', type: 'TEXT', title: '名称' },
    { name: 'amount', type: 'MONEY', title: '金额', measure: true, aggregatable: true },
    { name: 'count', type: 'NUMBER', title: '数量', aggregatable: true },
    { name: 'capacity', type: 'NUMBER', title: '容量' },
    { name: 'status', type: 'TEXT', title: '状态' }
  ])

  describe('measureColumns', () => {
    it('should only identify explicit numeric summary columns', () => {
      const { measureColumns } = useTableSummary(mockColumns)

      expect(measureColumns.value).toHaveLength(2)
      expect(measureColumns.value.map(c => c.name)).toEqual(['amount', 'count'])
    })

    it('should ignore numeric columns without measure or aggregatable flags', () => {
      const columns = ref<ColumnSchema[]>([
        { name: 'moneyAttr', type: 'MONEY', title: '金额属性' },
        { name: 'numberAttr', type: 'NUMBER', title: '数值属性' },
        { name: 'textSummary', type: 'TEXT', title: '文本', aggregatable: true },
        { name: 'amount', type: 'MONEY', title: '金额', measure: true }
      ])

      const { measureColumns } = useTableSummary(columns)

      expect(measureColumns.value.map(c => c.name)).toEqual(['amount'])
    })

    it('should recognize all supported numeric summary types when flagged', () => {
      const measureTypeColumns = ref<ColumnSchema[]>([
        { name: 'col1', type: 'NUMBER', title: 'Number', aggregatable: true },
        { name: 'col2', type: 'MONEY', title: 'Money', aggregatable: true },
        { name: 'col3', type: 'BIGDECIMAL', title: 'BigDecimal', aggregatable: true },
        { name: 'col4', type: 'INTEGER', title: 'Integer', aggregatable: true },
        { name: 'col5', type: 'BIGINT', title: 'BigInt', aggregatable: true },
        { name: 'col6', type: 'LONG', title: 'Long', aggregatable: true },
        { name: 'col7', type: 'TEXT', title: 'Text', aggregatable: true }
      ])

      const { measureColumns } = useTableSummary(measureTypeColumns)

      expect(measureColumns.value.map(c => c.name)).toEqual([
        'col1', 'col2', 'col3', 'col4', 'col5', 'col6'
      ])
    })
  })

  describe('calculateSelectedSummary', () => {
    it('should calculate summary for selected rows', () => {
      const { calculateSelectedSummary } = useTableSummary(mockColumns)

      const selectedRows = [
        { id: 1, name: 'Test 1', amount: 100, count: 5, capacity: 2 },
        { id: 2, name: 'Test 2', amount: 200, count: 10, capacity: 3 },
        { id: 3, name: 'Test 3', amount: 300, count: 15, capacity: 4 }
      ]

      const summary = calculateSelectedSummary(selectedRows)

      expect(summary._count).toBe(3)
      expect(summary.id).toBeUndefined()
      expect(summary.amount).toBe(600)
      expect(summary.count).toBe(30)
      expect(summary.capacity).toBeUndefined()
    })

    it('should handle empty selection', () => {
      const { calculateSelectedSummary } = useTableSummary(mockColumns)

      const summary = calculateSelectedSummary([])

      expect(summary._count).toBe(0)
      expect(summary.id).toBeUndefined()
      expect(summary.amount).toBeUndefined()
      expect(summary.count).toBeUndefined()
    })

    it('should handle null/undefined values', () => {
      const { calculateSelectedSummary } = useTableSummary(mockColumns)

      const selectedRows = [
        { id: 1, name: 'Test 1', amount: null, count: 5 },
        { id: 2, name: 'Test 2', amount: undefined, count: 10 },
        { id: 3, name: 'Test 3', amount: 100, count: null }
      ]

      const summary = calculateSelectedSummary(selectedRows)

      expect(summary._count).toBe(3)
      expect(summary.amount).toBe(100)
      expect(summary.count).toBe(15)
    })

    it('should handle non-numeric values', () => {
      const { calculateSelectedSummary } = useTableSummary(mockColumns)

      const selectedRows = [
        { id: 1, name: 'Test 1', amount: 'abc', count: 5 },
        { id: 2, name: 'Test 2', amount: 100, count: 'xyz' }
      ]

      const summary = calculateSelectedSummary(selectedRows)

      expect(summary._count).toBe(2)
      expect(summary.amount).toBe(100)
      expect(summary.count).toBe(5)
    })
  })

  describe('formatValue', () => {
    it('should format decimal numeric types', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(1234.5, 'MONEY')).toBe('1,234.50')
      expect(formatValue(1234.567, 'NUMBER')).toBe('1,234.57')
      expect(formatValue(9999.99, 'BIGDECIMAL')).toBe('9,999.99')
    })

    it('should format integer types without decimals', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(1234, 'INTEGER')).toBe('1,234')
      expect(formatValue(0, 'INTEGER')).toBe('0')
    })

    it('should handle empty and non-numeric values', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(null, 'MONEY')).toBe('')
      expect(formatValue(undefined, 'MONEY')).toBe('')
      expect(formatValue('text', 'MONEY')).toBe('text')
      expect(formatValue(0, 'MONEY')).toBe('0.00')
    })
  })

  describe('generateFooterData', () => {
    it('should generate footer data with labels, counts and summary columns', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ _count: 100, amount: 10000, count: 500 })

      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' },
        { field: 'amount', type: 'MONEY' },
        { field: 'count', type: 'NUMBER' },
        { field: 'capacity', type: 'NUMBER' }
      ]

      const selectedSummary = { _count: 3, amount: 600, count: 30 }
      const footerData = generateFooterData(visibleColumns, selectedSummary)

      expect(footerData).toHaveLength(2)
      expect(footerData[0]).toEqual(['选中', '3 条', '600.00', '30.00', null])
      expect(footerData[1]).toEqual(['合计', '100 条', '10,000.00', '500.00', null])
    })

    it('should keep selected summary cells empty when no rows are selected', () => {
      const { calculateSelectedSummary, generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100, amount: 10000, count: 500 })

      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' },
        { field: 'amount', type: 'MONEY' },
        { field: 'count', type: 'NUMBER' },
        { field: 'capacity', type: 'NUMBER' }
      ]

      const footerData = generateFooterData(visibleColumns, calculateSelectedSummary([]))

      expect(footerData[0]).toEqual(['选中', '0 条', '', '', null])
      expect(footerData[1]).toEqual(['合计', '100 条', '10,000.00', '500.00', null])
    })

    it('should still show zero totals when selected rows sum to zero', () => {
      const { calculateSelectedSummary, generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 1, amount: 0, count: 0 })

      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' },
        { field: 'amount', type: 'MONEY' },
        { field: 'count', type: 'NUMBER' }
      ]

      const footerData = generateFooterData(
        visibleColumns,
        calculateSelectedSummary([{ id: 1, amount: 0, count: 0 }])
      )

      expect(footerData[0]).toEqual(['选中', '1 条', '0.00', '0.00'])
      expect(footerData[1]).toEqual(['合计', '1 条', '0.00', '0.00'])
    })

    it('should prefer server total over fallback total', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100 })

      const footerData = generateFooterData(
        [{ field: undefined }, { field: 'id', type: 'INTEGER' }],
        { _count: 5 },
        200
      )

      expect(footerData[0][1]).toBe('5 条')
      expect(footerData[1][1]).toBe('100 条')
    })

    it('should fall back to table total when server summary has no count', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary(null)

      const footerData = generateFooterData(
        [{ field: undefined }, { field: 'id', type: 'INTEGER' }],
        { _count: 5 },
        123
      )

      expect(footerData[1][1]).toBe('123 条')
    })

    it('should show null for non-summary columns', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100 })

      const footerData = generateFooterData(
        [
          { field: undefined },
          { field: 'id', type: 'INTEGER' },
          { field: 'name', type: 'TEXT' }
        ],
        { _count: 3 }
      )

      expect(footerData[0][2]).toBeNull()
      expect(footerData[1][2]).toBeNull()
    })

    it('should handle zero counts', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 0 })

      const footerData = generateFooterData(
        [{ field: undefined }, { field: 'id', type: 'INTEGER' }],
        { _count: 0 }
      )

      expect(footerData[0][1]).toBe('0 条')
      expect(footerData[1][1]).toBe('0 条')
    })
  })

  describe('setServerSummary', () => {
    it('should set server summary data', () => {
      const { serverSummary, setServerSummary } = useTableSummary(mockColumns)

      const summaryData = { total: 100, amount: 10000 }
      setServerSummary(summaryData)

      expect(serverSummary.value).toEqual(summaryData)
    })

    it('should handle null server summary', () => {
      const { serverSummary, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary(null)

      expect(serverSummary.value).toBeNull()
    })

    it('should update existing server summary', () => {
      const { serverSummary, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 50 })
      expect(serverSummary.value?.total).toBe(50)

      setServerSummary({ total: 100 })
      expect(serverSummary.value?.total).toBe(100)
    })
  })
})
