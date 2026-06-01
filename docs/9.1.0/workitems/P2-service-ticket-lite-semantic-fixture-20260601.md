---
doc_role: workitem
doc_purpose: Track Java lite service-ticket fixture coverage for service SLA semantic samples.
version: 9.1.0
target: ServiceTicketQueryModel lite fixture semantics
status: fixture-ready-recipe-follow-up
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
| Stable semantic promotion | Deferred. Current engine can expose and query details, but cannot yet express the full SLA metric as a governed aggregate/recipe without failed repair attempts. |

## Implementation

| Path | Change |
|---|---|
| `foggy-mcp-launcher/src/main/resources/application-lite.yml` | Added `ServiceTicketQueryModel` to the lite visible semantic model list. |
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-schema.sql` | Added `dim_team`, `service_ticket`, and indexes for team and created time. |
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-data.sql` | Seeded support teams and service-ticket SLA rows for June 2026 plus one May row outside the current-month window. |
| `foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/McpLauncherLiteProfileConfigurationTest.java` | Added regression coverage for visible model-list, schema/data fixtures, and ServiceTicket TM/QM exposure. |

## Testing

| Command / Probe | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -Dtest=McpLauncherLiteProfileConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test -P'!multi-db'` | passed: launcher reactor test, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -DskipTests package -P'!multi-db'` | passed: launcher package reactor build. |
| Fresh Java lite health on `localhost:8066` | passed: actuator status `UP`. |
| Fresh Java lite fixture smoke | passed: `calls=2`, `transport_errors=0`, `mcp_errors=0`, `catalog_models=CustomerOrderLifecycleQueryModel,FactOrderQueryModel,ServiceTicketQueryModel`, `query_rows=3`. |
| Direct analyst MCP service-ticket aggregation | passed: grouped query returned `North Support Team=3`, `Online Support Team=2`, and `South Support Team=2` for June 2026 tickets. |
| Targeted `biz-024` replay | completed: `rows=3`, `transport_errors=0`, `mcp_errors=0`; all rows selected `ServiceTicketQueryModel`. Not promoted because all rows had failed query-model repair attempts before terminal summary. |

## Replay Findings

| Finding | Implication |
|---|---|
| All three source-label rows selected `ServiceTicketQueryModel`. | The catalog/fixture gap is closed. |
| The model attempted `count(if(...))`, interval/date-difference predicates, and alias ratio formulas inside `query_model`. | The current governed single-model payload is not sufficient for this SLA formula family. |
| Final answers were produced by pulling ticket details and letting the LLM summarize the SLA counts/rates. | This is useful exploratory behavior but not stable engine evidence. |
| `POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED` surfaced for free-form alias ratio over grouped counts. | SLA rate needs a governed recipe/post-aggregate shape, not arbitrary formula admission. |

## Progress Tracking

| Dimension | Status | Notes |
|---|---|---|
| Development | complete | Catalog visibility and runnable lite fixture are implemented. |
| Testing | complete | Focused launcher regression, package build, smoke, direct MCP probe, and targeted NL replay completed. |
| Experience | N/A | Pure backend/model fixture change; no UI surface. |
| Semantic promotion | deferred | Keep `biz-024` out of stable semantic gate until the SLA metric can be expressed without LLM post-processing over details. |

## Execution Check-in

| Item | Status |
|---|---|
| Scope implemented as intended | yes |
| Non-goals preserved | yes; no prompt-only rewrite and no arbitrary formula expansion. |
| Code paths listed | yes |
| Basic self-review completed | yes |
| Test status recorded | pass |
| Acceptance readiness | fixture-ready; recipe contract required before stable semantic promotion. |

## Follow-up

Define a governed DSL_CTE / recipe contract for SLA metric families:

| Capability | Needed Contract |
|---|---|
| First-response threshold | Express `firstResponseAt - createdAt <= 48h` as a supported elapsed-time predicate or recipe step. |
| Conditional aggregates | Count total tickets, SLA-met tickets, late-response tickets, and NULL first-response misses without free-form SQL-like `count(if(...))`. |
| NULL first-response miss count | Distinguish not-yet-overdue NULL rows from over-48h unresponded rows using an explicit reference time. |
| Rate derivation | Compute `slaMetCount / NULLIF(totalTicketCount, 0)` as a governed post-aggregate calculation or recipe output. |
