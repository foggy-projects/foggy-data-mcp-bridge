---
type: bug
bug_source: user-report
version: 9.2.0
ticket: BUG-data-viewer-direct-extdata-runtime-filter
severity: major
status: current-source-verified
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: data-viewer-query
updated_at: 2026-06-11
---

# Data Viewer Direct ExtData Runtime Filter Regression

## Background

TMS reported that `/data-viewer/api/query/direct/{qmModel}` still returns
`aggregate relation runtime filter 值不能为空` when the request body carries top-level
`extData.suggestionSheetId`. The same query works through
`/jdbc-model/query-model/v2/{model}` with `param.extData`.

The affected QM pattern is an aggregate relation RHS filter that reads runtime
context:

```javascript
const occupancyByOrder = occ
    .filterEq(occ.suggestionSheetId, (ctx) => ctx.extData.suggestionSheetId)
    .groupBy(occ.tenantId, occ.planningStationId, occ.sourceOrderId)
    .as('occupancyByOrder');
```

## Expected vs Actual

Expected:

- Direct data-viewer requests pass top-level `request.extData` to
  `DbQueryRequestDef.extData`, then to `ModelResultContext.extData`.
- Missing runtime values remain fail-closed.
- Runtime filters stay inside the aggregate relation RHS query and do not become
  main-query `slice` / `where`.

Actual in the reported local JAR:

- Direct requests with top-level `extData` still fail with
  `aggregate relation runtime filter 值不能为空`.

## Test Strategy

Added launcher-level HTTP regression coverage in
`foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/DataViewerApiSmokeTest.java`.

The test model
`OrderSalesAggregateRelationRuntimeFilterQueryModel` mirrors the TMS pattern by
reading `(ctx) => ctx.extData.orderId` inside an RHS aggregate relation filter.

Coverage:

- Direct request with `extData.orderId` succeeds.
- Direct request without `extData.orderId` returns fail-closed error.
- Direct request with blank top-level `extData.orderId` returns a fail-closed
  validation error.
- Direct request with nested `param.extData.orderId` is not treated as direct
  runtime context and returns the same fail-closed validation error.
- Result rows prove the LEFT JOIN main table is not filtered by RHS runtime
  filter, while unmatched RHS aggregate columns remain empty.

## Code Inventory

- `addons/foggy-data-viewer/src/main/java/com/foggyframework/dataviewer/domain/ViewerQueryRequest.java`
- `addons/foggy-data-viewer/src/main/java/com/foggyframework/dataviewer/controller/ViewerApiController.java`
- `foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/DataViewerApiSmokeTest.java`
- `foggy-mcp-launcher/src/test/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationRuntimeFilterQueryModel.qm`

## Verification

Passed:

```bash
mvn -pl foggy-mcp-launcher -Dtest=DataViewerApiSmokeTest test
```

Result:

- Tests run: 6
- Failures: 0
- Errors: 0
- Skipped: 0

2026-06-11 negative-case hardening:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -P'!multi-db' -Dtest='DataViewerApiSmokeTest#directQueryRuntimeFilterFailsClosedWhenExtDataValueBlank+directQueryRuntimeFilterDoesNotReadNestedParamExtData' -Dsurefire.failIfNoSpecifiedTests=false test
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -P'!multi-db' -Dtest=DataViewerApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

- Targeted negative tests: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- Full `DataViewerApiSmokeTest`: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.
- The first full-suite attempt was blocked by a missing local Maven
  `target/generated-test-sources/test-annotations` directory under
  `foggy-dataset`; after recreating the local generated directory, the suite
  passed.

Current-source verification passed on 2026-06-06:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -Dtest=DataViewerApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

- Reactor build passed across 14 modules.
- `DataViewerApiSmokeTest`: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.
- The logged `aggregate relation runtime filter 值不能为空` stack trace is the expected missing-`extData` negative case asserted by the test; the overall test result is success.

## Current Conclusion

Current source code passes the direct-entry regression test. If TMS still
reproduces the failure on `query-cloud-service-x3-3.0.0-SNAPSHOT-exec.jar`, the
running JAR likely does not include the data-viewer `extData` passthrough changes
and needs to be rebuilt/redeployed from a revision containing them.
