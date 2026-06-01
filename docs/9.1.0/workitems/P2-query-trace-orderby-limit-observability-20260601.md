---
doc_role: workitem
doc_purpose: Track Java MCP query-trace observability for TopN limit and orderBy semantic evidence.
version: 9.1.0
target: QueryExpertService trace summary
status: implemented
created_at: 2026-06-01
updated_at: 2026-06-01
source_type: optimization
---

# Query Trace OrderBy and Limit Observability

## Purpose

Record the Java-side trace change that lets the v3.9 evidence runner validate TopN semantics from structured `dataset.query_model` payload signals instead of only final result status or prompt text.

## Scope

| Item | Decision |
|---|---|
| Public DSL | No change. `payload.orderBy` and `payload.limit` keep their existing runtime semantics. |
| Trace summary | Add structured payload summaries for `orderBy` fields and directions. |
| Evidence usage | Support semantic-underexecution checks for `payload_limit`, `payload_order_by_fields`, and `payload_order_by_dirs`. |
| Compatibility | Missing orderBy trace summaries in historical artifacts remain `semantic_observation_unavailable`; stable orderBy assertions should wait for fresh replay artifacts. |

## Implementation

| Path | Change |
|---|---|
| `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/QueryExpertService.java` | `arguments_summary` now copies raw `payload_order_by` and derives `payload_order_by_fields` plus lower-cased `payload_order_by_dirs`. |
| `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/service/QueryExpertServiceRoutingCalibrationTest.java` | Nonblocked trace regression now asserts extracted `amount` / `desc` orderBy signals alongside existing `payload_limit`. |

## Testing

| Command | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home PATH=/Users/fengjianguang/.jdk/temurin-17/Contents/Home/bin:$PATH mvn -pl foggy-dataset-mcp -am -Dtest=QueryExpertServiceRoutingCalibrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | passed: `18/18`, reactor `BUILD SUCCESS`. |

## Acceptance Readiness

- current_status: implemented
- acceptance_scope: lightweight trace/evidence observability
- blocker: none
- follow_up: Run a fresh Java replay after this trace build, then promote orderBy assertions into the stable semantic expectation set where the fresh traces prove field/direction observability.
