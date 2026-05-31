---
type: workitem
workitem_source: v3.9-medium-matrix-hardening
version: 9.1.0
ticket: P2-field-comparison-predicate-fixture-20260531
priority: P2
status: verified-live
owner: java-engine
created_at: 2026-05-31
updated_at: 2026-05-31
---

# P2 Field Comparison Predicate Fixture

## Summary

The v3.9 medium replay surfaced a governance gap around partial predicate execution:

`金额为空 OR 客户为空 OR 订单日期晚于发货日期`

The Java query engine already supports field-to-field comparison through `$field`, but the lite/demo
order fixture did not expose a shipping date field, so this anomaly shape could not be verified in
the runnable MCP fixture.

## Change

- Added nullable `ship_date` to the lite `fact_order` schema and indexed it.
- Added `shipDate` to `FactOrderModel` and `FactOrderQueryModel`.
- Added `ORD-LITE-0006`, which covers the null-customer branch and `orderDate > shipDate`.
- Mirrored the `ship_date` column and anomaly row into MySQL, PostgreSQL, and SQL Server demo
  initialization scripts.
- Added a launcher regression that verifies the lite schema, fixture row, TM mapping, and QM
  exposure stay aligned.

## Verification

| Check | Result |
|---|---|
| Focused launcher regression | `mvn -pl foggy-mcp-launcher -am -Dtest=McpLauncherLiteProfileConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed with `3/3`. |
| Package | `mvn -pl foggy-mcp-launcher -am -DskipTests package` passed. |
| Live direct MCP query | `dataset.query_model` on fresh lite returned `ORD-LITE-0006` for `amount is null OR customer is null OR orderDate > shipDate`. |
| Evidence artifact | `experiments/spider-routing-eval/output/v39_fieldref_or_predicate_lite_direct_20260531.json`. |

## Code Inventory

| Path | Purpose |
|---|---|
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-schema.sql` | Lite `ship_date` schema and index. |
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-data.sql` | Lite anomaly fixture row. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/model/FactOrderModel.tm` | TM `shipDate` mapping. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/FactOrderQueryModel.qm` | QM `shipDate` exposure. |
| `foggy-dataset-demo/docker/*/init/*.sql` | Multi-db demo fixture parity. |
| `foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/McpLauncherLiteProfileConfigurationTest.java` | Fixture alignment regression. |

## Follow-up

This closes the runnable fixture gap for field-to-field comparison. Broader semantic-underexecution
detection still belongs in the v3.9 evidence runner: the runner should keep comparing expected
multi-predicate intent with observed tool payloads when new natural-language samples are added.
