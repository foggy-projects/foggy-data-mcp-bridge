import { describe, it, expect } from 'vitest'
import { useTableSelection } from './useTableSelection'

describe('useTableSelection', () => {
  describe('selectedRows', () => {
    it('should initialize with empty array', () => {
      const { selectedRows } = useTableSelection()

      expect(selectedRows.value).toEqual([])
    })
  })

  describe('onCheckboxChange', () => {
    it('should update selected rows when checkbox changes', () => {
      const { selectedRows, onCheckboxChange } = useTableSelection()

      const mockRecords = [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' }
      ]

      onCheckboxChange({ records: mockRecords })

      expect(selectedRows.value).toEqual(mockRecords)
    })

    it('should handle single row selection', () => {
      const { selectedRows, onCheckboxChange } = useTableSelection()

      const mockRecord = [{ id: 1, name: 'Test 1' }]

      onCheckboxChange({ records: mockRecord })

      expect(selectedRows.value).toHaveLength(1)
      expect(selectedRows.value[0]).toEqual(mockRecord[0])
    })

    it('should handle deselection', () => {
      const { selectedRows, onCheckboxChange } = useTableSelection()

      // 先选中
      onCheckboxChange({ records: [{ id: 1, name: 'Test 1' }] })
      expect(selectedRows.value).toHaveLength(1)

      // 取消选中
      onCheckboxChange({ records: [] })
      expect(selectedRows.value).toHaveLength(0)
    })

    it('should replace previous selection', () => {
      const { selectedRows, onCheckboxChange } = useTableSelection()

      onCheckboxChange({ records: [{ id: 1, name: 'Test 1' }] })
      expect(selectedRows.value[0].id).toBe(1)

      onCheckboxChange({ records: [{ id: 2, name: 'Test 2' }] })
      expect(selectedRows.value).toHaveLength(1)
      expect(selectedRows.value[0].id).toBe(2)
    })
  })

  describe('onCheckboxAll', () => {
    it('should select all rows', () => {
      const { selectedRows, onCheckboxAll } = useTableSelection()

      const mockRecords = [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' },
        { id: 3, name: 'Test 3' }
      ]

      onCheckboxAll({ records: mockRecords })

      expect(selectedRows.value).toEqual(mockRecords)
      expect(selectedRows.value).toHaveLength(3)
    })

    it('should deselect all rows', () => {
      const { selectedRows, onCheckboxAll } = useTableSelection()

      // 先全选
      onCheckboxAll({ records: [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' }
      ] })
      expect(selectedRows.value).toHaveLength(2)

      // 取消全选
      onCheckboxAll({ records: [] })
      expect(selectedRows.value).toHaveLength(0)
    })

    it('should replace previous selection with all rows', () => {
      const { selectedRows, onCheckboxChange, onCheckboxAll } = useTableSelection()

      // 先选中一行
      onCheckboxChange({ records: [{ id: 1, name: 'Test 1' }] })
      expect(selectedRows.value).toHaveLength(1)

      // 全选
      const allRecords = [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' },
        { id: 3, name: 'Test 3' }
      ]
      onCheckboxAll({ records: allRecords })
      expect(selectedRows.value).toHaveLength(3)
    })
  })

  describe('clearSelection', () => {
    it('should clear all selected rows', () => {
      const { selectedRows, onCheckboxChange, clearSelection } = useTableSelection()

      onCheckboxChange({ records: [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' }
      ] })
      expect(selectedRows.value).toHaveLength(2)

      clearSelection()
      expect(selectedRows.value).toEqual([])
    })

    it('should work when no rows are selected', () => {
      const { selectedRows, clearSelection } = useTableSelection()

      expect(selectedRows.value).toHaveLength(0)
      clearSelection()
      expect(selectedRows.value).toHaveLength(0)
    })

    it('should be idempotent', () => {
      const { selectedRows, onCheckboxChange, clearSelection } = useTableSelection()

      onCheckboxChange({ records: [{ id: 1, name: 'Test 1' }] })

      clearSelection()
      expect(selectedRows.value).toHaveLength(0)

      clearSelection()
      expect(selectedRows.value).toHaveLength(0)
    })
  })

  describe('getSelectedCount', () => {
    it('should return 0 when no rows selected', () => {
      const { getSelectedCount } = useTableSelection()

      expect(getSelectedCount()).toBe(0)
    })

    it('should return correct count of selected rows', () => {
      const { onCheckboxChange, getSelectedCount } = useTableSelection()

      onCheckboxChange({ records: [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' }
      ] })

      expect(getSelectedCount()).toBe(2)
    })

    it('should update when selection changes', () => {
      const { onCheckboxChange, clearSelection, getSelectedCount } = useTableSelection()

      expect(getSelectedCount()).toBe(0)

      onCheckboxChange({ records: [{ id: 1, name: 'Test 1' }] })
      expect(getSelectedCount()).toBe(1)

      onCheckboxChange({ records: [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' }
      ] })
      expect(getSelectedCount()).toBe(2)

      clearSelection()
      expect(getSelectedCount()).toBe(0)
    })
  })

  describe('Type Safety', () => {
    interface CustomRow {
      customId: number
      customName: string
    }

    it('should support custom row types', () => {
      const { selectedRows, onCheckboxChange } = useTableSelection<CustomRow>()

      const mockRecords: CustomRow[] = [
        { customId: 1, customName: 'Custom 1' },
        { customId: 2, customName: 'Custom 2' }
      ]

      onCheckboxChange({ records: mockRecords })

      expect(selectedRows.value).toEqual(mockRecords)
      expect(selectedRows.value[0].customId).toBe(1)
      expect(selectedRows.value[0].customName).toBe('Custom 1')
    })
  })

  describe('Integration', () => {
    it('should work together in complete workflow', () => {
      const { selectedRows, onCheckboxChange, onCheckboxAll, clearSelection, getSelectedCount } =
        useTableSelection()

      // 初始状态
      expect(getSelectedCount()).toBe(0)

      // 选中一行
      onCheckboxChange({ records: [{ id: 1, name: 'Test 1' }] })
      expect(getSelectedCount()).toBe(1)

      // 全选
      onCheckboxAll({ records: [
        { id: 1, name: 'Test 1' },
        { id: 2, name: 'Test 2' },
        { id: 3, name: 'Test 3' }
      ] })
      expect(getSelectedCount()).toBe(3)

      // 取消选中一行
      onCheckboxChange({ records: [
        { id: 1, name: 'Test 1' },
        { id: 3, name: 'Test 3' }
      ] })
      expect(getSelectedCount()).toBe(2)

      // 清空选择
      clearSelection()
      expect(getSelectedCount()).toBe(0)
      expect(selectedRows.value).toEqual([])
    })

    it('should handle rapid selection changes', () => {
      const { onCheckboxChange, getSelectedCount } = useTableSelection()

      // 快速连续选择
      for (let i = 1; i <= 5; i++) {
        onCheckboxChange({ records: Array.from({ length: i }, (_, j) => ({ id: j + 1 })) })
        expect(getSelectedCount()).toBe(i)
      }
    })
  })
})
