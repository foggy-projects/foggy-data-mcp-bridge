<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import {
  DataViewer,
  DataTable,
  SearchToolbar,
  DataTableWithSearch,
  buildTableColumns,
  createQuery,
  fetchQueryMeta,
  fetchQueryData,
  fetchQmSchema
} from 'foggy-data-viewer'
import type {
  EnhancedColumnSchema,
  SliceRequestDef,
  ColumnSchema,
  CreateQueryRequest,
  ViewerQueryRequest,
  QueryMetaResponse
} from 'foggy-data-viewer'

// ============ 场景切换 ============
type SceneType = 'home' | 'dataviewer' | 'toolbar' | 'custom-actions' | 'combined'
const currentScene = ref<SceneType>('home')

// ============ DataViewer 场景 ============
const queryId = ref<string | null>(null)
const dslInput = ref('')
const isSubmitting = ref(false)
const errorMessage = ref('')

const examples = [
  {
    name: '销售明细',
    model: 'FactSalesQueryModel',
    payload: {
      columns: ['orderId', 'salesDate$caption', 'product$caption', 'customer$caption', 'quantity', 'salesAmount', 'profitAmount'],
      slice: [
        { field: 'salesDate$caption', op: '>=', value: '2024-12-01' },
        { field: 'salesDate$caption', op: '<', value: '2024-12-31' }
      ],
      orderBy: [{ field: 'salesDate$caption', order: 'desc' }]
    }
  },
  {
    name: '商品列表',
    model: 'DimProductQueryModel',
    payload: {
      columns: ['productName', 'productId', 'brand', 'categoryName', 'subCategoryName', 'unitPrice', 'unitCost'],
      slice: [],
      orderBy: [{ field: 'productName', order: 'asc' }]
    }
  },
  {
    name: '客户列表',
    model: 'DimCustomerQueryModel',
    payload: {
      columns: ['customerName', 'customerId', 'customerType', 'memberLevel', 'gender', 'province', 'city'],
      slice: [],
      orderBy: [{ field: 'customerName', order: 'asc' }]
    }
  }
]

function selectExample(example: typeof examples[0]) {
  dslInput.value = JSON.stringify({ model: example.model, title: example.name, payload: example.payload }, null, 2)
  errorMessage.value = ''
}

async function submitQuery() {
  errorMessage.value = ''
  if (!dslInput.value.trim()) {
    errorMessage.value = '请输入查询 DSL'
    return
  }

  let request: CreateQueryRequest
  try {
    request = JSON.parse(dslInput.value)
  } catch (e) {
    errorMessage.value = 'JSON 格式错误: ' + (e as Error).message
    return
  }

  isSubmitting.value = true
  try {
    const response = await createQuery(request)
    if (response.success && response.queryId) {
      queryId.value = response.queryId
    } else {
      errorMessage.value = response.error || '创建查询失败'
    }
  } catch (e) {
    errorMessage.value = '请求失败: ' + (e as Error).message
  } finally {
    isSubmitting.value = false
  }
}

// ============ Toolbar 场景 ============
const toolbarQueryId = ref<string | null>(null)
const toolbarMeta = ref<QueryMetaResponse | null>(null)
const toolbarColumns = ref<EnhancedColumnSchema[]>([])
const toolbarData = ref<Record<string, unknown>[]>([])
const toolbarTotal = ref(0)
const toolbarLoading = ref(false)
const toolbarSlices = ref<SliceRequestDef[]>([])
const toolbarServerSummary = ref<Record<string, unknown> | null>(null)

async function initToolbarScene() {
  toolbarLoading.value = true
  try {
    // 创建商品列表查询
    const response = await createQuery({
      model: 'DimProductQueryModel',
      title: 'Toolbar 测试 - 商品列表',
      payload: {
        columns: ['productName', 'productId', 'brand', 'categoryName', 'subCategoryName', 'unitPrice', 'unitCost', 'status'],
        slice: [],
        orderBy: [{ field: 'productName', order: 'asc' }]
      }
    })

    if (response.success && response.queryId) {
      toolbarQueryId.value = response.queryId

      // 获取元数据
      toolbarMeta.value = await fetchQueryMeta(response.queryId)

      // 获取 schema 并构建列
      if (toolbarMeta.value?.tableConfig?.qmModel) {
        const schema = await fetchQmSchema(toolbarMeta.value.tableConfig.qmModel)
        toolbarColumns.value = buildTableColumns(schema, toolbarMeta.value.tableConfig)
      }

      // 加载数据
      await loadToolbarData()
    }
  } catch (e) {
    console.error('初始化 Toolbar 场景失败:', e)
  } finally {
    toolbarLoading.value = false
  }
}

async function loadToolbarData(start = 0, limit = 50) {
  if (!toolbarQueryId.value) return

  toolbarLoading.value = true
  try {
    const response = await fetchQueryData(toolbarQueryId.value, {
      start,
      limit,
      slice: toolbarSlices.value,
      orderBy: []
    })

    if (response.success) {
      toolbarData.value = response.items
      toolbarTotal.value = response.total
      toolbarServerSummary.value = response.totalData ?? null
    }
  } catch (e) {
    console.error('加载数据失败:', e)
  } finally {
    toolbarLoading.value = false
  }
}

function handleToolbarSearch() {
  // toolbarSlices 已通过 v-model 绑定自动更新
  loadToolbarData()
}

function handleToolbarReset() {
  toolbarSlices.value = []
  loadToolbarData()
}

function handleToolbarPageChange(page: number, size: number) {
  loadToolbarData((page - 1) * size, size)
}

// ============ 自定义操作列场景 ============
const actionsQueryId = ref<string | null>(null)
const actionsMeta = ref<QueryMetaResponse | null>(null)
const actionsColumns = ref<EnhancedColumnSchema[]>([])
const actionsData = ref<Record<string, unknown>[]>([])
const actionsTotal = ref(0)
const actionsLoading = ref(false)
const actionsServerSummary = ref<Record<string, unknown> | null>(null)

async function initActionsScene() {
  actionsLoading.value = true
  try {
    // 创建订单查询
    const response = await createQuery({
      model: 'FactOrderQueryModel',
      title: '自定义操作列测试 - 订单列表',
      payload: {
        columns: ['orderId', 'orderStatus', 'paymentStatus', 'orderTime', 'customer$caption', 'amount', 'payAmount'],
        slice: [
          { field: 'orderDate$caption', op: '>=', value: '2024-12-01' },
          { field: 'orderDate$caption', op: '<', value: '2024-12-31' }
        ],
        orderBy: [{ field: 'orderTime', order: 'desc' }]
      }
    })

    if (response.success && response.queryId) {
      actionsQueryId.value = response.queryId

      // 获取元数据
      actionsMeta.value = await fetchQueryMeta(response.queryId)

      // 获取 schema 并构建列（添加操作列）
      if (actionsMeta.value?.tableConfig?.qmModel) {
        const schema = await fetchQmSchema(actionsMeta.value.tableConfig.qmModel)

        // 添加操作列到 schema
        const schemaWithActions: ColumnSchema[] = [
          ...schema,
          { name: 'actions', type: 'TEXT', title: '操作', filterable: false, aggregatable: false, measure: false }
        ]

        // 构建列配置，包含操作列定制
        actionsColumns.value = buildTableColumns(schemaWithActions, {
          ...actionsMeta.value.tableConfig,
          visibleColumns: [...(actionsMeta.value.tableConfig.visibleColumns || []), 'actions'],
          customizations: [
            ...(actionsMeta.value.tableConfig.customizations || []),
            { name: 'actions', width: 200, fixed: 'right' }
          ]
        })
      }

      // 加载数据
      await loadActionsData()
    }
  } catch (e) {
    console.error('初始化操作列场景失败:', e)
  } finally {
    actionsLoading.value = false
  }
}

async function loadActionsData(start = 0, limit = 50) {
  if (!actionsQueryId.value) return

  actionsLoading.value = true
  try {
    const response = await fetchQueryData(actionsQueryId.value, { start, limit, slice: [], orderBy: [] })

    if (response.success) {
      actionsData.value = response.items
      actionsTotal.value = response.total
      actionsServerSummary.value = response.totalData ?? null
    }
  } catch (e) {
    console.error('加载数据失败:', e)
  } finally {
    actionsLoading.value = false
  }
}

function handleActionsPageChange(page: number, size: number) {
  loadActionsData((page - 1) * size, size)
}

function viewOrder(row: Record<string, unknown>) {
  alert(`查看订单详情\n\n订单号: ${row.orderId}\n客户: ${row['customer$caption']}\n金额: ${row.amount}\n状态: ${row.orderStatus}`)
}

function editOrder(row: Record<string, unknown>) {
  alert(`编辑订单\n\n订单号: ${row.orderId}\n\n（这里可以打开编辑对话框）`)
}

function deleteOrder(row: Record<string, unknown>) {
  if (confirm(`确定要删除订单 ${row.orderId} 吗？`)) {
    const index = actionsData.value.findIndex(item => item.orderId === row.orderId)
    if (index !== -1) {
      actionsData.value.splice(index, 1)
      actionsTotal.value--
      alert('删除成功（仅前端删除，刷新后恢复）')
    }
  }
}

// ============ 组合场景（DataTableWithSearch） ============
const combinedQueryId = ref<string | null>(null)
const combinedMeta = ref<QueryMetaResponse | null>(null)
const combinedColumns = ref<EnhancedColumnSchema[]>([])
const combinedData = ref<Record<string, unknown>[]>([])
const combinedTotal = ref(0)
const combinedLoading = ref(false)
const combinedSlices = ref<SliceRequestDef[]>([])
const combinedServerSummary = ref<Record<string, unknown> | null>(null)

async function initCombinedScene() {
  combinedLoading.value = true
  try {
    // 创建销售明细查询
    const response = await createQuery({
      model: 'FactSalesQueryModel',
      title: 'DataTableWithSearch 测试 - 销售明细',
      payload: {
        columns: ['orderId', 'salesDate$caption', 'product$caption', 'customer$caption', 'store$caption', 'quantity', 'salesAmount', 'profitAmount'],
        slice: [],
        orderBy: [{ field: 'salesDate$caption', order: 'desc' }]
      }
    })

    if (response.success && response.queryId) {
      combinedQueryId.value = response.queryId

      // 获取元数据
      combinedMeta.value = await fetchQueryMeta(response.queryId)

      // 获取 schema 并构建列
      if (combinedMeta.value?.tableConfig?.qmModel) {
        const schema = await fetchQmSchema(combinedMeta.value.tableConfig.qmModel)
        combinedColumns.value = buildTableColumns(schema, combinedMeta.value.tableConfig)
      }

      // 加载数据
      await loadCombinedData()
    }
  } catch (e) {
    console.error('初始化组合场景失败:', e)
  } finally {
    combinedLoading.value = false
  }
}

async function loadCombinedData(start = 0, limit = 50) {
  if (!combinedQueryId.value) return

  combinedLoading.value = true
  try {
    const response = await fetchQueryData(combinedQueryId.value, {
      start,
      limit,
      slice: combinedSlices.value,
      orderBy: []
    })

    if (response.success) {
      combinedData.value = response.items
      combinedTotal.value = response.total
      combinedServerSummary.value = response.totalData ?? null
    }
  } catch (e) {
    console.error('加载数据失败:', e)
  } finally {
    combinedLoading.value = false
  }
}

function handleCombinedFilterChange(slices: SliceRequestDef[]) {
  combinedSlices.value = slices
  loadCombinedData()
}

function handleCombinedPageChange(page: number, size: number) {
  loadCombinedData((page - 1) * size, size)
}

// ============ 场景切换逻辑 ============
function goToScene(scene: SceneType) {
  currentScene.value = scene

  // 初始化对应场景
  if (scene === 'toolbar' && !toolbarQueryId.value) {
    initToolbarScene()
  } else if (scene === 'custom-actions' && !actionsQueryId.value) {
    initActionsScene()
  } else if (scene === 'combined' && !combinedQueryId.value) {
    initCombinedScene()
  }
}

function goHome() {
  currentScene.value = 'home'
  queryId.value = null
}
</script>

<template>
  <div id="app">
    <!-- 首页 -->
    <div v-if="currentScene === 'home'" class="home-page">
      <div class="hero">
        <h1>Foggy Data Viewer - 验证应用</h1>
        <p class="subtitle">使用后台真实数据测试组件各项功能</p>
      </div>

      <div class="scenes-grid">
        <div class="scene-card" @click="goToScene('dataviewer')">
          <div class="scene-icon">📊</div>
          <h3>DataViewer 组件</h3>
          <p>完整的数据浏览器组件，支持 DSL 查询</p>
          <ul>
            <li>动态 DSL 查询创建</li>
            <li>自动获取元数据和 Schema</li>
            <li>完整的分页、排序、筛选功能</li>
          </ul>
        </div>

        <div class="scene-card" @click="goToScene('toolbar')">
          <div class="scene-icon">🔍</div>
          <h3>SearchToolbar 独立使用</h3>
          <p>搜索工具栏与 DataTable 分离使用</p>
          <ul>
            <li>SearchToolbar 独立放置</li>
            <li>手动控制筛选条件</li>
            <li>搜索/重置按钮事件</li>
          </ul>
        </div>

        <div class="scene-card" @click="goToScene('custom-actions')">
          <div class="scene-icon">⚡</div>
          <h3>自定义操作列</h3>
          <p>使用插槽自定义操作列按钮</p>
          <ul>
            <li>查看/编辑/删除按钮</li>
            <li>固定在右侧</li>
            <li>自定义样式和事件</li>
          </ul>
        </div>

        <div class="scene-card" @click="goToScene('combined')">
          <div class="scene-icon">🎯</div>
          <h3>DataTableWithSearch</h3>
          <p>SearchToolbar + DataTable 组合组件</p>
          <ul>
            <li>自动集成搜索工具栏</li>
            <li>属性透传</li>
            <li>统一的筛选事件</li>
          </ul>
        </div>
      </div>

      <div class="info-section">
        <h3>环境要求</h3>
        <ul>
          <li>后台服务运行在 <code>localhost:7108</code></li>
          <li>已配置 API 代理：<code>/data-viewer/api</code> → <code>http://localhost:7108</code></li>
        </ul>
      </div>
    </div>

    <!-- DataViewer 场景 -->
    <div v-else-if="currentScene === 'dataviewer'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>DataViewer 组件测试</h2>
      </div>

      <!-- 如果已有 queryId，显示 DataViewer -->
      <div v-if="queryId" class="viewer-container">
        <DataViewer :query-id="queryId" />
      </div>

      <!-- 否则显示 DSL 输入表单 -->
      <div v-else class="dsl-form">
        <div class="form-row">
          <div class="examples-panel">
            <h3>示例查询</h3>
            <div class="example-list">
              <button
                v-for="example in examples"
                :key="example.name"
                class="example-btn"
                @click="selectExample(example)"
              >
                {{ example.name }}
              </button>
            </div>
          </div>

          <div class="input-panel">
            <h3>DSL 输入</h3>
            <textarea
              v-model="dslInput"
              class="dsl-textarea"
              placeholder="输入 JSON 格式的查询参数..."
              :disabled="isSubmitting"
            ></textarea>
            <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>
            <button class="submit-btn" @click="submitQuery" :disabled="isSubmitting">
              {{ isSubmitting ? '提交中...' : '提交查询' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Toolbar 场景 -->
    <div v-else-if="currentScene === 'toolbar'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>SearchToolbar 独立使用测试</h2>
      </div>

      <div class="scene-content">
        <div class="feature-info">
          <p>SearchToolbar 独立放置在表格上方，手动处理筛选逻辑</p>
        </div>

        <!-- 独立的 SearchToolbar -->
        <div class="toolbar-container">
          <SearchToolbar
            :columns="toolbarColumns"
            :searchable-fields="['productName', 'brand', 'categoryName']"
            v-model="toolbarSlices"
            @search="handleToolbarSearch"
            @reset="handleToolbarReset"
          />
        </div>

        <!-- DataTable -->
        <div class="table-container">
          <DataTable
            :columns="toolbarColumns"
            :data="toolbarData"
            :total="toolbarTotal"
            :loading="toolbarLoading"
            :page-size="50"
            :show-filters="true"
            :server-summary="toolbarServerSummary"
            @page-change="handleToolbarPageChange"
          />
        </div>
      </div>
    </div>

    <!-- 自定义操作列场景 -->
    <div v-else-if="currentScene === 'custom-actions'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>自定义操作列测试</h2>
      </div>

      <div class="scene-content">
        <div class="feature-info">
          <p>使用 <code>#column-actions</code> 插槽自定义操作列，包含查看、编辑、删除按钮</p>
        </div>

        <div class="table-container">
          <DataTable
            :columns="actionsColumns"
            :data="actionsData"
            :total="actionsTotal"
            :loading="actionsLoading"
            :page-size="50"
            :show-filters="true"
            :server-summary="actionsServerSummary"
            @page-change="handleActionsPageChange"
          >
            <!-- 自定义操作列插槽 -->
            <template #column-actions="{ row }">
              <div class="action-buttons">
                <button @click.stop="viewOrder(row)" class="action-btn view" title="查看详情">
                  👁️ 查看
                </button>
                <button @click.stop="editOrder(row)" class="action-btn edit" title="编辑">
                  ✏️ 编辑
                </button>
                <button @click.stop="deleteOrder(row)" class="action-btn delete" title="删除">
                  🗑️ 删除
                </button>
              </div>
            </template>
          </DataTable>
        </div>
      </div>
    </div>

    <!-- DataTableWithSearch 场景 -->
    <div v-else-if="currentScene === 'combined'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>DataTableWithSearch 组合组件测试</h2>
      </div>

      <div class="scene-content">
        <div class="feature-info">
          <p>DataTableWithSearch 自动集成 SearchToolbar，通过 <code>searchable-fields</code> 配置可搜索字段</p>
        </div>

        <div class="table-container combined">
          <DataTableWithSearch
            :columns="combinedColumns"
            :data="combinedData"
            :total="combinedTotal"
            :loading="combinedLoading"
            :page-size="50"
            :show-filters="true"
            :server-summary="combinedServerSummary"
            :searchable-fields="['product$caption', 'customer$caption', 'store$caption', 'salesDate$caption']"
            @page-change="handleCombinedPageChange"
            @filter-change="handleCombinedFilterChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 首页样式 */
.home-page {
  min-height: 100%;
  padding: 40px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.hero {
  text-align: center;
  margin-bottom: 40px;
  color: white;
}

.hero h1 {
  font-size: 2.5rem;
  margin-bottom: 12px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.subtitle {
  font-size: 1.2rem;
  opacity: 0.9;
}

.scenes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto 40px;
}

.scene-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.scene-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.scene-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.scene-card h3 {
  font-size: 1.3rem;
  color: #303133;
  margin-bottom: 8px;
}

.scene-card > p {
  font-size: 0.9rem;
  color: #606266;
  margin-bottom: 16px;
}

.scene-card ul {
  list-style: none;
  padding: 0;
}

.scene-card li {
  font-size: 0.85rem;
  color: #909399;
  padding: 4px 0;
  padding-left: 20px;
  position: relative;
}

.scene-card li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: #67c23a;
}

.info-section {
  max-width: 600px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  padding: 20px;
}

.info-section h3 {
  font-size: 1.1rem;
  color: #303133;
  margin-bottom: 12px;
}

.info-section ul {
  list-style: none;
  padding: 0;
}

.info-section li {
  font-size: 0.9rem;
  color: #606266;
  padding: 6px 0;
}

.info-section code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', monospace;
  color: #409eff;
}

/* 场景页面通用样式 */
.scene-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.scene-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.back-btn {
  padding: 8px 16px;
  background: #f5f7fa;
  color: #606266;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #e4e7ed;
  color: #303133;
}

.scene-header h2 {
  font-size: 1.3rem;
  color: #303133;
}

.scene-content {
  flex: 1;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.feature-info {
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

.feature-info p {
  color: #409eff;
  font-size: 14px;
}

.feature-info code {
  background: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', monospace;
}

.toolbar-container {
  background: white;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.table-container {
  flex: 1;
  background: white;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-container.combined {
  padding: 0;
}

.table-container :deep(.data-table),
.table-container :deep(.data-table-with-search) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* DataViewer 场景 */
.viewer-container {
  flex: 1;
  overflow: hidden;
}

.viewer-container :deep(.data-viewer) {
  height: 100%;
}

.dsl-form {
  flex: 1;
  padding: 24px;
  overflow: auto;
}

.form-row {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.examples-panel,
.input-panel {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.examples-panel h3,
.input-panel h3 {
  font-size: 1.1rem;
  color: #303133;
  margin-bottom: 16px;
}

.example-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.example-btn {
  padding: 12px 16px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
  transition: all 0.2s;
}

.example-btn:hover {
  background: #ecf5ff;
  border-color: #409eff;
  color: #409eff;
}

.dsl-textarea {
  width: 100%;
  height: 300px;
  padding: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-family: 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
}

.dsl-textarea:focus {
  outline: none;
  border-color: #409eff;
}

.error-message {
  margin-top: 12px;
  padding: 10px 12px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 6px;
  color: #f56c6c;
  font-size: 0.9rem;
}

.submit-btn {
  margin-top: 16px;
  width: 100%;
  padding: 12px 24px;
  background: #409eff;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  cursor: pointer;
  transition: background 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #337ecc;
}

.submit-btn:disabled {
  background: #a0cfff;
  cursor: not-allowed;
}

/* 操作按钮样式 */
.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.action-btn {
  padding: 4px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
  white-space: nowrap;
}

.action-btn:hover {
  transform: translateY(-1px);
}

.action-btn.view {
  color: #1890ff;
  border-color: #91d5ff;
}

.action-btn.view:hover {
  background: #e6f7ff;
}

.action-btn.edit {
  color: #52c41a;
  border-color: #b7eb8f;
}

.action-btn.edit:hover {
  background: #f6ffed;
}

.action-btn.delete {
  color: #ff4d4f;
  border-color: #ffccc7;
}

.action-btn.delete:hover {
  background: #fff1f0;
}

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
