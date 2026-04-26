# P2-list_models模型发现入口与get_metadata隐藏-需求

## 文档作用

- doc_type: workitem / requirement
- intended_for: execution-agent / reviewer
- purpose: 定义新增 `dataset.list_models` 轻量模型发现工具，将 AI 首轮模型发现从 `dataset.get_metadata` 切换到 `dataset.list_models`，并复用 `application.yml` 中现有 `enabled` 字段对 `dataset.get_metadata` 做分阶段下线（Phase 1 deprecation 提示，Phase 2 关闭注册）
- 目标版本: 8.3.0.beta
- 需求等级: P2
- 状态: approved-for-implementation
- source type: optimization / API-contract clarification
- 责任仓: foggy-data-mcp-bridge
- 责任模块: foggy-dataset-mcp / foggy-mcp-launcher
- 记录时间: 2026-04-26

## 关联文档

- 实现规划：[P2-list_models模型发现入口与get_metadata隐藏-实现规划](./P2-list_models模型发现入口与get_metadata隐藏-实现规划.md)
- 返回样例与瘦身评估：[P2-get_metadata模型发现返回样例与瘦身评估](./P2-get_metadata模型发现返回样例与瘦身评估.md)
- 元数据时间维度分析：[P2-Metadata时间维度与属性分析报告](./P2-Metadata时间维度与属性分析报告.md)
- 单模型元数据输出：[qm_describe.md](./qm_describe.md)
- 时间维度样例行需求：[P2-Metadata时间维度样例行-需求](./P2-Metadata时间维度样例行-需求.md)

## 背景

2026-04-26 本地启动 `foggy-mcp-launcher` 后实测 `dataset.get_metadata`，demo 环境 5 个模型即返回：

| 指标 | 数值 |
|------|------|
| 内层 Markdown 字符数 | 8257 |
| 内层 Markdown 行数 | 405 |
| 模型数量 | 5 |
| 字典行数 | 10 |
| `[field:*]` 字段索引数量 | 127 |

这说明 `dataset.get_metadata` 当前不仅承担“发现所有模型”的职责，还承担全局字段索引、字典枚举等职责。对 AI 首轮路由来说，返回内容偏重，且会随着模型数量线性膨胀。

已确认的产品决策：

1. 新增 `dataset.list_models`，作为 AI 首轮发现模型的正式入口。
2. `dataset.list_models` 完成后，AI 工具提示词、QueryExpertService、测试用例与文档全部切换到 `list_models`。
3. `dataset.get_metadata` 通过现有 `application.yml` 的 `enabled` 字段做分阶段下线：Phase 1 仍保持 `enabled: true`，但描述文件加 deprecation 提示并从 AI 提示词中移除；Phase 2 在审计日志确认无活跃调用后改为 `enabled: false`。不引入新的可见性配置字段。

## 澄清后的工具职责

| 工具 | 新职责 | 阶段策略 |
|------|--------|----------|
| `dataset.list_models` | 轻量模型发现与路由 | 新增，作为首选入口 |
| `dataset.describe_model_internal` | 单模型完整字段、字典、时间维度、样例行 | 保持不变 |
| `dataset.get_metadata` | 旧的"全局语义索引 + 全模型详情" | Phase 1 保留 `enabled: true` 但描述文件加 deprecation 提示；Phase 2 在 audit 日志确认 AI 不再调用后改为 `enabled: false` |

复用现有 `application.yml` 中 `foggy.mcp.tools[*].enabled` 字段做分阶段下线，不引入新的可见性字段：

- Phase 1：`enabled: true`，`tools/list` 仍包含 `get_metadata`，但描述文件首段写明"已废弃，请改用 `dataset.list_models` + `dataset.describe_model_internal`"。AI 通过更新后的提示词和工具描述自然迁移；少量缓存了旧工具列表的客户端在过渡期内仍可继续工作。
- Phase 2：`enabled: false`，`tools/list` 不再包含 `get_metadata`，直接 `tools/call` 返回标准 `tool not found` 错误（明确信号，非静默错误）。Java 类源码保留，可日后通过开关重新启用，不强制删除实现。
- Phase 2 启动条件：上线 N 天（建议 14 天）后审计日志中 `/mcp/analyst/rpc` 角色对 `dataset.get_metadata` 的调用计数趋近 0。

## 目标返回格式

`dataset.list_models` 默认无参，返回 Markdown。业务包络继续沿用当前 MCP 工具返回约定：

```json
{
  "code": 200,
  "data": {
    "format": "markdown",
    "content": "# 数据模型列表\n...",
    "data": null
  }
}
```

Markdown 建议格式：

```markdown
# 数据模型列表

| 模型 | 简称 | 说明 | 主时间轴 | 适用问题 | 推荐下一步 |
|------|------|------|----------|----------|------------|
| FactSalesQueryModel | FS | 销售明细查询 | salesDate$id | 销售额、销量、商品/门店/客户维度分析 | dataset.describe_model_internal |
| FactOrderQueryModel | FO | 订单查询 | orderDate$id | 订单数、订单状态、订单金额分析 | dataset.describe_model_internal |
| FactPaymentQueryModel | FP | 支付查询 | payDate$id | 支付金额、支付方式、支付状态分析 | dataset.describe_model_internal |

## 使用规则

- 先根据用户问题选择一个最匹配模型。
- 不确定字段时，调用 `dataset.describe_model_internal` 获取单模型详情。
- 不要调用 `dataset.get_metadata` 作为首轮模型发现入口。
```

## 返回内容约束

`dataset.list_models` 只允许返回路由必要信息：

- 模型名称
- 模型简称
- 模型说明 / caption
- 主业务时间轴，优先取 `timeRole=business_date`
- 适用问题或核心业务场景的短描述
- 推荐下一步工具

默认不返回：

- 全量字段索引
- 全量字典枚举
- 维度成员样例
- 物理表和物理列明细
- 大段字段说明

体量目标：

- demo 5 模型场景下，Markdown 建议不超过 2000 字符。
- 字段索引数量必须为 0；该工具不是字段索引工具。
- 每个模型最多 1 行主体信息，必要补充最多 1 行说明。

## 实现范围

### 1. 新增工具实现

建议新增：

```text
foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/ListModelsTool.java
```

工具名：

```text
dataset.list_models
```

工具分类：

```text
ToolCategory.METADATA
```

建议调用链：

```text
ListModelsTool
  -> DatasetAccessor.listModels(...)
  -> LocalDatasetAccessor
  -> SemanticServiceResolver.getAllModelNames()
  -> QueryModelLoader / QM metadata
```

如短期不扩展 `DatasetAccessor` 接口，也可以先在工具内部复用现有 resolver 能力，但最终建议沉到 accessor 层，方便 local / remote 两种模式统一。

### 2. 新增 schema 与描述文件

新增：

```text
foggy-dataset-mcp/src/main/resources/schemas/list_models_schema.json
foggy-dataset-mcp/src/main/resources/schemas/descriptions/list_models.md
```

schema 初始可为空对象：

```json
{
  "type": "object",
  "properties": {},
  "additionalProperties": false
}
```

描述文件必须明确：

- 这是发现所有可用模型的首选工具。
- 工具只返回模型路由信息，不返回字段明细。
- 需要字段明细时调用 `dataset.describe_model_internal`。
- 不应为了首轮模型发现调用 `dataset.get_metadata`。

### 3. 注册默认工具

更新：

```text
foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/ToolConfigLoader.java
foggy-mcp-launcher/src/main/resources/application.yml
```

要求：

- `dataset.list_models` 作为内置默认工具注册。
- `foggy-mcp-launcher` 默认配置中显式列出 `dataset.list_models`。
- 工具描述和 schema 从资源文件加载。

### 4. get_metadata 分阶段下线

复用 `application.yml` 已有的 `foggy.mcp.tools[*].enabled` 字段，不新增可见性字段。

#### Phase 1：本批次随 list_models 一起上线

- `application.yml` 中 `dataset.get_metadata` 保持 `enabled: true`（或省略，默认即为 true）。
- 更新 `dataset.get_metadata` 的描述文件（`schemas/descriptions/get_metadata.md`）：
  - 首段加 deprecation 提示，例如：
    > ⚠️ Deprecated. 模型发现请改用 `dataset.list_models`，单模型详情请改用 `dataset.describe_model_internal`。本工具计划在后续版本下线。
  - 移除原本鼓励 AI 用本工具做"首轮发现"的措辞。
- 同步更新 QueryExpertService 系统提示词与 AI 测试用例（详见 §5）。
- 不需要改动 `McpToolDispatcher / ToolFilterService / McpToolCallbackFactory / HealthController / DevToolsController`，工具仍正常注册并对外暴露。

#### Phase 2：观察期后下线（不在本批次硬性要求）

- 启动条件：上线 14 天后，审计日志中 `/mcp/analyst/rpc` 与 `/mcp/admin/rpc` 角色对 `dataset.get_metadata` 的调用计数趋近 0。
- 操作：在 `application.yml` 中将 `dataset.get_metadata` 改为：

  ```yaml
  - name: "dataset.get_metadata"
    enabled: false
    descriptionFile: "classpath:/schemas/descriptions/get_metadata.md"
    schemaFile: "classpath:/schemas/get_metadata_schema.json"
    category: METADATA
  ```

- 行为：`tools/list` 不再返回该工具；直接 `tools/call` 返回标准 `tool not found` 错误。
- Java 类源码保留，需要时可通过翻回 `enabled: true` 快速恢复。

### 5. 切换 AI 内置流程

更新：

```text
foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/QueryExpertService.java
foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/SpringAiTestExecutor.java
foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json
```

要求：

- 系统提示词中的“首先调用 dataset_get_metadata”改为“首先调用 dataset_list_models”。
- QueryExpertService 的核心工具列表移除 `dataset.get_metadata`，加入 `dataset.list_models`。
- AI 测试期望工具从 `dataset.get_metadata` 切到 `dataset.list_models`。

## 非目标

- 本需求不删除 `dataset.get_metadata` 源码。
- 本需求不改变 `dataset.describe_model_internal` 的完整元数据职责。
- 本需求不在 `dataset.list_models` 中返回字段索引。
- 本需求不引入分页字段搜索工具；如需字段搜索，可后续另立 `dataset.search_fields`。
- 本需求不改变现有字段级权限、物理列权限和 namespace 传递规则。

## 测试计划

### 单元测试

- `ListModelsToolTest`
  - 工具名为 `dataset.list_models`
  - category 为 `METADATA`
  - 无参调用成功
  - 返回内容包含模型名称、简称、说明、主时间轴
  - 返回内容不包含 `[field:` 字段索引

- `ToolConfigLoaderTest`
  - 默认工具包含 `dataset.list_models`
  - YAML 覆盖不丢失内置默认工具

- `McpToolDispatcherTest`
  - `getToolDefinitions()` 包含 `dataset.list_models`
  - Phase 1 下 `dataset.get_metadata` 仍正常注册，描述文件包含 deprecation 提示文本

### Controller / MCP 测试

- `/mcp/analyst/rpc` `tools/list`
  - Phase 1：返回 `dataset.list_models`，仍返回 `dataset.get_metadata`（描述文件含 deprecation 文本）
  - Phase 2 验收时再补：不返回 `dataset.get_metadata`

- `/mcp/analyst/rpc` `tools/call`
  - `dataset.list_models` 调用成功
  - Phase 1：`dataset.get_metadata` 调用仍成功，返回内容不变
  - Phase 2：`dataset.get_metadata` 返回标准 `tool not found`

- `/mcp/business/rpc`
  - 业务角色仍只暴露自然语言工具，不因新增 metadata 工具改变权限面

### AI 流程测试

- 首轮模型发现期望工具改为 `dataset.list_models`
- 当用户问题需要字段详情时，下一步调用 `dataset.describe_model_internal`
- 不再出现 AI 首轮调用 `dataset.get_metadata` 的测试基线

### 回归验证

- `mvn test -pl foggy-dataset-mcp`
- 如改动 accessor 或 semantic service，补充相关模块测试
- 启动 `foggy-mcp-launcher` 后手动调用：

```json
{
  "jsonrpc": "2.0",
  "id": "list-models",
  "method": "tools/call",
  "params": {
    "name": "dataset.list_models",
    "arguments": {}
  }
}
```

## 验收标准

### Phase 1（本批次硬性）

- [ ] `tools/list` 中出现 `dataset.list_models`
- [ ] `dataset.list_models` 返回轻量模型列表，不包含 `[field:*]` 全局字段索引
- [ ] demo 5 模型场景下，`dataset.list_models` Markdown 每模型最多 1 行表格 + 1 行说明
- [ ] AI 内置提示词和 QueryExpertService 首轮入口切换为 `dataset.list_models`
- [ ] `dataset.describe_model_internal` 仍作为单模型详情入口
- [ ] `dataset.get_metadata` 描述文件首段包含明确 deprecation 提示，引导改用 `list_models` + `describe_model_internal`
- [ ] 单元测试、MCP controller 测试、AI 流程测试均覆盖新工具

### Phase 2（观察期后再启动）

- [ ] 上线 14 天后，审计日志中分析师 / 管理员角色对 `dataset.get_metadata` 的调用计数趋近 0
- [ ] `application.yml` 中 `dataset.get_metadata` 改为 `enabled: false`
- [ ] `tools/list` 不再返回 `dataset.get_metadata`，直接 `tools/call` 返回标准 `tool not found`

## Progress Tracking

### Development Progress

| 项目 | 状态 | 说明 |
|------|------|------|
| 需求决策 | done | 已确认新增 `dataset.list_models`，复用 `enabled` 字段分阶段下线 `dataset.get_metadata` |
| 工具实现 | pending | 待新增 `ListModelsTool` 与 accessor 能力 |
| 工具注册 | pending | 待更新默认工具配置与 launcher 配置 |
| Phase 1 deprecation 提示 | pending | 待更新 `get_metadata` 描述文件首段加 deprecation 文案 |
| AI 流程切换 | pending | 待更新 QueryExpertService、测试提示词与 AI 测试基线 |
| Phase 2 下线 | deferred | 上线 14 天观察后启动；本批不实施 |
| 文档同步 | in-progress | 本文已记录需求，后续实现完成后需回写结果 |

### Testing Progress

| 测试项 | 状态 | 说明 |
|--------|------|------|
| 单元测试 | pending | 待实现后新增/更新 |
| MCP controller 测试 | pending | 待覆盖 tools/list 与 tools/call |
| AI 流程测试 | pending | 待切换 expected_tool |
| 手工验证 | pending | 待启动服务后调用 `dataset.list_models` |

### Experience Progress

N/A。该需求为 MCP 后端工具契约与 AI 工具可见性调整，不涉及前端页面、表单、列表或交互流程。

## 执行检查清单

- [ ] 明确 `list_models` 返回模型列表，不返回字段索引
- [ ] 复用 `application.yml` 中现有 `enabled` 字段做分阶段下线，不引入新的可见性字段
- [ ] Phase 1：更新 `get_metadata` 描述文件首段加 deprecation 提示
- [ ] 更新 QueryExpertService 首轮提示词
- [ ] 更新 AI 测试用例 expected tool
- [ ] 更新 tool description 中所有"MODEL_NOT_FOUND -> get_metadata"类提示，改指向 `list_models` / `describe_model_internal`
- [ ] 手动验证 `tools/list` 包含 `list_models`
- [ ] Phase 2 启动前：审计日志统计 `get_metadata` 调用计数，确认趋近 0 后再切 `enabled: false`
