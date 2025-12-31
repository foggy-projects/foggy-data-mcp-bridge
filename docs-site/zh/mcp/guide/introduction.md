# 简介

Foggy MCP 服务实现 [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) 协议，为 AI 助手提供数据查询能力。

## 什么是 MCP？

MCP (Model Context Protocol) 是一个开放协议，允许 AI 助手（如 Claude、Cursor）与外部数据源和工具进行交互。通过 MCP，AI 可以：

- 查询数据库中的业务数据
- 执行复杂的数据分析
- 生成图表和报表

## 核心特性

### 多角色端点

根据用户角色提供不同的工具集：

| 角色 | 端点 | 工具范围 |
|------|------|----------|
| 管理员 | `/mcp/admin/rpc` | 全部工具 |
| 分析师 | `/mcp/analyst/rpc` | 专业数据工具 |
| 业务用户 | `/mcp/business/rpc` | 自然语言查询 |

### 丰富的查询工具

- **元数据查询** - 获取可用的数据模型和字段
- **结构化查询** - 精确控制的数据查询
- **自然语言查询** - AI 驱动的智能查询
- **图表生成** - 自动生成趋势图、对比图

### 多客户端支持

- Claude Desktop
- Cursor IDE
- 自定义 AI Agent
- REST API 直接调用

## 快速体验

```bash
# 克隆项目
git clone https://github.com/nicecho/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo

# 配置 API Key
cp .env.example .env
# 编辑 .env 设置 OPENAI_API_KEY

# 启动服务
docker-compose up -d

# 验证
curl http://localhost:7108/actuator/health
```

## 文档导航

### 入门指南

- [快速开始](./quick-start.md) - 5 分钟启动服务
- [架构概述](./architecture.md) - 了解系统架构

### 工具文档

- [工具概述](../tools/overview.md) - 所有可用工具
- [元数据工具](../tools/metadata.md) - 获取模型信息
- [查询工具](../tools/query.md) - 执行数据查询
- [自然语言查询](../tools/nl-query.md) - 智能数据查询

### 集成指南

- [Claude Desktop](../integration/claude-desktop.md) - 配置 Claude Desktop
- [Cursor](../integration/cursor.md) - 配置 Cursor IDE
- [API 调用](../integration/api.md) - 直接调用 API
