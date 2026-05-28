# Foggy Data MCP Bridge

[中文文档](README.zh-CN.md) | [Full Documentation](https://foggy-projects.github.io/foggy-data-mcp-docs/)

Enable AI assistants to query business data through a semantic layer and MCP instead of writing raw SQL directly.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MCP](https://img.shields.io/badge/MCP-Compatible-purple.svg)](https://modelcontextprotocol.io/)

## Why This Exists

Letting an LLM generate SQL directly against production data is usually the wrong abstraction:

- You have to expose schema details and database dialect quirks to the model.
- Permissions become hard to preserve once the model writes SQL itself.
- JOINs, aggregations, and business meaning drift quickly as prompts grow.
- Multi-database deployments multiply the problem.

Foggy Data MCP puts a semantic layer between AI and the database:

```text
AI assistant -> MCP tools / JSON DSL -> semantic layer -> safe SQL -> database
```

That layer gives you:

- business semantics instead of raw table exposure
- read-oriented query governance
- permission injection before execution
- reusable TM/QM models
- one query interface across multiple databases

## Who This Is For

- Teams building internal AI data assistants
- Products exposing governed data access through MCP
- ERP / BI / reporting projects that need business-friendly query interfaces
- Developers who want AI-accessible analytics without handing SQL generation to the model

If your immediate use case is Odoo, start here instead:

- [Foggy Odoo Bridge](https://github.com/foggy-projects/foggy-odoo-bridge)

## What You Get

- Semantic layer engine based on TM/QM models
- JSON Query DSL instead of raw SQL prompts
- Native MCP endpoints for AI clients and tools
- Multi-database support: MySQL, PostgreSQL, SQL Server, SQLite, MongoDB
- JavaScript-like modeling with FSScript
- Automatic chart generation support
- Java implementation for server deployment
- Python implementation for embeddable and FastAPI-based scenarios

## Example Query Flow

The AI only needs business fields, not table structure:

```json
{
  "model": "FactSalesQueryModel",
  "columns": ["customer$name", "sum(totalAmount)"],
  "filters": [{"field": "orderDate", "op": ">=", "value": "2024-01-01"}],
  "orderBy": [{"field": "totalAmount", "dir": "DESC"}],
  "limit": 10
}
```

Foggy turns that into governed SQL with JOIN handling, aggregation, and dialect translation built in.

## Quick Start

### 1. Clone and start the demo

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo

# Optional: enable natural-language querying
cp .env.example .env
# Edit .env and set OPENAI_API_KEY if needed

docker compose up -d
```

### 2. Verify the service

```bash
curl http://localhost:7108/actuator/health
```

### 3. Connect an AI client

Claude Desktop example:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

Cursor setup:

- [Cursor integration guide](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/integration/cursor)

### 4. Ask a question

- "Show me sales by brand for the last week"
- "Which products had the highest return rate last month?"
- "Generate a chart comparing revenue by region"

## Why Not Raw SQL

| Problem | Why It Matters |
|---------|----------------|
| Schema exposure | Prompts become tied to internal database design |
| SQL safety | It is difficult to prove what the model may generate |
| Missing business meaning | AI does not know what `order_status=3` means |
| Fragile JOIN logic | Complex reporting queries degrade quickly |
| Dialect differences | MySQL, PostgreSQL, SQL Server, and others diverge fast |

## Key Capabilities

### Security and Governance

- DSL-based queries instead of raw SQL generation
- Read-only query model by design
- Field-level and role-level access control
- Runtime permission injection before execution

### Modeling

- TM/QM semantic modeling
- Calculated fields and reusable business metrics
- FSScript for functions, imports, and dynamic logic
- Parent-child dimensions and pre-aggregation support

### Integration

- MCP endpoints for AI assistants
- Natural language to DSL workflows
- Chart rendering support
- Docker-based local demos and deployment

---

## 🎬 Quick Start (Docker)

### 1. Clone and Start

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo

# Optional: Set OpenAI API key for natural language queries
cp .env.example .env
# Edit .env to configure OPENAI_API_KEY (optional)

docker compose up -d
```

### 2. Verify Service

```bash
curl http://localhost:7108/actuator/health
```

### 3. Connect AI Client

**Claude Desktop** - Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

**Cursor IDE** - [See integration guide](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/integration/cursor)

### 4. Start Querying!

Now ask AI in natural language:
- *"Show me sales by brand for the last week"*
- *"Which products had the highest return rate last month?"*
- *"Generate a chart comparing revenue by region"*

---

## 📖 How It Works

### 1️⃣ Define Data Model (TM File)

Create `FactSalesModel.tm` using FSScript syntax:

```javascript
export const model = {
    name: 'FactSalesModel',
    caption: 'Sales Data',
    tableName: 'fact_sales',

    dimensions: [{
        name: 'product',
        tableName: 'dim_product',
        foreignKey: 'product_key',
        caption: 'Product',
        properties: [
            { column: 'brand', caption: 'Brand' },
            { column: 'category', caption: 'Category' }
        ]
    }],

    measures: [
        { column: 'quantity', caption: 'Quantity', aggregation: 'sum' },
        { column: 'sales_amount', caption: 'Sales Amount', aggregation: 'sum' }
    ]
};
```

### 2️⃣ AI Sends Semantic Query

AI doesn't need to know table structure, just semantic fields:

```json
{
  "model": "FactSalesQueryModel",
  "columns": ["product$brand", "salesAmount"],
  "filters": [{ "field": "orderDate", "op": ">=", "value": "2024-01-01" }],
  "orderBy": [{ "field": "salesAmount", "dir": "DESC" }],
  "limit": 10
}
```

### 3️⃣ Framework Generates Safe SQL

```sql
SELECT p.brand, SUM(f.sales_amount) as salesAmount
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
WHERE f.order_date >= '2024-01-01'
GROUP BY p.brand
ORDER BY salesAmount DESC
LIMIT 10
```

**No SQL injection risk. No unauthorized access. Just safe, semantic queries.**

---

## 🏗️ Project Structure

```
foggy-data-mcp-bridge/
├── foggy-core/                    # Core utilities
├── foggy-fsscript/                # FSScript scripting engine (JavaScript-like)
├── foggy-dataset/                 # Multi-database query layer (Dialects)
├── foggy-dataset-model/           # Semantic layer engine (TM/QM)
├── foggy-dataset-mcp/             # MCP server implementation
├── foggy-dataset-demo/            # Demo: E-commerce sample data
├── foggy-bean-copy/               # Bean mapping utilities
├── docs-site/                     # Documentation migration notice; source moved to foggy-data-mcp-docs
│
└── addons/                        # Extension modules
    ├── chart-render-service/      # Chart generation service
    ├── foggy-benchmark-spider2/   # Spider2 benchmark testing
    ├── foggy-dataset-client/      # Dataset client SDK
    ├── foggy-dataset-model-mongo/ # MongoDB model support
    ├── foggy-dataset-mongo/       # MongoDB query layer
    └── foggy-fsscript-client/     # FSScript client utilities
```

### Core Modules

| Module | Description |
|--------|-------------|
| **foggy-dataset-model** | Semantic layer engine - TM/QM modeling, DSL query execution |
| **foggy-dataset-mcp** | MCP server - AI assistant integration |
| **foggy-dataset** | Database abstraction - MySQL, PostgreSQL, SQL Server, SQLite |
| **foggy-fsscript** | Scripting engine - JavaScript-like syntax for TM/QM files |
| **foggy-dataset-demo** | Sample project - E-commerce data models |

### Extension Addons

| Addon | Purpose |
|-------|---------|
| **chart-render-service** | Generate charts from query results |
| **foggy-dataset-mongo** | MongoDB support (NoSQL) |
| **foggy-benchmark-spider2** | Spider2 benchmark for Text-to-SQL evaluation |

---

## 📚 Documentation

### 📘 Getting Started
- [MCP Introduction](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/guide/introduction) - What is Foggy MCP
- [Quick Start](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/guide/quick-start) - Get up and running
- [Architecture](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/guide/architecture) - System architecture overview

### 📗 Core Concepts
- [TM/QM Modeling](https://foggy-projects.github.io/foggy-data-mcp-docs/en/dataset-model/guide/introduction) - Build semantic layer
- [TM Syntax Manual](https://foggy-projects.github.io/foggy-data-mcp-docs/en/dataset-model/tm-qm/tm-syntax) - Table model reference
- [QM Syntax Manual](https://foggy-projects.github.io/foggy-data-mcp-docs/en/dataset-model/tm-qm/qm-syntax) - Query model reference
- [Query DSL API](https://foggy-projects.github.io/foggy-data-mcp-docs/en/dataset-model/api/query-api) - JSON query reference

### 📙 FSScript Engine
- [Why FSScript](https://foggy-projects.github.io/foggy-data-mcp-docs/en/fsscript/guide/why-fsscript) - Use cases
- [Syntax Guide](https://foggy-projects.github.io/foggy-data-mcp-docs/en/fsscript/syntax/variables) - Language reference
- [Spring Boot Integration](https://foggy-projects.github.io/foggy-data-mcp-docs/en/fsscript/java/spring-boot) - Java integration

### 📕 MCP Integration
- [Claude Desktop Setup](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/integration/claude-desktop)
- [Cursor Integration](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/integration/cursor)
- [MCP Tools Reference](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/tools/overview)
- [API Usage](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/integration/api)

### 🌐 Full Documentation Site
**Visit: [https://foggy-projects.github.io/foggy-data-mcp-docs/](https://foggy-projects.github.io/foggy-data-mcp-docs/)**

---

## 🎯 Use Cases

### 📊 Business Intelligence
- **Ad-Hoc Queries** - Business users ask questions in natural language
- **Multi-Dimensional Analysis** - Group by dimensions, aggregate measures
- **KPI Dashboards** - Track metrics with calculated fields

### 🔍 Data Analysis Platform
- **Self-Service Analytics** - Non-technical users query data without SQL
- **Dynamic Filtering** - Flexible conditions without schema knowledge
- **Data Exploration** - AI helps discover insights

### 🏢 Enterprise Data Gateway
- **Unified Data Access** - Single semantic layer across multiple databases
- **Access Control** - Role-based field-level permissions
- **Audit Logging** - Track all data access

### 🤖 AI Agent Development
- **RAG Systems** - Retrieve business data for AI reasoning
- **Chatbots** - Answer business questions from databases
- **Workflow Automation** - AI-driven data operations

---

## 🛠️ Development

### Prerequisites
- **Java 17+**
- **Maven 3.6+**
- **Docker** (optional, for demo)

### Local Build

```bash
# Build all modules
mvn clean install

# Run MCP server
cd foggy-dataset-mcp
mvn spring-boot:run
```

### IDE Setup
See [IDE Development Guide](https://foggy-projects.github.io/foggy-data-mcp-docs/en/mcp/guide/quick-start) for IntelliJ IDEA / VS Code configuration.

---

## 🤝 Contributing

We welcome contributions! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

[Apache License 2.0](LICENSE)

---

## 🌟 Star History

If you find this project useful, please give it a ⭐️ on GitHub!

[![Star History Chart](https://api.star-history.com/svg?repos=foggy-projects/foggy-data-mcp-bridge&type=Date)](https://star-history.com/#foggy-projects/foggy-data-mcp-bridge&Date)

---

## 📞 Support & Community

- **GitHub Issues**: [Report bugs or request features](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues)
- **Documentation**: [Full docs site](https://foggy-projects.github.io/foggy-data-mcp-docs/)
- **Discussions**: [Join conversations](https://github.com/foggy-projects/foggy-data-mcp-bridge/discussions)

---

**Built with ❤️ for the AI + Data community**
