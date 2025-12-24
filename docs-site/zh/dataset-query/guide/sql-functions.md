# SQL 辅助函数详解

本文档详细介绍 Foggy Dataset 提供的 SQL 辅助函数，用于在 FSScript 中安全地构建动态 SQL 查询。

## 1. 概述

### 1.1 为什么需要 SQL 辅助函数?

在动态 SQL 拼接中，常见的安全隐患是 **SQL 注入攻击**。传统的字符串拼接方式容易受到注入攻击:

```javascript
// ❌ 不安全的做法
let sql = `SELECT * FROM users WHERE name = '${userName}'`;
// 如果 userName = "admin' OR '1'='1"，将导致 SQL 注入
```

Foggy Dataset 提供的 SQL 辅助函数通过 **PreparedStatement 参数化查询** 解决这个问题:

```javascript
// ✅ 安全的做法
let sql = `SELECT * FROM users WHERE 1=1
    ${sqlExp(userName, 'AND name = ?')}
`;
// userName 会被安全地添加到 PreparedStatement 参数列表
```

### 1.2 工作原理

SQL 辅助函数配合 `QueryExpEvaluator` 工作:

1. FSScript 脚本执行时，使用 `QueryExpEvaluator` 而非普通的 `DefaultExpEvaluator`
2. SQL 辅助函数（如 `sqlExp`）将参数值添加到 `QueryExpEvaluator` 的参数列表
3. 返回包含 `?` 占位符的 SQL 片段
4. 最终生成的 SQL 和参数列表传递给 JDBC PreparedStatement

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

---

## 2. QueryExpEvaluator

### 2.1 创建 QueryExpEvaluator

`QueryExpEvaluator` 是专门用于 SQL 查询的表达式求值器:

```java
import com.foggyframework.dataset.model.QueryExpEvaluator;
import org.springframework.context.ApplicationContext;

// 创建带 Spring 上下文的 QueryExpEvaluator
QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

// 执行 FSScript
Exp exp = parser.compileEl(sqlScript);
String sql = (String) exp.evalResult(evaluator);

// 获取参数列表
List<Object> args = evaluator.getArgs();

// 使用 JdbcTemplate 执行查询
List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, args.toArray());
```

### 2.2 QueryExpEvaluator 的功能

除了收集 SQL 参数，`QueryExpEvaluator` 还支持:

- **分页参数**: `start`, `limit`
- **查询配置**: `QueryConfig`
- **结果映射**: `beanCls`
- **返回总数**: `returnTotal`

```java
QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

// 设置分页
evaluator.setStart(0);
evaluator.setLimit(20);

// 设置结果映射类
evaluator.setBeanCls(UserDTO.class);

// 是否返回总记录数
evaluator.setReturnTotal(true);
```

---

## 3. 核心函数

### 3.1 sqlExp - 条件参数

**签名**: `sqlExp(value, sqlFragment[, force])`

**功能**: 当 value 不为空时，将其添加到参数列表，并返回 SQL 片段；否则返回空字符串。

**参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `value` | any | 是 | 参数值，可以是任意类型 |
| `sqlFragment` | String | 是 | SQL 片段，使用 `?` 作为占位符 |
| `force` | Boolean | 否 | 是否强制添加，即使 value 为空（默认 false）|

**示例**:

```javascript
// 基本用法
${sqlExp(form.param.teamId, 'AND team_id = ?')}
// 如果 teamId 有值，返回: "AND team_id = ?"，并添加参数
// 如果 teamId 为空，返回: ""

// 多个占位符
${sqlExp(form.param.startTime, 'AND ? <= create_time')}
${sqlExp(form.param.endTime, 'AND create_time < ?')}

// 强制模式
${sqlExp(form.param.status, 'AND status = ?', true)}
// 即使 status 为空，也会返回 SQL 片段并添加 null 参数
```

---

### 3.2 sqlInExp - IN 查询

**签名**: `sqlInExp(array, sqlPrefix[, force])`

**功能**: 处理 IN 查询，自动生成多个占位符。

**参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `array` | Array/Collection | 是 | 数组或集合 |
| `sqlPrefix` | String | 是 | SQL 前缀，如 `"AND status IN "` |
| `force` | Boolean | 否 | 是否强制添加（默认 false）|

**示例**:

```javascript
// 数组参数
${sqlInExp(form.param.statusList, 'AND status IN ')}
// 如果 statusList = [10, 20, 30]，返回: "AND status IN (?,?,?)"
// 并添加 3 个参数: 10, 20, 30

// 空数组处理
${sqlInExp([], 'AND status IN ')}
// 返回: ""

// 强制模式 - 空数组
${sqlInExp([], 'AND status IN ', true)}
// 返回: "AND status IN (1) and 1=2"  (确保查询无结果)
```

**注意事项**:
- 自动处理 `Collection`、`Object[]` 和单个值
- 空数组时，默认返回空字符串（不添加条件）
- `force=true` 时，空数组返回恒假条件

---

### 3.3 toLikeStr / str2Like - 模糊查询（两端）

**签名**: `toLikeStr(str)` 或 `str2Like(str)`

**功能**: 在字符串两端添加 `%`，用于 LIKE 模糊查询。

**示例**:

```javascript
${sqlExp(toLikeStr(form.param.userName), 'AND name LIKE ?')}
// 如果 userName = "张三"，生成参数: "%张三%"
// SQL: "AND name LIKE ?"
```

---

### 3.4 toLikeStrL - 模糊查询（左侧）

**签名**: `toLikeStrL(str)`

**功能**: 在字符串左侧添加 `%`，用于后缀匹配。

**示例**:

```javascript
${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
// 如果 email = "gmail.com"，生成参数: "%gmail.com"
// 匹配所有以 gmail.com 结尾的邮箱
```

---

### 3.5 toLikeStrR - 模糊查询（右侧）

**签名**: `toLikeStrR(str)`

**功能**: 在字符串右侧添加 `%`，用于前缀匹配。

**示例**:

```javascript
${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}
// 如果 mobile = "138"，生成参数: "138%"
// 匹配所有以 138 开头的手机号
```

---

### 3.6 iif - 条件表达式

**签名**: `iif(condition, trueValue, falseValue)`

**功能**: 三元表达式，根据条件返回不同的值。

**示例**:

```javascript
// 简单条件
${iif(
    form.param.includeDeleted,
    '',
    'AND deleted = 0'
)}

// 复杂条件拼接
${iif(
    form.param.tranChannel == 'OFFLINE',
    "AND (tran_channel='OFFLINE' OR tran_channel='INNER' OR tran_channel IS NULL)",
    sqlExp(form.param.tranChannel, 'AND tran_channel = ?')
)}

// 范围查询
${iif(
    form.param.priceRange?.length == 2,
    sqlExp(form.param.priceRange[0], 'AND price >= ?') +
    sqlExp(form.param.priceRange[1], 'AND price <= ?'),
    ''
)}
```

---

## 4. 完整示例

### 4.1 简单查询示例

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
// Java 调用代码
@Service
public class UserQueryService {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> queryUsers(Map<String, Object> params) {
        // 加载 FSScript
        String scriptPath = "classpath:scripts/user_query.fsscript";
        String scriptContent = loadScript(scriptPath);

        // 编译脚本
        ExpParser parser = new ExpParser();
        Exp exp = parser.compileEl(scriptContent);

        // 创建查询求值器
        QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

        // 设置参数
        Map<String, Object> form = new HashMap<>();
        form.put("param", params);
        evaluator.setVar("form", form);

        // 执行脚本生成 SQL
        String sql = (String) exp.evalResult(evaluator);

        // 获取参数列表
        List<Object> args = evaluator.getArgs();

        // 执行查询
        return jdbcTemplate.queryForList(sql, args.toArray());
    }
}
```

### 4.2 复杂查询示例

```javascript
// order_detail_query.fsscript
import {workonSessionTokenUsingCache as token} from '@saasBasicWebUtils';

/**
 * 订单明细查询
 * 支持多表 JOIN、复杂条件、分页
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

### 4.3 带分页的查询

```java
@Service
public class OrderQueryService {

    public PageResult<Order> queryOrdersWithPaging(QueryParams params) {
        ExpParser parser = new ExpParser();
        Exp exp = parser.compileEl(scriptContent);

        QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);

        // 设置分页参数
        evaluator.setStart(params.getStart());
        evaluator.setLimit(params.getLimit());
        evaluator.setReturnTotal(true);

        // 设置查询参数
        Map<String, Object> form = new HashMap<>();
        form.put("param", params.getConditions());
        evaluator.setVar("form", form);

        // 生成 SQL
        String sql = (String) exp.evalResult(evaluator);
        List<Object> args = evaluator.getArgs();

        // 查询总数
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") tmp";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        // 查询分页数据
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

## 5. 最佳实践

### 5.1 安全性

**始终使用 SQL 辅助函数**
```javascript
// ✅ 正确
${sqlExp(form.param.userId, 'AND user_id = ?')}

// ❌ 错误 - SQL 注入风险
AND user_id = '${form.param.userId}'
```

**不要在 SQL 片段中拼接变量**
```javascript
// ✅ 正确
${sqlExp(form.param.fieldName, 'AND field_name = ?')}

// ❌ 错误 - 仍有注入风险
${sqlExp(form.param.value, `AND ${form.param.field} = ?`)}
```

### 5.2 性能优化

**避免在循环中使用 SQL 辅助函数**
```javascript
// ❌ 性能差
let conditions = '';
for (let field of form.param.fields) {
    conditions += sqlExp(field.value, `AND ${field.name} = ?`);
}

// ✅ 改用 sqlInExp 或预先处理
```

**合理使用索引**
```javascript
// ✅ 使用前缀匹配（可以用到索引）
${sqlExp(toLikeStrR(form.param.mobile), 'AND mobile LIKE ?')}

// ⚠️ 左侧模糊匹配无法使用索引
${sqlExp(toLikeStrL(form.param.email), 'AND email LIKE ?')}
```

### 5.3 可读性

**使用注释说明复杂逻辑**
```javascript
/**
 * 特殊渠道处理:
 * OFFLINE 渠道包含 OFFLINE/INNER/NULL 三种情况
 */
${iif(
    form.param.channel == 'OFFLINE',
    "AND (channel='OFFLINE' OR channel='INNER' OR channel IS NULL)",
    sqlExp(form.param.channel, 'AND channel = ?')
)}
```

**提取复杂条件为变量**
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

### 5.4 测试

```java
@Test
public void testSqlGeneration() {
    // 准备测试数据
    Map<String, Object> params = new HashMap<>();
    params.put("teamId", "team001");
    params.put("statusList", Arrays.asList(10, 20, 30));

    Map<String, Object> form = Map.of("param", params);

    // 执行脚本
    QueryExpEvaluator evaluator = QueryExpEvaluator.newInstance(applicationContext);
    evaluator.setVar("form", form);

    String sql = (String) exp.evalResult(evaluator);
    List<Object> args = evaluator.getArgs();

    // 验证结果
    assertThat(sql).contains("AND team_id = ?");
    assertThat(sql).contains("AND status IN (?,?,?)");
    assertThat(args).hasSize(4);
    assertThat(args.get(0)).isEqualTo("team001");
}
```

---

## 6. 函数汇总表

| 函数 | 功能 | 用途 |
|------|------|------|
| `sqlExp(value, sql[, force])` | 条件参数 | 单值条件查询 |
| `sqlInExp(array, sql[, force])` | IN 查询 | 多值 IN 查询 |
| `toLikeStr(str)` | 两端模糊 | LIKE '%value%' |
| `toLikeStrL(str)` | 左侧模糊 | LIKE '%value' |
| `toLikeStrR(str)` | 右侧模糊 | LIKE 'value%' |
| `iif(cond, true, false)` | 条件表达式 | 复杂条件拼接 |

---

## 7. 常见问题

**Q: sqlExp 和直接字符串拼接有什么区别?**

A: `sqlExp` 使用 PreparedStatement 参数化查询，防止 SQL 注入；字符串拼接存在注入风险。

**Q: 什么时候需要使用 force 参数?**

A: 当需要明确传递 null 值给数据库时，或在某些特殊业务场景下需要保留条件时使用。

**Q: sqlInExp 能否处理空数组?**

A: 可以。默认情况下空数组返回空字符串（不添加条件）；`force=true` 时返回恒假条件。

**Q: 如何在 FSScript 中使用数据库函数?**

A: 直接在 SQL 片段中使用:
```javascript
${sqlExp(form.param.date, 'AND DATE(create_time) = ?')}
AND YEAR(create_time) = 2024
```

**Q: 是否支持动态表名?**

A: 不建议动态表名，存在安全风险。如必须使用，应通过白名单验证:
```javascript
// Java 端验证
String tableName = validateTableName(params.get("tableName"));
evaluator.setVar("tableName", tableName);

// FSScript 中使用
SELECT * FROM ${tableName} WHERE ...
```
