<p align="center">
  <img src="logo.png" alt="Foggy Data MCP Bridge" width="120">
</p>

<h1 align="center">Foggy Data MCP Bridge</h1>

<p align="center">
  A governed semantic layer that lets AI assistants query business data through MCP—without making raw database schemas the AI interface.
</p>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> ·
  <a href="https://foggy-projects.github.io/foggy-data-mcp-docs/en/">Documentation</a> ·
  <a href="https://github.com/foggy-projects/foggy-data-mcp-bridge/releases">Releases</a> ·
  <a href="https://github.com/foggy-projects/foggy-data-mcp-bridge/issues">Issues</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="Apache 2.0 License"></a>
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00.svg" alt="Java 17+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F.svg" alt="Spring Boot 3.4">
  <img src="https://img.shields.io/badge/MCP-JSON--RPC-7B61FF.svg" alt="MCP JSON-RPC">
</p>

## Overview

Giving an LLM direct access to database schemas and asking it to generate SQL
couples the AI to physical tables, dialect details, and fragile join logic.
Foggy puts a semantic and governance layer in between:

```text
AI client
   │  MCP / JSON Query DSL
   ▼
Foggy Runtime
   ├── TM/QM semantic models
   ├── namespace and field policies
   ├── query planning and dialect translation
   └── model lifecycle and Runtime API
   │
   ▼
MySQL · PostgreSQL · SQL Server · SQLite · optional MongoDB
```

AI clients work with business concepts such as revenue, customer, product, and
order date. Foggy resolves those concepts into governed, read-oriented queries
and handles joins, aggregation, and database dialects at runtime.

## Highlights

- **Semantic modeling** — define reusable table models (TM) and query models
  (QM) with FSScript.
- **MCP-native access** — expose analyst, business, and administrative tool
  surfaces over JSON-RPC.
- **Structured query DSL** — keep generated queries inside a model-aware
  contract instead of accepting arbitrary AI-generated SQL.
- **Runtime governance** — namespace isolation, semantic field allowlists,
  physical-column deny rules, and auditable tool execution.
- **Model lifecycle** — register bundles, validate models, refresh atomically,
  and inspect runtime capabilities through REST APIs.
- **Extensible backends** — relational databases in the core repository, with
  optional MongoDB, cache, vector, pre-aggregation, GraphQL, viewer, and Odoo
  integrations.

## Quick start

The published Runtime Launcher is the shortest path for local development and
evaluation. It requires Java 17 or newer and uses a local SQLite database by
default.

> The launcher is a dev/test distribution. Add production authentication,
> network controls, datasource governance, and deployment hardening before
> exposing Foggy outside a trusted environment.

### 1. Download and start the Runtime

The commands below pin launcher `0.1.17`. Check the
[releases page](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases)
for a newer version.

```bash
mkdir foggy-runtime && cd foggy-runtime

curl -fLO https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/download/foggy-runtime-launcher-v0.1.17/foggy-runtime-launcher-0.1.17.jar
curl -fLO https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/download/foggy-runtime-launcher-v0.1.17/start-foggy-runtime.sh
curl -fLO https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/download/foggy-runtime-launcher-v0.1.17/SHA256SUMS

grep -E 'foggy-runtime-launcher-0.1.17.jar|start-foggy-runtime.sh' SHA256SUMS | sha256sum -c -
chmod +x start-foggy-runtime.sh
./start-foggy-runtime.sh
```

Windows users can download `start-foggy-runtime.ps1` from the same release and
run it from PowerShell. The default service URL is
`http://127.0.0.1:18066`.

### 2. Verify the service

```bash
curl http://127.0.0.1:18066/readyz
curl http://127.0.0.1:18066/api/v1/capabilities
```

The capabilities response reports the Runtime API version, enabled features,
and `securityMode`. If a deployment reports `auth-code`, supply the configured
auth code when using its Runtime API.

### 3. Connect an MCP client

Use the analyst JSON-RPC endpoint and select a namespace with `X-NS`:

```json
{
  "mcpServers": {
    "foggy-ai-analysis": {
      "url": "http://127.0.0.1:18066/mcp/analyst/rpc",
      "headers": {
        "X-NS": "salesdrop"
      }
    }
  }
}
```

Some clients require an additional `"type": "http"` field. Keep runtime
metadata and datasource credentials outside `mcpServers`.

You can probe the endpoint before configuring a client:

```bash
curl -X POST http://127.0.0.1:18066/mcp/analyst/rpc \
  -H 'Content-Type: application/json' \
  -H 'X-NS: salesdrop' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### 4. Add a datasource and semantic models

A useful query environment needs:

1. a namespace and datasource binding;
2. TM/QM model files in a model bundle;
3. successful model validation and refresh;
4. an MCP client connected with the matching `X-NS` header.

Follow the
[AI analysis onboarding guide](https://foggy-projects.github.io/foggy-data-mcp-docs/en/)
for the bundled SQLite example or for connecting your own datasource. See the
[semantic-layer syntax reference](https://foggy-projects.github.io/foggy-data-mcp-docs/en/whitepaper/v1.0/semantic-layer-syntax-reference.html)
and
[Query DSL reference](https://foggy-projects.github.io/foggy-data-mcp-docs/en/whitepaper/v1.0/query-dsl-syntax-reference.html)
when building models and queries.

## Query example

The AI submits semantic fields rather than physical joins:

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": [
      "product$brand",
      "sum(salesAmount) as totalSalesAmount"
    ],
    "slice": [
      {
        "field": "salesDate$caption",
        "op": "[)",
        "value": ["2026-01-01", "2027-01-01"]
      }
    ],
    "groupBy": ["product$brand"],
    "orderBy": ["-totalSalesAmount"],
    "limit": 10
  },
  "mode": "execute"
}
```

Foggy resolves the model relationships, applies configured access rules, plans
the aggregation, translates the query for the selected backend, and returns a
structured result.

## Run the source Docker demo

The repository also contains an e-commerce demo backed by MySQL. This path
builds the current source tree and requires an OpenAI-compatible API key.

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
cp .env.example .env
# Edit .env and set OPENAI_API_KEY and, if needed, OPENAI_BASE_URL.
docker compose up -d --build
curl http://localhost:7108/actuator/health
```

See the [Docker demo guide](docker/demo/README.md) for models, configuration,
logs, and cleanup commands.

## Repository layout

| Path | Responsibility |
| --- | --- |
| `foggy-dataset-model-api` | Stable query DTOs and backend SPI |
| `foggy-dataset-model-core` | Provider catalog and fail-closed governance |
| `foggy-dataset-model-engine` | TM/QM loading, planning, refresh, and execution |
| `foggy-runtime-api` | Datasource, namespace, bundle, model, and query APIs |
| `foggy-dataset-mcp` | MCP tools, discovery, dispatch, and audit |
| `foggy-mcp-launcher` | Executable Spring Boot assembly |
| `foggy-dataset` | JDBC access and relational dialects |
| `foggy-fsscript` | Script engine used by semantic models |
| `foggy-dataset-demo` | Example schemas, data, and semantic models |
| `addons` | Optional backend and product integrations |

For the complete module boundaries and lifecycle, read the
[canonical architecture guide](docs/architecture/README.md).

## Development

Prerequisites:

- JDK 17+
- Maven 3.9+
- Docker Compose v2 for integration environments

Run the unit-test reactor:

```bash
mvn -B -ntp test -DskipITs
```

Run focused tests while developing a module:

```bash
mvn -B -ntp -pl foggy-runtime-api,foggy-dataset-mcp -am test -DskipITs
```

Integration tests are opt-in and may require external databases:

```bash
mvn -B -ntp verify -DskipITs=false
```

Please open an issue before a large architectural change and submit changes
through a focused pull request. Never commit credentials, connection strings,
or logs containing sensitive data.

## Documentation and support

- [User documentation](https://foggy-projects.github.io/foggy-data-mcp-docs/en/)
- [Architecture](docs/architecture/README.md)
- [Releases](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases)
- [Bug reports and feature requests](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues)
- [Discussions](https://github.com/foggy-projects/foggy-data-mcp-bridge/discussions)
- [Foggy Odoo Bridge](https://github.com/foggy-projects/foggy-odoo-bridge)

## License

Copyright © Foggy Data MCP Bridge contributors.

Licensed under the [Apache License 2.0](LICENSE).
