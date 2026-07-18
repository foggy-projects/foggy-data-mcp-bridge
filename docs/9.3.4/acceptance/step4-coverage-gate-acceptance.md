---
acceptance_scope: feature
version: 9.3.4
target: step4-coverage-gate
doc_role: acceptance-record
doc_purpose: Record the formal feature acceptance decision for the 9.3.4 Step 4 coverage gate.
status: signed-off
decision: accepted
signed_off_by: Codex
signed_off_at: 2026-07-18
reviewed_by: Codex + independent requirement/workitem/artifact/companion reviewers
blocking_items: []
follow_up_required: yes
evidence_count: 12
---

# Step 4 Coverage Gate Feature Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: signoff owner / 9.3.4 root session / Step 5 owner / reviewer
- purpose: 记录 Step 4 feature 的正式验收结论、证据边界与 Step 5 开启条件

## Background

- Version: 9.3.4
- Scope: Step 4 feature acceptance
- Target: JaCoCo Unit+IT aggregate、critical/model gate 与 fail-closed evidence chain
- Tested commit: `f97483a0b87a82734d21888e7b5bea74b0c5fe55`
- Authority run: `step4-coverage-20260717-formal-r4`
- Goal alignment: 完成 9.3.4 的 coverage/critical gate 子目标，为 Step 5 single-authority
  rehearsal 提供已签收输入；不签收 9.3.4 version，不启动 9.3.5。

## Acceptance Basis

1. `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
2. `docs/9.3.4/contract/test-lane-evidence-contract.md`
3. `docs/9.3.4/implementation-plan.md`
4. `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`
5. `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`
6. `docs/9.3.4/acceptance-evidence-plan.md`
7. `docs/9.3.4/evidence/step-4/step4-coverage-exit-20260717.md`
8. `docs/9.3.4/evidence/step-4/step4-pivot-legacy-companion-evidence-20260718.md`
9. `docs/9.3.4/quality/step4-coverage-gate-final-implementation-quality.md`
10. `docs/9.3.4/coverage/step4-coverage-gate-coverage-audit.md`
11. 25 个 Step 4 workitem 及对应 historical RED/fail-closed records
12. formal raw manifests/receipts 与独立 requirement、workitem、artifact、companion reviews

## Checklist

- [x] Cfreeze 是 reviewed Cdiag 的 direct single parent，formalization delta、push/clean identity
      与 source-before/source-after seal 均通过。
- [x] fresh formal-r4 完整执行 Unit、Integration、Step 3 required 和 Addon companion；没有复用
      diagnostic exec/XML 或跨 run 拼接。
- [x] required=`773+59/5707/F0E0S0`；Unit=`681+55/4941`；Integration=`47+4/320`；
      database/external=`29/370 + 16/76`；Addon=`2/6`（union 外）。
- [x] exact provenance=`23 exec / 48 sessions / 16,953 execution class identities`，production
      universe=`24 modules / 2,098 classes`。
- [x] aggregate line=`54,624/76,830`、branch=`26,111/44,870`，exact 达 confirmed minimum，
      threshold、critical set 与 exclusion 未降低或扩大。
- [x] 12 个 critical class、23 个 applicable metric 全部通过，below=`0`；唯一 structural
      N/A=`NamespaceScope.branch`；model gate PASS。
- [x] report/exec/XML/source/Git/toolchain/POM/successor/fixture/state/lifecycle negatives 全部通过；
      missing、zero、skip、stale、tamper 与 cleanup failure 均 fail closed。
- [x] 三个 child 均 passed/reaped、process-group residue=0；runner container/volume/network
      residue=`0/0/0`；sensitive scan PASS。
- [x] candidate/final/summary/report/exec/coverage/lifecycle hash chain完整，public final artifact
      replay=`ARTIFACT VALID stage=final`。
- [x] coverage audit 发现的 Pivot legacy 候选 Major gap 已在同一 tested HEAD 的最终源码上补齐：
      legacy companion=`1/F0E0S0`，并与 formal 五个 V934 variant 共同覆盖两个分支。
- [x] implementation quality=`ready-for-coverage-audit`，open B/H/M=`0/0/0`、mandatory fixes=0；
      coverage audit=`ready-for-acceptance`，critical/major gap=`0/0`。
- [x] 25 个 Step 4 workitem 的实现与测试证据均为 covered；9 个既有 implementation closure
      已确认后置门，剩余 16 个 acceptance-pending 状态只在本 decision 后关闭。
- [x] `DEBT-unit-mysql57-fixture-classification-migration` 的 Step 4-side 运行/测试前置条件已满足；
      9.3.4 version 临时放行仍由 Step 7 决定，debt 保持 open，关闭期限仍为 9.3.5
      version acceptance。
- [x] Experience=`N/A`：本 feature 是后端测试/coverage/evidence authority，无页面、浏览器或
      人工体验交付，因此无需 Playwright。

## Evidence

- Formal identity/result：Cfreeze=`f97483a0…`；run-status=
  `formal-passed/completed/exit0`；source exact=`96e2c871…c3a3`。
- Report inventory：required `773+59/5707/F0E0S0`；Addon `2/6/F0E0S0`；Step 3 exact
  gap/overlap/extra=`0/0/0`。
- Coverage/provenance：`23/48/16953`；aggregate `54624/76830 line`、
  `26111/44870 branch`；critical `12/23/below0`；model PASS。
- Negative evidence：contract `27/27`、effective POM `4/4`、toolchain `5/5`、generic XML
  `118/118`、successor `12/12`、report inventory `30/30`、exec `17/17`、coverage XML `9/9`。
- Fixture/state evidence：Unit fixture/lifecycle `36/36 + 5/5`；DB report/state
  `14/14 + 18/18`；external report/sensitive/Redis state `12/12 + 24/24 + 4/4`。
- Lifecycle/security：three child reaped、PG residue=0；cleanup=`0/0/0`；sensitive PASS；四个
  demo DB 在 evidence seal 后恢复 exact container ID 并为 running/healthy。
- Artifact identity：final SHA-256=
  `b6f695ccd641649de23b83c104cd944dc36ae8fc99f662f3bfeedcf9841e6416`；23 个
  final bindings 经独立 path/size/SHA 复算全部一致。
- Pivot companion：HEAD/source 与 formal 相同；legacy XML=`1/F0E0S0`、property absent、
  SHA=`802933e6…0dd`，明确命中 `daily_product_sales / preagg_daily_product_sales`。
- Quality/audit：B/H/M/L=`0/0/0/1`、mandatory fixes=0；workitem coverage=
  `25/25`；critical/major evidence gap=`0/0`。
- Experience：`N/A`，无 UI/浏览器交付。

## Failed Items

- none

formal-r1/r2/r3、diagnostic failures 与 superseded candidate 均保持 immutable/excluded；它们是
fail-closed regression evidence，不属于本 acceptance 的 failed item，也未被拼接进 formal-r4。

## Risks / Open Items

- Step 5 负责拆分 post-restore live inventory validation 与 durable artifact replay 入口，保持
  当前 live-state fail-closed；quality Low 不阻断本 feature。
- Pivot focused raw XML 与 formal raw root 的 portable archive 由 Step 5 生成；当前版本化 companion
  已绑定 command、HEAD、source/XML SHA、cardinality 与分支 oracle。
- Unit MySQL fixture classification debt 必须在 9.3.5 version acceptance 前关闭；本 decision 只接受
  9.3.4-only replacement，不把临时例外永久化。
- Step 5 portable single authority、Step 6 remote CI/branch protection 与 Step 7 same-JAR/clean
  version authority 尚未完成。
- 本记录不是 `version-signoff.md`；9.3.4 继续 `in-progress`，9.3.5 继续 `queued`。

上述事项均有明确 owner/后续步骤，不是 Step 4 blocking item。

## Final Decision

- decision: `accepted`
- Step 4 的正式执行、coverage/critical/model gate、fail-closed negative、source/provenance、
  lifecycle/cleanup、安全与 artifact replay 均满足 feature acceptance criteria。
- implementation quality 与 coverage audit 已按序通过；审计发现的 Pivot legacy Major 候选缺口
  已补证并独立复核，最终 blocking items=`none`。
- Step 4=`passed`；Step 5=`ready / not-started`；9.3.4=`in-progress`；9.3.5=`queued`。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- acceptance_scope: feature
- signed_off_by: Codex
- signed_off_at: 2026-07-18
- tested_commit: `f97483a0b87a82734d21888e7b5bea74b0c5fe55`
- authority_run_id: `step4-coverage-20260717-formal-r4`
- acceptance_record:
  `docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md`
- blocking_items: none
- follow_up_required: yes
