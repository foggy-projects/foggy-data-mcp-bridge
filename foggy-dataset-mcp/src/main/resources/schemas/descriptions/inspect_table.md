# inspect_table

Inspect database table structure for TM file generation. **Admin Only** (`/mcp/admin/rpc`).

## 返回内容
- Column info (name, type, length, nullable)
- Primary/foreign key info
- Suggested TM type mappings
- Auto-generated TM template

## 参数
- `table_name`: 表名
- `data_source`: DataSource Bean名（可选，默认使用主数据源）
- `database_type`: "jdbc"(默认) 或 "mongo"

## 类型映射
| DB Type | TM Type |
|---------|---------|
| BIGINT | BIGINT |
| INT/INTEGER | INTEGER |
| DECIMAL/NUMERIC | MONEY |
| VARCHAR/TEXT | STRING |
| DATE | DAY |
| DATETIME/TIMESTAMP | DATETIME |
| BOOLEAN | BOOL |

## 角色检测
- 外键列 → dimension
- 主键列 → property
- amount/price/cost列 → measure
- quantity/count列 → measure
- 其他 → property
