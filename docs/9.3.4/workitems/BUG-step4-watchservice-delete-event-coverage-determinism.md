# BUG: Step 4 WatchService filtered-delete coverage depends on filesystem event timing

- severity: blocker for threshold freeze
- status: remediation-implemented / r33-excluded / awaiting-r34
- owner: Step 4 coverage and `foggy-core` test owner

## Observed failure

Fresh diagnostic-r32 completed but lost one non-critical aggregate branch and complexity outcome relative to the reviewed high-water. The sole semantic delta is `WatchServiceFileTracer.handleFileDeleted`, where `WatchServiceFileTracer.java:442` changed from `4/4` to `3/4` branch coverage.

The extension-filter test only created files. Coverage of filtered deletion consequently depended on teardown and host `WatchService` scheduling, while the existing mock-key deletion test covered only the unfiltered short-circuit path.

## Fix and proof

The existing watched-child-deletion test now reuses its mock key to dispatch an unfiltered child delete, a filtered-out `.txt` delete, and a filtered-in `.qm` delete. It asserts zero and one callbacks for the two filtered cases respectively. The existing test node remains in place.

No production source, POM, runner, report/testcase identity, threshold, critical target, or exclusion changes. Five independent focused JaCoCo runs restored line 442=`4/4` and method branch/complexity=`11/12` / `6/7`; one full `foggy-core` suite completed `97/F0E0S0` with the same counters.

## Original exit criteria（historical; superseded for execution）

1. Commit/push a new Cdiag containing this test-only stabilization and the r32 rejection record.
2. Run fresh all-lane diagnostic-r33 from that Cdiag under the strict environment contract.
3. Require aggregate line >= `54624/76830`, branch >= `26112/44870`, and complexity >= `17659/35571` before candidate generation.
4. Recreate candidate/Git-safe closure and independent reviews only from r33; do not reuse r32's non-canonical material.

## Execution status（2026-07-20）

r33 consumed the above fresh-run authorization but is `failed / excluded / non-reusable / non-candidate` before
canonical Unit authority. Its fallback cleanup result does not prove cleanup closure and must not be repaired or
reused. The remediation itself remains implemented; the new execution path is docs-only Cdiag → governed
readiness preflight → fresh r34, which alone may satisfy the unchanged exit criteria and recreate candidate/
Git-safe closure/review.
