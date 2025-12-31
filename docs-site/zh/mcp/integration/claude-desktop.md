# Claude Desktop 集成

本文档详细介绍如何将 Foggy MCP 服务集成到 Claude Desktop。

## 配置文件位置

Claude Desktop 的 MCP 配置文件位于：

| 操作系统 | 配置文件路径 |
|----------|-------------|
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

## 基本配置

### 最小配置

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

### 完整配置示例

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc",
      "headers": {
        "Authorization": "Bearer your-token-here"
      }
    }
  }
}
```

## 端点选择

根据使用场景选择不同的端点：

| 端点 | 适用用户 | 可用工具 |
|------|----------|----------|
| `/mcp/analyst/rpc` | 数据分析师 | 元数据、查询、图表（推荐） |
| `/mcp/admin/rpc` | 管理员 | 全部工具 |
| `/mcp/business/rpc` | 业务人员 | 仅自然语言查询 |

### 分析师端点（推荐）

适合需要精确控制查询的专业用户：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

### 业务端点

适合非技术用户，只需用自然语言描述需求：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/business/rpc"
    }
  }
}
```

## 生效配置

1. 保存配置文件
2. 完全退出 Claude Desktop（不只是关闭窗口）
   - macOS: 右键点击 Dock 图标 → 退出
   - Windows: 右键点击系统托盘图标 → 退出
3. 重新启动 Claude Desktop

## 验证连接

启动 Claude Desktop 后，可以通过以下方式验证：

1. 在对话框中输入：`有哪些可用的数据模型？`
2. Claude 应该调用 `dataset.get_metadata` 工具并返回模型列表

如果 Claude 无法识别 MCP 工具，请检查：
- 配置文件 JSON 格式是否正确
- MCP 服务是否正在运行
- URL 是否可访问

## 使用示例

### 查询元数据

```
请列出所有可用的数据模型和字段
```

### 执行查询

```
查询最近一周的销售数据，按商品分类汇总金额
```

### 生成图表

```
生成近 30 天的销售趋势图
```

## 多服务配置

可以同时配置多个 MCP 服务：

```json
{
  "mcpServers": {
    "foggy-sales": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    },
    "foggy-inventory": {
      "url": "http://inventory-server:7108/mcp/analyst/rpc"
    }
  }
}
```

## 远程服务器配置

如果 MCP 服务部署在远程服务器：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "https://mcp.example.com/mcp/analyst/rpc",
      "headers": {
        "Authorization": "Bearer your-api-token"
      }
    }
  }
}
```

## 故障排查

### 配置不生效

1. 确认配置文件路径正确
2. 使用 JSON 验证工具检查格式
3. 完全退出并重启 Claude Desktop

### 连接失败

```bash
# 测试服务是否可访问
curl http://localhost:7108/actuator/health

# 测试 MCP 端点
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"ping","params":{}}'
```

### 工具调用失败

查看 MCP 服务日志：

```bash
# Docker 环境
docker-compose logs -f mcp

# 本地开发
tail -f logs/mcp.log
```

## 下一步

- [Cursor 集成](./cursor.md) - 在 Cursor 中使用
- [API 调用](./api.md) - 直接调用 MCP API
- [工具列表](../tools/overview.md) - 查看所有可用工具
