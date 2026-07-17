---
evidence_type: successful-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r16
tested_commit: f863c672029d5d1e5a4903df74cf6cba22a04a85
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-pending
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r16 pass and threshold candidate

## Decision

`step4-coverage-20260717-diagnostic-r16` 从 clean/pushed
`HEAD == origin/main == f863c672029d5d1e5a4903df74cf6cba22a04a85` 启动并完整退出：
`exit_code=0`、`last_phase=completed`、`status=diagnostic-observed`。同一 authority window
完成全部 required lanes、source before/after seal、report inventory、23 exec / 48 sessions、
两次 deterministic aggregate report replay、coverage observation、model external gate、
sensitive scan 与 cleanup。

r16 是 Bean2Map cache timing oracle 改为多实例 correctness oracle 后的唯一 fresh
all-lane diagnostic。12/12 critical class 的 23 个适用指标均达到固定 line=`4/5`、
branch=`7/10` candidate floor，`below_floor_class_count=0`；唯一非适用指标仍是机器契约
预声明的 `NamespaceScope / foggy-dataset-model / branch`，count=`1`。工具从 sealed r16
生成 immutable threshold candidate，并由 public verification 复算通过，SHA-256=
`2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919`。

该结论只放行 independent threshold review 与 direct-single-parent Cfreeze；它不是 formal、
coverage audit、acceptance 或 Step 4 exit。

## Sealed result

- run window：`2026-07-17T07:08:31Z` 至 `2026-07-17T08:13:35Z`；
- source-before=source-after SHA-256=
  `025b55582f2be6c6ec6b546fbd609251cabc371c469add1261033ef22639a43e`，两个 TSV
  byte-identical；
- required reports：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,948 unique execution class identities`；
- fresh class universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be`；
- aggregate line=`54,624/76,830`（71.0972276455%）；
- aggregate branch=`26,111/44,870`（58.1925562737%）；
- critical classes=`12`，below floor=`0`，structural N/A metric=`1`；
- model external gate=`passed`；
- sensitive scan：5 patterns，extensions=`log,env,json,tsv,xml`，status=`passed`；
- cleanup：container/volume/network residue=`0/0/0`。

官方独立复算结果：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260717-diagnostic-r16
observation=90deb174cb7f577e0573292762c970e274519d081e3641e4ae6fe12fb81c0be1

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260717-diagnostic-r16
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `72fb0183a73e9af3c9ddc3ead477c75029525267672212e13cc86623b4cf5857` |
| `summary.env` | `ea94dd52bbec905074f875aa39089c9f08a340a888984a1e97bc1e2f7df39475` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `class-universe.json` | `111aad2e99e05e65e95fac998b0a7e3bf9278af984fa9b657be497bf34205452` |
| `report-inventory.json` | `186385b581b5fde5cc61f8b3bba7f840de7c666cc8a0960a333ebad578335030` |
| `exec-manifest.json` | `45b5fe7be3b079cc37d4838099839b59b75338528cd16eece63221f5151264b1` |
| `report/jacoco-aggregate.exec` | `454893e0f7330f82cdba8a1acd3a8617c1fb83f8170014c783bee01309dc6961` |
| `report/aggregate-provenance.json` | `fff63a6516a73036f08d06394ba3f99a86ee4745461e8e7c02078bff06ff4a48` |
| `report/report-provenance.json` | `d47f0a06ec52a81bd830a0193aa192e1abb2c00833c3dcd939df2f4310e46b46` |
| `report/jacoco-aggregate/jacoco.xml` | `8c4ba1431bf187f78befa617ced9f1ecd857223528d12ceb0406e373f4a660d4` |
| `coverage-observation.json` | `90deb174cb7f577e0573292762c970e274519d081e3641e4ae6fe12fb81c0be1` |
| `step4-coverage-diagnostic-r16-threshold-candidate-20260717.json` | `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919` |

Candidate exact 绑定 r16 的 run-status、summary、observation、diagnostic contract、threshold
predecessor、exec manifest、aggregate exec/XML 与 workspace class tree；它保持
`status=review-required`，不得在 review 或 freeze 时改变 bytes。

## Critical floor result

| Critical class | Line observed | Branch observed | Outcome |
|---|---:|---:|---|
| `CatalogSnapshotStore` | `202/214` | `83/104` | passed |
| `ModelBuildSingleFlight` | `79/89` | `31/44` | passed |
| `CatalogRefreshCoordinator` | `253/282` | `95/134` | passed |
| `DatasourceCatalogConvergence` | `51/52` | `20/26` | passed |
| `NamespaceScope` | `5/5` | `0/0` | branch structural N/A |
| `CommittedSourceRevisionRegistry` | `53/55` | `19/24` | passed |
| `RuntimeNamedDataSourceResolver` | `145/166` | `66/94` | passed |
| `WatchServiceFileTracer` | `204/244` | `99/128` | passed |
| `QueryFingerprintBuilder` | `159/179` | `65/84` | passed |
| `SecurityPolicyFingerprint` | `112/117` | `73/92` | passed |
| `CaffeineQueryCacheProvider` | `126/135` | `64/78` | passed |
| `RedisQueryCacheProvider` | `152/159` | `73/88` | passed |

## Stability comparison

- 12 个 critical class 与 r14 exact counters 完全一致；Bean2Map correctness remediation 未改变
  生产字节码 denominator；
- 相对 r14，aggregate line=`+2`、branch=`+5`：
  `BaselineRatioCalculator=+2/+3`、`ResultShaper=0/+1`、`QueryModelSupport=0/+1`；
- `unique_execution_classes=16,948`，相对 r14 为 `-8`；变化分散在 Unit 与外部 lane 的
  class-loading footprint，不改变 `23/48` session completeness、2,098 class universe、
  production totals 或 critical counters；
- candidate 按 fresh r16 exact observation 冻结，不保留 r14 较低 aggregate，也不以 r15
  partial evidence 拼接补绿；fresh formal 继续 fail closed。

## Cleanup and external restoration

Runner cleanup 证明 run-owned container/volume/network=`0/0/0`。runner 完整退出后，外层编排
以 `rc=0` 恢复四个 repo demo DB exact container；以下状态属于 evidence window 外的独立观察，
不是 runner-owned cleanup evidence：

| Service | Port | Exact container ID | Observed state |
|---|---:|---|---|
| MySQL 5.7 demo | 13306 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` | running / healthy / listening |
| MySQL 8 demo | 13308 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` | running / healthy / listening |
| PostgreSQL demo | 15432 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` | running / healthy / listening |
| SQL Server demo | 11433 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` | running / healthy / listening |

## Next gate

- [x] r16 sealed diagnostic、sensitive scan、cleanup 与 external restoration verified；
- [x] `below_floor_class_count=0`、structural N/A count=`1`；
- [x] threshold candidate generated and public verification passed；
- [x] independent exact projection review，B/H/M/L=`0/0/0/1`；
- [ ] reviewed threshold/contract/manifest Cfreeze working-tree delta and quality gate；
- [ ] Cfreeze direct-child commit/push/clean identity and fresh formal；
- [ ] final implementation quality, coverage audit and acceptance after formal PASS only。

Current `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；formal and Step 5 remain pending。
