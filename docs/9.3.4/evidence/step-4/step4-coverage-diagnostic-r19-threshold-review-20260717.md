---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r19
tested_commit: 613b11a0ae6732f865f918551cd9116079771b5e
candidate_sha256: 6588e30bd6e51aa27bcab06cf633cc51cefa2d5a27824eee5c003bcbe9f545b8
reviewer: "Codex /root+r19_threshold_metric_review+r19_threshold_binding_review"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/r19_threshold_metric_review"
  - "/root/r19_threshold_binding_review"
reviewed_at: 2026-07-17T14:46:01Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r19 threshold review

## Decision

确认 `step4-coverage-20260717-diagnostic-r19` 发布的 exact observed counters 可作为 Step 4
reviewed thresholds，decision=`confirm-observed-thresholds`。确认责任由 `Codex /root` 发起，
两路独立只读审查由 `/root/r19_threshold_metric_review` 与
`/root/r19_threshold_binding_review` 完成；canonical threshold 的 reviewer 使用
`Codex /root+r19_threshold_metric_review+r19_threshold_binding_review`。

本记录不声称用户或 release owner 已人工签收，`user_confirmation=not-asserted`。Decision
只授权把 candidate exact projection 写入 Cdiag 的 direct-single-parent Cfreeze。Fresh formal、
final implementation quality、coverage audit、acceptance 与 Step 5 均不在本次确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260717-diagnostic-r19` |
| diagnostic Git HEAD | `613b11a0ae6732f865f918551cd9116079771b5e` |
| diagnostic source SHA-256 | `a4ccff29f7bc0dc3f0613d2f732eecae95a0e324aa21dff8b31f789820c38f65` |
| run status SHA-256 | `c7511e0cf7b2499272851bb7574b435446d02594562a121869c882138e9467fa` |
| summary SHA-256 | `90e007edab12d0d12677c2820ee38dae70162108052a1056efb827955b408cfa` |
| observation SHA-256 | `43735e488079108c63fcfe7cb89b44245dd44b7eba8442ba8f5b6df23f5df50b` |
| diagnostic contract SHA-256 | `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be` |
| candidate path | `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r19-threshold-candidate-20260717.json` |
| candidate SHA-256 | `6588e30bd6e51aa27bcab06cf633cc51cefa2d5a27824eee5c003bcbe9f545b8` |

## Independent recomputation

两路审查没有把官方 candidate builder 当作唯一结论。metric review 直接读取 Step 1 frozen
critical policy、r19 observation 与 candidate，以 strict identity 和整数交叉乘法复算；binding
review 逐份解析 raw exec、aggregate exec/XML、source/class tree、report inventory、child lifecycle
与 outer marker：

1. candidate review 前后 SHA-256 均为
   `6588e30bd6e51aa27bcab06cf633cc51cefa2d5a27824eee5c003bcbe9f545b8`；
2. aggregate line=`54,624/76,830`、branch=`26,111/44,870` 是 observation 的 exact
   `covered/total/fraction` 投影，并与 r16 reviewed high-water 完全相等；
3. critical rows=`12`，FQCN/module/order 与 Step 1 policy、observation、candidate 三方一致；
4. 指标总数=`24`；required-positive=`23`（line `12`、branch `11`），每项
   `minimum == observed`；line/branch 用整数交叉乘法分别 `12/12` 达到 `4/5`、`11/11`
   达到 `7/10`；
5. 唯一 N/A 是 `NamespaceScope / foggy-dataset-model / branch`，observed=`0/0/null`、
   minimum=`null`；below-floor class=`0`；
6. raw exec=`23/23`，session rows/unique=`48/48`，raw class rows=`106,562`，unique execution
   class IDs=`16,931`；aggregate 解析仍为 `48 sessions / 16,931 classes`；
7. production universe=`24 modules / 2,098 classes`，class tree 与 candidate binding exact；
8. report inventory=`773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon=
   `2/6/F0E0S0` 且未混入 required union；
9. source before/after byte-identical；child lifecycle=`3/3 passed`、exit=`0`、reaped=`1`、
   residue=`0`；outer marker/run-context binding 一致；
10. public `validate-diagnostic-run` 与 `verify-threshold-candidate` 均 PASS。

```text
candidate_sha256=6588e30bd6e51aa27bcab06cf633cc51cefa2d5a27824eee5c003bcbe9f545b8
aggregate_line=54624/76830 aggregate_branch=26111/44870 exact_projection=passed
critical_rows=12 positive_metrics=23 n_a_metrics=1 below_floor_classes=0
minimum_equals_observed=23/23 unique_n_a=NamespaceScope/foggy-dataset-model/branch
exec_sessions_classes=23/48/16931 production_universe=24/2098
candidate_independent_recompute=passed evidence_binding_review=passed
```

## Reviewed thresholds

| Critical class | Line reviewed | Branch reviewed | Applicability |
|---|---:|---:|---|
| `CatalogSnapshotStore` | `202/214` | `83/104` | required / required |
| `ModelBuildSingleFlight` | `79/89` | `31/44` | required / required |
| `CatalogRefreshCoordinator` | `253/282` | `95/134` | required / required |
| `DatasourceCatalogConvergence` | `51/52` | `20/26` | required / required |
| `NamespaceScope` | `5/5` | `0/0` | required / structural N/A |
| `CommittedSourceRevisionRegistry` | `53/55` | `19/24` | required / required |
| `RuntimeNamedDataSourceResolver` | `145/166` | `66/94` | required / required |
| `WatchServiceFileTracer` | `204/244` | `99/128` | required / required |
| `QueryFingerprintBuilder` | `159/179` | `65/84` | required / required |
| `SecurityPolicyFingerprint` | `112/117` | `73/92` | required / required |
| `CaffeineQueryCacheProvider` | `126/135` | `64/78` | required / required |
| `RedisQueryCacheProvider` | `152/159` | `73/88` | required / required |

Aggregate reviewed thresholds 精确冻结为 line=`54,624/76,830`、branch=`26,111/44,870`；
不得通过 float、取整、分母重缩放或保留 r18 较低 aggregate 来改写。

## Cross-run stability review

- r19 与 r16 reviewed aggregate、12 个 critical exact counters、production denominator 和 class
  tree 完全一致；相对 r18 精确恢复 `+2 line / +4 branch`；
- deterministic NULL-axis oracle 位于既有 `PivotSqlParityIT` S12 testcase，三次 fresh JVM
  focused bitmap `3/3 identical`，没有新增 testcase、production、runner、floor、critical set 或
  exclusion 变化；
- execution class identity `16,931` 是本轮 class-loading footprint；`23 exec / 48 sessions`、
  `24 modules / 2,098 classes` 和 aggregate provenance 均完整；
- 因此 r19 candidate 未降低任何 reviewed threshold，也未拼接 r17/r18/其他 partial evidence。
  Fresh formal 仍必须对 exact threshold fail closed。

## Canonical freeze fields

Cfreeze 写入 `coverage-thresholds.json.review` 时使用：

```yaml
reviewer: Codex /root+r19_threshold_metric_review+r19_threshold_binding_review
reviewed_at: 2026-07-17T14:46:01Z
diagnostic_run_id: step4-coverage-20260717-diagnostic-r19
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r19-threshold-review-20260717.md
evidence_sha256: <本文件定稿后的实际 SHA-256>
decision: confirm-observed-thresholds
```

本文件不记录最终 confirmed-threshold SHA 或 Cfreeze commit SHA，避免 review evidence 与
canonical threshold 形成哈希环。

## Findings and boundary

- Blocker/High/Medium/Low：`0/0/0/0`；
- candidate 保持 immutable `review-required`，不得转写为 confirmed candidate；
- Cfreeze 必须是 Cdiag `613b11a0ae6732f865f918551cd9116079771b5e` 的唯一
  direct-single-parent child；
- mandatory machine delta 为 threshold、coverage contract、Step 4 `SHA256SUMS`；其他只允许
  `docs/9.3.4/**`；
- fresh formal 仍为 `pending`；本 review 不是 formal、coverage audit 或 acceptance。
