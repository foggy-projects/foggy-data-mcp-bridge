# Frontend Component Generator - 使用示例

## 场景 1：已知模型名称，快速生成组件

### 用户输入

```
我想基于 order_query_model 模型生成一个订��查询表格组件
- 组件名称：OrderQueryTable
- 需要显示所有列
```

### 生成的组件流程

1. 调用 SemanticController `/mcp/analyst/description-model-internal?model=order_query_model`
2. 获取模型 schema，自动生成 4 个文件
3. 返回生成完成信息

### 生成的文件内容

#### OrderQueryTable.vue

```vue
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElPagination } from 'element-plus'
import { DataTableWithSearch } from 'foggy-data-viewer'
import type { SliceRequestDef } from 'foggy-data-viewer'
import { columns } from './schemas/order-query-table.schema'
import { fetchOrderQueryTableData } from './apis/order-query-table.api'
import type { OrderQueryTableRow } from './schemas/order-query-table.schema'

// 数据状态
const data = ref<OrderQueryTableRow[]>([])
const total = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(50)
const currentFilters = ref<SliceRequestDef[]>([])

// 操作列控制
const showOperColumn = ref(false) // 默认不显示操作列

// 操作列配置
const operColumnSchema = {
  name: '__oper__',
  type: 'TEXT',
  title: '操作',
  width: 150,
  fixed: 'right',
}

// 合并列配置
const displayColumns = computed(() => {
  if (showOperColumn.value) {
    return [...columns, operColumnSchema]
  }
  return columns
})

// 初始化
onMounted(() => {
  loadData()
})

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const response = await fetchOrderQueryTableData({
      page: currentPage.value,
      pageSize: pageSize.value,
      filters: currentFilters.value
    })
    data.value = response.rows
    total.value = response.total
  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    loading.value = false
  }
}

// 分页变化（来自分页组件）
function handlePaginationChange() {
  currentPage.value = 1
  loadData()
}

// 分页变化（来自表格）
function handlePageChange(page: number, size: number) {
  currentPage.value = page
  pageSize.value = size
  loadData()
}

// 筛选变化
function handleFilterChange(filters: SliceRequestDef[]) {
  currentFilters.value = filters
  currentPage.value = 1 // 重置分页
  loadData()
}

// 排序变化
function handleSortChange(field: string, order: string) {
  console.log(`Sorting by ${field} ${order}`)
  // 如果需要后台排序，在这里实现
}

// 公开方法
function refresh() {
  currentPage.value = 1
  currentFilters.value = []
  loadData()
}

function clearFilters() {
  currentFilters.value = []
  currentPage.value = 1
  loadData()
}

defineExpose({
  refresh,
  clearFilters,
  data,
  total,
  loading
})
</script>

<template>
  <div class="order-query-table">
    <!-- 工具栏 + 分页栏 -->
    <div class="table-header-bar">
      <div class="toolbar-left">
        <slot name="toolbar">
          <!-- 用户可通过插槽添加按钮或其他组件 -->
        </slot>
      </div>
      <div class="pagination-right">
        <span class="total-info">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          @change="handlePaginationChange"
        />
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-content">
      <DataTableWithSearch
        :columns="displayColumns"
        :data="data"
        :total="total"
        :loading="loading"
        :page-size="pageSize"
        :show-search-toolbar="true"
        :show-filters="true"
        @page-change="handlePageChange"
        @filter-change="handleFilterChange"
        @sort-change="handleSortChange"
      >
        <!-- 操作列插槽 -->
        <template #__oper__="{ row }">
          <slot name="operColumn" :row="row" />
        </template>
      </DataTableWithSearch>
    </div>
  </div>
</template>

<style scoped>
.order-query-table {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  background-color: #fff;
  border-radius: 4px;
}

.table-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  gap: 16px;
}

.toolbar-left {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.pagination-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 0 0 auto;
}

.total-info {
  color: #606266;
  font-size: 14px;
  white-space: nowrap;
}

.table-content {
  flex: 1;
  overflow: auto;
}
</style>
```

#### schemas/order-query-table.schema.ts

```typescript
import type { EnhancedColumnSchema } from 'foggy-data-viewer'

export const columns: EnhancedColumnSchema[] = [
  {
    name: 'order_id',
    type: 'INTEGER',
    title: '订单ID',
    width: 100,
    fixed: 'left',
    filterable: true,
    filterType: 'number',
    sortable: true
  },
  {
    name: 'order_no',
    type: 'TEXT',
    title: '订单号',
    width: 150,
    filterable: true,
    filterType: 'text',
    sortable: true
  },
  {
    name: 'customer_name',
    type: 'TEXT',
    title: '客户名称',
    width: 150,
    filterable: true,
    filterType: 'text'
  },
  {
    name: 'amount',
    type: 'MONEY',
    title: '订单金额',
    width: 120,
    filterable: true,
    filterType: 'number',
    aggregatable: true,
    formatter: (value: number) => `¥${value.toFixed(2)}`
  },
  {
    name: 'status',
    type: 'TEXT',
    title: '订单状态',
    width: 100,
    filterable: true,
    filterType: 'select',
    filterOptions: [
      { label: '待支付', value: 'pending' },
      { label: '已支付', value: 'paid' },
      { label: '已发货', value: 'shipped' },
      { label: '已完成', value: 'completed' },
      { label: '已取消', value: 'cancelled' }
    ]
  },
  {
    name: 'create_time',
    type: 'DATETIME',
    title: '创建时间',
    width: 180,
    filterable: true,
    filterType: 'date',
    sortable: true,
    formatter: (value: string) => new Date(value).toLocaleString('zh-CN')
  },
  {
    name: 'update_time',
    type: 'DATETIME',
    title: '更新时间',
    width: 180,
    filterable: true,
    filterType: 'date',
    sortable: true,
    formatter: (value: string) => new Date(value).toLocaleString('zh-CN')
  }
]

export interface OrderQueryTableRow {
  order_id: number
  order_no: string
  customer_name: string
  amount: number
  status: string
  create_time: string
  update_time: string
}
```

#### apis/order-query-table.api.ts

```typescript
import axios from 'axios'
import type { SliceRequestDef } from 'foggy-data-viewer'

const API_BASE = process.env.VUE_APP_API_BASE || 'http://localhost:8080'
const NAMESPACE = process.env.VUE_APP_NAMESPACE || 'default'
const AUTHORIZATION = process.env.VUE_APP_AUTHORIZATION || '' // 可选的授权 token

export interface OrderQueryTableQueryRequest {
  page: number
  pageSize: number
  filters?: SliceRequestDef[]
  sort?: { field: string; order: 'asc' | 'desc' }
}

export interface OrderQueryTableQueryResponse {
  rows: any[]
  total: number
}

/**
 * 查询订单数据
 * @param request 查询请求参数
 * @returns 查询结果
 */
export async function fetchOrderQueryTableData(
  request: OrderQueryTableQueryRequest
): Promise<OrderQueryTableQueryResponse> {
  try {
    const params: any = {
      model: 'order_query_model',
      page: request.page,
      pageSize: request.pageSize
    }

    // 添加筛选条件
    if (request.filters && request.filters.length > 0) {
      params.slices = JSON.stringify(request.filters)
    }

    // 添加排序条件
    if (request.sort) {
      params.sort = JSON.stringify(request.sort)
    }

    // 构建请求 headers
    const headers: any = {
      'X-NS': NAMESPACE
    }

    // 如果配置了授权 token，添加到 header
    if (AUTHORIZATION) {
      headers['Authorization'] = AUTHORIZATION
    }

    const response = await axios.get(
      `${API_BASE}/jdbc-model/query-model/v2`,
      {
        params,
        headers
      }
    )

    // 验证响应
    if (response.data.code !== 200) {
      throw new Error(response.data.msg || '查询失败')
    }

    // 返回格式化的数据
    return {
      rows: response.data.data?.rows || [],
      total: response.data.data?.total || 0
    }
  } catch (error) {
    console.error('Failed to fetch order data:', error)
    throw error
  }
}
```

#### README.md

```markdown
# OrderQueryTable

订单查询表格组件，提供订单信息的浏览、搜索、筛选和排序功能。

**作者**: Frontend Team
**创建时间**: 2024-01-24
**基于模型**: order_query_model
**版本**: 1.0.0

## 功能特性

- 🎯 集成数据表格和搜索工具栏
- 🔍 支持多条件筛选（状态、时间范围、金额范围等）
- 📊 自动计算订单总金额汇总
- ⚡ 响应式加载状态和错误处理
- 📱 支持分页和排序
- 🎨 完整的 TypeScript 类型定义

## 快速开始

### 基本使用

```vue
<template>
  <OrderQueryTable ref="tableRef" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import OrderQueryTable from '@/components/models/OrderQueryTable.vue'

const tableRef = ref()

// 刷新表格
function handleRefresh() {
  tableRef.value?.refresh()
}

// 清空筛选
function handleClearFilters() {
  tableRef.value?.clearFilters()
}
</script>
```

### Props（通过 DataTableWithSearch 传递）

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageSize | `number` | `50` | 每页显示条数 |
| showFilters | `boolean` | `true` | 是否显示表头筛选器 |
| showSearchToolbar | `boolean` | `true` | 是否显示搜索工具栏 |

### Events

| 事件名 | 参数 | 说明 |
|--------|------|------|
| page-change | `(page, pageSize)` | 分页变化 |
| filter-change | `(filters)` | 筛选条件变化 |
| sort-change | `(field, order)` | 排序变化 |
| row-click | `(row, column)` | 行点击事件 |

### Methods

| 方法名 | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| refresh | - | `void` | 刷新表格数据 |
| clearFilters | - | `void` | 清空所有筛选条件 |

### 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| data | `OrderQueryTableRow[]` | 表格数据 |
| total | `number` | 总记录数 |
| loading | `boolean` | 加载状态 |

## 数据模型

### OrderQueryTableRow

```typescript
interface OrderQueryTableRow {
  order_id: number         // 订单ID
  order_no: string         // 订单号
  customer_name: string    // 客户名称
  amount: number           // 订单金额
  status: string           // 订单状态
  create_time: string      // 创建时间
  update_time: string      // 更新时间
}
```

## API 接口

本组件使用后台以下接口：

- **模型**: `order_query_model`
- **命名空间**: `default`
- **端点**: `/jdbc-model/query-model/v2`

### 请求参数

```typescript
{
  model: 'order_query_model',
  page: 1,
  pageSize: 50,
  slices: JSON.stringify([
    { field: 'status', op: '=', value: 'completed' }
  ]),
  sort: JSON.stringify({ field: 'create_time', order: 'desc' })
}
```

HTTP Header: `X-NS: default`

## 常见用法

### 1. 初始筛选条件

```typescript
// 在父组件中，通过 ref 调用方法后再设置筛选
onMounted(async () => {
  // 组件会自动加载所有数据
})
```

### 2. 修改显示列

编辑 `schemas/order-query-table.schema.ts` 文件：

```typescript
// 只显示部分列
export const columns: EnhancedColumnSchema[] = [
  // 保留需要的列配置
  {
    name: 'order_no',
    type: 'TEXT',
    title: '订单号',
    width: 150,
    filterable: true,
    filterType: 'text'
  },
  {
    name: 'amount',
    type: 'MONEY',
    title: '订单金额',
    width: 120,
    filterable: true,
    filterType: 'number'
  }
  // ... 其他需要的列
]
```

### 3. 自定义列宽

```typescript
{
  name: 'order_no',
  type: 'TEXT',
  title: '订单号',
  width: 200,  // 修改宽度
  minWidth: 150
}
```

### 4. 固定列（左侧）

```typescript
{
  name: 'order_id',
  type: 'INTEGER',
  title: '订单ID',
  width: 100,
  fixed: 'left'  // 固定在左侧
}
```

### 5. 自定义格式化

```typescript
{
  name: 'amount',
  type: 'MONEY',
  title: '订单金额',
  formatter: (value: number) => {
    return value > 10000 ? `¥${value.toFixed(2)}` : `¥${value}`
  }
}
```

### 6. 行点击事件处理

在父组件中捕获事件：

```vue
<template>
  <div>
    <OrderQueryTable @row-click="handleRowClick" />
  </div>
</template>

<script setup lang="ts">
function handleRowClick(row: any, column: any) {
  console.log('点击行:', row)
  // 可以导航到详情页面等
  router.push(`/order/${row.order_id}`)
}
</script>
```

## 常见问题

**Q: 如何改变 API 地址？**
A: 在 `.env` 文件中配置环境变量
```
VUE_APP_API_BASE=http://api.example.com
VUE_APP_NAMESPACE=production
```

**Q: 如何添加新的筛选条件？**
A: 编辑 `schemas/order-query-table.schema.ts` 中的列定义，添加 `filterable: true` 和指定 `filterType`

**Q: 表格数据不更新怎么办？**
A:
1. 检查网络连接和 API 地址
2. 打开浏览器开发者工具查看 Network 标签
3. 确认 API 返回的数据格式是否正确

**Q: 如何导出数据？**
A: 当前组件不支持导出，如需要可以自行在 API 层添加导出接口

## 环境变量

在项目的 `.env` 或 `.env.development` 中配置：

```
# API 基础地址
VUE_APP_API_BASE=http://localhost:8080

# 命名空间
VUE_APP_NAMESPACE=default
```

## 相关组件

- `SearchToolbar` - 独立的搜索工具栏组件
- `DataTable` - 基础数据表格组件
- `DataTableWithSearch` - 组合组件（搜索 + 表格）

## 依赖

- `vue@^3.4.0` - Vue 3 框架
- `foggy-data-viewer@^1.0.0` - 数据表格组件库
- `axios@^1.6.0` - HTTP 客户端
```

---

## 场景 2：基于列需求，推荐模型

### 用户输入

```
我需要一个包含以下列的表格组件：
- 商品ID
- 商品名称
- 商品价格
- 库存数量
- 分类
- 创建时间

我不知道对应的模型名称，请帮我找
```

### 执行流程

1. **第一步**：调用 SemanticController metadata 接口
   ```
   GET /mcp/analyst/metadata?q=商品ID,商品名称,商品价格,库存&ns=default
   ```

2. **第二步**：获取候选模型列表，向用户展示：
   ```
   找到以下匹配的模型，请选择：

   1. product_list_model - 商品列表模型
      ✓ 包含: product_id, product_name, price, stock, category, create_time
      (6/6 列匹配)

   2. product_query_model - 商品查询模型
      ✓ 包含: product_id, product_name, price, stock, category, create_time, update_time
      (7/6 列匹配)

   3. inventory_model - 库存管理模型
      ✓ 包含: product_id, product_name, stock, warehouse_location
      (2/6 列匹配)

   请输入选择（1-3 或直接输入模型名称）:
   ```

3. **第三步**：用户选择后，调用 description-model-internal 获取完整 schema

4. **第四步**：后续流程同"场景 1"

### 最终结果

生成 ProductListTable 组件，自动包含用户需要的所有列。

---

## 场景 3：只显示部分列

### 用户输入

```
基于 customer_model 生成组件
- 组件名称：CustomerTable
- 只显示列：customer_id, customer_name, phone, email
```

### 生成流程

1. 获取完整的 customer_model schema（包含 20+ 列）
2. 向用户确认：检测到模型有以下列，确认只显示指定的 4 列吗？
3. 生成时只在 schema.ts 中包含指定的 4 列
4. 生成的组件会自动使用这 4 列配置

### 生成的 schema 文件

```typescript
export const columns: EnhancedColumnSchema[] = [
  {
    name: 'customer_id',
    type: 'INTEGER',
    title: '客户ID',
    width: 100,
    fixed: 'left'
  },
  {
    name: 'customer_name',
    type: 'TEXT',
    title: '客户名称',
    width: 150
  },
  {
    name: 'phone',
    type: 'TEXT',
    title: '电话',
    width: 150
  },
  {
    name: 'email',
    type: 'TEXT',
    title: '邮箱',
    width: 200
  }
]

export interface CustomerTableRow {
  customer_id: number
  customer_name: string
  phone: string
  email: string
}
```

---

## 场景 4：配置文件缺失，自动填充

### 用户首次使用时

1. 技能检查 `.claude/config/component-generator.config.json` - 不存在
2. 检查 `~/.foggy/component-generator.config.json` - 不存在
3. 向用户询问 4 个必要参数：
   ```
   请提供以下配置信息：

   1️⃣ SemanticController API 地址 [http://localhost:8080]:
   > http://api.company.com

   2️⃣ 命名空间 [default]:
   > production

   3️⃣ 业务组件存放目录 [components]:
   > src/modules/shared

   4️⃣ 组件作者 [Frontend Team]:
   > ZhangSan
   ```

4. 用户输入后，自动保存到 `.claude/config/component-generator.config.json`：
   ```json
   {
     "apiBaseUrl": "http://api.company.com",
     "namespace": "production",
     "commonComponentPath": "modules/shared",
     "componentAuthor": "ZhangSan"
   }
   ```

5. 后续使用自动使用此配置，用户可随时编辑配置文件修改

---

## 场景 5：处理错误情况

### 情况 1：API 地址无法访问

```
❌ 无法连接到 API: http://localhost:8080
请检查：
1. API 地址是否正确
2. 服务是否启动
3. 网络连接是否正常

您可以：
- 编辑配置文件: .claude/config/component-generator.config.json
- 或使用其他 API 地址重试
```

### 情况 2：模型不存在

```
❌ 模型 'order_invalid_model' 不存在

您可以：
1. 检查模型名称拼写
2. 使用列需求搜索合适的模型
3. 查询已有的模型列表
```

### 情况 3：组件已存在

```
⚠️  组件 'OrderQueryTable' 已存在于 src/components/models/

您可以：
1. 选择覆盖（会丢失现有组件）
2. 创建新名称，例如 'OrderQueryTable_v2'
3. 退出并手动删除旧组件

选择操作 (1-3):
```

### 情况 4：模型无数据

```
⚠️  模型 'user_list_model' 返回的 schema 为空

可能原因：
- 模型权限不足
- 模型配置有误
- 命名空间不匹配

建议：
1. 检查命名空间是否正确 (当前: 'default')
2. 确认用户权限
3. 尝试其他模型
```
