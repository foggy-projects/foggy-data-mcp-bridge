---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r13
tested_commit: b76552e21479c75111f648a4aa678abe018cc3f9
candidate_sha256: 8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00
reviewer: "Codex /root+xml_full_closure_audit"
confirmation_owner: "Codex /root"
independent_reviewer: "/root/xml_full_closure_audit"
reviewed_at: 2026-07-17T01:07:00Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
---

# Step 4 r13 threshold review

## Decision

确认 `step4-coverage-20260717-diagnostic-r13` 发布的 exact observed counters 可作为 Step 4
reviewed thresholds，decision=`confirm-observed-thresholds`。确认责任由 `Codex /root` 发起，
独立机械复算由实际任务身份 `/root/xml_full_closure_audit` 完成；canonical threshold 的 reviewer
值使用 `Codex /root+xml_full_closure_audit`。本记录明确不声称用户或 release owner 已做人工
签收，`user_confirmation=not-asserted`。

本 decision 只授权把 candidate 的 exact projection 写入 direct-single-parent Cfreeze。Fresh
formal 尚未执行，formal gate/candidate/final、coverage audit、acceptance 与 Step 5 均不在本次
确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260717-diagnostic-r13` |
| diagnostic Git HEAD | `b76552e21479c75111f648a4aa678abe018cc3f9` |
| diagnostic source SHA-256 | `e95cd9b3ae411ce07ec2128164ff7f6b30a8f5d85d9a24ff6715516feee1f1d9` |
| run status SHA-256 | `6ce0f33af4d5f279df909e68ca24c87960912ce0f85a8bfa0cfa06b09cae7367` |
| summary SHA-256 | `bda19f6604faee84914843434cd33fc4f19428051a57509947573521313c6292` |
| observation SHA-256 | `91992393cc2dba4db2e8ae8f8e5fc400273329001b5a3aa61c8df7d91cb7f542` |
| diagnostic contract SHA-256 | `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be` |
| candidate path | `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r13-threshold-candidate-20260717.json` |
| candidate SHA-256 | `8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00` |

## Independent recomputation

独立复算没有调用 `freeze-thresholds` 的内部 candidate builder，而是分别读取 Step 1 frozen
critical policy、r13 `coverage-observation.json` 与 candidate，并按 strict JSON type identity
执行以下检查：

1. candidate bytes SHA-256 精确等于
   `8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`；
2. aggregate line=`54,624/76,830`、branch=`26,109/44,870` 均是 observation 的 exact
   `covered/total/fraction` 投影，reviewed minimum 与 observed 完全相同；
3. critical rows=`12`，FQCN/module/order 与 Step 1 frozen policy、observation、candidate 三方
   exact 一致；
4. required-positive metrics=`23`，每项 `minimum == observed`，line/branch 以 integer
   cross multiplication 分别达到 `4/5`、`7/10`；
5. N/A metrics=`1`，唯一 tuple 为
   `NamespaceScope / foggy-dataset-model / branch`，observed 精确为 `0/0/null`，minimum=`null`；
6. `below_floor_class_count=0`、`not_applicable_metric_count=1`、outcome=
   `at-or-above-floor`。

复算结果：

```text
candidate_sha256=8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00
aggregate_line=54624/76830 aggregate_branch=26109/44870 exact_projection=passed
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
| `WatchServiceFileTracer` | `204/244` | `98/128` | required / required |
| `QueryFingerprintBuilder` | `159/179` | `65/84` | required / required |
| `SecurityPolicyFingerprint` | `112/117` | `73/92` | required / required |
| `CaffeineQueryCacheProvider` | `126/135` | `64/78` | required / required |
| `RedisQueryCacheProvider` | `152/159` | `73/88` | required / required |

Aggregate reviewed thresholds 精确冻结为 line=`54,624/76,830`、branch=`26,109/44,870`；
它们不是通过小数取整得到的比例，也不得在 canonical threshold 中下调、改写分母或替换为
float。

## Canonical freeze fields

Cfreeze 写入 `coverage-thresholds.json.review` 时应使用：

```yaml
reviewer: Codex /root+xml_full_closure_audit
reviewed_at: 2026-07-17T01:07:00Z
diagnostic_run_id: step4-coverage-20260717-diagnostic-r13
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r13-threshold-review-20260717.md
evidence_sha256: <本文件定稿后的实际 SHA-256>
decision: confirm-observed-thresholds
```

本文件有意不记录最终 confirmed-threshold SHA 或 Cfreeze commit SHA，避免 review evidence 与
canonical threshold 形成哈希环。

## Boundary

- candidate 保持 immutable `review-required`，不得转写为 confirmed candidate；
- Cfreeze 必须是 diagnostic commit 的唯一 direct-single-parent child；
- mandatory delta 为 threshold、coverage contract、Step 4 `SHA256SUMS`，其他仅允许
  `docs/9.3.4/`；
- fresh formal 仍为 `pending`；本 review 不是 formal、coverage audit 或 version acceptance。
