---
doc_type: execution-evidence
version: 9.3.4
step: 5
planned_run_id: step5-semantic-replay-replacement-20260723-r1
preflight_commit: 1e068cd5d8e0fd6bce5afdaa33e4472261b81729
status: passed
decision: ACTIVATE_ONE_SEMANTIC_REPLAY_REPLACEMENT_REHEARSAL
created_at: 2026-07-23
---

# Step 5 semantic replay replacement r1 preflight

## Decision

- verdict: `passed / replacement attempt not yet consumed`
- authorized_next_action: commit and push this docs-only activation, reverify
  the exact source identity and attempt ledger, then launch exactly one
  replacement Step 5 rehearsal under an independent tmux owner.
- unchanged_scope: no Step 7 or main merge; no GitHub CI, tag, release, publish,
  verifier weakening, production API/SPI change or final authority pointer.

## Frozen Repair and Attempt Ledger

- canonical repair:
  `docs/9.3.4/workitems/BUG-step7-semantic-portable-replay-tool-contract.md`
  is repository-owner `ACCEPTED`.
- repair source seal:
  `1e068cd5d8e0fd6bce5afdaa33e4472261b81729`.
- frozen semantic policy:
  `v934-semantic-replay-canonical-materialization-v1`.
- materialization contract: exclusive independent copy only; target regular
  files must have `st_nlink == 1`; hardlinked inputs fail closed.
- exact permission restoration set:
  `evidence/step4/child-ready/{unit,integration,step3-required}.json`,
  source mode `0644`, canonical target mode `0600`.
- extracted artifact contract: bytes and metadata remain unchanged.
- attempt ledger: replacement Step 5 budget=`1`, consumed=`false`; replacement
  Step 7 budget=`1`, consumed=`false`; historical Step 7 r1 remains consumed.

## Clone and Source Identity

- private clone:
  `/tmp/foggy-v934-step7-replan-20260724.qEqdt9/repo`.
- private parent mode: `0700`.
- clone kind: full clone; `.git` is a real directory; shallow=`false`.
- origin:
  `git@github.com:foggy-projects/foggy-data-mcp-bridge.git`.
- branch:
  `codex/v934-step7-semantic-portable-replay-fix`.
- clean HEAD and remote branch:
  `1e068cd5d8e0fd6bce5afdaa33e4472261b81729`, divergence=`0/0`.
- `origin/main`:
  `62bf3fe1456af01179f09e2a9a80c3d229e2435f`; repair branch is one commit
  ahead before this docs-only activation.
- the original user workspace remained read-only at
  `9743f97d9d935d5e26311b78c158755bca51f17a` with its pre-existing
  `docs/9.3.5` modifications intact.

Direct runtime-source receipt:

- command/status: `scan-runtime-source / passed`.
- module/file/byte count: `13 / 1411 / 10757069`.
- set SHA-256:
  `22670362fff8f063791129e1e875768d6b1b44286ec8237591f458b5486b07f8`.
- contract SHA-256:
  `382a34e0c3ed81d0d39828bae73cf802b7be41fb0e681c126ead4ad5946f8ec0`.
- receipt SHA-256:
  `76313f97925f44bb695546307d69b6ad4f775b500e652bbb62ff6f74b1550404`.

## Frozen Tooling and Focused Checks

- Step 4/5/6 manifest closure: exact `63/8/16`, all entries passed.
- changed replay/release/coverage/package/pointer Python consumers compiled.
- `scripts/verify-v934-release-gate.sh` syntax passed.
- artifact self-test: status=`passed`, negatives=`105`, deterministic
  rebuilds=`2`; output SHA-256
  `12393a4588b8ae3ff97ab4e65346801f1cb7678f853fe610301a7f77ed064787`.
- portable replay self-test: status=`passed`, same-filesystem=`2`,
  cross-filesystem=`1`, negatives=`9`; output SHA-256
  `5a68764f8c4ffe94a8fc2ed078469f08ee3f4d862fb794324de44b5afa376f34`.
- package negative: status=`passed`, cases=`120`; output SHA-256
  `c2bf4fe888818d4454d7ea526d627fd99157a3cf270c7825d2ec0aba69384da1`.
- pointer negative: status=`passed`, cases=`5`; output SHA-256
  `14c1940d5850e672cfd8cad1dcd5055add259d1e114f508b075537b3e58b3d23`.
- initial observer commands used two incorrect CLI paths/options and exited
  before running their intended checks. The corrected commands above passed;
  neither invocation started a release runner or consumed an attempt.

## Runtime Base and Environment

- OCI index:
  `sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57`.
- linux/amd64 manifest and local image identity:
  `sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52`.
- config:
  `sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc`.
- required ports:
  `13306/13308/15432/11433/17017/16379/19530`, all free.
- candidate/final/authority pointers and planned run root: absent.
- planned run Docker container/network/volume namespace: absent.
- historical Step 4/5/7 tmux evidence owners: pane dead, not competing.
- unrelated host Java and Docker workloads do not bind required ports or the
  planned run namespace.
- ambient `MAVEN_OPTS` is present, so the runner launch must use the frozen
  `env -u` sanitation list.
- GitHub Actions: `enabled=false`; queued=`0`; in-progress=`0`.

## Attempt Boundary

This preflight did not start Step 4, Maven, database cells, Docker packaging,
the release runner or semantic authority. The replacement Step 5 attempt
remains available exactly once. It becomes consumed when the tmux-owned runner
is launched after the docs-only activation commit is clean, pushed and
remote-exact. A failed runner will be preserved and will not be retried.
