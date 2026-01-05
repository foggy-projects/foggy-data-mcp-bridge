# MCP Data Model Java - 开发指南

## 项目概述

MCP Data Model Java 是 mcp-data-model (Python) 的 Java 实现版本，基于 Spring AI 框架构建。

### 技术栈
- **框架**: Spring Boot 3.3.x + Spring AI 1.0.0-M4
- **AI 模型**: OpenAI 兼容接口（支持阿里云百炼）
- **协议**: JSON-RPC 2.0 (MCP)
- **响应式**: Spring WebFlux + Project Reactor
- **构建**: Maven

### 核心功能
- MCP 协议支持（JSON-RPC 2.0）
- 自然语言数据查询
- 图表生成和导出
- 流式响应（SSE/WebSocket）

## 快速开始

### 1. 环境准备

```bash
# 复制环境变量配置
cp .env.example .env

# 编辑 .env 配置 API Key
vim .env
```

### 2. 构建和运行

```bash
# 构建项目
./manage_service.sh build

# 启动服务
./manage_service.sh start

# 查看日志
./manage_service.sh logs

# 检查状态
./manage_service.sh status
```

### 3. 测试 API

```bash
# 健康检查
curl http://localhost:7108/healthz

# 获取服务信息
curl http://localhost:7108/info

# 测试 MCP 工具列表
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'

# 测试元数据获取
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/call","params":{"name":"dataset.get_metadata","arguments":{}}}'
```

## 项目结构

```
mcp-data-model-java/
├── src/main/java/com/foggy/mcp/
│   ├── McpDataModelApplication.java   # 主启动类
│   ├── config/
│   │   ├── McpProperties.java         # 配置属性
│   │   └── WebClientConfig.java       # HTTP 客户端配置
│   ├── controller/
│   │   ├── McpController.java         # MCP JSON-RPC 端点
│   │   └── HealthController.java      # 健康检查端点
│   ├── service/
│   │   ├── McpToolDispatcher.java     # 工具分发器
│   │   ├── QueryExpertService.java    # AI 查询专家服务
│   │   └── ProgressEvent.java         # 进度事件
│   ├── tools/
│   │   ├── McpTool.java               # 工具接口
│   │   ├── MetadataTool.java          # 元数据工具
│   │   ├── DescriptionModelTool.java     # 模型描述工具
│   │   ├── QueryModelTool.java        # 查询工具
│   │   └── NaturalLanguageQueryTool.java  # NL 查询工具
│   └── schema/
│       ├── McpRequest.java            # MCP 请求
│       ├── McpResponse.java           # MCP 响应
│       ├── McpError.java              # MCP 错误
│       ├── DatasetNLQueryRequest.java # NL 查询请求
│       └── DatasetNLQueryResponse.java # NL 查询响应
├── src/main/resources/
│   └── application.yml                # 配置文件
├── pom.xml                            # Maven 配置
├── manage_service.sh                  # 服务管理脚本
├── .env.example                       # 环境变量示例
└── CLAUDE.md                          # 开发指南
```

## 配置说明

### AI 模型配置

项目支持 OpenAI 兼容接口，可以通过配置切换不同的 AI 提供商：

```yaml
# application.yml 或 .env

# OpenAI
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o-mini

# 阿里云百炼
OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
OPENAI_MODEL=qwen-plus

# Ollama (本地)
OPENAI_BASE_URL=http://localhost:11434/v1
OPENAI_MODEL=qwen2:7b
```

### 外部服务配置

```yaml
# 数据查询层服务
DATASET_QUERY_URL=http://localhost:8080

# 图表渲染服务
CHART_RENDER_URL=http://localhost:3000
CHART_RENDER_TOKEN=default-render-token
```

## MCP 端点说明

| 端点 | 方法 | 用途 | 备注 |
|------|------|------|------|
| `/mcp/analyst/rpc` | POST | 标准 MCP JSON-RPC | Claude Desktop/Cursor 使用 |
| `/mcp/analyst/stream` | POST | SSE 流式响应 | Web 前端使用 |
| `/mcp/admin/rpc` | POST | 管理员 JSON-RPC | 拥有全部工具权限 |
| `/mcp/business/rpc` | POST | 业务用户 JSON-RPC | 仅自然语言查询 |
| `/healthz` | GET | 健康检查 | |
| `/readyz` | GET | 就绪检查 | |
| `/info` | GET | 服务信息 | |

## 可用工具

| 工具名称 | 描述 |
|----------|------|
| `dataset.get_metadata` | 获取用户级元数据包 |
| `dataset.description_model_internal` | 获取模型详细字段信息 |
| `dataset.query_model` | 执行数据查询 |
| `dataset_nl.query` | 智能自然语言查询 |

## 开发指南

### 添加新工具

1. 创建工具类实现 `McpTool` 接口：

```java
@Component
public class MyNewTool implements McpTool {

    @Override
    public String getName() {
        return "my.new_tool";
    }

    @Override
    public String getDescription() {
        return "工具描述";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        // 返回 JSON Schema
    }

    @Override
    public Object execute(Map<String, Object> arguments, String traceId) {
        // 工具逻辑
    }
}
```

2. 工具会自动注册到 `McpToolDispatcher`

### 调试技巧

```bash
# 查看详细日志
LOG_LEVEL=DEBUG ./manage_service.sh start

# 测试特定工具
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":"test",
    "method":"tools/call",
    "params":{
      "name":"dataset_nl.query",
      "arguments":{"query":"最近一周销售数据"}
    }
  }'
```

## 与 Python 版本对比

| 功能 | Python 版本 | Java 版本 |
|------|-------------|-----------|
| MCP 协议 | ✅ FastAPI | ✅ Spring Boot |
| AI 模型 | ✅ 多 Provider | ✅ Spring AI (OpenAI 兼容) |
| 流式响应 | ✅ WebSocket/SSE | ✅ SSE/WebFlux |
| 工具系统 | ✅ 动态注册 | ✅ Spring Bean 自动注册 |
| 双端口架构 | ✅ M1/M2 | ✅ Profile 配置 |
| 会话管理 | ✅ Redis | 🚧 内存 (可扩展) |
| 图表生成 | ✅ | ✅ chart-render-service |
| Excel 导出 | ✅ | ❌ (按需求不实现) |

## 常见问题

### Q: 如何切换 AI 提供商？
A: 修改 `.env` 中的 `OPENAI_BASE_URL` 和 `OPENAI_MODEL` 即可。

### Q: 工具调用失败怎么排查？
A:
1. 检查 `LOG_LEVEL=DEBUG` 日志
2. 确认外部服务 (DATASET_QUERY_URL) 可访问
3. 检查 API Key 配置

### Q: 如何添加新的外部服务？
A:
1. 在 `McpProperties` 添加配置
2. 在 `WebClientConfig` 创建对应的 `WebClient` Bean
3. 在工具中注入使用

## 后续计划

- [x] MCP JSON-RPC 协议支持
- [x] Spring AI Function Calling
- [x] 图表生成工具
- [x] M1/M2 双端口架构
- [ ] 添加 Redis 会话管理
- [ ] 添加单元测试和集成测试
- [ ] Docker 支持
- [ ] 完善工具调用结果处理

---
最后更新：2025-11-24
