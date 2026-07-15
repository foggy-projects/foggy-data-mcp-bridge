---
doc_role: independent-review-evidence
doc_purpose: Preserve the superseded r8d successor review and its invalidation reason.
version: 9.3.4
step: 2
candidate: step2-candidate-r8d-20260715
status: superseded
reviewers: v934_r6_identity_review+v934_snapshot_skip
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 2 successor r8 independent review（superseded）

> r8d 后续被 `completed` 窗口 signal fail-open 动态探针作废。本记录不得作为当前
> confirmation、coverage 或 acceptance 依据；替代证据为
> [step2-successor-r8e-independent-review-20260715.md](step2-successor-r8e-independent-review-20260715.md)。

## Decision

`PASS / confirm-approved`，Blocker/High/Medium/Low=`0/0/0/0`。两名 reviewer
分别以只读方式执行 canonical `validate` 与 `validate-summary`，独立复算 inventory、
source、runner、negative probes、outer publication seal 及 archived exact-r7 provenance；
均未修改 candidate/summary，也未执行 confirm、Unit、Integration 或外部 fixture。

候选 `step2-candidate-r8d-20260715` 可以从
`candidate / pending-independent-review` 提升为 `confirmed / passed`。该确认只冻结
Step 2 runner/evidence authority，不替代后续 Surefire/Failsafe actual execution。

## Candidate provenance

| Field | Value |
|---|---|
| run | `step2-candidate-r8d-20260715` |
| Git HEAD | `9f5428d8d15d08457d2d2d57296256178c224f5d` |
| started / finished | `2026-07-14T21:47:13Z / 2026-07-14T22:26:50Z` |
| outer status | `completed / exit_code=0 / passed / publish-validated / not-required` |
| protected source before / after / independently recomputed | `57a56807a285fc221554f39c3b549738e69b7879c00965a77eda05ef8c2c66ec` |
| candidate freeze | `d2271c88678427884a5aa6ccea8bcf50b3ba0de99ddcd0685a18d52d7a82af92` |
| candidate manifest | `a69f4bd9c644215ca11e7419f43b42dc21e4a6da7855a26d78ef6917070840b0` |
| pending candidate summary | `ec118135e5090a36705d9060e3016a320b9f79eed49f3056c75cfbae8aa8c636` |
| runner contract | `8a0af5f5f57e1c36806ea0ccb1e21b0816c588ee633e4ac3abfd8f64f5838e23` |
| parent link | `9ef46839347a5031271db8afd5284041368d97f051d6ff4c99066b64534dfbf2` |
| negative probes | `711af750913ef55eedd17cdea3e1fa96bc9df0498a68be1c8da8ba0ba1699e46`；`32/32` passed |
| canonical file/hash set | `13/13` self-consistent |

两路官方 validator 均 exit `0`，并得到
`532 sources / 820 discoveries / 770 executions / 519 predecessors`；candidate summary
校验通过，freeze/summary 均正确保持 `candidate / pending-independent-review`。

## Independent inventory recomputation

- sources：`532 = 530 reactor + 2 non-reactor`，其中
  `514 executable + 12 helper + 4 generator + 2 excluded`；
- discovery：`820 = 804 ClassSource containers + 16 non-executable rows`；
- positive execution identities：`770 = 724 Step 2 + 46 Step 3 deferred`，分区互斥；
- Step 2 ownership：Surefire `677`、Failsafe `47`；
- Step 3 ownership：Failsafe `46`；
- structural reports：`59 = 55 Surefire + 4 Failsafe`；
- predecessor：`519 = 480 execution refs + 39 structural refs`，typed XOR 成立；
- classpath：`2,395` 个 module/ordinal/identity 唯一且 ordinal 连续；
- rename：`35 sources / 64 planned reports / 76 planned execution identities`，其中
  positive `60 reports / 72 identities`、structural `4 / 4`；
- negatives：`32/32` probe 名唯一，全部 `passed` 且
  `expected_error == actual_error`。

## r7 to r8 authority amendment

`scripts/v934/step2-r8-authority-amendment.tsv` SHA-256 为
`0fb2f74260cebc233acb99500d64e381c98329f0899f39089a25b08a580f6691`，
精确包含 6 个受审 leaf：root POM、shared authority lock helper、Unit runner、
Integration runner、report tool 与 successor wrapper。successor schema=`6`、runner
schema=`4`、report manifest schema=`3`、publish CAS schema=`2`。

受审治理结果：

1. Surefire/Failsafe 默认 `failIfNoTests=true`；authority runner 只在 selected helper
   reactor 上显式放宽，owner exact report set 仍对 zero/missing/extra/stale/F/E/S
   fail-closed；
2. successor、Surefire、Failsafe 与 confirm 使用同一 git-dir nonblocking exclusive
   lock；并发 writer 在接触 run root、protected source 或 shared target 前失败；
3. Unit/Integration 以 typed outer/variant context、atomic run-status 与 schema-v3 report
   evidence 绑定同一 run，拒绝跨 run 拼接；
4. report verifier 对 static/deferred/structural discovery cardinality、raw XML、merged
   manifest 和 20 项 report mutation probes 进行复验；
5. r7 publication 由 startup/prepublish/post-archive 三段 exact CAS 保护；INT/TERM/HUP
   映射非零退出，EXIT cleanup 不可重入，swap/status-seal/postvalidate 失败均恢复并再次
   证明 exact r7；genesis publication 被拒绝；
6. confirm 必须在同一 authority lock 下绑定 passed outer status、非空 run log、exact
   superseded provenance 与 self-consistent exact-r7 archive，再把 publication hashes
   写入 confirmed freeze/manifest/summary。

最终受审 toolchain SHA：successor tool
`41b018abce5562c1815db43a8c63471c488022c92270c4e810646d2b6ab18692`，
successor wrapper
`fc663b3d12a0500eeed034705021ac44506999adb7ae435fdd18249975b57eb4`，
report tool
`ec478fac5eab355e1aed99c2b7a8934f0d5103425abfdd3f15f6f0733977c1ea`。

## Publication seal and superseded provenance

实际 r8d outer publication seal 可完整复算：

- `run.log`：`eb10a77d76b5afd74f61a407f7dc9c04c9bd50dd65bc5223f9624c864e032756`；
- `run-status.env`：`1d1ebab672bb72f24e8f68c7e101eb96920495977c71fa3e896e59535e144d64`；
- `superseded-provenance.env`：
  `6b6e8d5a3f9b2fa27995a81dfe48827a12852d577b2c8d7289a1e0913273e101`。

r7 archive 是非 symlink 实目录，13/13 manifest 自洽，且与原确认记录 exact：

- archived r7 freeze：`3fea72715f651755897cee4464fd6075d5ea2187672e9d0fe66c97bf6d02a5d6`；
- archived r7 manifest：`d0dfc94e1aa9bb8018d6ac6b5ae4b5c73ae25f6f6f81778a64e3f7787e2a3ca2`；
- original r7 confirmed summary：
  `69a94475ca4c9d7162e9e6b217f637026cc790d3de4b654d9d12eb4c0dbe4f61`。

## Excluded diagnostics

以下运行均 fail-closed，不能与 r8d 拼接：首次 r8 在 discovery 有意中断；r8b 在
test-compile 中断并记录 `exit=130 / pre-archive`；r8c 在 candidate generation 暴露
r7→r8 current-leaf ancestry guard 缺陷并以 exit `1` 结束。复用 r8c discovery 的
`probe-r8-ancestry-20260715` 仅用于验证修复与 32/32 negatives，不是 authority run。
所有失败/探针运行均未发布 candidate；r8d 从当前源码独立重做 compile、scan、
discovery、negative probes 与 publish validation。

## Confirmation result

confirm 于 `2026-07-14T22:36:38.579241+00:00` 在 shared authority lock 内原子完成，
reviewer 为 `v934_r6_identity_review+v934_snapshot_skip`。confirm 先复验 candidate 与
outer publication evidence，再写 confirmed freeze→manifest→summary，最后再次执行
canonical validate/validate-summary；全部通过：

- confirmed freeze：`1a725e1565bce923b3f6a62e0328e5b4a43f6af68b04463c422ecf0e6275e402`；
- confirmed manifest：`d24569b7caee1af87db7a2edaaeaf230a5455e82a3996a43669b7c9172df781b`；
- confirmed summary：`f342d4995a671347a3ef9e80633c42f725aa684042435ef30083e544d0150af5`。

confirmed freeze/summary 已封存 publication evidence schema `1` 以及本记录所列
run-log、run-status、superseded provenance、archived r7 freeze/manifest hashes。
Step 2 successor identity 现为 `confirmed / passed`，后续 Unit/Integration authority
必须完整重跑并 exact compare 该 r8 generation。
