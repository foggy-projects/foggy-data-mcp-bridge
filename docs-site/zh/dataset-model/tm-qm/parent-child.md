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

### 3.3 闭包表数据示例

```
组织结构：
总公司 (T001)
├── 技术部 (T002)
│   ├── 研发组 (T003)
│   │   └── 前端小组 (T005)
│   └── 测试组 (T006)
└── 销售部 (T004)
    ├── 华东区 (T007)
    └── 华南区 (T008)
        └── 深圳办 (T009)
```

| parent_id | team_id | distance |
|-----------|---------|----------|
| T001      | T001    | 0        |
| T001      | T002    | 1        |
| T001      | T003    | 2        |
| T001      | T004    | 1        |
| T001      | T005    | 3        |
| T001      | T006    | 2        |
| T001      | T007    | 2        |
| T001      | T008    | 2        |
| T001      | T009    | 3        |
| T002      | T002    | 0        |
| T002      | T003    | 1        |
| T002      | T005    | 2        |
| T002      | T006    | 1        |
| ...       | ...     | ...      |

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

## 5. 三种访问视角

父子维度提供**三种访问视角**，满足不同的查询需求：

| 视角 | 列名格式 | JOIN 路径 | 用途 |
|------|----------|-----------|------|
| **默认视角** | `team$id`, `team$caption` | `fact.team_id → dim_team` | 直接关联，保持原有行为 |
| **层级汇总视角** | `team$hierarchy$id`, `team$hierarchy$caption` | `closure.parent_id → dim_team` | 层级汇总查询 |
| **明细视角** | `team$self$id`, `team$self$caption` | `fact.team_id → dim_team (别名)` | 后代明细查询 |

### 5.1 默认视角

使用 `team$id`、`team$caption` 等列，通过 `fact.team_id` 直接关联维度表。

**行为**：与普通维度相同，直接匹配。

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
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d1.team_name
```

**说明**：`slice` 条件使用闭包表过滤，返回 T001 及其所有后代的销售记录。`team$caption` 显示每条记录实际所属团队的名称。

---

### 5.2 层级汇总视角

使用 `team$hierarchy$id`、`team$hierarchy$caption` 等列，通过 `closure.parent_id` 关联维度表。

**用途**：将后代数据汇总到祖先节点显示。

**示例**：查询 T001（总公司）及其所有后代的销售，**汇总显示为总公司**：

```json
{
    "param": {
        "columns": ["team$hierarchy$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "=", "value": "T001" }
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

**结果**：返回 **1 条记录**

| team$hierarchy$caption | salesAmount |
|------------------------|-------------|
| 总公司                  | 1000000     |

**说明**：所有后代（T002-T009）的销售数据都汇总到 T001（总公司）。

---

### 5.3 明细视角

使用 `team$self$id`、`team$self$caption` 等列，用于精确匹配或查看后代明细。

**用途**：
1. **精确匹配**：只查某个节点自身，不使用闭包表
2. **后代明细**：结合闭包表过滤，查看各后代的明细数据

#### 场景 1：精确匹配（只查自身）

```json
{
    "param": {
        "columns": ["team$self$caption", "salesAmount"],
        "slice": [
            { "field": "team$self$id", "op": "=", "value": "T001" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d3.team_name, SUM(t0.sales_amount)
FROM fact_team_sales t0
LEFT JOIN dim_team d3 ON t0.team_id = d3.team_id
WHERE d3.team_id = 'T001'
GROUP BY d3.team_name
```

**结果**：只返回 T001 自身的销售数据，不包含后代。

#### 场景 2：后代明细（分组显示各后代）

```json
{
    "param": {
        "columns": ["team$self$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "=", "value": "T001" }
        ],
        "groupBy": [
            { "field": "team$self$caption" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT d3.team_name AS "team$self$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d3 ON t0.team_id = d3.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d3.team_name
```

**结果**：返回 **N 条记录**（每个后代一条）

| team$self$caption | salesAmount |
|-------------------|-------------|
| 总公司             | 50000       |
| 技术部             | 120000      |
| 研发组             | 80000       |
| 前端小组           | 30000       |
| 测试组             | 40000       |
| 销售部             | 200000      |
| 华东区             | 150000      |
| 华南区             | 180000      |
| 深圳办             | 150000      |

---

### 5.4 视角对比总结

假设 T001（总公司）有 9 个团队（包括自身），各团队都有销售数据：

| 查询方式 | slice | groupBy | 返回记录数 | 说明 |
|----------|-------|---------|------------|------|
| 层级汇总 | `team$id = T001` | `team$hierarchy$caption` | 1 条 | 汇总到 T001 |
| 后代明细 | `team$id = T001` | `team$self$caption` | 9 条 | 各后代分别显示 |
| 精确匹配 | `team$self$id = T001` | - | 1 条 | 只查 T001 自身 |

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
| 关联方式 | 直接外键关联 | 通过闭包表关联 |
| 查询行为 | 精确匹配 | 支持层级汇总/后代明细/精确匹配 |
| 数据结构 | 单表 | 维度表 + 闭包表 |
| 可用列 | `dim$id`, `dim$caption` | 额外支持 `$hierarchy$`、`$self$` 视角 |
| 维护复杂度 | 低 | 中等 |

---

## 8. 最佳实践

1. **索引优化**：在闭包表的 `parent_id` 和 `team_id` 列建立索引
2. **数据一致性**：使用事务确保维度表和闭包表的一致性
3. **层级深度**：建议控制层级深度，过深会影响性能
4. **distance 字段**：虽非必需，但有助于查询特定层级的数据
5. **视角选择**：
   - 需要汇总到某节点 → 使用 `$hierarchy$` 视角
   - 需要查看后代明细 → 使用 `$self$` 视角 + 闭包表过滤
   - 需要精确匹配某节点 → 使用 `$self$` 视角

---

## 下一步

- [TM 语法手册](./tm-syntax.md) - 完整的 TM 定义语法
- [QM 语法手册](./qm-syntax.md) - 查询模型定义
- [DSL 查询 API](../api/query-api.md) - 查询 API 参考
