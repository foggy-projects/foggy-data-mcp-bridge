# P1-维度成员内部QM映射-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`阶段1-5已完成（2026-04-02）`
- 责任边界：
  - 后端负责维度成员能力的内部模型抽象、查询执行链路、权限挂载点与元数据输出
  - 前端负责消费统一成员接口，不感知底层维表、维度表或内部 QM 映射细节
  - 网关/插件可通过高级入口注入额外 DSL 条件与列裁剪

## 背景
当前 Foggy 已具备以下基础能力：

- QM 元数据已能返回字段信息
- 字段中已能标识维度字段、字典字段、层级维度能力
- 查询执行链路已具备 `beforeQuery -> 权限注入 -> SQL 生成 -> 查询执行` 的完整 pipeline
- 当前系统理念中，`QM 即视图`，适合作为内部统一查询抽象

在维度成员获取能力设计中，当前已收敛出一个关键方向：

- 不直接将“维度成员查询”绑定到旧接口实现
- 也不要求前端理解底层维度表、维度名、属性名、外键名
- 而是考虑将 `QM 中的某个维度` 自动映射为一个**内部 synthetic member-QM**
- 外部成员接口仍以 `model + fieldName` 为统一入口
- 内部再通过映射关系，将成员查询落到 synthetic member-QM 上执行

该能力本质上是“成员查询内部执行模型”的标准化，而不是前端正式契约的最终定稿。

## 当前共识
- 本阶段只考虑 `REFERENCE_QM` 模式
- 本阶段**不考虑** `context permission`
- 本阶段不处理“当前业务事实数据中是否存在该成员”的上下文裁剪
- 本阶段只考虑“指定 QM 下、指定维度”的成员权限控制
- 成员权限应由绑定的 `reference member-QM` 自身承担
- 对外成员能力计划提供两个入口：
  - 简单入口：给前端筛选器、下拉框、搜索联想直接使用
  - DSL 入口：给网关、插件、集成侧注入额外过滤条件、列权限、排序规则
- 两个入口最终都应收敛到同一个内部 synthetic member-QM
- synthetic member-QM 严格只暴露“该根维度及其内嵌子维度子树”相关字段，不允许查询原业务 QM 的其他维度、度量或业务字段
- `groupBy / orderBy / start / limit / columns / slice` 属于 QM 基础能力，本需求不额外禁用；手册仅说明成员查询推荐用法，字段空间仍限制在 member-QM 的根维度子树内
- `JOIN` 不作为单独能力设计；父子维的 closure join 与嵌套子维度 join 复用现有模型与引擎能力
- `8.1.10.beta` 优先支持“外部权限 patch”模式：由 Odoo 等网关/插件先完成权限翻译，再把过滤条件与可见列裁剪注入 Foggy

## 目标
- 建立“`QM + 维度字段` -> synthetic member-QM” 的内部映射机制
- 为维度成员查询提供统一、稳定、可复用的内部执行模型
- 让简单入口与 DSL 入口共用同一套成员查询内部基础设施
- 为后续在 Odoo 等插件场景中注入权限条件、列裁剪、排序策略提供标准承载点
- 在不要求前端理解底层维表结构的前提下，支持维度成员分页、搜索、层级查询和权限控制

## 非目标
- 本阶段不设计完整的前端正式契约定稿
- 本阶段不讨论“当前业务事实数据约束下的可选成员”问题
- 本阶段不做混合模式（如 `CONTEXT_DISTINCT` / `HYBRID`）
- 本阶段不要求前端直接感知或直接调用 synthetic member-QM
- 本阶段不处理任意通用 QM DSL 查询开放问题

## 核心需求

### 1. 内部 synthetic member-QM 抽象
- 对每个“可查询成员的维度字段”，自动生成或映射一个内部 synthetic member-QM
- 该内部 QM 只服务于维度成员查询场景
- 该内部 QM 应可复用现有 QueryFacade、beforeQuery、权限注入、SQL 生成与缓存能力

### 2. 命名规则
- 当前讨论中的建议命名规则为：
  - `${qmName}#${dimFieldBase}`
- 其中 `dimFieldBase` 优先基于 **QM 中暴露的维度字段基名**，而不是底层 TM 维度名
- 该命名主要用于内部标识、调试、缓存与权限挂载，不要求作为前端公开协议的一部分

### 3. 字段暴露边界
- synthetic member-QM 仅允许查询“根维度自身 + 其内嵌子维度子树”相关字段
- 根维度自身字段至少应包含：
  - `id`
  - `caption`
  - TM 中该维度的全部属性
- 对根维度的内嵌子维度，应递归展开：
  - `<relativePath>$id`
  - `<relativePath>$caption`
  - `<relativePath>$property`
- 其中 `relativePath` 以相对根维度的有效路径表示，每段优先使用 `alias`，否则使用 `name`
- 如果根维度或其任一子维度是层级维度，则在对应路径下允许保留字段：
  - `parentId`
  - `depth`
  - `hasChildren`
- 不允许暴露原业务 QM 的其他维度、度量字段、业务字段

### 4. 双入口模型
- 简单入口面向普通前端成员查询场景
- DSL 入口面向网关、插件、集成侧高级调用场景
- DSL 入口允许调用方在成员查询中注入：
  - 过滤条件
  - 列裁剪
  - 排序规则
  - 分页参数
- 但其作用范围必须被严格限制在 synthetic member-QM 的单维度字段集合内

### 5. 权限控制
- 成员权限不按事实数据上下文裁剪
- 成员权限由 synthetic member-QM 绑定的 reference member-QM 自身承担
- `8.1.10.beta` 推荐优先采用“外部权限 patch”模式：
  - 外部系统先完成角色、组织、租户等权限求值
  - 再将 `forcedSlice`、`visibleColumns`、`forcedOrderBy` 等信息注入 Foggy
  - Foggy 侧负责与 synthetic member-QM schema 求交并执行
- 需要保留后续内部权限扩展挂载点，但不要求本阶段先完成完整的内部角色权限体系
- 该方案应尽量避免要求用户手工维护大量独立的物理 QM 文件

### 6. 插件/网关集成
- 需支持类似 Odoo-MCP 插件作为前置网关的场景
- 插件可根据自身权限体系，对成员查询 DSL 注入条件
- 注入后再由 Foggy 内部执行成员查询
- Foggy 侧仍需保留最终字段白名单、排序白名单、列求交与强制字段控制
- Foggy 不要求在本阶段理解外部系统的角色模型，只负责接收其已翻译完成的成员查询 patch

## 待确认设计问题

### 1. synthetic member-QM 的形态
- 是作为“真实注册的 QueryModel”存在
- 还是作为“运行时虚拟 QueryModel”懒生成
- 或者采用“逻辑模型 + 执行时投影”方案

### 2. 字段 schema 约定
- synthetic member-QM 内部字段是否统一固定为：
  - `id`
  - `caption`
  - `parentId`
  - `depth`
  - `hasChildren`
  - `properties...`
- 或继续沿用 `xxx$id` / `xxx$caption` 风格

### 3. 权限挂载方式
- 在原 QM 的维度配置上声明成员权限扩展，然后编译到 synthetic member-QM
- 还是单独提供 overlay 配置给 synthetic member-QM
- 还是允许注册命名规则对应的权限增强器

### 4. 生命周期
- synthetic member-QM 在何时生成：
  - 启动期预生成
  - 首次访问懒生成
  - 模型加载后按需缓存
- 与 bundle / namespace / 热加载之间如何联动失效

### 5. DSL 入口边界
- DSL 入口在结构上是否完全复用标准 DSL
- 如果复用，字段空间如何限制在 single-dimension member-QM 上
- 允许哪些字段进入 `columns` / `slice` / `orderBy`
- 服务端强制字段与调用方请求列如何求交

### 6. 层级维度支持
- 层级维度在 synthetic member-QM 中的保留字段如何定义
- `childrenOf` / `descendantsOf` / `ancestorsOf` / `selfAndDescendantsOf` 如何映射到该内部模型

## 初步设计方向
当前讨论下，推荐优先沿以下方向继续展开：

- 将维度成员查询的内部执行模型统一抽象为 synthetic member-QM
- 外部仍以 `model + fieldName` 寻址，不暴露底层维表结构
- 简单入口与 DSL 入口都编译到同一个内部 member-QM
- member-QM 采用“根维度 + 维度子树逻辑视图”思路，根维度自身字段 canonical 化，子维度按相对路径展开
- 权限由 member-QM 自身承担，不与事实数据上下文耦合
- 插件/网关可以在 DSL 入口前置注入自身权限过滤，但最终仍由 Foggy 做字段与能力边界校验

## 推荐字段命名示例
以 `SaleOrderQM#product` 为例，如果 `product` 维度拥有属性与嵌套子维度：

- 根维度自身字段：
  - `id`
  - `caption`
  - `productId`
  - `brand`
  - `unitPrice`
- 一级子维度 `productCategory`：
  - `productCategory$id`
  - `productCategory$caption`
  - `productCategory$categoryId`
  - `productCategory$categoryLevel`
- 二级子维度 `productCategory$categoryGroup`：
  - `productCategory$categoryGroup$id`
  - `productCategory$categoryGroup$caption`
  - `productCategory$categoryGroup$groupId`
  - `productCategory$categoryGroup$groupType`

若某一层路径节点本身为父子维，则在该路径下额外保留：

- `<relativePath>$parentId`
- `<relativePath>$depth`
- `<relativePath>$hasChildren`

## MVP 建议范围
`8.1.10.beta` 的最小可行版本建议至少覆盖：

- 建立 `QM + 维度字段 -> synthetic member-QM` 的基础映射
- 支持普通维度的成员查询
- 支持根维度全部属性与内嵌子维度递归展开
- 支持层级维度的基础字段保留能力
- 支持简单入口与 DSL 入口共用同一执行模型
- 支持 namespace 下的模型解析
- 支持外部权限 patch 注入点
- 预留内部权限扩展点
- 暂不一次性解决全部高级场景，只先把内部模型与边界定义清楚

## 风险与注意事项
- 如果 synthetic member-QM 的命名、字段 schema、权限挂载点不稳定，后续接口与缓存都容易反复调整
- 如果 DSL 入口边界不清晰，容易演变成“变相开放任意内部 QM 查询”
- 如果继续要求用户手工维护大量维度成员 QM，配置成本会明显升高
- 如果不把 synthetic member-QM 与原 QM 的热加载、namespace、缓存策略一起考虑，后期运维复杂度会增加

## 跟踪说明
- 本需求当前仅用于记录“维度成员映射内部 QM”的需求边界与讨论共识
- 具体设计方案、字段 schema、权限挂载方式、生成时机等，待后续专项讨论确认后再补充
- 后续围绕该能力的设计、实现与验证，统一追加到 `docs/8.1.10.beta/` 目录下

## 开发阶段与验收标准

### 阶段 1：synthetic member-QM 解析与 schema 生成
- 目标：
  - 实现 `QM + fieldName -> synthetic member-QM` 的解析规则
  - 明确 `${qmName}#${dimFieldBase}` 命名
  - 实现根维度自身字段与内嵌子维度递归展开的 schema 生成
  - 明确父子维字段保留策略
- 验收标准：
  - 给定 `model + fieldName`，可稳定解析出唯一 synthetic member-QM 名称
  - 根维度自身字段至少包含 `id/caption/TM全部属性`
  - 内嵌子维度可按相对路径稳定展开，且命名不使用泛化的 `child`
  - 父子维路径节点可识别是否需要保留 `parentId/depth/hasChildren`
  - namespace 下解析结果稳定，不与原 QM 命名冲突

### 阶段 2：统一接入 QueryFacade 主链
- 目标：
  - 让 simple 入口与 DSL 入口统一落到 synthetic member-QM
  - 复用现有 `QueryFacade -> beforeQuery -> SQL 生成/执行` 链路
  - 不再为维度成员查询新增旁路执行模型
- 验收标准：
  - simple 入口与 DSL 入口的最终执行模型一致
  - synthetic member-QM 可被正常装载并进入主查询 pipeline
  - 可通过现有 SQL 生成能力输出 synthetic member-QM 的 SQL
  - 不再依赖旧维度成员旁路作为新主实现

### 阶段 3：嵌套子维度与父子维验证
- 目标：
  - 覆盖普通维度、嵌套子维度、父子维三类成员查询
  - 确认子维度字段查询、排序、过滤在 synthetic member-QM 下可用
  - 确认 hierarchy operator 在对应路径的 `$id` 字段上可工作
- 验收标准：
  - 可查询根维度属性
  - 可查询一级、二级内嵌子维度的 `id/caption/属性`
  - 可在子维度路径字段上执行过滤与排序
  - 对父子维路径节点，`childrenOf / descendantsOf / ancestorsOf / selfAndDescendantsOf` 等操作可正确工作

### 阶段 4：外部权限 patch 合并
- 目标：
  - 定义外部权限 patch 结构
  - 支持外部系统注入 `forcedSlice`、`visibleColumns`、`forcedOrderBy`
  - 由 Foggy 做 schema 求交与边界校验
- 验收标准：
  - 外部系统可在不暴露其角色模型细节的前提下完成权限注入
  - `visibleColumns` 与 synthetic member-QM schema 求交后结果稳定
  - `forcedSlice` 与请求 `slice` 合并后执行结果可预测
  - 不可见字段不会通过 `columns/orderBy` 绕过校验

### 阶段 5：缓存、热加载与文档收口
- 目标：
  - 完成 synthetic member-QM 的 namespace 缓存策略
  - 明确与 bundle 热加载、模型热更新的失效联动
  - 收口开发文档、样例 DSL、边界说明
- 验收标准：
  - synthetic member-QM 在同一 namespace 下可复用缓存
  - bundle 移除、文件热更新后 synthetic member-QM 可正确失效与重建
  - 文档中包含至少一组普通维度、嵌套维度、父子维的样例
  - 文档中明确 simple 入口、DSL 入口、外部权限 patch 的使用方式

## 开发过程记录要求

### 指定记录目录
- 开发关键日志必须记录到：`docs/8.1.10.beta/P1-维度成员内部QM映射/dev-logs/`
- 开发测试记录必须记录到：`docs/8.1.10.beta/P1-维度成员内部QM映射/test-records/`

### 强制要求
- 每完成一个开发阶段，必须至少新增一份对应的开发日志记录
- 每完成一个开发阶段，必须至少新增一份对应的测试记录
- 未补齐日志与测试记录，不视为该阶段完成

### 日志记录最少内容
- 日期、阶段名称、负责人
- 本阶段修改的核心文件与改动点
- 本阶段关键设计决策与取舍
- 遇到的问题、临时方案、后续待办
- 关联的测试记录文件名

### 测试记录最少内容
- 日期、阶段名称、测试人
- 测试范围与测试数据
- 覆盖的场景：
  - 普通维度
  - 内嵌子维度
  - 父子维
  - 外部权限 patch 合并
- 输入 DSL / simple 请求样例
- 关键 SQL 或关键执行日志
- 实际结果、预期结果、结论

### 推荐命名规则
- 开发日志：`S{阶段序号}-{YYYYMMDD}-{主题}.md`
- 测试记录：`T{阶段序号}-{YYYYMMDD}-{主题}.md`

## 阶段完成情况
- 阶段1：已完成
- 阶段2：已完成
- 阶段3：已完成
- 阶段4：已完成
- 阶段5：已完成

## 当前收口结论
- `QM + fieldName -> synthetic member-QM` 已可在运行时稳定解析，命名采用 `${qmName}#${dimFieldBase}`。
- simple 入口与 DSL 入口已统一落到 synthetic member-QM，并复用 `QueryFacade -> beforeQuery -> SQL 生成/执行` 主链。
- synthetic member-QM 已支持根维度全部属性、嵌套子维路径字段与父子维 hierarchy operator。
- `8.1.10.beta` MVP 已支持 external patch：`visibleColumns`、`forcedSlice`、`forcedOrderBy`。
- synthetic member-QM 已补齐 namespace 级缓存、`clearByNamespace`、bundle 移除与文件移除后的重建验证。
