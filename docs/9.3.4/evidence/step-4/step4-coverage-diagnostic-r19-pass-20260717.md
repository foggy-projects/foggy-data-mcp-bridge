---
evidence_type: successful-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r19
tested_commit: 613b11a0ae6732f865f918551cd9116079771b5e
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-pending
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r19 pass

## Decision

`step4-coverage-20260717-diagnostic-r19` 从 clean/pushed
`HEAD == origin/main == 613b11a0ae6732f865f918551cd9116079771b5e` 启动并完整退出：
`last_phase=completed`、`exit_code=0`、`status=diagnostic-observed`。同一 source-sealed
authority window 完成全部 required lanes、两次 aggregate report 重放、coverage observation、
model external gate、negative probes、sensitive scan 与 cleanup；public
`validate-diagnostic-run` 复算通过。

r19 aggregate line=`54,624/76,830`、branch=`26,111/44,870`，精确恢复且不低于 r16
reviewed high-water。critical classes=`12`、below-floor=`0`；唯一 structural N/A 仍为
`NamespaceScope / foggy-dataset-model / branch`。因此允许从 sealed r19 生成 immutable
threshold candidate；candidate 已通过 public verification，SHA-256=
`6588e30bd6e51aa27bcab06cf633cc51cefa2d5a27824eee5c003bcbe9f545b8`。

该 sealed diagnostic 结论随后已由两路 independent threshold review 与 canonical
formalization 推进到 Cfreeze working-tree ready；它仍不是已提交的 Cfreeze、fresh formal、
coverage audit、acceptance 或 Step 4 exit。

## Sealed result

- run window：`2026-07-17T13:31:52Z` 至 `2026-07-17T14:37:38Z`；
- source-before=source-after SHA-256=
  `a4ccff29f7bc0dc3f0613d2f732eecae95a0e324aa21dff8b31f789820c38f65`，两个 TSV
  byte-identical；
- required：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,931 unique execution class identities`；
- fresh class universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be`；
- aggregate instruction=`252,725/352,456`、line=`54,624/76,830`、branch=
  `26,111/44,870`、method=`9,068/12,701`、class=`1,503/1,716`、complexity=
  `17,658/35,571`；
- model external gate=`passed`、sensitive scan=`passed`、cleanup residue=`0/0/0`。

公开复算：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260717-diagnostic-r19
observation=43735e488079108c63fcfe7cb89b44245dd44b7eba8442ba8f5b6df23f5df50b

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260717-diagnostic-r19
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `c7511e0cf7b2499272851bb7574b435446d02594562a121869c882138e9467fa` |
| `run.log` | `5a2ca3e53838c516f1289ec611f84233f32e242168a0b79053993a1abcb05333` |
| `run-context.json` | `f34fc8642fac30865254b4e25a1af5bc3dd465e6dd9cd302c3e91782972d264b` |
| `summary.env` | `90e007edab12d0d12677c2820ee38dae70162108052a1056efb827955b408cfa` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `4060491ecbe30ed95ee278d6a19d8246e362b961326d82bac0cb30806845d2ad` |
| `child-lifecycle.json` | `342c20969b5baec1bc7fd810c704b90ea9a5dd6315f5d79e661b5f1ff1ddb053` |
| `model-gate.env` | `c967fa3f9deb45f967166cd176f516a7788966dea36ac52245de7c1612f620e9` |
| `class-universe.json` | `41e44187c85a0426e9ee1fc93dc1e91089c7693cb273a16e9acbf66c3590c3983` |
| `report-inventory.json` | `f4c91be1319bd08ba6e85f50184cd4e9dc56e62a7d610ae5fc46b3b16b696e52` |
| `exec-manifest.json` | `ab75669e985d4f3d939d9737afa033586be95c85ecc52bbdc6490c62487f257b` |
| `report/jacoco-aggregate.exec` | `f3aa604e8a05fb4b5bb6975f628cfc410478bec6afe3a1d5cd271d878b7da4d0` |
| `report/aggregate-provenance.json` | `5b0f4f6bd64a29d6c4e0be0e973ae8e6fab0031ac9849a0d17f4a5af492e9168` |
| `report/report-provenance.json` | `7e2914a9d4911e3aef1f18b275c7e571558f9197c468ab8897056cd0a18be40a` |
| `report/jacoco-aggregate/jacoco.xml` | `5c137de8f75edd6a8647eb264fc57be09a36e8e9883d02c5b3f496495bb88532` |
| `coverage-observation.json` | `43735e488079108c63fcfe7cb89b44245dd44b7eba8442ba8f5b6df23f5df50b` |
| threshold candidate | `6588e30bd6e51aa27bcab06cf633cc51cefa2d5a27824eee5c003bcbe9f545b8` |

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

## High-water and deterministic-oracle review

- r19 与 r16 aggregate exact 相同；相对 r18 恢复 `+2 line / +4 branch`；
- 恢复来自既有 `PivotSqlParityIT` S12 内的 deterministic NULL-axis business oracle，未新增/
  改名 testcase、未修改 production、runner、coverage floor、critical set 或 exclusion；
- 12 个 critical exact counters、production denominator、class tree、`23/48` execution structure
  均稳定；execution class identity `16,931` 只表示本次运行的 class-loading footprint；
- candidate 只能 exact 投影 r19 observation，不得回退 r18、拼接 partial artifact 或改写 bytes。

## Cleanup and external restoration

Runner-owned cleanup 证明 container/volume/network residue=`0/0/0`。runner 退出后外层 marker=
`runner_rc=0 / restore_rc=0`，并恢复开跑前绑定的四个 exact demo DB container；独立复查均为
`running / healthy`：MySQL 5.7 `0c50bf7e8684…c166`、MySQL 8
`f603e63eaf82…816a`、PostgreSQL `a4d6a278b0b9…1d07`、SQL Server
`7881fa53039f…1fd`。这些状态属于 evidence window 外的恢复复查，不混入 runner-owned artifact。

## Next gate

- [x] r19 sealed diagnostic、public validation、source seal、cleanup 与 external restoration；
- [x] aggregate 达到 r16 reviewed high-water，critical below-floor=`0`；
- [x] immutable candidate generated and public verification passed；
- [x] two independent exact-projection/evidence-binding reviews；
- [x] canonical threshold/contract/manifest formalization and pre-Cfreeze quality；
- [ ] direct-child Cfreeze commit/push/clean identity and fresh formal；
- [ ] formal PASS 后的 final implementation quality、coverage audit 与 acceptance。

Current `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；formal、Step 4 exit 与 Step 5
仍关闭。
