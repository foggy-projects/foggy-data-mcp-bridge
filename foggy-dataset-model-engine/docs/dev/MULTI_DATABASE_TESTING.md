# 多数据库测试指南

本文档说明如何在不同数据库上运行 foggy-dataset-model 测试。

## 快速开始

### 1. 启动数据库容器

```bash
cd foggy-dataset-model/docker

# 启动所有数据库
docker-compose -f docker-compose.test.yml up -d

# 或单独启动某个数据库
docker-compose -f docker-compose.test.yml up -d mysql-test      # MySQL
docker-compose -f docker-compose.test.yml up -d postgres-test   # PostgreSQL
docker-compose -f docker-compose.test.yml up -d sqlserver-test  # SQL Server
```

### 2. 检查容器状态

```bash
docker-compose -f docker-compose.test.yml ps
```

确保容器状态为 `healthy`。

### 3. SQL Server 初始化 (仅首次)

SQL Server 容器不支持自动执行初始化脚本，需要手动执行：

```bash
# 执行 schema 脚本
docker exec -it foggy-sqlserver-test /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Foggy_Test_123!" -C \
  -i /scripts/01-schema.sql

# 执行字典数据脚本
docker exec -it foggy-sqlserver-test /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test \
  -i /scripts/02-dict-data.sql

# 执行测试数据脚本
docker exec -it foggy-sqlserver-test /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test \
  -i /scripts/03-test-data.sql
```

## 运行测试

### MySQL 测试 (默认)

```bash
# 使用默认 docker profile
mvn test -pl foggy-dataset-model

# 或明确指定
mvn test -pl foggy-dataset-model -Dspring.profiles.active=docker
```

### PostgreSQL 测试

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=postgres
```

### SQL Server 测试

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlserver
```

### SQLite 测试 (嵌入式，无需 Docker)

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite
```

### 运行特定测试类

```bash
# 方言单元测试 (不需要数据库)
mvn test -pl foggy-dataset-model -Dtest=DialectTest

# 多数据库查询测试
mvn test -pl foggy-dataset-model -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=postgres
```

## 数据库连接信息

| 数据库 | 端口 | 用户名 | 密码 | 数据库名 |
|--------|------|--------|------|----------|
| MySQL | 13306 | foggy | foggy_test_123 | foggy_test |
| PostgreSQL | 15432 | foggy | foggy_test_123 | foggy_test |
| SQL Server | 11433 | sa | Foggy_Test_123! | foggy_test |
| SQLite | - | - | - | target/test-data/foggy_test.db |

## 数据库管理工具

Adminer 可通过 `http://localhost:18080` 访问，支持管理所有数据库。

## 测试数据说明

所有数据库使用相同的电商测试数据模型：

### 维度表
- `dim_date` - 日期维度 (2022-2024)
- `dim_product` - 商品维度 (500条)
- `dim_customer` - 客户维度 (1000条)
- `dim_store` - 门店维度 (50条)
- `dim_channel` - 渠道维度 (10条)
- `dim_promotion` - 促销维度 (30条)

### 事实表
- `fact_order` - 订单事实表
- `fact_sales` - 销售明细表
- `fact_payment` - 支付事实表
- `fact_return` - 退货事实表
- `fact_inventory_snapshot` - 库存快照表

### 字典表
- `dict_region` - 地区字典
- `dict_category` - 品类字典
- `dict_status` - 状态字典

## 常见问题

### 1. 容器启动失败

检查端口是否被占用：

```bash
netstat -ano | findstr "13306"   # Windows
netstat -tlnp | grep 13306       # Linux
```

### 2. SQL Server 健康检查失败

等待更长时间，SQL Server 启动较慢（约60秒）：

```bash
docker logs -f foggy-sqlserver-test
```

### 3. 测试数据未初始化

MySQL 和 PostgreSQL 会自动执行 init 目录下的脚本。
SQL Server 需要手动执行（见上方说明）。

### 4. 停止并清理容器

```bash
# 停止容器
docker-compose -f docker-compose.test.yml down

# 停止并删除数据卷
docker-compose -f docker-compose.test.yml down -v
```

## 方言测试说明

`DialectTest` 类测试各数据库方言的 SQL 生成功能，不依赖实际数据库连接：

- **MySQL**: 反引号引用 (`` ` ``), `LIMIT offset,count` 分页
- **PostgreSQL**: 双引号引用 (`"`), `LIMIT count OFFSET offset` 分页, 原生 NULLS FIRST/LAST
- **SQL Server**: 方括号引用 (`[]`), `OFFSET...FETCH` 分页 (需要 ORDER BY)
- **SQLite**: 双引号引用 (`"`), `LIMIT count OFFSET offset` 分页, 原生 NULLS FIRST/LAST (3.30+)
