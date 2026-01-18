# Quick Start

This guide introduces how to use the `foggy-dataset` module for SQL queries, focusing on how to safely build dynamic SQL using FSScript.

## 1. Add Dependencies

```xml
<dependency>
    <groupId>com.foggyframework</groupId>
    <artifactId>foggy-dataset</artifactId>
    <version>${foggy.version}</version>
</dependency>
```


---

## 2. Build Dynamic SQL with FSScript (Core)

In actual business, query conditions are often dynamic. Traditional string concatenation has **SQL injection risks**:

```javascript
// ❌ Unsafe approach
let sql = `SELECT * FROM users WHERE name = '${userName}'`;
// If userName = "admin' OR '1'='1", it will cause SQL injection
```

`foggy-dataset` provides SQL helper functions to solve this problem through **PreparedStatement parameterized queries**.

### 3.1 Core Functions

| Function                           | Function | Purpose |
|------------------------------|------|------|
| `sqlExp(value, sql,force)`   | Conditional parameter | Single value condition query |
| `sqlInExp(array, sql,force)` | IN query | Multi-value IN query |
| `toLikeStr(str)`             | Both sides fuzzy | `LIKE '%value%'` |
| `toLikeStrL(str)`            | Left side fuzzy | `LIKE '%value'` |
| `toLikeStrR(str)`            | Right side fuzzy | `LIKE 'value%'` |
| `iif(cond, true, false)`     | Conditional expression | Complex condition concatenation |

### 3.2 sqlExp - Conditional Parameter

**Signature**: `sqlExp(value, sqlFragment[, force])`

When `value` is not null, add it to parameter list and return SQL fragment; otherwise return empty string.

```javascript
// user_query.fsscript

export const sql = `
    SELECT id, name, email, status, create_time
    FROM users
    WHERE 1=1
        ${sqlExp(form.param.teamId, 'AND team_id = ?')}
        ${sqlExp(form.param.status, 'AND status = ?',true)}
        ${sqlExp(form.param.startTime, 'AND create_time >= ?')}
        ${sqlExp(form.param.endTime, 'AND create_time < ?')}
    ORDER BY create_time DESC
`;
```

**Execution Result**:
- If `form.param.teamId = "T001"`, generates: `AND team_id = ?`, adds `"T001"` to parameter list
- If `form.param.teamId = null`, generates: empty string (condition is ignored)
- If `form.param.status = null or ''`, generates: `AND status = ?`, adds `null or ''` to parameter list

### 3.3 sqlInExp - IN Query

**Signature**: `sqlInExp(array, sqlPrefix[, force])`

Handle IN queries, automatically generate multiple placeholders:

```javascript
export const sql = `
    SELECT * FROM orders
    WHERE 1=1
        ${sqlInExp(form.param.statusList, 'AND status IN ')}
        ${sqlInExp(form.param.productIds, 'AND product_id IN ')}
`;
```

**Execution Result**:
- If `statusList = [10, 20, 30]`, generates: `AND status IN (?,?,?)`, adds 3 parameters
- If `statusList = []` or `null`, generates: empty string

### 3.4 Fuzzy Query

```javascript
export const sql = `
    SELECT * FROM users
    WHERE 1=1
        ${sqlExp(toLikeStr(form.param.name), 'AND name LIKE ?')}
        ${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}
        ${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
`;
```

**Execution Result**:
- `toLikeStr("张三")` → `"%张三%"` (matches records containing "张三")
- `toLikeStrR("138")` → `"138%"` (matches mobile numbers starting with "138")
- `toLikeStrL("gmail.com")` → `"%gmail.com"` (matches emails ending with "gmail.com")

### 3.5 Conditional Expression iif

**Signature**: `iif(condition, trueValue, falseValue)`

Used for complex condition concatenation:

```javascript
//Import function to build permissions
import {buildAuth} from './auth-utils.fsscript';
//  import {buildAuth} from '@authHelper';
export const sql = `
    SELECT * FROM orders
    WHERE 1=1 ${buildAuth()}
        ${iif(
            form.param.includeDeleted,
            '',
            'AND deleted = 0'
        )}
        ${iif(
            form.param.channel == 'OFFLINE',
            "AND (channel = 'OFFLINE' OR channel = 'INNER' OR channel IS NULL)",
            sqlExp(form.param.channel, 'AND channel = ?')
        )}
        ${iif(
            form.param.priceRange?.length == 2,
            sqlExp(form.param.priceRange[0], 'AND price >= ?') +
            sqlExp(form.param.priceRange[1], 'AND price <= ?'),
            ''
        )}
`;
```

---

## 4. Complete Example

### 4.1 FSScript Query Script

```javascript
// order_query.fsscript

/**
 * Order query
 * Supports: team filter, status filter, keyword search, date range, amount range
 */
export const sql = `
    SELECT
        o.order_id,
        o.order_no,
        o.amount,
        o.status,
        o.create_time,
        c.customer_name,
        t.team_name
    FROM orders o
    LEFT JOIN customers c ON o.customer_id = c.id
    LEFT JOIN teams t ON o.team_id = t.id
    WHERE o.deleted = 0
        ${sqlExp(form.param.teamId, 'AND o.team_id = ?')}
        ${sqlInExp(form.param.statusList, 'AND o.status IN ')}
        ${sqlExp(toLikeStr(form.param.orderNo), 'AND o.order_no LIKE ?')}
        ${sqlExp(toLikeStr(form.param.customerName), 'AND c.customer_name LIKE ?')}
        ${sqlExp(form.param.startTime, 'AND o.create_time >= ?')}
        ${sqlExp(form.param.endTime, 'AND o.create_time < ?')}
        ${iif(
            form.param.amountRange?.length == 2,
            sqlExp(form.param.amountRange[0], 'AND o.amount >= ?') +
            sqlExp(form.param.amountRange[1], 'AND o.amount <= ?'),
            ''
        )}
    ORDER BY o.create_time DESC
`;
```

### 4.2 Java Calling Code

```java
@Service
public class OrderQueryService {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FileFsscriptLoader fsscriptLoader;

    public List<Map<String, Object>> queryOrders(Map<String, Object> params) {
        // 1. Load FSScript
        Fsscript script = fsscriptLoader.findLoadFsscript(
            getClass().getResource("order_query.fsscript")
        );

        // 2. Create QueryExpEvaluator
        QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

        // 3. Set parameters
        Map<String, Object> form = new HashMap<>();
        form.put("param", params);
        evaluator.setVar("form", form);

        // 4. Execute script, generate SQL
        ExpEvaluator ee = script.newInstance(applicationContext, evaluator);
        script.eval(ee);
        String sql = (String) ee.getExportMap().get("sql");

        // 5. Get parameter list
        List<Object> args = evaluator.getArgs();

        // 6. Execute query
        return jdbcTemplate.queryForList(sql, args.toArray());
    }
}
```

### 4.3 Query with Pagination

```java
public PageResult<Order> queryOrdersWithPaging(QueryParams params) {
    // Load and execute script...
    String sql = ...;
    List<Object> args = evaluator.getArgs();

    // Query total count
    String countSql = "SELECT COUNT(*) FROM (" + sql + ") tmp";
    Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

    // Get database dialect, generate pagination SQL
    FDialect dialect = DbUtils.getDialect(dataSource);
    String pageSql = dialect.generatePagingSql(sql, params.getStart(), params.getLimit());

    // Execute paginated query
    List<Order> data = jdbcTemplate.query(
        pageSql,
        new BeanPropertyRowMapper<>(Order.class),
        args.toArray()
    );

    return new PageResult<>(data, total, params.getStart(), params.getLimit());
}
```

---

## 5. How It Works

```
FSScript template string
        ↓
QueryExpEvaluator execution
        ↓
SQL helper functions collect parameters
        ↓
Generate SQL + parameter list
        ↓
PreparedStatement execution
```

1. When FSScript script executes, use `QueryExpEvaluator` instead of regular `DefaultExpEvaluator`
2. SQL helper functions (like `sqlExp`) add parameter values to `QueryExpEvaluator`'s parameter list
3. Return SQL fragment containing `?` placeholders
4. Final generated SQL and parameter list are passed to JDBC PreparedStatement

---

## 6. Best Practices

### 6.1 Security

```javascript
// ✅ Correct: use sqlExp
${sqlExp(form.param.userId, 'AND user_id = ?')}

// ❌ Wrong: direct variable concatenation (SQL injection risk)
AND user_id = '${form.param.userId}'
```

### 6.2 Performance Optimization

```javascript
// ✅ Use prefix matching (can use index)
${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}

// ⚠️ Left-side fuzzy matching cannot use index
${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
```

### 6.3 Readability

```javascript
// ✅ Extract complex conditions as variables
let hasDateRange = form.param.startDate && form.param.endDate;
let hasPriceRange = form.param.priceRange?.length == 2;

export const sql = `
    SELECT * FROM products
    WHERE 1=1
        ${iif(hasDateRange,
            sqlExp(form.param.startDate, 'AND create_time >= ?') +
            sqlExp(form.param.endDate, 'AND create_time <= ?'),
            ''
        )}
        ${iif(hasPriceRange,
            sqlExp(form.param.priceRange[0], 'AND price >= ?') +
            sqlExp(form.param.priceRange[1], 'AND price <= ?'),
            ''
        )}
`;
```

---

## Next Steps

- [SQL Helper Functions Guide](./sql-functions.md) - Complete function reference
- [Multi-Database Support](./multi-database.md) - MySQL, PostgreSQL, SQL Server, SQLite adaptation
- [API Reference](../api/query-api.md) - DatasetTemplate, QueryExpEvaluator API
