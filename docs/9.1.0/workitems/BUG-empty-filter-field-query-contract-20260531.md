---
type: bug
bug_source: acceptance-found
version: 9.1.0
ticket: BUG-empty-filter-field-query-contract-20260531
severity: major
status: verified-targeted-live
owner: java-engine
created_at: 2026-05-31
updated_at: 2026-05-31
---

# BUG Empty Filter Field Query Contract

## Summary

The v3.9 medium replay left one Java MCP residual where `dataset.query_model` received a filter
whose `field` was blank. The raw tool error was structured, but the retry guidance was generic and
the NL response stayed as `QUERY_MODEL_FAILED`.

## Fix

- `McpToolCallbackFactory` returns `repair_filter_field_or_stop` for empty filter-field failures.
- `QueryExpertService` returns `INVALID_QUERY_MODEL_FILTER` if the model still finishes with an
  unstructured response after this failure.
- Regression tests cover both the guidance path and the terminal fallback path.

## Verification

| Check | Result |
|---|---|
| Focused MCP tests | `McpToolCallbackFactoryTest,QueryExpertServiceRoutingCalibrationTest` passed with `24/24`. |
| Package | `mvn -pl foggy-mcp-launcher -am -DskipTests package` passed. |
| Live replay | `biz-007__gpt_oss_120b_medium` replayed through fresh Java lite and returned final `result`; evidence is `1/1`, residuals `0`. |
| Merged evidence | Replacing the single historical residual row yields `120/120` passed-or-terminal, residuals `0`. |

## Code Inventory

| Path | Purpose |
|---|---|
| `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/McpToolCallbackFactory.java` | Retry guidance. |
| `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/QueryExpertService.java` | Terminal fallback. |
| `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/service/McpToolCallbackFactoryTest.java` | Guidance regression. |
| `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/service/QueryExpertServiceRoutingCalibrationTest.java` | Terminal fallback regression. |

