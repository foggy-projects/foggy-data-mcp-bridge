---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-SOURCE-INVENTORY-FILEMODE-FALSE
severity: major
status: in-progress
reproduction_status: confirmed
test_strategy: source-identity-negative-test
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 source inventory 误拒绝 core.fileMode=false 的 clean checkout

## Background

调用方报告 Step 4 r4 启动前 HEAD/origin/main 为
`ceea084ca25a9d679ba128e3f6bd50a63322c112`；该值不是 run-owned Git seal。前置契约和负例
通过后，outer 在
`source-before` 以 exit code `2` fail closed；首份 source inventory、run context 与所有
测试 lane 均未发布。失败后的同 commit/worktree 直接重放返回：

```text
source inventory: worktree executable mode differs at row 1
```

该 row 1 文本来自直接复现，不是 r4 `run.log` 原文。outer 当时捕获但未重放
command-substitution 输出，r4 的 run-owned `git_head/source_before_sha256` 也保持为空；证据
记录不得反向补写这些缺失字段。

## Reproduction

仓库本地 `.git/config` 明确设置 `core.fileMode=false`。r4 commit 的 `3,968` 个 tracked
file 中，Git index 为 `3,923 × 100644 + 45 × 100755`；工作树实际权限为：

- `100644 -> 0644=35 / 0664=436 / 0744=3,431 / 0755=7 / 0775=14`；
- `100755 -> 0744=11 / 0755=9 / 0775=25`。

Git status 在 r4 启动时为 clean，且 `HEAD == origin/main`。现有 source validator 将 Git
mode 的 executable bit 与工作树 `stat` executable bit 直接比较，于第一个不一致 row 即
失败；全仓共有 `3,452` 个 `100644 -> executable worktree` row，因此不是单文件偶发漂移。

r4 immutable result：

- run：`step4-coverage-20260716-diagnostic-r4`；
- outer：`failed / exit_code=2 / last_phase=source-before`；
- source-before/after、run-context、run-owned Git head/status、summary 全 absent；
- Unit、Integration、database、external、Addon、aggregate、model gate 全部未执行；
- cleanup container/volume/network=`0/0/0`；
- decision=`excluded-from-step4-exit`。

## Root Cause

Git mode 只表达 tracked regular file 的 `100644/100755` identity。在
`core.fileMode=false` 的文件系统/checkout 上，Git mode 仍应由 `HEAD` 与 index 精确绑定，
但工作树额外或缺少 executable bit 不构成 Git 认可的 source drift。validator 把两层语义
合并成“permission bits 必须完全映射”，将 canonical clean checkout 判成非法。

修复不能简单删除 permission 校验。Git `HEAD/index path + mode + blob`、ordinary index
flags、工作树 exact content、canonical regular-file/owner/link/stat 安全边界仍须 fail closed；
只应解除 worktree executable bit 与 authoritative Git mode 的错误等价关系。

## Expected vs Actual

- Expected：`core.fileMode=false` 下，Git `100644` 对安全的 executable worktree permission、
  Git `100755` 对安全的 non-executable worktree permission 均可生成同一 canonical source
  identity；Git mode、index/blob/path 和工作树内容仍逐项精确验证。
- Expected：world-writable、special-bit、hardlink、错误 owner/group、非 regular file、内容
  不同、非 ordinary index flags、fsmonitor-valid shortcut 或 unstable stat/inode 必须失败。
- Actual：内容与 Git/index identity 未漂移，仅 worktree executable bit 不同就被拒绝；
  r4 无法发布 source-before seal，也无法进入任何 test lane。

## Test Strategy

`automation_decision=required`：

1. positive control 覆盖 `Git 100644 -> worktree 0775`，仅在 owner 为当前 euid、group 为
   已证明无其他成员的 private primary group 且其他安全条件满足时接受；
2. positive control 覆盖 `Git 100755 -> worktree 0644`，证明 worktree executable bit 不参与
   Git mode identity；
3. negative control 覆盖 world-writable、setuid/setgid/sticky special bit、hardlink、
   non-regular/symlink、owner/group 不可信、blob/content mismatch 与 stat/inode 漂移；
4. 所有 security Git 调用固定 `core.fsmonitor=false`、`core.untrackedCache=false`，以
   `git ls-files -f` 证明 index flags 全为 ordinary `H`，并把 flags receipt 纳入 source seal；
5. Git `HEAD/index` 仍逐 path 精确比较 mode/blob，工作树逐 file 做 canonical、owner、group、
   single-link、non-world-writable、no-special-bit 与 exact content 校验；
6. contract、coverage runtime 与 XML frozen replay 使用同一显式 policy/header，任一 policy
   或 receipt 漂移都 fail closed；
7. source identity positives/negatives、contract/XML/overlay 全量负例、manifest identity 与
   实现质量闸门通过后，才允许提交并发起新的 fresh diagnostic；
8. outer 必须把 source-hash 捕获的错误写入 run log，但日志可观测性不得代替 typed
   run-status 或 source seal；
9. tracked FIFO 必须由 full-path canonical preflight 在任何 worktree-aware Git 读取前
   fail-fast，不能等待 Git/filter 读取特殊文件；
10. 每个 tracked file 的完整读取前后 raw stat identity 必须 exact；即使某次并发重写可被
    Git clean 过滤成同一 blob，也必须作为 concurrent rewrite 拒绝；
11. source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个
    candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed
    attributes 若声明 external clean filter，则在任何 worktree-aware Git hash/driver hook
    执行前 fail closed，negative 证明 hook 未执行。

## Remediation Verification

source-policy remediation 已实现并静态通过：

- contract=`20/20`、source identity=`22/22`、XML=`63/63`、overlay=`12/12`；
- declared amendments SHA-256 不变：
  `1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`；
- coverage contract diagnostic/formal SHA-256：
  `5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
  `58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；
- successor manifest=`12/12`，SHA-256=
  `751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；
- top manifest=`54/54`，SHA-256=
  `ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`；
- coverage tool / contract-negative / XML tool SHA-256=
  `07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
  `732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
  `b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；
- overlay contract / overlay tool / outer SHA-256=
  `2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
  `a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
  `254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`。

当前状态仍为 `in-progress / r4 historical fail-closed / source-policy remediation statically
passed / final review passed B/H/M/L=0/0/0/2 / amend/push + fresh r5 pending`；两项 Low
均 accepted：`/usr/bin/echo` 平台前提漂移会 fail closed；同 UID 视为 build authority，未来
更强隔离改用 readonly snapshot/独立 checkout。静态绿色不替代 fresh all-lane
diagnostic，`can_enter_coverage_audit=no`，Step 5 关闭。

## Fix Checklist

- [x] 封存 r4 source-before fail-closed、artifact hashes/counts 与 cleanup 证据。
- [x] 确认 row 1 错误来自直接复现而非 immutable `run.log`。
- [x] 确认 `.git/config core.fileMode=false` 与 `3,968/3,452` 全仓统计。
- [x] 排除通过批量 `chmod`、跳过 source seal 或复用 r4 证据绕过问题。
- [x] 实现 Git mode 与安全 worktree permission 分层验证。
- [x] 补齐 file-mode positive 及 permission/index/fsmonitor fail-closed 回归。
- [x] NSS passwd census 拒绝另一用户共享当前 primary gid（`shared-primary-gid`）。
- [x] NSS group record 拒绝 foreign explicit member（`explicit-foreign-primary-group-member`）。
- [x] tracked source 拒绝非当前 egid（`tracked-source-foreign-gid`）。
- [x] tracked FIFO preflight 在任何 worktree-aware Git 读取前 fail-fast。
- [x] before/after raw stat identity 拒绝 Git-clean-equivalent concurrent rewrite。
- [x] 同步 contract/runtime/XML policy。
- [x] 刷新 successor/top identity manifests 并通过 `12/12 + 54/54`。
- [x] 完整 contract/source/XML/overlay negatives/static 通过。
- [x] 最终复核=`ready-with-risks`，B/H/M/L=`0/0/0/2`。
- [x] 两项 Low disposition 均 accepted。
- [ ] amend/push 后证明 clean worktree 且 `HEAD == origin/main`。
- [ ] 从新的 clean/pushed HEAD 完成唯一 fresh all-lane diagnostic。

## References

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md`
- `scripts/v934/step4/coverage_tool.py`
- `scripts/v934/step4/coverage_contract_negative_tool.py`
- `scripts/v934/step4/coverage-contract.json`
- `scripts/v934/step4/coverage_xml_tool.py`
- `scripts/verify-v934-step4-coverage.sh`
