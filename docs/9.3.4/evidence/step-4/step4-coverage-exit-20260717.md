---
doc_role: step-exit-evidence
doc_purpose: Record the exact fresh formal authority for the 9.3.4 Step 4 coverage gate.
version: 9.3.4
step: 4
status: formal-passed
decision: formal-passed-quality-reviewed
tested_commit: f97483a0b87a82734d21888e7b5bea74b0c5fe55
authority_run_id: step4-coverage-20260717-formal-r4
recorded_at: 2026-07-18
---

# Step 4 Coverage Formal Exit Evidence

## Scope and Current Decision

fresh formal run `step4-coverage-20260717-formal-r4` 在 clean/pushed Cfreeze
`f97483a0b87a82734d21888e7b5bea74b0c5fe55` 上完成，返回
`last_phase=completed / exit_code=0 / status=formal-passed`。confirmed threshold、完整
required lanes、23 份 raw exec、aggregate/critical gate、model gate、source seal、negative、
sensitive scan 与 cleanup 均通过；运行后 public `verify-artifact` 对 final artifact 重新复算为
`ARTIFACT VALID stage=final`。

本记录先确认 formal gate PASS。post-formal implementation quality 随后已审查为
`ready-for-coverage-audit`；test evidence coverage audit 和 feature acceptance 尚未完成，因此
Step 4 仍为 `in-progress`，`can_enter_coverage_audit=yes`、`can_enter_acceptance=no`，Step 5
仍关闭。

## Authority Identity

- Cdiag parent：`613b11a0ae6732f865f918551cd9116079771b5e`
- tested Cfreeze：`f97483a0b87a82734d21888e7b5bea74b0c5fe55`
- Git relation：direct single parent；`HEAD == origin/main == tested Cfreeze`
- formal window：`2026-07-17T15:09:20Z` → `2026-07-17T16:16:14Z`
  （Asia/Shanghai 完成时间 `2026-07-18T00:16:14+08:00`）
- formalization delta：`passed / formal / 17 changed paths`，SHA-256=
  `a16450ba319f0617e60e0f982de0310b6a1bb7c44546732386122c9a7c5fbdab`
- confirmed threshold SHA-256=
  `9c79ba79c9a6451ff77253d35dafb77f58d6705cbe38eb65c7df5bf1611ec7fd`
- formal-ready contract SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`
- Step 4 machine manifest SHA-256=
  `bee8e53034962a3de6d0d3b31739d5a01b09cf865cd9254d8ca42b4f5d6c2b15`

Canonical ignored run root：

`target/v934-step4-coverage/runs/step4-coverage-20260717-formal-r4/`

## Exact Test and Provenance Result

| Partition | Positive | Structural | Testcase | Result |
|---|---:|---:|---:|---|
| Unit | 681 | 55 | 4,941 | F0/E0/S0 |
| Integration | 47 | 4 | 320 | F0/E0/S0 |
| Step 3 required | 45 | 0 | 446 | F0/E0/S0 |
| required union | 773 | 59 | 5,707 | F0/E0/S0 |
| Addon companion（union 外） | 2 | 0 | 6 | F0/E0/S0 |

- database matrix=`29 reports / 370 testcase / F0E0S0`
- external matrix=`16 reports / 76 testcase / F0E0S0`
- raw exec=`23`、unique sessions=`48`、formal class-loading identities=`16,953`
- production universe=`24 modules / 2,098 classes`
- workspace class tree SHA-256=
  `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be`
- source-before/source-after byte-identical，SHA-256=
  `96e2c871dbb086d42226f5ac27254000969e2ff66c7bb24c9f5d902ea1ddc3a3`

## Coverage Gate

| Metric | Confirmed minimum | Formal observed | Outcome |
|---|---:|---:|---|
| aggregate line | `54,624/76,830` | `54,624/76,830` | passed |
| aggregate branch | `26,111/44,870` | `26,111/44,870` | passed |

- critical classes=`12`
- applicable critical metrics=`23`
- below-floor=`0`
- unique structural N/A=
  `NamespaceScope / foggy-dataset-model / branch = 0/0/null`
- model gate=`passed`；bundle minimum line/branch=`0.77/0.62`，
  `SemanticScaleSqlSupport=1.00/1.00`

## Artifact Identities and Public Replay

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `5bb59a017fc9714e6d4abe3afc28e06a0f5c96c99a0292109acb88f34cfa8625` |
| `summary.env` | `a3680239bb1a01c1e12d58aa928d67d44c4ed0deb3a68634ee477e3cbf222e6b` |
| `report-inventory.json` | `1121a2c58fbe4f6dce959a2d4354234e2cd7d661d0147f650cfebd85abc3b94b` |
| `exec-manifest.json` | `77496e2b6a13fbf86aeed066270100d51759d6f34025aed59f679e7450403523` |
| `coverage-observation.json` | `a8f57f79bcc07834a2f6fd41c19cb9771f9c412d04cf8dbcedb87cd391e1b2f2` |
| `coverage-gate.json` | `5ca8162a4a32a46e71cca85284907a04caf92210d418c21fc657e5156fe2c14a` |
| `candidate-manifest.json` | `acf78e51c9f729bf7c779639e90670af58b260f6e2a24ef69b69ac58803d7a36` |
| `final-manifest.json` | `b6f695ccd641649de23b83c104cd944dc36ae8fc99f662f3bfeedcf9841e6416` |
| aggregate exec | `0db02dbfe360b935887ad83d16c6708a2b8bc6e81f16ac6bb69c41c316f75efc` |
| aggregate XML | `07e7eb2616f4ddd818a2078271d1accc8780ef0ca81b4a5129de1742f97cd1de` |
| child lifecycle | `2aedbacf7f7ad081395f3f15938376289145d0cdab7c288d5495f792da6796f5` |

运行结束后在 restored external state 下完成以下公开复算：

```text
validate-contract: status=passed / workflow_state=formal / threshold_status=confirmed
validate-frozen-diagnostic: status=passed / diagnostic-r19
verify-threshold-candidate: THRESHOLD CANDIDATE VALID
verify-artifact candidate: ARTIFACT VALID stage=candidate
verify-artifact final + run-status: ARTIFACT VALID stage=final
```

## Lifecycle, Fail-Closed and Restoration

- Unit、Integration、Step 3 required 三个 child 均 `status=passed`、leader reaped=`1`、
  process-group residue=`0`；ready/completion receipt 与 parent run identity 精确绑定。
- Unit fixture negatives=`36/36`、lifecycle negatives=`5/5`；coverage exec negatives=`17`、
  coverage XML negatives=`9`；report inventory negatives=`30/30`；successor、source/Git、
  toolchain、generic XML 与 run-log lifecycle negatives 全部通过并被 summary/final artifact 绑定。
- runner-owned container/volume/network residue=`0/0/0`，sensitive scan=`passed`。
- 外层 wrapper 在 runner 完成后于同一秒恢复开跑前四个 exact demo DB container；独立 postcondition
  复查 MySQL 5.7、MySQL 8、PostgreSQL、SQL Server 均为原 container ID 且
  `running / healthy`。该恢复属于 evidence window 外状态，不进入 formal result。

## Post-Run Replay Boundary

运行内 `report_inventory_tool validate` 已在 23 exec 聚合前后通过并由 final artifact 哈希绑定。
四个 demo DB 恢复后，单独再次执行该 live validator 会因为 Unit fixture verifier 要求冻结端口
`13306` 全局 free 而返回 `E_UNIT_FIXTURE`；这是 public final verifier 之外的动态环境重放边界，
不是 sealed report inventory 的 counter/hash 差异。post-formal implementation quality 已将其定为
不阻断的 Low，由 Step 5 single authority/rehearsal 负责拆分 live 与 durable replay 入口；不得把
post-restore live validator 当成 public acceptance gate。

## Historical Isolation and Next Gate

formal-r1、formal-r2、formal-r3 及 failed/diagnostic runs 继续保持 immutable/excluded；formal-r4
没有复用其 XML、exec 或 candidate。下一顺序固定为：

1. post-formal implementation quality（已通过）；
2. test evidence coverage audit；
3. Step 4 feature acceptance；
4. 仅当后两项也通过后把 Step 5 标为 `ready / not-started`。

本记录不是 9.3.4 version signoff，不开放 9.3.5。
