---
evidence_type: successful-diagnostic-portable-capsule
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r22
tested_commit: cfb64d629b06f03a078ebae06580918d4c8df301
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-authorized
candidate_status: review-required
capsule_status: sealed
recorded_at: 2026-07-19
---

# Step 4 coverage diagnostic r22 pass

## Decision

`step4-coverage-20260719-diagnostic-r22` 从 clean、pushed
`HEAD == origin/codex/v934-release-authority == cfb64d629b06f03a078ebae06580918d4c8df301`
启动并完整退出：`last_phase=completed`、`exit_code=0`、`status=diagnostic-observed`。
Public `validate-diagnostic-run` 复算通过，source before/after、全部 required lanes、两次
aggregate replay、critical floor、model/sensitive gate 与 cleanup 均通过。

r22 aggregate line=`54,624/76,830`、branch=`26,111/44,870`，与 r19/r21 reviewed
high-water exact 相同。critical=`12 classes / 23 applicable metrics / 1 structural N/A / below 0`；
唯一 N/A 仍为 `NamespaceScope / foggy-dataset-model / branch`。Immutable threshold candidate
已通过 public verification 与两路 independent review，SHA-256=
`044f4b9f717b062d2c214d1c9b2494ec7da2d77e8ec69275acdf66eb01c70d5a`。

portable capsule 连续构建两次，archive 与 manifest 均逐字节相同；公共 verify 和真正空目录
materialize 均通过。r22 因此授权 Cdiag 的 direct-single-parent Cfreeze，但尚不是 Cfreeze、
fresh formal、coverage audit 或 acceptance。

## Sealed result

- run window：`2026-07-18T17:51:10Z` 至 `2026-07-18T19:00:56Z`；
- source-before=source-after SHA-256=
  `abb26710a7c7c9c23b31219a5c9e907a575227bd12e5af7ed055241e70118666`；
- required：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,946 execution class identities`；
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
run=step4-coverage-20260719-diagnostic-r22
observation=974c7e4c0434a7c7fb6650913b262c1c8dd18607787f01c7cc3a629eda58b04b

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260719-diagnostic-r22
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `a83ebf931b4c5d349ef665d26c831954d176d8a1a3783780dac801b402e8c6e1` |
| `run.log` | `0f475d7372dd4404fb4cbefbc05f0d63fdfbaef20aa8973349b5f7207c564750` |
| `run-context.json` | `321b0e0154dc76fb3e057f215421ec76e5ead5d1239c0d6e7e5f035486966c21` |
| `summary.env` | `a50f7bdc895248eedfcb5a92521944bfd05ef34a22092ca477f47aac75e3225f` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `57476c08a9a5abd315b31713f0964bca58dcea747f07790acd4639484ccd7842` |
| `child-lifecycle.json` | `024d6be5db91001f96e706df4e2f445ef08e674500ea7f71ace40862ae25586c` |
| `model-gate.env` | `5b8c4de99cb74dd513e6ad68323af4f15299c83cc95856963d6e852e09792ac5` |
| `class-universe.json` | `71308b9cb048ea882dd76b775f4330ad8fcf542e8fa38b1e01559ae6a61f6328` |
| `report-inventory.json` | `c5b4b94019941f59c42a840948f5462b37d11dab290fe295d85d5b4d3141bd36` |
| `exec-manifest.json` | `44ab415141d9451d8e3ee75d351f3d351b5f3779ddf2da9241401a432dc5a90a` |
| `report/jacoco-aggregate.exec` | `b90a2216f3dde3e5fe09537b9ee11dbb4f4b855b397bba67eb2b4ade43cfb8ed` |
| `report/aggregate-provenance.json` | `72017fa5b9fdd3a281aa45b7d238676eb994f6756f033af490d9eefe0c120c11` |
| `report/report-provenance.json` | `09d3e643ee6d1bcb54384461a730049b27250c9f3c973381a4fbdf4c4e6a404f` |
| `report/jacoco-aggregate/jacoco.xml` | `475878a261979ad2edde9fffeb90321822db64e4f23731defe28e5478e1f598c` |
| `coverage-observation.json` | `974c7e4c0434a7c7fb6650913b262c1c8dd18607787f01c7cc3a629eda58b04b` |
| threshold candidate | `044f4b9f717b062d2c214d1c9b2494ec7da2d77e8ec69275acdf66eb01c70d5a` |
| portable capsule archive | `b7a6282bed98013ac65df6e630cda4980951b835b0c128d6a47a76e07b1482b9` |
| portable capsule manifest | `bf61342c3a7cd35924e72d1fc861587da462ff19663c2722065eab64653877b4` |

## Portable capsule review

- canonical archive size=`20,008,564` bytes；manifest size=`1,978,286` bytes；
- deterministic double build：archive `cmp=passed`、manifest `cmp=passed`；
- manifest=`schema 1 / sealed / 6,638 entries / omitted_negative_fixture_symlinks=[]`；
- public verify 精确绑定 run/head/source/archive SHA 与 size；
- truly empty directory materialize=`passed`，materialized entries=`6,638`、symlink=`0`；
- source closure=`4,060 files / 80,528,318 bytes / mismatch 0`；
- candidate 在双 review、verify、materialize 前后 SHA 不变。

## Negative and independent review result

- coverage exec=`17`、coverage XML=`9`、generic XML=`124`、inventory=`30`；
- portable capsule self-test=`8/8`、successor overlay=`20/20`；
- Reviewer A：candidate/evidence/critical/capsule=`APPROVE / B0 H0 M0 L0`；
- Reviewer B：independent provenance/source/capsule replay=`APPROVE / B0 H0 M0 L0`；
- combined findings=`0/0/0/0`，`user_confirmation=not-asserted`。

## Cleanup and next gate

Runner-owned cleanup 证明 container/volume/network=`0/0/0`。退出后外层恢复了开跑前绑定的
MySQL 5.7、MySQL 8、PostgreSQL、SQL Server 四个 exact demo container；均为
`running / healthy`，端口 `13306/13308/15432/11433` 已恢复监听。外部恢复不混入 runner artifact。

独立 fresh clone portability smoke 同时证明 sibling Python repository 不存在时，14 个 producer
仍保持 `external=0`，model `13/13`、MCP `2/2`，且十个 parity artifact 全部位于 module-local
`target/parity/**`。

- [x] sealed diagnostic、public validation、high-water、critical 与 cleanup；
- [x] immutable candidate + two independent reviews；
- [x] deterministic capsule build/verify + empty sandbox materialize；
- [ ] direct-single-parent Cfreeze commit/push/clean；
- [ ] fresh clone frozen replay + fresh formal；
- [ ] implementation quality、coverage audit、acceptance 与 Step 4 exit。

Current `can_enter_cfreeze=yes`、`can_enter_coverage_audit=no`、`can_enter_acceptance=no`。
