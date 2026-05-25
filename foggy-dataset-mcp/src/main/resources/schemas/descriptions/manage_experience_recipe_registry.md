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
- `publish_validated` requires all publish gate evidence fields to be `passed` and requires these evidence artifact types: `owner_signoff`, `schema_validation`, `validation_report`, `positive_negative_examples`, `permission_scope`.
- Each evidence artifact must include `artifactType`, `artifactUri`, and `artifactHash`. `artifactUri` must use `foggy`, `https`, `s3`, or `oss`; `artifactHash` must use `sha256:<64 hex digest>`. `artifactId`, `artifactSignature`, `signedBy`, and `signedAt` should be provided when the caller has them.
- When server-side artifact resolution is enabled, every valid artifact reference must resolve successfully and the registry recomputes the content SHA-256 before publishing. Unresolvable content, path escape, or hash mismatch writes a blocked event and keeps the recipe as candidate.
- The built-in local resolver handles `foggy://` references under the configured artifact root. The built-in remote HTTP resolver handles only `https://` references when `foggy.mcp.experience-recipe.registry.remote-http.enabled=true` and the target host is explicitly listed in `remote-http.allowed-hosts`; userinfo, fragments, redirects, non-200 responses, and oversized responses are not accepted. For an internal artifact service, the resolver can also consume trusted response headers: `X-Foggy-Artifact-Object-Version`, `X-Foggy-Artifact-Object-Etag`, `X-Foggy-Artifact-Hash`, `X-Foggy-Artifact-Size`, and `X-Foggy-Artifact-Metadata-*` for namespace, tenant, owner, registry key, canonical recipe id, version, artifact type, and artifact hash.
- When `foggy.mcp.experience-recipe.registry.artifact-uri-policy.enabled=true`, every valid artifact URI must start with one of `artifact-uri-policy.allowed-uri-prefixes[]` after expanding `{namespace}`, `{tenant}`, `{registryKey}`, `{canonicalRecipeId}`, `{version}`, `{owner}`, and `{artifactType}` from the effective publication context. Empty prefixes or mismatched tenant/owner/recipe paths fail closed before content resolver calls.
- When `foggy.mcp.experience-recipe.registry.artifact-object-metadata-policy.enabled=true`, every valid artifact must include `objectVersion` or `objectEtag` and `objectMetadata` keys for `namespace`, `tenant`, `owner`, `registryKey`, `canonicalRecipeId`, `version`, `artifactType`, and `artifactHash`. Mismatched declared object metadata fails closed before content resolver calls. When `artifact-object-metadata-policy.require-resolved-object-metadata=true`, the registry uses object identity and metadata returned by the configured artifact resolver as the trusted facts; caller-declared metadata alone is not accepted.
- When server-side artifact signature verification is enabled, every valid artifact reference must resolve successfully and pass the configured verifier. The built-in verifier uses `sig:v1:ed25519:<keyId>:<base64url(signature)>`, verifies the artifact hash against resolved content, and binds the signature to namespace, tenant, registry key, canonical recipe id, recipe version, owner, artifact type, artifact URI, artifact hash, signer, and signing time. Missing verifier, unsupported algorithm, trust-key scope mismatch, expired/revoked key, invalid signature, or replayed payload writes a blocked event and keeps the recipe as candidate.
- The default Ed25519 verifier can be wired from `foggy.mcp.experience-recipe.registry.artifact-trust-keys[]`. Each trust key declares `keyId`, `publicKey`, allowed tenants, owners, artifact types, signed-by subjects, valid time window, and status. Empty scopes do not imply global permission.
- If publish evidence is incomplete, the registry writes a blocked event and does not publish the recipe.

Do not use this tool to search or select recipes for user questions.
