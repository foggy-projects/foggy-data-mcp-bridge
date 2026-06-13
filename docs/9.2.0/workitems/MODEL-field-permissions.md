---
type: optimization
version: 9.2.0
ticket: MODEL-field-permissions
severity: medium
status: local-verified
test_strategy: unit-and-integration-test
owner: foggy-dataset-model
created_at: 2026-06-13
updated_at: 2026-06-13
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# Declarative TM/QM Field Permissions

## Background

Before this work, field visibility was mainly controlled by runtime `fieldAccess` and physical-column `deniedColumns`. That works for request-time enforcement, but model authors had no stable TM/QM-level contract for hiding sensitive semantic fields from metadata and query execution by user role, group, permission, namespace, or security context.

This work adds a declarative `fieldPermissions` layer to both TableModel and QueryModel definitions. The resolved output is still fed into the existing `fieldAccess` enforcement path, so SQL/query behavior and metadata visibility share the same effective allowlist.

## Contract

`fieldPermissions` supports:

```js
{
  defaultVisible: false,
  visibleFields: [
    {
      when: { hasAnyRole: ['finance-manager'] },
      fields: ['orderId', 'salesAmount', 'grossMargin']
    }
  ],
  hiddenFields: [
    {
      when: { namespaceIn: ['public'] },
      fields: ['grossMargin']
    }
  ]
}
```

Resolution rules:

- TM permissions are the upper bound for fields owned by that TM.
- QM permissions can only narrow or select within the TM upper bound; they cannot expose a field hidden by TM.
- Runtime `fieldAccess` can only narrow the model-derived allowlist.
- `deniedColumns` are mapped back to QM fields through physical-column mapping and then removed from the effective allowlist.
- `hiddenFields` wins over `visibleFields`.
- Dimension suffixes such as `$id` and `$caption` normalize to the base dimension field.
- Request calculated-field aliases are included in the permission universe, but their dependencies remain governed by the normal field-access checks.
- Unknown fields and unsupported predicates fail closed.

Supported `when` predicates:

- `and`, `or`, `not`
- `hasAnyGroup`, `hasAllGroups`
- `hasAnyRole`, `hasAllRoles`
- `hasAnyPermission`, `hasAllPermissions`
- `namespaceIn`, `profileIn`
- `contextEq`, `contextIn`

## Code Inventory

| Path | Change |
|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/permission/FieldPermissionsDef.java` | Adds the declarative permission block. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/permission/FieldPermissionRuleDef.java` | Adds one conditional visible/hidden rule. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/DbModelDef.java` | Loads TM-level `fieldPermissions`. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/query/DbQueryModelDef.java` | Loads QM-level `fieldPermissions`. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/TableModel.java` | Exposes TM-level permissions through the SPI. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/QueryModel.java` | Exposes QM-level permissions through the SPI. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/permission/FieldPermissionResolver.java` | Resolves TM, QM, runtime, and physical-column permission layers into one effective allowlist. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelFieldPermissionResolveStep.java` | Applies the resolved allowlist before existing query field-access enforcement. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticServiceV3Impl.java` | Uses the same resolved allowlist for semantic metadata output. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java` | Carries security context into the semantic query path. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/permission/FieldPermissionResolverTest.java` | Unit coverage for resolver contracts and fail-closed cases. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/FieldAccessPermissionStepTest.java` | Regression coverage for normalized field-access enforcement. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/FieldAccessPermissionIntegrationTest.java` | Integration coverage for query execution permission behavior. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/SemanticServiceV3Test.java` | Metadata coverage for TM/QM field permission filtering. |

## Verification

2026-06-13 local gate:

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=FieldPermissionResolverTest,FieldAccessPermissionStepTest,FieldAccessPermissionIntegrationTest,SemanticServiceV3Test,PivotOuterResponseCacheTest,PivotOuterCacheTelemetryTest,PivotIntegrationTest,PivotCascadeGenerateSqlParityIntegrationTest'
```

Result:

- `Tests run: 138, Failures: 0, Errors: 0, Skipped: 1`.
- Covered field permission resolver, runtime enforcement, semantic metadata filtering, and the concurrently changed Pivot cache surface.

2026-06-13 deniedColumns precedence regression:

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=FieldAccessPermissionIntegrationTest$ModelPermissionAndDeniedColumnsTests#modelFieldPermissionsAllowedButDeniedColumnsRejected'
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=FieldAccessPermissionIntegrationTest'
```

Result:

- Targeted nested regression passed: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.
- Full integration class passed: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`.
- Verified that TM/QM `fieldPermissions` can allow `salesAmount`, but runtime `deniedColumns` for `fact_sales.sales_amount` still removes the field from the effective allowlist and rejects the query.

## Remaining Boundary

- This is field visibility and execution permission governance, not row-level access control.
- No arbitrary script predicate is supported; predicates are intentionally controlled and fail closed.
- Frontend consumers still need to verify their metadata display path if they cache schema separately from semantic metadata.
- Cross-bundle signed model freshness is not part of this workitem.
