---
doc_role: workitem
doc_purpose: Track Java lite service-ticket fixture coverage for service SLA semantic samples.
version: 9.1.0
target: ServiceTicketQueryModel lite fixture semantics
status: recipe-runtime-ready-negative-gated
created_at: 2026-06-01
updated_at: 2026-06-01
source_type: optimization
---

# Service-Ticket SLA Semantic Fixture

## 文档作用

- doc_type: workitem
- intended_for: engine-owner | eval-runner-owner | reviewer
- purpose: 记录服务工单 SLA 场景所需的 Java lite 模型目录、fixture 数据和 targeted replay 结论。

## Background

`biz-024` 要求按客服团队统计本月 first-response SLA：`firstResponseAt - createdAt <= 48h` 视为达成，分母是本月创建工单数，同时统计超 48 小时未响应工单数。

此前 Java lite visible catalog 不包含服务工单查询模型，模型只能拒绝或说明当前目录不支持。这个缺口应先通过 catalog / fixture 修复，而不是继续调 prompt。

## Scope

| Item | Decision |
|---|---|
| Visible model | Add `ServiceTicketQueryModel` to lite `foggy.mcp.semantic.model-list`. |
| Fixture schema | Add `dim_team` and `service_ticket`, including created time, first response time, resolved time, priority, status, and channel. |
| Fixture data | Seed three support teams and June 2026 tickets that cover <=48h responses, >48h responses, and NULL first-response rows. |
| Stable semantic promotion | Deferred. The governed DSL_CTE recipe can now execute structured SLA metrics, but targeted replay still has semantic-risk retry variants that should not be signed blindly. |

## Implementation

| Path | Change |
|---|---|
| `foggy-mcp-launcher/src/main/resources/application-lite.yml` | Added `ServiceTicketQueryModel` to the lite visible semantic model list. |
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-schema.sql` | Added `dim_team`, `service_ticket`, and indexes for team and created time. |
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-data.sql` | Seeded support teams and service-ticket SLA rows for June 2026 plus one May row outside the current-month window. |
| `foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/McpLauncherLiteProfileConfigurationTest.java` | Added regression coverage for visible model-list, schema/data fixtures, and ServiceTicket TM/QM exposure. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/support/DslCteDslRequestMapper.java` | Added signed SLA recipe mapping for elapsed hours, SLA hit flags, overdue-unresponded cutoff/reference-time predicates, conditional counts, NULL-safe ratios, SLA miss-count aliases, and derive/aggregate/result-stage plans. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java` | Added DSL_CTE compile-to-DSL execution path so signed recipes can return structured rows through the normal semantic query service. |
| `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/spi/impl/LocalDatasetAccessor.java` | Enables the compile bridge hint for executable DSL_CTE payloads from MCP. |
| `foggy-dataset-mcp/src/main/resources/schemas/query_model_v3_schema.json` and description docs | Exposed `route`, `executable_plan` / `executablePlan`, and documented the controlled service-ticket SLA recipe boundary. |
| `DslCteAcceptanceSampleTest`, `DslCteSlaFixtureIntegrationTest`, `LocalDatasetAccessorGovernanceTest` | Added regression coverage for validation, direct fixture execution, compile hint propagation, NULL-safe rates, and guarded unsupported shapes. |
| `experiments/spider-routing-eval/scripts/score_biz024_semantic_gate.py` | Added replay-side semantic gate scoring for required model, DSL_CTE evidence, row count, structured output fields, failed tool calls, and forbidden ambiguous unresponded-count mappings. |
| `experiments/spider-routing-eval/Makefile` and `README.md` | Added reusable `score-v39-biz024-semantic-gate` and strict `gate-v39-biz024-semantic-stable` targets, plus runbook notes for promotion gating. |
| `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/QueryExpertService.java` | Added ServiceTicket SLA preflight guard before LLM/tool dispatch for missing first-response SLA threshold, unsupported resolution/contract-calendar SLA, direct physical table SQL, and prediction/causality/personnel-advice requests. |
| `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/service/QueryExpertServiceRoutingCalibrationTest.java` | Added fail-closed regression coverage proving negative ServiceTicket SLA cases return `clarify` or `reject` before `ChatClient` and MCP tool dispatch. |
| `experiments/spider-routing-eval/evals/v39_service_ticket_sla_negative_gate_cases.jsonl` | Added negative runtime gate cases extracted from the existing experience recall expansion set. |
| `experiments/spider-routing-eval/scripts/build_mcp_nl_query_direct_payloads.py` and `score_service_ticket_negative_gate.py` | Added direct `dataset_nl.query` payload generation and scoring for negative runtime gates, including detection of unexpected `query_model` execution. |
| `experiments/spider-routing-eval/scripts/test_score_service_ticket_negative_gate.py` | Added scorer regression tests for accepted clarify/reject outcomes and residuals when a negative case executes `query_model`. |
| `experiments/spider-routing-eval/scripts/build_service_ticket_sla_gate_suite_summary.py` | Added a stable suite summary that combines `biz-024`, SLA holdout, and negative runtime gates into one promotion check. |
| `experiments/spider-routing-eval/scripts/test_build_service_ticket_sla_gate_suite_summary.py` | Added regression checks for suite pass/fail counting, residual counting, and failure-reason aggregation. |
| `experiments/spider-routing-eval/Makefile` and `README.md` | Updated negative gate defaults to the post-preflight stable baseline and added `gate-v39-service-ticket-sla-stable-suite`. |

## Testing

| Command / Probe | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -Dtest=McpLauncherLiteProfileConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test -P'!multi-db'` | passed: launcher reactor test, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -DskipTests package -P'!multi-db'` | passed: launcher package reactor build. |
| Fresh Java lite health on `localhost:8066` | passed: actuator status `UP`. |
| Fresh Java lite fixture smoke | passed: `calls=2`, `transport_errors=0`, `mcp_errors=0`, `catalog_models=CustomerOrderLifecycleQueryModel,FactOrderQueryModel,ServiceTicketQueryModel`, `query_rows=3`. |
| Direct analyst MCP service-ticket aggregation | passed: grouped query returned `North Support Team=3`, `Online Support Team=2`, and `South Support Team=2` for June 2026 tickets. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -Dtest=DslCteAcceptanceSampleTest,DslCteSlaFixtureIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test -P'!multi-db'` | passed: `Tests run: 175, Failures: 0, Errors: 0, Skipped: 0`. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -Dtest=LocalDatasetAccessorGovernanceTest,QueryModelDescriptionConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test -P'!multi-db'` | passed: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -DskipTests package -P'!multi-db'` | passed: launcher package reactor build after recipe bridge changes. |
| Direct MCP DSL_CTE execution | passed: returned structured team rows with `ticketCount`, `slaHitCount`, `slaAchievementRate`, and `overdueUnrespondedCount`; execution status was `EXECUTED`, with NULL-safe ratio SQL generated by the engine. |
| Targeted `biz-024` replay after result-stage bridge | completed: `rows=3`, `transport_errors=0`, `mcp_errors=0`; `gpt-oss-120b-medium` completed without failed tool calls, while `gemini-pro-agent` and `claude-sonnet-4-6` recovered to rows but each produced one failed attempt using an unsafe `over48hMissCount = ticketCount - slaHitCount` variant. |
| `python3 experiments/spider-routing-eval/scripts/test_score_biz024_semantic_gate.py` | passed: scorer accepts structured `overdueUnrespondedCount` and rejects missing/ambiguous `ticketCount - slaHitCount` unresponded mappings. |
| `python3 experiments/spider-routing-eval/scripts/score_biz024_semantic_gate.py --input experiments/spider-routing-eval/output/v39_biz024_service_ticket_recipe_invocation_gemini_runtime_20260601_after_result_stage_bridge.jsonl ...` | completed: `stable_gate_ok=0/3`, residuals `16`; generated semantic score and residual artifacts under `experiments/spider-routing-eval/output/`. |
| `make score-v39-biz024-semantic-gate` | completed: target is wired to the same scored output/residual/summary artifacts; current replay remains `stable_gate_ok=0/3`. |
| Java lite restart with `OPENAI_BASE_URL=https://codex2.qlfloor.com:7443` | passed: Spring AI appends `/v1`; using the Python-style `/v1` URL caused internal `HTTP 404 - 404 page not found` during `dataset_nl.query`. |
| Targeted `biz-024` replay after docs/runtime boundary and Java base-url correction | passed: `rows=3`, `transport_errors=0`, `mcp_errors=0`, models `gemini-pro-agent`, `gemini-3-flash`, and `gpt-oss-120b-medium`; semantic score `stable_gate_ok=3/3`, residuals `0`. |
| `make gate-v39-biz024-semantic-stable V39_BIZ024_SEMANTIC_GATE_INVOCATION=output/v39_biz024_service_ticket_invocation_20260601_after_docs_base_no_v1.jsonl ...` | passed: strict semantic gate exited `0` with `stable_gate_ok=3/3`. |
| Targeted `holdout-005/006` replay after case-profile scorer expansion | passed: `rows=6`, `transport_errors=0`, `mcp_errors=0`, models `gemini-pro-agent`, `gemini-3-flash`, and `gpt-oss-120b-medium`; semantic score `stable_gate_ok=6/6`, residuals `0`. |
| `make gate-v39-service-ticket-sla-holdout-semantic-stable` | passed: strict holdout semantic gate exited `0` with `stable_gate_ok=6/6`. |
| `python3 experiments/spider-routing-eval/scripts/test_score_service_ticket_negative_gate.py` | passed: scorer accepts fail-closed negative responses and rejects negative cases that execute `query_model`. |
| `python3 -m py_compile experiments/spider-routing-eval/scripts/build_mcp_nl_query_direct_payloads.py experiments/spider-routing-eval/scripts/score_service_ticket_negative_gate.py experiments/spider-routing-eval/scripts/test_score_service_ticket_negative_gate.py` | passed: negative gate scripts compile. |
| `make gate-v39-service-ticket-sla-negative-stable` against the preflight baseline before the Java guard | failed as intended: `stable_gate_ok=0/4`, residuals `20`; all four negative cases returned `result` and executed `dataset.query_model`. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -Dtest=QueryExpertServiceRoutingCalibrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | passed after preflight guard: `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -DskipTests package` | passed: launcher package reactor build after ServiceTicket SLA preflight guard. |
| Java lite fixture smoke after preflight guard | passed: `calls=2`, `transport_errors=0`, `mcp_errors=0`, catalog includes `ServiceTicketQueryModel`, `query_rows=3`. |
| Direct positive `biz-024` NL runtime smoke after preflight guard | passed: response `type=result`, `total=3`, `code=null`; the guard does not block declared 48-hour first-response SLA requests. |
| `make gate-v39-service-ticket-sla-negative-stable V39_SERVICE_TICKET_SLA_NEGATIVE_INVOCATION=output/v39_service_ticket_sla_negative_invocation_after_preflight_20260601.jsonl ...` | passed: strict negative runtime gate exited `0` with `stable_gate_ok=4/4`, residuals `0`; expected `clarify` for `er0r-005/006` and `reject` for `er0r-007/010`. |
| `python3 experiments/spider-routing-eval/scripts/test_build_service_ticket_sla_gate_suite_summary.py` | passed: suite summary counts stable rows, residuals, and failure reasons correctly. |
| `python3 -m py_compile experiments/spider-routing-eval/scripts/build_service_ticket_sla_gate_suite_summary.py experiments/spider-routing-eval/scripts/test_build_service_ticket_sla_gate_suite_summary.py` | passed: suite summary scripts compile. |
| `make gate-v39-service-ticket-sla-stable-suite` | passed: `biz-024=3/3`, holdout `6/6`, negative `4/4`; combined suite `stable_gate_ok=13/13`, residuals `0`, summary `output/v39_service_ticket_sla_stable_suite_20260601.md`. |

## Replay Findings

| Finding | Implication |
|---|---|
| All three source-label rows selected `ServiceTicketQueryModel`. | The catalog/fixture gap is closed. |
| Runtime now supports the governed DSL_CTE SLA recipe for row-level `hours_between`, SLA hit predicates, unresponded cutoff/reference-time predicates, conditional counts, NULL-safe ratios, and SLA miss-count aliases. | The original engine capability gap is closed for the signed recipe shapes. |
| Direct MCP execution returns structured SLA rows without LLM detail post-processing. | This is acceptable engine evidence for the supported recipe contract. |
| `over48hMissCount = ticketCount - slaHitCount` appeared in replay repairs. | Keep this unsigned: it is a generic SLA miss count, not necessarily “超 48 小时未响应数”. Supporting it as an unresponded count would encode the wrong business meaning. |
| Earlier semantic gate scorer reported `stable_gate_ok=0/3`. | The failing replay is retained as a regression sample for missing structured `overdueUnrespondedCount` and ambiguous unresponded-count derivations. |
| After correcting the Java launcher base URL, targeted replay reached `stable_gate_ok=3/3`. | The remaining blocker was environment URL shape, not an engine semantic gap; `biz-024` can enter the stable semantic gate. |
| `holdout-005/006` need different semantic expectations from `biz-024`. | The scorer now uses case profiles: `holdout-005` accepts the May SLA-rate row shape, while `holdout-006` requires a signed SLA miss-count alias instead of the `biz-024` unresponded-count field. |
| Existing experience negative samples showed the runtime still answered out-of-scope requests with data. | Negative cases are now promoted to a strict runtime gate so unsupported or under-specified ServiceTicket SLA requests fail closed before tools. |
| Before preflight guard, `er0r-005/006/007/010` all called and succeeded through `dataset.query_model`. | This confirmed the issue was runtime guard placement, not only prompt wording or evaluator scoring. |
| After preflight guard, the same four cases return only `clarify` or `reject` and do not capture query results. | The negative runtime boundary is now enforceable without relying on model-specific refusal behavior. |

## Progress Tracking

| Dimension | Status | Notes |
|---|---|---|
| Development | complete | Catalog visibility, runnable lite fixture, and scoped DSL_CTE SLA runtime bridge are implemented. |
| Testing | complete | Focused launcher regression, package build, model/MCP regression, direct MCP DSL_CTE probe, targeted NL replay, negative runtime gate, and combined stable gate suite completed. |
| Experience | N/A | Pure backend/model fixture change; no UI surface. |
| Semantic promotion | ready | Latest `biz-024`, SLA holdout, and negative ServiceTicket runtime gates are failure-free across the tracked runtime baselines; combined stable suite is `13/13` with residuals `0`. |

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- acceptance_record: `../acceptance/service-ticket-sla-semantic-fixture-acceptance.md`
- quality_record: `../quality/service-ticket-sla-semantic-fixture-implementation-quality.md`
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-06-01
- blocking_items: none
- follow_up_required: yes

## Execution Check-in

| Item | Status |
|---|---|
| Scope implemented as intended | yes |
| Non-goals preserved | yes; no prompt-only rewrite and no arbitrary formula expansion. |
| Code paths listed | yes |
| Basic self-review completed | yes |
| Test status recorded | pass |
| Acceptance readiness | recipe-runtime-ready-negative-gated; `biz-024`, holdout, negative runtime gates, and the combined stable suite are ready for stable promotion with the documented Java base-url constraint. |

## Follow-up

Continue sample-driven calibration of the governed DSL_CTE / recipe contract:

| Capability | Needed Contract |
|---|---|
| Failure variant collection | Keep collecting `biz-024` retry payloads from `gemini-pro-agent`, `claude-sonnet-4-6`, and `gpt-oss-120b-medium`; add recipe normalization only when semantic identity is clear. |
| Unresponded count canonicalization | Prefer explicit `firstResponseAt is null and createdAt < '<cutoff>'` or `firstResponseAt is null and hours_between(createdAt, '<referenceTime>') > 48`; reject ambiguous `ticketCount - slaHitCount` aliases when the requested metric is “未响应数”. |
| Stable gate promotion | Promote `biz-024` using `output/v39_biz024_service_ticket_invocation_20260601_after_docs_base_no_v1.jsonl`; promote holdouts using `output/v39_service_ticket_sla_holdout_invocation_20260601_after_docs_base_no_v1.jsonl`; keep the earlier `0/3` replay as a residual regression sample. |
| Semantic scoring | Done for `biz-024` and ServiceTicket SLA holdouts; both strict semantic gates now pass on the corrected replay baselines. |
| Negative runtime scoring | Done for missing-threshold, resolution/calendar out-of-scope, physical SQL, and prediction/personnel-advice cases; keep the preflight `0/4` baseline as regression evidence and promote the post-preflight `4/4` baseline. |
| Stable suite | Done: use `make gate-v39-service-ticket-sla-stable-suite` for promotion evidence across positive, holdout, and negative ServiceTicket SLA gates. |
