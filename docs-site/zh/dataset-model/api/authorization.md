# 行级权限控制

Foggy Dataset Model 通过 `queryBuilder` 在 SQL 生成阶段动态添加过滤条件，实现行级数据隔离（Row-Level Security）。

## 1. 基本语法

```javascript
const fo = loadTableModel('FactOrderModel');

export const queryModel = {
    name: 'FactOrderQueryModel',
    model: fo,

    accesses: [
        {
            property: 'salesTeamId',
            queryBuilder: (query) => {
                const token = getSessionToken();
                // 使用字段引用添加过滤条件
                query.and(fo.teamId, token.teamId);
            }
        }
    ],

    columnGroups: [...]
};
```

## 2. queryBuilder API

### 2.1 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `query` | QueryBuilder | 查询构建器，用于添加过滤条件 |

### 2.2 方法

```javascript
// 使用字段引用（推荐）
query.and(fo.teamId, value)           // 等值条件
query.and(fo.status, 'ACTIVE')        // 固定值条件

// 使用字段名字符串
query.and('teamId', value)            // 等值条件
query.and('status', 'ACTIVE')         // 固定值条件
```

| 方法 | 参数 | 说明 |
|------|------|------|
| `and(field, value)` | field: 字段引用或字段名<br/>value: 过滤值 | 添加 AND 等值条件 |

---

## 3. 配置方式

### 3.1 基于属性过滤

通过 `property` 指定关联的属性字段：

```javascript
const fo = loadTableModel('FactSalesModel');

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

通过 `dimension` 指定关联的维度：

```javascript
const fo = loadTableModel('FactSalesModel');

accesses: [
    {
        dimension: 'customer',
        queryBuilder: (query) => {
            const token = getSessionToken();
            query.and(fo.customer$id, token.customerId);
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

### 4.1 通过 Spring Bean 获取

在 `queryBuilder` 中调用注册的 Spring Bean 获取当前用户信息：

```javascript
accesses: [
    {
        property: 'salesTeamId',
        queryBuilder: (query) => {
            // 调用注册的 Spring Bean 方法
            const token = getSessionToken();

            query.and(fo.teamId, token.teamId);
        }
    }
]
```

### 4.2 注册 Spring Bean

在 Spring 配置中注册可供 FSScript 调用的函数：

```java
@Configuration
public class FsscriptConfig {

    @Bean
    public FsscriptGlobalBeanRegistry fsscriptGlobalBeanRegistry(
            SessionTokenService sessionTokenService) {

        FsscriptGlobalBeanRegistry registry = new FsscriptGlobalBeanRegistry();

        // 注册获取 Token 的函数
        registry.register("getSessionToken", () -> {
            return sessionTokenService.getCurrentToken();
        });

        return registry;
    }
}
```

---

## 5. 完整示例

### 5.1 按角色分级权限

**场景**：管理员无限制，经理可查看下属团队，员工只能查看自己的数据

```javascript
const fo = loadTableModel('FactSalesModel');

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
            query.and(fo.status, 'ACTIVE');
        }
    }
]
```

### 5.3 基于维度的权限控制

```javascript
const fo = loadTableModel('FactSalesModel');

accesses: [
    {
        dimension: 'customer',
        queryBuilder: (query) => {
            const token = getSessionToken();
            // 只能查看自己负责的客户数据
            query.and(fo.customer$id, token.customerId);
        }
    }
]
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
  AND t0.team_id = '用户所属团队ID'  -- 权限条件自动注入
```

---

## 7. 注意事项

### 7.1 安全性

- `queryBuilder` 使用参数化查询，自动防止 SQL 注入
- 不要在 `queryBuilder` 中拼接用户输入的字符串
- 使用字段引用（如 `fo.teamId`）确保类型安全

### 7.2 性能

- 权限条件会添加到每个查询中，确保相关列有索引
- 避免在 `queryBuilder` 中执行耗时操作

### 7.3 字段引用 vs 字段名字符串

| 方式 | 优点 | 缺点 |
|------|------|------|
| 字段引用（推荐） | 类型安全、IDE 支持、重构友好 | 仅支持等值条件 |
| 字段名字符串 | 简单直接 | 无类型检查 |

> **推荐**：优先使用字段引用方式，确保代码的可维护性。

---

## 下一步

- [QM 语法手册](../tm-qm/qm-syntax.md) - 完整的 QM 配置
- [DSL 查询 API](./query-api.md) - 查询接口参考
- [JSON 查询 DSL](../tm-qm/query-dsl.md) - DSL 完整语法
