---
doc_type: implementation-plan
status: PUBLISHED
date: 2026-10-21
intended_for: engine maintainers
purpose: 统一计算、聚合及 pivot 字段绑定与权限依赖，修复八类已复现问题并验证兼容性。
---

# 字段绑定与权限依赖一致性

基线 main `6d0d59e3`。前序 34 个 SQLite 探针确认八类问题，原始证据保存在
`target/name-alias-followup-audit/`。用户授权直接在 main 实施、专项回归后执行全量和多数据库验证。
初始实施不包含提交与发布；2026-10-21 用户追加授权提交推送、独立 issue 追踪、Launcher 与私有 Maven 发布，并在交付后推进架构优化。
追踪：[issue #147](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/147)。

## 合同与实施方案

1. 保持 `ref` 来源身份、`name` 查询标识、`alias` 输出键三个独立概念。共享内部绑定辅助逻辑，
   从实际 QM 列获取输出名与聚合定义；不向公共 Model SPI v2 增加接口，不以字符串替换猜测来源。
2. 先建立计算字段完整依赖闭包，覆盖 expression、partitionBy、windowOrderBy 和传递计算字段。
   查询权限、物理列映射、元数据可见性使用同一依赖规则。解析失败和循环依赖明确拒绝。
3. 请求内已选投影的 ORDER BY 输出别名追溯来源；只在已有允许输出别名的阶段解析。
   不将 alias 扩展为 columns/slice/groupBy 的通用同义词，不因别名绕过来源隐藏或黑名单。
4. pivot 的内存处理统一使用逻辑字段键。在 SQL/普通查询返回边界按实际绑定转换输出键，
   SQL 下推同样显式处理投影别名；保持 pivot 既有轴名、metric 名及 tree/grid/flat 格式。
   聚合方式优先取实际 QM 列，保留适用的 TM 兼容路径，不能把 AVG/COUNT_DISTINCT 改成 SUM。
5. 度量元数据以实际可查询列为准，避免公开已失效旧名；同时声明原名与新名时保留两者。
   结果 schema 的列名对应实际输出键，必要时附加查询名，查询模型元数据的 fieldName 仍为查询标识。
6. QM formula 支持独立输出 alias，未指定时仍返回 name；显式请求 AS 覆盖该次输出。
   预定义计算字段按依赖闭包注入，允许只选择末级公式，依赖列不自动成为最终输出列。
   保持重名冲突诊断，不允许别名覆盖已有字段或不同阶段表达式。

## 影响面与兼容性

| 范围 | 改变与风险 | 兼容约束与验证 |
|---|---|---|
| 窗口权限 | 原来漏校验的排序/分区字段现在会拒绝；属于权限收紧 | fieldAccess、deniedColumns、TM/QM 隐藏、传递窗口依赖；普通授权查询不变 |
| pivot | 修复 null 单元格、空轴合并、改名度量总计拒绝及下推查名 | 同一原生 SQL oracle 比对 grid/tree/flat、TopN、总计与非可加度量；保留逻辑轴名 |
| 计算字段 | 旧配置中被忽略的显式 alias 开始生效 | 未配置 alias 的返回键不变；记录迁移提醒；name 与 alias 分离，不增加输入同义名 |
| 结果 schema | 原来与 items 不匹配的 name 改为输出键 | 增补查询名追溯；同名默认路径、DSL AS、元数据 caption 不变；前端消费验证 |
| 元数据 | 去除不可查询的 TM 度量旧名 | 原字段与改名投影并存、JSON/单多模型 Markdown、权限裁剪与字段筛选 |
| 聚合排序权限 | 正确授权来源字段后允许按已选输出别名排序 | 未选别名、冲突、受限来源仍拒绝；保留 #145 DISTINCT 与 SQL Server 去重行为 |
| 依赖注入 | 单独选择末级公式时补齐前级依赖 | 无循环、完整拓扑、不可变请求集合、默认选择、聚合/窗口阶段、输出不泄漏依赖列 |
| 其他调用方 | Compose、Runtime API、MCP、Viewer 共享引擎路径 | 不改 namespace/refresh/catalog/缓存身份合同；运行既有集成及客户端测试 |

部署时刷新受影响模型并失效旧查询结果缓存。显式配置 formula alias 的调用方需改用 alias
读取结果；查询仍使用 name。历史忽略配置不视为可靠的别名合同，但必须清楚记录行为变化。

## 验证顺序

- 把探针转换为断言型真实 SQLite 回归，先记录失败，再实现优先权限与 pivot 修复。
- 每类问题覆盖正常、拒绝/错误和兼容对照；补 owner、alias 冲突、窗口传递依赖与聚合类型。
- 专项覆盖新测试、#146、#145、计算字段、权限、元数据、pivot、Compose 及 Runtime 边界。
- 专项通过后执行当前主线完整 Maven reactor、现有 IT、前端单元/类型/构建和浏览器验证；
  Windows 平台跳过在 Linux 补齐。SQLite、MySQL 5.7/8、PostgreSQL、SQL Server 执行受影响矩阵，
  仅对数据库自身不支持的窗口能力明确区分拒绝合同与成功执行。
- 新证据放在 `target/field-binding-dependencies-*`，不用 clean 删除历史证据；隔离临时数据库，
  不改用户既有服务。在线付费 LLM 分支不启用，环境重试和历史失败分别记录。

## 实现与兼容性复核

- `CalculatedFieldDependencies` 统一表达式、窗口分区/排序及传递依赖；权限、物理列映射、
  元数据可见性和预定义公式注入复用该规则。既有 result-stage grammar（ratio_to_total、rank_by、
  受支持的 OVER 形式）沿用原规范化器，不能因提前收集依赖而拒绝既有合法模型。
- `QueryFieldBindings` 负责查询标识到输出键的投影边界。pivot 主数据、domain、属性查找、
  层级 skeleton、非可加总计和 SQL 下推均使用该映射；不引入全局别名同义词。
- QM 度量及聚合元数据以实际绑定为准；保留原名和改名投影并存的配置。
- `CalculatedFieldDef.alias` 省略时不序列化，保留原 13 参数 Java 构造函数。
  SQL、Compose 和 MongoDB 适配均传递输出别名；MongoDB 内部计算仍按 name 引用。
- 请求 `calc AS output` 追溯原公式的聚合阶段，单独选择公式仍返回相同数值与行数。
  不同已选字段若争用输出键，明确报 `COLUMN_OUTPUT_ALIAS_COLLISION`，避免 Map 静默覆盖。
- `schema.columns[].queryName` 是可选新增属性。使用封闭 JSON schema、拒绝未知字段的客户端，
  需在升级时允许此属性；普通默认 name=alias 的查询不会新增该属性。

统一逻辑位于现有表达式分析和查询投影边界，未另建查询执行管线，未改变公共 Model SPI v2。
本轮没有调整 namespace、catalog、原子刷新、缓存身份、权限来源或数据库方言选择流程。

## 回归与测试记录

新增 20 项字段绑定集成型回归、4 项依赖规则单测、1 项层级轴回归和 1 项 MongoDB IT。
真实 SQLite 的首轮 11 项测试产生 7 个 failure、3 个 error，证明测试能够暴露既有问题；
保留原始失败报告，不以最终绿灯覆盖初始证据。

| 已完成验证 | 结果 |
|---|---|
| Windows 专项回归 | 266 项通过；补充层级/绑定专项 32 项通过 |
| Linux 最终专项 | 278 项，0 失败、0 错误、0 跳过 |
| Windows 完整 reactor verify（含 Runtime API/Console） | 5907 项，0 失败、0 错误，11 项平台条件跳过 |
| Linux 完整 reactor test（同一源码快照） | 5907 项，0 失败、0 错误、0 跳过，补齐上述 11 项 |
| MySQL 5.7 | 109 项通过，0 跳过 |
| MySQL 8 | 139 项通过，0 跳过 |
| PostgreSQL 15 | 139 项通过，0 跳过 |
| SQL Server 2022 | 109 项通过，0 跳过 |
| 前端单元测试 | Viewer 521 + Analytics Console 12 + Runtime Console 38 = 571 项通过 |
| 浏览器 | Runtime Console 20 + HTTP 鉴权 1 + Viewer 5 = 26 项通过，无 flaky/跳过 |
| 前端产物 | 类型、库构建、验证应用构建通过；41 个产物与 Linux 使用的文件哈希一致 |
| 通用 IT | 410 项通过，0 跳过 |
| Redis / MongoDB / MCP / 向量库 | 分别 3 / 31 / 23 / 20 项通过，0 跳过 |
| MySQL 5.7 预聚合专项 | 3 项通过，0 跳过 |
| IT 清单核对 | 47 / 47 个现有 IT 类均有最终执行证据，无遗漏 |
| 发布脚本与主线合同 | Python 36 项通过；current-main 合同检查通过 |

验证中的纠正与重试均保留：

1. 全量首次发现 Odoo 的既有后聚合公式被依赖分析提前拒绝，修复为沿用既有严格语法规范化，
   并加入专项回归；最终 Windows/Linux 全量均通过。
2. 跨数据库数值比较统一为数值语义比较，行比较按轴键定位，不依赖无 ORDER BY 的返回顺序。
3. Windows 并发加载测试曾因 5 秒内最后一个 waiter 未就绪而失败；原断言和超时不变，
   独立 4 项复测通过，随后串行完整验证通过。
4. Viewer 曾在高并行环境下出现 1 项超时；保持原断言和超时，限制 worker 后 521 项通过。
   曾出现 worker 配置错误导致 0 测试的运行，明确不计为通过证据。
5. 真实后端分页用例首次因夹具只有 12 组而失败。建立独立 `binding_viewer` 数据库、
   补齐跨月分组后，原分页/过滤/导出断言全部通过；未改变数据库矩阵的 `foggy_test` 数据。

Linux 前端不重复安装构建，使用已在 Windows 验证且 SHA-256 一致的产物。MCP 的在线付费
LLM 分支未启用。当前主线测试清单是本次门禁，历史版本冻结的测试脚本不冒充当前主线全量。

证据目录：`target/field-binding-dependencies-regression/`（初始失败与迭代）、
`target/field-binding-dependencies-full/`（最终分组 XML/JSON、日志、命令、源码清单、浏览器截图）。
目录内运行配置可能包含本轮临时服务凭据，不应作为公开附件直接发布。

## 最终验收

2026-10-21 完成全部 15 个后端验证组和前端、浏览器、Python 门禁。自动验收同时断言
退出码、非零测试数量、失败/错误/跳过数、47 类 IT 覆盖清单、源码与产物哈希。
`target/field-binding-dependencies-full/final-summary.json` 的最终状态为 `passed`。

改动直接位于 main 工作树；全量验收完成后进入用户授权的提交与发布流程。测试服务仅使用带本轮归属标记的
临时容器，验收后清理容器与网络，保留证据及既有服务，清理记录见同目录 `cleanup.json`。

## 发布交付

修复提交 `5c4f0e01` 已推送 main，Launcher 0.1.28 与私有 Maven 已发布并核验。
详见[发布记录](../../../release/launcher-0.1.28-publication.md)。后续结构优化独立记录。
