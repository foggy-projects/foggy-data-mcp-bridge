# Data Viewer 全表格自定义查询实施与验收

## 目标

在公共 `DataTableWithSearch` / `ListPresetManager` 查询链路中统一支持用户字段选择、条件编辑及方案保存/加载；保留现有 props、事件、插槽、expose、受控模式和业务 `fetchData` 兼容性。

## 不变量

- 用户展示字段、用户条件、业务必需字段、业务固定条件分离。
- 请求字段是用户展示字段与当前业务上下文必需字段的去重并集；补入字段不自动展示，也不写入用户方案。
- 请求条件是 `A AND (用户条件)`。每次执行前重新调用当前页面业务回调；回调异步、取消或异常均不得回退到无约束直查。
- 用户方案最多 50 个字段、20 个条件；嵌套条件按叶子递归计数。业务补入字段/条件不占用户额度。
- 维度/字典配置仅展示名称；维度提交元数据声明的选择字段 ID，字典提交元数据声明的编码，不虚构 `$caption`。
- 同一 QM 使用稳定 `tableInstanceId` 隔离不同业务表格；方案存储沿用现有 ListPreset JSON，不保存业务固定条件、必需字段或可信权限上下文。
- 后端继续执行模型、字段和数据权限校验；`userId` 路径参数仅是现有配置命名空间，不视为认证身份。

## 实施面

1. 扩展前端类型、字段/条件归一化和 `useTableQuery` 查询参数链，确保 mount、查询、翻页、排序、刷新、默认/手动应用、重置、清空及上下文切换均走同一业务回调链。
2. 将 `ListPresetManager` 的条件摘要升级为受元数据约束的完整条件编辑器，并在新增、保存、加载、应用阶段拒绝超限内容。
3. 增强 `ListPresetService` 结构校验、嵌套叶子计数和可插拔字段白名单校验；保持文件/Mongo 存储兼容。
4. 同步 `foggy-gen` 模板与模板测试；生成产物只通过生成器验证，不手改消费者 generated 文件。

## 验收矩阵

- 50/51 字段与 20/21 条件；嵌套 OR/AND 叶子递归计数。
- 维度 caption 显示、ID 提交；字典名称显示、编码提交；隐藏必需字段仍请求且不展示。
- 业务补入不进入用户持久化；固定条件 A 与用户 OR/同字段冲突始终保持 `A AND (...)`。
- 首次加载、搜索、翻页、排序、刷新、默认方案、手动应用、重置、清空全部包含当前 A 和必需字段。
- A1 切换 A2 后不遗留 A1；异步回调、取消、异常均无绕过请求。
- 旧方案兼容、同 QM 不同实例隔离、受控模式兼容、权限白名单拒绝未知字段。

## 发布边界

本次交付引擎公共包、后端兼容性说明和生成器接入方式；不实施 TMS 全量接入、不修改 TMS generated 文件、不重启 TMS 环境。2026-09-09 用户追加授权 commit、push 和 npm beta 发布；Maven 不在发布范围。

## 发布前 review 修复（1.0.1-beta.48）

以下为 beta.48 历史记录，执行依赖的展示/持久化语义以文末 beta.49 修正为准。

- 条件 JSON 使用后端 Jackson 声明的 `$or` / `$and` / `$expr`；原实现错误的无 `$` 字段尚未发布。
- 仅保存选中的字段设置，字段池超过 50 项不影响少量字段方案；加载的超限方案在过滤失效字段之前拒绝。
- 请求条件树及 value 深拷贝，业务回调修改不污染用户方案；表头编辑保留不可映射到单列输入的逻辑组。
- 每次读取当前 hooks 和 fetchData，取消不触发成功事件，before-query 异常进入原错误钩子链。
- 实际 fetch 前补齐当前展示字段和业务必需字段；初始化用户条件参与首次请求。
- 条件编辑复用维度成员选择器，字典使用 label 显示、value 提交。
- 后端拒绝空/混合逻辑组，并验证 partial update 合并后的完整方案。

固定条件仍由可信业务 hooks 或原 fetchData 配方根据当前页面上下文添加，必须与用户条件 AND 组合（slice 顶层数组为 AND）。组件无法从任意业务代码推断固定条件，也不替代服务端权限校验。必需字段声明使用响应式 schema.requiredFields / requiredRuntimeColumns；业务追加内容只存在于每次请求中。

验证：前端 `npm test` 28 文件 / 434 用例通过；`mvn -pl addons/foggy-data-viewer -Dtest=ListPresetServiceTest test` 15 用例通过。新增验收包括真实 `$or` JSON 的后端存储往返、50 字段 / 20 条件边界、partial update、A1→A2、业务回调原地修改隔离、取消/异常、替换 fetchData、逻辑组保留、70 项字段池保存单字段。

## TMS 验收修正（1.0.1-beta.49）

- requiredFields / requiredRuntimeColumns 仅为执行依赖 R，实际请求为 U∪R；展示、保存、字段额度按用户配置 U。U∩R 不再被字段池、默认列、锁定列、方案保存或额度校验排除。R 中未被用户选择的字段仅补入请求；旧 internalFields 参数保留类型兼容但不再豁免额度。
- SelectFilter 恢复已选 ID 时主动请求当前成员接口，优先使用 selectedItems 回填页外成员名称；数字/字符串 ID 的标签匹配不修改 DSL 类型。
- 标签回填独立于下拉列表及主表查询，不 emit 条件变化/commit；模型、字段、selectionField、loader 切换清空缓存并隔离旧响应；已选值变化使用请求序号防止过期回填覆盖，失败后可通过打开下拉重试。
- 本次不修改尚未确诊的 checkbox 交互，不操作 TMS 或发布 Maven。TMS 临时 adapter 绕过应在新包验收后再移除。

验证：针对性测试首轮 4 文件 / 135 用例通过；补充四类上下文竞态后，完整前端 `npm test` 28 文件 / 442 用例通过。`npm run build:lib`（Vite、vue-tsc、verify:package）通过；后端 `ListPresetServiceTest` 15 用例通过。Review 检查了 U/R 交集、隐式补入不持久化、50/51 边界、过期异步响应及标签回填不 emit 的调用链。

## 统一展示值投影（1.0.1-beta.50）

新增公开 formatCellDisplayValue(column, value) 和 DisplayValueColumn；DataTable 与消费者统一 Excel 适配器可复用同一纯文本链。字典 String 键匹配，未知值返回原值文本，空值为空，非空 customFormatter 优先于 dictItems、viewer 和类型默认值。不改变查询或行数据，不引入 Excel 库，不修改 TMS 页面。

兼容边界、完整列元数据保留与接入示例见 frontend/README.md。验收覆盖字典300→已揽收、字符串数字、未知值、空值、formatter/viewer优先级、类型默认、ID整数格式、冻结行数据和真实构建包导出入口。TMS 需要在统一 exportExcel.ts 适配器保留元数据并调用新 API，升级包本身不会自动改变旧适配器逻辑。

验证：完整前端 npm test 29 文件 / 458 用例通过；build:lib 的 Vite 82 模块构建、vue-tsc 和真实包入口 API 校验通过。本轮不涉及后端变更。
