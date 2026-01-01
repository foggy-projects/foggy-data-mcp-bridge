# Introduction

Foggy MCP Service implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) protocol, providing data query capabilities for AI assistants.

## Core Features

### DSL Query Syntax: Secure, Controllable, Easy to Understand

**This is the most important design philosophy of Foggy MCP.**

#### Why Not Let AI Generate SQL Directly?

Traditional approaches that let AI generate SQL directly have two major problems:

**Problem 1: Security Risks**

```sql
-- AI-generated SQL may cause:
DROP TABLE users;                    -- Data deletion
SELECT * FROM secrets;               -- Unauthorized access
SELECT * FROM orders; DROP TABLE--   -- SQL injection
```

**Problem 2: Difficult to Control and Understand**

```sql
-- Complex AI-generated SQL is hard to post-process
SELECT c.name, SUM(o.amount) as total
FROM customers c
LEFT JOIN orders o ON c.id = o.customer_id
LEFT JOIN products p ON o.product_id = p.id
WHERE c.region_id IN (SELECT id FROM regions WHERE parent_id = 1)
  AND o.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY c.id, c.name
HAVING total > 1000
ORDER BY total DESC;
```

- Complex SQL structure makes it difficult to inject additional permission controls at runtime
- Complex JOINs and subqueries make secondary optimization difficult
- SQL dialect differences across databases cause compatibility issues

#### DSL Query Syntax Solution

Foggy MCP uses **JSON-structured DSL query syntax**:

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["customer$caption", "sum(totalAmount) as total"],
    "slice": [{"field": "salesDate$id", "op": ">=", "value": "20250101"}],
    "orderBy": [{"field": "total", "dir": "DESC"}],
    "limit": 10
  }
}
```

**Advantage 1: Fundamentally Ensures Security**

| Risk Type | DSL Protection Mechanism |
|-----------|--------------------------|
| SQL Injection | DSL parameterized processing, AI never touches raw SQL |
| Unauthorized Access | Can only query defined models and fields |
| Data Tampering | DSL only supports query operations, cannot execute DDL/DML |
| Full Table Scan | Can configure forced filter conditions and row limits |

**Advantage 2: Developers Have Full Control**

JSON-structured DSL allows developers to post-process before query execution:

```
AI generates DSL  →  Program intercepts  →  Inject permissions/conditions  →  Execute query
                          ↓
                   - Auto-add data permission filters
                   - Force limit return rows
                   - Record audit logs
                   - Parameter validation and sanitization
```

**Advantage 3: TM/QM Middle Layer Pre-processing**

In the TM (Table Model) and QM (Query Model) layer, developers can:

- **Encapsulate complex relationships** - Multi-table JOIN relationships pre-defined in TM/QM, AI only needs to specify model name
- **Pre-process special fields** - JSON fields, encrypted fields converted to simple fields in TM
- **Semantic naming** - `customer$caption` is easier to understand than `c.name`
- **Control visibility scope** - QM defines which fields are visible to AI

```
Raw Table Structure (Complex)     TM/QM Layer (Simplified)      AI Sees
─────────────────────────         ─────────────────────────     ─────────────
orders.customer_id           →    customer$caption          →   "Customer Name"
orders.data->>'addr'         →    deliveryAddress           →   "Delivery Address"
Multi-table JOIN relations   →    Single model name         →   "FactSalesQueryModel"
```

**Advantage 4: Cross-Database Compatibility**

Same DSL automatically adapts to different databases:

| Database | Auto Handling |
|----------|---------------|
| MySQL | `LIMIT 10` |
| SQL Server | `TOP 10` / `OFFSET FETCH` |
| PostgreSQL | `LIMIT 10` |
| SQLite | `LIMIT 10` |

### Multi-Role Endpoints

Provides different MCP toolsets and permissions based on user roles:

| Role | Endpoint | Tool Scope | Use Case |
|------|----------|------------|----------|
| Admin | `/mcp/admin/rpc` | All tools | System management, debugging |
| Analyst | `/mcp/analyst/rpc` | Metadata + DSL Query | Data analysis, report development |
| Business User | `/mcp/business/rpc` | Natural language query only | Daily business queries |

### Rich Query Tools

- **Metadata Query** - Get available data models and field definitions
- **DSL Structured Query** - Secure, precise, controllable data queries
- **Natural Language Query** - AI-driven intelligent queries (internally converted to DSL)
- **Chart Generation** - Automatically generate trend charts, comparison charts, pie charts

### Multi-Client Support

- Any MCP-compatible client
- Claude Desktop - Anthropic's official desktop client
- Cursor IDE - AI programming assistant
- Custom AI Agent - Integration via SDK
- REST API - Direct JSON-RPC interface calls

## Quick Experience

```bash
# Clone project
git clone https://github.com/nicecho/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo

# Configure API Key
cp .env.example .env
# Edit .env to set OPENAI_API_KEY (optional)

# Start service
docker-compose up -d

# Verify
curl http://localhost:7108/actuator/health
```

## Documentation Navigation

### Getting Started

- [Quick Start](./quick-start.md) - Launch service in 5 minutes
- [Architecture Overview](./architecture.md) - Understand system architecture

### Tool Documentation

- [Tools Overview](../tools/overview.md) - All available tools
- [Metadata Tools](../tools/metadata.md) - Get model information
- [Query Tools](../tools/query.md) - Execute data queries
- [Natural Language Query](../tools/nl-query.md) - Intelligent data queries

### Integration Guides

- [Claude Desktop](../integration/claude-desktop.md) - Configure Claude Desktop
- [Cursor](../integration/cursor.md) - Configure Cursor IDE
- [API Usage](../integration/api.md) - Direct API calls
