## 文档作用

| 属性 | 值 |
|------|-----|
| doc_type | workitem (design decision) |
| intended_for | Java engine team, MCP integration team |
| purpose | 记录 semanticScaleFactor 按 namespace 加载期隔离的设计决策 |
| status | implemented |

---

## 背景

TM/QM 模型支持 `semanticScaleFactor`，用于把物理存储单位转换成语义单位，例如数据库保存“分”，语义查询返回“元”。

当前冲突来自两类上游系统：

| 上游 | 期望契约 | 示例 |
|------|----------|------|
| 业务前后台 / 既有 REST 调用 | 继续使用物理单位 | 金额字段仍按“分”理解 |
| AI Agent / MCP / LLM | 统一使用语义单位 | 金额字段永远按“元”理解 |

这不是一次查询参数差异，而是两套上游对同一套 TM/QM 的不同模型契约。

---

## 废弃方案

废弃上一轮 `applySemanticScale` runtime 开关方案。

废弃原因：

- 同一个字段会在不同请求上下文下拥有不同单位，容易污染 `columns / slice / having / orderBy / calculatedFields / pivot / timeWindow` 的一致性。
- `DbProperty.getDeclare()` 和 `DbMeasure.getDeclare()` 被多条 SQL 构建路径复用，运行时切换需要大面积传递上下文或使用 ThreadLocal。
- slice/having 过滤值、计算字段引用和元数据展示都要跟随运行时开关，复杂度高且容易产生隐性错误。
- 单位语义应属于模型视图契约，而不是单次查询执行状态。

因此不在 `DbQueryRequestDef`、`SemanticRequestContext`、`ModelResultContext` 中加入 `applySemanticScale`。

---

## 设计决策

### 核心方案

采用 **namespace 隔离 + 加载期 scale profile**。

同一套 TM/QM 文件可以通过 `ExternalBundleProperties` 注册到两个 namespace；dataset-model 在加载模型时根据 namespace policy 决定是否保留 `semanticScaleFactor`。

| Namespace | 使用方 | scale profile | 字段契约 |
|-----------|--------|---------------|----------|
| semantic namespace | AI Agent / MCP / LLM | semantic | 保留 `semanticScaleFactor`，金额是元 |
| physical namespace | 业务前后台 / 既有调用 | physical | 忽略 `semanticScaleFactor`，金额是分 |

`semantic-scale` 默认必须是 true。只有显式配置为 physical 的 namespace 才禁用转换。

### 外部 Bundle 注册

`ExternalBundleProperties` 只负责把同一目录注册成不同 namespace，不承载 dataset 语义策略：

```yaml
foggy:
  bundle:
    external:
      enabled: true
      bundles:
        - name: tms-models-physical
          namespace: tms-biz
          path: D:/models/tms
          watch: true

        - name: tms-models-semantic
          namespace: tms-ai
          path: D:/models/tms
          watch: true
```

### Dataset scale policy

dataset-model 负责按 namespace 决定加载规则：

```yaml
foggy:
  dataset:
    semantic-scale:
      default-enabled: true
      disabled-namespaces:
        - tms-biz
```

规则：

- `default-enabled=true` 是默认值，也是目标默认契约。
- 未配置的 namespace 都按 semantic profile 加载。
- `disabled-namespaces` 中的 namespace 按 physical profile 加载。
- 查询执行阶段不再允许用请求参数覆盖单位契约。

### 请求入口默认 namespace

底层模型加载仍保留原有默认 namespace 语义：`null` / `""` 表示空 namespace，不在存储层强行改写。

为了让 AI Agent / MCP / REST 调用可以在未传 `X-NS` 或 namespace 参数时进入 semantic namespace，新增请求入口默认值：

```yaml
foggy:
  dataset:
    request:
      default-namespace: tms-ai
    semantic-scale:
      default-enabled: true
      disabled-namespaces:
        - tms-biz
```

规则：

- 显式传入的 namespace 优先，且会 trim 后使用。
- 未传或传空 namespace 时，如果 `foggy.dataset.request.default-namespace` 非空，则入口层替换为该默认值。
- 如果未配置 `request.default-namespace`，继续向下传递原始 `null` / `""`，保持兼容。
- 该配置只作用于 API/MCP/Local accessor 请求入口，不改变 `ExternalBundleProperties` 和底层模型缓存的 namespace 语义。

---

## 加载期行为

模型加载流程：

1. `SystemBundlesContext.findResourceByName(name, namespace, ...)` 按 namespace 找到 TM/QM 文件。
2. `TableModelLoaderManagerImpl.load(modelName, namespace)` 转换出 `DbModelDef`。
3. 根据 `DatasetProperties.semanticScale` 判断当前 namespace 是否启用 scale。
4. 如果启用：保留 `DbPropertyDef / DbMeasureDef.semanticScaleFactor`。
5. 如果禁用：在模型初始化前清空 `semanticScaleFactor`，后续引擎看到的是物理模型视图。
6. `TableModel` 缓存仍按 `namespace:modelName` 隔离。

这个方案让 `DbPropertyImpl`、`DbMeasureSupport`、SQL visitor、slice/having、calculatedFields、pivot 和 timeWindow 都继续依赖稳定的模型定义，不需要运行时分支。

---

## 约束

- 不修改 `ExternalBundleProperties` 的语义职责；它是 bundle/namespace 注册层，不知道 dataset scale。
- 不新增 `applySemanticScale` 请求参数。
- 不在查询上下文里传递 scale 开关。
- 不允许一个 compose/query 同时混用 semantic namespace 和 physical namespace。
- MCP/LLM 入口只暴露 semantic namespace。
- physical namespace 仅用于明确需要物理单位的业务系统。

---

## 实现清单

### Java Engine

1. **配置类**
   - 在 `DatasetProperties` 增加 `SemanticScaleConfig`。
   - 字段：`defaultEnabled=true`、`disabledNamespaces=[]`。

2. **加载期 policy**
   - 新增或内联 namespace 判断逻辑。
   - 归一化 namespace：`null` / blank 视为默认 namespace。
   - 判断当前 namespace 是否启用 semantic scale。

3. **模型定义归一化**
   - 在 `TableModelLoaderManagerImpl.load(modelName, namespace)` 中，`DbModelDef` 初始化前处理。
   - physical namespace 下清空 properties/measures 中的 `semanticScaleFactor`。
   - semantic namespace 下保持现状。

4. **缓存与热加载**
   - 确认 `TableModel` 缓存 key 使用 `namespace:modelName`。
   - 确认 `clearByNamespace(namespace)` 仍能清理对应视图。

5. **MCP/REST 入口**
   - 不注入 scale 开关。
   - 入口只负责传递 namespace。
   - 可通过 `foggy.dataset.request.default-namespace` 让未传 namespace 的 MCP/REST 请求默认进入 semantic namespace。

### 测试

最低测试集：

| 场景 | 预期 |
|------|------|
| 同一 TM/QM 目录注册为 `tms-ai` 和 `tms-biz` | 两个 namespace 都能加载同名模型 |
| `tms-ai` 查询金额字段 | SQL 使用 `(column / factor)`，返回元 |
| `tms-biz` 查询金额字段 | SQL 使用 `column`，返回分 |
| `slice/having` 查询 | 两个 namespace 都按各自字段契约稳定执行 |
| `calculatedFields` 引用金额字段 | 不需要运行时额外判断 |
| 模型缓存 | `tms-ai:model` 与 `tms-biz:model` 不串用 |

---

## 状态

- Java: implemented（namespace loading policy 已实现并通过集成测试）
- Python: deferred（Java 稳定后再评估是否需要同构）

---

## 实现记录

2026-05-10 已完成 Java engine 第一阶段实现：

- `DatasetProperties` 增加 `foggy.dataset.semantic-scale.default-enabled` 与 `disabled-namespaces`。
- `DatasetProperties` 增加 `foggy.dataset.request.default-namespace`，用于请求入口缺省 namespace。
- `TableModelLoaderManagerImpl` 在加载期按 namespace policy 处理 `DbModelDef`。
- physical namespace 下清空 measure/property 以及嵌套 dimension property 的 `semanticScaleFactor`。
- 查询执行阶段不新增 runtime 分支，不新增 `applySemanticScale` 请求字段。
- REST / MCP / Local accessor 入口在缺失 namespace 时按 request default 解析，不改变底层默认 namespace。
- `docs/dev-guide/bundle-namespace.md` 补充同一 TM/QM 双 namespace + semantic-scale + request default namespace 配置示例。

验证：

```powershell
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest test
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest#disabledNamespace_queryUsesPhysicalValues test
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest#disabledNamespace_dimensionPropertyUsesPhysicalValues+disabledNamespace_groupedAggregationUsesPhysicalValues+disabledNamespace_havingUsesPhysicalValues+disabledNamespace_calculatedFieldUsesPhysicalLeafValue test
mvn -pl foggy-dataset-model -Dtest=SemanticScaleFactorIntegrationTest#loadModel_disabledNamespaceDoesNotPolluteDefaultNamespace test
```

结果：测试通过；`SemanticScaleFactorIntegrationTest` 共 20 tests。physical namespace 查询 SQL 使用原始物理列和聚合表达式，例如 `sales_amount`、`unit_price`、`SUM(sales_amount)`、`sales_amount + 10`，default semantic namespace 继续使用 `/100.0`，semantic/physical namespace 加载缓存不串扰。

---

## 讨论记录

- 2026-05-10：确认 runtime `applySemanticScale` 风险过高，废弃该方案。
- 2026-05-10：确认采用 namespace 隔离，同一套 TM/QM 文件通过 `ExternalBundleProperties` 注册到不同 namespace。
- 2026-05-10：确认 `semantic-scale` 默认 true，仅显式 physical namespace 禁用。
- 2026-05-10：完成 Java engine 加载期 namespace policy，实现与测试证据见进度文档。
- 2026-05-10：补齐 physical namespace 维度属性、聚合、having、calculatedFields、cache isolation 回归用例，并补通用配置示例。
- 2026-05-10：确认新增请求入口默认 namespace，API/MCP 未传 namespace 时可进入配置的 semantic namespace；底层空 namespace 兼容语义不变。
