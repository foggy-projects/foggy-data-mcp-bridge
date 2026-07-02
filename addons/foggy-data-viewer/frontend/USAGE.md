# DataTable 组件使用指南

## 一、安装和引入

### 1. 安装依赖

```bash
npm install foggy-data-viewer vxe-table element-plus
```

### 2. 引入样式（只需一次）

```typescript
// main.ts
import { createApp } from 'vue'
import App from './App.vue'

// 引入组件库（自动包含 vxe-table 和 element-plus 样式）
import 'foggy-data-viewer/style.css'

// 注册 vxe-table 和 element-plus（必须）
import VXETable from 'vxe-table'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

const app = createApp(App)
app.use(VXETable)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
```

**注意：只需引入 `foggy-data-viewer/style.css` 一次，无需单独引入 vxe-table 和 element-plus 的样式！**

## 二、基本使用

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTable, buildTableColumns } from 'foggy-data-viewer'
import type { EnhancedColumnSchema } from 'foggy-data-viewer'

// 定义列配置（包含完整的字段信息）
const qmSchema = [
  { 
    name: 'id', 
    type: 'INTEGER', 
    title: 'ID', 
    filterType: 'number', 
    measure: false, 
    aggregatable: false, 
    filterable: true 
  },
  { 
    name: 'amount', 
    type: 'MONEY', 
    title: '金额', 
    filterType: 'number', 
    measure: true, 
    aggregatable: true, 
    filterable: true 
  }
]

// 构建表格列
const columns = ref<EnhancedColumnSchema[]>(
  buildTableColumns(qmSchema, {
    visibleColumns: ['id', 'amount']
  })
)

const data = ref([
  { id: 1, amount: 1000 },
  { id: 2, amount: 2000 }
])

const total = ref(2)
const loading = ref(false)
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

## 三、透传 vxe-table 属性和事件

DataTable 支持透传所有 vxe-table 的属性和事件：

```vue
<template>
  <DataTable
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
    
    <!-- 透传 vxe-table 属性 -->
    :height="600"
    :max-height="800"
    :border="false"
    :stripe="false"
    :show-header-overflow="true"
    :show-footer-overflow="true"
    :highlight-hover-row="true"
    :highlight-current-row="true"
    
    <!-- 透传 vxe-table 事件 -->
    @cell-click="handleCellClick"
    @cell-dblclick="handleCellDblclick"
    @header-click="handleHeaderClick"
    @scroll="handleScroll"
    @page-change="handlePageChange"
  />
</template>
```

**规则：**
- 用户传入的属性会覆盖默认值
- 用户传入的事件会和默认事件共存（都会执行）

## 四、高级功能

### 4.1 自定义列宽和固定列

```typescript
const columns = buildTableColumns(qmSchema, {
  visibleColumns: ['id', 'name', 'amount'],
  customizations: [
    { name: 'id', width: 80, fixed: 'left' },
    { name: 'name', width: 150 },
    { name: 'amount', width: 120 }
  ]
})
```

### 4.2 服务器端汇总

```vue
<template>
  <DataTable
    :columns="columns"
    :data="data"
    :total="total"
    :server-summary="{ total: 150, amount: 1500000 }"
  />
</template>
```

### 4.3 自定义过滤器加载

```typescript
async function loadFilterOptions(columnName: string) {
  const response = await api.fetchFilterOptions(columnName)
  return response.options
}
```

```vue
<template>
  <DataTable
    :columns="columns"
    :data="data"
    :total="total"
    :filter-options-loader="loadFilterOptions"
  />
</template>
```

## 五、自定义列表

`DataTableWithSearch` 可以通过 `listPreset` 开启“自定义列表”入口，用户可保存列显隐、列顺序、列宽、固定列、当前筛选、排序和分页大小。

```vue
<script setup lang="ts">
import { DataTableWithSearch } from 'foggy-data-viewer'

const currentUser = { id: 'u_001' }
const model = 'TicketQueryModel'
</script>

<template>
  <DataTableWithSearch
    :schema="tableSchema"
    :fetch-data="fetchTickets"
    table-instance-id="ticket-list"
    :list-preset="{
      enabled: true,
      model,
      userId: currentUser.id,
      tableInstanceId: 'ticket-list',
      autoLoadDefault: true,
      placement: 'toolbar-right'
    }"
  />
</template>
```

### 5.1 前端配置

| 参数 | 说明 |
|---|---|
| `enabled` | 是否启用自定义列表 |
| `model` | QM 模型名，用于后端按模型隔离配置 |
| `userId` | v1 必填，前端显式传入的用户标识，只作为配置命名空间 |
| `tableInstanceId` | 同一 QM 下的表格实例标识 |
| `businessKey` | `tableInstanceId` 的旧兼容别名 |
| `autoLoadDefault` | 首次加载时是否自动应用默认列表 |
| `placement` | `toolbar-left`、`toolbar-right` 或 `external` |

`userId` 不是安全边界。真实数据权限仍由后端查询链路控制；后续如需从登录态解析用户，可由接入方二次开发后端身份解析。

### 5.2 默认查询配置与 tableInstanceId

`DataTableWithSearch` 在 `schema + fetchData` 模式下可按 `tableInstanceId` 自动加载默认查询配置。加载顺序是先应用后端 fallback，再应用用户默认 `listPreset`；用户列表可覆盖展示列、排序、筛选和分页大小。`requiredRuntimeColumns` 与 `lockedColumns` 由 TM/QM 产出的 `TableSchema` 提供，不放在默认查询配置里。

```vue
<DataTableWithSearch
  :schema="{ ...tableSchema, qmModel: model, tableInstanceId: 'ticket-list' }"
  :fetch-data="fetchTickets"
  :default-query-config-scope="{
    userId: currentUser.id,
    tenantId: currentTenant.id,
    roleIds: currentRoleIds
  }"
/>
```

后端 fallback 示例：

```yaml
foggy:
  data-viewer:
    table-defaults:
      system:
        ticket-list:
          query-model: TicketQueryModel
          table-instance-id: ticket-list
          default-visible-columns: [ticketNo, title, status]
          default-page-size: 50
```

TM/QM frontend-meta 示例：

```json
{
  "defaults": {
    "tableInstanceId": "ticket-list",
    "requiredRuntimeColumns": ["id", "tenantId"],
    "lockedColumns": ["ticketNo"]
  }
}
```

`requiredRuntimeColumns` 只会追加到 `fetchData(params).columns`，不会显示为普通表格列；适合详情跳转、权限判断、行级动作等运行时必需字段。`lockedColumns` 会在应用自定义列表后补回展示。

### 5.3 后端存储

自定义列表 API 路径包含 `/users/{userId}`，配置按 `userId + model + tableInstanceId/businessKey` 隔离。

默认 `storage: AUTO`：配置了 `spring.data.mongodb.uri` 时使用 Mongo；没有提供 Mongo URI 时直接使用文件系统。显式配置 `storage: MONGO` 时优先使用 Mongo，运行时不可用会退化到文件系统；显式配置 `storage: FILE` 时只使用文件系统。

```yaml
foggy:
  data-viewer:
    list-preset:
      storage: AUTO # AUTO | MONGO | FILE
      file-base-dir: data-viewer/list-presets
```

文件系统降级目录按安全清洗后的路径片段组织：

```text
{file-base-dir}/{safeUserId}/{safeModel}/{safeBusinessKey}/
  presets/{presetId}.json
  default.json
```

### 5.4 字段权限校验扩展

默认情况下，自定义列表后端只做基础结构校验，例如字段名非空、固定列取值合法、分页大小合法。若接入方需要校验字段是否属于当前 QM schema，或校验当前用户是否有字段权限，可以注册 `ListPresetFieldValidator` Bean。

```java
import com.foggyframework.dataviewer.service.listpreset.ListPresetFieldValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataViewerListPresetConfig {

    @Bean
    public ListPresetFieldValidator listPresetFieldValidator() {
        return (userId, model, businessKey, request) -> {
            // 按 userId/model/businessKey 查询接入方自己的 schema/权限服务。
            // 校验 request.getColumns(), request.getColumnSettings(),
            // request.getQuery().getSlice(), request.getQuery().getOrderBy()。
            // 不允许保存时抛 IllegalArgumentException，Controller 会转成业务失败响应。
        };
    }
}
```

没有注册该 Bean 时使用 no-op 校验器，避免强制依赖接入方的模型权限服务。

### 5.4 使用边界

- 第一版保存组件当前可表达的列、筛选、排序和分页状态，不提供完整 DSL 编辑器。
- 保存时至少需要保留一个可见字段。
- 应用列表时，若当前 schema 已不存在某些字段，组件会提示失效字段；实际列应用会以当前 schema 为准。
- `visibility` 暂保留为结构字段，强共享权限模型后续再完善。

### 5.5 验证命令

组件库单元测试与构建：

```bash
cd addons/foggy-data-viewer/frontend
npm test -- --run
npm run build:lib
```

verification-app 浏览器流程测试：

```bash
cd addons/foggy-data-viewer/verification-app
npm run test:e2e -- --project=chromium
```

Mongo store 真实读写测试默认跳过，避免没有 Mongo 的本地或 CI 环境被外部服务阻塞。需要验证真实 Mongo 时，先准备 Mongo 实例，再显式开启：

```bash
set FOGGY_DATA_VIEWER_MONGO_IT=true
set FOGGY_DATA_VIEWER_MONGO_URI=mongodb://localhost:27017/foggy_data_viewer_it
mvn test -pl addons/foggy-data-viewer -Dtest=MongoListPresetStoreIntegrationTest
```

## 六、行操作 (row-actions)

`DataTableWithSearch` 支持 `row-actions` 标准扩展点。传入该插槽后，组件自动注入一个固定在右侧的操作列：

```vue
<template>
  <DataTableWithSearch
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
  >
    <template #row-actions="{ row }">
      <button @click="handleEdit(row)">编辑</button>
      <button @click="handleDelete(row)">删除</button>
    </template>
  </DataTableWithSearch>
</template>
```

**说明：**
- 操作列名为 `_actions`，固定在右侧，默认宽度 120px
- 如果你的列配置中已包含 `_actions` 列，不会重复注入，使用你的配置
- 已有的 `column-actions` 用法（裸 DataTable 自定义列）仍然兼容

## 七、列内容自定义

### 7.1 使用 `column-*` 插槽渲染可点击单元格

业务页面需要把某一列渲染成“看起来像链接，并由业务页决定点击行为”时，推荐使用 `#column-{field}` 插槽。插槽参数为 `{ row, value, column }`。

```vue
<script setup lang="ts">
function openOrder(row: Record<string, unknown>) {
  // 可在这里打开详情弹窗、路由跳转，或调用上游业务逻辑
}
</script>

<template>
  <DataTableWithSearch
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
  >
    <template #column-orderNo="{ row, value }">
      <button type="button" class="table-link-cell" @click.stop="openOrder(row)">
        {{ value || '-' }}
      </button>
    </template>
  </DataTableWithSearch>
</template>

<style scoped>
.table-link-cell {
  padding: 0;
  border: 0;
  background: transparent;
  color: #2f7d5f;
  font: inherit;
  cursor: pointer;
}

.table-link-cell:hover {
  color: #1f9d6a;
  text-decoration: underline;
}
</style>
```

### 7.2 生成 QueryTable wrapper 的用法

`foggy-gen` 生成的 QueryTable wrapper 会继续透传 `column-*` / `filter-*` 动态插槽，所以业务页可以直接在生成组件上定制列内容：

```vue
<VehicleCapacityProfileManagementQueryTable
  :global-params="globalParams"
  :custom-params="customParams"
>
  <template #column-profileName="{ row, value }">
    <button type="button" class="table-link-cell" @click.stop="openProfile(row)">
      {{ value || '-' }}
    </button>
  </template>
</VehicleCapacityProfileManagementQueryTable>
```

### 7.3 使用 `render` 做纯展示渲染

如果只是加颜色、图标或标签，不需要把点击行为暴露给业务页，可以使用 `ColumnCustomization.render`，或在生成组件的 `columnOverrides[field].render` 中配置：

```typescript
import { h } from 'vue'

const columnOverrides = {
  status: {
    render: ({ value, column }) => h(
      'span',
      { class: value === 'enabled' ? 'status-ok' : 'status-muted' },
      `${column.title}: ${String(value ?? '-')}`
    )
  }
}
```

### 7.4 注册全局列渲染器

如果多个 QM 表格都要把同一类字段渲染成统一入口，不要修改 `generated/*QueryTable.vue`，也不要在每个页面重复写插槽。可以在业务应用启动时注册全局渲染器：

```typescript
import { h } from 'vue'
import { globalColumnRenderers } from 'foggy-data-viewer'

globalColumnRenderers.add({
  id: 'app.orderNoLink',
  priority: 100,
  match: ({ column }) => column.name === 'orderNo' && /运单号/.test(column.title ?? ''),
  render: ({ value }) => {
    const orderNo = String(value ?? '').trim()
    if (!orderNo) return '-'
    return h('button', {
      type: 'button',
      class: 'table-link-cell',
      onClick: (event: MouseEvent) => {
        event.stopPropagation()
        // router.push(...) 留在业务应用侧
      }
    }, orderNo)
  }
})
```

渲染优先级：`column-*` 插槽 > `column.customRender` > 全局列渲染器 > 默认渲染 / 字典 / formatter。需要退出全局渲染时，在列元数据上设置 `uiConfig.disableGlobalRender = true`。

选择原则：单页特殊交互优先用 `column-*` 插槽；纯展示型单元格可以用 `render`；跨表格统一入口用 `globalColumnRenderers`。

### 7.5 搜索生命周期 Hook

`globalSearchHooks` 运行在 `DataTableWithSearch` 的搜索动作层，早于最终 `fetchData` 与 `globalQueryHooks.onBeforeQuery`。上下文可区分入口来源和触发原因：

```typescript
import { globalSearchHooks } from 'foggy-data-viewer'

const dispose = globalSearchHooks.register({
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
    return false
  }
})

dispose()
```

`source` 取值：`search-toolbar`、`query-panel`、`column-filter`、`external`、`api`。`trigger` 取值：`search`、`reset`、`filter`、`sort`、`page`、`refresh`、`reload`、`mount`。

## 八、选中行 API

`DataTable` 和 `DataTableWithSearch` 都提供以下方法（通过组件 ref 调用）：

```vue
<script setup>
const tableRef = ref()

function handleBatchDelete() {
  const rows = tableRef.value.getSelectedRows()
  const count = tableRef.value.getSelectedCount()
  console.log(`将删除 ${count} 条记录`, rows)
  // ... 执行批量操作
  tableRef.value.clearSelection()
}
</script>

<template>
  <DataTableWithSearch ref="tableRef" ... >
    <template #toolbar>
      <button @click="handleBatchDelete">
        批量删除 ({{ tableRef?.getSelectedCount?.() ?? 0 }})
      </button>
    </template>
  </DataTableWithSearch>
</template>
```

| 方法 | 返回值 | 说明 |
|---|---|---|
| `getSelectedRows()` | `Record<string, unknown>[]` | 当前选中行数组 |
| `getSelectedCount()` | `number` | 当前选中行数量 |
| `clearSelection()` | `void` | 清空所有选中行 |

## 九、表格高度设置

DataTable 默认使用 `height: '100%'`，要求**父容器有明确的高度约束**。以下是两种常见场景的推荐写法。

### 9.1 固定像素高度

最简单的方式，直接给容器设固定高度：

```vue
<template>
  <div style="height: 600px;">
    <DataTableWithSearch :columns="columns" :data="data" :total="total" :loading="loading" />
  </div>
</template>
```

或通过 vxe-table 透传属性指定表格自身高度：

```vue
<DataTable :columns="columns" :data="data" :total="total" :loading="loading" :height="500" />
```

### 9.2 填满剩余空间（flex 布局）

当表格位于页面主内容区、需要自动填满剩余高度时，需要保证**从页面根到表格容器的完整 flex 高度链路**：

```vue
<template>
  <!-- 页面容器需要有明确高度 -->
  <div class="page" style="height: 100vh; display: flex; flex-direction: column;">
    <!-- 顶部固定区域 -->
    <header style="flex-shrink: 0;">页面标题</header>

    <!-- 表格填满剩余空间 -->
    <div style="flex: 1; min-height: 0; display: flex; flex-direction: column;">
      <DataTableWithSearch :columns="columns" :data="data" :total="total" :loading="loading" />
    </div>
  </div>
</template>
```

**关键要点：**
- 父容器必须有 `height`（`100vh`、固定像素、或 `flex: 1`）
- flex 容器需要 `min-height: 0` 以允许子元素收缩
- 百分比高度 `height: 100%` 依赖所有祖先节点都有明确高度，否则不会生效
- 组件**不承诺**在无高度约束的容器中自动铺满

## 十、完整示例

参考 `addons/foggy-data-viewer/verification-app/src/App.vue`
