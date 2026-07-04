<script setup lang="ts">
import { ref } from 'vue'
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
import PivotRawViewerDemo from './PivotRawViewerDemo.vue'
import type {
  EnhancedColumnSchema,
  SliceRequestDef,
  ColumnSchema,
  CreateQueryRequest,
  QueryMetaResponse,
  TableSchema,
  FetchDataParams,
  FetchDataResult,
  DataTableWithSearchRef
} from 'foggy-data-viewer'

// ============ 场景切换 ============
type SceneType = 'home' | 'dataviewer' | 'toolbar' | 'custom-actions' | 'combined' | 'schema-mode' | 'custom-list' | 'saved-query' | 'pivot-raw'
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
      toolbarMeta.value = await fetchQueryMeta('DimProductQueryModel', response.queryId)

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
    const response = await fetchQueryData('DimProductQueryModel', toolbarQueryId.value, {
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
      actionsMeta.value = await fetchQueryMeta('FactOrderQueryModel', response.queryId)

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
    const response = await fetchQueryData('FactOrderQueryModel', actionsQueryId.value, { start, limit, slice: [], orderBy: [] })

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
      combinedMeta.value = await fetchQueryMeta('FactSalesQueryModel', response.queryId)

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
    const response = await fetchQueryData('FactSalesQueryModel', combinedQueryId.value, {
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

// ============ Schema 模式场景（推荐用法） ============
const schemaQueryId = ref<string | null>(null)
const schemaTableSchema = ref<TableSchema | null>(null)
const schemaModeFetchData = ref<((params: FetchDataParams) => Promise<FetchDataResult>) | null>(null)
const schemaModeInitialized = ref(false)
const schemaTableRef = ref<DataTableWithSearchRef>()

// Schema 模式的 toolbar 按钮处理
function handleAdd() {
  alert('点击了新增按钮\n\n这里可以打开新增对话框')
}

function handleExport() {
  alert('点击了导出按钮\n\n这里可以触发数据导出逻辑')
}

function handleLoadSuccess(result: FetchDataResult) {
  console.log('加载成功:', result)
}

function handleLoadError(error: Error) {
  console.error('加载失败:', error)
}

async function initSchemaScene() {
  if (schemaModeInitialized.value) return

  try {
    // 1. 创建查询
    const response = await createQuery({
      model: 'FactSalesQueryModel',
      title: 'Schema 模式测试 - 销售明细',
      payload: {
        columns: ['orderId', 'salesDate$caption', 'product$caption', 'customer$caption', 'store$caption', 'quantity', 'salesAmount', 'profitAmount'],
        slice: [],
        orderBy: [{ field: 'salesDate$caption', order: 'desc' }]
      }
    })

    if (!response.success || !response.queryId) {
      console.error('创建查询失败:', response.error)
      return
    }

    schemaQueryId.value = response.queryId

    // 2. 获取元数据
    const meta = await fetchQueryMeta('FactSalesQueryModel', response.queryId)

    // 3. 获取 Schema 并构建 TableSchema
    if (meta?.tableConfig?.qmModel) {
      const qmSchema = await fetchQmSchema(meta.tableConfig.qmModel)
      const columns = buildTableColumns(qmSchema, meta.tableConfig)

      // 构建 TableSchema（传给组件）
      schemaTableSchema.value = {
        columns,
        searchableFields: ['product$caption', 'customer$caption', 'store$caption'],
        pageSize: 50,
        showFilters: true,
        showSearchToolbar: true,
        searchLayout: 'horizontal'
      }

      // 4. 创建 fetchData 函数（捕获 queryId）
      const queryId = response.queryId
      schemaModeFetchData.value = async (params: FetchDataParams): Promise<FetchDataResult> => {
        const dataResponse = await fetchQueryData('FactSalesQueryModel', queryId, {
          start: (params.page - 1) * params.pageSize,
          limit: params.pageSize,
          slice: params.slice,
          orderBy: params.orderBy
        })

        return {
          items: dataResponse.items,
          total: dataResponse.total,
          totalData: dataResponse.totalData
        }
      }

      schemaModeInitialized.value = true
    }
  } catch (e) {
    console.error('初始化 Schema 模式场景失败:', e)
  }
}

// ============ 自定义查询场景 ============
const customListTableRef = ref<DataTableWithSearchRef>()
const customListSchema = ref<TableSchema | null>(null)
const customListFetchData = ref<((params: FetchDataParams) => Promise<FetchDataResult>) | null>(null)
const customListInitialized = ref(false)
const customListQueryId = ref<string | null>(null)

function appendCustomListLongProbe(items: Record<string, unknown>[]): Record<string, unknown>[] {
  return items.map((item, index) => ({
    ...item,
    longProbeId: 88000 + index
  }))
}

async function initCustomListScene() {
  if (customListInitialized.value) return

  try {
    const response = await createQuery({
      model: 'FactSalesQueryModel',
      title: '自定义查询测试 - 销售明细',
      payload: {
        columns: ['orderId', 'salesDate$caption', 'product$caption', 'customer$caption', 'store$caption', 'quantity', 'salesAmount', 'profitAmount'],
        slice: [],
        orderBy: [{ field: 'salesDate$caption', order: 'desc' }]
      }
    })

    if (!response.success || !response.queryId) {
      console.error('创建查询失败:', response.error)
      return
    }

    customListQueryId.value = response.queryId

    const meta = await fetchQueryMeta('FactSalesQueryModel', response.queryId)

    if (meta?.tableConfig?.qmModel) {
      const qmSchema = await fetchQmSchema(meta.tableConfig.qmModel)
      const columns = buildTableColumns([
        ...qmSchema,
        {
          name: 'longProbeId',
          type: 'LONG',
          title: 'LONG测试ID',
          filterable: false,
          aggregatable: false,
          measure: false
        }
      ], {
        ...meta.tableConfig,
        visibleColumns: [...(meta.tableConfig.visibleColumns || []), 'longProbeId'],
        customizations: [
          ...(meta.tableConfig.customizations || []),
          { name: 'longProbeId', width: 130 }
        ]
      })

      customListSchema.value = {
        columns,
        searchableFields: ['product$caption', 'customer$caption', 'store$caption', 'salesDate$caption'],
        pageSize: 50,
        showFilters: true,
        showSearchToolbar: true,
        searchLayout: 'horizontal'
      }

      const queryId = response.queryId
      customListFetchData.value = async (params: FetchDataParams): Promise<FetchDataResult> => {
        const dataResponse = await fetchQueryData('FactSalesQueryModel', queryId, {
          start: (params.page - 1) * params.pageSize,
          limit: params.pageSize,
          slice: params.slice,
          orderBy: params.orderBy
        })

        return {
          items: appendCustomListLongProbe(dataResponse.items),
          total: dataResponse.total,
          totalData: dataResponse.totalData
        }
      }

      customListInitialized.value = true
    }
  } catch (e) {
    console.error('初始化自定义查询场景失败:', e)
  }
}

// ============ 保存查询场景 ============
const savedQueryTableRef = ref<DataTableWithSearchRef>()
const savedQuerySchema = ref<TableSchema | null>(null)
const savedQueryFetchData = ref<((params: FetchDataParams) => Promise<FetchDataResult>) | null>(null)
const savedQueryInitialized = ref(false)
const savedQueryQueryId = ref<string | null>(null)

async function initSavedQueryScene() {
  if (savedQueryInitialized.value) return

  try {
    // 创建查询
    const response = await createQuery({
      model: 'FactSalesDemoAuthQueryModel',
      title: '保存查询测试 - 销售明细',
      payload: {
        columns: ['orderId', 'orderStatus', 'paymentMethod', 'product$caption', 'customer$caption', 'store$caption', 'salesAmount'],
        slice: [],
        orderBy: [{ field: 'orderId', order: 'desc' }]
      }
    })

    if (!response.success || !response.queryId) {
      console.error('创建查询失败:', response.error)
      return
    }

    savedQueryQueryId.value = response.queryId

    // 获取元数据
    const meta = await fetchQueryMeta('FactSalesDemoAuthQueryModel', response.queryId)

    // 获取 Schema 并构建 TableSchema
    if (meta?.tableConfig?.qmModel) {
      const qmSchema = await fetchQmSchema(meta.tableConfig.qmModel)
      const columns = buildTableColumns(qmSchema, meta.tableConfig)

      savedQuerySchema.value = {
        columns,
        searchableFields: ['product$caption', 'customer$caption', 'store$caption'],
        pageSize: 50,
        showFilters: true,
        showSearchToolbar: true,
        searchLayout: 'horizontal'
      }

      // 创建 fetchData 函数
      const queryId = response.queryId
      savedQueryFetchData.value = async (params: FetchDataParams): Promise<FetchDataResult> => {
        const dataResponse = await fetchQueryData('FactSalesDemoAuthQueryModel', queryId, {
          start: (params.page - 1) * params.pageSize,
          limit: params.pageSize,
          slice: params.slice,
          orderBy: params.orderBy
        })

        return {
          items: dataResponse.items,
          total: dataResponse.total,
          totalData: dataResponse.totalData
        }
      }

      savedQueryInitialized.value = true
    }
  } catch (e) {
    console.error('初始化保存查询场景失败:', e)
  }
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
  } else if (scene === 'schema-mode' && !schemaModeInitialized.value) {
    initSchemaScene()
  } else if (scene === 'custom-list' && !customListInitialized.value) {
    initCustomListScene()
  } else if (scene === 'saved-query' && !savedQueryInitialized.value) {
    initSavedQueryScene()
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
        <div class="scene-card highlight" @click="goToScene('schema-mode')">
          <div class="scene-icon">⭐</div>
          <h3>Schema 模式（推荐）</h3>
          <p>最简洁的使用方式，传入 schema + fetchData</p>
          <ul>
            <li>组件自动管理分页状态</li>
            <li>组件自动管理加载状态</li>
            <li>无需手动处理事件</li>
            <li>支持搜索工具栏和表头筛选</li>
          </ul>
        </div>

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
          <h3>受控模式</h3>
          <p>用户完全控制 data/total/loading 等状态</p>
          <ul>
            <li>手动管理分页状态</li>
            <li>手动监听事件并加载数据</li>
            <li>适合需要完全控制的场景</li>
          </ul>
        </div>

        <div class="scene-card highlight" @click="goToScene('custom-list')">
          <div class="scene-icon">🧩</div>
          <h3>自定义查询（新）</h3>
          <p>保存列、筛选、排序和默认查询配置</p>
          <ul>
            <li>保存当前查询视图</li>
            <li>编辑和覆盖已有方案</li>
            <li>按用户和业务隔离</li>
            <li>支持默认方案自动加载</li>
          </ul>
        </div>

        <div class="scene-card highlight" @click="goToScene('saved-query')">
          <div class="scene-icon">💾</div>
          <h3>保存查询功能（新）</h3>
          <p>保存和加载查询配置，支持团队协作</p>
          <ul>
            <li>保存筛选条件和列配置</li>
            <li>三级分享（个人/部门/租户）</li>
            <li>查询列表管理</li>
            <li>一键应用已保存查询</li>
          </ul>
        </div>

        <div class="scene-card highlight" @click="goToScene('pivot-raw')">
          <div class="scene-icon text-icon">PVT</div>
          <h3>Pivot Raw Viewer（新）</h3>
          <p>验证 pivot/domainSlice 独立透视表展示</p>
          <ul>
            <li>二层科目表头</li>
            <li>行轴和列轴分页证据</li>
            <li>cell 保留全局事实范围</li>
            <li>独立于普通 flat table</li>
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

    <!-- DataTableWithSearch 场景（受控模式） -->
    <div v-else-if="currentScene === 'combined'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>DataTableWithSearch 受控模式测试</h2>
      </div>

      <div class="scene-content">
        <div class="feature-info">
          <p><strong>受控模式：</strong>用户手动管理 data、total、loading 等状态，适合需要完全控制数据流的场景</p>
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

    <!-- Schema 模式场景（推荐用法） -->
    <div v-else-if="currentScene === 'schema-mode'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>Schema 模式测试（推荐）</h2>
      </div>

      <div class="scene-content">
        <div class="feature-info success">
          <p><strong>Schema 模式：</strong>只需传入 <code>schema</code> + <code>fetchData</code>，组件自动管理所有状态（分页、加载、筛选），无需手动处理事件</p>
        </div>

        <!-- 代码示例 -->
        <div class="code-example">
          <h4>使用示例</h4>
          <pre><code>&lt;DataTableWithSearch
  :schema="tableSchema"
  :fetch-data="fetchData"
  @load-success="onSuccess"
  @load-error="onError"
/&gt;

// schema 包含: columns, searchableFields, pageSize, showFilters 等
// fetchData 函数签名: (params: FetchDataParams) => Promise&lt;FetchDataResult&gt;</code></pre>
        </div>

        <div v-if="schemaTableSchema && schemaModeFetchData" class="table-container combined">
          <DataTableWithSearch
            ref="schemaTableRef"
            :schema="schemaTableSchema"
            :fetch-data="schemaModeFetchData"
            @load-success="handleLoadSuccess"
            @load-error="handleLoadError"
          >
            <!-- toolbar 插槽：左侧放置自定义按钮 -->
            <template #toolbar>
              <button class="toolbar-btn primary" @click="handleAdd">
                + 新增
              </button>
              <button class="toolbar-btn" @click="handleExport">
                导出
              </button>
              <button class="toolbar-btn" @click="schemaTableRef?.refresh()">
                刷新
              </button>
            </template>
          </DataTableWithSearch>
        </div>

        <div v-else class="loading-placeholder">
          <p>正在初始化...</p>
        </div>
      </div>
    </div>

    <!-- 自定义查询场景（新功能） -->
    <div v-else-if="currentScene === 'custom-list'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>自定义查询功能测试（新功能）</h2>
      </div>

      <div class="scene-content custom-list-scene-content">
        <aside class="custom-list-aside">
          <div class="feature-info success">
            <p><strong>自定义查询：</strong>允许用户保存当前列、筛选条件、排序规则和分页大小，并可设为默认查询</p>
          </div>

          <div class="code-example custom-list-code-example">
            <h4>使用示例</h4>
            <pre><code>&lt;DataTableWithSearch
  :schema="schema"
  :fetch-data="fetchData"
  :list-preset="{
    userId: 'verification_user_001',
    model: 'FactSalesQueryModel',
    businessKey: 'sales-custom-list-demo-long'
  }"
/&gt;</code></pre>
          </div>

          <div class="feature-info custom-list-guide">
            <p><strong>操作指南：</strong>调整筛选条件或列状态后点击右上角“自定义查询”，可保存、应用、编辑名称与范围、覆盖和设置默认方案</p>
          </div>
        </aside>

        <div v-if="customListSchema && customListFetchData" class="table-container combined">
          <DataTableWithSearch
            ref="customListTableRef"
            :schema="customListSchema"
            :fetch-data="customListFetchData"
            :list-preset="{
              userId: 'verification_user_001',
              model: 'FactSalesQueryModel',
              businessKey: 'sales-custom-list-demo-long',
              allowShared: true,
              buttonText: '自定义查询',
              placement: 'toolbar-right'
            }"
            @load-success="handleLoadSuccess"
            @load-error="handleLoadError"
          >
            <template #toolbar>
              <button class="toolbar-btn" @click="customListTableRef?.refresh()">
                刷新数据
              </button>
              <button class="toolbar-btn" @click="customListTableRef?.clearAllFilters()">
                清空筛选
              </button>
            </template>
          </DataTableWithSearch>
        </div>

        <div v-else class="loading-placeholder">
          <p>正在初始化...</p>
        </div>
      </div>
    </div>

    <!-- 保存查询场景（新功能） -->
    <div v-else-if="currentScene === 'saved-query'" class="scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>保存查询功能测试（新功能）</h2>
      </div>

      <div class="scene-content saved-query-scene-content">
        <aside class="saved-query-aside">
          <div class="feature-info success">
            <p><strong>保存查询功能：</strong>允许用户保存常用的查询配置（列、筛选、排序），并支持团队成员之间共享查询</p>
          </div>

          <!-- 功能说明 -->
          <div class="code-example saved-query-code-example">
            <h4>功能特性</h4>
            <pre><code>✓ 统一查询方案入口：自定义、加载、保存和清空条件
✓ 保存当前实时表格状态：列、筛选、排序和分页大小
✓ 三级分享：PRIVATE（仅自己）/ DEPARTMENT（部门）/ TENANT（租户）
✓ 查询列表：在自定义查询面板中加载和管理已有方案
✓ 一键应用：快速加载已保存的查询配置
✓ 业务隔离：通过 businessId 区分不同业务的查询</code></pre>
          </div>

          <div class="code-example saved-query-code-example">
            <h4>使用示例</h4>
            <pre><code>&lt;DataTableWithSearch
  ref="tableRef"
  :schema="schema"
  :fetch-data="fetchData"
  enable-saved-query
  :list-preset="{
    userId: 'user_manager_001',
    model: 'FactSalesDemoAuthQueryModel',
    businessKey: 'sales-report-2024',
    allowShared: true,
    buttonText: '自定义查询',
    placement: 'toolbar-right'
  }"
/&gt;</code></pre>
          </div>

          <!-- 操作提示 -->
          <div class="feature-info saved-query-guide">
            <p><strong>操作指南：</strong>使用上方的"查询方案"下拉保存当前配置，或加载和应用已保存的查询方案</p>
            <p><strong>认证说明：</strong>当前使用 Authorization: Bearer manager-token-123（模拟门店经理身份）</p>
          </div>
        </aside>

        <div v-if="savedQuerySchema && savedQueryFetchData" class="table-container combined">
          <DataTableWithSearch
            ref="savedQueryTableRef"
            :schema="savedQuerySchema"
            :fetch-data="savedQueryFetchData"
            enable-saved-query
            :list-preset="{
              userId: 'user_manager_001',
              model: 'FactSalesDemoAuthQueryModel',
              businessKey: 'sales-report-2024',
              allowShared: true,
              buttonText: '自定义查询',
              placement: 'toolbar-right'
            }"
            @load-success="handleLoadSuccess"
            @load-error="handleLoadError"
          >
            <!-- toolbar 插槽 -->
            <template #toolbar>
              <button class="toolbar-btn" @click="savedQueryTableRef?.refresh()">
                刷新数据
              </button>
              <button class="toolbar-btn" @click="savedQueryTableRef?.clearAllFilters()">
                清空筛选
              </button>
            </template>
          </DataTableWithSearch>
        </div>

        <div v-else class="loading-placeholder">
          <p>正在初始化...</p>
        </div>
      </div>
    </div>

    <!-- Pivot Raw Viewer 场景（新功能） -->
    <div v-else-if="currentScene === 'pivot-raw'" class="scene-page pivot-scene-page">
      <div class="scene-header">
        <button class="back-btn" @click="goHome">← 返回首页</button>
        <h2>Pivot Raw Viewer 体验验证</h2>
      </div>

      <div class="scene-content pivot-scene-content">
        <PivotRawViewerDemo />
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

.scene-icon.text-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 44px;
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 6px;
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
  width: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.scene-header {
  width: 100%;
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
  width: 100%;
  min-width: 0;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.custom-list-scene-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.65fr);
  gap: 16px;
  min-height: 0;
}

.custom-list-aside {
  width: 100%;
  min-width: 0;
  overflow: auto;
}

.custom-list-aside .feature-info {
  margin-bottom: 16px;
}

.custom-list-code-example {
  margin-bottom: 16px;
}

.custom-list-guide {
  margin-bottom: 0;
}

.custom-list-scene-content > .table-container {
  width: 100%;
  min-width: 0;
  min-height: 0;
}

.saved-query-scene-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.65fr);
  gap: 16px;
  min-height: 0;
}

.saved-query-aside {
  width: 100%;
  min-width: 0;
  overflow: auto;
}

.saved-query-aside .feature-info,
.saved-query-code-example {
  margin-bottom: 16px;
}

.saved-query-guide {
  margin-bottom: 0;
}

.saved-query-scene-content > .table-container {
  width: 100%;
  min-width: 0;
  min-height: 0;
}

.pivot-scene-page {
  min-width: 0;
}

.pivot-scene-content {
  min-width: 0;
  overflow: auto;
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

/* 推荐卡片高亮样式 */
.scene-card.highlight {
  border: 2px solid #67c23a;
  background: linear-gradient(135deg, #f0f9eb 0%, #ffffff 100%);
}

.scene-card.highlight:hover {
  border-color: #67c23a;
  box-shadow: 0 12px 40px rgba(103, 194, 58, 0.3);
}

/* 成功样式的信息框 */
.feature-info.success {
  background: #f0f9eb;
  border-color: #c2e7b0;
}

.feature-info.success p {
  color: #67c23a;
}

/* 代码示例样式 */
.code-example {
  background: #2d2d2d;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.code-example h4 {
  color: #909399;
  font-size: 12px;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.code-example pre {
  margin: 0;
  overflow-x: auto;
}

.code-example code {
  color: #e6e6e6;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
}

/* 加载占位符 */
.loading-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 8px;
  color: #909399;
}

/* Toolbar 按钮样式 */
.toolbar-btn {
  padding: 6px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: white;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.toolbar-btn:hover {
  border-color: #409eff;
  color: #409eff;
}

.toolbar-btn.primary {
  background: #409eff;
  border-color: #409eff;
  color: white;
}

.toolbar-btn.primary:hover {
  background: #66b1ff;
  border-color: #66b1ff;
}

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }

  .custom-list-scene-content {
    display: flex;
    flex-direction: column;
  }

  .custom-list-aside {
    max-height: none;
    overflow: visible;
  }

  .saved-query-scene-content {
    display: flex;
    flex-direction: column;
  }

  .saved-query-aside {
    max-height: none;
    overflow: visible;
  }
}
</style>
