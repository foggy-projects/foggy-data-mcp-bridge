---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r28
tested_commit: 507e1732b587c530026f19fae5d570b8845e369b
candidate_sha256: 1b654214d23c60448d88d14fd7f2f5e8e7dd08636721f10bf735c5db08ea39d5
reviewer: "Codex /root+Reviewer A+Reviewer B"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/r26_candidate_review (Reviewer A)"
  - "/root/r28_capsule_review (Reviewer B)"
reviewed_at: 2026-07-19T10:41:12Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r28 threshold review

## Decision

确认 `step4-coverage-20260719-diagnostic-r28` 的 exact observed counters 可作为 Step 4
reviewed thresholds，decision=`confirm-observed-thresholds`。Reviewer A 独立复算 candidate、
raw XML、aggregate/critical counter、report/exec/class universe 与来源绑定；Reviewer B 独立复算
repository/source identity、outer restore receipt，并在新临时目录重建、逐字节比较及 materialize
portable capsule。

本记录不声称用户或 release owner 已人工签收，`user_confirmation=not-asserted`。它仅授权把
r28 candidate 的 exact projection 写入 `507e1732...` 的唯一 direct-single-parent Cfreeze。fresh
formal-r9、post-formal quality、coverage audit、Step 4 feature acceptance 和 9.3.4 version signoff
均不在本次确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260719-diagnostic-r28` |
| diagnostic Git HEAD | `507e1732b587c530026f19fae5d570b8845e369b` |
| diagnostic source SHA-256 | `6c8b09e357fbc9237c8fba51a477e4b4465c58ca53d025d7c6646f153e6e1b36` |
| run status SHA-256 | `79faf73fc89bdf73c17c132e6ae91ef99eaea558e125effd40c517aa93b70618` |
| summary SHA-256 | `e6c348f6d8215ac67d1fcae1176bc60b6fd5964eb9254fa0748b75d419c017c1` |
| observation SHA-256 | `cc3570e6d493ba58608da385b34c8e9906179fd998ec869d81aaa83d550d50e3` |
| diagnostic contract SHA-256 | `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b` |
| candidate SHA-256 | `1b654214d23c60448d88d14fd7f2f5e8e7dd08636721f10bf735c5db08ea39d5` |
| capsule archive SHA-256 / size | `3f0ae2bcf2f0cbd93228e9d8bfb0914dd0ef3fe2f85e8dd87143f4651f00cee6 / 20,010,306` |
| capsule manifest SHA-256 | `b23c140a4a326ef216c7353a6ee4e9511bb4a74fd1fcc3e68fa6d80b5a906e48` |

## Independent recomputation

1. Candidate public verification and both independent reviews preserved candidate SHA-256
   `1b654214d23c60448d88d14fd7f2f5e8e7dd08636721f10bf735c5db08ea39d5`.
2. Aggregate counters match raw XML and observation exactly: instruction=`252725/352456`, line=
   `54624/76830`, branch=`26112/44870`, complexity=`17659/35571`, method=`9068/12701`,
   class=`1503/1716`. These are exactly the r26 high-water counters; r27's one-count branch and
   complexity regression is not reused.
3. All `24` groups match raw XML for every counter. The `12` critical rows match raw XML,
   observation and candidate; positive metrics=`23`, structural N/A=`1`, below-floor=`0`.
4. Report authority is Unit=`681+55/4941/F0E0S0`, Integration=`47+4/320/F0E0S0`, database=
   `29/370/F0E0S0`, external=`16/76/F0E0S0`, required=`773+59/5707/F0E0S0`, Addon=
   `2/6/F0E0S0` and excluded from the required union.
5. Exec/session=`23/48`; production universe=`24 modules / 2,098 classes`; class tree binding is exact.
6. Source before/after is byte-identical. Public `validate-diagnostic-run` and
   `verify-threshold-candidate` both returned success in the fresh, non-shallow r28 repository.
7. Reviewer B independently rebuilt the capsule; canonical and rebuild archive/manifest were
   byte-identical. Manifest=`sealed / 6,638 entries / 0 symlink / 0 special`.
8. Fresh empty-directory materialize closed all `6,638` entries; self-test=`8/8`. Outer wrapper
   runner/restore/receipt/outcome=`0/0/0/0`, and four original demo containers were restored
   `running/healthy` with exact IDs. No container identifier is retained in this review.
9. Neither reviewer found a source or reference to the permanently noncanonical r27 diagnostic
   candidate/capsule.

```text
candidate_sha256=1b654214d23c60448d88d14fd7f2f5e8e7dd08636721f10bf735c5db08ea39d5
aggregate_line=54624/76830 aggregate_branch=26112/44870 aggregate_complexity=17659/35571
critical_rows=12 positive_metrics=23 structural_n_a=1 below_floor_classes=0
required=773+59/5707 addon=2/6 unit=681+55/4941 integration=47+4/320
exec_sessions=23/48 class_universe=24/2098
capsule_entries=6638 deterministic_rebuild=passed materialize=passed self_test=8/8
reviewer_a=APPROVE reviewer_b=APPROVE findings=0/0/0/0
```

## Reviewed thresholds

| Critical class | Line reviewed | Branch reviewed | Applicability |
|---|---:|---:|---|
| `CatalogSnapshotStore` | `202/214` | `83/104` | required / required |
| `ModelBuildSingleFlight` | `79/89` | `31/44` | required / required |
| `CatalogRefreshCoordinator` | `253/282` | `95/134` | required / required |
| `DatasourceCatalogConvergence` | `51/52` | `20/26` | required / required |
| `NamespaceScope` | `5/5` | `0/0` | required / structural N/A |
| `CommittedSourceRevisionRegistry` | `53/55` | `19/24` | required / required |
| `RuntimeNamedDataSourceResolver` | `145/166` | `66/94` | required / required |
| `WatchServiceFileTracer` | `204/244` | `99/128` | required / required |
| `QueryFingerprintBuilder` | `159/179` | `65/84` | required / required |
| `SecurityPolicyFingerprint` | `112/117` | `73/92` | required / required |
| `CaffeineQueryCacheProvider` | `126/135` | `64/78` | required / required |
| `RedisQueryCacheProvider` | `152/159` | `73/88` | required / required |

Aggregate reviewed thresholds are frozen exactly as line=`54,624/76,830` and branch=
`26,112/44,870`; float derivation, denominator rescaling, exclusions or partial evidence may not
rewrite them.

## Canonical freeze fields

Cfreeze writes the following review binding to `coverage-thresholds.json`:

```yaml
reviewer: Codex /root+Reviewer A+Reviewer B
reviewed_at: 2026-07-19T10:41:12Z
diagnostic_run_id: step4-coverage-20260719-diagnostic-r28
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r28-threshold-review-20260719.md
evidence_sha256: <actual SHA-256 of this finalized file>
decision: confirm-observed-thresholds
```

This file intentionally does not record the final confirmed threshold SHA or Cfreeze commit SHA,
avoiding a review/threshold hash cycle.

## Findings and boundary

- Reviewer A `/root/r26_candidate_review`: `APPROVE`.
- Reviewer B `/root/r28_capsule_review`: `APPROVE`.
- Blocker/High/Medium/Low=`0/0/0/0`; mandatory actions=`0`.
- Candidate remains immutable `review-required`; canonical thresholds carry the confirmed authority.
- Cfreeze must be the only direct-single-parent child of Cdiag `507e1732...`.
- Machine delta is exactly the six Step 4/Step 6 formalization paths; every other path is under
  `docs/9.3.4/**`.
- Fresh formal-r9 remains mandatory. This review is not formal, coverage audit, Step 4 feature
  acceptance, 9.3.4 version signoff, or authorization to start 9.3.5.
