# Foggy Data Viewer - 技术参考

## 核心组件 API

### DataTable.vue

**Props**：
```typescript
interface DataTableProps {
  columns: EnhancedColumnSchema[]      // 列配置（必填）
  data: any[]                          // 表格数据（必填）
  total: number                        // 总数据量（必填）
  loading?: boolean                    // 加载状态，默认 false
  pageSize?: number                    // 每页数量，默认 50
  showFilters?: boolean                // 显示筛选器，默认 true
  serverSummary?: object | null        // 服务端汇总数据
  initialSlice?: SliceCondition[]      // 初始筛选条件
  filterOptionsLoader?: (field: string) => Promise<FilterOption[]>
}
```

**Emits**：
```typescript
interface DataTableEmits {
  'page-change': (page: number, pageSize: number) => void
  'sort-change': (field: string | null, order: 'asc' | 'desc' | null) => void
  'filter-change': (slices: SliceCondition[]) => void
  'row-click': (row: any, column: any) => void
  'row-dblclick': (row: any, column: any) => void
}
```

**Exposed Methods**：
```typescript
interface DataTableExposed {
  resetPagination(): void
  clearFilters(): void
  getFilters(): SliceCondition[]
  setFilter(field: string, op: string, value: any): void
  getGridInstance(): VxeGridInstance
}
```

### DataViewer.vue

高层封装组件，集成了 API 调用逻辑。

**Props**：
```typescript
interface DataViewerProps {
  queryId: string                      // 查询 ID（必填）
  apiBaseUrl?: string                  // API 基础 URL
  pageSize?: number                    // 每页数量，默认 50
}
```

## Composables API

### useTableSummary

管理表格汇总行的计算和格式化。

```typescript
function useTableSummary(columns: Ref<ColumnSchema[]>) {
  return {
    measureColumns: ComputedRef<ColumnSchema[]>
    serverSummary: Ref<SummaryData | null>

    calculateSelectedSummary(rows: any[]): SummaryData
    formatValue(value: any, type: string): string
    generateFooterData(visibleColumns: any[], selectedSummary: SummaryData): any[][]
    setServerSummary(data: SummaryData | null): void
  }
}
```

**度量类型识别**：
自动识别以下类型为度量字段：
- NUMBER
- MONEY
- BIGDECIMAL
- INTEGER
- BIGINT
- LONG

### useTableSelection

管理表格行选择状态。

```typescript
function useTableSelection<T = any>() {
  return {
    selectedRows: Ref<T[]>

    onCheckboxChange(params: { records: T[] }): void
    onCheckboxAll(params: { records: T[] }): void
    clearSelection(): void
    getSelectedCount(): number
  }
}
```

## 工具函数

### buildTableColumns

```typescript
function buildTableColumns(
  qmSchema: ColumnSchema[],
  config: TableConfig
): EnhancedColumnSchema[]
```

**参数**：
- `qmSchema`: QM 模型的列定义数组
- `config.visibleColumns`: 可见列名称数组（与 showAll 二选一）
- `config.showAll`: 显示所有列（与 visibleColumns 二选一）
- `config.customizations`: 列自定义配置数组

**返回**：增强的列配置数组，包含原始 schema + 自定义配置

**错误处理**：
- 如果 visibleColumns 和 showAll 都未提供 → 抛出错误
- 如果列名在 schema 中不存在 → console.warn 并跳过该列

## 类型定义参考

### ColumnSchema
```typescript
interface ColumnSchema {
  name: string          // 字段名
  type: string          // 数据类型（INTEGER, TEXT, MONEY 等）
  title: string         // 显示标题
}
```

### EnhancedColumnSchema
```typescript
interface EnhancedColumnSchema extends ColumnSchema {
  width?: number                          // 固定宽度
  minWidth?: number                       // 最小宽度，默认 120
  fixed?: 'left' | 'right'                // 固定位置
  customFormatter?: (value: any) => string  // 自定义格式化
  customRender?: (params: any) => VNode   // 自定义渲染
  customFilterComponent?: Component        // 自定义筛选组件
}
```

### TableConfig
```typescript
interface TableConfig {
  qmModel: string                         // QM 模型名称
  visibleColumns?: string[]               // 可见列数组
  showAll?: boolean                       // 显示所有列
  customizations?: ColumnCustomization[]  // 列自定义配置
}
```

### ColumnCustomization
```typescript
interface ColumnCustomization {
  name: string                            // 列名
  width?: number                          // 宽度
  minWidth?: number                       // 最小宽度
  fixed?: 'left' | 'right'                // 固定位置
  formatter?: (value: any) => string      // 格式化函数
  render?: (params: any) => VNode         // 渲染函数
  filterComponent?: Component             // 筛选组件
}
```

### SliceCondition
```typescript
interface SliceCondition {
  field: string                           // 字段名
  op: string                              // 操作符（=, !=, >, <, in, like 等）
  value: any                              // 筛选值
}
```

## 数据类型系统

### JdbcColumnType 映射

| 类型常量 | 别名 | TypeScript 类型 | 格式化 |
|---------|------|----------------|-------|
| MONEY | NUMBER | number | 1,234.56 |
| TEXT | STRING | string | 原样显示 |
| INTEGER | - | number | 1,234 |
| BIGINT | LONG | number | 1,234 |
| BIGDECIMAL | - | number | 1,234.57 |
| DAY | - | Date | YYYY-MM-DD |
| DATETIME | - | Date | YYYY-MM-DD HH:mm:ss |
| BOOL | BOOLEAN | boolean | true/false |
| DICT | - | number | 字典值 |

## 筛选器组件

### 内置筛选器

| 组件 | 适用类型 | 操作符 |
|------|---------|--------|
| TextFilter | TEXT, STRING | =, !=, like, not like |
| SelectFilter | TEXT, DICT | in, not in |
| BoolFilter | BOOL | = |
| NumberRangeFilter | INTEGER, BIGINT, LONG | >, <, >=, <=, between |
| DateRangeFilter | DAY, DATETIME | >, <, between |

### 自定义筛选器

创建自定义筛选器组件：
```typescript
// MyCustomFilter.vue
<script setup lang="ts">
const props = defineProps<{
  modelValue: any
}>()

const emit = defineEmits<{
  'update:modelValue': [value: any]
}>()
</script>

<template>
  <div>
    <!-- 筛选器 UI -->
  </div>
</template>
```

在 TableConfig 中使用：
```typescript
import MyCustomFilter from './MyCustomFilter.vue'

const config: TableConfig = {
  customizations: [
    {
      name: 'myField',
      filterComponent: MyCustomFilter
    }
  ]
}
```

## 测试工具配置

### Vitest 配置
```typescript
// vitest.config.ts
export default defineConfig({
  test: {
    environment: 'happy-dom',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: ['**/*.test.ts', '**/node_modules/**']
    }
  }
})
```

### 测试工具导入
```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref, nextTick } from 'vue'
```

### Mock vxe-table
```typescript
const globalConfig = {
  global: {
    stubs: {
      'vxe-grid': true,
      'vxe-column': true,
      'vxe-toolbar': true
    }
  }
}
```

## 构建配置

### package.json 关键字段
```json
{
  "name": "foggy-data-viewer",
  "version": "1.0.0",
  "type": "module",
  "main": "./dist/index.js",
  "module": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.js",
      "types": "./dist/index.d.ts"
    },
    "./style.css": "./dist/style.css"
  },
  "files": ["dist", "src", "README.md"]
}
```

### Vite 库模式配置
```typescript
// vite.config.ts (mode === 'lib')
{
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'FoggyDataViewer',
      fileName: (format) => `index.${format === 'es' ? 'js' : format}`
    },
    rollupOptions: {
      external: ['vue', 'vxe-table', 'xe-utils', 'axios', 'element-plus'],
      output: {
        globals: {
          vue: 'Vue',
          'vxe-table': 'VxeTable',
          'xe-utils': 'XEUtils'
        }
      }
    }
  }
}
```

## 性能优化建议

### 大数据量优化
- 使用服务端分页（始终传递 total 而非全量数据）
- 使用 vxe-table 的虚拟滚动（默认启用）
- 汇总数据使用服务端计算（传递 serverSummary prop）

### 渲染优化
- 自定义渲染使用 `h()` 函数而非模板字符串
- 避免在 customFormatter 中执行复杂计算
- 使用 `computed` 缓存列配置

### 测试性能
- 使用 `--run` 标志避免 watch 模式
- 使用 `--coverage` 仅在需要时生成覆盖率报告
- 单个测试文件运行：`npm test -- DataTable.test.ts`

## 常见问题排查

### 组件不显示
- 检查是否注册了 vxe-table：`app.use(VxeTable)`
- 检查是否导入了样式：`import 'vxe-table/lib/style.css'`

### 类型错误
- 确保 `@types/node` 已安装
- 确保 `vue-tsc` 版本与 Vue 版本兼容
- 运行 `npm run build:lib` 重新生成类型定义

### 测试失败
- 确保使用了 stub：`stubs: { 'vxe-grid': true }`
- 检查是否需要 `await nextTick()` 等待 DOM 更新
- 使用 `wrapper.vm` 访问组件实例方法

### 构建失败
- 检查 `external` 配置是否包含所有外部依赖
- 确保 `src/index.ts` 导出了所有公开 API
- 运行 `npm install` 确保依赖已安装
