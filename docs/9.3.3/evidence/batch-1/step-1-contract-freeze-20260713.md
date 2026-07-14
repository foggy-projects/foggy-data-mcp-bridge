---
doc_role: contract_decision_evidence
doc_purpose: Record the exact Batch 1 lifecycle/Runtime/binding freeze and its additive DTO pre-fix baseline.
version: 9.3.3
batch: 1
step: 1
status: completed
recorded_at: 2026-07-13
---

# Batch 1 Step 1: Frozen Contract Evidence

## Outcome

`contract/model-lifecycle-concurrency-contract.md` is `status=confirmed` and
has no pending decision. The freeze includes:

- model authority package/type boundary without prematurely creating 9.4.0
  modules or SPI v2;
- unset/default/named namespace state and exact `open/openInherited` semantics;
- boot-epoch catalog/source identity, Runtime persisted binding epoch and MCP
  boot-epoch cold binding identity;
- exact Runtime success DTO additions, nine lifecycle codes and typed failure
  context while preserving HTTP 200, legacy `error.code` and failure `data=null`;
- `OPEN/RETIRING/REVOKED/CLOSED`, default `DRAIN`, explicit `HARD`, and
  `lease-drain-timeout-ms=60000` with fail-fast 1000..300000 range;
- bounded diagnostic/target counts and redaction rules.

The first review draft had two ambiguities that were corrected before exit:

1. generation/state/failed targets on failure could not live in null `data`, so
   exact `RuntimeLifecycleFailureContext` and diagnostic components were added
   under `RuntimeError.lifecycle`.
2. MCP config persistence did not define its generation domain, so it now uses
   a non-reused process boot UUID + sequence, intentionally cold on restart,
   while Runtime retains its persisted registry epoch + sequence.

## Additive DTO expected-red baseline

`RuntimeLifecycleDtoContractRedBaseline` uses reflection so the baseline itself
compiles before the new DTO types exist. It freezes enum values, every record
component/type/order and all three legacy constructors. Missing types/linkage
are converted to descriptive assertion failures, not test errors.

Initial explicit run:

```bash
mvn -B -pl foggy-runtime-api \
  -Dtest=com.foggyframework.runtime.api.dto.RuntimeLifecycleDtoContractRedBaseline \
  test
```

Result: tests=1, failures=1, errors=0, skipped=0. The one JUnit case contains
eight grouped contract failures: five missing frozen types and three additive
record-shape mismatches; legacy constructors still pass on current code.

- initial XML SHA-256:
  `75cd588ecff9c35ff2e25e15968d8d506963ad6e76675c437c25f537c478a2c7`.
- Step 7 replayed it in marker-bound run `20260713T115746Z-1058249`; the
  module default report path is not treated as final Batch 1 evidence.

## Boundary

No production lifecycle/DTO/pool file changed in Step 1. The reflection test is
expected red until the additive implementation lands; it must then be promoted
to a normal Runtime compatibility regression rather than deleted or weakened.
