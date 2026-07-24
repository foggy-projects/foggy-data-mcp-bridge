---
acceptance_scope: version
version: 9.4.1
target: v941-spi-v2-adoption-hardening
doc_role: acceptance-record
status: signed-off
decision: accepted-with-risks
signed_off_by: codex-reviewer
signed_off_at: 2026-07-24
reviewed_by: codex-same-implementation-session
independent_review: false
blocking_items: []
follow_up_required: yes-9.5.0-prerequisites
assurance_level: standard
---

# 9.4.1 SPI v2 Adoption/Hardening Version Signoff

## Document Purpose

- intended_for: release-owner / reviewer / project-root-session
- purpose: 对 9.4.1 无破坏 adoption、legacy debt freeze 和 provider/TCK hardening 形成正式结论。

## Acceptance Basis

- canonical spec: `docs/9.4.1/workitems/FEATURE-v941-spi-v2-adoption-hardening.md`
- base commit / current `origin/main`: `afd567e59261832b49ae640a32f9d4a9d5efe4ab`
- reviewed implementation candidate: `b5ac732f3e6af07205a708c3a57e48972b8b8c9a`
- branch: `codex/v941-spi-v2-adoption-hardening`
- candidate delta: 31 paths, 878 insertions, 47 deletions
- environment: independent Git worktree, JDK 17, Maven reactor
- original dirty workspace: read-only and unchanged

## Version Goal Conformance

| Goal / Criterion | Delivered | Evidence | Result |
|---|---|---|---|
| adoption inventory | direct aggregate `15 -> 14`; legacy import `12/114 -> 11/110` | architecture guard + reproducible inventory | pass |
| dependency freeze | exact direct-POM allowlist and per-module import ceilings | `LegacyAdoptionArchitectureTest` | pass |
| first-party query adoption | GraphQL/DataViewer stable request paths; legacy public converter retained | focused unit/controller/service tests | pass |
| auto-config hardening | nine hard class imports changed to exact `afterName` | affected compile/context tests | pass |
| provider fail closed | QUERY/CACHE_INVALIDATION impostors rejected at discovery | core catalog tests | pass |
| capability truthfulness | JDBC=QUERY, cache=CACHE_INVALIDATION only; immutable descriptors | JDBC/cache TCK | pass |
| compatibility | aggregate, bridge APIs, constructors, SPI and coordinates retained | diff review + affected compilation | pass |

## Evidence Coverage Summary

| Area | Classification | Evidence | Gaps | Result |
|---|---|---|---|---|
| architecture/inventory | core-blocker | 4 architecture guard tests; final 14/11/110/1 counts | static patterns do not prove dynamic call graphs | pass |
| provider/catalog/TCK | core-blocker | API/core/TCK/JDBC tests plus cache TCK/context | load/namespace/atomic refresh intentionally absent | pass |
| consumer compatibility | core-blocker | GraphQL/DataViewer converter/controller/service tests | external third-party consumers not rebuilt | pass |
| addon auto-config | major | 23-module affected compile and selected context contracts | no broad application matrix | pass |
| full release authority | scoped-risk | owner explicitly excluded for 9.4.1 | Step 5/7, replay, source-seal, five-DB and CI not run | accepted-risk |

## Executed Evidence

- `mvn -pl foggy-dataset-model-api,foggy-dataset-model-core,foggy-dataset-model-tck,foggy-dataset-model-jdbc -am test -DskipITs -Dsurefire.failIfNoTests=false -Dfailsafe.failIfNoTests=false`
  - BUILD SUCCESS; API 8, core 6, TCK 3, JDBC 5 tests.
- `mvn -pl foggy-dataset-model-web,addons/foggy-dataset-graphql,addons/foggy-data-viewer -am test -DskipITs -Dtest=ModelBackendWebAutoConfigurationTest,GraphqlToDslConverterTest,StableQueryFacadeRequestMapperTest,ViewerApiControllerTest,MemberQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.failIfNoTests=false -Dfailsafe.failIfNoTests=false`
  - 16/16 reactor modules succeeded in 59.965 seconds;
  - web 4, DataViewer 21, GraphQL 8 selected tests passed.
- `mvn -pl foggy-dataset-memory-grid-bridge,foggy-dataset-mcp,addons/foggy-odoo-bridge-java,addons/foggy-dataset-model-vector,addons/foggy-dataset-model-mongo,addons/foggy-dataset-model-preagg,addons/foggy-dataset-model-cache,addons/foggy-dataset-graphql,addons/foggy-data-viewer -am test -DskipITs -Dtest=QueryCacheBackendProviderTckTest,QueryCacheAutoConfigurationContextTest,QueryCacheBackendProviderAutoConfigurationTest,MongoModelAutoConfigurationContractTest,VectorModelAutoConfigurationContractTest,GraphqlAddonAutoConfigurationTest,DataViewerAutoConfigurationContextTest,DataViewerAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.failIfNoTests=false -Dfailsafe.failIfNoTests=false`
  - 23/23 reactor modules succeeded in 2 minutes 14 seconds;
  - DataViewer 6, Mongo 6, Vector 7, cache 23 and GraphQL 5 selected tests passed;
  - Odoo, MCP, memory-grid, preagg and all other changed production modules compiled.
- final source inventory:
  - 14 direct legacy aggregate POMs, exact allowlist match;
  - 11 modules / 110 production Java files importing old packages;
  - zero production imports of `LegacyQueryFacadeAdapter` or hard `DbModelAutoConfiguration` outside aggregate;
  - one approved direct query bypass, zero unapproved bypasses.
- `git diff --check` and frozen candidate review passed.

No command used `mvn install`.

## Review Findings and Remediation

The initial GraphQL/DataViewer affected run failed in the web test fixture because the fixture advertised QUERY and
CACHE_INVALIDATION while implementing neither corresponding role. This was the expected new fail-closed behavior,
not a production regression. The fixture was corrected to implement both operational ports, then the complete focused
selection passed. Repository search and affected tests found no production provider with the same overclaim.

Review additionally confirmed:

- stable request conversion retains the legacy adapter's field-visible mapping, pagination, authorization and explicit
  namespace semantics while returning an immutable top-level query map;
- `afterName` values preserve the exact legacy auto-configuration JVM name;
- catalog discovery validation is deliberately limited to already migrated QUERY and CACHE_INVALIDATION roles;
- no MODEL_LOAD, namespace isolation or ATOMIC_REFRESH provider/TCK claim was introduced;
- no public API/SPI was deleted or re-signed, and no Maven coordinate changed.

## Evidence Sufficiency

- assurance_level: standard
- sufficient_for_scope: yes; every changed production module compiled and all changed contract/query/provider paths
  have focused tests or context contracts.
- not_sufficient_for: deleting the compatibility aggregate/bridge or claiming full release authority.
- omitted_validation: Step 5/Step 7, authority, semantic/portable replay, source-seal, generic five-database matrix,
  GitHub CI, tag, release and publish; all were explicitly outside the approved 9.4.1 budget.

## Accepted Risks

- Review was performed in the same Codex implementation session and is not organizationally independent.
- The static architecture guard freezes known source patterns but cannot prove every reflective or dynamic call path.
- 14 direct aggregate dependencies, 110 legacy-package production files and one approved client proxy bypass remain.
- Model load, namespace isolation and atomic refresh do not yet have truthful v2 Provider/TCK coverage.
- Owner-excluded full release-governance authority was not executed.

None is a core blocker for the approved non-breaking 9.4.1 scope. They are blockers or prerequisites for an unqualified
legacy-removal decision.

## Final Decision

- decision: `accepted-with-risks`
- core blocking items: none
- rationale: all approved adoption, dependency-freeze, compatibility and provider/TCK hardening criteria passed their
  affected evidence; remaining gaps are explicitly retained legacy scope or owner-excluded process assurance.
- follow-up: use `docs/9.4.1/9.5.0-entry-assessment.md` and obtain explicit owner authorization before any 9.5.0 work.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.4.1/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: yes-9.5.0-prerequisites
