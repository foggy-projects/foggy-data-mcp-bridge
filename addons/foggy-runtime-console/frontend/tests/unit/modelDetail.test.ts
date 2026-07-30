import { describe, expect, it } from 'vitest'
import { normalizeModelDetail } from '@/features/models/modelDetail'

describe('structured model detail normalization', () => {
  it('normalizes canonical Runtime data into model, fields and physical tables', () => {
    const detail = normalizeModelDetail({
      data: {
        version: 'v3',
        models: {
          OrderModel: {
            name: '订单分析',
            factTable: 'orders',
            scenarios: ['趋势分析']
          }
        },
        fields: {
          customer: {
            name: '客户',
            fieldName: 'customer',
            type: 'TEXT',
            measure: false,
            sourceColumn: 'customer_name',
            models: {
              OrderModel: {
                description: '客户名称',
                usage: '用于分组'
              }
            }
          },
          amount: {
            name: '金额',
            fieldName: 'amount',
            type: 'MONEY',
            measure: true,
            aggregation: 'SUM'
          }
        },
        physicalTables: [{ table: 'orders', role: 'fact' }],
        modelSource: { known: true, bundleName: 'sales' }
      }
    }, 'OrderModel')

    expect(detail.modelInfo).toMatchObject({ name: '订单分析', factTable: 'orders' })
    expect(detail.fields).toEqual([
      expect.objectContaining({
        name: 'customer',
        kind: 'dimension',
        description: '客户名称',
        usage: '用于分组'
      }),
      expect.objectContaining({
        name: 'amount',
        kind: 'measure',
        aggregation: 'SUM'
      })
    ])
    expect(detail.physicalTables).toEqual([{ table: 'orders', role: 'fact' }])
    expect(detail.hasStructuredContent).toBe(true)
  })

  it('supports JSON content and explicit calculated fields', () => {
    const detail = normalizeModelDetail({
      content: JSON.stringify({
        fields: [{
          fieldName: 'margin',
          name: '毛利',
          type: 'NUMBER',
          calculated: true,
          description: '收入减成本'
        }],
        examples: [{ columns: ['margin'] }]
      })
    }, 'FinanceModel')

    expect(detail.fields[0]).toMatchObject({
      name: 'margin',
      kind: 'calculated',
      description: '收入减成本'
    })
    expect(detail.examples).toHaveLength(1)
    expect(detail.rawText).toContain('"margin"')
  })

  it('keeps malformed or empty content safe and diagnosable', () => {
    const malformed = normalizeModelDetail({ content: '{not-json' }, 'OrderModel')
    const empty = normalizeModelDetail({}, 'OrderModel')

    expect(malformed.hasStructuredContent).toBe(false)
    expect(malformed.rawText).toBe('{not-json')
    expect(empty.hasStructuredContent).toBe(false)
    expect(empty.fields).toEqual([])
  })
})
