---
evidence_type: diagnostic-source-seal-preflight-fail-closed
version: 9.3.4
step: 4
run_id: step4-v934-fsscript-lifecycle-inventory-diagnostic-20260722-r6
phase: outer-runner-source-seal-preflight
status: failed-excluded
failure_class: stale-nested-outer-runner-digest
decision: needs-replan-no-authority
recorded_at: 2026-07-22
---

# FSScript lifecycle inventory diagnostic source-seal preflight failure

## Governed identity

- Diagnostic-ready integration commit:
  `5245f2de4876c5a394806e553add12f58681b926`.
- Activation Cdiag:
  `b8be71aecf8e7526cc5b13cb67426e968f2c5a9a`.
- The fresh clone was clean, non-shallow and detached at that exact Cdiag.
- The activation commit is the direct child of the integration commit and changes only the delivery
  spec `status/readiness` fields from `APPROVED` to `ULTRA_EXECUTING`.
- The run ID above was invoked once in diagnostic mode. It was not retried.

## Bounded safe facts

- Fresh-clone preflight passed Git identity/topology, the two-field activation diff, Step4/5/6
  manifests, coverage contract, successor overlay, CI workflow, Docker/Compose, sanitized environment
  and no-clobber run-root checks.
- The canonical runner exited with code 1 during its raw source-seal binding preflight, before acquiring
  lane evidence or creating the run root.
- The actual outer runner digest and Step4 manifest digest were both
  `ccda7e7e78547a30ada3466df76d197c483ddd3d16be3f43dcad0ff47e37a57e`.
- The nested digest frozen in
  `scripts/v934/step4/run_log_lifecycle_negative_test.sh` remained
  `cf3979fc76ff4369eca88ce52e35dda66ead98f3579d09e1482646de3f109e82`.
- Therefore `--verify-runner-seal-bindings` correctly failed closed with
  `E_SOURCE_SEAL_BINDING`; no Maven test, database lane, coverage aggregation, candidate, capsule or
  formalization step started.
- The canonical run root is absent, the fresh clone remains clean, and run-owned container, volume and
  network counts are all zero.

## Decision

This Cdiag and invocation are permanently **failed / excluded / non-reusable / non-candidate**. The
frozen one-run rule forbids correcting the nested digest and retrying under this plan or Cdiag.

can_enter_cfreeze=no / can_enter_formal=no / can_enter_step5=no /
can_enter_step6=no / can_enter_step7=no / can_enter_9.3.5=no /
can_enter_9.4.0=no.

## Required successor boundary

A new approved successor replan must update the nested outer-runner digest binding, recompute all
affected Step4/5/6 manifests and transitive hashes, and explicitly run
`run_log_lifecycle_negative_test.sh --verify-runner-seal-bindings` during focused preflight before
creating a new activation Cdiag. It must use a new integration commit, Cdiag and run ID; it must not
reuse this invocation or relax source-seal verification.
