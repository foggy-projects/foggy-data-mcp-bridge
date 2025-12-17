# IDE Local Development Guide

Run MCP service locally using Java IDE (IntelliJ IDEA / Eclipse / VS Code), suitable for secondary development and debugging.

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker (for running test databases)
- IDE: IntelliJ IDEA (recommended) / Eclipse / VS Code

## Quick Start

### 1. Start Test Database

```bash
cd foggy-dataset-demo/docker

# Start MySQL (default)
docker compose up -d mysql

# Or start other databases
docker compose up -d postgres    # PostgreSQL
docker compose up -d sqlserver   # SQL Server
```

Wait for database health check to pass:

```bash
docker compose ps
# Confirm status is healthy
```

### 2. Import Project to IDE

#### IntelliJ IDEA

1. `File` → `Open` → Select `java-data-mcp-bridge` root directory
2. Wait for Maven indexing to complete
3. Confirm JDK version is 17+: `File` → `Project Structure` → `Project SDK`

#### Eclipse

1. `File` → `Import` → `Maven` → `Existing Maven Projects`
2. Select `java-data-mcp-bridge` root directory
3. Select all modules, click `Finish`

#### VS Code

1. Install `Extension Pack for Java` extension
2. `File` → `Open Folder` → Select `java-data-mcp-bridge` root directory
3. Wait for Java project to load

### 3. Configure Environment Variables

Configure MCP service startup parameters in IDE.

#### IntelliJ IDEA

1. Open `McpDataModelApplication.java`
2. Right-click → `Modify Run Configuration`
3. Add in `Environment variables`:

```
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o-mini
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:13306/foggy_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=foggy
SPRING_DATASOURCE_PASSWORD=foggy_test_123
```

#### Eclipse

1. `Run` → `Run Configurations`
2. Select `McpDataModelApplication`
3. Switch to `Environment` tab
4. Add the above environment variables

#### VS Code

Create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "MCP Service",
      "request": "launch",
      "mainClass": "com.foggyframework.dataset.mcp.McpDataModelApplication",
      "projectName": "foggy-dataset-mcp",
      "env": {
        "OPENAI_API_KEY": "sk-your-api-key",
        "OPENAI_BASE_URL": "https://api.openai.com",
        "OPENAI_MODEL": "gpt-4o-mini",
        "SPRING_DATASOURCE_URL": "jdbc:mysql://localhost:13306/foggy_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "SPRING_DATASOURCE_USERNAME": "foggy",
        "SPRING_DATASOURCE_PASSWORD": "foggy_test_123"
      }
    }
  ]
}
```

### 4. Start Service

Run main class:

```
foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/McpDataModelApplication.java
```

After successful startup you'll see:

```
Started McpDataModelApplication in X.XXX seconds
```

### 5. Verify Service

```bash
# Health check
curl http://localhost:7108/actuator/health

# Get tool list
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

## Database Connection Info

| Database | Port | URL | Username | Password |
|----------|------|-----|----------|----------|
| MySQL | 13306 | jdbc:mysql://localhost:13306/foggy_test | foggy | foggy_test_123 |
| PostgreSQL | 15432 | jdbc:postgresql://localhost:15432/foggy_test | foggy | foggy_test_123 |
| SQL Server | 11433 | jdbc:sqlserver://localhost:11433;databaseName=foggy_test | sa | Foggy_Test_123! |

## AI Service Configuration

| Service | OPENAI_BASE_URL | Model Examples |
|---------|-----------------|----------------|
| OpenAI | https://api.openai.com | gpt-4o-mini, gpt-4o |
| Alibaba Cloud Bailian | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-plus, qwen-turbo |
| Ollama Local | http://localhost:11434/v1 | qwen2:7b, llama3:8b |

## Connect AI Clients

Configure in Claude Desktop or Cursor:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

## Debugging Tips

### View Generated SQL

Set log level to DEBUG:

```properties
# application.yml or environment variable
logging.level.com.foggyframework.dataset=DEBUG
```

### Hot Reload

IntelliJ IDEA supports hot reload:
1. `Settings` → `Build, Execution, Deployment` → `Compiler`
2. Check `Build project automatically`
3. After modifying code, press `Ctrl+Shift+F9` to recompile

### Breakpoint Debugging

Set breakpoints at the following locations to trace query flow:
- `QueryModelTool.execute()` - Query entry point
- `SemanticQueryService.query()` - Semantic query processing
- `JdbcModelLoaderImpl.load()` - Model loading

## Directory Structure

```
foggy-dataset-mcp/
├── src/main/java/com/foggyframework/dataset/mcp/
│   ├── McpDataModelApplication.java   # Startup entry
│   ├── tools/                         # MCP tool implementations
│   ├── service/                       # Service layer
│   └── config/                        # Configuration classes
└── src/main/resources/
    ├── application.yml                # Default configuration
    └── schemas/                       # Tool descriptions and schemas
```

## FAQ

### Q: Database connection failed?

1. Confirm Docker container is running: `docker compose ps`
2. Check if port is occupied: `netstat -an | grep 13306`
3. Verify connection info is correct

### Q: AI API call failed?

1. Check if API Key is valid
2. Confirm BASE_URL is configured correctly
3. Check error messages in logs

### Q: Model not found?

1. Confirm `foggy-dataset-demo` module is compiled
2. Check `MCP_SEMANTIC_MODEL_LIST` environment variable
3. Check model loading info in startup logs
