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
│   └── 研发组 (T003)
└── 销售部 (T004)
```

| parent_id | team_id | distance |
|-----------|---------|----------|
| T001      | T001    | 0        |
| T001      | T002    | 1        |
| T001      | T003    | 2        |
| T001      | T004    | 1        |
| T002      | T002    | 0        |
| T002      | T003    | 1        |
| T003      | T003    | 0        |
| T004      | T004    | 0        |

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
                { column: 'level', caption: '层级' }
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

## 5. 查询行为

### 5.1 自动查询重写

当对父子维度进行过滤时，系统自动将条件重写为使用闭包表，查询该节点及其**所有后代**的数据。

**DSL 查询**：

```json
{
    "param": {
        "columns": ["team$caption", "totalAmount"],
        "slice": [
            { "name": "team$id", "type": "=", "value": "T002" }
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
WHERE d2.parent_id = 'T002'
GROUP BY d1.team_name
```

**查询结果**：
- 包含 T002（技术部）的数据
- 包含 T003（研发组）的数据
- 包含 T002 所有后代节点的数据

### 5.2 查询示例

**汇总某部门及所有子部门的销售**：

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "name": "team$id", "type": "=", "value": "T001" }
        ],
        "groupBy": [
            { "name": "team$caption" }
        ]
    }
}
```

结果将包含 T001（总公司）下所有部门的销售数据。

---

## 6. 闭包表维护

### 6.1 新增节点

```sql
-- 添加新团队 T005（隶属于研发组 T003）
INSERT INTO dim_team VALUES ('T005', '前端小组', 'T003', 4, 'ACTIVE');

-- 插入自身关系
INSERT INTO team_closure (parent_id, team_id, distance)
VALUES ('T005', 'T005', 0);

-- 插入所有祖先到新节点的关系
INSERT INTO team_closure (parent_id, team_id, distance)
SELECT parent_id, 'T005', distance + 1
FROM team_closure
WHERE team_id = 'T003';
```

### 6.2 删除节点

```sql
DELETE FROM team_closure WHERE team_id = 'T005' OR parent_id = 'T005';
DELETE FROM dim_team WHERE team_id = 'T005';
```

---

## 7. 与普通维度的区别

| 特性 | 普通维度 | 父子维度 |
|------|----------|----------|
| 层级支持 | 固定层级（如年-月-日） | 任意深度动态层级 |
| 关联方式 | 直接外键关联 | 通过闭包表关联 |
| 查询行为 | 精确匹配 | 包含所有后代 |
| 数据结构 | 单表 | 维度表 + 闭包表 |
| 维护复杂度 | 低 | 中等 |

---

## 8. 最佳实践

1. **索引优化**：在闭包表的 `parent_id` 和 `team_id` 列建立索引
2. **数据一致性**：使用事务确保维度表和闭包表的一致性
3. **层级深度**：建议控制层级深度，过深会影响性能
4. **distance 字段**：虽非必需，但有助于查询特定层级的数据

---

## 下一步

- [TM 语法手册](./tm-syntax.md) - 完整的 TM 定义语法
- [QM 语法手册](./qm-syntax.md) - 查询模型定义
- [DSL 查询 API](../api/query-api.md) - 查询 API 参考
