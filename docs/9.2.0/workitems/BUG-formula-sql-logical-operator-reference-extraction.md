---
workitem_type: bug
version: 9.2.0
target: Formula SQL logical operator reference extraction
status: implemented
created_at: 2026-06-06
updated_at: 2026-06-06
owner_surface: Java QueryModel formula expression compiler
---

# BUG: Formula SQL Logical Operator Reference Extraction

## Problem

`CalculatedFieldService.extractColumnReferences(...)` failed when an expression used SQL-style logical keywords such as `and` inside `if(...)` conditions:

```text
SUM(if(orderStatus in (...) and paymentStatus in (...) and dateMaturity < '2026-05-04', payAmount, 0))
```

The parser expected expression logical operators and raised a syntax error before column references could be collected. This blocked field-access dependency extraction for otherwise valid SQL-expression formulas.

## Fix

The SQL expression compilation path now normalizes string-external SQL logical words:

- `and` -> `&&`
- `or` -> `||`

The normalization is limited to `FsscriptDialect.SQL_EXPRESSION` and preserves quoted string literals, so values such as `'A and B'` or `'C or D'` are not rewritten.

## Evidence

Targeted regression command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='DialectTest$SqlServerDialectTest#testGeneratePagingSqlWithDistinctAndNoOrderBy,CalculatedFieldServiceTest#extractRefs_ifFunctionWithSqlAndConditions+extractRefs_sqlLogicalOperatorsIgnoreStringLiterals' test
```

Result:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

Prepared MySQL gate after refreshing local `foggy-dataset` and `foggy-dataset-demo` artifacts:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model surefire:test@test-mysql -Dsurefire.failIfNoSpecifiedTests=false
```

Result:

```text
Tests run: 3040, Failures: 0, Errors: 0, Skipped: 51
```

## Boundary

This does not add free-form SQL parsing. It only aligns the existing SQL-expression formula dialect with common SQL logical operator spelling before the existing AST compiler runs.
