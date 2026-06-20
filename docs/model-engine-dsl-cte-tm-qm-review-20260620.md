# foggy-dataset-model 引擎 DSL/CTE/TM/QM Review

日期：2026-06-20

基线：
- 已执行 `git pull`，当前 `main` 已是最新。
- 拉取时远端 `origin/tmp/issue-90-clean` 有更新记录，但当前工作分支没有新增变更。

范围：
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model`
- 重点关注 DSL 路由、DSL_CTE/CTE 编译、TM/QM 加载、查询生命周期、重复代码与重复处理逻辑。

## 总体结论

当前引擎的主干分层是成立的：TM 负责物理模型与 JoinGraph，QM 负责业务查询字段、条件、权限与多 TM 组合，QueryFacade 负责查询生命周期，JDBC QueryModel/QueryEngine 负责 SQL 分析和执行，compose 模块负责计划树与 CTE/subquery 组合。

审查时主要架构债集中在两处：

1. `SemanticQueryServiceV3Impl` 承担了过多路由、DSL 桥接、执行、SQL 生成、校验职责，尤其 DSL_CTE 分支在 plan、execute、generateSql 之间重复做验证和桥接。
2. CTE 组合存在两套并行实现：compose 模块已经有结构化 `SqlGenerationResult`/`RelationSql`/`RelationWrapStrategy`，但 `DslCteDslRequestMapper` 内部又手写了一套 SQL/CTE 拼接、参数合并、方言判断和 limit 追加逻辑。

本次已经按“先建测试、再定计划、再修复”的顺序完成第一轮收敛：DSL_CTE planning 已集中到 `DslCtePlanningService`，cross-model structured CTE base 已支持并抽出 `DslCteAssemblySupport`，语义请求 slice/having/postSlice 转换已集中到 `SemanticRequestNormalizer`，普通 validate 生产路径已复用 SQL-only facade pipeline。剩余值得继续治理的是：payload 直接转 JDBC slice 的旁路、字段引用提取重复、QueryFacade prepare 逻辑重复，以及 DSL_CTE wrapper 进一步迁移到 compose relation 抽象。

## 当前主流程

### TM 加载

TM 加载入口在 `TableModelLoaderManagerImpl.load(modelName, namespace)`，先按 namespace+name 查缓存，再加载 `.tm` 脚本、执行 fsscript、转换 `DbModelDef`、解析数据源、按类型委托具体 loader，并执行初始化：

- `impl/loader/TableModelLoaderManagerImpl.java:136`：按 namespace 加载 TM。
- `impl/loader/TableModelLoaderManagerImpl.java:144`：查找 `.tm` fsscript。
- `impl/loader/TableModelLoaderManagerImpl.java:151`：fsscript export object 转 `DbModelDef`。
- `impl/loader/TableModelLoaderManagerImpl.java:156`：解析有效数据源。
- `impl/loader/TableModelLoaderManagerImpl.java:167`：委托 `TableModelLoader`。
- `impl/loader/TableModelLoaderManagerImpl.java:168`：进入初始化。
- `impl/loader/JdbcTableModelLoaderImpl.java:47`：创建 `DbTableModelImpl`。
- `impl/loader/JdbcTableModelLoaderImpl.java:50`：根据 table/view/schema 设置 `QueryObject`。
- `impl/loader/TableModelLoaderManagerImpl.java:332`：初始化 TM。
- `impl/loader/TableModelLoaderManagerImpl.java:367`：加载维度。
- `impl/loader/TableModelLoaderManagerImpl.java:369`：加载属性。
- `impl/loader/TableModelLoaderManagerImpl.java:371`：加载度量。
- `impl/loader/TableModelLoaderManagerImpl.java:374`：加载预聚合。
- `impl/loader/TableModelLoaderManagerImpl.java:379`：调用 `jdbcModel.init()`。
- `impl/model/TableModelSupport.java:211`：构建列索引、JoinGraph 与维度索引。

这个设计清晰，TM 的物理语义与缓存边界基本合理。

### QM 加载

QM 加载入口在 `QueryModelLoaderImpl.getJdbcQueryModel`，按 namespace cache 解析 full name/short alias，必要时加载 `.qm`，设置 `NamespaceContext` 让 QM 脚本里的 `loadTableModel` 能找到同 namespace 下的 TM，再由 builder 构建 JDBC QM：

- `engine/query_model/QueryModelLoaderImpl.java:155`：获取 QM。
- `engine/query_model/QueryModelLoaderImpl.java:178`：查找 `.qm` fsscript。
- `engine/query_model/QueryModelLoaderImpl.java:180`：设置 `NamespaceContext`。
- `engine/query_model/QueryModelLoaderImpl.java:189`：转换 `DbQueryModelDef`。
- `engine/query_model/QueryModelLoaderImpl.java:309`：进入 `loadJdbcQueryModel`。
- `engine/query_model/JdbcQueryModelBuilder.java:85`：解析 `model` 与 `joins`。
- `engine/query_model/JdbcQueryModelBuilder.java:116`：构建 `JdbcQueryModelImpl`。
- `engine/query_model/JdbcQueryModelBuilder.java:148`：解析主模型与 join 数组。
- `engine/query_model/JdbcQueryModelBuilder.java:315`：通过 `NamespaceContext` 加载 TM。
- `engine/query_model/QueryModelLoaderImpl.java:336`：加载 column groups。
- `engine/query_model/QueryModelLoaderImpl.java:413`：加载 orders。
- `engine/query_model/QueryModelLoaderImpl.java:418`：加载 accesses。
- `engine/query_model/QueryModelLoaderImpl.java:423`：加载 member permissions。
- `engine/query_model/QueryModelLoaderImpl.java:451`：构建物理列映射缓存。

QM 层承担了业务字段、条件、权限和跨 TM join 的绑定，职责边界也基本合理。

### 查询执行

语义查询主入口在 `SemanticQueryServiceV3Impl.queryModel`：

- `semantic/service/impl/SemanticQueryServiceV3Impl.java:180`：进入 `queryModel`。
- `semantic/service/impl/SemanticQueryServiceV3Impl.java:182`：terminal plan。
- `semantic/service/impl/SemanticQueryServiceV3Impl.java:186`：semantic sql plan。
- `semantic/service/impl/SemanticQueryServiceV3Impl.java:190`：memory grid execution。
- `semantic/service/impl/SemanticQueryServiceV3Impl.java:194`：memory grid plan。
- `semantic/service/impl/SemanticQueryServiceV3Impl.java:198`：DSL_CTE plan。
- `semantic/service/impl/SemanticQueryServiceV3Impl.java:205`：普通 DSL 查询进入 `queryModelInternal`。

普通查询在 `queryModelInternal` 中构建 JDBC 请求并交给 QueryFacade：

- `semantic/service/impl/SemanticQueryServiceV3Impl.java:238`：`buildJdbcRequest`。
- `semantic/service/impl/SemanticQueryServiceV3Impl.java:287`：调用 `queryFacade.queryModelResult`。
- `service/impl/QueryFacadeImpl.java:122`：QueryFacade 查询生命周期。
- `service/impl/QueryFacadeImpl.java:135`：加载 QueryModel。
- `service/impl/QueryFacadeImpl.java:141`：执行 `beforeQuery` pipeline。
- `service/impl/QueryFacadeImpl.java:160`：调用 QueryModel 执行查询。
- `service/impl/QueryFacadeImpl.java:171`：执行结果处理 pipeline。

JDBC QueryModel/Engine 负责 SQL 分析与执行：

- `engine/query_model/JdbcQueryModelImpl.java:124`：`queryJdbc`。
- `engine/query_model/JdbcQueryModelImpl.java:130`：调用 `analysisQueryRequest`。
- `engine/query_model/JdbcQueryModelImpl.java:137`：执行 `beforeExecute` steps。
- `engine/query_model/JdbcQueryModelImpl.java:156`：执行 SQL。
- `engine/query_model/JdbcQueryModelImpl.java:168`：执行 `afterExecute` steps。
- `engine/JdbcModelQueryEngine.java:234`：SQL 分析入口。
- `engine/JdbcModelQueryEngine.java:245`：使用 QM 缓存的 merged JoinGraph。
- `engine/JdbcModelQueryEngine.java:250`：预处理内联表达式，且已有结果时会跳过。
- `engine/JdbcModelQueryEngine.java:257`：处理动态计算字段。

这条主链路的阶段划分是清晰的。

### Compose/CTE

compose 模块已经有比较完整的结构化 CTE/关系代数抽象：

- `engine/compose/compilation/ComposeSqlCompiler.java:61`：`compilePlanToSql`。
- `engine/compose/compilation/ComposeSqlCompiler.java:96`：委托 `ComposePlanner.compileToComposedSql`。
- `engine/compose/compilation/ComposePlanner.java:80`：方言到 CTE 支持能力的映射。
- `engine/compose/compilation/ComposePlanner.java:1005`：计划树编译为 `ComposedSql`。
- `engine/compose/compilation/ComposePlanner.java:1028`：前置 CTE hoist。
- `engine/compose/compilation/ComposePlanner.java:1646`：单 CTE/subquery 包装。
- `engine/compose/SqlGenerationResult.java:13`：SQL-only 结果用于 CTE/subquery 组合。
- `engine/compose/SqlGenerationResult.java:104`：判断是否存在结构化 CTE stages。
- `engine/compose/SqlGenerationResult.java:115`：组装完整 SQL。
- `engine/compose/relation/RelationSql.java:19`：结构化 relation SQL。
- `engine/compose/relation/RelationSql.java:64`：按 CTE items + body 顺序展开参数。
- `engine/compose/relation/RelationWrapStrategy.java:18`：inline subquery 策略。
- `engine/compose/relation/RelationWrapStrategy.java:21`：hoisted CTE 策略。
- `engine/compose/compilation/ComposeRelationCompiler.java:70`：编译 plan 为 relation。
- `engine/compose/compilation/ComposeRelationCompiler.java:126`：检测 relation 内 CTE。
- `engine/compose/compilation/RelationOuterQueryBuilder.java:124`：hoisted CTE 外层包装。
- `engine/compose/compilation/RelationOuterQueryBuilder.java:128`：inline subquery 外层包装。

这部分是后续收敛 DSL_CTE 的最佳落点。

## 主要问题与重复处理

### 1. DSL_CTE 执行路径重复验证和重复桥接

严重级别：高

处理状态：已处理。已新增 `DslCtePlanningService`，`queryModel` plan response、execute compiled DSL_CTE、`generateSql` DSL_CTE 分支共用同一个规划结果，不再在三个入口重复 validation + bridge selection。

执行 DSL_CTE 时，当前链路会多次做同一类工作：

- `SemanticQueryServiceV3Impl.java:198`：`queryModel` 先调用 `dslCtePlanResponseIfAny` 探测。
- `SemanticQueryServiceV3Impl.java:865`：`dslCtePlanResponseIfAny` 执行 `dslCteValidation`。
- `SemanticQueryServiceV3Impl.java:866` 到 `963`：依次尝试多个 bridge mapper。
- `SemanticQueryServiceV3Impl.java:990`：进入 execute 后再次 `dslCteValidation`。
- `SemanticQueryServiceV3Impl.java:991`：execute 再调用 `generateSql`。
- `SemanticQueryServiceV3Impl.java:391`：`generateSql` 中第三次 `dslCteValidation`。
- `SemanticQueryServiceV3Impl.java:393` 到 `490`：`generateSql` 中再次按同样顺序尝试 bridge mapper。

影响：
- 同一请求在 plan 探测、execute、SQL 生成中重复解析 executablePlan。
- 如果 bridge mapper 以后出现非幂等状态或 fallback model 差异，plan 结果和执行结果可能不一致。
- `SemanticQueryServiceV3Impl` 的 route 分发和 SQL 生成耦合过重，新增一种 DSL_CTE bridge 时要改多个分支。

建议：
- 抽出 `DslCtePlanningService`，只做一次 validation + bridge selection。
- 输出一个不可变 `DslCtePlan`，至少包含 `validation`、`kind`、`baseRequests`、`models`、`unsupportedReason`、`wrapper`。
- `queryModel` 的 plan response 和 execute response 都消费同一个 `DslCtePlan`。
- `generateSql` 不再重新探测 DSL_CTE，而是接收已规划好的 plan 或只作为基础 DSL 请求的 SQL 生成器。

### 2. CTE 组装存在两套并行体系

严重级别：高

处理状态：部分处理。cross-model join-align、source-rate、money-attribution、time-attribution 已支持结构化 `SqlGenerationResult.CteStage` 并抽出 `DslCteAssemblySupport`；但 DSL_CTE wrapper 仍未整体迁移到 compose `RelationSql`/`CteItem`，方言能力和 limit 外层追加仍有后续收敛空间。

compose 模块已经有 `SqlGenerationResult`、`RelationSql`、`RelationWrapStrategy`、`ComposePlanner`、`ComposeRelationCompiler` 这套结构化机制，但 DSL_CTE mapper 内部又手写了一套 CTE 拼接。

证据：
- `DslCteDslRequestMapper.java:4335`：result stage window 手写 wrap。
- `DslCteDslRequestMapper.java:4346`：该分支能消费 `base.hasCteStages()`。
- `DslCteDslRequestMapper.java:4532`：metric ratio 手写 wrap。
- `DslCteDslRequestMapper.java:4543`：该分支也能消费 `base.hasCteStages()`。
- `DslCteDslRequestMapper.java:4654`：join align 手写 wrap。
- `DslCteDslRequestMapper.java:4731`：join align 遇到 `base.hasCteStages()` 或 `WITH` 直接拒绝。
- `DslCteDslRequestMapper.java:4786`、`5005`、`5367`：多种 cross-model funnel wrap 继续手写 SQL。
- `DslCteDslRequestMapper.java:5266`、`5635`：部分 cross-model 分支继续拒绝结构化 CTE stages。
- `DslCteDslRequestMapper.java:3907`：通过扫描 SQL 字符串猜方言。
- `DslCteDslRequestMapper.java:3929`、`3936`：本地维护方言日期函数。
- `DslCteDslRequestMapper.java:4130`：直接对顶层 SQL 追加 limit。
- `DslCteDslRequestMapper.java:4155`：用正则判断是否已有 trailing limit。

影响：
- result-stage 分支支持结构化 CTE，cross-model 分支却拒绝，行为不一致。
- 方言能力在 `ComposePlanner`/`FDialect`/mapper 中重复维护，容易漂移。
- 参数顺序、CTE hoist、limit/pagination 的规则分散，未来修 bug 容易只修一条路径。
- `DslCteDslRequestMapper` 同时承担解析、规划、SQL 包装、方言适配，类体积和职责都过大。

建议：
- 建一个共享 `CteAssemblyService` 或直接复用 `RelationSql`/`CteItem`。
- DSL_CTE bridge 输出结构化 stage graph，不直接拼最终 SQL 字符串。
- cross-model 分支改为消费 `SqlGenerationResult.CteStage` 或 `RelationSql`，不要拒绝已结构化的 CTE stages。
- 方言能力从实际 QueryModel 的 `FDialect` 或 compose compile options 传入，不再通过 SQL 字符串猜。
- limit/pagination 统一落到外层 query builder，不在 mapper 中用字符串追加。

### 3. `buildJdbcRequest` 与 `processSliceValues` 重复转换 slice/having

严重级别：中

处理状态：已处理 service 内部重复。`processSliceValues`/`convertToJdbcSlice` 已删除，`buildJdbcRequest` 统一通过 `SemanticRequestNormalizer.toJdbcSlices` 转换 `slice`、`having`、`postSlice`；payload mapper 直接转 JDBC slice 的旁路仍列为后续风险。

`buildJdbcRequest` 已经把 `slice`、`having`、`postSlice` 转成 JDBC request：

- `SemanticQueryServiceV3Impl.java:1341` 到 `1347`：转换 slice。
- `SemanticQueryServiceV3Impl.java:1348` 到 `1352`：转换 having。
- `SemanticQueryServiceV3Impl.java:1354` 到 `1358`：转换 postSlice。

但 `queryModelInternal` 随后又覆盖 slice/having：

- `SemanticQueryServiceV3Impl.java:240`：注释称处理 `$caption` 值转换。
- `SemanticQueryServiceV3Impl.java:243`：重新处理 slice。
- `SemanticQueryServiceV3Impl.java:247`：重新处理 having。
- `SemanticQueryServiceV3Impl.java:1410`：`processSliceValues`。
- `SemanticQueryServiceV3Impl.java:1431`：`convertToJdbcSlice`。

问题点：
- `processSliceValues` 目前只是 field/op/value 拷贝，没有实际 `$caption` 转换。
- `buildJdbcRequest` 已转换过 slice/having，随后再次覆盖，属于重复处理。
- `processSliceValues` 不处理 `postSlice`，导致三类过滤的转换路径不一致。
- 如果 `SliceItem` 以后新增字段，两个转换函数都要同步修改。

建议：
- 删除 `processSliceValues` 的重复覆盖，或把真正的 `$caption` 转换抽成明确的 `CaptionSliceValueResolver`。
- 建立唯一的 `SemanticRequestNormalizer`，负责 case-insensitive、columns/groupBy 对齐、slice/having/postSlice 转换、orderBy 规范化。
- `buildJdbcRequest` 只调用这个 normalizer，不再分散转换。

### 4. payload 到 slice 的转换存在两条路径

严重级别：中

`SemanticQueryPayloadMapper` 同时提供 map 到语义 request 和 map 到 JDBC slice 的转换：

- `SemanticQueryPayloadMapper.java:196`：`convertToSliceItem`。
- `SemanticQueryPayloadMapper.java:289`：`convertToSliceRequestDef`。
- `SemanticQueryPayloadMapper.java:312`：`convertToSliceRequestDef` 支持 `maxDepth`。
- `SemanticQueryPayloadMapper.java:36`：保留 key 包含 `$expr`。

影响：
- 外部 payload 可能绕过语义层直接生成 `SliceRequestDef`，与 `SemanticQueryServiceV3Impl.convertToJdbcSlice` 的能力不一致。
- `maxDepth`、`$expr`、逻辑组递归、保留字段规则容易在两条转换链之间漂移。

建议：
- 外部 payload 统一先转 `SemanticQueryRequest`。
- JDBC request 只由 `SemanticRequestNormalizer` 生成。
- `SliceItem -> SliceRequestDef` 的转换保留一份，并覆盖 `maxDepth`、`$expr`、逻辑组。

### 5. 字段校验和权限校验重复遍历请求并重复解析表达式

严重级别：中

`FieldAccessPermissionStep` 和 `SchemaAwareFieldValidationStep` 都在 `beforeQuery` 里遍历 columns、slice、having、orderBy、calculatedFields，并各自处理表达式依赖。

字段权限：
- `FieldAccessPermissionStep.java:47`：`beforeQuery`。
- `FieldAccessPermissionStep.java:110`：构建 calculated field map。
- `FieldAccessPermissionStep.java:131`：校验 columns。
- `FieldAccessPermissionStep.java:195`：校验 slice。
- `FieldAccessPermissionStep.java:281`：解析表达式依赖。
- `FieldAccessPermissionStep.java:313`：字段权限 check。

schema-aware 校验：
- `SchemaAwareFieldValidationStep.java:39`：本地复制 trailing AS 正则。
- `SchemaAwareFieldValidationStep.java:44`：`beforeQuery`。
- `SchemaAwareFieldValidationStep.java:51`：收集 schema fields。
- `SchemaAwareFieldValidationStep.java:56`：构建 calculated field map。
- `SchemaAwareFieldValidationStep.java:126`：校验 columns。
- `SchemaAwareFieldValidationStep.java:160`：校验 slice。
- `SchemaAwareFieldValidationStep.java:207`：校验 orderBy。
- `SchemaAwareFieldValidationStep.java:247`：解析表达式依赖。

影响：
- 同一请求被多次遍历，同一表达式依赖被重复提取。
- 表达式解析失败策略分散：权限校验更偏 fail-closed，schema-aware 校验有 fail-open 逻辑。策略差异可以保留，但底层字段引用提取不应重复。
- `SchemaAwareFieldValidationStep` 复制 parser 正则，后续 parser 行为变化容易漏改。

建议：
- 抽出 `RequestFieldReferenceExtractor`，一次性提取字段引用、表达式依赖、字段来源位置。
- 把提取结果挂到 `ModelResultContext`，后续 permission/schema steps 只消费结果并执行各自策略。
- 表达式 parser 的 alias 处理应从 `InlineExpressionParser` 暴露公共方法，避免复制正则。

### 6. `validateQueryInternal` 与真实执行 pipeline 不一致

严重级别：中

处理状态：已处理生产路径。普通 query_model validate 现在构建同一个 JDBC request 和 `ModelResultContext`，调用 `QueryFacade.buildSqlOnly` 走 beforeQuery + SQL 生成前置链路，但不执行数据库查询。没有注入 `QueryFacade` 的轻量单测构造保留 legacy fallback，避免白名单/映射类单测必须启动完整 facade。

当前 validate mode 有独立手写校验逻辑：

- `SemanticQueryServiceV3Impl.java:216`：validate mode 直接走 `validateQueryInternal`。
- `SemanticQueryServiceV3Impl.java:1180`：`validateQueryInternal`。
- `SemanticQueryServiceV3Impl.java:1191`：只检查 columns 字段存在。
- `SemanticQueryServiceV3Impl.java:1200`：只检查 slice field 非空。
- `SemanticQueryServiceV3Impl.java:1222`：手工检查 groupBy 与 columns。
- `SemanticQueryServiceV3Impl.java:1233`：校验 output formatting。

它没有运行真实执行会经过的：
- case-insensitive 字段规范化。
- columns/groupBy 自动对齐。
- `beforeQuery` pipeline。
- schema-aware 字段校验。
- 权限字段校验。
- calculated field 依赖解析。
- systemSlice 合并。
- inline expression 预处理。

影响：
- validate 可能通过但 execute 失败。
- validate 可能报警但 execute 会自动规范化后成功。

建议：
- validate mode 应构建同一个 `DbQueryRequestDef`，执行同一个 normalization + beforeQuery validation-only pipeline。
- pipeline 中可能产生副作用的 step 需要区分 `phase=VALIDATE`，但校验规则不应另写一套。

### 7. QueryFacade 三个入口重复准备上下文

严重级别：中偏低

`doQuery`、`buildSqlOnly`、`prepareManagedRelation` 都重复执行 namespace 设置、request extData 合并、加载 QueryModel、设置 context.queryModel、执行 beforeQuery：

- `QueryFacadeImpl.java:122`：`doQuery`。
- `QueryFacadeImpl.java:124` 到 `141`：namespace/model/beforeQuery。
- `QueryFacadeImpl.java:184`：`buildSqlOnly`。
- `QueryFacadeImpl.java:186` 到 `201`：namespace/model/beforeQuery。
- `QueryFacadeImpl.java:225`：`prepareManagedRelation`。
- `QueryFacadeImpl.java:229` 到 `241`：namespace/model/beforeQuery。

不同入口后续行为确实不同：
- `doQuery` 支持 `skipQuery` 并执行 result process。
- `buildSqlOnly` 只生成 SQL。
- `prepareManagedRelation` 不支持 `skipQuery`，并执行指定阶段的 beforeExecute。

但前半段准备逻辑可抽取，降低新增生命周期入口时漏掉某个步骤的风险。

建议：
- 抽出 `prepareQueryContext(ModelResultContext, SkipPolicy)`。
- 返回 `PreparedQueryContext`，包含 `QueryModel`、`skipQuery`、`skipReason`。
- 三个入口只保留各自终端动作：execute、generateSql、prepareRelation。

### 8. skipQuery 后的 compose plan 执行归属不够清晰

严重级别：中

TimeWindow/comparative 这类计划在 beforeQuery 中可能让 QueryFacade skip 普通查询，但真正执行 plan 的逻辑放回了 `SemanticQueryServiceV3Impl`：

- `QueryFacadeImpl.java:148` 到 `156`：`skipQuery` 时 QueryFacade 返回空或已有结果，并执行 process。
- `SemanticQueryServiceV3Impl.java:291`：语义服务检查 `timeWindowPlan`/`comparativePlan`。
- `SemanticQueryServiceV3Impl.java:318`：语义服务调用 `PlanExecution.executePlan`。
- `QueryFacadeImpl.java:205` 到 `209`：`buildSqlOnly` 遇到同类 skip 返回空 `SqlGenerationResult`。
- `QueryFacadeImpl.java:243` 到 `245`：`prepareManagedRelation` 遇到 skip 直接拒绝。

影响：
- 同一种 beforeQuery 结果在 query、sql-only、managed relation 三个入口表现不同。
- QueryFacade 对 skip 的语义不完整，调用方需要知道 extData 中的特殊 key。
- compose plan 的执行所有权不清晰。

建议：
- 将 skip outcome 类型化，例如 `SkipQueryOutcome{kind=CACHE|COMPOSE_PLAN|UNSUPPORTED, plan, result}`。
- QueryFacade 负责返回结构化 outcome，SemanticQueryService 决定是否执行 plan，但不要读取裸 extData key。
- `buildSqlOnly` 对 compose plan 应该能返回编译后的 SQL，或显式返回 `UNSUPPORTED_COMPOSE_PLAN_SQL_ONLY`，不要返回空 SQL。

## 不建议立即改动的点

以下重复看起来是有意分层，不建议简单合并：

- `JdbcQueryModelImpl.queryJdbc`、`generateSql`、`prepareManagedRelation` 都调用 `analysisQueryRequest`，但后续生命周期不同：执行查询、只生成 SQL、准备受管关系。这里可共享准备逻辑，但不应强行合并三个终端动作。
- `FieldAccessPermissionStep` 与 `SchemaAwareFieldValidationStep` 的最终策略不同，一个偏权限 fail-closed，一个偏 schema-aware guardrail。建议合并字段引用提取，不建议合并策略本身。
- TM loader 与 QM builder 都有数据源处理，但 TM 解析自身数据源，QM 校验多 TM 数据源一致性，职责不同。

## 推荐重构顺序

1. 建 `DslCtePlanningService`。
   - 本次状态：已完成。
   - 单次 validation。
   - 单次 bridge selection。
   - plan/execute/generateSql 共用规划结果。
   - 先不改变最终 SQL 结构，降低回归风险。

2. 建统一 CTE assembler。
   - 本次状态：部分完成，已抽 `DslCteAssemblySupport` 处理 DSL_CTE cross-model structured base CTE；尚未整体迁移到 compose relation abstraction。
   - 优先复用 `SqlGenerationResult.CteStage`、`RelationSql`、`CteItem`。
   - 把 `DslCteDslRequestMapper.wrap` 中的手写 `WITH` 拼接迁移过去。
   - 方言能力从 `FDialect`/compile options 获取。

3. 建 `SemanticRequestNormalizer`。
   - 本次状态：已完成 service 内部 slice/having/postSlice 转换收敛。
   - 唯一负责 `SemanticQueryRequest -> DbQueryRequestDef`。
   - 合并 `buildJdbcRequest`、`processSliceValues`、`convertToJdbcSlice`。
   - 覆盖 `slice/having/postSlice`、`maxDepth`、`$expr`、case-insensitive、orderBy。

4. 建 `RequestFieldReferenceExtractor`。
   - 本次状态：未实施，保留为后续。
   - 一次提取 columns、groupBy、orderBy、slice、having、postSlice、calculatedFields 的字段引用。
   - 结果放入 `ModelResultContext`。
   - schema/permission steps 只消费提取结果。

5. 让 validate mode 复用真实 pipeline。
   - 本次状态：已完成普通 query_model 生产路径，通过 `QueryFacade.buildSqlOnly` 复用 beforeQuery + SQL-only pipeline。
   - 本次未引入 `QueryLifecyclePhase.VALIDATE`；当前 `buildSqlOnly` 已能覆盖无数据库执行的校验链路。
   - 如果后续 beforeQuery step 出现 validate/query 副作用差异，再补 phase-aware step 机制。
   - 手写规则已降级为无 `QueryFacade` 注入时的轻量单测 fallback。

6. 抽取 QueryFacade common prepare。
   - 本次状态：未实施，保留为后续。
   - 降低三个入口重复 lifecycle setup 的维护成本。
   - 顺便类型化 skip outcome。

## 回归测试建议

重构时至少覆盖以下用例：

- 普通 DSL 查询：columns、groupBy、slice、having、postSlice、orderBy。
- case-insensitive 字段解析。
- calculatedFields 与 inline expression。
- fieldAccess 白名单和 deniedColumns。
- systemSlice 与用户 slice 合并。
- DSL_CTE simple bridge。
- DSL_CTE result stage window/metric ratio，且 base SQL 带 `cteStages`。
- DSL_CTE cross-model join/funnel，且左右 base SQL 带 `cteStages`。
- SQL-only `generateSql` 与 direct execute 参数顺序一致。
- compose plan 在 query/sql-only/managed relation 三个入口的行为明确。

## 本次已处理风险

已按 BUG 流程先补失败单测，再实施修复：

- BUG 记录：`docs/9.2.0/workitems/BUG-dsl-cte-cross-model-cte-stages.md`。
- 修复范围：`DslCteDslRequestMapper` 的 cross-model join-align、source-rate、money-attribution、time-attribution wrapper。
- 修复内容：新增共享 structured-base CTE hoisting helper，支持消费 `SqlGenerationResult.CteStage`，将 base CTE stage 提升到最终顶层 `WITH`，按 wrapper alias 重命名 stage，重写 stage 引用，并保持参数顺序。
- 保留边界：继续拒绝 raw leading `WITH` base SQL；这类 SQL 仍需要结构化表示或 SQL parser 后才能安全合并。
- 新增测试：`DslCteAcceptanceSampleTest` 覆盖 cross-model join-align、time-attribution 的 structured CTE base，以及 raw `WITH` 拒绝。
- DSL_CTE planning 收敛：新增 `DslCtePlanningService` 与 `DslCtePlanningServiceTest`，`SemanticQueryServiceV3Impl` 的 plan/execute/generateSql DSL_CTE 分支共用同一规划结果。
- CTE assembly 收敛：新增 `DslCteAssemblySupport` 与 `DslCteAssemblySupportTest`，把 cross-model wrapper 的 structured base CTE hoist/alias rewrite/param ordering 抽成公共层。
- 请求 normalizer 收敛：新增 `SemanticRequestNormalizer` 与 `SemanticRequestNormalizerTest`，删除 service 内部重复的 `processSliceValues`/`convertToJdbcSlice`。
- validate pipeline 对齐：新增 `SemanticQueryServiceV3ValidatePipelineTest`，普通 query_model validate 生产路径通过 `QueryFacade.buildSqlOnly` 复用同一套 request/context/beforeQuery/SQL-only pipeline。
- 目标验证：`mvn -pl foggy-dataset-model "-Dtest=SemanticQueryServiceV3ValidatePipelineTest,SemanticRequestNormalizerTest,DslCteAssemblySupportTest,DslCtePlanningServiceTest,DslCteAcceptanceSampleTest" test` 已通过；default/mysql/postgres 三套 surefire 执行各 `Tests run: 194, Failures: 0, Errors: 0`。
- 扩展目标验证：`mvn -pl foggy-dataset-model "-Dtest=SemanticQueryServiceV3ValidatePipelineTest,SemanticRequestNormalizerTest,DslCteAssemblySupportTest,DslCtePlanningServiceTest,DslCteAcceptanceSampleTest,SemanticServiceV3Test" test` 已通过，`Tests run: 217, Failures: 0, Errors: 0`。
- 完整模块收口：已继续按 1~6 顺序拆分并修复全模块 baseline：
  - QM v2 same-table alias aggregate SQL 已修复。
  - Pivot Align fixture 泄漏导致的 preagg 与 DSL_CTE relation baseline 失败已修复。
  - semantic scale snapshot 数字参数格式漂移已通过测试侧规范化修复。
  - AggregateJoin SQL 断言已兼容方言引用符。
  - CTE-wrap running-sum postSlice parity 已改为按当前 fixture 动态生成阈值，避免重复 sqlite 执行时硬编码阈值失效。
- 最终完整模块验证：`mvn -pl foggy-dataset-model test` 已通过。

## 最终判断

当前引擎不是“架构不可用”，而是主干已经形成后，DSL_CTE 与语义服务层继续叠加需求，导致 planner、mapper、validator、assembler 的职责开始混在一起。最值得优先治理的是 DSL_CTE：它既有明显重复处理，又绕开了已有 compose/CTE 抽象，是后续维护和方言兼容的最大风险点。
