---
doc_role: independent-review-evidence
doc_purpose: Record dual independent review of the Step 2 r7 successor before confirmation.
version: 9.3.4
step: 2
candidate: step2-candidate-r7c-20260715
status: passed
reviewers: v934_r6_identity_review+v934_snapshot_skip
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 2 successor r7 independent review

## Decision

`PASS / confirm-approved`，blocker/high=`0`。两名 reviewer 独立、只读复算
candidate，均未修改源码、candidate 或 summary，也未执行 confirm。r7c 可以从
`candidate / pending-independent-review` 提升为 `confirmed / passed`；受 r7 保护的
Surefire/Failsafe authority 必须在确认后重新完整执行，r6 Unit 只能作为被替代的诊断证据。

第二位 reviewer 使用既有任务身份 `v934_snapshot_skip` 接收本轮 r7c delta-review；
本记录中的 reviewer 名称均为实际任务身份，不使用事后别名。

## Candidate provenance

| Field | Value |
|---|---|
| run | `step2-candidate-r7c-20260715` |
| Git HEAD | `9f5428d8d15d08457d2d2d57296256178c224f5d` |
| run status | `completed / exit_code=0 / passed` |
| started / finished | `2026-07-14T19:16:48Z / 2026-07-14T19:44:58Z` |
| protected source before / after / independently recomputed | `1eb9a2a7cf1d6090bfbdc6e1cc71e0f9d4ff904c6235e85b50070722270fd40c` |
| candidate freeze | `fe0506fa3c4ddc743b89de92162916475ed4b50b18807372151581fe5efdcec7` |
| candidate manifest | `a2ed639f66d406e9ad3b46ed8ceef56ae1fe25bd576981532f67e312f2fecf6c` |
| pending candidate summary | `7c67618ef0a56c0b63bf1a5e984f3af375857721c66514c735ba7f0c72704400` |
| run-status | `b4776c447b46cc4cc6263d27783c7090f55d8f34e5b9b3b32950fff8e32d1826` |
| runner contract | `f9b08a2ea946b88db863970ca06e09b5d6f917b9e47d6e4ed8166531815edf5a` |
| parent link | `97449fd8b879e4ab7354bb3f84a3e9e380bccdba5a9301dbc0993e8860259375` |
| negative-probes TSV | `5fda33670a6d605d7254ecbfdf0cb568bbd6d6b5f9b3f584edb31b78c37039e3` |
| file/hash set | 13/13, all canonical payload hashes passed |
| successor negatives | 24/24 passed with exact expected error code |

两路均独立执行 canonical `validate`、summary validate、哈希与 source guard 复算；
candidate summary 校验通过。

## Independent inventory recomputation

- sources：`532 = 530 reactor + 2 non-reactor`，其中 reactor 为
  `514 executable + 16 helper/generator`；
- discovery：`820 = 804 ClassSource containers + 16 non-executable rows`，其中
  positive reports `745`、structural reports `59`，交集为 `0`；
- positive execution identities：`770 = 724 Step 2 + 46 Step 3`，全局唯一；
- Step 2 ownership：Surefire `677`、Failsafe `47`；
- Step 3 ownership：Surefire `0`、Failsafe `46`；
- structural ownership：Surefire `55`、Failsafe `4`；
- predecessor：`519 = 480 execution refs + 39 structural refs`，全部一对一；
- rename：`35 sources / 64 reports / 76 execution identities`，其中 positive
  `60 reports / 72 identities`、structural `4 / 4`，renamed predecessor 为 `46 + 4`；
- classpath：`2,395` 个 identity/ordinal 全部唯一且顺序稳定，freeze 受审内容变化
  `34 = 1 Mongo + 20 foggy-core + 13 foggy-dataset-model`，未审漂移 `0`。

## r6 to r7 amendment review

`scripts/v934/step2-r7-runner-amendment.tsv` 的 SHA-256 为
`11ff594bf3689112ae0c0cd8be8e68bc6a5be3bfe0150adb8a80485ba3b10ac2`。
successor schema 为 `5`，runner contract schema 为 `3`，semantics 为
`corrective-lane-and-authority-remediation-v5`。两条受审变更为：

1. 根 `pom.xml`：SHA-256 从
   `0bd2e0f4...f8f9f` 变为
   `d3e771a80829f3ca066d484b6a32304846f54b2d2cf8880420137a958b471679`。
   Failsafe `failIfNoSpecifiedTests` 从 literal `true` 改为
   `${failsafe.failIfNoSpecifiedTests}`，同时根属性默认值保持 `true`；只有受审的
   Integration reactor selector 显式覆盖为 `false`，目标集合仍由 report verifier
   对 missing/extra/stale/owner/zero/failure/error/skip 全部 fail-closed；
2. `scripts/verify-v934-integration.sh`：SHA-256 从
   `3ef7...4c651` 变为
   `b0fe2ca91bb4dea2c8363e27bc1c5ae584db56fcc9f2cf80fa02049683ed7373`。
   runner 为选定 reactor Maven 调用传入 override，并在 preflight 后用 trap 固化
   `run.log` 与 `run-status.env`。SIGINT 诊断探针记录
   `exit_code=130 / last_phase=test-compile / status=failed`；该保证不覆盖 trap 安装前
   的 preflight 或不可捕获的 SIGKILL。

聚焦的相同 reactor 命令已运行 `QueryCacheLifecycleRealQueryIT`，结果
`2 tests / 0 failures / 0 errors / 0 skips`，XML SHA-256 为
`cda062fa8c2b7aee4813650a6b67ff5c40a94f0f433c2d3ed12a24e60403ccf5`。

r6→r7 的 source、discovery、execution、structural、Step 2、Step 3、predecessor、
rename 与 discovery-classpath 清单均 byte-identical；532 条 Java source 的逐文件
哈希变化数为 `0`。变化仅限受审 POM、runner/toolchain 与对应派生契约元数据。
24 项负向探针全部通过，包含新增的
`r7-runner-amendment-drift / failsafe-selector-override-drift`。

## Superseded and excluded provenance

r6 归档 payload 与原始 confirmed provenance 一致，且 manifest 13/13 通过：

- freeze：`759383fa65a6da9c06da9c083696fce2d1328efe2a745a3740257762d5db4ed2`；
- manifest：`8913834a5ea70c52c080dba10f9fe2329abcd5aea6123a2751ba9b4d4a059551`；
- confirmed summary：`0575b945d00380322c211ba086a9e73433594b8770b9fd9a23a76c78587ed008`。

r7 supersedes r6，是因为首次 Integration authority 暴露 reactor exact selector 被根
Failsafe literal 配置吞掉，且失败 runner 未完整固化日志/status。失败的
`step2-it-r1-20260715`、有意中断的 `step2-candidate-r7-20260715` 与
`step2-candidate-r7b-20260715` 只保留为诊断证据，不能与后续 authority 拼接。

## Confirmation result

confirm 动作于 `2026-07-14T19:52:48.319718+00:00` 原子完成，reviewer 为
`v934_r6_identity_review+v934_snapshot_skip`。确认后再次执行 canonical validate 与
summary validate，均通过：

- confirmed freeze：`3fea72715f651755897cee4464fd6075d5ea2187672e9d0fe66c97bf6d02a5d6`；
- confirmed manifest：`d0dfc94e1aa9bb8018d6ac6b5ae4b5c73ae25f6f6f81778a64e3f7787e2a3ca2`；
- confirmed summary：`69a94475ca4c9d7162e9e6b217f637026cc790d3de4b654d9d12eb4c0dbe4f61`。

candidate 哈希保留用于完整 provenance 链；此确认只冻结 Step 2 successor identity，
不替代后续 Surefire/Failsafe authority。
