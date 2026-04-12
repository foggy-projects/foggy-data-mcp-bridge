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

## 五、行操作 (row-actions)

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

## 六、选中行 API

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

## 七、表格高度设置

DataTable 默认使用 `height: '100%'`，要求**父容器有明确的高度约束**。以下是两种常见场景的推荐写法。

### 7.1 固定像素高度

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

### 7.2 填满剩余空间（flex 布局）

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

## 八、完整示例

参考 `addons/foggy-data-viewer/verification-app/src/App.vue`
