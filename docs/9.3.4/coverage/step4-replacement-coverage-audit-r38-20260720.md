---
doc_role: replacement_coverage_audit
version: 9.3.4
step: 4
tested_cfreeze: 62361688d838ba0a73348900502924decfbeeb68
cdiag_parent: 9743f97d9d935d5e26311b78c158755bca51f17a
formal_run_id: step4-coverage-20260720-formal-r38
status: ready-for-owner-signoff
recorded_at: 2026-07-20
---

# Step 4 r38 replacement coverage audit

## Scope and decision

This is a new replacement audit for the changed Addon successor-runner bytes. Its authoritative scope is
[step4-replacement-audit-scope-r38-20260720.json](step4-replacement-audit-scope-r38-20260720.json).

- audit rows=35 = legacy 31 + supplemental 4;
- additional blocking acceptance gate=1 (post-r10 report-stage public-receipt mode binding);
- governing acceptance checks=36 in total;
- the Addon mtime remediation is an independent BUG signoff control outside this 35+1 denominator;
- the Unit MySQL fixture-classification debt remains due before 9.3.5 version acceptance.

| Result | Count |
|---|---:|
| Audit rows covered | 35 |
| Audit rows partial | 0 |
| Audit rows not covered | 0 |
| Additional blocking gate passed | 1 |
| Critical / major evidence gaps | 0 / 0 |

Decision: ready-for-owner-signoff.

## Authority evidence

- E1: [fresh formal-r38 result](../evidence/step-4/step4-coverage-formal-r38-pass-20260720.md), the sole
  formal authority for Cfreeze 62361688d838ba0a73348900502924decfbeeb68.
- E2: r37 candidate/capsule review, used only as the frozen threshold and high-water baseline.
- E3: [same-Cfreeze Pivot r38 companion](../evidence/step-4/step4-pivot-legacy-companion-r38-20260720.md).
- E4: [independent r38 quality review](../quality/step4-formal-r38-addon-context-final-implementation-quality-20260720.md)
  and the canonical Addon publication-order BUG record.
- H: immutable historical RED and r11 records, retained only as regression context and never spliced into r38
  formal authority.

E1 is the sole formal authority. E2-E4 are current, Cfreeze-bound supporting controls. They do not import raw
exec, XML, logs, container identities, or failed-run authority.

## Legacy 31 mapping

| Workitem | Replacement evidence | Verdict |
|---|---|---|
| r6 MySQL57 port occupation | E1 fixture/preflight, Unit and DB closure | covered |
| Bean2Map timing oracle | E1 Unit full lane | covered |
| child run-log tee residue | E1 child lifecycle and cleanup | covered |
| database successor manifest | E1 Step 3 required and successor overlay | covered |
| evidence JSON numeric alias | E1 typed/XML negative suite | covered |
| exec class-ID scope drift | E1 exec manifest and aggregate provenance | covered |
| Git environment override | E1 source/provenance hostile-negative closure | covered |
| legacy JaCoCo argLine | E1 effective build/report authority | covered |
| lifecycle semantic validator | E1 lifecycle negatives and child lifecycle | covered |
| ListPreset coverage order | E1 Unit deterministic branch execution | covered |
| outer runner source seal | E1 source-before/after and formal provenance | covered |
| Pivot legacy hybrid fixture | E1 formal variants plus E3 companion | covered |
| Pivot NULL-axis oracle | E1 five-DB aggregate high-water | covered |
| PreAgg L2 hybrid fixture | E1 Integration/full union | covered |
| PreAgg Unit order isolation | E1 Unit full lane | covered |
| PreAgg validation wrong table | E1 Unit full lane | covered |
| QueryModel join-graph race | E1 Unit/concurrency coverage binding | covered |
| sensitive-scan authorization context | E1 sensitive-scan receipt | covered |
| source/context cross-link | E1 source/context provenance binding | covered |
| source inventory file mode | E1 source policy and preflight negatives | covered |
| threshold applicability/N/A | E1 coverage gate and structural applicability | covered |
| Unit hidden MySQL dependency | E1 run-owned fixture and Unit lane | covered |
| Unit lifecycle static drift | E1 lifecycle contract/negative and child result | covered |
| final mysqld handoff | E1 fixture readiness and lifecycle | covered |
| WatchService shutdown race | E1 Unit critical-class coverage | covered |
| snapshot cross-repository write | E1 fresh-clone isolation preflight and Unit lane | covered |
| fsmonitor valid-negative fixture | E1 bootstrap negative closure | covered |
| MapBeanInfo double-check race | E1 controlled Unit/aggregate result | covered |
| CALCULATE catalog portability | E1 tracked-catalog preflight and sibling-free fresh-clone Integration execution | covered |
| Unit MySQL known-consumer bound | E1 schema-2 fixture contract and Unit lane; classification debt remains excluded | covered |
| report-runner interpreter dispatch | E1 report-stage success plus governed interpreter negatives | covered |

## Supplemental four, receipt gate, and revalidation control

| Item | Replacement evidence | Verdict |
|---|---|---|
| ExportWithChart map order | E2 high-water baseline plus E1 exact formal branch/complexity | covered |
| effective-POM public mode portability | E1 strict public receipt=0644 regular/non-link | covered |
| Git-safe frozen capsule boundary | E2 r37 Git-safe candidate/capsule dual review and frozen-diagnostic replay | covered |
| WatchService delete determinism | E2 high-water recovery plus E1 Unit/aggregate result | covered |
| report-stage receipt mode binding (additional gate) | E2 strict-mode repair negatives plus E1 final receipt/provenance=0644 | passed |
| Addon parent/child mtime publication order (BUG control, outside denominator) | E1 child_mtime >= parent_mtime plus E4 fail-closed review | passed |

## Audit boundary

Independent r38 quality review is APPROVE with B/H/M/L=0/0/0/0; this audit finds critical/major evidence
gaps=0/0. The 35+1 mapping is bound only to Cfreeze 62361688d838ba0a73348900502924decfbeeb68 and
formal-r38. r11 and r37 remain historical/baseline material, not replacement formal authority.

This audit makes the canonical BUG eligible for owner signoff. It does not itself accept the remediation or
open Step 5, Steps 6-7, 9.3.5, or 9.4.0.
