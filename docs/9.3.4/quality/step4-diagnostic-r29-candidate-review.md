---
review_type: independent-diagnostic-candidate-fact-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r29
tested_commit: f420a4eaa3cf9bed0d7027b656ea71af6d0b03ca
candidate_sha256: 31019f99eb2a466ba02f7c07a2d7ff30b51625f52791adc5efb9c9d3fe9cf530
status: non-freezable
decision: candidate-facts-preserved-no-cfreeze-authority
reviewed_at: 2026-07-19
findings: "blocking=1; Git-safe capsule closure absent"
---

# Step 4 diagnostic-r29 candidate fact review

## Decision

The r29 candidate facts were independently recomputed and are retained as
diagnostic observation, but the candidate is **non-freezable**. Its required
evidence closure depends on a local capsule that contains raw run log
(`run.log`) and raw exec content, which is excluded from Git. Therefore the
candidate cannot be admitted to Cfreeze or serve as a canonical threshold.

This is a Git-safety decision rather than a numerical coverage failure. It
does not authorize formal, coverage audit, Step 4 acceptance, 9.3.4 signoff,
9.3.5, or 9.4.0.

## Preserved candidate facts

| Item | Reviewed value |
|---|---|
| diagnostic commit | `f420a4eaa3cf9bed0d7027b656ea71af6d0b03ca` |
| source SHA-256 (before=after) | `b4d5640d134191c83c7cbd614ad287427a98eedc208e5597218105f6a9f91842` |
| run status | `diagnostic-observed / completed / exit 0` |
| candidate SHA-256 | `31019f99eb2a466ba02f7c07a2d7ff30b51625f52791adc5efb9c9d3fe9cf530` |
| aggregate line | `54,624 / 76,830` |
| aggregate branch | `26,112 / 44,870` |
| aggregate complexity | `17,659 / 35,571` |
| critical policy | `12` classes; `23` applicable metrics; below-floor=`0` |
| structural N/A | only `NamespaceScope.branch = 0/0` |
| Unit lane | `681 + 55 structural / 4,941`, F0/E0/S0 |
| Integration lane | `47 + 4 structural / 320`, F0/E0/S0 |
| database matrix | `5` cells; `29 / 370`, F0/E0/S0 |
| external matrix | `16 / 76`, F0/E0/S0 |
| Step 3 required | `45 / 446`; database/external overlap, gap, and extra=`0/0/0` |
| required union | `773 + 59 structural / 5,707`, F0/E0/S0 |
| Addon companion | `2 / 6`, F0/E0/S0; excluded from required union |
| execution provenance | `23` exec files / `48` sessions, verified |
| production universe | `24` modules / `2,098` classes, verified |

The line and branch projection equals the previously governed high-water.
Those facts must not be mistaken for an accepted threshold or a waiver of the
evidence-chain requirements.

## Non-reuse and successor boundary

No r29 candidate, local capsule, or local review may be repackaged, appended,
or reused to satisfy a later Cfreeze. r28 is historical precedent only and
does not exempt r29 from Git-safe evidence requirements. Formal-r9 remains
failed and excluded; it is not an alternate source of authority.

The exact next path is:

`tool fix → new Cdiag → fresh diagnostic`

The tool fix must reject forbidden raw content and produce a Git-safe closure
by construction. After a clean, pushed Cdiag, the fresh diagnostic must create
new candidate facts and a new independently reviewed Git-safe closure. Only
that successor can be considered for a Cfreeze.
