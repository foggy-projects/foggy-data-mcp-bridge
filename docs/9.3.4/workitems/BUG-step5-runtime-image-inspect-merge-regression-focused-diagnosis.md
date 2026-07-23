---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-RUNTIME-IMAGE-INSPECT-MERGE-REGRESSION-FOCUSED-DIAGNOSIS
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
approved_by: project-owner-via-explicit-focused-diagnosis-authorization
approved_at: 2026-07-23
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-PACKAGE-PROOF-CLEAN-MAVEN-ENV-SUCCESSOR
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-FORMAT-REMEDIATION
open_questions: []
---

# Delivery Spec: Step 5 runtime-image inspect merge-regression focused diagnosis

## Document Purpose

- intended_for: ultra diagnosis / v9.3.4 release-authority owner /
  independent review.
- purpose: 在 r12 已排除 Maven/JVM control environment 并以有效
  `package-image-runtime-inspect / E_IMAGE` 回执 fail closed 后，定位当前 inspect
  实现、Docker CLI 输出语义和历史 merge resolution 之间的具体因果关系。
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-merge-regression-focused-diagnosis.md`

## Goal

- version_goal: 为 runtime-image inspect 的下一步最小修复提供可审计、可复现且不依赖
  另一次昂贵 package/Step4 运行的根因证据。
- target_outcome: 判定 `d2565c0a` 的 portable single-record implementation 是否在
  `f67ed053` 合并时被 `faf0efed` 分支的 `println` implementation 覆盖，以及 Docker CLI
  的终端换行是否使当前 strict parser 得到非三记录输出；形成唯一推荐修复边界，但不改代码。

## Scope

- in_scope:
  - 复核 `d2565c0a`、`faf0efed`、`f67ed053` 两个父提交、merge result和当前
    Cfreeze `5c57e537603004d47be936f74e92f873de5fe431` 的 inspect 实现及 ancestry；
  - 核对当前 Docker client/server 和 frozen base image 的可用性，仅记录版本和
    bounded status，不记录 daemon endpoint或 image identity；
  - 对同一个本地 cached image创建一个唯一 owned temporary tag，不 build、不 pull、
    不 create container；
  - 在独立 tmux runner 中对该 tag执行 exact current `println` format和 first-parent
    portable delimiter format，记录输出 record count、field-shape/platform classification，
    不持久化 raw image ID或 raw stdout；
  - 在内存中加载当前 package tool，先调用 current `docker_inspect_identity`，再只在该
    诊断进程内替换 format constant为 portable delimiter并调用同一函数，验证
    fail/pass差异；不得修改 tracked bytes；
  - 复核现有 Docker-free negative/self-test为何未建模 Docker CLI terminal newline，
    并判断其是否属于 regression coverage gap；
  - 删除 owned tag、tmux/control/result和其他诊断资源，复核 source、r11 Step4
    authority、candidate/final pointers和原工作区不变；
  - 形成 safe durable evidence和下一修复契约建议。
- affected_modules:
  - `docs/9.3.4` governance/evidence；
  - `scripts/v934/step5/release_package_tool.py`、相关 Git history和 retained r11
    artifacts仅作 read-only input；
  - host-local Docker image store中的一个临时 owned tag。
- external_dependencies: local Docker Engine、cached frozen base image、tmux、Git、
  Python。无 Maven、registry、GitHub、数据库、测试容器或网络 pull。

## Non-Goals

- out_of_scope:
  - 第二次 package invocation、任何 Maven goal、Step4 test lane、canonical Step5、
    candidate/archive/pointer、Step6/7、release/tag/publication；
  - 修改 production/test/tool/POM/Dockerfile/workflow/receipt schema、coverage threshold、
    API/SPI或 FSScript/Spring lifecycle semantics；
  - 为了得到成功结果放宽 three-field、ID、linux/amd64校验，添加 fallback/retry，或
    把 raw Docker output写入 Git evidence；
  - 预先实施 portable delimiter修复或更新 Step4/5/6 integrity closure。
- do_not_touch:
  - protected original workspace和 `docs/9.3.5` 用户改动；
  - retained r11 Step4 root、历史 evidence、candidate/final pointers；
  - 非本诊断创建的 image/container/network/volume/tmux/process。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Diagnose before another package attempt | r12 already reached the exact failure boundary with a valid receipt | no package or Step4 rerun in this contract |
| Compare both historical formats on one image | isolates formatting/parser behavior from image content and Maven output | same cached image, no pull/build/container |
| Use an owned temporary tag for the `E_IMAGE` path | exact pinned base reference maps failures to `E_BASE_IMAGE` | tag must be unique, counted and removed |
| Persist only bounded classifications | raw inspect output contains image identity | no raw stdout, tag token, digest or endpoint in Git |
| Treat merge resolution as a causal hypothesis until live proof | ancestry alone proves code replacement, not runtime behavior | root cause requires both history and controlled Docker evidence |

## Acceptance Criteria

- [x] AC-1: APPROVED plan is a clean pushed child of
  `3ebfa0d4abed159d0bb92d9e04a412097e2ac0a6`; activation is its clean pushed
  single-parent child and changes only this file to `ULTRA_EXECUTING`.
- [x] AC-2: governance root、execution clone、remote和 protected original
  workspace identities are exact; r11 Step4 remains public-valid and no
  package/canonical Step5 process or output exists.
- [x] AC-3: Git evidence establishes whether `d2565c0a` is in the first-parent
  ancestry, `faf0efed` is in the second-parent ancestry, and `f67ed053`
  selected which inspect implementation; later commits through Cfreeze are
  checked for additional inspect changes.
- [x] AC-4: one tmux-owned diagnostic uses one cached-image owned tag and no
  build/pull/container/Maven/package operation. Current and portable formats
  are compared on exactly the same image.
- [x] AC-5: durable results record only output line count, empty-record count,
  field-count/shape/platform booleans and current-function/portable-function
  pass/error classifications; raw image ID/stdout/tag token is absent.
- [x] AC-6: root-cause conclusion requires agreement between merge history,
  exact CLI behavior and current-function versus in-memory portable-function
  behavior. Any disagreement is `diagnosis-inconclusive`.
- [x] AC-7: the existing Docker-free regression fixture is evaluated against
  actual Docker newline behavior and any modeling gap is identified without
  editing tests.
- [x] AC-8: owned tag, tmux/control/result and temporary material are removed;
  no owned Docker residue/process remains; source, r11 Step4, pointers and
  protected workspace remain unchanged.
- [x] AC-9: result is committed and pushed as either
  `merge-regression-confirmed`, another bounded diagnosis category, or
  `diagnosis-inconclusive`; this contract stops before remediation.

## Contract / Data / Security Constraints

- API or event contract: no API/SPI/config/event/receipt/pointer change.
- data and migration: no business data or schema change; Docker tag is
  temporary diagnostic state only.
- compatibility and rollback: no product change. Removing the owned tag and
  control directory fully rolls back runtime diagnostic state.
- permissions and secrets: control directory mode 0700. Git evidence may
  contain commit IDs, version numbers, counts and booleans, but no raw Docker
  identity, stdout/stderr, endpoint, credential, temporary path or tag token.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-3 | blocker | Git topology/blame/diff and source-state checks | bounded ancestry and implementation selection facts |
| AC-4..AC-6 | blocker | same-image exact-format CLI and in-memory function matrix | counts/booleans/error categories only |
| AC-7 | critical | compare negative fixture with live newline semantics | regression-modeling conclusion |
| AC-8..AC-9 | blocker | exact Docker/tmux cleanup, authority invariance and privacy scan | cleanup/invariance checklist and durable result |

验证成本与循环控制：

- `<5m`: Git ancestry、tool source、Docker availability、r11 public-valid和privacy
  preflight；输入不变时一次。
- `<5m`: one live same-image diagnostic matrix；maximum attempts=1。
- `>30m`: none。禁止 Step4、Maven/package和 canonical Step5。
- reusable evidence: r11 Step4 terminal/public verification、r12 fixed receipt和clean-env
  result。
- invalidated evidence: tracked tool/source change、Docker image absence、current/portable
  matrix不是同一 image、raw result丢失或 owned cleanup失败。
- stop/replan: live matrix nonzero without safe bounded result、tag identity不确定、source/
  authority drift或历史/运行时证据不一致时立即停止。

## Bug Context

- bug_source: acceptance-found.
- severity: blocker to package proof and canonical Step5.
- environment: Docker 28.4.0 client/server on retained exact r11 Cfreeze.
- current_behavior: clean-environment package invocation reaches
  `package-image-runtime-inspect` and fails with valid `E_IMAGE`.
- expected_behavior: a linux/amd64 runtime image with canonical engine ID is
  accepted by strict identity inspection.
- reproduction_steps: compare the exact current and portable historical
  formats on one owned alias of the cached frozen image, then invoke the same
  current function with each format in memory.
- reproduction_status: r12 boundary confirmed; lower-level cause pending this
  diagnosis.
- existing_evidence:
  `docs/9.3.4/evidence/step-5/step5-package-proof-clean-maven-env-r12-fail-closed-20260723.md`
- existing_tests: package negative/self-test 117/117 passed before r12, including
  a mocked canonical inspect fixture.
- regression_protection: required in a later remediation if a modeling gap is
  confirmed.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks:
  - Docker CLI formatting can vary by version; evidence must bind the observed
    client/server version without generalizing beyond the strict supported
    contract;
  - an in-memory constant replacement is diagnostic only and must not be
    mistaken for a tested source patch;
  - a confirmed merge regression will require a new-source integrity closure
    and fresh authority chain before another package proof.
- open_questions: none.

## Ultra Execution Contract

- Push this APPROVED plan, then create and push the sole activation commit
  before the live diagnostic.
- Use an independent private tmux socket and one diagnostic process. After
  launch, Codex is observer-only; no attach/send-keys/retry.
- Do not invoke package, Maven, Step4 runner or canonical Step5. Do not modify
  tracked tool/test bytes.
- Persist only safe bounded classification data. Remove the owned tag and
  temporary result after classification.
- Fill `Implementation Result` and set `READY_FOR_SIGNOFF` only for a complete
  diagnosis; use `NEEDS_REPLAN` for inconclusive or scope-changing findings.
  Never self-set `ACCEPTED`.

## Implementation Result

> The governed focused matrix completed with
> `merge-regression-confirmed`.

- implementation_summary: proved that merge `f67ed053` selected the
  second-parent `println` package-tool blob and discarded the first-parent
  portable format/parser pair; reproduced the current four-record/E_IMAGE
  behavior and the historical one-record/three-field PASS on the same image.
- changed_paths:
  - `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-merge-regression-focused-diagnosis.md`
  - `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-merge-regression-r13-confirmed-20260723.md`
- tests_and_results: Git ancestry/blob selection confirmed; Step4/5/6 checksum
  manifests passed; r11 candidate/final public verification passed; one
  same-image tmux matrix passed with current line-count=4/empty-count=1 and
  `E_IMAGE`, historical portable line-count=1/field-count=3 and PASS; zero
  build/pull/container/package operations.
- manual_or_experience_evidence: existing Docker-free mock models one terminal
  line feed while real Docker current-format output contains two; owned tag,
  control material and ignored bytecode were removed; source, authority and
  protected workspace remained unchanged.
- deviations: the matrix retained a constant-only portable-format call as a
  negative control and separately evaluated the exact historical portable
  parser. This clarifies that format and parser must be restored atomically
  without expanding production scope.
- residual_risks: no source remediation has been implemented. Any patch changes
  release-tool bytes and therefore requires the exact Step4/5/6 integrity
  closure, a fresh new-source Step4 chain and a later one-shot package proof.
- readiness: READY_FOR_SIGNOFF — diagnosis is complete; remediation requires a
  separately approved work item and is not authorized here.

## References

- r12 fail-closed work item:
  `docs/9.3.4/workitems/BUG-step5-package-proof-clean-maven-env-successor.md`
- r12 evidence:
  `docs/9.3.4/evidence/step-5/step5-package-proof-clean-maven-env-r12-fail-closed-20260723.md`
- prior remediation:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-format-remediation.md`
- confirmed diagnosis evidence:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-merge-regression-r13-confirmed-20260723.md`
