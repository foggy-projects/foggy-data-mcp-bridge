---
doc_role: workitem
doc_purpose: Plan dialect-specific evidence for Pivot cascade and large-domain transport in 9.2.0.
version: 9.2.0
target: Java Pivot dialect cascade evidence
status: sqlserver-promotion-gate-env-gated
created_at: 2026-06-12
updated_at: 2026-06-18
---

# Pivot Dialect Cascade Evidence Plan

## Purpose

`PIVOT-92-F1` and `PIVOT-92-F2` keep dialect behavior explicit:

- SQL Server cascade support must not be enabled without live parity evidence.
- MySQL 5.7 must remain fail-closed for C2 cascade while continuing to provide large-domain transport evidence.

## Current State

| Dialect | Current Evidence | Boundary |
|---|---|---|
| SQLite | Local C2 cascade and Pivot integration parity tests pass. | Acts as the lightweight baseline, not as proof for other dialect renderers. |
| MySQL 5.7 | Live large-domain axis-domain transport and cascade fail-closed refusal evidence exist on the local docker profile. | No C2 cascade enablement because the dialect has no window-function path for the accepted implementation. |
| SQL Server | `application-sqlserver.yml`, compose service wiring, SQL Server CTE domain renderer, and no-service renderer/capability unit evidence exist. | Live service evidence is unavailable in this shell, so SQL Server cascade oracle and transport execution remain blocked by environment. |

## Release Gate Boundary

2026-06-18 decision:

- SQL Server is not part of the default 9.2 Java engine release blocker unless the release explicitly claims SQL Server prepared-service parity or SQL Server Pivot C2 cascade support.
- The current default Java engine evidence can rely on SQLite targeted checks plus prepared MySQL and PostgreSQL full gates.
- SQL Server remains a promotion gate for `PIVOT-92-F1`: no SQL Server cascade support, no SQL Server full prepared-service claim, and no SQL Server transport execution claim can be signed until live SQL Server evidence passes.
- Missing SQL Server service is therefore an environment-gated capability gap, not a regression in the already claimed default MySQL/PostgreSQL/SQLite engine boundary.
- Memory fallback or renderer-only evidence must not be used as a substitute for live SQL Server cascade parity.

## SQL Server Plan

SQL Server acceptance requires a reachable prepared service and live test execution:

1. Start or provision the SQL Server service expected by `application-sqlserver.yml`.
2. Confirm connectivity and schema bootstrap before running Pivot tests.
3. Run the C2 cascade SQL parity subset using the SQL Server profile.
4. Verify that SQL Server uses the intended SQL path and does not fall back to memory shaping for accepted C2 shapes.
5. Verify the SQL Server CTE domain transport renderer boundaries, including parameter limits and refusal behavior.
6. Compare result rows and ordering against the SQLite baseline fixtures.
7. Record logs and response diagnostics for pushdown, transport, and execution-path evidence.

SQL Server C2 cascade remains refused or unclaimed until these steps pass.

## MySQL 5.7 Plan

MySQL 5.7 is a fail-closed dialect for accepted C2 cascade in 9.2.0:

1. Keep the existing cascade refusal test as live evidence.
2. Keep large-domain axis-domain transport tests as positive evidence for non-cascade Pivot paths.
3. Keep renderer threshold tests for derived-table transport planning and refusal.
4. Add response/log consumption checks only after the shared diagnostics contract is stable.
5. Do not add memory fallback for cascade as a substitute for SQL parity.

MySQL 5.7 can be accepted as "large-domain transport supported, cascade refused", not as "cascade supported".

## Commands

| Purpose | Command | Status |
|---|---|---|
| SQLite Pivot baseline | `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=PivotIntegrationTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | available locally |
| MySQL 5.7 large-domain and cascade refusal | `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=docker -Dtest='PivotSqlParityIntegrationTest+PivotCascadeGenerateSqlParityIntegrationTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | available when local MySQL 5.7 docker service is running |
| SQL Server cascade parity | `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlserver -Dtest=PivotCascadeGenerateSqlParityIntegrationTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | env-gated |
| SQL Server full Pivot smoke | `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlserver -Dtest='PivotIntegrationTest+PivotSqlParityIntegrationTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | env-gated |
| SQL Server no-service renderer/capability subset | `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -Dtest='DomainRelationRendererTest#testSqlServerCteRenderer_TupleField,RelationModelTest#sqlServerWithCte' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | pass on 2026-06-18 |

## 2026-06-18 Local Environment Evidence

| Check | Result |
|---|---|
| `docker ps --format '{{.Names}} {{.Image}} {{.Ports}}'` | MySQL 8 and PostgreSQL containers are running; no `foggy-demo-sqlserver` container is running. |
| `docker images --format '{{.Repository}}:{{.Tag}}' \| rg 'mcr.microsoft.com/mssql\|postgres\|mysql\|redis'` | Local images include `mysql:8.0`, `postgres:15-alpine`, and `redis:7-alpine`; no `mcr.microsoft.com/mssql/server` image is present. |
| `command -v sqlcmd` | No local `sqlcmd` client is available. |
| `nc -z 127.0.0.1 11433`; `nc -z 127.0.0.1 1433` | Both probes return exit code `1`; SQL Server is not reachable on the configured test ports. |
| No-service SQL Server renderer/capability subset | Passed with `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` under each configured Surefire execution in the module run. |

## Acceptance Boundary

SQL Server can be promoted only when live results prove:

- Accepted C2 shapes execute through SQL pushdown.
- Result rows, ordering, and totals match the baseline oracle.
- CTE domain transport is applied or refused with stable diagnostics.
- Unsupported shapes still fail closed.

Until that evidence exists, SQL Server-specific Pivot cascade and prepared-service parity remain unclaimed. This does not block the default Java engine gate unless a release note or tag explicitly includes SQL Server support in its claimed scope.

MySQL 5.7 can be signed only as:

- Large-domain axis-domain transport evidence present.
- C2 cascade refusal present.
- No cascade enablement and no memory fallback.

## Documentation Handoff

When either dialect evidence changes, update:

- This workitem.
- `docs/9.2.0/README.md` readiness snapshot.
- The relevant acceptance record if the status changes from env-gated to verified.
