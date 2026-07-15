---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.3.4
target: step2-runner-split
status: reviewed
conclusion: ready-for-acceptance
reviewed_by: codex + v934_snapshot_skip + step2_requirement_coverage + step2_bug_coverage
reviewed_at: 2026-07-15
follow_up_required: yes
---

# Step 2 Runner Split Coverage Audit

## Background

本审计在 Step 2 implementation quality=`ready-with-risks` 后，检查 requirement、
acceptance item、15 个 Step 2 BUG 与 automated evidence 的映射。这里的 coverage 是
“测试证据覆盖”，不是 Step 4 JaCoCo code coverage；结论只适用于 Step 2 feature，
不签收五库、coverage、CI、release 或整个 9.3.4。

## Audit Basis

- quality gate：
  `docs/9.3.4/quality/step2-runner-split-implementation-quality.md`；
- requirement/contract/test plan：`docs/9.3.4/requirement/`、`contract/`、`test/`；
- workitems：`docs/9.3.4/workitems/*.md`，15/15 closed、unchecked=`0`；
- successor evidence：
  `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`；
- actual runner evidence：
  `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`；
- raw roots：successor、Unit、Integration 三个 exact
  `target/v934-step2-*/runs/*r8e-20260715/`；git head=
  `9f5428d8d15d08457d2d2d57296256178c224f5d`。

## Coverage Matrix

| Requirement / workitem | Risk | Required layer | Evidence | Result |
|---|---|---|---|---|
| INVENTORY / typed structural | critical | successor static + independent recompute | r8e review；532/820/770+59；33/33 negatives | covered |
| RUNNER-SPLIT | critical | all-reactor Unit + Failsafe variants | r8e exit；677 Unit + 47 IT positive；overlap=0 | covered |
| NO-ZERO | critical | report exact-set + mutation negatives | 59 strict-zero structural；positive>0；20+20 report negatives | covered |
| SKIP-ZERO（Step 2） | critical | raw XML outcome audit | 5,205 testcase；F/E/S=`0/0/0` | covered |
| REGRESSION-931-933（Step 2 portion） | critical | typed predecessor map + actual runner | 519 refs mapped；509 actual by Step 2；remaining 10 exact Step 3 owners | covered-in-scope |
| BUG-934-AGGREGATE-JOIN-SNAPSHOT-DEFAULT-SKIP | major | Unit | aggregate snapshot assertions execute by default；Unit S0 | covered |
| BUG-934-AUTHORITY-COMPLETED-SIGNAL-FAIL-OPEN | critical | runner contract + dynamic signals | INT/TERM/HUP=`130/143/129`；failed status；summary absent | covered |
| BUG-934-CALCULATE-MVP-SQLITE-SKIP | major | SQLite Failsafe | `CalculateMvpIT` in sqlite-broad；303 variant tests；S0 | covered |
| BUG-934-DATA-VIEWER-NAMESPACE-BINDING | major | Unit/Spring smoke | `DataViewerApiSmokeTest` in Unit exact set；target namespace binding | covered |
| BUG-934-EMBEDDING-SERVICE-DISABLED-UNIT | major | Unit/local fixture | `EmbeddingServiceTest` executes with local HTTP fixture；S0 | covered |
| BUG-934-FAILSAFE-REACTOR-SELECTOR-OVERRIDE | major | POM contract + Failsafe | six variants exact-set；helper selector override cannot hide owner gap | covered |
| BUG-934-MAVEN-ZERO-TEST-DEFAULT | major | POM/runner contract + negative | Surefire/Failsafe default failIfNoTests=true；zero mutation rejected | covered |
| BUG-934-MONGO-LOADER-ORDERING | major | Spring context + Unit authority | auto-configuration contract and Mongo tests in 4,890 Unit set | covered |
| BUG-934-MULTI-THREAD-EXECUTOR-PREMATURE-COMPLETION | major | deterministic Unit regression | 10,000-task start-gated exact result assertion in Unit；F0 | covered |
| BUG-934-PREDEFINED-CALCULATED-FIELD-IMMUTABLE-LIST | major | Unit regression | aggregate snapshot immutable `List.of` case executes；E0 | covered |
| BUG-934-STEP2-AUTHORITY-WORKSPACE-CONCURRENCY | critical | script contract + negative | shared exclusive lock、publish CAS、lock/CAS mutation probes | covered |
| BUG-934-STEP2-REPORT-CROSS-RUN-SPLICE | critical | report-tool negative | outer context/marker binding；cross-run splice rejected | covered |
| BUG-934-STEP2-REPORT-DISCOVERY-CARDINALITY | critical | report-tool negative + raw XML | discovery lower/exact bounds；partial testcase rejected | covered |
| BUG-934-SUREFIRE-NESTED-IT-LEAKAGE | major | runner ownership + exact set | Unit/IT raw union exact；nested IT extra/missing=`0` | covered |
| BUG-934-UNIT-AUTHORITY-DURABLE-STATUS | major | status contract + signal probe | Unit `completed/0/passed`；failed signal status is durable | covered |

## Evidence Summary

- confirmed successor：freeze
  `44b11ed756bf41e3b271ac57b59c2c882a0b31a56963f42ae154fdb5d37b2fb6`；manifest
  `4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919`；
  summary `f6b80aa5f48c6f32aaa99336823dd00d183d75a096767c74f7de2c21c1ac4b75`；
- Unit：`677 positive + 55 structural = 732 raw / 4,890 testcase / F0E0S0`；summary
  `55e9e8b67301aa24a743dfa56fd1f2c01ca9bd94c3889f11093c281d6ef2565a`；
- Integration：`47 + 4 = 51 raw / 315 testcase / F0E0S0`；summary
  `0ee6c45907a9b37b4dda726bb7ba38030a57503a40e52995f26d397ba0610e83`；
- combined：`724 positive + 59 structural = 783 raw / 5,205 testcase / F0E0S0`；
- independent raw review：783 XML 全部存在、不是 symlink，SHA、schema-v3 manifest
  与 XML metrics 一致；Step 2 actual predecessor refs=`470 execution + 39 structural =
  509`，余 10 refs exact owned by Step 3；
- expected-negative：successor `33/33`，Unit report `20/20`，Integration report
  `20/20`；三类 signal probes 另行实际注入；
- current source identity before/after/current：
  `12749d1fb9d37af04b8a3dd80ac49ea0fcc177309edcc0c49645a2c2c19a1a53`。

## Gaps

Critical/major evidence gap=`0/0`；Minor=`2`。独立 reviewer 的初始第三项 minor（Mongo
workitem 状态文案滞后）已在审计记录落盘前修正。

Non-blocking follow-ups：

- MultiThreadExecutor 的原始 premature-completion 路径已有强回归，但
  `shutdown=false`、`stopIfHasError=true`、interrupt 分支尚缺逐分支 deterministic
  latch tests；作为 Low 补强进入 Step 4 前待办，不影响本 BUG 的根因覆盖。
- runner 最终 PASS/disarm 顺序、report symlink check 顺序与编排复杂度属于 quality/
  maintainability risk；r8e 真实日志、status 和无 symlink run roots 已覆盖本次证据身份。
- 519 个 predecessor refs 已全部 typed；其中 509 由 Step 2 actual runners 覆盖，剩余
  10 个精确归属 Step 3。它们连同 Step 3 deferred=`46`、Step 4 JaCoCo、Step 5–7
  authority/CI/release 不在本审计范围，必须分别补 evidence，不能由
  `ready-for-acceptance` 外推。
- UI/manual/Playwright evidence 不适用：Step 2 没有用户界面或体验交付。

## Recommended Next Skills

- 如需独立 feature signoff，可使用 `foggy-acceptance-signoff` 且明确 scope=Step 2；
  当前版本仍按 1→7 顺序推进，因此不创建 9.3.4 version acceptance。
- 下一执行动作是 Step 3 五库/external matrix；其实现完成后重新执行 quality gate 和
  coverage audit，不复用本记录预签收 deferred executions。

## Conclusion

`ready-for-acceptance`（Step 2 only；审计语义=`ready-with-gaps /
can_enter_acceptance=yes`）。所有 Step 2 critical/major requirement 与 15 个 BUG 均有
自动化 positive/negative evidence，missing/overlap/skip/open checklist=`0`。
follow-up_required=`yes` 仅表示继续完成 Step 3–7 与 Low 补强；9.3.4 仍为
`in-progress`，Step 3=`ready`，9.3.5=`queued`。
