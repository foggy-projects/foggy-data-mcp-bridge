---
evidence_type: successful-diagnostic-portable-capsule
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r28
tested_commit: 507e1732b587c530026f19fae5d570b8845e369b
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-authorized
candidate_status: review-required
capsule_status: sealed
review_evidence_sha256: 18aff83396aa512bc9e2b6924b9cc53cdac61269e9124472e733a4394f8007d1
recorded_at: 2026-07-19
---

# Step 4 coverage diagnostic r28 pass

## Decision

`step4-coverage-20260719-diagnostic-r28` 从 fresh、non-shallow、clean repository 的
`HEAD == origin/codex/v934-release-authority == 507e1732b587c530026f19fae5d570b8845e369b`
启动并完整退出：`last_phase=completed`、`exit_code=0`、`status=diagnostic-observed`。
Public `validate-diagnostic-run` 复算通过，source before/after、全部 required lanes、两次
aggregate replay、critical floor、model/sensitive gate 与 cleanup 均通过。

r28 aggregate line=`54,624/76,830`、branch=`26,112/44,870`、complexity=
`17,659/35,571`，与 r26 governed high-water 精确一致。critical=`12 classes / 23 applicable
metrics / 1 structural N/A / below 0`；唯一 N/A 为 `NamespaceScope / foggy-dataset-model /
branch`。Immutable threshold candidate 已通过 public verification 和两路 independent review，
SHA-256=`1b654214d23c60448d88d14fd7f2f5e8e7dd08636721f10bf735c5db08ea39d5`。冻结后的 review
文档为 `step4-coverage-diagnostic-r28-threshold-review-20260719.md`，SHA-256=
`18aff83396aa512bc9e2b6924b9cc53cdac61269e9124472e733a4394f8007d1`。

portable capsule 经两次独立重建，archive 与 manifest 均与 canonical bytes exact；public verify、
真正空目录 materialize 与 self-test 均通过。因此 r28 是当前链唯一 reviewed diagnostic candidate
source，只授权对 Cdiag `507e1732...` 创建 direct-single-parent Cfreeze。它不是 Cfreeze、fresh
formal-r9、coverage audit、Step 4 acceptance 或 9.3.4 version signoff。

## Sealed result

- run window：`2026-07-19T09:31:43Z` 至 `2026-07-19T10:32:43Z`；
- source-before=source-after SHA-256=
  `6c8b09e357fbc9237c8fba51a477e4b4465c58ca53d025d7c6646f153e6e1b36`；
- required：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,934 unique execution class identities`；
- production universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b`；
- aggregate instruction=`252,725/352,456`、line=`54,624/76,830`、branch=
  `26,112/44,870`、method=`9,068/12,701`、class=`1,503/1,716`、complexity=
  `17,659/35,571`；
- model external gate=`passed`、sensitive scan=`passed`、runner cleanup residue=`0/0/0`；
- diagnostic acceptance artifact=`not-generated`。

公开复算：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260719-diagnostic-r28
observation=cc3570e6d493ba58608da385b34c8e9906179fd998ec869d81aaa83d550d50e3

[v934-coverage-xml] THRESHOLD CANDIDATE VALID
run=step4-coverage-20260719-diagnostic-r28
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `79faf73fc89bdf73c17c132e6ae91ef99eaea558e125effd40c517aa93b70618` |
| `summary.env` | `e6c348f6d8215ac67d1fcae1176bc60b6fd5964eb9254fa0748b75d419c017c1` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `8c7d5a76a20ccffd580f8d2544aef271e4e88093360536bc67187c881173fc13` |
| `child-lifecycle.json` | `bfbb1a705e748912007d3e0d7c290f9ed6f39d96fc7b1b36a255843172ff8e86` |
| `model-gate.env` | `bc0fa24e4d2db4faf755ac62423f2c2686fb1e42f2557db2ec05e47a240f9c4f` |
| `class-universe.json` | `b15731b150c8ada433eb04cfb4b55e2983524d9fed8bc58d2df6b9b8843109a8` |
| `report-inventory.json` | `81bd2ed14ba151eededec9e7d76ae0ef8910f796f22467bcf5dbc85001d0c062` |
| `exec-manifest.json` | `81b505742f6c03e35145fcc99700e1695edc039aaee5077c16cd1a68aab6bde5` |
| `report/jacoco-aggregate.exec` | `f6d9723b4e70dc65d9a54dfd3beb6546376bf32617ef1014fdaaf9fa67dbcf68` |
| `report/aggregate-provenance.json` | `c1626e119a660097412771811af1f22a923268fa184e2b167cb0d9b374cffcaa` |
| `report/report-provenance.json` | `cece98a9ee6c353aff831051bd85df65aedfd3dc7fd24c297877cb6b6d4f8480` |
| `report/jacoco-aggregate/jacoco.xml` | `fe701d3d12fda92f67f10056307c7e4bfb6d30a522e4da861d718690351dc7f9` |
| `coverage-observation.json` | `cc3570e6d493ba58608da385b34c8e9906179fd998ec869d81aaa83d550d50e3` |
| threshold candidate | `1b654214d23c60448d88d14fd7f2f5e8e7dd08636721f10bf735c5db08ea39d5` |
| threshold review | `18aff83396aa512bc9e2b6924b9cc53cdac61269e9124472e733a4394f8007d1` |
| portable capsule archive | `3f0ae2bcf2f0cbd93228e9d8bfb0914dd0ef3fe2f85e8dd87143f4651f00cee6` |
| portable capsule manifest | `b23c140a4a326ef216c7353a6ee4e9511bb4a74fd1fcc3e68fa6d80b5a906e48` |

## Portable capsule review

- canonical archive size=`20,010,306` bytes；manifest=`schema 1 / sealed / 6,638 entries`；
- Reviewer B 在 fresh r28 repository 重建，archive 与 manifest 分别与 canonical exact；
- symlink/special=`0/0`，`omitted_negative_fixture_symlinks=[]`；
- public verify 精确绑定 run/head/source/archive SHA 与 size；
- truly empty directory materialize=`passed`，全部 `6,638` entries 闭合且 mismatch=`0`；
- portable capsule self-test=`8/8`；candidate 与 review bytes 在复验前后不变。

## Negative and independent review result

- coverage exec=`17`、coverage XML=`9`、generic XML=`124`、inventory=`30`；
- successor overlay=`20/20`；
- Reviewer A `/root/r26_candidate_review`：candidate/counters/report/source=
  `APPROVE / B0 H0 M0 L0`；
- Reviewer B `/root/r28_capsule_review`：repository/source/capsule/materialize/restore=
  `APPROVE / B0 H0 M0 L0`；
- combined findings=`0/0/0/0`、mandatory actions=`0`、`user_confirmation=not-asserted`；
- r27 diagnostic candidate/capsule remains permanently noncanonical and is excluded.

## Cleanup and only legal next gate

Runner-owned cleanup proves container/volume/network=`0/0/0`。外层 wrapper 的
runner/restore/receipt/outcome=`0/0/0/0`，并恢复开跑前绑定的四个 demo container，均为
`running / healthy` 且 exact ID match；外部恢复不混入 runner artifact。

- [x] sealed r28 diagnostic、public validation、r26 high-water、critical floor 与 cleanup；
- [x] immutable candidate + two independent reviews；
- [x] deterministic capsule rebuild/verify + empty sandbox materialize；
- [ ] 唯一 direct-single-parent Cfreeze commit/push/clean；
- [ ] fresh clone frozen replay + fresh formal-r9；
- [ ] post-formal implementation quality、coverage audit、Step 4 feature acceptance；三门仅开放 Step 5；
- [ ] 完成 Steps 5–7；
- [ ] 形成独立的 9.3.4 version signoff。

唯一合法后续链路为：

```text
Cdiag 507e1732b587c530026f19fae5d570b8845e369b
  -> direct-single-parent Cfreeze
  -> fresh formal-r9
```

Cfreeze 必须写入 review evidence path，并绑定其冻结 SHA-256
`18aff83396aa512bc9e2b6924b9cc53cdac61269e9124472e733a4394f8007d1`。不得跳过 Cfreeze、
不得复用 r27、不得把 r28 diagnostic/candidate/capsule 当作 formal 或 acceptance evidence。

Current `can_enter_cfreeze=yes`、`can_enter_formal=no-before-cfreeze`、
`can_enter_coverage_audit=no`、`can_enter_acceptance=no`。
