---
evidence_type: focused-lifecycle-fail-closed
version: 9.3.4
step: 4
phase: successor-preactivation-focused-validation
status: failed-excluded
failure_class: stale-outer-executable-stream-digest
decision: needs-replan-no-activation
recorded_at: 2026-07-22
---

# FSScript lifecycle inventory successor focused validation failure

## Governed identity

- Failure base: `74ea896a3a0dfb0cf7182c39fe1534e74f7fb84b`.
- Approved successor Cplan: `f2474dcea5717dc116489f00f652d2e1a7ad3350`.
- Worktree: isolated clean successor worktree; the protected original workspace was not modified.
- No diagnostic-ready integration commit, activation Cdiag, governed run ID, run root, candidate or
  capsule was created.

## Focused validation facts

- The attempted implementation changed only the nested outer raw SHA and the resulting Step4/Step6
  transitive digest bindings.
- Canonical `--verify-runner-seal-bindings` passed with `status=passed count=4`.
- Step4 and Step6 `sha256sum -c`, Bash/Python/JSON syntax, `git diff --check` and changed-path audit
  passed before the lifecycle suite.
- The complete `run_log_lifecycle_negative_test.sh` focused suite was invoked once. It exited 1 and
  was not retried.
- The terminal rejection was:
  `E_EXECUTABLE_STREAM_SEAL` for `scripts/verify-v934-step4-coverage.sh`, with expected digest
  `f3d3587486e16a54c90af381554c2115c0188aaec28cc631717dd01f5c66a16a` and observed digest
  `c696f9bf34e76bd3a5cae97d28ed378cae2cc78b872fc9c948985afc5c763a0c`.
- The suite cleanup trap completed; no lifecycle helper/persistent-child process remained. The
  focused test did not start Docker lanes or Maven product tests.

## Root-cause boundary

- Commit `5245f2de4876c5a394806e553add12f58681b926` changed executable lines in the outer runner to include
  the accepted FSScript report inventory (`773/5707 -> 774/5709`). Its raw SHA changed from
  `cf3979fc...f109e82` to `ccda7e7e...e37a57e`.
- The lifecycle validator's outer executable-stream constant remained
  `f3d3587486e16a54c90af381554c2115c0188aaec28cc631717dd01f5c66a16a`, last updated at commit
  `5aaffbb4`.
- Therefore the r6 raw-seal failure masked a second deterministic stale consumer. After the raw
  binding was corrected, the executable-stream seal correctly failed closed.

## Decision

The attempted implementation and focused result are **failed / excluded / non-reusable**. The five
script/manifest edits were reverted. This Cplan cannot create an activation Cdiag or authorize a
diagnostic.

can_enter_cdiag=no / can_enter_diagnostic=no / can_enter_cfreeze=no /
can_enter_formal=no / can_enter_step5=no / can_enter_step6=no /
can_enter_step7=no / can_enter_9.3.5=no / can_enter_9.4.0=no.

## Required successor boundary

A new approved successor contract must explicitly bind both the canonical outer raw SHA
`ccda7e7e...e37a57e` and executable-stream SHA `c696f9bf...c763a0c`, preserve the existing mutation
negatives, recompute the full Step4/Step6 digest closure, and run both the exact raw-binding preflight
and complete lifecycle suite before creating a new activation Cdiag. It must use a new integration,
Cdiag and diagnostic run ID and must not treat this failed focused invocation as reusable authority.
