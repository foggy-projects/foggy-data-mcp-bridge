---
doc_role: acceptance-evidence-plan
doc_purpose: Define required evidence and gate order for 9.3.4 version acceptance.
version: 9.3.4
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-17
---

# 9.3.4 Acceptance Evidence Plan

## 文档作用

- doc_type: acceptance-evidence-plan
- intended_for: implementation owner / quality reviewer / coverage auditor /
  version signoff owner
- purpose: 在开工前冻结签收材料、critical 阻断项和 post-gate 顺序；不预填绿色。

## Required Materials

| Material | Planned path/evidence | 当前状态 |
|---|---|---|
| requirement | `requirement/P0-test-ci-evidence-chain.md` | ready |
| confirmed contract | `contract/test-lane-evidence-contract.md` | Step 1 frozen + Step 3 exit confirmed |
| module responsibility | `module-responsibility.md` | ready |
| reviewed code/test inventory | `code-inventory.md` + frozen inventory/predecessor migration/threshold manifests | Step 1 frozen / Step 3 runtime recorded / Step 4 r9 coverage-report excluded、remediations implemented、quality passed / commit pending / Steps 5–7 pending |
| implementation plan | `implementation-plan.md` | ready |
| progress/check-ins | `progress/test-ci-evidence-chain-progress.md` | in-progress / Steps 1–3 passed / Step 4 r1–r9 and Unit remediation r2 excluded / r9 remediations + fresh r10 pending |
| test plan/results | `test/test-ci-evidence-chain-test-plan.md` + exact raw reports | Steps 1–3 passed；version in-progress |
| Step 1–6 evidence | immutable per-step records under `docs/9.3.4/evidence/` + run roots | Steps 1–3 authority present；Step 4 r1–r9 与 Unit remediation r2 failed evidence、Unit remediation r3 pass evidence 已记录，但 Step 4 exit evidence absent；Steps 4–6 pending |
| implementation quality | `quality/step2-runner-split-implementation-quality.md` + `quality/step3-required-matrix-implementation-quality.md` + `quality/step4-diagnostic-ready-implementation-quality.md` | Step 2 reviewed；Step 3 ready-for-coverage-audit；Step 4 remediation quality passed / commit pending，B/H/M/L=`0/0/0/0`，can_enter_coverage_audit=no |
| coverage audit | `coverage/step2-runner-split-coverage-audit.md` + `coverage/step3-required-matrix-coverage-audit.md` | Step 2/3 feature evidence ready；Step 4 not-started/not-allowed，critical/major gap 尚未审计 |
| Step 3 feature acceptance | `acceptance/step3-required-matrix-acceptance.md` | signed-off / accepted；not version signoff |
| version signoff | planned `acceptance/version-signoff.md` | not-started |
| roadmap/status sync | `README.md` + authoritative roadmap | Step 3 synced；final version sync after version signoff |

Evidence documents must reference exact run id、commit SHA、root/archive digest 和原始
artifact location；不得只引用可移动 `latest` 指针。

Current Step 1 evidence：
`docs/9.3.4/evidence/step-1/inventory-contract-freeze-20260714.md`，confirmed summary
SHA-256=`579e9430bea6f873e7c4465cd1a6e45c49d348d84a89d5d648d25e3a5a4bbc50`。

Current Step 2 evidence：
`docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`；confirmed
successor manifest SHA-256=
`4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919`，actual result=
`724 positive + 59 structural / 5,205 testcase / F0/E0/S0`。Step 3 deferred=`46`。

Current Step 3 evidence：
`docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`。正式 parent
`step3-required-20260716-final-r4` 在 tested commit
`ce3d70c391c7b8bd8046fe66dde0ad568d66601e` 上确认 database `29/370`、required
external `16/76`、exact union `45/446/F0E0S0`、gap/overlap/extra=`0/0/0`，DB state=
`18/18`、Redis state=`4/4`、Addon companion=`2/6`、optional LLM reviewed exclusion。
Step 3 quality、coverage audit 与 feature acceptance 已按序完成；该结果满足 Step 3 exit，
但不满足 Step 4 coverage 或 9.3.4 version acceptance。

Current Step 4 readiness（2026-07-17）：状态仅为
`in-progress / r9 coverage-report excluded / exec-scope remediation implemented /
lifecycle semantic remediation implemented / quality passed / commit pending / fresh r10 pending`。
静态执行结构仍精确为
`23 exec / 48 sessions`，required
report overlay 为 `773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon
companion 仍单列 `2/6`。contract/source identity=`21/21 + 22/22`，effective POM/
toolchain/report inventory=`4/4 + 5/5 + 30/30`。Unit negative receipt=`36/36`：原
fixture/manifest schema/tamper=`20/20`、connection receipt typed=`7/7`、atomic
publisher=`3/3`、profile isolation=`6/6`；negative receipt schema tamper 另为 `4/4`，
真实 fixture lifecycle=
`5/5`。Step 2 derived view/successor overlay=`12/12 + 12/12`，XML=`68/68`，logger=
`9 类 / 14 case`；toolchain receipt 绑定 Step 1 raw 工具版本、ASM `9.6/9.7/9.7.1`
三层 realm 和 24 个 production module effective compiler。tracked FIFO preflight
fail-fast 与 before/after raw stat identity 并发重写拒绝均已纳入 `22/22`。
当前 r9-remediation static identity 已复验为 top=`60/60` /
`6be72b655b322d89763fc4871c953cd0d4bd5516206964d4cb1f8117b3133376`、successor=
`14/14` / `e63b315e9607c1f7efbf3f0bffe99e0800a4c1062e9fcfa5c2c569ecf67cc5db`、
amendment=`12 rows / 4 new + 8 changed` /
`998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`。
source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个
candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed
attributes 若声明 external clean filter，则在任何 worktree-aware Git hash/driver hook
执行前 fail closed，negative 证明 hook 未执行。

该 remediation 以 run-owned MySQL 5.7 替换完整 Unit lane；`6 reports / 11 testcase
nodes` 只是已知隐藏依赖清单，不是其他 Unit 测试无 DB 访问的声明。Step 2
identity/cardinality 继续提供结构基线，但其 Unit 正确性绿色不复用。机器例外契约为
`scripts/v934/step4/unit-mysql57-fixture-contract.json`，迁移债务为
`docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`。只有
`foggy-dataset` test resource 使用 `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholder；
其他模块 profile 不受 callback 影响。outer/callback 双层拒绝 underscore/dotted/hyphen
Spring/custom key 及 `@argfile`、`VMOptionsFile`、`javaagent/agentlib/agentpath` 间接注入；
adapter inventory 使用 scrubbed Git environment、`HEAD` tree 与 no-replace object。
connection receipt 的 closed Unit Maven window 由 configure `init_connect` 开始，由同一
root batch 先 disable 再按 `connection_id` SELECT 结束；有序 observed user 必须证明该
窗口内全部 non-super connection exclusively 使用 restricted `v934_unit`，callback 后的
provisioner `foggy` 控制面在窗口外。fixture hardening 的 top
manifest/amendment 最终静态身份已按上文复验；fresh Unit r3 与正式 remediation quality
均已通过，B/H/M/L=`0/0/0/0`。fresh r8 已在 lifecycle bootstrap contract drift 上 fail
closed/excluded；对应修复的 Unit shape/seal=`13+3`、Integration=`11+5` 且两路质量=
`0/0/0/0`。这是 r8 前的历史边界；当时 authoritative closure commit/push/clean HEAD 与
fresh r9 仍 required；不得以 Unit
或 focused lifecycle subgate 冒充 Step 4 通过结论。

Unit remediation r2 不是 acceptance evidence：
`step4-unit-fixture-quality-20260716-r2` 在 commit
`a603f839a98d99b2d7beb8379f76b4d85539328c` 上封存 source-before=`3,981 files` /
`087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`，lifecycle=`5/5`；
随后全 reactor `-Dspring.datasource.*` 形成 SQLite driver + MySQL URL，
`foggy-dataset-model=3,115/F0E631S0`。final manifest/summary absent；child=
`unit-mysql57-90da4977dc197f81` cleanup=`0/0/0`、port free，demo exact container 已恢复
healthy。r2=`excluded/non-reusable`，record=
`docs/9.3.4/evidence/step-4/step4-unit-profile-isolation-r2-fail-closed-20260716.md`。

Superseding Unit remediation r3 同样不是 Step 4 acceptance evidence，但其 Unit subgate
已经通过：run=`step4-unit-fixture-quality-20260716-r3`，tested commit=
`50161a0a869430e353f3933d9bb00dda59d9c4b1`，source before=after=`3,982 files` /
`1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；唯一 Surefire
invocation=`681 positive + 55 structural = 736 raw reports / 4,941 testcase / F0E0S0`。
fixture negatives=`36/36`、receipt schema/tamper=`4/4`；closed receipt 的 `18/18`
connections 全部为 `v934_unit`；lifecycle=`5/5`，逐项及最终 run-owned
container/volume/network cleanup=`0/0/0` 且 port free。evidence window 外按 exact ID
`0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` 恢复 demo
MySQL 为 `running/healthy`。record=
`docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`。

首次 clean all-lane attempt 已执行但不是 acceptance evidence：clean/pushed HEAD
`bc100b0f63bd3ff62d1105611dae41741790aedd` 的
`step4-coverage-20260716-diagnostic-r1` 在 `child-unit` 以
`3115 tests / 1 failure / 0 errors / 0 skipped` fail closed，未到 aggregate/report/
threshold。`PreAggregationDataValidationTest` 腐化 daily 但实际命中 monthly，并存在
raw-vs-raw/nullable-empty 伪绿；修复 focused=`9/F0E0S0`、组合=`57/F0E0S0`，source=
`affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`，见
`docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`。threshold 仍为
`diagnostic-pending`，尚无 aggregate baseline/review 或 Step 4 exit evidence。

第二次 clean all-lane attempt 同样不是 acceptance evidence：clean/pushed HEAD
`0101a44a07784bf6b484d490c7fb508727fbab70` 的
`step4-coverage-20260716-diagnostic-r2` 完整通过 Unit
`681 execution + 55 structural / 4,941 testcase / F0E0S0`，随后 Integration=
`312/F1E0S0`，唯一失败为 `PreAggregationL2CacheIT`；outer 在
`child-integration` fail closed，后续 required lanes、aggregate 与 threshold 未运行。failed
evidence 固定为
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md`。
L2 修复后 focused=`1/F0E0S0`、组合=`30/F0E0S0`；主动发现的 Pivot legacy/V934
SQLite 分支修复后各 `1/F0E0S0`。两项只改测试 fixture/assertion，不能替代 r3。

第三次 clean all-lane attempt 仍是 failed diagnostic，不是 acceptance evidence：
clean/pushed HEAD `e16693297239f2a861f3b93b3de60c1bb783bda0` 的
`step4-coverage-20260716-diagnostic-r3` 完整通过 Unit
`681 positive + 55 structural / 4,941 testcase / F0E0S0`，但 outer 在
`child-unit` 返回后检测到同 PGID 的 live logger 并 fail closed。Integration/
database/external/Addon/aggregate/threshold 未执行，summary absent。failed evidence=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`；
bug record=
`docs/9.3.4/workitems/BUG-step4-child-run-log-tee-residue-race.md`。未受管
`exec > >(tee ...)` 的 close/wait 竞态已被稳定定位，r3 Unit 绿色不得拼接使用。

第四次 attempt 同样不是 acceptance evidence：r4 的 reported launch head=
`ceea084ca25a9d679ba128e3f6bd50a63322c112`，run=
`step4-coverage-20260716-diagnostic-r4`。outer 在 `source-before` 以 exit code `2` fail
closed；run-owned Git/source seal、全部 test lane、aggregate、threshold 和 summary 均 absent。
reported launch head 不是 run-owned `tested_commit`。immutable failed evidence=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md`；BUG=
`docs/9.3.4/workitems/BUG-step4-source-inventory-filemode-false.md`；decision=
`excluded-from-step4-exit`。

第五次 attempt 同样不是 acceptance evidence：r5 tested commit=
`a35b99cb08f42817d8e75c440f18910b6961841b`，run=
`step4-coverage-20260716-diagnostic-r5`。r5 建立 run-owned source seal 并完成
Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0`、
Addon=`2/6/F0E0S0`；database-state companion 随后因 frozen Step 3 authority manifest
的 model POM SHA stale 而以 `E_AUTHORITY_MANIFEST` fail closed。database cells、
external、aggregate、threshold、source-after 与 summary 均 absent，r5=
`excluded-from-step4-exit`，partial lanes 不可复用。immutable failed evidence=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`；BUG=
`docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`。

successor database-state/required-report adapters 已实现，database runner、required
runner 与 report_inventory 均绑定 successor selector，frozen Step 3 保持不变；
focused/static remediation 通过，但不是 all-lane coverage evidence。

Cdiag implementation 已收口 managed FIFO logger、logger close/reap 负例、
PID/PGID/SID/starttime/boot-id child identity、清理前 member snapshot、typed child
lifecycle/XML/formalization-delta validator 与 generic XML negative tool。成功
`run-status.env` 只能在全部 canonical evidence 校验完成后作为最后一次原子
发布；formal 必须真实重放 threshold 所指 diagnostic run 的冻结
threshold/contract，拒绝合成 run。`Cfreeze` 只允许 diagnostic commit 的一个
direct-single-parent child，merge、multi-commit、shallow、replace/graft 均 fail closed。
pre-r4 最终快测、正式实现质量闸门与 identity/manifest 级联属于历史状态。r4 后已完成
source-policy 实现、focused static 与新 identity/manifest 级联；这些仍不是运行时 coverage
evidence。最终字节 review=`ready-with-risks`、B/H/M/L=`0/0/0/2`；两项 Low 均 accepted：
`/usr/bin/echo` 平台前提漂移会 fail closed；同 UID 视为 build authority，未来更强隔离改用
readonly snapshot/独立 checkout。该结论保留为 r4 前历史 review；当前 Unit fixture
remediation r3 与正式 quality 已通过。r8 又因 stale Unit direct-trap lifecycle shape 在
`bootstrap-negative` fail closed；run identity、lane、aggregate、threshold 与 summary
absent，r8 excluded/non-reusable。对应 immutable evidence/BUG 为
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md`、
`docs/9.3.4/workitems/BUG-step4-unit-lifecycle-static-contract-drift.md`。修复后 dynamic=
`9 类 / 14 case`、Unit shape/seal=`13+3`、Integration=`11+5`，两路独立 quality=
`0/0/0/0`。这是 r8 remediation 的历史边界；当时 closure commit/push/clean HEAD 和
fresh r9 之前，Step 5 仍关闭。

Step 4 implementation quality 已记录于
`docs/9.3.4/quality/step4-diagnostic-ready-implementation-quality.md`，decision=
`ready-with-risks` 的 r1/r2 结论仅作历史记录。Cdiag pre-r4 main gate 已以
`ready-with-risks`、open Blocker/High/Medium=`0/0/0` 放行了历史 clean-HEAD r4；source-policy
最终字节修正的正式复核为 `ready-with-risks`、B/H/M/L=`0/0/0/2`，两项 Low accepted。当前
Unit remediation superseding gate=`pass`；r8 lifecycle contract gate 的历史结论曾为
`pass / ready-for-commit-and-fresh-r9`。r9 随后在 coverage-report fail closed，且新的
lifecycle semantic bypass 审计重新打开 Step 4 quality；当前
`can_enter_coverage_audit=no`。fresh r10、aggregate review、confirmed thresholds 与 fresh
formal 完成前不得启动 coverage audit。

## Mandatory Acceptance Evidence

1. reviewed frozen `source-inventory.tsv` + `execution-inventory.tsv`：workspace/
   reactor boundary、helper/non-reactor disposition、nested report 和 DB/provider
   variants 全部明确；execution key orphan/overlap/duplicate/ambiguous=0。predecessor
   migration group declared/observed cardinality exact、unmapped/edge-duplicate=0；
   immutable Step 1 pre-rename baseline、rename-plan SHA、confirmed Step 2 successor
   parent link 与 approved exact delta 均可复算。Step 2 successor 的 positive execution
   与 structural report inventory typed 分离，519 predecessor edges 以 480 execution +
   39 structural XOR refs 保持完整。
2. Surefire/Failsafe actual execution：Step 2 与 Step 3 raw execution-key sets 分别
   exact match confirmed Step 2 successor 的各自 subset，二者并集覆盖该 generation
   全部 required positive execution inventory、交集为空；每个 variant 的 raw XML
   exact 等于 positive + structural expected set，positive tests>0、structural strict-zero，
   owning nested/variant reports fresh、exact。对 Unit lane，Step 2 identity/cardinality 只作
   结构基线，正确性不得复用；必须由 fresh Step 4 run-owned MySQL 5.7 完整 Unit lane
   replacement 提供新的正确性证据。
3. SQLite、MySQL 5.7、MySQL 8、PostgreSQL 15、SQL Server 2022 全部 required，
   product/version/physical identity/sentinel/fixture/parity 可复算且 S0。
4. Step 4 带 agent 重跑全部 required lanes；JaCoCo exec provenance、aggregate XML
   verifier、model UT+IT merged 既有门和 critical-class gates 全部实际生效；
   missing/low/tamper/empty-check/threshold drift fail。
5. Step 5 candidate 明确不计签收；Step 7 单一 clean-commit release authority 的
   source/worktree、reports、DB、coverage、JAR、nested JAR、image digest/embedded-JAR、
   inner/outer/archive digest 一致，archive 下载复验通过。
6. PR/main stable required aggregator 的实际远程运行和 branch-rule 证据；五库
   artifacts exact set/cardinality=5，任一 required job failure/skipped/cancelled、
   missing/duplicate/wrong-kind artifact 都使其失败。
7. release 校验 tag SHA；GitHub asset 与 Docker `/app/app.jar` 都使用同一已测 JAR
   SHA；missing artifact、skip-test/external 或 Docker source rebuild 会失败。
8. current source 上 migration-backed 9.3.1–9.3.3 successor 回归无退化；历史 raw
   evidence/runner 不修改，新旧映射与 current XML/manifest 均保留。
9. evidence sensitive scan 通过，无 credential/token/password/密钥 URL。
10. implementation self-check、formal quality、coverage audit、version acceptance
    按固定顺序完成。

## Acceptance Rules

以下任一缺失都阻断签收，不能降级为 `accepted-with-risks`：

- 五库任一 required lane 未执行、skipped、identity/sentinel 不可证或报告不 fresh；
- source/execution/structural inventory 仍有 orphan、nested/variant missing、overlap、
  duplicate、positive zero、structural nonzero/无 sibling、未声明 non-reactor source或
  双义命名；
- predecessor migration group cardinality 不符、node unmapped/edge duplicate，或要求
  篡改旧 runner 过门；
- aggregate/critical coverage 依赖空 reporter check、降门槛、扩大 exclusion 或缺失
  exec/XML 通过；
- CI required aggregator 无实际运行、五库 artifact set/cardinality 不精确，或不能
  拒绝 skipped/cancelled；
- branch protection/required check 外部状态无法证明；
- release/Docker 重建不同 JAR、image embedded SHA 不同、使用 skip-test/external
  通道或 artifact missing 只告警；
- authority 非 clean commit、跨 run 拼接、source/report/DB/JAR/image/hash 漂移；
- inner/outer/archive digest 或 download verification 失败；
- 9.3.1–9.3.3 critical regression 回退；
- 上游 gate 未通过就创建下游绿色 acceptance。

只有文档措辞、非 required 辅助 backend 粒度、已分配且不影响 critical gate 的
维护性风险可考虑 `accepted-with-risks`，并必须写 owner、版本和证据边界。

## Gate Order

```text
Step 7 implementation check-in + progress
  -> Implementation Self-Check
  -> foggy-implementation-quality-gate
  -> foggy-test-coverage-audit
  -> foggy-acceptance-signoff (version scope)
  -> README / requirement / contract / progress / roadmap status sync
```

任一上游 gate 未通过，不创建下游绿色记录。quality 只审实现质量，不代替测试
覆盖；coverage audit 只审 requirement/BUG/evidence 映射，不代替正式签收。

Step 3 feature 已使用同一顺序完成独立门：

```text
Step 3 execution check-in
  -> Step 3 implementation quality
  -> Step 3 test evidence coverage audit
  -> Step 3 feature acceptance
```

该 feature 链只打开 Step 4，不替代上方 Step 7 的 version-scope quality、coverage 与
version acceptance；Step 7 仍需在最终 clean authority 后重新执行版本后置门。

## Current Step 4 acceptance boundary（r9 superseding）

- r9=`excluded/non-reusable`：required lanes 与 `23/48` exec 已完成，但 exec-manifest、
  aggregate、source-after、threshold observation、summary 均 absent；
- 两个 blocker workitem 仍未通过 fresh authority closure：
  `BUG-step4-exec-class-id-scope-drift.md` 与
  `BUG-step4-lifecycle-semantic-validator-bypass.md`；
- focused exec/contract/overlay 结果只允许进入 implementation quality，不允许进入
  coverage audit 或 acceptance；
- `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。必须先取得 fresh r10 diagnostic，
  direct-child exact threshold freeze、fresh formal 与最终 implementation quality PASS，才可
  按 Gate Order 调用 test coverage audit；audit PASS 后才可执行 version acceptance。

## Final Signoff Contract

最终 acceptance record 至少使用：

```yaml
acceptance_scope: version
version: 9.3.4
target: test-ci-evidence-chain
status: signed-off | rejected | blocked
decision: accepted | accepted-with-risks | rejected | blocked
signed_off_by: <release-owner-or-role>
signed_off_at: YYYY-MM-DD
tested_commit: <exact-clean-commit-sha>
authority_run_id: <exact-run-id>
launcher_jar_sha256: <sha256>
image_app_jar_sha256: <same-sha256>
blocking_items: []
follow_up_required: yes | no
evidence_count: <actual>
```

正文必须包含 Background、Acceptance Basis、Checklist、Evidence、Failed Items、
Risks/Open Items、Final Decision 和 Signoff Marker。

## Downstream Handoff

- 只有 9.3.4 signed-off 后，9.3.5 才能标 ready，消费稳定 runner、coverage 和
  required CI 门来实施 typed phase / QueryFacade / API 拆解。
- 9.4.0 继续 queued；本版本 build-only coverage reporter 不构成 production
  module split 或 SPI v2 提前实施。
- experience=N/A：本版本没有 UI、浏览器或人工体验交付，不要求 screenshot/
  Playwright evidence。
