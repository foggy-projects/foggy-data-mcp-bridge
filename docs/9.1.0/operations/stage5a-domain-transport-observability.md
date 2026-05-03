---
doc_role: operations_guide
doc_purpose: Explain Stage 5A domain transport telemetry and operational checks for the 9.1.0 RC.
version: 9.1.0
target: PIVOT-91-B2 Stage 5A domain transport
status: rc-ready
created_at: 2026-05-02
---

# Stage 5A Domain Transport Observability

## Purpose

This guide explains how to interpret the safe telemetry markers added for Stage 5A large-domain transport. It is intended for release owners and operators validating the 9.1.0 RC in a real environment.

## Marker Summary

| Marker | Meaning | Expected Use |
|---|---|---|
| `pivot.domain_transport.planned` | Non-additive auxiliary rollup built an internal `DomainTransportPlan`. | Confirms `domain > 500` took the transport path instead of the small-domain `OR-of-AND` slice path. |
| `pivot.domain_transport.applied` | `JdbcModelQueryEngine` rendered and injected the transport relation into queryModel SQL. | Confirms the dialect renderer accepted the plan. |
| `pivot.domain_transport.refused` | Renderer, dialect, field resolution, relation validation, or version gate refused transport. | Treat as fail-closed behavior. Investigate reason and dialect. |
| `pivot.non_additive.domain_limit_exceeded` | Legacy small-domain slice path exceeded its safe limit. | Should not be the normal supported-dialect path after B2 for eligible non-additive auxiliary rollups. |
| `pivot.non_additive.aux_query.started` / `completed` | Auxiliary non-additive rollup query started/completed. | Use for auxiliary query count and duration tracking. |

Telemetry must not include raw SQL values. Safe fields include model, relation name, dialect/product name, placement, field count, tuple count, parameter count, duration, and sanitized reason class/message.

## Operational Checks

1. Confirm large-domain requests produce `pivot.domain_transport.planned`.
2. Confirm supported SQLite/PostgreSQL/MySQL8 paths also produce `pivot.domain_transport.applied`.
3. Confirm unsupported or unsafe cases produce `pivot.domain_transport.refused` and do not silently degrade into an unbounded full-scan fallback.
4. Track `tupleCount` and `parameterCount` distributions before GA promotion.
5. Watch auxiliary query duration and count for regressions after enabling large-domain workloads.

## Dialect Behavior

| Dialect | Expected 9.1.0 RC Behavior |
|---|---|
| SQLite | CTE `VALUES` transport, parameter-limit guarded. |
| PostgreSQL | CTE `VALUES` transport, parameter-limit guarded. |
| MySQL 8.0.19+ | CTE `VALUES ROW(...)` transport, selected only after JDBC metadata version gate. |
| MySQL before 8.0.19 / conservative MySQL | Derived-table `UNION ALL` transport through the MySQL 5.7 renderer, threshold guarded. |
| Unknown / unsupported dialect | Fail closed through `DomainTransportRefusalException`. |

## MySQL 5.7 Notes

MySQL 5.7 does not use the MySQL8 CTE `VALUES ROW(...)` path. The fallback renderer is intentionally limited by:

- tuple count: max `2000`
- bind parameter count: max `10000`
- rendered SQL length: max `1MB`

The renderer-level test `DomainRelationRendererTest` verifies the threshold refusal path. No external MySQL 5.7 live-database large-domain parity run was recorded for this RC closeout, so release owners should treat MySQL 5.7 support as guarded fallback behavior, not as a performance guarantee.

## Follow-Up Metrics

- Count of planned/applied/refused transport attempts by dialect.
- Refusal reason class/message by dialect.
- Tuple count percentiles for transported domains.
- Parameter count percentiles for transported domains.
- Auxiliary non-additive query duration percentiles.
- Number of requests still hitting `domain_limit_exceeded`.
