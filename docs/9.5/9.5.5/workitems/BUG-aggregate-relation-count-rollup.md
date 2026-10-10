---
doc_type: bug
intended_for: execution-agent / reviewer
purpose: 修复已聚合关联 COUNT 输出在父查询分组及总计中被重算为主侧行数的问题。
version: 9.5.5
status: READY_FOR_REVIEW
---

# Aggregate relation COUNT 外层汇总

COUNT 输出列的默认外层汇总应为 SUM；右侧子查询仍使用原 AggregateMeasure 的 COUNT 和过滤条件。
普通事实列 COUNT、分页 total 行数和 COUNT_DISTINCT/AVG 的聚合元数据不变。

定向验证：SQLite 真实 facade 执行，计数明细、COUNT(column) 空值、固定过滤、双关联键、分页、单组/多组、无匹配/空结果，优化开启及关闭；原始 COUNT 与显式 SUM 表达式总计作为对照。

运行环境的固定依赖产物验证与当前 main 的实现分别记录，不将单个类的修复覆盖当成整套引擎升级。

显式 `sum(childCount) as childSum` 的分组行正确，但该 alias 的 totalData 仍为 null。
此独立缺口以真实 facade characterization 用例记录，未在本次默认 COUNT 汇总修复中修复。

## 验证（2026-10-10）

```sh
mvn -B -ntp -pl foggy-dataset-model-engine -am test \
  -Dtest=AggregateCountRollupRegressionTest,AggregateJoinQueryModelTest,AggSqlOptimizerTest,TotalDataAggregatePlanTest,AverageTotalDataRegressionTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.failIfNoTests=false -DskipITs
```

105 tests，0 failures/errors/skipped。两个空测试开关仅用于 reactor 中未选择测试的依赖模块；
目标引擎实际执行上述五个测试类。6 个新增用例中包含独立表达式总计缺口的 characterization，
不将该断言解释为表达式总计已修复。

固定运行环境按现有 Query 依赖编译同一维护源码，仅替换该类及其内部类；其他 JAR entry
内容逐项一致。默认计数总计、分组、元数据、分页和并发回读均通过；环境明细 2+2+2 的总计
为 6，分页 total 保持 3。无匹配 COUNT 输出与 SUM 总计仍为 null，空列表 total 为 0。
已有业务关联计数模型另执行非空及空范围回读；未发布正式版本或执行全数据库矩阵。
