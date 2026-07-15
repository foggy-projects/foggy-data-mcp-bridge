---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.3.4
target: step3-required-matrix
status: reviewed
conclusion: ready-for-acceptance
reviewed_by: codex + independent formal manifest audit
reviewed_at: 2026-07-16
follow_up_required: yes
---

# Step 3 Required Matrix Test Evidence Coverage Audit

## Background

本审计在 Step 3 implementation quality=`ready-for-coverage-audit` 之后，检查 requirement、
acceptance item、四个 critical Step 3 BUG、状态负向与 automated evidence 的映射是否
完整。这里的 coverage 是“测试证据覆盖”，不是 Step 4 JaCoCo code coverage；结论只
适用于 Step 3 feature，不签收 Step 4–7 或整个 9.3.4。

## Audit Basis

- quality gate：
  `docs/9.3.4/quality/step3-required-matrix-implementation-quality.md`；
- requirement/contract/test plan：`docs/9.3.4/requirement/`、`contract/`、`test/`；
- exit evidence：
  `docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`；
- workitems：database contract、external matrix、PreAgg materialization、PreAgg
  refresh/query watermark；
- tested commit：`ce3d70c391c7b8bd8046fe66dde0ad568d66601e`；
- formal run：`step3-required-20260716-final-r4`；
- independent verifier/set audit：parent/DB/external/Addon manifests、exact execution
  union、state negatives、source/fixture seals、resource residue。

## Coverage Matrix

Evidence location key：

- `E1`：`docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`；
- `E2`：`target/v934-step3-required-matrix/runs/step3-required-20260716-final-r4/`
  下 `final/report-manifest.json`、`candidate-manifest.json`、`negative/probes.tsv`、
  `source-before.tsv`、`source-after.tsv`；
- `E3`：`target/v934-step3-database-matrix/runs/step3-required-20260716-final-r4/`
  下 `final/report-manifest.json`、`state-negative/manifest.json`、
  `state-negative/probes.tsv`、`negative/probes.tsv`；
- `E4`：`target/v934-step3-external-matrix/runs/step3-required-20260716-final-r4/`
  下 `final/report-manifest.json`、`candidate-manifest.json`、`negative/probes.tsv`、
  `negative/sensitive-probes.tsv`、`aggregate/redis-resource-state-negatives.tsv`；
- `E5`：`target/v934-step3-preagg-addon/runs/step3-required-20260716-final-r4/`
  下 `candidate-manifest.json`、`negative-probes.tsv`、runtime/resource evidence；
- `E6`：`docs/9.3.4/evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md`；
- `E7`：`docs/9.3.4/workitems/BUG-step3-*.md` 与
  `docs/9.3.4/workitems/BUG-multi-thread-executor-premature-completion.md`。

`E2`–`E5` 是 ignored run-local raw artifacts；其 identity、hash、统计与边界由 `E1`
持久化。`E6` 同样持久化 focused raw XML 的 command、HEAD、source/test SHA 与 content
SHA，但不是 immutable archive。

| Requirement / workitem | Risk | Validation layer | Evidence location / observation | Result |
|---|---|---|---|---|
| DB-IDENTITY | critical | integration + contract preflight | `E1/E3`：five fresh DB cells；exact product/version/coordinate/catalog/schema/sentinel | covered |
| DB-PARITY | critical | integration + QueryFacade/native oracle | `E1/E3`：five-cell `29 reports / 370 testcase / F0E0S0` | covered |
| SKIP-ZERO | critical | report verifier over raw XML | `E1/E2/E3/E4/E5`：DB/external/Addon `S0`；optional excluded，未被 skip 成绿色 | covered |
| Step 3 exact inventory | critical | collector + independent set recomputation | `E1/E2`：DB 29 + external 16 = union 45；gap/overlap/extra `0/0/0`；nodes 446 | covered |
| database state fail-closed | critical | negative integration/resource-state | `E1/E3`：wrong identity/major/catalog/schema/sentinel、fixture mutation、dirty/cleanup/readiness；`18/18` | covered |
| Redis state fail-closed | critical | negative integration/resource-state | `E1/E4`：wrong container identity/mount、dirty state、forced cleanup；`4/4` | covered |
| external required matrix | critical | integration across seven run-owned variants | `E1/E4`：Redis `2/3`、Mongo `4/30`、MySQL57 `8/23`、Vector `2/20`；total `16/76` | covered |
| optional LLM disposition | major | contract/static metadata review | `E1/E2`：`reviewed-optional-excluded`，owner/reason/next review 完整；optional leak=0 | covered |
| BUG-934-STEP3-DATABASE-CONTRACT-GAPS | critical | integration + report/state negatives | `E1/E3/E7`：DB `29/370`、state `18/18`、report negatives `14/14`、fixtures/source unchanged | covered |
| BUG-934-STEP3-EXTERNAL-MATRIX-GAPS | critical | integration + state/sensitive negatives | `E1/E4/E7`：`16/76`、report `12/12`、sensitive `24/24`、Redis state `4/4`、residue 0 | covered |
| BUG-934-STEP3-PREAGG-ADDON-MATERIALIZATION-CONTRACT | critical | unit/contract + real lifecycle integration | `E1/E5/E7`：explicit mapping/physical-column/watermark contract tests；SQLite/MySQL57 create/full/incremental/query `2/6`；negatives `4/4` | covered |
| BUG-934-S3-PREAGG-WATERMARK | critical | unit/service + lifecycle integration | `E1/E5/E7`：exclusive boundary、success-only publication、failure/no-W/type/future/late-row regressions；Addon `2/6` | covered |
| Step 4 concurrency prerequisite | major | focused deterministic unit | `E6/E7`：shutdown=false/error/interrupt `3/3/F0E0S0`；logger regression；raw XML SHA frozen in companion | covered |
| source/fixture integrity | critical | artifact seal + candidate verifier | `E1/E2/E3/E4`：parent source exact；five DB fixture exact；external MySQL content/snapshot/grants exact | covered |
| cleanup/sensitive safety | critical | lifecycle cleanup + negative scans | `E1/E2/E4/E5`：all run-owned container/volume/network residue `0/0/0`；scans/negatives pass | covered |
| diagnostic exclusion | major | governance/contract review | `E1/E2`：r1/r2/r3 与 MySQL hash/native-date diagnostics excluded；no cross-run splice | covered |

## Evidence Summary

- parent：`45 reports / 446 testcase / F0E0S0`，contract=
  `f2bd52df7ed2829051ad263f97d560d3f8babe048d25864edb725eb671ba4d1b`；
  final/candidate SHA=
  `9040bff263101ed7ad33dbc4681bbf37f48e39b00957d2bf8224fb506afa5282` /
  `6b1e5f3502dd2e666e5546ecf3c6469e9b5060bdafa8e7d4e1068fd41688cfa4`。
- database：`29/370/F0E0S0`；DB state=`18/18`；report negative=`14/14`；final
  SHA=`366c0306b973419b6cbdaff6ba22ce56f230706a509428d9130f5a722a61f014`。
- external：`16/76/F0E0S0`；Redis state=`4/4`；report/sensitive negatives=
  `12/12`、`24/24`；final/candidate SHA=
  `53cf60cc26b8680fc1a49dd8b3a8a32090666a0c58471148bf6de383889d4210` /
  `b2eac65e4452b206e640d746551e643929d41c71f30fc96ac46fa578964d2db4`。
- Addon companion：`2/6/F0E0S0`、negatives=`4/4`、candidate SHA=
  `2634a23e64aee150a574998e6af99084c8b7e5ff3064bda2b25c1c47ff5e1741`；
  `included_in_required_totals=false`。
- parent negatives=`17/17`；execution-key independent recomputation得到
  inventory/observed=`45/45`、duplicates=`0`、optional leak=`0`。
- same-HEAD focused prerequisite=`3/3/F0E0S0`；companion=`E6`，raw XML SHA=
  `5d3ba9ff2778886160262b499999753f98a0b9251f5a76f6b9f86c45d723291a`。

## Gaps

Critical/major evidence gap=`0/0`；Minor=`3`，均不属于 Step 3 feature acceptance
阻断：

1. Step 3 correctness 没有 JaCoCo exec；这是 Step 4 的显式输入，必须重新带 agent
   执行全部 required lanes，不能复用本轮 XML 冒充 coverage。
2. Mongo loader 的 production JDBC dialect 解耦仍开放；本轮只接受 run-local SQLite
   metadata guard 的 test-governance 边界。
3. portable archive、remote CI/branch rule 与 same-JAR release authority 分属 Step 5–7，
   不能由 run-local candidate 外推。

UI/manual/Playwright evidence=`N/A`：本 feature 没有用户界面或体验交付。

## Recommended Next Skills

- `foggy-acceptance-signoff`：对 Step 3 feature 做正式 accepted/rejected/blocked 决策；
  必须保留 Failed Items、Experience=N/A 与明确 evidence boundary。
- feature acceptance 后只把 Step 4 标为 `ready / not-started`；不得创建 9.3.4
  `acceptance/version-signoff.md`。

## Conclusion

`ready-for-acceptance`。所有 Step 3 critical/major requirement、四个 critical BUG、
database/external exact union、state/report/sensitive negatives、Addon companion 和
Step 4 并发前置项都有可复核 automated evidence，critical/major gap=`0/0`。本结论
只允许进入 Step 3 feature acceptance；9.3.4 仍 `in-progress`，Step 4 仍
`entry-candidate / not-started`。
