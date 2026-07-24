# DEBT：Unit MySQL 5.7 fixture 分类迁移

- 状态：`closed / migrated-to-governed-mysql57-it-lane`
- owner：`foggy-dataset`
- gate owner：`repository-owner-speed-forward`
- 最迟关闭点：`9.3.5` 版本验收前
- 发现来源：`step4-coverage-20260716-diagnostic-r7`；2026-07-19 follow-up consumer audit
- 机器契约：`scripts/v934/step4/unit-mysql57-fixture-contract.json`
- 当前分类契约：`scripts/v935/gate0/mysql57-classification.json`

## 债务说明

Step 2 冻结 inventory 将至少 7 个、合计至少 12 个 testcase node 的 `foggy-dataset` suite 记为
`db_kind=none / infra_kind=hermetic / execution_step=2`。r7 在 ambient MySQL 不可用时观察到其中
6 个报告 / 11 个 testcase error，证明该分类不真实；2026-07-19 follow-up audit 又确认
`DatasetJdbcUtilsTest#getOrCreateDataSource` 会真实建立 JDBC 连接并执行 `SELECT 1`，但旧实现吞掉
`SQLException`，因此不能出现在 r7 的 Maven error 集中。
9.3.4 不改写已签收的 Step 2 身份与计数，而由 fresh Step 4 Unit 全 lane、run-owned MySQL 5.7
执行替代其正确性证据；Step 2 绿色在 Unit lane 仅保留结构含义，不得再次作为正确性证据复用。

该例外作用于完整的 frozen Unit Maven invocation（`681+55 / 4,941`）。r7 的 `6/11` 是不可改写的
历史失败观测集；当前 `7/12` 是 reviewed lower bound，也不是“其他测试已证明绝无数据库访问”的声明。
不得新增或删除 frozen execution key；若发现新的隐藏数据库消费者，必须先更新机器契约并重新通过
fresh Step 4 diagnostic/formal run、质量闸门和覆盖审计。

第 7 个 consumer 的执行身份为：

`v934|8:surefire|4:unit|4:unit|51:com.foggyframework.dataset.fun.DatasetJdbcUtilsTest`

其 testcase node 数为 `1`。测试必须让连接或 `SELECT 1` 的 `SQLException` 直接传播为失败，不得通过
`catch`、`printStackTrace` 或 `assertDoesNotThrow` 产生假绿。

## 9.3.4 临时放行条件

仅在以下条件全部满足时，9.3.4 才允许带此债务签收：

1. fresh Step 4 Unit 使用唯一 run-owned MySQL 5.7，禁止复用 ambient listener；
2. 测试 JVM 的唯一账号连接回执非空，fixture before/after 一致；
3. Unit 精确为 `681+55 / 4,941 / F0E0S0`，全局仍为 `23 exec / 48 sessions`；
4. schema/tamper negatives、真实 lifecycle negatives、fallback cleanup 全部通过；
5. formal run、实现质量闸门、测试证据覆盖审计和 9.3.4 验收均通过。

任一条件失败即撤销临时放行，Step 4/Step 5 均不得开启后续状态。

## 9.3.5 关闭标准

在 9.3.5 验收前必须二选一完成，并删除本临时例外：

1. 将当前至少 7 个已知 suite / 12 个 node 及后续发现的真实 DB consumer 迁入受治理的数据库 lane；或
2. 去除其外部数据库依赖，使 `none/hermetic/step=2` 分类重新可证。

关闭证据必须包含更新后的 inventory、fresh fail-closed run、精确 report/testcase 映射、负例和正式验收记录。

## 9.3.5 关闭结果（2026-07-24）

已选择并完成路径 1。7 个 suite / 12 个 node 保留原类名和 FQCN，从 Surefire 默认 Unit lane 迁入
`foggy-dataset` 的 Failsafe `mysql57-it` lane：

- 默认 Spring 测试 datasource 改为进程内 H2，Unit lane 不再依赖 ambient MySQL；
- `mysql57-it` profile 的 URL、username、password 均为必填环境变量，无 fallback；
- 缺少配置的 fresh negative 按预期失败；
- 唯一 run-owned MySQL 5.7 正向执行精确产生 7 reports / 12 nodes / F0E0S0；
- `DatasetJdbcUtilsTest` 的 connection/`SELECT 1` 异常继续直接传播。

关闭记录见 `docs/9.3.5/gate-0-classification-decision-record.md`。9.3.4 的临时 Step 4 例外和机器契约
只保留历史含义，不再作为 9.3.5+ 的当前分类或正确性路径。
