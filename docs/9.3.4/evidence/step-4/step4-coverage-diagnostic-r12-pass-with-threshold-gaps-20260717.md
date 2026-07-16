---
evidence_type: successful-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r12
tested_commit: 05351ecab0d7fc43d12dfa307ffecf81feb41539
status: diagnostic-observed
decision: threshold-gap-remediation-required
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r12 pass with threshold gaps

## Decision

`step4-coverage-20260717-diagnostic-r12` 从 clean/pushed
`HEAD == origin/main == 05351ecab0d7fc43d12dfa307ffecf81feb41539` 启动并完整退出：
`exit_code=0`、`last_phase=completed`、`status=diagnostic-observed`。同一 authority window 内
完成全部 required lanes、source before/after seal、report inventory、23 exec / 48 sessions、
aggregate exact union、coverage observation、sensitive scan 与 cleanup。

该 run 是有效 diagnostic evidence，但不是 Step 4 exit：observation 如实记录 9/12 critical
class 低于 0.80/0.70 floor，且首次 threshold freeze 暴露 producer/consumer schema 与
`NamespaceScope.branch` structural N/A 缺口。candidate/formal/final artifacts 均未生成，
threshold 保持 `diagnostic-pending`。工具与测试修复后必须 fresh diagnostic；不得直接用 r12
创建 Cfreeze。

## Sealed result

- run window：`2026-07-16T21:39:42Z` 至 `2026-07-16T22:45:14Z`；
- source-before=source-after SHA-256=
  `17930734031e26b7237ef87cd65e136be7af5190dc561bb201441bb486bf0658`；
- required reports：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,935 execution class identities`；
- fresh class universe：`24 modules / 2,098 bytecode classes`；
- report universe：`24 groups / 2,066 reportable classes`；
- aggregate line=`54,478/76,830`（70.9071977092%）；
- aggregate branch=`25,980/44,870`（57.9006017384%）；
- critical classes=`12`，below floor=`9`，structural N/A metric=`1`；
- sensitive scan：5 patterns，extensions=`log,env,json,tsv,xml`，status=`passed`；
- cleanup：container/volume/network residue=`0/0/0`。

官方复核：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260717-diagnostic-r12
observation=e58dcdbc59e78f45d4fb5f3aedab271132bcb0405286f521f6fd38382141e0f8
```

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `37416cea59866df5e029877b9f536c0ea267b05c1e134c89adb116d6e3b4346d` |
| `summary.env` | `b4d73acdf9dff386382526cfe90b066cf1a0d2fab8ef8c04eb429ec5cff91554` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `report-inventory.json` | `1a18bd26e21950f89cd5c26fa318f51bee50115759d4ad445902cbfb23d808b1` |
| `exec-manifest.json` | `071a50bb05ad76e51887ab6ff0b9afb139c8557b4fd822add93edbab4121cb79` |
| `report/jacoco-aggregate.exec` | `9f85be958ee566f6ee21448d4c980b5db7a029363c350e0b7d47b9410e437913` |
| `report/aggregate-provenance.json` | `1ae147c7ae1147e642c2a1289b6fe99fb04647b2631597395d9b194aa55d6a61` |
| `report/report-provenance.json` | `2f7c6dbbc979edd06292f46eb0b9d318bf5d0993bd3fbc5044449164bed273db` |
| `report/jacoco-aggregate/jacoco.xml` | `82540b856a7d5bf70ea1adf89825ef5e032e75daa008dd8044c06dfe6982219b` |
| `coverage-observation.json` | `e58dcdbc59e78f45d4fb5f3aedab271132bcb0405286f521f6fd38382141e0f8` |

Summary 中 29 个 SHA 字段、23 个 exec 的 hash/size、32 个 report inventory path binding、
aggregate 的 23-input/48-session same-set 均经独立重算无 mismatch。

## Critical floor gaps

固定 r12 分母下的最小 covered-counter 缺口如下；这是 counter 数量，不是测试数量：

| Critical class | Line gap | Branch gap |
|---|---:|---:|
| `CatalogSnapshotStore` | +3 | +18 |
| `ModelBuildSingleFlight` | 0 | +5 |
| `CatalogRefreshCoordinator` | 0 | +12 |
| `RuntimeNamedDataSourceResolver` | 0 | +3 |
| `WatchServiceFileTracer` | +13 | +7 |
| `QueryFingerprintBuilder` | +8 | +3 |
| `SecurityPolicyFingerprint` | 0 | +13 |
| `CaffeineQueryCacheProvider` | 0 | +5 |
| `RedisQueryCacheProvider` | +5 | +9 |

`NamespaceScope.branch` 的 total=`0` 是生产字节码无 branch 的结构性 N/A，不得伪造成正
counter，也不得推广到其他 class/module/metric。

## Freeze discovery

对真实 r12 执行 `freeze-thresholds` 时，旧 consumer 先因 enriched critical metric keys 返回
`E_FREEZE_ZERO_COUNTER`，candidate 文件未生成。该问题登记为
`BUG-934-STEP4-THRESHOLD-FREEZE-OBSERVATION-APPLICABILITY-GAP`。修复必须保留：

- 9 个 below-floor class 继续 `E_FREEZE_FLOOR`，直到补测试；
- 未声明 zero 继续 fail closed；
- structural N/A 只接受 machine-contract 中 exact exception；
- applicability drift 在 formal 中 fail closed。

## Cleanup and external restoration

runner cleanup 证明 run-owned container/volume/network=`0/0/0`。退出后四个 repo demo DB
exact container 在 evidence window 外按原 ID 恢复，均为 `running/healthy`，监听端口 4/4：

| Service | Port | Exact container ID |
|---|---:|---|
| MySQL 5.7 demo | 13306 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` |
| MySQL 8 demo | 13308 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` |
| PostgreSQL demo | 15432 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` |
| SQL Server demo | 11433 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` |

## Next gate

- [x] r12 sealed diagnostic、sensitive scan、cleanup 与 external restoration 已核验；
- [x] 9 类 gap 与 1 个 structural N/A 已登记；
- [x] 修复 freeze schema/N/A lifecycle 并补 negatives；
- [x] 补足 9 类 coverage并运行 focused gates；fresh authority 重算仍 pending；
- [ ] commit/push 后执行 fresh all-lane diagnostic；
- [ ] fresh diagnostic candidate 验证通过后，才允许 direct-child Cfreeze 与 fresh formal。

remediation focused result：XML=`118/118`、contract=`27/27`、frozen replay policy=
`12/12`、overlay=`12/12`，九类相关测试=`136/F0E0S0`；预提交审查发现的 bool/int 与
`gap:false` numeric alias 已 fail closed。上述结果不改变 r12 的 immutable observation，
也不替代新的 all-lane run。

当前 `can_enter_coverage_audit=no`；Step 4、Step 5、formal、audit 与 acceptance 均未开启。
