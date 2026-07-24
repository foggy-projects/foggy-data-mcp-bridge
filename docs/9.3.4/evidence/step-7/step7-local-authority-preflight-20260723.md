---
doc_type: execution-evidence
version: 9.3.4
step: 7
planned_run_id: step7-local-authority-20260723-r1
preflight_commit: 0c0e7a2921d7a7f10f9a3640b08d015cce5c45db
status: passed
decision: ACTIVATE_ONE_AUTHORITY_ATTEMPT
created_at: 2026-07-23
---

# Step 7 exact-main local authority preflight

## Decision

- verdict: `passed / authority attempt not yet consumed`
- owner_authorization: continue Step 7 after PR #124 merge
- authorized_next_action: push one docs-only activation commit to `main`, then
  start exactly one `authority` runner in a new private tmux owner.
- unchanged_scope: no-CI；不启用 Actions；不执行 tag、release、publish 或
  9.3.5/9.4.0 production work。

## Entry Identity

- PR #124 state: `MERGED`
- merge commit:
  `0c0e7a2921d7a7f10f9a3640b08d015cce5c45db`
- merge parents:
  - main parent: `9cf8d617b787967730a7f82d1d01c58cd38e3c9d`
  - PR parent: `bae26655f35570157a2b9b00bcf1e620d8aa5e9c`
- private parent:
  `/tmp/foggy-v934-step7-authority-20260723.LwJ1DR`
- parent mode: `0700`
- clone kind: full clone；`.git` is a real directory
- clone state: clean、non-shallow、canonical SSH origin
- HEAD / `origin/main` / remote `refs/heads/main`: exact merge commit

## Runtime Source Receipt

- command/status: `scan-runtime-source / passed`
- module count: `13`
- file count: `1411`
- bytes: `10757069`
- set SHA-256:
  `22670362fff8f063791129e1e875768d6b1b44286ec8237591f458b5486b07f8`
- contract SHA-256:
  `382a34e0c3ed81d0d39828bae73cf802b7be41fb0e681c126ead4ad5946f8ec0`
- receipt SHA-256:
  `095621143a34e15c9c90e987eb8406e8f8ee9b0800a20fc6721ea33ef49894ea`

## Frozen Tooling and Focused Checks

- Step 4/5/6 manifests: exact `63/8/16`
- Bash syntax: `19` unique frozen files passed
- Python compile: `21` unique frozen files passed; pycache remained outside repo
- artifact self-test: `passed / 105 negative cases`
- package negative: `passed / 120 cases`
- pointer negative: `passed / 5 cases`
- output SHA-256:
  - artifact:
    `2ed8728ddfa6d6d976c9cda31e1eb6938a56925f6f65f276663bb6b73a171c84`
  - package:
    `34e8a8a96f5669a57083e7527e6672b4806bcc877190111478f35dccb3fdab6d`
  - pointer:
    `55f8c4719da206d5cf676cd972d9a2150c3e3d7b8cdaabc5b022b2c712c1a3d0`

An observer assertion initially queried the artifact tool's nonexistent
`.cases` field instead of its canonical `.negative_cases` field. All tools had
already exited successfully and published their passed receipts; correcting
the observation confirmed `105/120/5`. No release or Step 4 runner started,
so the authority attempt was not consumed.

## Runtime and Process Preconditions

- runtime base OCI index:
  `sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57`
- linux/amd64 manifest and local image identity:
  `sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52`
- config:
  `sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc`
- required ports `13306/13307/15432/11433/17017/16379/19530`: all free
- `target/v934-release-gate`: absent
- candidate/final/authority pointers and runs root: absent
- future run slug Docker container/network/volume residue: absent
- active Step 4/release runner: none
- historical tmux panes:
  - Step 5 r1: `pane_dead=1 / dead_status=1`
  - Step 4 r16: `pane_dead=1 / dead_status=0`
- the Step 5 historical server retains only a `tee` child; no Maven, JVM,
  Docker, Step 4 or release runner child remains.
- ambient `MAVEN_OPTS`: present；the approved launch uses `env -u` for all
  frozen Maven/JVM control variables and the sanitized subshell check passed.
- ambient Git controls: only `GIT_PAGER=cat`, which the runner explicitly
  removes before authority checks.

## Remote and Workspace Boundary

- GitHub Actions: `enabled=false`
- PR status checks: empty
- queued/in-progress Actions runs: `0/0`
- original workspace HEAD:
  `9743f97d9d935d5e26311b78c158755bca51f17a`
- original workspace remains limited to its existing
  `docs/9.3.5/README.md` modification and untracked
  `docs/9.3.5/workitems/`; neither path is touched or staged here.

## Attempt Boundary

No Maven lane, Step 4 runner, database fixture, package build or authority
runner was started by this preflight. The single Step 7 attempt becomes
consumed only after the activation commit is pushed to exact `origin/main`
and the private tmux owner starts
`verify-v934-release-gate.sh authority step7-local-authority-20260723-r1`.
