---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260718-diagnostic-r21
tested_commit: 5121a9c7fe35120c7864de8554e99188f5d1dc87
candidate_sha256: 5f789e986d9e79854fe98b8433b684564fb37c17c349cf3e80ba66f18ad98fdc
reviewer: "Codex /root+Reviewer A+Reviewer B"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/r20_postrun_playbook (Reviewer A)"
  - "Codex Reviewer B (/root/v935_scope_inventory)"
reviewed_at: 2026-07-18T16:33:09Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r21 threshold review

## Decision

确认 `step4-coverage-20260718-diagnostic-r21` 的 exact observed counters 可作为 Step 4 reviewed
thresholds，decision=`confirm-observed-thresholds`。确认责任由 `Codex /root` 发起；Reviewer A
独立复算 metric/exact projection，Reviewer B 独立复算 evidence binding 与 portable capsule。

本记录不声称用户或 release owner 已人工签收，`user_confirmation=not-asserted`。Decision 只授权
把 candidate exact projection 写入 Cdiag `5121a9c7…` 的 direct-single-parent Cfreeze。Fresh
formal、coverage audit、acceptance 与 Step 4 exit 均不在本次确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260718-diagnostic-r21` |
| diagnostic Git HEAD | `5121a9c7fe35120c7864de8554e99188f5d1dc87` |
| diagnostic source SHA-256 | `6b6796d496c3ee16bf66a410665e19fcfb8861c1d650050a9442e7d4f3e97f4d` |
| run status SHA-256 | `4f55749f529520e0d302674cd5fc7aaffea31f25ea6b14f1d4bee3dc4f7d87cc` |
| summary SHA-256 | `a1488df8d09652e06a3db901ca65238c9ed16d14e2be0feb9161156dff9f95a2` |
| observation SHA-256 | `8e0b0ad547524012cdc620fad50ff4b01d6f8d25bd034a61aa28fb51fa1d27d2` |
| diagnostic contract SHA-256 | `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b` |
| candidate SHA-256 | `5f789e986d9e79854fe98b8433b684564fb37c17c349cf3e80ba66f18ad98fdc` |
| capsule archive SHA-256 / size | `7e1b9461373f4f1e1f231b6d39711cd020938fa8182276beee7fe9e39abf0444 / 20,014,034` |
| capsule manifest SHA-256 | `8c1fcd86ddd046e7a308f5147b4b01aa000fe7880c540f2d73d4302ac13c37a6` |

## Independent recomputation

Reviewer A 未 import 官方 candidate builder，严格解析 observation/candidate/Step 1 policy 并用
整数交叉乘法复算；Reviewer B 逐份验证 run status、summary、raw exec、aggregate exec/XML、
source/class tree、provenance 和 capsule，并 materialize 到真正空目录：

1. candidate review 前、public verify 后、materialize 后 SHA-256 均为
   `5f789e986d9e79854fe98b8433b684564fb37c17c349cf3e80ba66f18ad98fdc`；
2. aggregate 六类 counters 与 observation、r19、r20 exact 相同：instruction=
   `252725/352456`、line=`54624/76830`、branch=`26111/44870`、complexity=
   `17658/35571`、method=`9068/12701`、class=`1503/1716`；
3. critical rows=`12`，FQCN/module/order/计数三方一致；required-positive=`23`，每项
   `minimum == observed`；line `12/12` 达到 `4/5`，branch `11/11` 达到 `7/10`；
4. 唯一 N/A=`NamespaceScope / foggy-dataset-model / branch`，observed=`0/0/null`、
   minimum=`null`；below-floor=`0`；
5. raw exec=`23/23`，sessions=`48`，execution class identities=`16,945`；production universe=
   `24 modules / 2,098 classes`，class tree binding exact；
6. report inventory=`773+59/5707/F0E0S0`，Addon=`2/6/F0E0S0` 且未混入 required union；
7. source before/after byte-identical；run/head/source/status/summary/observation/exec/provenance 的
   九项 candidate hash binding 全部与实际文件一致；
8. capsule double build archive/manifest `cmp=passed`，schema=`1`、status=`sealed`、entries=
   `6,638`、`omitted_negative_fixture_symlinks=[]`；
9. archive actual hash/size 与 manifest exact；public verify=`passed`；
10. empty sandbox materialize=`passed`，materialized entries=`6,638`、symlink=`0`，关键文件
    SHA 与原证据 exact。

```text
candidate_sha256=5f789e986d9e79854fe98b8433b684564fb37c17c349cf3e80ba66f18ad98fdc
aggregate_line=54624/76830 aggregate_branch=26111/44870 exact_projection=passed
critical_rows=12 positive_metrics=23 n_a_metrics=1 below_floor_classes=0
exec_sessions_classes=23/48/16945 production_universe=24/2098
capsule_entries=6638 capsule_symlinks=0 deterministic_build=passed materialize=passed
reviewer_a=APPROVE reviewer_b=APPROVE findings=0/0/0/0
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
不得通过 float、取整、分母重缩放、exclusion 或 partial evidence 改写。

## Canonical freeze fields

Cfreeze 写入 `coverage-thresholds.json.review` 时使用：

```yaml
reviewer: Codex /root+Reviewer A+Reviewer B
reviewed_at: 2026-07-18T16:33:09Z
diagnostic_run_id: step4-coverage-20260718-diagnostic-r21
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r21-threshold-review-20260719.md
evidence_sha256: <本文件定稿后的实际 SHA-256>
decision: confirm-observed-thresholds
```

本文件不记录最终 confirmed threshold SHA 或 Cfreeze commit SHA，避免 review evidence 与
canonical threshold 形成哈希环。

## Findings and boundary

- Reviewer A reviewed_at=`2026-07-18T16:30:51Z`，decision=`APPROVE`；
- Reviewer B reviewed_at=`2026-07-18T16:33:09Z`，decision=`APPROVE`；
- Blocker/High/Medium/Low=`0/0/0/0`；
- candidate 保持 immutable `review-required`，不得改写为 confirmed candidate；
- Cfreeze 必须是 Cdiag `5121a9c7fe35120c7864de8554e99188f5d1dc87` 的唯一
  direct-single-parent child；
- mandatory machine delta 为 threshold、coverage contract、Step 4 manifest、Step 6
  contract/tool/manifest；其他只允许 `docs/9.3.4/**`；
- fresh formal=`pending`；本 review 不是 formal、coverage audit 或 acceptance。
