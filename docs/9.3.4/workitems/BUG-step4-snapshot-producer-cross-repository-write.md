# BUG: Step 4 snapshot producers write outside the sealed repository

## Status

`IMPLEMENTED / AWAITING NEW CDIAG`

## Severity

Release blocker for 9.3.4 Step 4 formal authority.

## Observation

Fresh formal run `step4-coverage-20260719-formal-r5` reached the Unit child after all frozen
topology, contract, replay, negative-probe and class-universe checks had passed. The Unit lane then
ended with `511 tests / 0 failures / 2 errors / 0 skipped`. Both errors were
`IllegalStateException` from snapshot tests that required an adjacent
`foggy-data-mcp-bridge-python/pyproject.toml`.

Read-only inventory found `14` Java snapshot producers with module-local `target/parity/**`
artifacts. Ten also contained cross-repository behavior: seven unconditional sibling writes, two
MCP producers gated on a sibling `pyproject.toml`, and one order-dependent best-effort write. This
violated their frozen `surefire/unit/hermetic` classification and allowed effects outside the Step 4
source seal.

## Contract

- Every governed Java snapshot producer writes only its declared module-local
  `target/parity/**` artifact.
- Unit startup freezes the exact set of `14` tracked producer paths and their artifact names.
- Governed producers containing `foggy-data-mcp-bridge-python` or `pythonFixturePath` fail before
  Maven execution.
- Snapshot cases, Java-side assertions, Surefire report identities and testcase cardinality remain
  unchanged.
- Python replay, if retained, must consume an explicitly transported artifact in a separate,
  commit-and-digest-bound lane; hermetic Surefire may not mutate a sibling checkout.
- formal-r5 remains failed. No sibling repository or placeholder `pyproject.toml` may be injected to
  make it green.

## Governed source amendments

- `foggy-dataset-mcp`: the compose-script tool-error and domain-neutral runner producers;
- `foggy-dataset-model`: compose, compose-script, governance, pivot-domain, pivot-output, formula,
  semantic-scale and time-window producers;
- `scripts/verify-v934-unit.sh`: exact producer/artifact isolation preflight;
- Step 4 successor overlay, lifecycle seal, Step 4 manifest and Step 6 manifest closure.

All ten test sources are registered in
`scripts/v934/step4/successor/declared-amendments.tsv` with parent/current byte hashes and unchanged
`100644` mode. The Unit runner remains a declared runner binding with unchanged `100755` mode.

## Acceptance

1. Static isolation reports `producers=14 external=0` from the exact allowlist.
2. The ten amended producer classes pass and publish only module-local artifacts.
3. Step 4 contract validation reports `diagnostic-pending / diagnostic-ready`, 61 manifest paths,
   773 positive reports, 59 structural reports and 5,707 testcase nodes.
4. Overlay validation passes from a clean committed HEAD and rejects undeclared or mismatched
   source amendments.
5. A clone whose parent directory contains no Python sibling passes the focused producer tests and
   creates no path outside that clone.
6. A new complete diagnostic run succeeds before threshold review/freeze; only its direct-child
   Cfreeze may be used for a subsequent fresh formal run.
