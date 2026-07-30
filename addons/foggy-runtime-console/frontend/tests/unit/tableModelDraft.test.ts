import { describe, expect, it } from 'vitest'
import {
  buildSelectStatement,
  buildTableModelDraft,
  foggyPropertyType
} from '@/features/tables/tableModelDraft'

describe('Tables to SQL and TM draft helpers', () => {
  it('builds a read-only SELECT only for safe identifiers', () => {
    expect(buildSelectStatement('finance', 'invoices')).toBe('SELECT *\nFROM finance.invoices')
    expect(buildSelectStatement('', 'health')).toBe('SELECT *\nFROM health')
    expect(() => buildSelectStatement('public', 'orders; DROP TABLE users'))
      .toThrow('表名包含无法安全生成 SQL')
    expect(() => buildSelectStatement('../escape', 'orders'))
      .toThrow('Schema包含无法安全生成 SQL')
  })

  it('maps JDBC types conservatively', () => {
    expect(foggyPropertyType('BIGINT')).toBe('LONG')
    expect(foggyPropertyType('INTEGER')).toBe('INTEGER')
    expect(foggyPropertyType('DECIMAL(18,2)')).toBe('NUMBER')
    expect(foggyPropertyType('TIMESTAMP WITH TIME ZONE')).toBe('DATETIME')
    expect(foggyPropertyType('BOOLEAN')).toBe('BOOL')
    expect(foggyPropertyType('JSONB')).toBe('STRING')
  })

  it('creates a mechanical TM draft without inferring semantic roles', () => {
    const draft = buildTableModelDraft({
      dataSource: 'analytics',
      schema: 'finance',
      table: 'invoice_lines',
      primaryKeyColumns: ['invoice_id'],
      columns: [
        { name: 'invoice_id', jdbcType: 'BIGINT', nullable: false },
        { name: 'amount', jdbcType: 'DECIMAL', nullable: true }
      ]
    })

    expect(draft.modelName).toBe('InvoiceLinesModel')
    expect(draft.filename).toBe('InvoiceLinesModel.tm')
    expect(draft.content).toContain("tableName: 'invoice_lines'")
    expect(draft.content).toContain("idColumn: 'invoice_id'")
    expect(draft.content).toContain("type: 'LONG'")
    expect(draft.content).toContain('dimensions: []')
    expect(draft.content).toContain('measures: []')
    expect(draft.content).toContain('not validated, saved, registered, or refreshed')
  })
})
