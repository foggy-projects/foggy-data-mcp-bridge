---
type: bug
version: runtime-api-launcher-v0.1.1
source: github-issue
issue: GH-110
url: https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/110
severity: major
status: ready-for-verification
owner: foggy-data-mcp-bridge
created: 2026-07-01
updated: 2026-07-01
---

# BUG: dataset.list_models returns empty salesdrop catalog

## Source

- GitHub Issue: GH-110
- Reported environment: `foggy-ai-analysis v0.1.0`, Java launcher `runtime-api-launcher-v0.1.1`, runtime URL `http://127.0.0.1:18066`, namespace `salesdrop`

## Phenomenon

`dataset.list_models` returns an empty catalog for namespace `salesdrop`, while `dataset.describe_model_internal` and `dataset.query_model` can resolve `SalesDropDailyQueryModel`.

## Expected

The MCP catalog should expose the namespace-visible model catalog. If a model can be described and queried by name, it should not disappear from `dataset.list_models`.

## Actual

The default MCP launcher config provides a static `foggy.mcp.semantic.model-list` for ecommerce models. `ModelCatalogService` used that list exclusively, so those names were attempted in the `salesdrop` namespace and skipped when not visible. Dynamic discovery was never reached.

## Impact

AI clients see an empty model catalog and cannot reliably discover the available sales-drop model, even though direct describe/query works.

## Fix Checklist

- Keep explicit request-level `modelNames` semantics unchanged.
- Add namespace-specific `list_models` catalog configuration.
- Keep configured model-list semantics when configured models resolve.
- When configured model-list resolves no visible models in a non-default namespace, retry with dynamic model discovery.
- Do not fallback when a namespace-specific list is configured but resolves no visible models.
- Document that namespace-specific model-list overrides global model-list/use-all-models.
- Add regression coverage for namespace fallback, namespace override, property binding, and the MCP tool entrypoint.

## Verification

- Targeted tests: `McpPropertiesBindingTest`, `ListModelsToolTest` namespace catalog cases
- Command: `mvn -pl foggy-dataset-mcp -Dtest=McpPropertiesBindingTest,McpJsonRpcIdTypeTest,ListModelsToolTest test`
- Full module regression: `mvn -pl foggy-dataset-mcp test`
