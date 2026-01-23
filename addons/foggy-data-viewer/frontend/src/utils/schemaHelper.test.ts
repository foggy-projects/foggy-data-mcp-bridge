import { describe, it, expect, vi } from 'vitest'
import { buildTableColumns } from './schemaHelper'
import type { ColumnSchema, TableConfig } from '@/types'

describe('schemaHelper', () => {
  const mockQMSchema: ColumnSchema[] = [
    { name: 'id', type: 'INTEGER', title: 'ID' },
    { name: 'name', type: 'TEXT', title: '名称' },
    { name: 'amount', type: 'MONEY', title: '金额' },
    { name: 'status', type: 'TEXT', title: '状态' }
  ]

  describe('buildTableColumns', () => {
    it('should throw error when neither visibleColumns nor showAll is provided', () => {
      const config: TableConfig = {}
      expect(() => buildTableColumns(mockQMSchema, config)).toThrow(
        'Must specify either visibleColumns or showAll=true'
      )
    })

    it('should return all columns when showAll is true', () => {
      const config: TableConfig = { showAll: true }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result).toHaveLength(4)
      expect(result[0].name).toBe('id')
      expect(result[1].name).toBe('name')
      expect(result[2].name).toBe('amount')
      expect(result[3].name).toBe('status')
    })

    it('should return columns in specified order', () => {
      const config: TableConfig = {
        visibleColumns: ['amount', 'name', 'id']
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result).toHaveLength(3)
      expect(result[0].name).toBe('amount')
      expect(result[1].name).toBe('name')
      expect(result[2].name).toBe('id')
    })

    it('should apply width customization', () => {
      const config: TableConfig = {
        visibleColumns: ['id', 'name'],
        customizations: [
          { name: 'id', width: 150 },
          { name: 'name', width: 200 }
        ]
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0].width).toBe(150)
      expect(result[1].width).toBe(200)
    })

    it('should apply minWidth customization with default fallback', () => {
      const config: TableConfig = {
        visibleColumns: ['id', 'name'],
        customizations: [
          { name: 'id', minWidth: 100 }
        ]
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0].minWidth).toBe(100)
      expect(result[1].minWidth).toBe(120) // default
    })

    it('should apply fixed column customization', () => {
      const config: TableConfig = {
        visibleColumns: ['id', 'name', 'amount'],
        customizations: [
          { name: 'id', fixed: 'left' },
          { name: 'amount', fixed: 'right' }
        ]
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0].fixed).toBe('left')
      expect(result[1].fixed).toBeUndefined()
      expect(result[2].fixed).toBe('right')
    })

    it('should apply formatter customization', () => {
      const formatter = (v: unknown) => `¥${v}`
      const config: TableConfig = {
        visibleColumns: ['amount'],
        customizations: [
          { name: 'amount', formatter }
        ]
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0].customFormatter).toBe(formatter)
    })

    it('should apply render customization', () => {
      const render = ({ value }: { value: unknown }) => String(value)
      const config: TableConfig = {
        visibleColumns: ['status'],
        customizations: [
          { name: 'status', render }
        ]
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0].customRender).toBe(render)
    })

    it('should apply filterComponent customization', () => {
      const filterComponent = {}
      const config: TableConfig = {
        visibleColumns: ['status'],
        customizations: [
          { name: 'status', filterComponent }
        ]
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0].customFilterComponent).toBe(filterComponent)
    })

    it('should warn when column not found in schema', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

      const config: TableConfig = {
        visibleColumns: ['id', 'nonexistent', 'name']
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result).toHaveLength(2)
      expect(result[0].name).toBe('id')
      expect(result[1].name).toBe('name')
      expect(consoleSpy).toHaveBeenCalledWith(
        'Column "nonexistent" not found in QM schema'
      )

      consoleSpy.mockRestore()
    })

    it('should handle empty visibleColumns', () => {
      const config: TableConfig = {
        visibleColumns: []
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result).toHaveLength(0)
    })

    it('should merge multiple customizations correctly', () => {
      const formatter = (v: unknown) => `¥${v}`
      const render = ({ value }: { value: unknown }) => String(value)

      const config: TableConfig = {
        visibleColumns: ['id', 'amount'],
        customizations: [
          {
            name: 'id',
            width: 150,
            minWidth: 100,
            fixed: 'left'
          },
          {
            name: 'amount',
            width: 120,
            formatter,
            render
          }
        ]
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0]).toMatchObject({
        name: 'id',
        width: 150,
        minWidth: 100,
        fixed: 'left'
      })

      expect(result[1]).toMatchObject({
        name: 'amount',
        width: 120
      })
      expect(result[1].customFormatter).toBe(formatter)
      expect(result[1].customRender).toBe(render)
    })

    it('should preserve original schema properties', () => {
      const config: TableConfig = {
        visibleColumns: ['id', 'amount']
      }
      const result = buildTableColumns(mockQMSchema, config)

      expect(result[0]).toMatchObject({
        name: 'id',
        type: 'INTEGER',
        title: 'ID'
      })

      expect(result[1]).toMatchObject({
        name: 'amount',
        type: 'MONEY',
        title: '金额'
      })
    })
  })
})
