# foggy-data-viewer 前端使用与能力说明

## 1. 先说结论

`addons/foggy-data-viewer` 不是单纯的前端组件目录，而是一套完整的数据浏览方案，包含：

- 一个 Spring Boot addon，用来缓存查询、暴露 viewer API、托管静态页面、提供 saved-query / list-preset / table-defaults API。
- 一个可单独发布/复用的 Vue 3 组件库，目录在 `addons/foggy-data-viewer/frontend`。
- 一个面向 MCP / DSL 的“打开到浏览器”能力，后端工具名是 `dataset.open_in_viewer`。

如果你是前端使用方，实际有 3 种接入方式：

1. 直接复用后端托管的完整浏览页：打开 `/data-viewer/view/{model}/{queryId}`。
2. 在你自己的 Vue 项目里引入 `foggy-data-viewer` 组件库，自行组织页面。
3. 只复用它的 API 层：自己写 UI，但调用它提供的 `/data-viewer/api/...` 接口。

---

## 2. 现有文档情况

源码里已经有一些文档，但比较分散，且有少量内容和当前实现不完全一致：

- `addons/foggy-data-viewer/frontend/README.md`
- `addons/foggy-data-viewer/frontend/USAGE.md`
- `addons/foggy-data-viewer/frontend/COMPONENT_USAGE.md`
- `addons/foggy-data-viewer/frontend/SAVED_QUERY_USAGE.md`
- `addons/foggy-data-viewer/frontend/docs/SearchToolbar.md`

这份文档的目标是把“前端怎么接”和“它到底能做什么”统一整理到一个入口里。

---

## 3. 这套 addon 提供了哪些能力

### 3.1 页面和路由能力

- 托管 SPA 页面：`/data-viewer/index.html`
- 浏览指定查询：`/data-viewer/view/{model}/{queryId}`
- 后端会把静态前端资源打包进 jar 的 `static/data-viewer` 下

适合场景：

- AI / MCP 生成一个 `viewerUrl`，用户直接在浏览器里打开
- 业务系统里通过链接跳转到一个“只读但可交互”的数据浏览页

### 3.2 后端查询缓存能力

后端会把查询上下文缓存到 MongoDB，生成一个短 `queryId`。缓存里包含：

- `model`
- `columns`
- `slice`
- `groupBy`
- `orderBy`
- `calculatedFields`
- `title`
- `authorization`
- `expiresAt`
- `tableConfig`

这意味着前端不需要自己持久化完整 DSL，只要拿到 `queryId` 就能加载同一份查询上下文。

### 3.3 前端组件能力

组件库核心能力包括：

- 动态列构建：根据 QM Schema + TableConfig 生成列
- 表格展示：基于 `vxe-table`
- 服务端分页 / 排序 / 过滤
- 表头内嵌过滤器
- 独立搜索工具栏 `SearchToolbar`
- 组合组件 `DataTableWithSearch`
- 高层封装组件 `DataViewer`
- 选中行汇总 + 服务端全量汇总
- 自定义列渲染 / 格式化 / 自定义过滤器
- 查询前后钩子与全局钩子
- 保存查询 / 加载查询 UI 与 API
- 自定义查询 / 用户默认列表预设（ListPreset）
- `tableInstanceId` 默认查询配置与租户 / 角色 / 系统 fallback
- TM/QM frontend-meta 级 `requiredRuntimeColumns` / `lockedColumns`

### 3.4 支持的过滤器类型

按源码当前实现，内置过滤器主要有：

- `text`
- `number`
- `date`
- `datetime`
- `dict`
- `dimension`
- `bool`
- `custom`

对应组件：

- `TextFilter`
- `NumberRangeFilter`
- `DateRangeFilter`
- `SelectFilter`
- `BoolFilter`

### 3.5 MCP / DSL 能力

后端支持：

- `POST /data-viewer/api/query/create`
- MCP 工具 `dataset.open_in_viewer`

它们都能把一段查询 DSL 转成 `queryId + viewerUrl`，方便前端跳转。

---

## 4. 前端接入方式总览

### 4.1 方式 A：直接使用后端托管好的 viewer 页面

这是最省事的方式，适合“已经有查询 DSL 或已经拿到 queryId”的场景。

### 流程

1. 前端或后端调用 `POST /data-viewer/api/query/create`
2. 返回 `queryId` 和 `viewerUrl`
3. 浏览器跳转到 `viewerUrl`

### 请求示例

```json
{
  "model": "FactSalesQueryModel",
  "title": "销售明细查询",
  "payload": {
    "columns": [
      "orderId",
      "salesDate$caption",
      "customer$caption",
      "salesAmount"
    ],
    "slice": [
      { "field": "salesDate$caption", "op": ">=", "value": "2024-12-01" },
      { "field": "salesDate$caption", "op": "<", "value": "2025-01-01" }
    ],
    "orderBy": [
      { "field": "salesDate$caption", "order": "desc" }
    ]
  }
}
```

### 返回示例

```json
{
  "success": true,
  "queryId": "abc123456789def0",
  "viewerUrl": "/data-viewer/view/FactSalesQueryModel/abc123456789def0",
  "error": null
}
```

### 适合你什么时候用

- 只需要一个完整浏览页，不想自己搭页面
- 由 AI / MCP 生成查询后，直接给用户一个链接
- 想利用后端自动缓存、TTL、权限上下文和统一 viewer UI

### 4.2 方式 B：在自己的 Vue 项目中使用组件库

组件库入口在：

- `addons/foggy-data-viewer/frontend/src/index.ts`

导出的主要内容有：

- 组件：`DataTable`、`SearchToolbar`、`DataTableWithSearch`、`DataViewer`
- 工具：`buildTableColumns`、`calculateColumnWidth`
- API：`createQuery`、`fetchQueryMeta`、`fetchQueryData`、`fetchFilterOptions`、`fetchQmSchema`
- saved-query API：`saveQuery`、`listSavedQueries`、`getSavedQuery`、`updateSavedQuery`、`deleteSavedQuery`、`applySavedQuery`
- list-preset API：`listPresets`、`getDefaultListPreset`、`createListPreset`、`updateListPreset`、`deleteListPreset`、`setDefaultListPreset`
- table-defaults API：`getTableDefaultQueryConfig`
- hooks：`useTableQuery`、`globalQueryHooks`

### 安装和注册

```ts
import { createApp } from 'vue'
import App from './App.vue'

import VxeUI from 'vxe-pc-ui'
import VXETable from 'vxe-table'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

import 'foggy-data-viewer/style.css'

const app = createApp(App)

app.use(VxeUI)
app.use(VXETable)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
```

说明：

- `DataTable` / `DataTableWithSearch` 依赖 `vxe-table`
- 日期过滤器和 saved-query UI 依赖 `ElementPlus`
- 包里会统一引入样式，但插件注册仍需要宿主应用完成

### 4.3 方式 C：只用 API，不用现成组件

如果你想保留自己的页面风格，可以只调用这些接口：

- `GET /data-viewer/api/query/{model}/{queryId}/meta`
- `POST /data-viewer/api/query/{model}/{queryId}/data`
- `GET /data-viewer/api/schema/{qmModel}`
- `POST /data-viewer/api/query/create`

然后自己渲染筛选器、表格和分页。

---

## 5. 推荐的前端使用路径

如果你是业务前端，推荐按下面顺序选型：

1. 只想跳转查看数据：用方式 A。
2. 想嵌入到已有 Vue 页面：优先用 `DataTableWithSearch`。
3. 想完全自控数据加载和状态：用 `DataTable`。
4. 想复用现成 viewer 逻辑但自己管理页面：用 `DataViewer`。

---

## 6. 组件层怎么用

### 6.1 `DataTable`：最基础的表格组件

适合“我自己管理数据请求、分页、排序、过滤状态”的场景。

### 你需要提供

- `columns`
- `data`
- `total`
- `loading`

### 它会向外抛出的核心事件

- `page-change`
- `sort-change`
- `filter-change`
- `row-click`
- `row-dblclick`

### 典型用法

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTable, buildTableColumns } from 'foggy-data-viewer'

const qmSchema = [
  { name: 'id', type: 'INTEGER', title: 'ID', filterable: true },
  { name: 'customerName', type: 'TEXT', title: '客户', filterable: true },
  { name: 'amount', type: 'MONEY', title: '金额', filterable: true, aggregatable: true }
]

const columns = ref(buildTableColumns(qmSchema, {
  visibleColumns: ['id', 'customerName', 'amount'],
  customizations: [
    { name: 'id', width: 80, fixed: 'left' },
    { name: 'amount', width: 120 }
  ]
}))

const data = ref([])
const total = ref(0)
const loading = ref(false)

function handlePageChange(page: number, pageSize: number) {
  // 触发你的服务端分页请求
}

function handleSortChange(field: string | null, order: 'asc' | 'desc' | null) {
  // 触发你的服务端排序请求
}

function handleFilterChange(slice: any[]) {
  // 触发你的服务端过滤请求
}
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

### `DataTable` 的价值

- 已经把表头筛选器、分页、排序状态切换、汇总行、选择框整合好了
- 仍然允许你自己掌控数据请求
- 所有 `attrs` / 事件基本都能继续透传给 `vxe-grid`

这意味着如果你要用 `vxe-table` 的更多原生能力，比如更复杂的 grid 配置、导出、行样式、额外事件，通常可以继续通过透传方式接进去。

### 6.2 `SearchToolbar`：独立搜索工具栏

适合场景：

- 你不想把过滤器放在表头里
- 想把搜索区放在页面顶部
- 想做“字段级搜索区 + 表格区”的上下布局

它支持：

- `v-model` 双向绑定 `SliceRequestDef[]`
- 横向 / 纵向布局
- 搜索 / 重置按钮
- 从 `columns` 中自动推导可筛选字段
- `searchableFields` 精确控制显示哪些字段

### 6.3 `DataTableWithSearch`：最推荐的业务组件

这是当前最适合业务页面直接复用的组件。它把：

- `SearchToolbar`
- `DataTable`
- 查询状态管理
- 筛选合并
- 分页 / 排序触发数据加载
- query hooks

组合到了一起。

它有两种模式。

### 模式 1：Schema 模式

这是推荐模式。你传：

- `schema`
- `fetchData`

组件自动管理：

- 当前页
- pageSize
- 排序
- 筛选
- loading
- 数据加载

示例：

```vue
<script setup lang="ts">
import { DataTableWithSearch, buildTableColumns } from 'foggy-data-viewer'
import { fetchQmSchema, fetchQueryData } from 'foggy-data-viewer'
import { ref, onMounted } from 'vue'

const schema = ref()
const model = 'FactSalesQueryModel'
const queryId = 'abc123456789def0'

onMounted(async () => {
  const qmSchema = await fetchQmSchema(model)
  schema.value = {
    columns: buildTableColumns(qmSchema, {
      visibleColumns: ['orderId', 'customer$caption', 'salesAmount']
    }),
    searchableFields: ['customer$caption'],
    pageSize: 50,
    queryMode: 'combined',
    showFilters: true
  }
})

async function fetchData(params) {
  const result = await fetchQueryData(model, queryId, {
    start: (params.page - 1) * params.pageSize,
    limit: params.pageSize,
    slice: params.slice,
    orderBy: params.orderBy
  })

  return {
    items: result.items,
    total: result.total,
    totalData: result.totalData
  }
}
</script>

<template>
  <DataTableWithSearch
    v-if="schema"
    :schema="schema"
    :fetch-data="fetchData"
  />
</template>
```

### 模式 2：受控模式

如果你已经有自己的状态管理，也可以直接传：

- `columns`
- `data`
- `total`
- `loading`

这种模式下它更像是按 `queryMode` 组合查询入口和 `DataTable` 的外壳。

### 它额外提供的能力

- `filterMergeMode: 'replace' | 'merge'`
- `queryHooks`
- 运行时 `addQueryHook` / `removeQueryHook`
- `refresh()` / `reload()`
- `getMergedFilters()`
- `clearSearchFilters()` / `clearTableFilters()` / `clearAllFilters()`
- `tableInstanceId` 默认查询配置加载
- `listPreset` 用户自定义查询与默认列表预设加载

### 6.4 `DataViewer`：和后端 viewer API 绑定的高层组件

`DataViewer` 适合“我已经拿到 `model + queryId`，希望直接在自己页面中展示 viewer”。

它内部会自动：

1. 调 `fetchQueryMeta(model, queryId)`
2. 从 `tableConfig.qmModel` 再调 `fetchQmSchema`
3. 用 `buildTableColumns` 组装列
4. 调 `fetchQueryData(model, queryId, request)`
5. 把结果交给 `DataTable`

使用方式：

```vue
<template>
  <DataViewer model="FactSalesQueryModel" query-id="abc123456789def0" />
</template>
```

适合：

- 需要在自己的壳页面里嵌一个完整 viewer
- 想复用后端 queryId 机制，但不想直接跳转到 `/data-viewer/view/...`

---

## 7. 查询 DSL 和前端交互模型

这套前端是围绕 DSL 查询结构设计的，核心类型是：

### 7.1 过滤条件

```ts
interface SliceRequestDef {
  field: string
  op: string
  value?: unknown
  link?: 1 | 2
  children?: SliceRequestDef[]
}
```

### 7.2 排序条件

```ts
interface OrderRequestDef {
  field: string
  order: 'asc' | 'desc'
}
```

### 7.3 查询请求

```ts
interface ViewerQueryRequest {
  start?: number
  limit?: number
  columns?: string[]
  extData?: Record<string, unknown>
  slice?: SliceRequestDef[]
  orderBy?: OrderRequestDef[]
}
```

`/query/direct/{qmModel}` 必须显式传入非空 `columns`，生成组件会从当前显示列自动带上；`queryId` 查询可继续使用创建查询时缓存的列。

这意味着组件的分页、排序、过滤都天然偏向服务端查询，而不是本地一次性加载全部数据。

`extData` 只会透传到后端 `DbQueryRequestDef.extData`，供 QM 中显式声明的运行时表达式读取，例如 aggregate join RHS relation filter 中的 `(ctx) => ctx.extData.suggestionSheetId`。它不会被 data-viewer 自动转换为 `slice` 或主查询 `where` 条件。

---

## 8. 列配置怎么组织

前端列通常不是手写，而是用下面两部分合成：

1. 后端返回的 `ColumnSchema[]`
2. 前端的 `TableConfig`

### 8.1 `ColumnSchema`

来自：

- `GET /data-viewer/api/schema/{qmModel}`
- 或你自己从其他元数据接口转换

字段里常用的有：

- `name`
- `title`
- `type`
- `filterable`
- `aggregatable`
- `filterType`
- `dictItems`
- `measure`
- `format`
- `uiConfig`

### 8.2 `TableConfig`

```ts
interface TableConfig {
  qmModel?: string
  visibleColumns?: string[]
  showAll?: boolean
  customizations?: ColumnCustomization[]
}
```

### 8.3 `buildTableColumns`

```ts
const columns = buildTableColumns(qmSchema, {
  visibleColumns: ['id', 'name', 'amount'],
  customizations: [
    { name: 'id', width: 80, fixed: 'left' },
    { name: 'amount', formatter: v => `¥${Number(v).toFixed(2)}` }
  ]
})
```

它会自动处理：

- 列顺序
- 宽度
- 固定列
- 自定义 formatter
- 自定义 render
- 自定义 filterComponent

### 8.4 搜索栏字段控制

`DataTableWithSearch` 里，搜索栏字段优先级如下：

1. `props.searchableFields`
2. `schema.searchableFields`
3. 从 `columns` 中筛选 `uiConfig.showInToolbar === true`

---

## 9. saved-query 怎么接

这是这套 addon 的高级能力之一，适合报表页、分析页、运营后台。

### 9.1 前端侧能力

组件：

- `SavedQueryManager`
- `SaveQueryDialog`
- `QueryListDialog`
- `OptionManagerDialog`

API：

- `saveQuery`
- `listSavedQueries`
- `getSavedQuery`
- `updateSavedQuery`
- `deleteSavedQuery`
- `applySavedQuery`

### 9.2 最常见接法

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTableWithSearch, SavedQueryManager } from 'foggy-data-viewer'

const tableRef = ref()
</script>

<template>
  <SavedQueryManager
    :table-ref="tableRef"
    model="FactSalesQueryModel"
    current-user-id="u001"
  />

  <DataTableWithSearch
    ref="tableRef"
    :schema="schema"
    :fetch-data="fetchData"
  />
</template>
```

### 9.3 后端前置条件

saved-query 要真正可用，后端还需要：

- MongoDB
- `SecurityIdentityResolver` SPI
- 请求头里的 `Authorization`

否则会返回 503 或鉴权相关错误。

---

## 10. `tableInstanceId` 默认查询配置与自定义查询

这是 `foggy-data-viewer@1.0.1-beta.40` 开始可用的业务表格默认配置能力，适合“同一个 QM 在多个页面复用，但每个页面有不同默认列、默认排序、默认分页大小”的场景。

### 10.1 上游接入时必须明确的标识

| 字段 | 是否必需 | 说明 |
|---|---|---|
| `queryModel` / `qmModel` | 必需 | QM 模型名，用于定位 schema、fallback 配置和列表预设 |
| `tableInstanceId` | 必需 | 稳定且唯一的表格实例 ID，用于区分同一 QM 下的不同页面或业务查询 |
| `userId` | 必需 | 用户默认 ListPreset 的命名空间；不是数据权限边界 |
| `tenantId` | 可选 | 用于租户级 fallback |
| `roleIds` | 可选 | 用于角色级 fallback，按传入顺序匹配 |

最终优先级是：

1. 用户默认 ListPreset。
2. 租户级 fallback。
3. 角色级 fallback。
4. 系统级 fallback。

用户默认 ListPreset 如果没有 pageSize，会继承 fallback 的 pageSize。

### 10.2 前端组件接法

`DataTableWithSearch` 在 `schema + fetchData` 模式下可以自动加载默认配置：

```vue
<DataTableWithSearch
  :schema="{ ...tableSchema, qmModel: 'TicketQueryModel', tableInstanceId: 'ticket-list' }"
  :fetch-data="fetchTickets"
  :default-query-config-scope="{
    queryModel: 'TicketQueryModel',
    userId: currentUser.id,
    tenantId: currentTenant.id,
    roleIds: currentRoleIds
  }"
  :list-preset="{
    enabled: true,
    model: 'TicketQueryModel',
    userId: currentUser.id,
    tableInstanceId: 'ticket-list',
    autoLoadDefault: true
  }"
/>
```

如果使用 `foggy-gen` 生成的 QueryTable wrapper，升级后重新生成即可拿到 `tableInstanceId`、`defaultQueryConfigScope`、`defaultQueryConfigLoader`、`listPreset` 等透传能力。

### 10.3 后端 fallback 配置

后端 fallback 只负责默认展示列、默认排序、默认分页大小、默认筛选条件，不负责运行时必需列或锁定列。

```yaml
foggy:
  data-viewer:
    table-defaults:
      system:
        ticket-list:
          query-model: TicketQueryModel
          table-instance-id: ticket-list
          default-visible-columns: [ticketNo, title, status, createdAt]
          default-page-size: 50
          default-order-by:
            - field: createdAt
              dir: desc
      tenants:
        tenant-a:
          ticket-list:
            query-model: TicketQueryModel
            table-instance-id: ticket-list
            default-visible-columns: [ticketNo, title, assignee, status]
```

对应查询接口：

- `GET /data-viewer/api/table-defaults/default?queryModel=TicketQueryModel&tableInstanceId=ticket-list&userId=u001`

### 10.4 TM/QM frontend-meta 归属

`requiredRuntimeColumns` 和 `lockedColumns` 必须落在 TM/QM 产出的 frontend-meta / `TableSchema`，不要放到 table-defaults fallback 中。

```json
{
  "defaults": {
    "tableInstanceId": "ticket-list",
    "visibleColumns": ["ticketNo", "title", "status", "createdAt"],
    "requiredRuntimeColumns": ["id", "tenantId"],
    "lockedColumns": ["ticketNo"],
    "pageSize": 50
  }
}
```

运行时语义：

- `requiredRuntimeColumns` 只追加到 `fetchData(params).columns`，不会作为普通列展示。
- `lockedColumns` 会在应用自定义查询后补回展示，避免关键列被用户预设隐藏。
- `visibleColumns` 是默认展示列，可以被用户默认 ListPreset 覆盖。

### 10.5 自定义查询管理器交互

`ListPresetManager` 已内置在 `DataTableWithSearch` 的工具栏里。开启 `listPreset` 后，用户可以在同一个弹窗里完成：

- 搜索字段池，按字段类型过滤，适配数百字段的复杂模型。
- 勾选展示列，调整列宽、左/右固定。
- 在已选字段区执行上移、下移、移到顶部、移到底部。
- 保存当前筛选、排序、分页大小，也可以选择不保存筛选和排序。
- 将方案设为默认；再次进入同一 `tableInstanceId` 时优先加载用户默认方案。
- 通过工具栏“清空条件”清掉当前筛选条件；该动作保留当前列方案和排序。

`requiredRuntimeColumns` 会在管理器中作为运行时字段处理，不会保存为普通展示列。`lockedColumns` 会固定为可见，用户不能从方案里移除。

原型文件：

- `addons/foggy-data-viewer/docs/prototypes/custom-query-manager.html`

### 10.6 验收检查点

- 首次加载请求 `/data-viewer/api/table-defaults/default`，并带上 `queryModel`、`tableInstanceId`、`userId`。
- `fetchData(params)` 能收到 `tableInstanceId`。
- `params.columns` 包含展示列 + `lockedColumns` + `requiredRuntimeColumns`。
- 用户保存默认 ListPreset 后刷新，优先使用用户默认配置。
- 没有用户默认 ListPreset 时，能按租户、角色、系统 fallback 继续加载默认配置。
- 清空条件后，当前筛选为空，列配置和排序保持不变。

---

## 11. Query Hooks 能做什么

`DataTableWithSearch` / `useTableQuery` 支持三类 hook：

- `onBeforeQuery`
- `onAfterQuery`
- `onQueryError`

适合做：

- 自动补租户条件
- 自动补组织条件
- 请求前参数改写
- 结果二次加工
- 统一错误提示

### 声明式用法

```ts
const hooks = {
  onBeforeQuery(ctx) {
    ctx.params.slice.push({ field: 'tenantId', op: '=', value: 't001' })
  },
  onQueryError(_ctx, error) {
    console.error(error)
    return true
  }
}
```

### 全局用法

```ts
import { globalQueryHooks } from 'foggy-data-viewer'

const dispose = globalQueryHooks.add('onBeforeQuery', (ctx) => {
  ctx.params.slice.push({ field: 'tenantId', op: '=', value: 't001' })
})
```

执行顺序按源码当前实现为：

- Before: global -> props -> instance
- After: instance -> props -> global
- Error: instance -> props -> global

---

## 12. 后端接口总览

### viewer 相关

- `GET /data-viewer/api/query/{model}/{queryId}/meta`
- `POST /data-viewer/api/query/{model}/{queryId}/data`
- `POST /data-viewer/api/query/create`
- `GET /data-viewer/api/schema/{qmModel}`
- `GET /data-viewer/api/schema/download/{qmModel}`
- `GET /data-viewer/api/frontend-meta/{qmModel}`
- `GET /data-viewer/api/frontend-meta/download/{qmModel}`
- `POST /data-viewer/api/members/query`
- `GET /data-viewer/api/query/{model}/{queryId}/filter-options/{columnName}`
- `POST /data-viewer/api/query/direct/{qmModel}`

### 页面相关

- `GET /data-viewer`
- `GET /data-viewer/view/{model}/{queryId}`

### saved-query 相关

- `POST /data-viewer/api/saved-query`
- `GET /data-viewer/api/saved-query/list/{model}`
- `GET /data-viewer/api/saved-query/{id}`
- `PUT /data-viewer/api/saved-query/{id}`
- `DELETE /data-viewer/api/saved-query/{id}`
- `POST /data-viewer/api/saved-query/{id}/apply`

### list-preset 相关

- `GET /data-viewer/api/list-preset/users/{userId}/models/{model}`
- `GET /data-viewer/api/list-preset/users/{userId}/models/{model}/default`
- `POST /data-viewer/api/list-preset/users/{userId}/models/{model}`
- `GET /data-viewer/api/list-preset/users/{userId}/presets/{id}`
- `PUT /data-viewer/api/list-preset/users/{userId}/presets/{id}`
- `DELETE /data-viewer/api/list-preset/users/{userId}/presets/{id}`
- `POST /data-viewer/api/list-preset/users/{userId}/presets/{id}/default`
- `DELETE /data-viewer/api/list-preset/users/{userId}/models/{model}/default`

这些接口通过 query 参数 `businessKey` 接收 `tableInstanceId` 的旧兼容隔离字段。

### table-defaults 相关

- `GET /data-viewer/api/table-defaults/default`

---

## 13. 使用时必须注意的几个实现事实

下面这些点是我按当前源码确认出来的，和旧文档相比更可靠。

### 13.1 `buildTableColumns` 当前默认会显示全部列

`frontend/src/utils/schemaHelper.ts` 当前逻辑是：

- 如果 `showAll = true`，显示全部列
- 如果 `visibleColumns` 未传或是空数组，也显示全部列

也就是说，当前实现不是“必须显式传 `visibleColumns`”，而是“未传时默认 show all”。

### 13.2 `enableSavedQuery` prop 目前只是声明，没有自动渲染 saved-query UI

`DataTableWithSearch.vue` 里有 `enableSavedQuery?: boolean`，但当前组件模板没有依据这个 prop 自动挂出 `SavedQueryManager`。

实际可用方式仍然是：

- 显式在页面上渲染 `SavedQueryManager`
- 通过 `tableRef` 与 `DataTableWithSearch` 协作

### 13.3 `businessId` 目前只在前端类型里出现，后端实现尚未真正落地

前端 `savedQuery.ts` 和若干文档里提到了 `businessId`，但当前后端源码中的：

- `SavedQueryDef`
- `SavedQueryService`
- `SavedQueryController`

都没有真正保存或按 `businessId` 过滤查询。

因此当前版本里，不能把 `businessId` 当成已经生效的后端隔离能力。

### 13.4 维度过滤选项已有 queryId 模式接口

前端 API 里有：

- `fetchFilterOptions(model, queryId, columnName)`

对应后端路径是：

- `GET /data-viewer/api/query/{model}/{queryId}/filter-options/{columnName}`

当前 `ViewerApiController` 已实现这个 endpoint。接口会先校验 `queryId` 缓存，再从缓存的 `tableConfig.qmModel` 或路径 `model` 推导 QM，最后委托 `MemberQueryService` 查询维度成员。

这意味着：

- queryId viewer 模式下可以直接使用该接口。
- 直连查询或生成组件不走 queryId 时，可以优先使用 `/data-viewer/api/members/query` 或自定义 `filterOptionsLoader`。
- 接口异常时会返回空 options，避免前端过滤器直接报错。

### 13.5 saved-query 的“应用查询”目前重点是恢复筛选与排序，列变更要看父层怎么处理

`DataTableWithSearch` 暴露了：

- `getQueryState()`
- `applyQueryState()`

但源码注释已经写明：列的应用需要父组件处理，因为 schema 是从 props 传入的。

也就是说，saved-query 若要彻底恢复列显示顺序，父层通常还要配合重建 schema / columns。

---

## 14. 对前端团队的实际建议

### 如果你想最快上线

- 直接用后端托管页
- 通过 `createQuery` 或 `dataset.open_in_viewer` 获取 `viewerUrl`

### 如果你想嵌入业务系统

- 优先使用 `DataTableWithSearch`
- 让数据源完全走服务端
- 用 `buildTableColumns` 统一做列构建

### 如果你要做复杂报表页

- 用 `DataTableWithSearch + SavedQueryManager + QueryHooks`
- 对租户、组织、权限条件统一走 hooks 注入

### 如果你只需要一个基础表格底座

- 直接用 `DataTable`
- 继续透传 `vxe-grid` 属性和事件

---

## 15. 一个推荐的落地组合

对业务前端来说，比较稳的组合是：

1. 后端负责提供 `queryId` 或 DSL -> `viewerUrl` 转换能力。
2. 页面内嵌场景使用 `DataTableWithSearch`。
3. 元数据统一来自 `/data-viewer/api/schema/{qmModel}`。
4. 查询数据统一走 `/data-viewer/api/query/{model}/{queryId}/data`。
5. 权限附加条件统一放到 `queryHooks` 或 `globalQueryHooks`。
6. saved-query 在引入前先确认后端是否真的配好 `SecurityIdentityResolver`，并注意 `businessId` 当前未真正落地。
7. 多页面复用同一 QM 时，给每张业务表格配置稳定的 `tableInstanceId`，并把 `requiredRuntimeColumns` / `lockedColumns` 放到 TM/QM frontend-meta。

如果后续要继续完善这个 addon，优先建议补的点是：

- 让 `businessId` 在后端 domain / repository / service / controller 全链路生效
- 明确 `enableSavedQuery` 的真实行为，要么删掉，要么做成真正自动挂载
