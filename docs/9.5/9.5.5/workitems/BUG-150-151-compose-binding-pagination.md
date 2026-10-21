---
doc_type: bug
intended_for: maintainers
purpose: Define and verify mandatory JOIN output binding and stage-local Compose pagination for issues 150 and 151.
---

# #150 / #151：Compose JOIN 绑定与基础计划分页

状态：LOCALLY VERIFIED（本轮引擎验收通过；另有历史 v950 合同基线失败）。基线 main `89b17267`。在主目录 main 实施；用户后续授权提交推送、更新关闭 issue、发布 Launcher 和私有 Maven，完成后独立处理 v950。

## 合同与方案

1. 优先解决 #151：基础计划的显式 limit/start 是该阶段的关系语义，在其过滤、权限、聚合、结果阶段和排序后、被 JOIN/UNION/derived 消费前应用一次。没有显式分页时不引入普通查询默认页大小。limit=0 为空集；仅 start 表示跳过后取余下行；负值继续拒绝。
2. 分页由 Compose 基础计划 lowering 统一拥有，per-base planning 请求不再携带其分页，避免供应者预先分页、窗口/结果阶段分页和外层重复应用。先完成子查询条件注入再分页；同一计划的缓存哈希仍包含分页。
3. 保留数据库方言：SQLite/MySQL/PostgreSQL 的 LIMIT/OFFSET 与 SQL Server TOP/OFFSET FETCH；无法安全表达的计划明确拒绝，不静默丢参数。分页不移到最终 JOIN 结果层，UNION 各分支保持自己的窗口。
4. #150：抽取并复用 SchemaDerivation 的 JOIN on 左右输出名检查，默认编译路径及 G10 均执行。引用的是输入阶段实际暴露名称（含 alias），不是源模型可见字段全集；使用已有 JOIN_ON_LEFT/RIGHT_UNKNOWN_FIELD、phase、路径和可用列诊断。
5. 语义 planning port 可选携带实际 SELECT 输出名，保留旧四参数构造及无元数据供应者的声明列回退。JOIN 必须先完成受权限约束的 SQL planning，再按绑定输出校验，仍不执行 SQL。QM name/alias 分离、显式 AS、派生与 UNION 输出使用同一绑定边界；G10 的 schema/权限检查消费本次编译绑定的 schema。显式 PlanColumnRef 权限仍在语义 planning 前检查，保留 FIELD_ACCESS_DENIED/permission-validate 合同；不能把拒绝降级成 SQL 生成包装错误。
6. validate/preview/execute 共享编译与绑定，不以数据库执行作为字段校验。无效 JOIN 在所有模式均提前拒绝；有效计划 preview SQL 与执行行集一致。

## 影响面与兼容性

- 直接返回的 base、JOIN 两侧、derived 源、UNION 分支及复用计划均受基础分页修复影响；此前依赖分页被忽略而得到全量数据的请求将按明确声明收窄，这是数据正确性修复。
- 普通 QueryFacade 查询分页、公共 Model SPI v2、CLI 请求结构、RuntimeEnvelope 和 name/alias 合同保持不变。
- JOIN 校验独立于 G10，不全局启用 SchemaDerivation 的同名输出冲突行为，不切换旧默认权限策略。已合法的两侧同名键与输出投影继续兼容。
- 扩展回归发现 Java JoinPlan.builder 曾绕过脚本入口的限定名归一化。归一化收口到 JoinPlan 构造，保留 left/right 和已绑定计划变量前缀，且保留 nullSafe；随后再检查输出成员身份，避免将有效限定名错误拒绝。
- 仅来源模型存在、但未投影或已改名的 JOIN 引用将提前失败。已有有效别名、聚合输出和派生输出保持可用。
- 保留逐模型权限注入与物理列检查，分页不可前推越过过滤/聚合；模型请求、绑定、命名空间缓存键保持隔离。
- 内部 Compose planning port 使用无分页请求；测试替身和内部适配器按此合同校准，不能再用预置 LIMIT 的模拟 SQL 代替真实引擎测试。

- 兼容性补充复现：QM queryId/resultId 配置被旧 Compose 声明 schema 当作 queryId，可能生成错误外层列或拒绝合法 resultId。新增实际 Launcher 失败证据及 G10 两种状态回归，保证模型 alias 在 Compose 中仍有效；不会将 alias 扩展为基础 QM columns 的查询同义词。

- SQL Server 实测补充：请求级 `id AS left_id` 被表达式处理器建成计算列，已有排序绑定只解包 QM 别名，导致显式升序和模型默认降序重复。SqlFragment 记录纯列引用身份，表达式组合不继承，SQL 改写清除；统一排序仅解包无聚合/窗口/结果阶段的纯引用，继续保留表实例、聚合和计算表达式区别。普通查询同步受益，并加入多库真实分页回归。

## 验证计划与证据

先新增真实 SQL 回归：输入 limit/start/零行/无上限、左右输入、排序和聚合、UNION 分支、分页层次、合法及非法 JOIN 字段、G10 两种状态与权限边界。
补 Runtime validate→preview→execute 实际 Launcher 验证和稳定错误输出；无效请求不得触发数据库查询。
专项通过后执行 Windows/Linux 全量及 SQLite、MySQL 5.7/8、PostgreSQL、SQL Server 门禁。
保留原 `target/compose-150-151-audit` 复现证据，新证据放 `target/compose-150-151-fix`；不运行根 clean，失败尝试也保留。报告区分实测、平台跳过与未覆盖范围。

## 最终验收（2026-10-21）

- 专项回归 598 项通过，包含 F5 权限错误合同；最终 Windows/Linux 全量各 6,021 项，失败/错误均为 0。Windows 的 11 项平台跳过已逐项在 Linux 执行通过。
- SQLite 408、MySQL 5.7 217、MySQL 8 247、PostgreSQL 247、SQL Server 217，合计 1,336 项，0 失败/错误/跳过。
- 服务集成 490 项通过，包含 Redis、MongoDB、MCP、Milvus 和预聚合；47/47 个 IT 源码测试类在最终 IT/数据库报告中均有执行记录。
- 最终 Launcher 实际 HTTP validate→preview→execute 13 组通过，测试过的 JAR 及摘要已保存。
- 前端单元 571 项、发行脚本 Linux 38 项、门禁脚本 6 项通过；两个 Console 类型检查通过。
- 窗口能力边界：SQL Server 当前 Compose 无法提升窗口结果阶段 CTE，MySQL 5.7 不支持窗口函数；对应测试执行明确拒绝断言，不算跳过。其余三库验证真实排名后分页。
- 非本轮基线失败：历史 v950 发行合同冻结 31 个模块，main 根 POM 已有 43 个。Linux 该套测试 13 通过/1 错误；使用修复前 HEAD 的 POM 同样复现，相关文件与 HEAD 一致。没有修改冻结合同以掩盖失败。
- 定制 Compose planning provider 应返回无分页 SQL，并尽量提供输出列元数据；预置顶层分页会被明确拒绝，避免二次分页。旧四参数构造仍可用。

[本地完整报告及证据目录](../../../../target/compose-150-151-fix/RESULTS.md) 保存最终汇总、源码快照、JUnit XML、Runtime 响应、测试 JAR，以及所有失败/中止尝试。最终只采用 completion-* 验收批次。未运行根 clean；发布结果另行记录。
