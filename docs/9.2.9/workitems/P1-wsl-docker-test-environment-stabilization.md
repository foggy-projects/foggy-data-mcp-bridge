---
type: bug
bug_source: full-regression
version: 9.2.9
ticket: P1-wsl-docker-test-environment-stabilization
severity: major
status: completed
reproduction_status: confirmed-during-maven-test
reproduction_evidence: mysql-postgres-mongo-wsl-port-forwarding-resets
test_strategy: focused-test, module-regression, full-maven-test, full-maven-install
automation_decision: existing-regression-suite
owner: foggy-data-mcp-bridge
owner_module: foggy-dataset-model
created_at: 2026-07-08
updated_at: 2026-07-08
---

# P1 WSL Docker Test Environment Stabilization

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track the WSL-to-host-Docker regression-test failure, environment diagnosis, localized test fix, and full Maven verification.

## Background

During a full Maven regression run from WSL, Docker-backed tests initially failed against services exposed by the host Docker environment. The test data volumes were expected to remain on the host Docker side, so the first priority was to verify whether failures came from data loss, a container problem, or WSL/Docker Desktop connectivity.

The affected services were the development-test containers under the host Docker environment:

- `foggy-demo-mysql` on `127.0.0.1:13306`
- `foggy-demo-postgres` on `127.0.0.1:15432`
- `foggy-demo-mongo` on `127.0.0.1:17017`

## Problem Statement

The initial failures were not caused by missing Docker data. The containers and volumes were present, and direct in-container checks showed the expected schemas and data.

The failure mode was stale WSL-to-Docker Desktop port forwarding:

- MySQL JDBC from WSL to `127.0.0.1:13306` failed with EOF / communication-link errors.
- PostgreSQL JDBC from WSL to `127.0.0.1:15432` failed with connection reset.
- Mongo tests temporarily logged premature end-of-stream / connection reset against `localhost:17017`.

After the connectivity issue was cleared, the full regression exposed a separate locale-sensitive test assertion in `QueryRequestValidationStepTest`: the current environment returned English validation messages, while the test asserted only Chinese text.

## Target Outcome

- Confirm host Docker data is intact and no volume rebuild is required.
- Restore WSL access to Docker-backed test services without changing persisted data.
- Make the validation-message assertions independent of the active locale while preserving field/value-specific checks.
- Run focused, module-level, full `mvn test`, and full `mvn install` verification.

## Touched Code Areas

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/plugins/QueryRequestValidationStepTest.java`

## Environment Actions

Only service containers were restarted. No Docker volumes were removed or recreated.

| Service | Action | Result |
|---|---|---|
| MySQL | Restarted `foggy-demo-mysql` | WSL JDBC to `127.0.0.1:13306` recovered. |
| PostgreSQL | Restarted `foggy-demo-postgres` | WSL JDBC to `127.0.0.1:15432` recovered. |
| MongoDB | Restarted `foggy-demo-mongo` | Mongo tests reconnected and passed during full regression. |

## Implementation Notes

`QueryRequestValidationStepTest` now validates both stable message tokens and localized message variants:

- required field/value token remains mandatory, such as `field`, `op`, `value`, `invalid_op`, `INVALID_AGG`, or `invalid`;
- localized message fragments accept Chinese and English variants such as `不能为空`, `cannot be empty`, `required`, `不合法`, and `Invalid`.

This keeps the test behavior-specific without depending on the JVM or environment message locale.

## Acceptance Criteria

- Docker-backed MySQL, PostgreSQL, and MongoDB test access works from WSL.
- No Docker data volume reset is required.
- Locale-sensitive validation assertions pass in the current environment.
- Focused validation test passes.
- `foggy-dataset-model` module regression passes.
- Full `mvn test` passes.
- Full `mvn install` passes.

## Constraints / Non-Goals

- Do not recreate Docker volumes or reinitialize database data.
- Do not change production validation message behavior.
- Do not loosen tests to accept missing field/value context.
- Do not introduce runtime API, auth-code, RBAC, audit, or permission-model changes.

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Docker environment diagnosis | done | Host Docker containers and data volumes verified intact. |
| MySQL WSL connectivity recovery | done | Container restart restored WSL JDBC connectivity. |
| PostgreSQL WSL connectivity recovery | done | Container restart restored WSL JDBC connectivity. |
| Mongo WSL connectivity recovery | done | Container restart restored Mongo test connectivity. |
| Locale-sensitive assertion fix | done | Validation-message tests now accept Chinese and English localized fragments. |
| Focused verification | done | `QueryRequestValidationStepTest` passed. |
| Module regression | done | `foggy-dataset-model` and upstream modules passed. |
| Full Maven test | done | Full reactor `mvn test` passed. |
| Full Maven install | done | Full reactor `mvn install` passed. |

## Execution Checklist

- [x] Inspect WSL Docker context and socket behavior.
- [x] Verify Docker-backed MySQL data is intact.
- [x] Verify Docker-backed PostgreSQL data is intact.
- [x] Verify Docker-backed MongoDB service recovery path.
- [x] Restart only affected containers, preserving volumes.
- [x] Fix locale-sensitive validation-message test assertions.
- [x] Run focused validation test.
- [x] Run module regression.
- [x] Run full `mvn test`.
- [x] Run full `mvn install`.

## Verification Evidence

| Scope | Command | Result |
|---|---|---|
| Focused validation test | `mvn -pl foggy-dataset-model -am -Dtest=QueryRequestValidationStepTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed: `QueryRequestValidationStepTest` completed with 28 tests, 0 failures, 0 errors. |
| Module regression | `mvn -pl foggy-dataset-model -am test` | Passed: `foggy-dataset` 105 tests passed; `foggy-dataset-model` 3239 tests passed, 3 skipped. |
| Full Maven test | `mvn test` | Passed: 25 reactor modules SUCCESS, total time 06:53 min, finished at 2026-07-08T11:07:18+08:00. |
| Full Maven install | `mvn install` | Passed: 25 reactor modules SUCCESS, total time 06:24 min, finished at 2026-07-08T11:13:58+08:00. |

## Execution Check-In Summary

- completed_work: Docker/WSL diagnosis, container-level recovery, locale-independent test assertion fix, focused verification, module regression, full `mvn test`, and full `mvn install`.
- touched_code_paths: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/plugins/QueryRequestValidationStepTest.java`.
- self_check: passed; the code change is test-only and preserves behavior-specific assertions.
- test_status: pass.
- remaining_risks: plain `docker` from the current WSL shell may still require `sudo -n docker`; runtime JDBC/Mongo test access was restored after container restarts.
- acceptance_readiness: ready-for-review.

## Acceptance Status

- acceptance_status: ready-for-review
- acceptance_decision: pending
- signed_off_by: N/A
- signed_off_at: N/A
- blocking_items: none
- follow_up_required: no
