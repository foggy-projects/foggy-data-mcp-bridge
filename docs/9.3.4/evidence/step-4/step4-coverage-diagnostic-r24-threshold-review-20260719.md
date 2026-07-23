---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r24
tested_commit: 414c8b12bff31155584e639e74987bf22df13ba9
candidate_sha256: f13f3c351eee905ec8985591652adc95fae9b1fbec148585ca6100a428caa2ee
reviewer: "Codex /root+Reviewer A+Reviewer B"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/r23_doc_pattern_audit (Reviewer A)"
  - "/root/r24_machine_preflight (Reviewer B)"
reviewed_at: 2026-07-18T23:01:52Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r24 threshold review

## Decision

确认 `step4-coverage-20260719-diagnostic-r24` 的 exact observed counters 可作为 Step 4 reviewed
thresholds，decision=`confirm-observed-thresholds`。确认责任由 `Codex /root` 发起；Reviewer A
独立复算 candidate、metric、target probe 与 critical projection，Reviewer B 独立复算 source、
repository identity、portable capsule 与真正空目录 materialization。

本记录不声称用户或 release owner 已人工签收，`user_confirmation=not-asserted`。Decision 只授权
把 candidate exact projection 写入 Cdiag `414c8b12…` 的 direct-single-parent Cfreeze。Fresh
formal-r7、coverage audit、acceptance 与 Step 4 exit 均不在本次确认范围内。

## Reviewed inputs

| Input | Identity |
|---|---|
| diagnostic run | `step4-coverage-20260719-diagnostic-r24` |
| diagnostic Git HEAD | `414c8b12bff31155584e639e74987bf22df13ba9` |
| diagnostic source SHA-256 | `c9c0a57926b25302bd40e8260410ec3a366dcc5b647676143cafccb15d25c607` |
| run status SHA-256 | `d248d19163f54d116157ac746d7f120f3783c55e3916e84812b27800bf131d81` |
| summary SHA-256 | `fb2dd9c575d1c72122f010255201a699f1d12605574f581556b43f95848013f2` |
| observation SHA-256 | `48b72388c47a8010c7c05b82f345841a4277dcbe5ce52f4238e6b071e7ddc387` |
| diagnostic contract SHA-256 | `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530` |
| threshold predecessor SHA-256 | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| workspace class tree SHA-256 | `ba17cb2e724e875c05bf6b80826dcb87b2897b4a0da6f754989290a24304f57b` |
| candidate SHA-256 | `f13f3c351eee905ec8985591652adc95fae9b1fbec148585ca6100a428caa2ee` |
| capsule archive SHA-256 / size | `31a52185ccea806689e1d0daf80cf24f5cf6a2a9c2bf8ad037a30be4cca32f38 / 20,014,586` |
| capsule manifest SHA-256 | `903a375586a1355491d5b046e05a8879fc0c3149318c8a802f14a2a3432df32f` |

## Independent recomputation

两路 reviewer 未读取彼此结论，分别从密封 run/candidate/capsule 重算：

1. candidate review 前、public verify 后、rebuild/materialize 后 SHA-256 均为
   `f13f3c351eee905ec8985591652adc95fae9b1fbec148585ca6100a428caa2ee`；
2. aggregate 六类 counters 与 observation exact：instruction=`252725/352456`、line=
   `54624/76830`、branch=`26112/44870`、complexity=`17659/35571`、method=`9068/12701`、
   class=`1503/1716`；
3. r23→r24 的全量 class/method/source-line counter map 差异为 `0`；r24 由受控 regression
   将目标方法 branch=`4/4`、complexity=`3/3`、raw Unit probe=`10/11 / _wU` 稳定重现；
4. critical rows=`12`，FQCN/module/order/计数三方一致；required-positive=`23`，每项
   `minimum == observed`；line `12/12` 达到 `4/5`，branch `11/11` 达到 `7/10`；
5. 唯一 N/A=`NamespaceScope / foggy-dataset-model / branch`，observed=`0/0/null`、
   minimum=`null`；below-floor=`0`；
6. raw exec=`23/23`，sessions=`48`，execution class identities=`16,953`；production universe=
   `24 modules / 2,098 classes`，class tree binding exact；
7. report inventory=`773+59/5707/F0E0S0`，Addon=`2/6/F0E0S0` 且未混入 required union；
8. source before/after byte-identical；run/head/source/status/summary/observation/exec/provenance 的
   candidate hash binding 全部与实际文件一致；
9. capsule 两次独立 rebuild 彼此且与 canonical archive/manifest `cmp=passed`，schema=`1`、
   status=`sealed`、entries=`6,638`、files/dirs=`5,884/754`、symlink/special=`0/0`；
10. empty sandbox materialize=`passed`；tracked source closure=`4,077 files / 102,591,681 bytes`，
    missing/extra/type/mode/mtime/size/SHA mismatch 全部为 `0`。

```text
candidate_sha256=f13f3c351eee905ec8985591652adc95fae9b1fbec148585ca6100a428caa2ee
aggregate_line=54624/76830 aggregate_branch=26112/44870 exact_projection=passed
critical_rows=12 positive_metrics=23 n_a_metrics=1 below_floor_classes=0
exec_sessions_classes=23/48/16953 production_universe=24/2098
target_class_id=a6629aa379049ec7 target_probe=10/11/_wU target_method=4/4
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

Aggregate reviewed thresholds 精确冻结为 line=`54,624/76,830`、branch=`26,112/44,870`；
不得通过 float、取整、分母重缩放、exclusion 或 partial evidence 改写。

## Canonical freeze fields

Cfreeze 写入 `coverage-thresholds.json.review` 时使用：

```yaml
reviewer: Codex /root+Reviewer A+Reviewer B
reviewed_at: 2026-07-18T23:01:52Z
diagnostic_run_id: step4-coverage-20260719-diagnostic-r24
evidence_path: docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r24-threshold-review-20260719.md
evidence_sha256: <本文件定稿后的实际 SHA-256>
decision: confirm-observed-thresholds
```

本文件不记录最终 confirmed threshold SHA 或 Cfreeze commit SHA，避免 review evidence 与
canonical threshold 形成哈希环。

## Findings and boundary

- Reviewer A `/root/r23_doc_pattern_audit` decision=`APPROVE`；
- Reviewer B `/root/r24_machine_preflight` decision=`APPROVE`；
- Blocker/High/Medium/Low=`0/0/0/0`；
- candidate 保持 immutable `review-required`，不得改写为 confirmed candidate；
- Cfreeze 必须是 Cdiag `414c8b12bff31155584e639e74987bf22df13ba9` 的唯一
  direct-single-parent child；
- mandatory machine delta 为 threshold、coverage contract、Step 4 manifest、Step 6
  contract/tool/manifest；其他只允许 `docs/9.3.4/**`；
- fresh formal-r7=`pending`；本 review 不是 formal、coverage audit 或 acceptance。
