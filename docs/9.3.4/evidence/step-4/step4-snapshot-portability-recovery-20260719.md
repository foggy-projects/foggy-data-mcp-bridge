---
evidence_type: diagnostic-lineage-recovery
version: 9.3.4
step: 4
supersedes_execution_of: bf860be778dcb86f40c7cba7718a0e75e58d6a36
status: implementation-verified-awaiting-clean-cdiag
recorded_at: 2026-07-19
---

# Step 4 snapshot portability recovery

## Decision

formal-r5 correctly failed closed and remains immutable failed evidence. The recovery removes all
ten Java snapshot-producer references/writes to `foggy-data-mcp-bridge-python`, retains their
existing module-local `target/parity/**` publications, and adds an exact Unit preflight for
`14 local producers / 0 external references`.

Because this changes test sources and the Unit runner outside the formalization allowlist, the
threshold successor has been reset to `diagnostic-pending` and the coverage contract to
`diagnostic-ready`. r21 observations and the former Cfreeze are not reused for execution.

## Focused verification

- MCP producer classes: `2 tests / 0 failures / 0 errors / 0 skipped`;
- eight model producer classes: `13 tests / 0 failures / 0 errors / 0 skipped`;
- all ten declared local artifacts were created under the owning module's `target/parity/**`;
- source scan: exact producer paths=`14`, forbidden sibling references=`0`;
- `bash -n scripts/verify-v934-unit.sh` and `git diff --check`: passed;
- lifecycle negative suite: passed, including `unit-shape-negative=16`,
  `unit-semantic-seal-negative=2`, `source-seal-negative=3` and the existing signal/logger probes;
- Step 4 contract validation: passed with `workflow_state=diagnostic`,
  `threshold_status=diagnostic-pending`, `step4_manifest_files=61`,
  `required_positive_reports=773`, `required_structural_reports=59`,
  `required_testcases=5707`;
- Step 4, successor and Step 6 SHA-256 manifests: every entry passed.

The focused Maven checks are implementation smoke evidence only. They do not replace the official
Unit runner or the complete all-lane diagnostic.

## Machine identities before Cdiag commit

| Artifact | SHA-256 |
|---|---|
| Unit runner | `15a37faf2d6deb8c4b77962a02b4cf75145a5f18334020e014f09e88e993e569` |
| diagnostic thresholds | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| diagnostic coverage contract | `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530` |
| declared amendments | `045c43ae87040be8482cebddcbb6174774f9e2028823862a3063db74b45bdfb9` |
| successor overlay contract | `ce83636c3b8b88cd2a9a7891e57d2b49024d92cf3690a12eb35fd9a5693dc6d7` |
| successor overlay tool | `7706223063d49373903f5edabbd6a6b11a58c09b9690b2025cd26e14f396868e` |
| lifecycle negative tool | `67f06f1cc0fcab0da37adc4eb8231a40f815b235a3ac8f7d2f52040b66ada061` |
| database protected-tree manifest | `8dab4e4efa73eb12ecbaa664c6e3a355c5ec6731f2cb56519da397dbe412fc7a` |
| database successor contract | `bf9a142ac27b2b0da8eff51ed4af277dfd5bef9ed92a3935fc181b305cb485a5` |
| required successor contract | `4dbab92203edf8d0de0abb3796908364cd7157b6dfa65d7e4e902b6b31b42f4f` |
| successor manifest | `5ef3de54ef2b3036f9ebcd96a1f8e874cdf2bc113a59c65647c9687a9d428588` |
| Step 4 manifest | `c3a2c100047fc026c13372a9da5f50b04fb962a2b5043915c466082077c9b368` |
| Step 6 CI contract | `1c9bfbb818fc85b011f971968e73b6870b90167d21b067f556e8b9a56cd826b9` |
| Step 6 CI tool | `9d33dbcc88a5ad0a023f4cc21229fdbbf8749c81ca161a360c9d2bc57c9c977d` |
| Step 6 manifest | `a42f58e74bda15e21888546ec60f3be248310c6c9c48679afee106e5795b0a75` |

## Required continuation

1. commit and push a clean Cdiag;
2. validate the overlay from that committed HEAD;
3. in an isolated clone with no sibling Python repository, rerun the focused producers and verify
   no parent path is created or modified;
4. run the complete diagnostic under the existing exact database stop/restore wrapper;
5. review the new observation, freeze thresholds in a direct child, then run a new fresh formal.
