---
type: bug
bug_source: diagnostic-governance-found
version: 9.3.4
ticket: BUG-934-STEP4-PIVOT-NULL-AXIS-COVERAGE-ORACLE
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: deterministic-pivot-null-axis-correctness-regression
automation_decision: required
owner: step4-coverage
---

# Step 4 Pivot NULL axis coverage oracle 缺口

## Background

fresh diagnostic `step4-coverage-20260717-diagnostic-r18` 在 clean/pushed Cdiag
`5be1edaa16c5883cde2f66396ac26a1ae113430b` 上完成全部测试与证据 lane，runner 和 public
validation 均 PASS。然而 r18 aggregate 相对已 review 的 r16 exact high-water 少
`2 covered line / 4 covered branch`。逐 class/XML/source line 比较证明差异只在
`BaselineRatioCalculator` 与 `ResultShaper` 的 NULL row/column axis 路径。

governed evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`。

r18 threshold candidate 被治理层明确拒绝并保持 absent。不能用较低的 fresh observation 覆盖
r16 reviewed high-water，也不能把完整 diagnostic PASS 解释成 coverage-oracle 稳定。

## Expected vs Actual

- Expected：相同 production class tree 和完整报告库存下，NULL column-axis member 必须被
  `BaselineRatioCalculator` 明确排除在 first/last baseline domain 外，且 NULL cell 仍以真实
  first/last member 计算 ratio。
- Expected：NULL row-axis member 必须由 `ResultShaper` 确定性输出为独立 `__null__` tree node，
  不得与具名 member 合并或丢失 cells。
- Expected：上述行为由显式、确定性自动化 oracle 覆盖；fresh diagnostic/formal 不依赖共享
  fixture、LEFT JOIN 是否恰好产生 unmatched tuple 或 testcase/lane 顺序来命中 probes。
- Actual：r16 aggregate line=`54,624/76,830`、branch=`26,111/44,870`；r18 line=
  `54,622/76,830`、branch=`26,107/44,870`，分母、critical rows 和 production class tree 不变。
- Actual：`BaselineRatioCalculator` 从 line=`98/120`、branch=`57/88` 降至
  `96/120`、`54/88`；`ResultShaper` branch 从 `54/63` 降至 `53/63`。
- Actual：r16 命中、r18 未命中的 exact source 是 `BaselineRatioCalculator.java:76-78,82,209`
  与 `ResultShaper.java:91`；两次 all-lane 业务测试均为 F0E0S0。
- Actual：r18 public validation PASS、12/12 critical floor PASS，说明 coverage tooling、库存和
  provenance 正常；缺口位于业务测试 oracle，而不是证据聚合器。

## Root Cause

现有 `PivotSqlParityIT#testBaselineRatioParity` 通过真实数据库 LEFT JOIN 结果验证 baselineRatio
parity，但 testcase 自身没有构造或要求 unmatched row/column axis tuple。现有
`BaselineRatioCalculatorTest` 与 `ResultShaperTest` 同样只使用非 NULL axis values。因此，只有当
其他测试共享状态或某个数据库 lane 的 fixture 恰好留下 unmatched dimension row 时，以下生产
outcome 才会被顺带命中：

1. column axis value 为 NULL，设置 `hasNull` 并跳过该 tuple；
2. NULL coordinate 经 `buildKey` fallback 参与自己的 row group；
3. row axis value 为 NULL，tree shaper 使用 `__null__` fallback node。

r16 偶然覆盖这些 outcome，r18 没有覆盖；两次仍全部断言绿色。这证明 high-water 来自 incidental
data path，而非 stable correctness oracle。r16 到 r18 两个目标 production source byte-identical，
workspace class tree SHA-256 均为
`a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be`，所以当前判断为
test-evidence regression，不是 product regression。

## Fix Strategy

1. 在既有 Pivot parity testcase 内加入受控的 in-memory NULL-axis fixture，保持既有 testcase
   identity 和 Unit/Integration/required cardinality，不依赖数据库 mutation 或 method order。
2. 同时构造 NULL row+NULL column、NULL row+first/last real column、具名 row+first/last real
   column；通过 `BaselineRatioCalculator.apply` 验证 NULL column 不成为 baseline，但其 cell 使用
   real first/last denominator。
3. 验证 NULL row 与具名 row 的 baseline group 隔离，first/last ratio exact 正确，避免只追求
   probe 命中而不证明业务语义。
4. 将同一受控结果交给 `ResultShaper.shape(..., tree)`，断言独立 `__null__` node、具名 node、
   NULL-column cells 与 ratios 均保留。
5. 不修改 production、public API、POM、runner、aggregate/critical threshold 或 exclusion；不以
   降低 r16 high-water、扩展 N/A 或删除断言收口。
6. focused test 必须经过多个 fresh Maven/JVM/JaCoCo fork，比较两个 class 的 exact counters 或
   probe bitmap；随后完整重走 Cdiag -> fresh diagnostic -> threshold review -> direct-child
   Cfreeze -> fresh formal。

## Regression Test Decision

`automation_decision=required`。这是 exact aggregate formal gate 的 blocker；仅记录 r18 较低值、
手工检查输出或重跑碰运气都无法关闭。自动化必须直接断言 NULL-axis correctness，并以多 fresh fork
证明 coverage outcome 稳定。focused 绿色只验证修复方向，不能代替 all-lane diagnostic 或 formal
authority。

## Fix Checklist

- [x] r18 sealed diagnostic、public validation、source seal、cleanup 与 evidence-window 外 DB restore
  已确认。
- [x] required/Add-on 库存、exec/session、class universe 和全部 critical rows 已确认完整。
- [x] r18/r16 aggregate delta 已定位为 line `-2`、branch `-4`，denominator 不变。
- [x] 唯一 2 个 class 与 6 个 source line 已定位，目标 production source 无变更。
- [x] 根因确认为 NULL-axis outcome 缺少 deterministic correctness oracle；r18 candidate 保持 absent。
- [x] 在既有 testcase 内实现受控 NULL row/column axis regression，不改变 testcase identity/count。
- [x] 三次 fresh JVM/JaCoCo focused 均 `1/F0E0S0`；`BaselineRatioCalculator=79/131 probes`、
  `ResultShaper=45/139 probes`，两类 bitmap 均 `3/3 identical`。
- [x] 完整相关 test class=`PivotSqlParityIT 23/F0E0S0`，successor protected overlay 与双层
  manifest PASS，且无 production/threshold/exclusion 变更。
- [x] pre-Cdiag formal implementation quality=`PASS / 0/0/0/0`；只授权形成
  commit/push/clean Cdiag identity 与 fresh r19。
- [ ] replacement fresh diagnostic 全 lane PASS，aggregate 不低于 r16
  `54624/76830 line, 26111/44870 branch`。
- [ ] replacement threshold candidate/public verification/independent review PASS。
- [ ] direct-child Cfreeze commit/push，fresh formal PASS。
- [ ] final implementation quality、coverage audit 与 acceptance PASS。

## Evidence Mapping

| Requirement / finding | Current evidence | Closure evidence required |
|---|---|---|
| r18 all-lane completeness | r18 `run-status.env`、`summary.env`、`report-inventory.json` | complete |
| Public diagnostic recomputation | `validate-diagnostic-run` -> observation `7a02bfaa...dd76` | complete |
| r16 governed high-water | r16 PASS evidence + reviewed threshold | preserve, no lowering |
| Exact class/source localization | r16/r18 aggregate JaCoCo XML pair | complete |
| NULL column baseline semantics | `BaselineRatioCalculator.java:76-82,209` | deterministic automated assertions + focused proof |
| NULL row tree semantics | `ResultShaper.java:91` | deterministic automated assertions + focused proof |
| All-lane stability | none after remediation | replacement fresh diagnostic |
| Release authority | none | candidate/review/Cfreeze/fresh formal |

## Closure Scope

本 BUG 保持 `blocker / in-progress`。只有 deterministic regression、多 fork focused proof、
replacement fresh diagnostic、reviewed threshold 与 fresh formal 全部通过后才可关闭。r18 永久保留为
valid diagnostic + governed high-water gap evidence；不得补写 candidate、修改 sealed artifact 或把
public validation PASS 冒充 threshold-freeze authorization。

## References

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/algo/BaselineRatioCalculator.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/algo/ResultShaper.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIT.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/algo/BaselineRatioCalculatorTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/algo/ResultShaperTest.java`
- `scripts/v934/step4/coverage-thresholds.json`
- `scripts/v934/step4/coverage_xml_tool.py`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-pass-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-threshold-review-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`
