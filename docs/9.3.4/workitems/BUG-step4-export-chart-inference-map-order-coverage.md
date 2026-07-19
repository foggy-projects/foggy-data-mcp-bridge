# BUG: Step 4 ExportWithChart inferred-field coverage depends on unspecified map order

- severity: blocker for threshold freeze
- status: remediation-implemented / awaiting-new-diagnostic
- owner: Step 4 coverage and `foggy-dataset-mcp` test owner

## Observed failure

Fresh diagnostic-r27 passed mechanically but lost one non-critical aggregate branch and complexity outcome
relative to r26. The only XML delta was `ExportWithChartTool.java:248` (`mb/cb 0/2 -> 1/1`).
`fields_shouldBeInferredAutomatically` used `Map.of`, whose iteration order is unspecified, while
`inferFields` stops at the first numeric entry.

## Fix and proof

The existing test now builds ordered `LinkedHashMap` items with `category` before `amount`. No production
source, POM, test node, report cardinality or critical coverage target changed. Five fresh JVM/JaCoCo
replays each produced `16/F0E0S0`, line 248=`0 missed / 2 covered`, and identical target probe bitmap.

## Exit criteria

1. Commit/push a new Cdiag with this test-only stabilization and the r27 rejection evidence.
2. Run fresh all-lane diagnostic-r28 from that Cdiag.
3. Require aggregate branch >= `26112/44870` and complexity >= `17659/35571` before candidate generation.
4. Recreate candidate/capsule and independent reviews; do not reuse r27's non-canonical artifacts.
