---
doc_role: version_execution_index
doc_purpose: Index the executable 9.3.4 test and CI evidence-chain plan.
version: 9.3.4
status: in-progress
predecessor: 9.3.3 signed-off / accepted-with-risks
created_at: 2026-07-14
updated_at: 2026-07-17
---

# 9.3.4 测试与 CI 证据链

## 文档作用

- doc_type: requirement+contract+plan+progress+acceptance-evidence-index
- intended_for: project-root-session / build owner / CI owner / reviewer
- purpose: 把 Failsafe 分层、五数据库矩阵、聚合覆盖率、required CI 与
  immutable release evidence 拆成严格 1~7 的可执行版本计划。

## Entry Decision

- 9.3.3 已 `signed-off / accepted-with-risks`；replacement authority
  `20260714T084351Z-3271604`=`3824 tests / 519 reports / F0/E0/S3`，满足本版本
  predecessor。
- 9.3.4-A 最小门继续由
  `docs/9.3.3/preconditions/9.3.4-A-minimum-test-gate.md` 提供历史 authority；
  本目录只引用，不复制一份可漂移状态。
- Step 1 已以 `step1-candidate-r8-20260714` 正式确认；两路独立复核均为 PASS，
  blocker=0。最终 inventory validator、confirmed-summary validator 与 16/16 hash
  manifest 复验通过。
- Step 2 已由 signal-safe r8e successor、Surefire Unit 与 Failsafe Integration
  三条独立权威链通过：`724 positive / 59 structural / 5,205 testcase / F0/E0/S0`；
  `46` 个 external execution 保持 exact deferred-to-Step-3。
- r8d successor 及其 Unit/Integration 因 `completed` 窗口 signal fail-open 被明确
  作废，禁止用于 coverage/acceptance；r8e 的 INT/TERM/HUP 动态探针分别以
  `130/143/129` fail closed。
- Step 3 已由同一 committed HEAD 的正式父运行
  `step3-required-20260716-final-r4` 通过：database `29/370` + required external
  `16/76` = exact `45/446/F0E0S0`，gap/overlap/extra=`0/0/0`；DB state=`18/18`、
  Redis state=`4/4`、Addon companion=`2/6`。quality→coverage→feature acceptance
  已按序完成；Step 4 仍为 `in-progress`。diagnostic r7 在无 ambient DB listener 的环境中
  暴露 Unit 对 `127.0.0.1:13306` 的隐式依赖并 fail closed，尚无 coverage pass 或
  可复用的 reviewed aggregate baseline。
- Step 4 r5 已在 clean/pushed commit 上建立 run-owned source seal，但 database-state
  companion 选择 frozen Step 3 authority manifest 而 fail closed；该轮 Unit、Integration 和
  Addon 子结果只属于 r5 失败运行，不得拼接或复用。successor runtime-binding
  remediation 已通过正式质量闸门，两层独立库存保持：coverage execution=
  `23 exec / 48 sessions`；
  required report overlay=`773 positive + 59 structural / 5,707 testcase`，Addon companion=
  `2/6`；required 总计预期 `F0E0S0`。coverage contract mutations=`21/21`、
  source identity=`22/22`、effective POM=`4/4`、toolchain receipt=`5/5`、
  report inventory=`30/30`。Unit fixture negatives=`36/36`，其中原
  fixture/manifest schema/tamper=`20/20`、connection typed=`7/7`、atomic
  publisher=`3/3`、profile boundary=`6/6`；negative receipt 文件 schema/tamper 另为
  `4/4`，真实 fixture lifecycle=`5/5`。restricted credential receipt 的 closed Unit Maven
  observation window 由 `configure init_connect -> Maven -> 同一 root batch 先 disable 再
  SELECT` 界定，按 `connection_id` 保存有序 observed user；该窗口内所有 non-super
  connections 必须使用 run-owned `v934_unit`，callback 返回后的 provisioner `foggy`
  控制面连接在窗口外。Step 2 derived
  view/successor overlay=`12/12`/`12/12`，XML/provenance=
  `68/68`，logger lifecycle=`9 类 / 14 case`。
- r8 lifecycle closure 时点的 toolchain receipt 绑定 Step 1 raw 工具版本，并实测约束 compiler realm ASM
  `9.6`、JaCoCo realm ASM `9.7`、test classpath ASM `9.7.1` 以及 24 个
  production module 的 effective compiler。run-owned Unit fixture hardening 与 lifecycle
  remediation 的该历史静态 identity 为：top manifest=`60/60` /
  `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`，successor=
  `14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`，
  amendment=`12 rows / 4 new + 8 changed` /
  `998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`；fixture
  contract/tool/Unit runner/datasource adapter=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
  coverage diagnostic/formal=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；
  declared amendments=`18` /
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；
  overlay contract/tool=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`。
  datasource adapter inventory 由 scrubbed Git environment 枚举 `HEAD` tree，并以
  `GIT_NO_REPLACE_OBJECTS=1` 禁用 replace object；ambient guard 同时覆盖 underscore、
  dotted、hyphen 的 Spring/custom key，以及 `@argfile`、`VMOptionsFile`、`javaagent`、
  `agentlib`、`agentpath` 间接注入。
  新增回归证明 tracked FIFO 会在 worktree-aware Git 读取前 fail-fast，且 before/after raw
  stat identity 比较会拒绝内容可被 Git clean 等价化、但运行中发生的并发重写。
  source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个
  candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed
  attributes 若声明 external clean filter，则在任何 worktree-aware Git hash/driver hook
  执行前 fail closed，negative 证明 hook 未执行。
- 静态复核还发现并修复 root coverage `argLine` 早解析 fail-open：legacy
  profile 现在实际生成/读取 exec，低于既有门时正确失败；生产代码不变，
  canonical `@{argLine}` 已由 versioned contract guard 与 `8/8` negatives 自动保护。详见
  [BUG-step4-legacy-coverage-argline-fail-open](workitems/BUG-step4-legacy-coverage-argline-fail-open.md)。
- clean/pushed HEAD `bc100b0f63bd3ff62d1105611dae41741790aedd` 的
  `step4-coverage-20260716-diagnostic-r1` 在 `child-unit` 以
  `3115 tests / 1 failure / 0 errors / 0 skipped` fail closed。根因是腐化 daily 表但
  查询实际命中 monthly，同时暴露 raw-vs-raw 与 nullable/empty 伪绿。修复后的 focused
  整类=`9/F0E0S0`，四类组合=`57/F0E0S0`；源码 SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`。详见
  [BUG-step4-preagg-validation-wrong-table](workitems/BUG-step4-preagg-validation-wrong-table.md)。
- clean/pushed HEAD `0101a44a07784bf6b484d490c7fb508727fbab70` 的
  `step4-coverage-20260716-diagnostic-r2` 已完整通过 Unit
  `681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration 执行
  caffeine=`2/F0E0S0`、hermetic=`3/F0E0S0`、sqlite-broad=`307/F1E0S0`，合计
  `312/F1E0S0`。唯一失败为 `PreAggregationL2CacheIT` 未显式声明 snapshot-only，outer
  在 `child-integration` fail closed，summary absent，后续 lane/aggregate/threshold 均未执行。
  failed diagnostic 见
  [step4-coverage-diagnostic-r2-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md)。
- L2 fixture 已显式关闭 hybrid 并收紧 exact name/table/raw negative，最终源码 SHA-256=
  `bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`；focused=
  `1/F0E0S0`、与 `PreAggregationIT` 组合=`30/F0E0S0`。主动 hybrid 扫描还发现 Pivot
  legacy fallback 两次稳定 RED；仅 legacy 分支关闭 hybrid 后，legacy/V934 SQLite
  focused 各 `1/F0E0S0`，源码 SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`。生产
  Matcher、hybrid 默认、threshold/exclusion 均未修改。
- clean/pushed HEAD `e16693297239f2a861f3b93b3de60c1bb783bda0` 的 r3 完整通过 Unit
  `681 execution + 55 structural / 4,941 testcase / F0E0S0`，随后因 Unit runner 未
  close/wait 异步日志 `tee`，outer 在 `child-unit` 报 live process-group residue 并
  fail closed。r3 summary/observation absent，后续所有 lane/gate 均未执行；见
  [r3 failed evidence](evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md)
  与 [logger race BUG](workitems/BUG-step4-child-run-log-tee-residue-race.md)。
- r4 的 reported launch head 为 `ceea084ca25a9d679ba128e3f6bd50a63322c112`；outer 在
  `source-before` 以 exit code `2` fail closed，run-owned Git/source seal、全部测试 lane、
  aggregate 与 summary 均 absent，decision=`excluded-from-step4-exit`。根因是
  `core.fileMode=false` checkout 被错误要求 worktree executable bit 与 Git mode 完全一致；
  详见 [r4 failed evidence](evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md)
  与 [source inventory BUG](workitems/BUG-step4-source-inventory-filemode-false.md)。
- r5 tested commit=`a35b99cb08f42817d8e75c440f18910b6961841b`，run=
  `step4-coverage-20260716-diagnostic-r5`；Unit=`681+55 / 4,941 / F0E0S0`、
  Integration=`47+4 / 320 / F0E0S0`、Addon=`2/6/F0E0S0` 后，database-state 以
  `E_AUTHORITY_MANIFEST` 拒绝 stale `foggy-dataset-model/pom.xml`。database cells、
  external、aggregate、threshold 与 summary 均未完成，r5=
  `excluded-from-step4-exit`，partial lanes 不可复用。
- r6 tested commit=`eb10d9c10a73f379db9ce4fa3d05ff340b489fd4`，source-before=
  `3,974 files` / SHA-256=
  `3a4322e8442646c58ed522c0d4fb52071b3219cc1c2f204c209299bd8acc1cff`；Unit=
  `681+55 / 4,941 / F0E0S0`、Integration=`47+4 / 320 / F0E0S0`、Addon=
  `2/6/F0E0S0` 后，database-state 以 `E_DYNAMIC_PRECONDITION` 拒绝已被长期 repo demo
  container `foggy-demo-mysql` 占用的 frozen port `13306`。database cells、external、
  aggregate、threshold、source-after 与 summary 均 absent；r6=
  `excluded-from-step4-exit`，partial lanes 不可复用。这是环境阻塞，不是产品回归。
- r7 tested commit=`528a0a541d90ef77d577e1816b392d33168cb558`，source-before=
  `3,976 files` / SHA-256=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；在四个 frozen
  ports 均无 listener 时，Unit 暴露 6 suites / 11 errors，证明此前绿色隐式依赖 ambient
  MySQL/schema。outer=`child-unit / exit 1`，后续 lane、aggregate、threshold、source-after
  与 summary 全 absent；r7=`excluded-from-step4-exit`。remediation 以 run-owned pinned
  MySQL 5.7 替换完整 Unit lane，保持唯一 Maven invocation 与
  `681+55 / 4,941 / F0E0S0`；已知隐藏依赖清单仅为 `6 reports / 11 testcase nodes`，不是
  其余 Unit 测试无数据库访问的证明。Step 2 identity/cardinality 继续保留结构意义，其 Unit
  正确性绿色不复用；机器例外契约见
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`，迁移债务见
  [DEBT-unit-mysql57-fixture-classification-migration](workitems/DEBT-unit-mysql57-fixture-classification-migration.md)。
- Unit fixture quality r2=`step4-unit-fixture-quality-20260716-r2` 在 commit
  `a603f839a98d99b2d7beb8379f76b4d85539328c`、`3,981 files`、source SHA-256=
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`
  上先通过真实 lifecycle `5/5`，随后完整 Maven invocation 因全局
  `spring.datasource.*` 覆盖 `foggy-dataset-model` 的 SQLite profile 而 fail closed：
  `3,115 tests / 631 errors`。直接根因为 `org.sqlite.JDBC` 被配置为连接
  `jdbc:mysql://127.0.0.1:13306/...`；r2 excluded/non-reusable。修复已将
  `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` 三个 placeholder 仅放入
  `foggy-dataset` test resource，callback 不再传全局 Spring datasource/test 参数，其他
  profile 保持原配置。
- fresh Unit fixture quality r3=`step4-unit-fixture-quality-20260716-r3` 已在 commit
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`、`3,982 files`、source before=after
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`
  上通过：唯一 Surefire invocation=`681 positive + 55 structural = 736 raw reports /
  4,941 testcase / F0E0S0`；fixture negatives=`36/36`、receipt schema/tamper=`4/4`，
  closed receipt 内 `18/18` connections 全部为 `v934_unit`；真实 lifecycle=`5/5`，
  run-owned container/volume/network cleanup=`0/0/0` 且 port free。evidence window 外已按
  exact ID `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
  恢复 demo MySQL 为 `running/healthy`。record=
  [step4-unit-fixture-quality-r3-pass-20260717](evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md)。
- fresh all-lane r8=`step4-coverage-20260716-diagnostic-r8` 已从 clean/pushed
  `3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a` 启动，但在建立 run-owned Git/source seal
  前因 lifecycle static contract 仍要求旧 Unit direct EXIT token，于 `bootstrap-negative`
  fail closed；所有 lane、aggregate、threshold 与 summary absent，r8=
  `excluded/non-reusable`。fixture-aware Unit wrapper 正确，缺陷是测试契约漂移；record=
  [step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717](evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md)，BUG=
  [BUG-step4-unit-lifecycle-static-contract-drift](workitems/BUG-step4-unit-lifecycle-static-contract-drift.md)。
- lifecycle remediation 已通过：动态 `9 类 / 14 case`，Unit shape/source-seal=
  `13/13 + 3/3`，Integration=`11/11 + 5/5`；runner 使用 raw-byte SHA-256 seal，拒绝
  comment/quoted heredoc、EXIT/0、early return、shadow、false/subshell、heredoc source/eval
  与 CRLF drift。两路独立质量复核 B/H/M/L=`0/0/0/0`；tool=
  `8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b`，top=
  `60/60` / `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`。
- fresh all-lane r9=`step4-coverage-20260717-diagnostic-r9` 已从 clean/pushed commit
  `a0466ec04c51c436413e85836a7dee6153e18010` 启动并完成 Unit、Integration、Step 3
  required、report inventory=`773+59/5,707/F0E0S0` 与 `23 exec / 48 sessions`；随后
  exec verifier 把所有测试/依赖/CGLIB 同名多 ID 当成 production drift，在
  `coverage-report` 以 `E_CLASS_ID_MISMATCH` fail closed。`exec-manifest`、aggregate、
  source-after、threshold 与 summary absent，r9=`excluded/non-reusable`。只读复算为
  `16,939` 个 JaCoCo class-ID identities、135 个同名多 ID、frozen production 冲突=0；
  record/BUG=
  [r9 exec class scope failure](evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md) /
  [BUG-step4-exec-class-id-scope-drift](workitems/BUG-step4-exec-class-id-scope-drift.md)。
- r9 remediation 将 production class-ID consistency 冻结到 24-module production class
  universe，raw/aggregate 保留所有 JaCoCo ID，aggregate 按 ID 做 exact bitmap union；focused
  exec=`17/17`、contract mutations=`21/21`、XML identity/provenance=`68/68`、
  overlay=`12/12`。同轮质量复核又确认 lifecycle
  shape validator 的 comment/dead-context 与 dynamic `trap` 拼接绕过；已登记
  [BUG-step4-lifecycle-semantic-validator-bypass](workitems/BUG-step4-lifecycle-semantic-validator-bypass.md)，
  原 lifecycle quality 结论被重新打开。coded executable-stream 修复现已通过完整 regression：
  Unit/Integration shape=`16/16 + 14/14`、semantic stream=`2/2 + 5/5`、raw seal=`2/2`、
  outer/library=`3/3 + 3/3`；三路独立正式质量最终 B/H/M/L=`0/0/0/0`。
- fresh all-lane r10=`step4-coverage-20260717-diagnostic-r10` 已从 clean/pushed commit
  `47e0c027cd205a49d40db400ba26b99e6f97d60e` 完成 required=
  `773+59/5,707/F0E0S0`、Addon=`2/6`、`23 exec / 48 sessions / 16,947 class IDs`、
  aggregate/observation/source-after/cleanup；最终因 demo identity producer 复用
  credential-shaped authorization label，在 `sensitive-scan` fail closed。sensitive/summary
  receipt absent，r10=`excluded/non-reusable`；line=`54,478/76,830`、branch=
  `25,980/44,870` 只是 failed-run partial observation，不得冻结 threshold。record/BUG=
  [r10 sensitive-scan failure](evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md) /
  [BUG-step4-sensitive-scan-authorization-context-false-positive](workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md)。
- remediation 保持五条 sensitive patterns 原样不变，仅把 producer 改成明确的 demo identity
  result wording；outer bootstrap-negative 以同一数组执行内存 `7 dangerous + 3 safe` probe，
  `rg rc>1` 双路径 fail closed，fixture 不落 run root。launcher request smoke、manifest、
  coverage contract 与 successor overlay focused validation 已通过。
- fresh all-lane r11=`step4-coverage-20260717-diagnostic-r11` 已从 clean/pushed commit
  `141592ca9f4219d87a018774ee607b09a8e5a8a1` 启动；full coverage contract、successor
  overlay、sensitive bootstrap probe、authority 与 coverage contract negatives 通过后，
  lifecycle suite 在 `bootstrap-negative` 以 stable `E_SOURCE_SEAL` 拒绝 outer actual raw
  `57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649` 与内嵌旧值
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa` 的漂移。
  r11 在 run-owned Git/source seal 前退出，零测试 lane、exec、aggregate、observation 与
  summary，结论=`failed / excluded / non-reusable`。
- r11 remediation 现以独立 frozen constants 建立 Unit、Integration、outer、lifecycle library
  的 early four-way raw binding；canonical positive=`1`、partial-update negatives=`6` 均使用
  stable `E_SOURCE_SEAL_BINDING`。六类 negative 为 outer+manifest 刷新但 nested stale、
  outer-only drift、valid-64 nested-only wrong、missing、duplicate 与 invalid-format constant。
  outer 另由 raw CRLF negative 与 executable no-op
  semantic negative 双层保护。focused lifecycle suite=`PASS`，top manifest=`60/60`、
  successor manifest=`14/14`、coverage contract=`21/21`、overlay=`12/12`；当前 outer raw=
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec`、executable stream=
  `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`、lifecycle tool=
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc`、top manifest=
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`。安全复核发现的
  preflight TOCTOU Medium 已由 descriptor-bound strict read 关闭；两路 post-fix implementation
  review 与独立 docs/status review 最终 B/H/M/L=`0/0/0/0`。
- fresh r12=`step4-coverage-20260717-diagnostic-r12` 已在 clean/pushed commit
  `05351ecab0d7fc43d12dfa307ffecf81feb41539` 完整通过 all-lane：required=
  `773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48/16,935`，aggregate line=
  `54,478/76,830`、branch=`25,980/44,870`，sensitive scan 与 cleanup=`0/0/0` 均通过。
  该有效 diagnostic 同时发现 9/12 critical classes 低于 floor 与唯一
  `NamespaceScope.branch` structural N/A；旧 freeze consumer 无法消费真实 enriched shape，
  r12 因而只作 immutable diagnostic，不得直接创建 Cfreeze。
- remediation 不改生产逻辑、floor、critical set 或 exclusion：八份既有测试在不增加 report/
  testcase 节点下覆盖九个 gap class；focused=`136/F0E0S0`。threshold lifecycle 现 exact
  消费真实六字段 row/enriched counter，N/A 只接受唯一 machine tuple，并以 strict JSON
  identity 拒绝 bool/int、int/float 与 `gap:false` aliases；XML=`118/118`、contract=
  `27/27`、frozen replay policy=`12/12`、overlay=`12/12`。
- fresh r13=`step4-coverage-20260717-diagnostic-r13` 已在 clean/pushed Cdiag commit
  `b76552e21479c75111f648a4aa678abe018cc3f9` 完整封存：outer=
  `diagnostic-observed / completed / exit 0`，required=`773+59/5,707/F0E0S0`、Addon=
  `2/6`、exec=`23/48`；critical below-floor=`0`，唯一 structural N/A=`1`，即
  `NamespaceScope.branch`；sensitive scan=`passed`、cleanup=`0/0/0`。threshold freeze
  candidate 已通过 public verification，SHA-256=
  `8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`。
- 当前状态为 `in-progress / r13 diagnostic sealed / thresholds confirmed /
  Cfreeze formal-ready / fresh formal pending`。`coverage-thresholds.json=confirmed`，coverage
  contract/publication=`formal-ready`；candidate 仍保持 immutable `review-required`。当前工作树中的
  Cfreeze machine delta 只准备 reviewed threshold 转换；单次 direct-child commit/topology proof
  仍待完成，且该转换不是 Step 4 exit 或 formal pass。
  下一步只能在 Cfreeze commit/push、direct-parent delta 与 clean `HEAD == origin/main` 全部通过后
  执行 fresh formal；最终 implementation quality、coverage audit 与 acceptance 严格后置。
  `can_enter_coverage_audit=no`；Step 5、formal、coverage audit、acceptance 与 9.3.5 仍关闭。

## 执行资料

- requirement: [requirement/P0-test-ci-evidence-chain.md](requirement/P0-test-ci-evidence-chain.md)
- test/evidence contract: [contract/test-lane-evidence-contract.md](contract/test-lane-evidence-contract.md)
- module responsibility: [module-responsibility.md](module-responsibility.md)
- code inventory: [code-inventory.md](code-inventory.md)
- implementation plan: [implementation-plan.md](implementation-plan.md)
- project execution prompt: [execution-prompt.md](execution-prompt.md)
- progress: [progress/test-ci-evidence-chain-progress.md](progress/test-ci-evidence-chain-progress.md)
- test plan: [test/test-ci-evidence-chain-test-plan.md](test/test-ci-evidence-chain-test-plan.md)
- acceptance evidence plan: [acceptance-evidence-plan.md](acceptance-evidence-plan.md)
- Step 4 diagnostic-ready quality gate:
  [quality/step4-diagnostic-ready-implementation-quality.md](quality/step4-diagnostic-ready-implementation-quality.md)
- Step 4 diagnostic r1 PreAgg validation regression：
  [workitems/BUG-step4-preagg-validation-wrong-table.md](workitems/BUG-step4-preagg-validation-wrong-table.md)
- Step 4 diagnostic r2 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md)
- Step 4 diagnostic r2 PreAgg L2 fixture regression：
  [workitems/BUG-step4-preagg-l2-hybrid-fixture-drift.md](workitems/BUG-step4-preagg-l2-hybrid-fixture-drift.md)
- Step 4 proactive Pivot legacy fixture regression：
  [workitems/BUG-step4-pivot-legacy-hybrid-fixture-drift.md](workitems/BUG-step4-pivot-legacy-hybrid-fixture-drift.md)
- Step 4 diagnostic r3 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md)
- Step 4 child logger lifecycle regression：
  [workitems/BUG-step4-child-run-log-tee-residue-race.md](workitems/BUG-step4-child-run-log-tee-residue-race.md)
- Step 4 Git environment isolation regression：
  [workitems/BUG-step4-git-environment-override-bypass.md](workitems/BUG-step4-git-environment-override-bypass.md)
- Step 4 source/context/raw-exec provenance regression：
  [workitems/BUG-step4-source-context-crosslink-gap.md](workitems/BUG-step4-source-context-crosslink-gap.md)
- Step 4 diagnostic r4 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md)
- Step 4 source inventory file-mode regression：
  [workitems/BUG-step4-source-inventory-filemode-false.md](workitems/BUG-step4-source-inventory-filemode-false.md)
- Step 4 diagnostic r5 failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md)
- Step 4 database-state successor authority regression：
  [workitems/BUG-step4-database-state-successor-authority-manifest.md](workitems/BUG-step4-database-state-successor-authority-manifest.md)
- Step 4 diagnostic r6 environment failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md)
- Step 4 r6 MySQL 5.7 port environment blocker：
  [workitems/BLOCKER-step4-r6-mysql57-port-occupation.md](workitems/BLOCKER-step4-r6-mysql57-port-occupation.md)
- Step 4 diagnostic r7 Unit hidden MySQL failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md](evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md)
- Step 4 diagnostic r8 lifecycle contract drift failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md)
- Step 4 diagnostic r9 exec class scope failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md)
- Step 4 diagnostic r10 sensitive-scan failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md)
- Step 4 sensitive-scan producer-label regression：
  [workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md](workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md)
- Step 4 diagnostic r11 outer source-seal failed evidence：
  [evidence/step-4/step4-coverage-diagnostic-r11-outer-source-seal-fail-closed-20260717.md](evidence/step-4/step4-coverage-diagnostic-r11-outer-source-seal-fail-closed-20260717.md)
- Step 4 outer source-seal binding regression：
  [workitems/BUG-step4-outer-runner-source-seal-binding-drift.md](workitems/BUG-step4-outer-runner-source-seal-binding-drift.md)
- Step 4 diagnostic r12 successful observation with threshold gaps：
  [evidence/step-4/step4-coverage-diagnostic-r12-pass-with-threshold-gaps-20260717.md](evidence/step-4/step4-coverage-diagnostic-r12-pass-with-threshold-gaps-20260717.md)
- Step 4 threshold freeze observation/applicability regression：
  [workitems/BUG-step4-threshold-freeze-observation-applicability-gap.md](workitems/BUG-step4-threshold-freeze-observation-applicability-gap.md)
- Step 4 canonical evidence JSON numeric type-alias regression：
  [workitems/BUG-step4-evidence-json-numeric-type-alias-bypass.md](workitems/BUG-step4-evidence-json-numeric-type-alias-bypass.md)
- Step 4 exec class-ID scope regression：
  [workitems/BUG-step4-exec-class-id-scope-drift.md](workitems/BUG-step4-exec-class-id-scope-drift.md)
- Step 4 lifecycle semantic validator bypass regression：
  [workitems/BUG-step4-lifecycle-semantic-validator-bypass.md](workitems/BUG-step4-lifecycle-semantic-validator-bypass.md)
- Step 4 Unit lifecycle static contract drift regression：
  [workitems/BUG-step4-unit-lifecycle-static-contract-drift.md](workitems/BUG-step4-unit-lifecycle-static-contract-drift.md)
- Step 4 Unit hidden MySQL authority regression：
  [workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md](workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md)
- Step 4 Unit MySQL 5.7 fixture machine contract：
  [`scripts/v934/step4/unit-mysql57-fixture-contract.json`](../../scripts/v934/step4/unit-mysql57-fixture-contract.json)
- Step 4 Unit fixture classification migration debt：
  [workitems/DEBT-unit-mysql57-fixture-classification-migration.md](workitems/DEBT-unit-mysql57-fixture-classification-migration.md)
- Step 1 confirmed evidence:
  [evidence/step-1/inventory-contract-freeze-20260714.md](evidence/step-1/inventory-contract-freeze-20260714.md)
- Step 2 structural amendment evidence：
  [evidence/step-2/step2-structural-container-contract-amendment-20260714.md](evidence/step-2/step2-structural-container-contract-amendment-20260714.md)
- Step 2 successor independent review：
  [evidence/step-2/step2-successor-r8e-independent-review-20260715.md](evidence/step-2/step2-successor-r8e-independent-review-20260715.md)
- Step 2 runner exit：
  [evidence/step-2/step2-runner-split-exit-r8e-20260715.md](evidence/step-2/step2-runner-split-exit-r8e-20260715.md)
- Step 2 implementation quality：
  [quality/step2-runner-split-implementation-quality.md](quality/step2-runner-split-implementation-quality.md)
- Step 2 coverage audit：
  [coverage/step2-runner-split-coverage-audit.md](coverage/step2-runner-split-coverage-audit.md)
- Step 3 five-DB diagnostic foundation（not authority）：
  [evidence/step-3/step3-five-db-foundation-20260715.md](evidence/step-3/step3-five-db-foundation-20260715.md)
- Step 3 database matrix runner/collector candidate（run-local / not authority）：
  [evidence/step-3/step3-database-matrix-runner-candidate-20260715.md](evidence/step-3/step3-database-matrix-runner-candidate-20260715.md)
- Step 3 committed Redis external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-redis-runner-candidate-20260715.md](evidence/step-3/step3-external-redis-runner-candidate-20260715.md)
- Step 3 committed Mongo/DataViewer external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-mongo-runner-candidate-20260715.md](evidence/step-3/step3-external-mongo-runner-candidate-20260715.md)
- Step 3 committed MySQL57 external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-mysql-runner-candidate-20260715.md](evidence/step-3/step3-external-mysql-runner-candidate-20260715.md)
- Step 3 committed Vector external subset candidate（run-local / not full authority）：
  [evidence/step-3/step3-external-vector-runner-candidate-20260715.md](evidence/step-3/step3-external-vector-runner-candidate-20260715.md)
- Step 3 shared external matrix candidate（single outer / complete external subset）：
  [evidence/step-3/step3-shared-external-matrix-candidate-20260716.md](evidence/step-3/step3-shared-external-matrix-candidate-20260716.md)
- Step 3 formal required-matrix exit：
  [evidence/step-3/step3-required-matrix-exit-20260716.md](evidence/step-3/step3-required-matrix-exit-20260716.md)
- Step 4 MultiThread prerequisite companion evidence：
  [evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md](evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md)
- Step 3 implementation quality：
  [quality/step3-required-matrix-implementation-quality.md](quality/step3-required-matrix-implementation-quality.md)
- Step 3 test evidence coverage audit：
  [coverage/step3-required-matrix-coverage-audit.md](coverage/step3-required-matrix-coverage-audit.md)
- Step 3 feature acceptance：
  [acceptance/step3-required-matrix-acceptance.md](acceptance/step3-required-matrix-acceptance.md)

## 1~7 顺序

| Step | 内容 | 当前状态 | Exit |
|---:|---|---|---|
| 1 | 契约与静态库存冻结 | passed | r8 confirmed；532 sources / 820 discovery / 829 execution / 519 predecessor；28/28 negatives |
| 2 | Surefire/Failsafe 全量分层 | passed | r8e confirmed；724 positive + 59 structural；5,205 testcase；F0/E0/S0；signal-safe status |
| 3 | 五数据库与外部集成 required matrix | passed | r4 same-commit exact `45/446/F0E0S0`；DB state `18/18`、Redis state `4/4`；Addon companion `2/6`；feature accepted |
| 4 | JaCoCo unit+IT 聚合与关键类门 | in-progress / r13 diagnostic sealed / thresholds confirmed / Cfreeze formal-ready / fresh formal pending | r13=`b76552e2`：`773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48`、below-floor=`0`、`NamespaceScope.branch` N/A=`1`、sensitive passed、cleanup=`0/0/0`；candidate SHA=`8bb47382...14d00` verified；threshold SHA=`0cfc6765...227cb` confirmed；fresh formal/final quality pending；`can_enter_coverage_audit=no`；Step 5/formal/audit/acceptance closed |
| 5 | 单一 authority runner 与 immutable evidence rehearsal | pending | dirty-safe candidate 可独立复算，但不更新 final authority pointer |
| 6 | PR/main/release CI 接线 | pending | exact five-cell artifacts、stable aggregator、JAR/镜像同一制品 |
| 7 | clean-commit 权威回放与后置门 | pending | self-check→quality→coverage→version acceptance signed-off |

任一步未满足 exit，不进入下一步；expected-negative、diagnostic 和 superseded
run 不得拼接为绿色 authority。

9.3.1–9.3.3 historical run/FQCN/count 保持封存；重命名后的 9.3.4 通过 reviewed
predecessor migration manifest 和 successor current-source lanes 证明等价回归，
不要求不可变的 v933 Batch 7 runner 在新命名上原样通过。

## 范围边界

- 本版本允许创建 build-only coverage report module；它不含生产类、不进入
  Launcher，不是 9.4.0 的生产模块拆分。
- 不实施 9.3.5 的 typed execution phase、统一 QueryFacade、公共 DTO/大类拆解。
- 不实施 9.4.0 的 `model-api/core/jdbc/starter/web`、BackendProvider、SPI v2
  或 Addon TCK。
- 测试治理发现生产 BUG 时先建 9.3.4 workitem，做最小 RED→fix→GREEN；若需
  公共 API/模块边界变更则停止并转交 9.3.5/9.4.0。
