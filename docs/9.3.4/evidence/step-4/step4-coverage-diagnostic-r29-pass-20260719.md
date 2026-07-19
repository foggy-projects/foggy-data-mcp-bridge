---
evidence_type: diagnostic-observation-git-safety-blocked
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r29
tested_commit: f420a4eaa3cf9bed0d7027b656ea71af6d0b03ca
status: non-freezable
decision: no-cfreeze-authority
recorded_at: 2026-07-19
---

# Step 4 diagnostic-r29 observation — Git-safety blocked

## Preserved diagnostic facts

`step4-coverage-20260719-diagnostic-r29` completed from the clean,
non-shallow Cdiag `f420a4eaa3cf9bed0d7027b656ea71af6d0b03ca` under outer
`umask 077`. The runner, restoration, health receipt, and wrapper outcome
returned zero. Its sealed status is `diagnostic-observed / completed / exit 0`,
and source-before equals source-after.

| Scope | Observed result |
|---|---|
| aggregate line | `54,624 / 76,830` |
| aggregate branch | `26,112 / 44,870` |
| aggregate complexity | `17,659 / 35,571` |
| critical policy | `12` classes; `23` applicable metrics; below-floor=`0` |
| structural exception | only `NamespaceScope.branch = 0/0`, explicitly not-applicable |
| group / universe | `24` groups; `24` production modules / `2,098` classes |
| required union | `773 + 59 structural / 5,707`, F0/E0/S0 |
| Addon companion | `2 / 6`, F0/E0/S0; excluded from required union |
| execution provenance | `23` exec files / `48` sessions, verified |

The aggregate counters equal the prior governed high-water exactly; no
denominator was rescaled and no critical exception was added. The reviewed
candidate fact is retained as local observation only:
`31019f99eb2a466ba02f7c07a2d7ff30b51625f52791adc5efb9c9d3fe9cf530`
with status `review-required`.

## Git-safety blocking boundary

The local r29 capsule contains raw run log (`run.log`) and raw exec content.
Those inputs are not admissible Git content, so the local capsule is excluded
from Git. Consequently r29 has no Git-safe capsule closure that can accompany
the candidate into a Cfreeze.

This is an authority boundary, not a reinterpretation of the diagnostic
numbers: the completed diagnostic and candidate facts above remain recorded,
but r29 is **non-freezable**. It does not authorize Cfreeze, formal, coverage
audit, Step 4 acceptance, 9.3.4 signoff, 9.3.5, or 9.4.0. No local capsule,
candidate, review, or observation may be promoted, repaired in place, or used
as a substitute for a new run.

r28 is historical high-water only. Its earlier history is not an exemption to
the Git-safe content boundary, and it cannot be combined with r29. Formal-r9
remains failed, excluded, non-reusable, and non-candidate.

## Required successor path

The exact successor path is:

`tool fix → new Cdiag → fresh diagnostic`

The tool fix must make the intended capsule evidence Git-safe by construction
and fail closed for forbidden raw content. The new Cdiag must be clean and
pushed before the fresh diagnostic. Only that new diagnostic may produce a new
candidate and a separately reviewed Git-safe evidence closure; r29 is not a
source of freeze authority at any point in that chain.
