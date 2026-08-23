# Foggy Java engine Maven Central release runbook

This runbook is for the `foggy-data-mcp-bridge` Java engine. It keeps the
source commit, Maven Central deployment, annotated Git tag, and next
development version traceable.

## Version and branch policy

- `main` carries the next minor development line, for example
  `9.3.0-SNAPSHOT`.
- Do not create a long-lived branch named `v9.2.1-SNAPSHOT`. The snapshot is
  a Maven version, not a branch strategy.
- If a patch is actually needed after `v9.2.0`, create a maintenance branch
  from the immutable release tag:

  ```bash
  git switch -c maintenance/9.2.x v9.2.0
  scripts/release/set-engine-version.sh \
    --from 9.2.0 --to 9.2.1-SNAPSHOT --apply
  git add -- '**/pom.xml' 'pom.xml'
  git commit -m 'build: start 9.2.1 maintenance development'
  git push -u origin maintenance/9.2.x
  ```

  Release `9.2.1` from that branch, create `v9.2.1`, then merge or
  cherry-pick the bug fix commits into `main`. Keep `v9.2.0` unchanged.

## Prerequisites

- JDK 17, Maven, `curl`, `rg`, `jq`, `gpg`, `perl`, and checksum utilities.
- Maven `settings.xml` contains a `<server>` with id `central`; do not commit
  or print its credentials.
- The signing secret key is imported into the local GPG keyring. The key id
  can be passed as `FOGGY_GPG_KEY`.
- On WSL/Linux, use the Linux executable explicitly when the settings file
  contains a Windows path:

  ```bash
  export FOGGY_GPG_KEY=0x952B19B6B4A50B78
  export FOGGY_GPG_EXECUTABLE=/usr/bin/gpg
  ```

## Normal minor release

Run from a clean, pushed `main` branch. The version-freeze commit is the
source that will be published and tagged.

```bash
# 1. Freeze the current snapshot version.
scripts/release/set-engine-version.sh \
  --from 9.3.0-SNAPSHOT --to 9.3.0 --apply
git diff --check
git add -- '**/pom.xml' 'pom.xml'
git commit -m 'build: freeze 9.3.0 release version'
git push origin main

# 2. Read-only release gate.
scripts/release/maven-central-preflight.sh \
  --version 9.3.0 --gpg-key "$FOGGY_GPG_KEY"

# 3. Run the approved focused tests and affected-module build.
# Do not infer full-suite authorization from this runbook.

# 4. Publish. --skip-tests is only for the lane where focused tests have
#    already passed and the release decision explicitly allows test skips.
scripts/release/maven-central-publish.sh \
  --version 9.3.0 \
  --gpg-key "$FOGGY_GPG_KEY" \
  --skip-tests --confirm

# 5. Verify every public POM/JAR/sources/javadoc/checksum/signature.
scripts/release/maven-central-verify.sh --version 9.3.0

# 6. Only after verification, create and push the new annotated tag.
scripts/release/maven-central-tag.sh \
  --version 9.3.0 \
  --gpg-key "$FOGGY_GPG_KEY" \
  --deployment-id '<central-deployment-id>' \
  --confirm

# 7. Start the next development line.
scripts/release/set-engine-version.sh \
  --from 9.3.0 --to 9.4.0-SNAPSHOT --apply
git diff --check
git add -- '**/pom.xml' 'pom.xml'
git commit -m 'build: start 9.4.0 development'
git push origin main
```

The publish script does not create a tag. This leaves the public artifact and
the release evidence as a deliberate gate before the only tag push. The tag
script refuses existing local or remote tags and pushes only its requested
tag; it never uses `git push --tags`.

## Patch release

For a small fix, use `maintenance/9.2.x` from `v9.2.0`, change the POMs to
`9.2.1-SNAPSHOT`, implement and test the fix, then freeze to `9.2.1` and run
the same preflight/publish/verify/tag sequence with `--version 9.2.1`.
Afterward, merge or cherry-pick the fix commit into `main`, which remains on
the `9.3.0-SNAPSHOT` line.

## Release evidence

For each release, retain the following evidence in the release report or tag
message:

- Fixed: bug/regression and commit ids.
- Optimized: performance, stability, packaging, or boundary improvements.
- Added: new APIs, modules, tools, or user-visible behavior.
- Docs: release notes, migration/quickstart changes, and operational notes.
- Tests: focused tests, affected-module build, skipped lanes, and warnings.
- Artifact alignment: release commit, deployment id, tag, module count, and
  public checksum/signature verification.

The release tag must point to the exact commit used for the published source.
Never move or delete a published release tag. A failed or superseded release
gets a new version, such as `9.2.1`; it does not reuse `9.2.0`.
