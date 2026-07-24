---
acceptance_scope: version
version: 9.5.0
target: 00e4f090ca868e22d7e386e86ebfa4c01272e0bc
doc_role: acceptance-record
status: signed-off
decision: accepted-with-risks
signed_off_by: codex-reviewer
signed_off_at: 2026-07-24
reviewed_by: codex-same-implementation-session
independent_review: false
blocking_items: []
follow_up_required: yes-before-tag-release-publish
assurance_level: elevated
---

# 9.5.0 Model Legacy Exit Version Signoff

## Document Purpose

- intended_for: release-owner / reviewer / project-root-session
- purpose: 对 9.5.0 第一方 model legacy exit、SPI v2 role truthfulness、breaking 边界和
  owner 批准的 lean authority 形成正式结论。

## Acceptance Basis

- canonical spec: `docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`
- authority-found bug: `docs/9.5.0/workitems/BUG-launcher-smoke-sqlite-fixture-path.md`
- base commit / current `origin/main`: `63a8f15ff870ee40c83c458d7db455d6b5977deb`
- pre-authority candidate: `954539716c4e657f78d75a0ac8c8299644fea2be`
- accepted implementation candidate: `00e4f090ca868e22d7e386e86ebfa4c01272e0bc`
- branch: `codex/v950-legacy-exit`
- candidate delta: 1338 paths, 7763 insertions, 6717 deletions
- environment: independent Git worktree, JDK 17, Maven reactor
- original dirty workspace: read-only and unchanged

## Version Goal Conformance

| Goal / Criterion | Delivered | Evidence | Result |
|---|---|---|---|
| legacy coordinate exit | first-party old aggregate artifact/module `14 -> 0` | architecture guard + final inventory | pass |
| old package exit | production/runtime old package `11 modules / 110 files -> 0` | compile + tracked source guard | pass |
| bridge/bypass exit | legacy adapter and governed direct query bypass allowlist both 0 | `LegacyExitArchitectureTest` + review | pass |
| SPI v2 roles | real MODEL_LOAD and ATOMIC_REFRESH ports backed by engine operations | API/core/engine focused tests | pass |
| capability truthfulness | QUERY/MODEL_LOAD/ATOMIC_REFRESH/CACHE_INVALIDATION role mismatch fails closed | catalog/TCK/JDBC/cache tests | pass |
| provider boundaries | JDBC engine claims real roles; cache remains invalidation-only; other addons do not overclaim | provider/context/TCK evidence | pass |
| compatibility contract | stable model-api QueryFacade retained; approved Maven/package/legacy bridge break documented | public API tests + migration guide | pass-with-intentional-break |
| reactor/artifact | changed consumers compile; launcher artifact contains new engine identity only | affected reactors + artifact inspection | pass |
| governance docs | canonical, migration, breaking, rollback, BUG and acceptance record synchronized | document review | pass |

## Lean Authority and Remediation

The owner-approved command was:

```bash
mvn -B -ntp clean verify -DskipITs \
  -Dsurefire.failIfNoTests=false \
  -Dfailsafe.failIfNoTests=false
```

It ran on pre-authority candidate `954539716c4e657f78d75a0ac8c8299644fea2be`:

- reactor modules 1-28: all SUCCESS;
- exact module summaries: 4960 tests, 0 failures, 1 launcher error, 2 skipped;
- the two skipped tests were existing `ScriptRuntimeTest` cases;
- launcher `DataViewerApiSmokeTest` failed Spring context initialization because its test fixture lookup still named
  deleted path `foggy-dataset-model/src/test/resources/sqlite`;
- client and coverage tail modules were skipped after the launcher failure.

The finding was test-only and did not invalidate modules 1-28 product evidence. It was frozen and fixed under
`BUG-launcher-smoke-sqlite-fixture-path`:

- fixture candidates now point to `foggy-dataset-model-engine`;
- focused launcher command: 27/27 reactor modules SUCCESS;
- `DataViewerApiSmokeTest`: 6 tests, 0 failures/errors/skips, 9.018 seconds;
- full artifact command with tests explicitly skipped: 32/32 reactor modules SUCCESS in 1 minute 59 seconds;
- launcher fat JAR, FSScript client, dataset client and coverage aggregator completed.

No second full root `clean verify` was run. Acceptance therefore uses minimal invalidation-radius evidence:
unchanged authority modules 1-28 plus the post-fix focused launcher smoke and complete 32-module artifact build.

## Additional Executed Evidence

- focused API/core/TCK/engine reactor: 13/13 modules success; API 6, core 6, TCK 3, engine 16 tests.
- JDBC engine operational provider/TCK: 5 tests; 13/13 modules success.
- affected production compile: 30/30 modules success.
- launcher/client affected test-compile: 28/28 modules success.
- JDBC/cache/engine/launcher provider, context and assembly slice: 27/27 modules success;
  cache 23, launcher 7 and selected engine/JDBC tests passed.
- standalone pivot/benchmark test-compile: pivot has no Java source; benchmark 13 main and 2 test sources compiled.
- final static review:
  old POM artifact 0, old root module 0, production/runtime old package 0,
  `LegacyQueryFacadeAdapter` 0, active old fixture path 0, active script old path/FQCN 0,
  `.github` diff 0, protected `docs/9.3.5` diff 0.
- top-level changed shell scripts passed `bash -n`; `git diff --check` passed.
- launcher JAR:
  Spring Boot `JarLauncher`, expected `McpLauncherApplication`, 17 nested Foggy JARs,
  exactly one engine JAR, no old aggregate and no TCK JAR.

No command used `mvn install`.

## Review Findings

- No unauthorized query semantic, namespace isolation, permission, datasource-currentness, data-correctness or
  irreversible migration change was found.
- Stable `model-api QueryFacade` remains the public query contract; engine advanced query ownership is internal.
- MODEL_LOAD and ATOMIC_REFRESH claims correspond to callable roles and existing namespace-aware loader/atomic
  catalog publication behavior.
- Duplicate identity, missing provider, unsupported capability and capability-role mismatch remain fail closed.
- The launcher fixture omission was a core signoff finding until fixed; the accepted candidate includes the fix.
- Maven artifact removal, Java package rename and deprecated bridge deletion are intentional, explicitly approved
  source/binary breaking changes.

## Evidence Sufficiency

- assurance_level: elevated
- sufficient_for_scope: yes; all non-waivable legacy zero guards and provider truthfulness checks pass, all changed
  production modules compile, affected behavior has focused tests, and the authority-found test defect is closed.
- not_sufficient_for: claiming exhaustive cross-database, semantic replay, portable replay or release publication
  assurance.
- omitted_validation: Step 5/Step 7, semantic/portable replay, source-seal, generic five-database matrix,
  GitHub CI, tag, release and publish.

## Accepted Risks

- Review and signoff were performed in the same Codex session and are not organizationally independent.
- There is no single post-fix full root `clean verify` green run; the evidence is a documented composite with minimal
  invalidation radius.
- External Maven/source/binary consumers must migrate coordinates, imports, reflection/configuration strings and
  removed bridge usage before adopting 9.5.0.
- Static guards cannot prove every third-party reflective or serialized type-name dependency.
- Expanded semantic/portable replay, source-seal and multi-database authority were not run.

None is a core blocker for the approved 9.5.0 legacy-exit scope. They remain release-owner considerations before
tag/release/publish.

## Final Decision

- decision: `accepted-with-risks`
- core blocking items: none
- rationale: all approved legacy-exit and SPI v2 hardening criteria pass; the only lean-authority defect was test-only,
  is fixed on the accepted SHA, and has proportionate post-fix evidence. Remaining gaps are explicit process breadth
  or intentional external compatibility risks.
- follow-up: before tag/release/publish, decide whether to authorize semantic/portable replay plus the full database
  matrix; estimated wall time is 2-4 hours plus external fixture startup, with value in detecting reflective,
  cross-database SQL and portable-replay integration defects.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: yes-before-tag-release-publish
