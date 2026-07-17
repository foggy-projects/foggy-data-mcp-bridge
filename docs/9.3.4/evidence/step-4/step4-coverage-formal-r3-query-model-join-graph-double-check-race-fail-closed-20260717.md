# Step 4 formal-r3 QueryModel JoinGraph double-check race fail-closed evidence

- recorded_at: 2026-07-17
- run_id: `step4-coverage-20260717-formal-r3`
- tested_commit / Cfreeze: `a63c82c53ebaad1a1c22d78647fbda70b4bd6594`
- mode: `formal`
- outcome: `failed / exit 1 / last_phase=formal-coverage-gate`
- error: `E_FORMAL_LOW: formal aggregate branch is below the reviewed threshold`
- remediation_status: `in-progress`
- publication: success-only `summary.env`, `coverage-gate.json`, `candidate-manifest.json` and
  `final-manifest.json` are absent
- cleanup: `container_residue=0 / volume_residue=0 / network_residue=0`
- sensitive_scan: `passed`
- source seal: before=after
  `7054bfc200f78ce2d5412aaf7016252d02a98c69960f3660fdb58196df93336c`

## Fail-closed conclusion

formal-r3 完成了全部执行、库存、provenance、model gate、cleanup 与 sensitive-scan lane，随后只在
reviewed aggregate branch exact threshold 上少一个 covered outcome，因此 runner 正确 fail closed。
本次结果不得复用为正式绿色，不得补写 success-only artifact，也不得通过下调 threshold、扩大
exclusion 或删除 critical metric 放行。Step 4 formal acceptance 在确定性回归、fresh diagnostic、review、
direct-child Cfreeze 与 fresh formal 全部通过前保持关闭。

## Complete execution evidence before the gate

| item | formal-r3 result |
|---|---:|
| required inventory | `773 positive + 59 structural / 5707 testcase / F0E0S0` |
| Addon companion | `2 reports / 6 testcase` |
| exec / unique session / execution classes | `23 / 48 / 16946` |
| fresh production class universe | `24 modules / 2098 classes` |
| critical classes | `12/12 present; all applicable exact thresholds passed` |
| critical floor | `below_floor=0; NamespaceScope.branch remains the sole structural N/A` |
| aggregate line | `54624/76830`, exact reviewed value |
| aggregate branch | `26110/44870`, reviewed value `26111/44870`, delta `-1` |

模型外部门禁通过，23 份 exec 均通过 SHA、size、session 与 class-universe 校验。执行结果、报告库存和
critical rows 没有失败、错误或跳过；这不是测试漏跑、报告拼接、exec 丢失或 class universe 漂移。

## Exact localization

r16 reviewed diagnostic 与 formal-r3 的 reportable class counter 逐项比较后，只有以下生产类变化：

`com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport`

| scope | r16 diagnostic / reviewed | formal-r3 | delta |
|---|---:|---:|---:|
| class line | `347 covered / 89 missed` | `347 covered / 89 missed` | `0` |
| class branch | `196 covered / 108 missed` | `195 covered / 109 missed` | `-1 covered` |
| `getMergedJoinGraph` line | `6 covered / 0 missed` | `6 covered / 0 missed` | `0` |
| `getMergedJoinGraph` branch | `4 covered / 0 missed` | `3 covered / 1 missed` | `-1 covered` |
| source line 316 inner condition | `2 covered / 0 missed` | `1 covered / 1 missed` | `-1 covered` |

变化只位于 `getMergedJoinGraph()` 的内层 double-check condition：

```java
if (mergedJoinGraph == null) {
    synchronized (this) {
        if (mergedJoinGraph == null) { // QueryModelSupport.java:316
            mergedJoinGraph = buildMergedJoinGraph();
        }
    }
}
```

r16 中并发调用恰好让一个等待线程在首个线程初始化完成后进入 monitor，覆盖了内层
`mergedJoinGraph != null` outcome；formal-r3 的实际调度没有形成该交错。生产 DCL 实现、class tree、
source seal 与测试总量均未变化。因此根因是现有测试把一个 exact branch outcome 交给 incidental
concurrent scheduling，而不是生产行为回归。

## Required deterministic remediation

最小修复是增加受控并发 unit regression：在一个全新的 `QueryModelSupport` 实例上，让第一个调用者
进入 synchronized 初始化并阻塞在受控 `TableModel#getJoinGraph` build 窗口；随后启动第二调用者，使用
`ThreadMXBean` 确认其已经通过外层 null check、正以第一调用者为 owner 阻塞在同一实例 monitor。释放
build 后，第一个调用者完成初始化，第二调用者随后进入并确定性观察内层 non-null。测试必须同时断言
两个调用者获得同一 `JoinGraph`、build count=`1`，并使用有界等待、失败清理和 executor termination
断言。

修复只允许落在测试代码/既有确定性并发测试支撑中；不修改生产 DCL，不降低 aggregate 或 critical
threshold，不扩大 coverage exclusion。focused 多 fork 只能验证修复方向，随后仍须完整重走
Cdiag -> fresh diagnostic -> threshold review -> direct-child Cfreeze -> fresh formal。BUG 与 formal gate
在 regression、diagnostic 和 formal authority 全部通过前保持 `in-progress`。

## Deterministic remediation result before Cdiag

最终回归复用既有
`RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`；
没有新增或改名 `@Test`，该 class 仍为 `5` 个 testcase。选择此宿主是因为
`foggy-dataset-model/src/test/java` 属于冻结数据库矩阵保护树；候选 `JoinGraphTest` 改动已完全撤销，
successor protected overlay 在最终实现前后均 PASS。

- exact targeted testcase：`1/F0E0S0`；
- 5 个独立 Maven/JVM/JaCoCo fork：`5/5` PASS；
- `QueryModelSupport` class id=`d242dafe9de31249`，每次均为 `34/629 probes`；
- packed bitmap 五次 exact 相同，unique=`1`：
  `4P-_7xsAAIADAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEA`；
- `foggy-runtime-api` full module：`128/F0E0S0`；
- 最后一份 focused Surefire XML：`1/F0E0S0`，SHA-256=
  `58b8cdddc0f3f1f739bd694b997874984c381a95e03496adaf1798659c8085fa`。

五份 focused exec SHA-256 依次为：

1. `a4a9d4daabbb59670bd6e49fdb5b90e4fc8c0313745b6c59a43ab0844eb2c3a8`
2. `75ede280e74122387aa677843119b9c8b5f94b855a0eb2e2db52b09f19ae34bc`
3. `ea4860cc1743c7cdfc18f87c6b0a06fe236eb8f7bcccac812b01c1cd21dd4a1c`
4. `d441b173b558224908ae26e8ff22a83f10ca15bd4cea3e5c3e35e11ae95df9af`
5. `06604255911291d5329a60d34eabd8a01169f500ba2c62ad3ec1bd601ba85e88`

这些 focused/module 结果只关闭修复方向，不替代新的 clean/pushed Cdiag 上的 all-lane diagnostic 或
后续 direct-child Cfreeze/fresh formal authority。

## Environment restoration

runner 外层 EXIT trap 已恢复开跑前临时停止的四个 demo database container，restore result 为 success。
记录时四个原 container ID 均为 `running/healthy`，且对应 host port 均在监听：

| database | container ID | port |
|---|---|---:|
| MySQL 5.7 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` | `13306` |
| MySQL 8 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` | `13308` |
| PostgreSQL 15 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` | `15432` |
| SQL Server 2022 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` | `11433` |

## Immutable artifact hashes

| artifact | SHA-256 |
|---|---|
| `run-status.env` | `82e781e030d7796b4a61a9d3097df83c81a2b5bd202c64072ca1679a12e4585d` |
| `run.log` | `e837ca050f15933e2f6d2ee6663d86fb82c02e1a180c95115864b4d2e7bf516e` |
| `run-context.json` | `b7a44d2871dab7bb3ed27748b622ae68a409cf254cf03e3b65f175605a92e348` |
| `toolchain-receipt.json` | `4d59cd607375e2402cdfaad6d505389b3df445c2f8e7837f8d7056212af4f229` |
| `coverage-observation.json` | `d58eee8ddfcbb5ea758374a241ea6d406a3a09b6e2213f70f48bd32ccf9e9526` |
| aggregate `jacoco.xml` | `ebe88752936b9674ee31dd99023fcc7937449dcdf644d98104c6d8b83cd4e1d4` |
| aggregate exec | `02c78de8e4ee08462774d7f0616f04f07414a50c73e9e23dd83d916657634b15` |
| Unit exec | `daf2cd06685bca63d09c7145cd4036568c72fa8627f6b6758d87d1b48ef6ae55` |
| `exec-manifest.json` | `5963c2ec8507c60023ec54790afd7b1af7ecd31fe3b234331f6b50e977fa765a` |
| `report-inventory.json` | `4f4b4ee1c0cff6b94a9b5c7bafdff1af26617dac3a60603a8bbb840ffb529f7f` |
| `class-universe.json` | `4160e9943a6f706893d819887bfb30de54f8cf9f32a945c539e977f4053e6a56` |
| `formalization-delta.json` | `d0a82d4d1c96cf2f518440c8e1099df03e7eb37577b7e0ace4d356c4a1e9aba6` |
| `model-gate.env` | `9a88d3b1953b23893aac58caeca6de3163e8e071cd83cfdc8e529aa1c4fa407d` |
| `child-lifecycle.json` | `1d742283dc966ded31d42b32abbdc529fe2742c728456aee7f8aa4a279b3256b` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `source-before.tsv` | `7054bfc200f78ce2d5412aaf7016252d02a98c69960f3660fdb58196df93336c` |
| `source-after.tsv` | `7054bfc200f78ce2d5412aaf7016252d02a98c69960f3660fdb58196df93336c` |

## Evidence paths

- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/run-status.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/run.log`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/coverage-observation.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/report/jacoco-aggregate/jacoco.xml`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/report/jacoco-aggregate.exec`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/exec-manifest.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/report-inventory.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r3/class-universe.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r16/coverage-observation.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r16/report/jacoco-aggregate/jacoco.xml`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeNamedDataSourceResolverBindingTest.java`
