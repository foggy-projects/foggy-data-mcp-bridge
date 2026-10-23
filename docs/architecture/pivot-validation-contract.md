# Pivot 校验与执行合同（#162）

## 范围与影响面

修复 `queryModel(mode=validate)` 和 `validateQuery` 在识别 Pivot 前检查普通
`columns` 的路由错误。Pivot 管线提取共用的请求和模型准备步骤，并复用主聚合、
轴域、树骨架、附属属性的请求构造器。执行继续使用原有 SQL 下放与内存整形路径。

校验走普通查询的受治理 SQL-only 编译，包含模型权限、逻辑字段白名单、物理列
黑名单、计算字段依赖和系统过滤；不执行主查询、辅助查询、轴成员枚举或结果缓存
读取。SQL 下放的可选能力不足仍允许既有内存降级；要求 SQL 的 cascade 必须在
校验阶段完成同一规划器的编译检查。

## 兼容性

- 普通查询仍要求非空 `columns`，Pivot 不要求顶层 `columns`。
- Pivot 与非空顶层 `columns`、`timeWindow` 仍互斥。已有顶层排序、分页及
  result-stage 限制保持不变；非法组合现在在 validate 和 execute 一致拒绝。
- `name` 为输入字段身份，`alias` 为结果身份；不把输出别名增加为输入同义词。
  Pivot 对外仍返回逻辑轴、指标名，结果值通过已有绑定映射读取。
- 普通 QM 维度白名单沿用维度组授权，树骨架的隐式 id/parentId 属于该组；
  物理列黑名单仍精确检查骨架 parentId、附属属性及计算字段的物理依赖。
- 保留 flat/grid/tree、轴 TopN/域分页、总计，以及 tree 总计关闭的既有行为。
  parentShare 和 baselineRatio 的分母范围及 first/last 基准合同不变。
- validate 使用 `VALIDATE` 权限动作，execute 使用调用上下文的执行动作；允许
  权限策略显式区分两者。相同策略下两条路径应接受或拒绝同一字段依赖。
- 原先被忽略的无效轴控制引用会提前拒绝；它们没有有效的数据计算合同。
- 多数据库回归另发现可加指标的内存 TopN 按单元格记录数截断。本轮同时修正为
  按父级分区聚合成员排名，窗口计数为成员数，并保留所选成员的全部列桶单元格。
  排名遵循 SUM/COUNT、MIN、MAX 的已有可加性元数据；修复 MySQL 5.7 降级及
  parentShare 子级分页丢格。需要重查或重算的非可加排名仍沿用既有能力边界，
  不在本轮扩展；cascade 必须 SQL 下放的限制不变。
- validate 成功表示静态计划可以编译且当前校验权限允许，不承诺运行时数据基数、
  数据库可用性、实际轴成员或数值结果。动态保护继续在 execute 生效。

## 验证顺序

1. 合法 Pivot、互斥请求、未知字段、输入名/输出别名及计算依赖回归。
2. 权限动作、模型拒绝、字段白名单/物理黑名单、系统过滤与辅助字段权限回归。
3. JDBC 观察器断言 validate 不准备或执行业务 SQL，并用 execute 验证观察器有效。
4. 使用固定小数据集逐项验算 tree 的直接节点值、parentShare 分母、baselineRatio
   first/last（含分页范围），再覆盖 Runtime HTTP validate→execute。
5. 回归通过后执行全量 Maven、引擎集成及 SQLite/MySQL 5.7/MySQL 8/
   PostgreSQL/SQL Server 测试。所有日志、XML、复现载荷和校验结果保存在外部
   releases 证据目录，保留上一轮证据及本轮失败尝试。
