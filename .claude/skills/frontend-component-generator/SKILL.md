---
name: frontend-component-generator
description: 自动生成基于 foggy-data-viewer 的 Vue 业务组件。根据 QM 模型自动拉取 schema、生成组件代码、配置文件和文档。当用户需要快速创建数据表格业务组件时使用。
---

# Frontend Component Generator

根据 QM 模型生成完整的 Vue 3 数据表格组件。

## 前置条件

需先运行 `/foggy-frontend-init` 完成环境初始化。

## 两种工作模式

### Schema 模式（推荐，本技能默认）
```vue
<DataTableWithSearch
  :schema="tableSchema"
  :fetch-data="fetchData"
>
  <template #toolbar>
    <button @click="handleAdd">新增</button>
  </template>
</DataTableWithSearch>
```
组件自动管理分页、排序、筛选状态。工具栏布局：左侧插槽放按钮，右侧自动显示分页。

### 受控模式
```vue
<DataTableWithSearch
  :columns="columns"
  :data="data"
  :total="total"
  :loading="loading"
  @page-change="handlePageChange"
/>
```
用户手动管理所有状态。

## 执行流程

### 1. 检查环境
- `package.json` 有 `foggy-data-viewer`、`vxe-table`、`vxe-pc-ui`
- `src/apis/common/dslQuery.ts` 存在
- `src/main.js` 包含 `app.use(VxeUI)` 和 `app.use(VXETable)`

缺失则提示运行 `/foggy-frontend-init`。

### 2. 读取配置
- `.claude/config/semantic-api.config.json`
- `.claude/config/component-generator.config.json`（可选）

### 3. 获取模型 schema
使用 `qm-schema-viewer` 技能获取 QM 模型的列信息。

### 4. 确认生成配置
询问用户：组件名称（PascalCase）、显示的列、组件描述。

### 5. 生成文件

#### 主组件 `src/{path}/models/{ComponentName}.vue`
```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTableWithSearch } from 'foggy-data-viewer'
import type { TableSchema, FetchDataParams, FetchDataResult } from 'foggy-data-viewer'
import { tableSchema } from './schemas/{name}.schema'
import { fetchData as apiFetch } from './apis/{name}.api'

const tableRef = ref<InstanceType<typeof DataTableWithSearch>>()

const schema: TableSchema = tableSchema

async function fetchData(params: FetchDataParams): Promise<FetchDataResult> {
  return apiFetch(params)
}

defineExpose({
  refresh: () => tableRef.value?.refresh(),
  reload: () => tableRef.value?.reload(),
})
</script>

<template>
  <DataTableWithSearch
    ref="tableRef"
    :schema="schema"
    :fetch-data="fetchData"
  >
    <template v-if="$slots.toolbar" #toolbar>
      <slot name="toolbar" />
    </template>
  </DataTableWithSearch>
</template>
```

#### Schema 配置 `src/{path}/models/schemas/{name}.schema.ts`
```typescript
import type { EnhancedColumnSchema, TableSchema } from 'foggy-data-viewer'

export const columns: EnhancedColumnSchema[] = [
  { name: 'id', type: 'INTEGER', title: 'ID', fixed: 'left' },
  { name: 'name', type: 'TEXT', title: '名称', filterType: 'text' },
  // ... 根据 QM schema 生成
]

export const tableSchema: TableSchema = {
  columns,
  pageSize: 50,
  showFilters: true,
  showPager: true,  // 分页栏（默认显示）
}
```

#### API 层 `src/{path}/models/apis/{name}.api.ts`
```typescript
import { query } from '@/apis/common/dslQuery'
import type { FetchDataParams, FetchDataResult } from 'foggy-data-viewer'

export async function fetchData(params: FetchDataParams): Promise<FetchDataResult> {
  const result = await query('ModelName', {
    filters: params.slice,
    orderBy: params.orderBy,
    page: params.page,
    pageSize: params.pageSize,
  })
  return { items: result.items, total: result.total }
}
```

### 6. 输出总结
```
✅ 组件生成完成！

📁 生成的文件：
  - src/{path}/models/{ComponentName}.vue
  - src/{path}/models/schemas/{name}.schema.ts
  - src/{path}/models/apis/{name}.api.ts

🚀 使用：
  import {ComponentName} from '@/{path}/models/{ComponentName}.vue'
```

## 输出目录结构
```
src/{commonComponentPath}/models/
├── {ComponentName}.vue
├── schemas/{name}.schema.ts
└── apis/{name}.api.ts
```

## 决策规则
- 找不到匹配模型 → 询问用户手动输入模型名称
- 目标组件已存在 → 询问是否覆盖
- API 调用失败 → 显示错误，检查 API 地址

## 相关技能
- `qm-schema-viewer` - 获取模型 schema
- `frontend-dsl-query` - DSL 查询语法参考
