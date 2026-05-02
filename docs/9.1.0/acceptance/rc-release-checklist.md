---
doc_role: release_checklist
doc_purpose: Track the local release-candidate handoff checklist for Foggy Pivot Engine 9.1.0.
version: 9.1.0
target: Foggy Pivot Engine 9.1.0 RC
status: ready-for-release-owner-review
created_at: 2026-05-02
---

# 9.1.0 RC Release Checklist

## Scope

This checklist covers the repo-level Pivot Engine 9.1.0 RC handoff after Stage 5A B2 production domain transport was enabled and verified. It is not a package publication, signing, or remote deployment record.

Note: `v9.1.0-rc.1` points to commit `0c146da7`, before the later uncommitted PIVOT-91-C2 implementation/signoff changes in the working tree. If C2 is included, commit those changes and cut a new RC tag rather than moving or reusing `v9.1.0-rc.1`.

## Release Identity

| Item | Value |
|---|---|
| Branch | `9.1.0` |
| B2 production commit | `5992e246 feat(pivot): enable stage5a large-domain transport` |
| RC evidence commit | `2cfd32d3 docs(pivot): record 9.1.0 rc final gate` |
| Local RC tag at initial handoff | `v9.1.0-rc.1` |
| Recommended release label | `release candidate` |
| Acceptance decision | `accepted-with-risks` |

## Verification Evidence

| Gate | Result | Command / Notes |
|---|---|---|
| Baseline ancestor | PASS | `git merge-base --is-ancestor b3482490 HEAD` |
| Full release gate | PASS | `./scripts/verify-pivot-v9-release.ps1` passed after B2 production commit. |
| Renderer gate | PASS | `mvn test -pl foggy-dataset-model -am "-Dtest=DomainRelationRendererTest" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed with 7 tests. |
| Diff hygiene | PASS | `git diff --check` passed before RC evidence commit and after B2 production commit. |
| Worktree hygiene | PASS | Worktree was clean before this checklist update. |

## Release Owner Actions

- For the original B2-oriented RC, push the branch/tag when ready: `git push origin 9.1.0` and `git push origin v9.1.0-rc.1`.
- For a C2-inclusive RC, first commit the C2 implementation/signoff changes, then cut and push a new RC tag.
- Confirm whether external package signing, artifact publication, or release-note publication happens outside this repository.
- Keep C2 Cascade Generate implementation out of `v9.1.0-rc.1` unless release ownership explicitly chooses to cut a new C2-inclusive RC.
- If C2 is included, review `docs/9.1.0/acceptance/pivot-stage5b-c2-release-owner-review.md` before tagging.

## Rollback / Disable Strategy

- If Stage 5A transport causes a dialect-specific production issue, unsupported or unsafe paths already fail closed through `DomainTransportRefusalException`.
- If a broad rollback is required before publication, revert B2 production commit `5992e246` and the follow-up RC evidence/docs commits together.
- Do not replace transport refusal with full in-memory fallback for large domains unless correctness and memory behavior are separately approved.

## Residual Risks

- MySQL 5.7 has renderer-level threshold coverage, but no external MySQL 5.7 live-database parity run was recorded in this closeout.
- Production telemetry should confirm real domain-size distributions before GA promotion.
- Stage 5B C2 has a separate `accepted-with-risks` feature signoff after `v9.1.0-rc.1`; release ownership must decide whether it belongs in a new RC.
