---
evidence_type: successful-diagnostic-not-freezable
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-diagnostic-r32
tested_commit: a9ec2a2f3907d80ae9e6d7b0a4c579f0859607b5
status: diagnostic-observed
decision: threshold-candidate-rejected-high-water-regression
candidate_status: non-canonical
capsule_status: non-canonical
recorded_at: 2026-07-20
---

# Step 4 diagnostic-r32 WatchService delete high-water rejection

## Decision

`step4-coverage-20260720-diagnostic-r32` completed from a fresh, clean checkout of `a9ec2a2f3907d80ae9e6d7b0a4c579f0859607b5`. Its public diagnostic result is `diagnostic-observed / completed / exit 0`; source-before equals source-after, all required lanes passed, and runner-owned cleanup was `0/0/0` (container/volume/network).

Required evidence was `773+59 structural / 5,707 testcase / F0E0S0`; Addon companion evidence was `2/6 / F0E0S0`; execution provenance was `23` files / `48` sessions. The critical policy remained `12` classes / `23` applicable metrics / `1` structural N/A / below-floor=`0`.

The diagnostic remains non-freezable. Aggregate line coverage reached the governed floor, `54624/76830`, but branch=`26111/44870` and complexity=`17658/35571` are each one below the reviewed r26/r28 high-water: branch=`26112/44870`, complexity=`17659/35571`. Mechanical candidate verification cannot authorize a silent threshold reduction.

## Exact localization

The r28/r32 semantic comparison has exactly one changed production class counter:

| Scope | r28 | r32 |
|---|---:|---:|
| `WatchServiceFileTracer` branch | `99/128` | `98/128` |
| `WatchServiceFileTracer` complexity | `63/92` | `62/92` |
| `handleFileDeleted` branch | `11/12` | `10/12` |
| `handleFileDeleted` complexity | `6/7` | `5/7` |
| `WatchServiceFileTracer.java:442` branch | `4/4` | `3/4` |

The production and test sources for this class have no r28-to-r32 diff. The existing extension-filter test only exercised real file creation, so the filtered delete outcome depended on filesystem teardown/event timing; the existing mock-key deletion test exercised only the unfiltered short-circuit path.

## Rejected non-canonical material

Any r32 technical candidate or Git-safe capsule created while validating the tooling remains isolated, untracked, and non-canonical. This record deliberately stores no candidate, capsule, archive, XML, exec, log, run-tree, or runtime-artifact identity. None may enter review, Cfreeze, formal, audit, acceptance, or any successor authority chain.

## Controlled remediation

The Cdiag changes one existing test node, `WatchServiceFileTracerTest#watchedChildDeletionMustSignalAuthorityLossAndCleanOnlyChildTree`. It reuses a mock `WatchKey` to drive three synchronous delete cases: unfiltered child deletion, filtered-out `.txt` deletion, and filtered-in `.qm` deletion. The assertions require zero callback for the excluded file and exactly one callback for the included file.

No production source, POM, runner, test-node count, report identity, coverage threshold, critical set, exclusion, or public API changes. Five independent focused JaCoCo JVMs each restored line 442=`4/4` and `handleFileDeleted` branch=`11/12`, complexity=`6/7`; the full `foggy-core` suite was `97/F0E0S0` with the same counters. Independent review was `PASS / B/H/M/L=0/0/0/1`, mandatory fixes=`0`; the lone low finding is pre-existing singleton fake-watcher cleanup debt and is not expanded by this change.

## Only legal next gate

Commit and push this test-only Cdiag, verify its clean identity, then run one fresh strict-umask diagnostic-r33. It must complete every lane with source/cleanup closure, retain the same critical policy, and meet line >= `54624/76830`, branch >= `26112/44870`, complexity >= `17659/35571` before it may generate a new candidate or Git-safe capsule. Only then may independent review, direct-single-parent Cfreeze, fresh formal, and the post gates begin. Steps 5–7, 9.3.5, and 9.4.0 remain closed.
