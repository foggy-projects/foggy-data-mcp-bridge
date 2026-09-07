# HTTP authentication integration

Available from `foggy-data-viewer@1.0.1-beta.47`. Import the configuration function
from the package root. Configure before mounting the application or issuing SDK
requests; importing the SDK earlier (including in a production vendor chunk) is safe.

```ts
import { configureDataViewerHttp } from 'foggy-data-viewer'

configureDataViewerHttp({
  getHeaders: () => {
    const token = localStorage.getItem('x3_tms_token')
    return token ? { Authorization: `Bearer ${token}` } : {}
  },
  onUnauthorized: ({ status, method, path }) => {
    // Call the host application's existing session-expiry handler here.
    // Only status/method/path are supplied; no token or Axios error is supplied.
    handleSessionExpired({ status, method, path })
  }
})
// app.mount('#app') after configuration
```

`handleSessionExpired` above denotes a host-owned function, not an SDK export.
The SDK has no storage key, router, store, toast, token-refresh or redirect policy.
All query, direct query, metadata, schema, filter/member, list-preset CRUD and
table-default APIs use the same configuration. Existing generated callers need no edits.

## Headers and lifetime

`getHeaders` supports synchronous or asynchronous results and runs for each request.
SDK configuration never copies dynamic values into client defaults. Token A → B →
logout therefore affects the next request without recreating clients. An already
dispatched request retains its headers. If the provider throws or rejects, that
request rejects without being sent; no 401 notification is emitted.

Header names are case-insensitive. Precedence is client defaults < dynamic headers
< per-request headers. `undefined` means no override; `null` deletes the lower-priority
header. The default JSON Content-Type remains unless overridden. Every public HTTP
API accepts an optional last `DataViewerHttpRequestOptions` argument:

```ts
await fetchQueryDataDirect('TenantOrgManagementQuery', request, {
  headers: { 'X-NS': 'tms-biz' }
})
```

For unchanged generated callers, a shared namespace may also be returned by
`getHeaders`. Explicit per-request Authorization intentionally takes precedence;
do not place a captured token there or in global Axios defaults. Remove the old
Axios patch instead of running both mechanisms together.

Each `configureDataViewerHttp` call replaces the complete configuration, rather than
merging callbacks. `{}` resets it. Configuration does not install more interceptors.
It is shared within one loaded SDK module instance. Independently bundled copies
must each be configured; SSR applications must not share user-specific global
configuration across concurrent requests.

## HTTP 401 and errors

Each HTTP 401 invokes `onUnauthorized` once with `{ status: 401, method, path }`.
The path excludes URL credentials, query and fragment. Context excludes headers,
body and raw error; avoid putting secrets in URL path segments yourself.
The callback is a notification: it is not awaited and does not delay failure.
Synchronous exceptions and rejected callback promises are discarded without logging,
so they cannot mask the original Axios rejection or cause an unhandled rejection.
Host callbacks should handle their own failures when reporting is necessary.

The original HTTP error still reaches callers. Existing 404/410 mappings and API
response unwrapping are unchanged. HTTP 200 containing a business `code: 401` is
not treated as an HTTP 401 by this hook. No automatic retry or token refresh occurs.
Concurrent failed requests each notify; make session-expiry handling idempotent
in the host (for example, one pending login redirect until a new session begins).

## TMS migration from setupGlobalAxios.ts

1. Upgrade the SDK and configure it during application bootstrap after host
   services are initialized, before mounting. Keep TMS business HTTP client setup.
2. Extract the old patch's login-expiry logic into a host function, including its
   existing token removal, toast and router handling. Invoke it from this hook and
   the TMS business client's 401 handler; share their redirect/toast deduplication.
3. Keep ordinary query error presentation in the existing table `onQueryError` hook
   (`QueryHooks.onQueryError`). Treat HTTP 401 there as already notified and return
   `true` to suppress the table's fallback handling; show other failures once and
   return `true`. The HTTP API promise itself still rejects. Existing preset UI
   error presentation remains in place, so prefer routing/session clearing without
   an additional global toast if the operation already presents an error.
4. Remove the import and monkey patch from `setupGlobalAxios.ts` once its remaining
   responsibilities are explicitly installed on their owning business clients.
   Do not replace the SDK transport with an already-unwrapped TMS HTTP client.
5. Validate login, token renewal, logout and concurrent 401 responses in the TMS
   production bundle. This SDK release does not edit TMS generated files.

The hook owns login expiry only; it is not a replacement for all business-client
error presentation. No custom HTTP-client injection contract is introduced.

Example host table hooks (the host supplies `showExistingErrorToast`):

```ts
import axios from 'axios'
import type { QueryHooks } from 'foggy-data-viewer'

const queryHooks: QueryHooks = {
  onQueryError: (_context, error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) return true
    showExistingErrorToast(error.message)
    return true
  }
}
// Pass queryHooks through the existing table/wrapper hook configuration.
```

## Reproducing SDK verification

From `frontend`: `npm ci`, `npm test`, `npm run build:lib`.
From sibling `verification-app`: `npm ci`,
`npx vite build --config vite.http-auth.config.ts`, then
`npx playwright test --config playwright.http-auth.config.ts`.
Install Chromium with `npx playwright install chromium` if needed.
Alternatively set `PLAYWRIGHT_CHANNEL=msedge` to use installed Microsoft Edge.
The browser test consumes the built public SDK entry, asserts a separate vendor
chunk exists, and intercepts real browser HTTP requests to verify all 17 API calls
through token changes and 401 responses against the production preview server.
