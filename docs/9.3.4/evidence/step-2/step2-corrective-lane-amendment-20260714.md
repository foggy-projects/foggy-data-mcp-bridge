---
doc_role: step-evidence
doc_purpose: Record the reviewed Step 2 lane correction and the superseding r3 successor contract.
version: 9.3.4
step: 2
status: reviewed
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Step 2 corrective lane amendment

## Trigger

The first all-reactor unit authority attempt, `step2-unit-r1-20260714`, reached
`addons/foggy-dataset-model-mongo` with 66 tests and failed with 14 errors. The two
owners were frozen by r2 as Surefire unit tests, but both extend `MongoTestSupport`,
start a real Boot context with profile `docker`, probe `localhost:17017`, and execute
real `MongoTemplate` writes, aggregates and query-model comparisons.

The same failure exposed an independent Boot ordering defect: the Mongo model
auto-configuration evaluated its fail-closed `DataSource` condition before
`DataSourceAutoConfiguration` registered the bean. The production fix keeps the
`DataSource` condition and adds only the missing `afterName` ordering edge. The
existing six-node auto-configuration contract was strengthened in place and proved
RED `1/1/0/0`, then GREEN `1/0/0/0`; the full contract is `6/0/0/0`.

## Reviewed correction

`scripts/v934/step2-corrective-rename-plan.tsv`, SHA-256
`8c9c73c801efa786a71f07541a1003f456405525aeb7ae7583b2b938445ded09`, contains
exactly two corrections:

| Frozen owner | Corrected owner |
|---|---|
| `McpAuditLogMongoTest` / Surefire unit / Step 2 | `McpAuditLogMongoIT` / Failsafe external-mongo / mongo6 / Step 3 required |
| `MongoArrayElementAccessTest` / Surefire unit / Step 2 | `MongoArrayElementAccessIT` / Failsafe external-mongo / mongo6 / Step 3 required |

Neither execution owns a predecessor edge. Step 1 remains immutable; the corrective
plan is an additional hash-sealed parent of the r3 successor.

The production fix changes one reactor classpath content hash without changing its
identity or order. Across 2,395 entries, the only reviewed delta is
`foggy-mcp-launcher` ordinal `150`,
`repo:addons/foggy-dataset-model-mongo/target/classes`, from
`af9a2d3036ef831a70a0a74a11aade3e3651afdb17e5984877835d31570866db` to
`302eb382337c9daf6c91981b6373b53648afe81470d598fb0cc817837fcf292d`.
All other classpath identities and hashes remain exact.

## Supersession and resulting contract

r2 remains a confirmed historical candidate but is superseded because it classified
the two live Mongo executions as hermetic unit tests. The r3 wrapper first verified
and archived r2 using these immutable digests:

- freeze: `527712b74be3d662b09b35998435610ebfdd5ac752d4f5bcef6afabf78ddc329`;
- manifest: `e2eb6ea351ae97eaa3daf6e36d35c6076717f602cdf7cc7ecf283446f3bbe11e`;
- confirmed summary: `359788a00c6bafa3a766102a012d50229ca11b4e535de03ebe2b75e07cd2c089`.

r3 preserves 532 sources, 820 discovery rows, 59 structural reports, 770 positive
executions and 519 predecessor edges. It changes the execution split to:

- Step 2 required: 724 = 677 Surefire + 47 Failsafe;
- Step 3 deferred: 46 = 46 Failsafe;
- total ownership: 677 Surefire + 93 Failsafe;
- composed rename: 35 sources / 64 reports / 76 keys; positive 60 reports / 72
  keys plus 4 structural reports.

Fresh focused verification is `48/0/0/0` for the Mongo module unit set and
`18/0/0/0` for the two external Mongo ITs. These focused external results diagnose
the fix; their required release ownership remains Step 3.
