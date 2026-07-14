---
doc_role: expected_red_evidence
doc_purpose: Capture pre-fix cross-key serialization and order-dependent alias failures, plus the exact same-key source proof.
version: 9.3.3
batch: 1
step: 3
status: completed-expected-red
recorded_at: 2026-07-13
---

# Batch 1 Step 3: Single-flight and Alias Expected-Red Evidence

## Decision

Step 3 has two behavioral expected-red proofs and one explicit source proof.
The source proof is intentional: a 100-caller `buildCount=1` test against the
current manager-wide monitor would be falsely green and must not be presented
as single-flight evidence.

## Behavioral results

| Contract | Class | Tests/Failures/Errors/Skipped | Current observation | Status |
|---|---|---:|---|---|
| SF-ISOLATION | `...lifecycle.red.DistinctTableModelLoadOverlapRedBaseline` | 1/1/0/0 | both calls start through a Phaser, but only one reaches its controlled builder; bounded latch expires with remaining=1 | expected-red confirmed |
| deterministic alias | `...lifecycle.red.QueryModelAliasDeterminismRedBaseline` | 1/1/0/0 | forward `{Channel=DC, Customer=DC2}`; reverse `{Customer=DC, Channel=DC2}` | expected-red confirmed |

Both use SQLite/Spring public model seams. The overlap fixture releases its
blocked builder, cancels incomplete futures and asserts executor termination in
`finally`; neither class uses `Thread.sleep` or an unbounded wait.

## Commands

```bash
mvn -B -pl foggy-dataset-model -P'!multi-db,model-lifecycle' \
  -Dspring.profiles.active=sqlite \
  -Dtest=com.foggyframework.dataset.db.model.lifecycle.red.DistinctTableModelLoadOverlapRedBaseline \
  -Dv933.reportsDirectory="$PWD/foggy-dataset-model/target/v933-entry-gate/batch1-red/tm-distinct-overlap" \
  test \
  -l foggy-dataset-model/target/v933-entry-gate/batch1-red/tm-distinct-overlap/maven.log

scripts/assert-v933-red-report.sh \
  foggy-dataset-model/target/v933-entry-gate/batch1-red/tm-distinct-overlap/surefire-reports \
  com.foggyframework.dataset.db.model.lifecycle.red.DistinctTableModelLoadOverlapRedBaseline \
  1 "Timed out waiting for both distinct TM builders to overlap"

mvn -B -pl foggy-dataset-model -P'!multi-db,model-lifecycle' \
  -Dspring.profiles.active=sqlite \
  -Dtest=com.foggyframework.dataset.db.model.lifecycle.red.QueryModelAliasDeterminismRedBaseline \
  -Dv933.reportsDirectory="$PWD/foggy-dataset-model/target/v933-entry-gate/batch1-red/qm-alias-order" \
  test \
  -l foggy-dataset-model/target/v933-entry-gate/batch1-red/qm-alias-order/maven.log

scripts/assert-v933-red-report.sh \
  foggy-dataset-model/target/v933-entry-gate/batch1-red/qm-alias-order/surefire-reports \
  com.foggyframework.dataset.db.model.lifecycle.red.QueryModelAliasDeterminismRedBaseline \
  1 "canonical-to-alias map must be order-independent"
```

## Same-key and shared-failure source proof

- `TableModelLoaderManagerImpl.load` is method-level `synchronized` at current
  lines 131 and 136; lookup, build and put all remain under that one monitor at
  lines 137–171.
- A 100-caller success test can therefore observe build count 1 while all keys
  are globally serialized. It cannot prove a keyed flight.
- If build throws anywhere in lines 144–169, there is no in-flight future or
  shared failure object. The monitor is released; the next caller independently
  executes a new build. Current waiters are merely BLOCKED on the manager, not
  joined to a winner.
- `QueryModelLoaderImpl` likewise retains mutable maps and performs
  eval/build/register without a keyed future; alias allocation reads mutable
  `usedAliases` in arrival order at current lines 812–865.

Batch 4 must expose a package-private flight observer (or equivalent controlled
seam) for `winnerEntered`, `waiterJoined` and `flightRemoved`. Only then run:

1. 100 callers; wait until 99 waiters joined before releasing the winner;
   assert one build, same committed catalog identity and in-flight count zero.
2. winner marker failure; assert all 99 waiters receive the same stable
   category/cause and the flight is removed; call 101 becomes a new winner,
   succeeds with total build count 2 and publishes once.

These are mandatory green-transition tests, not a waiver.

## Local report identities

| Artifact | SHA-256 |
|---|---|
| `tm-distinct-overlap/maven.log` | `4ab41ede0c6be39d7359c4a8d270eddf2fe87405f25085f322417f7e1ed120a4` |
| `tm-distinct-overlap/...DistinctTableModelLoadOverlapRedBaseline.xml` | `e02da7feb4b7ea9d9893ebbb9c43fce52bfced28526b586aac89c37a30f21088` |
| `qm-alias-order/maven.log` | `06cac35483ca07c069686587c96d7f16764496a90075bd9278ca6635619e6a41` |
| `qm-alias-order/...QueryModelAliasDeterminismRedBaseline.xml` | `4f36675439c5556bd7315f902a9e7089c0a15be1aecb02c5ea21512e211e616a` |
