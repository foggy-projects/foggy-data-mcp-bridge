---
doc_role: implementation_classification_record
version: 9.3.5
status: closed
recorded_at: 2026-07-24
scope: controller-runtime-addon-loader-and-direct-query
---

# 9.3.5 Bypass Classification Record

## Decision

The controller/runtime/addon bypass inventory is closed for the 9.3.5 source
tree. Production consumers no longer call `QueryModelLoader` directly. Model
discovery and catalog resolution cross `SemanticModelCatalogReadPort`; legacy
loader behavior is contained by `LegacySemanticModelCatalogReadAdapter` inside
`foggy-dataset-model`.

Deprecated constructor signatures in `ModelCatalogService`,
`SemanticServiceResolverImpl`, `ToolConfigLoader`, and
`RuntimeModelOperations` retain `QueryModelLoader` parameters for one binary
compatibility period. Those parameters no longer establish an addon/runtime
execution bypass.

## `.query(` Classification

The remaining external-module text matches are approved responsibilities, not
direct query-model bypasses:

- Data Viewer and GraphQL execute through the stable DTO-only `QueryFacade`;
  member-query calls are service-level delegation and preset `.query(...)` is a
  builder property.
- `DatasetClientProxy` invokes the generic `DataSetModel` backend owned by the
  dataset-client adapter; it does not load or execute a `QueryModel`.
- vector search `.query(...)` sets the Spring AI search request text.
- MCP natural-language `.query(...)` sets a request field, while
  `JdbcTemplate.query(...)` belongs to the experience registry persistence
  adapter.
- runtime `CatalogModelKey.query(...)` is a typed catalog-key factory.

No main-source call matching
`queryModelLoader.(loadJdbcQueryModel|getJdbcQueryModel)` remains in
`addons`, `foggy-dataset-mcp`, `foggy-runtime-api`, or `foggy-mcp-launcher`.

## Affected Validation

- affected model/MCP reactor compile: success;
- `SemanticModelCatalogServiceTest`: 11/11;
- `SemanticServiceResolverImplTest`, `CatalogNamespaceAuthorityTest`,
  `CatalogAuthoritySpringWiringTest`, `ListModelsToolTest`, and
  `ListModelsCatalogControllerTest`: 43/43;
- production authority tests verify that loader calls remain absent; legacy
  resolver/list-model tests verify the compatibility adapter path, and the
  Spring smoke verifies the loader-free production constructors.

Two initial reactor test-selection commands stopped in `foggy-core` because
the repository hard-codes fail-on-missing selected tests per module. They ran
no selected product test and exposed no implementation failure; the same test
set was then executed directly in the two affected modules.
