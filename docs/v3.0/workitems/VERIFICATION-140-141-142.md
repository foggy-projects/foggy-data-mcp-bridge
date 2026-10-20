# #140 / #141 / #142 实施与验收记录

日期：2026-10-20。基线：`f0deb244aaa585da2ed26dec0fa786475d4f5804`。当前状态：实施、最终复查及本地 current-main 验收完成。后端专项测试全部通过；完整 Maven `verify` 成功（5,842 项、0 失败、0 错误、11 项平台条件跳过）；Viewer 完整浏览器链路 5/5 通过、零跳过。

## 实施结果

- #140/#141：期间键、当前期与比较期范围分离；两侧独立应用原始 slice 和治理条件；保持原生 AVG、去重计数等聚合口径；跨年和空业务分组正确关联。
- 支持 month/mom、month/quarter/year 的 yoy、day/week 的 wow、ISO week/yoy。上一周年不存在对应第 53 周时返回 null；首批拒绝非整周期；`[]` 包含结束自然日。DATE、普通 DATETIME 和有明确日期映射的日期维度走统一解析。
- #142：依赖分析包含表达式、partitionBy、windowOrderBy；窗口生产字段和后续标量分层执行；提取复合表达式内的窗口节点；保留隐藏依赖，最终过滤先于分页和总数统计。
- 校验、直接执行和 explain 使用类型化诊断，保留原始 JSON 路径、依赖和阶段信息。无效计划在业务 JDBC 前阻断；修正建议通过“校验→执行→数值”闭环验收。
- 必须一起调整的流程已落实：固定请求模型与数据源、治理上下文传递、受管叶子准备、延迟执行与结果处理顺序、最终 SQL 校验、分页/计数、CTE 别名作用域及实际投影 schema。

## 真实跨库回归发现并修复的问题

1. SQLite DATE 绑定原来带时分秒，和纯日期文本比较会丢失起始日。DATE 保持日期绑定，DATETIME 保留时间分量。
2. JVM UTC 与 MySQL JDBC Asia/Shanghai 不同时，自然日期边界不能先变成瞬时。生成范围使用 JDBC DATE；测试以 LocalDateTime 写入源日历值。月初零点、月末最后一秒、下月零点大额哨兵均已核对。
3. MySQL/SQL Server 的聚合 DECIMAL 除法可能缩减比例小数位。比例采用显式近似数因子，金额和差额保留原生数值类型；分母为零返回 null。
4. SQL Server 不允许在派生表内声明 WITH。最终统计使用已有结构化 CTE 在顶层组装，保持过滤与参数顺序。

## 测试执行与证据

本机 Docker 可用，全部数据库使用任务专用容器和夹具，没有借用 sy-test 或共享业务数据库。Linux 测试副本和 Windows 工作区的 66 个代码/资源文件按清单校验一致；独立 Maven 缓存仅影响依赖准备，不替代测试执行。证据归档后，本次专用容器和数据卷已清理。

| 范围 | 测试数 | 失败 / 错误 | 跳过 | 结果 |
|---|---:|---:|---:|---|
| Windows 完整 Maven reactor verify（收尾最终运行） | 5842 | 0 / 0 | 11 | 通过 |
| Linux 全 Maven reactor 单测 | 5842 | 0 / 0 | 2 | 通过 |
| 常规集成测试（含 SQLite 预聚合生命周期） | 357 | 0 / 0 | 0 | 通过 |
| SQLite 数据库矩阵 | 74 | 0 / 0 | 0 | 通过 |
| MySQL 5.7 数据库矩阵 | 74 | 0 / 0 | 0 | 通过 |
| MySQL 8 数据库矩阵 | 129 | 0 / 0 | 0 | 通过 |
| PostgreSQL 15 数据库矩阵 | 139 | 0 / 0 | 0 | 通过 |
| SQL Server 2022 数据库矩阵 | 74 | 0 / 0 | 0 | 通过 |
| MySQL 5.7 预聚合生命周期 | 3 | 0 / 0 | 0 | 通过 |
| Redis 7 跨 JVM / 缓存生命周期 | 3 | 0 / 0 | 0 | 通过 |
| MongoDB 6 | 30 | 0 / 0 | 0 | 通过 |
| Milvus 2.4 / 确定性 embedding | 20 | 0 / 0 | 0 | 通过 |
| MySQL 5.7 MCP 工具 / 直接调用 / Compose | 14 / 7 / 2 | 0 / 0 | 0 | 通过 |
| Viewer / Runtime Console / Analytics Console 单测 | 521 / 38 / 12 | 0 / 0 | 0 | 通过 |
| Runtime Console 桌面和移动浏览器（接口 mock） | 20 | 0 / 0 | 0 | 通过 |
| Viewer 生产构建鉴权浏览器用例 | 1 | 0 / 0 | 0 | 通过 |
| Viewer 完整浏览器回归（含真实 MySQL 后端） | 5 | 0 / 0 | 0 | 收尾复验通过 |
| ScriptRuntimeTest（含两个源码扫描） | 26 | 0 / 0 | 0 | 收尾复验通过 |

常规 IT、数据库矩阵、预聚合 companion 和外部服务共 926 次测试执行，0 失败、0 错误、0 跳过。当前源码的 46 个 IT 入口类均有实际报告；AiToolsIT 仅选择确定性方法，可选远程 LLM 方法不在此结论内。上述计数是测试执行次数，不把跨数据库重复运行算作独立用例。

`ComparativeCalendarRegressionIT` 共 24 个测试方法，在上述五种数据库实际执行。MySQL 5.7 不支持窗口函数的用例验证明确拒绝，不能解读为 5.7 已获得窗口执行能力。SQL Server 额外改变 DATEFIRST 验证 ISO 周起点。

主要执行方式：

```text
mvn -q -fae test
mvn -pl foggy-dataset-model-engine,addons/foggy-dataset-model-cache,addons/foggy-dataset-model-preagg -am -DskipUnitTests=true -DskipITs=false -Dit.test=<当前源码中的常规 IT 清单> -Dspring.profiles.active=sqlite -Dv933.cache.provider=caffeine -Dfailsafe.failIfNoSpecifiedTests=false verify
bash scripts/v934/step3/provision-database-cell.sh run <database> <run-id> <cell-root> -- bash <数据库测试 callback>
npm test                         # Viewer
npm run test:unit                # 两个 Console
npm run test:e2e                 # 浏览器测试
```

各数据库 callback 使用对应真实 profile 和 `v934.expectedDatabase`，包含既有数据库必测类及新增数值回归；MySQL 8/PostgreSQL 额外包含既有 Pivot/DSL_CTE 用例。SQLite 使用 `sqlite,v934-sqlite`，加载必需哨兵和附加夹具。

可复核的精确命令、选择器、源文件摘要、Maven XML、日志及数据库身份记录保存在工作区 `target/issues-140-142/`；最终报告只统计各轮新生成的 XML，不累计旧报告。汇总为 `final-summary.json`，Windows 报告为 `full-unit/`，Linux 报告为 `linux-evidence/target/issues-evidence/final/`，外部服务最终报告为同级 `external/`。数据库最终运行标识为 `issues140-142-1792442091`。`pre-literal-scope`、`external-initial`、`mcp-initial` 和初次数据库证据归档均为历史诊断，不计入最终通过数。

收尾最终门禁执行 `python scripts/current-main/verify_current_main.py` 和 `mvn -B -ntp -q verify`，均退出 0。本次新生成的 881 份 JUnit XML 保存在 `current-main-verify/`，完整日志为 `current-main-verify.log`；该命令使用仓库默认生命周期，IT 由前述专项矩阵独立执行，不把默认 verify 冒充外部数据库测试。收尾未改变生产代码，46 个生产文件与已通过五库及外部服务验收的版本摘要一致，证据为 `followup-production-source-verification.json`。

额外完成 Viewer 库构建、类型兼容性及包产物验证。收尾运行设置 `FOGGY_VIEWER_REAL_BACKEND=1`，使用本次打包的 Launcher 和任务专用 MySQL 8.0.44，真实分组分页、维度/指标过滤及 CSV 导出全部通过。电商基础夹具使用 01～04 脚本，共 2,400 个年月/门店分组；预聚合脚本会改写销售表，不混用于此浏览器夹具。最终浏览器报告为 `target/issues-140-142/viewer-e2e-complete.json`，对应日志为同名前缀 `.log`。

## 初轮问题处理与验收边界

- 初轮 Viewer `custom-list.spec.ts` 查找旧入口和旧配置界面而失败。收尾已按当前“查询方案”菜单和三步配置界面更新操作，保留保存、默认、应用、删除及重载顺序断言；组件源码未改动。
- 初轮真实浏览器因未启用 7108 demo 而跳过。收尾已启用真实服务，最终 5/5 通过，零跳过。
- 初轮 Windows 的 13 项跳过包含 11 项平台/符号链接条件及两个源码扫描路径过期用例。收尾修复源码路径，并将目标缺失改为失败；26 项 ScriptRuntimeTest 全通过，两个扫描不再静默跳过。
- 上一轮 Linux 全量单测实际执行了 Windows 跳过的平台/符号链接用例，仅有当时两个旧路径扫描跳过；该历史报告保持原始计数，不回写成零跳过。
- 既有测试夹具同步到当前合同：Runtime SQL 调用增加 namespace 参数；Pivot 使用实际 v3 权限/缓存约定；Redis 跨 JVM 手工上下文提供最终权限签名；MCP 精确目录断言从旧的 32 更新为 33，并确认仓库已存在的 FactSalesViewerMetadataQueryModel 在目录中。没有裁剪 catalog、削弱生产权限条件，也没有以跳过代替这些用例。
- V934 冻结门禁对应历史发布。现行已接受的 [Current-main 生产基线](../../9.5/9.5.5/workitems/FEATURE-current-main-production-baseline.md) 明确使用本地验证，不以 GitHub Actions 为门禁。使用 `scripts/current-main/verify_current_main.py` 和完整 Maven `verify`，保留 V934 清单及摘要原样，不宣称通过历史发布流程。
- 可选远程 LLM 评测不属于确定性必测范围，不调用付费模型。MCP 的直接工具及 Compose 集成另用真实 MySQL 验证。
- 财务日历、任意部分周期、任意窗口套窗口、任意中间过滤和通用 timeWindow/窗口链组合仍在明确的后续范围。比例为近似数；不承诺任意精度十进制比例或任意跨时区时间戳转换。

## 相关设计和复查

- [#140/#141 方案](BUG-140-141-timewindow-comparative-plan.md)
- [#140/#141 核心流程复查](REVIEW-140-141-query-engine-core-flow.md)
- [#142 方案](BUG-142-window-calculated-field-stage-plan.md)
- [#142 核心流程复查](REVIEW-142-window-calculation-core-flow.md)
- [合并前最终复查与现行门禁](REVIEW-140-141-142-final.md)
