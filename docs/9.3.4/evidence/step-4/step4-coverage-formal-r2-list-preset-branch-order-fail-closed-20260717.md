# Step 4 formal-r2 ListPreset branch-order fail-closed evidence

- recorded_at: 2026-07-17
- run_id: `step4-coverage-20260717-formal-r2`
- tested_commit: `1901a10138bac06a09b875c907b7aea6e2789b04`
- mode: `formal`
- outcome: `failed / exit 1 / last_phase=formal-coverage-gate`
- error: `E_FORMAL_LOW: formal aggregate branch is below the reviewed threshold`
- publication: `summary.env` and `coverage-gate.json` absent
- cleanup: `container_residue=0 / volume_residue=0 / network_residue=0`
- sensitive_scan: `passed`
- source seal: before=after
  `8f2a5133f81361fae769ef6dd8537884941fd0645f2b6f3f99822829f4847c13`

## All-lane result before the gate

formal-r2 已完成并通过全部执行、库存和 provenance lane：Unit=
`681+55/4941/F0E0S0`，Integration=`47+4/320/F0E0S0`，Step 3 required=
`45/446/F0E0S0`，Addon=`2/6`，database=`29/370/F0E0S0`，external=
`16/76/F0E0S0`，required inventory=`773+59/5707/F0E0S0`，exec/session=`23/48`，
class universe=`24/2098`，reportable class=`2066`。source before/after SHA 相等，run 没有
source drift。

## Exact counter delta

| scope | r14 diagnostic / confirmed | formal-r2 | delta |
|---|---:|---:|---:|
| aggregate line | 54622/76830 | 54622/76830 | 0 |
| aggregate branch | 26106/44870 | 26105/44870 | -1 |
| `FileSystemListPresetStore` line | 69/88 | 69/88 | 0 |
| `FileSystemListPresetStore` branch | 18/26 | 17/26 | -1 |
| `lambda$findById$2` branch | 4/4 | 3/4 | -1 |

全部 12 个 critical class exact 命中 reviewed canonical；23 个适用 critical metric delta=
`0`，below-floor=`0`，`NamespaceScope.branch=0/0/null` 仍为唯一 structural N/A。
WatchService 保持 `204/244 line, 99/128 branch`。2066 个 reportable class 的 line/branch
对比只有 `com.foggyframework.dataviewer.service.listpreset.FileSystemListPresetStore` 变化，
对应 source line 74：

```java
attrs.isRegularFile() && path.getFileName().toString().equals(fileName)
```

## Exec and report provenance

差异只存在于 Unit/Surefire `jacoco-ut.exec`：

| item | r14 | formal-r2 |
|---|---:|---:|
| class ID | `d1bd017e92baa090` | `d1bd017e92baa090` |
| probe count | 113 | 113 |
| covered probes | 74 | 73 |
| packed bitmap | `__9vQH4Dfs89f8ZDxP8B` | `__9vQH4Dfs89f8ZDxPsB` |

唯一 formal-r2 缺失 probe=`106`；其余 22 个 exec 均不包含该 class。两侧结构均为
23 exec / 48 unique sessions，manifest 中没有 missing、SHA mismatch 或 size mismatch。

报告库存两侧均为 `773 positive + 59 structural / 5707 testcase / F0E0S0`；834 份 authority
TEST XML 的 suite totals 与 testcase `classname/name/outcome` 完全一致，权威路径树与 testcase
semantic-tree SHA 也一致。WatchService=`11/11`、PostgreSQL Pivot targeted=`55/55`、parity=
`23/23`、cascade=`7/7` 均无差异。因此本次不是测试未执行、报告/exec 丢失、WatchService
shutdown race 或 Pivot 覆盖变化。

## Cause and decision

preset 使用 UUID 文件名，`Files.find` 未定义目录遍历顺序，`findFirst()` 在目标文件先出现时
提前短路，filename-false branch 不执行；非目标 regular file 先出现时则执行该 branch。
formal-r2 正确暴露了 exact aggregate threshold 仍依赖目录遍历顺序。

Decision：formal-r2 保持 immutable failed evidence；不复用、不修补、不盲重跑，也不下调
threshold。既有 FileStore testcase 必须加入不存在 ID 查询，确定性穷尽已有 regular files，
然后完整重走 Cdiag -> fresh diagnostic -> review -> direct-child Cfreeze -> fresh formal。

## Deterministic focused remediation result

修复只在既有 `shouldIsolatePresetByUserAndBusinessKey` 中增加：

```java
assertTrue(service.get("u1", "missing").isEmpty());
```

没有新增 `@Test` 或修改生产代码。5 次独立 Maven/JVM/JaCoCo fork 的目标 class ID 均为
`d1bd017e92baa090`，probe count=`113`，covered=`73`，packed bitmap 均为
`__9vQH4Dfs89f8ZDRP8B`，probe 106 为 `5/5` 命中，bitmap unique=`1`。focused suite 保持
`11/F0E0S0`；完整 Data Viewer 模块为 `104/F0E0S0`，合并目标类恢复 r14 exact
`74/113 / __9vQH4Dfs89f8ZDxP8B`。

focused raw exec SHA-256：

| fork | exec SHA-256 |
|---:|---|
| 1 | `3c91c80002371c661f455c4344e3a0ff7b2436f8a8d2a2bd00096792d0e685f6` |
| 2 | `bcfb8b7d205eea29b1f4b903f04a1fba91dc197b7765f6703cb92801347aa916` |
| 3 | `af3e112c613dec896698875d98b5d8e8b15a9f17ed3179ec4bc1764da485adf9` |
| 4 | `5f9d6581acc4c2fc462bf177eede3db2d8669effdfa87c4bb6826ae57b93c297` |
| 5 | `88f5c0fd8423b9d50e9a637895be21e7f890bc46df3b3769078c1a8eb051fbca` |

完整模块 exec=`target/v934-list-preset-module.exec`，SHA-256=
`6d0810a389fc7c2f6a9dbe0c981a24647312f967a0f3a09046448cde2ee68e28`。这些 focused/module
结果只关闭修复方向，不替代 fresh all-lane authority。

## Immutable artifact hashes

| artifact | SHA-256 |
|---|---|
| `run-status.env` | `1ceeebfbc4d5766402b8cbd5848a5de1c892a0f345e8d43a7e65f1dfc53c6f2d` |
| `coverage-observation.json` | `4e2b60de63402f14df762b80bfa9c8002ddbd0ebb672f654909cca9351e35cba` |
| aggregate `jacoco.xml` | `634230e70f8b7d4d6fdbf9ee57532783b0aa8ec86d856781849e930cdf1a8227` |
| aggregate exec | `af3b49ce347a9da51c5541356c0da7648190ff6745dd9ab5b2f55281be1e4a52` |
| Unit exec | `b95b405ddbc2e8ee327272c7cda32c496d87913ca8fc32c801c83ef3a926e900` |
| `exec-manifest.json` | `b1f70f54aac2dd2ec060918ccb85e4d45b40cf8a9ecadcac19c832aaa79bb51e` |
| `report-inventory.json` | `1ef37b75303ebda93eec7ed6ea14105c287cfc8d0f6debfc389dddde21fb0615` |
| `class-universe.json` | `56ede13b60957e8b6efe48f2a77650a50cf064993f803aa789c90f3ae2e96781` |
| `formalization-delta.json` | `b5dccf546daac08fa6c0594469f3acaad2c610eeeb396a5e0e3ed4bec2c68aa2` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |

## Evidence paths

- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r2/run-status.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r2/run.log`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r2/cleanup.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r2/sensitive-scan.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r2/coverage-observation.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r2/report/jacoco-aggregate/jacoco.xml`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r2/exec/jacoco-ut.exec`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r14/coverage-observation.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r14/report/jacoco-aggregate/jacoco.xml`
- `target/v934-list-preset-focused-r{1..5}.exec`
- `target/v934-list-preset-module.exec`
