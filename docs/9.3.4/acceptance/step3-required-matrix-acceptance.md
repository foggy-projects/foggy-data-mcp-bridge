---
acceptance_scope: feature
version: 9.3.4
target: step3-required-matrix
doc_role: acceptance-record
doc_purpose: Record the formal feature acceptance decision for the 9.3.4 Step 3 required matrix.
status: signed-off
decision: accepted
signed_off_by: Codex
signed_off_at: 2026-07-16
reviewed_by: Codex + independent source/evidence reviewers
evidence_count: 11
blocking_items: []
follow_up_required: yes
---

# Step 3 Required Matrix Feature Acceptance

## Background

- Version: 9.3.4
- Scope: Step 3 feature acceptance
- Target: five-database and required external correctness matrix
- Tested commit: `ce3d70c391c7b8bd8046fe66dde0ad568d66601e`
- Authority run: `step3-required-20260716-final-r4`
- Version boundary: 只关闭 Step 3，开启 Step 4；不签收 9.3.4 version，不启动
  9.3.5。

## Acceptance Basis

- `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
- `docs/9.3.4/contract/test-lane-evidence-contract.md`
- `docs/9.3.4/implementation-plan.md`
- `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`
- `docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`
- `docs/9.3.4/evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md`
- `docs/9.3.4/quality/step3-required-matrix-implementation-quality.md`
- `docs/9.3.4/coverage/step3-required-matrix-coverage-audit.md`
- 四个已关闭的 Step 3 critical workitem 与 MultiThread Step 4 prerequisite closure
- parent/DB/external/Addon raw manifests、state negatives、source/fixture seals 和
  independent verifier/set recomputation

## Checklist

- [x] 五库 fresh/run-scoped identity、sentinel、fixture 与 QueryFacade/native parity
      全部通过，required skip=0。
- [x] database=`29 reports / 370 testcase / F0E0S0`。
- [x] required external=`16 reports / 76 testcase / F0E0S0`。
- [x] exact required union=`45/446`，gap/overlap/extra=`0/0/0`。
- [x] optional LLM 已 review、未执行、明确排除，未被计成 passed。
- [x] DB state=`18/18`、Redis resource state=`4/4`，report/sensitive/parent negatives
      全部通过。
- [x] PreAgg Addon companion=`2 reports / 6 testcase / F0E0S0`，独立于 45/446。
- [x] parent/source/fixture before=after，run-owned container/volume/network residue=0。
- [x] Step 4 并发执行器前置补强=`3/3/F0E0S0`。
- [x] implementation quality=`ready-for-coverage-audit`，open B/H/M=`0/0/0`。
- [x] test evidence coverage=`ready-for-acceptance`，critical/major gap=`0/0`。
- [x] Experience=`N/A`，本 feature 无 UI/浏览器交付。

## Evidence

- Parent：`45/446/F0E0S0`；contract SHA=
  `f2bd52df7ed2829051ad263f97d560d3f8babe048d25864edb725eb671ba4d1b`；
  final/candidate SHA=
  `9040bff263101ed7ad33dbc4681bbf37f48e39b00957d2bf8224fb506afa5282` /
  `6b1e5f3502dd2e666e5546ecf3c6469e9b5060bdafa8e7d4e1068fd41688cfa4`。
- DB cells：SQLite `5/50`、MySQL 5.7 `5/50`、MySQL 8 `6/105`、PostgreSQL 15
  `8/115`、SQL Server 2022 `5/50`；DB state=`18/18`。
- External lanes：Redis `2/3`、Mongo `4/30`、MySQL57 `8/23`、Vector `2/20`；
  Redis state=`4/4`。
- Addon：SQLite `1/3` + MySQL57 `1/3`；candidate SHA=
  `2634a23e64aee150a574998e6af99084c8b7e5ff3064bda2b25c1c47ff5e1741`。
- Negative evidence：parent `17/17`、DB report `14/14`、external report `12/12`、
  external sensitive `24/24`、Addon `4/4`；all resource residue=`0/0/0`。
- Independent review：blocker/high/medium=`0/0/0`；required inventory/observed=
  `45/45`，duplicates/optional leak=`0/0`。
- MultiThread prerequisite：companion evidence 绑定 command、clean tested HEAD、source/test
  SHA 与 `3/3/F0E0S0`；raw XML SHA=
  `5d3ba9ff2778886160262b499999753f98a0b9251f5a76f6b9f86c45d723291a`，不计入
  45/446，也不作为 JaCoCo provenance。

## Failed Items

- none

Failed/diagnostic r1/r2/r3 与 external MySQL hash/native-date attempts 均已在 exit
evidence 中列为 excluded；它们没有 candidate、未跨 run 拼接，也不属于本 acceptance
的 failed item。

## Experience

`N/A`。交付是后端模型生命周期、数据库/external test authority 与证据链，无页面、
交互、浏览器或人工体验验收项。

## Risks / Open Items

- Step 4 必须带 JaCoCo agent 重跑 all required lanes；Step 3 correctness XML 不包含
  可接受的 coverage provenance。
- runtime watermark 未持久化：reload/restart 后 `W=null`，首次 refresh FULL。
- 不声明 refresh 后缓存立即一致，也不把 SQLite+MySQL57 Addon lifecycle 扩大成五库、
  Controller/Cache 全生命周期。
- Mongo loader production JDBC dialect decoupling 继续由 9.3.5/9.4.0 处理。
- Step 5 portable archive、Step 6 remote CI/branch protection、Step 7 clean version
  authority 全部未开始。

上述事项均在 feature scope 外有明确 owner/步骤，不构成 Step 3 blocking item。

## Final Decision

- decision: `accepted`
- Step 3 的 critical/major 正确性、exact inventory、negative、resource cleanup、
  optional disposition 与 Addon companion 均有同一 committed HEAD 的可复核证据。
- implementation quality 与 test evidence coverage 已按顺序通过，无 blocker/high/medium
  或 critical/major gap。
- Step 3=`passed`；Step 4=`ready / not-started`；9.3.4=`in-progress`；9.3.5=`queued`。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- acceptance_scope: feature
- signed_off_by: Codex
- signed_off_at: 2026-07-16
- tested_commit: `ce3d70c391c7b8bd8046fe66dde0ad568d66601e`
- authority_run_id: `step3-required-20260716-final-r4`
- acceptance_record:
  `docs/9.3.4/acceptance/step3-required-matrix-acceptance.md`
- blocking_items: none
- follow_up_required: yes
