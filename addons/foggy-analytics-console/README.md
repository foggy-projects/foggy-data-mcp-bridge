# Foggy Analytics Console

独立的 Analytics 产品 Console。普通用户的首入口是直接对话问数，不要求先创建 Report 或 Dashboard；
`ADMIN` / `DESIGNER` 还可完成 Report、Dashboard 草稿设计、校验、预览和单向发布，`VIEWER` 只消费
已发布渲染结果。它不替代 FAP Workbench 或 Runtime Console。

## 装配

标准 `foggy-mcp-launcher` 已内置本模块和 `foggy-analytics-runtime-api`，但默认 `lite` profile 不会启用
Console。开发环境通过 `analytics-console` Spring profile 显式开启；生产宿主也可以只依赖本模块和
`foggy-analytics-runtime-api`：

```xml
<dependency>
  <groupId>com.foggysource</groupId>
  <artifactId>foggy-analytics-console</artifactId>
  <version>9.3.0-SNAPSHOT</version>
</dependency>
```

最小本地配置示例：

```yaml
foggy:
  analytics:
    runtime-api:
      enabled: true
      bundles:
        - ref: console-drafts
          path: /absolute/development/path/console-drafts
          source-state: RUNTIME_OWNED
  analytics-console:
    enabled: true
    security-mode: static-dev-only
    catalog-path: /absolute/development/path/console-catalog.json
    function-trace-path: /absolute/development/path/function-traces
    question-profiles:
      - id: default
        display-name: 默认空间
        description: 在 default Namespace 内选择合适的语义模型进行分析。
        namespace: default
```

launcher 已提供 `application-analytics-console.yml`。标准发布包的 Bash 启动脚本使用
`ANALYTICS_CONSOLE_ENABLED=true`，PowerShell 启动脚本使用 `-AnalyticsConsole`；直接运行 JAR 时可显式传入
`--spring.profiles.active=lite,analytics-console`。FAP 默认关闭，因此不设置凭据时只启动 Console、Catalog
与 Analytics Runtime，不会发起外部连接，也不会隐式创建或修改 FAP 资源。

`static-dev-only` 仅用于本机开发。生产必须保留默认 `host-managed` 并提供
`AnalyticsConsoleSubjectResolver` Bean，从已认证的宿主请求解析 subject、产品角色以及不透明的数据权限
authority；浏览器提交的 owner、role、authority 均不会被信任。

入口：

- Web：`/analytics-console/`
- 产品 API：`/analytics-console/api/v1`
- Analytics Runtime API：`/analytics/api/v1`

产品写请求必须携带 `X-Foggy-Analytics-Console-Request: 1`。内置前端已自动附加该请求头。

## FAP 可选桥接

FAP 默认关闭。启用时还必须由宿主提供 `AnalyticsConsoleFapBindingResolver`，并配置以下服务端属性：

```yaml
foggy:
  analytics-console:
    fap:
      enabled: true
      base-url: https://fap.internal.example
      provider-ref: analytics-console-provider
      skill-name: analytics-design-guidance
      capability-name: analytics.design-read
      callback-capability-id: analytics.design-read
      callback-capability-revision: 1
      question-skill-name: analytics-question-answering
      question-capability-name: analytics.question-read
      question-callback-capability-id: analytics.question-read
      question-callback-capability-revision: 1
      callback-authorization: ${ANALYTICS_CONSOLE_FAP_CALLBACK_AUTHORIZATION}
      timeout-seconds: 30
```

`capability-name` 是提交 Ask 时由服务端冻结的选择；callback ID/revision 是入站回调的 exact contract
guard。Resolver 返回的 FAP credential、workspace/model binding 和数据 authority 不能写入 catalog 或 API
响应。Console 只保存不透明的 Ask/Execution/Task 关联，不保存 prompt。会话列表标题按需读取 FAP canonical
START turn 的首条用户消息并只做进程内缓存；暂时不可读时回退到问数 profile 的 display name，不会回填
Console catalog。

直接分析使用五项只读 Function：

- `foggy.analytics.model-dependencies.list@v3`：以 Markdown 目录列出会话所选 Namespace 下当前可用的
  QM 及其 AI prompt/description，同时保留结构化 `models` 兼容字段；
- `foggy.analytics.semantic-models.describe@v2`：描述 AI 针对当前问题选定的当前 QM；
- `foggy.analytics.semantic-queries.execute@v2`：只接受 columns、flat filters、groupBy、orderBy 和有界分页，
  保留为严格的轻量查询子集；
- `foggy.analytics.query-model.run@v2`：对应 MCP `dataset.query_model` 的完整单模型 DSL，支持
  validate/execute、calculatedFields、timeWindow、pivot 和受控单模型 DSL_CTE；
- `foggy.analytics.compose.run@v1`：对应 MCP `dataset.compose_script` 的受限 SemanticDSL，支持
  validate/preview/execute，用于跨模型 Join/Union、派生查询和多 Plan。

上述能力都不接受任意 SQL、standalone fsscript、文件/网络访问或调用方注入的 authority。Console
服务端固定 Namespace 并将不透明 FAP Subject 映射为宿主管理的查询身份；单模型调用自动解析当前有效
QM，并在一次调用内固定同一个 CatalogResolution。Compose 使用当前 Namespace catalog，并由引擎的
Compose authority/sandbox 再次约束。

可发布的问数 Skill bundle 位于
`src/main/resources/fap/analytics-question-answering/`。revision 7 包含主指令、完整 DSL/Compose 参考和
五项 Function 的 schema-delivery 声明；FAP 与 Worker 本地 Skill registry 必须以相同 digest 追加该修订，
Capability 也必须追加包含同一五项 Function 的 revision。不要原地覆盖旧修订，已开始的会话仍使用冻结的
旧 Skill/Capability/Function catalog，新能力只对新建会话生效。

Console 的问数 Skill/Capability 和 callback binding 由产品部署流程显式发布，callback endpoint 为
`/analytics-console/internal/fap/functions:invoke`。本模块不会持有 Provider admin credential，也不会在
应用启动时隐式创建或修改 FAP 资源。START 冻结 Namespace，AI 在该 Namespace 内按问题选择 QM；
CONTINUE 复用同一 Runtime
Execution；每次 Ask 的 request/invocation/task binding 都独立保存并用于 callback exact correlation。

五项 Function 的宿主 publisher 必须直接消费
`FapAnalyticsQuestionFunctionCatalog.publicationValues()`，并在 apply 前用
`scripts/release/fap_question_publication_gate.py` 校验 exact schema/projection digest。完整顺序和回退边界见
[`docs/release/fap-analytics-question-publication.md`](../../docs/release/fap-analytics-question-publication.md)。

安装并启用 Console 后，管理员可读取
`GET /analytics-console/api/v1/integrations/fap/question-publication`，取得完整、无凭据的
`foggy.analytics.question-host-sync-bundle.v1` handoff bundle。FAP 宿主可拉取该 bundle 后执行自己的
append-only dry-run/apply；该 GET 只导出 Function、Skill 和 callback 合同，不会连接或修改 FAP。

Console 自己承接的 Function callback 会把原始 arguments/result 按 invocation 写入独立的
`function-trace-path`，用于会话内的工具详情展示。Service Provider 仍是任务生命周期与时间轨迹的真相源；
这些执行负载不会写入 owner、目录、ACL 所在的 Console catalog，也不会让 Console 直连 FAP Runtime。

问数产品 API（均为 Console 自有 API）：

- `GET /analytics-console/api/v1/agent/question-profiles`
- `POST /analytics-console/api/v1/agent/questions`
- `POST /analytics-console/api/v1/agent/conversations/{conversationId}/turns`
- `GET /analytics-console/api/v1/agent/conversations/{conversationId}/turns`

这些对话 API 只在 `foggy.analytics-console.fap.enabled=true` 且服务端 FAP binding 完整时注册。FAP 关闭时，
Console 首页仍可访问，Analytics Runtime 的受治理语义能力仍可独立使用；内置前端会明确显示
`FAP NOT CONFIGURED`，不会退化为浏览器直连 LLM 或绕过 Java authority 执行查询。

## 边界

- Java Analytics Bundle store 是定义和 revision 的技术真相；Console catalog 只保存产品 owner、目录、
  展示 ACL、状态和不透明 FAP 关联。
- Viewer 详情不返回定义内容，只能使用受治理的 preview/render。
- 数据权限继续由当前 subject 对应的 QM/TM authority 执行；Console ACL 不能作为数据过滤条件。
- 问数 profile 是服务端 Namespace allowlist；会话冻结 Namespace，FAP callback 不能切换 namespace；
  单模型查询只提交 namespace、modelName 和业务参数，Provider 在单次调用开始时解析并固定当前有效 QM。
  FAP 只负责理解与编排，Java engine 才执行受治理语义查询。
- TMS 使用自己的发布表和 Function SDK adapter；它不依赖本 Console，也不与 Console 同步元数据。
- 当前 JSON catalog 是单进程 MVP store。多实例部署前必须替换 `AnalyticsConsoleCatalogRepository`，不能让
  多个进程共享写同一个 catalog 文件。

CLI 不复用 Console 产品 API：`foggy runtime` 继续连接 Foggy Runtime API v1，`foggy analytics`
连接独立 Analytics Runtime API v1。
