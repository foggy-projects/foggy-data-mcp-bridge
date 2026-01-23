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

## 五、完整示例

参考 `addons/foggy-data-viewer/verification-app/src/App.vue`
