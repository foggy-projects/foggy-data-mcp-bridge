# 简介

`foggy-dataset` 是 Foggy 框架的数据库基础层模块，提供多数据库支持、SQL 构建和查询执行等核心能力。

## 核心特性

### 多数据库支持

支持主流数据库，通过方言系统自动适配差异：

| 数据库 | 方言类 | 分页语法 |
|--------|--------|----------|
| MySQL 5.7+ | `MysqlDialect` | `LIMIT offset, limit` |
| PostgreSQL 12+ | `PostgresDialect` | `LIMIT limit OFFSET offset` |
| SQL Server 2012+ | `SqlServerDialect` | `OFFSET...FETCH` |
| SQLite 3.30+ | `SqliteDialect` | `LIMIT limit OFFSET offset` |

### 动态 SQL 构建

基于 FSScript 脚本引擎，通过 SQL 辅助函数安全地构建动态 SQL：

```javascript
export const sql = `
    SELECT * FROM orders
    WHERE 1=1
        ${sqlExp(form.param.teamId, 'AND team_id = ?')}
        ${sqlInExp(form.param.statusList, 'AND status IN ')}
        ${sqlExp(toLikeStr(form.param.keyword), 'AND name LIKE ?')}
`;
```

**核心优势**：
- ✅ **防止 SQL 注入**：使用 PreparedStatement 参数化查询
- ✅ **条件动态拼接**：空值自动跳过，无需大量 if-else
- ✅ **跨数据库兼容**：一套代码适配多种数据库

### 统一查询 API

`DatasetTemplate` 封装了 JdbcTemplate 和数据库方言，提供统一的查询接口：

```java
DatasetTemplate template = DataSourceQueryUtils.getDatasetTemplate(dataSource);

// 查询 Map 列表
List<Map<String, Object>> list = template.queryMapList(sql, args);

// 查询对象列表
List<User> users = template.queryObjectList(User.class, sql, args);

// 查询总数
Long count = template.queryCount(sql, args);
```

---

## 模块架构

```
foggy-dataset-mcp (MCP 服务)
        ↓
foggy-dataset-model (JM/QM 引擎)
        ↓
foggy-dataset (数据库基础层) ← 本模块
        ↓
foggy-fsscript (脚本引擎)
```

### 与 foggy-dataset-model 的区别

| 模块 | 定位 | 功能 |
|------|------|------|
| **foggy-dataset** | 数据库基础层 | SQL 构建、查询执行、方言系统 |
| **foggy-dataset-model** | 数据模型层 | JM/QM 引擎、元数据管理、权限控制 |

- **foggy-dataset** 关注：如何连接数据库、如何执行 SQL、如何处理多数据库差异
- **foggy-dataset-model** 关注：如何定义数据模型、如何自动生成查询、如何控制权限

---

## 核心组件

### 1. 数据库方言 (FDialect)

抽象数据库差异，提供统一接口：

```java
// 自动检测数据库类型
FDialect dialect = DbUtils.getDialect(dataSource);

// 生成分页 SQL
String pageSql = dialect.generatePagingSql(sql, 0, 20);

// 标识符引用（自动加引号）
String quoted = dialect.quoteIdentifier("user_name");
// MySQL: `user_name`
// PostgreSQL: "user_name"
```

### 2. 数据库工具 (DbUtils)

提供表结构查询、DDL 生成等功能：

```java
// 获取表元数据
SqlTable table = DbUtils.getTableByName(dataSource, "users");

// 生成 CREATE TABLE SQL
String createSql = DbUtils.generateCreateSql(dialect, table);

// 获取列信息
List<SqlColumn> columns = DbUtils.getColumnsByTableName(dataSource, "users");
```

### 3. 查询模板 (DatasetTemplate)

封装 JdbcTemplate，提供便捷的查询方法：

```java
DatasetTemplate template = DataSourceQueryUtils.getDatasetTemplate(dataSource);

// 带缓存获取表结构
SqlTable table = template.getSqlTableUsingCache("users", true);

// 获取可编辑表对象
EditSqlTable editTable = template.getDsSqlTable("users", true);

// 获取查询表对象
QuerySqlTable queryTable = template.getQuerySqlTable("users", true);
```

### 4. SQL 辅助函数

在 FSScript 中安全构建动态 SQL：

| 函数 | 功能 |
|------|------|
| `sqlExp(value, sql)` | 条件参数，值为空时跳过 |
| `sqlInExp(array, sql)` | IN 查询，自动生成占位符 |
| `toLikeStr(str)` | 两端模糊 `%value%` |
| `toLikeStrL(str)` | 左侧模糊 `%value` |
| `toLikeStrR(str)` | 右侧模糊 `value%` |
| `iif(cond, true, false)` | 条件表达式 |

### 5. 查询求值器 (QueryExpEvaluator)

配合 FSScript 执行，收集 SQL 参数：

```java
QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);
evaluator.setVar("form", formData);

// 执行脚本
String sql = (String) exp.evalResult(evaluator);

// 获取参数列表
List<Object> args = evaluator.getArgs();
```

---

## 适用场景

### 1. 动态查询

用户可自由组合查询条件，后端动态拼接 SQL：

```javascript
export const sql = `
    SELECT * FROM products
    WHERE 1=1
        ${sqlExp(form.param.categoryId, 'AND category_id = ?')}
        ${sqlExp(form.param.minPrice, 'AND price >= ?')}
        ${sqlExp(form.param.maxPrice, 'AND price <= ?')}
        ${sqlExp(toLikeStr(form.param.name), 'AND name LIKE ?')}
`;
```

### 2. 报表查询

复杂的多表 JOIN、聚合统计：

```javascript
export const sql = `
    SELECT
        DATE_FORMAT(o.create_time, '%Y-%m') AS month,
        SUM(o.amount) AS total_amount,
        COUNT(*) AS order_count
    FROM orders o
    WHERE o.status = 'COMPLETED'
        ${sqlExp(form.param.startDate, 'AND o.create_time >= ?')}
        ${sqlExp(form.param.endDate, 'AND o.create_time < ?')}
    GROUP BY DATE_FORMAT(o.create_time, '%Y-%m')
    ORDER BY month DESC
`;
```

### 3. 多数据库项目

同一套代码支持多种数据库部署：

```java
// 自动适配不同数据库
FDialect dialect = DbUtils.getDialect(dataSource);
String pageSql = dialect.generatePagingSql(baseSql, offset, limit);

// 字符串聚合（自动选择正确函数）
String aggFunc = dialect.buildStringAggFunction(column, ",");
// MySQL: GROUP_CONCAT
// PostgreSQL: STRING_AGG
```

---

## 下一步

- [快速开始](./quick-start.md) - 基础查询和 FSScript 动态 SQL
- [SQL 辅助函数](./sql-functions.md) - 完整的函数参考
- [多数据库支持](./multi-database.md) - 方言系统详解
