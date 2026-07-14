---
doc_role: batch_exit_evidence
doc_purpose: Close Batch 1 contract/failure-baseline work and map every remaining product criterion to its owning implementation batch.
version: 9.3.3
batch: 1
step: 7
status: completed
recorded_at: 2026-07-13
next_batch: 2
---

# Batch 1 Step 7: Exit and Batch 2 Readiness

## Exit decision

Batch 1 is **completed** and Batch 2 (`NamespaceScope`) is **ready to start**.
This is a contract/failure-baseline exit, not feature acceptance: every product
criterion below remains pending until its normal green implementation evidence
exists. Expected-red is never counted as a passed product test.

The exit satisfies the Batch 1 rule of “direct red or explicit source proof”:

- all observable contracts, exact Runtime DTO/error fields and binding/MCP
  generation strategies are frozen with no pending decision;
- directly injectable current defects have deterministic assertion-red tests;
- contracts whose real port does not exist yet have precise source proof,
  owning implementation batch and mandatory green-transition tests;
- no test uses sleep to decide an interleaving;
- all red classes are explicit-only and normal Gate 0 remains green.

## Authoritative expected-red replay

- command: `scripts/verify-v933-batch1-red-baselines.sh`
- run: `20260713T115746Z-1058249`
- result: 10 owning suites, 12 tests, 12 expected assertion failures,
  0 errors, 0 skipped.
- isolation: each case uses a unique run directory and fresh marker; the checker
  requires exactly one owning XML, every selected case failed, no errors/skips,
  and all scenario-specific failure patterns are present.
- default discovery: every class ends in `RedBaseline`; it matches neither
  default Surefire/Failsafe patterns nor the lifecycle profile's `*Test.java`
  include.
- sensitive-pattern scan over replay Maven/XML artifacts: 0 matches.

| Step | Case | Tests/Failures/Errors/Skipped | Baseline result |
|---:|---|---:|---|
| 1 | Runtime additive DTO/error shape | 1/1/0/0 | expected-red |
| 2 | production namespace restoration | 1/1/0/0 | expected-red |
| 2 | joined-QM completeness | 1/1/0/0 | expected-red |
| 3 | distinct-key builder overlap | 1/1/0/0 | expected-red |
| 3 | deterministic alias map | 1/1/0/0 | expected-red |
| 4 | Runtime binding generation/revoke handle | 2/2/0/0 | expected-red |
| 5 | Runtime refresh/validate isolation | 2/2/0/0 | expected-red |
| 5 | bundle source commit ordering | 1/1/0/0 | expected-red |
| 5 | file-event namespace scope | 1/1/0/0 | expected-red |
| 6 | single model/MCP catalog authority | 1/1/0/0 | expected-red |

Evidence identities:

- `summary.tsv` SHA-256:
  `e7da49508d2c2c332b3fa05e66ebdc9f974541a1eac0d8ce24510190366a6f1d`.
- `sha256sum.txt` SHA-256:
  `7bff42b482ac8beb7ac189ca442e677cbb1f3c97b5fb6d8a9eff90efa87a6130`.
- case logs/XML and their individual hashes are under
  `target/v933-batch1-red/runs/20260713T115746Z-1058249/`.
- aborted harness-development runs `20260713T115450Z-1048649` and
  `20260713T115536Z-1051172` are explicitly excluded from evidence.

## Gate 0 replay after all Batch 1 test/script changes

- command: `scripts/verify-v933-entry-gate.sh`
- run: `20260713T120110Z-1066109`
- result: passed.
- positive: deterministic unit 1, deterministic IT 1, SQLite preflight 1,
  MySQL 5.7 preflight 1, PostgreSQL 15 preflight 1; all 5 green with
  failures/errors/skips = 0.
- expected-negative runner cases: missing unit, missing IT, wrong required DB
  and missing owning report all failed closed (4/4).
- `summary.env` SHA-256:
  `d4385174f1f8a773252367ea2abccc269362094a49f55daad50ce8668ef9ad08`.
- `SHA256SUMS` SHA-256:
  `5b88d21d431e2650b45a68b1496ecd86920a3a110d4ba79bca5fffd7819b18b6`.
- sensitive-pattern scan over Gate Maven/XML artifacts: 0 matches.
- no remote GitHub Actions result is claimed.

## Critical criterion handoff matrix

`baseline captured` below means only that the current defect/absence is proven;
the acceptance status remains pending.

| ID | Batch 1 proof | Owning implementation batch and mandatory green evidence |
|---|---|---|
| PRE-934A | latest Gate 0 replay passed | retained on every batch; 9.3.4 full remains separate |
| NS-SCOPE | production-entry direct red; current context has no scope/token | Batch 2: nested named/default, exception/early return, wrong-thread, out-of-order, double-close and thread-pool reuse |
| SNAPSHOT | mutable loader maps/global catalog source proof | Batch 3: immutable defensive-copy snapshot; TM/QM/synthetic/alias/discovery/provenance same identity |
| GENERATION | DTO shape red; no generation type/source reference exists | Batch 3: read/failure unchanged; observable materialization/refresh exactly +1; two namespaces isolated |
| DS-GENERATION | Runtime wrapper direct red; Runtime/MCP source proof; current MCP logs expose reversible host/database/user identity | Batch 3: persisted Runtime epoch, cold MCP boot epoch, pinned old/new physical sentinels and log/diagnostic redaction audit |
| BINDING-REVOKE | removed old-handle direct red; no lease/admission state source proof | Batch 3: DRAIN/deadline/HARD/acquire-race controlled clock plus real DB IT |
| SF-SAME | manager monitor can make build=1 falsely green; no flight future source proof | Batch 4: 100 callers attach before release, build=1, same committed identity, in-flight=0 |
| SF-ISOLATION | distinct-key overlap direct red | Batch 4: model/namespace/generation/binding keys actually overlap |
| SF-FAIL | no shared failure/future source proof | Batch 4: all waiters share winner error; flight removed; next winner retries and publishes once |
| REFRESH-ATOMIC | clear-first direct red/source proof; no active snapshot port | Batch 5: blocked candidate readers see complete old then complete new only |
| REFRESH-FAIL | failed refresh invokes all live clears | Batch 5: failed candidate publish=0, old generation and native-query result remain valid when bindings valid |
| REFRESH-SCOPE | file event global-clear direct red | Batch 5: namespace A/model X changes once; namespace B/siblings unchanged |
| EVENT-CONVERGENCE | Runtime, bundle and file forbidden paths directly red | Batch 5: all event adapters call one coordinator; no production clear-first |
| SOURCE-COMMIT | bundle event pre-commit direct red; SourceRevision absent | Batch 5: committed revision ordering, stale candidate publish=0, unknown scope admission-blocked |
| QM-COMPLETE | joined TM failure returns partial builder result | Batch 3: full candidate fails and failed QM is never registered/discoverable |
| VALIDATE-ISOLATION | live bundle add/remove both directly red | Batch 5: valid/invalid detached validate leaves source/generation/names/alias identical |
| CATALOG-AUTHORITY | model/MCP names views split after one event; both caches global | Batch 6: both consumers return same per-namespace snapshot/catalog identity; no second names cache |
| CACHE-GEN | source proof: cache key says catalog generation unavailable and uses process-local instance UUID; Pivot identity only bundle/token | Batch 6: L1/L2/Redis/Pivot include actual catalog + dependency binding generations; missing/conflict is no-read/no-write |
| CACHE-CROSS-JVM | boot-epoch cold policy frozen; no catalog epoch implementation | Batch 6: two JVMs/restart share Redis, old epoch hits=0, no object address/instance hash key |
| REAL-QUERY | no lifecycle port exists; old/new sentinel inventory frozen | Batch 6: old/new, dual namespace and dual datasource QueryFacade rows equal corresponding native SQL and physical identity |
| API-COMPAT | exact reflection DTO red; `NON_NULL` and current legacy HTTP/code source proof | Batch 5/7: legacy consumer JSON, absent/null, opaque round-trip, sanitized failure/diagnostics and Controller HTTP/code tests |
| REGRESSION | Gate 0 only proves entry harness remains isolated | Batch 7: actual 9.3.1 fail-closed and 9.3.2 auto-config/Launcher regression suites |
| POST-GATES | source/baseline docs complete; not a formal quality gate | Batch 7: implementation self-check → formal quality gate → coverage audit → version acceptance |

Observable lazy enrichment is owned by SNAPSHOT/GENERATION: its Batch 3 test
must prove an observable publication advances exactly one generation while a
plain read/cache hit does not. Pivot and independent-JVM evidence are explicitly
owned by Batch 6 and cannot be inferred from the current source proof.

## Execution check-in

- completed work: exact contract freeze; 10 explicit red suites; deterministic
  red-report checker/runner; source-only gap mapping; Gate 0 post-change replay.
- touched production code: none in Batch 1.
- touched test paths: model lifecycle red fixtures, Runtime DTO/lifecycle/binding
  fixtures, fsscript source-order fixture and MCP catalog-authority fixture.
- touched scripts: `assert-v933-red-report.sh`,
  `verify-v933-batch1-red-baselines.sh`.
- touched docs: contract, progress/README/plan and Batch 1 Step 1–7 evidence.
- protected baseline: no reset/checkout/clean/stash; pre-existing 9.3.1/9.3.2
  dirty worktree remains protected by the Gate 0 baseline manifest.
- self-check: explicit red is separated from green Gate; all selected cases are
  assertion failures with zero errors/skips; waits/resources are bounded; no
  sleep-driven interleaving; no production lifecycle implementation leaked into
  Batch 1; product criteria remain pending.
- obvious risks/follow-ups: source-only contracts need their stated green tests
  immediately after each real port lands; local target evidence is not remote CI
  or release-immutable evidence; full 9.3.4 matrix/coverage is still deferred.
- self-check decision: `self-check-only` for contract/test-baseline work; no
  implementation quality blocker found.
- needs-formal-quality-gate: yes, after production implementation in Batch 7;
  not run or claimed early for this no-production-code Batch 1.

## Next execution point

Start Batch 2 only: implement `NamespaceScope` and migrate production namespace
entry points, then make the Step 2 namespace baseline a normal green regression
with the full advanced-scope matrix above. Do not start CatalogSnapshot or
binding production work until Batch 2's NS-SCOPE exit is green.

## Subsequent handoff update (2026-07-13)

Batch 2 subsequently completed with `NS-SCOPE=passed`; authoritative evidence is
`../batch-2/namespace-scope-exit-20260713.md`. This Batch 1 record and run
`20260713T115746Z-1058249` remain immutable repair-before evidence. Because the
old Namespace red source was promoted to a normal-green test and removed, the
Batch 1 runner is historical and is not claimed to be replayable post-fix.
