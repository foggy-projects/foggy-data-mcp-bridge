# Introduction

`foggy-dataset` is the database foundation layer module of the Foggy framework, providing core capabilities such as multi-database support, SQL construction, and query execution.

## Core Features

### Dynamic SQL Construction

Based on the FSScript script engine, safely build dynamic SQL through SQL helper functions:

```javascript
export const sql = `
    SELECT * FROM orders
    WHERE 1=1
        ${sqlExp(form.param.teamId, 'AND team_id = ?')}
        ${sqlInExp(form.param.statusList, 'AND status IN ')}
        ${sqlExp(toLikeStr(form.param.keyword), 'AND name LIKE ?')}
`;
```

**Core Advantages**:
- ✅ **Prevent SQL Injection**: Use PreparedStatement parameterized queries
- ✅ **Dynamic Condition Concatenation**: Empty values are automatically skipped, no need for extensive if-else, strong readability


### 1. SQL Helper Functions

Safely build dynamic SQL in FSScript:

| Function | Function |
|------|------|
| `sqlExp(value, sql)` | Conditional parameter, skipped when value is empty |
| `sqlInExp(array, sql)` | IN query, automatically generates placeholders |
| `toLikeStr(str)` | Both sides fuzzy `%value%` |
| `toLikeStrL(str)` | Left side fuzzy `%value` |
| `toLikeStrR(str)` | Right side fuzzy `value%` |
| `iif(cond, true, false)` | Conditional expression |

### 2. Query Evaluator (QueryExpEvaluator)

Works with FSScript execution to collect SQL parameters:

```java
QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);
evaluator.setVar("form", formData);

// Execute script
String sql = (String) exp.evalResult(evaluator);

// Get parameter list
List<Object> args = evaluator.getArgs();
```

---

## Use Cases

### 1. Dynamic Query

Users can freely combine query conditions, and the backend dynamically concatenates SQL:

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

### 2. Report Query

Complex multi-table JOIN and aggregation statistics, easily reused with fsscript features:

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

## Next Steps

- [Quick Start](./quick-start.md) - Basic queries and FSScript dynamic SQL
- [SQL Helper Functions](./sql-functions.md) - Complete function reference
- [Multi-Database Support](./multi-database.md) - Detailed dialect system
