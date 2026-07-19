---
evidence_type: successful-diagnostic-portable-capsule
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r26
tested_commit: 4fe86929de6206aa3e514c974635e90395c28b2e
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-authorized
candidate_status: review-required
capsule_status: sealed
review_evidence_sha256: 886f735d4f2e2812df9240f348b58c777777a326c84f89e077f41e80c4c539da
recorded_at: 2026-07-19
---

# Step 4 coverage diagnostic r26 pass

## Decision

`step4-coverage-20260719-diagnostic-r26` 从 clean、pushed
`HEAD == origin/codex/v934-release-authority == 4fe86929de6206aa3e514c974635e90395c28b2e`
启动并完整退出：`last_phase=completed`、`exit_code=0`、`status=diagnostic-observed`。
Public `validate-diagnostic-run` 复算通过，source before/after、全部 required lanes、两次
aggregate replay、critical floor、model/sensitive gate 与 cleanup 均通过。

r26 aggregate line=`54,624/76,830`、branch=`26,112/44,870`、complexity=
`17,659/35,571`。critical=`12 classes / 23 applicable metrics / 1 structural N/A / below 0`；
唯一 N/A 仍为 `NamespaceScope / foggy-dataset-model / branch`。Immutable threshold candidate
已通过 public verification 与两路 independent review，SHA-256=
`b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a`。冻结后的 review 文档为
`step4-coverage-diagnostic-r26-threshold-review-20260719.md`，SHA-256=
`886f735d4f2e2812df9240f348b58c777777a326c84f89e077f41e80c4c539da`。

portable capsule 经两次独立重建，archive 与 manifest 均与 canonical bytes exact；公共 verify、
真正空目录 materialize 与 self-test 均通过。因此 r26 是当前链唯一 reviewed diagnostic candidate
source，只授权 Cdiag 的 direct-single-parent Cfreeze。它不是 Cfreeze、fresh formal-r8、coverage
audit、Step 4 acceptance 或 9.3.4 version signoff。

## r25 supersession boundary

`step4-coverage-20260719-diagnostic-r25` 虽完成 public diagnostic validation，但测试发生在 Unit
MySQL 第七个真实消费者 `DatasetJdbcUtilsTest` 的 fail-closed assertion 与 fixture schema 2 生效前。
旧测试捕获 `SQLException` 并只输出 stack trace，不能证明数据库消费成功；旧 schema 1 又把历史
observed failure `6 reports / 11 nodes` 误当成 current consumer inventory。

r25 因而永久保持 `pre-remediation / superseded / non-candidate`。不得从 r25 生成或恢复 threshold
candidate、portable capsule、Cfreeze 或 formal authority；r26 的成功也不追认 r25。r26 以真实
`SELECT 1` 精确断言、schema 2 的 historical=`6/11` 与 reviewed current lower bound=`7/12`、
negative=`42/42`、lifecycle=`5/5` 重新建立当前候选来源。

## Sealed result

- run window：`2026-07-19T02:57:46Z` 至 `2026-07-19T04:07:07Z`；
- source-before=source-after SHA-256=
  `6acfad24cc3d43c3bf550c904aa61c7e01f5b7829d4e2f204d489ab6cc40a8f5`；
- required：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,952 execution class identities`；
- production universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b`；
- aggregate instruction=`252,725/352,456`、line=`54,624/76,830`、branch=
  `26,112/44,870`、method=`9,068/12,701`、class=`1,503/1,716`、complexity=
  `17,659/35,571`；
- target Unit exec SHA-256=
  `9712f746a6ff4d4105061abba1dcc7ce4ee8e2cc77ffcef30afdaee65bdf672e`；
- Unit MySQL fixture schema=`2`、historical=`6/11`、current lower bound=`7/12`、
  negative=`42/42`、lifecycle=`5/5`；
- isolated r4 positive=`Maven rc0 + XML 1/F0E0S0`、wrong-password=
  `Maven rc1 + XML 1/F0E1S0`、cleanup=`container/name absent + port released`；
- model external gate=`passed`、sensitive scan=`passed`、cleanup residue=`0/0/0`；
- diagnostic acceptance artifact=`not-generated`。

公开复算：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260719-diagnostic-r26
observation=15e1ed76eaa624c0899b980472689e34e1b272ddda58b2bc5cf27994abffe705

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260719-diagnostic-r26
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `09499e0e0d702e7e122b6225126e1a044b3d047881df9428aaa845a6819abd9b` |
| `run.log` | `cafab94d319e7be37c61e501fbbe681d3b38b030ab935fdf824f4d6cc488b860` |
| `run-context.json` | `8c0deaac452c39b22994ef68aa0a4eade77360d8a472a15bb9352a2dd1e0e4b3` |
| `summary.env` | `2613edc69c764083a73705385242e411e115a2197295bc2e6a4dfc1d2eb13918` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `20f9e571a15f36fcc777011cb70b1f7012392035eda660c879d7d600f2a7ccda` |
| `child-lifecycle.json` | `e354ecc32c515311d06438e67d2f49489feed2955d30bf2dcafcad832d07b027` |
| `model-gate.env` | `c0a9007ee33fc9323629a279fe18aca3cf9ee76de5dff33b2a6923a777a6647f` |
| `class-universe.json` | `a86a3502c43a8a6bd1c4b35f44d31fbf4210f579975cacbb71dd14f0b0b4cfa5` |
| `report-inventory.json` | `4397bbd059bbe49caf73d2b23bc4ed97df20e02e1ca86e2f1b9b21f1253f1ed6` |
| `exec-manifest.json` | `d69f151abf3da4995ae8062e967c25bfe5627754c1a492dcc6c5d8317a989574` |
| `report/jacoco-aggregate.exec` | `eb2d7cdb165f120faaf0d3adf277295e8da6b35466df4d42029fb0ed68f96448` |
| `report/aggregate-provenance.json` | `e87489dbdef149830c71cdc113bde288440c802bed3191824c0940834e1254e9` |
| `report/report-provenance.json` | `4a2407b60cdce4fa96f9b5a6258e7a23bc6109a151f8e1d060f3ada18da9718d` |
| `report/jacoco-aggregate/jacoco.xml` | `13ef004c3c4096e0d6aa902d86c8c7f04f465db511dce36a8daebc2e19b3cfca` |
| `coverage-observation.json` | `15e1ed76eaa624c0899b980472689e34e1b272ddda58b2bc5cf27994abffe705` |
| threshold candidate | `b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a` |
| threshold review | `886f735d4f2e2812df9240f348b58c777777a326c84f89e077f41e80c4c539da` |
| portable capsule archive | `29313c2b0fb88c248b9f8c1e6085bd3114f92e74bc0946ca32c33db4b74e61c2` |
| portable capsule manifest | `68bb1740548c323de89819d6b17d634013235665f37edba5fba56accf8db405e` |
| isolated r4 `status.env` | `391521f816d78cce98110a2063238e2400fabe0d1aec81dfe21dfabb498a0991` |
| isolated r4 `artifacts.tsv` | `4429295af89eb63c574ac85a96c95bc044debd8cb429cf47cf68d22c2a575349` |
| isolated r4 positive XML | `4e22c3f7132b9efcf41021fb6b03f1a5b318b1bde95b308ad78ddf75f6073c4b` |
| isolated r4 wrong-password XML | `dbf5445bf32379ce7e044950d418c17f4a24cf4d27a4aa54d4a5bb07acbb2f23` |
| isolated r4 `cleanup.env` | `21b3aa358d4da62eda304fce1a549be6f4acc6d6a6374d8d3091b89ca34f4aed` |

## Portable capsule review

- canonical archive size=`20,014,615` bytes；manifest size=`1,978,286` bytes；
- Reviewer B 独立重建两次，两个 archive/manifest 相互 exact，且分别与 canonical exact；
- manifest=`schema 1 / sealed / 6,638 entries / 5,884 files / 754 directories`；
- symlink/special=`0/0`，`omitted_negative_fixture_symlinks=[]`；
- public verify 精确绑定 run/head/source/archive SHA 与 size；
- truly empty directory materialize=`passed`，全部 `6,638` entries 闭合且 mismatch=`0`；
- portable capsule self-test=`8/8`；candidate 与 review bytes 在复验前后不变。

## Negative and independent review result

- coverage exec=`17`、coverage XML=`9`、generic XML=`124`、inventory=`30`；
- successor overlay=`20/20`、Unit fixture negative=`42/42`、lifecycle=`5/5`；
- Reviewer A `/root/r26_candidate_review`：candidate/counters/report/fixture/isolated=
  `APPROVE / B0 H0 M0 L0`；
- Reviewer B `/root/r26_capsule_review`：repository/source/capsule/materialize/restore=
  `APPROVE / B0 H0 M0 L0`；
- combined findings=`0/0/0/0`、mandatory actions=`0`、`user_confirmation=not-asserted`；
- canonical review identity=
  `886f735d4f2e2812df9240f348b58c777777a326c84f89e077f41e80c4c539da`。

## Cleanup and only legal next gate

Runner-owned cleanup 证明 container/volume/network=`0/0/0`。外层 wrapper 的
runner/restore/receipt/outcome=`0/0/0/0`，并恢复开跑前绑定的 MySQL 5.7、MySQL 8、PostgreSQL、
SQL Server 四个 exact demo container，均为 `running / healthy`；外部恢复不混入 runner artifact。

- [x] sealed r26 diagnostic、public validation、critical floor 与 cleanup；
- [x] Unit MySQL schema 2、current lower bound `7/12`、negative/lifecycle 与 isolated proof；
- [x] immutable candidate + two independent reviews；
- [x] deterministic capsule rebuild/verify + empty sandbox materialize；
- [ ] 唯一 direct-single-parent Cfreeze commit/push/clean；
- [ ] fresh clone frozen replay + fresh formal-r8；
- [ ] post-formal implementation quality、coverage audit、Step 4 feature acceptance；三门仅开放 Step 5；
- [ ] 完成 Steps 5–7；
- [ ] 形成独立的 9.3.4 version signoff。

唯一合法后续链路为：

```text
Cdiag 4fe86929de6206aa3e514c974635e90395c28b2e
  -> direct-single-parent Cfreeze
  -> fresh formal-r8
```

Cfreeze 必须写入 review evidence path，并绑定其冻结 SHA-256
`886f735d4f2e2812df9240f348b58c777777a326c84f89e077f41e80c4c539da`。不得跳过 Cfreeze、
不得复用 r25、不得把 r26 diagnostic/candidate/capsule 当作 formal 或 acceptance evidence。

Current `can_enter_cfreeze=yes`、`can_enter_formal=no-before-cfreeze`、
`can_enter_coverage_audit=no`、`can_enter_acceptance=no`。
