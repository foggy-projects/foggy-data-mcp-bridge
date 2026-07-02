# Foggy Data Viewer

一个基于 Vue 3 + TypeScript + vxe-table 的企业级数据表格组件库。

## 特性

- 🎯 **TypeScript 支持** - 完整的类型定义
- 📊 **动态列配置** - 基于 QM Schema + TableConfig 的灵活列配置
- 🔍 **高级筛选** - 支持多种数据类型的筛选器（表头过滤 + 独立搜索工具栏）
- 📈 **汇总行** - 自动计算选中行和全量汇总
- 🎨 **自定义渲染** - 支持 `column-*` 插槽、自定义格式化和渲染函数
- 🔧 **组合组件** - 提供 SearchToolbar + DataTable 的组合组件
- ✅ **高测试覆盖** - 核心组件覆盖率 90%+
- 📦 **按需引入** - ES Module 支持

## 安装

```bash
npm install foggy-data-viewer
# 或
yarn add foggy-data-viewer
# 或
pnpm add foggy-data-viewer
```

## 依赖

本组件库依赖以下包（需要在你的项目中安装）：

```bash
npm install vue vxe-table vxe-pc-ui xe-utils
```

## 快速开始

### 1. 注册组件

```typescript
// main.ts
import { createApp } from 'vue'
import App from './App.vue'

// 引入 vxe-table
import VxeUI from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'
import VxeTable from 'vxe-table'
import 'vxe-table/lib/style.css'

const app = createApp(App)

app.use(VxeUI)
app.use(VxeTable)

app.mount('#app')
```

### 2. 使用 DataTable 组件

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTable, buildTableColumns } from 'foggy-data-viewer'
import type { EnhancedColumnSchema } from 'foggy-data-viewer'

// 从服务器获取的 QM Schema
const qmSchema = [
  { name: 'id', type: 'INTEGER', title: 'ID' },
  { name: 'name', type: 'TEXT', title: '名称' },
  { name: 'amount', type: 'MONEY', title: '金额' }
]

// 使用 buildTableColumns 构建列配置
const columns = ref<EnhancedColumnSchema[]>(
  buildTableColumns(qmSchema, {
    visibleColumns: ['id', 'name', 'amount'],
    customizations: [
      { name: 'id', width: 80, fixed: 'left' },
      { name: 'name', width: 150 },
      { name: 'amount', width: 120 }
    ]
  })
)

const data = ref([
  { id: 1, name: '测试1', amount: 100 },
  { id: 2, name: '测试2', amount: 200 }
])

function handlePageChange(page: number, pageSize: number) {
  console.log('分页变化:', page, pageSize)
}
</script>

<template>
  <DataTable
    :columns="columns"
    :data="data"
    :total="100"
    :loading="false"
    @page-change="handlePageChange"
  />
</template>
```

## 组件列表

### 核心组件

| 组件 | 说明 | 文档 |
|------|------|------|
| **DataTable** | 基础数据表格组件（表头过滤器、分页、排序、汇总） | [见下方 API](#datatable-props) |
| **SearchToolbar** | 独立搜索工具栏组件（字段级快速筛选） | [完整文档](./docs/SearchToolbar.md) |
| **DataTableWithSearch** | 组合组件（SearchToolbar + DataTable + 属性透传，支持 queryMode 查询入口模式） | [完整文档](./docs/SearchToolbar.md#datatablewithsearch-使用) |
| **DataViewer** | 高层封装组件（集成 API 调用、元数据管理） | 待完善 |

### 工具函数

- **buildTableColumns** - 根据 QM Schema 构建列配置

### Composables

- **useTableSelection** - 表格行选择逻辑
- **useTableSummary** - 汇总行计算逻辑

### 过滤器组件

- **TextFilter** - 文本筛选（等于、左匹配、批量）
- **NumberRangeFilter** - 数字范围筛选
- **DateRangeFilter** - 日期范围筛选（需要 Element Plus）
- **SelectFilter** - 选择筛选（单选/多选、搜索）
- **BoolFilter** - 布尔筛选（是/否/全部）

## API

### DataTable Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| columns | `EnhancedColumnSchema[]` | - | 列配置（必填） |
| data | `any[]` | - | 表格数据（必填） |
| total | `number` | - | 总数据量（必填） |
| loading | `boolean` | `false` | 加载状态 |
| pageSize | `number` | `50` | 每页显示数量 |
| showFilters | `boolean` | `true` | 是否显示筛选器 |
| localFilter | `boolean` | `true` | 是否把列头筛选条件应用到当前 `data` |
| serverSummary | `object \| null` | `null` | 服务端汇总数据 |
| initialSlice | `SliceCondition[]` | - | 初始筛选条件 |
| filterOptionsLoader | `Function` | - | 筛选选项加载器 |

`DataTableWithSearch` 在 `schema + fetchData` 服务端查询模式下默认 `localFilter=false`：列头筛选仍会提交给 `fetchData`，但返回数据不会再被前端二次过滤，避免聚合字段、派生字段、字典展示字段被简单本地比较误删。需要纯前端过滤时，可显式设置 `localFilter=true` 或 `schema.localFilter=true`。

### DataTableWithSearch tableInstanceId 默认查询配置

`schema + fetchData` 模式支持按 `tableInstanceId` 加载默认查询配置。默认查询配置只负责默认展示列、排序、分页和筛选；`requiredRuntimeColumns`、`lockedColumns` 由 TM/QM 产出的 `TableSchema` 提供。

```vue
<DataTableWithSearch
  :schema="{ ...tableSchema, qmModel: 'TicketQueryModel', tableInstanceId: 'ticket-list' }"
  :fetch-data="fetchTickets"
  :default-query-config-scope="{ userId: currentUser.id, tenantId: currentTenant.id, roleIds: currentRoleIds }"
/>
```

后端可通过 `foggy.data-viewer.table-defaults.system/tenants/roles` 配置 fallback；用户默认配置仍可通过 `listPreset` 覆盖。`requiredRuntimeColumns` 只会追加到 `fetchData(params).columns`，不会显示为普通列。

### DataTable Events

| 事件名 | 参数 | 说明 |
|--------|------|------|
| page-change | `(page, pageSize)` | 分页变化 |
| sort-change | `(field, order)` | 排序变化 |
| filter-change | `(slices)` | 筛选变化 |
| row-click | `(row, column)` | 行点击 |
| row-dblclick | `(row, column)` | 行双击 |

### DataTable Methods

| 方法名 | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| resetPagination | - | `void` | 重置分页到第一页 |
| clearFilters | - | `void` | 清空所有筛选条件 |
| getFilters | - | `SliceCondition[]` | 获取当前筛选条件 |
| setFilter | `(field, op, value)` | `void` | 设置单个筛选条件 |
| getGridInstance | - | `VxeGrid` | 获取 vxe-grid 实例 |

### buildTableColumns

```typescript
function buildTableColumns(
  qmSchema: ColumnSchema[],
  config: TableConfig
): EnhancedColumnSchema[]
```

根据 QM Schema 和 TableConfig 构建表格列配置。

**参数：**
- `qmSchema`: QM 模型的列定义数组
- `config`: 表格配置对象
  - `visibleColumns?: string[]` - 可见列名称数组
  - `showAll?: boolean` - 是否显示所有列
  - `customizations?: ColumnCustomization[]` - 列自定义配置

**返回：**
- `EnhancedColumnSchema[]` - 增强的列配置数组

## 列内容自定义

### `column-*` 插槽（推荐用于交互）

当业务列需要渲染成链接、按钮，或点击后交给上游页面打开详情、编辑弹窗时，推荐使用 `#column-{field}` 插槽。插槽参数为 `{ row, value, column }`。

```vue
<script setup lang="ts">
function openDetail(row: Record<string, unknown>) {
  // 由业务系统决定跳转、弹窗或其他处理方式
}
</script>

<template>
  <DataTableWithSearch
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
  >
    <template #column-name="{ row, value }">
      <button type="button" class="link-cell" @click.stop="openDetail(row)">
        {{ value || '-' }}
      </button>
    </template>
  </DataTableWithSearch>
</template>
```

`foggy-gen` 生成的 QueryTable wrapper 也会透传 `column-*` / `filter-*` 动态插槽，因此业务页可以直接在生成组件上写同样的插槽：

```vue
<VehicleCapacityProfileManagementQueryTable>
  <template #column-profileName="{ row, value }">
    <button type="button" class="link-cell" @click.stop="openProfile(row)">
      {{ value || '-' }}
    </button>
  </template>
</VehicleCapacityProfileManagementQueryTable>
```

### `render` 函数（适合纯展示）

如果只是添加颜色、图标、标签等展示效果，也可以通过 `ColumnCustomization.render` 或生成组件的 `columnOverrides[field].render` 配置渲染函数：

```typescript
import { h } from 'vue'
import { buildTableColumns } from 'foggy-data-viewer'

const columns = buildTableColumns(qmSchema, {
  visibleColumns: ['name', 'status'],
  customizations: [
    {
      name: 'status',
      render: ({ value, column }) => h(
        'span',
        { class: value === 'active' ? 'status-ok' : 'status-muted' },
        `${column.title}: ${String(value ?? '-')}`
      )
    }
  ]
})
```

选择原则：需要绑定业务事件时优先用 `column-*` 插槽；只改显示内容时使用 `render` 更轻。

### 全局列渲染器（应用级统一增强）

业务系统可以在应用启动时注册 `globalColumnRenderers`，按列元数据统一增强单元格展示。生成的 QueryTable wrapper 不需要批量修改。

```typescript
import { h } from 'vue'
import { globalColumnRenderers } from 'foggy-data-viewer'

const dispose = globalColumnRenderers.add({
  id: 'app.orderNoLink',
  priority: 100,
  match: ({ column }) => column.name === 'orderNo' && /运单号/.test(column.title ?? ''),
  render: ({ value }) => {
    const text = String(value ?? '').trim()
    if (!text) return '-'
    return h('button', {
      type: 'button',
      class: 'data-viewer-link-cell',
      onClick: (event: MouseEvent) => {
        event.stopPropagation()
        // 在业务应用闭包里调用 router.push(...)
      }
    }, text)
  }
})

// 应用卸载或测试清理
dispose()
```

渲染优先级固定为：`column-*` 插槽 > `column.customRender` > `globalColumnRenderers` > 默认渲染/字典/formatter。列级退出可设置 `column.uiConfig.disableGlobalRender = true`。

### 全局搜索生命周期 Hook

`globalSearchHooks` 作用于搜索动作层，早于最终 `fetchData` 和 `globalQueryHooks.onBeforeQuery`。它可以区分搜索来源和触发原因，适合埋点、默认条件补充和无效查询拦截。

```typescript
import { globalSearchHooks } from 'foggy-data-viewer'

globalSearchHooks.register({
  beforeSearch: (ctx) => {
    if (ctx.trigger === 'search' && ctx.slice.length === 0) return false
    return {
      slice: [
        ...ctx.slice,
        { field: 'tenantId', op: '=', value: currentTenantId }
      ]
    }
  },
  afterSearch: (ctx, result) => {
    console.log(ctx.source, ctx.trigger, result.total)
  },
  searchError: (ctx, error) => {
    console.warn(ctx.source, ctx.trigger, error)
  }
})
```

`ctx.source` 为 `search-toolbar | query-panel | column-filter | external | api`，`ctx.trigger` 为 `search | reset | filter | sort | page | refresh | reload | mount`。

## 类型定义

```typescript
import type { Component } from 'vue'

interface ColumnSchema {
  name: string
  type: string
  title: string
}

interface CellRenderContext {
  row: Record<string, unknown>
  value: unknown
  column: EnhancedColumnSchema
}

type CellRenderFn = (params: CellRenderContext) => unknown

interface ColumnCustomization {
  name: string
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  formatter?: (value: unknown) => string
  render?: CellRenderFn
  filterComponent?: Component | unknown
}

interface TableConfig {
  visibleColumns?: string[]
  showAll?: boolean
  customizations?: ColumnCustomization[]
}

interface EnhancedColumnSchema extends ColumnSchema {
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  customFormatter?: (value: unknown) => string
  customRender?: CellRenderFn
  customFilterComponent?: Component | unknown
}
```

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 运行测试
npm test

# 测试覆盖率
npm run test:coverage

# 构建发布包（库产物 + 类型声明 + 入口校验）
npm run build

# 构建内嵌应用静态资源
npm run build:app
```

## 测试

本项目包含完整的单元测试，覆盖率达到 98%+：

```bash
npm test
```

测试覆盖情况：
- ✅ schemaHelper.ts: 100%
- ✅ useTableSelection.ts: 100%
- ✅ useTableSummary.ts: 97.67%
- ✅ DataTable.vue: 63.31%
- ✅ 所有过滤器组件: 100%

## 验证项目

本项目包含一个独立的验证项目，用于测试组件库的功能。参见 [verification-app](./verification-app/README.md)。

## License

Apache-2.0

## 相关项目

- [foggy-framework](https://github.com/foggy-projects/java-data-mcp-bridge) - Foggy Framework 主项目
- [vxe-table](https://github.com/x-extends/vxe-table) - 企业级表格组件
