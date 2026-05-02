---
doc_role: release_checklist
doc_purpose: Track the local release-candidate handoff checklist for Foggy Pivot Engine 9.1.0.
version: 9.1.0
target: Foggy Pivot Engine 9.1.0 RC
status: ready-for-rc2-review
created_at: 2026-05-02
---

# 9.1.0 RC Release Checklist

## Scope

This checklist covers the repo-level Pivot Engine 9.1.0 RC handoff after Stage 5A B2 production domain transport and the scoped Stage 5B C2 Cascade Generate implementation were enabled and verified. It is not a package publication, signing, or remote deployment record.

Note: `v9.1.0-rc.1` remains the earlier B2-oriented RC tag. The C2-inclusive release-candidate tag is `v9.1.0-rc.2` and must not be confused with `v9.1.0-rc.1`.

## Release Identity

| Item | Value |
|---|---|
| Branch | `9.1.0` |
| B2 production commit | `5992e246 feat(pivot): enable stage5a large-domain transport` |
| RC evidence commit | `2cfd32d3 docs(pivot): record 9.1.0 rc final gate` |
| Local RC tag at initial handoff | `v9.1.0-rc.1` |
| C2 production commit | `ea8b7e48 feat(pivot): implement c2 cascade generate topn` |
| C2 evidence/test supplement commit | `60d9be6e test(pivot): cover preagg topn parameter order` |
| Current C2-inclusive RC tag | `v9.1.0-rc.2` |
| Recommended release label | `release candidate` |
| Acceptance decision | `accepted-with-risks` |

## Verification Evidence

| Gate | Result | Command / Notes |
|---|---|---|
| Baseline ancestor | PASS | `git merge-base --is-ancestor b3482490 HEAD` |
| Full release gate | PASS | `./scripts/verify-pivot-v9-release.ps1` passed after B2 production commit. |
| Renderer gate | PASS | `mvn test -pl foggy-dataset-model -am "-Dtest=DomainRelationRendererTest" "-P!multi-db" "-Dsurefire.failIfNoSpecifiedTests=false"` passed with 7 tests. |
| C2 validation/refusal gate | PASS | `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateValidationTest test` passed. |
| C2 staged SQL/parity gate | PASS | `mvn -pl foggy-dataset-model "-Dtest=PivotAxisDomainSqlPlannerTest,PivotAxisDomainSqlPlannerCascadeTest,PivotCascadeGenerateSqlParityIntegrationTest,PivotIntegrationTest#testParentChildTopN" test` passed. |
| Diff hygiene | PASS | `git diff --check` passed before RC evidence commit and after B2 production commit. |
| Worktree hygiene | PASS with doc delta | Worktree was clean at `v9.1.0-rc.2`; later doc-only acceptance/routing updates are pending review. |

## Release Owner Actions

- For the C2-inclusive RC, use `v9.1.0-rc.2`.
- Do not move or reuse `v9.1.0-rc.1`; it remains the original B2-oriented RC tag.
- Confirm whether external package signing, artifact publication, or release-note publication happens outside this repository.
- Review `docs/9.1.0/acceptance/pivot-stage5b-c2-release-owner-review.md` for the included C2 v1 boundary before publication.

## Rollback / Disable Strategy

- If Stage 5A transport causes a dialect-specific production issue, unsupported or unsafe paths already fail closed through `DomainTransportRefusalException`.
- If a broad rollback is required before publication, revert B2 production commit `5992e246` and the follow-up RC evidence/docs commits together.
- Do not replace transport refusal with full in-memory fallback for large domains unless correctness and memory behavior are separately approved.

## Residual Risks

- MySQL 5.7 has renderer-level threshold coverage, but no external MySQL 5.7 live-database parity run was recorded in this closeout.
- Production telemetry should confirm real domain-size distributions before GA promotion.
- SQL Server cascade execution remains refused until dialect-specific oracle and CI evidence exist.
- Tree + cascade, cross-axis cascade, three-level cascade, having-only cascade, non-additive cascade totals, and outer Pivot cache are deferred to 9.2.0.
