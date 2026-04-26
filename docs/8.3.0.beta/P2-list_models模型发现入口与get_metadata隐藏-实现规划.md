# P2-list_models模型发现入口与get_metadata隐藏-实现规划

## 文档作用

- doc_type: implementation-plan
- intended_for: execution-agent / reviewer
- purpose: 把 [P2-list_models模型发现入口与get_metadata隐藏-需求](./P2-list_models模型发现入口与get_metadata隐藏-需求.md) 拆成可执行实现规划，覆盖 Java（`foggy-data-mcp-bridge`）与 Python（`foggy-data-mcp-bridge-python`）两端的工具新增、配置注册、AI 流程切换、测试与跨端同步顺序
- 目标版本: 8.3.0.beta
- 责任仓: foggy-data-mcp-bridge（主） + foggy-data-mcp-bridge-python（同步）
- 状态: ready-for-execution
- 记录时间: 2026-04-26

## 关联文档

- 需求：[P2-list_models模型发现入口与get_metadata隐藏-需求](./P2-list_models模型发现入口与get_metadata隐藏-需求.md)
- 返回样例评估：[P2-get_metadata模型发现返回样例与瘦身评估](./P2-get_metadata模型发现返回样例与瘦身评估.md)
- 单模型元数据输出：[qm_describe.md](./qm_describe.md)

## 目标范围

本规划仅覆盖：

- 新增 `dataset.list_models` 工具（Java + Python）
- AI 首轮模型发现入口由 `dataset.get_metadata` 切换为 `dataset.list_models`
- `dataset.get_metadata` 描述文件加 deprecation 提示（Phase 1）
- `dataset.get_metadata` 在观察期后改 `enabled: false`（Phase 2，本规划不实施，但定义启动条件）

不进入本期范围：

- 删除 `dataset.get_metadata` 源码或类
- 新增字段搜索工具（如 `dataset.search_fields`）
- 改造 `dataset.describe_model_internal` 既有职责
- 引入新的 `visible / aiVisible` 配置字段（已在需求评审阶段否决）
- 改动现有字段级权限、物理列权限、namespace 传递规则

## 前置依赖

- 无阻断性前置依赖
- Java 与 Python 当前 main 分支均稳定（参考 CLAUDE.md 测试基线）
- Python 侧 `scripts/sync_mcp_schemas.py` 工作正常（用于 Java→Python schema/描述同步）

## 实施原则

### 1. Java 是 schema / 描述文件的权威来源

依据 Python 仓 CLAUDE.md：

> 工具定义从 `src/foggy/mcp/schemas/` 加载（JSON schema + Markdown 描述），与 Java 共享同一套文件。

新增 `list_models_schema.json` 与 `list_models.md` 描述文件**只在 Java 仓写一次**，通过 `python scripts/sync_mcp_schemas.py` 同步到 Python 仓。两端不允许出现独立维护的副本。

### 2. 两端工具实现各自负责，行为契约一致

`ListModelsTool` 类的实现语言不同（Java 的 `ToolCategory.METADATA` 注解 + Spring 注入；Python 的 `BaseMcpTool` 子类 + `ClassVar` 元数据），但必须满足：

- 工具名相同：`dataset.list_models`
- 类目相同：`METADATA`
- 无参调用即可
- 返回 Markdown 业务包络一致（`{ code, data: { format, content, data } }`）
- Markdown 内容字段一致（模型名 / 简称 / 说明 / 主时间轴 / 适用问题 / 推荐下一步）

### 3. 分阶段下线 `get_metadata`，复用现有 `enabled` 字段

不引入新字段。Phase 1 保留 `enabled: true` + 描述加 deprecation；Phase 2 改 `enabled: false`。详见需求 §4。

### 4. 测试逐层落地，不做"只验 SQL 字符串"式测试

参考根 CLAUDE.md "集成测试规范"原则：单元测试覆盖工具内部分支；MCP controller 测试覆盖 `tools/list` / `tools/call` 真实链路；AI 流程测试覆盖期望工具调用顺序。

## 里程碑拆分

里程碑顺序按依赖关系排列。M6 是跨端同步关口，M7-M8 必须在 M6 之后执行。

| ID | 里程碑 | 仓库 | 依赖 | 完成定义 |
|----|--------|------|------|----------|
| M1 | schema + 描述文件 | Java | — | `list_models_schema.json` / `list_models.md` 落盘，描述文件四要点齐备 |
| M2 | `ListModelsTool` 实现 + 默认注册 | Java | M1 | 类创建并通过 `ToolConfigLoader` 默认注册，`application.yml` 中 launcher 默认列表含 `dataset.list_models` |
| M3 | `get_metadata` 描述加 deprecation | Java | M1 | `schemas/descriptions/get_metadata.md` 首段含 deprecation 文案，移除"首轮发现"鼓励语 |
| M4 | AI 流程切换 | Java | M2, M3 | `QueryExpertService` 系统提示词、`SpringAiTestExecutor`、`ai-test-cases/ecommerce-tests.json` 中首轮工具改为 `dataset.list_models` |
| M5 | Java 测试覆盖 | Java | M2, M3, M4 | 单元测试 / MCP controller 测试 / AI 流程测试全绿（详见 §测试矩阵） |
| M6 | 跨端 schema 同步 | Python | M1, M3 | `python scripts/sync_mcp_schemas.py` 执行，diff 仅含 `list_models.*` 新增 + `get_metadata.md` 改动 |
| M7 | `ListModelsTool` 实现 + 注册 | Python | M6 | `src/foggy/mcp/tools/list_models_tool.py` 创建并通过 `tool_dispatcher` 注册；`get_metadata` deprecation 提示在 Python 侧也生效（依赖 M6 同步） |
| M8 | Python 测试覆盖 | Python | M7 | pytest 通过 `test_list_models_tool.py` + `test_mcp_rpc_list_models.py`（详见 §测试矩阵） |
| M9 | 双端联调与验收准备 | 跨仓 | M5, M8 | 两侧 launcher 启动后手动调用 `dataset.list_models`，输出对照一致；需求 §验收标准 Phase 1 全部勾选 |

依赖关系图（M2、M3 互不依赖，可并行）：

```
M1 ─┬─> M2 ─┐
    │       ├─> M4 ─> M5 ─┐
    └─> M3 ─┘             │
                          ├─> M9
M1, M3 ─> M6 ─> M7 ─> M8 ─┘
```

## 文件清单

### Java 仓（`foggy-data-mcp-bridge-wt-dev-compose/`）

新增：

- `foggy-dataset-mcp/src/main/resources/schemas/list_models_schema.json`
- `foggy-dataset-mcp/src/main/resources/schemas/descriptions/list_models.md`
- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/ListModelsTool.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/tools/ListModelsToolTest.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/controller/ListModelsControllerTest.java`（如果与现有 metadata controller 测试共用，则增量补用例）

修改：

- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/ToolConfigLoader.java` —— 默认工具列表追加 `dataset.list_models`
- `foggy-mcp-launcher/src/main/resources/application.yml` —— `foggy.mcp.tools` 数组追加 `dataset.list_models` 配置
- `foggy-dataset-mcp/src/main/resources/schemas/descriptions/get_metadata.md` —— 首段加 deprecation 文案
- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/QueryExpertService.java` —— 系统提示词首轮调用改 `dataset_list_models`，工具列表替换
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/SpringAiTestExecutor.java` —— expected tool 切换
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json` —— expected_tool 切换

可能需要扩展（按 M2 实施时按需决定）：

- `DatasetAccessor` 接口新增 `listModels(...)` 方法 + `LocalDatasetAccessor` 实现 —— 若短期内不扩接口，工具内部直接复用 `SemanticServiceResolver.getAllModelNames()`，但实施时记录在 progress 文档中

### Python 仓（`foggy-data-mcp-bridge-python/`）

新增：

- `src/foggy/mcp/tools/list_models_tool.py`
- `tests/mcp/tools/test_list_models_tool.py`
- `tests/mcp/test_mcp_rpc_list_models.py`（如已有 `test_mcp_rpc_*.py` 则增量补用例）

由 M6 同步落盘（不手工编辑）：

- `src/foggy/mcp/schemas/list_models_schema.json`
- `src/foggy/mcp/schemas/descriptions/list_models.md`
- `src/foggy/mcp/schemas/descriptions/get_metadata.md`（更新）

修改：

- `src/foggy/mcp/services/tool_dispatcher.py` 或 `services/dispatcher.py` —— 注册 `ListModelsTool` 默认实例
- `src/foggy/mcp/launcher/app.py` 或对应工厂代码 —— 装配 `ListModelsTool` 与 `QueryService` 依赖

### 不修改

- Java `McpToolDispatcher` / `ToolFilterService` / `McpToolCallbackFactory` / `HealthController` / `DevToolsController`（无需引入可见性字段）
- Python `query_service.py` / `mcp_service.py` 已有公共能力，应直接复用，不重写

## 测试矩阵

### Java 单元测试

| 测试类 | 用例 |
|--------|------|
| `ListModelsToolTest` | 工具名 / category / 无参调用 / 返回内容包含模型名+简称+说明+主时间轴 / 不含 `[field:` 索引 / 每模型最多 1 行表格+1 行说明 |
| `ToolConfigLoaderTest` | 默认工具列表包含 `dataset.list_models`；YAML 覆盖不丢失内置默认 |
| `McpToolDispatcherTest` | `getToolDefinitions()` 包含 `dataset.list_models`；Phase 1 下 `dataset.get_metadata` 描述含 deprecation 文本 |

### Java MCP controller 测试

| 端点 | 用例 |
|------|------|
| `/mcp/analyst/rpc tools/list` | 返回包含 `dataset.list_models`；Phase 1 仍包含 `dataset.get_metadata` |
| `/mcp/analyst/rpc tools/call` | `dataset.list_models` 返回 `code: 200` 且 Markdown 不含 `[field:` |
| `/mcp/business/rpc` | 业务角色不变，不暴露 `dataset.list_models`（应只暴露自然语言工具） |

### Java AI 流程测试

| 用例文件 | 期望 |
|----------|------|
| `ai-test-cases/ecommerce-tests.json` | 首轮 expected_tool = `dataset.list_models`；下一步如需字段详情 = `dataset.describe_model_internal`；不出现 `dataset.get_metadata` 作为首轮 |

### Python 测试

| 测试文件 | 用例 |
|----------|------|
| `test_list_models_tool.py` | 工具名 / category / 无参调用 / Markdown 字段 / 不含字段索引 / 行数约束 |
| `test_mcp_rpc_list_models.py` | `tools/list` 包含 `dataset.list_models`；`tools/call` 调用成功并返回 Markdown 业务包络 |
| 已有 `test_mcp_rpc_*` | 不能因新增工具失败 |

### 回归

- Java：`mvn test -pl foggy-dataset-mcp`
- Python：`python -m pytest tests/mcp/ -q`
- 手动：双端 launcher 启动后分别 POST `tools/call` `dataset.list_models`，比对 Markdown 内容字段一致

## 阶段策略对齐

需求文档将下线分为 Phase 1 / Phase 2，本规划在两侧同步执行：

| 阶段 | Java 动作 | Python 动作 | 验证 |
|------|-----------|-------------|------|
| Phase 1 | M1-M5 全部完成 | M6-M8 全部完成 | 需求 §验收标准 Phase 1 七项全勾 |
| Phase 2 | `application.yml` 中 `dataset.get_metadata` 改 `enabled: false` | 同步配置（如 Python 侧有等价配置开关）或同样跳过注册 | 审计日志统计连续 14 天分析师/管理员对 `dataset.get_metadata` 调用计数趋近 0 |

Phase 2 不在本规划交付范围内，但启动条件需在 Phase 1 验收时记录到 progress 文档。

## 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| Python 侧 `sync_mcp_schemas.py` 与 Java 侧版本漂移 | M6 同步出错或漏文件 | M6 后强制运行 `python scripts/sync_mcp_schemas.py --diff` 复核，仅允许 `list_models.*` + `get_metadata.md` 两类 diff |
| 两侧 `ListModelsTool` 输出 Markdown 字段顺序/标点不一致 | AI 在两端表现不同 | 在 M9 双端联调时 diff 输出，必要时把"模板字符串拼接"统一抽到 schemas 描述文件作为示例固定 |
| `DatasetAccessor.listModels` 接口扩展引发其他实现类编译失败 | 编译 / 运行时报错 | M2 实施前先盘点 `DatasetAccessor` 实现类清单；如不止 `LocalDatasetAccessor`，则**默认走"工具内部直接复用 resolver 能力"**，不扩接口 |
| AI 端缓存了旧 `tools/list`，仍调用 `dataset.get_metadata` | Phase 1 期间正常，但 Phase 2 切换会出现 `tool not found` | Phase 1 描述文件首段写明 deprecation 提示，让 LLM 自然引导切换；Phase 2 启动前观察审计日志确认调用计数趋近 0 |
| Python 仓 `tools` 注册位置与 Java 不对称 | 工具新增后未生效 | M7 实施时 grep `MetadataTool` 出现位置作为参考点，对应位置都新增 `ListModelsTool` |

## 完成定义（DoD）

整个规划完成的标志（与 M9 一致）：

- [ ] Java 侧 §文件清单 全部落盘 / 修改完成
- [ ] Python 侧 §文件清单 全部落盘 / 修改完成
- [ ] §测试矩阵 全部用例通过
- [ ] `python scripts/sync_mcp_schemas.py --diff` 干净（无未同步项）
- [ ] 双端 launcher 各自启动，手动调用 `dataset.list_models` 输出 Markdown 内容字段一致
- [ ] 需求 §验收标准 Phase 1 七项全部勾选
- [ ] Phase 2 启动条件已写入 progress 文档（"上线 14 天后启动审计日志统计"）

## Progress Tracking 占位

实施过程中请同步更新一份 `P2-list_models模型发现入口与get_metadata隐藏-progress.md`（与本规划同目录），按里程碑 M1-M9 维护状态、证据链接与遗留项。
