---
doc_role: independent_review
doc_purpose: Record independent review and resolution of the 9.3.4 Step 3 five-database diagnostic foundation.
version: 9.3.4
step: 3
status: pass-with-followups
authority_status: not-authority
created_at: 2026-07-15
---

# Step 3 Five-DB Foundation Independent Review

## Decision

`pass-with-followups` for committing the diagnostic foundation. This review does not pass Step 3,
does not approve the database matrix runner, and does not change 9.3.4 acceptance state.

## Reviewers

- initial identity/fixture reviews：`v934_r6_identity_review`、`v934_snapshot_skip`；
- final code/docs/script reviews：`v934_final_code_review`、`v934_final_docs_review`、
  `v934_script_negative_review`。

All reviews were read-only. Findings were resolved by the owning root session and then replayed.

## Blocking Findings and Resolution

1. v933 QueryFacade probe/count regression and global fixture pollution.
   - resolution：explicit v933/v934 modes；v934-only SQLite profile and external fixture directory；
     shared DB rows restored to `110317/17384/5940` with v934 row count `0`.
   - proof：v933-only preflight `3/F0/E0/S0` and replacement real-query replay
     `11 tests / 6 reports / F0/E0/S0`.
2. historical runner immutability conflict.
   - resolution：historical `verify-v933-batch6-real-query.sh` restored byte-identical at SHA-256
     `0f560482112cd1241c54b279198d38130c59f76e100fa32fecfeb61be50ae403`；new v934 wrapper
     owns the upstream selector bridge.
   - proof：wrapper run `v934-step3-compat-r5-20260715` passed with frozen `8/2,25/25,25/25`
     probes.
3. identity overstatement and SQL Server URL ambiguity.
   - resolution：exact MySQL 5.7 product version；exact SQLite engine/driver/shared-cache URL；
     SQL Server explicit `databaseName` equal to active catalog；OCI/JAR byte proof remains assigned
     to the final runner and is not claimed by Java alone.
4. PostgreSQL schema drift.
   - resolution：fixture SQL fully qualifies `public`；manager requires
     `current_database|current_schema=foggy_test|public` before apply/clean.
5. raw evidence ambiguity and missing indexes.
   - resolution：diagnostic raw paths and ephemeral status are explicit；README/acceptance index
     link the Step 3 diagnostic as `not-authority`.

## Verification Reviewed

- Java test-compile：passed；
- v934 five-DB preflight + QueryFacade/native parity：`10/F0/E0/S0`；
- diagnostic negatives：`3/3` rejected with `1/F1/E0/S0` each；
- v933 compatibility：preflight `3/F0/E0/S0` + real-query `11/6/F0/E0/S0`；
- fixture manager：four external DB apply/verify/clean passed after schema/version tightening；
- static：`bash -n`、compose merged config、source manifest check、`git diff --check` passed.

## Open Follow-ups Blocking Step 3 Exit

- final runner must load the digest override, use fresh/run-scoped storage, record resolved compose
  identity and own cleanup/rollback traps；
- SQLite JAR byte SHA and all external image IDs must be archived by the runner；
- Pivot preagg invalid physical columns/string-only pseudo-green and heterogeneous/missing preagg
  fixtures must be repaired before assumption removal can count；
- remaining 19 database executions, 16 required external executions and full negative set remain open；
- formal implementation quality gate runs only after the complete Step 3 scope is implemented.
