---
doc_role: implementation_evidence
doc_purpose: Record the reviewed and reproducible 9.3.4 Step 1 inventory contract freeze.
version: 9.3.4
step: 1
status: confirmed
decision: passed
created_at: 2026-07-14
updated_at: 2026-07-14
---

# 9.3.4 Step 1 Inventory Contract Freeze

## Decision

- candidate run: `step1-candidate-r8-20260714`
- candidate root:
  `target/v934-step1-inventory/runs/step1-candidate-r8-20260714`
- baseline commit: `a377937e8e6a6c03afce655396d7363f7db1d7d4`
- candidate result: `passed`
- evidence status: `confirmed`
- independent review: `PASS / PASS`，blocker=`0`
- confirmation decision: `passed`
- confirmed reviewer:
  `dual-independent-review:precommit_scope_audit+v934_step1_contract`
- reviewed_at: `2026-07-14T12:16:54.211702+00:00`

本 run 只执行 reactor test compile、JUnit Platform discovery-only 和静态清单校验；
未执行测试方法，也未连接或启动数据库、Redis 等外部 fixture。Step 1 confirmation
不表示任何 Step 2/3 测试 lane 已通过。

## Frozen Scope

| Inventory | Frozen count | Review result |
|---|---:|---|
| workspace source candidates | 532 | 530 active-reactor + 2 reviewed non-reactor |
| source kinds | 532 | 516 executable + 12 helper + 4 generator |
| discovery modules | 21 | exact active modules owning discovery sources |
| discovery rows | 820 | 804 actual report owners + 16 reviewed `none` rows |
| execution keys | 829 | unique length-framed keys |
| required execution keys | 828 | Step 2=`785`, Step 3=`43` |
| optional execution keys | 1 | Step 3 with reviewed reason |
| ordered classpath entries | 2395 | 2285 external m2 + 110 current reactor class trees |
| stale active-reactor m2 entries | 0 | active reactor GAVs normalized in place |
| predecessor raw report nodes | 519 | regenerated from sealed v933 XML |
| predecessor migration edges | 519 | exact one-to-one raw-node coverage |

Parameterized/test-template invocation cardinality is deliberately runtime-deferred；Step 1
只冻结 discovery report ownership 和后续 execution ownership，不能把 discovery
container 数冒充实际 testcase 数。

## Controlled Rename Plan

`rename-successor-plan.tsv` 冻结 Step 2 的唯一允许改名 delta：

| Dimension | Count |
|---|---:|
| source files | 33 |
| discovery reports | 62 |
| execution keys | 74 |
| affected predecessor edges | 50 |
| Step 2 execution keys | 47 |
| Step 3 execution keys | 27 |
| required / optional | 73 / 1 |
| runner | 74 Failsafe |

- rename plan SHA-256:
  `acba7a9dc22c9c0fdbaa0438fe91018b61eee8a457a36f1918995f39aa74cfe2`
- 只允许 `IntegrationTest -> IT` 的已审阅路径/FQCN/report/execution-key 变换；runner、
  lane、variant、DB、infra、execution step、required/optional 语义不可漂移。
- Step 2 不得覆盖本目录；必须生成独立 `scripts/v934/successor/step2/`，以确认后的
  Step 1 manifest SHA 和上述 rename plan SHA 作为 parent，并再次独立复核、确认。

## Reproducibility and Integrity

- protected source before/after SHA-256:
  `2c73b8951dbeda43bde2b0b2aa0ef63cb0fb66e049a5c5bf7d8351e2eae8e9e2`
- wrapper source SHA-256:
  `941fe622b9b73322eafd08a7f30a98499a8bffc37237e763be2471f08d7c63dd`
- inventory tool source SHA-256:
  `49a044c84356723875c136b243a8613692b5c7a13d6eff1dd36bafc25f0eb752`
- discovery helper source SHA-256:
  `ec58a90d4f02ac1b814a26ac0d8e24a1417b5f33910b6813c585f34330091e41`
- discovery helper class-tree SHA-256:
  `a9540895b7a8969cf65eae2bde65fc570e1ccf03d3c6a61bf2b2d593f8539561`
- Java/Javac: `17.0.19`
- Maven: `3.8.7`（summary 保留 Maven 原始终端版本字段）
- candidate contract-freeze SHA-256:
  `1364f8e6fde9b9a2def443d8c286da6287d0efa35b334be230a8acb2cd31942b`
- candidate contract-manifest SHA-256:
  `8194023945451033fbd87fcb6e82bbc50b9957f92fda4d29523f8108d83a159c`
- candidate summary SHA-256:
  `1367de7121ca9cf57cc910087ade9721f39b035f4c5a23b821d8fa499985f37b`
- confirmed contract-freeze SHA-256:
  `ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876`
- confirmed contract-manifest SHA-256:
  `e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f`
- confirmed summary SHA-256:
  `579e9430bea6f873e7c4465cd1a6e45c49d348d84a89d5d648d25e3a5a4bbc50`

`discovery-classpath.tsv` 的 entry 顺序、文件/目录实时 hash、raw/normalized
cardinality 都由 validator 复算。属于 active reactor GAV 的本地仓同版本 JAR 必须
原位替换为本次 `target/classes`；r8 的 stale active-reactor m2 entry 为 0。

## Fail-closed Evidence

28/28 expected-negative probes 均以预期稳定错误码失败：

```text
orphan-source                 E_SOURCE_SET
missing-source-owner          E_SOURCE_OWNER
missing-source-reason         E_SOURCE_REASON
nonreactor-disposition        E_SOURCE_DISPOSITION
zero-execution-owner          E_EXEC_ZERO
duplicate-execution-key       E_EXEC_KEY_DUP
non-executable-owner          E_NONEXEC_EXEC
runner-overlap                E_RUNNER_OVERLAP
sqlite-lane-overlap           E_SQLITE_OVERLAP
missing-report-owner          E_REPORT_MISSING
unexpected-report-owner       E_REPORT_UNEXPECTED
invalid-step                  E_EXEC_STEP
optional-metadata             E_OPTIONAL_METADATA
unknown-successor             E_MIGRATION_SUCCESSOR
migration-cardinality         E_MIGRATION_CARDINALITY
duplicate-migration-edge      E_MIGRATION_EDGE_DUP
unmapped-predecessor          E_MIGRATION_UNMAPPED
invalid-classpath-hash        E_CLASSPATH_HASH
classpath-module-gap          E_CLASSPATH_MODULE_SET
duplicate-maven-variant       E_MAVEN_VARIANT
orphan-discovery-row          E_DISCOVERY_ORPHAN
missing-discovery-row         E_DISCOVERY_MISSING
tampered-discovery-hash       E_DISCOVERY_HASH
tampered-rename-successor     E_RENAME_PLAN
tampered-successor-policy     E_FREEZE_SUCCESSOR
freeze-count-tamper           E_FREEZE_COUNTS
missing-hash-entry            E_HASH_SET
stale-manifest                E_STALE_HASH
```

## Excluded Diagnostic Runs

以下 run 全部不构成绿色 evidence，且不得与 r8 拼接：

- `step1-candidate-20260714`：run-root marker bootstrap 失败。
- `step1-candidate-r2-20260714`：未审阅 zero-report abstract/suite suffix，停止。
- `step1-candidate-r3-20260714`：旧 runner 曾通过，但 source summary scope 与 freeze
  不一致，且每模块 classpath manifest 因末尾换行缺失丢最后一项；现 validator 会以
  `E_CLASSPATH_CARDINALITY raw=225 manifest=224` 拒绝。
- r4：修复 scope/hash 后中断。
- r5：发现 active reactor 同版本 stale m2 dependency 后中断。
- r6：confirm atomicity/toolchain provenance 加固后早期中断。
- r7：执行 2/21 modules 后发现跨 Step rename plan 缺口，停止并补契约。

## Independent Review

| Reviewer | Scope | Decision | Findings |
|---|---|---|---|
| `precommit_scope_audit` | machine inventory, protected scope, reproducibility | PASS | validator PASS；16/16 manifest；3025 protected files、2395 classpath entries、519 predecessor nodes/edges、helper bytecode 均独立复算一致；无 blocker |
| `v934_step1_contract` | contract/schema, Step 1 exit, Step 2 boundary | PASS | source/discovery/execution/classpath/rename/predecessor 与 DB/package/Maven/critical contract 独立复算一致；无 blocker、无非阻断 finding |

两路 reviewer 均给出无阻断结论后执行了 confirmation；reviewer、decision、
reviewed_at、evidence path 已原子写入 `contract-freeze.json`、`SHA256SUMS` 和 exact
candidate summary。confirmation 后重新执行 validator、confirmed-summary validator
与 16/16 `sha256sum -c`，全部通过。

## Entry Decision

Step 1 exit=`passed`。最终 freeze/manifest/summary digest 已独立复算，允许 Step 2
进入受控 rename 与 Surefire/Failsafe 分层。Step 2 必须生成并确认独立 successor；
不得改写或以 post-rename source 重新解释本 Step 1 immutable baseline。
