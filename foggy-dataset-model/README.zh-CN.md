# Foggy Dataset Model

**嵌入式语义层引擎** - 为 Java 应用提供声明式维度建模和动态查询能力。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

## 概述

Foggy Dataset Model 是一个可嵌入的数据建模和查询引擎，专为 OLAP 分析场景设计。通过声明式的 **JM（数据模型）** 和 **QM（查询模型）** 定义，自动处理多表 JOIN、聚合计算、维度过滤等复杂逻辑。

### 核心特性

- **声明式维度建模** - 使用 JavaScript 语法定义星型/雪花模型，自动生成 SQL
- **嵌套维度（雪花模型）** - 支持多层维度关联，简洁的嵌套语法
- **父子维度（层级数据）** - 基于闭包表自动处理组织架构等层级查询
- **多数据库支持** - MySQL、PostgreSQL、SQL Server、SQLite、MongoDB
- **模型与查询分离** - JM 定义数据结构，QM 定义可查询字段，职责清晰
- **可嵌入设计** - 作为 Spring Boot Starter 引入，无额外运维成本

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义数据模型（JM）

创建文件 `FactSalesModel.jm`：

```javascript
export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    // 维度定义
    dimensions: [
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            properties: [
                { column: 'brand', caption: '品牌' },
                { column: 'category_name', caption: '品类' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            properties: [
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' }
            ]
        }
    ],

    // 度量定义
    measures: [
        { column: 'quantity', caption: '销售数量', aggregation: 'sum' },
        { column: 'sales_amount', caption: '销售金额', type: 'MONEY', aggregation: 'sum' },
        { column: 'profit_amount', caption: '利润', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

### 3. 定义查询模型（QM）

创建文件 `FactSalesQueryModel.qm`：

```javascript
export const queryModel = {
    name: 'FactSalesQueryModel',
    model: 'FactSalesModel',

    columnGroups: [
        {
            caption: '商品维度',
            items: [
                { name: 'product$caption' },
                { name: 'product$brand' },
                { name: 'product$categoryName' }
            ]
        },
        {
            caption: '客户维度',
            items: [
                { name: 'customer$caption' },
                { name: 'customer$province' }
            ]
        },
        {
            caption: '度量',
            items: [
                { name: 'quantity' },
                { name: 'salesAmount' },
                { name: 'profitAmount' }
            ]
        }
    ],

    orders: [
        { name: 'salesAmount', order: 'desc' }
    ]
};
```

### 4. 执行查询

```java
@Autowired
private JdbcService jdbcService;

public void querySales() {
    JdbcQueryRequestDef request = new JdbcQueryRequestDef();
    request.setQueryModel("FactSalesQueryModel");
    request.setColumns(Arrays.asList(
        "product$caption",
        "customer$province",
        "salesAmount"
    ));

    // 按省份过滤
    SliceRequestDef slice = new SliceRequestDef();
    slice.setName("customer$province");
    slice.setType(CondType.EQ);
    slice.setValue("广东省");
    request.setSlice(Collections.singletonList(slice));

    // 按商品分组
    GroupRequestDef group = new GroupRequestDef();
    group.setName("product$caption");
    request.setGroupBy(Collections.singletonList(group));

    PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(request, 20);
    PagingResultImpl result = jdbcService.queryModelData(form);

    // 自动生成的 SQL:
    // SELECT p.product_name, SUM(f.sales_amount)
    // FROM fact_sales f
    // LEFT JOIN dim_product p ON f.product_key = p.product_key
    // LEFT JOIN dim_customer c ON f.customer_key = c.customer_key
    // WHERE c.province = '广东省'
    // GROUP BY p.product_name
    // ORDER BY SUM(f.sales_amount) DESC
    // LIMIT 20
}
```

## 高级特性

### 嵌套维度（雪花模型）

支持多层维度关联，外键指向父维度表而非事实表：

```javascript
dimensions: [
    {
        name: 'product',
        tableName: 'dim_product',
        foreignKey: 'product_key',
        primaryKey: 'product_key',
        captionColumn: 'product_name',

        // 嵌套子维度
        dimensions: [
            {
                name: 'category',
                alias: 'productCategory',  // QM 中使用 productCategory$xxx
                tableName: 'dim_category',
                foreignKey: 'category_key', // 在 dim_product 表上
                primaryKey: 'category_key',
                captionColumn: 'category_name',

                // 继续嵌套
                dimensions: [
                    {
                        name: 'group',
                        alias: 'categoryGroup',
                        tableName: 'dim_category_group',
                        foreignKey: 'group_key', // 在 dim_category 表上
                        primaryKey: 'group_key',
                        captionColumn: 'group_name'
                    }
                ]
            }
        ]
    }
]
```

生成的 SQL JOIN 链：
```sql
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
LEFT JOIN dim_category c ON p.category_key = c.category_key
LEFT JOIN dim_category_group g ON c.group_key = g.group_key
```

### 父子维度（层级数据）

基于闭包表处理组织架构、地区树等层级结构：

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: '团队',

    // 父子维度配置
    closureTableName: 'team_closure',
    parentKey: 'parent_id',
    childKey: 'team_id',

    properties: [
        { column: 'team_level', caption: '层级' },
        { column: 'manager_name', caption: '负责人' }
    ]
}
```

查询时自动包含所有子孙节点：
```java
// 过滤"销售一部"及其所有下级团队的数据
slice.setName("team$id");
slice.setValue("TEAM_SALES_1");
```

### 多数据库支持

自动适配不同数据库方言：

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo  # 或 postgresql, sqlserver, sqlite
```

支持的数据库：
- MySQL 5.7+
- PostgreSQL 12+
- SQL Server 2012+
- SQLite 3.30+
- MongoDB 4.0+

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    应用层 (Your App)                     │
├─────────────────────────────────────────────────────────┤
│                  JdbcService (查询入口)                  │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ JM 模型加载  │  │ QM 模型加载  │  │ SQL 生成引擎    │  │
│  │ (JdbcModel) │  │(QueryModel) │  │ (JdbcQuery)     │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
├─────────────────────────────────────────────────────────┤
│                   数据库方言层 (FDialect)                 │
├──────────┬──────────┬──────────┬──────────┬─────────────┤
│  MySQL   │ PostgreSQL│ SQLServer│  SQLite  │  MongoDB   │
└──────────┴──────────┴──────────┴──────────┴─────────────┘
```

## 文档

- [JM/QM 语法手册](docs/JM-QM-Syntax-Manual.md)
- [快速入门指南](docs/quick-start.md)
- [API 参考](docs/API-Reference.md)
- [多数据库适配](docs/MULTI_DATABASE_ADAPTER.md)
- [父子维度说明](docs/Parent-Child-Dimension.md)

## 适用场景

- **SaaS 产品报表模块** - 无需引入重量级 BI，直接在后端生成分析查询
- **企业数据中台** - 统一数据模型层，支持前端自助查询
- **低代码平台** - 配置化定义数据模型，动态生成查询
- **数据 API 服务** - 快速构建数据查询接口

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

[Apache License 2.0](../LICENSE)
