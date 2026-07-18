---
doc_role: acceptance-evidence-plan
doc_purpose: Define required evidence and gate order for 9.3.4 version acceptance.
version: 9.3.4
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-19
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
| confirmed contract | `contract/test-lane-evidence-contract.md` | current machine=`diagnostic-pending/diagnostic-ready`；formal-r6 immutable failed，replacement formal pending |
| module responsibility | `module-responsibility.md` | ready |
| reviewed code/test inventory | `code-inventory.md` + frozen inventory/predecessor migration/threshold manifests | historical formal-r4/r22 retained；NUL-token recovery inventory reviewed；Step 5–7 execution closed |
| implementation plan | `implementation-plan.md` | ready |
| progress/check-ins | `progress/test-ci-evidence-chain-progress.md` | in-progress / Step 4 replacement Cdiag / Step 5–7 hold |
| test plan/results | `test/test-ci-evidence-chain-test-plan.md` + exact raw reports | r23 full PASS but exact freeze refused for scheduling high-water；replacement diagnostic-r24 and formal-r7 pending |
| Step 1–6 evidence | immutable per-step records under `docs/9.3.4/evidence/` + run roots | Steps 1–3 passed；historical Step 4 retained；replacement Step 4 pending；Steps 5–6 closed |
| implementation quality | `quality/step2-runner-split-implementation-quality.md` + `quality/step3-required-matrix-implementation-quality.md` + Step 4 quality records | r6 recovery quality=`0/0/0/0`；replacement post-formal quality pending |
| coverage audit | `coverage/step2-runner-split-coverage-audit.md` + `coverage/step3-required-matrix-coverage-audit.md` + `coverage/step4-coverage-gate-coverage-audit.md` | historical Step 4 audit retained；replacement audit closed until formal-r7 |
| Step 3 feature acceptance | `acceptance/step3-required-matrix-acceptance.md` | signed-off / accepted；not version signoff |
| Step 4 feature acceptance | `acceptance/step4-coverage-gate-acceptance.md` | historical signed-off retained；does not authorize current Step 5；replacement acceptance pending |
| version signoff | planned `acceptance/version-signoff.md` | not-started |
| roadmap/status sync | `README.md` + authoritative roadmap | Step 4 replacement in-progress；Step 5–7 closed；9.3.5 queued |

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

Historical Step 4 diagnostic-r18 readiness snapshot（2026-07-17）：
`in-progress / diagnostic-r18 PASS / threshold candidate not authorized / Pivot NULL-axis
remediation verified / pre-Cdiag quality PASS / replacement Cdiag pending`。clean/pushed Cdiag=
`5be1edaa16c5883cde2f66396ac26a1ae113430b` 的 r18 已完成 full Unit=
`681+55/4941/F0E0S0`、required=`773+59/5707/F0E0S0`、exec/session=`23/48`、public
validation=`VALID`；但 aggregate 比 r16 reviewed high-water 少 `2 line / 4 branch`，故 candidate
absent、decision=`threshold-candidate-not-authorized`，不得降阈。既有 S12 deterministic oracle
已经三次 fresh JVM/JaCoCo bitmap `3/3 identical`、完整 class=`23/F0E0S0`；successor/top=
`14/14 + 60/60`，database/required/overlay/coverage validators PASS。machine=
`diagnostic-ready/diagnostic-pending`；本轮 pre-Cdiag implementation quality=
`PASS / B/H/M/L=0/0/0/0`，record=
`quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`；当前只允许 replacement
Cdiag commit/push/clean 与 fresh r19。该状态不是 acceptance evidence；
`can_enter_coverage_audit=no`、`can_enter_acceptance=no`，candidate/Cfreeze/formal/post-formal gates、
Step 5 均保持关闭。

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
最终字节修正的正式复核为 `ready-with-risks`、B/H/M/L=`0/0/0/2`，两项 Low accepted。历史
Unit remediation superseding gate=`pass`；r8 lifecycle contract gate 的历史结论曾为
`pass / ready-for-commit-and-fresh-r9`。r9 随后在 coverage-report fail closed，且新的
lifecycle semantic bypass 审计重新打开 Step 4 quality。formal-r1 recovery 是历史边界，
已由 r14/Cfreeze/formal-r2 推进并由 formal-r2 recovery supersede；
`can_enter_coverage_audit=no`。新的 Cdiag、fresh diagnostic、review/Cfreeze、fresh formal 与
最终 implementation quality 完成前不得启动 coverage audit。

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

## Historical Step 4 acceptance boundary（r9 snapshot）

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

## Historical Step 4 acceptance boundary（r12 snapshot）

- r12=`step4-coverage-20260717-diagnostic-r12` 是 clean/pushed commit
  `05351ecab0d7fc43d12dfa307ffecf81feb41539` 上完整、可复验的 diagnostic；all-lane、
  aggregate、sensitive scan 与 cleanup 通过，但 9/12 critical classes 低于冻结 floor，
  唯一 `NamespaceScope.branch` 为 machine-authorized structural N/A；
- r12 observation 是合法缺口证据，不是 reviewed threshold、formal gate 或 Step 4 exit。
  旧 freeze consumer 暴露的 enriched-row、N/A applicability 与 retained raw-exec replay
  闭包缺口已修复，九类测试 focused=`136/F0E0S0`，静态/负向门为 XML=`118/118`、
  contract=`27/27`、frozen replay=`12/12`、overlay=`12/12`；
- 该时点仍为 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。本轮正式
  implementation quality 已通过；仍必须依次完成 Cdiag commit/push/clean HEAD、fresh r13、direct-single-parent
  Cfreeze、fresh formal 与最终 implementation quality；只有上述链路通过后才按 Gate Order
  启动 test coverage audit，audit PASS 后才允许 acceptance。

## Historical Step 4 acceptance boundary（r13 / Cfreeze snapshot）

- r13=`step4-coverage-20260717-diagnostic-r13` 已在 clean/pushed commit
  `b76552e21479c75111f648a4aa678abe018cc3f9` sealed PASS：required=
  `773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48`、critical below-floor=`0`、
  structural N/A=`1`、sensitive scan=`passed`、cleanup=`0/0/0`；
- 唯一 structural N/A 为 exact `NamespaceScope.branch`。threshold candidate=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r13-threshold-candidate-20260717.json`，
  SHA-256=`8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`，public
  verification 已通过；
- Cfreeze 已以 `86d505810524383da6211bcc2a7965e9a4afb34e` commit/push；唯一 direct parent=
  `b76552e21479c75111f648a4aa678abe018cc3f9`，topology/formal-delta proof 通过。该转换本身
  不是 formal gate、最终 implementation quality、coverage audit、acceptance 或 Step 4 exit；
- 该时点 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，随后 fresh formal-r1 已执行
  并 fail closed；本 snapshot 已由下一节 supersede。Step 5 始终关闭。

## Superseding formal-r1 acceptance boundary（2026-07-17）

- r13/Cfreeze 仅是历史 diagnostic/freeze evidence；formal-r1 在同 source/class tree 上因
  shutdown-hook incidental coverage 下降被 `E_FORMAL_LOW` 正确拒绝，Step 4 未通过；
- formal-r1 全 lane evidence 与 absence semantics 保留，但不得计为 acceptance positive；
- deterministic regression 已以 5/5 identical probe bitmap 和 11/F0E0S0 focused result证明，
  仍不足以进入 coverage audit；
- 该时点 canonical machine state 已回到 `diagnostic-ready/diagnostic-pending`。必须取得新 Cdiag、fresh
  diagnostic、独立 threshold review、direct-child Cfreeze、fresh formal 和最终 implementation
  quality 的完整证据后，才允许运行 `foggy-test-coverage-audit`；
- 该时点 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，后续边界见 r14 与 formal-r2
  superseding sections；Step 5 始终保持关闭。

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

## Historical r14 acceptance boundary（2026-07-17）

- r14 diagnostic is a valid successful observation on tested commit
  `322bb346cca19998a90d6d990505ef033f3a496a`：required=`773+59/5707/F0E0S0`、Addon=`2/6`、
  exec/session=`23/48`、below-floor=`0`、N/A=`1`、cleanup=`0/0/0`、sensitive=`passed`；
- candidate SHA-256=`9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`
  and independent review authorize Cfreeze only；they are not formal or acceptance positives；
- canonical working-tree state=`confirmed/formal-ready`，but direct-child Cfreeze commit/push、
  topology/formal-delta proof、clean identity and fresh formal are still required；
- the only review Low is non-critical PostgreSQL Pivot probe variance。Acceptance may close it only by
  a fresh formal PASS under the exact r14 minima；a failed run must remain immutable；
- current `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。After formal PASS，final
  implementation quality must pass before the coverage audit skill is used；acceptance remains after
  audit PASS only。

## Superseding formal-r2 acceptance boundary（2026-07-17）

- direct-child Cfreeze=`1901a10138bac06a09b875c907b7aea6e2789b04` 已 commit/push，parent=
  `322bb346cca19998a90d6d990505ef033f3a496a`；r14 machine threshold/contract 曾为
  `confirmed/formal-ready`；
- immutable formal-r2=`step4-coverage-20260717-formal-r2` 完成 required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`、cleanup=`0/0/0`、
  sensitive=`passed`，随后在 `formal-coverage-gate` 以 aggregate branch `-1` fail closed；
- 12 个 critical class 与 aggregate line 全部 exact；唯一 delta 为
  `FileSystemListPresetStore` branch `18/26 -> 17/26`，根因是 `Files.find(...).findFirst()`
  在 UUID 文件上的未定义遍历顺序；不是漏跑、exec/report 缺失或生产回归；
- deterministic regression 不改生产、不增 testcase；focused 5/5 命中缺失 probe 106，
  Data Viewer=`104/F0E0S0`；machine state 已恢复 `diagnostic-ready/diagnostic-pending`；
- 该 historical boundary 已完成 Cdiag `9270d2d4…` 并进入 r15；r15 fail-closed 后由下节
  supersede。`can_enter_coverage_audit=no`、`can_enter_acceptance=no`；Step 5、coverage audit、
  acceptance 与 9.3.5 保持关闭。

## Superseding r15 acceptance boundary（2026-07-17）

- formal-r2 recovery Cdiag=`9270d2d4e58684226aeb15eff55b027e6aa4a7eb` 已 commit/push/clean；
  r15=`step4-coverage-20260717-diagnostic-r15` 在 Unit child 以 exit 1 fail closed；
- failure authority 仅为 immutable `run.log`/`run-status.env`；partial XML=`26 reports /
  124/F1E0S0`、partial exec=`2/48 sessions`，最终 sensitive scan 与 success-only artifacts
  未产生，禁止与任何后续 run 拼接；
- root cause 是已预热 static cache 上的单次 `nanoTime` 倍率 oracle，不是产品缓存回归；
  deterministic remediation 保持原 testcase cardinality，以同类不同 source 实例复制验证 cache metadata
  不保存实例数据，同时移除相邻批量测试的墙钟阈值；
- focused=`10/10` fresh JVM、class=`23/F0E0S0`、module=`27/F0E0S0`；formal pre-Cdiag
  quality=`PASS / 0/0/0/0`，但不替代完整 Unit replacement、diagnostic、formal 或 Step 4 exit；
- 当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；只允许按
  new Cdiag commit/push/clean -> fresh diagnostic -> review/Cfreeze -> fresh formal -> final quality
  推进，Step 5 与 9.3.5 保持关闭。

## Superseding r16 reviewed-Cfreeze acceptance boundary（2026-07-17）

- superseding Cdiag=`f863c672029d5d1e5a4903df74cf6cba22a04a85` 已 commit/push；fresh r16=
  `step4-coverage-20260717-diagnostic-r16` 完成 required=`773+59/5707/F0E0S0`、
  exec/session/identity=`23/48/16948`，aggregate=`54624/76830 line`、`26111/44870 branch`；
- 12 个 critical class 全部达到 observed minimum，唯一 structural N/A=
  `NamespaceScope.branch=0/0`；candidate SHA-256=
  `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919` 已两路独立复算，
  review SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
  B/H/M/L=`0/0/0/1`；
- canonical threshold=`confirmed` / SHA-256=
  `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`，contract=
  `formal-ready` / SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`；
- pre-Cfreeze implementation quality=`PASS / 0/0/0/1`，只授权一次 direct-child Cfreeze 与
  fresh formal，不授权 final quality、coverage audit 或 acceptance；
- Cfreeze commit/push、direct-parent/formal-delta/clean identity 与 fresh formal 仍 pending；唯一
  Low 要求 formal 完整复现 r16 aggregate 高水位，禁止通过下调 threshold 放行；
- 当前 Step 4、coverage audit、acceptance 均未签收，`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`；Step 5 与 9.3.5 保持关闭。

## Superseding formal-r3 fail-closed acceptance boundary（2026-07-17）

- direct-child Cfreeze=`a63c82c53ebaad1a1c22d78647fbda70b4bd6594`，parent=
  `f863c672029d5d1e5a4903df74cf6cba22a04a85`，已 commit/push/clean；
- immutable formal-r3=`step4-coverage-20260717-formal-r3` 完成 required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`、cleanup=`0/0/0`、
  sensitive=`passed`，随后在 `formal-coverage-gate` 因 aggregate branch
  `26110/44870 < 26111/44870` fail closed；aggregate line exact `54624/76830`，
  success-only summary/gate/candidate/final 保持 absent；
- r16 与 formal-r3 的唯一 class/method/source-line 差异为
  `QueryModelSupport#getMergedJoinGraph` line 316 inner double-check；这是旧测试依赖
  两个 caller 偶然并发的 coverage race，不是 product regression、report/exec 丢失
  或 critical-class 退化；
- 既有
  `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`
  已加入受控 QueryModel 并发窗口，确认第二 caller 在 exact support monitor 上
  `BLOCKED`后释放首次 build，并断言 single build/same graph；不新增或改名
  `@Test`，targeted、protected overlay 与 5/5 fresh Maven/JVM 已 PASS，QueryModelSupport
  class id=`d242dafe9de31249`、probes=`34/629`、packed bitmap unique=`1`、Surefire=
  `1/F0E0S0`；`foggy-runtime-api` full module=`128/F0E0S0`，
  `RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`；pre-Cdiag quality=
  `PASS / 0/0/0/0`，machine/contract/overlay/negative suites 通过；record=
  `quality/step4-formal-r3-recovery-implementation-quality.md`；all-lane diagnostic 尚未完成；
- machine 当时已恢复 contract=`diagnostic-ready` / threshold=`diagnostic-pending`；该轮
  manifest identity 只属于 formal-r3 recovery 的历史 pre-Cdiag 快照，不是 current identity；
- only allowed sequence 为 new Cdiag commit/push/clean ->
  fresh diagnostic -> candidate/review -> direct-child Cfreeze -> fresh formal -> final quality ->
  coverage audit -> acceptance。当前 Step 4=`in-progress`，`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`；Step 5 与 9.3.5 保持关闭。

## Superseding diagnostic-r17 fail-closed acceptance boundary（2026-07-17）

- Cdiag=`316a71f753827f8f34063b0eb0669271f696c5ee` 已 commit/push/clean，并被 fresh
  `step4-coverage-20260717-diagnostic-r17` 消耗；outer=`child-unit / exit 1`，Unit=
  `unit-mysql57-lifecycle-negative / exit 1`。r17 immutable、`excluded/non-reusable`；success-only
  lifecycle receipt、fixture/Unit XML、exec、source-after、aggregate、observation、summary、candidate
  与 final artifact 均 absent；
- root-cause RED 捕获 stock ping 在 PID1 仍为 `docker-entrypoi` 时 premature healthy。Step 4
  authority override 已让 MySQL57/8 同时要求 PID1=`mysqld` 与原 ping；两库 runtime GREEN 均
  证明首次 healthy 已是 final server；冻结 Step 3 provisioner 不变；
- remediation focused evidence 为旧字节三轮真实 lifecycle=`15/15`；penultimate 加固字节的
  `5/5` receipt=
  `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`。最新加固字节 r2=
  `step4-unit-lifecycle-handoff-current-20260717-r2` 已 `5/5` PASS，receipt SHA-256=
  `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`；所有成功 probe 的
  `provisioner.log` absent，run-owned residue=`0/0/0`，demo exact restore=
  `runner_rc=0/restore_rc=0` 且 healthy/listening。静态 overlay=`12/12`、Unit fixture negatives=`36/36`、coverage negatives=
  `27 + source/Git 22 + replay 12` PASS；
- 上述 focused/static evidence 不等于 full Unit 或 all-lane authority。完整 Unit 必须在新的
  clean/pushed replacement Cdiag 上由 fresh diagnostic 产生；当前 machine=
  `diagnostic-ready/diagnostic-pending`。pre-Cdiag formal implementation quality 已 PASS，
  B/H/M/L=`0/0/0/0`，record=
  `quality/step4-diagnostic-r17-recovery-implementation-quality.md`；当前只授权 replacement Cdiag
  commit/push/clean 与 fresh diagnostic，review/direct-child Cfreeze/formal 与 final quality 均 pending；
- 当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；test coverage audit、version
  acceptance、Step 5 与 9.3.5 均保持关闭。

## Superseding diagnostic-r18 governed-high-water boundary（2026-07-17）

- replacement Cdiag=`5be1edaa16c5883cde2f66396ac26a1ae113430b` 已 commit/push/clean；fresh
  `step4-coverage-20260717-diagnostic-r18` 完成 `diagnostic-observed / completed / exit 0`，
  public validator 返回 observation=
  `7a02bfaaec7d6d1afeac4a5cff20c708fb8ff1092185c25a31ac588b6845dd76`；required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/class identity=`23/48/16940`、
  cleanup=`0/0/0`、sensitive scan=`passed`；
- r18 aggregate=`54622/76830 line`、`26107/44870 branch`，低于 r16 已独立审阅的高水位
  `54624/76830 line`、`26111/44870 branch`，精确差值为 `-2 line / -4 branch`。因此
  decision=`threshold-candidate-not-authorized`，candidate 保持 absent；禁止通过降低 threshold
  将 r18 转成 Cfreeze 前置证据；
- 精确 XML/exec 差异定位为 `BaselineRatioCalculator=-2 line/-3 branch` 与
  `ResultShaper=-1 branch` 的 PostgreSQL execution bitmap 偶发性。对应
  `BUG-step4-pivot-null-axis-coverage-oracle.md` 已在既有
  `PivotSqlParityIT#testBaselineRatioParity` 内补充 null-column baseline exclusion 与 null-row
  tree fallback 的确定性业务语义 oracle；未新增/改名 `@Test`、未修改 production，三次 fresh
  JVM/JaCoCo focused 均 `1/F0E0S0` 且两目标 bitmap `3/3 identical`，完整 test class=
  `23/F0E0S0`，test node 仍为 `23`，successor/top authority 哈希链已重封并通过验证；
- r18 是有效、不可变的 diagnostic observation，但不是 threshold candidate、formal gate、最终质量、
  coverage audit 或 acceptance positive。pre-Cdiag implementation quality 已
  `PASS / 0/0/0/0`；当前 machine=`diagnostic-ready/diagnostic-pending`，只允许提交/push
  replacement Cdiag 并运行 fresh r19；r19 必须至少复现
  r16 高水位才可进入 candidate/review；
- 当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。Cfreeze、fresh formal、final quality、
  `foggy-test-coverage-audit`、`foggy-acceptance-signoff`、Step 5 与 9.3.5 全部保持关闭。

## Superseding diagnostic-r19 pre-Cfreeze acceptance boundary（2026-07-17）

- r19 diagnostic material 已齐：Cdiag=`613b11a0…`、required=`773+59/5707/F0E0S0`、Addon=
  `2/6`、`23/48/16931`、class universe=`24/2098`、source exact、cleanup=`0/0/0`、
  model/sensitive PASS；aggregate=`54624/76830 line, 26111/44870 branch`；
- immutable candidate `6588e30b…f545b8` 经 public verification 与两路 independent review PASS；
  canonical machine working tree=`confirmed/formal-ready`，pre-Cfreeze quality=`PASS/0/0/0/0`；
- acceptance_status 继续为 `not-started`。当前 material 只授权 direct-child Cfreeze 和 fresh formal，
  不得提前创建 Step 4 exit、final quality、coverage audit 或 acceptance 结论；
- `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。必须等待 fresh formal PASS，再按
  final implementation quality -> coverage audit -> feature acceptance 顺序开放；Step 5/9.3.5 closed。

## Superseding formal-r4 / final-quality acceptance boundary（2026-07-18）

- Cfreeze `f97483a0…` 已证明 direct-single-parent、push/clean identity；fresh formal-r4=
  `formal-passed / completed / exit 0`，public final artifact=`VALID`；
- required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/class identity=`23/48/16953`；
  aggregate=`54624/76830 line, 26111/44870 branch`，critical=`12/23/below0`，source/model/
  sensitive/cleanup 与 exact external restore 均满足边界；
- post-formal implementation quality=`ready-for-coverage-audit`，B/H/M/L=`0/0/0/1`、mandatory
  fixes=`0`；唯一 Low 由 Step 5 负责拆分 live inventory 与 durable artifact replay；
- `acceptance_status` 继续表示 9.3.4 version acceptance，保持 `not-started`。At that formal-r4
  checkpoint only Step 4
  test evidence coverage audit：`can_enter_coverage_audit=yes`、`can_enter_acceptance=no`；
  Step 4 仍 `in-progress`，Step 5/9.3.5 closed。

## Superseding Step 4 feature acceptance boundary（2026-07-18）

- coverage audit=`ready-for-acceptance`：25/25 Step 4 workitem covered，critical/major gap=`0/0`；
  审计发现的 Pivot legacy 分支已由同一 tested HEAD 的 current-source companion
  `1/F0E0S0` 补齐并独立复核；
- feature acceptance record=
  `docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md`，decision=`accepted`、
  blocking items=`none`；25 个 Step 4 workitem 已在 decision 后统一关闭；
- `acceptance_status` 仍表示 9.3.4 version acceptance，保持 `not-started`；本次只把
  Step 4 标为 `passed`，把 Step 5 标为 `ready / not-started`；
- live/durable replay Low、portable raw archive 与 Unit MySQL classification debt 分别由
  Step 5/9.3.5 承接；Steps 6/7、9.3.4 version signoff 与 9.3.5 开工均未提前。

## Superseding replacement evidence boundary（2026-07-19）

历史 feature acceptance 不改写，但 post-acceptance authority delta 必须以 replacement formal 重新
证明。formal-r6 在 bootstrap negative fixture fail closed，成功证据边界止于 delta/frozen replay/
contract；source、tests、coverage 和 final artifacts 均 absent。修复位于 prior formal allowlist 外，
所以 current `can_enter_step5=no / can_enter_coverage_audit=no / can_enter_acceptance=no`。

新的 acceptance 输入只能来自 controlled MapBeanInfo regression 之后 clean/pushed replacement Cdiag
的 fresh diagnostic-r24、全新 review/Cfreeze 和 fresh formal-r7；r22/r23/r6 不得拼接。r23 虽
public-valid，但 candidate/capsule 因 scheduling high-water 保持 absent。r7 PASS 后仍按 implementation
quality→coverage audit→acceptance 顺序恢复 downstream entry。

## Superseding diagnostic-r24 pre-Cfreeze acceptance boundary（2026-07-19）

- r24 在 clean/pushed Cdiag `414c8b12…` 上完成全部 required lanes；public validation、source seal、
  model/sensitive、cleanup 与 exact DB restore PASS；aggregate=`54624/76830 line + 26112/44870 branch`；
- deterministic MapBeanInfo target=`4/4 branch / 10/11 _wU`；candidate=`f13f3c35…2ee`，capsule=
  `6638 entries / 0 symlink`；dual independent review=`APPROVE / 0/0/0/0`；
- canonical machine=`confirmed/formal-ready`，frozen r24 replay PASS，pre-Cfreeze quality=
  `PASS / 0/0/0/0`。这些材料只授权 direct-child Cfreeze 和 fresh formal-r7；
- acceptance 仍未重开：`can_enter_step5=no / can_enter_coverage_audit=no / can_enter_acceptance=no`。
  formal-r7 PASS 后仍必须按 final quality→coverage audit→acceptance 顺序恢复。
