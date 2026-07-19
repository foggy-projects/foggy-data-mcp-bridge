---
evidence_type: superseded-pre-remediation-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r25
tested_commit: 5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7
status: in-progress
run_status: diagnostic-observed-superseded
remediation_status: r26-reviewed-cfreeze-pending
decision: remediation-required-non-candidate
candidate_status: r25-forbidden-r26-reviewed
recorded_at: 2026-07-19
---

# Step 4 diagnostic r25 与 Unit MySQL 7/12 remediation evidence

## Decision

`step4-coverage-20260719-diagnostic-r25` 从 clean/pushed Cdiag
`5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7` 完整退出，并通过 public diagnostic validator：

```text
[v934-coverage-xml] DIAGNOSTIC VALID
run=step4-coverage-20260719-diagnostic-r25
observation=01487f7efd930406ffa05af9408012aa1fb215d94ba9c36c261f72c1aec7e42a
```

该结论证明 r25 sealed run 的 lane、source、coverage provenance、cleanup 与公开复算有效。它不证明
run 前 machine contract 已完整列出所有 MySQL consumer。

r25 完成后的独立 consumer audit 确认
`DatasetJdbcUtilsTest#getOrCreateDataSource` 会建立 JDBC connection 并执行 `SELECT 1`，但旧实现
捕获 `SQLException` 后仅打印 stack trace。r25 tested schema 1 contract 只包含 r7 实际报错的
`6 reports / 11 nodes`，因此漏掉该 suite 的 1 个 node。当前 reviewed known-consumer lower bound
是 `7 reports / 12 nodes`。

所以 r25 保持 `diagnostic-observed`，同时被 supersede 为
`pre-remediation / non-candidate`。不得生成 r25 threshold candidate、portable capsule 或 Cfreeze；
不得将 r25 XML/exec 与后续运行拼接。r7 `6/11` 继续是 immutable historical observation，不被
改写为 7/12。

## Sealed r25 observation

- run window：`2026-07-19T00:49:51Z` 至 `2026-07-19T01:59:22Z`；
- status=`diagnostic-observed / completed / exit 0`；
- required=`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion=`2 reports / 6 testcase`；
- exec/session=`23/48`；
- production universe=`24 modules / 2,098 classes`；class-universe SHA-256=
  `e53103549fc7f4f460ca36847c82d441000c433b5619030d688a3c54d046f9b8`；
- aggregate line=`54,624/76,830`、branch=`26,112/44,870`、complexity=
  `17,659/35,571`；critical below-floor=`0`、structural N/A=`1`；
- source before=after SHA-256=
  `2f41810585ade813671740218a2c303b1306a14236337712ef71d3e4aa5b1677`；
- coverage observation SHA-256=
  `01487f7efd930406ffa05af9408012aa1fb215d94ba9c36c261f72c1aec7e42a`；
- runner cleanup container/volume/network=`0/0/0`；
- `acceptance_candidate=not-generated`。

Core artifact receipts：

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `bc5204d63ff213446acb2bd60ee68cb23230c4752e5a3c146e042ac1562be171` |
| `summary.env` | `2fa405eea956b26bd120b95df247fba846a9d7e59547987ad0cc056ccd75e266` |
| wrapper `final/status.env` | `1a88969c4923c41ec0e81c1492b0348b3934d32c773fad2077343ba293e4dffc` |
| wrapper `final/containers.tsv` | `28fb7906d7fdbb31dd88f356f558640074e506ddff5b60b6159acb8980da5b05` |

这些 sealed facts 不因 follow-up contract finding 而改写；变化的是 r25 的 downstream eligibility。

## Outer wrapper restore receipt

wrapper final status=`runner_rc=0 / restore_rc=0 / receipt_inspect_rc=0 /
wrapper_outcome_rc=0`。四个 evidence window 外开跑前 demo DB container 均以 original ID exact
恢复并为 `running / healthy`：

| Container | Original/current exact ID | Result |
|---|---|---|
| `foggy-demo-mysql` | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` | ID exact；running/healthy |
| `foggy-demo-mysql8` | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` | ID exact；running/healthy |
| `foggy-demo-postgres` | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` | ID exact；running/healthy |
| `foggy-demo-sqlserver` | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` | ID exact；running/healthy |

外层 restore 不混入 runner diagnostic artifact；这里只记录 evidence-window 后的环境恢复事实。

## 7/12 finding

漏记 consumer：

```text
execution_key=v934|8:surefire|4:unit|4:unit|51:com.foggyframework.dataset.fun.DatasetJdbcUtilsTest
testcase=getOrCreateDataSource
testcase_nodes=1
```

旧 oracle 的关键行为：

```text
ds.getConnection()
prepareStatement("select 1").executeQuery()
catch (SQLException e) { e.printStackTrace(); }
```

因此：

- r7 historical observed failures=`6 reports / 11 nodes`；
- reviewed known database consumers lower bound=`7 reports / 12 nodes`；
- r25 tested schema 1 known set=`6/11`，不足以授权 candidate；
- 这是一项 test-oracle / evidence-contract blocker，不是 r25 lane 执行或 product regression。

## Remediation state

计划中的最小闭包：

1. [implemented / local observed] `getOrCreateDataSource` 改为 `throws SQLException`，以
   try-with-resources 管理 connection、statement、result set，并对 `SELECT 1` 的列数、唯一行
   和值作精确断言；
2. [completed] fixture contract schema 2 分离 immutable historical `6/11` 与 reviewed known-consumer
   `reports_minimum/testcase_nodes_minimum=7/12`，纳入第 7 个 exact execution key；
3. [completed] fixture validator/negative=`42/42`；Step 4=`61/61 / 4805dd3e…`、successor=
   `14/14`、overlay=`20/20`、Step 6=`16/16 / 84407570…`，hash closure 完整；
4. [completed] machine validators 与 pre-Cdiag implementation quality=
   `APPROVE / B/H/M/L 0/0/0/0 / mandatory fixes 0`；
5. [completed] replacement Cdiag commit/push/clean→isolated r4 proof→fresh all-lane r26；未复用
   r25 artifacts；
6. [completed through review] r26 public-valid、7/12 exact、candidate/capsule/双审均 PASS；
   direct-child Cfreeze→fresh formal-r8→final quality/audit/acceptance 仍 pending。

Local pre-Cdiag oracle observation（non-authority）：

- positive：同一 disposable MySQL 随机端口，Maven rc=`0`，XML=`1/F0E0S0`，XML SHA-256=
  `7eea8fbe876c27e7600bf6b71851e95704db9209e5da7a1d6b11a458d332ad01`；
- wrong-password negative：Maven rc=`1`，XML=
  `tests=1 / errors=1 / failures=0 / skipped=0`，XML SHA-256=
  `71aeab932af1cc0beb2228eaab93199c7b990d1fd33ff2a47b4680e78a2d6454`；
- disposable container 自动删除；四个 demo DB original ID 未改变且保持 healthy；
- positive XML 后被 deliberate negative 覆盖，未保存 portable standalone receipt；因此这些数据
  继续只作 local observation，不与后续 authority 拼接。

Replacement evidence：

- Cdiag=`4fe86929de6206aa3e514c974635e90395c28b2e` 已 push/clean；isolated r4 durable proof=
  positive `Maven rc0 / XML 1/F0E0S0`、wrong-password `Maven rc1 / XML 1/F0E1S0`，cleanup=
  container absent、port released；
- r26=`step4-coverage-20260719-diagnostic-r26` public-valid，observation=
  `15e1ed76eaa624c0899b980472689e34e1b272ddda58b2bc5cf27994abffe705`，source before=after=
  `6acfad24cc3d43c3bf550c904aa61c7e01f5b7829d4e2f204d489ab6cc40a8f5`；
- r26 candidate/capsule 与两路 independent review 已完成，B/H/M/L=`0/0/0/0`、mandatory=`0`；
  r25 永久保持 `pre-remediation / superseded / non-candidate`。

BUG/evidence 保持 `in-progress / pre-Cfreeze`。`can_enter_cfreeze=yes`、
`can_enter_coverage_audit=no`、`can_enter_acceptance=no`；Cfreeze、fresh formal-r8 与 post-formal
gates 尚未完成，Step 5–7、9.3.5、9.4.0 均保持关闭。9.3.4 version signoff 后 classification-debt migration
owner=`9.3.5 Gate 0`，deadline=`9.3.5 version acceptance`。

## References

- `docs/9.3.4/workitems/BUG-step4-unit-mysql57-known-consumer-understatement.md`
- `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- `docs/9.3.4/quality/step4-unit-mysql712-remediation-implementation-quality.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`
- `scripts/v934/step4/unit-mysql57-fixture-contract.json`
- `scripts/v934/step4/unit_mysql_fixture_tool.py`
- `foggy-dataset/src/test/java/com/foggyframework/dataset/fun/DatasetJdbcUtilsTest.java`
