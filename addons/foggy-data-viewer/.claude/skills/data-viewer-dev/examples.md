# Foggy Data Viewer - 开发示例

## 场景 1：添加新的 Composable

**需求**：添加一个 `useTableExport` composable，用于导出表格数据为 CSV/Excel。

**步骤**：

1. 创建 composable 文件：
```typescript
// src/components/composables/useTableExport.ts
import { ref } from 'vue'

export function useTableExport() {
  const exporting = ref(false)

  async function exportToCSV(data: any[], columns: string[]) {
    exporting.value = true
    try {
      const csv = convertToCSV(data, columns)
      downloadFile(csv, 'export.csv', 'text/csv')
    } finally {
      exporting.value = false
    }
  }

  function convertToCSV(data: any[], columns: string[]): string {
    const headers = columns.join(',')
    const rows = data.map(row =>
      columns.map(col => JSON.stringify(row[col] ?? '')).join(',')
    )
    return [headers, ...rows].join('\n')
  }

  function downloadFile(content: string, filename: string, mimeType: string) {
    const blob = new Blob([content], { type: mimeType })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
  }

  return {
    exporting,
    exportToCSV
  }
}
```

2. 创建测试文件：
```typescript
// src/components/composables/useTableExport.test.ts
import { describe, it, expect, vi } from 'vitest'
import { useTableExport } from './useTableExport'

describe('useTableExport', () => {
  it('should initialize with exporting as false', () => {
    const { exporting } = useTableExport()
    expect(exporting.value).toBe(false)
  })

  it('should convert data to CSV format', async () => {
    const { exportToCSV } = useTableExport()

    // Mock document methods
    const createElementSpy = vi.spyOn(document, 'createElement')
    const mockLink = {
      href: '',
      download: '',
      click: vi.fn()
    }
    createElementSpy.mockReturnValue(mockLink as any)

    const data = [
      { id: 1, name: 'Test 1' },
      { id: 2, name: 'Test 2' }
    ]
    const columns = ['id', 'name']

    await exportToCSV(data, columns)

    expect(mockLink.click).toHaveBeenCalled()
    expect(mockLink.download).toBe('export.csv')
  })
})
```

3. 在 index.ts 中导出：
```typescript
// src/components/composables/index.ts
export { useTableSummary } from './useTableSummary'
export { useTableSelection } from './useTableSelection'
export { useTableExport } from './useTableExport'  // 新增
```

4. 在 DataTable.vue 中使用：
```vue
<script setup lang="ts">
import { useTableExport } from './composables'

const { exporting, exportToCSV } = useTableExport()

function handleExport() {
  exportToCSV(props.data, props.columns.map(c => c.name))
}

defineExpose({
  exportToCSV: handleExport
})
</script>
```

## 场景 2：添加新的筛选器组件

**需求**：添加一个时间范围筛选器 TimeRangeFilter。

**步骤**：

1. 创建筛选器组件：
```vue
<!-- src/components/filters/TimeRangeFilter.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElTimePicker } from 'element-plus'

const props = defineProps<{
  modelValue?: { start?: string; end?: string }
}>()

const emit = defineEmits<{
  'update:modelValue': [value: { start?: string; end?: string } | undefined]
}>()

const startTime = ref(props.modelValue?.start)
const endTime = ref(props.modelValue?.end)

const hasValue = computed(() => startTime.value || endTime.value)

function handleStartChange(value: string) {
  startTime.value = value
  emitValue()
}

function handleEndChange(value: string) {
  endTime.value = value
  emitValue()
}

function emitValue() {
  if (!hasValue.value) {
    emit('update:modelValue', undefined)
  } else {
    emit('update:modelValue', {
      start: startTime.value,
      end: endTime.value
    })
  }
}

function clear() {
  startTime.value = undefined
  endTime.value = undefined
  emit('update:modelValue', undefined)
}
</script>

<template>
  <div class="time-range-filter">
    <el-time-picker
      :model-value="startTime"
      placeholder="开始时间"
      @update:model-value="handleStartChange"
    />
    <span class="separator">~</span>
    <el-time-picker
      :model-value="endTime"
      placeholder="结束时间"
      @update:model-value="handleEndChange"
    />
    <button v-if="hasValue" @click="clear" class="clear-btn">清除</button>
  </div>
</template>

<style scoped>
.time-range-filter {
  display: flex;
  align-items: center;
  gap: 8px;
}
.separator {
  color: #999;
}
.clear-btn {
  padding: 4px 8px;
  font-size: 12px;
}
</style>
```

2. 创建测试文件：
```typescript
// src/components/filters/TimeRangeFilter.test.ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TimeRangeFilter from './TimeRangeFilter.vue'

describe('TimeRangeFilter', () => {
  it('should render time pickers', () => {
    const wrapper = mount(TimeRangeFilter, {
      global: {
        stubs: {
          'el-time-picker': true
        }
      }
    })
    expect(wrapper.find('.time-range-filter').exists()).toBe(true)
  })

  it('should emit update when start time changes', async () => {
    const wrapper = mount(TimeRangeFilter, {
      global: {
        stubs: {
          'el-time-picker': true
        }
      }
    })

    await wrapper.vm.handleStartChange('10:00:00')

    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([
      { start: '10:00:00', end: undefined }
    ])
  })

  it('should clear values when clear button clicked', async () => {
    const wrapper = mount(TimeRangeFilter, {
      props: {
        modelValue: { start: '10:00:00', end: '18:00:00' }
      },
      global: {
        stubs: {
          'el-time-picker': true
        }
      }
    })

    await wrapper.find('.clear-btn').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([undefined])
  })
})
```

3. 在 index.ts 中导出：
```typescript
// src/components/filters/index.ts
export { default as TextFilter } from './TextFilter.vue'
export { default as SelectFilter } from './SelectFilter.vue'
export { default as TimeRangeFilter } from './TimeRangeFilter.vue'  // 新增
```

## 场景 3：为 DataTable 添加新的 Props

**需求**：添加 `rowHeight` prop，允许用户自定义行高。

**步骤**：

1. 修改 DataTable.vue：
```vue
<script setup lang="ts">
// 添加新的 prop
const props = withDefaults(
  defineProps<{
    columns: EnhancedColumnSchema[]
    data: any[]
    total: number
    loading?: boolean
    pageSize?: number
    rowHeight?: number  // 新增
  }>(),
  {
    loading: false,
    pageSize: 50,
    rowHeight: 40  // 新增默认值
  }
)

// 在 vxe-grid 配置中使用
const gridOptions = computed(() => ({
  // ... 其他配置
  rowConfig: {
    height: props.rowHeight  // 新增
  }
}))
</script>
```

2. 更新类型定义（如果需要导出）：
```typescript
// src/types/index.ts
export interface DataTableProps {
  columns: EnhancedColumnSchema[]
  data: any[]
  total: number
  loading?: boolean
  pageSize?: number
  rowHeight?: number  // 新增
}
```

3. 添加测试用例：
```typescript
// src/components/DataTable.test.ts
describe('Props Validation', () => {
  it('should accept rowHeight prop', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        total: 3,
        loading: false,
        rowHeight: 50  // 新增
      },
      ...globalConfig
    })

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.props('rowHeight')).toBe(50)
  })

  it('should use default rowHeight when not provided', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        total: 3,
        loading: false
      },
      ...globalConfig
    })

    expect(wrapper.props('rowHeight')).toBe(40)  // 默认值
  })
})
```

4. 更新 README.md：
```markdown
### DataTable Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| rowHeight | `number` | `40` | 表格行高（像素） |
```

## 场景 4：修复 Bug

**需求**：修复 useTableSummary 中 formatValue 对 null 值处理不当的问题。

**步骤**：

1. 重现问题（编写失败的测试）：
```typescript
// src/components/composables/useTableSummary.test.ts
it('should handle null and undefined gracefully', () => {
  const { formatValue } = useTableSummary(mockColumns)

  expect(formatValue(null, 'MONEY')).toBe('')  // 当前失败
  expect(formatValue(undefined, 'MONEY')).toBe('')  // 当前失败
  expect(formatValue(0, 'MONEY')).toBe('0.00')  // 应该通过
})
```

2. 运行测试确认失败：
```bash
npm test -- useTableSummary.test.ts
```

3. 修复代码：
```typescript
// src/components/composables/useTableSummary.ts
function formatValue(value: any, type: string): string {
  // 修复：添加 null/undefined 检查
  if (value === null || value === undefined) {
    return ''
  }

  if (typeof value !== 'number') {
    return String(value)
  }

  // ... 其余格式化逻辑
}
```

4. 运行测试验证修复：
```bash
npm test -- useTableSummary.test.ts
```

5. 运行全量测试确保没有破坏其他功能：
```bash
npm test -- --run --coverage
```

## 场景 5：在验证项目中测试新功能

**需求**：在验证项目中测试新添加的 `rowHeight` prop。

**步骤**：

1. 修改 verification-app/src/App.vue：
```vue
<script setup lang="ts">
import { ref } from 'vue'
import { DataTable, buildTableColumns } from 'foggy-data-viewer'

const rowHeight = ref(40)

function toggleRowHeight() {
  rowHeight.value = rowHeight.value === 40 ? 60 : 40
}
</script>

<template>
  <div class="app">
    <div class="controls">
      <button @click="toggleRowHeight">
        切换行高 (当前: {{ rowHeight }}px)
      </button>
    </div>

    <DataTable
      :columns="columns"
      :data="data"
      :total="total"
      :loading="loading"
      :row-height="rowHeight"
    />
  </div>
</template>
```

2. 重新构建组件库：
```bash
cd addons/foggy-data-viewer/frontend
npm run build:lib
```

3. 重新安装验证项目依赖（刷新 file: 引用）：
```bash
cd ../verification-app
npm install --force
```

4. 启动验证服务器：
```bash
npm run dev
```

5. 在浏览器中测试：
   - 打开 http://localhost:5174/
   - 点击"切换行高"按钮
   - 观察表格行高变化

## 场景 6：准备发布到 npm

**需求**：发布 v1.1.0 版本到 npm。

**步骤**：

1. 运行全量测试：
```bash
cd addons/foggy-data-viewer/frontend
npm test -- --run --coverage
```

2. 检查测试覆盖率是否达标（≥90%）

3. 更新版本号：
```bash
npm version minor  # 1.0.0 → 1.1.0
```

4. 更新 CHANGELOG.md（如有）：
```markdown
## [1.1.0] - 2024-01-23

### Added
- 新增 `rowHeight` prop 支持自定义行高
- 新增 `useTableExport` composable 支持导出功能
- 新增 `TimeRangeFilter` 筛选器组件

### Fixed
- 修复 useTableSummary 对 null 值处理不当的问题

### Changed
- 提升 DataTable 测试覆盖率至 70%
```

5. 构建库：
```bash
npm run build:lib
```

6. 检查构建产物：
```bash
ls -lh dist/
# 应该看到：index.js, index.d.ts, style.css
```

7. 发布到 npm：
```bash
npm publish
```

8. 验证发布：
```bash
npm view foggy-data-viewer
```

## 场景 7：添加自定义格式化器

**需求**：为金额列添加货币符号格式化。

**步骤**：

在使用组件时配置：
```typescript
const columns = buildTableColumns(qmSchema, {
  visibleColumns: ['id', 'name', 'amount'],
  customizations: [
    {
      name: 'amount',
      formatter: (value) => {
        if (value === null || value === undefined) return ''
        return `¥${Number(value).toLocaleString('zh-CN', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2
        })}`
      }
    }
  ]
})
```

测试自定义格式化器：
```typescript
it('should apply custom formatter', () => {
  const formatter = vi.fn((value) => `¥${value}`)
  const columns = buildTableColumns(mockQMSchema, {
    visibleColumns: ['amount'],
    customizations: [
      { name: 'amount', formatter }
    ]
  })

  expect(columns[0].customFormatter).toBe(formatter)
  expect(formatter(100)).toBe('¥100')
})
```

## 场景 8：集成新的后端 API

**需求**：添加对新的 `/api/export` 端点的支持。

**步骤**：

1. 在 src/api/ 创建新的 API 模块：
```typescript
// src/api/export.ts
import axios from 'axios'

export interface ExportRequest {
  queryId: string
  format: 'csv' | 'excel'
  columns: string[]
}

export async function exportData(request: ExportRequest): Promise<Blob> {
  const response = await axios.post('/api/export', request, {
    responseType: 'blob'
  })
  return response.data
}
```

2. 在 DataViewer 组件中使用：
```vue
<script setup lang="ts">
import { exportData } from '@/api/export'

async function handleExport(format: 'csv' | 'excel') {
  try {
    const blob = await exportData({
      queryId: props.queryId,
      format,
      columns: columns.value.map(c => c.name)
    })

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `export.${format}`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    console.error('Export failed:', error)
  }
}
</script>
```

3. 添加单元测试：
```typescript
// src/api/export.test.ts
import { describe, it, expect, vi } from 'vitest'
import axios from 'axios'
import { exportData } from './export'

vi.mock('axios')

describe('exportData', () => {
  it('should call export API with correct params', async () => {
    const mockBlob = new Blob(['test'])
    vi.mocked(axios.post).mockResolvedValue({ data: mockBlob })

    const result = await exportData({
      queryId: 'test-query',
      format: 'csv',
      columns: ['id', 'name']
    })

    expect(axios.post).toHaveBeenCalledWith(
      '/api/export',
      { queryId: 'test-query', format: 'csv', columns: ['id', 'name'] },
      { responseType: 'blob' }
    )
    expect(result).toBe(mockBlob)
  })
})
```
