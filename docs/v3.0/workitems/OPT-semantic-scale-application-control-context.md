## 文档作用

| 属性 | 值 |
|------|-----|
| doc_type | implementation-context |
| intended_for | 继续会话的 Agent |
| purpose | 交接 semantic scale namespace loading policy 的实现上下文 |
| status | implemented |

---

## 当前决策

采用 namespace 隔离，不采用 runtime `applySemanticScale`。

核心规则：

- `semanticScaleFactor` 默认启用。
- 同一套 TM/QM 可以用 `ExternalBundleProperties` 注册到两个 namespace。
- dataset-model 根据 namespace 在模型加载期决定是否保留 `semanticScaleFactor`。
- 查询执行阶段不再传递 scale 开关。

---

## 推荐配置形态

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

  dataset:
    request:
      default-namespace: tms-ai
    semantic-scale:
      default-enabled: true
      disabled-namespaces:
        - tms-biz
```

`ExternalBundleProperties` 不需要知道 semantic scale；它只负责把同一路径注册到不同 namespace。

`foggy.dataset.request.default-namespace` 只作用在 REST / MCP / Local accessor 请求入口。上游未传 `X-NS` 或 namespace 参数时，入口层使用该默认值；底层 `null` / `""` 仍表示空 namespace，保持历史兼容。

---

## 已撤销代码

上一轮完成到一半的 runtime 方案已还原，以下代码不应继续使用：

| 旧设计 | 当前处理 |
|--------|----------|
| `DbQueryRequestDef.applySemanticScale` | 不再添加 |
| `SemanticRequestContext.applySemanticScale` | 不再添加 |
| `ModelResultContext.applySemanticScale` | 不再添加 |
| `DatasetProperties.mcpDefault/restDefault` | 不再添加 |
| MCP/REST 入口 scale 开关默认值注入 | 不再添加 |

---

## 当前关键代码位置

| 文件 | 关注点 |
|------|--------|
| `foggy-fsscript/src/main/java/com/foggyframework/bundle/external/ExternalBundleProperties.java` | 已支持 `name / namespace / path / watch` |
| `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java` | `findResourceByName(name, namespace, ...)` 已按 namespace 过滤 |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | 加载期 scale policy |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/config/DatasetRequestNamespaceResolver.java` | 请求入口默认 namespace 解析 |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/controller/NativeDatasetController.java` | REST native API 入口 namespace 默认值 |
| `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/McpToolDispatcher.java` | MCP tool context namespace 默认值 |
| `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/spi/impl/LocalDatasetAccessor.java` | Local accessor namespace 默认值 |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | 已按 `semanticScaleFactor` 生成 scaled declare |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/measure/DbMeasureSupport.java` | 已按 `semanticScaleFactor` 生成 scaled declare |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java` | SQL 缩放 helper |

---

## 已实现清单

1. `DatasetProperties` 已增加 namespace policy 配置。
2. `TableModelLoaderManagerImpl` 已在加载 `DbModelDef` 后，根据 namespace policy 归一化定义。
3. physical namespace 下会清空 measure/property 以及嵌套 dimension property 的 `semanticScaleFactor`。
4. `SemanticScaleFactorIntegrationTest` 已增加 physical namespace 元数据、select/slice、维度属性、聚合、having、calculatedFields、cache isolation 查询用例。
5. `DatasetRequestNamespaceResolver` 已增加 request default namespace 解析：显式 namespace 优先，缺失时使用 `foggy.dataset.request.default-namespace`，未配置时保留原值。
6. REST native API、MCP dispatcher、LocalDatasetAccessor 已接入 request default namespace。
7. `OPT-semantic-scale-application-control-progress.md` 已记录测试证据。
8. `docs/dev-guide/bundle-namespace.md` 已补充双 namespace、`foggy.dataset.semantic-scale` 与 `foggy.dataset.request.default-namespace` 配置示例。

---

## 风险提示

- namespace 可能同时承担租户、bundle、环境隔离语义；如果上游已经用 namespace 做租户，需要明确是否采用组合 namespace。
- 所有缓存、metadata、describeModel、queryModel 都必须传递同一个 namespace。
- 不允许同一个 compose/query 混用 semantic 和 physical namespace。
- MCP/LLM 不应暴露 physical namespace。
- 如果上游希望不传 namespace 即进入 AI 语义契约，必须配置 `foggy.dataset.request.default-namespace` 指向 semantic namespace；不要改底层空 namespace 语义。

---

## 接续建议

后续继续开发时不要恢复 `applySemanticScale` 请求参数或 runtime 上下文传递。下一步可转向真实上游 namespace 接入验证；Python 仍保持 deferred，等 Java 侧契约稳定后再评估移植。
