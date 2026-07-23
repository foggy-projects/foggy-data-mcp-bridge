---
doc_role: preexecution_decision_record
version: 9.3.5
gate: Gate-0-unit-mysql57-fixture-classification
status: DRAFT / awaiting-9.3.4-version-signoff
decision_owner: pending
baseline_commit: 0bfe75a9eee8277077d218654fcb6b353857589c
recorded_at: 2026-07-20
---

# 9.3.5 Gate 0：Unit MySQL fixture 分类债务决策包

## 目的与边界

本记录把已确认的两条关闭路径、共同 fail-closed 证据和需要项目 owner 作出的选择列清。它不选择路径，
不改变执行 inventory、测试、runner、POM 或生产代码，也不关闭
`DEBT-unit-mysql57-fixture-classification-migration`。

Gate 0 仅在 9.3.4 version signoff 后作为 9.3.5 的首项执行工作开启；无论选择哪条路径，必须在
9.3.5 version acceptance 前完成并删除临时例外。

## 已冻结事实

- r7 的 immutable historical observation 是 6 reports / 11 testcase errors；它不能被改写或用作
  当前总量。
- 当前 reviewed lower bound 是至少 7 suites / 12 testcase nodes；它不证明其他 Unit 测试没有 DB
  访问。
- `DatasetJdbcUtilsTest#getOrCreateDataSource` 是第 7 个已确认 consumer，执行身份为
  `v934|8:surefire|4:unit|4:unit|51:com.foggyframework.dataset.fun.DatasetJdbcUtilsTest`，其 1 个
  testcase node 必须让 JDBC connection 或 `SELECT 1` 的 `SQLException` 直接传播。
- 9.3.4 的 fresh Step 4 Unit run-owned MySQL 5.7 只提供该版本的临时正确性证据；Step 2 的
  `none/hermetic/step=2` 绿色不得重新作为正确性证据使用。

## 待 owner 选择的关闭路径

| Option | Outcome | Required proof | Prohibited shortcut |
|---|---|---|---|
| A. 迁入受治理 DB lane | 每个真实 DB consumer 有明确的 DB owner/lane | 更新 inventory、精确 report/testcase mapping、run-owned DB execution、fresh fail-closed run、DB/lifecycle negatives | 复用 ambient listener、只移动测试文件、不更新 execution identity |
| B. 去除外部 DB 依赖 | `none/hermetic/step=2` 分类重新可证 | 更新 inventory、测试/实现的依赖消除证据、fresh hermetic fail-closed run、连接/异常负例 | catch/print/`assertDoesNotThrow` 吞掉 JDBC exception、仅在本机有 DB 时绿 |

选择必须覆盖当前 lower bound 以及实施中发现的每一个真实 DB consumer。若发现新 consumer，先更新机器
契约与 decision record，并重新通过 fresh Step 4 diagnostic/formal run、质量闸门和覆盖审计，再进入相应
approved implementation；不得静默新增或删除 frozen execution key。

## 共同验收与负例

- [ ] 更新后的 inventory 明确每个 consumer 的 execution identity、DB/infra 分类、report 与 testcase
  cardinality；差异由审批记录解释。
- [ ] clean/fresh run 在预期基础设施缺失、错误、stale 或 tampered 时 fail closed，且没有 skip 或
  ambient fallback。
- [ ] 正向 run 的报告、testcase、F/E/S、fixture receipt 和 cleanup 能与更新后的 inventory 精确对应。
- [ ] JDBC connection 或 `SELECT 1` failure 不能被吞掉；选择 A/B 后都保留等价的防假绿负例。
- [ ] independent quality、coverage audit 与 9.3.5 version acceptance 明确记录债务删除和遗留风险。

## 决策与执行顺序

1. 9.3.4 version signoff 完成后，project owner 在 A/B 中作出选择，或批准一个不改变两条目标的
   等价方案。
2. 为所选路径创建唯一 approved implementation spec，冻结 inventory delta、兼容边界、测试命令与
   rollback 条件。
3. 实施只覆盖 Gate 0；发现新增 consumer、范围扩大或无法满足 fail-closed proof 时设置
   `NEEDS_REPLAN`，不得顺带启动 QueryFacade/API 或模块化重构。
4. Gate 0 evidence accepted 后，才可按 9.3.5 执行契约进入更广的 phase/public-API 工作。

## References

- governing debt: `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- machine contract: `scripts/v934/step4/unit-mysql57-fixture-contract.json`
- 9.3.5 baseline: `docs/9.3.5/code-inventory.md`
- roadmap: `docs/9.3.1/roadmap-9.3.1-to-9.4.0.md`
