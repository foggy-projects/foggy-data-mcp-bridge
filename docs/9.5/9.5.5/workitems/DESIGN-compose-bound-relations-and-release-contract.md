---
doc_type: design
intended_for: engine maintainers / release maintainers
purpose: Evaluate unified Compose binding, SQL Server CTE lowering and current-version release authority after the ordered follow-up audit.
---

# Compose 统一绑定与后续能力方案

状态：PROPOSED。这是五步推进的第 4、5 步设计结论，未宣称完成新的能力实现或版本签收。
当前已实现行为以 [引擎架构](../../../architecture/engine-field-binding-and-dependencies.md) 为准；
本轮修复与验证见 [执行工作项](OPT-compose-followup.md)。

## 4. 统一字段绑定与权限依赖

### 判断与问题归属

现有流程的边界合理：基础 QM 由受权限约束的 semantic planning 产生关系，再由 Compose 组合，
最终通过同一执行端口执行。Namespace、模型实例及授权上下文属于每次查询，不能通过共享 SQL
缓存或按模型名重用权限决定。此次错误属于关系组合丢失阶段语义和多个 schema 推导路径不一致，
不需要重写 QueryFacade、公共 Model SPI 或 Runtime API。

仍可优化的是编译产物的完整性。当前 SQL、`CteUnit.selectColumns`、声明 columns、
`SchemaDerivation` 与权限校验分别推导名字；可选基础规划元数据缺失时仍回退声明列。
`ColumnSpec.planProvenance` 只有一个来源，无法完整表达 UNION 多源和派生表达式的多个依赖。
已有 lineage/referencePolicy 属于局部语义元数据，不能直接当成完整且已授权的来源图。
G10 对未知中间计划来源仍明确拒绝；本轮保留已绑定来源不等于支持任意派生/UNION 的权限溯源。

### 建议的内部合同

在一次 compilation 内引入不可变 BoundRelation，携带有序 OutputSlot 列表、关系节点、阶段局部
过滤/聚合/窗口/分页和依赖关系。每个 slot 保存：

- 输出位置和输出键；原查询 name、真实来源 QM 列及 owner；值类型、聚合/维度角色。
- 计算表达式的直接输入 slot；传递依赖追到基础模型字段，包含窗口 partition/order、筛选、JOIN 键。
- 输入关系 handle 与基础权限来源分别保存，不能用中间 JOIN handle 冒充基础模型绑定。
- 输出名冲突/歧义和诊断路径；来源未知是显式状态，不能以空依赖解释为无权限约束。

绑定阶段一次完成解析，schema、SQL lowering、结果元数据、权限依赖均消费同一产物。
SQL 字符串和输出 alias 不作为权限来源真值。授权决定仍在每次请求基于 authority binding 求值；
deniedColumns 的物理检查仍保留，不能被新的语义依赖图取代。

| 节点 | 输出和依赖合同 | 必须保留的兼容性 |
|---|---|---|
| Base | 规划端口返回真实投影，slot 指向实际 QM 列；依赖在基础查询内授权 | name 是输入，alias 是该阶段输出；普通查询默认分页不变 |
| Derived | 无 columns 为完整透传；显式投影按输入 slot 绑定，表达式保存依赖集合 | 外层不能引用未投影源字段；允许现有合法 alias 链 |
| JOIN | 两侧关系身份与字段来源分别记录，on 只读各侧暴露的 slot | 不改变默认同名列行为；G10 歧义/限定引用策略独立迁移 |
| UNION | 按位置合并，保留两侧依赖集合，输出名取左侧 | 保留 ALL、去重、嵌套分组；不凭右侧异名拒绝合法 UNION |
| IN/subquery | 明确实际输出 slot 数量及相关绑定 | 单列自动选择、多列要求显式选择的现有规则不变 |
| 复用计划 | compilation 内按身份复用已绑定节点；分页阶段仍属于各节点 | 不因结构相同跨权限、namespace、generation 共享决定 |

### 影响面与迁移顺序

主要影响 ComposePlanner、SchemaDerivation、CteUnit、基础规划端口及 G10 权限校验；Runtime
validate/preview/execute 继续调用同一编译流程，返回 envelope 与错误码不变。普通查询、成员查询、
pivot 继续使用已有绑定机制，首期不一并替换；只在共享依赖合同已有明确兼容测试时抽取共用类型。

1. 先用现有编译数据生成 BoundRelation，双轨比较 schema/依赖，差异记为测试失败；不改变执行路径。
2. JDBC engine 内部规划端口强制提供真实投影；保留旧构造方式和适配器回退，明确适用范围。
   不把可选内部扩展升级为公共 SPI 的强制能力；无法可靠绑定的新复杂形态继续拒绝。
3. 按 Base → passthrough/简单 Derived → JOIN → UNION/复杂表达式替换重复推导。
   每一批证明行集、参数顺序、权限允许/拒绝、元数据及 G10 两种状态一致，再移除旧路径。
4. 最后评估编译期缓存收益。统计重复绑定次数、编译耗时与内存；不预设性能收益，不增加全局模型名缓存。

回归矩阵必须包含 name≠alias、显式 AS、维度/聚合/窗口、空投影透传、同名冲突、嵌套集合、复用计划、
输入/最终分页、允许字段与拒绝字段、行权限、namespace/refresh 隔离，以及五库和真实 Runtime 三模式。
不要求逐字 SQL 相同，但需要独立 SQL oracle 的有序/无序行集、重复次数、参数次序和同等拒绝行为。
风险最高的是 UNION 多源依赖、表达式闭包、同名列及权限缓存，按上述分批门禁推进。

## 5A. SQL Server 窗口结果阶段 CTE 提升

结论：可做，属于单独能力扩展。本轮不打开支持开关。SQL Server 支持根级 WITH；当前
`dialectSupportsCte=false` 实际约束的是 Compose 当前嵌套 lowering 策略。
`wrapSingleUnit` / prerequisites 路径仍以 relation-cte-hoist-unsupported 明确拒绝，测试覆盖该拒绝。
直接改布尔值会把 WITH 放进派生表/UNION 分支或使依赖、参数失序，不能作为修复。

建议先为一个基础窗口结果阶段做根级 CTE DAG 探针，再扩展多源组合：

1. 把 prerequisite CTE 和主体关系分开保存；收集根查询可达依赖，拓扑排序、检测环、分配唯一别名。
2. 按最终 SQL 文本顺序收集每个 CTE/主体的参数，不按 AST 遍历次数盲目拼接；共享节点不可丢参或重复绑定。
3. 保持先权限/基础筛选、再聚合与窗口、再结果条件与本阶段分页；JOIN/UNION 消费最终阶段输出。
4. 分离 root-CTE、nested-CTE、window-function、offset-pagination 方言能力。
   MySQL 5.7 不支持窗口函数，不能因增加根级 CTE 提升被错误开放。
5. SQL Server 实库覆盖窗口别名、分区/排序权限拒绝、结果分页、两侧窗口 JOIN、嵌套 UNION、IN 子查询、
   参数化重复计划及 validate→preview→execute；其余数据库跑等价结果回归。

通过条件是行集/排名/重复数与独立 SQL 基线一致、参数绑定一致、权限失败不执行，以及现有不支持
形态仍清晰拒绝。验证完成后才更新 capability/documentation，不能把当前分页修复算作窗口扩展验收。

## 5B. 当前版本发行验收合同

本轮只评估，不变更冻结合同、不执行发布。

已核对 `current-main.json`、`verify_current_main.py`、发行脚本与本轮消费端证据：

- releaseLine=9.5、activeIteration=9.5.5、Maven coordinate=9.3.0-SNAPSHOT 是不同维度。
- `current-main.json` 登记 CLI 0.1.22 / Launcher pin 0.1.18 / skill pin 0.1.17；本地 CLI 实际
  0.1.25 / 默认 Launcher pin 0.1.24 / skill pin 0.1.18。只读校验明确失败，证据保留在
  `target/compose-followup/current-main-contract-check.log`。这是既有跨仓版本偏差，不是本轮测试通过后的签收。
- 已发布 Launcher 0.1.32 与明确指定它的 Chinook 重放通过，不代表 CLI 默认选择也已指向 0.1.32。
  不从版本字符串相近推断依赖或能力兼容；Maven SNAPSHOT 必须定位到时间戳和摘要。
- v950 验收属于历史签收提交，固定 31 模块/32 项目。当前主干和后续发行不得复用该身份，
  不增加旧合同模块或把其严格拒绝改成通过。

建议建立一个经版本范围确认的发行清单，复用已有发布门禁，不再复制通用验收框架：

| 信息 | 真值/验收方式 |
|---|---|
| 候选源码 | bridge/CLI 各自 commit、干净状态及关联 workitem；明确 reactor profiles 和模块清单 |
| 版本维度 | 迭代号、Maven 坐标、Launcher/CLI/skill 版本分别登记；区分兼容基线与本次发行版本 |
| 制品身份 | 下载后的 JAR/包 SHA-256；Maven 时间戳版本、坐标和实际消费解析结果 |
| 必需能力 | Bean 装配、字段/权限、Compose 三模式及数据库能力清单；不支持场景有明确负向用例 |
| 测试证据 | 测试类清单、数量、退出码、跳过原因与覆盖补测；证据必须绑定同一候选及制品 |
| 发布复验 | 上传前门禁 → 下载回读摘要 → 实际制品 smoke → Query 宿主依赖/装配验证 |

实施先核定 CLI 默认 Launcher/skill 组合并做干净环境安装验收，再同步 `current-main.json` 与 CLI pin。
之后将本轮已落地的 Compose 制品门禁纳入新清单，覆盖缺项、旧报告、错摘要、篡改版本和缺失模块的
负向测试。不能简单把 JSON 中版本改到最新后宣称完成发行验收。
合同中的预期模块/能力范围应先独立确认，再与候选实际清单比较；不能从同一候选自动重写期望值，
否则新增/缺失模块的校验会失去作用。

证据使用候选唯一目录与摘要，release clean 只清理明确模块构建输出；失败证据同样保留。
历史 v950 工具、合同和验收结论保持独立，不参与本次能力扩展的通过判定。
