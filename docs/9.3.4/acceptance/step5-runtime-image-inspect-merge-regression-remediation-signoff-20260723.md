---
acceptance_scope: bug
version: 9.3.4
target: BUG-STEP5-RUNTIME-IMAGE-INSPECT-MERGE-REGRESSION-REMEDIATION
status: signed-off
decision: accepted
signed_off_by: independent-release-reviewer
signed_off_at: 2026-07-23
reviewed_by: independent-release-reviewer
blocking_items: []
follow_up_required: no
evidence_count: 13
---

# Step 5 runtime-image inspect merge-regression remediation signoff

## Document Purpose

- intended_for: release owner / project root session
- purpose: 对目标提交
  `919870138ae53c5f2445797814659a39b01890d4` 的 portable runtime-image
  inspect merge-regression remediation 形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-merge-regression-remediation.md`
- target_outcome: 原子恢复 portable single-record format/parser，保持严格
  identity/platform、receipt、cleanup 和 release authority 边界。
- signoff_scope: 目标源码与 Git 拓扑、Step4/5/6 integrity closure、r16-r19
  bounded evidence、低成本静态复核及批准后的 process-owner 边界；不重跑
  Step4、package proof 或 canonical Step5。
- contract_revision: `3b703233` 记录 `NEEDS_REPLAN`；
  `5a9d9fc8` 依据 release owner 明确授权批准 GitHub Actions process-owner
  澄清并恢复 `READY_FOR_SIGNOFF`。

## Acceptance Basis

- canonical implementation target:
  `919870138ae53c5f2445797814659a39b01890d4`。
- approved revised contract: 状态 `READY_FOR_SIGNOFF`，`open_questions: []`。
- confirmed diagnosis:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-merge-regression-r13-confirmed-20260723.md`。
- completed execution evidence:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-merge-regression-remediation-r19-passed-20260723.md`。
- r16 capsule manifest/archive、threshold review、primary review 和 independent
  review。
- exact implementation diff、single-parent topology、Step4/5/6 manifests、
  coverage-policy semantic comparison和 protected-workspace audit。
- live GitHub repository verification: Actions `enabled=false`，queued=0，
  in-progress=0；PR-triggered run `29996320563` 在 target SHA 上 completed /
  failure，仅作为外部 CI 披露，不提供本事项 authority。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | r13→plan→activation 为严格单父链，activation 仅改变治理状态 | `9c5de257`→`b5d192ae`→`c6cfb48e`，后续实现、reset、freeze、readiness 亦为单父链 | `git log --format='%H %P %s'` | pass |
| AC-2 | 原子恢复 portable format/parser并保持严格 ID、linux/amd64 | `{{.Id}}\|{{.Os}}\|{{.Architecture}}` 与 single-line split 同时恢复；`sha256:[0-9a-f]{64}`、linux、amd64 未放宽 | implementation diff、历史 `d2565c0a` pair | pass |
| AC-3 | canonical record通过，double LF、多记录、field count、ID、platform fail closed | Docker-free suite 独立实跑 120/120；新增三项与保留的 malformed/wrong-platform probes 均在源码中 | package negative result、source review | pass |
| AC-4 | syntax及Step4/5/6 closure通过 | 内存 compile通过；manifest 63/63、8/8、16/16；既有 static record为Step4 28、XML 130、overlay 20、Step6 negative 86 | static commands、Cdiag static review | pass |
| AC-5 | exact source clean/pushed且不混入原工作区 | remote target精确为 `91987013`；remediation range无 `docs/9.3.5`；原工作区仍仅有既有9.3.5修改 | Git remote/status/diff audit | pass |
| AC-6 | 唯一 remediation-owned Step4 chain通过 | r16 diagnostic、r17 formal、r18 release均exit 0；774+59/5709、Addon 2/6、F/E/S=0、23 exec/48 sessions、12 critical class均达标 | r16 capsule/reviews、r19 bounded evidence | pass |
| AC-7 | 唯一 remediation-owned clean-env package invocation通过 | count=1、exit=0、verified=true、无failure receipt；JAR/image/embedded-JAR及source/classes/reports seals一致 | r19 bounded evidence | pass |
| AC-8 | 仅分类为non-authoritative component proof | `package-context-passed-non-authoritative`；未授予candidate/pointer/release authority | r19 decision | pass |
| AC-9 | cleanup、privacy及工作区保持 | owned output/tmux/Docker residue清零；无敏感原始值；Step4 input、pointer和原工作区保持 | r19 cleanup/privacy fields、workspace audit | pass |
| AC-10 | remediation-owned/manual/tmux不调用canonical Step5；外部CI披露且不计数 | r19 process tree字段为false；外部run `29996320563` completed/failure并被排除；Actions已禁用且无活跃run | revised contract、GitHub API verification | pass |

## Implementation Quality

- scope and changed surface: 实现只修改 package tool、精确 Step4/5/6 hash/CI
  bindings和受治理的9.3.4证据；无 production runtime source。
- maintainability and duplication: 恢复历史已验证 pair，无新增 fallback、retry、
  parser分支或重复身份策略。
- error handling and edge cases: 多/空记录、字段数、ID和platform继续 fail closed；
  pinned base保持 `E_BASE_IMAGE`，其他image保持 `E_IMAGE`。
- contract and compatibility: public API/SPI、POM、Dockerfile、receipt/pointer
  schema、coverage floor/exclusion、selector、skip和test order未改变。
- evidence integrity: r16 archive SHA-256
  `8c189e1217fae18c5977cd46262d217d291b4a8feeae7e6ef0dec0ebba9911bc`
  与manifest一致；两个文件entry的digest/size均一致。coverage policy及12个
  critical-class reviewed thresholds与predecessor语义一致，仅刷新run-owned
  observation/review identity。

## Evidence Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence | Coverage |
|---|---|---|---|---|---|---|---|---|
| AC-1/AC-5 | critical | N/A | N/A | N/A | N/A | Git topology/diff/status | exact SHA and changed paths | covered |
| AC-2/AC-3 | blocker | Docker-free 120/120 | N/A | N/A | N/A | source/parser audit | implementation diff and independent negative run | covered |
| AC-4 | critical | compile/static | manifest closure | N/A | N/A | semantic threshold comparison | 63/8/16 exact manifests plus retained static record | covered |
| AC-6 | blocker | required lanes | Step4 diagnostic/formal/release | N/A | N/A | capsule and two reviews | r16 raw bounded capsule plus r19 terminal summary | covered |
| AC-7/AC-8 | blocker | package self-test | direct package proof | runtime image component | N/A | identity/receipt review | r19 package fields | covered |
| AC-9/AC-10 | blocker | N/A | N/A | N/A | N/A | cleanup/privacy/governance/API audit | r19, revised contract, GitHub state | covered |

## Independent Static Verification

- `release_package_tool.py` in-memory Python compile: pass。
- Docker-free negative command: 120 cases、exit 0、status passed。一次使用已存在
  output directory的preflight在运行cases前按设计以 `E_OUTPUT_EXISTS` fail
  closed；改用新的owned path后完成120/120，临时输出已删除。
- Step4/5/6 manifest independent digest verification:
  63/63、8/8、16/16，mismatch=0。
- coverage policy semantic comparison: parent policy、JaCoCo policy、model gate、
  critical floor、aggregate reviewed thresholds和12个critical reviewed entries
  不变；aggregate counts亦一致。
- forbidden surface diff: root POM、release Dockerfile、GitHub workflow及
  `src/main` production paths均无改动。

## Evidence Reuse Decision

- r16-r19 对应的实现字节、tested source、manifest、test selection、threshold
  policy和artifact identity没有因docs-only contract clarification改变，因此按
  approved minimum revalidation radius继续有效。
- r17/r18和r19采用契约批准的bounded retention，不要求重新暴露raw environment、
  Maven/Docker output或temporary paths；r19 terminal facts与r16 raw capsule、
  source seals、manifest closure和Git topology无冲突。
- 本次未运行Step4、package proof或canonical Step5。

## Failed Items

- none

## Risks / Follow-ups

- 本结论只接受 runtime-image inspect remediation 和
  `package-context-passed-non-authoritative` component proof。
- canonical Step5 archive/candidate/pointer、portable replay、Step6/7 authority、
  release、tag和publication仍需独立 owner-governed contract。
- GitHub Actions 当前为仓库级禁用；未来重新启用不追溯改变本次签收，但后续
  governed execution必须重新确认CI ownership边界。

## Final Decision

- decision: accepted
- rationale: 修订后 AC-1..AC-10 均有源码、Git拓扑、原始或批准的bounded
  evidence及独立静态复核支持；无失败项、未知关键证据、实现偏离或阻断风险。
- blocking_items: none
- follow_up_owner_and_due: canonical Step5由release owner另行批准；本事项无
  remediation follow-up。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent-release-reviewer
- signed_off_at: 2026-07-23
- acceptance_record:
  `docs/9.3.4/acceptance/step5-runtime-image-inspect-merge-regression-remediation-signoff-20260723.md`
- blocking_items: none
- follow_up_required: no
