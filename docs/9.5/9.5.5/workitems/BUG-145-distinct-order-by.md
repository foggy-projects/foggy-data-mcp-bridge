---
doc_type: implementation-plan
intended_for: maintainer / reviewer
purpose: 修复 Issue 145 的 DISTINCT 排序丢失，记录影响面、兼容边界及回归和全量测试证据。
status: LOCALLY_VERIFIED
date: 2026-10-20
---

# #145 DISTINCT 排序

目标：在当前 main（9.3.0-SNAPSHOT，治理迭代 9.5.5）直接修复；不发布发行包。

## 原因与方案

SELECT 使用 QM 投影列，ORDER BY 使用条件解析得到的 TM 列。嵌套维度的公开
`artist$id` 与底层 `track_album_artist$id` 指向相同列，但 `containSelect` 只比较
alias，DISTINCT 清理错误地删除排序。

仅在实际生成 SELECT DISTINCT 的非 GROUP BY 分支引入投影排序解析：

1. 显式排序优先按公开 SELECT 字段解析，避免 caption 条件解析误用 ID。
2. 只展开纯 QM 投影包装，以底层列对象身份匹配；不按列名、别名或去除表绑定后的
   root 猜测同一列。保留计算表达式和多表实例的语义边界。
3. 将排序绑定到选中的投影；保留方向及 null 顺序，不扩充 SELECT。
4. 显式请求未投影字段时抛出 `DISTINCT_ORDER_BY_NOT_SELECTED`，validate / execute /
   explain 均须拒绝。QM 默认排序及脚本预置排序仅保留投影内的项。
5. 创建请求专属排序对象，不修改缓存的 QM 排序定义。
6. QM 默认排序若已被同一投影上的显式排序覆盖，不再重复追加，保留显式方向；
   SQL Server 不允许 ORDER BY 重复列，不能仅依赖其他数据库容忍此类 SQL。

## 影响面

| 路径 | 风险 / 处理 |
|---|---|
| 普通明细查询 | 保留现有条件字段解析、非投影排序与 JOIN 逻辑 |
| GROUP BY，包括 distinct + groupBy | 保留现有聚合排序分支；实际未设置 SQL DISTINCT |
| DISTINCT 嵌套维度 / QM alias | 核心修复；短名、点路径、下划线路径应一致 |
| DISTINCT caption | 用真实投影值排序；字典 caption 禁止排序的既有约束继续执行 |
| 多 TM / 自连接 | 同名同物理列但不同表绑定不能互相匹配 |
| 计算字段 / 公式 / semantic scale | 保留表达式包装；不得降级成原始物理列 |
| 默认主键排序 | 未选主键继续忽略，避免改变去重粒度或使成员查询报错 |
| 默认排序关联表 | 即便排序未投影，也保留旧实现为 QM 默认排序建立的 JOIN，避免省去 INNER JOIN 后意外改变结果集合；单独验证该边界 |
| 同列显式排序与默认排序 | DISTINCT 中显式方向优先，忽略重复默认项；SQL Server 实际执行验证 |
| 显式非投影排序 | 有意收紧：由静默丢弃变成编译期错误；调用方应将排序字段加入 columns 或改用合法排序 |
| 分页 / total / explain | 排序先于分页；统计仍按原有 DISTINCT 投影；执行与 explain 应一致 |
| 权限 / namespace / cache / preagg | 不修改这些路径；通过现有集成用例验证兼容性 |
| 数据库 | SQLite 实值复现，MySQL 5.7/8、PostgreSQL、SQL Server 实际执行同一回归 |

## 验证顺序

1. 新回归先在未修复 main 上复现失败。
2. 修复后运行专项回归：升降序、同名不同 ID、分页/total、别名、默认排序、
   计算字段、非投影拒绝、普通查询/GROUP BY 对照；检查 SQL 和真实结果。
3. 运行完整 Maven reactor verify、常规集成测试、五数据库回归和外部服务测试。
4. 运行前端完整单元测试及仓库本地 current-main 合同检查；记录真实执行计数、
   失败、跳过和环境边界，不把默认 verify 表述成已运行所有 IT。

## 已完成的专项证据

- 未修复 main：11 项回归中 7 失败、4 通过；短别名和点路径排序、caption 排序、
  降序分页、QM 重命名、默认排序及非法显式排序拒绝均捕获原问题。无测试执行错误。
- 修复后扩展组合回归：59 项、0 失败、0 错误、0 跳过；含新增 6 项投影身份测试、
  19 项真实 SQL 回归，以及既有嵌套维度、B600 owner/self-join、semantic scale、
  synthetic member 兼容用例。
- 新回归用独立 Spring context 隔离外部 Bundle 生命周期，不继承其他用例的临时模型目录。
  权限用例遵守既有“维度整体授权”合同，同时验证字段白名单与物理列黑名单。
- 原始 Chinook v1.4.5 SQLite 数据、issue 原样 TM/QM/载荷，经 Runtime API
  validate / execute / explain 重放通过：119 个艺术家，升序前八个 ID 为 1～8；
  全部 ID 与独立 SQLite 查询一致；降序分页保留 total=119。
  使用命名数据源 `chinook-sqlite` 和 namespace `chinook`，执行与 explain SQL
  均包含 `order by d5.ArtistId asc`，SELECT 仍只含公开 ID 和 caption 两列。
  非投影显式排序三个 HTTP 入口均拒绝；explain 按既有合同隐藏内部错误详情，
  HTTP code 为 `QUERY_EXPLAIN_FAILED`，服务层保留 `DISTINCT_ORDER_BY_NOT_SELECTED`。

## 全量验证

以下为补齐 SQL Server 重复默认排序边界后的最终代码验证。
计数包含不同环境的重复覆盖，不应相加当作独立用例总数。

| 测试范围 | 结果 |
|---|---|
| Windows 完整 Maven reactor `mvn -B -ntp -q -fae -Pruntime-api verify` | 5,848 项，0 失败/错误，11 项平台条件跳过 |
| Linux 完整 Maven reactor `mvn -B -ntp -q -fae verify`，同一源码快照 | 5,848 项，0 失败/错误/跳过，补齐上述 11 项 |
| 常规 Failsafe 集成测试（SQLite、Caffeine、预聚合） | 376 项，0 失败/错误/跳过 |
| Redis 7、MongoDB 6 集成测试 | 分别 3、30 项，0 失败/错误/跳过 |
| MySQL MCP、AI 直接工具调用、ComposeScript | 分别 14 / 7 / 2 项，0 失败/错误/跳过 |
| Milvus 2.4 向量模型与向量存储 | 20 项，0 失败/错误/跳过 |
| 五数据库实际执行 | SQLite 93、MySQL 5.7 93、MySQL 8 148、PostgreSQL 15 158、SQL Server 2022 93 项，全部 0 失败/错误/跳过 |
| MySQL 5.7 预聚合生命周期 | 3 项，0 失败/错误/跳过 |
| 前端单元测试：Viewer / Runtime Console / Analytics Console | 分别 521 / 38 / 12 项，合计 571 项通过 |
| 浏览器：Runtime Console 桌面/移动端、Viewer 真实 MySQL、HTTP 认证 | 分别 20 / 5 / 1 项，合计 26 项通过，0 跳过 |
| Viewer 构建、类型兼容性、包校验 | 通过 |
| `scripts/current-main/verify_current_main.py`、`git diff --check` | 通过 |

Linux 首次在 Windows 映射目录运行时，既有文件监控用例未接收到变更事件。
迁移同一源码到 Linux 本地文件系统后，全量无跳过通过；不将首次失败计为通过。

MySQL 首轮受到 Windows bind mount 配置权限影响，`my.cnf` 被 MySQL 忽略，
导致 5.7 初始化超时、8.0 的既有日历聚合用例触发 `ONLY_FULL_GROUP_BY`。
临时测试 harness 通过启动参数恢复仓库既定 SQL mode、调整初始化等待与写盘参数，
并为 5.7 开启既有版本断言要求的 binlog；镜像 digest、数据夹具和测试断言均保留。
这些调整仅在忽略目录中，不修改仓库的生产配置，也不作为历史冻结门禁的重新签核。

执行记录覆盖仓库现有 47 个 IT 类。真实在线 LLM 推理不属于本次确定性回归：
`AiToolsIT` 运行配置、题库加载、报告和直接工具调用分支，不消耗外部 LLM 服务。
`chart-render-service` 没有现存自动化测试文件，不将其计为已执行测试套件。

SQL Server 初轮暴露了默认排序与请求排序重复的问题。本次在 DISTINCT 分支
消除重复默认项；普通查询对照用例移除自身夹具的隐式主键排序，仅验证“普通查询
允许非投影排序”的既有行为。普通查询显式主键排序与隐式主键排序重复的遗留限制
不在本次生产修改范围，未修改普通查询分支来绕过它。

原始日志和 XML 在本地 `target/issue145/`（忽略目录），专项证据为
`revised-regression/summary.json`，Windows 全量为 `revised-verify/summary.json`，
Linux 全量和集成为 `linux-evidence-final/revised-final/`，外部服务为
`linux-evidence-final/external-revised/`，五数据库为 `database-matrix/`，
Chinook 重放为 `chinook/summary.json` 及各 HTTP 响应。
`final-summary.json` 汇总最终 16 条 Maven 验证线路、前端及浏览器结果，并验证
47 个 IT 类覆盖、Launcher 校验值和源码一致性。

5,288 个源码文件经指纹比对，生产代码和测试代码与实际执行快照一致，后续仅更新
文档。变更直接位于既有 `main` 工作区
`D:/workspace/foggy-data-mcp/releases/engine-9.3.0`，基线为
`db5db39aa36dc30b51d9db7d941e333c8d39e0d2`。

## 后续收敛（2026-10-20）

以上记录保留 #145 首轮修复时的范围和证据。后续已按用户授权完成
[统一排序与字段绑定](REFACTOR-order-by-field-binding.md)：原 DISTINCT 专用绑定逻辑由
请求内通用绑定与 planner 取代，普通查询 SQL Server 重复排序的遗留限制已解决。
新增真实 NULL 排序、输出别名、自动分组及结果阶段回归，并重新完成全部测试。
后续最终结果以该 workitem 和 `target/order-binding/final-summary.json` 为准。
