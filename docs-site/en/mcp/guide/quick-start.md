# Quick Start

This guide helps you launch Foggy MCP service and connect AI clients in 5 minutes.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- AI Service API Key (optional) (OpenAI / Alibaba Cloud Bailian / Ollama)

## Docker Quick Start

### 1. Clone Project

```bash
git clone https://github.com/nicecho/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
```

### 2. Configure AI Service

```bash
# Copy environment variable template
cp .env.example .env

# Edit .env to set API Key
```

Minimal configuration only requires one variable:

```bash
# .env
OPENAI_API_KEY=sk-your-api-key-here
```

**Supported AI Services:**

| Service | BASE_URL | Model Example |
|---------|----------|---------------|
| OpenAI | `https://api.openai.com` | gpt-4o-mini |
| Alibaba Cloud Bailian | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-plus |
| Ollama | `http://host.docker.internal:11434/v1` | qwen2:7b |

### 3. Start Service

```bash
# One-click start (first time requires image build, ~3-5 minutes)
docker-compose up -d

# View startup logs
docker-compose logs -f mcp
```

### 4. Verify Service

```bash
# Health check
curl http://localhost:7108/actuator/health

# Get available tools list
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

## Connect AI Clients

### Claude Desktop

Edit Claude Desktop configuration file:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

Restart Claude Desktop to use.

### Cursor

Add MCP server in Cursor settings:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

## Test Queries

After successful connection, try in AI client:

```
"Query sales data for the last week"
"Summarize sales by product category"
"Top 10 products by sales last month"
"Compare order quantities across stores"
```

## Pre-built Data Models

Demo environment includes e-commerce scenario data models:

| Query Model | Description | Main Fields |
|-------------|-------------|-------------|
| FactSalesQueryModel | Sales Analysis | Product, Category, Sales Amount, Quantity |
| FactOrderQueryModel | Order Analysis | Order Number, Customer, Date, Amount |
| FactPaymentQueryModel | Payment Analysis | Payment Method, Amount, Status |
| FactReturnQueryModel | Return Analysis | Return Reason, Amount, Processing Status |
| FactInventorySnapshotQueryModel | Inventory Snapshot | Product, Warehouse, Stock Quantity |

## Common Commands

```bash
# Stop service
docker-compose down

# Restart MCP service
docker-compose restart mcp

# View real-time logs
docker-compose logs -f mcp

# Clear data and restart
docker-compose down -v
docker-compose up -d
```

## Access Database

To view demo data:

```bash
# Start Adminer (database management tool)
docker-compose --profile tools up -d adminer
```

Visit http://localhost:18080:
- **System**: MySQL
- **Server**: mysql
- **Username**: foggy
- **Password**: foggy_test_123
- **Database**: foggy_test

## Configuration Reference

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | AI API Key | (required) |
| `OPENAI_BASE_URL` | AI Service URL | https://api.openai.com |
| `OPENAI_MODEL` | Model Name | gpt-4o-mini |
| `MCP_PORT` | MCP Service Port | 7108 |
| `LOG_LEVEL` | Log Level | INFO |

### Endpoint Description

| Endpoint | Purpose |
|----------|---------|
| `/mcp/admin/rpc` | Admin endpoint (all tools) |
| `/mcp/analyst/rpc` | Analyst endpoint (professional tools) |
| `/mcp/business/rpc` | Business endpoint (natural language query) |
| `/actuator/health` | Health check |

## Troubleshooting

### Service Startup Failed

```bash
# Check service status
docker-compose ps

# View detailed logs
docker-compose logs mcp
```

### Cannot Connect to AI Service

1. Check if `OPENAI_API_KEY` is correct
2. Check network connection
3. If using Ollama, ensure service is running
