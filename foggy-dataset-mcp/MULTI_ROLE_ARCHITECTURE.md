# MCP 多角色工具访问架构

## 概述

本系统实现了基于用户角色的工具访问控制，通过不同的 HTTP 端点为不同类型的用户提供定制化的工具集合。

## 架构设计

### 1. 用户角色分类

系统定义了三种用户角色（`UserRole`）：

| 角色 | 路径前缀 | 描述 | 可用工具 |
|------|----------|------|----------|
| **ADMIN** | `/mcp/admin` | 管理员，拥有完全访问权限 | 所有工具 |
| **BUSINESS** | `/mcp/business` | 业务人员，使用简化的自然语言查询 | 仅自然语言查询工具 |
| **ANALYST** | `/mcp/analyst` | 数据分析师，使用专业数据处理工具 | 除自然语言查询外的所有专业工具 |

### 2. 工具分类体系

系统将工具按功能分为以下类别（`ToolCategory`）：

| 分类 | 描述 | 示例工具 |
|------|------|----------|
| **NATURAL_LANGUAGE** | 自然语言查询 | dataset_nl.query |
| **METADATA** | 元数据管理 | dataset.get_metadata, dataset.description_model_internal |
| **QUERY** | 数据查询 | dataset.query_model_v2 |
| **VISUALIZATION** | 数据可视化 | chart.generate |
| **EXPORT** | 数据导出 | dataset.export_with_chart |
| **SYSTEM** | 系统工具 | 健康检查等 |

### 3. 角色与工具分类映射

```
ADMIN    → 所有分类
BUSINESS → NATURAL_LANGUAGE
ANALYST  → METADATA, QUERY, VISUALIZATION, EXPORT, SYSTEM
```

## API 端点

### 管理员端点 (AdminMcpController)

- **路径**: `/mcp/admin/rpc` (同步), `/mcp/admin/stream` (流式)
- **用途**: 提供所有工具的完整访问权限
- **适用**: 管理员、开发人员

**示例请求**:
```bash
curl -X POST http://localhost:8080/mcp/admin/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'
```

### 业务人员端点 (BusinessMcpController)

- **路径**: `/mcp/business/rpc` (同步), `/mcp/business/stream` (流式)
- **用途**: 仅提供自然语言查询工具
- **适用**: 普通业务人员，不需要了解技术细节的用户

**可用工具**:
- `dataset_nl.query` - 自然语言查询

**示例请求**:
```bash
curl -X POST http://localhost:8080/mcp/business/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "dataset_nl.query",
      "arguments": {
        "query": "最近一周的销售额"
      }
    }
  }'
```

### 数据分析师端点 (AnalystMcpController)

- **路径**: `/mcp/analyst/rpc` (同步), `/mcp/analyst/stream` (流式)
- **用途**: 提供专业数据处理工具（不含自然语言查询）
- **适用**: 数据分析师、专业数据处理人员

**可用工具**:
- `dataset.get_metadata` - 获取元数据
- `dataset.description_model_internal` - 获取模型详细信息
- `dataset.query_model_v2` - 执行结构化查询
- `chart.generate` - 生成图表
- `dataset.export_with_chart` - 查询并生成图表

**示例请求**:
```bash
curl -X POST http://localhost:8080/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "dataset.query_model_v2",
      "arguments": {
        "model": "TmsCustomerModel",
        "payload": {
          "columns": ["name", "amount"]
        }
      }
    }
  }'
```

## 核心组件

### 服务层

1. **McpService** - 核心业务服务
   - 处理 MCP 协议的核心逻辑
   - 统一处理 initialize、tools/list、tools/call 等请求
   - 集成权限检查

2. **ToolFilterService** - 工具过滤服务
   - 根据用户角色过滤可用工具
   - 维护角色与工具分类的映射关系
   - 提供权限检查功能

3. **McpToolDispatcher** - 工具分发器
   - 注册和管理所有工具
   - 执行工具调用
   - 支持同步和流式执行

### 控制器层

- **AdminMcpController** - 管理员端点
- **BusinessMcpController** - 业务人员端点
- **AnalystMcpController** - 数据分析师端点

所有控制器共享相同的业务逻辑（通过 McpService），仅在用户角色上有所区别。

## 权限控制

### 工具访问控制流程

1. 客户端发送请求到特定角色的端点
2. Controller 将请求转发给 McpService，并指定用户角色
3. McpService 调用 ToolFilterService 检查权限
4. 如果有权限，执行工具；否则返回权限拒绝错误

### 错误处理

当用户尝试访问无权限的工具时，系统返回：
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Tool not found or access denied: <tool_name>"
  }
}
```

## 添加新工具

当需要添加新工具时，只需：

1. 实现 `McpTool` 接口
2. 在 `getCategories()` 方法中指定工具分类
3. 添加 `@Component` 注解

**示例**:
```java
@Component
public class MyNewTool implements McpTool {

    @Override
    public String getName() {
        return "my.new.tool";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // 指定此工具属于哪些分类
        return EnumSet.of(ToolCategory.QUERY, ToolCategory.EXPORT);
    }

    // ... 其他方法实现
}
```

工具会自动注册，并根据分类对不同角色可见。

## 添加新用户角色

如需添加新的用户角色：

1. 在 `UserRole` 枚举中添加新角色
2. 在 `ToolFilterService.ROLE_CATEGORY_MAPPING` 中配置该角色允许的分类
3. 创建对应的 Controller（可参考现有 Controller 实现）

## Claude Desktop IDE 配置

### 管理员配置

```json
{
  "mcpServers": {
    "dataset-admin": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-fetch"],
      "env": {
        "MCP_SERVER_URL": "http://localhost:8080/mcp/admin/rpc"
      }
    }
  }
}
```

### 业务人员配置

```json
{
  "mcpServers": {
    "dataset-business": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-fetch"],
      "env": {
        "MCP_SERVER_URL": "http://localhost:8080/mcp/business/rpc"
      }
    }
  }
}
```

### 数据分析师配置

```json
{
  "mcpServers": {
    "dataset-analyst": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-fetch"],
      "env": {
        "MCP_SERVER_URL": "http://localhost:8080/mcp/analyst/rpc"
      }
    }
  }
}
```

## 设计优势

1. **清晰的职责分离**: 通过服务层抽象，业务逻辑与控制器分离
2. **易于扩展**: 添加新角色或工具无需修改现有代码
3. **权限细粒度控制**: 基于工具分类的灵活权限管理
4. **代码复用**: 所有 Controller 共享相同的服务层实现
5. **便于维护**: 统一的业务逻辑，减少代码重复

## 后续扩展建议

1. **基于 Token 的身份验证**: 可在 Authorization header 中传递 token，动态确定用户角色
2. **基于数据库的角色配置**: 将角色与工具分类的映射关系存储在数据库中，支持动态配置
3. **审计日志**: 记录不同角色的工具使用情况
4. **速率限制**: 针对不同角色设置不同的调用频率限制
5. **工具使用统计**: 收集不同角色的工具使用数据，优化工具设计
