# Cursor 集成

本文档介绍如何将 Foggy MCP 服务集成到 Cursor IDE。

## 配置方式

Cursor 支持通过设置界面或配置文件添加 MCP 服务器。

### 方式一：设置界面（推荐）

1. 打开 Cursor
2. 进入 **Settings** → **Features** → **MCP Servers**
3. 点击 **Add Server**
4. 填写配置：
   - **Name**: `foggy-dataset`
   - **URL**: `http://localhost:7108/mcp/analyst/rpc`

### 方式二：配置文件

编辑 Cursor 配置文件：

- **macOS**: `~/Library/Application Support/Cursor/User/settings.json`
- **Windows**: `%APPDATA%\Cursor\User\settings.json`
- **Linux**: `~/.config/Cursor/User/settings.json`

添加以下配置：

```json
{
  "mcp.servers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

## 端点选择

根据使用场景选择端点：

| 端点 | 适用场景 | 说明 |
|------|----------|------|
| `/mcp/analyst/rpc` | 开发调试 | 结构化查询，精确控制 |
| `/mcp/admin/rpc` | 完全访问 | 包含所有工具 |
| `/mcp/business/rpc` | 简单查询 | 仅自然语言 |

## 使用场景

### 数据库查询辅助

在开发过程中，可以让 Claude 帮助：

```
查询 orders 表最近 7 天的订单统计
```

### 数据分析

```
分析销售数据，找出销售额最高的 10 个品类
```

### 生成报表

```
生成本月各门店的销售对比图表
```

## 在代码中使用

Cursor 的 AI 助手可以在代码编辑时调用 MCP 工具：

```javascript
// 让 Claude 帮你查询数据并生成代码
// 输入: "查询用户表的字段结构，生成 TypeScript 接口"

// Claude 会调用 dataset.describe_model_internal 获取元数据
// 然后生成类似以下代码：

interface User {
  id: number;
  username: string;
  email: string;
  createdAt: Date;
}
```

## 带认证的配置

如果 MCP 服务需要认证：

```json
{
  "mcp.servers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc",
      "headers": {
        "Authorization": "Bearer your-token"
      }
    }
  }
}
```

## 多项目配置

可以为不同项目配置不同的 MCP 服务：

```json
{
  "mcp.servers": {
    "sales-data": {
      "url": "http://sales-server:7108/mcp/analyst/rpc"
    },
    "inventory-data": {
      "url": "http://inventory-server:7108/mcp/analyst/rpc"
    }
  }
}
```

## 验证配置

配置完成后：

1. 重启 Cursor
2. 打开 AI Chat（Cmd/Ctrl + L）
3. 输入：`列出可用的数据模型`
4. 确认 Claude 能调用 `dataset.get_metadata` 工具

## 故障排查

### MCP 工具不可用

1. 检查 MCP 服务是否运行：
   ```bash
   curl http://localhost:7108/actuator/health
   ```

2. 检查 Cursor 日志：
   - **macOS**: `~/Library/Logs/Cursor/`
   - **Windows**: `%APPDATA%\Cursor\logs\`

### 配置不生效

1. 确保 JSON 格式正确
2. 完全退出并重启 Cursor
3. 检查 URL 是否包含完整路径（包括 `/rpc`）

### 连接超时

如果查询超时，可能是：
- MCP 服务响应慢，检查服务日志
- 网络连接问题
- 查询数据量过大，尝试添加过滤条件

## 与 Claude Desktop 对比

| 特性 | Cursor | Claude Desktop |
|------|--------|----------------|
| 代码集成 | ✅ 深度集成 | ❌ 独立应用 |
| 上下文感知 | ✅ 当前文件/项目 | ❌ 仅对话 |
| MCP 支持 | ✅ | ✅ |
| 配置复杂度 | 低 | 低 |

## 下一步

- [Claude Desktop 集成](./claude-desktop.md) - 在 Claude Desktop 中使用
- [API 调用](./api.md) - 直接调用 MCP API
- [工具列表](../tools/overview.md) - 查看所有可用工具
