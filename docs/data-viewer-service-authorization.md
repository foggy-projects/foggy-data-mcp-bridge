# DataViewer MCP service authorization

`ViewerGrantService` stores a reusable link grant separately from each short-lived browser session. The browser exchanges a 256-bit random link secret, sent in a URL fragment and then a same-origin POST, for a per-view HttpOnly Cookie. Only SHA-256 secret digests are stored. The fragment is cleared before any other page action. The original MCP Authorization value is never sent to the browser or stored in the query/grant/session records.

## Configuration

```yaml
foggy:
  data-viewer:
    enabled: true
    base-url: https://analytics.example.com/data-viewer
    cache:
      storage: sqlite
      sqlite-path: /var/lib/foggy/viewer/query-context.sqlite
      ttl-minutes: 60 # Applies to ungranted query caches only.
    link-authorization:
      enabled: true
      link-ttl: 0s # Default: permanent. Example finite lifetime: 7d.
      session-ttl: 24h # Default: absolute 24 hours from issuance. Example: 8h.
      secure-cookie: true # Default; HTTPS. Local loopback demo explicitly uses false.
      cookie-name: foggy-viewer
```

Link TTL must be zero or positive. Session TTL must be at least one second. There is no idle timeout or two-hour cap. Cookies are HttpOnly, SameSite=Strict, scoped to `/data-viewer`, named per query ID. The standalone client sends `X-Foggy-View` for schema/metadata calls so tabs never choose another view's Cookie. Query-specific routes additionally check their own query ID. A Cookie cannot authorize a different namespace, model, projection or query.

The reusable flow is opt-in (`enabled: false` outside the demo profile). Enabling it requires a host `ViewerIdentityProvider`; absence fails startup. Its `verify` must authenticate the complete MCP credential against the authoritative registry and return a durable, non-secret credential reference plus a permission-version fingerprint. It must never return the credential as the reference. `revalidate` must consult the current registry/permissions on every exchange and request, rejecting lost identities or changed scope. `runtimeAuthorization` resolves a server-only reference/credential for the existing engine authorization chain, never an anonymous fallback. Production hosts implement this SPI against their own credential registry/vault; no unified personal login is required.

The synthetic demo adapter uses explicitly configured complete-header fingerprints mapped to service station scopes. Its server-internal references are protected by a process HMAC and sent only to the existing loopback model-permission callback. Rotating the process HMAC on restart does not break persisted links: a fresh internal reference is issued on the next request. Loaded credential mappings and permission versions are checked on every request; external static configuration changes take effect when the Launcher is restarted with the new configuration.

Creation also probes the actual original query through `QueryFacade` with the MCP authorization, so model, fields, query parameters and row authorization still apply. Subsequent queries, metadata and filter candidates go through the existing engine. Base `slice`, `having`, grouping, columns, calculations and parameters remain fixed; users may add allowed-field filters and sort/page within this result. Detail `slice` is never converted to `having`. A grouped grant cannot become an ungrouped detail query; create a separate authorized detail link. CSV export uses the same paginated data endpoint. Unscoped direct/member/download/preset APIs are rejected for these sessions. Candidates are the distinct values of the first 100 governed result rows, not an unrestricted member model.

## Persistence and revocation

SQLite defaults to `data-viewer/query-context.sqlite`, relative to the server working directory. Set an absolute persistent path for stable deployments and protect the directory with service-account filesystem permissions. Query contexts, link grants and browser sessions share this database. Permanent contexts use a sentinel in the existing NOT NULL SQLite expiry column, surfaced as `null`; the normal cleanup does not delete them. Finite contexts remain until their link expiry. Grant tombstones are retained, including expired/revoked state. Expired sessions are removed on exchange. Startup retains live grants and sessions.

Mongo query storage also supports expiry `null` (no Mongo TTL deletion) and uses a shared `MongoViewerGrantStore` with atomic revocation. The bounded legacy memory store cannot provide durable grants and fails startup for this feature. Custom shared storage may implement `ViewerGrantStore`.

`POST /data-viewer/api/link/revoke` accepts `{"secret":"<link secret>"}` in its body and the owning MCP Authorization header. Cookie possession alone is insufficient. Do not log the body or header. Revocation persists and makes every associated session fail its next request. Expired links behave the same way. Session expiration leaves a live link reusable. Permission or credential changes require a newly authorized link; the old snapshot is rejected.

The automatic Harness card now dispatches the registered `dataset.open_in_viewer` MCP tool through Harness's existing policy pipeline, preserving `having` and using the connector's existing headers/address. It supports a single unambiguous Foggy viewer MCP connector. It never falls back to anonymous `/query/create` or another managed Runtime port.
