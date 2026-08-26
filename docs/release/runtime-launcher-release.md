# Standard Foggy Runtime Launcher release

The standard `foggy-mcp-launcher` executable JAR contains Analytics Runtime API and the
Analytics Console backend, SPA, and FAP question Skill delivery. Both Analytics surfaces
remain disabled under the default `lite` runtime profile.

## Build and package

For a dirty-worktree deployment candidate, run the focused Analytics/FAP lane, Console typecheck,
and production frontend build in one command:

```bash
python3 scripts/release/runtime_launcher_package.py package \
  --candidate \
  --release-version <launcher-version> \
  --output-dir <new-empty-candidate-directory>
```

Candidate manifests always report `releaseReady=false`. Their `validation` object records the
validation mode, Java/Console test scope, and per-stage duration/status. Candidate mode does not
replace the clean formal release lane.

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

For the complete tag, GitHub Release, and OBS workflow, use the repository publisher instead of
assembling or rewriting assets in a caller-owned script:

```bash
python3 scripts/release/runtime_launcher_publish.py \
  --release-version <launcher-version>
```

Use `--skip-upload` for a local-only formal package and both runtime smokes. Use
`--reuse-package --output-dir <existing-release-directory>` only to revalidate an existing exact
six-asset package after an interrupted external publication. The publisher requires clean
`origin/main`, creates an annotated tag and draft GitHub Release conservatively, checks the exact
asset set and GitHub digests, publishes the draft, then waits for the public OBS replay. Existing
tags or releases are never replaced unless the caller has explicit replacement authority; moving
an existing tag additionally requires `--retarget-tag`.

Development-only `--allow-dirty` or `--skip-java-tests` runs are marked
`releaseReady=false` in the generated manifest and must not be published. Skipping Java
tests does not skip the Console frontend rebuild.

Before either candidate or release packaging accepts the JAR, it runs
`fap_question_delivery.py check --skip-compile` against the compiled Java catalog. This prevents
FunctionRef, Skill revision, schema delivery, and publication digest drift without maintaining a
second digest table in the Python packager.

## Release gates

The packager verifies all of the following before writing release assets:

- exactly one nested `foggy-analytics-console` JAR;
- exactly one nested `foggy-analytics-runtime-api` JAR;
- conditional `application-analytics-console.yml` activation config;
- Console `index.html`, theme initialization, and all HTML-referenced SPA files;
- no stale, unreferenced Vite `assets/index-*` files;
- the FAP question Skill, DSL reference, Compose/CTE reference, and Function schema delivery;
- when supplied by the Console build, the host-managed Function publication manifest and its exact
  five schema/projection digests (previously published launcher `0.1.18` remains valid without this
  later manifest);
- no `expectedModelRevision` or `MODEL_REVISION_CONFLICT` in embedded live Analytics artifacts;
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

Before a GitHub Release is copied to OBS, the shared sync script performs an offline exact-asset
gate that does not require credentials:

```bash
bash scripts/sync-release-to-obs.sh \
  --component launcher \
  --tag foggy-runtime-launcher-v<version> \
  --repo foggy-projects/foggy-data-mcp-bridge \
  --source-dir <six-asset-release-directory> \
  --validate-assets-only
```

The live workflow writes `foggy-runtime/latest/launcher.json` first, verifies its public replay,
then reconciles the derived shared `foggy-runtime/latest.json` from the independent CLI, Skills,
and Launcher component indexes. This prevents concurrent releases in the three repositories from
silently losing another component through a shared read/modify/write race.

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

FAP publication is a separate host-owned operation. Use
[`fap-analytics-question-publication.md`](fap-analytics-question-publication.md) and the read-only
`fap_question_publication_gate.py`; the launcher release workflow intentionally has no FAP apply
credential or management call.
