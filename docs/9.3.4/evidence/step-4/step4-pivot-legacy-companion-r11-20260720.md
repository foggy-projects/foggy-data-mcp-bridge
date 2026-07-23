---
doc_role: supplemental_coverage_evidence
version: 9.3.4
step: 4
tested_cfreeze: 4b17dbe34ae17168d44377c19ea6637b4c37a200
formal_run_id: step4-coverage-20260720-formal-r11
status: passed-supplemental-only
recorded_at: 2026-07-20
---

# Step 4 r11 same-Cfreeze Pivot supplemental companion

## Purpose and boundary

This companion re-executes the legacy Pivot pre-aggregation path on the same Cfreeze as formal-r11. It closes
the supplemental branch-evidence boundary identified by the replacement audit plan; it is not a rerun,
replacement, or extension of formal-r11's `23 exec / 48 session` totals.

## Execution

The fresh formal clone ran:

```bash
mvn -q -pl foggy-dataset-model \
  -DskipUnitTests=true -DskipITs=false \
  -Dfailsafe.failIfNoTests=true \
  -Dfailsafe.failIfNoSpecifiedTests=true \
  '-Dit.test=PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder' \
  verify
```

Result: exit=`0`; selected Failsafe report=`1 test / 0 failure / 0 error / 0 skipped`.

- Test source SHA-256:
  `18ebeedf8d79a84d5ba59ff991aeffeefea73cbb8aa51c6c10444ed64ba6d325`.
- Selected report file SHA-256:
  `12b88b70db5b702cb4d585174726ee96ea8674f4fd00b4e3558015abb62da9ef`.

The report is run-local and is not checked in. The only persisted facts are the command, Cfreeze identity,
safe cardinality/result, and content identity above. This companion did not alter formal-r11 aggregate,
execution, threshold, candidate, or final-artifact counters.
