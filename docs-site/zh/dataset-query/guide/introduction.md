# 简介

`foggy-dataset` 是 Foggy 框架的数据库基础层模块，提供多数据库支持、SQL 构建和查询执行等核心能力。

## 核心特性

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
- ✅ **条件动态拼接**：空值自动跳过，无需大量 if-else，可读性强


### 1. SQL 辅助函数

在 FSScript 中安全构建动态 SQL：

| 函数 | 功能 |
|------|------|
| `sqlExp(value, sql)` | 条件参数，值为空时跳过 |
| `sqlInExp(array, sql)` | IN 查询，自动生成占位符 |
| `toLikeStr(str)` | 两端模糊 `%value%` |
| `toLikeStrL(str)` | 左侧模糊 `%value` |
| `toLikeStrR(str)` | 右侧模糊 `value%` |
| `iif(cond, true, false)` | 条件表达式 |

### 2. 查询求值器 (QueryExpEvaluator)

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

复杂的多表 JOIN、聚合统计，借助fsscript特性轻松复用：

```javascript
// utils.fsscript
export const selectSql = `
    SELECT
        DATE_FORMAT(o.create_time, '%Y-%m') AS month,
        SUM(o.amount) AS total_amount,
        COUNT(*) AS order_count
    FROM orders o
`;
```
```javascript
import {selectSql} from './utils.fsscript';

export const sql = `
    ${selectSql}
    WHERE o.status = 'COMPLETED'
        ${sqlExp(form.param.startDate, 'AND o.create_time >= ?')}
        ${sqlExp(form.param.endDate, 'AND o.create_time < ?')}
    GROUP BY DATE_FORMAT(o.create_time, '%Y-%m')
    ORDER BY month DESC
`;
```

---

## 下一步

- [快速开始](./quick-start.md) - 基础查询和 FSScript 动态 SQL
- [SQL 辅助函数](./sql-functions.md) - 完整的函数参考
- [多数据库支持](./multi-database.md) - 方言系统详解
