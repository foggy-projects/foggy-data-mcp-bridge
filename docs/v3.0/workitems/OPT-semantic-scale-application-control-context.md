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
    semantic-scale:
      default-enabled: true
      disabled-namespaces:
        - tms-biz
```

`ExternalBundleProperties` 不需要知道 semantic scale；它只负责把同一路径注册到不同 namespace。

---

## 已撤销代码

上一轮完成到一半的 runtime 方案已还原，以下代码不应继续使用：

| 旧设计 | 当前处理 |
|--------|----------|
| `DbQueryRequestDef.applySemanticScale` | 不再添加 |
| `SemanticRequestContext.applySemanticScale` | 不再添加 |
| `ModelResultContext.applySemanticScale` | 不再添加 |
| `DatasetProperties.mcpDefault/restDefault` | 不再添加 |
| MCP/REST 入口默认值注入 | 不再添加 |

---

## 当前关键代码位置

| 文件 | 关注点 |
|------|--------|
| `foggy-fsscript/src/main/java/com/foggyframework/bundle/external/ExternalBundleProperties.java` | 已支持 `name / namespace / path / watch` |
| `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java` | `findResourceByName(name, namespace, ...)` 已按 namespace 过滤 |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | 建议加入加载期 scale policy |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | 已按 `semanticScaleFactor` 生成 scaled declare |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/measure/DbMeasureSupport.java` | 已按 `semanticScaleFactor` 生成 scaled declare |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java` | SQL 缩放 helper |

---

## 已实现清单

1. `DatasetProperties` 已增加 namespace policy 配置。
2. `TableModelLoaderManagerImpl` 已在加载 `DbModelDef` 后，根据 namespace policy 归一化定义。
3. physical namespace 下会清空 measure/property 以及嵌套 dimension property 的 `semanticScaleFactor`。
4. `SemanticScaleFactorIntegrationTest` 已增加 physical namespace 元数据、select/slice、维度属性、聚合、having、calculatedFields、cache isolation 查询用例。
5. `OPT-semantic-scale-application-control-progress.md` 已记录测试证据。
6. `docs/dev-guide/bundle-namespace.md` 已补充双 namespace 与 `foggy.dataset.semantic-scale` 配置示例。

---

## 风险提示

- namespace 可能同时承担租户、bundle、环境隔离语义；如果上游已经用 namespace 做租户，需要明确是否采用组合 namespace。
- 所有缓存、metadata、describeModel、queryModel 都必须传递同一个 namespace。
- 不允许同一个 compose/query 混用 semantic 和 physical namespace。
- MCP/LLM 不应暴露 physical namespace。

---

## 接续建议

后续继续开发时不要恢复 `applySemanticScale` 请求参数或 runtime 上下文传递。下一步可转向真实上游 namespace 接入验证；Python 仍保持 deferred，等 Java 侧契约稳定后再评估移植。
