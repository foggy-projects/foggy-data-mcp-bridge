---
name: qm-generate
description: 根据 TM 模型生成 QM（查询模型）文件。当用户要求生成 QM 文件、创建查询模型、或使用 /qm 时使用。
---

# QM Generator

根据 TM 模型生成 QM（查询模型）文件，定义可查询的字段和 UI 配置。

## QM语法规范
如果需要获取更多的qm语法规范，请参考[Foggy QM 语法规范](https://foggy-projects.github.io/foggy-data-mcp-bridge/downloads/qm-syntax.md)

## 使用场景

当用户需要以下操作时使用：
- 根据 TM 模型创建 QM 查询模型文件
- 为事实表生成查询视图
- 创建多模型关联的查询模型
- 添加计算字段到查询模型

## 执行流程

1. 读取 TM 模型文件（使用 Glob 查找 .tm 文件，或使用用户指定的模型路径）
2. 分析 TM 模型结构，提取：
   - 模型名称
   - 事实表属性字段
   - 维度字段
   - 度量字段
3. 生成 QM 文件内容：
   - 使用 `loadTableModel` 加载模型
   - 定义 `queryModel` 对象
   - 创建 `columnGroups`，按逻辑分组字段
   - 添加默认排序（通常按时间字段降序）
4. 将 QM 文件写入 `{TM模型名}QueryModel.qm`
5. 输出文件路径和生成的字段列表

## 输入要求

用户需提供：
- TM 模型名称或文件路径（必需）
- QM 文件存放路径（可选，默认见下方路径规范）
- 列分组配置（可选，默认按属性、维度、度量分组）

## 文件存放路径

**默认路径**（用户未指定时）：
```
src/main/resources/foggy/templates/query/{TM模型名}QueryModel.qm
```

**目录结构说明**：
```
src/main/resources/foggy/templates/
├── model/                    # TM 表模型目录
│   ├── Fact{Name}Model.tm
│   ├── Dim{Name}Model.tm
│   └── mongo/
├── query/                   # QM 查询模型目录
│   ├── {Name}QueryModel.qm  # 查询模型
│   └── mongo/               # MongoDB 查询模型（可选分类）
├── dimensions/              # 维度构建器（可选）
└── dicts.fsscript          # 字典定义
```

如果用户指定了其他路径，按用户指定的路径生成。

## 输出格式

```
QM 文件已生成：{文件路径}

包含字段：
- 事实表属性：{字段列表}
- 维度：{字段列表}
- 度量：{字段列表}
```

## 约束条件

- QM 文件名格式：`{TM模型名}QueryModel.qm`
- 必须使用 `loadTableModel` 加载模型
- 字段引用必须使用 `ref` 语法（V2 语法）
- 维度字段自动展开为 `$id` 和 `$caption` 两列
- 嵌套维度使用路径语法（如 `fo.product.category$caption`）
- 输出列名使用下划线分隔路径（如 `product_category$caption`）

## 决策规则

- 如果 TM 模型包含时间字段 → 默认排序使用该字段降序
- 如果 TM 模型包含多个事实表 → 询问用户是否需要多模型关联
- 如果用户指定列分组 → 按用户指定的分组生成 columnGroups
- 如果 TM 模型无维度字段 → 仅生成属性和度量分组
- 如果 TM 模型无度量字段 → 仅生成属性和维度分组
- 如果字段名包含中文 → 保留中文字段名，添加 caption 显示名称

## 默认列分组策略

1. **基础信息组**：包含所有事实表属性（非维度、非度量字段）
2. **维度组**：包含所有维度字段，使用 `ref` 引用自动展开
3. **度量组**：包含所有度量字段

## 示例输出

```javascript
const fo = loadTableModel('FactOrderModel');

export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: '订单查询',
    model: fo,

    columnGroups: [
        {
            caption: '基础信息',
            items: [
                { ref: fo.orderId },
                { ref: fo.orderStatus },
                { ref: fo.orderTime }
            ]
        },
        {
            caption: '维度',
            items: [
                { ref: fo.customer },
                { ref: fo.product },
                { ref: fo.region }
            ]
        },
        {
            caption: '度量',
            items: [
                { ref: fo.totalQuantity },
                { ref: fo.totalAmount }
            ]
        }
    ],

    orders: [
        { name: 'orderTime', order: 'desc' }
    ]
};
```
