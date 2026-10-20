---
doc_type: implementation-plan
status: PUBLISHED
date: 2026-10-20
intended_for: engine maintainers
purpose: 明确 QM name/alias 合同，修复维度列注册和语义元数据不一致，并记录兼容性与验证。
---

# #146 QM 查询名称与输出别名

目标迭代 9.5.5，直接在 main 实施；Java 坐标保持 9.3.0-SNAPSHOT。
初始实施授权包括实现、专项回归和全量测试；验证完成后，用户另行授权提交、推送、
更新并关闭 issue，以及发布 Launcher 和私有 Maven。发布结果见文末。

## 根因与已有测试的边界

`createQueryColumn` 已保存普通属性的 `name` 与 `alias`，所以原有 QM 属性别名、TM 维度 alias
和输出别名排序确实可以使用。问题发生在具体维度列进入 `addJdbcQueryColumn` 后：
旧分支重新构造维度 ID/caption 列并提前返回，任意业务名称没有完整注册；
此前针对 `$caption` 后缀的别名配对也不能覆盖任意新名称。

元数据另有独立缺口：`SemanticServiceV3Impl` 从 TM 的 effectiveName 生成维度字段名，
普通属性改名后也可能继续推荐已无法选择的原名称。因此只修加载或只修 describe 都不完整。
新增内部来源标记保留实际列身份，注册、元数据与权限均沿此身份处理覆盖名称。
已有 NestedDimensionTest 和 DistinctOrderByRegressionIT 测试的是不同别名作用域，
不能替代具体维度列 name/alias 与 JSON/Markdown 一致性的回归；该缺口早于 #145。

## 使用合同

- `ref` 绑定来源列，保留 owner、维度角色、类型、表达式及物理权限来源。
- 具体列 `name` 为 QM 查询标识，用于 columns/slice/groupBy/orderBy。未设置时沿用现有 ref 派生名称。
- `alias` 为 SQL 输出别名及返回键；未设置时沿用现有 ref 派生输出名，并不自动跟随 name。
- `name != alias` 合法。选择用 name；已选列 orderBy 可用 alias，保留 #145 首项排序和去重。
  本次不把 alias 新增为任意 columns/slice/groupBy 的同义词，不改变既有 condition 定义。
- 元数据公开可查询 name，另列输出名，避免把输出键当成输入标识；无覆盖的短维度名保持兼容。
- TM 的 dimension.alias、表实例 alias、QM 的列 alias、DSL 的 AS 分属不同作用域。
- 整维度 ref 的 name/alias 不新增前缀语义：保留现有展开行为，加载时对被忽略的覆盖值给出警告，
  文档指引显式配置具体 `$id`/`$caption` 列；不让以前可加载的模型突然失败。

## 实施与影响面

1. 注册具体维度列时保留 QM 名称和输出别名，不修改共享 TM。默认维度展开、日期根、层级视角、
   synthetic member-QM、点/下划线/TM alias 兼容入口保留；明确名称冲突必须拒绝。
2. 引入引擎内部的字段命名绑定辅助，JSON、单模型 Markdown、多模型 Markdown 共同使用实际 QM 列，
   保留维度语义和来源。修复普通属性自定义 name 的 describe 不一致；不重写公共 SPI 或请求 DTO。
3. 自定义字段保持底层 owner 和权限约束；覆盖 TM/QM fieldPermissions、runtime fieldAccess、deniedColumns。
4. 默认无覆盖模型输出不变；新覆盖模型的输出键按配置生效，是修复原先忽略配置的行为。
   旧维度路径继续查询。重命名的普通属性保持现有 name-only 查找行为。
5. 分组/自动分组/排序按来源绑定而不是字符串后缀推断维度角色。新声明的具体 caption 命名投影
   筛选其来源 caption；默认路径及显式 condition 保持既有行为。显式 columns 不补入未请求列、
   不改变聚合粒度和查询生命周期。
6. 物理列映射同时登记新查询名和输出名，来自真实 SELECT/condition 列及其 owner，
   防止新名称遗漏物理权限检查。普通属性只有在原名称不再声明时才从元数据中隐藏；
   原属性与改名投影并存时，两者均公开。
7. 不放宽 TM 自身的重复维度 name 校验。TM 加载阶段已拒绝的重复定义，不能由 QM alias
   补救；owner 消歧回归使用两条有效嵌套路径，以及同一 TM 的两个 self-join 实例。

## 验证顺序

- 修改前：8 个发布版 0.1.26 独立对照模型；现有 NestedDimensionTest 11 项及
  DistinctOrderByRegressionIT 33 项通过，说明原有别名覆盖不能替代具体维度列覆盖。
- 先补实际 TM/QM 加载、SQLite 执行和 JSON/Markdown 的回归，记录 red，再实现修复。
- 专项覆盖 name-only/alias-only/name=alias/name!=alias，ID/caption、嵌套维度双路径、同 TM 多实例、普通属性、
  columns/slice/groupBy/orderBy、输出键、validate/execute/explain、权限、旧路径和原有别名测试。
- 专项通过后跑完整 Maven reactor、已有 IT（含外部依赖）、前端单元与浏览器套件；
  SQL Server、MySQL 5.7/8、PostgreSQL、SQLite 执行受影响的回归矩阵。Windows 条件跳过在 Linux 补跑。
- 不用 clean 清除旧证据；新证据使用 target/issue146-* 并保留运行命令、状态、XML 和源码快照。
  在线付费 LLM 分支不启用；任何未运行或失败项明确记录，不把部分结果称为全量通过。

## 验证结果（2026-10-20）

先完成专项回归，再执行全量测试。最终代码包含 26 项新增回归，覆盖原属性与改名投影并存、
多模型各自的输出键、嵌套路径、self-join、ID/caption 筛选、元数据及来源权限。

| 验证范围 | 最终结果 |
|---|---|
| 专项回归（含新增 26 项） | 177 项，零失败、错误、跳过 |
| Windows 完整 Maven reactor，runtime-api/runtime-console 装配 | 5,882 项，零失败、错误；11 项平台条件跳过 |
| Linux 全量及环境补齐后的尾部补跑 | 按测试类合并 5,882 项，零失败、错误、跳过；覆盖 Windows 跳过项 |
| 常规集成，含 SQLite 预检、原生 SQL 对照、多数据库通用用例 | 410 项，零失败、错误、跳过 |
| MySQL 5.7 / SQL Server 2022 扩展回归 | 各 89 项，零失败、错误、跳过 |
| MySQL 8 / PostgreSQL 15 扩展回归，含 Pivot 专用检查 | 各 119 项，零失败、错误、跳过 |
| Redis 7 / MongoDB 6 / Milvus 2.4 | 3 / 30 / 20 项，零失败、错误、跳过 |
| MySQL MCP、AI 直接工具调用、ComposeScript | 14 + 7 + 2 = 23 项，零失败、错误、跳过 |
| MySQL 5.7 预聚合生命周期 | 3 项，零失败、错误、跳过 |
| Viewer / Runtime Console / Analytics Console 单元测试 | 521 / 38 / 12 项，共 571 项通过 |
| Runtime Console / Viewer 真实后端 / HTTP 认证浏览器测试 | 20 / 5 / 1 项，共 26 项通过，无跳过或 flaky |
| Viewer 构建、类型兼容及包校验；Console 类型检查与构建 | 通过 |
| 发布脚本 Python 测试 | Linux 36 项通过 |
| current-main 合同与 git diff --check | 通过 |

47 个现有 IT 类均有最终证据；`AiToolsIT` 只运行配置、题库、报告及直接工具分支，
未启用在线 LLM。`chart-render-service` 没有现存自动化测试，不计入已运行套件。
上述线路存在重复覆盖，不将执行次数相加当作独立测试总数。

原始 Chinook TM/QM/DSL 使用本轮构建的 Runtime Launcher 验证：
JSON/Markdown 公开新名称，validate/execute/explain 均成功，返回
`albumArtistId=90, albumArtistName=Iron Maiden, lineCount=34, totalAmount=33.66`，
与独立 SQLite SQL 一致。同一 QM 改用旧 `artist$id/artist$caption` 后仍返回旧键及相同数据。
该 JAR 也通过 Viewer 真实后端浏览器检查。

本地 Launcher SHA-256：
`63f604fb29d860b52d43caad9e314505cf351329ab44fcad862f22a4fb1f5b66`。
这是发布前的测试构建；当时尚未提交、推送、更新 issue 或发布新版本。

### 证据与环境重试

证据位于 `target/issue146-tests/regression-complete/` 和 `target/issue146-full/`；
最终汇总为 `target/issue146-full/final-summary.json`。保存了命令、退出状态、XML、
数据库初始化脚本、浏览器 JSON、源码清单及原始失败报告，没有使用 clean 删除证据。
5,298 个文件的源码快照与 Windows、两个 Linux 执行目录的生产及测试代码一致；
之后只更新文档。源码校验见 `source-verification.json`、`native-source-verification.json`。

- Linux 首次快照时间为 1970 年，导致 Maven 漏复制资源；修正文件时间后重跑。
  并发测试向 Windows 挂载目录输出大量日志时超时，修复前 main 也复现；
  改为容器本地落盘后，基线 4 项及最终完整引擎测试均通过，没有修改超时或断言。
- 干净 Linux 快照跳过前端构建，曾使 Console 静态页面冒烟返回 404。
  补入本轮 Windows 构建的前端资源并逐文件校验哈希后，重跑该类及尾部模块，共 69 项通过。
  `linux-unit-aggregate/summary.json` 按类替换重跑结果，与 Windows 的 884 个报告类集合一致；
  不是把首轮失败报告算成通过。
- 初始 IT 启动配置漏传缓存提供者、预聚合路径；数据库扩展检查初始缺少仓库的
  `14-v934-preagg-fixture.sql`。补齐配置/夹具后相关线路通过。SQLite 专用 B600 测试保留在
  SQLite 专项中，不用它验证外部数据库的数据集；四库均执行新增 26 项命名回归。
- 额外扫描历史 `scripts/v950/tests`：Linux 13 项通过、1 项因冻结的 31 模块合同与当前
  43 模块 reactor 不符而报错；修复前 `92b26c47` 对照相同。未改写历史签收合同，
  当前主线 `scripts/current-main/verify_current_main.py` 已通过。该历史检查不计作当前门禁通过。

## 发布结果（2026-10-20）

修复已提交并推送 main（`40309be1`），#146 已更新并关闭。Launcher 0.1.27 与私有
9.3.0-SNAPSHOT 已发布且下载核验通过；详见[发布记录](../../../release/launcher-0.1.27-publication.md)。
