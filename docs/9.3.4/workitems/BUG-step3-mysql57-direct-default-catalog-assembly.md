---
type: bug
bug_source: test-governance-found
version: 9.3.4
ticket: BUG-934-STEP3-MYSQL57-DIRECT-CATALOG-ASSEMBLY
severity: critical
status: in-progress
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-mcp/foggy-dataset-demo
---

# MySQL57 direct 冷目录被默认混合 bundle 阻断

## Symptom

`AiToolsIT` 的 required direct 节点补上 fail-closed 断言后，真实 MySQL57 执行不再把
`22/23` case 写成绿色。稳定失败为：

```text
Direct tool calls failed: 1/23 failed
- case=META-001; error=CATALOG_REFRESH_FAILED: namespace=''
```

`namespace=''` 是合法 default namespace，不是错误输入。历史完整日志中的 nested cause
为：

```text
Cannot find TableModelLoader for type [vector]
```

## Root Cause

1. `META-001` 直接调用 `dataset.list_models`，冷目录先物化完整 default namespace，之后
   才应用 MCP 的 `model-list` 输出过滤。
2. `foggy-dataset-demo` 默认 bundle 同时发现 JDBC、Vector、Odoo、PreAgg 等约 60 个 QM。
3. 当前 MCP direct classpath 有 demo，但没有 Vector provider/Milvus；因此加载
   `DocumentSearchQueryModel` 时失败。
4. Catalog coordinator 按 9.3.3 契约拒绝发布半成品 snapshot，外层正确返回
   `CATALOG_REFRESH_FAILED`。

只增加 Vector 测试依赖不是完整修复；随后仍可能因 Odoo datasource 等未装配能力失败。

## Step 3 Unblock

MySQL57 direct authority invocation 使用隔离的 ecommerce JDBC bundle，不修改 coordinator
的原子刷新语义：

```text
-Dfoggy.demo.enabled=false
-Dfoggy.bundle.external.enabled=true
-Dfoggy.bundle.external.bundles[0].name=ai-mysql57-ecommerce
-Dfoggy.bundle.external.bundles[0].path=classpath*:/foggy/templates/ecommerce
-Dfoggy.bundle.external.bundles[0].watch=false
```

该 bundle 仍完整物化 34 个 JDBC QM，再由既有 `model-list` 收敛对外模型。

## Required Regression

- [x] direct JUnit 节点对空 case 集与任一失败 case fail closed
- [x] source amendment 冻结旧/新 SHA，discovery cardinality 保持 `3 + 4 + optional 1`
- [ ] 单跑 `META-001`，隔离 bundle 下 cold catalog 成功
- [ ] 执行全部 direct cases，要求 `23/23`
- [ ] `mysql57-direct` 精确 `2 reports / 7 testcase / F0/E0/S0`
- [ ] optional `AiModelCallTest` 不进入 required selector
- [ ] launcher default/lite 冷启动 `dataset.list_models` 回归
- [ ] 缺 provider 时仍不发布部分 snapshot

## Long-term Production Follow-up

生产 launcher 默认依赖 monolithic demo，而 provider 与 datasource 是可选装配。长期修复
应按 capability/profile 拆分或显式激活 model-source bundle，或者引入独立于 MCP 输出
过滤的 source allowlist；不在 9.3.4 中放宽 Catalog fail-closed。

## Evidence Boundary

现有失败报告只用于复现和根因确认，不是 required 绿色证据。MySQL57 direct 在上述全部
回归完成前保持 pending，Redis/Mongo/Vector 的结果不能替代它。

## References

- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiToolsIT.java`
- `docs/9.3.4/workitems/BUG-step3-external-matrix-gaps.md`
- `scripts/v934/step3/external-matrix-contract.json`
