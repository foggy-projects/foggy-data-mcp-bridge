---
evidence_type: successful-diagnostic-capsule-refusal
version: 9.3.4
step: 4
run_id: step4-coverage-20260718-diagnostic-r20
tested_commit: 0dee7f81190441c84b34fdcf8acf25333e01f80d
status: diagnostic-observed
decision: capsule-ineligible-threshold-freeze-not-authorized
candidate_status: absent
capsule_status: absent
recorded_at: 2026-07-18
---

# Step 4 coverage diagnostic r20 capsule refusal

## Decision

`step4-coverage-20260718-diagnostic-r20` 从 clean、pushed diagnostic HEAD
`0dee7f81190441c84b34fdcf8acf25333e01f80d` 启动并完整退出。runner 结果是有效的
`diagnostic-observed / completed / exit 0`，公开 `validate-diagnostic-run` 复算通过；全部 required
lane、coverage observation、model/sensitive gate 与 run-owned cleanup 都通过。

runner 退出后的 portable capsule publication 被工具 fail closed：r20 的 negative 工具已在负例结束后
清理 `symlink.exec` 与 `symlink.xml`，实际 capsule closure 的 symlink 集为空；tested commit 中的 capsule
工具仍要求这两条旧负例 symlink 存在并声明为 omission，因此 `build` 返回
`E_CLOSURE: diagnostic negative fixture symlink set differs`。archive 与 manifest 均未发布。

该冲突不能通过事后补建 symlink 或使用未提交工具打包来绕过，否则会把人工修改后的状态冒充
`exact-retained-diagnostic-run-bytes`。曾由 r20 observation 生成的 transient threshold candidate 已撤销，
canonical candidate、capsule archive 和 capsule manifest 均保持 absent。r20 的治理结论固定为：

```text
diagnostic-observed / capsule-ineligible / threshold-freeze-not-authorized
```

r20 不得进入 threshold review、Cfreeze、fresh formal、coverage audit 或 acceptance。替代链必须是新的
diagnostic-ready commit、clean/pushed identity 和完整 fresh r21。

## Sealed runner result

- run window：`2026-07-18T13:53:36Z` 至 `2026-07-18T15:03:08Z`；
- source-before=source-after SHA-256=
  `c4fb10cb850f625c37cb0dd11f76ed1a0f8d14855d421e8be4df8068cc93439e`；
- required：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,934 execution class identities`；
- fresh class universe：`24 modules / 2,098 bytecode classes`；
- aggregate instruction=`252,725/352,456`、line=`54,624/76,830`、branch=
  `26,111/44,870`、method=`9,068/12,701`、class=`1,503/1,716`、complexity=
  `17,658/35,571`；
- critical classes=`12`、applicable metrics=`23`、structural N/A=`1`、below-floor=`0`；
- 唯一 N/A：`NamespaceScope / foggy-dataset-model / branch`；
- model external gate=`passed`、sensitive scan=`passed`、cleanup residue=`0/0/0`；
- diagnostic acceptance artifact=`not-generated`。

公开复算：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260718-diagnostic-r20
observation=0445c45f3090c49b70912fa3a44a4306149518ad2cd012d1befc1ce0ac29e4e9
```

## Immutable artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `792767fdc4c0ff825072b4eaa60dfeea455bbc118c6161fafef0be71b2f1a474` |
| `run.log` | `eb50fdb8900c0c457697f246e3f7df3de4850cf90769a90cf763171bba249ae1` |
| `run-context.json` | `fd602e43c33691e46986e232fcd0ad679132f2e89119714580a31bf827f0f511` |
| `summary.env` | `2f9556f2f3d983bcd40ca0721f8ad1d18b3640e9f93cd11f3494aa12d77d253c` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `c0c2aa225240eff8b9ae65eda90b76c22f8790bc55ee3a37f296fb386485a6ae` |
| `child-lifecycle.json` | `ed39f46e53306f040422e6f5eb5f01faf344cf14a627aa1a66366a158890e7c9` |
| `model-gate.env` | `2588c5c66fd10d560a679232a2695334e2e0e5ea6d96d103abab5ee8326f0807` |
| `class-universe.json` | `7c8a676796ef237245a895d0970677652ad366d336eb5b7d72b9bfae382cbc2d` |
| `report-inventory.json` | `b5886187b43bcba0fb2cd1aaa1c6e5655b487d0f70aac7a39d3adacb0eb8abaa` |
| `exec-manifest.json` | `306ed7de7f39bacc7b4251b4785c62ed3f7dac0c5d18ace90fe17ba8107c666a` |
| `report/jacoco-aggregate.exec` | `34f87120a83d60e218c58153cdcb1a536b9fb5eb6f1927ab7ddf09f6e232e1e7` |
| `report/aggregate-provenance.json` | `341162078ec2c5d19e9f4c9ed4b7789005d54c8cfc18ef4ff9722e95a0b636f2` |
| `report/report-provenance.json` | `7553d5bd91a8ef1e32f329711fd2fd9205afa1cbb3f979d3d6739faaee353abc` |
| `report/jacoco-aggregate/jacoco.xml` | `2b939f07aec53cf9fca41d264609a4e7c21438aeae607250061d047602681a0d` |
| `coverage-observation.json` | `0445c45f3090c49b70912fa3a44a4306149518ad2cd012d1befc1ce0ac29e4e9` |

## Publication refusal and replacement contract

- r20 canonical threshold candidate：absent；
- r20 portable capsule archive：absent；
- r20 portable capsule manifest：absent；
- tested capsule tool SHA-256=
  `20977a1937c66b997bea4271a530ff73b3a22304d62029b188f415d393f48a30`；
- observed closure before repair：`6,638` entries、actual symlink set=`[]`；
- replacement semantic：capsule closure 中任何 symlink 都必须 `E_SYMLINK`，manifest 保留 schema v1
  但 `omitted_negative_fixture_symlinks` 必须精确为 `[]`；
- replacement regression：capsule self-test=`8/8`，外层 coverage XML negative=`124/124`；
- replacement authority：new Cdiag commit → push/clean → full r21；不得复用 r20 run ID 或 bytes。

runner-owned cleanup 后，外层已恢复四个开发数据库容器；MySQL 5.7、MySQL 8、PostgreSQL 与
SQL Server 均为 `running / healthy`，冻结端口 `13306/13308/15432/11433` 已恢复监听。该恢复属于
evidence window 外的运维复查，不混入 runner-owned artifact。
