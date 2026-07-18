---
evidence_type: successful-diagnostic-portable-capsule
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r24
tested_commit: 414c8b12bff31155584e639e74987bf22df13ba9
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-authorized
candidate_status: review-required
capsule_status: sealed
recorded_at: 2026-07-19
---

# Step 4 coverage diagnostic r24 pass

## Decision

`step4-coverage-20260719-diagnostic-r24` 从 clean、pushed
`HEAD == origin/codex/v934-release-authority == 414c8b12bff31155584e639e74987bf22df13ba9`
启动并完整退出：`last_phase=completed`、`exit_code=0`、`status=diagnostic-observed`。
Public `validate-diagnostic-run` 复算通过，source before/after、全部 required lanes、两次
aggregate replay、critical floor、model/sensitive gate 与 cleanup 均通过。

r24 aggregate line=`54,624/76,830`、branch=`26,112/44,870`、complexity=`17,659/35,571`，
与 r23 exact 相同；r23 的同值来自偶发调度，而 r24 已由 Cdiag 中的 controlled-monitor existing-node
regression 将 `MapBeanInfoHelper#getBeanProperty` 固定为 branch=`4/4`、complexity=`3/3`、raw probe=
`10/11 / _wU`。critical=`12 classes / 23 applicable metrics / 1 structural N/A / below 0`；唯一
N/A 仍为 `NamespaceScope / foggy-dataset-model / branch`。Immutable threshold candidate 已通过
public verification 与两路 independent review，SHA-256=
`f13f3c351eee905ec8985591652adc95fae9b1fbec148585ca6100a428caa2ee`。

portable capsule 连续独立构建两次，且两次结果与 canonical archive/manifest 均逐字节相同；公共
verify 和真正空目录 materialize 均通过。r24 因此授权 Cdiag 的 direct-single-parent Cfreeze，但尚
不是 Cfreeze、fresh formal、coverage audit 或 acceptance。

## Sealed result

- run window：`2026-07-18T21:39:03Z` 至 `2026-07-18T22:48:52Z`；
- source-before=source-after SHA-256=
  `c9c0a57926b25302bd40e8260410ec3a366dcc5b647676143cafccb15d25c607`；
- required：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,953 execution class identities`；
- production universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b`；
- aggregate instruction=`252,725/352,456`、line=`54,624/76,830`、branch=
  `26,112/44,870`、method=`9,068/12,701`、class=`1,503/1,716`、complexity=
  `17,659/35,571`；
- target Unit exec SHA-256=
  `f815299ecc0d50bc108f812412e9967e836315a815a0ad618ff6caf1e734a762`；
- model external gate=`passed`、sensitive scan=`passed`、cleanup residue=`0/0/0`；
- diagnostic acceptance artifact=`not-generated`。

公开复算：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260719-diagnostic-r24
observation=48b72388c47a8010c7c05b82f345841a4277dcbe5ce52f4238e6b071e7ddc387

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260719-diagnostic-r24
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `d248d19163f54d116157ac746d7f120f3783c55e3916e84812b27800bf131d81` |
| `run.log` | `598d54542ade7d1eed9cc3f68546da372a0a365a1ee7fb65735957296a35460a` |
| `run-context.json` | `7b9c0158fcab73e35221854a26e7a939343dd76e23e46134d45c6c296655d84a` |
| `summary.env` | `fb2dd9c575d1c72122f010255201a699f1d12605574f581556b43f95848013f2` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `8dea439c89afe9e9e81bf19aa819e9c8c8255ce9c176cb8103548d0956855cba` |
| `child-lifecycle.json` | `5d5053eb5bd6ab5b84ca61977dff4e7fd46c64c76e7f7064a10165bf62b9a6a5` |
| `model-gate.env` | `7a996e032945d1895ea4ea8b2768dfd92b0e70e76764328668406d29320ecd7c` |
| `class-universe.json` | `b560b10527175f88887c97906d65cdb4962b5ff00aed02c1e4fc2cec6ed7a19a` |
| `report-inventory.json` | `2bbeb2665bfec674f93afc7592daa67831122c518a4eddd2a9979d9e3bc0ebd3` |
| `exec-manifest.json` | `b886f7455952df549fafb9c0d7a27dbf16dc18add8feacd5123c319e1687eff9` |
| `report/jacoco-aggregate.exec` | `8afdc4c55469551be3ebfe7e99d619028f42498f10a1f5152898ff9c5176b14d` |
| `report/aggregate-provenance.json` | `242325ef48196758e4271476a4d491ef8e1d34637d94ad309bfe912edfc4effb` |
| `report/report-provenance.json` | `eef8d67aa61970b3b5dffb83d638359f0f011e0b54997eb832ea6716c16523f3` |
| `report/jacoco-aggregate/jacoco.xml` | `debb72d55e7fe1565692ae31cea16b9eab82f02eeacb728feb26eb262db01d3d` |
| `coverage-observation.json` | `48b72388c47a8010c7c05b82f345841a4277dcbe5ce52f4238e6b071e7ddc387` |
| threshold candidate | `f13f3c351eee905ec8985591652adc95fae9b1fbec148585ca6100a428caa2ee` |
| portable capsule archive | `31a52185ccea806689e1d0daf80cf24f5cf6a2a9c2bf8ad037a30be4cca32f38` |
| portable capsule manifest | `903a375586a1355491d5b046e05a8879fc0c3149318c8a802f14a2a3432df32f` |

## Portable capsule review

- canonical archive size=`20,014,586` bytes；manifest size=`1,978,286` bytes；
- independent double rebuild：两次 archive/manifest 相互 `cmp=passed`，且各自与 canonical exact；
- manifest=`schema 1 / sealed / 6,638 entries / 5,884 files / 754 dirs`；
- symlink/special=`0/0`，`omitted_negative_fixture_symlinks=[]`；
- public verify 精确绑定 run/head/source/archive SHA 与 size；
- truly empty directory materialize=`passed`，missing/extra/type/mode/mtime/size/SHA mismatch 全为 `0`；
- tracked source closure=`4,077 files / 102,591,681 bytes / mismatch 0`；
- candidate 在双 review、verify、rebuild、materialize 前后 SHA 不变。

## Negative and independent review result

- coverage exec=`17`、coverage XML=`9`、generic XML=`124`、inventory=`30`；
- portable capsule self-test=`8/8`、successor overlay=`20/20`；
- Reviewer A `/root/r23_doc_pattern_audit`：candidate/counter/probe/critical=`APPROVE / B0 H0 M0 L0`；
- Reviewer B `/root/r24_machine_preflight`：source/capsule/materialize/sensitive=`APPROVE / B0 H0 M0 L0`；
- combined findings=`0/0/0/0`，`user_confirmation=not-asserted`。

## Cleanup and next gate

Runner-owned cleanup 证明 container/volume/network=`0/0/0`。退出后外层恢复了开跑前绑定的
MySQL 5.7、MySQL 8、PostgreSQL、SQL Server 四个 exact demo container；均为
`running / healthy`。外部恢复不混入 runner artifact。

- [x] sealed diagnostic、public validation、high-water、critical 与 cleanup；
- [x] immutable candidate + two independent reviews；
- [x] deterministic capsule rebuild/verify + empty sandbox materialize；
- [ ] direct-single-parent Cfreeze commit/push/clean；
- [ ] fresh clone frozen replay + fresh formal-r7；
- [ ] implementation quality、coverage audit、acceptance 与 Step 4 exit。

Current `can_enter_cfreeze=yes`、`can_enter_coverage_audit=no`、`can_enter_acceptance=no`。
