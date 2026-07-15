---
doc_role: step-exit-evidence
doc_purpose: Preserve the superseded r8d Surefire/Failsafe execution and its invalidation reason.
version: 9.3.4
step: 2
status: superseded
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 2 runner split exit evidence（superseded）

> 本记录绑定的 r8d authority 在 formal implementation quality review 中被
> `completed` 窗口信号 fail-open 缺陷作废。其 successor、Unit 与 Integration
> 结果只保留为历史诊断，禁止用于当前 Step 2、coverage 或 acceptance。当前唯一
> Step 2 exit 证据为
> [step2-runner-split-exit-r8e-20260715.md](step2-runner-split-exit-r8e-20260715.md)。

## Decision

历史机械结果为 `PASS`，但 authority 判定已被后续 signal probe 撤销。confirmed r8
successor、Surefire Unit authority 与
Failsafe Integration authority 均独立、完整执行并以 exit `0` 结束；两个 runner 的
execution/report sets 互斥且并集 exact 覆盖 confirmed Step 2 positive inventory：

- positive executions：`724 = 677 Surefire + 47 Failsafe`；
- structural reports：`59 = 55 Surefire + 4 Failsafe`；
- raw reports：`783 = 732 Surefire + 51 Failsafe`；
- testcase nodes：`5,205 = 4,890 Surefire + 315 Failsafe`；
- failures/errors/skipped：`0/0/0`；
- report mutation probes：两条 authority 各 `20/20` passed；
- source before/after：均为
  `57a56807a285fc221554f39c3b549738e69b7879c00965a77eda05ef8c2c66ec`。

Step 2 不消费 `46` 个 confirmed Step 3 deferred Failsafe executions；它们仍保持
`deferred-to-step3`，不能由本记录提前标 pass。

## Confirmed successor basis

authority runs 均绑定 `step2-candidate-r8d-20260715` 的 confirmed generation：

| Evidence | SHA-256 |
|---|---|
| confirmed freeze | `1a725e1565bce923b3f6a62e0328e5b4a43f6af68b04463c422ecf0e6275e402` |
| confirmed manifest | `d24569b7caee1af87db7a2edaaeaf230a5455e82a3996a43669b7c9172df781b` |
| confirmed summary | `f342d4995a671347a3ef9e80633c42f725aa684042435ef30083e544d0150af5` |
| Step 2 required execution set | `42a9467cdbcfbed5ed54d0bdfa276d92daa7fa2c83795cd13a21df931d0fc1d0` |
| structural report set | `dd885e54c1ef3dc01d2a4cb2b364f6333b78d362eb7f5b9a7d32968516809e0f` |
| Step 3 deferred set | `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` |
| runner contract | `8a0af5f5f57e1c36806ea0ccb1e21b0816c588ee633e4ac3abfd8f64f5838e23` |

successor inventory 为 `532 sources / 820 discovery rows / 770 positive executions /
59 structural reports / 519 predecessor edges / 2,395 classpath entries`；32/32 successor
mutation probes passed。独立复核与 publication seal 见
`step2-successor-r8-independent-review-20260715.md`。

## Surefire Unit authority

Run：`step2-unit-r8-20260715`，时间
`2026-07-14T22:38:29Z` 至 `2026-07-14T22:48:50Z`。

| Field | Value |
|---|---|
| runner / variant | `surefire / unit` |
| execution reports | `677 / 677 exact` |
| structural reports | `55 / 55 exact` |
| raw reports | `732` |
| tests / testcase nodes | `4,890 / 4,890` |
| failures / errors / skipped | `0 / 0 / 0` |
| outer marker | `65cd6890dc212c9c4b334142c925d49423a5f61ca5971c724d82d17249203b4f` |
| final report manifest | `9e062384be7a1bf9f940de10c46fd83fd83e63e52bbd66a2d4c0a93aa5394bf9` |
| run status | `e4644cfe5dc76fd25fbd36897e3b525054dc287a785893edd937369fdacc0a1f` |
| summary | `23e4870b632af695ef26030b944fece73d75cf956312208f517c984b5d07c6e4` |
| run log | `c6ced11c6742c4a53e59a917ea79159136acced0e4030b78bb30b1e2bb2a7fbc` |
| report negatives | `e38ad3f408e385b6ba0ea36aa303e7b8ed4b20b7b6b8059f283b624d28241756`；`20/20` |

`run-status.env` 为 `completed / exit_code=0 / passed`，并封存 source before/after、
outer marker、confirmed successor manifest 与 final report manifest。report schema=`3`；
static report 的 `actual testcase nodes == discovered_test_nodes`，structural report 必须
为 zero testcase 且具有 frozen positive sibling。

## Failsafe Integration authority

Run：`step2-it-r8-20260715`，时间
`2026-07-14T22:49:14Z` 至 `2026-07-14T22:59:27Z`。

| Field | Value |
|---|---|
| runner | `failsafe` |
| variants | `caffeine-sqlite, hermetic, sqlite-broad, sqlite-harness, sqlite-lifecycle, sqlite-refresh` |
| execution reports | `47 / 47 exact` |
| structural reports | `4 / 4 exact` |
| raw reports | `51` |
| tests / testcase nodes | `315 / 315` |
| failures / errors / skipped | `0 / 0 / 0` |
| outer marker | `491d8c1324c292971c91191806416583b87e3d4135115cb703acaae14a807ae5` |
| final report manifest | `457f8f3d767f3df5603db3d8fa2a7c24d1191fe4e2a45dd95d76d5d3eb0566d0` |
| run status | `5bfdd0cefdc71f6d73a76aad92fca150431211080098f284228e92c98470a964` |
| summary | `618a28f58df6383c7b4ec0e8c9f041d6b2b20fe27109ce9dbae4416facd384ff` |
| run log | `6420e2d08b5d8e820fcaaab4d89cd620a0c2935d6244037be8a94ad1c3961f7e` |
| report negatives | `e38ad3f408e385b6ba0ea36aa303e7b8ed4b20b7b6b8059f283b624d28241756`；`20/20` |

Variant exact results：

| Variant | Executions | Structural | Tests | F/E/S |
|---|---:|---:|---:|---:|
| `caffeine-sqlite` | 1 | 0 | 2 | 0/0/0 |
| `hermetic` | 1 | 0 | 3 | 0/0/0 |
| `sqlite-broad` | 42 | 4 | 303 | 0/0/0 |
| `sqlite-harness` | 1 | 0 | 1 | 0/0/0 |
| `sqlite-lifecycle` | 1 | 0 | 4 | 0/0/0 |
| `sqlite-refresh` | 1 | 0 | 2 | 0/0/0 |

每个 variant 在执行前清除全 reactor Failsafe XML，使用独立 typed marker/context，
owner reactor 允许 selector helper module zero-tests，但最终 report verifier 对该 variant
的 exact execution/structural set、raw XML cardinality、F/E/S 与 cross-run provenance
fail-closed。merge 只接受同一 outer marker 下的 6 个 manifest。

## Fail-closed and exclusion record

- root POM 默认 Surefire/Failsafe `failIfNoTests=true`；runner 的 helper override 不改变
  owner report exact-set gate；
- Unit 与 Integration 使用同一 git-dir authority lock，执行期间无法并发修改 shared
  test bytecode、reports 或 successor canonical；
- `run-context.json`、variant marker、run-status、summary、final manifest 与 source guard
  均为同一 run 生成；跨 run、mtime-only、partial testcase、zero-test positive report、
  missing/extra/stale XML 均由 report mutation probes证明会被拒绝；
- `step2-unit-r7-20260715` 虽有 4,890 tests/0 F/E/S，但缺少 r8 typed status/report
  authority，只保留为 diagnostic；
- r8/r8b/r8c successor failures 与 ancestry probe 均不属于 authority evidence；
- 本次执行没有启动 MongoDB/Redis/PostgreSQL/MySQL/SQL Server 等 Step 3 external
  fixtures；相关 `46` executions 仍由 Step 3 单独负责。

## Exit

Step 2 的 predecessor、runner split、rename、actual execution、structural evidence、
source immutability、negative probes 与 durable run-status 全部满足 contract。Step 2 可标
`passed`；Step 3 可从 `pending` 进入 `ready`，但在 external matrix 完成前不得标
`in-progress/passed` 或提前声称整个 9.3.4 完成。
