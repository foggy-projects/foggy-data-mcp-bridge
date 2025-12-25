# 父子维度（Parent-Child Dimension）使用指南

父子维度是Foggy Dataset Model支持的一种特殊维度类型，用于处理层级结构数据（如组织架构、商品分类、地区等）。

## 概述

### 什么是父子维度？

父子维度是一种自引用的层级结构，其中每个成员都可以有一个父成员和多个子成员。典型的应用场景包括：

- **组织架构**：公司 → 部门 → 团队 → 小组
- **商品分类**：大类 → 中类 → 小类
- **地理区域**：国家 → 省份 → 城市 → 区县
- **菜单权限**：系统 → 模块 → 页面 → 功能

### 闭包表（Closure Table）模式

Foggy Dataset Model使用**闭包表**（Closure Table）模式来存储和查询层级关系。闭包表预先存储了所有祖先-后代关系，包括节点到自身的关系，从而实现高效的层级查询。

**优势**：
- 查询任意层级的祖先/后代只需一次简单查询
- 无需递归查询，性能更好
- 支持任意深度的层级结构

## 数据表结构

### 1. 维度表（Dimension Table）

存储维度成员的基本信息：

```sql
-- 示例：团队维度表
CREATE TABLE dim_team (
    team_id VARCHAR(64) PRIMARY KEY,    -- 团队ID（主键）
    team_name VARCHAR(100) NOT NULL,    -- 团队名称
    parent_id VARCHAR(64),              -- 父团队ID（自引用外键）
    level INT,                          -- 层级深度
    status VARCHAR(20) DEFAULT 'ACTIVE' -- 状态
);

-- 示例数据
INSERT INTO dim_team VALUES ('T001', '总公司', NULL, 1, 'ACTIVE');
INSERT INTO dim_team VALUES ('T002', '技术部', 'T001', 2, 'ACTIVE');
INSERT INTO dim_team VALUES ('T003', '研发组', 'T002', 3, 'ACTIVE');
INSERT INTO dim_team VALUES ('T004', '销售部', 'T001', 2, 'ACTIVE');
```

### 2. 闭包表（Closure Table）

存储所有祖先-后代关系：

```sql
-- 闭包表结构
CREATE TABLE team_closure (
    parent_id VARCHAR(64) NOT NULL,     -- 祖先ID
    team_id VARCHAR(64) NOT NULL,       -- 后代ID（也称child_id）
    distance INT DEFAULT 0,             -- 距离（0表示自身）
    PRIMARY KEY (parent_id, team_id)
);

-- 建议索引
CREATE INDEX idx_team_closure_parent_id ON team_closure (parent_id);
CREATE INDEX idx_team_closure_team_id ON team_closure (team_id);
```

**闭包表数据示例**：

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

**数据说明**：
- `distance=0`：节点到自身的关系
- `distance=1`：直接父子关系
- `distance>1`：间接祖先关系

## 模型定义

### TM文件配置

在TM模型文件中定义父子维度：

```javascript
export const model = {
    name: 'FactTeamSalesModel',
    caption: '团队销售事实',
    tableName: 'fact_team_sales',
    idColumn: 'sales_id',

    dimensions: [
        {
            name: 'team',                    // 维度名称
            tableName: 'dim_team',           // 维度表名
            foreignKey: 'team_id',           // 事实表外键
            primaryKey: 'team_id',           // 维度表主键
            captionColumn: 'team_name',      // 显示字段
            caption: '团队',

            // === 父子维度特有配置 ===
            closureTableName: 'team_closure', // 闭包表名（必填）
            parentKey: 'parent_id',           // 闭包表中的祖先列（必填）
            childKey: 'team_id',              // 闭包表中的后代列（必填）

            properties: [
                { column: 'team_id', caption: '团队ID' },
                { column: 'team_name', caption: '团队名称' },
                { column: 'parent_id', caption: '上级团队' },
                { column: 'level', caption: '层级' }
            ]
        }
    ],

    properties: [
        { column: 'sales_id', caption: '销售ID', type: 'STRING' },
        { column: 'sales_date', caption: '销售日期', type: 'DATE' },
        { column: 'sales_amount', caption: '销售金额', type: 'MONEY' }
    ],

    measures: [
        {
            name: 'totalAmount',
            caption: '销售总额',
            column: 'sales_amount',
            aggregation: 'SUM'
        },
        {
            name: 'salesCount',
            caption: '销售笔数',
            aggregation: 'COUNT'
        }
    ]
};
```

### 配置字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `closureTableName` | string | 是 | 闭包表名称 |
| `closureTableSchema` | string | 否 | 闭包表Schema（跨Schema时使用） |
| `parentKey` | string | 是 | 闭包表中的祖先列名（如`parent_id`） |
| `childKey` | string | 是 | 闭包表中的后代列名（如`team_id`或`child_id`） |

## 查询行为

### 自动查询重写

当对父子维度进行过滤时，系统会自动将条件重写为使用闭包表，从而查询该节点及其所有后代的数据。

**原始查询条件**：
```javascript
{
    slicers: [
        { name: 'team$id', value: ['T002'] }  // 筛选技术部
    ]
}
```

**生成的SQL**（简化示意）：
```sql
SELECT ...
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id  -- 自动JOIN闭包表
WHERE d2.parent_id IN ('T002')  -- 条件转换到闭包表的parent_id
```

**查询结果**：
- 包含T002（技术部）自身的数据
- 包含T003（研发组）的数据（T002的子节点）
- 包含T002所有后代节点的数据

### 查询示例

**1. 汇总某部门及其所有子部门的销售**

```javascript
// 查询模型请求
{
    queryModelName: 'TeamSalesQuery',
    columns: ['team$caption'],
    measures: ['totalAmount', 'salesCount'],
    slicers: [
        { name: 'team$id', value: ['T001'] }  // 总公司
    ]
}
```

结果将包含总公司下所有部门的汇总数据。

**2. 按层级分组统计**

```javascript
{
    queryModelName: 'TeamSalesQuery',
    columns: ['team$level', 'team$caption'],
    measures: ['totalAmount'],
    slicers: [
        { name: 'team$id', value: ['T001'] }
    ]
}
```

## 闭包表维护

### 新增节点

当添加新节点时，需要同时更新闭包表：

```sql
-- 添加新团队 T005（隶属于研发组T003）
INSERT INTO dim_team VALUES ('T005', '前端小组', 'T003', 4, 'ACTIVE');

-- 更新闭包表
-- 1. 插入到自身的关系
INSERT INTO team_closure (parent_id, team_id, distance) VALUES ('T005', 'T005', 0);

-- 2. 插入所有祖先到新节点的关系
INSERT INTO team_closure (parent_id, team_id, distance)
SELECT parent_id, 'T005', distance + 1
FROM team_closure
WHERE team_id = 'T003';  -- 父节点ID
```

### 删除节点

```sql
-- 删除节点及其闭包关系
DELETE FROM team_closure WHERE team_id = 'T005' OR parent_id = 'T005';
DELETE FROM dim_team WHERE team_id = 'T005';
```

### 移动节点

移动节点到新的父节点需要：
1. 删除旧的祖先关系
2. 建立新的祖先关系

```sql
-- 将T003从T002移动到T004下（此处为示意，实际需处理子树）
-- 1. 删除T003及其子树与原祖先的关系
DELETE FROM team_closure
WHERE team_id IN (SELECT team_id FROM team_closure WHERE parent_id = 'T003')
  AND parent_id IN (SELECT parent_id FROM team_closure WHERE team_id = 'T003' AND distance > 0);

-- 2. 建立与新祖先的关系
INSERT INTO team_closure (parent_id, team_id, distance)
SELECT p.parent_id, c.team_id, p.distance + c.distance + 1
FROM team_closure p
CROSS JOIN team_closure c
WHERE p.team_id = 'T004'  -- 新父节点
  AND c.parent_id = 'T003'; -- 被移动的子树根节点
```

> **建议**：复杂的层级变更建议通过存储过程或应用层逻辑处理，以确保数据一致性。

## 实现原理

### 类结构

```
JdbcDimensionSupport (基类)
    ├── JdbcModelDimensionImpl      (普通维度)
    ├── JdbcModelTimeDimensionImpl  (时间维度)
    └── JdbcModelParentChildDimensionImpl (父子维度)
            ├── parentKey           // 闭包表祖先列
            ├── childKey            // 闭包表后代列
            ├── closureTableName    // 闭包表名
            ├── closureQueryObject  // 闭包表查询对象
            ├── parentKeyJdbcColumn // 祖先列对象
            └── childKeyJdbcColumn  // 后代列对象
```

### 加载过程

1. `JdbcModelLoaderImpl` 解析TM文件中的维度定义
2. 检测到`parentKey`不为空时，创建`JdbcModelParentChildDimensionImpl`实例
3. 加载闭包表作为独立的`QueryObject`
4. 设置闭包表的`childKey`为主键，用于与事实表关联

### 查询过程

1. `JdbcModelQueryEngine` 处理查询条件
2. 检测到维度列属于父子维度时：
   - 自动JOIN闭包表
   - 将原始条件列替换为闭包表的`parentKey`列
3. 生成的SQL自动包含层级关系查询

## 与普通维度的区别

| 特性 | 普通维度 | 父子维度 |
|------|----------|----------|
| 层级支持 | 固定层级（如年-月-日） | 任意深度动态层级 |
| 关联方式 | 直接外键关联 | 通过闭包表关联 |
| 查询行为 | 精确匹配 | 包含所有后代 |
| 数据结构 | 单表 | 维度表 + 闭包表 |
| 维护复杂度 | 低 | 中等（需维护闭包表） |

## 最佳实践

1. **索引优化**：在闭包表的`parent_id`和`team_id`（child_id）列上建立索引
2. **数据一致性**：使用事务确保维度表和闭包表的一致性
3. **层级深度**：建议控制层级深度，过深的层级会影响查询性能
4. **distance字段**：虽然不是必需的，但`distance`字段有助于查询特定层级的数据
5. **定期校验**：定期检查闭包表数据完整性，确保所有关系正确记录

## 相关文档

- [TM/QM 语法手册](./TM-QM-Syntax-Manual.md)
- [API 参考文档](./API-Reference.md)
