# Standard Foggy Runtime Launcher release

The standard `foggy-mcp-launcher` executable JAR contains Analytics Runtime API and the
Analytics Console backend, SPA, and FAP question Skill delivery. Both Analytics surfaces
remain disabled under the default `lite` runtime profile.

## Build and package

Run from a clean `foggy-data-mcp-bridge` checkout:

```bash
python3 scripts/release/runtime_launcher_package.py package \
  --release-version <launcher-version> \
  --output-dir <new-empty-release-directory>
```

The command first cleans the two artifact-owning modules, then packages the affected
reactor:

```bash
mvn -pl addons/foggy-analytics-console,foggy-mcp-launcher clean
mvn -Pruntime-api -pl foggy-mcp-launcher -am package -DskipITs=true
```

The scoped `clean` is mandatory. It avoids deleting repository-owned evidence below the
aggregator's `target/`, while forcing `foggy-analytics-console` to run `npm ci`, typecheck,
frontend unit tests, and the Vite build before the addon JAR is nested in the executable
launcher. A Console source change therefore cannot reuse an older `target/classes` SPA.
The command fails on a dirty worktree, a non-empty output directory, or any build-induced
change to tracked files.

Development-only `--allow-dirty` or `--skip-java-tests` runs are marked
`releaseReady=false` in the generated manifest and must not be published. Skipping Java
tests does not skip the Console frontend rebuild.

## Release gates

The packager verifies all of the following before writing release assets:

- exactly one nested `foggy-analytics-console` JAR;
- exactly one nested `foggy-analytics-runtime-api` JAR;
- conditional `application-analytics-console.yml` activation config;
- Console `index.html`, theme initialization, and all HTML-referenced SPA files;
- no stale, unreferenced Vite `assets/index-*` files;
- the FAP question Skill, DSL reference, Compose/CTE reference, and Function schema delivery;
- no `expectedModelRevision` in the embedded live Function schema;
- outer launcher, nested Console, and nested Analytics Runtime SHA-256 digests.

To inspect any locally built executable JAR without assembling a release directory:

```bash
python3 scripts/release/runtime_launcher_package.py verify \
  --jar foggy-mcp-launcher/target/foggy-mcp-launcher-<java-version>.jar
```

The output directory contains the executable JAR, Bash and PowerShell start scripts,
README, `runtime-launcher-manifest.json`, and `SHA256SUMS`. The manifest advertises
`features.analyticsConsole.embedded=true`, `enabledByDefault=false`, the activation
contract, and nested component digests.

## Runtime smoke

Default profile:

```bash
./start-foggy-runtime.sh
foggy-runtime --base-url http://127.0.0.1:18066 wait-ready --timeout-seconds 90
```

`/analytics-console/index.html`, `/analytics-console/api/v1/session`, and
`/analytics/api/v1/capabilities` must be unavailable.

Explicit Console opt-in:

```bash
PORT=18166 ANALYTICS_CONSOLE_ENABLED=true ./start-foggy-runtime.sh
foggy-runtime --base-url http://127.0.0.1:18166 wait-ready --timeout-seconds 90
```

`/analytics-console/` and `/analytics/api/v1/capabilities` must succeed. FAP remains
disabled unless a deployment separately provides all server-side FAP bindings and
credentials. Launcher startup never provisions or mutates FAP state and never migrates
historical business data.
