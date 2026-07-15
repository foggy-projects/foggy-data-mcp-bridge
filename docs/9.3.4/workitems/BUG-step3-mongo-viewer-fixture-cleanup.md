---
type: bug
bug_source: test-governance-found
version: 9.3.4
ticket: BUG-934-STEP3-MONGO-VIEWER-FIXTURE-CLEANUP
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: addons/foggy-data-viewer
---

# Mongo DataViewer 集成测试未在 suite 结束后清理 fixture

## Symptom

正式候选 `external-mongo-candidate-142f4360-r2` 的 Mongo/DataViewer exact selector
执行得到 `4 reports / 30 testcase / F0/E0/S0`，但 runner 随后的 run-scoped database
fixture 校验得到非空 `list_presets`，因此以
`failed / last_phase=mongo-fixture / exit_code=1` 拒绝候选。失败运行没有
`summary.env` 或 `candidate-manifest.json`，container 与两个 named volume residue 均为 `0`。

## Root Cause

`MongoListPresetStoreIT` 仅在 `@BeforeEach` 调用 `repository.deleteAll()`。JUnit 本次最后
执行 `shouldPersistAndListByUserModelAndBusinessKey`，该方法保存 3 条记录；suite 结束后没有
对应清理，数据库最终状态依赖测试顺序。把 runner 的 fixture 预期放宽为 3 会固化顺序依赖，
不能证明测试间和运行间隔离。

## Fix

增加 `@AfterEach` teardown，始终清空 `ListPresetRepository`。Step 3 contract 通过精确
source amendment 绑定修改后的测试源码；报告数量与 testcase 数量保持 `1/3` 不变。

## Required Regression

- [x] 原始正式候选在测试 XML 全绿时仍因残留 fixture fail closed
- [x] 失败候选不生成 summary/candidate，Docker residue 为 `0/0`
- [x] 修改后 exact Mongo/DataViewer selector 为 `4/30/F0/E0/S0`
- [x] suite 后 `list_presets=0` 且 viewer database 只包含该集合
- [x] candidate verifier 与 INT/TERM/HUP 清理证据通过

Closed by committed candidate `external-mongo-candidate-ccb29f47-r1`; evidence is recorded in
`docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`.

## References

- `addons/foggy-data-viewer/src/test/java/com/foggyframework/dataviewer/service/listpreset/MongoListPresetStoreIT.java`
- `scripts/verify-v934-external-mongo.sh`
- `docs/9.3.4/workitems/BUG-step3-external-matrix-gaps.md`
- `docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`
