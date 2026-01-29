# 父子维度（层级结构）

父子维度用于处理树形层级结构数据，通过闭包表实现高效查询。

## 使用场景

树形结构数据：

- **组织架构**：团队/部门层级
- **地理区域**：省/市/区层级
- **商品分类**：多级分类树
- **成本中心**：财务科目层级

## 闭包表设计

闭包表存储所有祖先-后代关系，支持高效的层级查询。

### 闭包表结构

```sql
CREATE TABLE team_closure (
    parent_id VARCHAR(50),  -- 祖先节点ID
    child_id  VARCHAR(50),  -- 后代节点ID
    depth     INT,          -- 层级深度（0表示自己）
    PRIMARY KEY (parent_id, child_id)
);
```

### 闭包表数据示例

假设组织架构：

```
销售总部 (HQ)
├── 华东区 (EAST)
│   ├── 上海团队 (SH)
│   └── 杭州团队 (HZ)
└── 华南区 (SOUTH)
    └── 深圳团队 (SZ)
```

闭包表数据：

| parent_id | child_id | depth |
|-----------|----------|-------|
| HQ        | HQ       | 0     |
| HQ        | EAST     | 1     |
| HQ        | SH       | 2     |
| HQ        | HZ       | 2     |
| HQ        | SOUTH    | 1     |
| HQ        | SZ       | 2     |
| EAST      | EAST     | 0     |
| EAST      | SH       | 1     |
| EAST      | HZ       | 1     |
| SOUTH     | SOUTH    | 0     |
| SOUTH     | SZ       | 1     |
| SH        | SH       | 0     |
| HZ        | HZ       | 0     |
| SZ        | SZ       | 0     |

## TM 语法

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: '团队',
    description: '销售所属团队',
    keyDescription: '团队ID，字符串格式',

    // 父子维度专用配置 ⚠️
    closureTableName: 'team_closure',  // 闭包表名（必填）
    parentKey: 'parent_id',            // 闭包表祖先列（必填）
    childKey: 'team_id',               // 闭包表后代列（必填）

    properties: [
        { column: 'team_id', caption: '团队ID', type: 'STRING' },
        { column: 'team_name', caption: '团队名称', type: 'STRING' },
        { column: 'parent_id', caption: '上级团队', type: 'STRING' },
        { column: 'team_level', caption: '层级', type: 'INTEGER' },
        { column: 'manager_name', caption: '负责人', type: 'STRING' }
    ]
}
```

## 父子维度专用字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `closureTableName` | string | 是 | 闭包表名称 |
| `closureTableSchema` | string | 否 | 闭包表 Schema（跨 Schema 时使用） |
| `parentKey` | string | 是 | 闭包表中的祖先列（如 `parent_id`） |
| `childKey` | string | 是 | 闭包表中的后代列（如 `team_id`） |

## 查询示例

### DSL 查询语法

查询"华东区及其下属团队"的销售：

```json
{
    "filters": [
        {
            "column": "team$_ancestors",
            "op": "eq",
            "value": "EAST"
        }
    ]
}
```

生成的 SQL：

```sql
SELECT ...
FROM fact_sales f
LEFT JOIN dim_team t ON f.team_id = t.team_id
WHERE t.team_id IN (
    SELECT child_id
    FROM team_closure
    WHERE parent_id = 'EAST'
)
```

### 常用操作符

| 操作符 | 说明 | 示例 |
|-------|------|------|
| `$_ancestors` | 祖先节点筛选 | 查询某团队及其所有下属 |
| `$_level` | 层级深度 | 查询特定层级的节点 |

## 维度复用

创建父子维度构建器：

```javascript
// dimensions/hierarchy-dims.fsscript

/**
 * 构建组织/团队父子维度
 */
export function buildOrgDim(options = {}) {
    const {
        name = 'team',
        tableName = 'dim_team',
        foreignKey = 'team_id',
        closureTableName = 'team_closure',
        caption = '团队',
        description = '组织团队'
    } = options;

    return {
        name,
        tableName,
        foreignKey,
        primaryKey: 'team_id',
        captionColumn: 'team_name',
        caption,
        description,

        // 父子维度配置
        closureTableName,
        parentKey: 'parent_id',
        childKey: 'team_id',

        properties: [
            { column: 'team_id', caption: '团队ID', type: 'STRING' },
            { column: 'team_name', caption: '团队名称', type: 'STRING' },
            { column: 'parent_id', caption: '上级团队', type: 'STRING' },
            { column: 'team_level', caption: '层级', type: 'INTEGER' },
            { column: 'manager_name', caption: '负责人', type: 'STRING' }
        ]
    };
}

/**
 * 构建区域父子维度
 */
export function buildRegionDim(options = {}) {
    const {
        name = 'region',
        foreignKey = 'region_id',
        caption = '区域'
    } = options;

    return {
        name,
        tableName: 'dim_region',
        foreignKey,
        primaryKey: 'region_id',
        captionColumn: 'region_name',
        caption,

        closureTableName: 'region_closure',
        parentKey: 'parent_id',
        childKey: 'region_id',

        properties: [
            { column: 'region_id', caption: '区域ID', type: 'STRING' },
            { column: 'region_name', caption: '区域名称', type: 'STRING' },
            { column: 'region_type', caption: '区域类型', type: 'STRING' },
            { column: 'region_level', caption: '层级', type: 'INTEGER' }
        ]
    };
}
```

## 使用示例

```javascript
// FactSalesModel.tm
import { buildOrgDim } from '../dimensions/hierarchy-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        buildOrgDim({
            name: 'salesTeam',
            caption: '销售团队',
            description: '负责销售的团队'
        }),
        // 其他维度...
    ],

    properties: [...],
    measures: [...]
};
```

## 闭包表维护

### 插入新节点

当插入新团队"苏州团队(SZ)"作为"华东区(EAST)"的子节点时：

```sql
-- 1. 插入自身关系
INSERT INTO team_closure (parent_id, child_id, depth)
VALUES ('SZ', 'SZ', 0);

-- 2. 插入与祖先的关系
INSERT INTO team_closure (parent_id, child_id, depth)
SELECT parent_id, 'SZ', depth + 1
FROM team_closure
WHERE child_id = 'EAST';
```

### 删除节点

删除节点及其所有后代：

```sql
DELETE FROM team_closure
WHERE child_id IN (
    SELECT child_id
    FROM team_closure
    WHERE parent_id = 'EAST'
);
```

## 最佳实践

1. **闭包表索引**：在 `(parent_id, child_id)` 和 `(child_id, parent_id)` 上建立索引
2. **层级深度**：添加 `depth` 字段，方便查询特定层级
3. **节点层级**：在维度表中添加 `team_level` 字段，存储节点在树中的层级
4. **触发器维护**：使用数据库触发器自动维护闭包表（可选）

## 详细文档

更多闭包表设计和实现细节，请参考项目文档：
- [父子维度文档](https://foggy-projects.github.io/foggy-data-mcp-bridge/zh/dataset-model/tm-qm/parent-child.html)
