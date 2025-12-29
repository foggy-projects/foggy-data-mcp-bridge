# QueryModel JSON 格式规范

> **设计文档** - 用于未来可视化 IDE 开发参考，当前版本不实现

## 概述

本规范定义了一种纯 JSON 格式的 QueryModel 配置（`.qm.json`），用于支持可视化 IDE 配置。与 V2 代码格式（`.qm`）不同，JSON 格式是纯数据结构，不包含任何代码逻辑。

### 格式对比

| 特性 | V2 代码格式 (.qm) | JSON 格式 (.qm.json) |
|------|------------------|---------------------|
| 可视化编辑 | 困难（含代码逻辑） | 简单（纯数据结构） |
| 灵活性 | 高（可写复杂脚本） | 受限（仅声明式配置） |
| 目标用户 | 资深开发人员 | 业务分析师/普通用户 |
| IDE 支持 | 代码编辑器 | 可视化配置界面 |
| 加载方式 | 脚本引擎执行 | JSON 解析器直接加载 |

## JSON 格式结构

```json
{
  "$schema": "https://foggy-framework.com/schemas/qm-v1.json",
  "name": "QueryModelName",
  "caption": "查询模型显示名称",
  "description": "查询模型描述",

  "model": "TableModelName",

  "joins": [
    {
      "type": "leftJoin",
      "target": "TargetTableModel",
      "alias": "别名（可选）",
      "on": [
        { "left": "sourceColumn", "right": "targetColumn" }
      ]
    }
  ],

  "columnGroups": [
    {
      "caption": "分组名称",
      "items": [
        { "ref": "column" },
        { "ref": "dimension$attribute" }
      ]
    }
  ],

  "orders": [
    { "ref": "column", "order": "asc" }
  ],

  "accesses": []
}
```

## 字段详细说明

### 基础字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `$schema` | string | 否 | JSON Schema URL，用于 IDE 验证和自动补全 |
| `name` | string | 是 | QueryModel 唯一标识符 |
| `caption` | string | 是 | 显示名称 |
| `description` | string | 否 | 描述信息 |
| `model` | string | 是 | 主表 TableModel 名称 |

### joins - 关联定义

```json
{
  "joins": [
    {
      "type": "leftJoin | innerJoin | rightJoin",
      "target": "TableModelName",
      "alias": "可选别名",
      "on": [
        { "left": "sourceColumn", "right": "targetColumn" },
        { "left": "column2", "right": "column2" }
      ]
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | 是 | JOIN 类型：`leftJoin`、`innerJoin`、`rightJoin` |
| `target` | string | 是 | 目标 TableModel 名称 |
| `alias` | string | 否 | 目标表别名（用于嵌套维度访问） |
| `on` | array | 是 | JOIN 条件数组（多条件为 AND 关系） |

**嵌套维度示例：**

```json
{
  "joins": [
    {
      "type": "leftJoin",
      "target": "DimProductModel",
      "on": [{ "left": "productId", "right": "productId" }]
    },
    {
      "type": "leftJoin",
      "target": "DimCategoryModel",
      "alias": "productCategory",
      "source": "DimProductModel",
      "on": [{ "left": "categoryId", "right": "categoryId" }]
    }
  ]
}
```

> `source` 字段指定从哪个表 JOIN 到目标表，不指定则默认为主表。

### columnGroups - 列分组

```json
{
  "columnGroups": [
    {
      "caption": "分组显示名称",
      "items": [
        { "ref": "columnName" },
        { "ref": "columnName", "ui": { "fixed": "left", "width": 150 } },
        { "ref": "dimension$attribute" },
        { "ref": "alias$attribute" }
      ]
    }
  ]
}
```

**列引用语法：**

| 语法 | 说明 | 示例 |
|------|------|------|
| `column` | 主表列 | `orderId` |
| `dim$attr` | 维度表属性 | `product$brand` |
| `alias$attr` | 通过别名访问嵌套维度 | `productCategory$categoryName` |
| `dim$caption` | 维度表标题字段 | `customer$caption` |

**UI 配置选项：**

| 属性 | 类型 | 说明 |
|------|------|------|
| `fixed` | string | 固定列：`left` 或 `right` |
| `width` | number | 列宽度（像素） |
| `visible` | boolean | 是否默认可见 |

### orders - 默认排序

```json
{
  "orders": [
    { "ref": "column", "order": "asc" },
    { "ref": "dimension$caption", "order": "desc" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ref` | string | 是 | 列引用 |
| `order` | string | 是 | 排序方向：`asc` 或 `desc` |

### accesses - 权限控制

```json
{
  "accesses": [
    {
      "name": "accessName",
      "caption": "权限显示名称",
      "filter": {
        "column": "storeId",
        "operator": "in",
        "valueSource": "session",
        "valueKey": "userStores"
      }
    }
  ]
}
```

> 详细权限配置参见 `Authorization-Control.md`

## 完整示例

### 单表查询

```json
{
  "$schema": "https://foggy-framework.com/schemas/qm-v1.json",
  "name": "DimProductQueryModel",
  "caption": "商品查询",
  "description": "商品维度表查询模型",

  "model": "DimProductModel",

  "columnGroups": [
    {
      "caption": "商品基本信息",
      "items": [
        { "ref": "productId", "ui": { "fixed": "left", "width": 100 } },
        { "ref": "productName" },
        { "ref": "brand" },
        { "ref": "unitPrice" }
      ]
    },
    {
      "caption": "分类信息",
      "items": [
        { "ref": "categoryName" },
        { "ref": "subCategoryName" }
      ]
    }
  ],

  "orders": [
    { "ref": "productId", "order": "asc" }
  ],

  "accesses": []
}
```

### 多表关联查询

```json
{
  "$schema": "https://foggy-framework.com/schemas/qm-v1.json",
  "name": "FactOrderQueryModel",
  "caption": "订单查询",
  "description": "订单事实表查询模型",

  "model": "FactOrderModel",

  "joins": [
    {
      "type": "leftJoin",
      "target": "DimDateModel",
      "on": [{ "left": "orderDateKey", "right": "dateKey" }]
    },
    {
      "type": "leftJoin",
      "target": "DimProductModel",
      "on": [{ "left": "productKey", "right": "productKey" }]
    },
    {
      "type": "leftJoin",
      "target": "DimCustomerModel",
      "on": [{ "left": "customerKey", "right": "customerKey" }]
    },
    {
      "type": "leftJoin",
      "target": "DimStoreModel",
      "on": [{ "left": "storeKey", "right": "storeKey" }]
    }
  ],

  "columnGroups": [
    {
      "caption": "订单信息",
      "items": [
        { "ref": "orderId", "ui": { "fixed": "left", "width": 150 } },
        { "ref": "orderLineNo" },
        { "ref": "orderStatus" },
        { "ref": "orderTime" }
      ]
    },
    {
      "caption": "日期维度",
      "items": [
        { "ref": "orderDate$caption" },
        { "ref": "orderDate$year" },
        { "ref": "orderDate$quarter" },
        { "ref": "orderDate$month" }
      ]
    },
    {
      "caption": "商品维度",
      "items": [
        { "ref": "product$caption" },
        { "ref": "product$categoryName" },
        { "ref": "product$brand" }
      ]
    },
    {
      "caption": "客户维度",
      "items": [
        { "ref": "customer$caption" },
        { "ref": "customer$customerType" },
        { "ref": "customer$province" }
      ]
    },
    {
      "caption": "门店维度",
      "items": [
        { "ref": "store$caption" },
        { "ref": "store$storeType" },
        { "ref": "store$city" }
      ]
    },
    {
      "caption": "度量",
      "items": [
        { "ref": "quantity" },
        { "ref": "unitPrice" },
        { "ref": "salesAmount" },
        { "ref": "discountAmount" }
      ]
    }
  ],

  "orders": [
    { "ref": "orderTime", "order": "desc" }
  ],

  "accesses": []
}
```

### 嵌套维度（雪花模型）

```json
{
  "$schema": "https://foggy-framework.com/schemas/qm-v1.json",
  "name": "FactSalesNestedDimQueryModel",
  "caption": "嵌套维度销售查询",
  "description": "演示嵌套维度（雪花模型）的查询模型",

  "model": "FactSalesNestedDimModel",

  "joins": [
    {
      "type": "leftJoin",
      "target": "DimProductModel",
      "on": [{ "left": "productKey", "right": "productKey" }]
    },
    {
      "type": "leftJoin",
      "target": "DimCategoryModel",
      "alias": "productCategory",
      "source": "DimProductModel",
      "on": [{ "left": "categoryId", "right": "categoryId" }]
    },
    {
      "type": "leftJoin",
      "target": "DimCategoryGroupModel",
      "alias": "categoryGroup",
      "source": "productCategory",
      "on": [{ "left": "groupId", "right": "groupId" }]
    }
  ],

  "columnGroups": [
    {
      "caption": "商品维度（一级）",
      "items": [
        { "ref": "product$caption" },
        { "ref": "product$brand" }
      ]
    },
    {
      "caption": "品类维度（二级）",
      "items": [
        { "ref": "productCategory$caption" },
        { "ref": "productCategory$categoryLevel" }
      ]
    },
    {
      "caption": "品类组维度（三级）",
      "items": [
        { "ref": "categoryGroup$caption" },
        { "ref": "categoryGroup$groupType" }
      ]
    }
  ],

  "orders": [
    { "ref": "salesDate$caption", "order": "desc" }
  ],

  "accesses": []
}
```

## 加载器实现建议

### 加载流程

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  .qm.json 文件  │ -> │  JSON 解析器     │ -> │  QueryModel     │
│  (纯数据结构)   │    │  (直接映射)      │    │  (内存对象)     │
└─────────────────┘    └──────────────────┘    └─────────────────┘

┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  .qm 文件       │ -> │  脚本引擎执行    │ -> │  QueryModel     │
│  (V2 代码格式)  │    │  (Fsscript)      │    │  (内存对象)     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### 实现要点

1. **文件扩展名识别**
   - `.qm.json` - 使用 JSON 加载器
   - `.qm` - 使用脚本引擎加载器

2. **列引用解析**
   - 解析 `ref` 字符串为 ColumnRef 对象
   - 支持 `column`、`dim$attr`、`alias$attr` 语法

3. **JOIN 图构建**
   - 根据 `joins` 数组构建 JoinGraph
   - 处理 `source` 字段实现嵌套 JOIN

4. **TableModel 查找**
   - 通过名称查找已加载的 TableModel
   - 验证列引用的有效性

## 可视化 IDE 设计建议

### 配置界面结构

```
┌─────────────────────────────────────────────────────────────┐
│ QueryModel 配置器                                           │
├─────────────────────────────────────────────────────────────┤
│ 基本信息                                                    │
│ ┌──────────┐ ┌────────────────────────────────────────────┐ │
│ │ 名称:    │ │ FactOrderQueryModel                        │ │
│ │ 标题:    │ │ 订单查询                                   │ │
│ │ 描述:    │ │ 订单事实表查询模型                         │ │
│ └──────────┘ └────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│ 数据模型                                                    │
│ ┌──────────────────────┐ ┌────────────────────────────────┐ │
│ │ 主表:                │ │ FactOrderModel         [选择]  │ │
│ └──────────────────────┘ └────────────────────────────────┘ │
│                                                             │
│ 关联表:                                                     │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ [+] DimDateModel      LEFT JOIN  orderDateKey=dateKey   │ │
│ │ [+] DimProductModel   LEFT JOIN  productKey=productKey  │ │
│ │ [+] DimCustomerModel  LEFT JOIN  customerKey=customerKey│ │
│ │                                          [添加关联表]   │ │
│ └─────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│ 列分组                                                      │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ [订单信息]                                              │ │
│ │   ☑ orderId (订单ID)           固定: 左  宽度: 150      │ │
│ │   ☑ orderLineNo (行号)                                  │ │
│ │   ☑ orderStatus (状态)                                  │ │
│ │                                                         │ │
│ │ [日期维度]                                              │ │
│ │   ☑ orderDate$caption (日期)                            │ │
│ │   ☑ orderDate$year (年份)                               │ │
│ │   ☐ orderDate$quarter (季度)                            │ │
│ └─────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│ 默认排序                                                    │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 1. orderTime 降序                               [删除]  │ │
│ │                                           [添加排序]    │ │
│ └─────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                              [预览 SQL]  [保存]  [取消]     │
└─────────────────────────────────────────────────────────────┘
```

### 关联表配置对话框

```
┌─────────────────────────────────────────────────────────────┐
│ 添加关联表                                                  │
├─────────────────────────────────────────────────────────────┤
│ 目标表: [DimProductModel     ▼]                             │
│ 别名:   [                     ] (可选，用于嵌套维度)        │
│ 类型:   [LEFT JOIN           ▼]                             │
│                                                             │
│ 关联条件:                                                   │
│ ┌───────────────┐    ┌───────────────┐                     │
│ │ productKey  ▼ │ =  │ productKey  ▼ │  [删除]             │
│ └───────────────┘    └───────────────┘                     │
│                                        [添加条件]           │
├─────────────────────────────────────────────────────────────┤
│                                      [确定]  [取消]         │
└─────────────────────────────────────────────────────────────┘
```

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2024-01 | 初始设计规范 |

## 参考文档

- [TM-QM-Syntax-Manual.md](../guide/TM-QM-Syntax-Manual.md) - TM/QM 语法手册
- [QM-LOADER-V2-DESIGN.md](./QM-LOADER-V2-DESIGN.md) - V2 加载器设计文档
- [Authorization-Control.md](../security/Authorization-Control.md) - 权限控制文档
