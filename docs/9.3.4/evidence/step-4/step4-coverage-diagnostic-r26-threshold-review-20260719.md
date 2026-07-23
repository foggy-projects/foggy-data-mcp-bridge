---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r26
tested_commit: 4fe86929de6206aa3e514c974635e90395c28b2e
candidate_sha256: b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a
reviewer: "Codex /root+Reviewer A+Reviewer B"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/r26_candidate_review (Reviewer A)"
  - "/root/r26_capsule_review (Reviewer B)"
reviewed_at: 2026-07-19T04:39:04Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r26 threshold review

## Decision

确认 `step4-coverage-20260719-diagnostic-r26` 的 exact observed counters 可作为 Step 4
reviewed thresholds，decision=`confirm-observed-thresholds`。Reviewer A 独立复算 candidate、
所有 aggregate/critical counter、report/exec/class universe、Unit MySQL schema 2 与 isolated
focused proof；Reviewer B 独立复算 repository/source identity、outer restore receipt，并在自己的
临时目录重新构建两份 portable capsule、逐字节比较和 materialize。

本记录不声称用户或 release owner 已人工签收，`user_confirmation=not-asserted`。它只授权把
candidate exact projection 写入 Cdiag `4fe86929...` 的唯一 direct-single-parent Cfreeze。Fresh
formal-r8、post-formal quality、coverage audit、Step 4 feature acceptance 和 9.3.4 version signoff
均不在本次确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260719-diagnostic-r26` |
| diagnostic Git HEAD | `4fe86929de6206aa3e514c974635e90395c28b2e` |
| diagnostic source SHA-256 | `6acfad24cc3d43c3bf550c904aa61c7e01f5b7829d4e2f204d489ab6cc40a8f5` |
| run status SHA-256 | `09499e0e0d702e7e122b6225126e1a044b3d047881df9428aaa845a6819abd9b` |
| summary SHA-256 | `2613edc69c764083a73705385242e411e115a2197295bc2e6a4dfc1d2eb13918` |
| observation SHA-256 | `15e1ed76eaa624c0899b980472689e34e1b272ddda58b2bc5cf27994abffe705` |
| diagnostic contract SHA-256 | `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b` |
| candidate SHA-256 | `b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a` |
| capsule archive SHA-256 / size | `29313c2b0fb88c248b9f8c1e6085bd3114f92e74bc0946ca32c33db4b74e61c2 / 20,014,615` |
| capsule manifest SHA-256 | `68bb1740548c323de89819d6b17d634013235665f37edba5fba56accf8db405e` |
| isolated proof status SHA-256 | `391521f816d78cce98110a2063238e2400fabe0d1aec81dfe21dfabb498a0991` |

## Independent recomputation

1. Candidate public verification and both independent reviews preserved candidate SHA-256
   `b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a`.
2. Aggregate counters match raw XML and observation exactly: instruction=`252725/352456`, line=
   `54624/76830`, branch=`26112/44870`, complexity=`17659/35571`, method=`9068/12701`,
   class=`1503/1716`.
3. All `24` groups match raw XML for every counter. The `12` critical rows match raw XML,
   observation and candidate; positive metrics=`23`, structural N/A=`1`, below-floor=`0`.
4. Report authority is Unit=`681+55/4941/F0E0S0`, Integration=`47+4/320/F0E0S0`, database=
   `29/370/F0E0S0`, external=`16/76/F0E0S0`, required=`773+59/5707/F0E0S0`, Addon=
   `2/6/F0E0S0` and excluded from the required union.
5. Exec/session=`23/48`; production universe=`24 modules / 2,098 classes`; class tree binding is exact.
6. Unit MySQL fixture schema 2 preserves historical observed=`6 reports / 11 nodes` while current
   reviewed known lower bound=`7 reports / 12 nodes`; negative=`42/42`, lifecycle=`5/5`.
7. Fresh-clone isolated r4 proof ran the same FQCN/method against one digest-pinned random-port
   MySQL: positive Maven=`0`, XML=`1/F0E0S0`; wrong-password Maven=`1`, XML=`1/F0E1S0`.
   Its exact CID/name are absent and random port released after cleanup.
8. Source before/after is byte-identical. Public `validate-diagnostic-run` and
   `verify-threshold-candidate` both returned success.
9. Reviewer B's two independent capsule rebuilds are mutually and canonically byte-identical;
   manifest=`sealed / 6,638 entries / 5,884 files / 754 directories / 0 symlink / 0 special`.
10. Fresh empty-directory materialize closed all `6,638` entries; self-test=`8/8`. Outer wrapper
    runner/restore/receipt/outcome=`0/0/0/0`, and four original demo container IDs were restored
    `running/healthy`.

```text
candidate_sha256=b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a
aggregate_line=54624/76830 aggregate_branch=26112/44870 exact_projection=passed
critical_rows=12 positive_metrics=23 structural_n_a=1 below_floor_classes=0
required=773+59/5707 addon=2/6 unit=681+55/4941
fixture_historical=6/11 fixture_current_lower_bound=7/12 negative=42/42 lifecycle=5/5
isolated_positive=rc0+1/F0E0S0 isolated_wrong_password=rc1+1/F0E1S0
capsule_entries=6638 deterministic_build=passed materialize=passed self_test=8/8
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
reviewed_at: 2026-07-19T04:39:04Z
diagnostic_run_id: step4-coverage-20260719-diagnostic-r26
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r26-threshold-review-20260719.md
evidence_sha256: <actual SHA-256 of this finalized file>
decision: confirm-observed-thresholds
```

This file intentionally does not record the final confirmed threshold SHA or Cfreeze commit SHA,
avoiding a review/threshold hash cycle.

## Findings and boundary

- Reviewer A `/root/r26_candidate_review`: `APPROVE`.
- Reviewer B `/root/r26_capsule_review`: `APPROVE`.
- Blocker/High/Medium/Low=`0/0/0/0`; mandatory actions=`0`.
- Candidate remains immutable `review-required`; canonical thresholds carry the confirmed authority.
- Cfreeze must be the only direct-single-parent child of Cdiag `4fe86929...`.
- Machine delta is exactly the six Step 4/Step 6 formalization paths; every other path is under
  `docs/9.3.4/**`.
- Fresh formal-r8 remains mandatory. This review is not formal, coverage audit, Step 4 feature
  acceptance, 9.3.4 version signoff, or authorization to start 9.3.5.
