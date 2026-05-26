import type { PivotRawPayload } from '@/types/pivot'

export const soaCandidatePivotRawFixture: PivotRawPayload = {
  viewMode: 'pivotTable',
  shape: 'grid',
  rowAxes: [
    { field: 'openingOrg', title: '开单组织', role: 'rowAxis' },
    {
      field: 'orderId',
      title: '运单',
      role: 'rowAxis',
      offset: 0,
      limit: 50,
      orderBy: ['-noPaidValue'],
      domainSliceEnabled: true
    }
  ],
  columnAxes: [
    { field: 'subjectCode', title: '科目', role: 'columnAxis' }
  ],
  metrics: [
    { field: 'noPaidValue', title: '未核销金额', aggregate: 'sum', format: 'money' },
    { field: 'paidValue', title: '已核销金额', aggregate: 'sum', format: 'money' }
  ],
  rowMembers: [
    {
      key: 'EO-001',
      values: {
        openingOrg: '华东',
        orderId: 'EO-001'
      }
    },
    {
      key: 'EO-002',
      values: {
        openingOrg: '华东',
        orderId: 'EO-002'
      }
    }
  ],
  columnMembers: [
    {
      key: 'subject_freight',
      title: '运输费',
      axisValue: 'FREIGHT',
      values: {
        subjectCode: 'FREIGHT'
      }
    },
    {
      key: 'subject_service',
      title: '服务费',
      axisValue: 'SERVICE',
      values: {
        subjectCode: 'SERVICE'
      }
    }
  ],
  cells: [
    { rowKey: 'EO-001', columnKey: 'subject_freight', metricField: 'noPaidValue', value: 1200 },
    { rowKey: 'EO-001', columnKey: 'subject_freight', metricField: 'paidValue', value: 0 },
    { rowKey: 'EO-001', columnKey: 'subject_service', metricField: 'noPaidValue', value: 0 },
    { rowKey: 'EO-001', columnKey: 'subject_service', metricField: 'paidValue', value: 600 },
    { rowKey: 'EO-002', columnKey: 'subject_freight', metricField: 'noPaidValue', value: 300 },
    { rowKey: 'EO-002', columnKey: 'subject_freight', metricField: 'paidValue', value: 100 },
    { rowKey: 'EO-002', columnKey: 'subject_service', metricField: 'noPaidValue', value: 80 },
    { rowKey: 'EO-002', columnKey: 'subject_service', metricField: 'paidValue', value: 0 }
  ],
  axisPages: {
    rows: [
      {
        field: 'orderId',
        offset: 0,
        limit: 50,
        total: 2,
        hasMore: false,
        pageScope: 'globalAxis'
      }
    ],
    columns: [
      {
        field: 'subjectCode',
        offset: 0,
        limit: 10,
        total: 2,
        hasMore: false,
        pageScope: 'globalAxis'
      }
    ]
  },
  evidence: {
    cellCalculationScope: 'global_slice_and_surviving_axes',
    domainSliceEnabled: true,
    rowAxisDomainSlice: [
      { field: 'noPaidValue', op: '>', value: 0 }
    ],
    globalSlice: [
      { field: 'rpDir', op: '=', value: 2 },
      { field: 'orderType', op: '=', value: 100 }
    ]
  }
}
