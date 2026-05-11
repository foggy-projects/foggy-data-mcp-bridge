## 文档作用

| 属性 | 值 |
|------|-----|
| doc_type | workitem-progress |
| intended_for | Java engine team |
| purpose | 记录 semantic scale namespace loading policy 的执行进度 |
| status | implemented |

---

## 方向调整记录

上一轮 runtime `applySemanticScale` 方案已撤销。

已还原代码路径：

| 文件 | 处理 |
|------|------|
| `DatasetProperties.java` | 移除 `mcpDefault/restDefault` runtime 配置 |
| `DbQueryRequestDef.java` | 移除 `applySemanticScale` 请求字段 |
| `SemanticRequestContext.java` | 移除 `applySemanticScale` 上下文传递 |
| `ModelResultContext.java` | 移除 `applySemanticScale` 字段 |
| `DatasetAccessorConfig.java` | 移除 runtime scale 默认值注入 |
| `LocalDatasetAccessor.java` | 移除 MCP 入口默认 scale 注入 |

新的方案：namespace 隔离 + 加载期 scale profile。

---

## 新开发计划

### Step 1：配置层

状态：done

目标：

- 在 `DatasetProperties` 中新增 `SemanticScaleConfig`。
- 配置字段：
  - `defaultEnabled = true`
  - `disabledNamespaces = []`

验收：

- 不再出现 `mcpDefault/restDefault`。
- 不再支持请求级覆盖。
- `null` / blank namespace 的判断规则明确。

### Step 2：namespace scale policy

状态：done

目标：

- 新增小型 resolver/helper，或在 loader 中封装判断。
- 规则：默认启用，命中 disabled namespace 才禁用。
- namespace 归一化逻辑与 `TableModelLoaderManagerImpl.buildFullName()` 保持一致。

验收：

- `tms-ai` 默认 semantic。
- `tms-biz` 配置到 disabled 后 physical。

### Step 3：模型加载期归一化

状态：done

目标：

- 在 `TableModelLoaderManagerImpl.load(modelName, namespace)` 中处理 `DbModelDef`。
- physical namespace 下清空 property/measure 以及嵌套 dimension property 的 `semanticScaleFactor`。
- semantic namespace 下保持现有 `SemanticScaleSqlSupport.scaledDeclare()` 行为。

验收：

- query engine 不需要新增运行时分支。
- `DbPropertyImpl` 和 `DbMeasureSupport` 不需要接收上下文参数。

### Step 4：双 namespace 测试

状态：done

目标：

- 同一套 TM/QM 文件注册成 semantic/physical 两个 namespace。
- 覆盖默认 semantic 查询和 physical namespace select/slice/维度属性/聚合/having/calculatedFields。

建议用例：

| 用例 | 预期 |
|------|------|
| semantic namespace select | SQL 出现 `/ factor` |
| physical namespace select | SQL 不出现 `/ factor` |
| semantic namespace slice | 过滤值按元理解 |
| physical namespace slice | 过滤值按分理解 |
| physical namespace dimension property | 使用原始维度物理列 |
| physical namespace aggregate | `SUM(column)` 不自动除以 factor |
| physical namespace having | having 阈值按物理单位比较 |
| calculatedFields | 两个 namespace 都不需要特殊上下文 |
| cache isolation | 两个 namespace 不串模型 |

### Step 5：入口文档与部署样例

状态：done

目标：

- 文档说明 MCP/LLM 使用 semantic namespace。
- 业务系统如需物理单位，使用 physical namespace。
- `ExternalBundleProperties` 示例展示同一路径双注册。

### Step 6：请求入口默认 namespace

状态：done

目标：

- 支持上游 API/MCP 调用未传 namespace 时使用配置的 semantic namespace。
- 不改变底层 `null` / `""` 默认 namespace 语义。
- 不恢复 runtime `applySemanticScale` 或单位开关。

实现规则：

- 新增 `foggy.dataset.request.default-namespace`，默认空字符串。
- Dataset Native REST namespace 优先级为 `X-NS header > body.namespace > request default`。
- `body.namespace` 只读取请求 body 顶层字段，不读取 `payload.namespace` 或 compose script 内部字段。
- MCP dispatcher、LocalDatasetAccessor 仍按显式 namespace 入参优先；未传或空白 namespace 才使用 request default。
- REST native API、MCP dispatcher、LocalDatasetAccessor 在请求入口统一解析 effective namespace。

验收：

- 未配置 request default 时，旧调用继续保留 `null` / `""`。
- 配置 `default-namespace=tms-ai` 后，缺失 namespace 的入口请求进入 `tms-ai`。
- REST body 顶层 `namespace=tms-biz` 且无 `X-NS` 时进入 `tms-biz`。
- REST 同时传 `X-NS=tms-ai` 与 body `namespace=tms-biz` 时进入 `tms-ai`。
- Python 侧暂不改动。

---

## 不再执行

| 旧任务 | 处理 |
|--------|------|
| REST 入口注入 `restDefault` | cancelled |
| MCP 入口注入 `mcpDefault` | cancelled |
| `DbQueryRequestDef.applySemanticScale` | cancelled |
| `SqlExpContext` 或 ThreadLocal runtime 传递 | cancelled |
| slice/having 根据 runtime 开关反向转换 | cancelled |

---

## 当前状态

- Development: implemented
- Testing: passed
- Experience: N/A，纯后端模型加载策略，无 UI 交互

---

## 实现与测试证据

2026-05-10 已完成 Java engine 侧 namespace loading policy：

| 文件 | 结果 |
|------|------|
| `DatasetProperties.java` | 新增 `semanticScale.defaultEnabled=true` 与 `semanticScale.disabledNamespaces=[]` |
| `DatasetRequestNamespaceResolver.java` | 新增请求入口 `X-NS / body.namespace / default` 解析 |
| `DbModelAutoConfiguration.java` | 注入 `DatasetProperties` 到 `TableModelLoaderManagerImpl` |
| `TableModelLoaderManagerImpl.java` | 在 `DbModelDef` 转换后、初始化前按 namespace 清空 `semanticScaleFactor` |
| `NativeDatasetController.java` | REST native API 支持 `X-NS > body.namespace > request default` |
| `McpToolDispatcher.java` | MCP tool context 未传 namespace 时应用 request default namespace |
| `LocalDatasetAccessor.java` | local accessor metadata / describeModel / queryModel 未传 namespace 时应用 request default namespace |
| `SemanticScaleFactorIntegrationTest.java` | 新增 physical namespace 元数据、select/slice、维度属性、聚合、having、calculatedFields、cache isolation 查询用例 |
| `docs/dev-guide/bundle-namespace.md` | 补充同一 TM/QM 双 namespace + semantic-scale + request default namespace 配置示例 |

测试命令：

```powershell
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest test
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest#disabledNamespace_queryUsesPhysicalValues test
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest#disabledNamespace_dimensionPropertyUsesPhysicalValues+disabledNamespace_groupedAggregationUsesPhysicalValues+disabledNamespace_havingUsesPhysicalValues+disabledNamespace_calculatedFieldUsesPhysicalLeafValue test
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest#loadModel_disabledNamespaceDoesNotPolluteDefaultNamespace test
mvn -pl foggy-dataset-model "-Dtest=DatasetRequestNamespaceResolverTest,DbModelAutoConfigurationTest,NativeDatasetControllerTest" test
mvn -pl foggy-dataset-mcp -am "-Dtest=McpToolDispatcherTest,LocalDatasetAccessorGovernanceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

测试结果：

- `SemanticScaleFactorIntegrationTest`: 20 tests, 0 failures, 0 errors.
- 新增 physical namespace 4 用例单独执行通过：4 tests, 0 failures, 0 errors.
- namespace cache isolation 用例单独执行通过：1 test, 0 failures, 0 errors.
- physical namespace 查询生成 `t1.sales_amount`、`dp.unit_price`、`SUM(t1.sales_amount)`、`t1.sales_amount + 10`，未出现 `/100.0`。
- default semantic namespace 既有用例继续生成 `/100.0`。
- request default namespace 定向测试通过：model 模块 21 tests，MCP reactor 28 tests，0 failures，0 errors。
- request namespace 覆盖 resolver、配置默认值、REST native controller、MCP dispatcher 和 LocalDatasetAccessor 入口传递。

2026-05-11 补齐 Dataset Native REST body namespace 契约：

- `/semantic/v3/dataset/query`、`/compose`、`/list_models`、`/describe_model_internal` 均支持 body 顶层 `namespace`。
- 有效 namespace 优先级固定为 `X-NS header > body.namespace > foggy.dataset.request.default-namespace`。
- `X-NS` 与 body namespace 冲突时，`X-NS` 生效并记录冲突解析日志。
- 定向测试命令：

```powershell
mvn -pl foggy-dataset-model "-Dtest=DatasetRequestNamespaceResolverTest,DbModelAutoConfigurationTest,NativeDatasetControllerTest" test
mvn -pl foggy-dataset-mcp -am "-Dtest=McpToolDispatcherTest,LocalDatasetAccessorGovernanceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- 测试结果：model 模块 21 tests，MCP reactor 28 tests，0 failures，0 errors。

---

## 后续评审要求

实现完成后需要轻量 implementation check-in：

- 确认没有恢复 runtime `applySemanticScale`。
- 确认 namespace policy 只影响加载期。
- 确认 `request.default-namespace` 只影响请求入口，不改变底层空 namespace。
- 确认测试覆盖 semantic/physical 两个 namespace。
- 确认同一模型在 semantic/physical namespace 间不会缓存串扰。
- 确认 README 和部署样例同步。
