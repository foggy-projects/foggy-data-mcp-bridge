---
type: bug
bug_source: user-report
version: 9.2.0
ticket: BUG-data-viewer-direct-extdata-runtime-filter
severity: major
status: ready-for-verification
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: data-viewer-query
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

- Tests run: 4
- Failures: 0
- Errors: 0
- Skipped: 0

## Current Conclusion

Current source code passes the direct-entry regression test. If TMS still
reproduces the failure on `query-cloud-service-x3-3.0.0-SNAPSHOT-exec.jar`, the
running JAR likely does not include the data-viewer `extData` passthrough changes
and needs to be rebuilt/redeployed from a revision containing them.
