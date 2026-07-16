---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260716-diagnostic-r4
reported_launch_head: ceea084ca25a9d679ba128e3f6bd50a63322c112
status: failed
decision: excluded-from-step4-exit
recorded_at: 2026-07-16
---

# Step 4 coverage diagnostic r4 fail-closed evidence

## Decision

调用方报告 `step4-coverage-20260716-diagnostic-r4` 启动前 HEAD/origin/main 为
`ceea084ca25a9d679ba128e3f6bd50a63322c112`。该值不是 run-owned Git seal。契约、
successor overlay、authority、
logger lifecycle 与 generic XML 负例均通过；outer 随后在发布首份 source inventory 前于
`source-before` fail closed。

本 run 未建立 run-owned Git/source seal，也没有执行 Unit、Integration、database、required
external、Addon、aggregate、model gate 或 threshold observation。`summary.env` 不存在。因此
r4 明确标记为 `excluded-from-step4-exit`，不得进入 threshold freeze、coverage audit、正式验收，
也不得把本轮负例绿色与其他 run 的 lane 结果拼接。

## Immutable result

- outer：`failed / exit_code=2 / last_phase=source-before`；
- `run-status.env` 中 `git_head`、`started_at`、`source_before_sha256`、
  `source_after_sha256` 与 `outer_marker_sha256` 均为空；
- `source-before.tsv`、`source-after.tsv`、`run-context.json`、`git-head.txt`、
  `git-start-status.txt`、`toolchain-receipt.json` 与 `summary.env` 均 absent；
- coverage contract：full contract 通过，structure-only negative=`20/20`，当时的 source
  Git identity negative/control=`7/7`；
- successor overlay：positive 通过，negative=`12/12`；
- authority：positive=`2`、negative=`14`；
- managed logger lifecycle：`14/14`；generic XML fast negative=`63/63`；
- cleanup：container/volume/network=`0/0/0`；
- 所有测试 lane、JaCoCo raw exec、aggregate observation 与 acceptance candidate 均 absent。

## Artifact identity

- outer `run-status.env`：
  `21f9424811d305bf4f451a2d0571f51392efa5a68bd040c2f0fef441419c5f1e`；
- outer `run.log`：
  `d391410c8c3ad902d1e5f245c8da8673976eb64b8b9fc928d042261824bba451`；
- outer `cleanup.env`：
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1`；
- contract negative receipt：
  `767d76955e1ae54b124ebb68265e5cb179b96797b12953eb01fdb18016034ebd`；
- managed logger lifecycle receipt：
  `ed407f15866eda22ffdd4afc03ddc47b33dbb4d588161c12277cb6db50bc66d0`；
- generic XML negative receipt：
  `553f2ee879e309dcee2590477037714e41f50702cdc289d6d1045377b90c8c49`；
- successor overlay negative receipt：
  `cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06`。

原始 ignored evidence 位于：

- `target/v934-step4-coverage/runs/step4-coverage-20260716-diagnostic-r4/`。

## Failure observation boundary

对 r4 同一 commit/worktree 直接重放 `coverage_tool.py source-hash`，得到：

```text
source inventory: worktree executable mode differs at row 1
```

该文本是失败后的直接复现结果，不是 `run.log` 原文。r4 outer 当时在 Bash command
substitution 中捕获了 source-hash 输出，却没有把捕获内容重新写入日志；因此 immutable
`run.log` 只证明前置负例通过并在 source seal 前终止。不得把上述 row 1 文本描述成 r4
日志自带的错误，也不得据此补写缺失的 run-owned Git/source identity。

## Root cause boundary

仓库本地 `.git/config` 设置 `core.fileMode=false`。r4 commit 共跟踪 `3,968` 个文件：Git
index 为 `3,923` 个 `100644`、`45` 个 `100755`；工作树权限分布为：

- Git `100644`：worktree `0644=35`、`0664=436`、`0744=3,431`、`0755=7`、
  `0775=14`；
- Git `100755`：worktree `0744=11`、`0755=9`、`0775=25`。

因此有 `3,452` 个 Git `100644` 文件在工作树上带 executable bit。Git 在
`core.fileMode=false` 下仍将该 checkout 判为 clean；source validator 却要求工作树
executable bit 与 Git mode 完全一致，导致 clean canonical source 在 row 1 被误拒绝。
这是 source identity 的权限语义过度约束，不是内容漂移，也不能通过批量 `chmod`、关闭
source seal 或放宽 blob/path/index identity 来规避。

## Post-r4 remediation status

当前状态为 `in-progress / r4 historical fail-closed / source-policy remediation statically
passed / final review passed B/H/M/L=0/0/0/2 / amend/push + fresh r5 pending`。两项 Low
均 accepted：`/usr/bin/echo` 平台前提漂移会 fail closed；同 UID 视为 build authority，未来
更强隔离改用 readonly snapshot/独立 checkout。修复保留 exact HEAD/index
path+Git mode+blob，将 worktree permission 与 Git mode 分层；worktree 仍须通过 regular
file、exact content、owner/private-primary-group、single-link、stable stat/inode、
non-world-writable/no-special-bit 校验。security Git 调用关闭 fsmonitor/untracked-cache 并
要求 ordinary index flags。tracked FIFO 必须在任何 worktree-aware Git 读取前由 canonical
preflight fail-fast；完整读取前后的 raw stat identity 必须 exact，从而拒绝即使 Git clean
过滤结果等价的 concurrent rewrite。
source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个 candidate，
使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed attributes 若声明
external clean filter，则在任何 worktree-aware Git hash/driver hook 执行前 fail closed，
negative 证明 hook 未执行。

focused/static 结果：contract=`20/20`、source identity=`22/22`、XML=`63/63`、overlay=
`12/12`。declared amendments SHA-256 保持
`1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`；coverage contract
diagnostic/formal SHA-256=
`5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
`58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；successor
manifest=`12/12`，SHA-256=
`751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；top manifest=
`54/54`，SHA-256=
`ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`；coverage tool /
contract-negative / XML tool SHA-256=
`07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
`732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
`b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；overlay contract /
overlay tool / outer SHA-256=
`2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
`a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
`254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`。

这些结果只证明 r4 后修复的静态边界，不改变 r4 的 immutable failed decision，也不是
all-lane coverage evidence。

## Next gate

- [x] 区分 authoritative Git mode 与非权威 worktree permission bits，同时继续精确绑定
  `HEAD/index path + Git mode + blob` 和工作树内容；
- [x] 工作树继续拒绝非 regular、错误 owner/group、不安全 group、multi-link、world-write、
  special-bit、内容漂移与 unstable stat/inode；
- [x] 固定关闭 fsmonitor/untracked-cache 影响并验证 ordinary index flags，补
  file-mode-safe positive 与 world-write/hardlink/special-bit 等 fail-closed 回归；
- [x] tracked FIFO canonical preflight 在 worktree-aware Git 读取前 fail-fast；
- [x] before/after raw stat identity 比较拒绝 Git-clean-equivalent concurrent rewrite；
- [x] contract、runtime validator、XML frozen validator 的 source identity policy 字节一致，
  identity manifest 与完整 negatives/static 通过；
- [x] final review=`ready-with-risks`，B/H/M/L=`0/0/0/2`；
- [x] 两项 Low disposition 均 accepted；
- [ ] 从新的 clean、committed、pushed HEAD 使用唯一 r5 run id 重跑 fresh all-lane
  diagnostic；不得复用 r4 目录或把 r4 前置负例结果拼入新 run；
- [ ] 只有 fresh diagnostic 完整 observation、reviewed exact threshold、独立 freeze commit、
  fresh formal replay、最终质量闸门与 coverage evidence audit 全部通过后，才可判定 Step 4
  exit 或下一阶段开启条件满足。
