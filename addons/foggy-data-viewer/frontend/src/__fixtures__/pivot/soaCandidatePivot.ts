import type { PivotViewModel } from '@/types/pivot'

export const soaCandidatePivotFixture: PivotViewModel = {
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
  headerTree: [
    { field: 'openingOrg', title: '开单组织', role: 'rowAxis', width: 140 },
    { field: 'orderId', title: '运单', role: 'rowAxis', width: 160 },
    {
      key: 'subject_freight',
      title: '运输费',
      role: 'columnAxisMember',
      axisValue: 'FREIGHT',
      children: [
        {
          field: 'subject_freight__noPaidValue',
          title: '未核销金额',
          role: 'metric',
          metricField: 'noPaidValue',
          width: 130
        },
        {
          field: 'subject_freight__paidValue',
          title: '已核销金额',
          role: 'metric',
          metricField: 'paidValue',
          width: 130
        }
      ]
    },
    {
      key: 'subject_service',
      title: '服务费',
      role: 'columnAxisMember',
      axisValue: 'SERVICE',
      children: [
        {
          field: 'subject_service__noPaidValue',
          title: '未核销金额',
          role: 'metric',
          metricField: 'noPaidValue',
          width: 130
        },
        {
          field: 'subject_service__paidValue',
          title: '已核销金额',
          role: 'metric',
          metricField: 'paidValue',
          width: 130
        }
      ]
    }
  ],
  items: [
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
