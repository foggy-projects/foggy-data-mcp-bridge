---
doc_role: supplemental_coverage_evidence
version: 9.3.4
step: 4
tested_cfreeze: 62361688d838ba0a73348900502924decfbeeb68
cdiag_parent: 9743f97d9d935d5e26311b78c158755bca51f17a
formal_run_id: step4-coverage-20260720-formal-r38
status: passed-supplemental-only
recorded_at: 2026-07-20
---

# Step 4 r38 same-Cfreeze Pivot supplemental companion

## Purpose and boundary

This companion re-executes the legacy Pivot pre-aggregation path in a separate fresh clean clone of the same
Cfreeze as formal-r38. It closes the supplemental branch-evidence boundary for the r38 replacement audit; it
is not a rerun, replacement, or extension of the formal 23 exec / 48 session totals.

## Execution

The fresh clone preflight had no prior module target tree. It ran with ambient Maven and JVM option variables
removed:

~~~bash
env -u MAVEN_ARGS -u MAVEN_BASEDIR -u MAVEN_CONFIG -u MAVEN_OPTS \
  -u MAVEN_SKIP_RC -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS \
  mvn -q -pl foggy-dataset-model \
    -DskipUnitTests=true -DskipITs=false \
    -Dfailsafe.failIfNoTests=true \
    -Dfailsafe.failIfNoSpecifiedTests=true \
    '-Dit.test=PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder' \
    verify
~~~

Result: exit=0; selected Failsafe report=1 test / 0 failure / 0 error / 0 skipped.

- Tested source identity:
  foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIT.java
  SHA-256=18ebeedf8d79a84d5ba59ff991aeffeefea73cbb8aa51c6c10444ed64ba6d325.
- Newly produced selected report identity:
  SHA-256=067d439c23c6a1b555f50763cd0a75fb53eaeefe2a0bea3d81d9f8b87f9495b8.
- The report was verified as a regular, non-link file with mode 0664; its parsed safe cardinality is exactly
  1/F0E0S0 and the selected test case is present once.

The report is run-local and is not checked in. This companion does not alter formal-r38 aggregate, execution,
threshold, candidate, or final-artifact counters. It cannot restore downstream authority by itself.
