---
doc_type: execution-evidence
version: 9.3.4
step: 7
planned_run_id: step7-semantic-authority-20260724-r1
preflight_commit: b3b252c1a657f61feccca3ed9316216758c19e96
status: passed
decision: ACTIVATE_ONE_REPLACEMENT_STEP7_AUTHORITY_ATTEMPT
created_at: 2026-07-24
---

# Step 7 semantic-replay replacement authority preflight

## Decision

- verdict: `passed / replacement Step 7 attempt not yet consumed`
- owner_authorization: repository owner explicitly accepted replacement Step 5
  and authorized continuation on `2026-07-24`。
- authorized_next_action: commit this docs-only activation on the independent
  activation branch, reverify that remote `main` is still the exact preflight
  commit, fast-forward the activation commit to `main`, then launch exactly one
  authority runner under a new private tmux owner。
- unchanged_scope: no GitHub CI, tag, release, publish, production API/SPI
  change, verifier relaxation or premature final authority pointer。

## Entry Identity

- repair merge PR: `#125`, state=`MERGED`。
- merge base:
  `62bf3fe1456af01179f09e2a9a80c3d229e2435f`。
- accepted repair branch head:
  `4124bdcf63e35e7c129ca6b8a3a4aa15df805e33`。
- exact merge commit:
  `b3b252c1a657f61feccca3ed9316216758c19e96`。
- private parent:
  `/tmp/foggy-v934-step7-semantic-authority-20260724.ygUAP5`。
- parent mode: `0700`。
- clone kind: full clone；`.git` is a real directory；shallow=`false`。
- clone state: clean、canonical SSH origin；HEAD / upstream /
  `origin/main` / remote `refs/heads/main` are the exact merge commit before
  this docs-only activation。
- activation branch:
  `codex/v934-step7-semantic-authority-activation`。
- original user workspace remains at
  `9743f97d9d935d5e26311b78c158755bca51f17a` with only its pre-existing
  `docs/9.3.5` changes。

## Frozen Repair and Attempt Ledger

- semantic portable replay repair: `ACCEPTED`。
- replacement Step 5:
  run=`step5-semantic-replay-replacement-20260723-r1`,
  tested commit=`b9b8adfd725399cf069dd4165582b7d2e8af4b39`,
  attempt consumed=`true`, acceptance=`accepted`。
- historical Step 7:
  run=`step7-local-authority-20260723-r1`, attempt consumed=`true`,
  immutable candidate-only。
- replacement Step 7:
  run=`step7-semantic-authority-20260724-r1`, budget=`1`,
  consumed=`false`。
- frozen semantic policy:
  `v934-semantic-replay-canonical-materialization-v1`。
- canonical evidence and tested classes use exclusive independent copy only；
  target regular files require `st_nlink == 1` and hardlinked inputs fail
  closed。
- exact permission restoration set:
  `evidence/step4/child-ready/{unit,integration,step3-required}.json`,
  source=`0644`, canonical semantic target=`0600`。
- extracted artifact source tree remains byte- and metadata-unchanged。

## Runtime Source Receipt

- command/status: `scan-runtime-source / passed`。
- git head:
  `b3b252c1a657f61feccca3ed9316216758c19e96`。
- module/file/byte count: `13 / 1411 / 10757069`。
- set SHA-256:
  `22670362fff8f063791129e1e875768d6b1b44286ec8237591f458b5486b07f8`。
- contract SHA-256:
  `382a34e0c3ed81d0d39828bae73cf802b7be41fb0e681c126ead4ad5946f8ec0`。
- receipt SHA-256:
  `e1fef990d5a57084c568119108e9ac39ae4c4faf7e9ba08215f22c97e36005d6`。

## Frozen Tooling and Focused Checks

- Step 4/5/6 manifest closure: exact `63/8/16`, all entries passed。
- Bash syntax: all `12` scripts under `scripts/v934` plus the release runner
  passed。
- Python compile: all `33` Python tools under `scripts/v934` passed；generated
  cache was moved out of the private clone before activation。
- release artifact self-test:
  status=`passed`, negatives=`105`, deterministic rebuilds=`2`,
  output SHA-256=
  `1138a1d97fb1ffe238512aa173a844c48850d889214ddca176076b250185b1e8`。
- portable replay self-test:
  status=`passed`, same-filesystem=`2`, cross-filesystem=`1`, negatives=`9/9`,
  output SHA-256=
  `5a68764f8c4ffe94a8fc2ed078469f08ee3f4d862fb794324de44b5afa376f34`。
- package negative:
  status=`passed`, cases=`120`, output SHA-256=
  `73c8104b8ae444b45aec302bd995aa343a119e010c7cd9d1788643d22b5c638b`。
- pointer negative:
  status=`passed`, cases=`5`, output SHA-256=
  `56406053ce5821986fc2541f4f55a6d86c6d5023681ed34c1f83fd0ee69666b6`。
- coverage contract negatives:
  `28/28`, output SHA-256=
  `67393ab748b05fec4c6c9b83f17364091951910c9760f6d4286540c173768d0d`。
- coverage XML negatives:
  `130/130`, output SHA-256=
  `b1694689637c02db72b32708e3d3094211985f0974ce7d71acdfb41355646e1f`。
- CI static negatives:
  `86/86`, output SHA-256=
  `9ba9c1d403ae269b543b72bfe8b1ef18b6bd5c773ff023a3becaf844d7b44638`；
  workflow validation passed with `16` tooling paths and `4` unchanged
  workflows。
- focused evidence root:
  `/tmp/v934-step7-semantic-preflight-20260724.YjwR2J`, mode=`0700`。

## Runtime and Process Preconditions

- runtime base OCI index:
  `sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57`。
- linux/amd64 manifest and pinned local image identity:
  `sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52`。
- config:
  `sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc`。
- required ports:
  `13306/13308/15432/11433/17017/16379/19530`, all free。
- `target/v934-release-gate`, candidate/final/authority pointers and planned
  run root are absent。
- planned run container/network/volume namespace is absent。
- historical Step 4/5/7 tmux panes are all dead；the latest relevant statuses
  are old Step 7=`0` and replacement Step 5=`0`。
- no active Maven, Step 4 or release runner competes for the governed
  workspace or ports。
- ambient `MAVEN_OPTS` is present；the authority launch must use the complete
  frozen `env -u` sanitation list。

## Remote and Governance Boundary

- PR #125=`MERGED` with exact base/head/merge identities above。
- GitHub Actions: `enabled=false`。
- PR status checks: empty。
- queued/in-progress Actions runs: `0/0`。
- no branch protection or active ruleset。
- no v9.3.4 tag or release exists。
- no final authority pointer may be created before the same-run semantic replay
  and ordered implementation-quality, coverage-audit and version-signoff chain
  pass。

## Attempt Boundary and Launch Contract

- This preflight did not start Maven, Step 4, database cells, Docker packaging,
  the release runner or the replacement Step 7 authority。
- The replacement Step 7 attempt remains available exactly once and becomes
  consumed only when the tmux-owned runner is launched from the clean,
  remote-exact activation commit on `main`。
- planned invocation:

```text
env -u MAVEN_ARGS -u MAVEN_BASEDIR -u MAVEN_CONFIG -u MAVEN_OPTS \
  -u MAVEN_SKIP_RC -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  -u _JAVA_OPTIONS \
  ./scripts/verify-v934-release-gate.sh authority \
  step7-semantic-authority-20260724-r1
```

- process owner: a new private `0700` tmux control directory；after launch the
  current session is observer-only and does not write to the pane or take over
  Maven/JVM/Docker/process ownership。
- stop rule: any runner failure is immutable, consumes the only attempt,
  returns the parent feature to a stopped `NEEDS_REPLAN` condition and forbids
  an automatic retry or cross-run evidence splice。
- success boundary: runner `candidate-passed` is not final authority by itself；
  downstream semantic replay and the ordered review chain remain mandatory。
