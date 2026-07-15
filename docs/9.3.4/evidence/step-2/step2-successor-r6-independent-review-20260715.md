---
doc_role: independent-review-evidence
doc_purpose: Record dual independent review of the Step 2 r6 successor before confirmation.
version: 9.3.4
step: 2
candidate: step2-candidate-r6-20260715
status: passed
reviewers: v934_r6_identity_review+v934_snapshot_skip
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 2 successor r6 independent review

## Decision

`PASS / confirm-approved`，blocker/high=`0`。两名 reviewer 独立、只读复算
candidate，均未修改源码、candidate 或 summary，也未执行 confirm。r6 可以从
`candidate / pending-independent-review` 提升为 `confirmed / passed`；Step 2 的
Surefire/Failsafe authority 仍须在确认后分别通过。

第二位 reviewer 使用既有任务身份 `v934_snapshot_skip` 接收新的 r6 delta-review
任务；本记录中的 reviewer 名称均为实际任务身份，不使用事后别名。

## Candidate provenance

| Field | Value |
|---|---|
| run | `step2-candidate-r6-20260715` |
| Git HEAD | `9f5428d8d15d08457d2d2d57296256178c224f5d` |
| run status | `completed / exit_code=0 / passed` |
| started / finished | `2026-07-14T18:00:37Z / 2026-07-14T18:27:46Z` |
| protected source before / after / independently recomputed | `d42a44d337e98f5e3de805875ef61fd3058552c387b780316307e33459fb7af8` |
| candidate freeze | `58e82e5e8fe6b25d966602cdac98f0248136014bb524ef44832aa6a14309e5a8` |
| candidate manifest | `9d1e04606e82510734ec883e63c5432493251588a21f177a1a7379c4a8def6b9` |
| pending candidate summary | `9ffedd59e721b9cca25def03ed3fd6e0180846ce54eb1a64a50bc22f4e234a25` |
| run-status | `7b19ac17d4bf3354569138274fe16c89d119bfb1acf1194cfa5091d108d4b87a` |
| file/hash set | 13/13, all canonical payload hashes passed |
| successor negatives | 22/22 passed with exact expected error code |

两路均独立执行 canonical `validate` 和哈希复算；candidate summary 校验通过。

## Independent inventory recomputation

- sources：`532 = 530 reactor + 2 non-reactor`，其中 executable reactor `514`；
- discovery：`820 = 804 ClassSource containers + 16 non-executable rows`；
- positive report identities / structural reports：`745 / 59`，交集为 `0`；
- positive execution identities：`770 = 724 Step 2 + 46 Step 3`；
- Step 2 ownership：Surefire `677`、Failsafe `47`；
- Step 3 ownership：Surefire `0`、Failsafe `46`；
- structural ownership：Surefire `55`、Failsafe `4`；
- predecessor：`519 = 480 execution refs + 39 structural refs`，typed XOR violations `0`；
- rename：`35 sources / 64 reports / 76 execution identities`，其中 positive
  `60 reports / 72 identities`、structural `4 / 4`；
- classpath：`2,395` 个 identity/ordinal 全部唯一且顺序稳定，累计受审内容变化
  `34 = 1 Mongo + 20 foggy-core + 13 foggy-dataset-model`，未审漂移 `0`。

## r5b to r6 amendment review

`scripts/v934/step2-r6-source-amendment.tsv` 的 SHA-256 为
`0751dcd49d20f9722f9aab8d532db3ca105b20cd7970dc3a9d6169b614189c7d`，
schema 为 `4`，semantics 为
`corrective-lane-and-authority-remediation-v4`。清单精确包含三条受审变更：

1. `EmbeddingServiceTest.java`：源码变为
   `8f263147183609d9cbb8ff3bf802fb93f08dd2825c42bd5465e16c0e91b5a4b2`，
   vector test-classes 变为
   `feb299dee8b6a25e272e0ab07b1e002d4bb1ae5a69b713b4c74fac376701786a`，
   discovery 保持 `15` nodes。本地 loopback HTTP fixture 不读取 API Key、不访问公网，
   focused 结果 `15/0/0/0`；
2. `JavaQueryModelAggregateJoinSnapshotTest.java`：源码变为
   `727319eed18283a813fca6f5eda71893030811bdf642caa014477768f13c00e0`，
   model test-classes 变为
   `07d3e021d6ffe8b2fe5c9303fa99fe657f6fcebe175f7bb38ca960c2b8e56f42`，
   discovery 保持 `1` node。29-case 行为断言默认执行，property 仅控制文件写出，
   focused 结果 `1/0/0/0`；
3. `PredefinedCalculatedFieldInjector.java`：源码变为
   `24eeea7ee4bec78312dac7dfc71f17b2ceba46ab27c9bfeccbd7f7910fd92d5b`，
   model main-classes 变为
   `2913758f4eea56f89ac92bfda52446e5995640af8326dc7a11e36e196acb9496`。
   在 `removeIf/addAll` 前复制并回设 request list，精确修复公开 DTO 接收不可变
   `List` 的生产缺陷；该 main tree 在 classpath 中出现并受审 `13` 次。

r5b 到 r6 的 execution、required/deferred、structural、migration、runner TSV 全部
byte-identical；discovery identity/count 不变。classpath identity/order 不变，仅上述
`13` 条 model main-classes 内容哈希变化。22 项负向探针全部通过，包含新的
`r6-source-amendment-drift / E_PARENT_LINK`。

## Superseded provenance

r5b 的归档 payload 与原始 confirmed provenance 一致，且 manifest 13/13 通过：

- freeze：`4a505ae0fdcb7c09ebe9c6562ccd29a1bb9ebf80a97bde11fbcfe4357bab7497`；
- manifest：`ff45fb9bd6ac20d172fee44c771818e9193ad5ec3df2d3475c05e9fa73fb835f`；
- confirmed summary：`53d8c66846a8f2815eb2b8de339a724940681ef0a9057491695060bfc362c52b`。

r6 supersedes r5b 的原因是首次 r5b Unit authority 暴露两个 zero-skip blocker，
并在启用 snapshot 默认断言后暴露不可变 calculated-field list 的生产缺陷。失败运行
只作为诊断证据，不能与后续 authority 拼接。

## Confirmation result

confirm 动作于 `2026-07-14T18:41:37.379351+00:00` 原子完成，reviewer 为
`v934_r6_identity_review+v934_snapshot_skip`。确认后再次执行 canonical validate 与
summary validate，均通过：

- confirmed freeze：`759383fa65a6da9c06da9c083696fce2d1328efe2a745a3740257762d5db4ed2`；
- confirmed manifest：`8913834a5ea70c52c080dba10f9fe2329abcd5aea6123a2751ba9b4d4a059551`；
- confirmed summary：`0575b945d00380322c211ba086a9e73433594b8770b9fd9a23a76c78587ed008`。

candidate 哈希保留用于完整 provenance 链；此确认只冻结 Step 2 successor identity，
不替代后续 Surefire/Failsafe authority。
