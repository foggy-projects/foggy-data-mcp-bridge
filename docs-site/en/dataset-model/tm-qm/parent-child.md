# Parent-Child Dimension

<DownloadButton filename="parent-child.md" title="Download this document" />

Parent-Child Dimensions handle hierarchical structure data such as organizational structures, product categories, regions, etc.

## 1. What is a Parent-Child Dimension

A parent-child dimension is a self-referencing hierarchical structure where each member can have one parent and multiple children.

**Typical Use Cases**:

- **Organizational Structure**: Company → Department → Team → Group
- **Product Categories**: Major Category → Mid Category → Sub Category
- **Geographic Regions**: Country → Province → City → District
- **Menu Permissions**: System → Module → Page → Function

## 2. Closure Table Pattern

Foggy Dataset Model uses **Closure Table** to store hierarchical relationships, pre-storing all ancestor-descendant relationships for efficient queries.

**Advantages**:
- Query ancestors/descendants at any level with a single simple query
- No recursive queries needed, better performance
- Supports hierarchies of arbitrary depth

---

## 3. Data Table Structure

### 3.1 Dimension Table

Stores basic information of dimension members:

```sql
CREATE TABLE dim_team (
    team_id VARCHAR(64) PRIMARY KEY,
    team_name VARCHAR(100) NOT NULL,
    parent_id VARCHAR(64),
    level INT,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);
```

### 3.2 Closure Table

Stores all ancestor-descendant relationships:

```sql
CREATE TABLE team_closure (
    parent_id VARCHAR(64) NOT NULL,  -- Ancestor ID
    team_id VARCHAR(64) NOT NULL,    -- Descendant ID
    distance INT DEFAULT 0,          -- Distance (0 means self)
    PRIMARY KEY (parent_id, team_id)
);

-- Recommended indexes
CREATE INDEX idx_team_closure_parent ON team_closure (parent_id);
CREATE INDEX idx_team_closure_child ON team_closure (team_id);
```

### 3.3 Sample Data

The following sample data is from the `foggy-dataset-demo` project for demonstration and testing.

#### Organizational Structure

```
Head Office (T001)
├── Technology Dept (T002)
│   ├── R&D Group (T003)
│   │   ├── Frontend Team (T006)
│   │   └── Backend Team (T007)
│   └── QA Group (T004)
└── Sales Dept (T005)
    ├── East Region (T008)
    └── North Region (T009)
```

#### Team Dimension Table (dim_team)

| team_id | team_name | parent_id | team_level | manager_name |
|---------|-----------|-----------|------------|--------------|
| T001    | Head Office     | NULL      | 1          | Manager Zhang |
| T002    | Technology Dept | T001      | 2          | Manager Li    |
| T003    | R&D Group       | T002      | 3          | Lead Wang     |
| T004    | QA Group        | T002      | 3          | Lead Zhao     |
| T005    | Sales Dept      | T001      | 2          | Manager Qian  |
| T006    | Frontend Team   | T003      | 4          | Lead Sun      |
| T007    | Backend Team    | T003      | 4          | Lead Zhou     |
| T008    | East Region     | T005      | 3          | Manager Wu    |
| T009    | North Region    | T005      | 3          | Manager Zheng |

#### Closure Table (team_closure)

| parent_id | team_id | distance | Description |
|-----------|---------|----------|-------------|
| T001      | T001    | 0        | Self |
| T001      | T002    | 1        | Head Office → Technology Dept |
| T001      | T003    | 2        | Head Office → Technology Dept → R&D Group |
| T001      | T004    | 2        | Head Office → Technology Dept → QA Group |
| T001      | T005    | 1        | Head Office → Sales Dept |
| T001      | T006    | 3        | Head Office → Technology Dept → R&D Group → Frontend |
| T001      | T007    | 3        | Head Office → Technology Dept → R&D Group → Backend |
| T001      | T008    | 2        | Head Office → Sales Dept → East Region |
| T001      | T009    | 2        | Head Office → Sales Dept → North Region |
| T002      | T002    | 0        | Self |
| T002      | T003    | 1        | Technology Dept → R&D Group |
| T002      | T004    | 1        | Technology Dept → QA Group |
| T002      | T006    | 2        | Technology Dept → R&D Group → Frontend |
| T002      | T007    | 2        | Technology Dept → R&D Group → Backend |
| T003      | T003    | 0        | Self |
| T003      | T006    | 1        | R&D Group → Frontend Team |
| T003      | T007    | 1        | R&D Group → Backend Team |
| T004      | T004    | 0        | Self |
| T005      | T005    | 0        | Self |
| T005      | T008    | 1        | Sales Dept → East Region |
| T005      | T009    | 1        | Sales Dept → North Region |
| T006      | T006    | 0        | Self |
| T007      | T007    | 0        | Self |
| T008      | T008    | 0        | Self |
| T009      | T009    | 0        | Self |

> **Total 25 records**: 9 self-records + 16 ancestor-descendant relationships

#### Sales Fact Table (fact_team_sales)

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

> **Total 18 records**: 9 teams × 2 days

#### Summary Data Reference

| Team | Own Sales | Including Subordinates (Hierarchical Sum) |
|------|-----------|-------------------------------------------|
| T001 Head Office | 110,000 | 610,000 (Entire company) |
| T002 Technology Dept | 65,000 | 130,000 (Including R&D, QA, Frontend/Backend) |
| T003 R&D Group | 22,000 | 48,000 (Including Frontend/Backend teams) |
| T005 Sales Dept | 220,000 | 408,000 (Including East, North regions) |

---

## 4. TM Model Configuration

```javascript
export const model = {
    name: 'FactTeamSalesModel',
    caption: 'Team Sales Fact Table',
    tableName: 'fact_team_sales',
    idColumn: 'sales_id',

    dimensions: [
        {
            name: 'team',
            tableName: 'dim_team',
            foreignKey: 'team_id',
            primaryKey: 'team_id',
            captionColumn: 'team_name',
            caption: 'Team',

            // === Parent-Child Dimension Config ===
            closureTableName: 'team_closure',  // Closure table name (required)
            parentKey: 'parent_id',            // Ancestor column in closure table (required)
            childKey: 'team_id',               // Descendant column in closure table (required)

            properties: [
                { column: 'team_id', caption: 'Team ID' },
                { column: 'team_name', caption: 'Team Name' },
                { column: 'parent_id', caption: 'Parent Team' },
                { column: 'level', caption: 'Level', alias: 'teamLevel' }
            ]
        }
    ],

    properties: [...],
    measures: [...]
};
```

### 4.1 Configuration Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `closureTableName` | string | Yes | Closure table name |
| `closureTableSchema` | string | No | Closure table schema |
| `parentKey` | string | Yes | Ancestor column in closure table |
| `childKey` | string | Yes | Descendant column in closure table |

---

## 5. Two Access Perspectives

Parent-child dimensions provide **two access perspectives** with clear semantics and consistent behavior:

| Perspective | Column Format | Behavior | Use Case |
|-------------|---------------|----------|----------|
| **Default Perspective** | `team$id`, `team$caption` | Same as regular dimensions, exact match | Exact query, detail display |
| **Hierarchy Perspective** | `team$hierarchy$id`, `team$hierarchy$caption` | Uses closure table, matches node and all descendants | Hierarchical aggregation, descendant range filtering |

**Design Principles**:
- **Default = Regular Dimension**: Behaves exactly like non-parent-child dimensions, no "magic"
- **Hierarchy = Explicit Request**: Closure table only activated when user explicitly uses `$hierarchy$`

---

### 5.1 Default Perspective (Regular Dimension Behavior)

Using columns like `team$id`, `team$caption` behaves exactly like regular dimensions.

**Example**: Query only T001's own sales data

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

**Generated SQL**:

```sql
SELECT d1.team_name, t0.sales_amount
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
WHERE d1.team_id = 'T001'
GROUP BY d1.team_name
```

**Returned Data** (only T001's own sales):

| team$caption | salesAmount |
|--------------|-------------|
| Head Office  | 50,000      |
| Head Office  | 60,000      |
---

### 5.2 Hierarchy Perspective (Using Closure Table)

Using columns like `team$hierarchy$id`, `team$hierarchy$caption` activates closure table for hierarchical operations.

#### Scenario 1: Hierarchical Aggregation (Aggregate to Ancestor Node)

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

**Generated SQL**:

```sql
SELECT d4.team_name AS "team$hierarchy$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
LEFT JOIN dim_team d4 ON d2.parent_id = d4.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d4.team_name
```

**Returned Data**:

| team$hierarchy$caption | totalSalesAmount |
|------------------------|------------------|
| Head Office            | 648,000          |

**Explanation**: Sales data of T001 and all its descendants (T002-T009) aggregated as "Head Office".

#### Scenario 2: Descendant Details (Show Each Descendant Separately)

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

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d1.team_name
```

**Returned Data** (9 records, one per descendant):

| team$caption | salesAmount |
|--------------|-------------|
| Head Office  | 110,000     |
| Technology Dept | 65,000   |
| R&D Group    | 22,000      |
| QA Group     | 17,000      |
| Sales Dept   | 220,000     |
| Frontend Team| 11,000      |
| Backend Team | 15,000      |
| East Region  | 100,000     |
| North Region | 88,000      |

**Explanation**: Use `team$hierarchy$id` to filter descendant range, but group by default `team$caption` to show details of each team.

---

### 5.3 Perspective Comparison Summary

Assuming T001 (Head Office) has 9 teams (including itself), each with sales data:

| Query Type | slice | groupBy | Records Returned | Description |
|------------|-------|---------|------------------|-------------|
| Exact Match | `team$id = T001` | `team$caption` | 1 record | Only T001 itself |
| Hierarchy Aggregation | `team$hierarchy$id = T001` | `team$hierarchy$caption` | 1 record | Aggregate to T001 |
| Descendant Details | `team$hierarchy$id = T001` | `team$caption` | 9 records | Each descendant shown separately |

---

### 5.4 Hierarchy Operators

Besides the `$hierarchy$` perspective, fine-grained hierarchical queries are supported via `op` operators without using `$hierarchy$` column names:

| op | Direction | Meaning | SQL Condition | Includes Self |
|----|-----------|---------|---------------|---------------|
| `childrenOf` | Descendant ↓ | Direct children | `distance = 1` | No |
| `descendantsOf` | Descendant ↓ | All descendants | `distance > 0` | No |
| `selfAndDescendantsOf` | Descendant ↓ | Self and all descendants | No restriction | Yes |
| `selfAndAncestorsOf` | Ancestor ↑ | Self and all ancestors | No restriction | Yes |
| `ancestorsOf` | Ancestor ↑ | All ancestors | `distance > 0` | No |

All operators support the `maxDepth` parameter to limit query depth.

---

#### 5.4.1 childrenOf - Query Direct Children

Query direct children of a specified node (distance = 1).

**Request**:

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "childrenOf", "value": "T001" }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001' AND d2.distance = 1
GROUP BY d1.team_name
```

**Returned Data** (T001's direct sub-departments):

| team$caption | salesAmount |
|--------------|-------------|
| Technology Dept | 65,000   |
| Sales Dept   | 220,000     |

---

#### 5.4.2 descendantsOf - Query All Descendants

Query all descendants of a specified node, excluding itself (distance > 0).

**Request**:

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "descendantsOf", "value": "T001" }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001' AND d2.distance > 0
GROUP BY d1.team_name
```

**Returned Data** (All T001's descendants, excluding T001):

| team$caption | salesAmount |
|--------------|-------------|
| Technology Dept | 65,000   |
| R&D Group    | 22,000      |
| QA Group     | 17,000      |
| Sales Dept   | 220,000     |
| Frontend Team| 11,000      |
| Backend Team | 15,000      |
| East Region  | 100,000     |
| North Region | 88,000      |

---

#### 5.4.3 selfAndDescendantsOf - Query Self and All Descendants

Query specified node and all its descendants (equivalent to `$hierarchy$` perspective).

**Request**:

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "selfAndDescendantsOf", "value": "T001" }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001'
GROUP BY d1.team_name
```

**Returned Data** (T001 and all descendants):

| team$caption | salesAmount |
|--------------|-------------|
| Head Office  | 110,000     |
| Technology Dept | 65,000   |
| R&D Group    | 22,000      |
| QA Group     | 17,000      |
| Sales Dept   | 220,000     |
| Frontend Team| 11,000      |
| Backend Team | 15,000      |
| East Region  | 100,000     |
| North Region | 88,000      |

---

#### 5.4.4 selfAndAncestorsOf - Query Self and All Ancestors

Query a specified node and all its ancestors (reverse JOIN on closure table). Typical use case: Odoo `parent_of` permission rules.

**Request**:

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "selfAndAncestorsOf", "value": "T003" }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL** (note the reversed JOIN direction):

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.parent_id
WHERE d2.team_id = 'T003'
GROUP BY d1.team_name
```

**Returned Data** (T003 R&D Group + all its ancestors):

| team$caption | salesAmount |
|--------------|-------------|
| Head Office  | 110,000     |
| Technology Dept | 65,000   |
| R&D Group    | 22,000      |

---

#### 5.4.5 ancestorsOf - Query All Ancestors (Excluding Self)

Query all ancestors of a specified node, excluding itself (distance > 0).

**Request**:

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "ancestorsOf", "value": "T003" }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.parent_id
WHERE d2.team_id = 'T003' AND d2.distance > 0
GROUP BY d1.team_name
```

**Returned Data** (T003's ancestors, excluding T003 itself):

| team$caption | salesAmount |
|--------------|-------------|
| Head Office  | 110,000     |
| Technology Dept | 65,000   |

---

#### 5.4.6 maxDepth - Limit Query Depth

Use `maxDepth` parameter to limit the depth of hierarchical queries.

**Example 1**: Query T001's descendants within 2 levels

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "descendantsOf", "value": "T001", "maxDepth": 2 }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T001' AND d2.distance BETWEEN 1 AND 2
GROUP BY d1.team_name
```

**Returned Data** (T001's descendants within 2 levels, i.e., children and grandchildren):

| team$caption | salesAmount |
|--------------|-------------|
| Technology Dept | 65,000   |
| R&D Group    | 22,000      |
| QA Group     | 17,000      |
| Sales Dept   | 220,000     |
| East Region  | 100,000     |
| North Region | 88,000      |

> Note: Excludes T006 (Frontend Team) and T007 (Backend Team) as they are 3 levels away from T001.

---

**Example 2**: childrenOf + maxDepth (Extend children range)

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "childrenOf", "value": "T002", "maxDepth": 2 }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id = 'T002' AND d2.distance BETWEEN 1 AND 2
GROUP BY d1.team_name
```

**Returned Data** (T002's children within 2 levels):

| team$caption | salesAmount |
|--------------|-------------|
| R&D Group    | 22,000      |
| QA Group     | 17,000      |
| Frontend Team| 11,000      |
| Backend Team | 15,000      |

---

#### 5.4.7 Multi-Value Query

Hierarchy operators support passing multiple values to query descendants of multiple nodes.

**Request**:

```json
{
    "param": {
        "columns": ["team$caption", "salesAmount"],
        "slice": [
            { "field": "team$id", "op": "childrenOf", "value": ["T002", "T005"] }
        ],
       "groupBy": [
          { "field": "team$caption" }
       ]
    }
}
```

**Generated SQL**:

```sql
SELECT d1.team_name AS "team$caption",
       SUM(t0.sales_amount) AS "salesAmount"
FROM fact_team_sales t0
LEFT JOIN dim_team d1 ON t0.team_id = d1.team_id
LEFT JOIN team_closure d2 ON t0.team_id = d2.team_id
WHERE d2.parent_id IN ('T002', 'T005') AND d2.distance = 1
GROUP BY d1.team_name
```

**Returned Data** (Direct children of T002 and T005):

| team$caption | salesAmount |
|--------------|-------------|
| R&D Group    | 22,000      |
| QA Group     | 17,000      |
| East Region  | 100,000     |
| North Region | 88,000      |

---

#### 5.4.8 Operator Comparison Table

| Query Need | Recommended Approach | Description |
|------------|----------------------|-------------|
| Exact match on a node | `team$id = 'T001'` | Default perspective |
| Aggregate to a node | `team$hierarchy$id` + `team$hierarchy$caption` | Hierarchy perspective |
| Descendant details | `team$hierarchy$id` + `team$caption` | Mixed perspective |
| Direct children | `op: childrenOf` | Hierarchy operator |
| All descendants (excluding self) | `op: descendantsOf` | Hierarchy operator |
| Self and all ancestors | `op: selfAndAncestorsOf` | Ancestor direction operator |
| All ancestors (excluding self) | `op: ancestorsOf` | Ancestor direction operator |
| Depth-limited query | `op` + `maxDepth` | All operators support this |

---

## 6. Closure Table Maintenance

### 6.1 Add Node

```sql
-- Add new team T010 (under R&D Group T003)
INSERT INTO dim_team VALUES ('T010', 'Backend Team', 'T003', 4, 'ACTIVE');

-- Insert self relationship
INSERT INTO team_closure (parent_id, team_id, distance)
VALUES ('T010', 'T010', 0);

-- Insert all ancestor-to-new-node relationships
INSERT INTO team_closure (parent_id, team_id, distance)
SELECT parent_id, 'T010', distance + 1
FROM team_closure
WHERE team_id = 'T003';
```

### 6.2 Delete Node

```sql
DELETE FROM team_closure WHERE team_id = 'T010' OR parent_id = 'T010';
DELETE FROM dim_team WHERE team_id = 'T010';
```

---

## 7. Differences from Regular Dimensions

| Feature | Regular Dimension | Parent-Child Dimension |
|---------|-------------------|------------------------|
| Hierarchy Support | Fixed hierarchy (e.g., year-month-day) | Arbitrary depth dynamic hierarchy |
| Association Method | Direct foreign key | Supports closure table association |
| Query Behavior | Exact match | Default exact match, `$hierarchy$` enables hierarchy operations |
| Data Structure | Single table | Dimension table + Closure table |
| Available Columns | `dim$id`, `dim$caption` | Additionally supports `$hierarchy$` perspective |
| Maintenance Complexity | Low | Medium |

---

## 8. Best Practices

1. **Index Optimization**: Create indexes on `parent_id` and `team_id` columns in closure table
2. **Data Consistency**: Use transactions to ensure consistency between dimension and closure tables
3. **Hierarchy Depth**: Recommend controlling hierarchy depth; excessive depth impacts performance
4. **distance Field**: Although not required, helps query specific hierarchy levels
5. **Perspective Selection**:
   - Exact match on a node → Use default perspective `team$id`
   - Need to aggregate to a node → Use `team$hierarchy$id` + `team$hierarchy$caption`
   - Need to view descendant details → Use `team$hierarchy$id` + `team$caption`

---

## Next Steps

- [JSON Query DSL](./query-dsl.md) - Complete DSL query syntax (recommended)
- [TM Syntax Manual](./tm-syntax.md) - Complete TM definition syntax
- [QM Syntax Manual](./qm-syntax.md) - Query model definitions
- [Query API](../api/query-api.md) - HTTP API reference
