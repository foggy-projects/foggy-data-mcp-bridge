# FSScript SQL Helper Functions Guide

[中文文档](FSScript-SQL-Functions.zh-CN.md)

This document describes the SQL helper functions provided by Foggy Dataset for safely building dynamic SQL queries in FSScript.

## Table of Contents

1. [Overview](#1-overview)
2. [QueryExpEvaluator](#2-queryexpevaluator)
3. [Core Functions](#3-core-functions)
4. [Complete Examples](#4-complete-examples)
5. [Best Practices](#5-best-practices)

---

## 1. Overview

### 1.1 Why SQL Helper Functions?

A common security vulnerability in dynamic SQL construction is **SQL injection attacks**. Traditional string concatenation is susceptible to injection:

```javascript
// ❌ Unsafe approach
let sql = `SELECT * FROM users WHERE name = '${userName}'`;
// If userName = "admin' OR '1'='1", SQL injection occurs
```

Foggy Dataset's SQL helper functions solve this through **PreparedStatement parameterized queries**:

```javascript
// ✅ Safe approach
let sql = `SELECT * FROM users WHERE 1=1
    ${sqlExp(userName, 'AND name = ?')}
`;
// userName is safely added to PreparedStatement parameter list
```

### 1.2 How It Works

SQL helper functions work with `QueryExpEvaluator`:

1. FSScript executes using `QueryExpEvaluator` instead of regular `DefaultExpEvaluator`
2. SQL helper functions (like `sqlExp`) add parameter values to `QueryExpEvaluator`'s parameter list
3. Return SQL fragments containing `?` placeholders
4. Final SQL and parameter list are passed to JDBC PreparedStatement

```
FSScript Template String
        ↓
    QueryExpEvaluator executes
        ↓
    SQL helper functions collect parameters
        ↓
    Generate SQL + parameter list
        ↓
    PreparedStatement executes
```

---

## 2. QueryExpEvaluator

### 2.1 Creating QueryExpEvaluator

`QueryExpEvaluator` is an expression evaluator specifically for SQL queries:

```java
import com.foggyframework.dataset.model.QueryExpEvaluator;
import org.springframework.context.ApplicationContext;

// Create QueryExpEvaluator with Spring context
QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

// Execute FSScript
Exp exp = parser.compileEl(sqlScript);
String sql = (String) exp.evalResult(evaluator);

// Get parameter list
List<Object> args = evaluator.getArgs();

// Execute query with JdbcTemplate
List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, args.toArray());
```

### 2.2 QueryExpEvaluator Features

Besides collecting SQL parameters, `QueryExpEvaluator` supports:

- **Pagination parameters**: `start`, `limit`
- **Query configuration**: `QueryConfig`
- **Result mapping**: `beanCls`
- **Return total count**: `returnTotal`

```java
QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

// Set pagination
evaluator.setStart(0);
evaluator.setLimit(20);

// Set result mapping class
evaluator.setBeanCls(UserDTO.class);

// Whether to return total record count
evaluator.setReturnTotal(true);
```

---

## 3. Core Functions

### 3.1 sqlExp - Conditional Parameter

**Signature**: `sqlExp(value, sqlFragment[, force])`

**Function**: When value is not empty, adds it to parameter list and returns SQL fragment; otherwise returns empty string.

**Parameters**:
- `value` - Parameter value, can be any type
- `sqlFragment` - SQL fragment, use `?` as placeholder
- `force` (optional) - Force add even if value is empty (default false)

**Examples**:

```javascript
// Basic usage
${sqlExp(form.param.teamId, 'AND team_id = ?')}
// If teamId has value, returns: "AND team_id = ?" and adds parameter
// If teamId is empty, returns: ""

// Multiple placeholders
${sqlExp(form.param.startTime, 'AND ? <= create_time')}
${sqlExp(form.param.endTime, 'AND create_time < ?')}

// Force mode
${sqlExp(form.param.status, 'AND status = ?', true)}
// Even if status is empty, returns SQL fragment and adds null parameter
```

### 3.2 sqlInExp - IN Query

**Signature**: `sqlInExp(array, sqlPrefix[, force])`

**Function**: Handles IN queries, automatically generates multiple placeholders.

**Parameters**:
- `array` - Array or collection
- `sqlPrefix` - SQL prefix, e.g., "AND status IN "
- `force` (optional) - Force add (default false)

**Examples**:

```javascript
// Array parameter
${sqlInExp(form.param.statusList, 'AND status IN ')}
// If statusList = [10, 20, 30], returns: "AND status IN (?,?,?)"
// and adds 3 parameters: 10, 20, 30

// Empty array handling
${sqlInExp([], 'AND status IN ')}
// Returns: ""

// Force mode - empty array
${sqlInExp([], 'AND status IN ', true)}
// Returns: "AND status IN (1) and 1=2"  (ensures no results)
```

**Notes**:
- Automatically handles `Collection`, `Object[]`, and single values
- Empty array returns empty string by default (no condition added)
- `force=true` with empty array returns always-false condition

### 3.3 toLikeStr / str2Like - Fuzzy Query (Both Ends)

**Signature**: `toLikeStr(str)` or `str2Like(str)`

**Function**: Adds `%` to both ends of string for LIKE fuzzy queries.

**Example**:

```javascript
${sqlExp(toLikeStr(form.param.userName), 'AND name LIKE ?')}
// If userName = "John", generates parameter: "%John%"
// SQL: "AND name LIKE ?"
```

### 3.4 toLikeStrL - Fuzzy Query (Left Side)

**Signature**: `toLikeStrL(str)`

**Function**: Adds `%` to left side of string for suffix matching.

**Example**:

```javascript
${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
// If email = "gmail.com", generates parameter: "%gmail.com"
// Matches all emails ending with gmail.com
```

### 3.5 toLikeStrR - Fuzzy Query (Right Side)

**Signature**: `toLikeStrR(str)`

**Function**: Adds `%` to right side of string for prefix matching.

**Example**:

```javascript
${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}
// If mobile = "138", generates parameter: "138%"
// Matches all phone numbers starting with 138
```

### 3.6 iif - Conditional Expression

**Signature**: `iif(condition, trueValue, falseValue)`

**Function**: Ternary expression, returns different values based on condition.

**Examples**:

```javascript
// Simple condition
${iif(
    form.param.includeDeleted,
    '',
    'AND deleted = 0'
)}

// Complex condition composition
${iif(
    form.param.tranChannel == 'OFFLINE',
    "AND (tran_channel='OFFLINE' OR tran_channel='INNER' OR tran_channel IS NULL)",
    sqlExp(form.param.tranChannel, 'AND tran_channel = ?')
)}

// Range query
${iif(
    form.param.priceRange?.length == 2,
    sqlExp(form.param.priceRange[0], 'AND price >= ?') +
    sqlExp(form.param.priceRange[1], 'AND price <= ?'),
    ''
)}
```

---

## 4. Complete Examples

### 4.1 Simple Query Example

```javascript
// user_query.fsscript
import {getCurrentUser} from '@authService';

export const sql = `
    SELECT
        id,
        name,
        email,
        status,
        create_time
    FROM users
    WHERE tenant_id = '${getCurrentUser().tenantId}'
        ${sqlExp(form.param.keyword, 'AND name LIKE ?', toLikeStr)}
        ${sqlInExp(form.param.statusList, 'AND status IN ')}
        ${sqlExp(form.param.startDate, 'AND create_time >= ?')}
    ORDER BY create_time DESC
`;
```

```java
// Java calling code
@Service
public class UserQueryService {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> queryUsers(Map<String, Object> params) {
        // Load FSScript
        String scriptPath = "classpath:scripts/user_query.fsscript";
        String scriptContent = loadScript(scriptPath);

        // Compile script
        ExpParser parser = new ExpParser();
        Exp exp = parser.compileEl(scriptContent);

        // Create query evaluator
        QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

        // Set parameters
        Map<String, Object> form = new HashMap<>();
        form.put("param", params);
        evaluator.setVar("form", form);

        // Execute script to generate SQL
        String sql = (String) exp.evalResult(evaluator);

        // Get parameter list
        List<Object> args = evaluator.getArgs();

        // Execute query
        return jdbcTemplate.queryForList(sql, args.toArray());
    }
}
```

### 4.2 Complex Query Example

```javascript
// order_detail_query.fsscript
import {workonSessionTokenUsingCache as token} from '@saasBasicWebUtils';

/**
 * Order detail query
 * Supports multi-table JOIN, complex conditions, pagination
 */
export const sql = `
    SELECT
        o.order_id,
        o.order_no,
        o.amount,
        o.status,
        o.create_time,
        c.customer_name,
        c.customer_mobile,
        t.team_name,
        p.product_name
    FROM orders o
    LEFT JOIN customers c ON o.customer_id = c.id
    LEFT JOIN teams t ON o.team_id = t.id
    LEFT JOIN order_items oi ON o.order_id = oi.order_id
    LEFT JOIN products p ON oi.product_id = p.id
    WHERE o.tenant_id = '${token.tenantId}'
        AND o.deleted = 0
        ${sqlExp(form.param.teamId, 'AND o.team_id = ?')}
        ${sqlInExp(form.param.statusList, 'AND o.status IN ')}
        ${sqlExp(form.param.orderNo, 'AND o.order_no LIKE ?', toLikeStr)}
        ${sqlExp(form.param.customerMobile, 'AND c.customer_mobile LIKE ?', toLikeStrR)}
        ${sqlExp(form.param.startTime, 'AND ? <= o.create_time')}
        ${sqlExp(form.param.endTime, 'AND o.create_time < ?')}
        ${iif(
            form.param.amountRange?.length == 2,
            sqlExp(form.param.amountRange[0], 'AND o.amount >= ?') +
            sqlExp(form.param.amountRange[1], 'AND o.amount <= ?'),
            ''
        )}
        ${iif(
            form.param.paymentMethod == 'CASH',
            "AND (o.payment_method = 'CASH' OR o.payment_method IS NULL)",
            sqlExp(form.param.paymentMethod, 'AND o.payment_method = ?')
        )}
    ORDER BY o.create_time DESC
`;
```

### 4.3 Query with Pagination

```java
@Service
public class OrderQueryService {

    public PageResult<Order> queryOrdersWithPaging(QueryParams params) {
        ExpParser parser = new ExpParser();
        Exp exp = parser.compileEl(scriptContent);

        QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

        // Set pagination parameters
        evaluator.setStart(params.getStart());
        evaluator.setLimit(params.getLimit());
        evaluator.setReturnTotal(true);

        // Set query parameters
        Map<String, Object> form = new HashMap<>();
        form.put("param", params.getConditions());
        evaluator.setVar("form", form);

        // Generate SQL
        String sql = (String) exp.evalResult(evaluator);
        List<Object> args = evaluator.getArgs();

        // Query total count
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") tmp";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        // Query paginated data
        String pageSql = sql + " LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(evaluator.getLimit());
        pageArgs.add(evaluator.getStart());

        List<Order> data = jdbcTemplate.query(pageSql,
            new BeanPropertyRowMapper<>(Order.class),
            pageArgs.toArray());

        return new PageResult<>(data, total, params.getStart(), params.getLimit());
    }
}
```

---

## 5. Best Practices

### 5.1 Security

✅ **Always use SQL helper functions**
```javascript
// ✅ Correct
${sqlExp(form.param.userId, 'AND user_id = ?')}

// ❌ Wrong - SQL injection risk
AND user_id = '${form.param.userId}'
```

✅ **Don't concatenate variables in SQL fragments**
```javascript
// ✅ Correct
${sqlExp(form.param.fieldName, 'AND field_name = ?')}

// ❌ Wrong - Still has injection risk
${sqlExp(form.param.value, `AND ${form.param.field} = ?`)}
```

### 5.2 Performance Optimization

✅ **Avoid SQL helper functions in loops**
```javascript
// ❌ Poor performance
let conditions = '';
for (let field of form.param.fields) {
    conditions += sqlExp(field.value, `AND ${field.name} = ?`);
}

// ✅ Use sqlInExp or pre-process instead
```

✅ **Use indexes wisely**
```javascript
// ✅ Prefix match (can use index)
${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}

// ⚠️ Left-side fuzzy match cannot use index
${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
```

### 5.3 Readability

✅ **Add comments for complex logic**
```javascript
/**
 * Special channel handling:
 * OFFLINE channel includes OFFLINE/INNER/NULL three cases
 */
${iif(
    form.param.channel == 'OFFLINE',
    "AND (channel='OFFLINE' OR channel='INNER' OR channel IS NULL)",
    sqlExp(form.param.channel, 'AND channel = ?')
)}
```

✅ **Extract complex conditions to variables**
```javascript
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

### 5.4 Testing

```java
@Test
public void testSqlGeneration() {
    // Prepare test data
    Map<String, Object> params = new HashMap<>();
    params.put("teamId", "team001");
    params.put("statusList", Arrays.asList(10, 20, 30));

    Map<String, Object> form = Map.of("param", params);

    // Execute script
    QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);
    evaluator.setVar("form", form);

    String sql = (String) exp.evalResult(evaluator);
    List<Object> args = evaluator.getArgs();

    // Verify results
    assertThat(sql).contains("AND team_id = ?");
    assertThat(sql).contains("AND status IN (?,?,?)");
    assertThat(args).hasSize(4);
    assertThat(args.get(0)).isEqualTo("team001");
}
```

---

## Appendix

### A. Function Summary Table

| Function | Purpose | Use Case |
|----------|---------|----------|
| `sqlExp(value, sql[, force])` | Conditional parameter | Single value condition query |
| `sqlInExp(array, sql[, force])` | IN query | Multi-value IN query |
| `toLikeStr(str)` | Both-ends fuzzy | LIKE '%value%' |
| `toLikeStrL(str)` | Left-side fuzzy | LIKE '%value' |
| `toLikeStrR(str)` | Right-side fuzzy | LIKE 'value%' |
| `iif(cond, true, false)` | Conditional expression | Complex condition composition |

### B. FAQ

**Q: What's the difference between sqlExp and direct string concatenation?**

A: `sqlExp` uses PreparedStatement parameterized queries to prevent SQL injection; string concatenation has injection risks.

**Q: When should I use the force parameter?**

A: When you need to explicitly pass null value to database, or in special business scenarios where you need to preserve the condition.

**Q: Can sqlInExp handle empty arrays?**

A: Yes. By default, empty array returns empty string (no condition added); `force=true` returns always-false condition.

**Q: How to use database functions in FSScript?**

A: Use directly in SQL fragments:
```javascript
${sqlExp(form.param.date, 'AND DATE(create_time) = ?')}
AND YEAR(create_time) = 2024
```

**Q: Is dynamic table name supported?**

A: Not recommended due to security risks. If necessary, validate through whitelist:
```javascript
// Java side validation
String tableName = validateTableName(params.get("tableName"));
evaluator.setVar("tableName", tableName);

// Use in FSScript
SELECT * FROM ${tableName} WHERE ...
```
