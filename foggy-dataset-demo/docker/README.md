# Foggy Dataset Demo - Docker Environment

电商数据集演示环境，包含多种数据库的测试数据。

## 快速开始

```bash
# 启动所有服务
docker-compose up -d

# 仅启动 MySQL
docker-compose up -d mysql

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f mysql
```

## 数据库连接信息

| 数据库 | 端口 | 用户名 | 密码 | 数据库名 |
|--------|------|--------|------|----------|
| MySQL | 13306 | foggy | foggy_test_123 | foggy_test |
| PostgreSQL | 15432 | foggy | foggy_test_123 | foggy_test |
| SQL Server | 11433 | sa | Foggy_Test_123! | foggy_test |
| Redis | 16379 | - | - | - |
| MongoDB | 17017 | - | - | foggy_test |

## 数据初始化

### MySQL / PostgreSQL

首次启动时会自动执行 `init/` 目录下的 SQL 脚本：
- `01-schema.sql` - 表结构
- `02-dict-data.sql` - 字典数据
- `03-test-data.sql` - 测试数据

### SQL Server

SQL Server 容器不支持自动初始化，需要手动执行：

```bash
# Linux/macOS
./init-db.sh sqlserver

# Windows
init-db.cmd sqlserver
```

### 重新初始化

如果需要重新初始化数据（会清空现有数据）：

```bash
# Linux/macOS
./init-db.sh mysql        # 初始化 MySQL
./init-db.sh all          # 初始化所有数据库

# Windows
init-db.cmd mysql         # 初始化 MySQL
init-db.cmd all           # 初始化所有数据库
```

## 测试数据说明

| 表名 | 记录数 | 说明 |
|------|--------|------|
| dim_date | ~1100 | 2022-2024 日期维度 |
| dim_product | 500 | 商品维度 |
| dim_customer | 1000 | 客户维度 |
| dim_store | 50 | 门店维度 |
| dim_channel | 10 | 渠道维度 |
| dim_promotion | 30 | 促销活动维度 |
| fact_order | 20000 | 订单事实表 |
| fact_sales | ~100000 | 销售明细事实表 |
| fact_payment | ~20000 | 支付事实表 |
| fact_return | ~5000 | 退货事实表 |
| fact_inventory_snapshot | ~25000 | 库存快照 |

## 管理工具

启动 Adminer（数据库管理 Web 界面）：

```bash
docker-compose up -d adminer
```

访问 http://localhost:18080

- 系统：MySQL
- 服务器：mysql（容器名）
- 用户名：foggy
- 密码：foggy_test_123
- 数据库：foggy_test

## 常用命令

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷（清空所有数据）
docker-compose down -v

# 重建服务
docker-compose up -d --build --force-recreate

# 进入 MySQL 命令行
docker exec -it foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test

# 进入 PostgreSQL 命令行
docker exec -it foggy-demo-postgres psql -U foggy -d foggy_test

# 进入 MongoDB 命令行
docker exec -it foggy-demo-mongo mongosh foggy_test

# 进入 Redis 命令行
docker exec -it foggy-demo-redis redis-cli
```

## 目录结构

```
docker/
├── docker-compose.yml    # Docker Compose 配置
├── init-db.sh           # 数据库初始化脚本 (Linux/macOS)
├── init-db.cmd          # 数据库初始化脚本 (Windows)
├── README.md            # 本文档
├── mysql/
│   ├── conf/
│   │   └── my.cnf       # MySQL 配置
│   └── init/
│       ├── 01-schema.sql
│       ├── 02-dict-data.sql
│       └── 03-test-data.sql
├── postgres/
│   └── init/
│       ├── 01-schema.sql
│       ├── 02-dict-data.sql
│       └── 03-test-data.sql
└── sqlserver/
    └── init/
        ├── 01-schema.sql
        ├── 02-dict-data.sql
        └── 03-test-data.sql
```
