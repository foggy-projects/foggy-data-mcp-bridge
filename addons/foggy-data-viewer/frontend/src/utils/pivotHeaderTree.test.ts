import { describe, expect, it } from 'vitest'
import { soaCandidatePivotFixture } from '@/__fixtures__/pivot/soaCandidatePivot'
import type { PivotHeaderNode } from '@/types/pivot'
import {
  buildPivotGridColumns,
  flattenPivotLeafNodes,
  validatePivotHeaderTree
} from './pivotHeaderTree'

describe('pivotHeaderTree', () => {
  it('validates the SOA candidate pivot fixture', () => {
    const result = validatePivotHeaderTree(soaCandidatePivotFixture.headerTree)

    expect(result.valid).toBe(true)
    expect(result.errors).toEqual([])
  })

  it('flattens only leaf nodes in display order', () => {
    const leaves = flattenPivotLeafNodes(soaCandidatePivotFixture.headerTree)

    expect(leaves.map(node => node.field)).toEqual([
      'openingOrg',
      'orderId',
      'subject_freight__noPaidValue',
      'subject_freight__paidValue',
      'subject_service__noPaidValue',
      'subject_service__paidValue'
    ])
  })

  it('builds grouped grid columns for column axis members and metrics', () => {
    const columns = buildPivotGridColumns(soaCandidatePivotFixture.headerTree)

    expect(columns).toHaveLength(4)
    expect(columns[0]).toMatchObject({
      field: 'openingOrg',
      title: '开单组织',
      fixed: 'left',
      meta: { role: 'rowAxis' }
    })
    expect(columns[1]).toMatchObject({
      field: 'orderId',
      title: '运单',
      fixed: 'left',
      meta: { role: 'rowAxis' }
    })
    expect(columns[2]).toMatchObject({
      title: '运输费',
      meta: { role: 'columnAxisMember', key: 'subject_freight' }
    })
    expect(columns[2].children?.map(child => child.field)).toEqual([
      'subject_freight__noPaidValue',
      'subject_freight__paidValue'
    ])
    expect(columns[2].children?.[0]).toMatchObject({
      title: '未核销金额',
      align: 'right',
      headerAlign: 'center',
      meta: { role: 'metric', metricField: 'noPaidValue' }
    })
  })

  it('does not turn axis page metadata into grid columns', () => {
    const leaves = flattenPivotLeafNodes(soaCandidatePivotFixture.headerTree)
    const leafFields = leaves.map(node => node.field)

    expect(leafFields).not.toContain('subjectCode')
    expect(leafFields).not.toContain('axisPages')
  })

  it('rejects duplicate leaf fields', () => {
    const tree: PivotHeaderNode[] = [
      { field: 'orderId', title: '运单', role: 'rowAxis' },
      { field: 'orderId', title: '重复运单', role: 'rowAxis' }
    ]

    const result = validatePivotHeaderTree(tree)

    expect(result.valid).toBe(false)
    expect(result.errors).toContain('headerTree[1]: duplicate leaf field "orderId"')
    expect(() => buildPivotGridColumns(tree)).toThrow(/duplicate leaf field/)
  })

  it('rejects group nodes with empty children', () => {
    const tree: PivotHeaderNode[] = [
      {
        key: 'subject_empty',
        title: '空科目',
        role: 'columnAxisMember',
        children: []
      }
    ]

    const result = validatePivotHeaderTree(tree)

    expect(result.valid).toBe(false)
    expect(result.errors).toContain('headerTree[0]: children must contain at least one node')
  })

  it('rejects leaf nodes without a field', () => {
    const tree: PivotHeaderNode[] = [
      { title: '缺字段', role: 'metric' }
    ]

    const result = validatePivotHeaderTree(tree)

    expect(result.valid).toBe(false)
    expect(result.errors).toContain('headerTree[0]: leaf node field is required')
  })

  it('allows row axis fixed default to be disabled', () => {
    const columns = buildPivotGridColumns(soaCandidatePivotFixture.headerTree, {
      defaultRowAxisFixed: false
    })

    expect(columns[0].fixed).toBeUndefined()
    expect(columns[1].fixed).toBeUndefined()
  })
})
