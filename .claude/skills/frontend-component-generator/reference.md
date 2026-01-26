# Frontend Component Generator - 技术参考

## 文件生成规范

### 1. 组件文件 ({ComponentName}.vue)

**命名规则**：
- 文件名：PascalCase + .vue（例如 OrderQueryTable.vue）
- 组件名：与文件名同名
- 目录：`src/{commonComponentPath}/models/`

**必须包含的部分**：
- 导入必要的 Vue 组件和类型
- 导入 schema 和 API 层
- 声明响应式状态（data、computed、ref）
- 生命周期钩子（onMounted 用于初始化数据）
- 事件处理方法
- 公开的 API 方法（通过 defineExpose）
- 样式范围隔离（scoped）
- **顶部工具栏区域**（支持 `toolbar` 命名插槽，靠左对齐）
- **分页控件**（靠右对齐，显示总数和分页选项）

**组件布局结构**：
```
┌─────────────────────────────────────────────────┐
│  [toolbar插槽 - 靠左]      [总数] [分页组件 - 靠右] │  <- table-header-bar
├─────────────────────────────────────────────────┤
│  [checkbox] [列1] [列2] ... [操作列(可选)]      │  <- DataTableWithSearch
│  [数据行...]                                    │
└─────────────────────────────────────────────────┘
```

**操作列**：
- 位置：checkbox 右侧，表格最后（fixed: 'right'）
- 宽度：150px（可自定义）
- 显示：通过 `showOperColumn` prop 控制（默认 false）
- 内容：通过 `operColumn` 插槽传递（`:row` 为行数据）

**导入语句示例**：
```typescript
import { ref, onMounted, computed } from 'vue'
import { ElPagination } from 'element-plus'
import { DataTableWithSearch } from 'foggy-data-viewer'
import type { EnhancedColumnSchema, SliceRequestDef } from 'foggy-data-viewer'
import { columns } from './schemas/{componentName}.schema'
import { fetch{ComponentName}Data } from './apis/{componentName}.api'
```

**必须实现的方法**：
- `loadData()` - 加载数据
- `handlePaginationChange()` - 分页组件变化（页数或每页条数）
- `handlePageChange(page: number, pageSize: number)` - 表格分页变化
- `handleFilterChange(filters: SliceRequestDef[])` - 筛选变化
- `handleSortChange(field: string, order: string)` - 排序变化
- `refresh()` - 刷新数据
- `clearFilters()` - 清空筛选

**可选的方法**：
- `handleRowClick(row: any, column: any)` - 行点击事件
- `exportData()` - 导出数据（如需要）

### 2. Schema 配置文件 (schemas/{componentName}.schema.ts)

**命名规则**：
- 文件名：kebab-case + .schema.ts（例如 order-query-table.schema.ts）
- 导出常量：`columns`
- 导出类型：`{ComponentName}Row` 或 `{ComponentName}Item`

**必须包含的内容**：
1. 列配置数组（columns）
2. 行数据类型定义（interface {ComponentName}Row）
3. 筛选器配置（如需自定义）

**列配置字段映射**：

| 后台 field | 映射到 schema | 说明 |
|-----------|--------------|------|
| name | name | 列字段名 |
| type | type | 数据类型（INTEGER、TEXT、MONEY 等） |
| title | title | 列标题 |
| description | title（备用）| 列描述 |
| aggregatable | aggregatable | 是否支持汇总 |
| sortable | sortable | 是否支持排序 |
| (自定义) | width | 列宽（根据类型推荐） |
| (自定义) | filterable | 是否可筛选 |
| (自定义) | filterType | 筛选器类型（text、number、date、select、bool） |

**类型映射规则**：

| type | filterType | defaultWidth | 说明 |
|-----|-----------|--------------|------|
| INTEGER | number | 100 | 整数 |
| BIGINT | number | 120 | 大整数 |
| MONEY | number | 120 | 金额（右对齐） |
| NUMBER | number | 100 | 浮点数 |
| TEXT | text | 150 | 文本 |
| STRING | text | 150 | 字符串 |
| DAY | date | 120 | 日期（YYYY-MM-DD） |
| DATETIME | date | 180 | 日期时间（YYYY-MM-DD HH:mm:ss） |
| BOOL | bool | 100 | 布尔值 |
| DICT | select | 120 | 字典（需要 options） |

**示例**：
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
    name: 'amount',
    type: 'MONEY',
    title: '金额',
    width: 120,
    filterable: true,
    filterType: 'number',
    aggregatable: true,
    formatter: (value: number) => `¥${value.toFixed(2)}`
  },
  {
    name: 'create_time',
    type: 'DATETIME',
    title: '创建时间',
    width: 180,
    filterable: true,
    filterType: 'date',
    sortable: true
  }
]

export interface OrderQueryTableRow {
  order_id: number
  order_no: string
  amount: number
  status: string
  create_time: string
  update_time: string
  customer_name: string
}
```

### 3. API 封装文件 (apis/{componentName}.api.ts)

**命名规则**：
- 文件名：kebab-case + .api.ts（例如 order-query-table.api.ts）
- 导出函数：`fetch{ComponentName}Data`
- 导出类型：`{ComponentName}QueryRequest`, `{ComponentName}QueryResponse`

**必须包含的内容**：
1. API 基础 URL 配置
2. 请求类型定义（QueryRequest）
3. 响应类型定义（QueryResponse）
4. 查询函数实现
5. 错误处理

**标准结构**：
```typescript
import axios from 'axios'
import type { SliceRequestDef } from 'foggy-data-viewer'

const API_BASE = process.env.VUE_APP_API_BASE || 'http://localhost:8080'
const NAMESPACE = process.env.VUE_APP_NAMESPACE || 'default'
const AUTHORIZATION = process.env.VUE_APP_AUTHORIZATION || '' // 可选的授权 token

export interface {ComponentName}QueryRequest {
  page: number
  pageSize: number
  filters?: SliceRequestDef[]
  sort?: { field: string; order: 'asc' | 'desc' }
}

export interface {ComponentName}QueryResponse {
  rows: any[]
  total: number
}

export async function fetch{ComponentName}Data(
  request: {ComponentName}QueryRequest
): Promise<{ComponentName}QueryResponse> {
  // 构建 headers
  const headers: any = {
    'X-NS': NAMESPACE
  }

  // 如果配置了授权信息，添加到 header
  if (AUTHORIZATION) {
    headers['Authorization'] = AUTHORIZATION
  }

  // 实现具体逻辑
}
```

**查询函数需要处理**：
1. 请求参数构建（page、pageSize、filters、sort）
2. HTTP Header 设置（X-NS 命名空间、可选的 Authorization）
3. 响应验证（code === 200）
4. 错误转换和提示
5. 数据转换（如需要）

### 4. README 文档文件

**路径**：`src/{commonComponentPath}/models/README.md`

**必须包含的部分**：

1. **头部信息**
   - 组件标题（# {ComponentName}）
   - 组件描述（一句话功能说明）
   - 元数据：作者、创建时间、基于模型、版本

2. **功能特性**（Markdown 列表）
   - 自动集成的功能
   - 支持的特性
   - 关键点亮点

3. **安装和使用**
   - 基本导入示例
   - 最简单的使用方式
   - Props 表格说明
   - Events 表格说明

4. **API 文档**
   - 后台模型名称
   - 所用命名空间
   - 数据接口说明
   - 请求/响应格式

5. **自定义示例**
   - 修改列配置
   - 添加自定义格式化
   - 隐藏特定列
   - 修改列宽和位置

6. **常见问题**
   - Q&A 格式
   - 常见错误及解决方案

## 配置文件规范

### component-generator.config.json

**位置**：`.claude/config/component-generator.config.json` 或 `~/.foggy/component-generator.config.json`

**结构**：
```json
{
  "apiBaseUrl": "http://localhost:8080",
  "namespace": "default",
  "commonComponentPath": "components",
  "componentAuthor": "Frontend Team",
  "authorization": ""
}
```

**字段说明**：

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| apiBaseUrl | string | SemanticController 基础 URL | `http://localhost:8080` |
| namespace | string | 默认命名空间（HTTP Header X-NS） | `default` |
| commonComponentPath | string | 业务组件存放目录（相对 src/） | `components` |
| componentAuthor | string | 组件作者名称 | `Frontend Team` |
| authorization | string | 授权 token（可选，留空则不使用） | `Bearer xxx` 或 JWT token |

**authorization 字段说明**：
- 用途：用于后台有权限控制的场景
- 可选：可以留空（`""`）或完全不设置
- 格式：支持任意格式，通常为 `Bearer xxx` 或 JWT token
- 使用：如果设置，会在所有 SemanticController API 请求的 `Authorization` header 中使用

## 筛选器类型映射

### TextFilter（文本筛选）
- 适用类型：TEXT, STRING
- 支持的操作：=（等于）、like（模糊）、in（批量）
- 用户界面：输入框 + 下拉操作符选择

### NumberRangeFilter（数字范围筛选）
- 适用类型：INTEGER, BIGINT, MONEY, NUMBER
- 支持的操作：=、>=、<=、between
- 用户界面：数字输入框 + 范围选择

### DateRangeFilter（日期范围筛选）
- 适用类型：DAY, DATETIME
- 支持的操作：=、>=、<=、between
- 用户界面：日期选择器 + 范围选择

### SelectFilter（选择筛选）
- 适用类型：DICT、有预定义值的TEXT
- 支持的操作：=（单选）、in（多选）
- 用户界面：下拉框 + 多选

### BoolFilter（布尔筛选）
- 适用类型：BOOL
- 支持的操作：=
- 用户界面：三态选择（是/否/全部）

## 列宽推荐值

基于数据类型的推荐默认宽度：

| 类型 | 推荐宽度 | 最小宽度 | 说明 |
|-----|---------|--------|------|
| INTEGER | 80-100 | 60 | 整数 ID |
| BIGINT | 100-120 | 80 | 大整数 |
| MONEY | 120 | 100 | 金额（需要空间显示符号） |
| NUMBER | 100 | 80 | 浮点数 |
| TEXT (< 20) | 120 | 100 | 短文本（名称、编码） |
| TEXT (> 20) | 150-200 | 120 | 长文本（描述、备注） |
| DAY | 120 | 100 | 日期 |
| DATETIME | 180 | 150 | 日期时间 |
| BOOL | 80 | 60 | 布尔值 |

## 错误处理规范

### API 调用错误

1. **网络错误**
   - 显示：服务器连接失败，请检查网络和 API 地址
   - 建议用户重试或检查配置

2. **API 返回非 200**
   - 显示：获取 response.data.msg
   - 记录：完整的错误响应用于调试

3. **模型不存在或 schema 为空**
   - 显示：模型 xxx 不存在或无可用字段
   - 建议用户检查模型名称或搜索其他模型

### 文件生成错误

1. **目录不存在**
   - 自动创建必要的目录结构

2. **文件已存在**
   - 询问用户：是否覆盖、备份、或选择新名称

3. **无写入权限**
   - 显示错误信息，建议检查目录权限

## 环境变量支持

生成的组件和 API 层应支持以下环境变量（用于不同环境切换）：

```typescript
// 在 .env, .env.development, .env.production 中配置
VUE_APP_API_BASE=http://localhost:8080
VUE_APP_NAMESPACE=default
```

## 类型导出规范

### 从组件导出

```typescript
// 在 src/{commonComponentPath}/models/index.ts 中
export { default as OrderQueryTable } from './OrderQueryTable.vue'
export type { OrderQueryTableRow } from './schemas/order-query-table.schema'
export { fetchOrderQueryTableData } from './apis/order-query-table.api'
```

### 供外部使用

```typescript
// 其他组件中使用
import { OrderQueryTable, type OrderQueryTableRow } from '@/{commonComponentPath}/models'
```

## 与 foggy-data-viewer 的集成

### Slots（插槽）说明

生成的组件提供以下命名插槽供父组件使用：

#### toolbar 插槽

**位置**：表格顶部左侧，用于放置自定义操作按钮和组件

**使用示例**：
```vue
<template>
  <OrderQueryTable>
    <template #toolbar>
      <el-button type="primary" size="small">新增</el-button>
      <el-button type="default" size="small">编辑</el-button>
      <el-button type="danger" size="small">删除</el-button>
      <el-divider direction="vertical" />
      <el-button type="info" size="small">导出</el-button>
    </template>
  </OrderQueryTable>
</template>
```

**特性**：
- 支持任意 Vue 组件
- 自动布局为 Flex 行，间距 8px
- 宽度自适应，不会被压缩
- 与右侧分页控件通过 gap 16px 分离

#### operColumn 插槽

**位置**：表格操作列，checkbox 右侧，表格最后（fixed: 'right'）

**启用方式**：在父组件中通过 `v-model:showOperColumn="true"` 或 `:showOperColumn="true"` 启用

**使用示例**：
```vue
<template>
  <OrderQueryTable :show-oper-column="true">
    <template #operColumn="{ row }">
      <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
      <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
    </template>
  </OrderQueryTable>
</template>

<script setup lang="ts">
function handleEdit(row: any) {
  console.log('编辑行:', row)
}

function handleDelete(row: any) {
  console.log('删除行:', row)
}
</script>
```

**特性**：
- `row` 作用域插槽参数包含完整的行数据
- 宽度：150px（可在组件中修改 operColumnSchema）
- 固定位置：right（始终显示在右侧）
- 条件显示：仅当 `showOperColumn` 为 true 时显示

#### 页面级别使用示例

```vue
<template>
  <div class="page-wrapper">
    <OrderQueryTable ref="tableRef">
      <!-- toolbar 插槽 - 放在 <template #toolbar> 中 -->
      <template #toolbar>
        <el-button-group>
          <el-button type="primary" size="small" @click="handleAddNew">
            <el-icon><Plus /></el-icon>新增
          </el-button>
          <el-button type="success" size="small" @click="handleEdit">
            <el-icon><Edit /></el-icon>编辑
          </el-button>
          <el-button type="danger" size="small" @click="handleDelete">
            <el-icon><Delete /></el-icon>删除
          </el-button>
        </el-button-group>

        <el-divider direction="vertical" />

        <el-button type="info" size="small" @click="handleExport">
          <el-icon><Download /></el-icon>导出
        </el-button>
      </template>
    </OrderQueryTable>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import OrderQueryTable from '@/components/models/OrderQueryTable.vue'

const tableRef = ref()

function handleAddNew() {
  // 打开新增对话框
  console.log('新增记录')
}

function handleEdit() {
  // 获取选中行，打开编辑对话框
  console.log('编辑记录')
}

function handleDelete() {
  // 删除选中行
  console.log('删除记录')
}

function handleExport() {
  // 调用导出接口
  tableRef.value?.refresh() // 刷新表格
}
</script>
```

### DataTableWithSearch Props 说明

生成的组件应支持以下关键 props：

```typescript
{
  columns: EnhancedColumnSchema[]     // 从 schema 导入
  data: any[]                         // 从 API 获取
  total: number                       // API 返回的总数
  loading: boolean                    // 加载状态
  pageSize: number                    // 每页条数，默认 50
  showFilters: boolean                // 是否显示筛选器，默认 true
  showSearchToolbar: boolean          // 是否显示搜索工具栏，默认 true
  initialSlice: SliceRequestDef[]     // 初始筛选条件
}
```

### 生成的组件 Props 说明

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| showOperColumn | `boolean` | `false` | 是否显示操作列 |

### 必须实现的事件处理

```typescript
@page-change="(page, pageSize) => { /* 重新加载数据 */ }"
@filter-change="(filters) => { /* 重新加载数据 */ }"
@sort-change="(field, order) => { /* 处理排序 */ }"
@row-click="(row, column) => { /* 可选：行点击事件 */ }"
```
