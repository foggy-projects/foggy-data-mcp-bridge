---
doc_role: independent-review-evidence
doc_purpose: Record dual independent review of the Step 2 r5 successor before confirmation.
version: 9.3.4
step: 2
candidate: step2-candidate-r5b-20260715
status: passed
reviewers: v934_r5_identity_review+v934_r5_delta_review
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 2 successor r5 independent review

## Decision

`PASS / confirm-approved`，blocker=`0`。两名 reviewer 独立、只读复算 candidate，
均未修改源码、candidate 或 summary，也未执行 confirm。r5 可以从
`candidate / pending-independent-review` 提升为 `confirmed / passed`；Step 2 的
Surefire/Failsafe authority 仍须在确认后分别通过。

## Candidate provenance

| Field | Value |
|---|---|
| run | `step2-candidate-r5b-20260715` |
| run status | `completed / exit_code=0 / passed` |
| started / finished | `2026-07-14T16:59:41Z / 2026-07-14T17:20:04Z` |
| protected source before / after / independently recomputed | `09330fc724caf1a403fdfaa91d2baeaaa3707274206efa8a10afce8cd24f2052` |
| candidate freeze | `a4bcc913b02fd9a71ceab526b84db2986dacbd78881cffb1bc5e2a44d99357ad` |
| candidate manifest | `d9cf3ad912a1239fbe908f98eaa8bd94594b124988cfc01fcb2cccd7a71673c0` |
| pending candidate summary | `288f424e7f23c7526258baac9eebb9cb379d6915342d5cfa5de5a3218d0046af` |
| file/hash set | 13/13, all canonical payload hashes passed |
| successor negatives | 21/21 passed with exact expected error code |

两路均独立执行 `validate`；identity reviewer 另执行 `validate-summary`，全部 exit `0`。

## Independent inventory recomputation

- sources：`532`，其中 reactor `530`、executable reactor `514`；
- discovery：`820 = 804 report containers + 16 reviewed-none`；
- positive report identities / structural reports：`745 / 59`，交集为 `0`；
- positive execution identities：`770 = 724 Step 2 + 46 Step 3`；
- Step 2 ownership：Surefire `677`、Failsafe `47`；
- Step 3 ownership：Failsafe `46`；
- structural ownership：Surefire `55`、Failsafe `4`；
- predecessor：`519 = 480 execution refs + 39 structural refs`，typed XOR violations `0`；
- rename：`35 sources / 64 reports / 76 execution identities`，其中 positive
  `60 reports / 72 identities`、structural `4 / 4`；
- active reactor：runner contract 与 root reactor 均为相同、排序稳定的 `24` 个模块，
  source/report owner 的 orphan 与 overlap 均为 `0`。

## r4 to r5 amendment review

`scripts/v934/step2-r5-source-amendment.tsv` 的 SHA-256 为
`3a4c6d424a6a0568f2818ecf36337515904a51d5b54a5f88c9f04beead615391`，
且精确包含两条受审变更：

1. `MultiThreadExecutor.java`：源码从 canonical r4
   `906c00e51bdbfbff6627a7df6f23961c532bf6c713be48a7f2caf0c5c6c78cae`
   变为 `71305f6d56ddc70baae810898b635671d76314a2cf1652b730670f810224f2cc`；
   `foggy-core/target/classes` 从
   `fcc8d1bed99f03b126690e4feb3b975bc48a4e88039854904461e0a6559d9e06`
   变为 `1cd8887f0169d081e65db23b8370c931e03f270af67fa5edb8935c16ac367be1`；
2. `FsscriptClientProxyTest.java`：源码从 canonical r4
   `72409720aba005ee57dc9e610761976f1eac92d70ea92ec8b1943361a8ab3cef`
   变为 `7e5fd76f4ab2921ae2f3ed783a1db545488a598c69fc55857722d149e0d5ec2f`；
   test-classes 从
   `fb561bc0af8c72e46caccde82aa668929007b1cb6513d667ee4f4ca374ed90f4`
   变为 `e3f89be6e3a6188886dd9da71f8c978b117dd83511c139f8b51883c632257fcd`。

Fsscript discovery 仍为 `10` 个节点，report/execution identity 零变化。Step 1 到 r5
的 2,395 条 classpath 中内容变化精确为 `21` 行：Mongo main-classes `1` 行与
共享 `foggy-core` main-classes `20` 行；identity、ordinal 与顺序零变化，未出现第
22 条漂移。r4 到 r5 除 core `20` 行外，source/discovery/execution/structural/
Step2/Step3/rename/predecessor 均保持 exact。

## Runner and fail-closed review

- Surefire 排除外层 IT 的同时排除 `*IT$*`、`*ITCase$*`、`*E2E$*`、
  `*E2ETest$*` 与 `MultiDatabaseQueryTest$*`；删除 nested rule 的探针精确得到
  `E_POM_CONTRACT`；
- successor wrapper、Unit runner 与 Integration runner 都枚举并清理全部 `24`
  个 reactor 模块；report collector 同样扫描全部 `24` 个模块；
- 三个无测试源模块仍受 `unowned-reactor-extra / E_EXTRA_REPORT` 约束；
- authority runner 的 fresh-checkout 顺序为：创建 evidence sentinel 与 source
  baseline、清理 24 模块、compile-only `test-compile`、完整 successor/confirmed
  校验、再清 lane reports、创建 marker、执行 authority、收集与负向验证；
- successor 21 项 probe 的定义、执行结果与冻结顺序一致；report tool 的 14 项
  probe 均有明确 mutation/expected error，并由两条 authority runner 调用。

## Superseded provenance

r4 归档 13/13 payload hashes 全部有效，且精确匹配其最终 confirmed provenance：

- freeze：`508ca2f7e7b05e12e406a0816ee554e04a60b176b605d52083eb98a507fb836d`；
- manifest：`59de358d8df02cd27543f17c5d133b4c2a9efa827475cee078fa3a89341c22de`；
- confirmed summary：`84ea971a1e827b0bba6a42542ee66be4520fc80003087842a36813bc4b094ff3`。

Step 1 parent 的 freeze/manifest/confirmed-summary 也分别复算为
`ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876`、
`e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f`、
`579e9430bea6f873e7c4465cd1a6e45c49d348d84a89d5d648d25e3a5a4bbc50`。
