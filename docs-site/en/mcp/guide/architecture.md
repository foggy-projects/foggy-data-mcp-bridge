# Architecture Overview

Foggy MCP Service implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) protocol, providing data query capabilities for AI assistants.

## Overall Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        AI Clients                                │
│  (Claude Desktop / Cursor / Custom Agent)                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ MCP Protocol (JSON-RPC 2.0)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Foggy MCP Service Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ /mcp/admin   │  │ /mcp/analyst │  │ /mcp/business│          │
│  │  All Tools   │  │ Pro Tools    │  │  NL Query    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                              │                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    MCP Tool Dispatcher                     │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐            │  │
│  │  │ Metadata   │ │ Query Tool │ │ NL Query   │  ...       │  │
│  │  │ Tools      │ │            │ │ Tool       │            │  │
│  │  └────────────┘ └────────────┘ └────────────┘            │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Foggy Dataset Model                            │
│              (TM/QM Model Engine, Semantic Query Layer)          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Database                                  │
│         MySQL / PostgreSQL / SQL Server / SQLite / MongoDB       │
└─────────────────────────────────────────────────────────────────┘
```

## Component Description

### MCP Service Layer

Provides JSON-RPC 2.0 interface, handling AI client requests:

- **Endpoint Management** - Multiple endpoints with different permission levels
- **Tool Registration** - Register and manage available MCP tools
- **Request Routing** - Route requests to corresponding tool handlers

### Tool Dispatcher

Responsible for distributing tool calls:

- **Metadata Tools** - Query model and field information
- **Query Tools** - Execute DSL structured queries
- **NL Query Tools** - Handle natural language queries
- **Chart Tools** - Generate data visualization charts

### Dataset Model Layer

Core semantic layer engine:

- **TM Engine** - Parse and load table models
- **QM Engine** - Parse and load query models
- **Query Builder** - Build SQL queries from DSL
- **Multi-Database Adapter** - Adapt to different databases

## Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                      AI Client Request                           │
│         {"method": "tools/call", "name": "dataset.query"}        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     1. JSON-RPC Parsing                          │
│                  Parse request, validate format                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    2. Permission Check                           │
│            Verify endpoint permissions, check access rights      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    3. Tool Dispatch                              │
│               Route to corresponding tool handler                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    4. DSL Parsing                                │
│          Parse JSON DSL, validate field and model                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    5. SQL Generation                             │
│       TM/QM engine generates SQL, handle auto-JOIN               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    6. Query Execution                            │
│           Execute SQL, return results                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    7. Result Formatting                          │
│           Format results to MCP response format                  │
└─────────────────────────────────────────────────────────────────┘
```

## Security Architecture

### Multi-Layer Defense

```
┌─────────────────────────────────────────────────────────────────┐
│                   Layer 1: Endpoint Permissions                  │
│            Different endpoints provide different tool sets       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Layer 2: Model Access Control                  │
│               QM defines accessible models and fields            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Layer 3: DSL Validation                        │
│            All queries must go through DSL parsing               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Layer 4: Parameterized Queries                 │
│        Use PreparedStatement to prevent SQL injection            │
└─────────────────────────────────────────────────────────────────┘
```

## Next Steps

- [Tools Overview](../tools/overview.md) - Understand all available tools
- [Claude Desktop Integration](../integration/claude-desktop.md) - Configure Claude Desktop
- [API Usage](../integration/api.md) - Direct API calls
