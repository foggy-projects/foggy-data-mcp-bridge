---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-MONGO-LOADER-ORDERING
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: addons/foggy-dataset-model-mongo
---

# Mongo model loader 自动配置顺序回归

## Background

9.3.4 Step 2 全量 unit lane 执行到 `foggy-dataset-model-mongo` 时，真实 Spring Boot
上下文没有注册 `TmMongoModelLoaderImpl`。9.3.2 已有的切片契约测试预先手工注册了
`DataSource`，没有覆盖 `DataSourceAutoConfiguration` 与 Mongo model 自动配置的真实注册顺序。

来源为 `scripts/verify-v934-unit.sh step2-unit-r1-20260714`：Mongo 模块共执行 66 个
测试，14 个 error，集中在 `MongoArrayElementAccessTest` 与 `McpAuditLogMongoTest`，
错误均为 `Cannot find TableModelLoader for type [mongo]`。

## Reproduction

生产条件诊断显示 `MongoModelAutoConfiguration` 求值时尚无 `DataSource`，因此
`@ConditionalOnBean` 不匹配；上下文后续才完成 `DataSource` 注册。为消除外部数据库因素，
强化现有 `ApplicationContextRunner` 正向契约，同时导入 `MongoModelAutoConfiguration` 与
`DataSourceAutoConfiguration`，并提供无需建立连接的 MySQL JDBC URL。该修改不新增
Step 1 discovery node，类内仍为冻结的 6 个测试方法。

修复前执行：

```bash
mvn -pl addons/foggy-dataset-model-mongo \
  -Dtest=MongoModelAutoConfigurationContractTest#completeConditionsCreateExactlyOneLoaderWithoutConnectingToMongo \
  test
```

稳定 RED 结果：`Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`。断言同时确认上下文
已有唯一 `DataSource`，但 `TmMongoModelLoaderImpl` 数量为 0。

## Expected vs Actual

- 期望：标准 `DataSourceAutoConfiguration` 与 Mongo 核心 bean 条件满足后，注册唯一
  `TmMongoModelLoaderImpl`。
- 实际：Mongo model 自动配置先于 `DataSourceAutoConfiguration` 求值，缺少
  `DataSource` 时被永久跳过；即使上下文稍后具备 `DataSource` 也不会重新求值。

## Impact Scope

- 影响 Boot 3 标准自动配置装配路径中的 Mongo TM/QM loader。
- loader 缺失会使 Mongo 模型解析 fail-closed，Mongo 查询相关测试和运行时查询失败。
- 手工提前声明 `DataSource` 的切片上下文不受影响，因此原有测试可能伪绿。

## Test Strategy

采用自动化集成契约测试，直接模拟两个自动配置的注册顺序，不依赖真实数据库连接：

1. 先运行强化后的既有 focused 测试并保留 RED 结果。
2. 修复后运行同一 focused 测试确认 GREEN。
3. 运行整个 `MongoModelAutoConfigurationContractTest`，确认禁用、缺类、完整条件和
   back-off 契约均未回归。
4. 由 9.3.4 unit lane 重跑真实 Spring Boot Mongo 测试链路，作为最终回归证据。

## Code Inventory

- `addons/foggy-dataset-model-mongo/src/main/java/com/foggyframework/dataset/db/model/mongo/MongoModelAutoConfiguration.java`
  - 声明相对 `DataSourceAutoConfiguration` 的执行顺序，保留 fail-closed bean 条件。
- `addons/foggy-dataset-model-mongo/src/test/java/io/foggytest/autoconfigure/modelmongo/MongoModelAutoConfigurationContractTest.java`
  - 原位强化既有正向测试，覆盖真实自动配置注册顺序且保持 6 个 discovery nodes。

## Fix Checklist

- [x] 确认全量 unit lane 失败现象与影响测试。
- [x] 用生产条件报告定位 `DataSource` 注册顺序根因。
- [x] 强化既有正向测试并取得可稳定失败的自动配置顺序 RED。
- [x] 为 Mongo model 自动配置声明 `DataSourceAutoConfiguration` 后置关系。
- [x] focused 测试 GREEN。
- [x] 整组自动配置契约测试 GREEN，且保持冻结的 6 个 test nodes。
- [x] 9.3.4 unit lane 重新通过。

## Verification

最小生产修复后验证结果：

```text
focused: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
contract class: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
fresh module unit: Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
focused external Mongo IT: Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
```

执行命令：

```bash
mvn -pl addons/foggy-dataset-model-mongo \
  -Dtest=MongoModelAutoConfigurationContractTest#completeConditionsCreateExactlyOneLoaderWithoutConnectingToMongo \
  test
mvn -pl addons/foggy-dataset-model-mongo \
  -Dtest=MongoModelAutoConfigurationContractTest \
  test
mvn -pl addons/foggy-dataset-model-mongo clean test \
  -DskipUnitTests=false -DskipITs=true
mvn -pl addons/foggy-dataset-model-mongo \
  -DskipUnitTests=true -DskipITs=false \
  -Dit.test=McpAuditLogMongoIT,MongoArrayElementAccessIT \
  verify
```

首次不带 `clean` 的模块复跑虽返回成功，但保留了改名前两类的旧 Surefire XML；该组
旧 XML 明确排除，不计入上述结果。`clean test` 只留下 3 个 fresh reports/48 tests；
focused Failsafe 只留下 2 个 fresh reports/18 tests。最终权威 unit runner 还会在执行前
清理所有 owning module report 目录，并执行 exact-set、marker freshness 与 mtime 校验。

work item=`closed`；r8e Unit authority 已通过该自动配置回归。两个 Mongo external IT
仍按 successor exact 归属 Step 3，不由本 workitem 的 Step 2 closure 提前标 pass。

## References

- `addons/foggy-dataset-model-mongo/target/surefire-reports/`
- `target/v934-step2-unit/runs/step2-unit-r1-20260714/run.marker`
- `docs/9.3.4/evidence/step-2/`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
