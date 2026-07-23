---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-RUNTIME-IMAGE-INSPECT-MERGE-REGRESSION-REMEDIATION
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: release-owner-via-explicit-remediation-authorization
approved_at: 2026-07-23
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-MERGE-REGRESSION-FOCUSED-DIAGNOSIS
  - BUG-STEP5-PACKAGE-PROOF-CLEAN-MAVEN-ENV-SUCCESSOR
supersedes:
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-FORMAT-REMEDIATION
open_questions: []
---

# Delivery Spec: Step 5 runtime-image inspect merge-regression remediation

## Document Purpose

- intended_for: ultra implementation / v9.3.4 release-authority owner /
  independent signoff.
- purpose: 冻结 r13 已确认 merge regression 的最小源码修复、回归保护、完整性闭包和
  一次性 runtime proof 边界。
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-merge-regression-remediation.md`

## Goal

- version_goal: 恢复 package 路径对合法 linux/amd64 runtime image 的严格身份读取，
  同时保持所有 malformed identity、错误平台和异常记录 fail closed。
- target_outcome: 原子恢复 `d2565c0a` 的 portable single-record format/parser，
  修正 Docker-free fixture 对 Docker CLI terminal newline 的建模缺口；完成新源码的
  focused/static 验证、唯一 fresh Step4 authority chain 和唯一 clean-environment
  package component proof。成功只分类为
  `package-context-passed-non-authoritative`，并停在 canonical Step5 授权边界。

## Scope

- in_scope:
  - 在 `scripts/v934/step5/release_package_tool.py` 原子恢复 delimiter-separated
    single-record inspect format及其 matching parser；
  - 保持 exact three-field、`sha256:<64 lowercase hex>`、`linux/amd64` 校验、
    `E_IMAGE`/`E_BASE_IMAGE`、subphase、receipt和 cleanup语义不变；
  - 更新现有 Docker-free negative fixture，使 canonical output为一个终端换行的
    portable single record，并验证 exact command format；
  - 增加对历史 `println` 输出加 Docker CLI terminal newline后形成的额外空记录、
    多记录、delimiter/field-count、malformed ID和 wrong platform的 fail-closed回归；
  - 刷新该源码变化所需的精确 Step4/5/6 integrity/CI closure；
  - focused/static全绿后提交 clean/pushed source freeze；旧 r11 Step4只保留诊断背景，
    不作为新源码 authority；
  - 按当前 Step4 machine-state规则建立 reviewed diagnostic-ready input，随后在独立
    clean clone和私有 tmux中运行唯一 fresh Step4 authority chain；
  - 仅在 fresh Step4 terminal PASS 后，以同源 release root在第二个私有 tmux中运行
    唯一 clean-environment direct package proof；
  - 形成 bounded evidence，清理 owned output/tmux/Docker residue，回写本 work item。
- affected_modules:
  - `scripts/v934/step5/release_package_tool.py`；
  - `scripts/v934/step5/SHA256SUMS`；
  - `scripts/v934/step4/SHA256SUMS`及 fresh authority需要的既有 machine-state文档；
  - `scripts/v934/step6/ci_contract_tool.py`、
    `scripts/v934/step6/ci-contract.json`、`scripts/v934/step6/SHA256SUMS`；
  - `docs/9.3.4`治理、证据、质量和 scoped acceptance记录。
- external_dependencies: Git、Python、tmux、Maven、local Docker Engine、已缓存
  frozen base image和既有 required database/external test环境。

## Non-Goals

- out_of_scope:
  - 修改 production runtime、public API/SPI、Bean/config/event contract、POM、
    Dockerfile、frozen base image、package layout、receipt schema、pointer schema；
  - 降低 coverage floor、扩大 exclusion、删除/skip required tests、改变测试顺序、
    selector或 workflow graph；
  - 放宽 identity/platform校验、增加 fallback/retry、接受多记录或空字段；
  - 复用旧 Step4 artifact为新源码 authority，或拼接历史 runtime output；
  - 运行 canonical Step5 gate、portable replay、candidate/archive/pointer、
    Step6/7 authority、release/tag/publication；
  - 扩大到方案 B/C式工具重构或任何 9.3.5/9.4.0工作。
- do_not_touch:
  - 原工作区及用户 `docs/9.3.5/README.md`、`docs/9.3.5/workitems/`；
  - 历史 evidence、旧 r11 Step4 root、candidate/final pointers；
  - 非本运行创建的 Docker、tmux、Maven或数据库资源。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Restore format and parser atomically | r13证明 constant-only edit仍为 `E_IMAGE` | exact strict validation remains unchanged |
| Reuse the historical portable pair | `d2565c0a` already defines the one-record contract lost at merge | no new parser policy or public contract |
| Correct fixture fidelity | existing three-line mock omits Docker CLI's extra terminal newline | add fail-closed probe for the real `println` shape |
| Refresh only exact integrity closure | Step4/5/6 bind package-tool bytes | no unrelated runner/workflow/source drift |
| One fresh Step4, then one package proof | changed governed tool bytes need same-source authority and runtime confirmation | no retry; no canonical Step5 under this authorization |
| Use tmux as process owner | runner must survive Codex client/process interruption | private 0700 control dir; observer-only after launch |

## Acceptance Criteria

- [ ] AC-1: APPROVED plan is a clean pushed child of the r13 result; activation
  is its clean pushed single-parent child and changes only this file from
  `APPROVED` to `ULTRA_EXECUTING` plus readiness.
- [ ] AC-2: portable exact command and matching single-record parser are
  restored atomically; strict field count, ID shape and linux/amd64 policy
  remain unchanged.
- [ ] AC-3: Docker-free regression accepts the canonical portable record and
  rejects the real `println` double-terminal-LF shape, extra/multiple records,
  malformed delimiter/field count, malformed ID and wrong platform as
  `E_IMAGE`; receipt/privacy behavior remains unchanged.
- [ ] AC-4: Python syntax, package negative suite, Step4/5/6 manifests, Step4
  contract/static negatives and Step6 workflow/negative validators pass for
  the exact declared closure.
- [ ] AC-5: changed source is committed and pushed cleanly; protected original
  workspace and all out-of-scope files remain unchanged.
- [ ] AC-6: one reviewed diagnostic-ready source input leads to exactly one
  fresh tmux-owned Step4 authority chain on a clean non-shallow clone. Required
  lanes, Addon, coverage floor/critical set, source seal, cleanup and public
  artifact verification all pass with F/E/S=0.
- [ ] AC-7: only after AC-6, exactly one clean-environment direct package
  invocation runs from the same-source Step4 release root. Success requires
  exit 0, no failure receipt, internal `verified=true`, exact JAR/image/
  embedded-JAR identity, source/classes/reports invariance and cleanup.
- [ ] AC-8: success is recorded only as
  `package-context-passed-non-authoritative`; failure requires a valid fixed
  receipt and fail-closed category. Missing/invalid receipt, identity drift,
  cleanup failure or nonzero Step4 result is inconclusive and forbids retry.
- [ ] AC-9: owned package output, receipt, tmux/control and Docker residue are
  removed; source, Step4 input, pointers and original workspace remain
  unchanged; evidence contains no raw environment, image/container identity,
  endpoint, credential or temporary path.
- [ ] AC-10: result is committed/pushed and this item becomes
  `READY_FOR_SIGNOFF` only after AC-1..AC-9. No canonical Step5 execution
  occurs; its next rehearsal requires a separate owner-governed contract.

## Contract / Data / Security Constraints

- API or event contract: no production API/SPI/config/event/receipt/pointer
  change.
- data and migration: no business data or schema migration; only run-owned
  test/database/image state may be created and must be cleaned.
- compatibility and rollback: reverting the portable pair restores the known
  fail-closed regression; no compatibility expansion is introduced.
- permissions and secrets: tmux control directory mode 0700. Durable evidence
  may contain commit IDs, versions, bounded counts/status and booleans only.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-2/AC-3 | blocker | Python compile and package Docker-free negative suite | exact case count, F/E status and privacy result |
| AC-4 | critical | Step4/5/6 manifest, Step4 contract/static negative, Step6 workflow/negative | command/result summary and closure identities |
| AC-5 | critical | Git diff/topology/status and protected-workspace audit | changed-path allowlist and SHA |
| AC-6 | blocker | one fresh full Step4 authority chain | run ID, tested SHA, terminal states, counts, coverage and cleanup |
| AC-7/AC-8 | blocker | one direct receipted package proof | invocation count, category, receipt-valid or verified booleans |
| AC-9/AC-10 | blocker | cleanup/invariance/privacy and governance review | safe evidence path and stop-boundary statement |

验证成本与循环控制：

- `<5m`: Git、syntax、manifest、contract/static checks；输入不变时一次。
- `5-30m`: package negative suite和 final direct package proof；package只允许一次。
- `>60m`: fresh Step4 authority chain，预计 60-120分钟，最多一次。
- reusable evidence: r13 merge/runtime diagnosis和 r11 Step4结果只作根因/环境背景，
  不授权新源码。
- rerun trigger: source、tool、policy、threshold、selector、environment identity或
  test-selection变化使对应证据失效。
- maximum attempts: fresh Step4一次、package一次。任一失败先写 safe evidence并
  `NEEDS_REPLAN`，不得自动重试。

## Bug Context

- bug_source: acceptance-found merge regression.
- severity: blocker to canonical Step5.
- environment: PR #124 successor branch；Docker client/server 28.4.0；
  r12 clean Maven/JVM environment package path。
- current_behavior: current `println` template plus Docker CLI terminal newline
  yields four `splitlines()` records, so a valid image is rejected as
  `package-image-runtime-inspect / E_IMAGE`.
- expected_behavior: one portable delimiter-separated record is split into
  exactly three strictly validated fields.
- reproduction_steps: use the r13 same-image matrix or run package against a
  valid cached linux/amd64 image after fresh Step4 authority.
- reproduction_status: confirmed.
- existing_evidence:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-merge-regression-r13-confirmed-20260723.md`.
- existing_tests: package negative suite, Step4/5/6 integrity and CI contract
  validators.
- regression_protection: required.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks:
  - fixture仍可能偏离其他 Docker版本；exact portable one-record contract和 live
    package proof共同降低该风险；
  - Step4是高成本且只允许一次，任何环境或 authority不一致必须 fail closed；
  - package成功仍不证明 canonical Step5 archive/pointer路径。
- open_questions: none.

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、r13 evidence、历史 portable commit和现有
  Step4/5/6 closure。
- 先提交并 push APPROVED plan，再创建仅改变本文件 status/readiness的 activation
  commit并 push；activation前不得改源码。
- 优先建立会对当前实现失败的 deterministic regression，再原子修改 format/parser。
- focused/static全绿后冻结 source SHA；不得在 live Step4过程中编辑该 source。
- fresh Step4和 package分别由私有 tmux pane `exec`唯一持有；启动后 Codex只读观察。
- fresh Step4未 terminal PASS不得启动package；package后不得调用 canonical Step5。
- 如需改变目标、范围、兼容、安全、API/SPI或 declared closure，设置
  `NEEDS_REPLAN`并停止。
- 完成后填写 `Implementation Result`并设为 `READY_FOR_SIGNOFF`；不得自行
  `ACCEPTED`。

## Implementation Result

> 由执行会话填写。

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: pending

## References

- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- confirmed diagnosis:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-merge-regression-focused-diagnosis.md`
- r13 evidence:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-merge-regression-r13-confirmed-20260723.md`
- superseded remediation:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-format-remediation.md`
