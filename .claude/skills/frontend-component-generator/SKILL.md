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

## ⭐ 两种工作模式

DataTableWithSearch 组件支持两种工作模式：

### Schema 模式（推荐）
传入 `schema` + `fetchData`，组件自动管理所有状态（分页、加载、筛选）。

```vue
<DataTableWithSearch
  :schema="tableSchema"
  :fetch-data="fetchData"
  @load-success="onSuccess"
  @load-error="onError"
>
  <!-- 可选：自定义工具栏按钮（显示在分页栏左侧） -->
  <template #toolbar>
    <button @click="handleAdd">新增</button>
    <button @click="handleExport">导出</button>
  </template>
</DataTableWithSearch>
```

**优势**：
- 代码最简洁，无需手动管理状态
- 自动处理分页、排序、筛选变化
- 自动触发数据加载
- 内置顶部工具栏（分页在右侧，支持左侧插槽放自定义按钮）

### 受控模式
传入 `columns` + `data` + `total` + `loading`，用户手动管理所有状态。

```vue
<DataTableWithSearch
  :columns="columns"
  :data="data"
  :total="total"
  :loading="loading"
  @page-change="handlePageChange"
  @filter-change="handleFilterChange"
  @sort-change="handleSortChange"
/>
```

**适用场景**：
- 需要完全控制数据流
- 需要在加载前后执行自定义逻辑
- 需要与外部状态管理集成

**本技能生成的组件默认使用 Schema 模式**。

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

读取 `src/main.js` 或 `src/main.ts`，验证是否包含以下五项（vxe-table v4.7+ 需要 VxeUI）：
1. `import VxeUI from 'vxe-pc-ui'`
2. `import VXETable from 'vxe-table'`
3. `import 'foggy-data-viewer/style.css'`
4. `app.use(VxeUI)`（必须在 VXETable 之前）
5. `app.use(VXETable)`

**如果基础环境缺失** → 提示用户先运行 `/foggy-frontend-init`

**如果 VXETable 未注册** → 显示警告并提供配置代码：

```
⚠️ 警告：检测到 VxeUI/VXETable 未全局注册

foggy-data-viewer 依赖 VXETable 表格引擎，vxe-table v4.7+ 还需要 VxeUI。
必须在 main.js 中注册，否则生成的表格组件无法显示。

请在 src/main.js 中添加以下代码：

import VxeUI from 'vxe-pc-ui'
import VXETable from 'vxe-table'
import 'foggy-data-viewer/style.css'

// VxeUI 必须在 VXETable 之前注册
app.use(VxeUI)
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

生成的组件使用 **Schema 模式**（推荐），包含：
- 导入 foggy-data-viewer 组件和类型定义
- 导入本项目的 schema 配置和 API 层
- 定义 TableSchema 和 fetchData 函数
- 公开的方法（refresh、reload、clearFilters 等）
- **顶部工具栏**（插槽支持自定义按钮）
- **操作列**（可选，通过插槽定制）

核心代码结构（Schema 模式）：
```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTableWithSearch } from 'foggy-data-viewer'
import type { TableSchema, FetchDataParams, FetchDataResult } from 'foggy-data-viewer'
import { tableSchema } from './schemas/{componentName}.schema'
import { fetchOrderData } from './apis/{componentName}.api'

// 组件引用
const tableRef = ref<InstanceType<typeof DataTableWithSearch>>()

// Schema 配置
const schema: TableSchema = tableSchema

// 数据加载函数
async function fetchData(params: FetchDataParams): Promise<FetchDataResult> {
  const result = await fetchOrderData({
    page: params.page,
    pageSize: params.pageSize,
    filters: params.slice,
    orderBy: params.orderBy
  })
  return {
    items: result.rows,
    total: result.total,
    totalData: result.totalData
  }
}

// 公开方法
defineExpose({
  refresh: () => tableRef.value?.refresh(),
  reload: () => tableRef.value?.reload(),
  clearFilters: () => tableRef.value?.clearAllFilters()
})
</script>

<template>
  <div class="component-wrapper">
    <!-- 数据表格（Schema 模式：组件自动管理分页/加载/筛选状态） -->
    <!-- 工具栏布局：左侧自定义按钮插槽，右侧分页栏 -->
    <DataTableWithSearch
      ref="tableRef"
      :schema="schema"
      :fetch-data="fetchData"
      @load-success="(result) => $emit('load-success', result)"
      @load-error="(error) => $emit('load-error', error)"
    >
      <!-- 顶部工具栏插槽（按钮显示在分页栏左侧） -->
      <template v-if="$slots.toolbar" #toolbar>
        <slot name="toolbar" />
      </template>
      <!-- 透传操作列插槽 -->
      <template v-if="$slots['column-actions']" #column-actions="slotData">
        <slot name="column-actions" v-bind="slotData" />
      </template>
    </DataTableWithSearch>
  </div>
</template>
```

> **注**: Schema 模式下，组件自动处理分页、排序、筛选变化并重新加载数据，无需手动管理状态

#### 6.2 Schema 配置文件
**路径**：`src/{commonComponentPath}/models/schemas/{componentName}.schema.ts`

生成包含：
- 从后台 schema 映射的列配置
- **TableSchema 配置**（用于 Schema 模式）
- **自动列宽计算**（组件内部自动调用 `calculateColumnWidth`）
- 列的排序、筛选器类型等定制配置
- 类型定义导出

**默认行为**：
- `width` 不传或为 0 → 组件自动根据列名长度和类型计算
- `filterable` 不传 → 默认为 `true`（可筛选）
- `sortable` 不传 → 默认为 `false`

示例结构：
```typescript
import type { EnhancedColumnSchema, TableSchema } from 'foggy-data-viewer'

// 列配置
export const columns: EnhancedColumnSchema[] = [
  {
    name: 'order_id',
    type: 'INTEGER',
    title: '订单ID',
    fixed: 'left'
    // width 自动计算
    // filterable 默认 true
  },
  {
    name: 'order_no',
    type: 'TEXT',
    title: '订单号',
    filterType: 'text'
  },
  {
    name: 'speed',
    type: 'NUMBER',
    title: '速度(km/h)',  // 自动计算宽度: ~170px
    tooltip: '车辆行驶速度，单位：千米/小时',
    filterType: 'number'
  },
  {
    name: 'amount',
    type: 'MONEY',
    title: '金额',
    width: 150,  // 手动指定宽度（覆盖自动计算）
    sortable: true
  }
]

// TableSchema 配置（用于 Schema 模式）
export const tableSchema: TableSchema = {
  columns,
  searchableFields: ['order_no', 'customer_name'],  // 搜索工具栏显示的字段
  pageSize: 50,
  showFilters: true,
  showPager: true,             // 是否显示分页栏（默认 true）
  showSearchToolbar: true,
  searchLayout: 'horizontal'
}

// 行数据类型定义
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
- 支持 Schema 模式的返回格式

示例结构：
```typescript
import { query, type SliceRequestDef, type OrderRequestDef } from '@/apis/common/dslQuery'
import type { OrderQueryRow } from '../schemas/{componentName}.schema'

export interface QueryParams {
  page?: number
  pageSize?: number
  filters?: SliceRequestDef[]
  orderBy?: OrderRequestDef[]
}

export interface QueryResult {
  rows: OrderQueryRow[]
  total: number
  totalData?: Record<string, unknown>  // 服务端汇总数据（可选）
}

/**
 * 查询订单数据
 *
 * 返回格式兼容 FetchDataResult，可直接用于 Schema 模式
 */
export async function fetchOrderData(params: QueryParams): Promise<QueryResult> {
  const result = await query<OrderQueryRow>('OrderQueryModel', {
    columns: ['orderId', 'orderNo', 'amount', 'status', 'createTime'],
    filters: params.filters,
    orderBy: params.orderBy || [{ field: 'createTime', dir: 'desc' }],
    page: params.page || 1,
    pageSize: params.pageSize || 50,
  })

  return {
    rows: result.items,
    total: result.total,
    totalData: result.totalData
  }
}
```

#### 6.4 使用文档
**路径**：`src/{commonComponentPath}/models/README.md`

生成包含：
- 组件基本信息（名称、作者、基于的模型）
- 快速开始示例
- API 接口说明
- 常见问题 FAQ

核心内容：
```markdown
# {ComponentName}

**基于模型**: {modelName}
**作者**: {componentAuthor}
**创建时间**: {currentDate}

## 快速开始

\`\`\`vue
<template>
  <OrderQueryTable />
</template>

<script setup lang="ts">
import OrderQueryTable from '@/{commonComponentPath}/models/OrderQueryTable.vue'
</script>
\`\`\`

## API 配置

- **端点**: `/jdbc-model/query-model/v2/{modelName}`
- **Vite 代理**: 需在 `vite.config.js` 中配置 `/jdbc-model` 代理

## 常见问题

- 修改列配置: 编辑 `schemas/{componentName}.schema.ts`
- 修改 API 逻辑: 编辑 `apis/{componentName}.api.ts`
```

> **注**: 完整文档包含详细的 Props、Events、API 请求格式等

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

**原因**: VxeUI 或 VXETable 未全局注册，或样式未导入。

**解决方法**:

检查 `src/main.js` 是否包含（vxe-table v4.7+ 需要同时注册 VxeUI）：

```javascript
import VxeUI from 'vxe-pc-ui'
import VXETable from 'vxe-table'
import 'foggy-data-viewer/style.css'

// 注意：VxeUI 必须在 VXETable 之前注册
app.use(VxeUI)
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

### Q5: 列名太长导致排序图标错位或换行？

**原因**: 列宽度设置过小，无法容纳列名和排序图标。

**解决方法**:

**方法 1: 使用自动计算**（推荐，默认行为）

不指定 `width` 或设置为 0，组件会自动计算合理宽度：

```typescript
export const columns: EnhancedColumnSchema[] = [
  {
    name: 'speed',
    type: 'NUMBER',
    title: '速度(km/h)'
    // width 不传，自动计算为 ~170px
  }
]
```

**方法 2: 简化列名 + tooltip**

```typescript
{
  name: 'speed',
  type: 'NUMBER',
  title: '速度',              // 简短标题
  tooltip: '速度(km/h)',      // 完整说明
  width: 100,
  sortable: true
}
```

**方法 3: 手动增加列宽**

```typescript
{
  name: 'speed',
  type: 'NUMBER',
  title: '速度(km/h)',
  width: 150,  // 手动增加到 150px
  sortable: true
}
```

---

### Q6: 如何修改生成的组件显示的列？

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

### Q7: 组件生成失败，提示无法连接到 API？

**解决方法**:

1. 检查后端服务是否启动
2. 检查 API 地址配置是否正确
3. 检查网络连接
4. 确认命名空间（namespace）配置是否与后端一致

---

### Q8: 何时使用 Schema 模式？何时使用受控模式？

**Schema 模式（推荐）** - 大多数场景：
- 标准数据表格展示
- 不需要复杂的自定义加载逻辑
- 希望代码最简洁

**受控模式** - 特殊场景：
- 需要在数据加载前后执行自定义逻辑
- 需要与 Vuex/Pinia 等状态管理集成
- 需要跨组件共享数据状态
- 需要实现复杂的缓存策略

---

### Q9: 如何在表格顶部添加操作按钮（新增、导出等）？

**解决方法**:

使用 `#toolbar` 插槽，按钮会显示在分页栏左侧：

```vue
<DataTableWithSearch
  :schema="tableSchema"
  :fetch-data="fetchData"
>
  <template #toolbar>
    <button class="btn primary" @click="handleAdd">+ 新增</button>
    <button class="btn" @click="handleExport">导出</button>
    <button class="btn" @click="tableRef?.refresh()">刷新</button>
  </template>
</DataTableWithSearch>
```

**工具栏布局**：
- 左侧：`#toolbar` 插槽内容（自定义按钮）
- 右侧：分页组件（自动显示，可通过 `showPager: false` 隐藏）

---

### Q10: 如何隐藏分页栏？

**解决方法**:

在 TableSchema 中设置 `showPager: false`：

```typescript
export const tableSchema: TableSchema = {
  columns,
  showPager: false,  // 隐藏分页栏
  // ...
}
```

或在组件上直接传入 prop：

```vue
<DataTableWithSearch
  :schema="tableSchema"
  :fetch-data="fetchData"
  :show-pager="false"
/>
```

---

### Q11: 点击搜索按钮时，后台收到两个相同的请求？

**原因**: 同时监听了 `@update:model-value` 和 `@search` 事件。

**错误用法**:
```vue
<SearchToolbar
  v-model="slices"
  @update:model-value="handleSearch"  <!-- 会在输入时触发 -->
  @search="handleSearch"              <!-- 点击按钮也会触发 -->
/>
```

**正确用法**:
```vue
<!-- 方式1：仅响应搜索按钮（推荐） -->
<SearchToolbar
  v-model="slices"
  @search="handleSearch"
  @reset="handleReset"
/>

<!-- 方式2：实时搜索（隐藏按钮） -->
<SearchToolbar
  v-model="slices"
  :show-actions="false"
  @update:model-value="handleSearch"
/>
```

---

### Q12: 横向滚动表格时，表头不跟随内容同步滚动？

**原因**: 在自定义样式中设置了 `overflow: visible`。

**禁止的写法**:
```css
:deep(.vxe-table--header-wrapper) {
  overflow: visible !important;  /* 会破坏滚动同步 */
}
```

**解决方法**:

不要修改 vxe-table 表头相关元素的 overflow 属性。如需让过滤器下拉框溢出显示，使用 `z-index` 或 Vue 的 `Teleport` 组件。