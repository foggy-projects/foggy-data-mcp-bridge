# 行级权限控制

Foggy Dataset Model 通过 `queryBuilder` 在 SQL 生成阶段动态添加过滤条件，实现行级数据隔离（Row-Level Security）。

## 1. 基本语法

```javascript
const fo = loadTableModel('FactOrderModel');
import { getSessionToken } from '@sessionTokenService';

export const queryModel = {
    name: 'FactOrderQueryModel',
    model: fo,

    accesses: [
        {
            property: 'salesTeamId',
            queryBuilder: (query) => {
                const token = getSessionToken();
                // 使用字段引用（推荐）
                query.and(fo.salesTeamId, token.teamId);
            }
        }
    ],

    columnGroups: [...]
};
```

## 2. queryBuilder API

### 2.1 函数签名与参数

`queryBuilder` 函数的参数根据配置方式不同：

**基于属性过滤**：
```javascript
queryBuilder: (query) => { ... }
```

**基于维度过滤**：
```javascript
queryBuilder: (query, dimension) => { ... }
```

**可用的上下文变量**：

| 变量 | 类型 | 说明 |
|------|------|------|
| `query` | JdbcQuery | 查询构建器（参数传入） |
| `query.queryModel` | QueryModel | 查询模型（通过 query 访问） |
| `dimension` | DbDimension | 关联的维度（参数传入，仅维度过滤时） |
| `property` | DbProperty | 关联的属性（参数传入，仅属性过滤时） |

```javascript
// 访问查询请求
query.queryRequest          // 当前查询请求对象
query.queryRequest.extData  // 前端传入的扩展数据
```

### 2.2 字段引用方法（推荐）

使用字段引用可以避免手写 SQL 和表别名：

| 方法 | 说明 | 示例 |
|------|------|------|
| `and(ref, value)` | 等于条件 | `query.and(fo.teamId, 'T001')` |
| `andIn(ref, values)` | IN 条件 | `query.andIn(fo.status, ['A', 'B'])` |
| `andNe(ref, value)` | 不等于条件 | `query.andNe(fo.status, 'DELETED')` |
| `andNotNull(ref)` | 非空条件 | `query.andNotNull(fo.teamId)` |
| `andNull(ref)` | 为空条件 | `query.andNull(fo.deletedAt)` |

**示例**：

```javascript
const fo = loadTableModel('FactOrderModel');

queryBuilder: (query) => {
    const token = getSessionToken();

    // 等于条件：自动生成 t0.team_id = ?
    query.and(fo.teamId, token.teamId);

    // IN 条件：自动生成 t0.status in (?, ?)
    query.andIn(fo.status, ['ACTIVE', 'PENDING']);

    // 不等于条件
    query.andNe(fo.orderStatus, 'CANCELLED');
}
```

### 2.3 原生 SQL 方法

需要复杂条件时，使用原生 SQL 方法：

| 方法 | 说明 |
|------|------|
| `andSql(sql)` | 原生 SQL 片段 |
| `andSql(sql, value)` | SQL + 单个参数 |
| `andSqlList(sql, values)` | SQL + 参数数组 |

**获取表别名**：使用 `fo.$alias` 获取表别名（如 `"t0"`）

```javascript
queryBuilder: (query) => {
    const t = fo.$alias;

    // 原生 SQL（无参数）
    query.andSql(t + '.state not in (60, 70)');

    // 原生 SQL（单参数）
    query.andSql(t + '.team_id = ?', token.teamId);

    // 原生 SQL（多参数）
    query.andSqlList(t + '.region_id = ? and ' + t + '.status = ?', [regionId, 'ACTIVE']);
}
```

> **注意**：原生 SQL 中使用的是**数据库列名**（如 `team_id`），不是模型字段名（如 `teamId`）。

---

### 2.4 复杂子查询示例

```javascript
accesses: [
    {
        property: 'customerId',
        queryBuilder: (query) => {
            const token = getSessionToken();
            const extData = query?.queryRequest?.extData;
            const t = fo.$alias;

            // 基础条件（使用字段引用）
            query.and(fo.clearingTeamId, token.clearingTeamId);

            // 动态子查询（使用原生 SQL）
            if (extData?.userName || extData?.userTel) {
                let subQuery = t + `.tms_customer_id in (
                    select tms_customer_id from basic.tms_user
                    where clearing_team_id = ?`;
                const params = [token.clearingTeamId];

                if (extData.userName) {
                    subQuery += ' and tms_user_name = ?';
                    params.push(extData.userName);
                }
                if (extData.userTel) {
                    subQuery += ' and tms_user_tel = ?';
                    params.push(extData.userTel);
                }
                subQuery += ')';

                query.andSqlList(subQuery, params);
            }
        }
    }
]
```

---

## 3. 配置方式

### 3.1 基于属性过滤

通过 `property` 指定关联的属性字段：

```javascript
const fo = loadTableModel('FactOrderModel');

accesses: [
    {
        property: 'salesTeamId',
        queryBuilder: (query) => {
            const token = getSessionToken();
            query.and(fo.teamId, token.teamId);
        }
    }
]
```

### 3.2 基于维度过滤

通过 `dimension` 指定关联的维度。此时 `dimension` 需要作为参数显式传入：

```javascript
accesses: [
    {
        dimension: 'customer',
        queryBuilder: (query, dimension) => {
            const token = getSessionToken();
            // 获取维度表别名（通过 query.queryModel 访问）
            const d = query.queryModel.getAlias(dimension.queryObject);
            query.andSql(d + '.customer_id = ?', token.customerId);
        }
    }
]
```

### 3.3 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `dimension` | string | 二选一 | 关联的维度名称 |
| `property` | string | 二选一 | 关联的属性名称 |
| `queryBuilder` | function | 是 | 构建权限过滤条件的函数 |

> **注意**：`dimension` 和 `property` 必须二选一。

---

## 4. 获取用户上下文

### 4.1 通过 import 语法获取

使用 ES6 风格的 import 语法从 Spring Bean 获取当前用户信息：

```javascript
const fo = loadTableModel('FactOrderModel');
import { getSessionToken } from '@sessionTokenService';

export const queryModel = {
    name: 'FactOrderQueryModel',
    model: fo,

    accesses: [
        {
            property: 'salesTeamId',
            queryBuilder: (query) => {
                const token = getSessionToken();
                query.and(fo.teamId, token.teamId);
            }
        }
    ],

    columnGroups: [...]
};
```

### 4.2 Spring Bean 配置

确保 Spring Bean 提供了可调用的方法：

```java
@Service
public class SessionTokenService {

    public SessionToken getSessionToken() {
        // 从 SecurityContext 或其他来源获取当前用户信息
        return SecurityContextHolder.getContext().getSessionToken();
    }
}
```

**import 语法说明**：

| 语法 | 说明 |
|------|------|
| `import { methodName } from '@beanName'` | 从 Spring Bean 导入方法 |
| `@beanName` | Bean 名称（首字母小写的类名） |

> **注意**：`@beanName` 对应 Spring 容器中的 Bean 名称，默认为首字母小写的类名（如 `SessionTokenService` → `@sessionTokenService`）。

---

## 5. 完整示例

### 5.1 按角色分级权限

**场景**：管理员无限制，经理可查看下属团队，员工只能查看自己的数据

```javascript
const fo = loadTableModel('FactSalesModel');
import { getSessionToken } from '@sessionTokenService';

export const queryModel = {
    name: 'FactSalesQueryModel',
    model: fo,

    accesses: [
        {
            property: 'salesTeamId',
            queryBuilder: (query) => {
                const token = getSessionToken();

                if (token.role === 'ADMIN') {
                    // 管理员无限制
                    return;
                }

                if (token.role === 'MANAGER') {
                    // 经理可查看自己团队的数据
                    query.and(fo.teamId, token.teamId);
                } else {
                    // 普通员工只能查看自己的数据
                    query.and(fo.salespersonId, token.userId);
                }
            }
        }
    ],

    columnGroups: [
        {
            caption: '销售信息',
            items: [
                { ref: fo.salesId },
                { ref: fo.customer },
                { ref: fo.salesAmount }
            ]
        }
    ]
};
```

### 5.2 多条件组合

```javascript
const fo = loadTableModel('FactOrderModel');

accesses: [
    {
        property: 'regionId',
        queryBuilder: (query) => {
            const token = getSessionToken();
            query.and(fo.regionId, token.regionId);
        }
    },
    {
        property: 'status',
        queryBuilder: (query) => {
            // 只显示有效数据
            query.andNe(fo.status, 'DELETED');
        }
    }
]
```

### 5.3 基于维度的权限控制

```javascript
accesses: [
    {
        dimension: 'customer',
        queryBuilder: (query, dimension) => {
            const token = getSessionToken();
            // 使用 query.queryModel 获取维度表别名
            const d = query.queryModel.getAlias(dimension.queryObject);
            // 只能查看自己负责的客户数据
            query.andSql(d + '.customer_id = ?', token.customerId);
        }
    }
]
```

### 5.4 多表关联查询

```javascript
const fs = loadTableModel('FactSalesModel');
const fr = loadTableModel('FactReturnModel');

export const queryModel = {
    name: 'SalesReturnJoinQueryModel',
    model: fs,
    joins: [
        fs.leftJoin(fr).on(fs.orderId, fr.orderId)
    ],

    accesses: [
        {
            property: 'salesTeamId',
            queryBuilder: (query) => {
                const token = getSessionToken();
                // 使用字段引用（自动解析表别名）
                query.and(fs.teamId, token.teamId);
                query.andNe(fr.returnStatus, 'REJECTED');
            }
        }
    ],

    columnGroups: [...]
};
```

---

## 6. 生成的 SQL 示例

**QM 配置**：

```javascript
const fo = loadTableModel('FactOrderModel');

accesses: [
    {
        property: 'teamId',
        queryBuilder: (query) => {
            const token = getSessionToken();
            query.and(fo.teamId, token.teamId);
        }
    }
]
```

**DSL 查询**：

```json
{
    "param": {
        "columns": ["orderId", "customer$caption", "totalAmount"],
        "slice": [
            { "field": "orderStatus", "op": "=", "value": "COMPLETED" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT
    t0.order_id AS orderId,
    d1.customer_name AS "customer$caption",
    t0.total_amount AS totalAmount
FROM fact_order t0
LEFT JOIN dim_customer d1 ON t0.customer_id = d1.customer_id
WHERE t0.order_status = 'COMPLETED'
  AND t0.team_id = ?  -- 权限条件自动注入（参数化）
```

---

## 7. 注意事项

### 7.1 安全性

- 使用 `?` 占位符进行参数化查询，自动防止 SQL 注入
- 不要在 SQL 中直接拼接用户输入的字符串
- 字段引用方法自动处理参数化

### 7.2 性能

- 权限条件会添加到每个查询中，确保相关列有索引
- 避免在 `queryBuilder` 中执行耗时操作

### 7.3 API 选择指南

| 场景 | 推荐方法 | 示例 |
|------|---------|------|
| 简单相等条件 | `and(ref, value)` | `query.and(fo.teamId, value)` |
| IN 条件 | `andIn(ref, values)` | `query.andIn(fo.status, list)` |
| 不等于条件 | `andNe(ref, value)` | `query.andNe(fo.status, 'X')` |
| 复杂条件/子查询 | `andSql()` | `query.andSql(sql, value)` |
| 需要表别名 | `fo.$alias` | `fo.$alias + '.column'` |

---

## 下一步

- [QM 语法手册](../tm-qm/qm-syntax.md) - 完整的 QM 配置
- [DSL 查询 API](./query-api.md) - 查询接口参考
- [JSON 查询 DSL](../tm-qm/query-dsl.md) - DSL 完整语法
