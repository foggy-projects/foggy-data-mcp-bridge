# 行级权限控制（QM 文件）

Foggy Dataset Model 通过 `queryBuilder` 在 SQL 生成阶段动态添加过滤条件，实现行级数据隔离（Row-Level Security）。

> **提示**：本文档介绍在 QM 文件中声明式定义权限。如需更灵活的权限控制（如列级权限、数据脱敏、复杂业务逻辑），请参考 [Java 编程式权限控制](./java-authorization.md)。

## 1. 基本语法

```javascript
const fo = loadTableModel('FactOrderModel');
import { getSessionToken } from '@sessionTokenService';

export const queryModel = {
    name: 'FactOrderQueryModel',
    model: fo,

    accesses: [
        {
            queryBuilder: (context) => {
                const query = context.query;
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

`queryBuilder` 函数只接受一个 `context` 参数，包含所有需要的上下文信息：

```javascript
queryBuilder: (context) => {
    const query = context.query;
    // ...
}
```

**context 可用属性**：

| 属性 | 类型 | 说明 |
|------|------|------|
| `context.query` | JdbcQuery | 查询构建器 |
| `context.queryModel` | QueryModel | 查询模型 |
| `context.securityContext` | SecurityContext | 安全上下文（用户信息） |
| `context.request` | PagingRequest | 原始请求对象 |

```javascript
// 访问查询请求
context.request.param          // 当前查询请求对象
context.request.param.extData  // 前端传入的扩展数据

// 访问安全上下文
context.securityContext?.userId    // 当前用户ID
context.securityContext?.tenantId  // 租户ID
context.securityContext?.roles     // 用户角色列表
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

queryBuilder: (context) => {
    const query = context.query;
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

**获取表别名**：

- 主表别名：使用 `fo.$alias` 获取（如 `"t0"`）
- 维度表别名：使用 `context.queryModel.getDimensionAlias('维度名')` 获取（如 `"d1"`）

```javascript
queryBuilder: (context) => {
    const query = context.query;
    const token = getSessionToken();

    // 获取主表别名
    const t = fo.$alias;

    // 获取维度表别名（便捷方法）
    const d = context.queryModel.getDimensionAlias('store');

    // 原生 SQL（无参数）
    query.andSql(t + '.state not in (60, 70)');

    // 原生 SQL（单参数）
    query.andSql(t + '.team_id = ?', token.teamId);

    // 维度表条件
    query.andSql(d + '.store_type = ?', '直营店');
}
```

> **注意**：原生 SQL 中使用的是**数据库列名**（如 `team_id`），不是模型字段名（如 `teamId`）。

---

### 2.4 复杂子查询示例

```javascript
accesses: [
    {
        queryBuilder: (context) => {
            const query = context.query;
            const token = getSessionToken();
            const extData = context.request?.param?.extData;
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

## 3. 配置说明

`accesses` 数组中的每个元素只需要包含 `queryBuilder` 函数：

```javascript
accesses: [
    {
        queryBuilder: (context) => {
            // 权限过滤逻辑
        }
    },
    {
        queryBuilder: (context) => {
            // 另一个权限过滤逻辑
        }
    }
]
```

由于使用了 `loadTableModel` 的字段引用机制（如 `fo.teamId`），不再需要显式指定 `property` 或 `dimension`。字段引用会自动处理表别名和 JOIN 逻辑。

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
            queryBuilder: (context) => {
                const query = context.query;
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
            queryBuilder: (context) => {
                const query = context.query;
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
        queryBuilder: (context) => {
            const query = context.query;
            const token = getSessionToken();
            query.and(fo.regionId, token.regionId);
        }
    },
    {
        queryBuilder: (context) => {
            const query = context.query;
            // 只显示有效数据
            query.andNe(fo.status, 'DELETED');
        }
    }
]
```

### 5.3 多表关联查询

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
            queryBuilder: (context) => {
                const query = context.query;
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
        queryBuilder: (context) => {
            const query = context.query;
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

- [Java 编程式权限控制](./java-authorization.md) - 通过 Java 代码动态控制权限
- [QM 语法手册](../tm-qm/qm-syntax.md) - 完整的 QM 配置
- [DSL 查询 API](./query-api.md) - 查询接口参考
- [JSON 查询 DSL](../tm-qm/query-dsl.md) - DSL 完整语法
