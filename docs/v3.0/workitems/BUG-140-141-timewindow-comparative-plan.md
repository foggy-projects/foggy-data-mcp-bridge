---
doc_role: implementation-plan
doc_purpose: Define period semantics and a staged implementation for GitHub issues 140 and 141.
version: v3.0
ticket: BUG-140-141-timewindow-comparative-plan
github_issues:
  - https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/140
  - https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/141
status: implemented-backend-verified
owner: runtime-query-planning
created_at: 2026-10-20
updated_at: 2026-10-20
source_commit: 72c76545e781629371e1b577981ed2ecfc9a24e6
---

# timeWindow 比较期间规划实施方案

本方案联合处理 #140 的月度环比粒度及跨年关联错误，以及 #141 的当前期范围未进入查询计划。实施目标是统一比较类 timeWindow 的期间身份、比较关系、读取范围和输出 schema，继续复用 BaseModelPlan、DerivedQueryPlan、JoinPlan 及现有治理编译链。

2026-10-20 用户已确认 ISO 周同比、非整周期首批拒绝、日期闭区间包含结束自然日三个核心合同。[#142 方案](BUG-142-window-calculated-field-stage-plan.md)及其[核心流程 review](REVIEW-142-window-calculation-core-flow.md)也已落档；本轮仍只更新文档，不修改引擎代码或开始编写修复测试。后续按 #140/#141、#142 顺序实施。`v3.0` 为现有引擎 workitem 归档目录，不代表本次承诺发布版本。初稿落档后的[核心流程 review](REVIEW-140-141-query-engine-core-flow.md) 已完成静态分析，其必要调整已纳入下文；运行时影响在实施阶段用定向测试验证。

## 1. 范围和依赖

- #139 由独立会话修复，当前分支已包含提交 f0deb244。复用其执行路由，不在本工作项重新实现数据源路由；本次文档更新未重新验证该修复，后续命名数据源的端到端验收仍需覆盖。
- #140/#141 一起设计、连续实施、联合验收。可以拆分提交，不能在只有关联键或只有范围正确时宣布完成。
- #142 的方案讨论和静态 review 已落档，本轮不启动实现。实施按 #140/#141、#142 的依赖顺序分步推进和分别验收，不合成一次通用规划器重写。保留 timeWindow 已支持的后置标量计算；#140/#141 不承担普通 calculatedFields 的窗口依赖绑定与结果阶段改造。
- 不新增 qoq、财务日历、多时间窗口、缺失期间补零、跨数据源 Join 能力。
- 不借机重写 QueryFacade、通用 Compose 优化器、缓存框架或 JDBC 表达式引擎。
- 滚动与累计模式建立保护性回归；不能把本次比较范围算法直接施加到它们。

## 2. 必须保持的语义不变量

1. grain 决定输出粒度；不能为编译补日级 groupBy，使月度结果变成日级。
2. 当前和比较分支在每个“期间键 × 业务分组键”上最多产生一行。当前分支 LEFT JOIN 比较分支。
3. 期间身份、当前期间到比较期间的映射、源数据范围是三个不同概念。
4. 源模型、namespace、权限身份、模型解析结果和业务时区在一次调用内保持一致。
5. 原生 SUM、COUNT、AVG、MIN、MAX、去重计数各自在原始允许数据上按其模型语义聚合，不能统一改成二次 SUM。
6. 比较关系不能附加原始日期相等、自然年份相等等多余约束。
7. current 有数据且 prior 不存在：prior、diff、ratio 为 null；prior 为 0：diff 正常，ratio 为 null。
8. 无 current 的期间不自动补行。隐藏期间列不得出现在公共结果、改变 DISTINCT、或暴露未授权字段。
9. 排序、分页在比较和后置投影之后；总行数语义沿用公共 API 合同，不得等于分页后条数而冒充全量条数。
10. validate、explain、execute 的最终结构编译使用同一实现，但各自保留正确的权限动作及副作用边界。

## 3. 周期支持矩阵

以下为本次目标合同，其中三个核心语义选择已获用户确认。支持目标不代表当前引擎已经实现，不能以当前 validator 接受请求作为已有正确能力的证明。

| comparison | grain | 本次目标 | 期间身份和比较映射 |
|---|---|---|---|
| mom | month | 必修，直接覆盖 #140 | 自然年月，prior = 当前月前一月 |
| yoy | month | 必修，直接覆盖 #141 | 自然年月，prior = 去年同月 |
| yoy | quarter | 保持并补范围/边界回归 | 自然年季度，prior = 去年同季度 |
| yoy | year | 修正现有 dayOfYear 分支 | 自然年份，prior = 上一年；不需要日级子键 |
| wow | day | 修正并验证跨年 | 业务自然日期，prior = 前 7 个自然日 |
| wow | week | 修正完整周身份 | 周起始日期，prior = 前一个完整周 |
| yoy | week | 按已确认周历合同支持 | ISO 周年 + 周号，prior = 上一周年相同周号；无对应第 53 周则 prior 为 null |
| yoy | day | 继续拒绝 | 不在当前兼容矩阵内，不借本次引入闰日同比语义 |
| mom | day/week/quarter/year | 继续拒绝 | 不把 mom 重新解释成任意粒度的上一期 |
| wow | month/quarter/year | 继续拒绝 | 不新增组合 |
| qoq 或自定义比较 | 任意 | 本次不新增 | 后续单独扩展公开合同 |
| ytd/mtd/rolling_* | 当前允许的组合 | 保护已有行为，单独列出语义缺口 | 不复用比较期范围算法，不在本次顺带改变 ROWS/日历窗口合同 |

### 3.1 日历和时间轴

- 自然年月可用 `year * 12 + month - 1`；自然季度可用 `year * 4 + quarter - 1`。这些是内部实现选择，不是公共字段。
- 周使用确定周历下的周起始日期或周序号。周同比不能用固定减 52 周、`自然年 + week`、或 dayOfYear 替代。
- 标准周历采用周一开始的 ISO 周规则，并在模型时间轴已声明不同含义时明确拒绝冲突，不重新解释已有 `$week`。
- 从模型元数据解析时间字段及其来源。仅凭 `$year`、`$month` 后缀不能确认其属于自然历；财年/财季不能套用自然历算法。
- 原生 DATE/DATETIME 可经受控表达式产生期间键；整数日期代理键必须通过已声明的逻辑日期/时间维度适配。不能对 `20240101` 直接执行日期截断，也不能将代理键和 ISO 字符串直接比较。
- 优先复用现有 FDialect 日期截断/日期偏移能力，但要求明确支持；父类“返回原表达式”的 fallback 不算支持。
- 使用已有业务时区和请求时间基准；不能引入执行机器默认时区。绝对日期保持模型定义的记录日期语义，不能无依据做时区转换。

### 3.2 分组和展示

- 优先支持模型已有的 year + month、year + quarter、year，以及可确认语义的 yearMonth/periodStart。
- 仅 month 或 quarter 无法区分跨年周期；不能默默合并年份。缺少必要期间身份时给出可操作诊断。
- 周身份不可被附加的自然年份分组切开。时间展示字段与业务分组键由同一时间轴解析结果分类。
- 业务分组键保持原语义；空业务维度采用方言支持的空值安全相等表达式，不用任意哨兵值 COALESCE。
- 只按当前请求实际选择的指标和维度形成输出；targetMetrics 不能令原本选中的非目标指标无故消失。

### 3.3 日期维度与原生日期字段的统一接入

本次目标同时支持以下输入表示，统一解析为“可执行的逻辑日期表达式 + 时间语义 + 期间映射”，不强制所有调用方建立日期维表：

| 模型表示 | 解析规则 | 支持边界 |
|---|---|---|
| 独立日期维表，事实表以日期键关联 | 沿已声明维度关系取得实际 DATE/DATETIME 日期或等价的明确时间映射 | 整数代理键不能当时间戳；caption 只有在类型/语义证实为日期时才可使用 |
| 事实表上的内联日期维度 | 使用该维度实际 DATE/DATETIME 列，复用已声明时间属性 | 不要求额外关联维表 |
| QM 暴露的普通 DATE/DATETIME 属性 | 直接解析已授权字段，按 grain 派生期间键 | 不要求预先建立 `$year/$month` 等伪维度属性 |
| 只有无映射的整数或字符串日期键 | 缺少可验证逻辑日期语义 | 明确诊断，不按名称或数值外形猜测日期 |

- 时间轴解析应覆盖 QM 可见字段及其底层属性/维度来源，不能继续只枚举 `getDimensions()`。内部 DATE 对应的枚举别名需按现有类型规范归一化。
- 调用方显式选择的 DATE/DATETIME 字段可作为时间轴；timeRole 用于解释业务含义和推荐，不能只因不是固定的 business_date 字面量就把明确的支付日期/创建时间轴排除。实际字段存在性、类型、模型访问权限仍必须校验。
- 多个日期字段时由 timeWindow.field 明确选择，不能自动在下单、支付、发货日期之间切换。
- 纯 DATETIME 没有年月属性时，规划器派生内部期间键和稳定的周期展示投影；不能为显示周期要求调用方虚构元数据字段，或把原始时分秒直接保留在 groupBy 中。公开展示别名/类型须在请求归一化合同和 OutputSchema 中一致表达，并有真实服务用例。
- DATETIME 事实可以含任意时分秒；“首批拒绝非整周期”只约束 timeWindow.value 的观察区间是否对齐 grain，不要求事实记录都落在周期起点。仍可使用普通 slice 做记录筛选，并按已确认的 slice 合同影响两期数据。
- DATE 使用自然日期。无时区 DATETIME 按模型约定的记录时间解释；若源列表示绝对时间且业务周期按某时区计算，则分组和边界转换必须使用同一已确定时区，不猜测服务器时区。
- `[] [2025-01-01, 2025-01-31]` 在 DATETIME 上生成从 1 月 1 日开始至 2 月 1 日开始的半开范围，包含 1 月 31 日全天。不能用 `<= 2025-01-31 00:00:00` 或拼接最大毫秒替代。
- 无日期维表不等于自动补齐没有数据的期间；缺期仍遵守第 2 节的 null/不补行规则。

## 4. value、range 和 slice 合同

### 4.1 当前期与比较期

`timeWindow.value` 定义当前期观察范围。对完整连续自然周期，比较期范围由期间映射推导：

| 请求 | current 读取范围 | prior 读取范围 |
|---|---|---|
| 2012 全年月度同比 | [2012-01-01, 2013-01-01) | [2011-01-01, 2012-01-01) |
| 2012 全年月度环比 | [2012-01-01, 2013-01-01) | [2011-12-01, 2012-12-01) |
| 2025 Q1 季度同比 | [2025-01-01, 2025-04-01) | [2024-01-01, 2024-04-01) |
| 一个完整周的周环比 | [weekStart, nextWeekStart) | [previousWeekStart, weekStart) |

- 周同比通过期间映射生成所需范围集合；不能假定所有范围都能通过对两个日期端点减一年得到。
- 无 value 时保留“对 slice 允许的数据进行比较”的行为，不从用户时间 slice 自动推断当前期范围。
- range 保留 `[)`、`[]`。timeWindow 的自然日期边界统一规范化为 `[start, endExclusive)`；`[]` 日期结束边界为次日开始，包含结束自然日。该规则已由用户确认，不改变普通 DATETIME slice 运算符的含义。
- 相对日期使用注入的请求 Clock/业务时区一次求值；编译、绑定参数和诊断共享结果。独立请求跨日允许范围变化，同一请求内不得变化。
- start > end、非法日期/日历映射、超过既有资源预算等在 validate 拒绝；空区间采用确定的空结果语义并测试。
- 日期界限通过已有受治理的过滤/绑定机制进入计划，不能拼接调用方字符串 SQL。

### 4.2 slice 叠加规则

令 A 为权限/系统约束，S 为调用方完整 slice，W 为当前期范围，P(W) 为比较期范围：

```text
current = A AND S AND date in W
prior   = A AND S AND date in P(W)
```

- 普通业务过滤、其他日期轴过滤、同一日期轴过滤均原样作用于两个分支。不得删除、平移或放宽 S/A。
- `slice` 覆盖 2011—2012 年且 value 为 2012 年同比：正常比较。
- `slice` 仅允许 2012 年且 value 为 2012 年同比：prior 被用户源过滤排除，返回 null，并对可静态证明的冲突给出诊断；不新增无条件硬错误。
- 部分裁掉比较期：缺失比较值保持 null；可证明的用户过滤裁剪产生 warning。
- 不基于权限过滤泄露被隐藏期间或数据存在性。复杂 AND/OR 保留原表达式；诊断无法证明时不要猜测，也不为诊断额外执行数据查询。
- 不自动把 year、month、yearMonth 等时间属性 slice 变成输出过滤。若未来支持这种便捷语义，应单独设计公开合同。

### 4.3 非整周期范围首批明确拒绝

用户已确认首批采用完整周期范围：对比较模式，先按 range 规范化边界，再验证是否对齐 grain。月粒度需要两端为月初；季度、年和 ISO 周分别对齐对应周期起点。不能静默扩大、缩小或取整。

范围不对齐时给出明确的部分周期不支持诊断，并在发布说明记录从“接受但错误”收紧为“明确拒绝”。这不禁止 DATETIME 事实包含时分秒，不改变普通 queryModel 任意日期 slice，也不据此拒绝现有 rolling/YTD 请求。

无 value 时不额外发明完整周期过滤；保留由 slice 限制数据的行为。有 value 时，完整周期检查只作用于该观察范围，slice 仍可裁剪其中数据并产生相应诊断。未来部分周期比较需单独制定截片映射、月末/闰日及空截片合同。

## 5. 内部结构

建议引入 timeWindow 专用不可变中间表示，类名可在实现时小幅调整：

```text
SemanticQueryRequest
  -> TimeWindowDef                         保持公开字段
  -> TimeAxisResolver                     解析逻辑日期、分组、日历/方言能力
  -> ComparativePeriodResolver            映射当前/比较期间，解析边界和范围
  -> ResolvedComparativeWindow             期间键、范围、业务键、指标、诊断
  -> ComparativePlanBuilder
       Base(current filters + aggregation)
       Base(prior filters + aggregation)
       Derived(period identity / alignment)
       Join(current LEFT prior)
       Derived(diff / ratio / public projection)
  -> existing Compose compiler -> compiled SQL + params + output schema
```

- 避免“shiftField + grainKeyField”作为唯一抽象。范围集合与期间对应关系是内部数据，不新增用户可控数据源/权限字段。
- ResolvedComparativeWindow 是计划构造和描述的共同输入；最终可见字段以真实 OutputSchema 为准，不能用手写列名单给缺失列补合法性。
- 构造两个不可变 BaseModelPlan；原始请求、slice 列表和计算字段 AST 不得因两侧构造产生交叉修改。
- 不把整个源模型先聚合再对 AVG/COUNT DISTINCT 等做二次聚合；必要期间键在正确的基础聚合阶段形成。
- 当前/比较源范围不同，不能被缓存或公共子表达式归并为同一个基础计划。沿用已有计划等价规则并加保护测试，不全局关闭优化。
- 不提前增加动态日历表和任意日历脚本执行面。ISO 周同比若需要映射节点，应先验证现有受控表达式/元数据能够表达，再决定最小扩展。

## 6. 类级改动清单

以下路径均相对仓库根目录，Java engine 类位于 `foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/`。

| 类或文件 | 本次调整 | 不应顺带扩大 |
|---|---|---|
| `engine/compose/plan/TimeWindowDef` | 保持公开字段；补边界合同及规范化入口 | 不新增 qoq、多窗口等公开能力 |
| `engine/compose/plan/TimeWindowValidator` | 校验真实时间轴、兼容矩阵、期间/范围和实际输出；结构错误统一诊断 | 不用字段名 fallback 绕过 metadata |
| 新 `TimeAxisResolver` | 模型时间轴、展示字段、业务键、逻辑日期及能力解析 | 不重做整个 TM 元数据系统 |
| 新 `ComparativePeriodResolver` / `ResolvedComparativeWindow` | 统一日/月/季/年/周期间和范围；Clock 可注入 | 不实现通用日历服务 |
| `engine/compose/plan/TimeWindowExpander` | 比较分支委托新构造器，移除重复分支；累计/滚动保持独立 | 不全局改变窗口 frame |
| 新 `ComparativePlanBuilder` | 两侧范围/聚合、期间映射、Join、最终投影 | 不绕过 Compose 直接执行 SQL |
| `plugins/result_set_filter/TimeWindowInterceptor` | 作为适配器调用统一解析；保留 final controls；输出/后置计算引用真实 schema | 不新建第二套权限/缓存生命周期 |
| `semantic/service/impl/SemanticQueryServiceV3Impl` | 共用最终计划准备/编译；保留完整请求身份和动作；接 #139 路由 | 不复制 PlanExecution 或另建执行器 |
| `semantic/domain/SemanticRequestContext` 与 Compose 上下文桥接 | 保留请求固定模型、权限 session、完整身份、治理字段和 explain trace；复用现有 copy/withGovernance | 不对跨模型 Compose 共享一个单模型 pin |
| `service/impl/QueryFacadeImpl`、`ModelResultContext` | 区分缓存结果与待执行计划；真实结果回填后 process 恰好一次 | 不给普通查询重排全部 Step |
| `plugins/result_set_filter/L1CacheStep` 及结果步骤适配 | 防止占位结果写入；命中不再执行；仅处理具备已定义语义的输出 | 不默认对比例求和或新增 timeWindow 小计能力 |
| `engine/compose/compilation/ComposeSqlCompiler`、`PerBaseCompiler` | 必要时透传权限动作/请求绑定；核查两分支上下文 | 不全局修改 SQL 优化规则 |
| `engine/query_model/JdbcQueryModelImpl` 受管准备入口 | 用定向测试核实 Compose 基础分支的物理列权限及预聚合准备；必要时复用 PREPARE_MANAGED_RELATION | 不直接替换所有 generateSql 调用 |
| `engine/compose/schema/OutputSchema` 使用处 | 计划实际输出为权威来源，隐藏列隔离 | 不大改所有 schema 类型 |
| `foggy-dataset/.../dialect/FDialect` 及各方言 | 复用、验证需要的截断/偏移能力；只补已证实的必要差异 | 不把 no-op fallback 当作支持 |
| Runtime API / MCP / Analytics schema 和说明 | 更新已确定的支持矩阵、范围及诊断，保持入口一致 | 不分别创造不同 DSL |
| 测试 fixture / parity catalog | 改正错误月度 happy case，新增业务期望 | 不仅重新录制现有错误 SQL |

## 7. 分步改造与检查点

### S0 已确认合同固化和基线

在 #142 方案讨论结束、统一开工后，将已确认的 ISO 周同比、非整周期拒绝、日期闭区间及日期字段接入规则固化为测试；记录发行版/source commit；核对 #139 接口。先补能暴露问题的行为测试，并保留普通聚合、季度同比、累计/滚动、后置计算正例。

退出条件：目标矩阵与兼容性变化无未决项；关键失败用例确实失败于目标缺陷。

### S1 统一最终计划校验

提取仅用于 timeWindow 的准备/编译共用逻辑，validate 不执行数据 SQL，explain 不产生执行副作用。透传完整 SemanticRequestContext 及正确 PermissionAction；当前 validate 为 VALIDATE，带 payload 的 explain 按现有合同为 EXECUTE，不新增 EXPLAIN 动作。核实 R06 的物理列安全准备。现阶段不要求所有错误周期转绿，但 validate 必须能捕获最终计划结构错误。

退出条件：缺字段、粒度错配等在 validate/explain/execute 得到一致结构诊断；普通查询正例保持。

### S2 期间解析与能力检查

引入时间轴和期间中间表示；实现月、季度、年及 day/wow；按已确认合同实现 week/wow 和 week/yoy。先做纯函数映射测试，不依赖数据库宽松的类型转换。

退出条件：跨年、53 周、闰年、字段映射、时区/Clock 测试通过；不支持的时间轴明确诊断。

### S3 当前/比较分支与范围下推

构造两个基础计划；原样保留源 slice/治理过滤，追加各自范围；在基础聚合粒度产生期间键，再做映射关联。保留原指标聚合语义。

退出条件：#140/#141 最小数据集精确数值和范围均正确；执行计划没有日粒度退化及关联放大。

### S4 输出和生命周期收口

从计划输出生成公共 schema；后置 calculatedFields、排序、分页使用实际可见字段。将待执行计划与缓存命中区分，真实结果写回同一上下文后再运行允许的 process 步骤一次；不得仅在外层补一次 process 而留下占位结果提前处理。修正分页 start/limit/total/hasMore，区别总行数与指标 totalData。避免重新运行整条 beforeQuery 管线。

退出条件：投影、别名、null、后置计算、total、权限和错误封装一致；无内部列泄漏。

### S5 多方言及发行产物验收

运行矩阵中的数值测试，验证 native DATE 与日期维度两种模型，接入 #139 命名数据源回归，更新公共说明及 fixtures。最后用打包 Launcher 通过公开 validate/explain/execute 命令重跑 Chinook 请求。

退出条件：真实数值证据、范围参数、支持/不支持诊断齐全；数据库未运行必须标为未验证，不得计作通过。然后才转入 #142。

## 8. 回归矩阵

| 维度 | 必测用例及断言 |
|---|---|
| 期望值 | 2012-11 current=37.62，prior=42.62，diff=-5.00，ratio 约 -0.11731581417175；使用 Decimal/BigDecimal 独立核对 |
| 月环比 | 12 月 100、次年 1 月 150，差额 50；每月多天多行仍只输出一行 |
| 缺期 | 1 月有、2 月无、3 月有；3 月 prior 为 null，不借用 1 月 |
| 零/空 | prior=0、prior 缺失、current 缺失、空输入；遵守 null 和不补行合同 |
| 季度/年 | Q1 对去年 Q1、跨年季度、年度同比仅年键、不额外请求 dayOfYear |
| 周 | ISO 周跨自然年、周一/周日边界、52/53 周、缺失上周年对应周；按已确认日历验证 |
| 日/周比较 | 日粒度向前 7 个自然日，跨年及业务时区边界 |
| value | 全年改单月输出确实改变；范围外放大额哨兵记录；右边界记录严格排除 |
| range/日期 | `[)` 与已确定的 `[]` 等价用例；反向、空、非法范围；注入 Clock 的跨日稳定性 |
| 非整周期 | 明确拒绝不对齐 grain 的 value；[] 先规范化再检查；合法 DATETIME 明细时间不受此限制 |
| 日期表示 | 独立日期维表/整数代理键映射、内联时间维度、普通 DATE 属性、普通 DATETIME 属性生成同样期间结果；无映射键拒绝；月底含时分秒/小数秒的记录及 null 日期 |
| slice | 无 slice、业务 slice、覆盖两期的日期 slice、完全/部分裁掉 prior、其他时间轴、复杂 OR/AND |
| 业务键 | 两个门店不同金额、空维度、复合业务键、重复显示 caption；验证无错配/放大 |
| 指标 | SUM、COUNT、AVG、MIN/MAX、已有支持的去重计数；目标/非目标指标并存、别名 |
| 输出 | 请求列集合、实际 schema、内部键不可见、ORDER BY、limit/start、returnTotal、timeWindow 后置标量公式 |
| 生命周期 | validate/explain 不执行数据查询；execute 不重复运行效果步骤；完整身份/动作；请求对象重复使用结果一致 |
| 路由/治理 | #139 的两命名空间同名 QM；受保护模型、行列权限在两分支保持；查询失败不能换库 |
| 优化 | 不同范围的 base plan 不被错误合并；预聚合与原表数值一致；未匹配时正确回退既有受治理路径 |
| 既有功能 | 普通 detail/aggregate、Compose join、季度同比、timeWindow 后置计算、rolling/MTD/YTD 现有正例 |
| 方言 | SQLite、PostgreSQL、MySQL 8、SQL Server；比较类若只使用 Join/aggregate，不应因没有窗口函数被跳过；MySQL 5.7 做相应非窗口覆盖 |

测试层级：期间解析单测 → 计划/OutputSchema 编译测试 → SQL/参数检查 → 真实数据库数值测试 → Runtime API 三入口 → Launcher 公开 CLI。结构快照仅作为辅助。

## 9. 测试落点和运行要求

- 修正 `TimeWindowExpanderTest` 的 mom/day-key 断言，以及 year/week 的错误行为固化。
- 新增解析器、范围、slice 合同测试；扩展 `TimeWindowValidatorTest`、`TimeWindowOutputSchemaTest`。
- 扩展 `ComparativeExecutionIT`，请求必须真的包含 value；新增 mom、week、year 及当前范围外哨兵记录。
- 更新 `TimeWindowParitySnapshotTest` 和 `src/test/resources/parity/timeWindow/`，移除月度案例的日级 groupBy。
- 扩展 `SemanticQueryServiceV3ValidatePipelineTest`、权限动作/身份测试和 Runtime API 错误映射测试。
- `TimeWindowExecutionIT` 用于累计/滚动保护，不能把 ROWS 与自然日等价写成新的时间合同。
- 执行前按当前 Maven profiles/Surefire/Failsafe 配置选择命令，明确区分单元测试、IT、多数据库和发行包验收。新测试名不得被默认运行器遗漏；不把 helper 模拟实现当作真实服务验证。
- 根 POM 默认 skipITs=true，Surefire 排除 *IT；正式证据沿用 `scripts/verify-v934-unit.sh`、`scripts/verify-v934-integration.sh`、`scripts/verify-v934-database-matrix.sh` 的现行门禁和 inventory，不能用普通 mvn test 或旧 multi-db profile 替代。开发定向运行与正式 lane 分开记录。

## 10. 兼容性与发布

- 不改公共 namespace/model 传参，不新增调用方可控数据源/权限配置。
- 保持 `__prior`、`__diff`、`__ratio` 命名以及已支持后置计算的引用合同。
- value 从被忽略变为生效，结果和扫描量会改变，这是目标行为，必须在发布说明列明。
- 错误的旧 SQL/输出不作为兼容目标；但不能无说明收紧可接受请求或改变日历。
- 周历/非整周期/闭区间日期的决策必须写入公开说明及 capability/diagnostic 适用范围；不由某个客户端自行猜测。
- 计划缓存/结果缓存若受最终语义变化影响，应检查既有版本化与失效机制后按需调整，不假设仅更新代码就自然失效。

## 11. 已确认决策和开工条件

1. 周同比采用 ISO 上一周年相同周号，第 53 周无对应项时 prior 为 null；不默认采用前 52 周。
2. 非整周期首批明确拒绝，并记录兼容性收紧；本批不实现部分周期截片比较。
3. timeWindow 的纯日期 `[]` 采用包含结束自然日的合同，并规范化为下一日开区间；普通 slice 保持不变。

以上三项由用户于 2026-10-20 明确确认。#142 讨论与文档 review 已完成，本轮仍只落档，不启动实现。

## 12. 后续 #142

窗口依赖绑定、聚合 → 窗口 → 标量结果阶段、复合 OVER 绑定及缺失输出诊断已纳入 [#142 实施方案](BUG-142-window-calculated-field-stage-plan.md)。后续实现按 #140/#141 到 #142 的依赖顺序分步推进、分别验收。#142 复用本次明确的实际输出 schema 和请求上下文原则，但不要求 #140/#141 先完成普通窗口计算的依赖与结果阶段改造。

## 13. 核心 review 后的范围结论

- **本次一起做**：完整上下文桥接、最终计划统一校验、pending plan/缓存结果状态区分、真实结果单次处理、实际输出 schema、分页封装、周方言一致性、测试执行入口收口。
- **先验证再决定最小改动**：基础 Compose 编译是否缺少物理列/预聚合受管准备保护。确认缺口后复用现有受管准备入口，不另写安全检查或全局替换编译器。
- **只保护，不重构**：计划哈希、预聚合匹配、普通查询生命周期、通用 Compose join。已有 PlanHash 包含 slice，未发现需要全局重写的依据。
- **后续单独处理**：#142；rolling 的 ROWS 与自然日差异；累计/滚动 value 的历史读取与输出范围；财务日历和通用小计。发布结论明确 #141 本次解决的是比较模式。

以上发现及证据路径见[核心流程 review](REVIEW-140-141-query-engine-core-flow.md)。本轮仅产出方案与静态 review，不把建议和测试计划标记为已实现或已通过。

## 实施进展（2026-10-20）

此前“仅讨论/未实施”的文字为方案阶段记录；用户已授权统一实施，当前状态以本节和后续验收记录为准。

期间比较已接入 ComparativePeriodResolver、TimeAxisResolver、ComparativePlanBuilder 与 CalendarPeriodSql：完整期间键用于两分支关联；当前/上期范围独立生成；原 slice 和治理条件保留在两分支。ISO 周同比按上一周年同周号映射，无对应第 53 周返回 null。DATE 与 DATETIME 轴共用期间语义，DATE 的 JDBC 绑定保留纯日期，SQLite 不再给 DATE 边界追加午夜时间。

公共链路已调整：timeWindow 使用延迟执行结果，真实结果在 QueryFacade 的 process 阶段前产生；编译保留治理、权限动作和 catalog 固定上下文；受管基础关系准备复用权限与预聚合保护；主查询和计数使用请求固定模型的数据源。公开列由实际计划投影决定，分页与 count 基于最终关系。

必需后端全量测试已通过。完整结果、初轮问题的收尾修复和现行门禁见 [最终验收记录](VERIFICATION-140-141-142.md)；不将实际 Maven 执行结果冒充历史发布门禁通过。

## 最终验收（2026-10-20）

实现与本次必须合并的核心流程调整已完成。Windows/Linux 全量单测各 5,842 项，均无失败和错误；常规集成、五库矩阵及外部服务共 926 次测试执行，零失败、零错误、零跳过。后续收尾已修复 Viewer E2E 失配、补跑真实 MySQL 浏览器链路（5/5、零跳过），并恢复两个源码扫描的正式 JUnit 执行。当前版本沿用 current-main 本地验收门禁，V934 历史清单保持原样。详见 [最终验收记录](VERIFICATION-140-141-142.md)。
