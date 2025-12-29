# 权限控制

Foggy Dataset Model 提供行级数据访问控制（Row-Level Security），通过 QM 中的 `accesses` 配置实现。

## 1. 概述

权限控制通过 QM（查询模型）中的 `accesses` 配置，使用 `queryBuilder` 动态添加过滤条件，实现行级数据隔离。

---

## 2. 行级权限（Row-Level Security）

### 2.1 基于属性的行级过滤

通过 `accesses` 配置 `queryBuilder`，在查询时动态添加过滤条件：

```javascript
export const queryModel = {
    name: 'FactSalesQueryModel',
    model: 'FactSalesModel',

    accesses: [
        {
            property: 'teamId',
            queryBuilder: (query, property) => {
                const token = getSessionToken();
                query.and('t0.team_id = ?', token.teamId);
            }
        }
    ],

    columnGroups: [...]
};
```

### 2.2 queryBuilder 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `query` | QueryBuilder | 查询构建器，用于添加条件 |
| `property` | string | 当前属性名 |

### 2.3 QueryBuilder 方法

```javascript
query.and(sql, ...params)    // 添加 AND 条件
query.or(sql, ...params)     // 添加 OR 条件
```

**示例**：

```javascript
// 添加单个条件
query.and('t0.team_id = ?', token.teamId);

// 添加多个条件
query.and('t0.status = ? AND t0.region_id = ?', 'ACTIVE', token.regionId);

// 添加复杂条件
query.and('t0.amount > 0 AND t0.team_id = ?', token.teamId);
```

---

## 3. 基于维度的行级过滤

### 3.1 配置方式

除了 `property`，也可以基于维度进行过滤：

```javascript
accesses: [
    {
        dimension: 'team',
        queryBuilder: (query, dimension) => {
            const token = getSessionToken();
            // 使用维度别名（如 d1）
            query.and('d1.team_id = ?', token.teamId);
        }
    }
]
```

### 3.2 父子维度权限

对于父子维度，可以利用闭包表实现层级权限：

```javascript
accesses: [
    {
        dimension: 'team',
        queryBuilder: (query, dimension) => {
            const token = getSessionToken();
            // 查询用户所属团队及所有下级团队的数据
            query.and(`
                EXISTS (
                    SELECT 1 FROM team_closure tc
                    WHERE tc.team_id = d1.team_id
                    AND tc.parent_id = ?
                )
            `, token.teamId);
        }
    }
]
```

---

## 4. 获取用户上下文

### 4.1 通过 Spring Bean 注入

`queryBuilder` 中可以调用 Spring Bean 获取当前用户信息：

```javascript
accesses: [
    {
        property: 'clearingTeamId',
        queryBuilder: (query, property) => {
            // 通过 Spring Bean 获取用户 Token
            const token = getWorkonSessionTokenUsingCache();

            query.and(
                'a1.effective_number > 0 AND a1.clearing_team_id = ?',
                token.loginClearingTeamId
            );
        }
    }
]
```

### 4.2 Spring Bean 注册

在 Spring 配置中注册可供 FSScript 调用的 Bean：

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

        registry.register("getWorkonSessionTokenUsingCache", () -> {
            return sessionTokenService.getTokenFromCache();
        });

        return registry;
    }
}
```

---

## 5. 完整示例

### 5.1 销售数据权限控制

**场景**：销售人员只能查看自己团队的销售数据

```javascript
export const queryModel = {
    name: 'FactSalesQueryModel',
    model: 'FactSalesModel',

    accesses: [
        {
            property: 'salesTeamId',
            queryBuilder: (query, property) => {
                const token = getSessionToken();

                if (token.role === 'ADMIN') {
                    // 管理员无限制
                    return;
                }

                if (token.role === 'MANAGER') {
                    // 经理可查看下属团队
                    query.and(`
                        t0.team_id IN (
                            SELECT team_id FROM team_closure
                            WHERE parent_id = ?
                        )
                    `, token.teamId);
                } else {
                    // 普通员工只能查看自己的数据
                    query.and('t0.salesperson_id = ?', token.userId);
                }
            }
        }
    ],

    columnGroups: [
        {
            caption: '销售信息',
            items: [
                { name: 'salesId' },
                { name: 'customer$caption' },
                { name: 'salesAmount' }
            ]
        },
        {
            caption: '利润数据',
            items: [
                { name: 'profitAmount' },
                { name: 'costAmount' }
            ]
        }
    ]
};
```

### 5.2 多条件权限

```javascript
accesses: [
    {
        property: 'regionId',
        queryBuilder: (query, property) => {
            const token = getSessionToken();
            query.and('t0.region_id = ?', token.regionId);
        }
    },
    {
        property: 'status',
        queryBuilder: (query, property) => {
            // 只显示有效数据
            query.and('t0.status = ?', 'ACTIVE');
        }
    }
]
```

---

## 6. 生成的 SQL 示例

**QM 配置**：

```javascript
accesses: [
    {
        property: 'teamId',
        queryBuilder: (query, property) => {
            const token = getSessionToken();
            query.and('t0.team_id = ?', token.teamId);
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
            { "name": "orderStatus", "type": "=", "value": "COMPLETED" }
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

- `queryBuilder` 中的参数使用 `?` 占位符，自动参数化防止 SQL 注入
- 不要在 `queryBuilder` 中拼接用户输入的字符串

### 7.2 性能

- 权限条件会添加到每个查询中，确保相关列有索引
- 复杂的子查询可能影响性能，建议优化或使用物化视图

### 7.3 表别名

| 别名 | 说明 |
|------|------|
| `t0` | 事实表 |
| `d1`, `d2`, ... | 维度表（按定义顺序） |
| `a1`, `a2`, ... | 其他关联表 |

---

## 8. 使用 Java 控制权限

> 本节内容即将推出...

---

## 下一步

- [QM 语法手册](../tm-qm/qm-syntax.md) - 完整的 QM 配置
- [DSL 查询 API](./query-api.md) - 查询接口参考
- [TM 语法手册](../tm-qm/tm-syntax.md) - 表格模型定义
