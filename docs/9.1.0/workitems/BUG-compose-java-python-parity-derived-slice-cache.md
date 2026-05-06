---
type: bug
bug_source: regression-found
version: 9.1.0
ticket: BUG-compose-java-python-parity-derived-slice-cache
severity: major
status: ready-for-verification
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model-compose
---

# BUG: Java Compose Python Parity for Derived Slice and Per-Base Cache

## Background

Python compose fixes for AR benchmark hardening exposed three Java parity gaps:

- `PerBaseCompiler` keyed the governance cache only by model and binding identity, so two different `BaseModelPlan` shapes for the same model/binding could reuse the first generated SQL.
- `DerivedQueryPlan.slice` rendered every value as a bound parameter, so `{"value": {"$field": "rhs"}}` became a map parameter instead of a field-to-field predicate.
- Compile-only paths could bypass schema derivation and still allow filtering an alias created by the same derived SELECT stage.

## Expected vs Actual

Expected:

- Cache reuse is limited to identical base query shapes.
- Derived field-to-field slices render as `<innerAlias>.<lhs> <op> <innerAlias>.<rhs>` with no map parameter.
- Same-stage derived aliases are rejected before SQL execution with `compose-schema-error/derived-query/same-stage-alias`.

Actual:

- Different same-model base plans could share a stale `SqlGenerationResult`.
- Field reference values were parameterized.
- Same-stage alias filters could lower to SQL and fail later at execution time.

## Impact Scope

This affects Java compose compilation for union/join/derived plans. The Odoo AR benchmark currently runs through the Python vendored runtime, so this is Java parity hardening rather than the immediate Odoo benchmark blocker.

## Test Strategy

Unit tests were added/updated in:

- `DerivedLoweringTest`
- `PerBaseCompileTest`

Coverage:

- Different base query shapes for the same model/binding invoke `generateSql` separately.
- `{"$field": ...}` derived slice values render as field references.
- Same-stage derived alias filters are rejected during compile.

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/compilation/PerBaseCompiler.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/compilation/ComposePlanner.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/compilation/SliceShape.java`

## Verification

Commands:

```powershell
mvn -pl foggy-dataset-model clean "-Dtest=DerivedLoweringTest,PerBaseCompileTest,ComposeSchemaExceptionTest,SchemaDerivationTest" test
mvn -pl foggy-dataset-model test
```

Result:

- Targeted tests: passed.
- Full `foggy-dataset-model` module tests: passed.
