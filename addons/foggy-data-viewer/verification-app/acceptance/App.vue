<script setup lang="ts">
import { ref } from 'vue'
import { DataTableWithSearch } from 'foggy-data-viewer'
import type { TableSchema, FetchDataParams, MemberQueryRequest } from 'foggy-data-viewer'
const userId = ref('acceptance-user-a')
const table = ref<any>()
const lastRequest = ref<unknown>()
const lastError = ref('')
const columns = [
  { name: 'orderNo', title: '运单号', type: 'TEXT', width: 160 },
  { name: 'status', title: '运单状态', type: 'TEXT', dictId: 'status', dictItems: [{ value: 'SIGNED', label: '已签收' }, { value: 'TRANSIT', label: '运输中' }] },
  { name: 'openingDate', title: '开单日期', type: 'DAY' },
  { name: 'openingTime', title: '开单时间', type: 'DATETIME' },
  { name: 'origin$caption', title: '始发站点', type: 'TEXT', memberLookup: { enabled: true, selectionFieldName: 'origin$id', displayFieldName: 'origin$caption' } },
  { name: 'destination$caption', title: '运达站点', type: 'TEXT', memberLookup: { enabled: true, selectionFieldName: 'destination$id', displayFieldName: 'destination$caption' } },
  { name: 'consignor', title: '发货人姓名', type: 'TEXT' },
  { name: 'consignee', title: '收货人姓名', type: 'TEXT' },
  { name: 'phone', title: '收货人电话', type: 'TEXT' },
  { name: 'goods', title: '货物名称', type: 'TEXT' },
  { name: 'payment', title: '付款方式', type: 'TEXT', dictId: 'payment', dictItems: [{ value: 'PREPAID', label: '寄付' }, { value: 'COLLECT', label: '到付' }] },
  { name: 'pieces', title: '件数', type: 'INTEGER' },
  { name: 'weight', title: '重量', type: 'NUMBER' },
  { name: 'customer', title: '客户名称', type: 'TEXT' },
  { name: 'pickupTime', title: '揽收时间', type: 'DATETIME' },
  { name: 'service', title: '运输产品', type: 'TEXT', dictItems: [{ value: 'STANDARD', label: '标准快运' }] },
  { name: 'customerOrderNo', title: '客户单号', type: 'TEXT' },
  ...Array.from({ length: 38 }, (_, i) => ({ name: `extension${i}`, title: `扩展字段 ${i + 1}`, type: 'TEXT' }))
]
const schema: TableSchema = { columns, pageSize: 20 }
const defaultQueryConfig = {
  defaultVisibleColumns: columns.slice(0, 17).map(column => column.name),
  defaultSlices: [], defaultOrderBy: [], defaultPageSize: 20
}
async function fetchData(params: FetchDataParams) {
  lastError.value = ''
  lastRequest.value = JSON.parse(JSON.stringify(params))
  const response = await fetch('/data-viewer/api/acceptance/query', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(params) })
  if (!response.ok) throw new Error('本地验收查询失败')
  return response.json()
}
async function memberLoader(request: MemberQueryRequest) {
  const response = await fetch('/data-viewer/api/acceptance/members', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) })
  return response.json()
}
</script>
<template>
  <header class="acceptance-banner"><strong>Foggy Data Viewer · 自定义查询验收</strong><span>本地模拟数据 / 隔离文件存储 / 不连接 TMS</span></header>
  <main class="acceptance-main">
    <div class="acceptance-heading"><div><h1>已签收运单列表</h1><p>验收原型三步流程、页面固定条件与个人方案</p></div><el-select v-model="userId" style="width: 180px" aria-label="验收用户"><el-option label="验收用户 A" value="acceptance-user-a" /><el-option label="验收用户 B" value="acceptance-user-b" /></el-select></div>
    <div class="acceptance-table">
      <DataTableWithSearch :key="userId" ref="table" :schema="schema" :fetch-data="fetchData" :default-query-config="defaultQueryConfig"
        :fixed-slice="[{ field: 'status', op: '=', value: 'SIGNED' }]" :required-runtime-columns="['runtimeId']"
        query-time-zone="Asia/Shanghai" :list-preset="{ userId, model: 'AcceptanceOrders', businessKey: 'signed-orders' }"
        qm-model="AcceptanceOrders" :filter-member-loader="memberLoader" @load-error="error => lastError = error.message" />
    </div>
    <el-alert v-if="lastError" :title="lastError" type="error" />
    <details class="acceptance-evidence"><summary>验收证据：最近一次请求</summary><pre data-testid="last-request">{{ JSON.stringify(lastRequest, null, 2) }}</pre></details>
  </main>
</template>
<style>
* { box-sizing: border-box; } body { margin: 0; background: #eef2f7; color: #172033; font-family: 'Microsoft YaHei UI', sans-serif; }
.acceptance-banner { height: 46px; background: #14233a; color: #fff; display: flex; align-items: center; justify-content: space-between; padding: 0 28px; font-size: 13px; }.acceptance-banner span { color: #a6bbd9; }
.acceptance-main { margin: 28px; }.acceptance-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 22px; }.acceptance-heading h1 { font-size: 24px; margin: 0; }.acceptance-heading p { color: #6b7689; font-size: 13px; }
.acceptance-table { height: 540px; padding: 16px; border: 1px solid #dbe2ed; border-radius: 12px; background: white; }.acceptance-evidence { margin-top: 18px; color: #53647b; font-size: 12px; }.acceptance-evidence pre { background: #fff; border: 1px solid #dbe2ed; padding: 16px; max-height: 260px; overflow: auto; }
</style>
