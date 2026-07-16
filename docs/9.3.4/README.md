---
doc_role: version_execution_index
doc_purpose: Index the executable 9.3.4 test and CI evidence-chain plan.
version: 9.3.4
status: in-progress
predecessor: 9.3.3 signed-off / accepted-with-risks
created_at: 2026-07-14
updated_at: 2026-07-16
---

# 9.3.4 测试与 CI 证据链

## 文档作用

- doc_type: requirement+contract+plan+progress+acceptance-evidence-index
- intended_for: project-root-session / build owner / CI owner / reviewer
- purpose: 把 Failsafe 分层、五数据库矩阵、聚合覆盖率、required CI 与
  immutable release evidence 拆成严格 1~7 的可执行版本计划。

## Entry Decision

- 9.3.3 已 `signed-off / accepted-with-risks`；replacement authority
  `20260714T084351Z-3271604`=`3824 tests / 519 reports / F0/E0/S3`，满足本版本
  predecessor。
- 9.3.4-A 最小门继续由
  `docs/9.3.3/preconditions/9.3.4-A-minimum-test-gate.md` 提供历史 authority；
  本目录只引用，不复制一份可漂移状态。
- Step 1 已以 `step1-candidate-r8-20260714` 正式确认；两路独立复核均为 PASS，
  blocker=0。最终 inventory validator、confirmed-summary validator 与 16/16 hash
  manifest 复验通过。
- Step 2 已由 signal-safe r8e successor、Surefire Unit 与 Failsafe Integration
  三条独立权威链通过：`724 positive / 59 structural / 5,205 testcase / F0/E0/S0`；
  `46` 个 external execution 保持 exact deferred-to-Step-3。
- r8d successor 及其 Unit/Integration 因 `completed` 窗口 signal fail-open 被明确
  作废，禁止用于 coverage/acceptance；r8e 的 INT/TERM/HUP 动态探针分别以
  `130/143/129` fail closed。
- Step 3 已由同一 committed HEAD 的正式父运行
  `step3-required-20260716-final-r4` 通过：database `29/370` + required external
  `16/76` = exact `45/446/F0E0S0`，gap/overlap/extra=`0/0/0`；DB state=`18/18`、
  Redis state=`4/4`、Addon companion=`2/6`。quality→coverage→feature acceptance
  已按序完成；Step 4 已进入 `in-progress / diagnostic-ready`，尚无 coverage
  pass 或 aggregate baseline/review。
- Step 4 diagnostic-ready baseline 已冻结两层独立库存：coverage execution=
  `23 exec / 48 sessions`；
  required report overlay=`773 positive + 59 structural / 5,707 testcase`，Addon companion=
  `2/6`；required 总计预期 `F0E0S0`。raw contract、effective POM、toolchain
  receipt 与 report inventory 负例分别精确通过 `8/8`、`4/4`、`5/5`、
  `27/27`。
- toolchain receipt 绑定 Step 1 raw 工具版本，并实测约束 compiler realm ASM
  `9.6`、JaCoCo realm ASM `9.7`、test classpath ASM `9.7.1` 以及 24 个
  production module 的 effective compiler。本地 Step 4 `SHA256SUMS` 已生成并
  通过 exact 48 项校验，manifest SHA-256=
  `c8ae4f1015760ee831935c6c34fa0b83c9e8954065b909bbb80ab2b4a67d2417`。
- 静态复核还发现并修复 root coverage `argLine` 早解析 fail-open：legacy
  profile 现在实际生成/读取 exec，低于既有门时正确失败；生产代码不变，
  canonical `@{argLine}` 已由 versioned contract guard 与 `8/8` negatives 自动保护。详见
  [BUG-step4-legacy-coverage-argline-fail-open](workitems/BUG-step4-legacy-coverage-argline-fail-open.md)。
- `coverage-thresholds.json` 仍为 `diagnostic-pending`；尚未从 clean
  committed/pushed HEAD 执行 fresh all-lane diagnostic，因此没有 Step 4 exit
  evidence，Step 5 仍关闭。

## 执行资料

- requirement: [requirement/P0-test-ci-evidence-chain.md](requirement/P0-test-ci-evidence-chain.md)
- test/evidence contract: [contract/test-lane-evidence-contract.md](contract/test-lane-evidence-contract.md)
- module responsibility: [module-responsibility.md](module-responsibility.md)
- code inventory: [code-inventory.md](code-inventory.md)
- implementation plan: [implementation-plan.md](implementation-plan.md)
- project execution prompt: [execution-prompt.md](execution-prompt.md)
- progress: [progress/test-ci-evidence-chain-progress.md](progress/test-ci-evidence-chain-progress.md)
- test plan: [test/test-ci-evidence-chain-test-plan.md](test/test-ci-evidence-chain-test-plan.md)
- acceptance evidence plan: [acceptance-evidence-plan.md](acceptance-evidence-plan.md)
- Step 4 diagnostic-ready quality gate:
  [quality/step4-diagnostic-ready-implementation-quality.md](quality/step4-diagnostic-ready-implementation-quality.md)
- Step 1 confirmed evidence:
  [evidence/step-1/inventory-contract-freeze-20260714.md](evidence/step-1/inventory-contract-freeze-20260714.md)
- Step 2 structural amendment evidence：
  [evidence/step-2/step2-structural-container-contract-amendment-20260714.md](evidence/step-2/step2-structural-container-contract-amendment-20260714.md)
- Step 2 successor independent review：
  [evidence/step-2/step2-successor-r8e-independent-review-20260715.md](evidence/step-2/step2-successor-r8e-independent-review-20260715.md)
- Step 2 runner exit：
  [evidence/step-2/step2-runner-split-exit-r8e-20260715.md](evidence/step-2/step2-runner-split-exit-r8e-20260715.md)
- Step 2 implementation quality：
  [quality/step2-runner-split-implementation-quality.md](quality/step2-runner-split-implementation-quality.md)
- Step 2 coverage audit：
  [coverage/step2-runner-split-coverage-audit.md](coverage/step2-runner-split-coverage-audit.md)
- Step 3 five-DB diagnostic foundation（not authority）：
  [evidence/step-3/step3-five-db-foundation-20260715.md](evidence/step-3/step3-five-db-foundation-20260715.md)
- Step 3 database matrix runner/collector candidate（run-local / not authority）：
  [evidence/step-3/step3-database-matrix-runner-candidate-20260715.md](evidence/step-3/step3-database-matrix-runner-candidate-20260715.md)
- Step 3 committed Redis external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-redis-runner-candidate-20260715.md](evidence/step-3/step3-external-redis-runner-candidate-20260715.md)
- Step 3 committed Mongo/DataViewer external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-mongo-runner-candidate-20260715.md](evidence/step-3/step3-external-mongo-runner-candidate-20260715.md)
- Step 3 committed MySQL57 external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-mysql-runner-candidate-20260715.md](evidence/step-3/step3-external-mysql-runner-candidate-20260715.md)
- Step 3 committed Vector external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-vector-runner-candidate-20260715.md](evidence/step-3/step3-external-vector-runner-candidate-20260715.md)
- Step 3 shared external matrix candidate（single outer / complete external subset）：
  [evidence/step-3/step3-shared-external-matrix-candidate-20260716.md](evidence/step-3/step3-shared-external-matrix-candidate-20260716.md)
- Step 3 formal required-matrix exit：
  [evidence/step-3/step3-required-matrix-exit-20260716.md](evidence/step-3/step3-required-matrix-exit-20260716.md)
- Step 4 MultiThread prerequisite companion evidence：
  [evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md](evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md)
- Step 3 implementation quality：
  [quality/step3-required-matrix-implementation-quality.md](quality/step3-required-matrix-implementation-quality.md)
- Step 3 test evidence coverage audit：
  [coverage/step3-required-matrix-coverage-audit.md](coverage/step3-required-matrix-coverage-audit.md)
- Step 3 feature acceptance：
  [acceptance/step3-required-matrix-acceptance.md](acceptance/step3-required-matrix-acceptance.md)

## 1~7 顺序

| Step | 内容 | 当前状态 | Exit |
|---:|---|---|---|
| 1 | 契约与静态库存冻结 | passed | r8 confirmed；532 sources / 820 discovery / 829 execution / 519 predecessor；28/28 negatives |
| 2 | Surefire/Failsafe 全量分层 | passed | r8e confirmed；724 positive + 59 structural；5,205 testcase；F0/E0/S0；signal-safe status |
| 3 | 五数据库与外部集成 required matrix | passed | r4 same-commit exact `45/446/F0E0S0`；DB state `18/18`、Redis state `4/4`；Addon companion `2/6`；feature accepted |
| 4 | JaCoCo unit+IT 聚合与关键类门 | in-progress / diagnostic-ready | 静态契约 exact 23/48 + 773/59/5707；阈值 diagnostic-pending；下一动作是 clean committed/pushed HEAD 的 fresh all-lane diagnostic |
| 5 | 单一 authority runner 与 immutable evidence rehearsal | pending | dirty-safe candidate 可独立复算，但不更新 final authority pointer |
| 6 | PR/main/release CI 接线 | pending | exact five-cell artifacts、stable aggregator、JAR/镜像同一制品 |
| 7 | clean-commit 权威回放与后置门 | pending | self-check→quality→coverage→version acceptance signed-off |

任一步未满足 exit，不进入下一步；expected-negative、diagnostic 和 superseded
run 不得拼接为绿色 authority。

9.3.1–9.3.3 historical run/FQCN/count 保持封存；重命名后的 9.3.4 通过 reviewed
predecessor migration manifest 和 successor current-source lanes 证明等价回归，
不要求不可变的 v933 Batch 7 runner 在新命名上原样通过。

## 范围边界

- 本版本允许创建 build-only coverage report module；它不含生产类、不进入
  Launcher，不是 9.4.0 的生产模块拆分。
- 不实施 9.3.5 的 typed execution phase、统一 QueryFacade、公共 DTO/大类拆解。
- 不实施 9.4.0 的 `model-api/core/jdbc/starter/web`、BackendProvider、SPI v2
  或 Addon TCK。
- 测试治理发现生产 BUG 时先建 9.3.4 workitem，做最小 RED→fix→GREEN；若需
  公共 API/模块边界变更则停止并转交 9.3.5/9.4.0。
