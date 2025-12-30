# 父子维度

父子维度（Parent-Child Dimension）用于处理层级结构数据，如组织架构、商品分类、地区等。

## 1. 什么是父子维度

父子维度是一种自引用的层级结构，每个成员可以有一个父成员和多个子成员。

**典型应用场景**：

- **组织架构**：公司 → 部门 → 团队 → 小组
- **商品分类**：大类 → 中类 → 小类
- **地理区域**：国家 → 省份 → 城市 → 区县
- **菜单权限**：系统 → 模块 → 页面 → 功能

## 2. 闭包表模式

Foggy Dataset Model 使用**闭包表**（Closure Table）存储层级关系，预存所有祖先-后代关系，实现高效查询。

**优势**：
- 查询任意层级的祖先/后代只需一次简单查询
- 无需递归查询，性能更好
- 支持任意深度的层级结构

---

## 3. 数据表结构

### 3.1 维度表

存储维度成员的基本信息：

```sql
CREATE TABLE dim_team (
    team_id VARCHAR(64) PRIMARY KEY,
    team_name VARCHAR(100) NOT NULL,
    parent_id VARCHAR(64),
    level INT,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);
```

### 3.2 闭包表

存储所有祖先-后代关系：

```sql
CREATE TABLE team_closure (
    parent_id VARCHAR(64) NOT NULL,  -- 祖先 ID
    team_id VARCHAR(64) NOT NULL,    -- 后代 ID
    distance INT DEFAULT 0,          -- 距离（0 表示自身）
    PRIMARY KEY (parent_id, team_id)
);

-- 建议索引
CREATE INDEX idx_team_closure_parent ON team_closure (parent_id);
CREATE INDEX idx_team_closure_child ON team_closure (team_id);
```

### 3.3 示例数据

以下示例数据来自 `foggy-dataset-demo` 项目，用于演示和测试。

#### 组织架构

```
总公司 (T001)
├── 技术部 (T002)
│   ├── 研发组 (T003)
│   │   ├── 前端小组 (T006)
│   │   └── 后端小组 (T007)
│   └── 测试组 (T004)
└── 销售部 (T005)
    ├── 华东区 (T008)
    └── 华北区 (T009)
```

#### 团队维度表 (dim_team)

| team_id | team_name | parent_id | team_level | manager_name |
|---------|-----------|-----------|------------|--------------|
| T001    | 总公司     | NULL      | 1          | 张总          |
| T002    | 技术部     | T001      | 2          | 李经理        |
| T003    | 研发组     | T002      | 3          | 王组长        |
| T004    | 测试组     | T002      | 3          | 赵组长        |
| T005    | 销售部     | T001      | 2          | 钱经理        |
| T006    | 前端小组   | T003      | 4          | 孙组长        |
| T007    | 后端小组   | T003      | 4          | 周组长        |
| T008    | 华东区     | T005      | 3          | 吴经理        |
| T009    | 华北区     | T005      | 3          | 郑经理        |

#### 闭包表 (team_closure)

| parent_id | team_id | distance | 说明 |
|-----------|---------|----------|------|
| T001      | T001    | 0        | 自身 |
| T001      | T002    | 1        | 总公司 → 技术部 |
| T001      | T003    | 2        | 总公司 → 技术部 → 研发组 |
| T001      | T004    | 2        | 总公司 → 技术部 → 测试组 |
| T001      | T005    | 1        | 总公司 → 销售部 |
| T001      | T006    | 3        | 总公司 → 技术部 → 研发组 → 前端小组 |
| T001      | T007    | 3        | 总公司 → 技术部 → 研发组 → 后端小组 |
| T001      | T008    | 2        | 总公司 → 销售部 → 华东区 |
| T001      | T009    | 2        | 总公司 → 销售部 → 华北区 |
| T002      | T002    | 0        | 自身 |
| T002      | T003    | 1        | 技术部 → 研发组 |
| T002      | T004    | 1        | 技术部 → 测试组 |
| T002      | T006    | 2        | 技术部 → 研发组 → 前端小组 |
| T002      | T007    | 2        | 技术部 → 研发组 → 后端小组 |
| T003      | T003    | 0        | 自身 |
| T003      | T006    | 1        | 研发组 → 前端小组 |
| T003      | T007    | 1        | 研发组 → 后端小组 |
| T004      | T004    | 0        | 自身 |
| T005      | T005    | 0        | 自身 |
| T005      | T008    | 1        | 销售部 → 华东区 |
| T005      | T009    | 1        | 销售部 → 华北区 |
| T006      | T006    | 0        | 自身 |
| T007      | T007    | 0        | 自身 |
| T008      | T008    | 0        | 自身 |
| T009      | T009    | 0        | 自身 |

> **共 25 条记录**：9 条自身记录 + 16 条祖先-后代关系

#### 销售事实表 (fact_team_sales)

| team_id | date_key | sales_amount | sales_count |
|---------|----------|--------------|-------------|
| T001    | 20240101 | 50,000       | 5           |
| T001    | 20240102 | 60,000       | 6           |
| T002    | 20240101 | 30,000       | 3           |
| T002    | 20240102 | 35,000       | 4           |
| T003    | 20240101 | 10,000       | 2           |
| T003    | 20240102 | 12,000       | 2           |
| T004    | 20240101 | 8,000        | 1           |
| T004    | 20240102 | 9,000        | 1           |
| T005    | 20240101 | 100,000      | 20          |
| T005    | 20240102 | 120,000      | 25          |
| T006    | 20240101 | 5,000        | 1           |
| T006    | 20240102 | 6,000        | 1           |
| T007    | 20240101 | 7,000        | 1           |
| T007    | 20240102 | 8,000        | 2           |
| T008    | 20240101 | 45,000       | 10          |
| T008    | 20240102 | 55,000       | 12          |
| T009    | 20240101 | 40,000       | 8           |
| T009    | 20240102 | 48,000       | 10          |

> **共 18 条记录**：9 个团队 × 2 天

#### 汇总数据参考

| 团队 | 自身销售额 | 含下属销售额（层级汇总） |
|------|-----------|------------------------|
| T001 总公司 | 110,000 | 610,000（全公司） |
| T002 技术部 | 65,000 | 130,000（含研发组、测试组、前端/后端小组） |
| T003 研发组 | 22,000 | 48,000（含前端/后端小组） |
| T005 销售部 | 220,000 | 408,000（含华东区、华北区） |

---

## 4. TM 模型配置

```javascript
export const model = {
    name: 'FactTeamSalesModel',
    caption: '团队销售事实表',
    tableName: 'fact_team_sales',
    idColumn: 'sales_id',

    dimensions: [
        {
            name: 'team',
            tableName: 'dim_team',
            foreignKey: 'team_id',
            primaryKey: 'team_id',
            captionColumn: 'team_name',
            caption: '团队',

            // === 父子维度配置 ===
            closureTableName: 'team_closure',  // 闭包表名（必填）
            parentKey: 'parent_id',            // 闭包表祖先列（必填）
            childKey: 'team_id',               // 闭包表后代列（必填）

            properties: [
                { column: 'team_id', caption: '团队ID' },
                { column: 'team_name', caption: '团队名称' },
                { column: 'parent_id', caption: '上级团队' },
                { column: 'level', caption: '层级', alias: 'teamLevel' }
            ]
        }
    ],

    properties: [...],
    measures: [...]
};
```

### 4.1 配置字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `closureTableName` | string | 是 | 闭包表名称 |
| `closureTableSchema` | string | 否 | 闭包表 Schema |
| `parentKey` | string | 是 | 闭包表中的祖先列 |
| `childKey` | string | 是 | 闭包表中的后代列 |

---

## 5. 两种访问视角

父子维度提供**两种访问视角**，语义清晰，行为一致：

| 视角 | 列名格式 | 行为 | 用途 |
|------|----------|------|------|
| **默认视角** | `team$id`, `team$caption` | 与普通维度相同，精确匹配 | 精确查询、明细展示 |
| **层级视角** | `team$hierarchy$id`, `team$hierarchy$caption` | 使用闭包表，匹配节点及所有后代 | 层级汇总、后代范围筛选 |

**设计原则**：
- **默认 = 普通维度**：行为与非父子维完全一致，无"魔法"
- **层级 = 显式请求**：用户明确使用 `$hierarchy$` 才启用闭包表

---

### 5.1 默认视角（普通维度行为）

使用 `team$id`、`team$caption` 等列，行为与普通维度完全相同。

**示例**：只查 T001 自身的销售数据

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "=", "value": "T001" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name, SUM(t0.sales_amount)
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
WHERE d1.team_id = 'T001'
GROUP BY d1.team_name
```

**返回数据**（只包含 T001 自身的销售）：

| team$caption | salesAmount |
|--------------|-------------|
| 总公司        | 110,000     |

---

### 5.2 层级视角（使用闭包表）

使用 `team$hierarchy$id`、`team$hierarchy$caption` 等列，启用闭包表进行层级操作。

#### 场景 1：层级汇总（汇总到祖先节点）

```json
{
    "param": {
        "columns": ["team$hierarchy$caption", "salesAmount"],
        "slice": [
            { "field": "team$hierarchy$id", "op": "=", "value": "T001" }
        ],
        "groupBy": [
            { "field": "team$hierarchy$caption" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d4.team_name AS "team$hierarchy$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
LEFT JOIN dim_team d4 ON d2.parent_id = d4.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d4.team_name
```

**返回数据**：

| team$hierarchy$caption | salesAmount |
|------------------------|-------------|
| 总公司                  | 648,000     |

**说明**：T001 及其所有后代（T002-T009）的销售数据汇总显示为"总公司"。

#### 场景 2：后代明细（分别显示各后代）

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$hierarchy$id", "op": "=", "value": "T001" }
        ],
        "groupBy": [
            { "field": "team$caption" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d1.team_name
```

**返回数据**（9 条记录，每个后代一条）：

| team$caption | salesAmount |
|--------------|-------------|
| 总公司        | 110,000     |
| 技术部        | 65,000      |
| 研发组        | 22,000      |
| 测试组        | 17,000      |
| 销售部        | 220,000     |
| 前端小组      | 11,000      |
| 后端小组      | 15,000      |
| 华东区        | 100,000     |
| 华北区        | 88,000      |

**说明**：使用 `team$hierarchy$id` 过滤后代范围，但用默认的 `team$caption` 分组显示各团队明细。

---

### 5.3 视角对比总结

假设 T001（总公司）有 9 个团队（包括自身），各团队都有销售数据：

| 查询方式 | slice | groupBy | 返回记录数 | 说明 |
|----------|-------|---------|------------|------|
| 精确匹配 | `team$id = T001` | `team$caption` | 1 条 | 只查 T001 自身 |
| 层级汇总 | `team$hierarchy$id = T001` | `team$hierarchy$caption` | 1 条 | 汇总到 T001 |
| 后代明细 | `team$hierarchy$id = T001` | `team$caption` | 9 条 | 各后代分别显示 |

---

### 5.4 层级操作符

除了 `$hierarchy$` 视角，还支持通过 `op` 操作符进行细粒度层级查询，无需使用 `$hierarchy$` 列名：

| op | 含义 | SQL 条件 | 包含自身 |
|----|------|----------|----------|
| `childrenOf` | 直接子节点 | `distance = 1` | 否 |
| `descendantsOf` | 所有后代 | `distance > 0` | 否 |
| `selfAndDescendantsOf` | 自身及所有后代 | 无限制 | 是 |

支持 `maxDepth` 参数限制查询深度。

---

#### 5.4.1 childrenOf - 查询直接子节点

查询指定节点的直接子节点（distance = 1）。

**请求**：

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "childrenOf", "value": "T001" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001' AND d2.distance = 1
GROUP BY d1.team_name
```

**返回数据**（T001 的直接子部门）：

| team$caption | salesAmount |
|--------------|-------------|
| 技术部        | 65,000      |
| 销售部        | 220,000     |

---

#### 5.4.2 descendantsOf - 查询所有后代

查询指定节点的所有后代，不包含自身（distance > 0）。

**请求**：

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "descendantsOf", "value": "T001" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001' AND d2.distance > 0
GROUP BY d1.team_name
```

**返回数据**（T001 的所有后代，不含 T001）：

| team$caption | salesAmount |
|--------------|-------------|
| 技术部        | 65,000      |
| 研发组        | 22,000      |
| 测试组        | 17,000      |
| 销售部        | 220,000     |
| 前端小组      | 11,000      |
| 后端小组      | 15,000      |
| 华东区        | 100,000     |
| 华北区        | 88,000      |

---

#### 5.4.3 selfAndDescendantsOf - 查询自身及所有后代

查询指定节点及其所有后代（等效于 `$hierarchy$` 视角）。

**请求**：

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "selfAndDescendantsOf", "value": "T001" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d1.team_name
```

**返回数据**（T001 及所有后代）：

| team$caption | salesAmount |
|--------------|-------------|
| 总公司        | 110,000     |
| 技术部        | 65,000      |
| 研发组        | 22,000      |
| 测试组        | 17,000      |
| 销售部        | 220,000     |
| 前端小组      | 11,000      |
| 后端小组      | 15,000      |
| 华东区        | 100,000     |
| 华北区        | 88,000      |

---

#### 5.4.4 maxDepth - 限制查询深度

使用 `maxDepth` 参数限制层级查询的深度。

**示例 1**：查询 T001 的 2 级以内后代

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "descendantsOf", "value": "T001", "maxDepth": 2 }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001' AND d2.distance BETWEEN 1 AND 2
GROUP BY d1.team_name
```

**返回数据**（T001 的 2 级以内后代，即子和孙）：

| team$caption | salesAmount |
|--------------|-------------|
| 技术部        | 65,000      |
| 研发组        | 22,000      |
| 测试组        | 17,000      |
| 销售部        | 220,000     |
| 华东区        | 100,000     |
| 华北区        | 88,000      |

> 注：不包含 T006（前端小组）和 T007（后端小组），因为它们距离 T001 是 3 级。

---

**示例 2**：childrenOf + maxDepth（扩展子节点范围）

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "childrenOf", "value": "T002", "maxDepth": 2 }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T002' AND d2.distance BETWEEN 1 AND 2
GROUP BY d1.team_name
```

**返回数据**（T002 的 2 级以内子节点）：

| team$caption | salesAmount |
|--------------|-------------|
| 研发组        | 22,000      |
| 测试组        | 17,000      |
| 前端小组      | 11,000      |
| 后端小组      | 15,000      |

---

#### 5.4.5 多值查询

层级操作符支持传入多个值，查询多个节点的后代。

**请求**：

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "childrenOf", "value": ["T002", "T005"] }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id IN ('T002', 'T005') AND d2.distance = 1
GROUP BY d1.team_name
```

**返回数据**（T002 和 T005 的直接子节点）：

| team$caption | salesAmount |
|--------------|-------------|
| 研发组        | 22,000      |
| 测试组        | 17,000      |
| 华东区        | 100,000     |
| 华北区        | 88,000      |

---

#### 5.4.6 操作符对比表

| 查询需求 | 推荐方式 | 说明 |
|----------|----------|------|
| 精确匹配某节点 | `team$id = 'T001'` | 默认视角 |
| 汇总到某节点 | `team$hierarchy$id` + `team$hierarchy$caption` | 层级视角 |
| 各后代明细 | `team$hierarchy$id` + `team$caption` | 混合视角 |
| 直接子节点 | `op: childrenOf` | 层级操作符 |
| 所有后代（不含自身） | `op: descendantsOf` | 层级操作符 |
| 限定深度查询 | `op` + `maxDepth` | 层级操作符 |

---

## 6. 闭包表维护

### 6.1 新增节点

```sql
-- 添加新团队 T010（隶属于研发组 T003）
INSERT INTO dim_team VALUES ('T010', '后端小组', 'T003', 4, 'ACTIVE');

-- 插入自身关系
INSERT INTO team_closure (parent_id, team_id, distance)
VALUES ('T010', 'T010', 0);

-- 插入所有祖先到新节点的关系
INSERT INTO team_closure (parent_id, team_id, distance)
SELECT parent_id, 'T010', distance + 1
FROM team_closure
WHERE team_id = 'T003';
```

### 6.2 删除节点

```sql
DELETE FROM team_closure WHERE team_id = 'T010' OR parent_id = 'T010';
DELETE FROM dim_team WHERE team_id = 'T010';
```

---

## 7. 与普通维度的区别

| 特性 | 普通维度 | 父子维度 |
|------|----------|----------|
| 层级支持 | 固定层级（如年-月-日） | 任意深度动态层级 |
| 关联方式 | 直接外键关联 | 支持闭包表关联 |
| 查询行为 | 精确匹配 | 默认精确匹配，`$hierarchy$` 启用层级操作 |
| 数据结构 | 单表 | 维度表 + 闭包表 |
| 可用列 | `dim$id`, `dim$caption` | 额外支持 `$hierarchy$` 视角 |
| 维护复杂度 | 低 | 中等 |

---

## 8. 最佳实践

1. **索引优化**：在闭包表的 `parent_id` 和 `team_id` 列建立索引
2. **数据一致性**：使用事务确保维度表和闭包表的一致性
3. **层级深度**：建议控制层级深度，过深会影响性能
4. **distance 字段**：虽非必需，但有助于查询特定层级的数据
5. **视角选择**：
   - 精确匹配某节点 → 使用默认视角 `team$id`
   - 需要汇总到某节点 → 使用 `team$hierarchy$id` + `team$hierarchy$caption`
   - 需要查看后代明细 → 使用 `team$hierarchy$id` + `team$caption`

---

## 下一步

- [TM 语法手册](./tm-syntax.md) - 完整的 TM 定义语法
- [QM 语法手册](./qm-syntax.md) - 查询模型定义
- [DSL 查询 API](../api/query-api.md) - 查询 API 参考
