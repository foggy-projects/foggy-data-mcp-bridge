---
evidence_type: formal-failure-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-formal-r7
tested_commit: 439aea5e4e30b7a9fb50acc51e4898244f479df4
status: failed
decision: requires-new-diagnostic
failed_phase: child-integration
recorded_at: 2026-07-19
---

# Step 4 formal-r7 CALCULATE catalog portability failure

## Decision

`step4-coverage-20260719-formal-r7` 在真正 fresh、non-shallow、clean clone 的 Cfreeze
`439aea5e…` 上，于 Integration `sqlite-broad` fail closed。该 run 永久保持
`failed / excluded / non-reusable`；不得续跑、补写成功态 artifact、与 r24 拼接或提升为
formal authority。

唯一失败不是生产 CALCULATE 行为回归，而是测试从仓外父目录读取 parity catalog 的隐藏环境
依赖。修复路径不属于 r24 Cfreeze formalization allowlist，因此 Step 4 状态回到
`diagnostic-ready / diagnostic-pending`，必须建立新 Cdiag lineage。

## Run identity and completed boundary

- fresh clone HEAD=`439aea5e4e30b7a9fb50acc51e4898244f479df4`，唯一 parent=
  `414c8b12bff31155584e639e74987bf22df13ba9`；formalization delta PASS；
- outer=`child-integration / exit 1 / failed`，started=`2026-07-18T23:22:12Z`，
  finished=`2026-07-18T23:44:10Z`；
- Unit 完整通过：`681 execution + 55 structural / 4941 tests / F0 E0 S0`，source
  before=after=`bb18458a…`；MySQL57 fixture lifecycle/negative/cleanup 均 PASS；
- Integration `caffeine-sqlite=1 report/2 tests`、`hermetic=1/3` 已收集并通过；
- `sqlite-broad` 执行 `307 tests / F1 E0 S0`，唯一失败为
  `CalculateMvpIT.parityCatalogCasesStayExecutable`；后续 8 个 integration tests 及所有 Step 3/
  external/AddOn/aggregate 均未启动；
- success-only outer `summary.env`、exec manifest、aggregate、observation、threshold、candidate、
  final 均 absent，未发生成功态污染。

失败 XML SHA-256=
`d6eff64047d9983739df48004386052008ee357e019fc6890cdee13760463c8d`，精确错误为：

```text
CalculateMvpIT.parityCatalogCasesStayExecutable:450
  -> resolveParityCatalogPath:625
Cannot locate P1-CALCULATE parity catalog from working directory
```

## Root cause

`CalculateMvpIT` 从模块工作目录按顺序查找 `docs/...`、`../docs/...`、`../../docs/...`。
bridge Git tree 从未包含目标文件；普通工作区的第三个候选恰好解析到仓外
`/home/sa/workspace/foggy-data-mcp/docs/v1.5.1/`，使 diagnostic 被父目录偶然喂绿。

上层仓与 Python 兄弟仓的两个 tracked 副本内容一致：Git blob=
`d7879a6a0c3ac3846719911a0c3b87b3e2ad9f11`，raw SHA-256=
`f52eba376e3b2e94c2d03c8f01fcc6d9c3b98623d82938608aeacb90dd03ef60`，
size=`3407`，cases=`9`。fresh clone 没有仓外父目录，故正确暴露缺口。

## Cleanup, restore, and sensitive boundary

- runner cleanup=`containers 0 / volumes 0 / networks 0 / passed`；Unit/Integration child
  process group residue=`0`；
- wrapper=`runner_rc 1 / restore_rc 0`；MySQL57、MySQL8、PostgreSQL15、SQLServer2022 四个
  demo DB 均以原 exact container ID 恢复为 `running / healthy`；
- 独立 source 重算仍为 `4083 files / bb18458a…`，与 source-before exact；
- outer、Unit、Integration 原始 evidence 的正式五组 sensitive patterns 命中文件=`0`；官方
  success-stage `sensitive-scan.env` absent 符合 early-failure 边界。

## Persistent capsule

最小持久副本位于 `formal-r7-failure-capsule/`，manifest SHA-256=
`c7dbf3a5a2851421390792f23a97644b3f258a9537d824fc0915d53f861102ae`，
entries=`9`，payload bytes=`10303`，独立 hash/size/exact-set/sensitive scan PASS。manifest 对每个
entry 标记 byte-exact、Base64-encoded byte-exact、normalized excerpt 或 post-run observation
provenance；原始 Failsafe 文本以 RFC 4648 Base64 持久化，解码后保持 byte-exact，不依赖临时
clone 存续。

| Artifact | SHA-256 |
|---|---|
| raw `CalculateMvpIT` Failsafe text | `f7bc9e32c69b852314f3c8f084c7b8a1aaaa7c3a883143d4744e5775d13ec1c2` |
| Base64 stored payload | `486ecd2b4f1f8f59fdb8d8c586c2019dfb7b5fa7bca072c5dd1e143e8ed00190` |
| outer `run-status.env` | `d24dda69cff4249ccd10d5324fa25bcdd34553cbe722475f4f07d55d76e2f6c4` |
| integration `run-status.env` | `489b15e6dfe558cbac998d322729bcae3caf32f18e97bf01ac01372d7c65b0b9` |
| Unit `summary.env` | `ee82354a5548c04fc652b447ba00587e0946d07b8002277b25e4717c8e98c6ab` |
| outer `run-context.json` | `3af9905bd4d70e97803018de70032ae8fbde8aa8e6a4f0a49e40f9680f141a2b` |
| formalization delta | `6cff2e9d6b6b516dcbebc5ba7bd7edd0e7d10113072ab86b4db136199d5e8c63` |
| cleanup | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| failure excerpt | `b2712c23f7c52f3a432530a469fd7a6da0e7fa67dabcb9b3deefc7b455367528` |
| outer restore | `878fdca795c1d9eb1e1a46f117e0cf81ed2701231d7fe78a0675847dff2f0d2d` |

## Recovery boundary

最小修复只把 exact catalog 跟踪到主仓 `docs/v1.5.1/`，并在 Step 4 authority 的任何
测试之前验证 exact `100644` Git blob 与 raw SHA-256。生产源码、Java 测试源码、POM、
selector、14 nodes、coverage floor 与 skip policy均不改。

当前工作树 focused `CalculateMvpIT` 已为 `14/0/0/0`，目标 testcase 实际 PASS；Java test
source SHA 仍为 `21d8d817…`。这只证明最小修复，不替代提交后的 fresh clone focused proof 或
新的 all-lane diagnostic/formal。

下一条唯一合法链路为：独立 remediation quality → Cdiag commit/push → fresh clone focused+
preflight negatives → fresh all-lane diagnostic → threshold/capsule双审 → direct-child Cfreeze →
replacement formal。r24/Cfreeze/formal-r7 永久只作历史证据。
