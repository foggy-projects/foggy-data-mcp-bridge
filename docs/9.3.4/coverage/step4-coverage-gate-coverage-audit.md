---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.3.4
target: step4-coverage-gate
status: reviewed
conclusion: ready-for-acceptance
reviewed_by: Codex + independent requirement/workitem/artifact audits
reviewed_at: 2026-07-18
follow_up_required: yes
---

# Step 4 Coverage Gate Test Evidence Coverage Audit

## Background

本审计在 fresh formal-r4 与 post-formal implementation quality 通过后，逐项检查 Step 4
requirement、acceptance item、25 个 Step 4 workitem、正负例、生命周期、清理和 public artifact
的测试证据映射。这里审的是“测试证据覆盖是否足以进入 feature acceptance”；不签收 Step 5–7、
9.3.4 version 或 9.3.5。

审计中发现 `PivotSqlParityIT` legacy fallback 没进入 formal 五库 variant。该 Major 候选缺口没有
被降级：先在 formal-r4 同一 tested HEAD 的最终源码上补跑 current-source companion，取得
`1/F0E0S0` 和可区分分支的动态 oracle，再由两路独立复核确认与 formal-r4 V934 分支互补。修复后
Step 4 workitem 子矩阵为 `covered=25 / partial=0 / not-covered=0`。

## Audit Basis

- tested Cfreeze：`f97483a0b87a82734d21888e7b5bea74b0c5fe55`；
- formal run：`step4-coverage-20260717-formal-r4`；
- requirement/contract/test plan：`docs/9.3.4/requirement/`、`contract/`、`test/`；
- formal exit：
  `docs/9.3.4/evidence/step-4/step4-coverage-exit-20260717.md`；
- post-formal quality：
  `docs/9.3.4/quality/step4-coverage-gate-final-implementation-quality.md`；
- Pivot legacy companion：
  `docs/9.3.4/evidence/step-4/step4-pivot-legacy-companion-evidence-20260718.md`；
- 25 个 Step 4 workitem 与相应 historical RED/fail-closed records；
- formal raw manifests、exact report/exec set、aggregate/critical gate、negative、source、child
  lifecycle、cleanup/sensitive receipts；
- 三路初审与 Pivot 缺口两路补证复核。

## Coverage Matrix

Evidence location key：

- `E1`：Step 4 formal exit record；
- `E2`：`target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r4/` 下
  `run-status.env`、`report-inventory.json`、`exec-manifest.json`、`coverage-observation.json`、
  `coverage-gate.json`、`child-lifecycle.json`、`final-manifest.json`、negative/cleanup/sensitive
  receipts；
- `E3`：同一 run ID 的 Unit、Integration、Step 3 database/external/required 与 Addon raw
  reports/manifests；
- `E4`：formal exit 中持久化的 artifact SHA、source/Cfreeze/provenance/public replay；
- `E5`：`docs/9.3.4/evidence/step-4/*fail-closed*.md` 与 diagnostic pass/review records；
- `E6`：`docs/9.3.4/workitems/BLOCKER-step4-*.md`、`BUG-step4-*.md`；
- `E7`：Pivot legacy current-source companion；
- `E8`：post-formal implementation quality；
- `E9`：Steps 1–3 exit/coverage/acceptance evidence 与 successor mapping。

`E2/E3` 是 ignored run-local raw artifacts；其 identity、hash、统计和 replay boundary 由 `E1/E4`
持久化。表中的 Manual 仅表示独立治理复核，不是 UI/体验手测；本后端 feature 的 Playwright 与
manual experience 均为 `N/A`。

### Requirement / Acceptance Mapping

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence Path | Coverage |
|---|---|---|---|---|---|---|---|---|
| INVENTORY | critical | yes | yes | formal | N/A | review | `E1/E2/E9`：required exact union、orphan/overlap/duplicate=0 | covered |
| RUNNER-SPLIT | critical | Surefire | Failsafe | child authority | N/A | N/A | `E1/E2/E3`：Unit/Integration 独立 child 与 exact report set | covered |
| NO-ZERO | critical | yes | yes | negatives | N/A | N/A | `E1/E2`：required nonzero；report-inventory negatives `30/30` | covered |
| DB-IDENTITY | critical | N/A | five DB | state E2E | N/A | review | `E1/E3`：product/version/coordinate/catalog/schema/sentinel；state `18/18` | covered |
| DB-PARITY | critical | N/A | five DB | QueryFacade/native | N/A | N/A | `E1/E3`：database `29/370/F0E0S0` | covered |
| SKIP-ZERO | critical | yes | yes | verifier | N/A | N/A | `E1/E2/E3`：required、DB、external、Addon 均 `S0` | covered |
| COVERAGE-AGG | critical | agent | agent | aggregate | N/A | review | `E1/E2`：`23 exec/48 sessions`；line `54624/76830`、branch `26111/44870` | covered |
| COVERAGE-CRITICAL | critical | yes | yes | gate | N/A | review | `E1/E2`：12 类、23 applicable、below=0；唯一 N/A=`NamespaceScope.branch` | covered |
| AUTHORITY（Step 4 slice） | critical | yes | yes | fresh formal | N/A | review | `E1/E2/E4`：direct-child Cfreeze、source seal、single run、public final replay | covered |
| EVIDENCE-IMMUTABLE（Step 4 slice） | critical | N/A | N/A | hash/replay | N/A | review | `E1/E2/E4`：candidate/final/summary/report/exec/coverage/lifecycle hash chain | covered |
| CI-REQUIRED | critical | N/A | N/A | Step 6 | N/A | N/A | out of scope：Step 6；本 feature 不冒充 remote CI | not-covered |
| RELEASE-SAME-ARTIFACT | critical | N/A | N/A | Step 7 | N/A | N/A | out of scope：Steps 5–7；本 feature 不冒充 JAR/image authority | not-covered |
| REGRESSION-931-933（Step 4 slice） | critical | yes | yes | successor | N/A | review | `E1/E3/E9`：current-source successor lanes 全部进入 fresh union | covered |
| POST-GATES（feature slice） | major | N/A | N/A | ordered gate | N/A | review | `E8`→本 audit；feature acceptance 是下一步 | covered |

`CI-REQUIRED` 与 `RELEASE-SAME-ARTIFACT` 的 `not-covered` 是版本计划中明确的 Step 6/7 工作，
不计为 Step 4 feature gap；`AUTHORITY/EVIDENCE-IMMUTABLE` 仅结论到 formal artifact，portable
archive/single final runner 继续由 Step 5 承接。

### Step 4 Workitem Mapping

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence Path | Coverage |
|---|---|---|---|---|---|---|---|---|
| R6 MySQL57 port occupation | critical | N/A | DB state | fail-closed | N/A | restore review | `E5/E6/E2`：r6 RED；formal fixture/state/cleanup | covered |
| Bean2Map timing oracle | critical | yes | N/A | formal union | N/A | N/A | `E5/E6/E3`：r15 RED；deterministic test + formal Unit | covered |
| child run-log tee residue race | major | N/A | runner | lifecycle | N/A | N/A | `E5/E6/E2`：r3 RED；managed logger、child lifecycle、PG residue=0 | covered |
| database successor manifest | major | N/A | DB/required | successor | N/A | review | `E5/E6/E3`：r5 RED；DB `29/370`、state `18/18`、required `45/446` | covered |
| evidence JSON numeric alias | critical | tool negative | N/A | typed replay | N/A | N/A | `E6/E2`：generic XML typed negatives `118/118` | covered |
| exec class-ID scope drift | critical | N/A | exec verifier | aggregate | N/A | review | `E5/E6/E2`：r9 RED；exec negatives `17/17`、23-exec exact union | covered |
| Git environment override | critical | N/A | N/A | provenance negative | N/A | security review | `E6/E2/E4`：Git/source hostile negatives、frozen replay、source seal | covered |
| legacy JaCoCo argLine | major | N/A | build profile | exec authority | N/A | review | `E6/E2`：three-state regression、effective POM `4/4`、23 exec | covered |
| lifecycle semantic validator | critical | tool negative | runner | coded lifecycle | N/A | N/A | `E6/E2`：comment/dead-branch/trap mutations + actual child lifecycle | covered |
| ListPreset coverage order | critical | yes | N/A | formal branch | N/A | N/A | `E5/E6/E3`：formal-r2 RED；deterministic missing-ID oracle | covered |
| outer runner source-seal binding | critical | N/A | N/A | source/provenance | N/A | review | `E5/E6/E2/E4`：r11 RED；early binding、source exact、formalization delta | covered |
| Pivot legacy hybrid fixture | major | N/A | focused + five DB | companion/formal | N/A | independent review | `E5/E6/E7/E3`：current-source legacy `1/F0E0S0` + five V934 variants | covered |
| Pivot NULL-axis oracle | critical | N/A | five DB | aggregate | N/A | review | `E5/E6/E3`：r18 gap；r19/formal exact high-water | covered |
| PreAgg L2 hybrid fixture | major | N/A | yes | formal union | N/A | N/A | `E5/E6/E3`：r2 RED；focused/combined + formal Integration | covered |
| PreAgg Unit order isolation | major | yes | N/A | formal Unit | N/A | N/A | `E6/E3`：snapshot-only fixture、focused/aggregate Unit | covered |
| PreAgg validation wrong table | major | yes | N/A | formal Unit | N/A | N/A | `E5/E6/E3`：r1 RED；exact table corruption/restore oracle | covered |
| QueryModel join-graph DCL race | critical | yes | N/A | aggregate branch | N/A | N/A | `E5/E6/E3`：formal-r3 RED；deterministic concurrency oracle | covered |
| sensitive-scan false positive | critical | tool negative | N/A | security scan | N/A | review | `E5/E6/E2`：r10 RED；danger/safe fixtures、formal sensitive PASS | covered |
| source/context cross-link | critical | tool negative | N/A | provenance replay | N/A | review | `E6/E2/E4`：source/context/raw-exec cross-link negatives + final binding | covered |
| source inventory fileMode | major | tool negative | N/A | source seal | N/A | review | `E5/E6/E2`：r4 RED；permission/index/NSS/FIFO mutations、source exact | covered |
| threshold applicability/N/A | critical | yes | yes | coverage gate | N/A | independent review | `E5/E6/E2`：r12 gap；23 positive metrics + exact structural N/A | covered |
| Unit hidden MySQL dependency | critical | full Unit | fixture integration | lifecycle | N/A | review | `E5/E6/E3`：r7 RED；`681+55/4941`、fixture `36/36`、lifecycle `5/5` | covered |
| Unit lifecycle static drift | critical | N/A | runner | coded lifecycle | N/A | N/A | `E5/E6/E2`：r8 RED；shape/semantic/source-seal negatives + actual completion | covered |
| final mysqld handoff race | critical | fixture | DB lifecycle | readiness | N/A | runtime review | `E5/E6/E3`：r17 RED；final PID1/health receipt、fixture/cleanup PASS | covered |
| WatchService shutdown race | critical | yes | N/A | critical gate | N/A | N/A | `E5/E6/E2/E3`：formal-r1 RED；stable tracer oracle + exact formal row | covered |

## Evidence Summary

- formal terminal=`formal-passed / completed / exit 0`；public final artifact=`VALID`；final
  manifest SHA-256=`b6f695ccd641649de23b83c104cd944dc36ae8fc99f662f3bfeedcf9841e6416`。
- required=`773 positive + 59 structural / 5707 testcase / F0E0S0`；Unit=
  `681+55/4941`，Integration=`47+4/320`，Step 3=`45/446`；Addon=`2/6`（union 外）。
- exec=`23`、sessions=`48`、execution class identities=`16,953`；production universe=
  `24 modules / 2,098 classes`。
- aggregate line=`54,624/76,830`、branch=`26,111/44,870`，exact 达 confirmed minimum；
  critical=`12/23/below0`，唯一 N/A=`NamespaceScope.branch`；model gate PASS。
- contract=`27/27`、effective reporter POM=`4/4`、toolchain=`5/5`、generic XML=`118/118`、
  successor=`12/12`、report inventory=`30/30`、exec=`17/17`、coverage XML=`9/9`。
- Unit fixture/lifecycle=`36/36 + 5/5`；DB report/state=`14/14 + 18/18`；external
  report/sensitive/Redis state=`12/12 + 24/24 + 4/4`；Step 3 required=`17/17`。
- three child=`passed/reaped/PG residue 0`；runner container/volume/network residue=`0/0/0`；
  sensitive PASS；source before=after=`96e2c871…c3a3`。
- final artifact 的 23 个 path/size/SHA binding 经独立复算全部一致。
- Pivot legacy companion=`1/F0E0S0`，XML SHA-256=`802933e6…0dd`，source SHA-256=
  `18ebeedf…d325`；与 formal-r4 五个 V934 variant 共同覆盖两个分支，不改变正式统计。

## Gaps

Step 4 feature evidence gap：Critical/Major/Minor=`0/0/0`。

下列是已分派的后续边界，不是 Step 4 coverage gap：

1. post-restore live inventory validator 与 durable artifact replay 的入口差异，质量级别 Low，
   owner=Step 5 single-authority/rehearsal；不得削弱 live fail-closed。
2. Pivot companion raw XML 与 formal raw root 仍是 local `target`；command/HEAD/source/XML SHA
   已持久化，portable raw archive 由 Step 5 生成。
3. `DEBT-unit-mysql57-fixture-classification-migration` 保持
   `open / accepted-for-9.3.4-only`，必须在 9.3.5 version acceptance 前关闭。
4. remote CI/branch protection、same-JAR asset/image 与 clean version authority 分属 Steps 6/7。

UI/browser/Playwright/manual experience=`N/A`；本 feature 没有前端或人工体验交付。

## Recommended Next Skills

- 使用 `foggy-acceptance-signoff` 对 Step 4 feature 做正式 `accepted/rejected/blocked` 决策；
  acceptance 必须明确 companion、Low/debt boundary、Failed Items 与 Experience=`N/A`。
- 只有 feature acceptance=`accepted` 后，才能关闭剩余 16 个 acceptance-pending workitem、
  确认 9 个既有 implementation closure 的后置门，并把 Step 5 标为 `ready / not-started`；
  不得创建 9.3.4 `version-signoff.md`。

## Conclusion

`ready-for-acceptance`。Step 4 scope 的 requirement、25 个 workitem、formal positive/negative、
aggregate/critical/model gate、source/provenance、lifecycle、cleanup、安全扫描和 public final artifact
均有可复核证据；审计中发现的 Pivot legacy Major 候选缺口已由 same-HEAD current-source companion
补齐并经两路独立复核，最终 critical/major gap=`0/0`。本结论只开放 Step 4 feature acceptance；
9.3.4 仍 `in-progress`，Step 5 与 9.3.5 尚未开放。
