<script setup lang="ts">
import { PivotRawViewer } from 'foggy-data-viewer'
import type { PivotRawPayload } from 'foggy-data-viewer'

const soaPivotPayload: PivotRawPayload = {
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
</script>

<template>
  <section class="pivot-demo" data-testid="pivot-demo-page">
    <header class="pivot-demo-summary">
      <div>
        <h3>TMS X6 SOA 应付核销候选透视表</h3>
        <p>ACCOUNT_RP_ITEM 按运单行轴、科目列轴展示，domainSlice 只选择轴域，cell 保留全局事实范围。</p>
      </div>
      <dl>
        <div>
          <dt>行轴</dt>
          <dd>openingOrg / orderId</dd>
        </div>
        <div>
          <dt>列轴</dt>
          <dd>subjectCode</dd>
        </div>
        <div>
          <dt>指标</dt>
          <dd>noPaidValue / paidValue</dd>
        </div>
      </dl>
    </header>

    <main class="pivot-demo-viewer" data-testid="pivot-demo-viewer">
      <PivotRawViewer
        :raw-payload="soaPivotPayload"
        height="430"
        empty-cell-text="-"
      />
    </main>
  </section>
</template>

<style scoped>
.pivot-demo {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}

.pivot-demo-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: start;
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.pivot-demo-summary h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.pivot-demo-summary p {
  max-width: 760px;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}

.pivot-demo-summary dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, max-content));
  gap: 12px;
  margin: 0;
}

.pivot-demo-summary dt {
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
}

.pivot-demo-summary dd {
  margin: 0;
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
}

.pivot-demo-viewer {
  min-width: 0;
  min-height: 0;
  padding: 12px;
  overflow: auto;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

@media (max-width: 900px) {
  .pivot-demo-summary {
    grid-template-columns: 1fr;
  }

  .pivot-demo-summary dl {
    grid-template-columns: 1fr;
  }
}
</style>
