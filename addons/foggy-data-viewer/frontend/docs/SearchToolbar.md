# SearchToolbar 和 DataTableWithSearch 组件使用指南

## 组件概述

### SearchToolbar
独立的搜索工具栏组件，提供字段级快速筛选功能。

**特点：**
- ✅ 独立组件，可单独使用或集成到 DataTable
- ✅ 复用 DataTable 的过滤器组件（TextFilter、NumberRangeFilter、DateRangeFilter 等）
- ✅ 支持水平/垂直布局
- ✅ 支持配置可搜索字段
- ✅ 使用 DSL SliceRequestDef 格式，与 DataTable 保持一致

### DataTableWithSearch
组合了 SearchToolbar 和 DataTable 的高级组件。

**特点：**
- ✅ 完整的属性透传到底层组件
- ✅ 完整的事件透传
- ✅ 支持搜索工具栏和表头过滤器的筛选条件合并
- ✅ 暴露子组件实例，方便调用方法
- ✅ 统一的配置接口

---

## SearchToolbar 使用

### 基本用法

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { SearchToolbar, buildTableColumns } from 'foggy-data-viewer'
import type { EnhancedColumnSchema, SliceRequestDef } from 'foggy-data-viewer'

const qmSchema = [
  { name: 'customerName', type: 'TEXT', title: '客户名称', filterable: true },
  { name: 'orderDate', type: 'DAY', title: '下单日期', filterable: true },
  { name: 'amount', type: 'MONEY', title: '订单金额', filterable: true }
]

const columns = ref<EnhancedColumnSchema[]>(
  buildTableColumns(qmSchema, { showAll: true })
)

const searchSlices = ref<SliceRequestDef[]>([])

function handleSearchChange(slices: SliceRequestDef[]) {
  console.log('筛选条件变化:', slices)
  // 发送请求到后端
}

function handleSearch() {
  console.log('点击搜索按钮')
}

function handleReset() {
  console.log('点击重置按钮')
}
</script>

<template>
  <SearchToolbar
    :columns="columns"
    :searchable-fields="['customerName', 'orderDate', 'amount']"
    v-model="searchSlices"
    @update:model-value="handleSearchChange"
    @search="handleSearch"
    @reset="handleReset"
  />
</template>
```

### Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| columns | `EnhancedColumnSchema[]` | - | 列配置（必填） |
| searchableFields | `string[]` | - | 可搜索的字段列表（不指定则显示所有可筛选字段） |
| modelValue | `SliceRequestDef[]` | - | 当前筛选条件（v-model） |
| layout | `'horizontal' \| 'vertical'` | `'horizontal'` | 布局方式 |
| showActions | `boolean` | `true` | 是否显示操作按钮（搜索、重置） |
| filterOptionsLoader | `Function` | - | 过滤选项加载器（用于维度列） |

### Events

| 事件名 | 参数 | 说明 |
|--------|------|------|
| update:modelValue | `(slices: SliceRequestDef[])` | 筛选条件变化（实时） |
| search | - | 点击搜索按钮 |
| reset | - | 点击重置按钮 |

### Methods

```typescript
const searchToolbarRef = ref<InstanceType<typeof SearchToolbar>>()

// 清空所有筛选
searchToolbarRef.value?.clearFilters()

// 获取当前筛选条件
const filters = searchToolbarRef.value?.getFilters()
```

### 布局模式

**水平布局（默认）：**
```vue
<SearchToolbar
  :columns="columns"
  layout="horizontal"
/>
```

**垂直布局：**
```vue
<SearchToolbar
  :columns="columns"
  layout="vertical"
/>
```

---

## DataTableWithSearch 使用

### 基本用法

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTableWithSearch, buildTableColumns } from 'foggy-data-viewer'
import type { EnhancedColumnSchema, SliceRequestDef } from 'foggy-data-viewer'

const qmSchema = [
  { name: 'id', type: 'INTEGER', title: 'ID', filterable: true },
  { name: 'customerName', type: 'TEXT', title: '客户名称', filterable: true },
  { name: 'amount', type: 'MONEY', title: '订单金额', filterable: true }
]

const columns = ref<EnhancedColumnSchema[]>(
  buildTableColumns(qmSchema, { showAll: true })
)

const data = ref([])
const total = ref(0)
const loading = ref(false)

function loadData(slices?: SliceRequestDef[]) {
  loading.value = true
  // 发送请求到后端，传递 slices 筛选条件
  // ...
}

function handleFilterChange(slices: SliceRequestDef[]) {
  console.log('筛选条件变化（合并后）:', slices)
  loadData(slices)
}
</script>

<template>
  <DataTableWithSearch
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
    query-mode="panel"
    :searchable-fields="['customerName', 'amount']"
    @filter-change="handleFilterChange"
  />
</template>
```

### Props

DataTableWithSearch 继承了 DataTable 的所有 Props，并在 `queryMode` 需要 SearchToolbar 时透传 SearchToolbar 相关配置。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| queryMode | `'panel' \| 'column' \| 'combined' \| 'none'` | - | 内置查询入口模式 |
| searchableFields | `string[]` | - | 搜索工具栏可搜索字段 |
| searchLayout | `'horizontal' \| 'vertical'` | `'horizontal'` | 搜索工具栏布局 |
| showSearchActions | `boolean` | `true` | 是否显示搜索按钮 |
| filterMergeMode | `'replace' \| 'merge'` | `'merge'` | 筛选条件合并模式 |
| localFilter | `boolean` | `schema + fetchData: false；受控模式: true` | 是否把列头筛选条件应用到当前页 `data` |

### 查询入口模式

推荐新页面优先使用 `queryMode`，避免同时配置多个查询入口开关后语义不清。

| 模式 | 显示内容 | 适用场景 |
|------|----------|----------|
| `panel` | 只显示组件面板查询；不显示列头筛选 | 页面需要一个表格上方查询 Form |
| `column` | 只显示列头筛选；不显示组件面板查询 | 页面希望筛选入口贴近列头 |
| `combined` | 组件面板查询和列头筛选同时显示 | 明确需要组合查询 |
| `none` | 不显示组件内置查询入口 | 业务页面已有自定义查询 Form |

当传入 `querySchema` 时，`panel`/`combined` 使用 `QueryPanel` 作为面板查询入口；未传 `querySchema` 时使用 `SearchToolbar` 作为面板查询入口。

未设置 `queryMode` 时不显示 SearchToolbar；旧的 `showQueryPanel` 仍只控制 `QueryPanel`，`showFilters` 仍只控制列头筛选。Schema 模式下 `schema.queryMode` 优先于 `queryMode` prop，`schema.showFilters` 优先于同名 prop。

在 `schema + fetchData` 服务端查询模式下，列头筛选默认只作为后端查询条件，不会再对当前页 `data` 做本地二次过滤。这样可以避免 `groupConcat` 聚合列、派生列、字典展示列等字段因为前端简单比较而隐藏后端已经返回的有效行。需要兼容纯前端筛选时，可显式传入 `localFilter=true`，或在 schema 上配置 `localFilter: true`。

**筛选条件合并模式说明：**
- `merge`：合并搜索工具栏和表头过滤器的条件（默认）
- `replace`：搜索工具栏优先，如果为空则使用表头筛选

`combined` 模式下合并规则与 `filterMergeMode` 一致：默认 `merge` 按 QueryPanel、SearchToolbar、列头筛选的顺序合并，同字段保留先出现的条件；`replace` 模式下 QueryPanel 条件始终保留，其余条件优先使用 SearchToolbar，SearchToolbar 为空时使用列头筛选。

```vue
<!-- 只保留列头筛选 -->
<DataTableWithSearch query-mode="column" />

<!-- 业务页面已有自定义查询 Form，关闭组件内置查询入口 -->
<DataTableWithSearch query-mode="none" />

<!-- 使用组件查询面板，不显示列头筛选 -->
<DataTableWithSearch
  query-mode="panel"
  :query-schema="querySchema"
/>

<!-- 组件查询面板与列头筛选同时使用 -->
<DataTableWithSearch
  query-mode="combined"
  :query-schema="querySchema"
  filter-merge-mode="merge"
/>
```

### Events

| 事件名 | 参数 | 说明 |
|--------|------|------|
| filter-change | `(slices: SliceRequestDef[])` | 筛选条件变化（合并后的条件） |
| search | `(slices: SliceRequestDef[])` | 点击搜索按钮 |
| reset | - | 点击重置按钮 |
| page-change | `(page, size)` | 分页变化 |
| sort-change | `(field, order)` | 排序变化 |
| row-click | `(row, column)` | 行点击 |
| row-dblclick | `(row, column)` | 行双击 |

### Methods

```typescript
const tableRef = ref<InstanceType<typeof DataTableWithSearch>>()

// 获取子组件实例
const searchToolbar = tableRef.value?.getSearchToolbar()
const dataTable = tableRef.value?.getDataTable()

// 清空筛选
tableRef.value?.clearSearchFilters()  // 清空搜索工具栏筛选
tableRef.value?.clearTableFilters()   // 清空表头筛选
tableRef.value?.clearAllFilters()     // 清空所有筛选

// 获取合并后的筛选条件
const filters = tableRef.value?.getMergedFilters()

// 重置分页
tableRef.value?.resetPagination()
```

### 属性透传

DataTableWithSearch 支持透传所有 vxe-table 的属性和事件：

```vue
<DataTableWithSearch
  :columns="columns"
  :data="data"
  :total="total"
  :loading="loading"

  <!-- vxe-table 属性透传 -->
  :border="true"
  :stripe="true"
  :height="600"
  :row-class-name="({ row }) => row.status === 'error' ? 'row-error' : ''"

  <!-- vxe-table 事件透传 -->
  @cell-click="handleCellClick"
  @cell-dblclick="handleCellDblclick"
/>
```

### 插槽透传

DataTableWithSearch 会透传标准插槽，以及 `column-*` / `filter-*` 动态插槽到底层 DataTable：

```vue
<DataTableWithSearch
  :columns="columns"
  :data="data"
  :total="total"
  :loading="loading"
>
  <!-- 自定义列内容 -->
  <template #column-status="{ row, value, column }">
    <button type="button" class="status-link" @click.stop="openStatus(row, column)">
      {{ value || '-' }}
    </button>
  </template>

  <!-- 自定义过滤器 -->
  <template #filter-status="{ column, modelValue, onChange }">
    <MyCustomFilter :model-value="modelValue" @update:model-value="onChange" />
  </template>

  <!-- 空数据提示 -->
  <template #empty>
    <div class="custom-empty">暂无数据</div>
  </template>
</DataTableWithSearch>
```

插槽规则：
- `column-{field}`：覆盖指定字段的单元格内容，参数为 `{ row, value, column }`。
- `filter-{field}`：覆盖指定字段的表头筛选器，参数包含 `{ column, field, modelValue, onChange, onCommit }`。
- `toolbar`、`toolbar-right`、`footer`、`empty`、`row-actions` 为标准插槽，其中 `row-actions` 会映射为固定在右侧的操作列。

`foggy-gen` 生成的 QueryTable wrapper 也会透传 `column-*` / `filter-*` 动态插槽。业务页使用生成组件时，可以直接写：

```vue
<OrderQueryTable>
  <template #column-orderNo="{ row, value }">
    <button type="button" class="link-cell" @click.stop="openOrder(row)">
      {{ value || '-' }}
    </button>
  </template>
</OrderQueryTable>
```

---

## 实战示例

### 示例 1: 独立使用 SearchToolbar

```vue
<template>
  <div>
    <!-- 搜索工具栏 -->
    <SearchToolbar
      :columns="columns"
      :searchable-fields="['customerName', 'orderDate']"
      v-model="searchSlices"
      @search="handleSearch"
    />

    <!-- 自定义表格（不使用 DataTable） -->
    <MyCustomTable :data="filteredData" />
  </div>
</template>
```

### 示例 2: DataTable 的 toolbar 插槽使用 SearchToolbar

```vue
<template>
  <DataTable
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
  >
    <template #toolbar>
      <SearchToolbar
        :columns="columns"
        :searchable-fields="['customerName', 'amount']"
        :show-actions="false"
        v-model="searchSlices"
        @update:model-value="handleSearchChange"
      />
    </template>
  </DataTable>
</template>
```

### 示例 3: 使用 DataTableWithSearch 的完整示例

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { DataTableWithSearch, buildTableColumns } from 'foggy-data-viewer'

const columns = ref([])
const data = ref([])
const total = ref(0)
const loading = ref(false)
const tableRef = ref()

onMounted(() => {
  columns.value = buildTableColumns(qmSchema, { showAll: true })
  loadData()
})

function loadData(slices?: SliceRequestDef[]) {
  loading.value = true

  // 调用后端 API
  api.getData({
    page: 1,
    size: 50,
    slices: slices  // 传递筛选条件
  }).then(res => {
    data.value = res.data
    total.value = res.total
    loading.value = false
  })
}

function handleFilterChange(slices: SliceRequestDef[]) {
  loadData(slices)
  tableRef.value?.resetPagination()  // 重置到第一页
}
</script>

<template>
  <DataTableWithSearch
    ref="tableRef"
    :columns="columns"
    :data="data"
    :total="total"
    :loading="loading"
    query-mode="panel"
    :searchable-fields="['customerName', 'orderDate', 'amount']"
    :search-layout="'horizontal'"
    :filter-merge-mode="'merge'"
    @filter-change="handleFilterChange"
    @search="handleSearch"
    @reset="handleReset"
    @row-dblclick="handleRowDblclick"
  />
</template>
```

---

## 最佳实践

### 1. 筛选字段选择

SearchToolbar 适合放置常用的筛选字段：
- 业务主键字段（订单号、客户名称等）
- 日期范围字段（下单日期、创建时间等）
- 关键指标字段（金额、数量等）

不常用的字段可以放在表头过滤器中。

### 2. 布局选择

- **水平布局**：适合筛选字段较少（3-5个）的场景
- **垂直布局**：适合筛选字段较多（5个以上）的场景

### 3. 合并模式选择

- **merge 模式**：搜索工具栏 + 表头过滤器同时使用，适合复杂查询场景
- **replace 模式**：只使用搜索工具栏筛选，表头过滤器作为备选，适合简单查询场景

### 4. 查询入口选择

- 页面只有组件查询入口：使用 `query-mode="panel"` 或 `query-mode="column"`
- 页面已有业务自定义查询 Form：使用 `query-mode="none"`
- 确实需要面板查询和列头筛选同时存在：使用 `query-mode="combined"` 并确认合并规则

`show-search-toolbar` 已移除；查询入口统一使用 `queryMode` 表达。`show-filters` 仍只控制列头筛选。

### 5. 性能优化

```vue
<DataTableWithSearch
  :columns="columns"
  :data="data"
  :total="total"
  :loading="loading"
  query-mode="panel"
/>
```

---

## 常见问题

### Q: SearchToolbar 和表头过滤器有什么区别？

**A:**
- **SearchToolbar**: 独立区域，更显眼，适合常用筛选字段
- **表头过滤器**: 嵌入表头，节省空间，适合所有可筛选字段

两者可以同时使用，筛选条件会自动合并。

### Q: 如何只使用 SearchToolbar，不显示表头过滤器？

**A:**
```vue
<DataTableWithSearch
  query-mode="panel"
/>
```

### Q: 如何只使用列头筛选，不显示面板查询？

**A:**
```vue
<DataTableWithSearch
  query-mode="column"
/>
```

### Q: 业务页面已有自己的查询 Form，组件内置查询入口应该怎么关？

**A:**
```vue
<DataTableWithSearch
  query-mode="none"
/>
```

### Q: 如何自定义 SearchToolbar 的过滤器组件？

**A:**
通过 `customFilterComponent` 属性：

```typescript
const columns = buildTableColumns(qmSchema, {
  customizations: [
    {
      name: 'status',
      customFilterComponent: MyCustomStatusFilter
    }
  ]
})
```

### Q: 如何在 SearchToolbar 中使用维度过滤器？

**A:**
传递 `filterOptionsLoader` 函数：

```vue
<DataTableWithSearch
  :columns="columns"
  :filter-options-loader="loadDimensionOptions"
  query-mode="panel"
/>
```

```typescript
async function loadDimensionOptions(columnName: string) {
  const res = await api.getDimensionValues(columnName)
  return res.data.map(item => ({
    label: item.caption,
    value: item.id
  }))
}
```

---

## 相关链接

- [DataTable 文档](../README.md)
- [过滤器组件文档](./filters/README.md)
- [在线示例](../../verification-app)
