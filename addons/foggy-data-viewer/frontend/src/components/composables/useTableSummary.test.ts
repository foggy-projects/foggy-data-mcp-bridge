import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useTableSummary } from './useTableSummary'
import type { ColumnSchema } from '@/types'

describe('useTableSummary', () => {
  const mockColumns = ref<ColumnSchema[]>([
    { name: 'id', type: 'INTEGER', title: 'ID' },
    { name: 'name', type: 'TEXT', title: '名称' },
    { name: 'amount', type: 'MONEY', title: '金额' },
    { name: 'count', type: 'NUMBER', title: '数量' },
    { name: 'status', type: 'TEXT', title: '状态' }
  ])

  describe('measureColumns', () => {
    it('should identify measure columns correctly', () => {
      const { measureColumns } = useTableSummary(mockColumns)

      expect(measureColumns.value).toHaveLength(3)  // id, amount, count
      expect(measureColumns.value[0].name).toBe('id')
      expect(measureColumns.value[1].name).toBe('amount')
      expect(measureColumns.value[2].name).toBe('count')
    })

    it('should handle columns without type', () => {
      const columnsWithoutType = ref<ColumnSchema[]>([
        { name: 'col1', type: '', title: 'Column 1' },
        { name: 'col2', type: 'MONEY', title: 'Column 2' }
      ])

      const { measureColumns } = useTableSummary(columnsWithoutType)

      expect(measureColumns.value).toHaveLength(1)
      expect(measureColumns.value[0].name).toBe('col2')
    })

    it('should recognize all measure types', () => {
      const measureTypeColumns = ref<ColumnSchema[]>([
        { name: 'col1', type: 'NUMBER', title: 'Number' },
        { name: 'col2', type: 'MONEY', title: 'Money' },
        { name: 'col3', type: 'BIGDECIMAL', title: 'BigDecimal' },
        { name: 'col4', type: 'INTEGER', title: 'Integer' },
        { name: 'col5', type: 'BIGINT', title: 'BigInt' },
        { name: 'col6', type: 'LONG', title: 'Long' },
        { name: 'col7', type: 'TEXT', title: 'Text' }
      ])

      const { measureColumns } = useTableSummary(measureTypeColumns)

      expect(measureColumns.value).toHaveLength(6)
      expect(measureColumns.value.map(c => c.name)).toEqual([
        'col1', 'col2', 'col3', 'col4', 'col5', 'col6'
      ])
    })
  })

  describe('calculateSelectedSummary', () => {
    it('should calculate summary for selected rows', () => {
      const { calculateSelectedSummary } = useTableSummary(mockColumns)

      const selectedRows = [
        { id: 1, name: 'Test 1', amount: 100, count: 5 },
        { id: 2, name: 'Test 2', amount: 200, count: 10 },
        { id: 3, name: 'Test 3', amount: 300, count: 15 }
      ]

      const summary = calculateSelectedSummary(selectedRows)

      expect(summary._count).toBe(3)
      expect(summary.id).toBe(6)  // 1 + 2 + 3
      expect(summary.amount).toBe(600)
      expect(summary.count).toBe(30)
    })

    it('should handle empty selection', () => {
      const { calculateSelectedSummary } = useTableSummary(mockColumns)

      const summary = calculateSelectedSummary([])

      expect(summary._count).toBe(0)
      expect(summary.id).toBe(0)
      expect(summary.amount).toBe(0)
      expect(summary.count).toBe(0)
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
      expect(summary.id).toBe(6)
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
      expect(summary.id).toBe(3)
      expect(summary.amount).toBe(100)
      expect(summary.count).toBe(5)
    })
  })

  describe('formatValue', () => {
    it('should format MONEY type', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(1234.5, 'MONEY')).toBe('1,234.50')
      expect(formatValue(1000000, 'MONEY')).toBe('1,000,000.00')
    })

    it('should format NUMBER type', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(1234.567, 'NUMBER')).toBe('1,234.57')
    })

    it('should format BIGDECIMAL type', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(9999.99, 'BIGDECIMAL')).toBe('9,999.99')
    })

    it('should format INTEGER type without decimals', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(1234, 'INTEGER')).toBe('1,234')
    })

    it('should handle null/undefined values', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(null, 'MONEY')).toBe('')
      expect(formatValue(undefined, 'MONEY')).toBe('')
    })

    it('should handle non-numeric values', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue('text', 'MONEY')).toBe('text')
    })

    it('should format zero correctly', () => {
      const { formatValue } = useTableSummary(mockColumns)

      expect(formatValue(0, 'MONEY')).toBe('0.00')
      expect(formatValue(0, 'INTEGER')).toBe('0')
    })
  })

  describe('generateFooterData', () => {
    it('should generate footer data with two rows', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100, amount: 10000, count: 500 })

      const visibleColumns = [
        { field: undefined },  // checkbox column
        { field: 'id', type: 'INTEGER' },
        { field: 'amount', type: 'MONEY' },
        { field: 'count', type: 'NUMBER' }
      ]

      const selectedSummary = { _count: 3, amount: 600, count: 30 }

      const footerData = generateFooterData(visibleColumns, selectedSummary)

      expect(footerData).toHaveLength(2)
      expect(footerData[0]).toHaveLength(4)  // 选中行
      expect(footerData[1]).toHaveLength(4)  // 全量汇总
    })

    it('should show labels in first column', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100 })

      const visibleColumns = [{ field: undefined }]
      const selectedSummary = { _count: 3 }

      const footerData = generateFooterData(visibleColumns, selectedSummary)

      expect(footerData[0][0]).toBe('选中')
      expect(footerData[1][0]).toBe('合计')
    })

    it('should show count in second column', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100 })

      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' }
      ]
      const selectedSummary = { _count: 5 }

      const footerData = generateFooterData(visibleColumns, selectedSummary)

      expect(footerData[0][1]).toBe('5 条')
      expect(footerData[1][1]).toBe('100 条')
    })

    it('should show measure values in other columns', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100, amount: 10000 })

      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' },
        { field: 'amount', type: 'MONEY' }
      ]
      const selectedSummary = { _count: 3, amount: 600 }

      const footerData = generateFooterData(visibleColumns, selectedSummary)

      expect(footerData[0][2]).toBe('600.00')
      expect(footerData[1][2]).toBe('10,000.00')
    })

    it('should show null for non-measure columns', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 100 })

      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' },
        { field: 'name', type: 'TEXT' }  // non-measure
      ]
      const selectedSummary = { _count: 3 }

      const footerData = generateFooterData(visibleColumns, selectedSummary)

      expect(footerData[0][2]).toBeNull()
      expect(footerData[1][2]).toBeNull()
    })

    it('should handle zero counts', () => {
      const { generateFooterData, setServerSummary } = useTableSummary(mockColumns)

      setServerSummary({ total: 0 })

      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' }
      ]
      const selectedSummary = { _count: 0 }

      const footerData = generateFooterData(visibleColumns, selectedSummary)

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

  describe('Integration', () => {
    it('should work together for complete workflow', () => {
      const {
        measureColumns,
        calculateSelectedSummary,
        generateFooterData,
        setServerSummary
      } = useTableSummary(mockColumns)

      // 1. 识别度量列
      expect(measureColumns.value).toHaveLength(3)  // id, amount, count

      // 2. 设置服务端汇总
      setServerSummary({ total: 100, amount: 50000, count: 1000 })

      // 3. 计算选中行汇总
      const selectedRows = [
        { id: 1, amount: 100, count: 5 },
        { id: 2, amount: 200, count: 10 }
      ]
      const selectedSummary = calculateSelectedSummary(selectedRows)

      expect(selectedSummary._count).toBe(2)
      expect(selectedSummary.amount).toBe(300)

      // 4. 生成 footer 数据
      const visibleColumns = [
        { field: undefined },
        { field: 'id', type: 'INTEGER' },
        { field: 'amount', type: 'MONEY' }
      ]
      const footerData = generateFooterData(visibleColumns, selectedSummary)

      expect(footerData).toHaveLength(2)
      expect(footerData[0][1]).toBe('2 条')
      expect(footerData[1][1]).toBe('100 条')
    })
  })
})
