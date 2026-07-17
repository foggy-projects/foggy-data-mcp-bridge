---
evidence_type: successful-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r13
tested_commit: b76552e21479c75111f648a4aa678abe018cc3f9
status: diagnostic-observed
decision: threshold-candidate-verified-formal-pending
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r13 pass and threshold candidate

## Decision

`step4-coverage-20260717-diagnostic-r13` 从 clean/pushed
`HEAD == origin/main == b76552e21479c75111f648a4aa678abe018cc3f9` 启动并完整退出：
`exit_code=0`、`last_phase=completed`、`status=diagnostic-observed`。同一 authority window 内
完成全部 required lanes、source before/after seal、report inventory、23 exec / 48 sessions、
aggregate exact union、coverage observation、sensitive scan 与 cleanup。

r13 将 r12 的 9 个 critical coverage gap 全部关闭：12/12 critical class 的所有适用指标均达到固定
line=`4/5`、branch=`7/10` candidate floor，`below_floor_class_count=0`；唯一非适用指标仍是
机器契约预声明的 `NamespaceScope / foggy-dataset-model / branch`，count=`1`。随后生成并通过
public verification 的 immutable threshold candidate，SHA-256=
`8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`。

该结论只放行 reviewed threshold 的 direct-single-parent Cfreeze；canonical threshold、formal
coverage、coverage audit、acceptance 与 Step 5 在对应后置门完成前仍保持关闭。

## Sealed result

- run window：`2026-07-16T23:58:00Z` 至 `2026-07-17T01:02:36Z`；
- source-before=source-after SHA-256=
  `e95cd9b3ae411ce07ec2128164ff7f6b30a8f5d85d9a24ff6715516feee1f1d9`；
- required reports：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,939 execution class identities`；
- fresh class universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be`；
- aggregate line=`54,624/76,830`（71.0972276455%）；
- aggregate branch=`26,109/44,870`（58.1880989525%）；
- critical classes=`12`，below floor=`0`，structural N/A metric=`1`；
- sensitive scan：5 patterns，extensions=`log,env,json,tsv,xml`，status=`passed`；
- cleanup：container/volume/network residue=`0/0/0`。

官方 sealed diagnostic 与 candidate verification 的 canonical identities 为：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260717-diagnostic-r13
observation=91992393cc2dba4db2e8ae8f8e5fc400273329001b5a3aa61c8df7d91cb7f542

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260717-diagnostic-r13
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `6ce0f33af4d5f279df909e68ca24c87960912ce0f85a8bfa0cfa06b09cae7367` |
| `summary.env` | `bda19f6604faee84914843434cd33fc4f19428051a57509947573521313c6292` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `class-universe.json` | `50bfff3c78d8f9a99aaa16f8d6a8ce19438dcf3ff96b230ffb07b56e2ae2e7a2` |
| `report-inventory.json` | `34ba751d5c77dae1744eefb32e1d6e1e02a2e9c37cbf2bf41c38fca941111a11` |
| `exec-manifest.json` | `340e365da55696c2bc38b8e075b793b02c514d875781478769c8ba7fce94f950` |
| `report/jacoco-aggregate.exec` | `5de31adb5007b158578b8c9c6bc3c9000c87eb30c746122028e5199cae6eea55` |
| `report/aggregate-provenance.json` | `2b598960509e0738ac393dbfd14c7be98b316e0ad8405468257e984c4d7b5aa5` |
| `report/report-provenance.json` | `b263eb5877f48173b647352ac761c88bec38a284f7857d9b276527a62281e224` |
| `report/jacoco-aggregate/jacoco.xml` | `7d524aa41aaf618bea7b8ae804af0e52abfdf25b9d4a1967b7001c10707bc937` |
| `coverage-observation.json` | `91992393cc2dba4db2e8ae8f8e5fc400273329001b5a3aa61c8df7d91cb7f542` |
| `step4-coverage-diagnostic-r13-threshold-candidate-20260717.json` | `8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00` |

Candidate exact 绑定 r13 的 run-status、summary、observation、diagnostic contract、threshold
predecessor、exec manifest、aggregate exec/XML 与 workspace class tree；它保持
`status=review-required`，不得在 review 或 freeze 时回写、补字段或改变 bytes。

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
| `WatchServiceFileTracer` | `204/244` | `98/128` | passed |
| `QueryFingerprintBuilder` | `159/179` | `65/84` | passed |
| `SecurityPolicyFingerprint` | `112/117` | `73/92` | passed |
| `CaffeineQueryCacheProvider` | `126/135` | `64/78` | passed |
| `RedisQueryCacheProvider` | `152/159` | `73/88` | passed |

## Cleanup and external restoration

runner cleanup 证明 run-owned container/volume/network=`0/0/0`。退出后四个 repo demo DB
exact container 在 evidence window 外按原 ID 恢复，均为 `running/healthy`，监听端口 4/4：

| Service | Port | Exact container ID |
|---|---:|---|
| MySQL 5.7 demo | 13306 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` |
| MySQL 8 demo | 13308 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` |
| PostgreSQL demo | 15432 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` |
| SQL Server demo | 11433 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` |

## Next gate

- [x] r13 sealed diagnostic、sensitive scan、cleanup 与 external restoration 已核验；
- [x] `below_floor_class_count=0`、structural N/A count=`1`；
- [x] threshold candidate 已生成并通过 public verification；
- [x] independent exact projection review 已记录；
- [x] reviewed threshold/contract/manifest 已写入 Cfreeze working-tree machine delta；
- [ ] 将全部 allowlisted delta 一次提交为 Cdiag 的唯一 direct-child，并完成 topology proof；
- [ ] Cfreeze 后执行 fresh formal，并完成最终 implementation quality；
- [ ] 之后才按 Gate Order 进入 coverage audit 与 acceptance。

当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；formal 与 Step 5 仍 pending。
