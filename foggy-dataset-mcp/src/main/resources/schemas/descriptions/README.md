# MCP Tool Description Maintenance

This directory is the source of truth for MCP tool descriptions.

## Ownership

- Java `foggy-dataset-mcp/src/main/resources/schemas/descriptions/` owns `query_model_v3*.md`.
- Python engine and Odoo bridge copies must be synchronized from this directory unless a versioned work item records an explicit runtime-specific exception.
- Do not make Python or Odoo-only prompt contract changes first.

## Query Model Prompt Changes

Before changing `query_model_v3.md`, `query_model_v3_basic.md`, or `query_model_v3_no_vector.md`:

1. Read `query_model_v3_CHANGELOG.md`.
2. Add a new changelog entry explaining the user-facing problem, the contract rule being changed, affected files, and validation evidence.
3. Synchronize the updated files to Python and Odoo bridge runtime copies.
4. Run prompt/contract tests in the affected repos.

Keep runtime tool descriptions focused on instructions for the business LLM. Put maintenance rationale in the changelog, not inside the tool prompt.
