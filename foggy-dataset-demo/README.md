# Foggy Dataset Demo

[中文文档](README.zh-CN.md)

E-commerce dataset demo module providing unified test data and model definitions for the Foggy Dataset framework.

## Module Description

This module contains:

- **TM Model Definitions** - Dimension and fact table models for the e-commerce domain
- **QM Query Models** - Query model definitions supporting multi-dimensional queries and multi-table JOINs
- **Docker Environment** - Multi-database test environment (MySQL, PostgreSQL, SQL Server)
- **Sample Data** - Complete e-commerce test dataset

## Data Models

### Dimension Tables

| Model | Description | Table Name |
|------|------|------|
| DimDateModel | Date dimension | dim_date |
| DimProductModel | Product dimension | dim_product |
| DimCustomerModel | Customer dimension | dim_customer |
| DimStoreModel | Store dimension | dim_store |
| DimChannelModel | Channel dimension | dim_channel |
| DimPromotionModel | Promotion dimension | dim_promotion |

### Fact Tables

| Model | Description | Table Name |
|------|------|------|
| FactSalesModel | Sales detail fact table | fact_sales |
| FactOrderModel | Order fact table | fact_order |
| FactPaymentModel | Payment fact table | fact_payment |
| FactReturnModel | Return fact table | fact_return |
| FactInventorySnapshotModel | Inventory snapshot fact table | fact_inventory_snapshot |
| FactTeamSalesModel | Team sales (parent-child dimension example) | fact_team_sales |
| FactSalesNestedDimModel | Nested dimension example (snowflake schema) | fact_sales_nested |

### Query Models

| Model | Description | Related Fact Table |
|------|------|------------|
| FactSalesQueryModel | Sales query model | FactSalesModel |
| FactOrderQueryModel | Order query model | FactOrderModel |
| FactPaymentQueryModel | Payment query model | FactPaymentModel |
| FactReturnQueryModel | Return query model | FactReturnModel |
| FactInventorySnapshotQueryModel | Inventory snapshot query model | FactInventorySnapshotModel |
| FactTeamSalesQueryModel | Team sales query model | FactTeamSalesModel |
| FactSalesNestedDimQueryModel | Nested dimension query model | FactSalesNestedDimModel |
| OrderPaymentJoinQueryModel | Order-Payment join query | FactOrderModel + FactPaymentModel |
| SalesReturnJoinQueryModel | Sales-Return join query | FactSalesModel + FactReturnModel |

## Quick Start

### 1. Start Database

```bash
cd docker

# Start MySQL
docker-compose up -d mysql

# Or start all databases
docker-compose up -d
```

### 2. Data Initialization

MySQL and PostgreSQL will **auto-initialize** data on first startup.

To manually reinitialize:

```bash
cd docker

# Initialize MySQL
./init-db.sh mysql

# Initialize PostgreSQL
./init-db.sh postgres

# Initialize SQL Server (must run manually on first time)
./init-db.sh sqlserver

# Initialize all databases
./init-db.sh all
```

### 3. Connection Information

| Database | Port | Username | Password | Database Name |
|--------|------|--------|------|----------|
| MySQL | 13306 | foggy | foggy_test_123 | foggy_test |
| PostgreSQL | 15432 | foggy | foggy_test_123 | foggy_test |
| SQL Server | 11433 | sa | Foggy_Test_123! | foggy_test |

### 4. Verify Data

```bash
# Connect to MySQL
docker exec -it foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test

# Check record counts
SELECT 'fact_sales' AS tbl, COUNT(*) AS cnt FROM fact_sales
UNION ALL SELECT 'fact_order', COUNT(*) FROM fact_order
UNION ALL SELECT 'dim_product', COUNT(*) FROM dim_product
UNION ALL SELECT 'dim_customer', COUNT(*) FROM dim_customer;
```

## Test Data Scale

| Table | Records | Description |
|------|--------|------|
| dim_date | ~1,100 | 2022-2024 three years |
| dim_product | 500 | 5 major categories |
| dim_customer | 1,000 | VIP/regular/new customers |
| dim_store | 50 | Direct/franchise stores |
| dim_channel | 10 | Online/offline channels |
| dim_promotion | 30 | Various promotions |
| fact_order | 20,000 | Order headers |
| fact_sales | ~100,000 | Order details |
| fact_payment | ~22,000 | Payment records |
| fact_return | ~5,000 | Return records |
| fact_inventory_snapshot | ~25,000 | Inventory snapshots |

## Using in Other Modules

### Maven Dependency

```xml
<!-- Use Demo data models in tests -->
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-demo</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

### Load Models

Model files are located in classpath:
- TM models: `foggy/templates/ecommerce/model/`
- QM query models: `foggy/templates/ecommerce/query/`

```java
// Example: Load sales fact table model
String modelPath = "foggy/templates/ecommerce/model/FactSalesModel.tm";
InputStream is = getClass().getClassLoader().getResourceAsStream(modelPath);

// Example: Load sales query model
String queryPath = "foggy/templates/ecommerce/query/FactSalesQueryModel.qm";
InputStream qis = getClass().getClassLoader().getResourceAsStream(queryPath);
```

## Directory Structure

```
foggy-dataset-demo/
├── pom.xml
├── README.md
├── src/main/resources/
│   └── foggy/templates/ecommerce/
│       ├── model/                    # TM model definitions
│       │   ├── DimChannelModel.tm
│       │   ├── DimCustomerModel.tm
│       │   ├── DimDateModel.tm
│       │   ├── DimProductModel.tm
│       │   ├── DimPromotionModel.tm
│       │   ├── DimStoreModel.tm
│       │   ├── FactInventorySnapshotModel.tm
│       │   ├── FactOrderModel.tm
│       │   ├── FactPaymentModel.tm
│       │   ├── FactReturnModel.tm
│       │   ├── FactSalesModel.tm
│       │   ├── FactSalesNestedDimModel.tm
│       │   └── FactTeamSalesModel.tm
│       └── query/                    # QM query models
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
    ├── init-db.sh               # Linux/macOS init script
    ├── init-db.cmd              # Windows init script
    ├── README.md
    ├── mysql/
    │   ├── conf/my.cnf
    │   └── init/*.sql
    ├── postgres/
    │   └── init/*.sql
    └── sqlserver/
        └── init/*.sql
```

## FAQ

### Q: How to clear data and reinitialize?

```bash
cd docker

# Stop and remove data volumes
docker-compose down -v

# Restart (auto-initializes)
docker-compose up -d mysql
```

### Q: SQL Server initialization failed?

SQL Server starts slowly, wait 60+ seconds on first startup:

```bash
# Wait for SQL Server to be ready
docker-compose up -d sqlserver
sleep 60

# Then initialize
./init-db.sh sqlserver
```

### Q: How to start only needed services?

```bash
# Start MySQL only
docker-compose up -d mysql

# Start MySQL + Adminer (admin UI)
docker-compose up -d mysql adminer
```

### Q: How to access database management UI?

After starting Adminer, visit http://localhost:18080

- System: MySQL
- Server: mysql
- Username: foggy
- Password: foggy_test_123
- Database: foggy_test
