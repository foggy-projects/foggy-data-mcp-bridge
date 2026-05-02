---
type: execution-prompt
version: 8.3.0.beta
req_id: P2-list_models模型发现入口与get_metadata隐藏
parent_req: P2-list_models模型发现入口与get_metadata隐藏-需求
target_repos: foggy-data-mcp-bridge + foggy-data-mcp-bridge-python
target_modules: foggy-dataset-mcp / foggy-mcp-launcher (Java) · foggy.mcp (Python)
phase: Phase 1（本批次硬性）· Phase 2 不在本次交付范围
status: ready-for-execution
---

# `dataset.list_models` 双引擎落地 · 开工提示词

## 执行位置（读在最前）

- **Java 工作目录**：`D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
  - 这是 8.3.0.beta worktree。所有 Java 改动只在这里写
  - 文档里写 `foggy-data-mcp-bridge/...` 形式的路径，物理位置都在该 worktree 下
  - **不要**往 mainline `foggy-data-mcp-bridge/` 目录写入
  - Maven 命令在 worktree 根目录执行：`mvn test -pl foggy-dataset-mcp ...`
- **Python 工作目录**：`D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-python`
  - 独立仓，路径如其字面，不是 worktree
  - pytest 命令：`python -m pytest tests/mcp/ -q`
- **schema/描述文件权威源在 Java 仓**，通过 `python scripts/sync_mcp_schemas.py` 单向同步到 Python 仓。**不要在 Python 仓手工编辑** `src/foggy/mcp/schemas/` 下任何文件。

## 角色与语境设定

你是这次 8.3.0.beta `dataset.list_models` 双引擎落地的执行人。本次工作要在 Java（`foggy-data-mcp-bridge`）和 Python（`foggy-data-mcp-bridge-python`）两个仓同时落地一个新的轻量级模型发现工具，并把 AI 首轮发现入口从 `dataset.get_metadata` 切换过来。

`dataset.get_metadata` 不删除、不隐藏、不新增可见性字段——只在描述文件首段加 deprecation 提示，等观察期 14 天后再走 Phase 2（不在本次交付范围）。

## 必读前置

按顺序读完再动手：

1. **需求文档**：`foggy-data-mcp-bridge/docs/8.3.0.beta/P2-list_models模型发现入口与get_metadata隐藏-需求.md`
   - 重点：§澄清后的工具职责（理解为什么不引入 `visible` 字段）、§返回内容约束、§验收标准 Phase 1
2. **实现规划**：`foggy-data-mcp-bridge/docs/8.3.0.beta/P2-list_models模型发现入口与get_metadata隐藏-实现规划.md`
   - 这是你的工作蓝图。重点章节：§里程碑拆分（M1-M9 依赖图）、§文件清单、§测试矩阵、§风险与缓解
3. **返回样例评估**（背景参考，可略读）：`foggy-data-mcp-bridge/docs/8.3.0.beta/P2-get_metadata模型发现返回样例与瘦身评估.md`
4. **Java 仓 CLAUDE.md**：worktree 根目录下 `CLAUDE.md`
   - 重点：§MCP 端点（角色端点 / Namespace 传递链）、§API 开发规范（RX 统一返回）
5. **Python 仓 CLAUDE.md**：`foggy-data-mcp-bridge-python/CLAUDE.md`
   - 重点：§MCP 工具（对齐 Java）、§同步 MCP 工具定义（`sync_mcp_schemas.py`）
6. **现有同类工具的实现样例**（作为风格参考，不要复制粘贴）：
   - Java：`foggy-dataset-mcp/src/main/resources/schemas/get_metadata_schema.json` + `descriptions/get_metadata.md`
   - Java：`foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/config/McpProperties.java`（看 `ToolConfigItem` 字段）
   - Java：`foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/ToolConfigLoader.java`（看默认工具如何注册）
   - Java：`foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/McpToolDispatcher.java`
   - Java：`foggy-mcp-launcher/src/main/resources/application.yml`（第 120-157 行 `foggy.mcp.tools` 数组）
   - Python：`foggy-data-mcp-bridge-python/src/foggy/mcp/tools/metadata_tool.py`（看 `BaseMcpTool` 子类风格）
   - Python：`foggy-data-mcp-bridge-python/src/foggy/mcp/services/tool_dispatcher.py`

## 工作步骤（按 M1-M9 顺序）

### Step 0：起手准备

1. 在 worktree 根目录跑一次 baseline：

   ```bash
   mvn test -pl foggy-dataset-mcp -q
   ```

   记录通过数，作为基线。

2. 在 Python 仓跑一次 baseline：

   ```bash
   cd D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-python
   python -m pytest tests/mcp/ -q
   ```

   同样记录基线。

3. 创建空的 progress 文档（在 Java 仓，与需求/规划同目录），按 M1-M9 留好行：

   ```text
   foggy-data-mcp-bridge/docs/8.3.0.beta/P2-list_models模型发现入口与get_metadata隐藏-progress.md
   ```

   先写一行 status: `in-progress`，里程碑表格全部 pending。

### Step 1：M1 · Java 侧 schema + 描述文件

新增两个资源文件：

- `foggy-dataset-mcp/src/main/resources/schemas/list_models_schema.json`
  - schema 内容：空对象（无入参），`additionalProperties: false`
  - 参照需求文档 §实现范围 §2 中的示例
- `foggy-dataset-mcp/src/main/resources/schemas/descriptions/list_models.md`
  - 必须包含以下要点（参照需求 §实现范围 §2）：
    - 这是发现所有可用模型的首选工具
    - 工具只返回模型路由信息，不返回字段明细
    - 需要字段明细时调用 `dataset.describe_model_internal`
    - 不应为了首轮模型发现调用 `dataset.get_metadata`
  - 简洁优先，参考 `descriptions/get_metadata.md` 的篇幅

完成后更新 progress 文档 M1 行为 done。

### Step 2：M2 · Java 侧 `ListModelsTool` 实现 + 默认注册

新增类：

- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/ListModelsTool.java`
  - 工具名：`dataset.list_models`
  - 工具分类：`ToolCategory.METADATA`
  - 无参调用，返回 Markdown 业务包络（`{ code, data: { format, content, data } }`）
  - 输出 Markdown 字段必须包含：模型名 / 简称 / 说明 / 主时间轴（优先 `timeRole=business_date`）/ 适用问题 / 推荐下一步
  - 输出**严禁**包含 `[field:` 字段索引、字典枚举、维度成员样例、物理表/列明细
  - 每模型最多 1 行表格 + 1 行说明（硬约束）
  - 调用链按需求 §实现范围 §1 的建议；如不扩展 `DatasetAccessor` 接口，可在工具内部直接复用 `SemanticServiceResolver.getAllModelNames()`，但要在 progress 文档中记录该决定
  - 参考 Java 仓现有 metadata 工具的实现风格

修改：

- `ToolConfigLoader.java`：默认工具列表追加 `dataset.list_models`
- `application.yml`：第 120-157 行的 `foggy.mcp.tools` 数组追加：

  ```yaml
  - name: "dataset.list_models"
    descriptionFile: "classpath:/schemas/descriptions/list_models.md"
    schemaFile: "classpath:/schemas/list_models_schema.json"
    category: METADATA
  ```

完成后更新 progress 文档 M2 行为 done。

### Step 3：M3 · Java 侧 `get_metadata` 描述加 deprecation

修改：

- `foggy-dataset-mcp/src/main/resources/schemas/descriptions/get_metadata.md`
  - 首段插入 deprecation 提示，例如：
    > ⚠️ Deprecated. 模型发现请改用 `dataset.list_models`，单模型详情请改用 `dataset.describe_model_internal`。本工具计划在后续版本下线。
  - 移除原本鼓励 AI 用本工具做"首轮发现"的措辞（保留功能描述与参数说明）

**不要修改** `application.yml` 中 `dataset.get_metadata` 的 `enabled` 状态，本次保持 `enabled: true`。

M2 / M3 互不依赖可以并行。

### Step 4：M4 · Java 侧 AI 流程切换

修改：

- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/QueryExpertService.java`
  - 系统提示词中所有"首先调用 `dataset_get_metadata`" → "首先调用 `dataset_list_models`"
  - 核心工具列表移除 `dataset.get_metadata`，加入 `dataset.list_models`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/SpringAiTestExecutor.java`
  - 期望工具切换
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
  - 用例中 expected_tool 切换
- 检查 grep `MODEL_NOT_FOUND` 在 tool descriptions 与代码注释中，所有"建议改用 `get_metadata`"类提示改指向 `list_models` / `describe_model_internal`

### Step 5：M5 · Java 测试覆盖

新增/补充：

- `ListModelsToolTest`：工具名 / category / 无参调用 / 返回 Markdown 字段 / 不含 `[field:` / 行数约束
- `ToolConfigLoaderTest`：默认工具列表包含 `dataset.list_models`，YAML 覆盖不丢失内置默认
- `McpToolDispatcherTest`：`getToolDefinitions()` 包含 `dataset.list_models`；Phase 1 下 `dataset.get_metadata` 描述含 deprecation 文本
- MCP controller 测试：`/mcp/analyst/rpc tools/list` 包含 `dataset.list_models`；`tools/call` 调用成功；`/mcp/business/rpc` 不暴露
- AI 流程测试：首轮 expected_tool = `dataset.list_models`，下一步如需字段详情 = `dataset.describe_model_internal`

跑：

```bash
mvn test -pl foggy-dataset-mcp -q
```

必须**绿且测试数 ≥ 基线 + 新增**。失败先修，不要囫囵吞枣。

### Step 6：M6 · 跨端 schema 同步

切换到 Python 仓：

```bash
cd D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-python
```

先 dry-run 看 diff：

```bash
python scripts/sync_mcp_schemas.py --diff
```

确认 diff 仅包含：
- 新增：`schemas/list_models_schema.json`、`schemas/descriptions/list_models.md`
- 改动：`schemas/descriptions/get_metadata.md`（deprecation 段落）

如有其他文件变化，停下来排查。一切干净后执行：

```bash
python scripts/sync_mcp_schemas.py
```

### Step 7：M7 · Python 侧 `ListModelsTool` 实现 + 注册

新增：

- `src/foggy/mcp/tools/list_models_tool.py`
  - 类风格参照 `metadata_tool.py`：继承 `BaseMcpTool`，`tool_name: ClassVar[str] = "list_models"`（注意 Python 侧的命名习惯，参照 `metadata_tool.py` 第 14 行—— Java 侧叫 `dataset.list_models`，Python 侧的 `tool_name` 是否带前缀以仓内同类工具为准）
  - `tool_category = ToolCategory.METADATA`
  - `get_parameters` 返回空列表（无参）
  - `execute` 方法：调用 `query_service` 取所有模型，输出与 Java 端**字段一致**的 Markdown
  - 输出严禁包含字段索引等

修改：

- `src/foggy/mcp/services/tool_dispatcher.py` 或 `services/dispatcher.py`：注册 `ListModelsTool` 默认实例
- `src/foggy/mcp/launcher/app.py` 或对应工厂代码：装配 `ListModelsTool` 与 `QueryService` 依赖

**对齐原则**：Python 侧的 Markdown 输出字段、顺序、标点要和 Java 一致。如果发现不一致，记录到 progress 文档"风险" 区，并在 M9 双端联调时统一。

### Step 8：M8 · Python 测试覆盖

新增：

- `tests/mcp/tools/test_list_models_tool.py`
- `tests/mcp/test_mcp_rpc_list_models.py`（如已有 `test_mcp_rpc_*.py` 则增量补用例）

跑：

```bash
python -m pytest tests/mcp/ -q
```

必须**绿且测试数 ≥ 基线 + 新增**。

### Step 9：M9 · 双端联调与验收

1. Java 侧启动 launcher：

   ```bash
   java -jar foggy-mcp-launcher/target/foggy-mcp-launcher.jar
   ```

2. Python 侧启动 launcher：

   ```bash
   cd D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-python
   python -m foggy.mcp.launcher.app
   ```

3. 分别 POST：

   ```json
   {
     "jsonrpc": "2.0",
     "id": "list-models",
     "method": "tools/call",
     "params": { "name": "dataset.list_models", "arguments": {} }
   }
   ```

4. 把两侧返回的 Markdown 内容字段做 diff，确认字段名/顺序/列数一致；不一致的统一到一致后再次跑测试。

5. 比对需求 §验收标准 Phase 1 七条全部勾选。

6. 把 Phase 2 启动条件写入 progress：上线 14 天后，审计日志中 `/mcp/analyst/rpc` 与 `/mcp/admin/rpc` 角色对 `dataset.get_metadata` 的调用计数趋近 0 后，再走 Phase 2 切 `enabled: false`。

## 红线与雷区

绝对不能做的：

- ❌ **不要**新增 `visible` / `aiVisible` 字段到 `McpProperties.ToolConfigItem`（需求评审阶段已明确否决，复用现有 `enabled`）
- ❌ **不要**修改 `McpToolDispatcher / ToolFilterService / McpToolCallbackFactory / HealthController / DevToolsController`（这些类不需要联动改造）
- ❌ **不要**删除 `dataset.get_metadata` 源码或在 `application.yml` 中改其 `enabled: false`（这是 Phase 2 动作）
- ❌ **不要**在 Python 仓手工编辑 `src/foggy/mcp/schemas/` 下任何文件（必须走 `sync_mcp_schemas.py`）
- ❌ **不要**让 `ListModelsTool` 输出包含 `[field:` 字段索引（这是体量膨胀的根因，反过来失去重构意义）
- ❌ **不要**新增分页字段搜索工具（`dataset.search_fields` 是后续另立的事，不在本次范围）
- ❌ **不要**在 Java mainline `foggy-data-mcp-bridge/` 目录写入文件（worktree 改动不能漂到 mainline）

需要警惕的：

- ⚠️ `DatasetAccessor` 可能不止 `LocalDatasetAccessor` 一个实现类，扩接口前先 grep；如有多个实现，**默认走"工具内部直接复用 resolver 能力"**，把扩接口推到下个版本
- ⚠️ Python 侧工具名前缀（`dataset.list_models` vs `list_models`）以仓内现有同类工具为准，参照 `metadata_tool.py:14`，**不要凭空选**
- ⚠️ Python 侧测试 baseline 受 `pytest_collection_modifyitems` / autouse fixture 影响，新增测试如出现"看上去不相关的"测试失败，先看是否被 fixture 顺序污染（v1.3 R1 BUG 有先例），不要急着改业务代码

## 完成定义（DoD）

按需求 §验收标准 Phase 1 七项全部勾选 + 实现规划 §完成定义全部勾选 + progress 文档每个里程碑都有 done 状态与证据链接：

- [ ] Java 侧 `ListModelsTool`、schema、描述、注册全部落盘
- [ ] Java 侧 `get_metadata` 描述含 deprecation
- [ ] Java 侧 QueryExpertService / SpringAiTestExecutor / ecommerce-tests.json 全部切换
- [ ] Java 侧 `mvn test -pl foggy-dataset-mcp` 绿且测试数 ≥ 基线 + 新增
- [ ] `python scripts/sync_mcp_schemas.py --diff` 干净
- [ ] Python 侧 `ListModelsTool` 实现 + 注册落盘
- [ ] Python 侧 `python -m pytest tests/mcp/ -q` 绿且测试数 ≥ 基线 + 新增
- [ ] 双端 launcher 各自启动，手动调用 `dataset.list_models` 输出字段一致
- [ ] progress 文档 M1-M9 全 done，Phase 2 启动条件已写入

## 提交方式

- Java 侧：在 worktree 内独立 commit，**不**合并到 mainline `foggy-data-mcp-bridge/`
- Python 侧：在 `foggy-data-mcp-bridge-python` 仓 main 分支 commit
- 两侧 commit message 都引用本 execution-prompt 的 req_id：`P2-list_models`

## 卡住怎么办

如果遇到以下情况，**停下来更新 progress 文档说明，然后向用户求助**，不要硬推：

- `DatasetAccessor` 扩接口涉及超过 2 个实现类
- `sync_mcp_schemas.py` 的 diff 出现非预期文件改动
- Java / Python 两端的 Markdown 输出字段无法对齐（结构性差异）
- AI 流程测试改动后 Spring AI 测试基础设施报错（涉及 mock 框架的可能需要明确决策）
- 任意一侧 baseline 测试在你修改前就已经红
