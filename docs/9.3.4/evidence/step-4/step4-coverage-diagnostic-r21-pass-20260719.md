---
evidence_type: successful-diagnostic-portable-capsule
version: 9.3.4
step: 4
run_id: step4-coverage-20260718-diagnostic-r21
tested_commit: 5121a9c7fe35120c7864de8554e99188f5d1dc87
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-authorized
candidate_status: review-required
capsule_status: sealed
recorded_at: 2026-07-19
---

# Step 4 coverage diagnostic r21 pass

## Decision

`step4-coverage-20260718-diagnostic-r21` 从 clean、pushed
`HEAD == origin/codex/v934-release-authority == 5121a9c7fe35120c7864de8554e99188f5d1dc87`
启动并完整退出：`last_phase=completed`、`exit_code=0`、`status=diagnostic-observed`。
Public `validate-diagnostic-run` 复算通过，source before/after、全部 required lanes、两次
aggregate replay、critical floor、model/sensitive gate 与 cleanup 均通过。

r21 aggregate line=`54,624/76,830`、branch=`26,111/44,870`，与 r19/r20 及既有 reviewed
high-water exact 相同。critical=`12 classes / 23 applicable metrics / 1 structural N/A / below 0`；
唯一 N/A 仍为 `NamespaceScope / foggy-dataset-model / branch`。因此允许生成 immutable
threshold candidate；candidate 已通过 public verification 与两路 independent review，SHA-256=
`5f789e986d9e79854fe98b8433b684564fb37c17c349cf3e80ba66f18ad98fdc`。

portable capsule 已在不修改 run-root、target/classes 或 support files 的窗口中连续构建两次，
archive 与 manifest 均逐字节相同；公共 verify 和真正空目录 materialize 均通过。r21 因此授权
Cdiag 的 direct-single-parent Cfreeze，但尚不是 Cfreeze、fresh formal、coverage audit 或 acceptance。

## Sealed result

- run window：`2026-07-18T15:17:16Z` 至 `2026-07-18T16:26:35Z`；
- source-before=source-after SHA-256=
  `6b6796d496c3ee16bf66a410665e19fcfb8861c1d650050a9442e7d4f3e97f4d`；
- required：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,945 execution class identities`；
- production universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b`；
- aggregate instruction=`252,725/352,456`、line=`54,624/76,830`、branch=
  `26,111/44,870`、method=`9,068/12,701`、class=`1,503/1,716`、complexity=
  `17,658/35,571`；
- model external gate=`passed`、sensitive scan=`passed`、cleanup residue=`0/0/0`；
- diagnostic acceptance artifact=`not-generated`。

公开复算：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260718-diagnostic-r21
observation=8e0b0ad547524012cdc620fad50ff4b01d6f8d25bd034a61aa28fb51fa1d27d2

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260718-diagnostic-r21
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `4f55749f529520e0d302674cd5fc7aaffea31f25ea6b14f1d4bee3dc4f7d87cc` |
| `run.log` | `8eaac563ed2cdf3dc607cf29512dbdb0ee8f35f1c1e4aaa6e3fa26c51b0c5d7a` |
| `run-context.json` | `c9a06bfe1a2e6a3a10a001715b2b936052fbf67d91b678da53e0a066a5291f58` |
| `summary.env` | `a1488df8d09652e06a3db901ca65238c9ed16d14e2be0feb9161156dff9f95a2` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `a7d0d885139de885f7f851cf729e5ad7b36237e894f7f84be2928f121b76e9f9` |
| `child-lifecycle.json` | `da95d353a27d4e20b8a68629798e5b980b293c3cdfb078a3477b4ba18bb9e83e` |
| `model-gate.env` | `bc9cd221e50f58e97971c16d79110afe7aeac5059775ef6263f0a4889b5eda43` |
| `class-universe.json` | `ba0fc38266eabc8f2c86e8b02e7a19154ceb5067e7c21fcb76794dd1a7d6e968` |
| `report-inventory.json` | `ef82d7cab3011c82e2278f78f9715348c6ed7baee364125e97df843b3825e673` |
| `exec-manifest.json` | `04cb92bcfc22230a875045c981a6276a6023d04518f5421b7005e282116d41ad` |
| `report/jacoco-aggregate.exec` | `c49322ca118d039c76bf636db6166f11b016551b817760839e1386c1d5ac07ed` |
| `report/aggregate-provenance.json` | `739685bac4195c617ece1086dbbb7086089acd305f7c1cafe554265348fd6144` |
| `report/report-provenance.json` | `7f1cfa04f059f606d59968a649a1ba37883ec53e032ff58947d4465f9a88f48b` |
| `report/jacoco-aggregate/jacoco.xml` | `0bd4fa1afe5a0c96df3de3be6dec83f5711ed411c42d1a7631267e53c889b657` |
| `coverage-observation.json` | `8e0b0ad547524012cdc620fad50ff4b01d6f8d25bd034a61aa28fb51fa1d27d2` |
| threshold candidate | `5f789e986d9e79854fe98b8433b684564fb37c17c349cf3e80ba66f18ad98fdc` |
| portable capsule archive | `7e1b9461373f4f1e1f231b6d39711cd020938fa8182276beee7fe9e39abf0444` |
| portable capsule manifest | `8c1fcd86ddd046e7a308f5147b4b01aa000fe7880c540f2d73d4302ac13c37a6` |

## Portable capsule review

- canonical archive size=`20,014,034` bytes；manifest size=`1,978,286` bytes；
- deterministic double build：archive `cmp=passed`、manifest `cmp=passed`；
- manifest=`schema 1 / sealed / 6,638 entries / omitted_negative_fixture_symlinks=[]`；
- public verify 精确绑定 run/head/source/archive SHA 与 size；
- Reviewer B 从真正空目录 materialize=`passed`，materialized entries=`6,638`、symlink=`0`；
- materialized run/support/class files 的 mode、mtime、size、SHA 与 manifest exact；
- candidate 在双 review、verify、materialize 前后 SHA 不变。

## Negative and independent review result

- coverage contract=`27`、Git identity=`22`、threshold/frozen replay=`12`；
- coverage XML fast negatives=`124`，其中 capsule self-test=`8/8`；
- successor overlay=`20/20`、database adapter=`14/14`、CI=`86/86`；
- Reviewer A：exact metric/projection=`APPROVE / B0 H0 M0 L0`；
- Reviewer B：evidence binding/capsule=`APPROVE / B0 H0 M0 L0`；
- combined findings=`0/0/0/0`，`user_confirmation=not-asserted`。

## Cleanup and next gate

Runner-owned cleanup 证明 container/volume/network=`0/0/0`。退出后外层恢复了开跑前绑定的
MySQL 5.7、MySQL 8、PostgreSQL、SQL Server 四个 exact demo container；均为
`running / healthy`，端口 `13306/13308/15432/11433` 已恢复监听。外部恢复不混入 runner artifact。

- [x] sealed diagnostic、public validation、high-water、critical 与 cleanup；
- [x] immutable candidate + two independent reviews；
- [x] deterministic capsule build/verify + empty sandbox materialize；
- [ ] direct-single-parent Cfreeze commit/push/clean；
- [ ] fresh clone frozen replay + fresh formal；
- [ ] implementation quality、coverage audit、acceptance 与 Step 4 exit。

Current `can_enter_cfreeze=yes`、`can_enter_coverage_audit=no`、`can_enter_acceptance=no`。
