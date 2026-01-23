# Schema Helper 使用说明

## 核心概念

### formatter vs render

- **formatter**: 用于数据格式化，影响**导出**和**显示**
  - 返回值：`string`
  - 使用场景：格式化金额、日期、将 JSON 转文字等
  - 示例：`formatter: (v) => '¥' + v`
  - **不会修改原始数据**

- **render**: 用于自定义渲染，仅影响**前端显示**
  - 返回值：`VNode | string`
  - 使用场景：添加图标、颜色、特殊样式等
  - 示例：`render: ({ value }) => h('span', { style: { color: 'red' } }, value)`
  - **不会修改原始数据**

> **重要**：
> - formatter 和 render 都只影响显示/导出，**不会修改 data 中的原始数据**
> - 有 formatter 一般不需要 render，但不绝对
> - 如果同时存在，render 用于显示，formatter 用于导出

## 基本用法

```typescript
import { buildTableColumns } from '@/utils/schemaHelper'
import type { TableConfig } from '@/types'

// 1. 定义表格配置
const config: TableConfig = {
  // 必须指定显示的列及顺序
  visibleColumns: ['id', 'name', 'amount', 'status'],

  // 或者显示所有列
  // showAll: true,

  // 列定制
  customizations: [
    {
      name: 'id',
      width: 150,
      fixed: 'left'
    },
    {
      name: 'amount',
      formatter: (v) => `¥${Number(v).toFixed(2)}`
    },
    {
      name: 'status',
      render: ({ value }) => h('span', {
        style: { color: value === 'active' ? 'green' : 'red' }
      }, value)
    }
  ]
}

// 2. 合并 QM schema 和定制配置
const columns = buildTableColumns(qmSchema, config)

// 3. 传给 DataTable 组件
<DataTable :columns="columns" :data="data" />
```

## 常见场景

### 场景1: 布尔值渲染成符号

```typescript
{
  name: 'isPaid',
  render: ({ value }) => h('span', {
    style: { fontSize: '18px', color: value ? '#67c23a' : '#f56c6c' }
  }, value ? '✓' : '✗')
}
```

### 场景2: JSON 导出为文字

```typescript
{
  name: 'metadata',
  formatter: (value) => {
    if (typeof value === 'object') {
      return JSON.stringify(value)
    }
    return String(value)
  }
}
```

### 场景3: 状态带颜色显示

```typescript
{
  name: 'status',
  render: ({ value }) => {
    const colors = { success: '#67c23a', warning: '#e6a23c', error: '#f56c6c' }
    return h('span', { style: { color: colors[value] } }, value)
  }
}
```

### 场景4: 同时使用 formatter 和 render

```typescript
{
  name: 'price',
  // 导出时格式化为文字
  formatter: (v) => `¥${Number(v).toFixed(2)}`,
  // 显示时添加样式
  render: ({ value }) => h('span', {
    style: { fontWeight: 'bold', color: '#e6a23c' }
  }, `¥${Number(value).toFixed(2)}`)
}
```

## 配置选项

### TableConfig

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| visibleColumns | string[] | 是* | 显示的列及顺序 |
| showAll | boolean | 是* | 显示所有列 |
| customizations | ColumnCustomization[] | 否 | 列定制配置 |

*注：`visibleColumns` 和 `showAll` 必须二选一

### ColumnCustomization

| 属性 | 类型 | 说明 |
|------|------|------|
| name | string | 列名（必填） |
| width | number | 列宽 |
| minWidth | number | 最小列宽 |
| fixed | 'left' \| 'right' | 固定列 |
| formatter | (value) => string | 格式化函数（用于导出） |
| render | ({ row, value }) => VNode \| string | 渲染函数（用于显示） |
| filterComponent | Component | 自定义过滤器组件 |
