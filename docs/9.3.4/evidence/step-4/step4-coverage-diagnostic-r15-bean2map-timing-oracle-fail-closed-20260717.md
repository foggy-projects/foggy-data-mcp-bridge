# Step 4 diagnostic-r15 Bean2Map timing-oracle fail-closed evidence

- recorded_at: 2026-07-17
- run_id: `step4-coverage-20260717-diagnostic-r15`
- tested_commit: `9270d2d4e58684226aeb15eff55b027e6aa4a7eb`
- mode: `diagnostic`
- outcome: `failed / exit 1 / last_phase=child-unit`
- started_at: `2026-07-17T06:13:33Z`
- finished_at: `2026-07-17T06:22:29Z`
- source_before: `a44750ae863f2d50a4f6d4ff0c50840083b8880481b641498fd33964c6cbc3d7`
- source_after: absent
- cleanup: `container_residue=0 / volume_residue=0 / network_residue=0`
- sensitive_scan: not reached / receipt absent
- publication: report inventory、exec manifest、aggregate、coverage observation、threshold
  candidate、summary and coverage gate absent

## Passed boundary before Unit failure

r15 从 clean `HEAD == origin/main == 9270d2d4…` 启动，四个 frozen ports 在 evidence window
前均无 listener。以下 preflight 已通过：runner seal bindings=`4`、full coverage contract、
successor overlay、sensitive-pattern memory probes=`7 dangerous + 3 safe`、authority parent=
`2 positive + 14 negative`、contract mutations=`27/27`、source/Git identity=`22/22`、
threshold/frozen replay=`12/12`、run-log lifecycle、XML negatives=`118/118`、overlay negatives=
`12/12`、toolchain seal/negative=`5/5`、Step 2 report view、fresh class universe=
`24 modules / 2098 classes`、Unit fixture lifecycle negatives=`5/5`。

这些前置结果只属于 r15 failed run，不得与后续 run 拼接。Unit child 只产生 26 份 partial
XML=`124/F1E0S0`（`foggy-core` 24 reports/97、`foggy-bean-copy` 2 reports/27）和一个 partial
`jacoco-ut.exec`：仅 `2/21` Unit sessions、即全轮 `2/48` sessions，class-ID rows=`3,271`、
probes=`130,928`、covered probes=`44,501`。其余 22 个 required exec 均 absent；未发布完整
Unit summary/inventory，Integration、Addon、database、external、aggregate 与 diagnostic
threshold observation 均未开始。

## Exact failure

`foggy-bean-copy` 的 `Bean2MapUtilsTest#testCachingMechanism` 失败：

| sample | observed |
|---|---:|
| first call | 26,839 ns |
| second call | 304,859 ns |
| third call | 8,517 ns |
| asserted upper bound | 80,517 ns (`first * 3`) |

Suite result=`23 tests / 1 failure / 0 errors / 0 skipped`；模块在失败点输出=
`27 tests / 1 failure / 0 errors / 0 skipped`。sealed `run.log` 精确保存上述 samples、
`Bean2MapUtilsTest.java:407` 与 assertion message。

失败时 Surefire XML 位于 canonical module target 而非 run-owned root；在任何 focused rerun 前
捕获 SHA-256=`29d831e3c6f2afb031827e28c0919727eee08a603f290ff964614a692677dc62`。
该路径会被后续 Maven 执行覆盖，因此 failure authority 以 immutable run.log/run-status 为准，
不把当前 module XML 冒充 retained r15 artifact。

## Cause and decision

同一测试类在该方法前已经多次调用 `copyPropertiesSafe`，1000-copy test 也使用同一
SourceBean/TargetBean class；静态 `PROPERTY_CACHE` 已预热。原断言比较的是两个 cache-hit 的
单次墙钟采样，不能证明缓存命中或性能退化。third=`8,517ns` 立即回落，符合 JVM/OS/JaCoCo
调度噪声而非持续功能缺陷。

Decision：r15 保持 immutable failed evidence；不复用 partial Unit exec/report，不盲重跑。
在原 testcase 内用三个同类、不同 source 实例的重复复制 correctness 替代纳秒倍率，并删除邻接
1000-copy 的 1000ms 墙钟门；然后形成新 Cdiag 并完整重走 diagnostic -> review -> Cfreeze ->
formal。

## Deterministic remediation result

修复不改生产、不新增 testcase。`testCachingMechanism` 以三个同类、不同 source 实例连续复制 `John Doe/30`、
`Jane Doe/31`、`Alex Doe/32`，逐 target 精确断言，证明 cache metadata 不得保留实例值。
`testPerformanceWithManyObjects` 仍执行 1000 次并断言首末结果，只移除墙钟阈值。

focused 10 个独立 Maven/JVM/JaCoCo fork 均为 `1/F0E0S0`：

| fork | exec SHA-256 |
|---:|---|
| 1 | `3a832df0c911c6e7b4b14698129086fa5e757da556215fda465d879f4b128e96` |
| 2 | `252a44cdb41c8446c919acea13827baa8505e9f16ee79b481b89dd25342fa6db` |
| 3 | `4851be1eb77f9f4c553834e752a48ff2c7afbad737878c57ae0973ac956d4ba3` |
| 4 | `f3f5e260b9d37547d66ffc47ed8ec6219714a2cc6c93841d8a90dcb7b7b8947d` |
| 5 | `3d1df94dc927c174b2fa3c1657fafd411f88406d22d82b9bf5688e45d2c324b9` |
| 6 | `e411215fac3afd149aed3fa84b9d9078247b63817d520f30472c8e7fb960713e` |
| 7 | `557ec57c0c284f5993eb82da5755b0fd3ffb022037610e0bb37d6180de4eec65` |
| 8 | `a85a993dd5a729fe96ad2cd578d655d7139756fdaa4938d4daf9001e364e0ae5` |
| 9 | `d8c6071e4fbf5eac6f9ee52486f1517aa5d8717a5ad12042c6db7a8ddc31bc7a` |
| 10 | `7ad6d08f499c5c3a4694ee32095b848ce3878399c6e24dd40c348e8817ced168` |

完整 class=`23/F0E0S0`，exec SHA-256=
`df6914b25731199bd2673e09e5c95ebd311422b021059792539da41145249cff`；完整
`foggy-bean-copy` module=`27/F0E0S0`，exec SHA-256=
`20a9050f1abf9b7711a7a13cf830ce9160c69853ccc5e40bafa8a986ecbb37d2`。module verification
完成时 class XML SHA-256=
`f8b812c18bc6763c273551c4b47286f22f09b63f673a0f394750aba361da00fb`，这是
contemporaneous capture；canonical module target 可被后续独立 review rerun 覆盖，故该
XML 不是 retained artifact，也不用于 r15 authority。

这些 focused/module 结果只证明 remediation，不替代 fresh Unit replacement 或 Step 4 exit。

## Immutable artifact hashes

| artifact | SHA-256 |
|---|---|
| `run-status.env` | `c97d157a7b6a347fbf18b5c2191c2f500d51ef9fa554f423d07cb05e3f93f1e8` |
| `run.log` | `c7b3dae2c4bc12dbc9a35823aeb63010f702e57167ecf06362dc985409a17204` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `source-before.tsv` | `a44750ae863f2d50a4f6d4ff0c50840083b8880481b641498fd33964c6cbc3d7` |
| `run-context.json` | `cf641774d51974773b2e8a566de14f0544d53a101cb9d5ca2c04e362be431452` |
| `class-universe.json` | `25f26547ed09731b8a9e6bfd2ba90e18c5928a292b226df64ea0a577d81900e3` |
| partial `jacoco-ut.exec` | `410b52201eeaab5c17c7c85eb2abd674b7b69edf564b6e1b810a181ec3088939` |
| `child-ready/unit.json` | `3884adaf171f089ff9468c0043e8db80cf2b4fa9080306f05de3f13e16dbfdc6` |
| `toolchain-receipt.json` | `66b5fc5d494bc05e435c87127ba805bb95b77ca61e3ee7c47e175b2a1b576bd4` |
| Unit child `run-status.env` | `45124b20e724b6a61855dd2ba22ee97fda3af7a628d45fc355416af1e875ada7` |
| Step 2 `SHA256SUMS` | `f517f5dbcdadb214328f6980babf26d782abcd8990bc14a4f52403a3baf4e298` |

## Cleanup and restoration

r15 run-owned MySQL fixture lifecycle cleanup=`0 containers / 0 volumes / 0 networks / port free`；
outer cleanup receipt 同为 `0/0/0`。编排会话在 evidence window 外另行恢复并观察以下 exact
demo IDs 为 `running/healthy`，ports `13306/13308/15432/11433` listening；该外部观察不属于
r15 runner-owned immutable evidence：

- MySQL 5.7 `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
- MySQL 8 `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a`
- PostgreSQL `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07`
- SQL Server `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd`

## Evidence paths

- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r15/run-status.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r15/run.log`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r15/cleanup.env`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r15/source-before.tsv`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r15/class-universe.json`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r15/exec/jacoco-ut.exec`
- `target/v934-bean-cache-instance-focused-r{1..10}.exec`
- `target/v934-bean-cache-instance-class.exec`
- `target/v934-bean-cache-instance-module.exec`
