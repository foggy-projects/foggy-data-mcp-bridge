# Field binding contract gates

Run the SQLite contract suite (no external database):

```bash
python scripts/field-binding/run_gate.py --database sqlite --output target/field-binding-sqlite
```

Run the same naming, permission, ordering and pivot contracts on a real database:

```bash
python scripts/field-binding/run_gate.py --database sqlserver --output target/field-binding-sqlserver
```

Supported databases: sqlite, mysql57, mysql8, postgres, sqlserver. External lanes create a unique,
labelled Docker container on a dynamic loopback port, initialize repository fixtures, and remove only
that owned container on completion. Use a new output directory for each run. No Maven clean/install.
`--external` uses SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD
instead; that database must already contain the repository fixtures. The tests create/drop their own
regression tables, so use a dedicated test database. `--maven` selects the executable; `--offline`
is suitable only after dependencies are available locally.

The gate validates exit status, nonzero test execution, every required test class, and zero failures,
errors and skips. Database identity is checked through actual JDBC metadata by the existing parity IT.
Raw local fixture/Maven logs can contain runtime configuration; CI publishes only the summary.
Member contracts include nested target-table results, renamed input fields, canonical synthetic identity,
source/model/field/physical permissions, root subtree compatibility and same-directory detached validation.

Exercise an actual launcher JAR with isolated SQLite data and Runtime API:

```bash
python scripts/field-binding/artifact_smoke.py --jar path/to/launcher.jar --output target/field-binding-artifact
```

The smoke covers field names, aliases, schema, window permissions, transitive formulas, ordinary and
DISTINCT ordering, pivot TopN/non-additive totals, and a real model refresh. It checks an independent SQL
oracle, binds only loopback, isolates registries/cache files, and stops its own JVM. It does not touch
an existing runtime. It also checks nested members through both RuntimeEnvelope and legacy RX routes,
hidden member fields, and repeated validation of a registered directory without publishing a catalog.
Pass `--cli-source /path/to/foggy-runtime-cli/src` to also verify `members list` and its exit status.
Python assertions must be enabled.

`runtime_launcher_publish.py` runs this gate before any external tag/release operation. Evidence is
preserved under `.foggy-runtime/field-binding-smoke/`; the six release assets remain unchanged.
The `Field Binding Contracts` workflow runs SQLite/four-database gates for relevant changes and checks
published launcher checksums and runtime behavior on release or manual dispatch.

For member policy, Viewer authorization/current-generation refresh, and detached directory identity:

```bash
python scripts/field-binding/member_policy_smoke.py --jar path/to/launcher.jar --output target/member-policy-artifact
```

This gate asserts nested/externally injected hierarchy denial, descendant permission inheritance,
all five hierarchy operators, renamed Viewer selection round-trips, identity propagation to selected
values, real bundle refresh, and TM/QM conflicts versus same-resource subdirectory deduplication.
It owns an isolated SQLite runtime and stops only that JVM. The database matrix also includes
`MemberPolicyRegressionTest` (ordinary/member SQL, field/window dependencies and detached isolation).

Compose JOIN output binding and input pagination (#150/#151) are covered by
`ComposeBindingPaginationRegressionTest` in every SQLite/four-database lane. It checks actual rows,
both input sides, zero/unbounded/offset windows, aggregates, subquery filters, window results,
UNION and derived windows, G10 on/off and field permissions. Runtime HTTP parity is verified with:

```bash
python scripts/field-binding/compose_binding_smoke.py --jar path/to/launcher.jar --output target/compose-binding-artifact
```

The gate compares validate and preview SQL/params, checks executed row sets and ordinary-query
pagination, and verifies invalid JOIN diagnostics in all three modes. It preserves responses and
stops only its isolated loopback JVM. It also checks that a zero limit cannot bypass
hidden-field permissions, including a request-level alias.

`runtime_launcher_publish.py` requires the Compose gate before creating a tag or Release.
The publisher verifies all 19 named checks, all three modes, JVM shutdown and the exact
JAR SHA-256; missing, duplicate, stale or incomplete evidence blocks publication.
Reports are retained under `.foggy-runtime/compose-binding-smoke/`, outside release assets.
