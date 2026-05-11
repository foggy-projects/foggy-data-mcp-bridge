---
doc_role: version_overview
doc_purpose: Track Java engine v3.0 semantic-field contract work derived from the root Foggy Data MCP planning docs.
version: v3.0
target: Java TM/QM semantic field contract hardening
status: draft
created_at: 2026-05-10
---

# Foggy Data MCP Bridge v3.0 Java Execution Index

## 文档作用

- doc_type: version-overview
- intended_for: execution-agent | reviewer | signoff-owner
- purpose: 汇总 Java worktree 中 v3.0 语义字段契约执行入口，承接 root `docs/v3.0` 的候选优化设计。

## Upstream Baseline

Root source of truth:

- `../../../docs/v3.0/OPT-money-scale-semantic-field-contract.md` in workspace root `foggy-data-mcp`

The root design defines the cross-project contract. This Java worktree owns the first implementation phase only.

## Candidate Scope

| ID | Priority | Item | Owner | Status | Exit Criteria |
|---|---|---|---|---|---|
| MONEY-30-J1 | P1 | Money scale semantic field contract for Java TM/QM engine | `foggy-dataset-model` | accepted | Java engine returns, filters, sorts, aggregates, and calculates money semantic fields in business units while preserving TM/QM governance |
| MONEY-30-J2 | P2 | Semantic scale namespace loading policy | `foggy-dataset-model` | replanned | Same TM/QM files can be loaded as semantic or physical namespace views; semantic scale defaults to enabled and only explicit physical namespaces opt out |

## Primary Documents

- `workitems/OPT-money-scale-semantic-field-contract-java.md`
- `workitems/OPT-money-scale-semantic-field-contract-java-progress.md`
- `workitems/OPT-semantic-scale-application-control.md`
- `detailed_design/01_money_scale_semantic_field_java_plan.md`

## Current Boundary

- Java engine first.
- Python parity is deferred to a later root planning item.
- SQL optimization is deferred; v3.0 Java first phase prioritizes semantic correctness.
- No arbitrary SQL fragment field is introduced. TM/QM models keep using `column` for a single physical column and use `semanticScaleFactor` for unit conversion.
- Runtime `applySemanticScale` request/context switching is rejected. Unit contract is selected by namespace at model load time.
