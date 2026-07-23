---
doc_type: execution-evidence
version: 9.3.4
step: 5
run_id: step5-local-rehearsal-20260723-no-ci-fullclone-r2
tested_commit: 6c3ee97abbe49c0cf5cf485d2ddeb4ba7ff7c84f
status: passed
decision: READY_FOR_SIGNOFF
created_at: 2026-07-23
---

# Step 5 no-CI private-full-clone replacement rehearsal

## Decision and boundary

- The single owner-approved replacement rehearsal passed.
- This evidence advances Step 5 only to `READY_FOR_SIGNOFF`.
- It is a rehearsal candidate, not a Step7 authority or 9.3.4 final signoff.
- GitHub Actions remained disabled and is not an evidence source.
- No merge, Step7, tag, release or publish action was authorized or performed.
- The predecessor r1 remains immutable failed-closed evidence. No result from r1
  was reused or spliced into this run.

## Workspace and identity

- execution environment: private full clone under a `0700` parent;
  `.git` was a real directory, the clone was clean and non-shallow.
- local branch: `codex/v934-step5-replacement-r2`.
- canonical remote branch: `origin/codex/v934-release-authority`.
- tested commit and pushed PR head:
  `6c3ee97abbe49c0cf5cf485d2ddeb4ba7ff7c84f`.
- source before/after: 4298 files, exact SHA-256
  `f0a33a720920b0a07464419fe12b7f28578c6828d92e7cf76a6c564365cbcb49`.
- runtime-source before/after: 13 modules, 1411 files, 10,757,069 bytes,
  set SHA-256
  `22670362fff8f063791129e1e875768d6b1b44286ec8237591f458b5486b07f8`.

## Invocation and process ownership

The replacement was started once through the existing runner:

```text
env -u MAVEN_ARGS -u MAVEN_BASEDIR -u MAVEN_CONFIG -u MAVEN_OPTS \
  -u MAVEN_SKIP_RC -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  -u _JAVA_OPTIONS \
  ./scripts/verify-v934-release-gate.sh rehearsal \
  step5-local-rehearsal-20260723-no-ci-fullclone-r2
```

- A private tmux control directory owned the process.
- After launch, the current session acted as a read-only observer.
- No retry was started. The unique replacement attempt was consumed
  successfully.
- Terminal summary:
  `status=candidate-passed`,
  `final_authority_pointer_updated=false`.

## Same-run Step4 result

The nested Step4 release used the same run id and tested commit.

| Lane | Result |
|---|---|
| Step4 aggregate | 23 exec / 48 sessions |
| Required overlay | 774 positive + 59 structural / 5709 testcase / F0E0S0 |
| Unit | 682 executions + 55 structural / 4943 testcase / F0E0S0 |
| Integration | 47 executions + 4 structural / 320 testcase / F0E0S0 |
| Five-database matrix | 29 reports / 370 testcase / F0E0S0 |
| Database state | 18/18 |
| External matrix | 16 reports / 76 testcase / F0E0S0 |
| External resource state | 4/4 |
| Addon companion | 2 reports / 6 testcase / F0E0S0 |

- Step4 run status: `release-passed`, exit code `0`.
- Step4 interval: `2026-07-23T12:07:34Z` to
  `2026-07-23T13:08:08Z`.
- final manifest SHA-256:
  `62d347025250f2d20b016516fd0725b255c6ff44a188cb11765f1d3f93605355`.
- toolchain receipt SHA-256:
  `8a738752adcdc4aa953dbe752778d2c83ada91863b456cae20e0091f44152751`.
- coverage gate confirmed all 24 governed groups and 12 critical groups; zero
  group was below its frozen floor.

## Package and runtime image identity

- Launcher JAR: 80,391,351 bytes, 139 nested libraries.
- Launcher JAR SHA-256:
  `14aac41389fd4728232ca99e47ed792e1da57641a2c6ce8dd2a73d3f7889f677`.
- package manifest SHA-256:
  `41e16ab6b2d402f50b6b22eaa78af3c5b6eb8213b6744117ad7aebc382c43b02`.
- runtime image id:
  `sha256:d109c64d99f44f3335fc06f173c167494522bb82d4d9df83ea3ebbc266e38bf0`.
- embedded `/app/app.jar` has the exact Launcher JAR SHA-256 above.
- build context contained exactly `Dockerfile` and `app.jar`.
- image manifest SHA-256:
  `fd5d0ad9781b2a15c6792261523fe5bada27641d583f1ae33ebf8c47048d6de0`.
- runtime base index / manifest / config digests:
  - `sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57`
  - `sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52`
  - `sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc`
- container, isolated build context, image tag and readback copy were removed.

## Archive and portable verification

- archive SHA-256:
  `36245c874d13f914750a9a1e7e2cb1ef4e95de2bc2c37c8f9ce8cae8ca035ba6`.
- archive size: 106,205,199 bytes.
- payload: 8303 files / 9424 entries / 528,541,789 bytes.
- file manifest SHA-256:
  `b25dd9305b4e36a07f05924bcf8dd0011ae88e8e0d5338319b72b77bb181df33`.
- root manifest SHA-256:
  `b3c542b00373e8bc99154f03ce93033a1a11f1bc10d1543edc3a7130327c79b0`.
- archive manifest SHA-256:
  `a8065f45679bbe79a5195f1163d62e07942657d12fa4b67846ddb28b4ef14005`.
- digest-file SHA-256:
  `85b31807b4d4ac315d125d9d6d94a3705737c4e4d2f313dad91b1eaf0b3f7e10`.
- `verify-archive` and extraction into a different directory both passed.
- durable seal passed for 16,648 files and 2257 directories.
- sensitive scan passed across 8302 files / 528,541,426 bytes with 8
  patterns, 46 text extensions and 2 recursive archive extensions.
- `portable_byte_verify=passed`.
- `portable_semantic_replay=required-downstream`; this rehearsal does not claim
  that downstream semantic replay.

## Expected-negative contracts

| Contract | Result |
|---|---:|
| Artifact self-test | 105 cases passed; two deterministic rebuilds |
| Package/JAR/image | 120 cases passed |
| Candidate/final pointer publication | 5 cases passed |

- package negative result SHA-256:
  `73c8104b8ae444b45aec302bd995aa343a119e010c7cd9d1788643d22b5c638b`.
- pointer negative result SHA-256:
  `56406053ce5821986fc2541f4f55a6d86c6d5023681ed34c1f83fd0ee69666b6`.
- Pointer probes covered final-pointer scope refusal, injected pre-commit
  failure, post-replace rollback, first-publication rollback and symlink
  refusal.

## Pointer and authority boundary

The candidate pointer records:

- run id:
  `step5-local-rehearsal-20260723-no-ci-fullclone-r2`;
- tested commit:
  `6c3ee97abbe49c0cf5cf485d2ddeb4ba7ff7c84f`;
- summary SHA-256:
  `31a67b736cfb7993f51c643ff7fa72bb50959cdd8c9308e1e130a92ea532cafc`;
- archive and Launcher JAR SHA-256 values listed above;
- status: `passed`.

`final-run.env` and `authority-run.env` are absent. The rehearsal did not
publish or update a final authority pointer.

## Cleanup, remote and protected workspace

- No run-owned Docker container, network or volume remained after completion.
- Required ports `13306`, `13307`, `15432`, `11433`, `17017`, `16379` and
  `19530` were free.
- The private clone was clean after the run; local/remote ahead-behind was
  `0/0` before this evidence-only closure commit.
- PR #124 and the canonical remote branch pointed to the exact tested commit.
- Repository Actions remained `enabled=false`; there were no queued or
  in-progress Actions runs.
- The original workspace remained at
  `9743f97d9d935d5e26311b78c158755bca51f17a` with only the pre-existing
  `docs/9.3.5/README.md` modification and untracked
  `docs/9.3.5/workitems/`. Those files were not modified, moved, stashed,
  reset or included.

## Residual risk and next action

- The deliberate no-CI choice means future PR/merge transitions do not have an
  automated required-check or branch-protection backstop.
- Semantic portable replay is downstream, not claimed here.
- Step7 requires Step5 independent acceptance, an exact clean `origin/main`
  entry and separate execution authorization.
- The next permitted action is independent Step5 signoff. This execution
  record must not self-promote to `ACCEPTED`.
