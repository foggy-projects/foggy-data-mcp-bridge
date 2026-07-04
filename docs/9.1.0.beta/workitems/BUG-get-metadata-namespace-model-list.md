# BUG: dataset.get_metadata namespace model-list isolation

## Problem

`dataset.get_metadata` used the global `foggy.mcp.semantic.model-list` before considering the request namespace.
When a non-default namespace such as `salesdrop` was used, legacy globally configured models could appear in the
metadata diagnostics even though they were not visible in that namespace.

Observed legacy model names:

- `FactOrderQueryModel`
- `CrmLead`
- `CustomerOrderLifecycleQueryModel`
- `ServiceTicketQueryModel`

## Fix

- Prefer `semantic.namespaces.<namespace>.model-list` when the request resolves to a configured namespace.
- For an explicit request namespace with no namespace-specific list, dynamically discover models visible in that namespace.
- Keep the legacy global `model-list` behavior only for the default/no-namespace path.

## Regression Coverage

- `LocalDatasetAccessorGovernanceTest.metadataExplicitNamespaceShouldUseNamespaceModelList`
- `LocalDatasetAccessorGovernanceTest.metadataExplicitNamespaceShouldNotUseGlobalLegacyModelList`
- `LocalDatasetAccessorGovernanceTest.metadataDefaultNamespacePathShouldKeepGlobalLegacyModelList`
- `ListModelsToolTest`

## Verification

`mvn -pl foggy-dataset-mcp "-Dtest=LocalDatasetAccessorGovernanceTest,ListModelsToolTest" test`

Result: pass, 33 tests.

`mvn -pl foggy-dataset-mcp -DskipTests compile`

Result: pass.
