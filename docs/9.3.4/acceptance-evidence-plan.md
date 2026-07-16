---
doc_role: acceptance-evidence-plan
doc_purpose: Define required evidence and gate order for 9.3.4 version acceptance.
version: 9.3.4
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-16
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
| reviewed code/test inventory | `code-inventory.md` + frozen inventory/predecessor migration/threshold manifests | Step 1 frozen / Step 3 runtime recorded / Step 4 r1 fail-closed + fix focused-green / r2 and Steps 5–7 pending |
| implementation plan | `implementation-plan.md` | ready |
| progress/check-ins | `progress/test-ci-evidence-chain-progress.md` | in-progress / Steps 1–3 passed / Step 4 r1 fail-closed / r2 pending |
| test plan/results | `test/test-ci-evidence-chain-test-plan.md` + exact raw reports | Steps 1–3 passed；version in-progress |
| Step 1–6 evidence | immutable per-step records under `docs/9.3.4/evidence/` + run roots | Steps 1–3 authority present；Step 4 r1 failed run recorded but exit evidence absent；Steps 4–6 pending |
| implementation quality | `quality/step2-runner-split-implementation-quality.md` + `quality/step3-required-matrix-implementation-quality.md` + `quality/step4-diagnostic-ready-implementation-quality.md` | Step 2 reviewed；Step 3 ready-for-coverage-audit；Step 4 historical ready-with-risks 已追加 r1 post-review，仅放行修复后的 clean-HEAD r2，can_enter_coverage_audit=no |
| coverage audit | `coverage/step2-runner-split-coverage-audit.md` + `coverage/step3-required-matrix-coverage-audit.md` | Step 2/3 feature evidence ready；critical/major gap=0 |
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

Current Step 4 readiness（2026-07-16）：状态仅为
`in-progress / diagnostic r1 fail-closed / r2 pending`。静态执行结构精确为
`23 exec / 48 sessions`，required
report overlay 为 `773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon
companion 仍单列 `2/6`。raw contract/effective POM/toolchain/report inventory 负例
分别为 `8/8`、`4/4`、`5/5`、`27/27`；Step 2 derived view/successor overlay=
`12/12`、`8/8`；toolchain receipt 绑定 Step 1
raw 工具版本、ASM `9.6/9.7/9.7.1` 三层 realm 和 24 个 production module
effective compiler。report amendment=`10 rows = 4 new + 6 changed`，SHA-256=
`5a1a07e2c47835fa244b90a06334341e13660a305d9eb7c74c64ee2f36a06504`，successor
declared amendments=`15`。本地 Step 4 `SHA256SUMS` 已生成并通过 exact 49 项校验，
manifest SHA-256=
`c735e8c1f7b74d72afe2d1d1872128d11a16acbd7373c750e59709624560106e`。

首次 clean all-lane attempt 已执行但不是 acceptance evidence：clean/pushed HEAD
`bc100b0f63bd3ff62d1105611dae41741790aedd` 的
`step4-coverage-20260716-diagnostic-r1` 在 `child-unit` 以
`3115 tests / 1 failure / 0 errors / 0 skipped` fail closed，未到 aggregate/report/
threshold。`PreAggregationDataValidationTest` 腐化 daily 但实际命中 monthly，并存在
raw-vs-raw/nullable-empty 伪绿；修复 focused=`9/F0E0S0`、组合=`57/F0E0S0`，source=
`affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`，见
`docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`。threshold 仍为
`diagnostic-pending`，尚无 aggregate baseline/review 或 Step 4 exit evidence。下一动作是
提交并推送修复，验证新的 clean HEAD 后执行 r2；Step 5 仍关闭。

Step 4 implementation quality 已记录于
`docs/9.3.4/quality/step4-diagnostic-ready-implementation-quality.md`，decision=
`ready-with-risks`，并已追加 r1 post-review。该决定现在只放行修复提交/推送后的
clean-HEAD r2，明确 `can_enter_coverage_audit=no`；待完整 all-lane observation 与
threshold review 完成后必须
重新判断。

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
   owning nested/variant reports fresh、exact。
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
