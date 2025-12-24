# 快速开始

本指南介绍如何使用 `foggy-dataset` 模块进行 SQL 查询，重点讲解如何使用 FSScript 安全地构建动态 SQL。

## 1. 添加依赖

```xml
<dependency>
    <groupId>com.foggyframework</groupId>
    <artifactId>foggy-dataset</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

## 2. 基础查询

### 2.1 使用 DatasetTemplate

`DatasetTemplate` 是基础查询工具类，封装了 `JdbcTemplate` 和数据库方言：

```java
@Autowired
DataSource dataSource;

// 获取 DatasetTemplate（自动检测数据库类型）
DatasetTemplate template = DataSourceQueryUtils.getDatasetTemplate(dataSource);

// 查询 Map 列表
List<Map<String, Object>> users = template.queryMapList(
    "SELECT * FROM users WHERE status = ?",
    new Object[]{1}
);

// 查询对象列表
List<User> userList = template.queryObjectList(
    User.class,
    "SELECT * FROM users WHERE age > ?",
    18
);

// 查询单个对象（不会因空结果抛异常）
User user = template.queryForObject(
    User.class,
    "SELECT * FROM users WHERE user_id = ?",
    "U001"
);

// 查询总数
Long count = template.queryCount(
    "SELECT * FROM users WHERE status = ?",
    new Object[]{1}
);
```

### 2.2 多数据库支持

`foggy-dataset` 自动检测数据库类型，生成适配的 SQL：

```java
// 自动检测数据库方言
FDialect dialect = DbUtils.getDialect(dataSource);
log.info("数据库类型: {}", dialect.getProductName());

// 生成分页 SQL（自动适配不同数据库）
String sql = "SELECT * FROM users";
String pageSql = dialect.generatePagingSql(sql, 0, 20);
// MySQL: SELECT * FROM users LIMIT 0, 20
// PostgreSQL: SELECT * FROM users LIMIT 20 OFFSET 0
// SQL Server: SELECT * FROM users OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY
```

---

## 3. 使用 FSScript 构建动态 SQL（核心）

在实际业务中，查询条件往往是动态的。传统的字符串拼接方式存在 **SQL 注入风险**：

```javascript
// ❌ 不安全的做法
let sql = `SELECT * FROM users WHERE name = '${userName}'`;
// 如果 userName = "admin' OR '1'='1"，将导致 SQL 注入
```

`foggy-dataset` 提供 SQL 辅助函数，通过 **PreparedStatement 参数化查询** 解决这个问题。

### 3.1 核心函数

| 函数 | 功能 | 用途 |
|------|------|------|
| `sqlExp(value, sql)` | 条件参数 | 单值条件查询 |
| `sqlInExp(array, sql)` | IN 查询 | 多值 IN 查询 |
| `toLikeStr(str)` | 两端模糊 | `LIKE '%value%'` |
| `toLikeStrL(str)` | 左侧模糊 | `LIKE '%value'` |
| `toLikeStrR(str)` | 右侧模糊 | `LIKE 'value%'` |
| `iif(cond, true, false)` | 条件表达式 | 复杂条件拼接 |

### 3.2 sqlExp - 条件参数

**签名**: `sqlExp(value, sqlFragment[, force])`

当 `value` 不为空时，将其添加到参数列表，并返回 SQL 片段；否则返回空字符串。

```javascript
// user_query.fsscript

export const sql = `
    SELECT id, name, email, status, create_time
    FROM users
    WHERE 1=1
        ${sqlExp(form.param.teamId, 'AND team_id = ?')}
        ${sqlExp(form.param.status, 'AND status = ?')}
        ${sqlExp(form.param.startTime, 'AND create_time >= ?')}
        ${sqlExp(form.param.endTime, 'AND create_time < ?')}
    ORDER BY create_time DESC
`;
```

**执行结果**：
- 如果 `form.param.teamId = "T001"`，生成：`AND team_id = ?`，参数列表添加 `"T001"`
- 如果 `form.param.teamId = null`，生成：空字符串（条件被忽略）

### 3.3 sqlInExp - IN 查询

**签名**: `sqlInExp(array, sqlPrefix[, force])`

处理 IN 查询，自动生成多个占位符：

```javascript
export const sql = `
    SELECT * FROM orders
    WHERE 1=1
        ${sqlInExp(form.param.statusList, 'AND status IN ')}
        ${sqlInExp(form.param.productIds, 'AND product_id IN ')}
`;
```

**执行结果**：
- 如果 `statusList = [10, 20, 30]`，生成：`AND status IN (?,?,?)`，添加 3 个参数
- 如果 `statusList = []` 或 `null`，生成：空字符串

### 3.4 模糊查询

```javascript
export const sql = `
    SELECT * FROM users
    WHERE 1=1
        ${sqlExp(toLikeStr(form.param.name), 'AND name LIKE ?')}
        ${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}
        ${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
`;
```

**执行结果**：
- `toLikeStr("张三")` → `"%张三%"`（匹配包含"张三"的记录）
- `toLikeStrR("138")` → `"138%"`（匹配以"138"开头的手机号）
- `toLikeStrL("gmail.com")` → `"%gmail.com"`（匹配以"gmail.com"结尾的邮箱）

### 3.5 条件表达式 iif

**签名**: `iif(condition, trueValue, falseValue)`

用于复杂的条件拼接：

```javascript
export const sql = `
    SELECT * FROM orders
    WHERE 1=1
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

## 4. 完整示例

### 4.1 FSScript 查询脚本

```javascript
// order_query.fsscript

/**
 * 订单查询
 * 支持：团队筛选、状态筛选、关键字搜索、日期范围、金额范围
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

### 4.2 Java 调用代码

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
        // 1. 加载 FSScript
        Fsscript script = fsscriptLoader.findLoadFsscript(
            getClass().getResource("order_query.fsscript")
        );

        // 2. 创建 QueryExpEvaluator
        QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

        // 3. 设置参数
        Map<String, Object> form = new HashMap<>();
        form.put("param", params);
        evaluator.setVar("form", form);

        // 4. 执行脚本，生成 SQL
        ExpEvaluator ee = script.newInstance(applicationContext, evaluator);
        script.eval(ee);
        String sql = (String) ee.getExportMap().get("sql");

        // 5. 获取参数列表
        List<Object> args = evaluator.getArgs();

        // 6. 执行查询
        return jdbcTemplate.queryForList(sql, args.toArray());
    }
}
```

### 4.3 带分页的查询

```java
public PageResult<Order> queryOrdersWithPaging(QueryParams params) {
    // 加载和执行脚本...
    String sql = ...;
    List<Object> args = evaluator.getArgs();

    // 查询总数
    String countSql = "SELECT COUNT(*) FROM (" + sql + ") tmp";
    Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

    // 获取数据库方言，生成分页 SQL
    FDialect dialect = DbUtils.getDialect(dataSource);
    String pageSql = dialect.generatePagingSql(sql, params.getStart(), params.getLimit());

    // 执行分页查询
    List<Order> data = jdbcTemplate.query(
        pageSql,
        new BeanPropertyRowMapper<>(Order.class),
        args.toArray()
    );

    return new PageResult<>(data, total, params.getStart(), params.getLimit());
}
```

---

## 5. 工作原理

```
FSScript 模板字符串
        ↓
QueryExpEvaluator 执行
        ↓
SQL 辅助函数收集参数
        ↓
生成 SQL + 参数列表
        ↓
PreparedStatement 执行
```

1. FSScript 脚本执行时，使用 `QueryExpEvaluator` 而非普通的 `DefaultExpEvaluator`
2. SQL 辅助函数（如 `sqlExp`）将参数值添加到 `QueryExpEvaluator` 的参数列表
3. 返回包含 `?` 占位符的 SQL 片段
4. 最终生成的 SQL 和参数列表传递给 JDBC PreparedStatement

---

## 6. 最佳实践

### 6.1 安全性

```javascript
// ✅ 正确：使用 sqlExp
${sqlExp(form.param.userId, 'AND user_id = ?')}

// ❌ 错误：直接拼接变量（SQL 注入风险）
AND user_id = '${form.param.userId}'
```

### 6.2 性能优化

```javascript
// ✅ 使用前缀匹配（可以用到索引）
${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}

// ⚠️ 左侧模糊匹配无法使用索引
${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
```

### 6.3 可读性

```javascript
// ✅ 提取复杂条件为变量
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

## 下一步

- [SQL 辅助函数详解](./sql-functions.md) - 完整的函数参考
- [多数据库支持](./multi-database.md) - MySQL、PostgreSQL、SQL Server、SQLite 适配
- [API 参考](../api/query-api.md) - DatasetTemplate、QueryExpEvaluator API
