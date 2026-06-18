---
doc_role: workitem
doc_purpose: Track the 2026-06-18 TMS upstream, target database EXPLAIN, and model registry promotion boundary for Java engine 9.2.0.
version: 9.2.0
target: TMS target evidence and registry promotion handoff
status: blocked-awaiting-authority-tms
created_at: 2026-06-18
updated_at: 2026-06-18
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
related_repo: foggy-model-registry
---

# TMS Target Evidence And Registry Handoff

## Purpose

This record separates the default Java engine release boundary from TMS-specific
consumer and package promotion evidence.

The Java engine now has local SQLite, MySQL, and PostgreSQL prepared-service
coverage for the documented 9.2.0 aggregate-join and hardening boundary. Real
TMS consumer confirmation and target database `EXPLAIN` remain required before
claiming TMS production optimizer confidence or publishing a TMS model package
through `foggy-model-registry`.

## 2026-06-18 Audit Facts

| Area | Current Fact | Boundary |
|---|---|---|
| TMS workspace | No local TMS/query-cloud-service checkout exists under `/Users/fengjianguang/foggy-projects`. | This repo cannot run the real `OrderSettlementCandidateQuery`, `FactOrderSettlementModel`, or `OrderStationStockProjectionQuery` scenarios. |
| GitHub #85 | `gh issue view 85 -R foggy-projects/foggy-data-mcp-bridge` shows issue #85 still `OPEN`, updated at `2026-06-13T04:41:07Z`. | Keep `BUG-formula-property-missing-column-error` as `local-verified-awaiting-upstream`. |
| `availablePieceCount` upstream tracker | Initial search returned #85 and #84 only; issue #96 was created on 2026-06-18 as the dedicated `OrderStationStockProjectionQuery.availablePieceCount` tracker. | Keep `BUG-qm-predefined-formula-slice-injection` as `local-verified-awaiting-upstream` until #96 receives real TMS pass/fail confirmation. |
| Registry repo | `/Users/fengjianguang/foggy-projects/foggy-model-registry` is present, clean before update, and `git pull --ff-only` reported already up to date. | Registry can record a promotion gate, but cannot publish without authority TMS model artifacts. |
| Registry promotion gate | `foggy-model-registry` commit `881912a docs: refresh tms promotion gate evidence` updates the TMS promotion gate with PostgreSQL evidence and SQL Server non-blocking boundary. | Status remains `gate-defined-not-published`. |
| SQL Server | SQL Server remains service-gated and unclaimed. | This blocks only SQL Server-specific support claims, not the default MySQL/PostgreSQL/SQLite engine boundary. |

## Required Target TMS Evidence

Before TMS production optimizer confidence or registry package publication:

1. Authority TMS TM/QM source directory is available and contains the real
   target model files.
2. TMS consumer confirms #85 against the real settlement model family, or a
   faithful local branch that removes one carrier `column` for verification.
3. TMS consumer confirms `OrderStationStockProjectionQuery.availablePieceCount`
   can be referenced from filter-like clauses without being selected in
   `columns`; tracked by https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/96.
4. Target TMS database query execution passes for the representative aggregate
   relation shape.
5. Generated SQL and `EXPLAIN` or equivalent plan evidence proves the RHS
   aggregate relation receives selective source-key filters before or during
   aggregation.
6. At least one left join-key filter and one aggregate-measure filter are
   verified against the target TMS grain.
7. Permission, tenant/access guard, and denied physical-column boundaries are
   documented for the target TMS model.
8. Registry publish/pull/checksum flow passes before any `foggy.tms.pro`
   channel is created.

## Current Decision

Decision: blocked-awaiting-authority-tms.

The current Java engine evidence is sufficient for the default 9.2 hardening
boundary, with upstream follow-ups still open. TMS package publication and
production optimizer confidence remain blocked until authority TMS models and
target database evidence are available.

## Cross-Links

- Upstream confirmation packet: `upstream-verification-handoff-20260606.md`
- Aggregate join workitem: `query-model-aggregate-join.md`
- Registry gate: `/Users/fengjianguang/foggy-projects/foggy-model-registry/docs/v1.0/P2-tms-aggregate-relation-promotion-gate.md`
