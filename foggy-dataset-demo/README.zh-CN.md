# Foggy Dataset Demo

电商数据集演示模块，为 Foggy Dataset 框架提供统一的测试数据和模型定义。

## 模块说明

本模块包含：

- **JM 模型定义** - 电商领域的维度表和事实表模型
- **QM 查询模型** - 查询模型定义，支持多维度查询和多表JOIN
- **Docker 环境** - 多数据库测试环境（MySQL、PostgreSQL、SQL Server）
- **示例数据** - 完整的电商测试数据集

## 数据模型

### 维度表 (Dimension)

| 模型 | 说明 | 表名 |
|------|------|------|
| DimDateModel | 日期维度 | dim_date |
| DimProductModel | 商品维度 | dim_product |
| DimCustomerModel | 客户维度 | dim_customer |
| DimStoreModel | 门店维度 | dim_store |
| DimChannelModel | 渠道维度 | dim_channel |
| DimPromotionModel | 促销活动维度 | dim_promotion |

### 事实表 (Fact)

| 模型 | 说明 | 表名 |
|------|------|------|
| FactSalesModel | 销售明细事实表 | fact_sales |
| FactOrderModel | 订单事实表 | fact_order |
| FactPaymentModel | 支付事实表 | fact_payment |
| FactReturnModel | 退货事实表 | fact_return |
| FactInventorySnapshotModel | 库存快照事实表 | fact_inventory_snapshot |
| FactTeamSalesModel | 团队销售（父子维度示例） | fact_team_sales |
| FactSalesNestedDimModel | 嵌套维度示例（雪花模型） | fact_sales_nested |

### 查询模型 (Query Model)

| 模型 | 说明 | 关联事实表 |
|------|------|------------|
| FactSalesQueryModel | 销售查询模型 | FactSalesModel |
| FactOrderQueryModel | 订单查询模型 | FactOrderModel |
| FactPaymentQueryModel | 支付查询模型 | FactPaymentModel |
| FactReturnQueryModel | 退货查询模型 | FactReturnModel |
| FactInventorySnapshotQueryModel | 库存快照查询模型 | FactInventorySnapshotModel |
| FactTeamSalesQueryModel | 团队销售查询模型 | FactTeamSalesModel |
| FactSalesNestedDimQueryModel | 嵌套维度查询模型 | FactSalesNestedDimModel |
| OrderPaymentJoinQueryModel | 订单-支付联合查询 | FactOrderModel + FactPaymentModel |
| SalesReturnJoinQueryModel | 销售-退货联合查询 | FactSalesModel + FactReturnModel |

## 快速开始

### 1. 启动数据库

```bash
cd docker

# 启动 MySQL
docker-compose up -d mysql

# 或启动所有数据库
docker-compose up -d
```

### 2. 数据初始化

MySQL 和 PostgreSQL 首次启动时会**自动初始化**数据。

如需手动重新初始化：

```bash
cd docker

# 初始化 MySQL
./init-db.sh mysql

# 初始化 PostgreSQL
./init-db.sh postgres

# 初始化 SQL Server（首次必须手动执行）
./init-db.sh sqlserver

# 初始化所有数据库
./init-db.sh all
```

### 3. 连接信息

| 数据库 | 端口 | 用户名 | 密码 | 数据库名 |
|--------|------|--------|------|----------|
| MySQL | 13306 | foggy | foggy_test_123 | foggy_test |
| PostgreSQL | 15432 | foggy | foggy_test_123 | foggy_test |
| SQL Server | 11433 | sa | Foggy_Test_123! | foggy_test |

### 4. 验证数据

```bash
# 连接 MySQL
docker exec -it foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test

# 查看数据量
SELECT 'fact_sales' AS tbl, COUNT(*) AS cnt FROM fact_sales
UNION ALL SELECT 'fact_order', COUNT(*) FROM fact_order
UNION ALL SELECT 'dim_product', COUNT(*) FROM dim_product
UNION ALL SELECT 'dim_customer', COUNT(*) FROM dim_customer;
```

## 测试数据规模

| 表名 | 记录数 | 说明 |
|------|--------|------|
| dim_date | ~1,100 | 2022-2024 三年日期 |
| dim_product | 500 | 5 大品类商品 |
| dim_customer | 1,000 | VIP/普通/新客户 |
| dim_store | 50 | 直营/加盟门店 |
| dim_channel | 10 | 线上/线下渠道 |
| dim_promotion | 30 | 各类促销活动 |
| fact_order | 20,000 | 订单头 |
| fact_sales | ~100,000 | 订单明细 |
| fact_payment | ~22,000 | 支付记录 |
| fact_return | ~5,000 | 退货记录 |
| fact_inventory_snapshot | ~25,000 | 库存快照 |

## 在其他模块中使用

### Maven 依赖

```xml
<!-- 测试时使用 Demo 数据模型 -->
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-demo</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

### 加载模型

模型文件位于 classpath:
- JM 模型: `foggy/templates/ecommerce/model/`
- QM 查询模型: `foggy/templates/ecommerce/query/`

```java
// 示例：加载销售事实表模型
String modelPath = "foggy/templates/ecommerce/model/FactSalesModel.jm";
InputStream is = getClass().getClassLoader().getResourceAsStream(modelPath);

// 示例：加载销售查询模型
String queryPath = "foggy/templates/ecommerce/query/FactSalesQueryModel.qm";
InputStream qis = getClass().getClassLoader().getResourceAsStream(queryPath);
```

## 目录结构

```
foggy-dataset-demo/
├── pom.xml
├── README.md
├── src/main/resources/
│   └── foggy/templates/ecommerce/
│       ├── model/                    # JM 模型定义
│       │   ├── DimChannelModel.jm
│       │   ├── DimCustomerModel.jm
│       │   ├── DimDateModel.jm
│       │   ├── DimProductModel.jm
│       │   ├── DimPromotionModel.jm
│       │   ├── DimStoreModel.jm
│       │   ├── FactInventorySnapshotModel.jm
│       │   ├── FactOrderModel.jm
│       │   ├── FactPaymentModel.jm
│       │   ├── FactReturnModel.jm
│       │   ├── FactSalesModel.jm
│       │   ├── FactSalesNestedDimModel.jm
│       │   └── FactTeamSalesModel.jm
│       └── query/                    # QM 查询模型
│           ├── FactSalesQueryModel.qm
│           ├── FactOrderQueryModel.qm
│           ├── FactPaymentQueryModel.qm
│           ├── FactReturnQueryModel.qm
│           ├── FactInventorySnapshotQueryModel.qm
│           ├── FactTeamSalesQueryModel.qm
│           ├── FactSalesNestedDimQueryModel.qm
│           ├── OrderPaymentJoinQueryModel.qm
│           └── SalesReturnJoinQueryModel.qm
└── docker/
    ├── docker-compose.yml
    ├── init-db.sh               # Linux/macOS 初始化脚本
    ├── init-db.cmd              # Windows 初始化脚本
    ├── README.md
    ├── mysql/
    │   ├── conf/my.cnf
    │   └── init/*.sql
    ├── postgres/
    │   └── init/*.sql
    └── sqlserver/
        └── init/*.sql
```

## 常见问题

### Q: 如何清空数据重新初始化？

```bash
cd docker

# 停止并删除数据卷
docker-compose down -v

# 重新启动（会自动初始化）
docker-compose up -d mysql
```

### Q: SQL Server 初始化失败？

SQL Server 启动较慢，首次启动需要等待 60 秒以上：

```bash
# 等待 SQL Server 就绪
docker-compose up -d sqlserver
sleep 60

# 然后执行初始化
./init-db.sh sqlserver
```

### Q: 如何只启动需要的服务？

```bash
# 仅启动 MySQL
docker-compose up -d mysql

# 启动 MySQL + Adminer（管理界面）
docker-compose up -d mysql adminer
```

### Q: 如何访问数据库管理界面？

启动 Adminer 后访问 http://localhost:18080

- 系统：MySQL
- 服务器：mysql
- 用户名：foggy
- 密码：foggy_test_123
- 数据库：foggy_test
