---
type: bug
version: runtime-api-launcher-v0.1.1
source: github-issue
issue: GH-109
url: https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/109
severity: major
status: ready-for-verification
owner: foggy-data-mcp-bridge
created: 2026-07-01
updated: 2026-07-01
---

# BUG: MCP initialize response changes numeric JSON-RPC id to string

## Source

- GitHub Issue: GH-109
- Reported environment: `foggy-ai-analysis v0.1.0`, Java launcher `runtime-api-launcher-v0.1.1`, runtime URL `http://127.0.0.1:18066`, namespace `salesdrop`

## Phenomenon

The MCP `initialize` request uses numeric JSON-RPC id `0`, but the response serializes the id as string `"0"`. Strict MCP clients reject the startup handshake.

## Expected

JSON-RPC `id` must preserve the caller-provided JSON type. Numeric id `0` must be returned as JSON number `0`; string ids remain strings.

## Actual

`McpRequest.id` and `McpResponse.id` were typed as `String`, causing Jackson to coerce numeric request ids to string values.

## Impact

MCP startup fails for strict clients that compare JSON-RPC ids by both value and type.

## Fix Checklist

- Change MCP request/response id fields to `Object`.
- Change response factory methods to accept `Object id`.
- Add serialization regression tests for numeric and string ids.

## Verification

- Targeted test: `McpJsonRpcIdTypeTest`
- Command: `mvn -pl foggy-dataset-mcp -Dtest=McpJsonRpcIdTypeTest,ListModelsToolTest test`
