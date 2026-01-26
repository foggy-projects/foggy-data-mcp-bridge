---
name: frontend-component-generator
description: 自动生成基于 foggy-data-viewer 的 Vue 业务组件。根据 QM 模型自动拉取 schema、生成组件代码、配置文件和文档。当用户需要快速创建数据表格业务组件时使用。
---

# Frontend Component Generator

根据后台 QM 模型自动生成完整的 Vue 3 业务组件（包含组件代码、schema 配置、API 封装、类型定义、使用文档）。

## 使用场景

当用户需要以下操作时使用：
- 基于 QM 模型快速生成数据表格组件
- 集成 foggy-data-viewer 的 DataTableWithSearch 组件
- 自动拉取模型 schema 并配置表格列
- 生成 API 接口层和 TypeScript 类型定义
- 创建组件使用文档和示例

## 依赖技能

- `qm-schema-viewer` - 获取 QM 模型 schema 信息
- `frontend-dsl-query` - 生成公共 DSL 查询 API

## 前置条件

使用本技能前，请确保已运行 `foggy-frontend-init` 完成环境初始化：
- 已安装 `foggy-data-viewer@beta` 和 `axios`
- 已创建 `.claude/config/semantic-api.config.json`
- 已生成 `src/apis/common/dslQuery.ts`

如果环境未就绪，请先运行 `/foggy-frontend-init`。

## 执行流程

### 第一步：检查环境

快速检查（不安装，仅验证）：
- 检查 `package.json` 中是否有 `foggy-data-viewer`
- 检查 `package.json` 中是否有 `vxe-table`、`vxe-pc-ui`、`xe-utils`
- 检查 `src/apis/common/dslQuery.ts` 是否存在
- ⭐ **检查 `src/main.js` 或 `src/main.ts` 是否包含 VXETable 注册** ⬅️ 关键检查
- 检查 `.claude/config/semantic-api.config.json` 是否存在

**检查 VXETable 配置**：

读取 `src/main.js` 或 `src/main.ts`，验证是否包含以下三项：
1. `import VXETable from 'vxe-table'`
2. `import 'foggy-data-viewer/style.css'`
3. `app.use(VXETable)`

**如果基础环境缺失** → 提示用户先运行 `/foggy-frontend-init`

**如果 VXETable 未注册** → 显示警告并提供配置代码：

```
⚠️ 警告：检测到 VXETable 未全局注册

foggy-data-viewer 依赖 VXETable 表格引擎，必须在 main.js 中注册，否则生成的表格组件无法显示。

请在 src/main.js 中添加以下代码：

import VXETable from 'vxe-table'
import 'foggy-data-viewer/style.css'

app.use(VXETable)

或运行 /foggy-frontend-init 自动完成配置。

是否继续生成组件？[y/N]
```

### 第二步：读取配置文件

1. 读取通用配置：`.claude/config/semantic-api.config.json`
2. 读取组件生成器专用配置：`.claude/config/component-generator.config.json`
3. 如果组件配置不存在，询问用户（见下方）

### 第三步：配置交互流程（仅当组件配置不存在时）

**通用配置** `.claude/config/semantic-api.config.json`（与其他技能共用）：
```json
{
  "apiBaseUrl": "http://localhost:7108",
  "namespace": "default",
  "authorization": ""
}
```

**组件生成器专用配置** `.claude/config/component-generator.config.json`：
```json
{
  "commonComponentPath": "components",
  "componentAuthor": "Frontend Team"
}
```

询问用户：
1. **通用组件存放目录**（默认 `components`）
2. **组件作者**（默认 `Frontend Team`）

### 第四步：获取模型信息

使用 `qm-schema-viewer` 技能获取模型 schema：

**方式A：已知模型名称**
- 用户直接提供 QM 模型名称
- 调用 qm-schema-viewer 获取 schema

**方式B：未知模型，基于列需求搜索**
- 用户描述需要的列
- 调用 qm-schema-viewer 搜索匹配的模型
- 向用户展示候选模型列表

### 第五步：确认生成配置

向用户展示以下选项并确认：

1. **选择显示的列**
   - 默认：显示所有列
   - 选项：允许用户指定特定列子集

2. **选择组件类型**
   - 默认：`DataTableWithSearch`
   - 可选：`DataTable`、`SearchToolbar`

3. **组件名称**
   - 问题：请提供生成的组件名称（PascalCase，例如 `OrderQueryTable`）
   - 验证：不能与现有组件重名

4. **组件描述**（用于文档）

### 第六步：生成组件文件

根据用户确认的配置生成以下文件：

#### 6.1 主组件文件
**路径**：`src/{commonComponentPath}/models/{ComponentName}.vue`

生成的组件包含：
- 导入 foggy-data-viewer 组件和类型定义
- 导入本项目的 schema 配置和 API 层
- 定义数据响应式状态（分页、加载、排序、筛选）
- 实现数据加载逻辑（集成 API 层）
- 事件处理（分页变化、排序变化、筛选变化、行点击）
- 公开的方法（刷新数据、重置筛选等）
- **顶部工具栏区域**（支持插槽，靠左对齐，用户可添加自定义按钮等）
- **分页控件**（靠右对齐，显示总数和分页选项）
- **操作列**（checkbox 右边，支持通过插槽自定义操作按钮，可通过 Props 控制显示/隐藏）

示例结构：
```vue
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElPagination } from 'element-plus'
import { DataTableWithSearch } from 'foggy-data-viewer'
import type { EnhancedColumnSchema, SliceRequestDef } from 'foggy-data-viewer'
import { columns } from './schemas/{componentName}.schema'
import { fetchOrderData } from './apis/{componentName}.api'

// 数据状态
const data = ref([])
const total = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(50)
const currentFilters = ref<SliceRequestDef[]>([])

// Props
const showOperColumn = ref(false) // 是否显示操作列

// 计算操作列配置
const operColumnSchema: EnhancedColumnSchema = {
  name: '__oper__',
  type: 'TEXT',
  title: '操作',
  width: 150,
  fixed: 'right',
  // render 函数由组件使用者通过插槽控制
}

// 合并列配置（如果显示操作列）
const displayColumns = computed(() => {
  if (showOperColumn.value) {
    return [...columns, operColumnSchema]
  }
  return columns
})

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const response = await fetchOrderData({
      page: currentPage.value,
      pageSize: pageSize.value,
      filters: currentFilters.value
    })
    data.value = response.rows
    total.value = response.total
  } finally {
    loading.value = false
  }
}

// 分页变化（来自分页组件）
function handlePaginationChange() {
  currentPage.value = 1 // 切换每页条数时重置到第一页
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
  currentPage.value = 1 // 筛选时重置到第一页
  loadData()
}

// 排序变化
function handleSortChange(field: string, order: string) {
  // 如需后台排序，在此实现
  console.log(`Sorting by ${field} ${order}`)
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

#### 6.2 Schema 配置文件
**路径**：`src/{commonComponentPath}/models/schemas/{componentName}.schema.ts`

生成包含：
- 从后台 schema 映射的列配置
- 列的宽度、排序、筛选器类型等定制配置
- 类型定义导出

示例结构：
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
    filterType: 'number'
  },
  {
    name: 'order_no',
    type: 'TEXT',
    title: '订单号',
    width: 150,
    filterable: true,
    filterType: 'text'
  },
  // ... 其他列配置
]

export interface OrderQueryRow {
  order_id: number
  order_no: string
  // ... 其他字段类型定义
}
```

#### 6.3 API 封装层
**路径**：`src/{commonComponentPath}/models/apis/{componentName}.api.ts`

生成包含：
- 基于公共 dslQuery 的业务封装
- 类型定义（根据 schema 生成）

示例结构：
```typescript
import { query, type SliceRequestDef } from '@/apis/common/dslQuery'
import type { OrderQueryRow } from '../schemas/{componentName}.schema'

export interface QueryParams {
  page?: number
  pageSize?: number
  filters?: SliceRequestDef[]
}

/**
 * 查询订单数据
 */
export async function fetchOrderData(params: QueryParams): Promise<{
  rows: OrderQueryRow[]
  total: number
}> {
  const result = await query<OrderQueryRow>('OrderQueryModel', {
    columns: ['orderId', 'orderNo', 'amount', 'status', 'createTime'],
    filters: params.filters,
    orderBy: ['-createTime'],
    page: params.page || 1,
    pageSize: params.pageSize || 50,
  })

  return {
    rows: result.items,
    total: result.total,
  }
}
```

#### 6.4 使用文档
**路径**：`src/{commonComponentPath}/models/README.md`

生成包含：
- 组件简介（名称、作者、创建日期、描述）
- 功能特性列表
- 快速开始（引入和基本使用）
- Props 和 Events 列表
- 常见用法示例
- API 接口说明
- 自定义列配置示例
- 常见问题 FAQ

示例结构：
```markdown
# {ComponentName}

{组件描述}

**作者**: {componentAuthor}
**创建时间**: {currentDate}
**基于模型**: {modelName}

## 功能特性

- 自动集成数据表格和搜索工具栏
- 支持分页、排序、多条件筛选
- 完整的 TypeScript 类型定义
- 响应式列配置

## 快速开始

### 基本使用

\`\`\`vue
<template>
  <OrderQueryTable />
</template>

<script setup lang="ts">
import OrderQueryTable from '@/{commonComponentPath}/models/OrderQueryTable.vue'
</script>
\`\`\`

### Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| loading | `boolean` | `false` | 加载状态 |
| pageSize | `number` | `50` | 每页显示条数 |

## API 接口

本组件使用 Foggy Dataset Model 的查询接口：

- **端点**: `POST /jdbc-model/query-model/v2/{QueryModelName}`
- **完整URL示例**: `http://localhost:8080/jdbc-model/query-model/v2/OrderQueryModel`
- **命名空间**: 通过后端配置文件指定（默认 `default`）

### 请求格式

```json
{
  "param": {
    "columns": ["column1", "column2"],
    "sliceRequest": [
      {
        "column": "status",
        "operator": "eq",
        "value": "ACTIVE"
      }
    ],
    "orderBy": ["-createdAt"]
  },
  "page": 1,
  "pageSize": 50
}
```

### Vite 代理配置（必需）

开发环境下需要配置 Vite 代理转发：

```javascript
// vite.config.js
import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    proxy: {
      '/jdbc-model': {
        target: 'http://localhost:8080',  // 后端服务地址
        changeOrigin: true
      }
    }
  }
})
```

### 生产环境配置

生产环境通过 Nginx 或其他反向代理配置路由。

## 常见问题

Q: 如何修改显示的列？
A: 编辑 `schemas/{componentName}.schema.ts` 文件中的 columns 数组
```

#### 6.5 索引文件（可选）
**路径**：`src/{commonComponentPath}/models/index.ts`

如果目录下已有多个组件，生成或更新 index 文件，导出所有模型组件。

### 第七步：输出总结

向用户显示生成完成的信息：

```
✅ 组件生成完成！

📁 生成的文件：
  - src/{commonComponentPath}/models/{ComponentName}.vue
  - src/{commonComponentPath}/models/schemas/{componentName}.schema.ts
  - src/{commonComponentPath}/models/apis/{componentName}.api.ts
  - src/{commonComponentPath}/models/README.md

📖 使用文档：
  请查看 src/{commonComponentPath}/models/README.md

🚀 快速开始：
  在你的页面中导入并使用组件：
  import {ComponentName} from '@/{commonComponentPath}/models/{ComponentName}.vue'

⚙️ 配置信息已保存到：
  .claude/config/component-generator.config.json
```

## 输入要求

**必需输入（由用户提供）**：
1. 模型标识：模型名称 OR 列需求描述
2. 组件名称（PascalCase）
3. 组件描述

**可选输入**：
1. 显示的列子集（默认：所有列）
2. 组件类型选择（默认：DataTableWithSearch）
3. 自定义列配置（宽度、固定位置等）

**环境信息（自动检测或从配置读取）**：
1. SemanticController API 地址
2. 命名空间
3. 通用组件路径
4. 组件作者

## 输出格式

生成 4-5 个文件到指定目录：

```
src/{commonComponentPath}/models/
├── {ComponentName}.vue              # 主组件
├── schemas/
│   └── {componentName}.schema.ts    # 列定义
├── apis/
│   └── {componentName}.api.ts       # API 接口层
├── README.md                        # 使用文档
└── index.ts                         # 索引（可选）
```

## 约束条件

- 必须联网调用 SemanticController API（需要 WebFetch 工具）
- 组件名称必须符合 PascalCase（例如 `OrderQueryTable`）
- 生成的文件必须通过 TypeScript 类型检查
- API 封装必须包含错误处理和响应验证
- 生成的文档必须包含实际可用的代码示例
- 配置文件必须以 JSON 格式保存，可被后续技能读取
- 不覆盖现有文件（如已存在则询问用户是否覆盖）

## 决策规则

- 如果用户提供列需求但找不到匹配模型 → 询问用户是否扩大搜索范围或手动输入模型名称
- 如果配置文件不存在且用户未提供 API 信息 → 使用默认值并提示用户可稍后编辑配置
- 如果目标组件已存在 → 询问用户是否覆盖、备份或选择新名称
- 如果 API 调用失败 → 显示错误信息，询问用户是否检查 API 地址和网络连接
- 如果模型 schema 为空或格式异常 → 询问用户是否使用该模型或手动配置列
- 如果用户未指定显示列 → 默认显示所有列，但在文档中说明如何自定义
- 如果组件路径不存在 → 自动创建必要的目录结构（models、schemas、apis）

## 相关技能

- `qm-schema-viewer` - 获取模型 schema，包含 API 端点详细文档
- `frontend-dsl-query` - 生成公共 DSL 查询 API，包含 DSL 语法参考

## ⚠️ 常见问题

### Q1: 生成的表格组件不显示，但 API 返回数据正常，控制台无报错？

**原因**: VXETable 未全局注册或样式未导入。

**解决方法**:

检查 `src/main.js` 是否包含：

```javascript
import VXETable from 'vxe-table'
import 'foggy-data-viewer/style.css'

app.use(VXETable)
```

如果缺失，请运行 `/foggy-frontend-init` 自动配置，或手动添加上述代码并重启开发服务器。

---

### Q2: 提示 "Cannot find module 'vxe-table'"？

**原因**: 缺少必需依赖包。

**解决方法**:

```bash
npm install vxe-table vxe-pc-ui xe-utils
```

或运行 `/foggy-frontend-init` 自动安装所有依赖。

---

### Q3: 表格样式错乱或显示不正常？

**原因**: 缺少样式文件导入。

**解决方法**:

确保 `src/main.js` 中包含：

```javascript
import 'foggy-data-viewer/style.css'
```

---

### Q4: API 请求返回 404 错误？

**原因**: API 端点配置错误或后端服务未启动。

**解决方法**:

1. 检查 `.claude/config/semantic-api.config.json` 中的 `apiBaseUrl` 是否正确
2. 确认后端服务已启动（默认端口 8080）
3. 检查 Vite 代理配置是否正确：

```javascript
// vite.config.js
server: {
  proxy: {
    '/jdbc-model': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

---

### Q5: 如何修改生成的组件显示的列？

**解决方法**:

编辑 `src/{commonComponentPath}/models/schemas/{componentName}.schema.ts` 文件：

```typescript
// 添加、删除或修改列配置
export const columns: EnhancedColumnSchema[] = [
  {
    name: 'order_id',
    type: 'INTEGER',
    title: '订单ID',
    width: 100,
    fixed: 'left'
  },
  // ... 其他列
]
```

---

### Q6: 组件生成失败，提示无法连接到 API？

**解决方法**:

1. 检查后端服务是否启动
2. 检查 API 地址配置是否正确
3. 检查网络连接
4. 确认命名空间（namespace）配置是否与后端一致