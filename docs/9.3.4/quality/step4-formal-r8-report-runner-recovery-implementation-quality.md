---
review_type: implementation-quality
version: 9.3.4
step: 4
scope: formal-r8 report-runner portability recovery
status: passed
decision: ready-for-new-Cdiag
reviewed_at: 2026-07-19
---

# Step 4 formal-r8 report-runner recovery implementation quality

## Decision

本次最小 recovery implementation quality=`PASS`，B/H/M/L=`0/0/0/0`，mandatory finding=`0`。
报告 runner 的三个错误 command positions 已统一使用显式 `python3`；Git `100644` Python tools
继续保持 non-executable，不以 chmod 或主工作树偶然的 executable bit 作为运行前提。

该结论只放行一个原子 new Cdiag checkpoint，不把 formal-r8 修补成成功态，也不授权 Step 5、
coverage audit、feature acceptance、9.3.5 或 9.4.0。current-state 文档独立复核也已 PASS。

## Review basis

- formal-r8 固定为 `failed / excluded / non-reusable / non-candidate`；failure capsule=
  `9003f11fbd74eb0741f729cd0002e5c4d93ad97844d97544b250bd5d7a0be4e5 / 16 entries /
  282473 bytes / sensitive 0`；
- runtime delta 仅为 `coverage_report_runner.sh` 三个 direct command positions 增加显式
  interpreter；runner SHA-256=
  `a83ed709ccbbf152cbbeba8d25c2fffb0bd3343ba5bf86a7a0a627562bad4d12`；
- negative contract SHA-256=
  `4a988edb028300035ab234060d3ae671c9ea603f2d1316ee598d313c46ccc63d`；production 默认同时绑定
  runner raw SHA 与 `292` 条 logical executable command stream SHA=
  `186d986fe6a13af7435190f6fbc8dcbacf614c9a53906e2ac48f18d140eb46e8`；
- 四个 tool target、七个 top-level interpreter calls、十六个 target-reference commands exact；
  direct command-position count=`0`；
- raw/stream/semantic mutation=`44/44 / 43/43 / 33/33`。33 个 semantic probes 显式关闭两层
  source seal 后仍以 `30 COMMAND + 3 SCOPE` 拒绝；11 个 dynamic/heredoc probes 均由 raw seal
  拒绝，其中 inline-Python heredoc direct call 的 shell stream unchanged、semantic accepted，证明
  raw seal 是必要且有效的独立层；
- Git-mode mutation=`4/4`，non-executable smoke=`4/4`：Git=`100644`、copy=`0644`、direct=
  permission denied、`python3 --help`=passed；
- Step 4 manifest=
  `6a48ab012fb660bf6459bec76840c3f7d41ccbe08325bb104e81934c6e1d0782 / 61/61`；Step 6
  manifest=`d1efe0310cfef1c992482d168f75a1a4cab5b92c1f33e37e7d5b88e2a49543bd / 16/16`；
- coverage contract=`diagnostic-ready`，threshold=`diagnostic-pending`；未修改 production Java、Java
  test、POM、selector、report/testcase cardinality、coverage floor、critical class、exclusion、database
  matrix 或 public API。

## Independent review findings

| Severity | Open | Disposition |
|---|---:|---|
| Blocker | 0 | none |
| High | 0 | none |
| Medium | 0 | selective parser finding closed by exact raw + logical-stream seals |
| Low | 0 | none |

独立红队先后构造 comment/heredoc/dead-scope decoy、wrapper/literal/rebind、quote/indirect/split
spelling、two-variable/`printf -v`/empty expansion/command substitution/ANSI-C path、dynamic eval/
`bash -c`、fake heredoc、multiline substitution 与 inline-Python heredoc direct execution。最终只读复审
结论=`APPROVE`，B/H/M/L=`0/0/0/0`，mandatory=`0`。

## Verification summary

| Check | Result |
|---|---|
| Python/Bash syntax | passed |
| full coverage contract negative tool | `27/27` governed probes；dispatch layers all passed |
| runner source seals | raw exact；logical stream=`292` commands exact |
| dispatch semantics | `4 tools / 7 calls / 16 target refs / direct 0` |
| dispatch negatives | `raw 44 / stream 43 / semantic 33 / source 11 / raw-only 1` |
| Git mode / nonexec smoke | `4/4 / 4/4` |
| Step 4 / Step 6 hash closure | `61/61 / 16/16` |
| machine reset | `diagnostic-ready / diagnostic-pending` |
| formal-r8 capsule | exact set/hash/size and sensitive scan passed |

## Authorization boundary

current-state/document-link review=`PASS / 0/0/0/0 / mandatory 0 / broken links 0 / status drift 0`。
当前只允许提交并 push 一个包含 failure capsule、BUG、三处 runtime fix、negative contract、machine
reset、hash cascade 与 current-state records 的原子 Cdiag。
随后必须在 clean fresh clone 中先证明 Git modes=`100644`、direct denied、interpreter passed，再运行
唯一 fresh diagnostic-r27。r27 candidate/capsule/双审、direct-child Cfreeze、fresh formal-r9、final
quality、replacement coverage audit `31/31` 和 feature acceptance 仍须逐门完成。
