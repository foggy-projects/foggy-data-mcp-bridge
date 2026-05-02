# Pivot Stage 5A Large-Domain Transport Spike Report

## 1. Executive Summary

This spike evaluates the feasibility of replacing oversized `IN` / `OR-of-AND` domain predicates with a joinable domain relation (Stage 5A) for Pivot non-additive rollup queries when the surviving domain exceeds the 500-tuple limit.

**Recommendation:** **No-Go for 9.0.0.beta.** The current fail-closed behavior (`domain > 500` throws `NonAdditiveRollupDomainTooLargeException`) is secure, predictable, and sufficient for the initial beta. Introducing a cross-database transport mechanism inside the Semantic engine entails moderate lifecycle complexity (parameter ordering, CTE injection without bypassing `queryModel` permissions/preAgg) that exceeds the acceptable risk profile for a release-prep phase. Defer to 9.0.x or 9.1.0.

## 2. Code & Test Inspection

Current `domain > 500` handling:
- **Enforcement location:** `NonAdditiveRollupExecutor.addAxisDomainSlice()` throws `NonAdditiveRollupDomainTooLargeException` if `domain.size() > MAX_IN_LIST_SIZE` (500).
- **Tuple correlation & Null handling:** For multi-field axes, it generates an OR-of-AND slice constraint: `OR(AND(col1=?, col2=?), AND(col1=?, col2 IS NULL))`.
- **Parameter ordering:** Base query parameters (system slices, inline expressions) are generated first by `queryModel`, then the domain slice values are appended at the end of the `WHERE` clause.

## 3. Dialect Capability Matrix

| Capability | SQLite 3.x | PostgreSQL 12+ | MySQL 8.0 | MySQL 5.7 |
| :--- | :--- | :--- | :--- | :--- |
| **`VALUES` CTE** | Yes (`VALUES(1, 'A')`) | Yes (`VALUES(1, 'A')`) | Yes (`VALUES ROW(1, 'A')`) | No (Requires `SELECT ... UNION ALL`) |
| **`UNION ALL` CTE** | Yes | Yes | Yes | No (No CTE support) |
| **Derived Table (UNION ALL)** | Yes | Yes | Yes | Yes |
| **Null-Safe Join** | `IS` | `IS NOT DISTINCT FROM` | `<=>` | `<=>` |
| **Param Count Limit** | 999 (default) or 32,766 | 65,535 (16-bit proto) | 65,535 (bound by max packet) | 65,535 |
| **Temp Tables** | Connection scoped | Connection scoped | Connection scoped | Connection scoped |

**Analysis:**
- MySQL 5.7 remains the primary blocker for modern CTE-based approaches, necessitating either a derived table (inline subquery) or temp tables.
- `UNION ALL` inside a derived table `(SELECT ? AS k1 UNION ALL SELECT ?)` is the most universally compatible purely relational shape across all 4 dialects, but creates extremely long SQL text.

## 4. Internal Transport Abstraction Design

To implement this without bypassing the `queryModel` lifecycle, we need an internal abstraction (e.g., `DomainTransportPlan`) passed via `SemanticRequestContext` or a hidden field in `SemanticQueryRequest`.

### Design: `DomainRelationRenderer`

1. **Abstractions:**
   - `DomainTransportPlan`: Contains the matrix of tuples (list of lists of objects) and axis field names.
   - `DomainRelationRenderer`: A dialect-specific SPI that formats the `DomainTransportPlan` into a joinable relation (e.g., CTE, derived table, or temp table).
2. **Lifecycle Integration (`JdbcModelQueryEngine`):**
   - When generating the `ManagedSqlRelation`, if a `DomainTransportPlan` is present, the engine invokes the `DomainRelationRenderer`.
   - The renderer outputs a relation string `_pivot_domain` and a parameter list.
   - The engine injects an `INNER JOIN _pivot_domain d ON (base.f1 = d.f1 OR (base.f1 IS NULL AND d.f1 IS NULL))` into the base query generation.
3. **Parameter Ordering:**
   - If using `VALUES` or `UNION ALL`, the domain parameters must be prepended or appended consistently. Since the domain relation joins to the base table, its parameters typically appear at the start of the query (if a CTE) or inline in the `FROM` clause. The engine must reconcile these parameters with `queryModel` slice parameters.
4. **Fallback:**
   - `DomainTransportPlan` includes an `isSupported()` check. If `false` (e.g., MySQL 5.7 if CTE is used and derived table is too complex), the engine falls back to `NonAdditiveRollupDomainTooLargeException`.

## 5. Prototype Decision

**Prototype Status:** Not Committed.
**Reason:** The implementation risk is higher than "low". Injecting a dynamic join and shifting parameter lists into `JdbcModelQueryEngine` requires structural changes to the internal relation builder (`RelationOuterQueryBuilder` or `JdbcModelQueryEngine` base logic). Attempting to hack this into the slice builder as an `IN` clause bypasses the intent of a joinable transport. Writing a clean `DomainRelationRenderer` is a feature-level effort, not a spike-level fix.

## 6. Python Impact

- Python currently aligns with Java's S8a contract.
- Python should implement the `domain > 500` fail-closed limit.
- Since Java is deferring Stage 5A, Python does not need to build a `DomainTransportPlan` for 9.0.0.beta.

## 7. Next Steps

1. Maintain `NonAdditiveRollupDomainTooLargeException` for 9.0.0.beta.
2. In 9.1.0, introduce `DomainRelationRenderer` and `DomainTransportPlan`.
3. Target **UNION ALL Derived Table** as the universal fallback, and **VALUES** for PostgreSQL/SQLite to minimize SQL parse overhead.
