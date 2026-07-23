---
evidence_type: formal-failure-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-formal-r8
tested_commit: 7c18019ed12d25c029de7e7e49caef77a79b2e67
status: failed
decision: requires-new-diagnostic
failed_phase: coverage-report
exit_code: 126
recorded_at: 2026-07-19
---

# Step 4 formal-r8 report-runner permission failure

## Decision

`step4-coverage-20260719-formal-r8` 在真正 fresh、non-shallow、clean clone 的 Cfreeze
`7c18019e…` 上通过 Unit、Integration、Step 3 required 与 report inventory，随后在
`coverage-report` 阶段以 `126 / Permission denied` fail closed。该 run 永久保持
`failed / excluded / non-reusable / non-candidate`；不得续跑 aggregation、补写成功态 artifact、
复用 raw exec 或与 replacement run 拼接。

失败来自报告脚本依赖主工作树的非权威 executable bit，不是业务测试或覆盖率阈值回归。
`coverage_report_runner.sh` 不在 Cfreeze formalization allowlist，因此修复必须进入新的 Cdiag
lineage。

## Run identity and completed boundary

- Cdiag=`4fe86929de6206aa3e514c974635e90395c28b2e`；Cfreeze=
  `7c18019ed12d25c029de7e7e49caef77a79b2e67`，direct-single-parent 与 `26` 路径
  formalization delta 均 PASS；
- run window=`2026-07-19T05:09:57Z..2026-07-19T06:07:29Z`；outer wrapper finished=
  `2026-07-19T06:07:37Z`；
- Unit=`681 execution + 55 structural / 4941 / F0 E0 S0`；fixture schema=`2`，current
  known-consumer lower bound=`7 reports / 12 nodes`，negative=`42/42`，lifecycle=`5/5`；
- Integration=`47 + 4 / 320 / F0 E0 S0`；六个 variants 全 PASS；
- database matrix=`29/370/F0E0S0`，state negative=`18/18`；external matrix=
  `16/76/F0E0S0`，Redis state=`4/4`；Addon companion=`2/6/F0E0S0`；
- Step 3 required=`45/446/F0E0S0`；report inventory=
  `773 required + 59 structural / 5707 / F0 E0 S0`，inventory negative=`30/30`；
- 三个 outer child 均 `exit 0 / reaped / process-group residue 0`；runner cleanup=
  `containers/volumes/networks=0/0/0`；
- independent post-run source recomputation=`4106 files / c8de202a…afa`，与 source-before exact。

上述只证明 child/report inventory 边界。23 个 raw exec 文件存在，但
`coverage_exec_tool.py verify` 尚未运行，因此不得声称 `23/48` exec/session provenance 已通过。

## Failure and absent success boundary

精确失败行来自 raw `run.log` line `322402`：

```text
coverage_report_runner.sh: line 275: coverage_exec_tool.py: Permission denied
```

`run-status.env`=`last_phase=coverage-report / exit_code=126 / status=failed`。以下成功态 artifact
全部 absent：

- `exec-manifest.json`；
- aggregate exec、aggregate XML、aggregate/report provenance；
- `coverage-observation.json`、model gate 与 `coverage-gate.json`；
- `summary.env`、`candidate-manifest.json`、`final-manifest.json`；
- official `sensitive-scan.env` 与 runner-owned source-after inventory。

因此 r8 没有 aggregate/critical/model coverage 结论，也没有 Step 4 candidate/final authority。

## Root cause

Git 中 `coverage_exec_tool.py` 与 `coverage_tool.py` 均为 `100644`；Cfreeze 主工作树设置
`core.fileMode=false`，两个文件偶然呈现 `0775/0755`，Git clean 不会报告这种差异。fresh clone
使用 `core.fileMode=true`，按 Git mode 正确物化为 `0664`。

报告脚本共有三个依赖 executable bit 的直接调用点：

1. `"$EXEC_TOOL" verify`；
2. `"$CONTRACT_TOOL" validate-contract`；
3. `"$EXEC_TOOL" verify-aggregate`。

fresh clone 上两个工具 direct `--help` 均返回 `126`，显式 `python3 … --help` 均返回 `0`。
r8 在第一个点停止；若只修第一处，第二处会确定性成为下一失败点。对 tracked shell、Python、
workflow 与 Python subprocess 的独立扫描未发现其它 Git `100644 *.py` command-position 调用。

## Cleanup, restore, and sensitive boundary

- wrapper=`runner_started 1 / runner_finished 1 / runner_rc 126 / restore_rc 0 /
  receipt_inspect_rc 0 / wrapper_outcome_rc 126`；
- MySQL57、MySQL8、PostgreSQL15、SQLServer2022 四个 demo DB 均以开跑前 exact container ID
  恢复为 `running / healthy`，live `docker inspect` 二次复核一致；
- raw run root、outer log 与 final restore receipt 以正式五组 sensitive patterns 独立扫描，
  matches=`0`；official success-stage sensitive receipt absent 符合失败边界；
- main repository 仍为 clean/pushed exact Cfreeze，未被失败 run 修改。

## Persistent capsule

持久 capsule 位于 `formal-r8-failure-capsule/`，manifest SHA-256=
`9003f11fbd74eb0741f729cd0002e5c4d93ad97844d97544b250bd5d7a0be4e5`；
`16 entries / 282473 bytes`，exact set、path/size/SHA 与敏感扫描均 PASS。58 MB raw run log
不入 Git，manifest 固定其 `24d90601…e14` hash/size，失败行以 normalized excerpt 保存。

关键 artifact：

| Artifact | SHA-256 |
|---|---|
| run status | `874ce7183de28043f0aa5de9fb675efb6e08c0dca906a78928e44b521002f1a8` |
| run context | `4d5690a985dec298c3432db45ce1561a727c580c04b564f68be1dfb47f44d373` |
| formalization delta | `5e62e28993bf2e5e17e09db9a7eb450c1f6a0edc64acd7f740afe6637b3c99b6` |
| report inventory | `e1c8af8499e61100a3ebd7b76dd7c74b38f5382899c67e9a412f63f30efb1a7c` |
| toolchain receipt | `34735c49dbcd4d9056dc07058eedf58d1d46a6fdf41c28c86158823e62e5ace5` |
| cleanup | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| outer status / containers | `1aa178a3…6467 / 28fb7906…5b05` |

## Recovery boundary

最小 runtime 修复是三个调用点统一显式使用 `python3`；successor gate 同时封印 runner raw bytes、
完整 logical executable stream，并覆盖全部四个 Git `100644` Python tool target 与七个 logical
dispatch，纳入 raw/stream/semantic/Git-mode mutation 与四个
non-executable dispatch smoke。Step 4/Step 6 hash closure 与 machine state 必须重置为
`diagnostic-ready / diagnostic-pending`。

唯一合法后续链路为：recovery quality → new Cdiag commit/push/clean → fresh diagnostic-r27 →
candidate/capsule/双审 → direct-child Cfreeze → fresh formal-r9 → final quality → replacement coverage
audit `31/31` → feature acceptance。Step 5、coverage audit、acceptance 在此之前继续关闭。
