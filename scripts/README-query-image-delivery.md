# Query image delivery acceptance

`verify-query-image-delivery.py` runs the actual packaged launcher on Linux with
Java 17 in headless mode. It uses Python standard-library HTTP/SQLite helpers for
test orchestration; rendering stays entirely inside Java. No Docker renderer,
system Chinese-font installation, AI API, or Python rendering library is used.

Build the launcher with the `runtime-api` Maven profile so public fixture setup,
model validation, query execution and CLI capability discovery are available.
The normal launcher build deliberately omits that optional Runtime API adapter.
This acceptance profile provides public fixture configuration and validation.
The PNG MCP tool adds no Runtime API dependency: `export-image` uses MCP
`tools/list` and `tools/call`. Product users do not need to choose a Maven profile
or run an external test runtime to export query images.

After the relevant unit and integration checks have passed, package with:

```bash
mvn -B -ntp -Pruntime-api -pl foggy-mcp-launcher -am \
  -DskipUnitTests=true -DskipITs=true package
```

This repository binds Surefire to `skipUnitTests`; `-DskipTests` alone does not
skip its unit suites. Select a Java 17 `JAVA_HOME` for this build.

Requirements: Java 17, Python 3, `fc-list`, and a DejaVu font directory at
`/usr/share/fonts/truetype/dejavu`. The script isolates fontconfig to that Latin
font directory, verifies zero Chinese font matches, and checks the Noto Sans SC
font and OFL license inside the launcher. It never removes shared fonts.

```bash
python3 scripts/verify-query-image-delivery.py \
  --root .acceptance/query-images-delivery/linux \
  --jar foggy-mcp-launcher/target/foggy-mcp-launcher-9.3.0-SNAPSHOT.jar \
  --port 19073 --serve
```

The script creates only synthetic data and isolated registry state under
`--root`, with fresh `runtime-state/run-*` registries on each run so previous
bundle registrations cannot affect detached model validation. Both
`image_delivery_a` and `image_delivery_b` contain
`DeliverySalesQueryModel`, with different SQLite rows. Real engine query rows
are compared against an independent SQLite SELECT before PNG export. It checks
native MCP images, namespace isolation, model/field/row permissions, empty
results and safe metadata. Sanitized JSON, logs and PNG evidence go under
`--root`; no generated authorization value is persisted.

`--serve` retains the localhost launcher for installed CLI or MCP clients after
printing `READY_FOR_CLIENTS`. Generate a test-only opaque identity in the client
environment (the helper always generates its own synthetic identity and never
consumes the global authorization environment variable):

```bash
export FOGGY_RUNTIME_AUTHORIZATION="query-image-fixture-reader:$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
```

Use `query-image-fixture-east:` plus a random UUID for the restricted reader
(three 华东 rows). Missing or other prefixes are denied. These fixture prefix
resolvers are test scaffolding, not production identity validation.

The MCP endpoint is `http://127.0.0.1:19073/mcp/analyst/rpc`; send `X-NS` and the
opaque `Authorization` header. Query payloads are saved as `query.json` and
`query-empty.json`. Image axes use `month` and `salesAmount`; the other columns
are `targetAmount` and `orders`. Supply a title, `unit: "万元"`, and PNG output.

Create `--root/stop-runtime` once all clients finish. The helper stops only its
own launcher process. Remove that marker before another `--serve` run.
