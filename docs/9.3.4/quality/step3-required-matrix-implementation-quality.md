---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.3.4
target: step3-required-matrix
status: reviewed
decision: ready-for-coverage-audit
reviewed_by: codex + independent source audit + formal manifest audit
reviewed_at: 2026-07-16
follow_up_required: yes
---

# Implementation Quality Gate

## Background

本记录在最终 execution check-in 之后审查 9.3.4 Step 3 的实现收口、复杂度、可读性、
并发/发布边界、fail-closed 行为和证据回写。范围包含 PreAgg materialization/watermark、
五库与 external runner/report authority、fixture/readiness 修复及 Step 4 并发前置补强；
不替代测试证据覆盖审计，不审 JaCoCo Step 4，也不签收整个 9.3.4。

## Check Basis

- execution check-in：
  `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`；
- exit evidence：
  `docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`；
- tested commit：`ce3d70c391c7b8bd8046fe66dde0ad568d66601e`；
- formal run：`step3-required-20260716-final-r4`；
- exact authority：required `45/446/F0E0S0`、Addon companion `2/6/F0E0S0`；
- independent read-only source review：PreAgg lifecycle/watermark/SQL builder、scheduler
  state isolation、SQLite/native DATE binding 与 runner/contract freeze；
- independent evidence review：parent/DB/external/Addon verifiers、execution-key set
  recomputation、source/fixture seals、negative/state/resource cleanup。

## Changed Surface

- production/model：runtime PreAggregation watermark/refresh publication，matcher、hybrid
  rewriter、query step default 与 lifecycle interception；
- production/addon：`PreAggPhysicalColumnContract` 统一物化列显式映射，shared strict-DAY
  `PreAggWatermarkResolver` 统一 semantic/source/materialized watermark roles，另含
  DDL/refresh SQL builder、refresh service、scheduler synchronization/state snapshot；
- production/core：`MultiThreadExecutor` debug logging；
- tests/fixtures：PreAgg lifecycle、service/scheduler/matcher/builder、five-DB native DATE
  fixtures、SQLite DATE/TEXT binding、deterministic concurrency branches；
- test authority：Step 3 parent/database/external/Addon contracts、runners、report/state
  verifiers、sensitive and cleanup probes；
- docs/workitems：四个 Step 3 critical BUG closure、Step 2 Low/Minor follow-up closure。

## Quality Checklist

| Check | Result | Evidence |
|---|---|---|
| scope and unrelated-change control | pass | changes map to four Step 3 critical workitems and one Step 4 prerequisite; no public API/module split |
| materialization contract consistency | pass | Addon/query consume consistent explicit mapping data；watermark roles share strict resolver；unknown/type-mismatched roles fail closed |
| watermark semantics and publication | pass | exclusive `< W / >= W`; publish only after success; first no-W refresh FULL |
| concurrency and state isolation | pass | service locks runtime preAgg; scheduler holds taskInfo across capture→service→mirror publication; result/future snapshots isolated |
| dialect/fixture correctness | pass | native DATE across MySQL/PostgreSQL/SQL Server; SQLite binding role-aware; no silent MySQL fallback |
| authority fail-closed design | pass | exact contract/hash/source/tree binding; missing/stale/wrong/state/signal probes reject |
| readability and duplication | pass-with-follow-up | shared resolvers/libraries centralize safety logic; orchestration remains large but bounded to test authority |
| test determinism | pass | fresh storage, deterministic embedding/seed/readiness, exact XML and branch-latch tests |
| cleanup and sensitive handling | pass | all run-owned residue 0/0/0; sensitive scans and negatives pass |
| documentation boundary | pass | Step 3 implementation closed；Step 4 entry-candidate/not-started pending coverage + acceptance；no JaCoCo/version/CI overclaim |

## Findings

Resolved before this decision：

1. High — MySQL container health could precede business identity/final init. Readiness now
   requires business login plus four final `preagg_watermark` markers; DB state suite=`18/18`。
2. High — native DATE/PreAgg fixture changed external MySQL content while two verifiers retained
   an old hash. Runner and report tool now bind the same canonical content; final MySQL `8/23`
   and parent external `16/76` pass with before=after.
3. High — refresh result was not query-visible and inclusive day semantics could lose late rows.
   Runtime publication is success-only，hybrid boundary is exclusive，query reads W once.
4. Medium — materialized/source/watermark roles were inferred independently. Shared strict-DAY
   resolver and explicit mappings remove cross-side token reuse and reject unknown mappings.
5. Medium — scheduler exposed mutable/live state and dialect fallback could select MySQL.
   State snapshots and fail-closed dialect resolution now have deterministic regressions.
6. Medium — Step 4 prerequisite lacked deterministic executor branches and emitted wait output to
   stdout. Logger plus three focused tests close both findings.

Open blocker/high/medium=`0/0/0`。

Open non-blocking findings：

1. Low — Step 3 runners are intentionally verbose and large. Step 5 single authority should
   centralize remaining non-safety orchestration without weakening current exact verifiers.
2. Low — Mongo-only model loading still uses a run-local SQLite metadata guard; production JDBC
   dialect decoupling remains a 9.3.5/9.4.0 follow-up, not a hidden Step 3 completion claim.
3. Low — runtime watermark is volatile by design in this scope. Reload/restart produces `W=null`
   and first refresh FULL; durable persistence requires a separately reviewed contract.
4. Low — `PreAggSqlBuilder`（575 lines）与既有 `PreAggQueryRewriter`（2148 lines）仍是
   production 大类；9.3.5 负责按 typed execution stage 拆解，本 Step 已抽离 physical/
   watermark resolution，不在 9.3.4 扩公共 API 或为降行数重开 tested commit。
5. Low — `PreAggregationDef` Javadoc 示例中 `granularity`、`dimensionProperties` 两行
   星号缩进不齐；这是纯注释 hygiene，留给下一次 code-bearing maintenance，不能为此
   修改已由 formal run 绑定的 `ce3d70c3`。

## Risks / Follow-ups

- Step 4 must attach JaCoCo agents and rerun all required lanes; Step 3 correctness XML cannot be
  reused as coverage exec provenance.
- Step 5 owns portable archive, runner simplification and remaining Step 2 PASS/disarm/symlink
  maintenance findings.
- Step 6/7 own remote CI, branch protection, same-JAR release and version authority; none are
  implied by this feature gate.
- Addon evidence is SQLite+MySQL 5.7 `2/6`; it must not be generalized to five-DB Controller/Cache
  lifecycle or immediate cache consistency.

## Recommended Next Skills

- `foggy-test-coverage-audit`：按 requirement、four critical Step 3 BUG、DB/external/state
  negatives、Addon companion 和 MultiThread prerequisite 逐项核对 evidence mapping。
- coverage audit 通过后使用 `foggy-acceptance-signoff` 做 Step 3 feature acceptance；
  不创建 9.3.4 version signoff。

## Decision

`ready-for-coverage-audit`。Step 3 实现已收口，独立 source/evidence review 未发现开放
blocker/high/medium；Low 均有明确下游 owner，且未被写成已完成能力。本决定只放行
Step 3 feature 的测试证据覆盖审计；9.3.4 仍 `in-progress`，Step 4 仍
`entry-candidate / not-started`，9.3.5 仍 `queued`。
