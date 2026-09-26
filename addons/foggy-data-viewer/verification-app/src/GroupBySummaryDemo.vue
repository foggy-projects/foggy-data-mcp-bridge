<script setup lang="ts">
import { ref } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { DataTableWithSearch, fetchQueryDataDirect } from 'foggy-data-viewer'
import type { DataTableWithSearchRef, FetchDataParams, FetchDataResult, GroupRequestDef, TableSchema } from 'foggy-data-viewer'

const model = 'FactSalesQueryModel'
const groupBy: GroupRequestDef[] = [
  { field: 'salesDate$year' },
  { field: 'salesDate$month' },
  { field: 'store$caption' }
]
const schema: TableSchema = {
  pageSize: 20,
  columns: [
    { name: 'salesDate$year', title: '销售年份', type: 'TEXT', filterType: 'number', filterable: true },
    { name: 'salesDate$month', title: '销售月份', type: 'INTEGER', filterType: 'number', filterable: true },
    { name: 'store$caption', title: '门店', type: 'TEXT', filterType: 'text', filterable: true },
    { name: 'totalAmount', title: '销售收入', type: 'MONEY', filterType: 'number', filterable: true, measure: true },
    { name: 'orderCount', title: '销售记录数', type: 'INTEGER', filterType: 'number', filterable: true, measure: true }
  ]
}
const table = ref<DataTableWithSearchRef>()
const lastRequest = ref<FetchDataParams>()
const lastError = ref('')
const exportStatus = ref('')
const exporting = ref(false)

async function fetchData(params: FetchDataParams): Promise<FetchDataResult> {
  lastRequest.value = structuredClone(params)
  const columns = params.columns.map(column => {
    if (column === 'totalAmount') return 'sum(salesAmount) as totalAmount'
    if (column === 'orderCount') return 'count(orderId) as orderCount'
    return column
  })
  const result = await fetchQueryDataDirect(model, {
    start: (params.page - 1) * params.pageSize,
    limit: params.pageSize,
    columns,
    slice: params.slice,
    having: params.having,
    groupBy: params.groupBy,
    returnTotal: params.returnTotal,
    orderBy: params.orderBy
  })
  return result as FetchDataResult
}

async function exportCsv() {
  if (exporting.value || !table.value) return
  exporting.value = true
  lastError.value = ''
  exportStatus.value = '正在导出…'
  try {
    const rows: Record<string, unknown>[] = []
    let page = 1
    while (true) {
      const result = await table.value.executeQuery({ page, pageSize: 100 }, { trigger: 'export' })
      if (!result || typeof result.hasNext !== 'boolean') throw new Error('导出查询缺少 hasNext')
      rows.push(...result.items)
      if (rows.length > 10000) throw new Error('导出超过 10000 行上限')
      if (!result.hasNext) break
      if (result.items.length === 0) throw new Error('导出分页返回空页')
      page += 1
    }
    const columns = schema.columns.map(column => column.name)
    const csvCell = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`
    const csv = [columns.join(','), ...rows.map(row => columns.map(column => csvCell(row[column])).join(','))].join('\r\n')
    const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = 'group-by-summary.csv'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    exportStatus.value = `已导出 ${rows.length} 个分组`
    ElMessage.success(exportStatus.value)
  } catch (error) {
    lastError.value = error instanceof Error ? error.message : String(error)
    exportStatus.value = '导出失败'
    ElMessage.error(lastError.value)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <section class="group-summary-demo">
    <div class="table-container combined group-summary-table">
      <DataTableWithSearch
        ref="table"
        :schema="schema"
        :fetch-data="fetchData"
        table-mode="groupBy"
        :group-by="groupBy"
        :show-search-actions="false"
        @load-error="(error: Error) => lastError = error.message"
      >
        <template #toolbar-right>
          <el-tooltip :content="exporting ? '导出中…' : '导出 CSV'" placement="top">
            <el-button
              circle
              :icon="Download"
              :loading="exporting"
              :disabled="exporting"
              :aria-label="exporting ? '导出中' : '导出 CSV'"
              @click="exportCsv"
            />
          </el-tooltip>
          <span class="group-summary-export-status" role="status">{{ exportStatus }}</span>
        </template>
      </DataTableWithSearch>
    </div>
    <p v-if="lastError" role="alert">{{ lastError }}</p>
    <details class="group-summary-request">
      <summary>最近一次查询请求</summary>
      <pre data-testid="group-by-last-request">{{ JSON.stringify(lastRequest, null, 2) }}</pre>
    </details>
  </section>
</template>

<style scoped>
.group-summary-demo { display: flex; flex: 1; flex-direction: column; gap: 12px; min-height: 0; }
.group-summary-table { min-height: 480px; }
.group-summary-export-status { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.group-summary-request pre { max-height: 220px; overflow: auto; }
</style>
