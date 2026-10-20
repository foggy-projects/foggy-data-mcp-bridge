---
doc_type: implementation-plan
status: LOCALLY_VERIFIED
date: 2026-10-20
---

# 统一排序与字段绑定

在 main 的 #145 修复基础上实施；治理迭代 9.5.5，Java 坐标仍为 9.3.0-SNAPSHOT。
用户明确授权规划后直接实施、回归后全量测试。没有发布、推送或修改外部业务数据。

## 设计

1. 引入请求内 `OrderByFieldBindings`，记录分组转换前的源列与转换后的投影列。
   输出名称、绑定的源表达式、实际排序表达式分别处理。只解开纯 QM alias 包装，
   不解开表实例、计算、semantic scale、聚合包装，不依赖 SQL 文本或物理列名判断等价。
   身份只在当前 pinned model / request 内使用，不成为持久化或公共 SPI 身份。
2. 引入 `OrderByPlanner`，普通 / DISTINCT / GROUP BY 共享绑定和按表达式去重；
   模式决定是否允许非投影字段。分组的源字段通过显式 lineage 绑定到分组后的表达式，
   不将 SUM/AVG、原始列和结果阶段表达式混为一谈。
3. 保留既有脚本预置排序优先级，随后是请求排序，最后 QM 默认（含隐式主键）排序；
   同表达式只保留首次出现的方向和 null 选项。不同字段原相对顺序不变。
   模型默认不再重复追加已经出现的表达式，修复普通查询 SQL Server 重复排序。
4. 请求字段先解析 calculated / SELECT 语义；普通模式为既有 condition-only 名称保留
   条件解析 fallback。DISTINCT / GROUP BY 不以 condition alias 猜测投影。
5. AutoGroupBy 只负责补充分组，不按字符串提前删排序。合法别名交由统一绑定；
   非法显式排序由编译器拒绝。结果阶段延迟排序保留，由相应阶段处理。
6. 不扩充 SELECT，不修改缓存 QM orders，不裁剪 JOIN，不修改 namespace、缓存 key、
   权限阶段、模型生命周期、公共 DTO/SPI。结果阶段 renderer 仅补必要去重，不改计算公式。
7. 真实 NULL 回归暴露了原有方向拼接错误：native 方言需要 `expr ASC NULLS FIRST`，
   emulated 方言需要先计算 null rank 再排序值。新增共享 `OrderBySqlRenderer` 供普通、
   window、postAggregate 三个渲染位置使用。
   SQL Server 的 DISTINCT 不允许直接 ORDER BY null-rank CASE，非 native 方言在需要
   NULL 位置时渲染为 `SELECT 原投影 FROM (SELECT DISTINCT 原投影 ...) ... ORDER BY ...`；
   没有隐藏输出列、没有新增绑定参数，分页在外层执行，total 按等价关系计算。

## 影响面与兼容性

| 范围 | 行为 / 风险 / 验收 |
|---|---|
| 普通查询 | 非投影排序保留；显式主键与隐式默认只生成一次。先查公开查询字段可能纠正旧 caption→ID 排序；用不同字典序的真实数据验证 |
| GROUP BY / 自动分组 | 绑定到聚合后的投影；短名、点路径和下划线别名一致。非法显式排序从可能静默忽略改为拒绝，是有意收紧 |
| DISTINCT | 保持 #145 非投影错误码、去重粒度、分页/total、默认非投影省略 |
| 重复请求 / 模型 / 脚本排序 | 首项决定方向及 null 选项；脚本优先级保留；不同表实例及不同表达式不能误合并 |
| 默认 JOIN | 在去重或忽略默认排序前保留原 JOIN 依赖，INNER JOIN 行过滤不变 |
| 多阶段 | window / postAggregate 保持阶段边界；重复外层排序不得重新出现，total 与明细不漂移 |
| 权限 / cache / preagg / namespace | 不改变治理顺序；真实字段权限、物理列、缓存和预聚合 IT 回归 |
| 模型快照 | 仅创建请求级绑定与排序副本，多次查询不能改变共享 QM 定义 |
| 数据库 | SQL Server 实际执行是必需门禁；SQLite、MySQL 5.7/8、PostgreSQL 同组矩阵 |
| 接入 | validate / execute / explain 共享底层排序规划；没有新增请求参数 |

不把本次绑定提升为所有谓词的统一表达式系统；不合并 WHERE 与 SELECT 的业务语义。
不会直接全局替换 `JdbcQuery.containSelect`，以避免改变其他 JOIN / 投影调用方。

排序重复项采用首项保留，不改变不同表达式的先后次序。对普通查询，SQL Server 以前
可能报错的显式主键 + 隐式主键现在成功；其他容忍重复排序的数据库保留原来的首项方向。
NULL 包装只在 DISTINCT 且显式 NULL 选项且方言不支持 native NULLS 时发生，可能增加一层
派生表；不声称提升数据库执行速度，结果正确性和分页/total 等价由真实数据库回归验证。

### 调用方迁移与部署边界

- 普通查询按 caption 排序时以实际输出值为准；如果调用方以前依赖条件定义隐式转成 ID，
  应明确请求 `$id`，不能继续依赖条件解析副作用。
- 自动分组的非法排序会被拒绝。调用方应删除无效排序，或显式选择符合业务粒度的分组/
  聚合表达式；引擎不会自动增加列来改变结果粒度。
- 已选列的输出 alias 与源名称共享同一绑定，重复请求仅首项生效；不增加名称歧义时的
  猜测或跨表同名合并。
- 请求/响应 DTO 和共享缓存数据格式不变。部署到启用了跨进程结果缓存的环境时，应按
  既有发布流程失效受影响模型的旧结果，避免继续返回旧排序结果；滚动部署期间不能假定
  新旧版本的排序结果语义完全相同。本地实施未操作任何共享生产缓存。

## 修改前证据

`target/order-binding/red/summary.json`：23 项，3 失败、0 错误、0 跳过。
捕获普通主键重复（预期 1 项实际 2 项）、不同别名重复（预期 2 项实际 4 项），
自动分组未投影排序被静默删除；其他 20 项通过。

既有 AutoGroupBy 单元及集成用例中“忽略非法排序”的断言改为“保留到绑定后拒绝”，
这是上述显式兼容性决策，不是跳过用例或放宽断言。

## 验证顺序

1. 补普通查询重复主键、别名重复、自动分组别名和非法排序回归，在修改前记录失败。
2. 完成绑定身份、聚合 lineage、脚本/请求/默认优先级、方向/null、共享模型不变的单元测试。
3. SQLite 专项和既有嵌套维度、自连接、scale、window/postAggregate、权限兼容测试；
   SQL Server 与另外四数据库执行，检查实际行序、SQL、total。
4. 专项通过后运行完整 Maven reactor、全部已有 IT 范围（外部服务按实际环境启动）、
   前端单元与浏览器套件、current-main 合同。按 lane 记录错误、跳过、源代码快照。
   在线付费 LLM 分支仍不启用；不能把默认跳过 IT 的 verify 称为所有 IT 已通过。
5. 修订当前架构文档与本记录的最终行为、证据和限制。

## 架构判断与后续优化边界

现有主流程按接入规范化、模型快照、权限编译、缓存/预聚合决策、SQL/结果阶段执行划分，
方向合理。本次问题集中在编译器内部：三种查询模式分别解析排序，又由 AutoGroupBy 提前
按字符串删除排序，使名称解析、合法性判断和 SQL 渲染的职责交叉。将绑定与策略收敛到
请求内 planner，与上述阶段划分一致；方言 renderer 只处理 SQL 语法差异。

仍有可以分步优化的地方：

1. `JdbcModelQueryEngine` 仍承担字段解析、JOIN 依赖和多阶段协调。后续可将更多编译结果
   表达为不可变的阶段输入/输出，但应先建立包含权限、预聚合和多阶段查询的等价性门禁。
2. `JdbcQuery.containSelect` 等旧调用仍使用名称/alias 判断。可逐个调用方迁移到有明确
   lineage 的绑定，不能一次全局替换；尤其不能把条件语义与投影语义强行合并。
3. window 与 postAggregate 使用不同的阶段输出引用。后续可统一阶段结果引用的类型，
   前提是保留每个阶段的可见字段、粒度与依赖关系；本次仅统一排序策略和 NULL 渲染。
4. JOIN 裁剪、派生表消除和绑定缓存需要独立性能证据。INNER JOIN 可能过滤数据，表达式
   身份依赖模型 generation，因此不能根据“没有出现在 SELECT”或字段同名就做优化。

这些属于后续优化方向，不作为本次性能收益，也不在没有专项验证时扩大修改范围。

## 最终验证（2026-10-20）

最终专项与五数据库回归全部通过后，才启动下列全量测试。最终证据统一放在
`target/order-binding/`（本地忽略目录）；`final-summary.json` 汇总 17 条 Maven 验证线路、
前端/浏览器结果、47 个现有 IT 类覆盖、Launcher SHA-256 以及源码一致性。
各线路存在重复覆盖，不能将执行次数相加当成独立测试数。

| 验证范围 | 最终结果 |
|---|---|
| 专项单元 / 集成回归 | 54 / 92 项，合计 146 项，零失败、错误、跳过 |
| Windows 完整 Maven reactor（runtime-api） | 5,856 项，零失败/错误，11 项平台条件跳过 |
| Linux 完整 Maven reactor，同一源码快照 | 5,856 项，零失败/错误/跳过，覆盖上述平台条件用例 |
| 常规集成：SQLite、Caffeine、预聚合 | 390 项，零失败/错误/跳过 |
| Redis 7 / MongoDB 6 | 3 / 30 项，零失败/错误/跳过 |
| MySQL MCP / AI 直接工具调用 / ComposeScript | 14 / 7 / 2 项，零失败/错误/跳过 |
| Milvus 2.4 向量模型与存储 | 20 项，零失败/错误/跳过 |
| SQLite / SQL Server 2022 / MySQL 5.7 | 各 107 项，零失败/错误/跳过 |
| MySQL 8 / PostgreSQL 15 | 162 / 172 项，零失败/错误/跳过 |
| MySQL 5.7 预聚合生命周期 | 3 项，零失败/错误/跳过 |
| Viewer / Runtime Console / Analytics Console 单元测试 | 521 / 38 / 12 项，合计 571 项通过 |
| Runtime Console / Viewer 真实后端 / HTTP 认证浏览器测试 | 20 / 5 / 1 项，合计 26 项通过，无失败、跳过或重试后才成功的用例 |
| Viewer 构建、类型兼容性、包校验 | 通过 |
| current-main 合同 / git diff --check | 通过 |
| 原始 Chinook，使用本轮完整构建的 Launcher | 119 个 artist，与独立 SQLite SQL 的顺序一致；降序分页、total、validate/execute/explain 均通过 |

Chinook 原数据库 SHA-256 不变；普通查询对照保留 QM 的隐式主键默认排序，
SQL Server 实际执行验证重复项已经消除。真实 NULL 数据覆盖 FIRST/LAST 与三种查询模式，
并检查分页和 total；不是仅匹配 SQL 文本。

源码快照包含 5,292 个文件。Linux 执行目录逐文件与快照原始字节匹配；Windows 工作区
按快照相同的换行规则比较，生产代码和测试代码一致，测试后只更新文档。
证据分别为 `native-source-verification.json` 和 `source-verification.json`。
最终 JAR 也用于 Viewer 真实 MySQL 和 Chinook HTTP 验证。

执行环境说明：Linux 使用本地文件系统运行文件监控用例，避免 Windows bind mount
事件语义差异；首次快照时间戳导致的 Maven 资源复制问题已在最终快照修正，随后完整重跑。
数据库 harness 沿用 #145 已记录的 MySQL 启动参数修正，不改变夹具或测试断言。
在线 LLM 推理未启用，`AiToolsIT` 覆盖配置、题库、报告与直接工具分支；没有声称验证在线模型。
`chart-render-service` 没有现存自动化测试文件，不将其计作已运行套件。

最终变更位于既有 `main` 工作区 `D:/workspace/foggy-data-mcp/releases/engine-9.3.0`，
基线 `db5db39aa36dc30b51d9db7d941e333c8d39e0d2`；未提交、推送或发布。
本次创建的数据库、向量服务、测试 Runner 和本地 Launcher 已清理，未触碰其他运行服务。

## 发布授权（2026-10-20）

用户在完整验证后明确授权提交并推送 main、更新并关闭 #145、重新发布 Launcher、发布私有 Maven。
Launcher 使用下一个补丁版本 0.1.26，沿用既有 prerelease 渠道；私有库保持 9.3.0-SNAPSHOT，
按 distributionManagement 发布新的时间戳快照，排除 Launcher 与 coverage-report。
Launcher 发布执行标准 packager、运行时 smoke 和 GitHub 六资产摘要校验。
最终外部发布结果以 GitHub Release、issue 修复说明和后续发布记录为准。
