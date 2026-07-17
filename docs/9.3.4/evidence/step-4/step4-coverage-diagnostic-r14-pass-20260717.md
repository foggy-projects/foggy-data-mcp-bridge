---
evidence_type: successful-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r14
tested_commit: 322bb346cca19998a90d6d990505ef033f3a496a
status: diagnostic-observed
decision: threshold-candidate-verified-formal-pending
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r14 pass and threshold candidate

## Decision

`step4-coverage-20260717-diagnostic-r14` 从 clean/pushed
`HEAD == origin/main == 322bb346cca19998a90d6d990505ef033f3a496a` 启动并完整退出：
`exit_code=0`、`last_phase=completed`、`status=diagnostic-observed`。同一 authority window
完成全部 required lanes、source before/after seal、report inventory、23 exec / 48 sessions、
两次 deterministic aggregate report replay、coverage observation、sensitive scan 与 cleanup。

r14 是 formal-r1 shutdown-hook race remediation 后的唯一 fresh diagnostic。12/12 critical
class 的所有适用指标均达到固定 line=`4/5`、branch=`7/10` candidate floor，
`below_floor_class_count=0`；唯一非适用指标仍是机器契约预声明的
`NamespaceScope / foggy-dataset-model / branch`，count=`1`。工具随后生成 immutable threshold
candidate，并由 public verification 复算通过，SHA-256=
`9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`。

该结论只放行 independent threshold review 与 direct-single-parent Cfreeze；它不是 formal、
coverage audit、acceptance 或 Step 4 exit。

## Sealed result

- run window：`2026-07-17T02:58:13Z` 至 `2026-07-17T04:03:23Z`；
- source-before=source-after SHA-256=
  `04e485088ae233c388cd0fd0bd9190d347e3b2a4d65faac238a1678147ec9d81`；
- required reports：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,956 execution class identities`；
- fresh class universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be`；
- aggregate line=`54,622/76,830`（71.0946244956%）；
- aggregate branch=`26,106/44,870`（58.1814129708%）；
- critical classes=`12`，below floor=`0`，structural N/A metric=`1`；
- sensitive scan：5 patterns，extensions=`log,env,json,tsv,xml`，status=`passed`；
- cleanup：container/volume/network residue=`0/0/0`。

官方独立复算结果：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260717-diagnostic-r14
observation=f3cab617a3e43ed5d0948687466c3f37de26bafc8fe9a65feb8f48a07dee412f

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260717-diagnostic-r14
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `ed222ffbf1c73fe66fb3d9c5c8bf4a5acfbd95b8817bd59a2093888f4a4d4cc5` |
| `summary.env` | `16deb94b7afeeb3ef4ba63dd00502f93c5b5e4f548349e020216634e5ba6497c` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `class-universe.json` | `f5d71196781ee2338d00458c9db325eb35a6d07c1f36982bc6bb999c88466e01` |
| `report-inventory.json` | `d24873274a65c43263f43829964ff05add444e860e9e3f91769462695e004840` |
| `exec-manifest.json` | `563337b7edb96e0d8013b1d2dd9b53f84e69262184061cf65dadb4a03c331890` |
| `report/jacoco-aggregate.exec` | `fc1ab799ddfc008bf9cb35a8fb195d3f5b65945a48b91e671b0768410455e96d` |
| `report/aggregate-provenance.json` | `3485900b86df0efbc84ecd05a6c9b125af9c049c9a17b7b78b8fc8e3b9b4192f` |
| `report/report-provenance.json` | `92d72f7d5c32f091d8499be06aec9e2eac79b883f58a7c6f0f3a2e4b506f4717` |
| `report/jacoco-aggregate/jacoco.xml` | `767cffafc248aea02ce23247281a8838c2c0cca0102a351d8b447af94651a5ed` |
| `coverage-observation.json` | `f3cab617a3e43ed5d0948687466c3f37de26bafc8fe9a65feb8f48a07dee412f` |
| `step4-coverage-diagnostic-r14-threshold-candidate-20260717.json` | `9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55` |

Candidate exact 绑定 r14 的 run-status、summary、observation、diagnostic contract、threshold
predecessor、exec manifest、aggregate exec/XML 与 workspace class tree；它保持
`status=review-required`，不得在 review 或 freeze 时回写或改变 bytes。

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
| `WatchServiceFileTracer` | `204/244` | `99/128` | passed / deterministic shutdown covered |
| `QueryFingerprintBuilder` | `159/179` | `65/84` | passed |
| `SecurityPolicyFingerprint` | `112/117` | `73/92` | passed |
| `CaffeineQueryCacheProvider` | `126/135` | `64/78` | passed |
| `RedisQueryCacheProvider` | `152/159` | `73/88` | passed |

## Stability comparison

- relative to r13，`WatchServiceFileTracer` line remains `204/244` and branch increases
  `98/128 -> 99/128`；formal-r1 missing `9 line / 3 branch` is no longer reproduced；
- r14 aggregate is r13 line `-2` / branch `-3` because non-critical
  `BaselineRatioCalculator` is `-2/-3` and `ResultShaper` is branch `-1`，while tracer is branch
  `+1`；the same two pivot classes were at r13 values in formal-r1；
- candidate therefore freezes the fresh r14 exact observation rather than preserving r13's higher
  incidental aggregate high-water mark；all production totals and all critical totals are unchanged。

## Cleanup and external restoration

Runner cleanup proves run-owned container/volume/network=`0/0/0`。退出后四个 repo demo DB
exact container 在 evidence window 外按原 ID 恢复，均为 `running/healthy`：

| Service | Port | Exact container ID |
|---|---:|---|
| MySQL 5.7 demo | 13306 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` |
| MySQL 8 demo | 13308 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` |
| PostgreSQL demo | 15432 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` |
| SQL Server demo | 11433 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` |

## Next gate

- [x] r14 sealed diagnostic、sensitive scan、cleanup 与 external restoration verified；
- [x] `below_floor_class_count=0`、structural N/A count=`1`；
- [x] threshold candidate generated and public verification passed；
- [x] independent exact projection review recorded；
- [x] reviewed threshold/contract/manifest written as one allowlisted Cfreeze working-tree delta；
- [ ] Cfreeze direct-child commit/push/clean identity and fresh formal；
- [ ] final implementation quality, coverage audit and acceptance after formal PASS only。

Current `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；formal and Step 5 remain pending。
