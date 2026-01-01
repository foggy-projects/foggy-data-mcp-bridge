Inspect database table structure to retrieve metadata for TM (Table Model) file generation.

**Admin Only:** This tool is restricted to the admin endpoint (`/mcp/admin/rpc`).

This tool directly queries the database using JDBC DatabaseMetaData API to get:
- Column information (name, type, length, nullable)
- Primary key information
- Foreign key relationships (for dimension detection)
- Index information (optional)
- Suggested TM type mappings
- Auto-generated TM template

**Multi-DataSource Support:**
- Use `data_source` parameter to specify a Spring Bean name for the DataSource
- If empty, uses the default DataSource
- Supports `database_type`: "jdbc" (default) or "mongo" (future)

**Use Cases:**
1. Generate TM files for existing database tables
2. Discover table structure before creating data models
3. Identify dimension relationships through foreign keys
4. Inspect tables from multiple data sources

**Type Mapping:**
- BIGINT → `BIGINT`
- INT/INTEGER → `INTEGER`
- DECIMAL/NUMERIC → `MONEY`
- VARCHAR/TEXT → `STRING`
- DATE → `DAY`
- DATETIME/TIMESTAMP → `DATETIME`
- BOOLEAN → `BOOL`

**Role Detection:**
- Columns with foreign keys → `dimension`
- Primary key columns → `property`
- Numeric columns with amount/price/cost in name → `measure`
- Quantity/count columns → `measure`
- Other columns → `property`

**Returns:**
- Table metadata with column details
- Suggested model type (fact/dimension)
- Suggested model name
- Foreign key relationships for dimension configuration
- Auto-generated TM template code
