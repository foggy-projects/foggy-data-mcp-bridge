---
evidence_type: diagnostic-environment-precondition-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r31
tested_commit: f80fadd62ca00d3ba56f1be04e92113ba1145019
phase: child-unit/unit-mysql57-lifecycle-negative
status: failed-excluded
failure_class: environment-precondition
decision: no-cfreeze-authority
recorded_at: 2026-07-19
---

# Step 4 diagnostic-r31 fixed-port precondition fail-closed record

## Decision

Fresh r31 started from the clean, pushed successor-binding repair Cdiag under
outer `umask 077`. Contract, overlay, static bootstrap, source-seal and class
universe checks completed. It then stopped in `child-unit`, at the first
`unit-mysql57-lifecycle-negative` precondition, before a lifecycle probe,
fixture provision, Maven Unit execution, or any test lane began.

The run-owned fixture deliberately requires its fixed host port to be free.
Its derived Docker resource counts were `0/0/0` (container/volume/network),
while the port-free predicate was false. The outer cleanup receipt also
recorded `0/0/0` and passed. Independent read-only checks confirmed that the
port remained occupied by one Docker-published mapping outside the r31-derived
project. No listener identity, runtime log, raw execution data, or container
metadata is tracked by this record.

r31 is permanently **failed / excluded / non-reusable / non-candidate**. It
has no lifecycle receipt, normal fixture receipt, Maven Unit result, lane
result, aggregate, XML, observation, summary, candidate, or final authority.
It must not be rerun under the same ID, repaired in place, or combined with
any historical or successor material.

## Environment boundary

This is an execution-environment precondition failure, not evidence of a
product or coverage regression. The fail-closed port check must not be relaxed,
and the runner must not take over, reuse, terminate, or clean an external
listener. The failed run proves only that r31 itself left no derived Docker
resources; it does not establish ownership of the pre-existing listener.

## Authority status and next gate

`can_enter_cfreeze=no / can_enter_formal=no / can_enter_step5=no /
can_enter_coverage_audit=no / can_enter_acceptance=no`.

The single fresh diagnostic attempt authorized by the predecessor Cdiag has
been consumed. The only permitted successor path is:

`seal r31 exclusion → new clean/pushed Cdiag → independently verify the fixed
port is free → fresh strict-umask diagnostic-r32 → new candidate/Git-safe
capsule/independent review → direct-single-parent Cfreeze → fresh formal →
post gates`

An authorized environment operator must restore the port precondition outside
this evidence chain. No source, runner, contract, threshold, or fixture change
is justified by r31 alone.
