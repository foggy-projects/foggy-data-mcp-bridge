---
type: bug
bug_source: test-governance-found
version: 9.3.4
ticket: BUG-934-STEP3-MYSQL57-DIRECT-CATALOG-ASSEMBLY
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-mcp/foggy-dataset-demo
---

# MySQL57 direct 冷目录被模型源边界阻断

## Symptom

`AiToolsIT` 的 required direct 节点补上 fail-closed 断言后，真实 MySQL57 执行不再把
`22/23` case 写成绿色。稳定失败为：

```text
Direct tool calls failed: 1/23 failed
- case=META-001; error=CATALOG_REFRESH_FAILED: namespace=''
```

`namespace=''` 是合法 default namespace，不是错误输入。第一次完整日志中的 nested cause
为：

```text
Cannot find TableModelLoader for type [vector]
```

关闭默认混合 bundle、改用 ecommerce 根路径后的 fresh attempt
`external-mysql-dev-a9c3r4` 仍正确拒绝 `22/23`。这次最内层 cause 为：

```text
No bean named 'demoAuthorizationService' available
```

同目录的第二个权限演示 QM 还引用 `demoSessionTokenService`；只补一个 Bean 仍会留下
顺序依赖失败。

## Root Cause

1. `META-001` 直接调用 `dataset.list_models`，冷目录先物化完整 default namespace，之后
   才应用 MCP 的 `model-list` 输出过滤。
2. `foggy-dataset-demo` 默认 bundle 同时发现 JDBC、Vector、Odoo、PreAgg 等约 60 个 QM。
3. 当前 MCP direct classpath 有 demo，但没有 Vector provider/Milvus；因此加载
   `DocumentSearchQueryModel` 时失败。
4. Catalog coordinator 按 9.3.3 契约拒绝发布半成品 snapshot，外层正确返回
   `CATALOG_REFRESH_FAILED`。
5. ecommerce 根目录本身有 `34 QM / 61 files`；其中 `demo/**` 两个权限演示 QM 依赖
   `JdbcModelDemoAutoConfiguration` 提供的两个 Bean。`foggy.demo.enabled=false` 会同时
   关闭默认 bundle 与这些 Bean，因此“整根 ecommerce”仍不是自包含的 JDBC 模型源。

只增加 Vector 测试依赖不是完整修复；随后仍可能因 Odoo datasource 等未装配能力失败。

## Step 3 Unblock

MySQL57 authority runner 从已纳入 source seal 的 ecommerce 树生成 run-owned curated
bundle，不修改 coordinator 的原子刷新语义，也不向通用 MCP 测试配置补入 demo Bean：

```text
-Dfoggy.demo.enabled=false
-Dfoggy.bundle.external.enabled=true
-Dfoggy.bundle.external.bundles[0].name=ai-mysql57-ecommerce
-Dfoggy.bundle.external.bundles[0].path=file:<run-root>/cells/mysql57/ecommerce-bundle
-Dfoggy.bundle.external.bundles[0].watch=false
```

curated manifest 精确保留 `59 files / 32 QM / 25 TM / 2 fsscript`，只排除
`demo/**` 两个需要 demo service 的权限演示 QM；逐文件 SHA/size 必须与源树一致，任何
demo 注入、缺失、额外、symlink 或内容漂移都由 candidate verifier fail closed。既有
生产 `model-list` 不承担 source allowlist 职责；required runner 显式启用
`use-all-models=true`，并在现有 MCP 节点中要求公开 catalog 精确为这 32 个模型，防止
测试配置把多装载模型裁剪后伪绿。

## Required Regression

- [x] direct JUnit 节点对空 case 集与任一失败 case fail closed
- [x] source amendment 冻结旧/新 SHA，discovery cardinality 保持 `3 + 4 + optional 1`
- [x] curated bundle 生成与 manifest/candidate 严格校验已实现
- [x] `META-001` 规则要求内容同时包含 FactSales/FactOrder，不再以任意非空 Map 通过
- [x] MySQL required runner 强制 full catalog，现有节点要求精确 32 QM 且排除两个 demo QM
- [x] 单跑 `META-001`，隔离 bundle 下 cold catalog 成功
- [x] 执行全部 direct cases，要求 `23/23`
- [x] `mysql57-direct` 精确 `2 reports / 7 testcase / F0/E0/S0`
- [x] optional `AiModelCallTest` 不进入 required selector

`external-mysql-dev-a9c3r4` 及此前所有 MySQL 尝试均为失败/诊断证据：durable status
为 failed、无 candidate、Docker container/volume residue=`0/0`，不得与后续绿色结果拼接。

## Long-term Production Follow-up

生产 launcher 默认依赖 monolithic demo，而 provider 与 datasource 是可选装配。长期修复
应按 capability/profile 拆分或显式激活 model-source bundle，或者引入独立于 MCP 输出
过滤的 source allowlist；不在 9.3.4 中放宽 Catalog fail-closed。

- [ ] launcher default/lite 冷启动 `dataset.list_models` 回归
- [ ] 缺 provider 时仍不发布部分 snapshot

上述两项是生产装配长期项，不属于本次 Step 3 MySQL required lane 的关闭条件。

## Closure

Committed fresh candidate `external-mysql-candidate-97f1cbfa-r2` 在 exact curated bundle 上完成
cold catalog，direct structured report 为 `23/23`，JUnit 为 `2 reports / 7 testcase /
F0/E0/S0`，required selector 中不存在 optional `AiModelCallTest`。本 BUG 以 Step 3 unblock
scope 关闭；生产 follow-up 保持显式开放。证据见
`docs/9.3.4/evidence/step-3/step3-external-mysql-runner-candidate-20260715.md`。

## Evidence Boundary

旧失败报告只用于复现和根因确认，不是 required 绿色证据。当前关闭结论只绑定 committed
fresh candidate `external-mysql-candidate-97f1cbfa-r2`；Redis/Mongo/Vector 的结果不能替代它。

## References

- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiToolsIT.java`
- `docs/9.3.4/workitems/BUG-step3-external-matrix-gaps.md`
- `scripts/v934/step3/external-matrix-contract.json`
