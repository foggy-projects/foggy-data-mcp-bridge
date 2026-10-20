---
doc_type: architecture
intended_for: engine maintainers
purpose: Define the current internal field identity and calculated dependency boundaries.
---

# 引擎字段绑定与计算依赖

字段身份分为来源 ref、查询 name 和输出 alias。绑定保留实际 QM 列及来源 owner，
不能用输出字符串推断权限、聚合类型或维度角色。alias 不是通用输入同义词。

`QmColumnNaming` 管理实际 QM 列的命名与元数据来源；`QueryFieldBindings` 在结果投影边界
解析输出键。普通查询 schema.name 对应 items 键，必要时 queryName 保留查询标识。
pivot 主数据、domain、属性查询、层级 skeleton、非可加总计及 SQL 下推在边界转为逻辑键，
对外轴与 metric 名称保持查询标识。聚合语义从实际 QM 绑定取值，不能把 AVG 等总计改成求和。

`CalculatedFieldDependencies` 统一收集 expression、partitionBy、windowOrderBy 及传递公式依赖。
语义权限、物理列映射、元数据可见性、公式注入复用该规则；非法解析和循环依赖明确失败。
已有后聚合表达式沿用原规范化器。依赖注入不会自动把依赖字段加入最终投影。

已选表达式的 ORDER BY 输出别名追溯来源校验，不因此扩展 columns/slice/groupBy 的输入合同。
不同已选字段争用输出键时拒绝，避免结果 Map 静默覆盖。

这些组件属于 model-engine 内部。公共 Model SPI v2、namespace、catalog、原子刷新和缓存身份
不因此增加接口或改变生命周期。模型刷新及受影响旧结果缓存失效仍遵循既有发布流程。

当前实现及兼容说明见[字段名称合同](../dev-guide/qm-field-names.md)和
[修复工作项](../9.5/9.5.5/workitems/BUG-field-binding-dependencies.md)。
