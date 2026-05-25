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
    :list-preset="{
      enabled: true,
      model,
      userId: currentUser.id,
      businessKey: 'ticket-list',
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
| `businessKey` | 同一模型在不同业务页面的隔离 key |
| `autoLoadDefault` | 首次加载时是否自动应用默认列表 |
| `placement` | `toolbar-left`、`toolbar-right` 或 `external` |

`userId` 不是安全边界。真实数据权限仍由后端查询链路控制；后续如需从登录态解析用户，可由接入方二次开发后端身份解析。

### 5.2 后端存储

自定义列表 API 路径包含 `/users/{userId}`，配置按 `userId + model + businessKey` 隔离。

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

### 5.3 字段权限校验扩展

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

## 七、选中行 API

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

## 八、表格高度设置

DataTable 默认使用 `height: '100%'`，要求**父容器有明确的高度约束**。以下是两种常见场景的推荐写法。

### 8.1 固定像素高度

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

### 8.2 填满剩余空间（flex 布局）

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

## 九、完整示例

参考 `addons/foggy-data-viewer/verification-app/src/App.vue`
