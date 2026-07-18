---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.3.4
target: step4-coverage-gate
status: reviewed
decision: ready-for-coverage-audit
reviewed_by: Codex + independent source/artifact/state reviewers
reviewed_at: 2026-07-18
follow_up_required: yes
---

# Step 4 Coverage Gate Final Implementation Quality

## Background

本记录在 fresh formal execution check-in 后，以正式模式审查 9.3.4 Step 4 的实现收口、
fail-closed、复杂度、可读性、来源/制品绑定、生命周期与文档边界。它只决定是否允许进入
`foggy-test-coverage-audit`；不替代 requirement-to-test 映射，不签收 Step 4 或 9.3.4。

## Check Basis

- tested Cfreeze：`f97483a0b87a82734d21888e7b5bea74b0c5fe55`
- direct parent Cdiag：`613b11a0ae6732f865f918551cd9116079771b5e`
- formal run：`step4-coverage-20260717-formal-r4`
- exit evidence：
  `docs/9.3.4/evidence/step-4/step4-coverage-exit-20260717.md`
- formal result：`formal-passed / completed / exit 0`
- public replay：candidate/final 均 `ARTIFACT VALID`；final SHA-256=
  `b6f695ccd641649de23b83c104cd944dc36ae8fc99f662f3bfeedcf9841e6416`
- independent reviews：source/Cfreeze/history isolation、artifact/hash/state/lifecycle、
  post-restore validator boundary 三路只读复核

## Changed Surface

Step 4 被审查的完整实现面包含：JaCoCo report-only module/profile、23-exec ledger、Unit/
Integration/DB/external/Add-on successor runners、typed XML/exec/report verifier、critical/model
gate、Unit MySQL 5.7 fixture、source/Git/toolchain seal、negative/lifecycle/cleanup contracts，以及
为消除 incidental coverage 补入的确定性业务 oracle。

Cfreeze 相对 Cdiag 的实际增量精确为 17 个路径：14 个 `docs/9.3.4/**` 文件以及 confirmed
threshold、formal-ready contract、Step 4 SHA256SUMS 三件 machine 文件；没有 production、
test、POM、runner、floor、critical set、exclusion 或 successor byte 变化。formal-r4 后只做
版本文档回写。

## Quality Checklist

| Check | Result | Evidence |
|---|---|---|
| Git/Cfreeze identity | pass | direct single parent；non-shallow；无 replace/graft；formalization delta=`passed / 17 paths` |
| source and class seal | pass | source before=after=`96e2c871…c3a3`；class tree=`a72007a8…99be`；`24/2,098` |
| formal terminal state | pass | run-status=`formal-passed/completed/exit0`；summary=`formal-candidate-ready` |
| test/report exactness | pass | required=`773+59/5707/F0E0S0`；Addon=`2/6`；Unit=`681+55/4941`；Integration=`47+4/320` |
| DB/external exactness | pass | database=`29/370`；external=`16/76`；Step 3 union=`45/446` |
| exec/provenance | pass | `23 exec / 48 sessions / 16,953 execution class identities`；aggregate/report provenance sealed |
| aggregate gate | pass | line=`54,624/76,830`；branch=`26,111/44,870`，exact 达 confirmed minimum |
| critical/model gate | pass | `12` classes、`23` applicable metrics、below=`0`；唯一 N/A=`NamespaceScope.branch`；model PASS |
| fail-closed evidence | pass | contract、exec、XML、report inventory、source/Git、toolchain、POM、fixture、state negatives 全 PASS |
| child lifecycle | pass | 3 child leader reaped=`1`、PG residue=`0`；ready/completion identity exact |
| cleanup/security | pass | runner residue=`0/0/0`；sensitive PASS；四个 exact demo DB 在 seal 后恢复为 running/healthy |
| public artifact replay | pass | clean Cfreeze 上 frozen diagnostic、candidate、final public verifier 全 PASS |
| historical isolation | pass | formal-r1/r2/r3 与 failed diagnostics immutable/excluded；只消费 reviewed r19 frozen baseline |
| implementation clarity | pass | safety invariants集中于 typed tools/contracts；未新增 bypass、吞异常、重复 threshold authority 或公共 API |
| documentation boundary | pass | formal exit 已 check-in；Step 4/5、acceptance 与 9.3.5 未提前写绿 |

## Findings

Open Blocker/High/Medium/Low=`0/0/0/1`；mandatory fixes=`0`。

### Low — live inventory replay 与 durable artifact replay 的入口易混淆

`report_inventory_tool validate` 会重建 live authority，并通过 Unit fixture verifier 检查冻结端口
`13306` 当前仍 free。formal-r4 evidence window 内 `verify + validate` 均 PASS；runner seal 后外层
按契约恢复 exact demo MySQL，故在 restored state 再单独调用 live validator 会正确 fail closed 为
`E_UNIT_FIXTURE`。这不会产生 false-green，final artifact 已绑定 report inventory、cleanup、summary
和 provenance，restored state 的 canonical public replay 是
`verify-artifact final --run-status`，且已 PASS。

该 Low 不阻断 coverage audit。owner=`Step 5 single authority runner / immutable evidence
rehearsal`；关闭点为显式拆分 durable artifact replay 与 evidence-window live lifecycle validation，
或提供 outer-boundary-aware 验证入口。不得把 post-restore live validator 误写成 public acceptance
gate，也不为此修改已测试 Cfreeze 或重跑 formal-r4。

## Workitem and Debt Review

25 个 Step 4 workitem 的实现与 formal closure evidence 均已齐；9 个已 closed 不回退，16 个当前
`in-progress` 项已无开放实现 Blocker/High/Medium。它们的 frontmatter/checklist 仍保留到后续
coverage audit 与 feature acceptance 实际完成后统一关闭，避免把尚未发生的 gate 预填为绿色；
这属于治理顺序，不是实现 finding。

`DEBT-unit-mysql57-fixture-classification-migration.md` 继续保持
`open / accepted-for-9.3.4-only`，owner/gate owner 仍为 `foggy-dataset / 9.3.5 version
acceptance`。formal 已满足临时放行的运行与测试条件；本质量闸门不冒充 coverage audit、feature
acceptance 或 9.3.4 version acceptance。

## Risks / Follow-ups

- Step 5 负责上述 validator 入口可用性、single authority、portable archive 与 rehearsal；
  不得削弱当前 live-state fail-closed。
- Step 6/7 的 remote CI、branch protection、same-JAR release 和 clean version authority 尚未开始。
- Step 4 feature acceptance 通过也只开放 Step 5，不把 9.3.5 标为 ready。
- 现有大型 shell/Python authority tools 是测试治理实现，后续简化必须保留 exact manifest、
  source/provenance 和 negative semantics。

## Recommended Next Skill

使用 `foggy-test-coverage-audit`，逐项核对 requirement、25 个 Step 4 workitem、confirmed
aggregate/critical threshold、全 lane、negative、lifecycle、cleanup、public final artifact 与
自动化证据映射。只有 critical/major evidence gap=`0/0` 才可进入 feature acceptance。

## Decision

`ready-for-coverage-audit`。formal-r4 与独立 source/artifact/state review 未发现开放
Blocker/High/Medium，唯一 Low 有明确 Step 5 owner且不产生伪绿；实现已收口，正式测试证据可进入
覆盖审计。当前 `can_enter_coverage_audit=yes`、`can_enter_acceptance=no`；Step 4 仍
`in-progress`，Step 5 与 9.3.5 仍关闭。
