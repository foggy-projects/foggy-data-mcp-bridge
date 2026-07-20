---
doc_role: replacement_coverage_audit
version: 9.3.4
step: 4
tested_cfreeze: 4b17dbe34ae17168d44377c19ea6637b4c37a200
formal_run_id: step4-coverage-20260720-formal-r11
status: ready-for-acceptance
recorded_at: 2026-07-20
---

# Step 4 replacement coverage audit

## Scope and decision

This is a replacement audit; it does not alter the historical 25-row r4 audit. Its authoritative scope is
[step4-replacement-audit-scope-20260720.json](step4-replacement-audit-scope-20260720.json):

- audit rows=`35` = legacy `31` + supplemental `4`;
- additional blocking acceptance gate=`1` (post-r10 report-stage public-receipt mode binding);
- governing acceptance checks=`36` in total;
- out of scope: `DEBT-unit-mysql57-fixture-classification-migration`, which remains due before 9.3.5 version
  acceptance and is not silently treated as closed.

Decision: `ready-for-acceptance`.

| Result | Count |
|---|---:|
| Audit rows covered | 35 |
| Audit rows partial | 0 |
| Audit rows not covered | 0 |
| Additional blocking gate passed | 1 |
| Critical / major evidence gaps | 0 / 0 |

## Authority evidence

- `E1`: fresh formal-r11 result and final public artifact:
  [formal-r11 pass](../evidence/step-4/step4-coverage-formal-r11-pass-20260720.md).
- `E2`: r35 threshold/capsule review and diagnostic pass, used only as the Cfreeze threshold baseline.
- `E3`: same-Cfreeze Pivot supplemental evidence:
  [Pivot r11 companion](../evidence/step-4/step4-pivot-legacy-companion-r11-20260720.md).
- `E4`: historical RED/fail-closed records and their governed remediation workitems, retained as regression
  evidence rather than spliced formal authority.
- `E5`: independent final quality:
  [formal-r11 quality review](../quality/step4-formal-r11-final-implementation-quality-20260720.md).

`E1` is the sole replacement formal authority. `E2`–`E4` explain why each regression remains governed;
they do not import historical exec, XML, logs, container identities, or failed-run authority.

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
| Pivot legacy hybrid fixture | E1 formal variants + E3 same-Cfreeze companion | covered |
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
| CALCULATE catalog portability | E1 tracked-catalog preflight and Integration execution in a sibling-free fresh clone | covered |
| Unit MySQL known-consumer bound | E1 schema-2 fixture contract and Unit lane; classification debt remains excluded | covered |
| report-runner interpreter dispatch | E1 report-stage success plus governed interpreter negatives | covered |

## Supplemental four and additional gate

| Item | Replacement evidence | Verdict |
|---|---|---|
| ExportWithChart map order | E2 r35 high-water plus E1 exact formal branch/complexity | covered |
| effective-POM public mode portability | E1 strict-umask formal receipt=`0644` regular/non-link | covered |
| Git-safe frozen capsule boundary | E2 r35 Git-safe candidate/capsule dual review and frozen-diagnostic replay | covered |
| WatchService delete determinism | E2 high-water recovery plus E1 Unit/aggregate result | covered |
| report-stage receipt mode binding (additional gate) | E2 strict-umask repair negatives + E1 final receipt/provenance=`0644` | passed |

## Audit boundary

Independent review approved the mapping with B/H/M/L=`0/0/0/0` and critical/major evidence gaps=`0/0`.
The workitem conclusions above are bound to a current formal execution, while historical fail-closed records
remain immutable. This audit opens only Step 4 feature acceptance; r10, r34, prior Cfreezes and raw runtime
artifacts are not authority. The independent debt remains explicitly open for 9.3.5 version acceptance.
