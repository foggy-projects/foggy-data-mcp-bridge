---
doc_role: expected_red_evidence
doc_purpose: Capture pre-fix Runtime datasource generation/revocation failures and the missing lease-state source proof.
version: 9.3.3
batch: 1
step: 4
status: completed-expected-red-and-source-proof
recorded_at: 2026-07-13
---

# Batch 1 Step 4: Datasource Binding Expected-Red Evidence

## Decision

Step 4 is complete for Batch 1 baseline purposes: two behavioral assertions
prove the mutable-handle/revocation defects, while the absent lease/admission
port is recorded as an explicit source proof with mandatory green-transition
tests. This does not claim DS-GENERATION or BINDING-REVOKE is implemented.

## Behavioral result

Class:
`com.foggyframework.runtime.api.service.RuntimeDatasourceBindingRedBaseline`

| Scenario | Current observation | Result |
|---|---|---|
| A→B fingerprint change | `resolve(changed)` returns the same `ManagedRuntimeDataSource` wrapper that previously represented A | expected-red |
| remove then old-handle borrow | removed slot's old wrapper calls `ensurePool()` and successfully recreates/borrows | expected-red |

Owning XML: tests=2, failures=2, errors=0, skipped=0. The fixture uses fake
pools/connections, closes any unexpected connection and destroys the manager in
`finally`; no real credential, network endpoint, thread or sleep is involved.

Explicit command:

```bash
mvn -B -pl foggy-runtime-api -P'!multi-db' \
  -DskipITs=true \
  -Dtest=com.foggyframework.runtime.api.service.RuntimeDatasourceBindingRedBaseline \
  test
```

Local artifacts from the capture run:

| Artifact | SHA-256 |
|---|---|
| `target/v933-red-baseline/runtime-datasource-binding-20260713T1934/maven.log` | `b2b697c5c8ea8c99b184d7a13097236f3805d6c2cee44820cc41ff08a6ee593e` |
| `foggy-runtime-api/target/surefire-reports/...RuntimeDatasourceBindingRedBaseline.xml` | `ecebb95c56583c98a7efc9bcfe783a914d8b3b21dd6ffd211d3a74048ad32cef` |

Step 7 replayed this class in marker-bound run `20260713T115746Z-1058249` for
the Batch 1 summary; the fixed default report path above is not by itself
authoritative evidence.

## Lease/admission source proof

- `RuntimeDatasourceRecord` currently has no registry epoch, binding generation
  or admission state; `RegistryFile` remains version 1 (current
  `RuntimeDatasourceRegistryService` lines 340–363).
- `resolve` indexes slots only by logical name and returns the slot's one final
  wrapper (current `ManagedDataSourcePoolManager` lines 92–102, 335–365).
- `onRecordSaved` and `remove` immediately call `closePool` (current lines
  105–121). `closePool` closes regardless of active connection/lease count
  (current lines 410–425); only unrelated idle cleanup checks activity.
- `ManagedRuntimeDataSource` retains only a mutable `PoolSlot` reference and
  delegates every later borrow to it (current lines 506–522). There is no
  `OPEN/RETIRING/REVOKED/CLOSED` admission state, no generation-pinned lease,
  no drain deadline and no HARD path.
- current pool properties contain connection/idle/lifetime settings only; the
  frozen `lease-drain-timeout-ms` property does not yet exist.
- MCP `DataSourceManager` currently replaces `dataSources/configs` map entries
  without generation, lease/admission state or old-pool close (current lines
  50–62, 97–120, 212–216). Its `DataSourceFactoryImpl` cache key uses URL +
  username only (current lines 49–68), so credential/pool-policy change can
  accidentally reuse an old instance. The frozen MCP adapter uses a new boot
  UUID + sequence domain and the same lease state machine; it never derives
  identity from those sensitive configuration values.
- MCP configure/factory logging currently emits host/port/database and username
  plus a masked URL (current `DataSourceManager` lines 104–105 and
  `DataSourceFactoryImpl` lines 54/67). Batch 3 must remove reversible physical
  identity from logs/evidence, not merely avoid the password.

Because the lease type/state machine does not exist, a compiling deterministic
deadline test cannot precede that port without creating a fake implementation
that tests itself. Batch 3 must introduce the port and immediately add these
controlled-clock green tests:

1. A→B: held old lease stays on A; new lease goes to B; old handle never sees B.
2. remove/disable commit: old generation rejects new borrow immediately and
   cannot recreate a pool.
3. DRAIN: held lease may finish before 60 seconds; close exactly once after last
   release.
4. deadline: advance fake clock/scheduler to 60 seconds; force revoke/close;
   no retry/reacquire.
5. HARD: active lease is closed immediately.
6. acquire-vs-commit barrier has one linearization result; old release never
   decrements the new generation's active count.
7. registry migration/restart/remove-recreate never reuses a generation.
8. MCP configure/reconfigure/remove/re-add closes or drains the actual old
   pool, publishes a new boot-epoch generation and emits affected-catalog
   notification; restart assigns a fresh cold identity in sorted name order.
9. adapter/controller/factory logs and failure diagnostics contain no host,
   port, database/catalog, username, URL, credential or pool instance identity.

These are required adapter tests, followed by an old/new physical sentinel IT;
the source proof is not a waiver.
