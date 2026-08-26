# Foggy Runtime Launcher @RELEASE_VERSION@

This package is the standard dev/test launcher for Foggy Runtime API, MCP, and the embedded Analytics Console.

## Build identity

- Source commit: `@SOURCE_COMMIT@`
- Java project version: `@JAVA_PROJECT_VERSION@`
- Maven profile: `runtime-api`
- Runtime API readiness endpoint: `/api/v1/capabilities`

## Quick start

The default remains the `lite` profile. Analytics Console is present in the JAR but disabled.

PowerShell:

```powershell
.\start-foggy-runtime.ps1
```

Bash:

```bash
chmod +x ./start-foggy-runtime.sh
./start-foggy-runtime.sh
```

## Enable Analytics Console

No separate Node.js or frontend installation is required. The Console SPA and backend are embedded in the launcher JAR.

PowerShell:

```powershell
.\start-foggy-runtime.ps1 -AnalyticsConsole
```

Bash:

```bash
ANALYTICS_CONSOLE_ENABLED=true ./start-foggy-runtime.sh
```

After readiness succeeds, open `http://127.0.0.1:18066/analytics-console/`. The Console catalog and Function traces are stored below the launcher's work directory unless their paths are overridden.

FAP integration is optional and remains disabled by default. Enabling Console does not provision, publish, or mutate FAP resources. A FAP-enabled deployment must separately supply its FAP URL, binding, callback authorization, Skill, Capability, and Function catalog. Do not put credentials in this package or in its manifest.

An authenticated Console administrator can export the credential-free FAP handoff bundle from `GET /analytics-console/api/v1/integrations/fap/question-publication`. A host-managed FAP publisher may pull that bundle and append missing immutable Function, Skill, and Capability revisions. The endpoint is read-only and never applies FAP changes.

Useful port overrides:

```powershell
.\start-foggy-runtime.ps1 -Port 18166 -AnalyticsConsole
```

```bash
PORT=18166 ANALYTICS_CONSOLE_ENABLED=true ./start-foggy-runtime.sh
```

This launcher starts without an AI provider configuration. Deterministic Runtime API, MCP tools, and the direct Analytics Runtime lane remain available; AI-only tools and FAP-backed conversation endpoints stay disabled until their server-side integrations are explicitly configured.

This package is intended for local development, AI analysis demos, and regression validation. Its `static-dev-only` Console authority and `none-dev-test-only` Runtime API security must not be exposed as a production service. Production hosts must supply authenticated, host-managed subject/authority resolvers and normal network hardening.
