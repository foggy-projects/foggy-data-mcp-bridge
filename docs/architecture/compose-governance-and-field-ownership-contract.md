# Compose 结果治理、字段所有者及参数合同

## 方案与影响面

本轮在现有 Compose 规划、输出绑定及语义 SQL 端口收口六组缺陷，不新增计算或权限引擎。顺序为结果治理、嵌套字段所有者、基础计算字段定义、参数归一化、输出冲突、安全参数及 nullSafe。保留上一轮改动与证据，不运行根目录 clean。

## 结果治理

Compose 下推 SQL 只适用于结果处理对当前模型请求为恒等变换的路径。每个覆盖 process 的具体扩展及自定义管理器必须明确声明 Compose 恒等能力；默认未知即拒绝。beforeQuery 仍逐基础模型执行并应用服务器权限；无法转换为关系的结果短路也拒绝，随后在实际模型上下文检查能力；validate/preview 同样检查且不读取业务数据。Pivot 恒等声明不能替代 Compose 声明。

存在脱敏、结果行过滤、截断等非恒等变换时，Compose 在规划阶段拒绝，普通查询继续执行原处理。不能将首个 QM 的处理器用于整个 JOIN/UNION，也不能在聚合前任意改变数据。扩展仅可在证明不改变行、列、值和权限语义时声明恒等；上下文缺失、异常或子类未自行声明均失败关闭。

## 字段所有者

限定字段必须由被引用计划实际输出，并沿每层关系的可见投影传播。兄弟字段不能因同名而冒充祖先字段；合法透传及显式重命名保留所有者关系。原样透传与重命名副本同时存在时优先原字段；仅剩多个不同重命名输出时，要求显式选择当前层输出，避免猜测。JOIN 左右按既有冲突规则暴露输出；UNION 按位置及左侧 schema 合并，只暴露 UNION 输出，分支限定名不能穿透。字符串与 typed 引用遵循相同边界。

## 参数及计算字段

基础 calculatedFields 完整传递窗口分区、排序、帧和 emptyDefault，复用普通查询依赖和权限验证；派生层非空 calculatedFields/having 仍拒绝。已知数组、字符串、布尔与分页参数按类型验证，错误位置结构化返回，禁止忽略、截断或溢出。分页仅接受 0..2147483647 的整数，limit=0 返回空，null 保持省略语义；start 优先于 offset，但两者有值都须合法。query 只接受一个选项对象，不接受来源选择；dsl 的 QM 字符串、model:plan、legacy source:plan 保留。未知扩展键不作全局拒绝。动态 Map 与字面量安全参数一律禁止，服务器上下文是权限来源。

## 输出名称与 nullSafe

可输出列名按 Locale.ROOT 大小写折叠检查冲突，拒绝 X/x 等无法被数据库及结果 Map 一致表示的两列；保留 G10 同名 JOIN 歧义解析规则。

JOIN nullSafe 可省略或为 null（等同 false），有值必须为 boolean，仅等值 = 允许 true。false 保持 SQL NULL 不相等；true 令双方 NULL 相等，并复用已有方言渲染。非等值配 true 在规划时拒绝。内部时间窗口 nullSafe 不变。

Compose 方言的整数字面量在入口前保留实际大小，避免词法器先截断导致边界校验失效；其他脚本方言保留原数字解析行为。旧端口缺少输出元数据且基础投影完全省略时保留原通配兼容路径；显式投影始终要求所有者证明，生产端口必须返回权威输出。

## 窗口与查询形状支持范围

完整传递定义不表示新增通用 CTE 提升能力。SQLite、MySQL 8 和 PostgreSQL 的本轮基础窗口场景验算正确；MySQL 5.7 不支持窗口函数，明确拒绝。SQL Server 普通查询对照显式选择 `[id, category, revenue, running]`，并声明 `[id, category, revenue]` 行粒度以覆盖窗口内部依赖，但现有 Compose 的相关 CTE 阶段形状会以 `RELATION_CTE_HOIST_UNSUPPORTED` 在 validate/preview/execute 一致拒绝，拒绝时不准备或执行业务 SQL；该受支持的普通查询对照验证 id=2 的分组累计为 30、id=4 为 70。此保护禁止退化为普通分组计算，通用 SQL Server CTE 提升另行设计。另已记录普通窗口自动分组未纳入隐含依赖时的既存 SQL Server 限制；本轮对照不把该自动分组形状宣称为已支持。

## 构件影响

生产改动涉及 model-engine、FSScript 的 Compose 方言、data-viewer 后端能力声明和 MCP 查询说明。部署时应配套更新 FSScript 与引擎：混用旧 FSScript 仍可能在参数校验前截断整数字面量。混用旧 Viewer 或自定义结果处理扩展会因缺少 Compose 能力声明而默认拒绝，需同步升级或审查后显式声明。普通查询不受该能力门禁影响；稳定公开 QueryFacade 不扩展。Runtime API 本轮仅新增回归测试。

## 兼容性与验证

### 执行阶段治理与显式执行（发布后补充）

统一修复分为四组：执行阶段治理证明、只读模式执行门禁、链式 JOIN 所有者绑定、参数及诊断。影响集中在 model-engine 和 Runtime 回归，复用既有规划、权限和字段绑定机制，不扩大为全引擎重构。

`DataSetResultStep` 恒等声明不能证明 `QueryExecutionStep` 安全。每个具体执行扩展必须自行声明 `composeSqlOnlyIsSafe(context)`，默认 false；父类声明不能为新增行为的子类背书，异常同样失败关闭。声明表示：跳过普通执行和结果钩子不改变行、值和授权语义；必要的权限检查须在 独立默认钩子 `prepareComposeSql(context)` 中完成，该阶段不得访问业务数据、读缓存、提供结果短路或改写 SQL。物理列检查在该阶段执行；内置 L2 缓存和预聚合优化明确允许省略。非恒等脱敏或无法证明安全的扩展，在所有 Compose 模式规划时拒绝，不能把首模型的处理器套在整个 JOIN/UNION 结果上。自定义执行器也需要自行实现并承担该准备合同。现有 `QueryExecutionPhase` 枚举保持不变，避免破坏消费方穷举 switch 的源码兼容性。

validate/preview 只允许规划。脚本显式 `execute()` 统一以 `COMPOSE_EXPLICIT_EXECUTE_FORBIDDEN` 拒绝，包含丢弃执行结果、函数内调用和中间变量调用；门禁位于运行时执行能力边界，不依赖源码文本扫描。`toSql()` 和返回 QueryPlan 的自动规划仍可用。execute 模式保留显式执行行为，并继续执行全部 Compose 治理证明。宿主在脚本运行时外的原有直接 Java 执行 API 保持兼容。

链式 `.on(leftRef, rightRef)`、`.and(leftRef, rightRef)` 保留字段所属计划，到权威输出绑定阶段调用共享所有者解析。重命名透传绑定当前输出；外部计划、错侧引用、隐藏字段、不可见 UNION 分支不能按同名列回填。字段同时来自多个关系实例时，不能猜测关系出现位置。UNION 本身的输出引用仍合法。条件引用的所有者身份纳入计划去重键，避免同名但不同来源的条件复用。

两个 fluent 方法严格接受两个字段引用；第三个 nullSafe 参数不属于现有合同，必须明确报错。nullSafe 继续使用 Map JOIN 条件的 boolean/null 合同，true 仅支持等值；类型和组合错误保留 `join.on[index].nullSafe` 结构化位置，禁止包装异常时丢弃诊断。

兼容性：普通查询和合法 Map JOIN 不变；新增 SPI 默认方法保持源码和二进制兼容，但未证明安全的执行扩展的 Compose 请求改为拒绝。错误接受的字段所有者和多余参数改为拒绝，合法重命名链式 JOIN 修正数值；validate/preview 中显式执行由读取数据改为明确报错。消费方应同步审查自定义治理扩展，不能为绕过门禁而无条件声明安全。

验证顺序为治理及只读门禁、字段绑定、参数诊断定向回归，再运行全量和 MySQL 5.7/8、PostgreSQL、SQL Server 测试。保留既有证据与失败尝试，不使用根目录 clean。

新增能力方法使用默认值保持 SPI 源码/二进制兼容；未知结果处理器的 Compose 请求由不安全执行改为明确拒绝，需扩展方审查后声明。被忽略的错误参数、无效所有者及大小写冲突由接受改拒绝；合法查询、普通查询治理及权限不变。基础窗口定义恢复会改变之前错误的数值，属于修复。

验证包含参数矩阵、两态 G10、脱敏与权限拒绝、窗口/默认值独立验算、嵌套 JOIN/UNION/重命名、Runtime 三态一致性及无业务 SQL 计数；回归通过后全量、独立 IT、MySQL 5.7/8、PostgreSQL 15、SQL Server 2022，并补 Linux 平台测试。所有尝试日志及测试报告留在独立证据目录。

### 时间计划、分组/窗口键及脚本边界（2026-10-25）

统一修复在现有规划和脚本边界内推进三组缺陷与两项合同边界，不扩大为全引擎重构。优先顺序是时间计划治理、字段所有者、方法参数，随后明确已知不支持的参数及计划返回形状。

原生 `timeWindow` 通过内部 Compose 生成 SQL，同样必须证明省略普通执行/结果钩子的安全性。基础叶关系复用纯 `prepareComposeSql` 准入，规划阶段不执行旧的普通执行钩子，不读缓存或业务数据，不改写 SQL 或提供结果短路；声明未知、非恒等脱敏和纯检查拒绝均在业务 SQL 前失败关闭。普通查询继续使用原生命周期。不能为兼容而无条件声明安全，也不能把首模型结果处理器套在组合结果上。累计、滚动及同比环比共享此准入；Pivot 等其他托管关系的现有合同不因本轮被任意扩大。

fluent `groupBy` 及窗口 `partitionBy/orderBy` 中的 `PlanColumnRef` 必须保留 owner 到权威输出绑定阶段。分组、窗口键、schema、权限依赖、归一化和计划哈希遵循同一可见性边界：合法祖先引用跟随透传和重命名；外部计划、隐藏字段及不透明 UNION 分支拒绝。保留既有字符串 getter、builder 和公开 Java 签名，通过内部来源信息保留引用，不把 typed 引用提前降为裸字符串。

脚本 `execute()`、`toSql()` 不接收实参；Java 已有上下文重载保持兼容。固定签名的脚本方法拒绝多余或错误类型实参，合法可变参数方法仍保留原合同。`lag()/lead()` 无参默认偏移 1；显式偏移仅接受 0..2147483647 的整数，禁止分数、溢出或字符串回退。合法偏移 0 保持“当前行”语义。窗口键只接受既有字符串/字段引用数组，null 保持省略语义，禁止把错误类型静默变为空键。

Compose `dsl/query` 暂不支持非空 `timeWindow`，应带参数位置明确拒绝；null/空对象保持省略兼容。普通语义查询的 `timeWindow` 能力继续支持并执行上述治理。该限制针对已知参数，不全局拒绝未知扩展键，也不在本轮新增完整的 Compose `timeWindow` 参数下推能力。

自动处理 QueryPlan 的返回合同为：直接返回 QueryPlan，或顶层 `{plans: plan}`、`{plans: {name: plan}}`、`{plans: [plan]}`；这些容器也可包含普通结果值。其他位置逸出的 QueryPlan，以及未求值的字段/聚合/窗口/子查询/JOIN builder 等规划描述对象，在自动执行和序列化前带结果路径拒绝，使用 `COMPOSE_RESULT_SHAPE_UNSUPPORTED`，不增加任意递归自动执行。返回普通数据、显式 execute 返回的行、`toSql()` 返回的 SQL 描述均保持支持。validate/preview 的显式执行门禁独立保留。

影响面集中于 model-engine 的托管关系、字段绑定和脚本方法分派；Runtime 补接口回归。合法 Map/string DSL、JOIN/UNION、nullSafe 及现有 Java 签名保持兼容；错误引用、非法参数和不安全扩展由静默接受改为明确拒绝，原先错误的分组及窗口数值被修正。消费端依赖执行扩展保护的时间计划须显式证明可下推；无法证明时拒绝是有意的安全收紧。

本轮独立证据目录为 `D:/workspace/foggy-data-mcp/releases/compose-time-governance-binding-fix-20261025`。验证先执行定向回归及真实 Runtime 的原生/Compose 入口对照，核对两态 G10、权限、三态诊断、独立数值和 validate/preview 的业务 SQL 准备/执行计数，再运行全量、独立 IT 和真实 MySQL 5.7/8、PostgreSQL、SQL Server 矩阵；Windows 跳过项使用 Linux 补测。保留此前源码与全部测试证据，不使用根目录 clean。
