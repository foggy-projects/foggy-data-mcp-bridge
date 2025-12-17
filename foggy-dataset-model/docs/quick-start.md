# 快速入门

本指南帮助你在 5 分钟内创建第一个 Foggy Dataset 数据模型。

## 场景

假设我们有一个简单的销售系统，包含：
- 商品表 `dim_product`
- 销售记录表 `fact_sales`

## 第一步：创建维度模型 (JM)

创建文件 `DimProductModel.jm`：

```javascript
// 商品维度模型
def DimProductModel = {
    name: "DimProductModel",
    tableName: "dim_product",
    idColumn: "product_id",
    caption: "商品维度",

    // 属性定义
    properties: [
        { name: "productId", column: "product_id", type: "Long", caption: "商品ID" },
        { name: "productName", column: "product_name", type: "String", caption: "商品名称" },
        { name: "category", column: "category", type: "String", caption: "品类" },
        { name: "price", column: "price", type: "MONEY", caption: "单价" }
    ]
}

export { DimProductModel }
```

## 第二步：创建事实表模型 (JM)

创建文件 `FactSalesModel.jm`：

```javascript
import { DimProductModel } from "./DimProductModel.jm"

// 销售事实表模型
def FactSalesModel = {
    name: "FactSalesModel",
    tableName: "fact_sales",
    idColumn: "sales_id",
    caption: "销售事实表",

    // 属性定义
    properties: [
        { name: "salesId", column: "sales_id", type: "Long", caption: "销售ID" },
        { name: "productId", column: "product_id", type: "Long", caption: "商品ID" },
        { name: "quantity", column: "quantity", type: "Integer", caption: "数量" },
        { name: "amount", column: "amount", type: "MONEY", caption: "金额" },
        { name: "salesDate", column: "sales_date", type: "Date", caption: "销售日期" }
    ],

    // 维度定义 - 关联商品
    dimensions: [
        {
            name: "product",
            caption: "商品",
            foreignKey: "product_id",
            queryObject: DimProductModel
        }
    ],

    // 度量定义 - 可聚合的数值
    measures: [
        { name: "totalQuantity", column: "quantity", type: "Integer", aggregation: "SUM", caption: "总数量" },
        { name: "totalAmount", column: "amount", type: "MONEY", aggregation: "SUM", caption: "总金额" }
    ]
}

export { FactSalesModel }
```

## 第三步：创建查询模型 (QM)

创建文件 `FactSalesQueryModel.qm`：

```javascript
import { FactSalesModel } from "../model/FactSalesModel.jm"

def FactSalesQueryModel = {
    name: "FactSalesQueryModel",
    caption: "销售查询模型",
    model: FactSalesModel,

    // 列组定义 - 组织可查询的列
    columnGroups: [
        {
            name: "basic",
            caption: "基础信息",
            columns: ["salesId", "salesDate"]
        },
        {
            name: "product",
            caption: "商品信息",
            columns: ["product.productName", "product.category", "product.price"]
        },
        {
            name: "measures",
            caption: "度量",
            columns: ["quantity", "amount"]
        }
    ]
}

export { FactSalesQueryModel }
```

## 第四步：使用查询引擎

```java
// 加载查询模型
JdbcQueryModel queryModel = jdbcQueryModelLoader.load("FactSalesQueryModel");

// 创建查询引擎
JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

// 构建查询请求
JdbcQueryRequestDef request = new JdbcQueryRequestDef();
request.setQueryModel("FactSalesQueryModel");
request.setColumns(Arrays.asList(
    "salesDate$caption",          // 维度名称列 (自动JOIN)
    "product$caption",            // 商品名称
    "product$category",           // 商品品类
    "quantity",
    "amount"
));

// 添加条件
SliceRequestDef slice = new SliceRequestDef();
slice.setName("product$category");
slice.setType("=");
slice.setValue("电子产品");
request.setSlice(Arrays.asList(slice));

// 生成 SQL
engine.analysisQueryRequest(context, request);
String sql = engine.getSql();
```

生成的 SQL：

```sql
SELECT
    t0.sales_date,
    t1.product_name,
    t1.category,
    t0.quantity,
    t0.amount
FROM fact_sales t0
LEFT JOIN dim_product t1 ON t0.product_id = t1.product_id
WHERE t1.category = ?
```

## 下一步

- [JM/QM 语法手册](guide/JM-QM-Syntax-Manual.md) - 学习完整的模型定义语法
- [API 参考](guide/API-Reference.md) - HTTP API 接口文档
- [父子维度](guide/Parent-Child-Dimension.md) - 层级结构维度
