---
layout: home
hero:
  name: Foggy Data MCP Bridge
  text: AI-Native Semantic Layer Framework
  tagline: Enable AI assistants to query business data safely and accurately through MCP protocol
  actions:
    - theme: brand
      text: Get Started
      link: /en/mcp/guide/introduction
    - theme: alt
      text: Quick Start
      link: /en/mcp/guide/quick-start
    - theme: alt
      text: GitHub
      link: https://github.com/foggy-projects/foggy-data-mcp-bridge

features:
  - icon: 🔒
    title: Security First
    details: DSL-based queries eliminate SQL injection risks. Field-level access control with read-only design.
    link: /en/dataset/api/authorization
    linkText: Access Control
  - icon: 🎯
    title: Model-as-Code
    details: Define semantic layer using FSScript (JavaScript-like syntax). Functions, imports, and dynamic logic.
    link: /en/dataset/tm-qm/tm-syntax
    linkText: TM/QM Syntax
  - icon: 🌐
    title: Multi-Database
    details: MySQL, PostgreSQL, SQL Server, SQLite, MongoDB - same DSL query works everywhere.
    link: /en/dataset/guide/introduction
    linkText: Database Support
  - icon: 🤖
    title: AI-Native MCP
    details: Native MCP protocol support. Out-of-box integration with Claude Desktop & Cursor.
    link: /en/mcp/guide/introduction
    linkText: MCP Integration
  - icon: 📊
    title: Auto Visualization
    details: Automatic chart generation - trend charts, bar charts, pie charts, and more.
    link: /en/mcp/tools/chart
    linkText: Chart Features
  - icon: 🚀
    title: Production Ready
    details: Spring Boot based, Docker support, bilingual docs, extensible addon system.
    link: /en/mcp/guide/quick-start
    linkText: Quick Deploy
---

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #bd34fe 30%, #41d1ff);
}
</style>

## Why This Project?

### ❌ The Problem: Letting AI Write SQL Directly is Dangerous

| Problem | Impact |
|---------|--------|
| **Security Risks** | AI may generate `DELETE`, `UPDATE`, or access sensitive tables |
| **Schema Exposure** | Must share complete database schema with AI |
| **No Business Semantics** | What does `order_status=3` mean? AI doesn't know |
| **Complex JOINs** | Multi-table relationships and aggregations are fragile |

### ✅ Our Solution: Semantic Layer with DSL Query Language

```
AI → JSON DSL Query → Semantic Layer → Safe SQL → Database
                            ↓
                    • Prevents SQL injection
                    • Enforces access control
                    • Handles multi-table JOINs
                    • Abstracts database dialects
```

**Example**: AI only needs to know semantic meaning, not database internals

```json
{
  "model": "FactSalesQueryModel",
  "columns": ["customer$name", "sum(totalAmount)"],
  "filters": [{"field": "orderDate", "op": ">=", "value": "2024-01-01"}],
  "orderBy": [{"field": "totalAmount", "dir": "DESC"}],
  "limit": 10
}
```

## 🎯 Use Cases

<div class="use-cases">

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

</div>

## 🚀 Quick Start

**Docker One-Click Start**:

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
docker compose up -d
```

**Claude Desktop Configuration**:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

Then query data using natural language:
- *"Show me sales by brand for the last week"*
- *"Which products had the highest return rate last month?"*
- *"Generate a chart comparing revenue by region"*

## 📦 Core Modules

<div class="modules-grid">

| Module | Description | Docs |
|--------|-------------|------|
| **foggy-dataset-model** | Semantic layer engine - TM/QM modeling, DSL query execution | [Guide](/en/dataset-model/guide/introduction) |
| **foggy-dataset** | Database abstraction - Multi-database dialect support | [Query Layer](/en/dataset-query/guide/introduction) |
| **foggy-fsscript** | Script engine - JavaScript-like syntax for TM/QM files | [FSScript Guide](/en/fsscript/guide/introduction) |
| **foggy-dataset-mcp** | MCP server - AI assistant integration | [MCP Service](/en/mcp/guide/introduction) |

</div>

### 📘 TM/QM Modeling
- [TM Syntax Manual](/en/dataset-model/tm-qm/tm-syntax) - Table model definition
- [QM Syntax Manual](/en/dataset-model/tm-qm/qm-syntax) - Query model definition
- [Query DSL API](/en/dataset-model/api/query-api) - JSON query reference
- [Calculated Fields](/en/dataset-model/tm-qm/calculated-fields) - Derived metrics

### 📗 FSScript Engine
- [Why FSScript](/en/fsscript/guide/why-fsscript) - Design philosophy
- [Syntax Guide](/en/fsscript/syntax/variables) - Language reference
- [Spring Boot Integration](/en/fsscript/java/spring-boot) - Java integration
