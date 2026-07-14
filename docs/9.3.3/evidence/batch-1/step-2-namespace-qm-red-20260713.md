---
doc_role: expected_red_evidence
doc_purpose: Capture the pre-fix Namespace restoration and joined-QM completeness failures for Batch 1 Step 2.
version: 9.3.3
batch: 1
step: 2
status: completed-expected-red
recorded_at: 2026-07-13
---

# Batch 1 Step 2: Namespace and QM Completeness Expected-Red Evidence

## Decision

Step 2 is complete as a **pre-fix failure baseline**, not as a product pass.
Both classes deliberately end in `RedBaseline`, so normal Surefire/Failsafe
discovery does not run them. They are selected by fully-qualified `-Dtest`
only; Maven exit code 1 is accepted only after the owning XML is checked by
`scripts/assert-v933-red-report.sh`.

No production source, POM or external state changed in this step.

## Results

| Contract | Class | Tests/Failures/Errors/Skipped | Current observation | Status |
|---|---|---:|---|---|
| NS-SCOPE | `com.foggyframework.dataset.db.model.lifecycle.red.NamespaceProductionEntryRestorationRedBaseline` | 1/1/0/0 | normal, exception and explicit-default inner entries all leave the prior `outer-a` state as `null` | expected-red confirmed |
| QM-COMPLETE | `com.foggyframework.dataset.db.model.lifecycle.red.JdbcQueryModelCompletenessRedBaseline` | 1/1/0/0 | joined TM throws, but `JdbcQueryModelBuilder` records no outward failure and returns a root-only `JdbcQueryModelImpl` | expected-red confirmed |

The namespace test uses one JUnit case with bounded `assertAll`; its XML has
one failed test while the failure body records all three restoration paths.
Neither test uses `Thread.sleep`, an unbounded wait or a real external DB.

## Commands

```bash
mvn -B -pl foggy-dataset-model -P'!multi-db,model-lifecycle' \
  -Dtest=com.foggyframework.dataset.db.model.lifecycle.red.NamespaceProductionEntryRestorationRedBaseline \
  -Dv933.reportsDirectory="$PWD/foggy-dataset-model/target/v933-entry-gate/batch1-red/namespace" \
  test \
  -l foggy-dataset-model/target/v933-entry-gate/batch1-red/namespace/maven.log

scripts/assert-v933-red-report.sh \
  foggy-dataset-model/target/v933-entry-gate/batch1-red/namespace/surefire-reports \
  com.foggyframework.dataset.db.model.lifecycle.red.NamespaceProductionEntryRestorationRedBaseline \
  1 "normal return must restore"

mvn -B -pl foggy-dataset-model -P'!multi-db,model-lifecycle' \
  -Dtest=com.foggyframework.dataset.db.model.lifecycle.red.JdbcQueryModelCompletenessRedBaseline \
  -Dv933.reportsDirectory="$PWD/foggy-dataset-model/target/v933-entry-gate/batch1-red/qm-completeness" \
  test \
  -l foggy-dataset-model/target/v933-entry-gate/batch1-red/qm-completeness/maven.log

scripts/assert-v933-red-report.sh \
  foggy-dataset-model/target/v933-entry-gate/batch1-red/qm-completeness/surefire-reports \
  com.foggyframework.dataset.db.model.lifecycle.red.JdbcQueryModelCompletenessRedBaseline \
  1 "failed joined TM dependency"
```

## Local report identities

Target reports are local execution artifacts; these hashes bind this record to
the exact runs without claiming they are immutable release evidence.

| Artifact | SHA-256 |
|---|---|
| `namespace/maven.log` | `3d8592cbdac7ed30d5e67be195b7d0926807ce9ef9bd21e2fcb5a48fd95a3925` |
| `namespace/...NamespaceProductionEntryRestorationRedBaseline.xml` | `a045b014ffac3957ca825580d24d20a66758bbf67efd285238741eab1428ec35` |
| `qm-completeness/maven.log` | `49810fc4254e928d26da740a35dddec3d5be01afd949c27e82d9c2022dfa5175` |
| `qm-completeness/...JdbcQueryModelCompletenessRedBaseline.xml` | `6b740416beff4fcce4885c5294936b7d08a3fb3958a06c45384bb7b1032e192b` |

## Green transition

- Batch 2 must make the namespace case green through `NamespaceScope`, then
  retain it as a normal unit regression.
- The catalog/QM implementation batch must reject the whole candidate on any
  accumulated dependency error and prove the failed QM was never published.
- Assertions are the frozen target behavior and must not be weakened merely to
  accommodate current implementation.
