---
doc_role: implementation_decision_record
version: 9.3.5
gate: Gate-0-unit-mysql57-fixture-classification
status: CLOSED / option-A-governed-db-lane
decision_owner: repository-owner-speed-forward
baseline_commit: 3b1c7249ba75b3bab54cb0f898ea1c198e5303d4
recorded_at: 2026-07-24
---

# 9.3.5 Gate 0：Unit MySQL fixture 分类债务关闭记录

## 决策

采用 Option A：7 个已确认的真实 MySQL consumer 从默认 Unit lane 迁入模块级 Failsafe DB lane。
测试类名和 FQCN 保持不变，执行身份改为 `failsafe/mysql57-it`；默认测试上下文改用 hermetic H2，
不再包含 ambient MySQL listener 或带默认值的 MySQL 凭据。

该决定依据 9.4.0 speed-forward 交付契约直接实施。它关闭
`DEBT-unit-mysql57-fixture-classification-migration`，不启动 version authority、Step 5/Step 7、
semantic/portable replay 或 source-seal。

## 当前 inventory

| Suite | Testcase nodes | Runner | Profile | DB owner |
|---|---:|---|---|---|
| `com.foggyframework.dataset.db.dialect.FDialectTest` | 2 | Failsafe | `mysql57-it` | run-owned MySQL 5.7 |
| `com.foggyframework.dataset.fun.DatasetJdbcUtilsTest` | 1 | Failsafe | `mysql57-it` | run-owned MySQL 5.7 |
| `com.foggyframework.dataset.db.utils.JdbcTableUtilsTest` | 4 | Failsafe | `mysql57-it` | run-owned MySQL 5.7 |
| `com.foggyframework.dataset.db.fsscript.SyncSqlTableTest` | 1 | Failsafe | `mysql57-it` | run-owned MySQL 5.7 |
| `com.foggyframework.dataset.db.table.dll.JdbcUpdaterTest` | 2 | Failsafe | `mysql57-it` | run-owned MySQL 5.7 |
| `com.foggyframework.dataset.db.data.dll.SqlTableRowEditorTest` | 1 | Failsafe | `mysql57-it` | run-owned MySQL 5.7 |
| `com.foggyframework.dataset.table.curd.BugFixInsertUpdateMapTest` | 1 | Failsafe | `mysql57-it` | run-owned MySQL 5.7 |

总计为 7 reports / 12 testcase nodes。机器可读契约位于
`scripts/v935/gate0/mysql57-classification.json`。9.3.4 的 Surefire execution key、6/11 历史失败观测和
7/12 reviewed lower bound 保留为历史事实，不再代表当前执行分类。

## 实现边界

- `foggy-dataset` Surefire 明确排除上述 7 个类；显式 Maven profile `mysql57-it` 才把它们加入
  Failsafe，普通 `verify` 不隐式启动 DB lane。
- 7 个类显式激活同名 Spring profile `mysql57-it`；profile 只接受
  `V935_GATE0_MYSQL57_URL/USERNAME/PASSWORD`，没有默认值。
- `application.yml` 的默认测试 datasource 为进程内 H2，仅用于不要求外部数据库的 Spring context
  测试。
- `DatasetJdbcUtilsTest#getOrCreateDataSource` 仍直接传播 JDBC connection 或 `SELECT 1` failure；
  不允许 catch/print/`assertDoesNotThrow` 假绿。
- 原 `scripts/v934/step4/unit-mysql57-fixture-contract.json` 不改写，继续作为 9.3.4 历史契约。

## Fresh 关闭证据

在独立 worktree、基线 `3b1c7249ba75b3bab54cb0f898ea1c198e5303d4` 上完成：

1. `mvn -pl foggy-dataset -am -DskipITs test`
   - reactor SUCCESS；`foggy-dataset` 默认 Unit 为 93 / F0E0S0；非 DB Spring tests 使用 H2。
2. 清除三个 `V935_GATE0_MYSQL57_*` 环境变量后，定向 Failsafe invocation 失败，原因为必需 URL
   placeholder 未解析；证明没有 ambient fallback。
3. 使用唯一 run-owned `mysql:5.7`、随机 host port 和专用非 root 测试账号执行精确 7 个类；
   Failsafe 报告为 7、testcase nodes 为 12、F0E0S0。
4. 临时容器按精确名称停止并由 `--rm` 清理。

可复现入口：`scripts/v935/gate0/verify-mysql57-classification.sh`。该入口只执行 Gate 0 受影响的
Unit/DB lane 和 fail-closed negative，不承担版本级验收。

## 关闭结论

Gate 0 的 classification debt 已关闭：真实 DB consumers 有明确 runner、profile、fixture owner 和
fail-closed 配置；默认 Unit lane 恢复 hermetic 分类。speed-forward 可继续 QueryFacade/DTO 切片。

## References

- governing debt: `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- historical machine contract: `scripts/v934/step4/unit-mysql57-fixture-contract.json`
- current machine contract: `scripts/v935/gate0/mysql57-classification.json`
- canonical delivery contract: `docs/9.4.0/workitems/FEATURE-v934-v940-speed-forward.md`
