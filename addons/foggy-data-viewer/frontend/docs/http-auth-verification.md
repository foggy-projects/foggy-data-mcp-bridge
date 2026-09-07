# HTTP authentication verification — 2026-09-07

Target: `foggy-data-viewer@1.0.1-beta.47`.

- `npm test`: 26 files, 422 tests passed. Includes 8 new HTTP regression cases
  exercising 17 public API functions through real Axios instances and a test adapter.
- `npm run build:lib`: ES/UMD bundles, TypeScript declarations and package artifact
  checks passed. Public entry exports the configuration function and four HTTP types.
- Vite consumer production build: passed, emitted a distinct `sdk-vendor` chunk.
- Playwright production preview: 1 test passed using installed Microsoft Edge.
  Static SDK import precedes host configuration. All 17 API calls were exercised
  with token A, B, absent token and HTTP 401 (68 browser requests total).
  Request-level X-NS remained present; each 401 notified once and remained rejected.
- Package inspection: authentication integration guide and declaration files included.

Environment issues: original port 53175 was rejected by Windows, so the test uses
18175. Playwright Chromium download failed with a TLS bad-record error; installed
Edge was used via `PLAYWRIGHT_CHANNEL=msedge` instead.

Scope: browser HTTP responses are intercepted test fixtures, not the live TMS
backend. TMS router/store/toast integration, cross-origin CORS, multiple independent
SDK bundles and concurrent per-user SSR configuration were not integration-tested.
Node `foggy-gen`'s build-time metadata fetch and the standalone demo application's
own fetch are outside the public browser SDK HTTP configuration.

See `http-auth.md` for request-header priority, callback semantics, limitations and
the TMS migration procedure. No backend authentication or TMS generated files changed.
