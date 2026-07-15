---
doc_role: independent-review-evidence
doc_purpose: Record dual independent review of the Step 2 r4 successor before confirmation.
version: 9.3.4
step: 2
candidate: step2-candidate-r4-20260714
status: passed
reviewers: v934_r3_full_review+v934_r3_delta_review
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Step 2 successor r4 independent review

## Decision

`PASS / confirm-approved`，blocker=`0`。两名 reviewer 全程只读，未修改
candidate、summary、源码，也未执行 confirm。本结论确认 successor 契约与两处测试
修正的边界；Step 2 全量 Surefire/Failsafe authority 仍须另行通过。

## Candidate provenance

| Field | Value |
|---|---|
| run | `step2-candidate-r4-20260714` |
| run status | `completed / exit_code=0 / passed` |
| started / finished | `2026-07-14T15:41:53Z / 2026-07-14T16:05:39Z` |
| protected source before / after / current | `84cf8c49d7f711d7be52b9393d225867cf6e911971d890d7cee1ecb59e4706ee` |
| candidate freeze | `b476486eb7a651b684f4ac5cb7eef33ff8707d738eb851315ef78bc3531e443c` |
| candidate manifest | `b35db16a65a6292f04435253aa8498481d9032d4ea1cccc345f32fd9d5e8d18c` |
| candidate summary | `24775cebd24534bd97ffab2a32c32f5bbb3f49602d3210549a498cbbb827fddc` |
| file/hash set | 13/13, all `SHA256SUMS -c` passed |
| expected-negative | 19/19 passed with exact error code |

两路复核均独立执行 `validate` 与 `validate-summary`，结果为 exit `0`。

## Independent recomputation

- sources：`532`，其中 reactor `530`、executable reactor `514`；
- discovery：`820 = 804 reports + 16 reviewed-none`；
- positive/structural：`770 / 59`，集合完整且交集为 `0`；
- Step 2/Step 3：`724 / 46`，交集、缺失、额外均为 `0`；
- positive runner ownership：Surefire `677`、Failsafe `93`；
- Step 2 runner ownership：Surefire `677`、Failsafe `47`；
- structural ownership：Surefire `55`、Failsafe `4`；
- predecessor：`480 execution + 39 structural = 519`，row-level XOR violations `0`；
- rename：`35 sources / 64 reports / 76 keys`，其中 positive `60 reports / 72 keys`
  与 structural `4 reports`；
- renamed predecessor：positive `46`、structural `4`。

## r3 to r4 amendment review

`scripts/v934/step2-test-source-amendment.tsv` 的 SHA-256 为
`771b08b3825778f5aab6f656e15de6dc568b8070811451e5b5c010f44ee69368`，
且恰好包含两条受控测试源码修正：

- `CalculateMvpIT` 保持真实 `JdbcModelQueryEngine` 路径，只在 capability seam 注入
  MySQL dialect 与 MySQL 5 metadata，确定性断言
  `CALCULATE_WINDOW_UNSUPPORTED`；聚焦类结果为 `14/0/0/0`；
- `DataViewerApiSmokeTest` 只在嵌套测试应用中为目标 namespace 显式绑定 SQLite
  datasource，未开启全局回退；聚焦类结果为 `6/0/0/0`，生产 fail-closed 未放宽。

两处当前源码 SHA-256 分别为
`21d8d817f8d3ca4554f87ddba13438c1e9765b2f7980b829c13ff1b23cebc0c4` 与
`a8cd13c3eff498ada6f7426a7f6bc4b7a0e03b64d92a06862e888a28145efa09`；
对应 test-classes tree SHA-256 分别为
`099bec240a18627d810ee8aa88f3662ee0e54032ca0976bb5114efe49ad9c16d` 与
`f0db90b2bdf3dc516bbc88e9c13558dcdd6f7ca4ac19ceb6a15caf92e744aba7`。

r3→r4 的 820 条 discovery 中，node、FQCN、report、execution identity 均零漂移；
仅两处 source hash 与所属模块的 test-classes hash 改变。source、execution、required、
deferred、structural、rename、predecessor、runner 清单均 byte-identical。2,395 条
classpath data rows byte-identical，文件 SHA-256 为
`9f88b0cb291a5ee015ff8ddb1b4518ecfd8d5ecb901d1fadc6ff57e283311c00`。

## Superseded archive review

r3 已由 wrapper 原子归档到 r4 run 的 `superseded-successor/`，13/13 payload hashes
全部有效，且 provenance 精确匹配 r3 的最终 confirmed hashes：

- freeze：`28dfeba2f477544e7e55f520665c07266540b9221e4dd7cc0670f792638a585f`；
- manifest：`631dfad5955caa25aca239dd186f06e69352318fcee7af28536335db3d1c4698`；
- confirmed summary：`f87fcfeed93b2a6bed480eecaa8f1f2d218648467f55284249558882e8e8e3c5`。

两名 reviewer 均确认 r4 可进入 confirm；Mongo loader、Data Viewer namespace fixture
与 CALCULATE zero-skip 三个 work item 仍须分别由后续 Surefire/Failsafe authority 关闭。
