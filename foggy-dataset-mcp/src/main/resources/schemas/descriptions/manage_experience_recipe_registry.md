# dataset.manage_experience_recipe_registry

Manage the runtime experience recipe registry lifecycle.

Use this tool only for authoring, review, publishing, rollback, or retirement of experience recipes. Runtime routing and analysis should use `dataset.search_experience_recipes` first and then normal query execution tools.

Supported lifecycle operations:

- `create_draft_stub`: create a non-discoverable draft registry row.
- `promote_draft_to_candidate`: move a draft into candidate review.
- `publish_validated`: publish a candidate as validated and active for discovery.
- `deprecate_recipe`: retire a validated recipe from discovery.
- `reject_candidate`: reject a candidate recipe.
- `rollback_validated_to_candidate`: remove a validated recipe from discovery and return it to candidate review.

Governance rules:

- The MCP actor must be `registry_admin`. Production callers should pass this through MCP context or the `X-Registry-Actor-Role` header. The `actorRole` argument is only a local/evaluation fallback.
- Every mutation requires `idempotencyKey`.
- Use `expectedFromStatus`, `expectedFromActiveForDiscovery`, and `expectedRecordVersion` when the caller has read the current row and wants compare-and-set protection.
- `publish_validated` requires all publish gate evidence fields to be `passed`; otherwise the registry writes a blocked event and does not publish the recipe.

Do not use this tool to search or select recipes for user questions.
