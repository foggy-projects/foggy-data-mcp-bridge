---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r14
tested_commit: 322bb346cca19998a90d6d990505ef033f3a496a
candidate_sha256: 9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55
reviewer: "Codex /root+r14_threshold_candidate_review+r14_diagnostic_evidence_review"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/r14_threshold_candidate_review"
  - "/root/r14_diagnostic_evidence_review"
reviewed_at: 2026-07-17T04:14:16Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/1"
---

# Step 4 r14 threshold review

## Decision

确认 `step4-coverage-20260717-diagnostic-r14` 发布的 exact observed counters 可作为 Step 4
reviewed thresholds，decision=`confirm-observed-thresholds`。确认责任由 `Codex /root` 发起，
两路独立只读审查由实际任务身份 `/root/r14_threshold_candidate_review` 与
`/root/r14_diagnostic_evidence_review` 完成；canonical threshold 的 reviewer 使用
`Codex /root+r14_threshold_candidate_review+r14_diagnostic_evidence_review`。

本记录明确不声称用户或 release owner 已做人工签收，`user_confirmation=not-asserted`。
Decision 只授权把 candidate exact projection 写入 Cdiag 的 direct-single-parent Cfreeze。
Fresh formal、final implementation quality、coverage audit、acceptance 与 Step 5 均不在本次
确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260717-diagnostic-r14` |
| diagnostic Git HEAD | `322bb346cca19998a90d6d990505ef033f3a496a` |
| diagnostic source SHA-256 | `04e485088ae233c388cd0fd0bd9190d347e3b2a4d65faac238a1678147ec9d81` |
| run status SHA-256 | `ed222ffbf1c73fe66fb3d9c5c8bf4a5acfbd95b8817bd59a2093888f4a4d4cc5` |
| summary SHA-256 | `16deb94b7afeeb3ef4ba63dd00502f93c5b5e4f548349e020216634e5ba6497c` |
| observation SHA-256 | `f3cab617a3e43ed5d0948687466c3f37de26bafc8fe9a65feb8f48a07dee412f` |
| diagnostic contract SHA-256 | `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be` |
| candidate path | `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r14-threshold-candidate-20260717.json` |
| candidate SHA-256 | `9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55` |

## Independent recomputation

独立复算未依赖 `freeze-thresholds` 的内部 candidate builder，而是读取 Step 1 frozen critical
policy、r14 `coverage-observation.json` 与 candidate，并按 strict JSON type identity 检查：

1. candidate bytes SHA-256 精确为
   `9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`；
2. aggregate line=`54,622/76,830`、branch=`26,106/44,870` 是 observation 的 exact
   `covered/total/fraction` 投影，reviewed minimum 与 observed 完全相同；
3. critical rows=`12`，FQCN/module/order 与 Step 1 policy、observation、candidate 三方一致；
4. required-positive metrics=`23`，每项 `minimum == observed`，line/branch 用 integer cross
   multiplication 分别达到 `4/5`、`7/10`；
5. N/A metrics=`1`，唯一 tuple 是
   `NamespaceScope / foggy-dataset-model / branch`，observed=`0/0/null`、minimum=`null`；
6. `below_floor_class_count=0`、`not_applicable_metric_count=1`、outcome=
   `at-or-above-floor`；
7. public `validate-diagnostic-run` 与 `verify-threshold-candidate` 均 PASS。

```text
candidate_sha256=9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55
aggregate_line=54622/76830 aggregate_branch=26106/44870 exact_projection=passed
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

Aggregate reviewed thresholds 精确冻结为 line=`54,622/76,830`、branch=`26,106/44,870`；
不得通过 float、取整、分母重缩放或保留 r13 较高 aggregate 来改写。

## Cross-run stability review

- r14 `WatchServiceFileTracer=204/244 line, 99/128 branch`；相对 formal-r1 为
  `+9 line/+4 branch`，相对 r13 为 line 相同、branch `+1`；
- r14 Unit bitmap=`185/245`，完整包含 5/5 focused identical bitmap 与 formal-r1 缺失的
  probes `[103,110,223,226,230,231,234]`，且没有 probe 丢失；
- r14 相对 r13 aggregate line `-2` / branch `-3` 来自非关键 Pivot class：
  `BaselineRatioCalculator=-2/-3`、`ResultShaper=0/-1`，同时 tracer branch=`+1`；差异只由
  PostgreSQL exec 的附加 probes 贡献；
- 按 r14 较低真实 observation freeze 不会把 formal-r1 变绿：formal-r1 相对本 candidate
  仍为 aggregate line `-7`，tracer line `-9`、branch `-4`；
- 因此该差异是 Low 风险而非 blocker。Fresh formal 必须继续 fail closed，且本 review 不提前
  声称 formal 稳定通过。

## Canonical freeze fields

Cfreeze 写入 `coverage-thresholds.json.review` 时使用：

```yaml
reviewer: Codex /root+r14_threshold_candidate_review+r14_diagnostic_evidence_review
reviewed_at: 2026-07-17T04:14:16Z
diagnostic_run_id: step4-coverage-20260717-diagnostic-r14
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r14-threshold-review-20260717.md
evidence_sha256: <本文件定稿后的实际 SHA-256>
decision: confirm-observed-thresholds
```

本文件不记录最终 confirmed-threshold SHA 或 Cfreeze commit SHA，避免 review evidence 与
canonical threshold 形成哈希环。

## Findings and boundary

- Blocker/High/Medium：`0/0/0`；
- Low：`1`，即上述非关键 PostgreSQL Pivot probe 波动；由 fresh formal 保持 fail-closed；
- candidate 保持 immutable `review-required`，不得转写为 confirmed candidate；
- Cfreeze 必须是 Cdiag `322bb346...a496a` 的唯一 direct-single-parent child；
- mandatory delta 为 threshold、coverage contract、Step 4 `SHA256SUMS`，其他只允许
  `docs/9.3.4/`；
- fresh formal 仍为 `pending`；本 review 不是 formal、coverage audit 或 version acceptance。
