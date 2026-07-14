---
doc_role: acceptance-evidence-plan
doc_purpose: Define evidence, gate order and final version signoff location for 9.3.3.
version: 9.3.3
status: completed
created_at: 2026-07-13
updated_at: 2026-07-14
---

# 9.3.3 Acceptance Evidence Plan

## 文档作用

- doc_type: acceptance-evidence
- intended_for: execution agent / quality reviewer / coverage auditor / release owner
- purpose: 明确实现完成后必须形成什么证据、按什么顺序复核、最终在哪里签收。

## Required Material

| 材料 | 目标路径 | 当前状态 |
|---|---|---|
| requirement/contract/plan | `docs/9.3.3` 当前文档 | completed / signed-off |
| progress + self-check | `progress/model-lifecycle-concurrency-progress.md` | completed；self-check passed-to-formal-quality-gate |
| test evidence | `test/model-lifecycle-concurrency-test-plan.md` | completed；replacement Batch 7 authority passed |
| Batch 2 exit | `evidence/batch-2/namespace-scope-exit-20260713.md` | completed；25 product + 7 compatibility green |
| Batch 3 exit | `evidence/batch-3/catalog-binding-exit-20260713.md` | completed；run `20260713T150948Z-1719636`；149 tests green |
| Batch 4 exit | `evidence/batch-4/single-flight-exit-20260713.md` | completed；run `20260713T164144Z-1910217`；139 tests green；three `SF-*` passed |
| Batch 5 exit | `evidence/batch-5/atomic-refresh-exit-20260714.md` | completed；run `20260713T200646Z-2120785`；90 tests / 19 owning reports green |
| Batch 6 entry | `evidence/batch-6/entry-checkin-20260714.md` | historical entry boundary；catalog expected-red assigned at entry |
| Batch 6 Catalog Authority | `evidence/batch-6/catalog-authority-green-20260714.md` | `CATALOG-AUTHORITY` passed；run `20260714T021057Z-2445113`；53 tests / 7 owning reports green；later replayed by Batch 6 exit |
| Batch 6 Cache Identity/Cross-JVM | `evidence/batch-6/cache-identity-cross-jvm-green-20260714.md` | Step 4 authoritative run `20260714T025444Z-2628329` passed independent second review；`CACHE-CROSS-JVM` passed |
| Batch 6 Pivot Cache Generation | `evidence/batch-6/pivot-cache-generation-green-20260714.md` | Step 5 authoritative run `20260714T032135Z-2678670`；direct 38/4 + supporting 61/3 = 99/7 green；`CACHE-GEN` passed；independent second review no blocker |
| Batch 6 REAL-QUERY | `evidence/batch-6/real-query-green-20260714.md` | Step 6 authoritative run `20260714T041047Z-2755326`；11 tests / 6 Failsafe reports green；`REAL-QUERY` passed；independent post-run review no blocker |
| Batch 6 Steps 2/3 fresh replay | `evidence/batch-6/cache-identity-replay-green-20260714.md` | Step 7 child authority `20260714T044313Z-2824177`；current Step 2 74 + Step 3 one = 75 tests / 6 reports green；independent post-run review no blocker |
| Batch 6 aggregate exit | `evidence/batch-6/batch-6-exit-20260714.md` | completed；run `20260714T045604Z-2854237`；11 children、676 criteria / 677 XML testcases / 99 reports、4/4 expected-negative、0 red、F/E/S=0；two independent reviews no blocker |
| Batch 7 compatibility/regression exit | `evidence/batch-7/batch-7-regression-exit-20260714.md` | superseded sealed run `20260714T074009Z-3153871`；内容审计 no blocker，但 formal quality gate 发现 watcher authority gaps，修复后不得复用为最终 signoff evidence |
| Batch 7 replacement authority | `evidence/batch-7/batch-7-regression-exit-20260714-r2.md` | passed；`20260714T084351Z-3271604`=`3824/519/F0/E0/S3`；independent audit no blocker |
| implementation quality | `quality/model-lifecycle-concurrency-implementation-quality.md` | reviewed；ready-with-risks |
| coverage audit | `coverage/model-lifecycle-concurrency-coverage-audit.md` | reviewed；ready-with-gaps |
| version acceptance | `acceptance/version-signoff.md` | signed-off / accepted-with-risks |

不要求 UI screenshot/playwright；experience=N/A。Batch 6 Steps 2/3 的
Caffeine/Redis strong-key evidence 与 Step 5 Pivot authority 已共同提升
`CACHE-GEN`；Step 4 已提升 `CACHE-CROSS-JVM`；Step 6 已提升
`REAL-QUERY`；Step 7 aggregate exit 已通过，Batch 6 completed。Batch 7
首次 compatibility/regression run 已封存并保持 superseded；replacement replay 与
ordered post-gates 已完成。必须保留
命令、XML report、日志/产物路径和数据库/并发非敏感元数据。

## Current Execution Checkpoint

| Evidence lane | Final run | Result |
|---|---|---|
| Batch 5 atomic refresh/source/Runtime/MCP | `20260713T200646Z-2120785` | 90/90 green in 19 owning reports：model 32、fsscript 7、Runtime 34、MCP 17 |
| post-Batch5 Batch 4 replay | `20260713T201031Z-2124453` | 140/140 green |
| post-Batch5 Batch 3 replay | `20260713T201525Z-2129344` | 166/166 green |
| 9.3.4-A entry gate replay | `20260713T201941Z-2133081` | 5 positive green + 4/4 expected-negative fail closed |
| post-Batch5 Batch 2 replay | `20260713T202421Z-2138021` | 25 product + 7 compatibility green |
| Batch 6 entry remaining-red baseline | `20260713T202645Z-2141955` | historical：only `CATALOG-AUTHORITY`, 1/1 expected-red |
| Batch 6 Catalog Authority | `20260714T021057Z-2445113` | 53/53 green in 7 owning reports；direct 17 + supporting consumers 36；failures/errors/skipped=`0/0/0` |
| post-Catalog remaining-red replay | `20260714T021251Z-2449309` | 0 cases / 0 tests / 0 sources |
| Batch 6 Step 2 strong key | focused logs | model 4 + addon 68 = 72/72 green；complete L1/L2 identity、incomplete no-key、generation rotation、SQLite datasource isolation |
| Batch 6 Step 3 two contexts | focused XML/log | 1/1 green；two real Spring contexts/different instances；same complete identity same L1/L2 keys；catalog/binding rotation |
| Batch 6 Step 4 cross-JVM | `20260714T025444Z-2628329` | passed：1/0/0/0；production auto-config template/provider；snapshot-derived exact resolution；2 child JVMs/27 probes/same binding；old physical-key read controls=2；current identity misses=2；post-write hits=2；Redis `SCAN`=`DBSIZE`=4；two-layer hash checks；independent second review no blocker |
| Batch 6 Step 5 Pivot/CACHE-GEN | `20260714T032135Z-2678670` | passed：direct 38/4 + supporting 61/3 = 99/7；failures/errors/skipped=`0/0/0`；full SHA-256 lifecycle identity、provider/manual additive only、all refusal/provider-exception gates no lookup/store；independent second review no blocker |
| Batch 6 Step 6 REAL-QUERY | `20260714T041047Z-2755326` | passed：11/11 in 6 Failsafe reports；model lifecycle 4 + SQLite/MySQL 5.7/PostgreSQL 15 parity 3 + Caffeine/Redis 4；native rows/columns/order/values parity；independent post-run review no blocker |
| Batch 6 Steps 2/3 fresh replay | `20260714T044313Z-2824177` | passed Step 7 child authority：75/75 in 6 reports；current Step 2=74，Step 3=1；three lanes each exactly one `BUILD SUCCESS`；independent post-run review no blocker |
| Batch 6 aggregate exit | `20260714T045604Z-2854237` | passed：11 children；676 criteria / 677 XML testcases / 99 reports；F/E/S=0；4/4 expected-negative；remaining red=0；source/child/inner/outer/dirty/container checks passed；two independent reviews no blocker |

Batch 5 已将 `REFRESH-ATOMIC`、`REFRESH-FAIL`、`REFRESH-SCOPE`、
`EVENT-CONVERGENCE`、`SOURCE-COMMIT`、`VALIDATE-ISOLATION` 提升为
passed；Batch 6 已将 `CATALOG-AUTHORITY`、`CACHE-CROSS-JVM`、
`CACHE-GEN` 与 `REAL-QUERY` 全部提升为 passed；Step 7 aggregate exit 也已
passed。Batch 6 completed；Batch 7 replacement authority 与 ordered post-gates
均 completed，9.3.3 已 version signoff。

Catalog Authority evidence 证明 coordinator 是唯一 recovery authority，
namespace view 在同一 identity 下完整携带 names、aliases、models、exact
resolutions/binding provenance，native/MCP metadata 使用 bounded seqlock，
blocked/partial/churn fail closed；resolver 与 MCP catalog tracked constructors
共用一个 `SemanticModelCatalogService` authority bean 且无 legacy
loader/bundle interaction，model service store/coordinator 注入由 model
test/source audit 证明；consumer 不存在 independent names cache/watcher。权威
hashes 见 criterion evidence。

旧 run `20260714T014919Z-2373851` 为 diagnostic 且
`superseded-by-review`；即使其内部 summary=`passed`，也不计入任何通过结论
或测试总数。

Cache source/Steps 2/3 evidence 证明 `QueryCacheKeyBuilder` 不使用 instance
UUID/object address fallback；boot UUID 只在 `CatalogSnapshotStore` 中作为预期
process-cold catalog/source epoch。Step 4 authority 进一步证明 production
auto-config template/provider、真实 non-empty published snapshot 派生 exact
resolution，并由 runner 独立推导 child/report/Redis state。old physical-key
read controls=2 仅为物理 Redis 可读控制，不代表 lifecycle resolution/provider
命中。
XML/summary/outer manifest/source manifest SHA-256 分别为
`93e92cf913098bbd110da8b0a98fd2a16414b2fe615e7fc98edb9277dffd51f9`、
`ff76b8a81360606e97a316797f40f7d145951c56e7e803911be030d4a3e5358e`、
`a54b7a4b18b750a5afb99fc3ee47b91af4759d0420f60cf9fb4c28edb1950ed3`、
`f7c53054262502c7a348ea93e0ed9e12d461e2c18c4dd2105563b03ab300eea6`；
container cleaned，no credentials。

`20260714T023304Z-2522929` failed diagnostic；
`20260714T023442Z-2527116` 因 test-only serializer、empty snapshot/manual
resolution、runner self-report window 为 diagnostic/superseded；
`20260714T025247Z-2623994` product test green 但 runner model-segment parser
failed，亦为 diagnostic/superseded。三者均不计入 pass。

Step 5 Pivot authority 证明 complete catalog/source/canonical model/exact sorted
bindings 使用 full SHA-256 identity；provider/manual token additive-only；
missing/incomplete/JDBC-empty/conflict/provider exception 均 no lookup/no store。
provider exception blocker 已由 pipeline 第 8 个 direct case 收口。Step 5
summary/source/outer SHA-256 分别为
`45f9cc2ab69f6e63434606be645b607a430869ca1a6ff073ce16a709163c40d7`、
`50f3dc8fd40a9bde4d88f5487318730f54a8be22eeeb011ff0cbfb2fe207849b`、
`e7014adaaa1932d62993d2a051830e9bb3f3b2fb4c7a982f356b32fc67d04ad7`。

Step 6 REAL-QUERY authority 以 production QueryFacade/Semantic/Pivot 和 Spring
cache provider 路径证明 atomic refresh、namespace isolation、binding rebind、
Pivot cache rotation，以及 SQLite/MySQL 5.7/PostgreSQL 15 的 native rows/
columns/order/values parity。summary/source/inner/outer SHA-256 分别为
`5602a3a75b6fb99d938ace19f7ca6b6528d0a9876fc2f799db14d24df5604f9e`、
`95aa4d9d5fbe31465724ad87eb704b531e04a4a2be0393f1a0b8afc2baf61bb0`、
`071987f7df30a3d08a6a194bf77f39a0861853ce36470db19090a36fd351f1aa`、
`6464baeda9e899736f3031dea27f1ceafc4a0b72e3863dbcafcbce499ce17cfe`。

Step 7 cache-identity fresh replay 证明 current-source Step 2=74、Step 3=1；
summary/source/inner/outer SHA-256 分别为
`b8cff91a339df48b8ba79549af40948bae1a0bad1618a8f809266bd0898e2e9c`、
`0563a797fcdb4a9c33e8e176851b50b466012734340f2eb077b5a28763dfe053`、
`143bbccea012ff2a8ed4d183bb468dfbde7770953bdac021d959b1fdc16855ea`、
`ffc7a11de0fcd2e9b3ec843ccdc2471c5636bd6df6ddfd72b63887991825e296`。
Earlier run `20260714T044020Z-2817047` is diagnostic/superseded and excluded。

Step 7 aggregate summary/children/source/inner/sealed/outer SHA-256 分别为
`10d72c2daa76361861a3796d0bc5699e1f09fecd53f82fa4b185b05ec8c803b1`、
`b2ddfc24c44c762464fb4df54a5532b58a51f433fedbbf030239b871dc4e052a`、
`e22630c9b1ccbc102861e1396bc9c55a68bab6d5557ada6a5c1fa86f0fdfdea1`、
`3318f2e69e3d41de57886a56cd70f23e0dbf76d53186f8a32f7eb5820366223f`、
`81a6456852fdb41f14eea316a60f4eea2f27b632dfc710c637a38f7435edab6d`、
`fc73ce9e1e19ce14c2733706daf720bf3af6a7fa35688fad54c43285ea2a43a9`。

Step 7 aggregate 已按 676 criterion tests / 677 asserted XML testcases / 99
reports / 4 expected negatives / 0 remaining red 通过，并由两路独立二审
确认无 blocker。Batch 7 replacement 与全部后置门亦已完成，9.3.3 已正式
version signoff。

## Evidence Package

最终至少包含：

- baseline commit/dirty manifest/patch checksum；
- 9.3.4-A no-test 与 wrong-DB 预期失败证据；
- repair-before/after contract 结果；
- NamespaceScope 嵌套/异常/线程池证据；
- snapshot/generation/single-flight 状态转移与 100-call build count；
- atomic success/failed/stale/target refresh observations；
- observable lazy materialization 切代与 plain read 不切代证据；
- datasource save/remove/disable/rebind generation、admission boundary、lease drain 与 pinned physical identity；
- committed SourceRevision、unknown-scope admission block 和 stale candidate 丢弃；
- partial QM 整体失败、Runtime validate live isolation、model/MCP catalog 单 authority 证据；
- Caffeine/Redis/Pivot generation key 与独立 JVM/重启 cold-cache 证据；
- Runtime additive DTO/旧 consumer compatibility 与稳定 sanitized error 证据；
- SQLite + MySQL 5.7 + PostgreSQL 15 的真实 SQL/返回数据；
- SQL Server、9.3.1 isolation、9.3.2 auto-config/Launcher 回归；
- compile/package 结果（明确不是测试证据）。

## Gate Order

```text
implementation check-in + progress
  -> Implementation Self-Check
  -> foggy-implementation-quality-gate
  -> foggy-test-coverage-audit
  -> foggy-acceptance-signoff (version scope)
  -> README / requirement / progress / roadmap status sync
```

任一上游 gate 未通过，不创建下游绿色结论。

## Acceptance Rules

以下任一缺失都阻断签收，不能降级为 accepted-with-risks：

- CatalogSnapshot 同代/不可变证据；
- catalog 与 datasource binding generation；
- observable catalog publication 切代；
- same-key single-flight 和 failure retry；
- atomic success、binding-valid source/model failed refresh keeps old、target namespace isolation；
- binding remove/disable/rebind 后的新查询 admission fail-closed 与在途 lease 策略；
- committed SourceRevision、unknown-scope admission block；
- partial QM、Runtime validate isolation、model/MCP single catalog authority；
- NamespaceScope nested/exception/thread-pool；
- generation-aware cache fail-closed；
- 独立 JVM/进程重启共享 Redis 不碰撞旧 epoch key；
- Runtime additive DTO 对旧 consumer 兼容且错误信息已脱敏；
- 至少 SQLite + required external DB 的真实查询；
- 9.3.4-A 实际运行/no-test/DB preflight 证据。

只有文档措辞、额外 backend 直测粒度、非 required 数据库扩展等 minor gap 可考虑 `accepted-with-risks`，且必须写 owner/follow-up version。

## Final Signoff Contract

最终记录必须使用 version template 语义：

```yaml
acceptance_scope: version
version: 9.3.3
target: model-lifecycle-concurrency
status: signed-off | rejected | blocked
decision: accepted | accepted-with-risks | rejected | blocked
signed_off_by: <release-owner-or-role>
signed_off_at: YYYY-MM-DD
blocking_items: []
follow_up_required: yes | no
evidence_count: <actual>
```

正文固定包含 Background、Acceptance Basis、Checklist、Module Summary、Evidence、Risks/Open Items、Final Decision、Signoff Marker。

## Downstream Handoff

- 9.3.4 接收：已稳定的并发 suite、Failsafe 最小分层和 evidence schema，扩展到完整 CI/DB/coverage/release gate。
- 9.3.5 接收：CatalogIdentity、NamespaceScope 和 query pinned snapshot contract，用于统一 QueryFacade/执行阶段。
- 9.4.0 接收：经验证但仍位于现有 model authority 内的 lifecycle/binding ports，再抽取为 SPI v2/module API。
