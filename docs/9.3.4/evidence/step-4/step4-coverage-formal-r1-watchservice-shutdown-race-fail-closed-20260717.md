# Step 4 formal-r1 WatchService shutdown hook race fail-closed evidence

- recorded_at: 2026-07-17
- run_id: `step4-coverage-20260717-formal-r1`
- tested_commit: `86d505810524383da6211bcc2a7965e9a4afb34e`
- mode: `formal`
- outcome: `failed / exit 1 / last_phase=formal-coverage-gate`
- error: `E_FORMAL_LOW: formal aggregate line is below the reviewed threshold`
- publication: `summary.env`, `coverage-gate.json`, `candidate-manifest.json` and
  `final-manifest.json` absent
- cleanup: `container_residue=0 / volume_residue=0 / network_residue=0`
- sensitive_scan: `passed`

## All-lane result before the gate

formal-r1 已完成并通过全部执行/库存/provenance lane：unit=`681+55/4941/F0E0S0`，
integration=`47+4/320/F0E0S0`，Step 3 required=`45/446/F0E0S0`，Addon=`2/6`，
database=`29/370/F0E0S0`，external=`16/76/F0E0S0`，required inventory=
`773+59/5707/F0E0S0`，exec=`23/48`，class universe=`24/2098`。source before/after SHA
相等，run 没有 source drift。

## Exact counter delta

| scope | r13 diagnostic / confirmed | formal-r1 | delta |
|---|---:|---:|---:|
| aggregate line | 54624/76830 | 54615/76830 | -9 |
| aggregate branch | 26109/44870 | 26106/44870 | -3 |
| `WatchServiceFileTracer` line | 204/244 | 195/244 | -9 |
| `WatchServiceFileTracer` branch | 98/128 | 95/128 | -3 |

24 个 module group / 全部 class XML 的 diff 只有
`com.foggyframework.core.utils.file.WatchServiceFileTracer` 变化；其余 11 个 critical class
counter 全部相同，`NamespaceScope.branch` 仍为唯一 `0/0/null` N/A。formal-r1 的 tracer
line=`195/244=79.918%`，同时违反 exact reviewed minimum 和 80% candidate floor。

源码级缺失为：

- `processEvents()`：line 329-330，line 311 少一个 branch outcome；
- `shutdown()`：line 509、514、516、518、521、524、525，line 509/516 各少一个 branch。

23 exec probe bitmap 对比中只有 `jacoco-ut.exec` 不同：245 probes 中 r13 covered=183、
formal-r1 covered=176，r13-only probes=`[103,110,223,226,230,231,234]`；22 个 integration
exec 对目标类逐字节相同。

## Cause and decision

`shutdown()` 的唯一显式入口是 JVM shutdown hook；JaCoCo dump-on-exit 也是 shutdown hook。
两个 hook 的执行顺序不受保证，因此 r13 的 9/3 counters 是偶发退出时序覆盖。formal-r1
正确暴露了冻结阈值中的不确定性。

Decision：formal-r1 保持 immutable failed evidence；不复用、不修补、不以同一 Cfreeze
盲重跑 formal-r2。修复必须进入新 diagnostic generation，通过既有 testcase 内 isolated
tracer 的显式 shutdown，在 JVM exit 前稳定命中目标 probes，然后重新执行
diagnostic -> review -> Cfreeze -> formal。

## Deterministic focused remediation result

修复放入既有 `testWatchServiceAvailable`，没有新增 `@Test`。连续 5 次独立 Maven/JVM fork
均以 JaCoCo 0.8.12 生成独立 exec；目标类每次为 `177/245 probes`，formal-r1 缺失的
`[103,110,223,226,230,231,234]` 每次全部命中，五份 packed bitmap 完全相同
（unique=`1`）。Surefire XML 保持 `11 tests / failures=0 / errors=0 / skipped=0`。

隔离实例显式 shutdown 后 `isAvailable=false`，全局 singleton 仍为 available。该 focused
结果关闭随机 hook 顺序的最小回归，但不替代 fresh all-lane diagnostic/formal authority。

复现 focused exec 的命令模板（`r1..r5` 分别使用唯一 destFile/sessionId）：

```bash
mvn -q -pl foggy-core -P v934-coverage \
  -Dtest=com.foggyframework.core.utils.file.WatchServiceFileTracerTest \
  -DskipITs=true \
  -Djacoco.ut.destFile="$PWD/target/v934-watch-shutdown-focused-r1.exec" \
  -Dv934.coverage.sessionId=v934-watch-shutdown-focused-r1 test

javac -encoding UTF-8 \
  -cp "$HOME/.m2/repository/org/jacoco/org.jacoco.core/0.8.12/org.jacoco.core-0.8.12.jar" \
  -d target/v934-watch-inspector scripts/v934/step4/JaCoCoExecInspector.java
java -cp "target/v934-watch-inspector:$HOME/.m2/repository/org/jacoco/org.jacoco.core/0.8.12/org.jacoco.core-0.8.12.jar" \
  JaCoCoExecInspector "$PWD/target/v934-watch-shutdown-focused-r1.exec"
```

Raw focused exec SHA-256：

| fork | exec SHA-256 |
|---:|---|
| 1 | `f998c0cf3d822cfe531ccd336a8e37e3ff11b65a20f4992e45f5fa000a185e48` |
| 2 | `1002377330711a06c3ef805365933a8eae0b7442214ad59b76cad16f67830473` |
| 3 | `b1cd4e05a2a1070e5bb0c45ffd052657ef6425e7b8ca05fcb40959e85f6c8a3c` |
| 4 | `274450f65102edda8569020848c247f9ed36e1f79ac233b8151493e84cea0ee8` |
| 5 | `d28ef4ad0b2fd61c7f8283a91cb481d21a17eeec8352ab134b1f614866148354` |

五次目标 class ID 均为 `cef1c03b00a13ddd`，packed bitmap 均为
`__iQiM9wj_-9--8__PEA_9_52__37___z_Pv_sQcHg`，其文本 SHA-256=
`650066ea7da31b938530faa2db4e5fdddd416cea26774bfd8c44b4430817b3b9`。最后一次 Surefire XML=
`foggy-core/target/surefire-reports/TEST-com.foggyframework.core.utils.file.WatchServiceFileTracerTest.xml`，
SHA-256=`d7c20e3b5020e8ab8dbe7c5cb86ce1926cc106b0df4b5b9dc2daa27030dda4f5`。

## Evidence paths

- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r1/run-status.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r1/run.log`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r1/cleanup.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r1/sensitive-scan.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r1/coverage-observation.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r1/report/jacoco-aggregate/jacoco.xml`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r1/exec/jacoco-ut.exec`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r13/report/jacoco-aggregate/jacoco.xml`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r13/exec/jacoco-ut.exec`
- `target/v934-watch-shutdown-focused-r{1..5}.exec`
- `target/v934-watch-shutdown-focused-r{1..5}.tsv`
