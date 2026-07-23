---
evidence_type: successful-diagnostic-not-freezable
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r27
tested_commit: f102b52c76d3a5cd34cc57d2576a3575c5f0b1df
status: diagnostic-observed
decision: threshold-candidate-rejected-high-water-regression
candidate_status: non-canonical
capsule_status: non-canonical
recorded_at: 2026-07-19
---

# Step 4 diagnostic-r27 ExportWithChart map-order high-water rejection

## Decision

`step4-coverage-20260719-diagnostic-r27` 在 clean、pushed Cdiag
`f102b52c76d3a5cd34cc57d2576a3575c5f0b1df` 上完成，public
`validate-diagnostic-run` 返回 `DIAGNOSTIC VALID`，required=`773+59/5707/F0E0S0`、
Addon=`2/6`、exec/session=`23/48`、source before=after、model/sensitive、runner cleanup 和四个
demo DB exact restore 均通过。

它仍不得产生 Cfreeze authority。r27 aggregate line=`54624/76830`，但 branch=`26111/44870`、
complexity=`17658/35571`，相对 r26 reviewed high-water
`26112/44870`、`17659/35571` 各低 1。按既有 governed-high-water 规则，机械
candidate verification 不能授权静默降低 formal threshold。

## Exact localization

XML、raw exec 与 source comparison 只有一个 coverage outcome 变化：

| Scope | r26 | r27 |
|---|---:|---:|
| `foggy-dataset-mcp` branch | `2135` | `2134` |
| `ExportWithChartTool` branch | `62/96` | `61/96` |
| `inferFields` branch | `16/22` | `15/22` |
| `ExportWithChartTool.java:248` | `mb=0 cb=2` | `mb=1 cb=1` |

production blob 与 test blob 在 r26/r27 间均未变；差异来自
`ExportWithChartToolTest#fields_shouldBeInferredAutomatically` 将一条包含
`category`（String）和 `amount`（Number）的 item 写成 `Map.of(...)`。`inferFields` 对
`entrySet()` 逐项判断并在首个数值字段 `break`；`Map.of` 的迭代顺序未规定，导致 line 248 的 false
outcome 偶发缺失。

## Rejected non-canonical artifacts

为验证工具链而临时生成的 r27 threshold candidate 的 SHA-256 为
`7db6ef019d05e19d0ac4cd979117a1b15febe6f27a3cfd3c927402fabebb2961`；其 portable archive/manifest
SHA-256 为 `2b6e0f495f85eb04b96b863dee638bae0245831cecf77ec48810ffca2845f2eb` /
`2bcd13fb647dbeb103c2417189a158bbe3e63601ee1a8ff7966b4ec65526dc04`。
它们在发现 high-water regression 后移出 canonical evidence tree，仅作为本拒绝决定的可恢复
本地审计副本；不得提交、引用、复用或据此 Cfreeze。

## Controlled remediation

本 Cdiag 只修改同一既有测试节点：将两条 item 改为显式
`LinkedHashMap(category -> amount)` 插入顺序。生产代码、POM、runner、report cardinality、
`@Test` 数量和 critical set 均不变。这样 line 248 必先得到 false、再得到 true，且已有
`yField=amount` assertion 继续约束行为。

五个独立 Maven/Surefire JVM 都通过 `ExportWithChartToolTest` 的 `16/F0E0S0`，并以独立
JaCoCo exec/report 得到 line 248=`mb=0/cb=2`。目标 production class
`ExportWithChartTool` 的 class id/probe bitmap 在五轮完全一致：

```text
class_id=eefbab946aa40203 probes=182 covered=134
bitmap=f37_3_Pq__l_e68z_z8AKhy__U___zE
fresh_jvm_runs=5/5
```

独立代码审查为 `PASS / B0 H0 M0 L0`，mandatory=`0`。这只证明受控稳定化质量；它不替代新的
all-lane diagnostic。

## Immutable r27 identities

| Artifact | SHA-256 |
|---|---|
| observation | `ddea424bef56cb332f39a1f485aea78aa272a5b92bc18993f3f4d9f9e4eff32d` |
| aggregate XML | `a9c74374ed5fb71c43f73e4307372c30b2ae60e7c19eb5688d9b80ad901569d3` |
| aggregate exec | `17a94f79030abe1d56657d50c7118d084baeb74571d09c664480cb60dbd7bf83` |
| exec manifest | `428465f624779cd63df57cb72d5af76408f79379315c9ec368d471c039065d2b` |
| report inventory | `88cc699d4fec22b6211cd1567057912390086e3d44dccf0dd34888ae41208ca3` |
| source before/after | `81dcef4b8dfcb82a83e454a4e67e4dc551ed34f2cf43926974db49f242226011` |

## Only legal next gate

New Cdiag commit/push/clean is mandatory, followed by one fresh all-lane diagnostic-r28. It must meet or
exceed r26 reviewed aggregate branch and complexity before a new candidate/capsule/dual review/Cfreeze can
begin. Step 5–7, 9.3.5 and 9.4.0 remain closed.
