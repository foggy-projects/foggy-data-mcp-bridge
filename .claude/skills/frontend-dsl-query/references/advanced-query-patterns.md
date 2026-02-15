# 前端高级查询模式

## 去重计数 (COUNTD)

```typescript
import { dslQuery, type CalculatedFieldDef } from '@/apis/common/dslQuery'

// 按品类统计独立客户数
const uvField: CalculatedFieldDef = {
  name: 'uv',
  caption: '独立客户数',
  expression: 'COUNTD(customer$id)',
}

const result = await dslQuery('FactSalesQueryModel', {
  pageSize: 100,
  param: {
    columns: ['product$categoryName', 'uv'],
    calculatedFields: [uvField],
    groupBy: ['product$categoryName'],
  },
})
```

## 窗口函数 - 排名

```typescript
import type { CalculatedFieldDef, WindowOrderDef } from '@/apis/common/dslQuery'

const rankField: CalculatedFieldDef = {
  name: 'salesRank',
  caption: '品类销售排名',
  expression: 'RANK()',
  partitionBy: ['product$categoryName'],
  windowOrderBy: [{ field: 'salesAmount', dir: 'desc' }],
}

const result = await dslQuery('FactSalesQueryModel', {
  pageSize: 100,
  param: {
    columns: ['product$categoryName', 'product$caption', 'salesAmount', 'salesRank'],
    calculatedFields: [rankField],
  },
})
```

## 窗口函数 - 移动平均

```typescript
const ma7Field: CalculatedFieldDef = {
  name: 'ma7',
  caption: '7日移动平均',
  expression: 'AVG(salesAmount)',
  partitionBy: ['product$caption'],
  windowOrderBy: [{ field: 'salesDate$caption', dir: 'asc' }],
  windowFrame: 'ROWS BETWEEN 6 PRECEDING AND CURRENT ROW',
}

const result = await dslQuery('FactSalesQueryModel', {
  pageSize: 100,
  param: {
    columns: ['product$caption', 'salesDate$caption', 'salesAmount', 'ma7'],
    calculatedFields: [ma7Field],
  },
})
```

## 引用 QM 预定义字段

QM 中已定义的计算字段（如 `profitRate`、`salesRank`、`ma7`）可直接在 columns 中引用，无需定义 calculatedFields：

```typescript
const result = await dslQuery('FactSalesQueryModel', {
  pageSize: 100,
  param: {
    columns: ['product$categoryName', 'salesAmount', 'profitRate', 'salesRank', 'ma7'],
  },
})
```

## CalculatedFieldDef 窗口属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `partitionBy` | `string[]` | 窗口分区字段 |
| `windowOrderBy` | `WindowOrderDef[]` | 窗口排序 `{ field, dir }` |
| `windowFrame` | `string` | 窗口帧（如 `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW`） |

## 常用窗口函数

| 表达式 | 说明 |
|--------|------|
| `ROW_NUMBER()` | 行号 |
| `RANK()` | 排名（并列跳号） |
| `DENSE_RANK()` | 排名（并列不跳号） |
| `LAG(field)` | 前一行值 |
| `LEAD(field)` | 后一行值 |
| `AVG(field)` + windowFrame | 移动平均 |
| `SUM(field)` + windowFrame | 累计求和 |
