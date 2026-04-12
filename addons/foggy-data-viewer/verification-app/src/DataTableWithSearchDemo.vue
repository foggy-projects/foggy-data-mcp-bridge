<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { DataTableWithSearch, buildTableColumns } from 'foggy-data-viewer'
import type { EnhancedColumnSchema, SliceRequestDef } from 'foggy-data-viewer'

// 模拟 QM Schema
const qmSchema = [
  { name: 'id', type: 'INTEGER', title: 'ID', filterType: 'number', filterable: true },
  { name: 'orderNo', type: 'TEXT', title: '订单号', filterType: 'text', filterable: true },
  { name: 'customerName', type: 'TEXT', title: '客户名称', filterType: 'text', filterable: true },
  { name: 'amount', type: 'MONEY', title: '订单金额', filterType: 'number', measure: true, aggregatable: true, filterable: true },
  { name: 'quantity', type: 'INTEGER', title: '数量', filterType: 'number', measure: true, aggregatable: true, filterable: true },
  { name: 'status', type: 'TEXT', title: '状态', filterType: 'text', filterable: true },
  { name: 'orderDate', type: 'DAY', title: '下单日期', filterType: 'date', filterable: true }
]

const columns = ref<EnhancedColumnSchema[]>([])
const data = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

const serverSummary = ref({
  total: 150,
  amount: 1500000,
  quantity: 5000
})

// 选中行计数（响应式）
const selectedCount = ref(0)
const tableRef = ref<InstanceType<typeof DataTableWithSearch> | null>(null)

function refreshSelectedCount() {
  selectedCount.value = tableRef.value?.getSelectedCount?.() ?? 0
}

function handleBatchAction() {
  const rows = tableRef.value?.getSelectedRows?.() ?? []
  console.log('批量操作选中行:', rows)
  alert(`选中了 ${rows.length} 条记录`)
}

function handleEdit(row: any) {
  console.log('编辑行:', row)
  alert(`编辑订单: ${row.orderNo}`)
}

function handleDelete(row: any) {
  console.log('删除行:', row)
  alert(`删除订单: ${row.orderNo}`)
}

onMounted(() => {
  columns.value = buildTableColumns(qmSchema, {
    visibleColumns: ['id', 'orderNo', 'customerName', 'amount', 'quantity', 'status', 'orderDate'],
    customizations: [
      { name: 'id', width: 80, fixed: 'left' },
      { name: 'orderNo', width: 150 },
      { name: 'customerName', width: 150 },
      { name: 'amount', width: 120 },
      { name: 'quantity', width: 100 },
      { name: 'status', width: 100 },
      { name: 'orderDate', width: 120 }
    ]
  })

  loadData(1, 50)
})

function loadData(page: number, pageSize: number, slices?: SliceRequestDef[]) {
  loading.value = true

  setTimeout(() => {
    const mockData = []
    const statusList = ['待支付', '已支付', '已发货', '已完成', '已取消']

    for (let i = 0; i < pageSize; i++) {
      const index = (page - 1) * pageSize + i + 1
      if (index > 150) break

      mockData.push({
        id: index,
        orderNo: `ORD-2024-${String(index).padStart(6, '0')}`,
        customerName: `客户${index}`,
        amount: Math.floor(Math.random() * 10000) + 100,
        quantity: Math.floor(Math.random() * 100) + 1,
        status: statusList[Math.floor(Math.random() * statusList.length)],
        orderDate: new Date(2024, 0, Math.floor(Math.random() * 365))
      })
    }

    data.value = mockData
    total.value = 150
    loading.value = false

    if (slices && slices.length > 0) {
      console.log('应用筛选条件:', slices)
    }
  }, 500)
}

function handlePageChange(page: number, pageSize: number) {
  console.log('分页变化:', page, pageSize)
  loadData(page, pageSize)
}

function handleFilterChange(slices: SliceRequestDef[]) {
  console.log('筛选条件变化（合并后）:', slices)
  loadData(1, 50, slices)
}

function handleSearch(slices: SliceRequestDef[]) {
  console.log('搜索按钮点击:', slices)
}

function handleReset() {
  console.log('重置按钮点击')
  loadData(1, 50)
}
</script>

<template>
  <div class="demo-page">
    <div class="header">
      <h1>DataTableWithSearch 组合组件示例</h1>
      <p>验证项：toolbar slot / row-actions / 批量选择并读取选中行 / 高度自适应</p>
    </div>

    <div class="content">
      <DataTableWithSearch
        ref="tableRef"
        :columns="columns"
        :data="data"
        :total="total"
        :loading="loading"
        :page-size="50"
        :show-filters="true"
        :show-search-toolbar="true"
        :searchable-fields="['customerName', 'orderDate', 'amount']"
        :search-layout="'horizontal'"
        :server-summary="serverSummary"
        @page-change="handlePageChange"
        @filter-change="handleFilterChange"
        @search="handleSearch"
        @reset="handleReset"
        @checkbox-change="refreshSelectedCount"
        @checkbox-all="refreshSelectedCount"
      >
        <!-- toolbar slot 验证 -->
        <template #toolbar>
          <button @click="handleBatchAction" :disabled="selectedCount === 0" style="padding: 4px 12px; cursor: pointer;">
            批量操作 ({{ selectedCount }})
          </button>
        </template>

        <!-- row-actions slot 验证 -->
        <template #row-actions="{ row }">
          <div style="display: flex; gap: 4px;">
            <button @click="handleEdit(row)" style="padding: 2px 8px; font-size: 12px; cursor: pointer;">编辑</button>
            <button @click="handleDelete(row)" style="padding: 2px 8px; font-size: 12px; cursor: pointer; color: red;">删除</button>
          </div>
        </template>
      </DataTableWithSearch>
    </div>
  </div>
</template>

<style scoped>
.demo-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding: 20px;
}

.header {
  text-align: center;
  margin-bottom: 30px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.header h1 {
  color: #2c3e50;
  margin-bottom: 10px;
  margin-top: 0;
}

.header p {
  color: #7f8c8d;
  font-size: 14px;
  margin: 0;
}

.content {
  max-width: 1400px;
  margin: 0 auto;
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  height: 800px;
  display: flex;
  flex-direction: column;
}
</style>
