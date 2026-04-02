# Stage3 开发日志：hierarchy 与路径字段主链

## 本阶段目标

- 让 synthetic member-QM 在根维度、嵌套子维度路径字段上可执行查询
- 让父子维 hierarchy operator 在 synthetic member-QM 的 `$id` 路径字段上复用主引擎能力
- 保持 simple 入口继续归一到 QueryFacade 主链

## 关键实现点

### 1. synthetic runtime column 需要保留维度语义

阶段2的 synthetic 字段虽然已经能注册到运行时 `JdbcQueryModel`，但 hierarchy operator 依赖列对象仍然带有维度语义。

本阶段将 synthetic 的 root/path `$id` 与 `$caption` 字段改为显式包装：

- `SyntheticMemberDimensionFieldColumn`
- `SyntheticMemberPropertyFieldColumn`
- `SyntheticMemberPlainFieldColumn`

其中：

- `$id` / `$caption` 对外是 synthetic 字段名
- 底层执行列仍绑定到维度 `QueryObject`
- `getDimension()`、`isDimension()`、`isCaptionColumn()` 明确返回维度语义

这样 `JdbcModelQueryEngine` 在处理 hierarchy operator 时，仍然能把 synthetic 字段识别为维度列，而不是普通业务列。

### 2. 避免复用原始 DbDimensionColumn 导致重复列扩展

如果直接 decorate 原始维度列，`QueryModelSupport.addJdbcQueryColumn(...)` 在处理 caption / hierarchy 扩展时，会沿着原列再次展开，导致 synthetic nested path 出现重复 query column。

本阶段改为：

- synthetic 字段的外层列对象自己声明维度语义
- 底层 delegate 只保留物理表字段绑定
- 不再把原始 `DbDimensionColumn` 直接暴露给 synthetic runtime schema

结果是：

- synthetic canonical `id/caption` 不会被丢失
- nested path caption 不会重复注册

### 3. synthetic member-QM 的 JoinGraph 需要补 hierarchy closure 边

仅注册根维度与嵌套维度之间的 join 还不够。
父子维 operator 会进一步使用：

- `closureQueryObject`
- `ancestorClosureQueryObject`

所以在 synthetic runtime model factory 中补充了 hierarchy join 注册：

- root parent-child dimension 注册 closure / ancestorClosure join
- nested child dimension 若本身也是 parent-child，也同样注册

这样 synthetic member-QM 以维表为执行根时，仍能通过主引擎拼出 closure 查询。

### 4. simple 入口的 hierarchy 需要归一成 synthetic `id` slice

`JdbcServiceImpl.queryDimensionData(...)` 之前已经归一到 synthetic member-QM，但 hierarchy 参数还没有转成 DSL。

本阶段补了最小归一规则：

- `childrenOf:T002`
- `descendantsOf:T002`
- `selfAndDescendantsOf:T002`
- `ancestorsOf:T002`
- `selfAndAncestorsOf:T002`

统一翻译为 synthetic model 上字段 `id` 的 slice。

本阶段只做 Stage3 所需最小闭环，不扩展新的 hierarchy DSL 语法。

## 结果

- synthetic member-QM 可查询根维度 canonical 字段
- 可查询一级、二级嵌套子维度路径字段
- 可在子维度路径字段上过滤、排序
- 可在父子维 `$id` 字段上执行：
  - `childrenOf`
  - `descendantsOf`
  - `ancestorsOf`
  - `selfAndDescendantsOf`
  - `selfAndAncestorsOf`
- simple 入口也能归一到 synthetic member-QM 并执行 hierarchy 查询

## 风险与留口

- 本阶段未扩展阶段4的 external/internal permission patch
- `id` 的 canonical 语义当前仍对应维表主键视角，不是业务属性列
- hierarchy 语义仍完全依赖现有主引擎与 parent-child dimension 实现，本阶段未新增独立成员查询旁路
