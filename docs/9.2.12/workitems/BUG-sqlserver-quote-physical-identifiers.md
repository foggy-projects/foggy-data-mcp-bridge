---
type: bug
bug_source: github-issue
version: 9.2.12
ticket: BUG-sqlserver-quote-physical-identifiers
severity: major
status: ready-for-verification
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model
---

# BUG Work Item

## Document Purpose

- doc_type: bug
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track GitHub issue #117 regression coverage and fix for SQL Server physical identifiers containing spaces.

## Background

GitHub issue #117 reports that SQL Server query execution emits unquoted physical column names for TM fields whose source columns contain spaces.

The reported model validates, but `query.execute` generates invalid SQL such as `Total Including Tax [totalIncludingTax]`, `where Invoice Date Key >= ?`, and `order by Sale Key desc`.

## Reproduction

1. Define a SQL Server TM/QM over a physical table such as `[Fact].[Sale]`.
2. Map model fields to physical columns that contain spaces:
   - `Total Including Tax`
   - `Invoice Date Key`
   - `Stock Item Key`
   - `Sale Key`
3. Execute a query selecting, filtering, or ordering by those fields.

Before the fix, SQL generation used raw physical names without SQL Server identifier quoting.

## Expected vs Actual

Expected:

- Generated SQL should quote physical identifiers that require dialect quoting.
- SQL Server output should render `t1.[Total Including Tax]`, `t1.[Invoice Date Key]`, and `t1.[Sale Key]`.
- Select aliases should continue using the existing dialect quoting path.

Actual before fix:

- Physical column names were emitted as raw SQL fragments.
- SQL Server parsed spaced column names as multiple tokens and rejected the query.

## Impact Scope

- `foggy-dataset-model` JDBC query SQL generation.
- SQL Server datasources with direct physical tables or views that expose spaced column names.
- Select, where, order, group, expression, formula, and field-reference DSL paths that render a physical `DbColumn`.

## Test Strategy

Automation is required because this is an engine-level SQL rendering regression.

Primary regression:

- A unit test should render a SQL Server query with spaced physical column names through `SimpleSqlJdbcQueryVisitor`.

Adjacent regression guard:

- Verify formula-generated where fragments use the same dialect-safe column declaration.
- Verify simple identifiers remain unquoted to preserve existing SQL shape for other dialects and schemas.

## Code Inventory

- `foggy-dataset/src/main/java/com/foggyframework/dataset/db/dialect/FDialect.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/DbColumn.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query/SimpleSqlJdbcQueryVisitor.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/formula/SqlFormulaSupport.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`

## Fix Checklist

- [x] Add dialect-aware rendering for physical `DbColumn` declarations.
- [x] Use dialect-aware declarations in select, order, group, formula, expression, and field-reference paths.
- [x] Quote only identifiers that need quoting to avoid broad SQL shape churn.
- [x] Add regression coverage for SQL Server spaced physical column names.
- [x] Run the targeted regression test.

## Verification

- `mvn -pl foggy-dataset-model -am -Dtest=SqlServerIdentifierQuotingTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` - passed. The test ran 3 cases under the default, mysql, and postgres surefire executions with 0 failures.
- `git diff --check` - passed with CRLF/LF conversion warnings only.

## References

- GitHub issue: https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/117
