# Quick Start

This guide helps you quickly launch Foggy MCP service and connect AI clients.

## Choose Your Path

Select the approach that fits your background:

| Method | Best For | Time | Features |
|--------|----------|------|----------|
| [🐳 Docker Quick Start](#docker-quick-start) | Non-Java developers, quick validation | 5 min | Out-of-box, no dev environment needed |
| [☕ Java Integration](#java-integration) | Java developers, production deployment | 15 min | Full control, deep customization |

---

## 🐳 Docker Quick Start

Best for: Quick feature exploration, proof of concept, AI client testing

### Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- AI Service API Key (optional, for natural language queries)

### 1. Clone Project

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
```

### 2. Configure AI Service (Optional)

```bash
# Copy environment variable template
cp .env.example .env

# Edit .env to set API Key (for natural language query feature)
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

### 5. Connect AI Client

After service starts, jump to [Connect AI Clients](#connect-ai-clients) section to complete configuration.

---

## ☕ Java Integration

Best for: Java developers, existing project integration, production deployment, deep customization

### Prerequisites

- JDK 17+
- Maven 3.6+
- Any IDE (IntelliJ IDEA, VS Code, etc.)
- MySQL / PostgreSQL / SQLite database

### 1. Add Dependencies

#### 1.1 New Project

For new projects, add to `pom.xml`:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Foggy MCP Service (includes dataset-model) -->
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-dataset-mcp</artifactId>
        <version>8.0.1-beta</version>
    </dependency>

    <!-- Database Driver (choose based on your needs) -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

#### 1.2 Existing Project

For existing Spring Boot projects with datasource configured, just add:

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-mcp</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

### 2. Configure Main Application Class

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "my-mcp-server")
public class MyMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMcpApplication.class, args);
    }
}
```

### 3. Configuration File

Create or edit `src/main/resources/application.yml`:

```yaml
server:
  port: 7108

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

# Foggy Configuration
foggy:
  dataset:
    show-sql: true              # Print SQL during development
    show-sql-parameters: true   # Show SQL parameters
    show-execution-time: true   # Show execution time

  # MCP Configuration
  mcp:
    # AI Service Configuration (for natural language queries)
    ai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      model: ${OPENAI_MODEL:gpt-4o-mini}
```

### 4. Create Data Models

Create TM/QM model file directory:

```
src/main/resources/
└── foggy/
    └── templates/
        ├── FactOrderModel.tm      # Table Model
        └── FactOrderQueryModel.qm # Query Model
```

**Example TM File** `FactOrderModel.tm`:

```javascript
export const model = {
    name: 'FactOrderModel',
    caption: 'Order Fact Table',
    tableName: 'fact_order',
    idColumn: 'order_id',

    dimensions: [
        {
            name: 'customer',
            caption: 'Customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_id',
            primaryKey: 'customer_id',
            captionColumn: 'customer_name',
            properties: [
                { column: 'customer_id', caption: 'Customer ID' },
                { column: 'customer_name', caption: 'Customer Name' }
            ]
        }
    ],

    properties: [
        { column: 'order_id', caption: 'Order ID', type: 'STRING' },
        { column: 'order_status', caption: 'Order Status', type: 'STRING' }
    ],

    measures: [
        { column: 'amount', caption: 'Order Amount', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

**Example QM File** `FactOrderQueryModel.qm`:

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: 'Order Query',
    model: 'FactOrderModel',

    columnGroups: [
        {
            caption: 'Order Info',
            items: [
                { name: 'orderId' },
                { name: 'orderStatus' },
                { name: 'customer$caption' },
                { name: 'amount' }
            ]
        }
    ]
};
```

> **Tip**: For complete TM/QM syntax, see [TM Syntax Manual](/en/dataset/jm-qm/jm-syntax) and [QM Syntax Manual](/en/dataset/jm-qm/qm-syntax).
>
> For detailed data model creation guide, see [Dataset Model Quick Start](/en/dataset/guide/quick-start).

### 5. Start Service

```bash
# Maven start
mvn spring-boot:run

# Or run main application class in IDE
```

### 6. Verify Service

```bash
# Health check
curl http://localhost:7108/actuator/health

# Get available tools list
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

---

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

For detailed configuration, see [Cursor Integration Guide](../integration/cursor.md).

---

## Test Queries

After successful connection, try in AI client:

```
"Query sales data for the last week"
"Summarize sales by product category"
"Top 10 products by sales last month"
"Compare order quantities across stores"
```

---

## Pre-built Data Models (Docker)

Docker demo environment includes e-commerce scenario data models:

| Query Model | Description | Main Fields |
|-------------|-------------|-------------|
| FactSalesQueryModel | Sales Analysis | Product, Category, Sales Amount, Quantity |
| FactOrderQueryModel | Order Analysis | Order Number, Customer, Date, Amount |
| FactPaymentQueryModel | Payment Analysis | Payment Method, Amount, Status |
| FactReturnQueryModel | Return Analysis | Return Reason, Amount, Processing Status |
| FactInventorySnapshotQueryModel | Inventory Snapshot | Product, Warehouse, Stock Quantity |

---

## MCP Endpoint Description

| Endpoint | Purpose | Use Case |
|----------|---------|----------|
| `/mcp/admin/rpc` | Admin endpoint | All tool permissions, development debugging |
| `/mcp/analyst/rpc` | Analyst endpoint | Professional data analysis tools |
| `/mcp/business/rpc` | Business endpoint | Natural language queries only |
| `/actuator/health` | Health check | Service monitoring |

---

## Common Commands (Docker)

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

---

## Access Database (Docker)

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

---

## Troubleshooting

### Service Startup Failed

```bash
# Docker environment: Check service status
docker-compose ps
docker-compose logs mcp

# Java environment: Check logs
# Verify datasource configuration is correct
# Verify TM/QM file syntax is correct
```

### Cannot Connect to AI Service

1. Check if `OPENAI_API_KEY` is correct
2. Check network connection
3. If using Alibaba Cloud, ensure `OPENAI_BASE_URL` includes `/v1`

### Claude Desktop Cannot Connect

1. Verify configuration file path is correct
2. Check if JSON format is valid
3. Completely quit and restart Claude Desktop
4. Confirm MCP service is running and port is accessible

---

## Next Steps

- [Architecture Overview](./architecture.md) - Understand MCP service architecture
- [Tools List](../tools/overview.md) - View all available tools
- [Claude Desktop Integration](../integration/claude-desktop.md) - Detailed configuration guide
- [TM/QM Modeling](/en/dataset/guide/quick-start) - Create custom data models
