---
layout: home
hero:
  name: Foggy Data MCP Bridge
  text: AI-Native Semantic Layer Framework
  tagline: Enable AI assistants to query business data safely and accurately through MCP protocol
  actions:
    - theme: brand
      text: 中文文档
      link: /zh/
    - theme: alt
      text: English Docs
      link: /en/
    - theme: alt
      text: GitHub
      link: https://github.com/foggy-projects/foggy-data-mcp-bridge

features:
  - icon: 🔒
    title: Security First
    details: DSL-based queries eliminate SQL injection risks. Field-level access control with read-only design.
  - icon: 🎯
    title: Model-as-Code
    details: Define data models using FSScript (JavaScript-like syntax). Functions, imports, and dynamic logic.
  - icon: 🌐
    title: Multi-Database
    details: MySQL, PostgreSQL, SQL Server, SQLite, MongoDB - same DSL works everywhere.
  - icon: 🤖
    title: AI-Native MCP
    details: Native MCP protocol support. Out-of-box integration with Claude Desktop & Cursor.
  - icon: 📊
    title: Auto Visualization
    details: Automatic chart generation - trend charts, bar charts, pie charts, and more.
  - icon: 🚀
    title: Production Ready
    details: Spring Boot based, Docker support, bilingual docs, extensible addon system.
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

## 📦 Core Modules

| Module | Description |
|--------|-------------|
| **foggy-dataset-model** | Semantic layer engine - TM/QM modeling, DSL query execution |
| **foggy-dataset** | Database abstraction - MySQL, PostgreSQL, SQL Server, SQLite |
| **foggy-fsscript** | Script engine - JavaScript-like syntax for TM/QM files |
| **foggy-dataset-mcp** | MCP server - AI assistant integration |
