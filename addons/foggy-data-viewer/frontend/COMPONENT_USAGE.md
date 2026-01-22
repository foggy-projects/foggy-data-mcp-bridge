# DataTable 组件使用文档

## 简介

DataTable 是一个基于 vxe-table 的增强型数据表格组件，支持：
- ✅ 后端 QM Schema 与前端定制参数合并
- ✅ 自定义列宽、固定列、显示顺序
- ✅ 自定义格式化器（formatter）和渲染器（render）
- ✅ 内嵌表头过滤器
- ✅ 服务端分页、排序、过滤
- ✅ 行选择和汇总统计

## 快速开始

### 1. 安装依赖

```bash
npm install vue vxe-table xe-utils
```

### 2. Schema 结构说明

后端返回的 `ColumnSchema` 结构：

```typescript
interface ColumnSchema {
  name: string                // 列名
  type: string                // 数据类型：INTEGER, TEXT, MONEY, DATETIME, BOOL 等
  title?: string              // 列标题（显示名称）
  filterable?: boolean        // 是否可过滤
  aggregatable?: boolean      // 是否可聚合

  // 过滤器元数据
  filterType?: 'text' | 'number' | 'date' | 'datetime' | 'dict' | 'dimension' | 'bool' | 'custom'
  dictId?: string             // 字典ID
  dictItems?: DictItem[]      // 字典项列表
  dimensionRef?: string       // 维度引用
  format?: string             // 格式化字符串
  measure?: boolean           // 是否为度量
  uiConfig?: Record<string, unknown>  // UI配置
}

interface DictItem {
  value: string | number      // 字典值
  label: string               // 字典标签
}
```

### 3. 获取 Schema 的方式

#### 方式1: 从 data-viewer 获取（推荐用于已缓存的查询）

```typescript
import axios from 'axios'

// 获取已缓存查询的 schema
async function fetchQMSchema(queryId: string) {
  const response = await axios.get(`/data-viewer/api/query/${queryId}/meta`)
  return response.data.schema  // 返回 ColumnSchema[]
}
```

#### 方式2: 从 SemanticController 获取（推荐用于直接获取 QM 模型）

```typescript
import axios from 'axios'

// 根据 QM 模型名称获取完整的字段元数据
async function fetchQMSchemaByModel(modelName: string) {
  const response = await axios.get(
    `/semantic/v1/description-model-internal/${modelName}`,
    { params: { format: 'json' } }
  )

  // 需要将 SemanticMetadataResponse 转换为 ColumnSchema[]
  return convertToColumnSchema(response.data)
}

// 转换函数（根据实际返回结构调整）
function convertToColumnSchema(semanticResponse: any): ColumnSchema[] {
  // 根据 SemanticMetadataResponse 的实际结构进行转换
  // 这里需要根据你的实际数据结构实现
  return semanticResponse.fields.map(field => ({
    name: field.name,
    type: field.type,
    title: field.title,
    filterType: inferFilterType(field),
    // ... 其他字段映射
  }))
}
```

### 4. 基本使用

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DataTable from '@/components/DataTable.vue'
import { buildTableColumns } from '@/utils/schemaHelper'
import type { TableConfig } from '@/types'

// 定义表格配置
const config: TableConfig = {
  // 必须显式指定显示的列及顺序
  visibleColumns: ['id', 'name', 'amount', 'status'],

  // 列定制
  customizations: [
    { name: 'id', width: 150, fixed: 'left' },
    { name: 'amount', formatter: (v) => `¥${Number(v).toFixed(2)}` }
  ]
}

const columns = ref([])
const data = ref([])
const total = ref(0)
const loading = ref(false)

onMounted(async () => {
  // 方式1: 从 data-viewer 获取（用于已缓存的查询）
  const qmSchema = await fetchQMSchema('query-id-123')

  // 或方式2: 从 SemanticController 获取（用于直接获取 QM 模型）
  // const qmSchema = await fetchQMSchemaByModel('OrderModel')

  // 合并 schema 和定制配置
  columns.value = buildTableColumns(qmSchema, config)

  // 加载数据
  const response = await fetchData()
  data.value = response.items
  total.value = response.total
})
</script>

<template>
  <DataTable
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
    @page-change="handlePageChange"
    @sort-change="handleSortChange"
    @filter-change="handleFilterChange"
  />
</template>
```

## API 端点说明

### data-viewer API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/data-viewer/api/query/{queryId}/meta` | GET | 获取已缓存查询的元数据（包含 schema） |
| `/data-viewer/api/query/{queryId}/data` | POST | 执行查询并返回数据 |
| `/data-viewer/api/query/{queryId}/filter-options/{columnName}` | GET | 获取维度/字典的过滤选项 |

### SemanticController API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/semantic/v1/description-model-internal/{model}` | GET/POST | 获取指定 QM 模型的完整字段元数据 |
| `/semantic/v1/query-model/v2/{model}` | POST | 执行语义查询 |

**推荐使用场景**：
- 如果你已经有 `queryId`（通过 data-viewer 缓存的查询），使用 data-viewer API
- 如果你想直接根据 QM 模型名称获取 schema，使用 SemanticController API

## 核心概念

### TableConfig 配置

```typescript
interface TableConfig {
  // 显示的列及顺序（必填，除非 showAll=true）
  visibleColumns?: string[]

  // 显示所有列
  showAll?: boolean

  // 列定制配置
  customizations?: ColumnCustomization[]
}
```

**重要规则**：
- 必须指定 `visibleColumns` 或 `showAll`，否则会抛出错误
- `visibleColumns` 数组的顺序就是表格列的显示顺序

### ColumnCustomization 配置

```typescript
interface ColumnCustomization {
  name: string              // 列名（必填）
  width?: number            // 列宽
  minWidth?: number         // 最小列宽
  fixed?: 'left' | 'right'  // 固定列
  formatter?: (value: unknown) => string  // 格式化器（用于导出）
  render?: (params: { row: Record<string, unknown>; value: unknown }) => VNode | string  // 渲染器（用于显示）
  filterComponent?: Component  // 自定义过滤器组件
}
```

### formatter vs render

| 属性 | 用途 | 返回值 | 影响范围 | 修改原始数据 |
|------|------|--------|----------|------------|
| **formatter** | 数据格式化 | `string` | 显示 + 导出 | ❌ 否 |
| **render** | 自定义渲染 | `VNode \| string` | 仅显示 | ❌ 否 |

**关键点**：
- formatter 和 render **都不会修改原始数据**
- 通过 vxe-table API 获取的行数据是**原始数据**，不包含 format 后的值
- 有 formatter 一般不需要 render，但不绝对
- 如果同时存在，render 用于显示，formatter 用于导出

## 使用场景

### 场景 1: 显式指定列及顺序

```typescript
const config: TableConfig = {
  visibleColumns: ['orderId', 'customerName', 'amount', 'status'],
  customizations: [
    { name: 'orderId', width: 150, fixed: 'left' },
    { name: 'amount', width: 120 }
  ]
}
```

### 场景 2: 显示所有列

```typescript
const config: TableConfig = {
  showAll: true,  // 按 QM schema 顺序显示所有列
  customizations: [
    { name: 'orderId', width: 150 }
  ]
}
```

### 场景 3: 格式化金额

```typescript
{
  name: 'amount',
  formatter: (value) => `¥${Number(value).toFixed(2)}`
}
```

### 场景 4: 布尔值渲染成符号

```typescript
import { h } from 'vue'

{
  name: 'isPaid',
  render: ({ value }) => h('span', {
    style: { fontSize: '18px', color: value ? '#67c23a' : '#f56c6c' }
  }, value ? '✓' : '✗')
}
```

### 场景 5: 状态带颜色显示

```typescript
{
  name: 'status',
  render: ({ value }) => {
    const colors = {
      success: '#67c23a',
      warning: '#e6a23c',
      error: '#f56c6c'
    }
    return h('span', {
      style: { color: colors[value as string], fontWeight: 'bold' }
    }, value)
  }
}
```

### 场景 6: JSON 导出为文字

```typescript
{
  name: 'metadata',
  formatter: (value) => {
    if (typeof value === 'object') {
      return JSON.stringify(value)
    }
    return String(value)
  }
}
```

### 场景 7: 同时使用 formatter 和 render

```typescript
{
  name: 'price',
  // 导出时格式化为文字
  formatter: (v) => `¥${Number(v).toFixed(2)}`,
  // 显示时添加样式
  render: ({ value }) => h('span', {
    style: { fontWeight: 'bold', color: '#e6a23c' }
  }, `¥${Number(value).toFixed(2)}`)
}
```

## 组件 Props

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| columns | EnhancedColumnSchema[] | 是 | - | 列配置（通过 buildTableColumns 生成） |
| data | Record<string, unknown>[] | 是 | - | 表格数据 |
| total | number | 是 | - | 总行数 |
| loading | boolean | 是 | - | 加载状态 |
| pageSize | number | 否 | 50 | 每页大小 |
| showFilters | boolean | 否 | true | 是否显示过滤行 |
| initialSlice | SliceRequestDef[] | 否 | - | 初始过滤条件 |
| serverSummary | Record<string, unknown> | 否 | - | 后端返回的汇总数据 |
| filterOptionsLoader | Function | 否 | - | 过滤选项加载器 |

## 组件 Events

| 事件 | 参数 | 说明 |
|------|------|------|
| page-change | (page: number, size: number) | 分页变化 |
| sort-change | (field: string \| null, order: 'asc' \| 'desc' \| null) | 排序变化 |
| filter-change | (slices: SliceRequestDef[]) | 过滤条件变化 |
| row-click | (row: Record<string, unknown>, column: EnhancedColumnSchema) | 行点击 |
| row-dblclick | (row: Record<string, unknown>, column: EnhancedColumnSchema) | 行双击 |

## 组件方法

通过 ref 访问组件实例方法：

```typescript
const tableRef = ref<InstanceType<typeof DataTable>>()

// 重置分页
tableRef.value?.resetPagination()

// 清除所有过滤
tableRef.value?.clearFilters()

// 获取当前过滤状态
const filters = tableRef.value?.getFilters()

// 设置过滤值
tableRef.value?.setFilter('columnName', slices)

// 获取 vxe-grid 实例
const gridInstance = tableRef.value?.getGridInstance()
```

## 完整示例

```vue
<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import DataTable from '@/components/DataTable.vue'
import { fetchQMSchema, fetchQueryData } from '@/api'
import { buildTableColumns } from '@/utils/schemaHelper'
import type { TableConfig, SliceRequestDef } from '@/types'

const tableConfig: TableConfig = {
  visibleColumns: ['orderId', 'orderDate', 'customerName', 'amount', 'status', 'isPaid'],
  customizations: [
    {
      name: 'orderId',
      width: 150,
      fixed: 'left'
    },
    {
      name: 'orderDate',
      width: 120
    },
    {
      name: 'customerName',
      width: 150
    },
    {
      name: 'amount',
      width: 120,
      formatter: (value) => `¥${Number(value).toFixed(2)}`
    },
    {
      name: 'status',
      width: 100,
      render: ({ value }) => {
        const statusMap: Record<string, { text: string; color: string }> = {
          paid: { text: '已支付', color: '#67c23a' },
          pending: { text: '待支付', color: '#e6a23c' },
          cancelled: { text: '已取消', color: '#909399' }
        }
        const status = statusMap[value as string] || { text: value as string, color: '#909399' }
        return h('span', { style: { color: status.color, fontWeight: 'bold' } }, status.text)
      }
    },
    {
      name: 'isPaid',
      width: 80,
      render: ({ value }) => {
        return h('span', {
          style: { fontSize: '18px', color: value ? '#67c23a' : '#f56c6c' }
        }, value ? '✓' : '✗')
      }
    }
  ]
}

const columns = ref([])
const data = ref([])
const total = ref(0)
const loading = ref(false)
const tableRef = ref()

const queryParams = ref({
  start: 0,
  limit: 50,
  slice: [],
  orderBy: []
})

async function loadData() {
  try {
    loading.value = true
    const response = await fetchQueryData('order-query', queryParams.value)
    data.value = response.items
    total.value = response.total
  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number, size: number) {
  queryParams.value.start = (page - 1) * size
  queryParams.value.limit = size
  loadData()
}

function handleSortChange(field: string | null, order: 'asc' | 'desc' | null) {
  if (field && order) {
    queryParams.value.orderBy = [{ field, order }]
  } else {
    queryParams.value.orderBy = []
  }
  loadData()
}

function handleFilterChange(slices: SliceRequestDef[]) {
  queryParams.value.slice = slices
  queryParams.value.start = 0
  tableRef.value?.resetPagination()
  loadData()
}

onMounted(async () => {
  const qmSchema = await fetchQMSchema('OrderModel')
  columns.value = buildTableColumns(qmSchema, tableConfig)
  await loadData()
})
</script>

<template>
  <div class="page-container">
    <DataTable
      ref="tableRef"
      :columns="columns"
      :data="data"
      :total="total"
      :loading="loading"
      @page-change="handlePageChange"
      @sort-change="handleSortChange"
      @filter-change="handleFilterChange"
    />
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}
</style>
```

## 常见问题

### Q1: 为什么必须显式指定 visibleColumns？

**A**: 这是设计上的要求，确保前端对列的显示有完全的控制权。如果不想逐个指定，可以使用 `showAll: true`。

### Q2: formatter 会修改原始数据吗？

**A**: 不会。formatter 只影响显示和导出，原始数据保持不变。

### Q3: 通过 vxe-table API 获取的行数据包含 format 后的值吗？

**A**: 不包含。API 返回的是原始数据对象。

### Q4: 如何同时使用 formatter 和 render？

**A**: 可以同时配置，render 用于显示，formatter 用于导出。

### Q5: 如何获取格式化后的值？

**A**: 手动调用 formatter 函数：
```typescript
const formattedValue = customFormatter(row.amount)
```

## 类型定义

完整的类型定义请参考 `src/types/index.ts`。

## 更多示例

更多示例请参考 `src/examples/EnhancedTableExample.vue`。
