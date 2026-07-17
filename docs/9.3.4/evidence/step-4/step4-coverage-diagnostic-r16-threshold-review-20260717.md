---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r16
tested_commit: f863c672029d5d1e5a4903df74cf6cba22a04a85
candidate_sha256: 2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919
reviewer: "Codex /root+bean2map_test_semantics_review+r15_failure_evidence_audit"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/bean2map_test_semantics_review"
  - "/root/r15_failure_evidence_audit"
reviewed_at: 2026-07-17T08:22:19Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/1"
---

# Step 4 r16 threshold review

## Decision

确认 `step4-coverage-20260717-diagnostic-r16` 发布的 exact observed counters 可作为 Step 4
reviewed thresholds，decision=`confirm-observed-thresholds`。确认责任由 `Codex /root` 发起，
两路独立只读审查由实际任务身份 `/root/bean2map_test_semantics_review` 与
`/root/r15_failure_evidence_audit` 完成；canonical threshold 的 reviewer 使用
`Codex /root+bean2map_test_semantics_review+r15_failure_evidence_audit`。

本记录明确不声称用户或 release owner 已做人工签收，`user_confirmation=not-asserted`。
Decision 只授权把 candidate exact projection 写入 Cdiag 的 direct-single-parent Cfreeze。
Fresh formal、final implementation quality、coverage audit、acceptance 与 Step 5 均不在本次
确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260717-diagnostic-r16` |
| diagnostic Git HEAD | `f863c672029d5d1e5a4903df74cf6cba22a04a85` |
| diagnostic source SHA-256 | `025b55582f2be6c6ec6b546fbd609251cabc371c469add1261033ef22639a43e` |
| run status SHA-256 | `72fb0183a73e9af3c9ddc3ead477c75029525267672212e13cc86623b4cf5857` |
| summary SHA-256 | `ea94dd52bbec905074f875aa39089c9f08a340a888984a1e97bc1e2f7df39475` |
| observation SHA-256 | `90deb174cb7f577e0573292762c970e274519d081e3641e4ae6fe12fb81c0be1` |
| diagnostic contract SHA-256 | `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be` |
| candidate path | `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-threshold-candidate-20260717.json` |
| candidate SHA-256 | `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919` |

## Independent recomputation

独立复算未依赖 `freeze-thresholds` 的内部 candidate builder，而是读取 Step 1 frozen critical
policy、r16 `coverage-observation.json` 与 candidate，并按 strict JSON type identity、duplicate
key 拒绝、artifact byte hash 与 integer cross multiplication 检查：

1. candidate bytes SHA-256 精确为
   `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919`；
2. aggregate line=`54,624/76,830`、branch=`26,111/44,870` 是 observation 的 exact
   `covered/total/fraction` 投影，reviewed minimum 与 observed 完全相同；
3. critical rows=`12`，FQCN/module/order 与 Step 1 policy、observation、candidate 三方一致；
4. required-positive metrics=`23`，每项 `minimum == observed`，line/branch 用 integer cross
   multiplication 分别达到 `4/5`、`7/10`；
5. N/A metrics=`1`，唯一 tuple 是
   `NamespaceScope / foggy-dataset-model / branch`，observed=`0/0/null`、minimum=`null`；
6. `below_floor_class_count=0`、`not_applicable_metric_count=1`、outcome=
   `at-or-above-floor`；
7. candidate evidence binding=`12/12`，summary artifact hashes=`29/29`，raw exec=`23/23`，
   report inventory path/hash=`31/31`，child ready/complete=`6/6`；
8. public `validate-diagnostic-run` 与 `verify-threshold-candidate` 均 PASS。

```text
candidate_sha256=2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919
aggregate_line=54624/76830 aggregate_branch=26111/44870 exact_projection=passed
critical_rows=12 order=step1-policy-exact positive_metrics=23 n_a_metrics=1
minimum_equals_observed=23/23 unique_n_a=NamespaceScope/foggy-dataset-model/branch
candidate_independent_recompute=passed
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
不得通过 float、取整、分母重缩放或保留 r14 较低 aggregate 来改写。

## Cross-run stability review

- 12 个 critical exact counters 与 r14 完全相同，生产 class tree 与 denominator 不变；
- r16 相对 r14 aggregate line=`+2` / branch=`+5`，精确来自非关键类：
  `BaselineRatioCalculator=+2/+3`、`ResultShaper=0/+1`、`QueryModelSupport=0/+1`；
- execution class identity 从 `16,956` 变为 `16,948`，分散于 Unit、Milvus、Mongo、Redis 与 MCP
  class-loading footprint；23 exec、48 unique sessions、2,098 production class universe 与所有
  critical counters 不变；
- r15 是 Unit fail-closed partial run，只有 `2/48 sessions`，任何 r15 exec/report 均未拼入 r16；
- 因此按 r16 exact observation freeze 不降低任何阈值，但跨 run probe 稳定性记为 Low 风险，
  fresh formal 必须继续 fail closed，不得把本 review 当作 formal 稳定性证明。

## Canonical freeze fields

Cfreeze 写入 `coverage-thresholds.json.review` 时使用：

```yaml
reviewer: Codex /root+bean2map_test_semantics_review+r15_failure_evidence_audit
reviewed_at: 2026-07-17T08:22:19Z
diagnostic_run_id: step4-coverage-20260717-diagnostic-r16
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-threshold-review-20260717.md
evidence_sha256: <本文件定稿后的实际 SHA-256>
decision: confirm-observed-thresholds
```

本文件不记录最终 confirmed-threshold SHA 或 Cfreeze commit SHA，避免 review evidence 与
canonical threshold 形成哈希环。

## Findings and boundary

- Blocker/High/Medium：`0/0/0`；
- Low：`1`，即上述非关键类 probe 波动；由 fresh formal 保持 fail-closed；
- candidate 保持 immutable `review-required`，不得转写为 confirmed candidate；
- Cfreeze 必须是 Cdiag `f863c672029d5d1e5a4903df74cf6cba22a04a85` 的唯一
  direct-single-parent child；
- mandatory delta 为 threshold、coverage contract、Step 4 `SHA256SUMS`，其他只允许
  `docs/9.3.4/`；
- fresh formal 仍为 `pending`；本 review 不是 formal、coverage audit 或 version acceptance。
