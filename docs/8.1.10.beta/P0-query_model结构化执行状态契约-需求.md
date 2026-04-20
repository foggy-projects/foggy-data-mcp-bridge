# P0 — query_model 结构化执行状态契约（Java）— 需求

## 文档作用

- doc_type: `requirement`
- intended_for: `sub-agent`
- purpose: 为 Java 网关引擎定义 `query_model` 成功 / 失败结构化响应契约，并与 Python / Odoo 消费侧保持一致

## 基本信息

- 目标版本：`8.1.10.beta`
- 需求等级：`P0`
- 状态：`draft`
- 责任项目：`foggy-data-mcp-bridge`
- 上游文档：`docs/v1.2/P0-query_model结构化执行状态与导出候选过滤-需求.md`（workspace root）

## 当前问题

Java 侧 `dataset.query_model` 当前路径中：

- `QueryModelTool` 会把业务失败包装成 `RX.failB(...)` 或等效失败对象
- `McpService.handleToolsCall()` 会统一把工具结果塞进 `result.content[].text`

这对 LLM 可读，但对 Odoo 导出链路不够稳定：

- 失败查询没有稳定的结构化状态字段可读
- 下游需要理解文本或 `RX` 形态才能判断是否成功
- 与 Python 侧希望暴露的结构化契约无法自然对齐

## 需求范围

### 1. MCP 返回增加 result.status

对 `dataset.query_model` 的 MCP 返回，在 `result` 下新增：

- `status: success | failed`

成功示例：

```json
{
  "result": {
    "status": "success",
    "content": [
      {
        "type": "text",
        "text": "..."
      }
    ]
  }
}
```

失败示例：

```json
{
  "result": {
    "status": "failed",
    "content": [
      {
        "type": "text",
        "text": "查询被拒绝：column \"totalamount\" does not exist"
      }
    ]
  }
}
```

### 2. QueryModelTool / DatasetAccessor 返回语义

对 `dataset.query_model` 来说，业务级失败不能只停留在 `RX.failB` 文案层。

要求：

- 对查询拒绝、校验失败、执行失败等业务场景，最终对外必须能得到 `result.status=failed`
- 如果内部仍保留 `RX` 包装，也不能影响最终 MCP 结果中的 `status`
- 不要求本条改造所有工具，只聚焦 `dataset.query_model`

### 3. MCP tools/call 边界

`McpService.handleToolsCall()` 需要：

- 保留 `result.content[].text`
- 增加 `result.status`
- 让 Odoo 消费方可直接读取状态，而不必解析文本

顶层 MCP `error` 继续保留给协议级失败，不承担业务查询失败的主表达职责。

## 向后兼容约束

- 现有 `content[].text` 保持存在
- 现有成功查询结果 JSON 主体结构不被破坏
- 只对 `dataset.query_model` 增加 `result.status`，不扩散到无关工具

## Code Inventory

- path: `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/QueryModelTool.java`
  - role: `dataset.query_model` 工具返回语义
  - expected_change: `update`
  - notes: 不再让业务失败只留在 `RX.failB` 文案中

- path: `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/McpService.java`
  - role: MCP `tools/call` 外层包装
  - expected_change: `update`
  - notes: 在 `result` 下增加 `status`，同时保持 `content` 兼容

- path: `foggy-dataset-mcp/src/test/java`
  - role: 契约与 MCP 包装测试
  - expected_change: `update`
  - notes: 覆盖成功 / 失败两类 query_model 返回

## 验收标准

1. 非法 alias 排序场景下，Java `dataset.query_model` 返回：
   - `result.status=failed`
   - `result.content[].text` 中保留当前错误文案
2. 正常查询返回：
   - `result.status=success`
3. `McpService.handleToolsCall()` 返回的 MCP 结果包含：
   - `result.content`
   - `result.status`
4. Odoo 或其他调用方不需要通过字符串匹配来区分成功 / 失败
5. 现有成功查询的文本消费链路不回归

## 非目标

- 不在本条中改造 `dataset.get_metadata` 等其他工具
- 不在本条中引入新的前端或 Odoo UI 交互设计
- 不在本条中处理 v1.3 的导出预览与 checkbox 多选
