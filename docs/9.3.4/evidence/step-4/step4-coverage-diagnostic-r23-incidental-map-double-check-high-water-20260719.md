---
evidence_type: successful-diagnostic-not-freezable
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r23
tested_commit: 9b0bd281118845eeba0bd56aa15ab8e56aa8b585
status: diagnostic-observed
decision: threshold-candidate-not-authorized
candidate_status: absent
capsule_status: absent
replacement_run_id: step4-coverage-20260719-diagnostic-r24
recorded_at: 2026-07-19
---

# Step 4 diagnostic-r23 incidental MapBeanInfo double-check high-water

## Decision

`step4-coverage-20260719-diagnostic-r23` 是完整且有效的 diagnostic PASS：runner=
`completed / exit 0 / diagnostic-observed`，public `validate-diagnostic-run` PASS，source
before/after exact，required/model/sensitive/cleanup gates 全部通过，四个外部 demo database 也按
原 container identity 恢复为 `running/healthy`。

Run window=`2026-07-18T19:59:20Z..2026-07-18T21:08:00Z`；public result=
`DIAGNOSTIC VALID / observation=27b7aa62db1e8953dc5f25005ad23caaea77fe20b99f7ca5a3fa2bd60600453a`。

但是 r23 不授权 threshold candidate。它的 aggregate branch=`26,112/44,870`，比
r20/r21/r22 的稳定 reviewed high-water 多一个 covered outcome；complexity 同步从
`17,658/35,571` 增为 `17,659/35,571`。差异来自一个未被确定性测试控制的并发调度 outcome。
冻结器会把 diagnostic observed branch 原样设为 formal minimum，直接冻结 r23 会把后续 formal
变成调度概率门。candidate 因此保持 absent，不得以重跑碰运气、降低 denominator、扩大 exclusion
或手工改 candidate 放行。

## Complete diagnostic result

| Item | r23 result |
|---|---:|
| required inventory | `773 positive + 59 structural / 5,707 testcase / F0E0S0` |
| Addon companion | `2 reports / 6 testcase / F0E0S0` |
| Unit | `681 positive + 55 structural / 4,941 testcase / F0E0S0` |
| Integration | `47 positive + 4 structural / 320 testcase / F0E0S0` |
| Step 3 required | `45 reports / 446 testcase / F0E0S0` |
| exec / session / execution classes | `23 / 48 / 16,962` |
| production universe | `24 modules / 2,098 classes` |
| critical | `12 classes / 23 applicable / 1 structural N/A / below-floor 0` |
| aggregate | instruction `252,725/352,456`; line `54,624/76,830`; branch `26,112/44,870`; method `9,068/12,701`; class `1,503/1,716`; complexity `17,659/35,571` |
| cleanup / sensitive | `0/0/0 / passed` |

唯一 structural N/A 仍为 `NamespaceScope.branch=0/0`。workspace class tree SHA-256=
`ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b`。

## Exact counter and raw-exec localization

r22/r23 aggregate XML 逐 group/class/method/source 比较只有一处变化：

`com.foggyframework.core.utils.beanhelper.BeanInfoHelper$MapBeanInfoHelper#getBeanProperty`

| Scope | r22 | r23 |
|---|---:|---:|
| method branch | `3/4 covered` | `4/4 covered` |
| method complexity | `2/3 covered` | `3/3 covered` |
| `BeanInfoHelper.java:245` | `1 missed / 1 covered` | `0 missed / 2 covered` |

该行是外层 miss 后、进入 `synchronized (this)` 的第二次 `bp == null` 检查。只有另一个线程在等待者
完成外层 miss 后先填充 `mapBp`，等待者随后取得 monitor，才会命中 inner false outcome。r22→r23
没有 Java/POM 变化；production blob=`e3544f270c680b013d3104fd795e91221cc1ad0f`，当时的 test
blob=`8a83e020c0a1f77641656da1c252793ef29564f0` 在两次 run 间均相同。

raw exec 复核证明只有 `jacoco-ut.exec` 的目标类发生变化：class id=
`a6629aa379049ec7`、11 probes，r22=`9/11 / bitmap 7wU`，r23=`10/11 / bitmap _wU`，新增的是
probe index `4`；其余 22 份 exec 对该类完全相同。19 个 retained diagnostic 中仅 r1/r12/r23
达到 `10/11`，15 次为 `9/11`，说明它是真实覆盖但不是可冻结的稳定 oracle。

## Deterministic remediation

最小修复只扩展既有
`BeanInfoHelperTest#getClassHelper`，不增加或改名 `@Test`，不修改 production：

1. main thread 在启动 lookup thread 前持有全新 `MapBeanInfoHelper` monitor；
2. lookup 完成第一次 miss 后必然在唯一 monitor-enter 处进入 `BLOCKED`；
3. main thread 在同一 monitor 内安装 expected property，再释放 monitor；
4. monitor happens-before 保证 lookup 的 inner read 观察到该 property；
5. 同一测试还显式覆盖首次创建与 cached fast path，并用 5 秒有界等待、join 与存活断言收口；
6. 上述交错在一个 JUnit node 内执行 100 次，test cardinality 保持不变。

五个独立 Maven/JVM/JaCoCo fork 均为 `1/F0E0S0`，目标 class id 均为
`a6629aa379049ec7`，probe=`10/11 / bitmap _wU`，`5/5` exact identical；目标方法
branch=`4/4`、complexity=`3/3`。完整 `foggy-core` module=`97/F0E0S0`。独立代码复核结论=
`APPROVE / B0 H0 M0 L0`。

这些 focused 结果只授权形成新 Cdiag 和 fresh diagnostic-r24，不替代 all-lane authority。

## Immutable artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `81c525ab8c53fd744edec5464e7306a012b56e71d798edeb0d689517456e4b47` |
| `run.log` | `9770ebfd1c68a118f1fd883b365e6c383ee1cefd0ed9793bddc0611dd37b1681` |
| `run-context.json` | `1e08537e165c522f2dbd7adfc88917dddb8aae69b5775144356c1e4adefff448` |
| `summary.env` | `2c85e8f8ec9f6b950eb3c0f49311ce90cbb77debc1fe58a7964089679c52fcc0` |
| `coverage-observation.json` | `27b7aa62db1e8953dc5f25005ad23caaea77fe20b99f7ca5a3fa2bd60600453a` |
| aggregate `jacoco.xml` | `4a13999b6c6fca15824a525c896e5c1f21c6eb4a4dbbe0ad94fafa4e0693541c` |
| aggregate exec | `015d7df4882fa3c55b018e53033b82090bd6d843d9d82d962c8713ec1f433312` |
| Unit exec | `0dbcd38fa920e63496c9bd923d3a3a29bce800d94a4c48d78a9ba3cff0406f24` |
| `exec-manifest.json` | `1d45834d8bfc272a835bce38dedf0f97f5322713fef4cbdd955a1b3753b18864` |
| `report-inventory.json` | `58e988fbb396f6dc1610f1efbedb359c8166b65e94674571be78a74b0f191d7c` |
| `class-universe.json` | `f7d7b2d0f32f978510e009d5db91444dca58d7d0547fe094990185e87ac546b6` |
| source before/after | `a8627df1ab19f832dd2d7e8abbb584303ab7e77c10398155d3b5a57c006198bf` |
| cleanup / sensitive | `5524f0c6…34ff1 / b4f3df29…74701` |

## External restoration

Outer wrapper=`runner_rc=0 / restore_rc=0`。以下 exact pre-run containers 均已恢复：

| Database | Container ID | Port | State |
|---|---|---:|---|
| MySQL 5.7 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` | 13306 | running / healthy |
| MySQL 8 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` | 13308 | running / healthy |
| PostgreSQL 15 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` | 15432 | running / healthy |
| SQL Server 2022 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` | 11433 | running / healthy |

## Next authority

- [x] r23 PASS、public validation、source seal、cleanup 与 DB restoration；
- [x] r23/r22 exact delta 与 raw probe 已定位；
- [x] candidate 保持 absent；
- [x] existing-node deterministic regression、5 个 fresh JVM probe exact、full-module GREEN、独立代码复核；
- [ ] commit/push/clean replacement Cdiag；
- [ ] fresh diagnostic-r24 全 lane PASS 并确认稳定 `4/4` outcome；
- [ ] candidate/capsule/双审、direct-child Cfreeze、fresh formal-r7；
- [ ] final quality→coverage audit→acceptance 后恢复 Step 5。
