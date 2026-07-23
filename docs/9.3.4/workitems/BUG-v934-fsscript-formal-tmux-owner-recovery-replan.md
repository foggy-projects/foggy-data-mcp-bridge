---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-V934-FSSCRIPT-FORMAL-TMUX-OWNER-RECOVERY-REPLAN
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: project-owner-via-explicit-tmux-recovery-authorization
approved_at: 2026-07-23
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-V934-FSSCRIPT-FORMAL-EXTERNAL-INTERRUPTION-RECOVERY-REPLAN
open_questions: []
---

# Delivery Spec: FSScript formal tmux-owner recovery replan

## Document Purpose

- intended_for: ultra-implementation / v9.3.4 release-authority owner / independent signoff.
- purpose: 在 r8、r9 均因承载 Codex/terminal session 外部中断而缺失 formal terminal authority
  后，以独立 `tmux` server 作为唯一长期 terminal owner，授权一次新的完整 Step4 formal
  execution；Codex 启动后只作为只读观察者。
- canonical_path:
  `docs/9.3.4/workitems/BUG-v934-fsscript-formal-tmux-owner-recovery-replan.md`

## Goal

- version_goal: 关闭 FSScript 生命周期补丁的 Step4 formal authority 缺口，同时保持 immutable
  Cfreeze、reviewed threshold、runner、产品与测试字节不变。
- target_outcome: 新 activation clean/pushed 后，从 fresh non-shallow clone detached 到 exact
  Cfreeze，通过独立 tmux socket/session 启动唯一 r10 formal invocation；Codex 进程退出不再向
  runner 传播 HUP/TERM。只有 pane exit `0` 且 runner 全部 canonical terminal receipts PASS，才
  允许形成 durable evidence 并继续 Step5-7。

## Scope

- in_scope:
  - 本 Cplan 作为 r9 fail-closed record 后唯一 successor authority；
  - 创建只改变本文件 `status/readiness` 的 clean/pushed activation commit；
  - 复核 immutable Cfreeze `5c57e537603004d47be936f74e92f873de5fe431` 的 Git topology、
    source/contract/manifest/threshold seals 和 formal-delta；
  - 使用全新 disposable clone、run root、run ID、0700 control directory、独立 tmux socket 与
    单一 session/window 启动完整 formal；
  - 使用 `remain-on-exit` 保存 pane 终态，runner 以 `exec` 成为 pane 主进程，确保
    `pane_dead_status` 是 runner 实际退出码；
  - 启动后 Codex 仅通过 `tmux list-panes`、`capture-pane`、只读日志/receipt 检查观察，不向
    pane 注入命令或改变运行状态；
  - formal terminal PASS 后记录 tmux identity、activation/Cfreeze/run identity、全部终态与清理
    evidence，再按 controlling item 顺序推进 Step5、Step6、Step7。
- affected_modules:
  - `docs/9.3.4` governance/evidence；
  - 既有 Step4/5/6 runners 仅作不可修改执行输入；
  - host-local `/tmp` disposable clone/control socket/run output。
- external_dependencies: tmux 3.4、GitHub PR #124、Maven、Docker/Compose、既有数据库 authority
  环境；不发布制品、不部署业务环境。

## Non-Goals

- out_of_scope:
  - 修改产品、测试、POM、runner、manifest、threshold/exclusion、selector、fork、skip、测试
    顺序、API/SPI 或 Spring/FSScript 生产语义；
  - 修改 runner 使其 daemonize，使用 `nohup`、`disown`、systemd、cron 或未审计 wrapper；
  - attach 到 tmux、`send-keys`、`pipe-pane`、重启 pane、局部续跑、运行中修补文件或人工改变
    runner exit status；
  - 复用或拼接 r8/r9 runtime bytes；
  - 把 tmux pane exit `0` 单独当成 formal PASS；
  - 扩大到方案 B/C、v9.3.5 或 v9.4.0。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 和 `docs/9.3.5/workitems/`；
  - immutable Cfreeze tracked bytes；
  - r8/r9 roots、非本 run-owned Docker/数据库资源和用户已有 tmux server/session。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| tmux is the terminal owner; Codex is observer only | r8/r9 的缺口来自承载 agent/PTY 被外部终止，不是 completed lane 产品失败 | 独立 socket，真实 PTY，Codex disconnect 不传播信号 |
| runner is pane main process through shell `exec` | 避免 wrapper 吞掉或改写 runner exit status | `pane_dead_status` 必须等于 runner exit；仍需 canonical receipts |
| preserve pane with `remain-on-exit` | 允许新 Codex 会话在 runner 结束后读取准确终态 | 不使用 respawn/restart；dead pane 只读保留至 evidence 完成 |
| reuse immutable Cfreeze input, never reuse r8/r9 evidence | source/tool/policy/threshold/selector 未改变，formal gate要求 exact direct-child Cfreeze | fresh clone/root/run ID；旧 runtime bytes永久 excluded |
| no new diagnostic | 两次失败均为外部 terminal-owner interruption，completed child均 F0/E0/S0 | 若任一 frozen digest或环境假设变化，本决定失效 |
| authorize exactly one r10 attempt | 已完成显式 execution-strategy replan，不能继续无限昂贵重跑 | r10 非 terminal PASS 时立即 NEEDS_REPLAN，不自动 r11 |
| Step5-7 require outer terminal PASS | child/local/tmux status不能替代完整 authority | 所有 source/coverage/model/sensitive/cleanup/candidate/capsule receipts 必须闭合 |

## Acceptance Criteria

- [ ] AC-1: 本 APPROVED Cplan 是 r9 failure-record commit
  `f217f456516f4ba73cf6dd7a90ba4a0c8cad9331` 的 clean/pushed 单父直接子；原工作区仍为
  `9743f97d9d935d5e26311b78c158755bca51f17a`，用户 v9.3.5 改动不变。
- [ ] AC-2: tmux capability probe 使用独立 socket，证明 detached pane 具有真实 PTY、pane
  process 是自身 PGID/SID leader、`remain-on-exit` 能保留 exact nonzero dead status；probe server
  已清理，未接触用户 tmux server。
- [ ] AC-3: activation 是 Cplan 的 clean/pushed 单父直接子，diff 仅为本文件
  `APPROVED -> ULTRA_EXECUTING` 与 readiness activation identity。
- [ ] AC-4: fresh clone clean/non-shallow、无 replace/graft、remote含 activation、detached exact
  Cfreeze；formal delta仍为 Cdiag `462d64ac...` 的 direct-single-parent child与六个 exact paths；
  source/contract/manifest/threshold digests 与 frozen record一致。
- [ ] AC-5: r10 启动前 run root、control socket/session与 run-owned runtime residue均不存在；唯一
  tmux pane通过 `exec` 启动唯一 formal invocation，并记录 socket/session/window、pane PID/SID/
  PGID/TTY、start command与 run ID。
- [ ] AC-6: 启动后所有观察均为只读；没有 attach、send-keys、pipe-pane、respawn、局部续跑或
  第二 runner。tmux pane dead status与 runner终态一致。
- [ ] AC-7: formal完成所有 required lanes，至少保持 `774 positive + 59 structural / 5709
  testcases`、Addon `2/6`、F/E/S=0；outer source-after、summary/run-status、coverage/critical、
  model、sensitive、cleanup、candidate、capsule全部 PASS，且无 process/container/database/port
  residue。
- [ ] AC-8: durable evidence绑定 Cplan/activation/Cfreeze/run/tmux identities与 receipt digests，
  明确 r8/r9未复用；本 work item达到 `READY_FOR_SIGNOFF` 后，才按 controlling item顺序推进
  Step5、Step6、Step7，任一失败立即停止。

## Contract / Data / Security Constraints

- API or event contract: 不修改 production API/SPI、Bean、配置、事件、数据库或 evidence schema。
- data and migration: 无业务 schema/data migration；只允许 r10 run-owned 临时 fixture/container。
- compatibility and rollback: tmux 只改变 host execution ownership，不改变 runner或产品语义；
  失败记录追加保留，不回滚/抹除 historical evidence。
- permissions and secrets: control directory mode `0700`；socket、session metadata和 sanitized digest
  可记入 evidence，不保存凭证、raw environment、数据库 secret或用户 tmux identity。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-3 | blocker | Git parent/path/status/push、tmux isolated capability probe | Cplan/Cactivation exact SHA and probe facts |
| AC-4..AC-5 | blocker | fresh clone seals、formal delta、no-clobber/residue、tmux owner identity | preflight receipt and sanitized tmux metadata |
| AC-6 | blocker | observer command audit and pane status checks | observation log containing only allowed read operations |
| AC-7 | blocker | one complete fresh Step4 formal all-lane execution | canonical terminal receipts, totals, coverage and cleanup |
| AC-8 | critical | provenance/no-splice review and controlling-item gates | durable formal evidence and subsequent stage records |

验证成本与循环控制：

- `<5m`: Git/digest/formal-delta/residue/tmux probe与 owner identity；输入不变时一次。
- `5-30m`: post-run receipt/final-artifact replay与 focused static verification。
- `>60m`: r10 formal及后续 Step5/6 governed runs；每个 stage/run ID最多一次。
- expected duration: r10 formal预计 60-120 分钟；tmux session不依赖 Codex turn存活。
- reusable evidence: immutable Cfreeze/formalization bytes、diagnostic r7 frozen provenance及已签收
  FSScript focused tests；不能替代 r10 formal lane。
- invalidated evidence: r8/r9全部 runtime output永久 excluded；tmux probe只验证 owner mechanism，
  不验证产品或 formal结果。
- rerun trigger: 任一 source/tool/policy/threshold/selector/environment digest变化时本授权失效；
  不得在本 Cplan下修补。
- maximum attempts: r10一次；pane nonzero、消失、identity不符、receipt缺失或 formal非PASS均立即
  `NEEDS_REPLAN`，不得自动发起 r11。

## Bug Context

- bug_source: release-authority formal execution externally interrupted twice.
- severity: blocker to Step5-7 and v9.3.4 signoff.
- environment: PR #124；Cdiag `462d64ac...`；Cfreeze `5c57e537...`；r8/r9 failed/excluded。
- current_behavior: foreground runner由 Codex unified exec PTY持有；Codex进程/turn被误杀时，
  runner及其 child收到终止，缺失 outer terminal chain。
- expected_behavior: runner由独立 tmux terminal server持续持有；Codex只读观察，即使 agent会话退出，
  runner仍自然完成并保留 exact pane exit status。
- reproduction_steps: r8在 Integration、r9在 Step3 database-matrix入口前后分别留下 partial child
  receipts且无 outer terminal receipts；r9 Step3 trap记录 `exit_code=1`、
  `last_phase=child-database-matrix`。
- reproduction_status: confirmed non-product execution-owner interruption.
- existing_evidence:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-dual-seal-formal-recovery-20260723-r9-external-interruption-fail-closed.md`。
- existing_tests: completed r9 Unit/Integration/Addon均 F0/E0/S0，但全部 excluded且不可拼接。
- regression_protection: host execution contract and durable tmux identity evidence required；不修改
  production/test suite。
- waiver_reason_and_risk: N/A。

## Risks and Open Questions

- known_risks:
  - host reboot、tmux server被显式 kill或机器资源故障仍可中断 runner；此时 fail closed；
  - tmux dead status仅证明进程退出码，不证明 receipt完整，必须双重验证；
  - detached执行降低实时交互能力，因此禁止运行中干预，只能等待自然终态；
  - r10是新的昂贵尝试，任何非终态都需再次由 owner决定是否值得继续。
- open_questions: none

## Ultra Execution Contract

- 先提交并 push 本 APPROVED Cplan；再创建唯一 activation commit，只修改本文件
  `status/readiness`，push后才可运行。
- 建议 run ID：
  `step4-v934-fsscript-lifecycle-dual-seal-formal-tmux-recovery-20260723-r10`。
- 使用 fresh clean non-shallow clone detached exact Cfreeze；独立 control directory与 tmux socket
  必须在新 run前不存在并以 mode 0700创建。
- tmux session必须 `remain-on-exit=on`；pane command只做 `umask 077` 后 `exec` canonical runner，
  不得包裹重试、日志tee、timeout或 exit-code translation。
- 启动后 Codex只能执行 tmux/list/capture与 filesystem/process/container只读观察；不得 attach、
  send-keys、respawn或启动第二 invocation。
- pane dead status `0` 后仍必须完整复核 outer terminal chain；任一 receipt/gate不PASS即失败。
- formal PASS后完成 durable evidence/result并转 `READY_FOR_SIGNOFF`，再推进 Step5-7；不得自行
  `ACCEPTED`。
- 任一 gate失败，将本文件设为 `NEEDS_REPLAN`、记录 failed/excluded evidence并停止。

## Implementation Result

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: tmux 3.4 isolated capability probe observed real PTY,
  PID=PGID=SID for the pane process and exact retained `pane_dead_status=7`; probe server cleaned.
- deviations: none
- residual_risks: pending
- readiness: ULTRA_EXECUTING — this exact two-field transition is the activation identity for r10 tmux-owned formal execution

## References

- predecessor recovery work item:
  `docs/9.3.4/workitems/BUG-v934-fsscript-formal-external-interruption-recovery-replan.md`
- r9 interruption record:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-dual-seal-formal-recovery-20260723-r9-external-interruption-fail-closed.md`
- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
