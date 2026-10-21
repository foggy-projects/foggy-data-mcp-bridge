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

成员入口由 `MemberFieldBinding` 将源 QM 查询 name 或无歧义维度路径解析到真实 owner。
`QM#完整维度路径` 是 synthetic 模型身份，嵌套成员直接查询目标维表，包含未被事实引用的成员。
显式 `QM#root` 仍可查询根及子树字段；输出 alias 不成为新的通用输入名称。
synthetic 实例固定编译时的源 QM，MEMBER_QUERY、行谓词与 TM/QM 成员权限均针对该实例求值。
字段白名单映射到完整的相对子树字段名；普通 QM 保持原有维度权限分组规则。
自动生成的旧列不能重新开放已隐藏的 renamed 投影，物理列拒绝不会在转换中被清空。
无法映射到目标成员查询的源行权限明确拒绝，不能通过丢弃谓词扩大数据范围。

成员 patch 的 `visibleColumns` / `allowedHierarchyOps` 用 null 表示未声明，空列表表示禁止；
visibleColumns 中未知字段明确失败。同一维度仍以 QM 覆盖 TM。祖先成员入口引用子树的选择、
筛选、排序、分组、计算字段（含窗口）依赖时，叠加沿途子维度策略；不同维度约束取交集，
强制谓词转换为相对完整路径并 AND。仅查询父自身不引入子级过滤，独立子入口不反向继承父级。
无法重定位的子级 SQL queryBuilder 明确拒绝祖先入口，使用独立子入口执行原策略。
最终成员权限检查在 system/external patch 之后，递归覆盖 slice、having、postSlice 的层级操作。
客户端 external patch 单独校验语义/物理字段依赖，不能利用后置合并绕过权限；可信内部行谓词保持既有权限。
用户条件中的 `$expr` 和 `$field` 同样展开为来源依赖；被 exColumns 排除且无其他使用的子列不激活子策略。

Viewer 从 `MemberBindingService` 获取当前完整维度路径及真实 id/caption 查询名，不截断路径、
不以输出 alias 推导查询字段，不缓存模型名对应的层级元数据。主查询与已选回填均经 QueryFacade
并透传 authorization / namespace；字段描述只有执行授权查询成功后才返回。签名链接仍使用原查询作用域。

这些组件属于 model-engine 内部。公共 Model SPI v2、namespace、catalog、原子刷新和缓存身份
不因此增加接口或改变生命周期。模型刷新及受影响旧结果缓存失效仍遵循既有发布流程。

当前实现及兼容说明见[字段名称合同](../dev-guide/qm-field-names.md)和
[修复工作项](../9.5/9.5.5/workitems/BUG-field-binding-dependencies.md)。

## 编译与复用边界

`QueryFieldBindings.Projection` 在一次投影入口编译不可变绑定列表，保存查询表达式、输出键和
实际来源列；结果映射不再逐行查找模型。编译产物只用于该投影，不跨模型共享。

`CalculatedDependencyGraph` 在模型加载时编译直接依赖、基础依赖闭包与拓扑顺序。
`QueryModelSupport` 持有所属实例的图，按表达式及窗口字段的语义快照检测现有可变 DTO 的变化；
变化时先成功构建新图再替换。旧图不随 DTO 修改而变化，失败时不会返回旧图冒充新定义。
新模型实例具有独立图，不引入按名称缓存，也不改变 namespace/generation/refresh 的身份体系。

请求定义创建独立覆盖图，模型预定义公式继续优先。覆盖基础字段时重新计算传递闭包；
不能直接复用模型原有的闭包结果。模型循环在加载时拒绝；请求内临时 AS 自引用在注入阶段
只收集边，在实际权限依赖展开或后续 SQL 阶段按既有规则校验，避免拒绝合法聚合投影。
该图不保存 fieldAccess/deniedColumns 的权限决定；权限仍对每次请求独立求值。

SQLite/四数据库合同及实际 Launcher 冒烟入口见
[字段绑定门禁](../../scripts/field-binding/README.md)。正式发布脚本在创建 tag/release 之前执行
制品检查；失败、缺失报告或不匹配的 JAR 摘要均阻止发布。
