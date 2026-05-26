import { describe, expect, it } from 'vitest'
import { soaCandidatePivotRawFixture } from '@/__fixtures__/pivot/soaCandidatePivotRaw'
import { buildPivotCellField, toPivotViewModel } from './pivotViewModelAdapter'
import type { PivotRawPayload } from '@/types/pivot'

describe('pivotViewModelAdapter', () => {
  it('builds a PivotViewModel from raw axis members and cells', () => {
    const viewModel = toPivotViewModel(soaCandidatePivotRawFixture)

    expect(viewModel.viewMode).toBe('pivotTable')
    expect(viewModel.shape).toBe('grid')
    expect(viewModel.rowAxes).toEqual(soaCandidatePivotRawFixture.rowAxes)
    expect(viewModel.columnAxes).toEqual(soaCandidatePivotRawFixture.columnAxes)
    expect(viewModel.metrics).toEqual(soaCandidatePivotRawFixture.metrics)
    expect(viewModel.axisPages).toEqual(soaCandidatePivotRawFixture.axisPages)
    expect(viewModel.evidence).toEqual(soaCandidatePivotRawFixture.evidence)
  })

  it('builds row axis leaves and column member metric groups', () => {
    const viewModel = toPivotViewModel(soaCandidatePivotRawFixture)

    expect(viewModel.headerTree[0]).toMatchObject({
      field: 'openingOrg',
      title: '开单组织',
      role: 'rowAxis'
    })
    expect(viewModel.headerTree[2]).toMatchObject({
      key: 'subject_freight',
      title: '运输费',
      role: 'columnAxisMember',
      axisValue: 'FREIGHT'
    })
    expect(viewModel.headerTree[2].children).toEqual([
      {
        field: 'subject_freight__noPaidValue',
        title: '未核销金额',
        role: 'metric',
        metricField: 'noPaidValue'
      },
      {
        field: 'subject_freight__paidValue',
        title: '已核销金额',
        role: 'metric',
        metricField: 'paidValue'
      }
    ])
  })

  it('materializes flat grid items while preserving zero-valued cells', () => {
    const viewModel = toPivotViewModel(soaCandidatePivotRawFixture)

    expect(viewModel.items).toEqual([
      {
        openingOrg: '华东',
        orderId: 'EO-001',
        subject_freight__noPaidValue: 1200,
        subject_freight__paidValue: 0,
        subject_service__noPaidValue: 0,
        subject_service__paidValue: 600
      },
      {
        openingOrg: '华东',
        orderId: 'EO-002',
        subject_freight__noPaidValue: 300,
        subject_freight__paidValue: 100,
        subject_service__noPaidValue: 80,
        subject_service__paidValue: 0
      }
    ])
  })

  it('generates stable sanitized cell field keys', () => {
    expect(buildPivotCellField('subject freight', 'noPaidValue')).toBe('subject_freight__noPaidValue')
    expect(buildPivotCellField(' / ', 'paid value')).toBe('empty__paid_value')
  })

  it('rejects unsupported pivot shapes', () => {
    const payload: PivotRawPayload = {
      ...soaCandidatePivotRawFixture,
      shape: 'tree'
    }

    expect(() => toPivotViewModel(payload)).toThrow('Unsupported pivot shape "tree"')
  })

  it('rejects duplicate generated cell fields', () => {
    const payload: PivotRawPayload = {
      ...soaCandidatePivotRawFixture,
      columnMembers: [
        {
          key: 'subject freight',
          values: { subjectCode: 'FREIGHT' }
        },
        {
          key: 'subject/freight',
          values: { subjectCode: 'FREIGHT_DUP' }
        }
      ]
    }

    expect(() => toPivotViewModel(payload)).toThrow('Duplicate pivot cell field "subject_freight__noPaidValue"')
  })

  it('rejects cells that reference unknown row members', () => {
    const payload: PivotRawPayload = {
      ...soaCandidatePivotRawFixture,
      cells: [
        {
          rowKey: 'missing-row',
          columnKey: 'subject_freight',
          metricField: 'noPaidValue',
          value: 100
        }
      ]
    }

    expect(() => toPivotViewModel(payload)).toThrow('unknown row member "missing-row"')
  })

  it('rejects cells that reference unknown column members', () => {
    const payload: PivotRawPayload = {
      ...soaCandidatePivotRawFixture,
      cells: [
        {
          rowKey: 'EO-001',
          columnKey: 'missing-column',
          metricField: 'noPaidValue',
          value: 100
        }
      ]
    }

    expect(() => toPivotViewModel(payload)).toThrow('unknown column member "missing-column"')
  })

  it('rejects cells that reference unknown metrics', () => {
    const payload: PivotRawPayload = {
      ...soaCandidatePivotRawFixture,
      cells: [
        {
          rowKey: 'EO-001',
          columnKey: 'subject_freight',
          metricField: 'missingMetric',
          value: 100
        }
      ]
    }

    expect(() => toPivotViewModel(payload)).toThrow('unknown metric "missingMetric"')
  })
})
