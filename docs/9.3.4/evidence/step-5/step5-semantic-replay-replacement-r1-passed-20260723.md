---
doc_type: execution-evidence
version: 9.3.4
step: 5
run_id: step5-semantic-replay-replacement-20260723-r1
tested_commit: b9b8adfd725399cf069dd4165582b7d2e8af4b39
status: passed
decision: READY_FOR_SIGNOFF
created_at: 2026-07-23
---

# Step 5 semantic-replay replacement rehearsal

## Decision and boundary

- The single owner-approved semantic-replay replacement Step 5 rehearsal
  completed with tmux pane exit status `0` and runner
  `status=candidate-passed`.
- This record advances only the replacement Step 5 scope to
  `READY_FOR_SIGNOFF`. It does not self-sign `ACCEPTED`, authorize a merge or
  start the new Step 7 authority.
- The canonical parent feature remains `NEEDS_REPLAN` until replacement Step 5
  receives explicit independent acceptance and a new exact-main Step 7
  authority completes.
- No GitHub CI, tag, release or publish action was performed. Coverage and
  evidence verifiers were not weakened.
- The host-local generated file timestamps display `2026-07-24` because of the
  known future clock/timezone offset. The governed run-status interval is
  `2026-07-23T16:52:28Z` through `2026-07-23T17:50:16Z`; all manual evidence
  dates use the authoritative date `2026-07-23`.

## Workspace, source and attempt identity

- private full clone:
  `/tmp/foggy-v934-step7-replan-20260724.qEqdt9/repo`
- private parent mode: `0700`; `.git` is a real directory; clone is
  non-shallow.
- branch:
  `codex/v934-step7-semantic-portable-replay-fix`.
- tested commit, local HEAD, upstream and remote branch:
  `b9b8adfd725399cf069dd4165582b7d2e8af4b39`.
- canonical `origin/main` remained
  `62bf3fe1456af01179f09e2a9a80c3d229e2435f`.
- source before/after: `4304` files, exact SHA-256
  `deb88472117dabdc20f01268fd30d87bd7e1cf57f0849b21fbc782b53fc256f9`.
- runtime source before/after: `13` modules / `1411` files /
  `10757069` bytes, set SHA-256
  `22670362fff8f063791129e1e875768d6b1b44286ec8237591f458b5486b07f8`.
- replacement Step 5 budget=`1`; the tmux launch consumed the only attempt.
  No retry was started.

## Invocation and process ownership

The unique runner invocation was:

```text
env -u MAVEN_ARGS -u MAVEN_BASEDIR -u MAVEN_CONFIG -u MAVEN_OPTS \
  -u MAVEN_SKIP_RC -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  -u _JAVA_OPTIONS \
  ./scripts/verify-v934-release-gate.sh rehearsal \
  step5-semantic-replay-replacement-20260723-r1
```

- tmux control directory:
  `/tmp/v934-step5-semantic-replacement-20260723-r1-owner.5mIIVn`,
  mode=`0700`.
- tmux session:
  `v934-step5-semantic-replacement-r1`.
- after launch, the implementation session remained read-only and did not
  write to or take ownership of the pane.
- terminal pane state:
  `dead=1`, `status=0`.

## Same-run Step 4 and lane result

| Lane | Result |
|---|---|
| Step 4 aggregate | `23 exec / 48 sessions` |
| Required inventory | `774 positive + 59 structural / 5709 testcase / F0E0S0` |
| Unit | `682 executions + 55 structural / 4943 testcase / F0E0S0` |
| Integration | `47 executions + 4 structural / 320 testcase / F0E0S0` |
| Five-database matrix | `29 reports / 370 testcase / F0E0S0` |
| Database state negatives | `18/18` |
| External matrix | `16 reports / 76 testcase / F0E0S0` |
| Redis state contract | `4/4` |
| Addon companion | `2 reports / 6 testcase / F0E0S0` |

- Step 3 required matrix: `45 reports / 446 testcase / F0E0S0`,
  negatives=`17/17`, residue=`0/0/0`.
- Step 4 status: `release-passed`, exit code `0`.
- Step 4 final manifest SHA-256:
  `91487329123f112d5e132570db6bdb2018f3d4c3fd533ede03d86fb1607f32ce`.
- toolchain receipt SHA-256:
  `7e51e6059a16724180e16bb9fe86af82c2c986ec65f37cc08393c391ca604cb7`.
- coverage aggregate: line=`54630/76834`, branch=`26117/44876`;
  both exactly met the frozen minima. All `12` critical classes passed.

## Semantic replay contract regression

- the runner-owned portable replay self-test passed:
  same-filesystem=`2`, cross-filesystem=`1`, negatives=`9/9`.
- policy:
  `v934-semantic-replay-canonical-materialization-v1`.
- copy method:
  `exclusive-independent-copy-only`.
- permission restoration set:
  - `evidence/step4/child-ready/unit.json`: `0644 -> 0600`
  - `evidence/step4/child-ready/integration.json`: `0644 -> 0600`
  - `evidence/step4/child-ready/step3-required.json`: `0644 -> 0600`
- source-tree rule:
  `byte-and-metadata-unchanged`.
- negative coverage includes missing/extra/wrong permission policy, restored
  mode drift, receipt drift, source race, hardlinked input and existing target.
- self-test receipt SHA-256:
  `5a68764f8c4ffe94a8fc2ed078469f08ee3f4d862fb794324de44b5afa376f34`.
- This rehearsal still records
  `portable_semantic_replay=required-downstream`; the actual authority semantic
  replay remains a new Step 7 obligation and is not claimed here.

## Package, image and archive identity

- Launcher JAR: `80391351` bytes / `139` nested libraries.
- Launcher JAR and runtime image `/app/app.jar` SHA-256:
  `ed48e51e3ab57e86f67d04ed2ed12e812acf29e7da7153b11c7930fa0e14ecae`.
- package manifest SHA-256:
  `3220cd735647ca2e1a88163155b0ee99a97eac0f12fb7e59e5e16306840f5f95`.
- image id:
  `sha256:81a948fb53484493c87c4dbb1f600aec7eb7c71a0b7bf03320ee5fd2b3dabf5a`.
- the isolated image build context contained exactly `Dockerfile` and
  `app.jar`; container, context, image tag and readback copy cleanup flags are
  all `true`.
- pinned runtime base index / manifest / config:
  - `sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57`
  - `sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52`
  - `sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc`
- archive SHA-256:
  `613e1cacbbb08494dfb45fe049cf1b89b5c0c2f5e5bd01211be6314474e73ade`.
- archive size: `106097217` bytes.
- payload: `8303` files / `9424` entries / `524038410` bytes.
- file/root/archive manifest SHA-256:
  - `35bd44fd9871e607f6134ade3b12502fb1736f52442f00e25c7e255f0405244b`
  - `462d2e16efbb21373859f1465272f100687048e2458f3b4133bb21be4003506d`
  - `1ca2d439ab018eac6c891576b72607941428f9ec6dd9b42e084e8c547da4936f`
- build, `verify-archive` and independent `extract-verify` receipts all report
  `status=passed` with the exact same payload and identities.
- sensitive scan passed across `8302` files / `524038047` bytes, with `8`
  patterns, `46` text extensions and `2` recursive archive extensions.

## Pointer, cleanup and governance boundary

- candidate pointer:
  `target/v934-release-gate/candidate-run.env`.
- candidate pointer SHA-256:
  `4655f1bb3b695e11ca055fc2cd5434528fe03c7a4684823ebfb4ac74e0cb3797`.
- candidate pointer binds this run, tested commit, rehearsal mode, summary,
  archive and Launcher JAR; status=`passed`.
- release summary SHA-256:
  `bd4870eb2d0f36ec23a284c7bc4b04028c7c883c0d097b051ef64d9390b707f5`.
- `authority-final-run.env`, `final-authority-run.env` and `final-run.env`
  remain absent.
- run-owned container/network/volume residue is `0/0/0`; required ports
  `13306/13308/15432/11433/17017/16379/19530` are free.
- GitHub Actions remains `enabled=false`, queued/in-progress=`0/0`; no remote
  CI result was used.
- no tag, release, publish, branch protection or required check action was
  performed.
- the original workspace remains at
  `9743f97d9d935d5e26311b78c158755bca51f17a` with only its pre-existing
  `docs/9.3.5/README.md` modification and untracked
  `docs/9.3.5/workitems/`; they were not reset, stashed, overwritten, deleted
  or mixed into this branch.

## Readiness audit and next action

The `foggy-delivery-signoff` evidence checklist was applied as a readiness
audit:

| Item | Risk | Evidence | Result |
|---|---|---|---|
| tested identity and source seal | critical | candidate pointer, summary, source/runtime before-after seals, remote-exact branch | covered |
| same-run lanes and coverage | critical | Step 2/3/4 summaries, final manifest, coverage gate and cleanup receipts | covered |
| semantic replay repair regression | critical | same/cross filesystem positives, `9/9` negatives and exact policy receipt | covered |
| package/JAR/image/archive | critical | package/image manifests, archive build/verify/extract receipts and exact hashes | covered |
| pointer and no-CI boundary | critical | final-pointer absence, pointer negatives, Actions disabled and no active run | covered |
| protected workspace | major | original workspace status and exact HEAD | covered |

- failed items: none.
- deviations: none from the approved replacement Step 5 activation contract.
- residual risks:
  - replacement Step 5 is not yet independently accepted;
  - the repaired bytes are not yet merged into exact clean `origin/main`;
  - new Step 7 authority, downstream semantic replay and ordered review chain
    remain pending;
  - the owner-approved absence of required CI/branch protection remains a
    process risk.
- readiness:
  `READY_FOR_SIGNOFF` for semantic-replay replacement Step 5 only.
- next permitted action:
  explicit independent/repository-owner Step 5 acceptance. Do not start Step 7
  or create a final authority pointer before that acceptance and a separately
  frozen exact-main activation.
