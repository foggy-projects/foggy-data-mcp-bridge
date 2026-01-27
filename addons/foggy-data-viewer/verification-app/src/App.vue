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
  { name: 'orderDate', type: 'DAY', title: '下单日期', filterType: 'date', measure: false, aggregatable: false, filterable: true },
  { name: 'actions', type: 'TEXT', title: '操作', filterType: 'none', measure: false, aggregatable: false, filterable: false }
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
    visibleColumns: ['id', 'orderNo', 'customerName', 'amount', 'quantity', 'status', 'orderDate', 'actions'],
    customizations: [
      { name: 'id', width: 80, fixed: 'left' },
      { name: 'orderNo', width: 150 },
      { name: 'customerName', width: 150 },
      { name: 'amount', width: 120 },
      { name: 'quantity', width: 100 },
      { name: 'status', width: 100 },
      { name: 'orderDate', width: 120 },
      { name: 'actions', width: 200, fixed: 'right' }
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

// 截断信息
const truncationInfo = ref<any>(null)

// 测试大数据截断
function testLargeDataTruncation() {
  loading.value = true
  truncationInfo.value = null

  // 模拟 MCP 查询返回大数据并触发截断
  setTimeout(() => {
    // 模拟 150 行数据（实际上原始数据是 5000 行）
    const mockData = []
    const statusList = ['待支付', '已支付', '已发货', '已完成', '已取消']

    for (let i = 0; i < 100; i++) {
      mockData.push({
        id: i + 1,
        orderNo: `ORD-2024-${String(i + 1).padStart(6, '0')}`,
        customerName: `客户${i + 1}`,
        amount: Math.floor(Math.random() * 10000) + 100,
        quantity: Math.floor(Math.random() * 100) + 1,
        status: statusList[Math.floor(Math.random() * statusList.length)],
        orderDate: new Date(2024, 0, Math.floor(Math.random() * 365))
      })
    }

    data.value = mockData
    total.value = 5000 // 原始总数

    // 模拟截断信息（来自后端）
    truncationInfo.value = {
      truncated: true,
      originalRowCount: 5000,
      truncatedRowCount: 100,
      columnCount: 7,
      cellCount: 35000,
      message: '数据量较大（5000 行 × 7 列 = 35000 单元格），已自动截断为 100 行。',
      viewerUrl: 'http://localhost:8080/data-viewer/view/demo123456',
      apiUrl: 'http://localhost:8080/data-viewer/api/query/demo123456/data',
      hint: '您可以访问上述链接查看完整数据，或通过 API 分页获取（参数：start, limit）'
    }

    loading.value = false

    // 滚动到截断信息提示
    setTimeout(() => {
      document.querySelector('.truncation-alert')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 100)
  }, 800)
}

// 清除截断测试
function clearTruncationTest() {
  truncationInfo.value = null
  loadData(1, 50)
}

// 查看行详情（操作列按钮）
function viewRowDetails(row: any) {
  const details = Object.entries(row)
    .map(([key, value]) => {
      const field = qmSchema.find(f => f.name === key)
      const label = field?.title || key
      return `${label}: ${value}`
    })
    .join('\n')

  alert(`📋 订单详细信息\n\n${details}`)
}

// 编辑行（操作列按钮）
function editRow(row: any) {
  console.log('编辑行:', row)
  alert(`✏️ 编辑订单\n\n订单号: ${row.orderNo}\n客户: ${row.customerName}\n\n（这里可以打开编辑对话框）`)
}

// 删除行（操作列按钮）
function deleteRow(row: any) {
  if (confirm(`确定要删除订单 ${row.orderNo} 吗？`)) {
    console.log('删除行:', row)
    // 这里可以调用删除 API
    const index = data.value.findIndex(item => item.id === row.id)
    if (index !== -1) {
      data.value.splice(index, 1)
      total.value--
      alert('✅ 删除成功')
    }
  }
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
          <li>🆕 MCP 大数据自动截断（点击下方按钮测试）</li>
          <li>🎯 自定义操作列插槽（查看/编辑/删除按钮）</li>
        </ul>

        <div class="test-buttons">
          <button @click="testLargeDataTruncation" class="test-btn primary" :disabled="loading">
            🧪 测试大数据截断
          </button>
          <button @click="clearTruncationTest" class="test-btn" :disabled="!truncationInfo || loading">
            🔄 恢复正常数据
          </button>
        </div>
      </div>

      <!-- 截断信息提示 -->
      <div v-if="truncationInfo" class="truncation-alert">
        <div class="alert-header">
          <span class="alert-icon">⚠️</span>
          <h3>数据已自动截断</h3>
        </div>
        <div class="alert-body">
          <p class="message">{{ truncationInfo.message }}</p>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">原始行数:</span>
              <span class="value">{{ truncationInfo.originalRowCount.toLocaleString() }} 行</span>
            </div>
            <div class="info-item">
              <span class="label">截断后:</span>
              <span class="value">{{ truncationInfo.truncatedRowCount }} 行</span>
            </div>
            <div class="info-item">
              <span class="label">列数:</span>
              <span class="value">{{ truncationInfo.columnCount }} 列</span>
            </div>
            <div class="info-item">
              <span class="label">总单元格:</span>
              <span class="value">{{ truncationInfo.cellCount.toLocaleString() }} 个</span>
            </div>
          </div>
          <div class="links">
            <p class="hint">{{ truncationInfo.hint }}</p>
            <div class="link-buttons">
              <a :href="truncationInfo.viewerUrl" target="_blank" class="link-btn viewer">
                🔍 在浏览器中查看完整数据
              </a>
              <a :href="truncationInfo.apiUrl" target="_blank" class="link-btn api">
                📡 API 查询接口
              </a>
            </div>
          </div>
        </div>
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
        >
          <!-- 自定义操作列插槽 -->
          <template #column-actions="{ row }">
            <div class="action-buttons">
              <button @click.stop="viewRowDetails(row)" class="action-btn view" title="查看详情">
                <span class="icon">👁️</span>
                <span class="text">查看</span>
              </button>
              <button @click.stop="editRow(row)" class="action-btn edit" title="编辑">
                <span class="icon">✏️</span>
                <span class="text">编辑</span>
              </button>
              <button @click.stop="deleteRow(row)" class="action-btn delete" title="删除">
                <span class="icon">🗑️</span>
                <span class="text">删除</span>
              </button>
            </div>
          </template>
        </DataTable>
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

/* 测试按钮样式 */
.test-buttons {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e0e0e0;
  display: flex;
  gap: 12px;
}

.test-btn {
  padding: 10px 20px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: white;
  color: #606266;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.test-btn:hover:not(:disabled) {
  background: #f5f7fa;
  border-color: #c0c4cc;
}

.test-btn.primary {
  background: #409eff;
  color: white;
  border-color: #409eff;
}

.test-btn.primary:hover:not(:disabled) {
  background: #66b1ff;
  border-color: #66b1ff;
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 截断信息提示样式 */
.truncation-alert {
  background: #fff7e6;
  border: 2px solid #ffa940;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(255, 169, 64, 0.15);
  animation: slideIn 0.5s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.alert-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.alert-icon {
  font-size: 28px;
}

.alert-header h3 {
  color: #d46b08;
  margin: 0;
  font-size: 18px;
}

.alert-body .message {
  color: #d46b08;
  font-size: 15px;
  line-height: 1.6;
  margin-bottom: 16px;
  padding: 12px;
  background: white;
  border-radius: 4px;
  font-weight: 500;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.info-item {
  background: white;
  padding: 12px;
  border-radius: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-item .label {
  color: #8c8c8c;
  font-size: 13px;
}

.info-item .value {
  color: #262626;
  font-weight: 600;
  font-size: 14px;
}

.links {
  background: white;
  padding: 16px;
  border-radius: 4px;
}

.hint {
  color: #595959;
  font-size: 13px;
  margin-bottom: 12px;
  line-height: 1.6;
}

.link-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 4px;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s;
}

.link-btn.viewer {
  background: #1890ff;
  color: white;
}

.link-btn.viewer:hover {
  background: #40a9ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(24, 144, 255, 0.3);
}

.link-btn.api {
  background: #52c41a;
  color: white;
}

.link-btn.api:hover {
  background: #73d13d;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(82, 196, 26, 0.3);
}

/* 操作按钮样式 */
.action-buttons {
  display: flex;
  gap: 6px;
  justify-content: center;
  align-items: center;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
  white-space: nowrap;
}

.action-btn .icon {
  font-size: 14px;
  line-height: 1;
}

.action-btn .text {
  line-height: 1;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.action-btn.view {
  color: #1890ff;
  border-color: #91d5ff;
}

.action-btn.view:hover {
  background: #e6f7ff;
  border-color: #40a9ff;
}

.action-btn.edit {
  color: #52c41a;
  border-color: #b7eb8f;
}

.action-btn.edit:hover {
  background: #f6ffed;
  border-color: #73d13d;
}

.action-btn.delete {
  color: #ff4d4f;
  border-color: #ffccc7;
}

.action-btn.delete:hover {
  background: #fff1f0;
  border-color: #ff7875;
}
</style>
