---
evidence_type: portability-remediation
version: 9.3.4
step: 4
source_run: step4-coverage-20260719-formal-r8
status: implemented-awaiting-replacement-authority
recorded_at: 2026-07-19
---

# Step 4 report-runner interpreter recovery

## Outcome

formal-r8 暴露的 fresh-clone 可移植性缺陷已按最小边界修复：报告 runner 的三个错误 Python
调用全部显式使用 `python3`；runner 使用的四个 Python 工具继续保持权威 Git mode=`100644`。
修复不依赖主工作树
偶然存在的 executable bit，也没有把失败的 r8 artifact 修补成成功态。

该结果当前只属于 pre-Cdiag 实现证据；独立 code/docs quality reviews 已 PASS，仍必须经 new Cdiag
commit/push/clean 和 fresh diagnostic-r27 后，才可进入新的 candidate/freeze/formal 链。

## Exact implementation delta

`scripts/v934/step4/coverage_report_runner.sh` 的三处调用从直接执行改为：

1. `python3 "$EXEC_TOOL" verify`；
2. `python3 "$CONTRACT_TOOL" validate-contract`；
3. `python3 "$EXEC_TOOL" verify-aggregate`。

`coverage_contract_negative_tool.py` 新增以下权威约束：

- 通过 `git ls-files --stage` 读取 Git mode，禁止以当前 worktree 的 `stat` executable bit 作为判断；
- 先封印 runner exact raw bytes 与完整 `292` 条 executable logical command stream，再绑定四个 exact
  tool target、七个 top-level `python3` dispatch 和一个 provenance-only argument reference，direct
  command-position count 必须为 `0`；
- raw/stream/semantic mutations 分别为 `44/44 / 43/43 / 33/33`；semantic probes 显式关闭两层
  source seal 后，覆盖七处去解释器、四处 target rebind、comment/operator-comment/
  heredoc/dead-scope decoy、`command/env/exec/if/!` wrapper、command substitution、braced/unquoted
  variable、四个 literal direct 与 quote/indirect/split spellings，仍全部以 stable semantic code 拒绝；
- `11/11` source-seal probes 覆盖变量拼接、`printf -v`、empty expansion、command substitution、
  ANSI-C path、dynamic eval/bash、fake heredoc 与 multiline construction；其中 inline-Python heredoc
  direct call 在 shell stream unchanged、semantic accepted 时仍由 raw seal fail closed；
- `4/4` Git-mode mutations 覆盖 `100755`、`120000`、untracked、missing；
- 将全部四个 Python tools 复制成 `0644`，复算 file mode 并证明 direct execution=
  `permission-denied` 且 interpreter execution=`passed`；
- non-executable probe 前后重新校验 report runner SHA，避免 probe 改写被测输入。

没有 production Java、Java test、POM、test selector、report/testcase cardinality、coverage floor、
critical class、exclusion、数据库矩阵或公共 API 变化。

## Machine reset and hash closure

失败的 formal authority 已撤回，machine state 重置为：

- coverage contract=`diagnostic-ready`，SHA-256=
  `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530`；
- threshold=`diagnostic-pending`，SHA-256=
  `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96`；
- report runner SHA-256=
  `a83ed709ccbbf152cbbeba8d25c2fffb0bd3343ba5bf86a7a0a627562bad4d12`；
- coverage contract negative tool SHA-256=
  `4a988edb028300035ab234060d3ae671c9ea603f2d1316ee598d313c46ccc63d`；
- Step 4 manifest=`61/61`，manifest SHA-256=
  `6a48ab012fb660bf6459bec76840c3f7d41ccbe08325bb104e81934c6e1d0782`；
- Step 6 contract/tool=
  `36434cc895ffcb7d29fb156d575eedc68e41123a88d1eb9ed4678df0338d0f52 /
  e38706c40a037edac26dc1c3bb90d1c1e3bf0146264123a497f3765cf4760a2e`；
- Step 6 manifest=`16/16`，manifest SHA-256=
  `d1efe0310cfef1c992482d168f75a1a4cab5b92c1f33e37e7d5b88e2a49543bd`。

Step 6 只同步其既有 Step 4 hash binding；CI 语义、workflow 角色与 release gate 未放宽。

## Verification

| Check | Result |
|---|---|
| full coverage contract | `passed / workflow_state=diagnostic / threshold_status=diagnostic-pending` |
| Step 4 / Step 6 manifests | `61/61 / 16/16` |
| governed contract mutations | `27/27` |
| Python tool / dispatch bindings | `4/4 targets + 7/7 top-level calls`；target-reference commands=`16`，direct=`0` |
| raw runner / logical stream seal | `a83ed709…4d12 / 186d986f…46e8`；logical commands=`292` |
| raw / stream / semantic mutations | `44/44 / 43/43 / 33/33`；semantic=`30 COMMAND + 3 SCOPE` |
| source-seal bypass mutations | `11/11`；raw-only inline-Python heredoc=`1/1` |
| Git-mode mutations | `4/4`：`100755 / 120000 / untracked / missing` rejected |
| non-executable dispatch smoke | `4/4`；file=`0644`、Git=`100644`，direct denied，`python3` passed |
| source/Git identity negatives | `22/22` |
| threshold/frozen replay negatives | `12/12` |
| coverage XML negatives | `124/124` |
| successor overlay | `parents=3 / contracts=4 / amendments=35 / required=45/446 / Addon=2/6` |
| overlay negatives | `20/20` |
| lifecycle / authority negatives | lifecycle PASS；authority positive=`2`、negative=`14` |
| CI workflow validation | `16 tooling paths / 4 workflows / passed` |
| syntax | Python compile and Bash syntax PASS |

formal-r8 failure capsule 保持独立 immutable，manifest=
`9003f11fbd74eb0741f729cd0002e5c4d93ad97844d97544b250bd5d7a0be4e5`，
`16 entries / 282473 bytes / sensitive matches 0`。r8 raw exec、XML 和已完成 lane 结果不得输入
replacement diagnostic。

## Authority boundary

当前 `can_enter_step5=no / can_enter_coverage_audit=no / can_enter_acceptance=no`。下一合法动作只有：

1. 已完成的独立 pre-Cdiag code/docs implementation-quality reviews；
2. 一个包含失败封存、最小修复、machine reset 与 current-state 文档的原子 Cdiag commit/push；
3. clean fresh clone 中复核 Git `100644` 与解释器 dispatch，再运行唯一 all-lane diagnostic-r27。

之后仍必须形成全新 candidate/capsule/双审、direct-child Cfreeze、fresh formal-r9、final quality、
replacement coverage audit `31/31` 与 feature acceptance，才能重新开放 Step 5。
