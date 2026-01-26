<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { DataTable, SearchToolbar, buildTableColumns } from 'foggy-data-viewer'
import type { EnhancedColumnSchema, SliceRequestDef } from 'foggy-data-viewer'

// 模拟 QM Schema（从服务器获取）
const qmSchema = [
  { name: 'id', type: 'INTEGER', title: 'ID', filterType: 'number', measure: false, aggregatable: false, filterable: true },
  { name: 'orderNo', type: 'TEXT', title: '订单号', filterType: 'text', measure: false, aggregatable: false, filterable: true },
  { name: 'customerName', type: 'TEXT', title: '客户名称', filterType: 'text', measure: false, aggregatable: false, filterable: true },
  { name: 'amount', type: 'MONEY', title: '订单金额', filterType: 'number', measure: true, aggregatable: true, filterable: true },
  { name: 'quantity', type: 'INTEGER', title: '数量', filterType: 'number', measure: true, aggregatable: true, filterable: true },
  { name: 'status', type: 'TEXT', title: '状态', filterType: 'text', measure: false, aggregatable: false, filterable: true },
  { name: 'orderDate', type: 'DAY', title: '下单日期', filterType: 'date', measure: false, aggregatable: false, filterable: true }
]

// 使用 buildTableColumns 构建列配置
const columns = ref<EnhancedColumnSchema[]>([])
const data = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

// SearchToolbar 的筛选条件
const searchSlices = ref<SliceRequestDef[]>([])

// 模拟从服务器获取的汇总数据
const serverSummary = ref({
  total: 150,
  amount: 1500000,
  quantity: 5000
})

onMounted(() => {
  // 构建表格列
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

  // 加载初始数据
  loadData(1, 50)
})

// 模拟加载数据
function loadData(page: number, pageSize: number, slices?: SliceRequestDef[]) {
  loading.value = true

  // 模拟异步加载
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

    // 这里可以根据 slices 筛选数据
    if (slices && slices.length > 0) {
      console.log('应用筛选条件:', slices)
    }
  }, 500)
}

// 处理分页变化
function handlePageChange(page: number, pageSize: number) {
  console.log('分页变化:', page, pageSize)
  loadData(page, pageSize, searchSlices.value)
}

// 处理排序变化
function handleSortChange(field: string | null, order: string | null) {
  console.log('排序变化:', field, order)
  // 这里可以重新加载数据，传递排序参数到后端
}

// 处理表头筛选变化
function handleFilterChange(slices: any[]) {
  console.log('表头筛选变化:', slices)
  // 这里可以重新加载数据，传递筛选条件到后端
}

// 处理搜索工具栏筛选变化
function handleSearchChange(slices: SliceRequestDef[]) {
  console.log('搜索工具栏筛选变化:', slices)
  searchSlices.value = slices
  // 实时搜索：立即重新加载数据
  loadData(1, 50, slices)
}

// 处理搜索按钮点击
function handleSearch() {
  console.log('点击搜索按钮')
  loadData(1, 50, searchSlices.value)
}

// 处理重置按钮点击
function handleReset() {
  console.log('点击重置按钮')
  searchSlices.value = []
  loadData(1, 50)
}

// 处理行点击
function handleRowClick(row: any) {
  console.log('点击行:', row)
}

// 处理行双击
function handleRowDblClick(row: any) {
  console.log('双击行:', row)
  alert(`订单详情：\n订单号: ${row.orderNo}\n客户: ${row.customerName}\n金额: ${row.amount}`)
}
</script>

<template>
  <div class="app">
    <div class="header">
      <h1>Foggy Data Viewer - DataTable 验证</h1>
      <p>这是一个独立的验证项目，用于测试 DataTable 组件的功能</p>
    </div>

    <div class="content">
      <div class="info-panel">
        <h3>功能说明</h3>
        <ul>
          <li>✅ 使用 <code>file:</code> 协议引用本地未发布的组件包</li>
          <li>✅ 支持列配置、固定列、自定义宽度</li>
          <li>✅ 支持分页（点击分页器测试）</li>
          <li>✅ 支持排序（点击列头测试）</li>
          <li>✅ 支持筛选（打开筛选面板测试）</li>
          <li>✅ 显示汇总行（选中行查看）</li>
          <li>✅ 行点击/双击事件</li>
          <li>🆕 独立的 SearchToolbar 组件（快速筛选）</li>
        </ul>
      </div>

      <!-- SearchToolbar 独立使用示例 -->
      <div class="search-panel">
        <h3>搜索工具栏（独立使用）</h3>
        <SearchToolbar
          :columns="columns"
          :searchable-fields="['customerName', 'orderDate', 'amount']"
          v-model="searchSlices"
          @update:model-value="handleSearchChange"
          @search="handleSearch"
          @reset="handleReset"
        />
      </div>

      <div class="table-container">
        <DataTable
          :columns="columns"
          :data="data"
          :total="total"
          :loading="loading"
          :page-size="50"
          :show-filters="true"
          :server-summary="serverSummary"
          @page-change="handlePageChange"
          @sort-change="handleSortChange"
          @filter-change="handleFilterChange"
          @row-click="handleRowClick"
          @row-dblclick="handleRowDblClick"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.app {
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
}

.header p {
  color: #7f8c8d;
  font-size: 14px;
}

.content {
  max-width: 1400px;
  margin: 0 auto;
}

.info-panel {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.search-panel {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.search-panel h3 {
  color: #2c3e50;
  margin-bottom: 15px;
  margin-top: 0;
}

.info-panel h3 {
  color: #2c3e50;
  margin-bottom: 15px;
}

.info-panel ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.info-panel li {
  padding: 8px 0;
  color: #34495e;
  font-size: 14px;
}

.info-panel code {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #e74c3c;
}

.table-container {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  height: 800px;
  display: flex;
  flex-direction: column;
}

.table-container :deep(.data-table) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.table-container :deep(.table-wrapper) {
  flex: 1;
  overflow: auto;
}

.table-container :deep(.vxe-table) {
  height: 100% !important;
}
</style>
