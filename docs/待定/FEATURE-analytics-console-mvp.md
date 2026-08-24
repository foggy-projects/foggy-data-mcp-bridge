---
doc_role: workitem
doc_type: implementation-plan
intended_for: implementation agent / reviewer
purpose: 记录未绑定正式 9.3.x 发布版本的独立 Analytics Console MVP 实施与验证边界。
status: DIRECT_QUESTION_IMPLEMENTED_FOCUSED_VERIFIED
recorded_at: 2026-08-24
---

# FEATURE Analytics Console MVP

当前 Java 坐标保持 `9.3.0-SNAPSHOT`，本项不触发正式版本切换。由于 9.5.5 canonical 迭代明确排除
Console Agent 和大型产品扩张，本项先归档在 `docs/待定/`；确认正式版本后再迁入唯一活跃子迭代。

## 顺序

1. 新建可选 `foggy-analytics-console` addon、独立 Web/API 路由和 launcher profile。
2. 实现 ADMIN/DESIGNER 草稿编辑、exact revision 保存、校验、预览和单向发布。
3. 实现 Console 自有 owner、目录、展示 ACL 和 VIEWER 只读渲染。
4. 实现服务端 FAP gateway、subject binding SPI 和 Function SDK 边界，不把凭据下发浏览器。
5. 用模块依赖与文档 guard 证明 TMS 与 Console 无业务依赖。
6. 保持 CLI `runtime`/`analytics` 双域，补 Console 开发 lane 文档与聚焦测试。
7. 把“直接对话问数”提升为普通用户首入口；不要求先创建 Report/Dashboard，并保持与设计会话隔离。

## 验证预算

- Java：新 addon 与 Analytics 受影响模块的 named/focused tests。
- Frontend：typecheck、unit tests、production build；浏览器 E2E 需单独授权后执行。
- CLI：Analytics lane 和 shared dispatch 的 named tests。
- 不运行全 reactor、全仓、全浏览器或完整 FAP/TMS E2E。

## 安全边界

- 不修改历史业务或运行数据；测试只使用新临时目录和 fake gateway。
- Console 默认关闭；没有 host subject resolver、可写 metadata root 和 Analytics Runtime beans 时拒绝启用。
- FAP 变更、Provider/Capability live publication 和生产凭据不在本项自动执行范围。

## 实施结果

| Step | Result |
|---|---|
| 1 | 新增默认关闭的 `addons/foggy-analytics-console`、`/analytics-console/` Web、独立产品 API 和 launcher `analytics-console` profile。 |
| 2 | ADMIN/DESIGNER 可创建 runtime-owned Bundle 草稿，按 exact revision 保存、校验、预览并单向发布；发布后 technical Bundle 在 Console 中锁定。 |
| 3 | Console catalog 独立保存 owner、目录、展示 audience 和状态；VIEWER 不读取定义内容，只消费被授权的 published preview/render。 |
| 4 | 服务端 FAP gateway、binding resolver SPI、Ask/Execution/Task 关联和 Function SDK callback 已实现；凭据、prompt 和 authority 不持久化。 |
| 5 | TMS 根版本仍为 `9.3.0-SNAPSHOT`，TMS 使用自己的 template publication/access policy；源码无 `foggy-analytics-console` 引用。 |
| 6 | CLI 继续保持 `foggy runtime` / `foggy analytics` 两个命令域、独立 URL 与 credential scope；Analytics 11 tests 通过。 |
| 7 | 新增 QUESTION/DESIGN 两种 conversation mode；QUESTION 绑定服务端 QM profile 和 exact model revision，支持 FAP START/CONTINUE/turn transcript，不创建 Analytics asset。 |
| 8 | Function SDK 新增 semantic model describe 与 strict semantic query；embedded、HTTP、FAP 三种 adapter 共用同一合同，Java engine 使用当前 subject authority 执行。 |

请求边界另增加：浏览器 mutation 专用请求头、Viewer definition suppression、FAP exact Provider/Capability/
Ask request+invocation/Conversation/Execution/Task/Subject correlation、FAP response 5 MiB 上限和启用配置 fail-fast。

问数的 strict query v1 只包含 columns、flat filters、groupBy、orderBy、start/limit、returnTotal 和 distinct；
raw SQL、Compose、脚本、calculated fields、hints、extData 与调用方权限过滤都不在合同中。Console callback
还会二次校验会话冻结的 namespace、QM 与 revision，不能借 CONTINUE 或 Function 参数切换数据范围。

## 验证证据

- Function/Runtime focused lane：Contract 7 tests、FAP changed lane 13 tests、HTTP client 5 tests、Foggy semantic
  adapter 2 tests、Runtime API controller 15 tests、endpoint qualification 1 test，均为 0 failures/errors。
- Console direct-question focused lane：agent START/CONTINUE、turn gateway、callback exact correlation、activation
  guard、catalog/service 与 static-dev authority resolver 共 7 classes / 14 tests，均通过。
- Launcher：profile configuration 1 test；`AnalyticsConsoleDirectQuestionSmokeTest` 1 test 在 `lite,analytics-console`
  组合上下文内完成 stable QM revision resolve、semantic describe 和真实 `amount` query。
- Frontend：`vue-tsc --noEmit`、2 unit tests、Vite production build 通过；最终 JS 约 80.23 kB、CSS 约 13.10 kB。
- Maven affected package 使用 `maven.test.skip=true` 成功，生成 9.3.0-SNAPSHOT Console 与 launcher JAR；测试
  证据来自上述 named/focused lanes，不把 package 伪装成额外测试。
- Disposable HTTP smoke：健康状态 `UP`，semantic describe/query capabilities 均为 supported；
  `FactOrderQueryModel` exact revision 解析成功，describe 返回 `amount`，query 返回 5 行且 total=6。临时 SQLite
  与 catalog 已在验证后删除。
- CLI：`PYTHONPATH=src python3 -m unittest tests.test_analytics_cli`，11 tests 通过。
- TMS boundary：根 `foggy-data.version=9.3.0-SNAPSHOT`；排除 build output 后源码扫描没有 Console addon 引用。
- 未运行：全仓、全 reactor、完整浏览器、live FAP、完整 Console/TMS E2E（未获授权）。

## 已知后续门禁

- 当前 file catalog 只承诺单进程；多实例需提供独立 `AnalyticsConsoleCatalogRepository` 实现。
- FAP Ask 已接受但 catalog 落盘失败时，首版没有自动 reconcile orphan Ask；生产启用前应补 pending/outcome-unknown recovery。
- FAP Provider/Skill/Function/Capability/callback binding 仍由部署流程显式发布；Console 不持有 Provider admin
  credential，也不在启动时隐式修改 FAP 资源。当前代码已具备 static-dev binding、START/CONTINUE/turn 和
  callback 闭环，但本批没有执行 live FAP mutation。
- 生产宿主必须实现 subject/FAP binding，完成真实认证、callback credential rotation 和 capability publication；本项不保存或生成生产凭据。
- immutable publication copy、draft fork、历史 revision、审批/协作、浏览器 E2E 和 rollout signoff 仍属后续，不阻塞默认关闭 MVP。
