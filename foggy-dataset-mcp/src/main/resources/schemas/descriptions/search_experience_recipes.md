# dataset.search_experience_recipes

Search the runtime experience recipe registry for validated recipes that are active for discovery.

Use this tool before generating a fresh DSL, SQL, CTE, or memory-grid plan when the question looks like a recurring business analysis pattern. The tool only returns recipes whose registry status is `validated` and whose `activeForDiscovery` flag is true.

Before returning recipes it applies deterministic registry governance:

1. namespace and tenant scope filters
2. permission tag filters from MCP context headers
3. optional owner/delegation filters
4. canonical recipe dedupe before `limit`
5. conflict summary when more than one canonical group remains

Optional filters:

- `registryKey`: performs exact runtime lookup for a known recipe key while still enforcing `validated`, `activeForDiscovery`, namespace, tenant, permission, and owner governance.
- `businessType`: narrows results to a business problem family.
- `route`: narrows results to a known execution route such as `DSL_CTE`, `SQL`, or `MEMORY_GRID`.
- `namespace` / `tenantId`: local or evaluation fallback only; production callers should pass these through MCP context or headers.
- `permissionTags`: local or evaluation fallback only; production authorization should come from `X-Roles` or `X-Permission-Tags`.
- `ownerRoles`: optional owner/delegation filter; production delegation should come from `X-Recipe-Owner-Roles`.
- `limit`: caps returned rows.

Do not use draft, candidate, rejected, or deprecated recipes for routing decisions.
If `governanceDecision` is `conflict_exposed`, do not silently pick the first recipe. Hand the conflict to ER0/L2 or human review according to the router policy.
