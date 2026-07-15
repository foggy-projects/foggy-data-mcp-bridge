# Pre-Aggregation Optimization Issues

> Tracking document for pre-aggregation code quality improvements across `foggy-dataset-model` and `addons/foggy-dataset-model-preagg`.

## Issue Summary

| # | Priority | Module | Issue | Status |
|---|----------|--------|-------|--------|
| 1 | P0 | addon | SQL Injection in PreAggSqlBuilder | ✅ Fixed |
| 2 | P0 | addon | Non-atomic TRUNCATE+INSERT in FullRefreshStrategy | ✅ Fixed |
| 3 | P0 | addon | Non-atomic DELETE+INSERT in IncrementalRefreshStrategy | ✅ Fixed |
| 4 | P1 | addon | MySQL dialect hardcoded in PreAggSqlBuilder | ✅ Fixed |
| 5 | P2 | both | `getFullTableName()` duplicated 3x | ✅ Fixed |
| 6 | P2 | core | `extractSliceColumn()` duplicated 2x | Deferred |
| 7 | P2 | core | `toSnakeCase()`/`normalizePropertyName()` duplicated 2x | ✅ Fixed |
| 8 | P2 | both | `findPreAggregation()` duplicated 2x | Deferred |
| 9 | P3 | addon | Controller uses ResponseEntity instead of RX | ✅ Fixed |
| 10 | P3 | addon | Fragile GROUP BY string search in buildIncrementalInsertSql | ✅ Fixed |
| 11 | P4 | addon | ScheduledTaskInfo thread safety in PreAggScheduler | ✅ Fixed |
| 12 | P4 | core | Repeated HashSet creation in isSatisfiableBy() | ✅ Fixed |
| 13 | P4 | core | Redundant aggregation compatibility branches | ✅ Fixed |
| 14 | P3 | addon | Watermark state lost on restart (in-memory only) | Deferred |
| 15 | P4 | addon | PreAggSqlBuilder/PreAggregationInterceptor re-created per call | Won't Fix |

**Summary:** 11/15 fixed, 3 deferred, 1 won't fix.

---

## P0 — Security & Correctness

### #1 SQL Injection in PreAggSqlBuilder ✅

**File:** `addons/.../preagg/ddl/PreAggSqlBuilder.java`

**Problem:** Date values directly interpolated into SQL via `String.format`.

**Fix:** Created `ParameterizedSql` class (sql + params list), `buildIncrementalDeleteSql()` and `buildIncrementalInsertSql()` now return `ParameterizedSql` with `?` placeholders. Callers use `PreparedStatement` to bind parameters.

**Tests:** `PreAggSqlBuilderTest` — 7 tests verifying parameterized SQL, no literal date values, correct structure.

---

### #2 Non-atomic TRUNCATE+INSERT in FullRefreshStrategy ✅

**File:** `addons/.../preagg/refresh/FullRefreshStrategy.java`

**Problem:** `TRUNCATE TABLE` (DDL, implicit commit in MySQL) followed by `INSERT`. If INSERT fails, all data is lost.

**Fix:**
- Replaced `TRUNCATE TABLE` with `DELETE FROM` (DML, transactional)
- Wrapped in programmatic transaction (`conn.setAutoCommit(false)` + `commit`/`rollback`)

**Tests:** `FullRefreshStrategyTest` — 3 tests verifying transaction behavior and rollback on failure.

---

### #3 Non-atomic DELETE+INSERT in IncrementalRefreshStrategy ✅

**File:** `addons/.../preagg/refresh/IncrementalRefreshStrategy.java`

**Problem:** `DELETE` and `INSERT` executed as separate statements without transaction.

**Fix:** Same transaction wrapping as #2, plus uses `PreparedStatement` for `ParameterizedSql`.

**Tests:** `IncrementalRefreshStrategyTest` — 5 tests verifying incremental logic and parameterized SQL execution.

---

## P1 — Multi-Database Dialect

### #4 MySQL Dialect Hardcoded in PreAggSqlBuilder ✅

**Files:**
- `foggy-dataset/.../dialect/FDialect.java` — 3 new default methods
- `MysqlDialect.java`, `PostgresDialect.java`, `SqlServerDialect.java`, `SqliteDialect.java` — dialect-specific overrides
- `PreAggSqlBuilder.java` — accepts `FDialect` in constructor, delegates all SQL generation
- `PreAggRefreshService.java` — resolves dialect via `DbUtils.getDialect(dataSource)`
- `PreAggRefreshContext.java` — carries `FDialect dialect` field
- `FullRefreshStrategy.java` / `IncrementalRefreshStrategy.java` — creates `PreAggSqlBuilder` with context dialect

**New methods on FDialect:**
- `buildDateTruncateExpression(column, granularity)` — YEAR/QUARTER/MONTH/WEEK/DAY/HOUR/MINUTE
- `buildCurrentTimestampExpression()` — NOW()/GETDATE()/datetime('now')
- `mapColumnType(abstractType)` — DDL type mapping per dialect

**Tests:** `DateTruncateTest` — 42 tests (4 dialects × 7 granularities + timestamp + type mapping + cross-dialect consistency).

---

## P2 — Code Duplication

### #5 `getFullTableName()` duplicated 3x ✅

**Fix:** Added `default getQualifiedTableName()` to `PreAggregation` SPI interface. Removed private `getFullTableName()` from `PreAggSqlBuilder`, `FullRefreshStrategy`, `IncrementalRefreshStrategy`.

### #6 `extractSliceColumn()` duplicated 2x — Deferred

**Reason:** The two versions differ in behavior — `PreAggregationInterceptor`'s version also calls `addDimension(dimName)`. Unifying would require careful analysis to ensure no behavioral change. Low risk as-is.

### #7 `toSnakeCase()` / `normalizePropertyName()` duplicated 2x ✅

**Fix:** Both `PreAggregationImpl.toSnakeCase()` and `PreAggQueryRequirement.normalizePropertyName()` now delegate to existing `StringUtils.to_sm_string()`. Removed private `toSnakeCase()` from `PreAggregationImpl`.

### #8 `findPreAggregation()` duplicated 2x — Deferred

**Reason:** Adding a default method to `TableModel` SPI interface is an architectural change that should be evaluated holistically. The duplication is low-risk (simple iteration over a list).

---

## P3 — Architecture

### #9 Controller Uses ResponseEntity Instead of RX ✅

**File:** `addons/.../preagg/controller/PreAggController.java`

**Fix:** Replaced all `ResponseEntity<?>` returns with `RX<?>`:
- `ResponseEntity.ok(data)` → `RX.ok(data)`
- `ResponseEntity.notFound().build()` → `RX.notFound().build()`
- `ResponseEntity.badRequest().body(...)` → `RX.failB(msg)`

### #10 Fragile GROUP BY String Search ✅

**Fix:** Refactored `PreAggSqlBuilder` to use `RefreshSqlParts` internal class. SELECT, GROUP BY, and WHERE clauses are built independently as lists, then assembled. No more `indexOf("GROUP BY")` string search.

### #14 Watermark State Lost on Restart — Deferred

**Reason:** This is an enhancement (persistence layer), not a bug. Current in-memory behavior is acceptable for initial release. Can be added as `JdbcPreAggWatermarkStore` in a future iteration.

---

## P4 — Thread Safety & Performance

### #11 ScheduledTaskInfo Thread Safety ✅

**File:** `addons/.../preagg/scheduler/PreAggScheduler.java`

**Fix:** Added `synchronized(taskInfo)` blocks around both reads (getting lastRefreshTime/watermark) and writes (setting lastRefreshTime/lastResult/lastWatermark) in `executeRefresh()`.

### #12 Repeated HashSet Creation in isSatisfiableBy() ✅

**File:** `foggy-dataset-model/.../engine/preagg/PreAggQueryRequirement.java`

**Fix:** Extracted `buildNormalizedPropertySet()` helper method. Both the dimensionProperties loop and the sliceColumns loop now call this shared helper instead of inline `new HashSet<>()` + loop.

### #13 Redundant Aggregation Compatibility Branches ✅

**File:** `foggy-dataset-model/.../engine/preagg/PreAggQueryRequirement.java`

**Fix:** Simplified `isAggregationCompatible()` from 30+ lines to 6 lines:
```java
if (preAggAgg == null || queryAgg == null) return true;
if (preAggAgg == queryAgg) return true;
return preAggAgg == DbAggregation.COUNT && queryAgg == DbAggregation.SUM;
```
The SUM==SUM, MIN==MIN, MAX==MAX branches were redundant (already covered by `preAggAgg == queryAgg`).

### #15 Objects Re-created Per Call — Won't Fix

**Reason:** Analysis shows these objects carry per-call state:
- `PreAggregationInterceptor` has `hybridQueryEnabled` set per call
- `PreAggQueryRewriter` takes `queryModel` (varies per call) in constructor

They are not truly stateless, so reuse would require refactoring to a different pattern (e.g., method parameters instead of constructor injection), which is not worth the complexity for negligible performance gain.

---

## Test Coverage

| Test Class | Module | Tests | Focus |
|---|---|---|---|
| `PreAggSqlBuilderTest` | addon | 7 | SQL injection prevention, parameterized SQL, DDL generation |
| `FullRefreshStrategyTest` | addon | 3 | Transaction atomicity, DELETE FROM (not TRUNCATE) |
| `IncrementalRefreshStrategyTest` | addon | 5 | Incremental logic, PreparedStatement usage |
| `DateTruncateTest` | foggy-dataset | 42 | 4 dialects × 7 granularities + timestamp + type mapping |
| `PreAggregationIT` | core | 23 | End-to-end matching, rewriting, hybrid queries |
| `PreAggQueryRequirementTest` | core | 25 | Requirement building, matching, edge cases |

**Total: 105 tests, all passing.**
