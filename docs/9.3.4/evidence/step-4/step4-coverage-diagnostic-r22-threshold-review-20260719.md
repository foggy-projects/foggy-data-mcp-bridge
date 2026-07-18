---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r22
tested_commit: cfb64d629b06f03a078ebae06580918d4c8df301
candidate_sha256: 044f4b9f717b062d2c214d1c9b2494ec7da2d77e8ec69275acdf66eb01c70d5a
reviewer: "Codex /root+Reviewer A+Reviewer B"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/v935_execution_contract (Reviewer A)"
  - "/root/v940_module_boundary (Reviewer B)"
reviewed_at: 2026-07-18T19:11:27Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r22 threshold review

## Decision

确认 `step4-coverage-20260719-diagnostic-r22` 的 exact observed counters 可作为 Step 4 reviewed
thresholds，decision=`confirm-observed-thresholds`。确认责任由 `Codex /root` 发起；Reviewer A
独立复算 candidate、metric、evidence 与 critical projection，Reviewer B 独立复算 source、
provenance、report universe 与 portable capsule。

本记录不声称用户或 release owner 已人工签收，`user_confirmation=not-asserted`。Decision 只授权
把 candidate exact projection 写入 Cdiag `cfb64d62…` 的 direct-single-parent Cfreeze。Fresh
formal、coverage audit、acceptance 与 Step 4 exit 均不在本次确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260719-diagnostic-r22` |
| diagnostic Git HEAD | `cfb64d629b06f03a078ebae06580918d4c8df301` |
| diagnostic source SHA-256 | `abb26710a7c7c9c23b31219a5c9e907a575227bd12e5af7ed055241e70118666` |
| run status SHA-256 | `a83ebf931b4c5d349ef665d26c831954d176d8a1a3783780dac801b402e8c6e1` |
| summary SHA-256 | `a50f7bdc895248eedfcb5a92521944bfd05ef34a22092ca477f47aac75e3225f` |
| observation SHA-256 | `974c7e4c0434a7c7fb6650913b262c1c8dd18607787f01c7cc3a629eda58b04b` |
| diagnostic contract SHA-256 | `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b` |
| candidate SHA-256 | `044f4b9f717b062d2c214d1c9b2494ec7da2d77e8ec69275acdf66eb01c70d5a` |
| capsule archive SHA-256 / size | `b7a6282bed98013ac65df6e630cda4980951b835b0c128d6a47a76e07b1482b9 / 20,008,564` |
| capsule manifest SHA-256 | `bf61342c3a7cd35924e72d1fc861587da462ff19663c2722065eab64653877b4` |

## Independent recomputation

两路 reviewer 未读取彼此结论，分别从密封 run/candidate/capsule 重算：

1. candidate review 前、public verify 后、materialize 后 SHA-256 均为
   `044f4b9f717b062d2c214d1c9b2494ec7da2d77e8ec69275acdf66eb01c70d5a`；
2. aggregate 六类 counters 与 observation 和既有 reviewed high-water exact 相同：instruction=
   `252725/352456`、line=`54624/76830`、branch=`26111/44870`、complexity=
   `17658/35571`、method=`9068/12701`、class=`1503/1716`；
3. critical rows=`12`，FQCN/module/order/计数三方一致；required-positive=`23`，每项
   `minimum == observed`；line `12/12` 达到 `4/5`，branch `11/11` 达到 `7/10`；
4. 唯一 N/A=`NamespaceScope / foggy-dataset-model / branch`，observed=`0/0/null`、
   minimum=`null`；below-floor=`0`；
5. raw exec=`23/23`，sessions=`48`，execution class identities=`16,946`；production universe=
   `24 modules / 2,098 classes`，class tree binding exact；
6. report inventory=`773+59/5707/F0E0S0`，Addon=`2/6/F0E0S0` 且未混入 required union；
7. source before/after byte-identical；run/head/source/status/summary/observation/exec/provenance 的
   candidate hash binding 全部与实际文件一致；
8. capsule double build archive/manifest `cmp=passed`，schema=`1`、status=`sealed`、entries=
   `6,638`、`omitted_negative_fixture_symlinks=[]`；
9. archive actual hash/size 与 manifest exact；public verify=`passed`；
10. empty sandbox materialize=`passed`；source closure=`4,060 files / 80,528,318 bytes`，
    missing/type/mode/mtime/size/SHA mismatch 全部为 `0`。

```text
candidate_sha256=044f4b9f717b062d2c214d1c9b2494ec7da2d77e8ec69275acdf66eb01c70d5a
aggregate_line=54624/76830 aggregate_branch=26111/44870 exact_projection=passed
critical_rows=12 positive_metrics=23 n_a_metrics=1 below_floor_classes=0
exec_sessions_classes=23/48/16946 production_universe=24/2098
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
reviewed_at: 2026-07-18T19:11:27Z
diagnostic_run_id: step4-coverage-20260719-diagnostic-r22
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r22-threshold-review-20260719.md
evidence_sha256: <本文件定稿后的实际 SHA-256>
decision: confirm-observed-thresholds
```

本文件不记录最终 confirmed threshold SHA 或 Cfreeze commit SHA，避免 review evidence 与
canonical threshold 形成哈希环。

## Findings and boundary

- Reviewer A decision=`APPROVE`；
- Reviewer B decision=`APPROVE`；capsule 子复核 8/8 negatives=`APPROVE`；
- Blocker/High/Medium/Low=`0/0/0/0`；
- candidate 保持 immutable `review-required`，不得改写为 confirmed candidate；
- Cfreeze 必须是 Cdiag `cfb64d629b06f03a078ebae06580918d4c8df301` 的唯一
  direct-single-parent child；
- mandatory machine delta 为 threshold、coverage contract、Step 4 manifest、Step 6
  contract/tool/manifest；其他只允许 `docs/9.3.4/**`；
- fresh formal=`pending`；本 review 不是 formal、coverage audit 或 acceptance。
