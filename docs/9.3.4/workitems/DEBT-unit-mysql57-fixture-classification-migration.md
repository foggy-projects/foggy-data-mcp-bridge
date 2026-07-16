# DEBT：Unit MySQL 5.7 fixture 分类迁移

- 状态：`open / accepted-for-9.3.4-only`
- owner：`foggy-dataset`
- gate owner：`9.3.5 version acceptance`
- 最迟关闭点：`9.3.5` 版本验收前
- 发现来源：`step4-coverage-20260716-diagnostic-r7`
- 机器契约：`scripts/v934/step4/unit-mysql57-fixture-contract.json`

## 债务说明

Step 2 冻结 inventory 将 6 个、合计 11 个 testcase node 的 `foggy-dataset` suite 记为
`db_kind=none / infra_kind=hermetic / execution_step=2`。r7 在 ambient MySQL 不可用时证明该分类不真实。
9.3.4 不改写已签收的 Step 2 身份与计数，而由 fresh Step 4 Unit 全 lane、run-owned MySQL 5.7
执行替代其正确性证据；Step 2 绿色在 Unit lane 仅保留结构含义，不得再次作为正确性证据复用。

该例外作用于完整的 frozen Unit Maven invocation（`681+55 / 4,941`），6/11 是已知隐藏依赖清单，
不是“其他测试已证明绝无数据库访问”的声明。新增 execution key 被禁止；若发现新的隐藏数据库消费者，
必须先更新机器契约并重新通过 Step 4 formal run、质量闸门和覆盖审计。

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

1. 将 6 个已知 suite 及后续发现的真实 DB consumer 迁入受治理的数据库 lane；或
2. 去除其外部数据库依赖，使 `none/hermetic/step=2` 分类重新可证。

关闭证据必须包含更新后的 inventory、fresh fail-closed run、精确 report/testcase 映射、负例和正式验收记录。
