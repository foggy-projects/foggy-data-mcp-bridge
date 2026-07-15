---
type: bug
bug_source: test-governance-found
version: 9.3.4
ticket: BUG-934-STEP3-MYSQL57-MCP-FALSE-GREEN-ASSERTIONS
severity: critical
status: in-progress
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-mcp
---

# MySQL57 MCP 工具测试把业务失败签成绿色

## Symptom

fresh MySQL57 attempt `external-mysql-dev-a9c3r4` 的 `mysql57-mcp` XML 为
`5 reports / 14 testcase / F0/E0/S0`，但同一 run log 明确记录两个业务失败：

```text
QUERY_MODEL_SLICE_CONTRACT_INVALID: payload.slice must be an array of filter objects
Field 'totalAmount' not found in model 'FactOrderQueryModel'
```

两个测试只检查返回对象非空；`QueryModelTool` 正确返回的 `RX(code=600)` 因而被 JUnit
签成绿色。metadata 的 `instanceof RX` 可被错误类型绕过，无效模型没有失败断言；Compose
oracle 与实际结果同时为空时也可通过。direct `META-001` 的 `NOT_EMPTY` 规则还能把没有
`items/models` 的任意 data Map 包成单元素结果。

## Root Cause

测试只验证 Java 调用有返回值，没有验证 MCP 响应类型、`RX` success code、业务数据行、
schema、过滤口径或错误终态。两个请求本身还停留在旧 slice DSL 与不存在的字段名。

## Fix

- `McpToolsIT` 使用统一强类型 helper，成功路径要求 `RX=200`、正确 data 类型、非空真实行、
  请求列/schema/pagination 一致；metadata/description 必须包含签名模型与字段。
- 条件查询改用 filter-object 数组并逐行核对二级品类；订单字段改为真实的 `amount`。
- validate 明确要求不执行取数；invalid model 明确要求 `600/B600`、无 data、消息标识
  `NonExistentModel` 不存在。
- Compose 要求唯一 plan、手写 SQL oracle 非空、实际结果非空且逐行相等。
- `META-001` 保留 `NOT_EMPTY`，再要求 `content` 同时包含 `FactSalesQueryModel` 与
  `FactOrderQueryModel`。
- required MySQL runner 强制 `use-all-models=true`；现有 metadata 节点同时调用
  `dataset.list_models`，要求 curated catalog 精确 32 QM，并拒绝两个 demo 权限模型与
  Vector 模型泄漏。

这些改动不新增 `@Test` 或题库 case；冻结 cardinality 仍为 `mysql57-mcp=5/14`、
`mysql57-compose=1/2`、direct cases=`23`。

## Required Regression

- [x] 两个历史业务失败可由 run log 精确复现
- [x] Maven test-compile 通过，source amendment 冻结原/新 SHA 与原 report 集合
- [x] 题库仍精确 23 cases，META-001 两条内容规则由 ResultValidator 单测覆盖
- [ ] fresh MySQL57 `mysql57-mcp` 精确 `5 reports / 14 testcase / F0/E0/S0`
- [ ] fresh MySQL57 `mysql57-compose` 精确 `1 report / 2 testcase / F0/E0/S0`
- [ ] direct structured report 精确 `23/23` 且 META-001 内容规则通过
- [ ] candidate verifier 拒绝 empty result、wrong schema/plan 与 failed direct case

## Evidence Boundary

编译与单元测试只证明改动可执行，不证明真实 MySQL57 结果。该 BUG 在 committed fresh
candidate 产生前保持 `in-progress`；旧的绿色 XML 已被判定为 false green，不能进入
Step 3 authority。

## References

- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/integration/McpToolsIT.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/integration/ComposeScriptToolIT.java`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.3.4/workitems/BUG-step3-mysql57-direct-default-catalog-assembly.md`
- `scripts/v934/step3/external-matrix-contract.json`
