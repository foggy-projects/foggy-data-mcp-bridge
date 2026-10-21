---
doc_type: workitem
intended_for: maintainers
purpose: Track ordered consumer verification, release gates, Compose boundary audit, architecture planning and capability evaluation after issues 150/151.
---

# Compose 后续五步推进

状态：VERIFIED。基线 main `aecfdb94`。用户授权按顺序推进 1—5；1—3 已实施并验证，4—5 已交付方案。

1. 消费端：发布的 Launcher 0.1.32 / CLI 0.1.25 重放 #150/#151 原始 Chinook 模型及脚本，对照实际 SQLite 行集；核对 Query 宿主解析的依赖和自动配置装配。使用独立测试实例，不改变已运行业务环境。
2. 发布门禁：把 Compose 制品检查纳入正式发布前置，缺失报告、错误摘要、失败或缺失场景均阻止发布；覆盖门禁正反测试。
3. 组合边界审计：嵌套 JOIN/UNION、derived、subquery、复用计划的输出字段、权限依赖、分页层次。先记录复现及影响面，再修复已证实的问题并做定向/必要多库回归。
4. 统一规划方案：明确实际输出 schema、字段来源、权限依赖的唯一归属，评估消除声明 schema 回退和重复推导的迁移路径，保留 name/alias 合同。
5. 能力扩展评估：分别形成 SQL Server 窗口结果阶段 CTE 提升和当前版本发行验收合同的决策/实施方案；不改 v950 冻结合同，不未经完整验收宣称扩展支持。

证据目录 `target/compose-followup/`。不运行根 clean，保留既有证据；无新发布动作。

## 1. 消费端验收完成

原 issue 的四个模型和脚本、Chinook v1.4.5 数据摘要均保存。0.1.32 / CLI 0.1.25：
输入 limit=5 返回 ID 1—5；无输入分页 59 行、47 个匹配客户、金额 477.53；输入第二页与最终第二页
均逐行匹配 SQLite 基线。无效 JOIN 仅 validate/preview，未执行。第一次比较因 JSON 省略空字段
产生夹具 KeyError，统一将省略和 null 视为 SQL NULL 后通过，保留原失败证据。

Query 宿主解析 228 项 classpath，Engine/Viewer/Runtime API 摘要与本轮发布完全一致。
宿主自身 5 项 Runtime/Analytics 装配测试通过；发布 JAR 的两个 Bean 上下文及真实成员查询通过。
这是依赖、宿主装配切片和引擎上下文验收，不代表带业务数据库/Nacos 的整应用部署验收；
未修改 TMS 源码，未启停业务服务或客户环境。

## 2. 发布门禁完成

初始发布前 Compose 制品门禁验证 14 个具名场景、validate/preview/execute、JVM 退出与 JAR 摘要。
包括新补的 limit=0 原字段/AS 字段权限拒绝及允许字段对照。16 项 publisher 单元测试通过，
实际下载的 Launcher 0.1.32 经新增门禁全部通过。报告保留在独立目录，发行资产不改变。

## 3. 已复现的组合边界问题与修复方案

新增真实 SQLite 回归首轮 31 项中 2 失败/5 错误（重复 G10 参数场景），证据在 `step3/red-retry/`：

- 右侧嵌套 `a UNION (b UNION ALL c)` 被平铺成左结合 SQL，预期 `[1,2]` 实际 `[1,1,2]`，属于数据错误。
- UNION 两侧只有 ORDER BY、无 limit/start 时没有分支封装，validate/preview 通过但 SQLite 执行拒绝。
- 默认 G10=false 路径允许两侧列数不同的 UNION 通过 validate；应与 G10 路径同样提前拒绝。
- `plan.query({limit:...})` 的 SQL 为透传 SELECT *，schema 却为空；后续 G10 派生字段及 IN 子查询无法解析。

方案：UNION 每个操作数保持独立关系边界，保留 AST 分组、排序及分页；两侧实际输出列数共用
SchemaDerivation 的检查，在生成 UNION SQL 前强制执行；省略 columns 的 derived schema 继承输入。
影响仅限组合关系的 SQL/schema。UNION 仍按位置对齐并采用左侧输出名，不改变合法异名分支、
UNION ALL 重复行、权限绑定或参数顺序。此前漏检的无效请求提前失败，错误结果按声明的集合语义纠正。
扩展检查嵌套 JOIN、共享计划、G10 两种状态和权限拒绝，随后执行 Compose 回归及五数据库。
额外派生表封装可能改变数据库优化计划；本轮证明结果与权限正确性，未宣称性能改善。

扩展回归另外确认四项：仅 start 的派生查询在 SQLite/MySQL 缺少 LIMIT；G10 嵌套 JOIN 覆盖
基础 provenance 导致合法字段被误拒绝；SQL Server 无界派生 ORDER BY 被嵌套时非法；SQL Server
start + limit=0 生成非法 FETCH NEXT 0。前两项见 `green-first/`，SQL Server 实库五个错误用例
见 `sqlserver-red/`（含不同 G10 参数）。共八类原因，未发现可据此证明的权限绕过。

修复集中在现有 SchemaDerivation / ComposePlanner / ComposePaginationRenderer 边界：保留已绑定
来源及歧义、正确渲染方言分页，不注册拥有全权限的虚构中间模型。既有未知来源仍 fail closed。
G10 开/关、允许字段和被禁 amount 均覆盖。整个 Compose 包 1,170 项通过、零跳过，见
`step3/compose-green/`。发布制品门禁同时扩展为 19 个具名检查；最终制品验证与多库结果见后续记录。

## 4—5. 规划与独立评估完成

见 [统一绑定与后续能力方案](DESIGN-compose-bound-relations-and-release-contract.md)。保持现有引擎
边界，以编译期 BoundRelation/OutputSlot 消除多处推导，先双轨验证再分阶段切换；当前未引入新的公共 SPI。
SQL Server 根级 CTE DAG 提升需要独立参数/权限/窗口验收，本次不直接打开支持开关。

当前发行合同检查另外记录了既有 CLI/Launcher/skill 版本偏差；没有为了使校验变绿而修改
current-main.json、CLI 默认 pin 或冻结 v950。新发行清单需要区分源码、迭代、坐标、兼容基线与发布制品，
并由下载回读与消费者装配证据支持。第 4、5 步交付的是方案和影响面，尚未实施能力扩展或发行签收。

## 最终验证（2026-10-21）

| 范围 | 结果 | 本地证据（相对于 target/compose-followup） |
|---|---|---|
| 发布 0.1.32 原始 Chinook 重放 | 4 个正向场景匹配独立 SQL；无效 JOIN 未 execute | step1/chinook-retry/summary.json |
| Query 宿主装配切片 / 发布 JAR Bean 上下文 | 5 / 2 项通过；依赖摘要匹配已发布 Maven | step1/query-host-tests.log、published-bean-context.log |
| Compose 包 | 1,170 项，零失败/错误/跳过 | step3/compose-green/summary.json |
| SQLite / MySQL 5.7 / MySQL 8 / PostgreSQL / SQL Server | 423 / 232 / 262 / 262 / 232，共 1,411 项，零失败/错误/跳过 | matrix-summary.json |
| Windows 全量 reactor | 6,036 项，零失败/错误，11 项平台跳过 | step3/full-windows/summary.json |
| Linux 全量 reactor | 6,036 项，零失败/错误/跳过；覆盖 Windows 全部 11 项 | step3/full-linux/summary.json、platform-skip-coverage.json |
| 发布脚本 Python | Linux 41 项通过，其中 publisher 16 项也在 Windows 通过 | release-python-linux.log、step2/publisher-final-tests.log |
| 新构建 Launcher HTTP | 19 个具名检查及三模式通过，进程退出 | step3/artifact-compose/summary.json |

全量 reactor 使用 `verify -DskipITs=true -Pruntime-api,runtime-console`，两项 frontend build skip
使用现存 Console 静态资源；本轮没有前端源码变化。表中多库包含选定真实数据库 IT，
不是重跑上轮全部 47 类外部服务 IT，也不是客户环境/Nacos 整应用部署签收。
Linux 独立源码快照只对 75 个 shell 文件规范化换行，主目录不变；实现文件摘要与测试快照一致。

新本地 Launcher SHA-256：`8e248d04ae394b65c0f684d38301c9606116ed97a7c305f894cd1b93f4d48b95`。
内嵌 Engine 摘要与本轮 reactor JAR 相同，并确认 DbModelAutoConfiguration 仍包含 MemberBindingService
注册引用，见 `step3/artifact-identity.json`。本轮没有上传 Launcher/Maven；已发布 0.1.32 不含此次新增八类修复。

汇总：`target/compose-followup/FINAL-SUMMARY.json`。初始失败、修复前红灯及最终通过证据均保留。
既有 compose-150-151-fix、compose-150-151-audit、v950-contract-scope-fix 证据目录保留；未执行根 clean。
所有临时数据库和 Linux runner 均已退出，未修改原有业务容器。
