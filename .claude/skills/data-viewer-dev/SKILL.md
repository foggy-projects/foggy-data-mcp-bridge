---
name: data-viewer-dev
description: 开发和维护 foggy-data-viewer Vue 3 组件库（DataTable、Composables、工具函数）。当用户需要开发新组件、修改现有组件、编写测试、更新文档或构建发布时使用。
---

# Foggy Data Viewer 组件开发

开发和维护 foggy-data-viewer Vue 3 组件库。

## 工作目录

**组件库**：`addons/foggy-data-viewer/frontend`
**验证项目**：`addons/foggy-data-viewer/verification-app`

**核心文件**：
- `src/components/DataTable.vue` - 核心表格（工具栏、分页、排序、汇总）
- `src/components/DataTableWithSearch.vue` - 组合组件（SearchToolbar + DataTable）
- `src/components/SearchToolbar.vue` - 独立搜索工具栏
- `src/components/composables/` - 可复用逻辑
- `src/components/filters/` - 筛选器组件
- `src/types/index.ts` - 类型定义
- `src/index.ts` - 导出入口

## 组件架构

### DataTable 工具栏布局
```
┌─────────────────────────────────────────────────────┐
│ [toolbar 插槽: 自定义按钮]          [vxe-pager 分页] │  ← 顶部工具栏
├─────────────────────────────────────────────────────┤
│                    vxe-grid 表格                     │
└─────────────────────────────────────────────────────┘
```

- 工具栏：左侧 `#toolbar` 插槽，右侧 `vxe-pager` 分页组件
- `showPager` prop 控制分页显示（默认 true）
- 分页组件使用 `vxe-pager`（非 vxe-grid 内置 pagerConfig）

### DataTableWithSearch 两种模式

**Schema 模式**（推荐）：
```vue
<DataTableWithSearch :schema="tableSchema" :fetch-data="fetchData">
  <template #toolbar>
    <button @click="handleAdd">新增</button>
  </template>
</DataTableWithSearch>
```
组件自动管理分页、排序、筛选状态。

**受控模式**：
```vue
<DataTableWithSearch :columns="columns" :data="data" :total="total" :loading="loading" />
```
用户手动管理所有状态。

## 执行流程

1. **分析需求** - 读取相关组件代码，确定修改范围
2. **开发/修改** - 遵循 Vue 3 `<script setup lang="ts">` 风格
3. **编写测试** - 使用 Vitest，vxe 组件用 stub
4. **运行测试** - `cd addons/foggy-data-viewer/frontend && npm test -- --run`
5. **构建库** - `npm run build:lib`
6. **验证功能** - 在 verification-app 中测试

## 关键约束

### 代码规范
- TypeScript 严格模式，禁止 `any`
- Vue 3 Composition API `<script setup lang="ts">`
- Props 用 `defineProps`，Events 用 `defineEmits`

### 测试要求
- vxe 组件必须 stub：`global: { stubs: { 'vxe-grid': true, 'vxe-pager': true } }`
- 核心组件覆盖率 ≥90%

### 构建要求
- 使用 `npm run build:lib`（不是 `npm run build`）
- vue、vxe-table、vxe-pc-ui、axios 标记为 external

## 依赖配置

### vxe-table v4.7+ 要求

组件库 `src/index.ts` 统一导入样式：
```typescript
import 'vxe-pc-ui/lib/style.css'
import 'vxe-table/lib/style.css'
import 'element-plus/dist/index.css'
```

使用方 `main.ts` 必须注册（顺序重要）：
```typescript
import VxeUI from 'vxe-pc-ui'
import VXETable from 'vxe-table'
import 'foggy-data-viewer/style.css'

app.use(VxeUI)      // 必须在 VXETable 之前
app.use(VXETable)
```

### verification-app 配置

`verification-app/src/main.ts`：
```typescript
import VxeUI from 'vxe-pc-ui'
import VXETable from 'vxe-table'
import ElementPlus from 'element-plus'
import 'foggy-data-viewer/style.css'

app.use(VxeUI)
app.use(VXETable)
app.use(ElementPlus, { locale: zhCn })
```

## 决策规则

- 修改 DataTable → 更新 DataTable.test.ts
- 添加新 Composable → 在 `composables/` 创建，在 `index.ts` 导出
- 添加新类型 → 在 `types/index.ts` 定义，在 `index.ts` 导出
- 修改 Props/Events → 更新对应测试和 README
- verification-app 报错 → 先执行 `npm run build:lib` 重新构建

## 已知陷阱

### SearchToolbar 事件重复
```vue
<!-- 错误：会发两次请求 -->
<SearchToolbar v-model="slices" @update:model-value="search" @search="search" />

<!-- 正确：只监听 @search -->
<SearchToolbar v-model="slices" @search="search" />
```

### CSS 滚动同步
禁止设置 `overflow: visible` 在表头元素，会破坏横向滚动同步。

### 构建后生效
修改 frontend 代码后，**必须 `npm run build:lib`** 才能在 verification-app 生效。

## 类型导出清单

`src/index.ts` 导出的主要类型：
- `TableSchema`, `ColumnSchema`, `EnhancedColumnSchema`
- `FetchDataParams`, `FetchDataResult`, `FetchDataFn`
- `SliceRequestDef`, `OrderRequestDef`
- `PaginationState`, `SortState`
- `TableConfig`, `ColumnCustomization`
