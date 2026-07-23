---
review_type: implementation-quality
version: 9.3.4
step: 4
scope: Unit MySQL known-consumer 7/12 remediation
status: approved-pre-cdiag
decision: APPROVE
reviewed_at: 2026-07-19
blocker: 0
high: 0
medium: 0
low: 0
mandatory_fixes: 0
---

# Step 4 Unit MySQL 7/12 remediation implementation quality

## Decision

独立实现审查结论为 `APPROVE`，B/H/M/L=`0/0/0/0`，mandatory fixes=`0`。本结论授权创建
new Cdiag、push/clean 后执行 isolated proof 与 fresh diagnostic-r26；不替代 clean-commit、完整
Unit/all-lane、candidate、formal 或 post-gate authority。

## Reviewed implementation

- `DatasetJdbcUtilsTest#getOrCreateDataSource` 保持同一 FQCN、同一 `@Test` 和 `1` 个 node；
  `SQLException` 直接传播，删除 catch-and-log；`Connection`、`PreparedStatement`、`ResultSet`
  均由 try-with-resources 管理，并精确断言 `SELECT 1` 的列数、唯一行和值；
- source 全 LF，`.gitattributes` 对该 exact path 固定 `text eol=lf`；raw SHA-256=
  `74159d7ab93c6638d9a7b8a849f65615e157fa0bd52db11d91073fa0148598e4`；
- fixture contract schema 2 已将 immutable r7 observed failure `6/11` 与 reviewed known-consumer
  lower bound `7/12` 分离；contract SHA-256=
  `4284155dfed2b4ea1691f6a7569310ef858502fd578fe08cd8df30e492ce6ccb`；
- production validator 与 negatives 共用真实 repository-binding helper。真实 copied Step2
  inventory/source probes 先通过 baseline，再执行单一 mutation，避免 unrelated failure 假通过；
  tool SHA-256=`463b040594010823b88248e4968e0a0d0e0a950b2d8a77bf3f9dfa6e2734b299`。

## Machine evidence

- Java `test-compile`：PASS；
- fixture contract：schema=`2`、historical=`6/11`、known lower bound=`7/12`；
- fixture negatives/verify=`42/42`，receipt SHA-256=
  `26804c87f393685d6c5fd77daddc875273edce6bd1b9622f3b5c652fd443d4db`；
- fixture lifecycle=`5/5`；exact demo MySQL restore receipt为 runner/restore/outcome=`0/0/0`，
  original ID exact、`running/healthy`；
- successor manifest=`14/14`，SHA-256=
  `bde8572401f6195652d45f56759f93763b042b6c2bcd68480f585fb42e16becb`；
  overlay validate PASS、negative=`20/20`；
- Step 4 manifest=`61/61`，SHA-256=
  `4805dd3e0af565f6eab760d67d78bbcaa6fee6e655e4df934000f8f5d2e872a0`；
- coverage contract PASS、negative=`27/27`，source-hash Git cases=`22/22`，run-log lifecycle PASS；
- Step 6 manifest=`16/16`，SHA-256=
  `84407570db8a21feb3fded97c3dff0ee692143ff9e927bfa5b3977e86c18d850`；
  workflow validation PASS、negative=`86/86`；
- `py_compile`、`git diff --check`：PASS。

## Dynamic evidence boundary

修复前工作树曾做一次 local focused observation：正向观察为 `1/F0E0S0`，错误密码观察为
`1 error / Maven rc 1`。正向 XML 随后被 deliberate negative 覆盖，未形成 portable standalone
receipt，因此该观察不作为 authority。new Cdiag push/clean 后必须在 isolated checkout 重跑正/负
proof，并持久化两份 XML、Maven rc、fixture identity 与 cleanup receipt；随后 fresh r26 才能提供
完整 Unit/all-lane authority。

## Remaining gates

1. new Cdiag commit/push/clean；
2. isolated durable focused positive/negative proof；
3. fresh diagnostic-r26、candidate/capsule/review；
4. direct-child Cfreeze、fresh formal-r8；
5. final quality→coverage audit→acceptance。
