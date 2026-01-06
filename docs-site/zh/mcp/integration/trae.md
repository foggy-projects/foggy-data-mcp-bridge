# Trae CN 集成

本文档介绍如何将 Foggy MCP 服务集成到字节跳动 Trae IDE。

## 关于 Trae

[Trae](https://www.trae.cn/) 是字节跳动推出的 AI 原生 IDE，内置 Deepseek、Doubao 等大模型，从 v1.3.0 版本开始支持 MCP 协议。

## 前置条件

- Trae CN v1.3.0 或更高版本
- Foggy MCP 服务已启动（参考 [快速开始](../guide/quick-start.md)）

## 配置方式

### 方式一：JSON 配置（推荐）

1. 打开 Trae IDE
2. 点击右上角 **设置图标** → **MCP**
3. 点击 **原始配置（JSON）** 按钮
4. 在 `mcp.json` 文件中添加以下配置：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

### 方式二：MCP 市场

如果 Foggy MCP 已发布到 Trae MCP 市场，可以直接搜索添加：

1. 点击 **设置** → **MCP** → **添加**
2. 在市场中搜索 "foggy" 或 "dataset"
3. 点击添加并配置

## 端点选择

| 端点 | 适用场景 | 说明 |
|------|----------|------|
| `/mcp/analyst/rpc` | 数据分析 | 结构化查询，推荐使用 |
| `/mcp/admin/rpc` | 完全访问 | 包含所有工具权限 |
| `/mcp/business/rpc` | 简单查询 | 仅自然语言查询 |

## 验证配置

配置完成后，MCP 服务状态会显示在 MCP 列表中：

- **绿色**：连接成功
- **红色**：连接失败，点击查看错误信息

如遇问题，可查看日志：
- **Windows**: `C:\Users\<用户名>\AppData\Roaming\Trae CN\logs`
- **macOS**: `~/Library/Application Support/Trae CN/logs`

## 在智能体中使用

Trae 的 **Builder with MCP** 是内置智能体，配置的 MCP 服务会自动添加到该智能体中。

### 使用步骤

1. 打开 AI Chat 面板
2. 选择 **Builder with MCP** 智能体
3. 输入自然语言查询：

```
查询最近一周的销售数据，按商品分类汇总
```

### 添加到自定义智能体

你也可以将 MCP 服务添加到自定义智能体：

1. 进入 **智能体设置**
2. 在 **工具** 部分添加 `foggy-dataset`
3. 智能体将自动调用 MCP 工具完成任务

## 使用示例

### 数据查询

```
查询订单表最近 7 天的订单统计
```

### 数据分析

```
分析销售数据，找出销售额最高的 10 个品类
```

### 生成报表

```
生成本月各门店的销售对比报表
```

### 代码生成

```
查询用户表的字段结构，生成 TypeScript 接口定义
```

## 带认证的配置

如果 MCP 服务需要认证：

```json
{
  "mcpServers": {
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

可以同时配置多个 MCP 服务：

```json
{
  "mcpServers": {
    "sales-data": {
      "url": "http://sales-server:7108/mcp/analyst/rpc"
    },
    "inventory-data": {
      "url": "http://inventory-server:7108/mcp/analyst/rpc"
    }
  }
}
```

## 故障排查

### MCP 服务显示红色

1. 检查 MCP 服务是否运行：
   ```bash
   curl http://localhost:7108/actuator/health
   ```

2. 确认端点路径是否正确（使用 `/rpc`）

3. 检查 Trae 日志获取详细错误信息

### 配置不生效

1. 确保 JSON 格式正确
2. 重启 Trae IDE
3. 检查 URL 是否可访问

### 工具调用失败

1. 检查 MCP 服务日志
2. 确认数据源连接正常
3. 尝试使用更简单的查询测试

## 与其他 IDE 对比

| 特性 | Trae CN | Cursor | Claude Desktop |
|------|---------|--------|----------------|
| 免费使用 | ✅ | 部分免费 | 订阅制 |
| 内置模型 | Deepseek/Doubao | Claude | Claude |
| MCP 支持 | ✅ | ✅ | ✅ |
| 智能体 | ✅ 内置 | ✅ | ❌ |
| 代码集成 | ✅ | ✅ | ❌ |

## 参考链接

- [Trae 官网](https://www.trae.cn/)
- [Trae MCP 文档](https://docs.trae.cn/ide/model-context-protocol)

## 下一步

- [Cursor 集成](./cursor.md) - 在 Cursor 中使用
- [Claude Desktop 集成](./claude-desktop.md) - 在 Claude Desktop 中使用
- [工具列表](../tools/overview.md) - 查看所有可用工具
