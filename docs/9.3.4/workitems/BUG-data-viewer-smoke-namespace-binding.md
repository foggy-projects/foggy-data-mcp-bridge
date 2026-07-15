---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-DATA-VIEWER-NAMESPACE-BINDING
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-mcp-launcher
---

# Data Viewer smoke 测试缺少 namespace 数据源绑定

## Background

9.3.4 Step 2 全量 Surefire authority 执行到 `foggy-mcp-launcher` 时，
`DataViewerApiSmokeTest` 的物理 namespace 场景失败。该测试动态注册了
`data-viewer-smoke-physical` bundle，但仍依赖命名 namespace 自动回退到进程全局
`DataSource`。这与 9.3.1 已签收的生产隔离/fail-closed 契约冲突。

## Reproduction

权威失败运行：

```bash
scripts/verify-v934-unit.sh step2-unit-r2-20260714
```

聚焦复现：

```bash
mvn -q -pl foggy-mcp-launcher \
  -DskipITs=true \
  -Dtest=com.foggyframework.mcp.launcher.DataViewerApiSmokeTest \
  test
```

两次均稳定得到 `Tests run: 6, Failures: 2, Errors: 0, Skipped: 0`。
失败集中在 `frontendMetaUsesNamespace` 与 `directQueryUsesNamespace`，根因信息为：

```text
No default data source bound for namespace 'data-viewer-smoke-physical'
while loading model 'FactSalesSemanticScaleModel'
```

## Expected vs Actual

- 期望：测试夹具为自己创建的命名 namespace 显式绑定 SQLite `DataSource`，
  在不开启兼容回退的前提下验证 semantic-scale 差异。
- 实际：夹具只注册 bundle，模型加载正确 fail-closed；测试仍按旧的全局
  datasource 回退预期断言。

## Impact Scope

- 生产 fail-closed 逻辑没有回归，不应通过全局打开
  `allow-global-fallback-for-namespace` 来隐藏失败。
- 影响 Data Viewer 真实 HTTP smoke 夹具，并使 Step 2 unit authority 无法收口。

## Test Strategy

1. 保留 focused RED `6/2/0/0`。
2. 在 `SmokeApplication` 中注册仅解析目标 namespace 的
   `NamedDataSourceResolver`，显式返回同一 SQLite `DataSource`。
3. 重跑同一 focused class，要求 `6/0/0/0`。
4. 重跑 Step 2 全量 Surefire authority，要求 exact set、fresh mtime 与零 skip。

## Code Inventory

- `foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/DataViewerApiSmokeTest.java`
  - 将物理 namespace 固定为测试常量。
  - 注册测试专用 `NamedDataSourceResolver`，只为该 namespace 提供显式默认绑定。

## Fix Checklist

- [x] 全量 authority 捕获失败。
- [x] focused 运行稳定复现 RED。
- [x] 确认生产 fail-closed 行为符合 9.3.1 契约。
- [x] 夹具改为显式 namespace datasource 绑定。
- [x] focused class GREEN。
- [x] Step 2 Surefire authority GREEN。

## Verification

修正后使用同一 focused selector 执行：

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

r4 successor 以 `scripts/v934/step2-test-source-amendment.tsv` 锁定该源码与
`foggy-mcp-launcher/target/test-classes` 哈希，同时确认 discovery nodes 仍为 6，
report/execution ownership 零变化。最终关闭条件仍是 Step 2 Surefire authority 通过。

## References

- `foggy-mcp-launcher/target/surefire-reports/TEST-com.foggyframework.mcp.launcher.DataViewerApiSmokeTest.xml`
- `target/v934-step2-unit/runs/step2-unit-r2-20260714/run.marker`
- `target/v934-step2-successor/runs/step2-candidate-r4-20260714/summary.env`
- `docs/9.3.1/evidence/production-isolation-acceptance-20260714.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
