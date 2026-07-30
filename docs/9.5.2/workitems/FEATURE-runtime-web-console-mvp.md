---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.2
ticket: runtime-web-console-mvp
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: repository-owner-via-user-request
approved_at: 2026-07-27
open_questions: []
---

# Delivery Spec: Runtime Web Console MVP

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 固定 Java Runtime Web 管理端 MVP 的产品范围、模块边界、鉴权、技术选型、构建交付、
  验收与验证预算。
- canonical_path: `docs/9.5.2/workitems/FEATURE-runtime-web-console-mvp.md`
- current_iteration_limit: 本轮只创建本文件与 9.5.2 最小索引，不创建模块，不修改 Java、前端或
  Maven 业务实现。

## Goal

- version_goal: 在现有 Java Runtime 与 Maven reactor 内提供一个可选、同源、轻量的 Web 管理
  入口，使持有 Runtime API auth-code 的运维或开发用户可以在浏览器中完成 Runtime 的常用管理、
  模型检查和查询验证操作。
- target_outcome:
  - 新增独立 Addon 模块 `addons/foggy-runtime-console`，Console 应用不进入
    `foggy-data-viewer`，也不承载 Runtime 管理业务 API。
  - `foggy-runtime-api` 继续拥有管理 API、`X-Foggy-Runtime-Code` 校验和错误契约；
    `foggy-mcp-launcher` 只负责最终依赖与可执行 JAR 装配。
  - Console 静态资源进入 Addon JAR，并由 Spring Boot 在同一 origin 的 `/console/` 提供。
  - 默认兼容现有只保护 mutation 的部署；显式启用 Console 时使用
    `foggy.runtime-api.auth-scope=management-all`。
- critical_outcomes:
  - 浏览器端路由守卫不被当作安全边界；所有 Console 使用的 Runtime API 在服务端真实校验
    `X-Foggy-Runtime-Code`。
  - Token 不进入 URL、日志、持久化 localStorage、错误详情或前端诊断输出。
  - Console 可以从干净源码构建并进入 Addon JAR；不依赖仓库中被忽略的本地 `dist`。
  - 不引入独立 Node 服务、SSR、微前端、BFF、账号体系、RBAC 或审计系统。
- success_is_sufficient_when: AC-1 至 AC-15 均有直接通过证据，受影响 Maven/前端测试通过，
  打包后的 launcher 能从 `/console/` 完成 token 校验和核心页面 smoke，且安全 review 未发现
  服务端鉴权旁路或 token 泄漏。

## Background and Repository Facts

### 当前边界

- 根 `CLAUDE.md` 固定 Java 17、Spring Boot 3.4.5、Maven 多模块 reactor，并规定：
  `foggy-runtime-api` 负责数据源、namespace、Bundle、模型、查询与 compose 管理 API；
  `foggy-mcp-launcher` 是最终装配根，不是业务逻辑模块；`addons/*` 是可选能力。
- `docs/architecture/module-boundaries.md` 将 `addons/foggy-data-viewer` 定位为数据浏览 UI/资源
  Addon。Console 可以消费其公开前端组件，但不能反向把 Runtime 管理应用放入 data-viewer。
- 根 reactor 当前包含 `addons/foggy-data-viewer`，尚无 `foggy-runtime-console`。
- `foggy-mcp-launcher` 当前只在 Maven `runtime-api` profile 中引入 `foggy-runtime-api`；默认
  launcher 不启用 Runtime API。
- `foggy-runtime-api/pom.xml` 和 launcher profile 注释当前仍把 Runtime API 描述为开发/测试
  adapter。9.5.2 的“生产态同源”只冻结静态资源随 JAR 运行的拓扑，不自动把共享 auth-code
  提升为客户生产 IAM，也不未经批准删除现有支持级别提示。

### 当前版本与依赖基线

以下结论来自当前 `pom.xml`、`package.json`、`package-lock.json` 和本地构建工具，不是通用推荐：

| Area | Repository declaration | Current resolved / environment fact | 9.5.2 decision |
|---|---|---|---|
| Java | root `java.version=17` | OpenJDK 17.0.19 | 保持 Java 17 |
| Maven | multi-module reactor；project version `9.1.0.beta` | Maven 3.8.7 | 不新建仓库；Addon 进入现有 reactor |
| Spring Boot | parent and property `3.4.5` | repository property `spring.version=6.2.0` | 复用 Boot 3.4.5，不另建服务栈 |
| data-viewer package | `foggy-data-viewer@1.0.1-beta.45` | `dist/` 被根 `.gitignore` 忽略 | 只消费公开组件/source API，Console 构建不得依赖本地 ignored dist |
| Vue library lane | `vue ^3.4.0` | lock: `3.5.26` | Console 使用 Vue 3.5.x |
| TypeScript library lane | `typescript ^5.3.0` | lock: `5.9.3` | Console 使用 TypeScript 5.9.x |
| Vite library lane | `vite ^5.0.0` | lock: `5.4.21` | 作为 library 历史基线，不作为新 app 首选 |
| verification app lane | Vue `^3.5.24`, TS `~5.9.3`, Vite `^7.2.4` | lock: Vue `3.5.34`, TS `5.9.3`, Vite `7.3.3` | Console 作为 app，以该 consumer lane 为初始基线并提交自己的 lockfile |
| Vite runtime | Vite 7 engine | Node `^20.19.0 || >=22.12.0`; current Node `22.23.1`, npm `10.9.8` | Node 只用于构建；构建工具必须固定兼容版本 |
| HTTP | data-viewer/verification app both use Axios | lock: Axios `1.13.2` / `1.16.0` | 使用单一 Axios client |
| UI/table | Element Plus、vxe-pc-ui、vxe-table、xe-utils already used | verification lock: Element Plus `2.13.7`, vxe-table `4.18.13`, xe-utils `3.9.1` | 不新增另一套 UI 框架；直接声明兼容依赖并锁定 |
| unit/E2E | Vitest in library；Playwright in verification app | Vitest `1.6.1`, Playwright `1.60.0` | 复用 Vitest + Playwright 测试分层 |

实现必须提交 Console 自己的 `package-lock.json`。依赖版本以实现时生成并评审通过的 lockfile 为
交付真值，不在实现阶段无理由追逐最新版本。

### 当前鉴权事实

`RuntimeApiAuthInterceptor` 已核实使用常量 Header
`X-Foggy-Runtime-Code`，通过 `MessageDigest.isEqual` 比较，并只注册到 `/api/v1/**` 与 legacy
bundle 路径。当前 `RuntimeApiAuthCodeGateTest` 明确锁定的是 mutation-only 行为：

- 已保护：Bundle/datasource 增删改、datasource test、namespace 绑定写、resource save、model
  validate/refresh、完整 FSScript execute 和 legacy bundle mutation。
- 当前未保护的 GET 管理读取：`GET /api/v1/capabilities`、`GET /api/v1/bundles`、
  `GET /api/v1/datasources`、`GET /api/v1/datasources/diagnostics`、
  `GET /api/v1/namespaces/{namespace}/datasource`、`GET /api/v1/models`。
- 另有 POST 形式的读取/执行端点当前也在 mutation gate 外：model describe、resource export、
  query、table inspect/list、SQL query 和 compose。
- 9.5.1 已将 `POST /api/v1/fsscript/execute` 收紧为作者/管理面，并明确
  `Authorization` 数据面身份与 `X-Foggy-Runtime-Code` 管理凭据不得互相提升。

结论：现有 GET 管理接口并非都受保护；Console 不能直接建立在当前默认 gate 上。

## Scope

### In Scope

- 在现有仓库新增 Maven Addon：`addons/foggy-runtime-console`。
- Addon 内包含独立 `frontend/` Vue SPA、最小 Spring Boot 静态资源/启用配置，以及打包测试。
- 在 `foggy-runtime-api` 增加：
  - `GET /api/v1/access/check` 安全 token 校验端点；
  - `foggy.runtime-api.auth-scope=mutations|management-all`；
  - `management-all` 的服务端路径保护与对应 endpoint inventory tests。
- Console 登录、session token 管理、概览、数据源/namespace、Bundle/resource、模型、查询、
  table/SQL、compose 和高级 FSScript 页面。
- 复用 `foggy-data-viewer` 的公开结果表格、查询面板或 pivot 组件；Console 页面、路由、API
  adapter、登录和管理表单留在 Console 模块。
- Maven 构建前端并把静态产物装入 Addon JAR；launcher 通过显式 profile/依赖完成最终装配。
- 本地 Vite proxy、生产同源、JUnit、Vitest、Playwright 和打包 smoke。
- 实现时同步受影响的 canonical 架构、配置和开发文档；本轮文档创建不提前改写当前架构事实。

### Affected Modules

- future implementation:
  - root `pom.xml`
  - `addons/foggy-runtime-console`
  - `foggy-runtime-api`
  - `foggy-mcp-launcher`
  - `docs/architecture` and relevant `docs/dev-guide`
  - `docs/9.5.2`
- current documentation-only turn:
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/FEATURE-runtime-web-console-mvp.md`
- external_dependencies: npm registry and Maven Central are build-time dependency sources only；生产运行
  不需要 Node 或外部前端服务。

## Non-Goals

- 不建立用户账号、注册、找回密码、Session Server、SSO、OAuth/OIDC、RBAC、租户权限或审计。
- 不实现 token 签发、轮换、撤销列表、过期策略、加密托管或多 token 管理。
- 不实现 Console 页面/资源版本管理、多人协作、审批流或 Git 编辑器。
- 不拆分独立 Git 仓库，不发布独立 Console 服务，不部署 Node、SSR、Nuxt、BFF 或反向代理层。
- 不引入微前端、Module Federation、iframe 应用拼装或多 Runtime 聚合控制台。
- 不把 Console 应用、登录、路由或 Runtime API adapter 直接写入 `foggy-data-viewer`。
- 不新增另一套 UI 框架；不为 MVP 引入 Monaco 等重量级代码编辑器，JSON/FSScript 编辑先使用
  受控 textarea、格式化和明确错误展示。
- 不改变 RuntimeEnvelope、TM/QM、query DSL、Compose、FSScript 或数据源 registry 的业务语义。
- 不把 Runtime auth-code 转换成 `Authorization`，也不让它替代 9.5.1 的模型数据面权限。
- 不承诺旧浏览器；IE 和停止维护的浏览器不在兼容范围。
- 不在本事项中把现有 Runtime API 从开发/测试适配器重新定位为具备企业生产安全承诺的管理平台。
- 不在本事项运行 release authority、全数据库矩阵、tag、publish、source-seal 或 remote CI。

### Do Not Touch

- 9.5.0 Model SPI v2 模块身份、已删除 legacy 坐标/包和 provider 边界。
- `foggy-dataset-model-engine` 的查询、权限、缓存与预聚合语义。
- `foggy-data-viewer` 的独立组件库定位和发布资产规则；除非 Console clean-build 发现公开组件
  契约缺陷并先进入 `NEEDS_REPLAN`，不得借本事项重构整个 viewer。
- launcher 内不得新增 Runtime Controller、鉴权拦截器、Console API Service 或前端源码。

### Non-Blocking or Waivable Items

- 非关键视觉细节、空状态文案、次要快捷键和高级 JSON 编辑体验可以由 owner 延后。
- MVP 可不提供浏览器端记住 namespace 之外的个人偏好；token 安全、API 鉴权和核心操作不可豁免。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 新建 `addons/foggy-runtime-console` | Console 是可选 UI Addon，不属于 engine/runtime API 或 viewer 组件库 | 进入现有 reactor，不新建仓库 |
| Vue 3 + TypeScript + Vite | 仓库已有 library 与 verification app 两条实际链路 | Console 采用 Vue 3.5/TS 5.9/Vite 7 app lane，提交 lockfile |
| 无独立 Node 服务 | Spring Boot 可同源提供静态资源，Node 只需构建 | 生产 JAR/JVM 单进程运行 |
| 不新增 UI 框架 | Element Plus、vxe 体系已存在并与 viewer 一致 | Console 直接声明依赖，避免依赖偶然传递 |
| 通过公开边界复用 viewer | 可复用 DataTable、QueryPanel、PivotViewer/PivotGrid 等 | 不复制组件，不把 Console 页面放进 viewer；clean build 不依赖 ignored dist |
| 新增 `vue-router` 4.x | Console 有多个明确页面，需要 URL 可定位 | 这是唯一必要的新应用基础依赖；使用 hash history 降低服务端 SPA fallback 复杂度 |
| 不新增 Pinia | MVP 全局状态仅 token/session、namespace 和小量 UI 状态 | 使用 Vue Composition API 的 typed store/composable |
| 使用 Axios | 仓库前端已使用，便于统一 Header、RuntimeEnvelope 与 401 处理 | 只允许相对、同源 API base；禁止向任意 URL 自动附加 token |
| `sessionStorage` 保存 token | 内存模式刷新即丢失；localStorage 暴露窗口更长 | 按 tab 保存，关闭 tab 清除；仍需通过 CSP/XSS 防护降低可读风险 |
| `auth-scope` 默认 `mutations` | 保持现有调用方和 9.2.1 合同兼容 | Console 启用时必须显式 `management-all` |
| `management-all` 覆盖全部 `/api/v1/**` | Console 包含读、写、查询和诊断，逐个遗漏容易产生旁路 | Runtime auth-code 只是外层管理 gate；`Authorization` 数据权限继续独立 |
| 固定入口 `/console/` | 避免可配置 base path 与构建产物错配 | hash routes 位于 `/console/#/...`；支持 servlet context 时 API base 从 pathname 推导 |
| Console `dist` 不提交 | JAR 是部署制品，提交 dist 会造成源码/产物漂移 | Maven package 必须从 package-lock 生成并校验 JAR 内容 |
| launcher 只装配 | 保持 canonical module boundary | 通过显式 `runtime-console` profile 或等价 opt-in 依赖引入 runtime-api + console |

## User Operation Flow

1. 部署方在 launcher 中显式启用 Runtime API 与 Console，并配置 auth-code 和
   `auth-scope=management-all`。
2. 用户访问 `/console/`。未建立有效 tab session 时进入 token 页面，不从 URL、cookie 或服务端
   HTML 注入 token。
3. 用户输入 Runtime API token；前端仅通过 Header
   `X-Foggy-Runtime-Code` 调用 `GET /api/v1/access/check`。
4. 服务端返回成功后，前端把 token 写入当前 tab 的 `sessionStorage`，进入概览页；失败时只显示
   稳定错误码/安全文案，不回显提交值。
5. 页面通过同一个 Axios client 调用 Runtime API。每次请求动态读取当前 session token 并附加
   Header；namespace 仍按现有合同使用 `X-NS`。
6. 浏览器刷新后，应用先读取 session token 并重新调用 access check；校验成功才恢复页面。
7. 任一 API 返回 `401 RUNTIME_AUTH_REQUIRED` 时，清除 session token 并回到登录页；业务 `403`
   或模型权限错误不得误报成 management token 失效。
8. 用户点击退出时清除 token、内存状态和可能含敏感输入的表单，再导航到登录页。

## Pages and Feature Inventory

| Page | MVP capability | Required behavior |
|---|---|---|
| Token Login | 输入、校验、清除 Runtime token | 无 remember-me；不允许 URL token；错误不回显 token |
| Overview | capabilities、Runtime/API/schema/security mode、warnings | 只展示安全化信息；不展示配置 auth-code |
| Datasources & Namespaces | list、diagnostics、add/update/delete/test、namespace binding | password 输入默认遮罩；列表不回显 secret；危险操作二次确认 |
| Bundles & Resources | bundle list/add/update/remove、resource export/save | 配置 Bundle 的不可变限制按 API 显示；保存前展示目标路径和文件数 |
| Models | list、describe、validate、refresh | namespace 可选；生命周期 error/warning 保留 RuntimeEnvelope 语义 |
| Query Workbench | model selection、JSON DSL validate/execute、table/pivot result | 复用 viewer 结果组件；auth-code 不映射到 data-plane Authorization |
| Tables & SQL | table list、inspect、read-only SQL query | UI 明确这是数据读取；服务端现有 SQL 限制保持；结果复用 DataTable |
| Compose | validate、preview、execute | JSON payload 编辑、结果/诊断展示；不发明第二套 DSL |
| Advanced FSScript | execute 原始脚本 | 默认折叠并显示作者/管理面风险提示；token gate 必须在服务端 |
| Session | 当前 namespace、连接状态、logout | 不提供账号资料、角色、token 查看或复制功能 |

MVP 不要求一次性提供复杂可视化设计器。各 workbench 的第一版以可编辑文本、格式化、示例、提交、
响应诊断和结果表格为完整闭环。

## Module Boundaries and Dependency Direction

### Confirmed Structure

```text
foggy-data-mcp-bridge/
├── addons/
│   ├── foggy-data-viewer/
│   └── foggy-runtime-console/
│       ├── pom.xml
│       ├── frontend/
│       │   ├── package.json
│       │   ├── package-lock.json
│       │   ├── vite.config.ts
│       │   └── src/
│       └── src/main/
│           ├── java/          # only enablement/static delivery/bootstrap checks
│           └── resources/     # no checked-in generated console dist
├── foggy-runtime-api/         # management API, RuntimeEnvelope, auth-code gate
├── foggy-mcp-launcher/        # final dependency/profile and executable JAR assembly
└── docs/9.5.2/
```

### Ownership Rules

| Concern | Owner | Forbidden placement |
|---|---|---|
| Runtime management API and DTO | `foggy-runtime-api` | console addon, launcher, data-viewer |
| `X-Foggy-Runtime-Code` validation, auth-scope, access check | `foggy-runtime-api` | JavaScript-only checks, launcher |
| Console SPA, route, page, API adapter | `addons/foggy-runtime-console/frontend` | data-viewer, launcher |
| Static resource activation, `/console/` delivery, secure enablement precondition | `addons/foggy-runtime-console` Java/resources | runtime business services |
| Reusable table/query/pivot primitives | `addons/foggy-data-viewer/frontend` | Console-specific login or navigation |
| Executable artifact inclusion/profile | `foggy-mcp-launcher` | business API or page implementation |

Required dependency direction:

```text
foggy-data-viewer public frontend API ──→ consumed by console frontend
foggy-runtime-api ──────────────────────→ provides console HTTP contract/auth
foggy-runtime-console ──────────────────→ may compile against runtime-api/web autoconfigure only
foggy-mcp-launcher ─────────────────────→ assembles runtime-api + runtime-console
```

`foggy-runtime-api`、viewer 和基础模块不得反向依赖 launcher 或 Console。

## Frontend-to-API Mapping

| Console capability | Existing/new Runtime API | Method | `management-all` | Notes |
|---|---|---:|---:|---|
| Token check | `/api/v1/access/check` | GET | required, always verifies submitted code | new additive endpoint |
| Overview | `/api/v1/capabilities` | GET | required | currently open under mutation scope |
| Bundle list/create | `/api/v1/bundles` | GET/POST | required | GET currently open, POST protected |
| Bundle update/remove | `/api/v1/bundles/{name}` | PUT/DELETE | required | existing mutation protection retained |
| Resource export/save | `/api/v1/resources/export`, `/api/v1/resources/save` | POST | required | export currently outside mutation gate |
| Datasource list/create | `/api/v1/datasources` | GET/POST | required | GET currently open |
| Datasource diagnostics | `/api/v1/datasources/diagnostics` | GET | required | currently open |
| Datasource update/remove/test | `/api/v1/datasources/{name}`, `/test` | PUT/DELETE/POST | required | existing mutation protection retained |
| Namespace binding | `/api/v1/namespaces/{namespace}/datasource` | GET/PUT | required | GET currently open |
| Model list/describe | `/api/v1/models`, `/api/v1/models/{model}/describe` | GET/POST | required | both currently outside mutation gate |
| Model validate/refresh | `/api/v1/models/validate`, `/api/v1/models/refresh` | POST | required | existing protection retained |
| Query validate/execute | `/api/v1/query/{model}/validate`, `/execute` | POST | required outer gate | optional `Authorization` remains separate data identity |
| Table list/inspect | `/api/v1/tables/list`, `/api/v1/tables/inspect` | POST | required | request body and `X-NS` unchanged |
| SQL query | `/api/v1/sql/query` | POST | required | does not expand server SQL semantics |
| Compose | `/api/v1/compose/validate`, `/preview`, `/execute` | POST | required | existing RuntimeEnvelope mapping |
| FSScript | `/api/v1/fsscript/execute` | POST | required | already management protected; remains author-level |

`management-all` should be implemented as a central path policy over `/api/v1/**`, not as another scattered
Controller annotation inventory. Legacy `/api/bundles/**` retains current mutation protection and does not become a
Console API.

## Authentication and Token Handling

### Access Check Contract

Additive endpoint: `GET /api/v1/access/check`.

- Request carries only `X-Foggy-Runtime-Code`; token is never accepted from query string, path, cookie or body.
- Correct token returns HTTP 200 and a minimal `RuntimeEnvelope`, for example authenticated state、effective
  auth scope and Runtime API version；不得返回 token、token 长度、摘要、配置来源或比较细节。
- Missing/invalid token returns the same generic `401 RUNTIME_AUTH_REQUIRED` shape used by the interceptor.
- Auth-code mode enabled but code missing returns existing fail-closed
  `503 RUNTIME_AUTH_CODE_NOT_CONFIGURED` semantics.
- Endpoint itself must always pass through the real interceptor check; it cannot be a controller that merely reports
  whether a code is configured.
- Response must include `Cache-Control: no-store`；capabilities/access response must not be cached by a shared proxy.

### Auth Scope Contract

New compatible configuration:

```properties
foggy.runtime-api.auth-scope=mutations       # compatibility default
foggy.runtime-api.auth-scope=management-all  # required by Console
```

- `mutations`: preserve the current endpoint inventory and tests, including currently open reads/execution except
  the separately protected FSScript author endpoint.
- `management-all`: require a valid Runtime code for every `/api/v1/**` request, including GET and read-like POST.
- Unknown/blank configured values fail startup with a safe configuration error；不得静默降级为 `mutations`。
- `foggy.runtime-console.enabled=true` with Runtime API disabled、missing auth-code、effective security mode not
  auth-code or auth-scope not `management-all` must fail startup or refuse Console activation. It must not serve an
  apparently functional but server-unprotected Console.
- `X-Foggy-Runtime-Code` remains management/deployment possession proof only. It must never be copied into
  `Authorization`, `RequestIdentity` or model permission decisions.

### Browser Storage Decision

| Option | Benefit | Risk | MVP conclusion |
|---|---|---|---|
| memory only | shortest persistence; cleared on reload | every refresh logs out; poor admin usability | not default; may hold a working copy in memory after validation |
| `sessionStorage` | survives reload within one tab; closes with tab | readable by same-origin XSS; duplicated tab behavior varies | selected MVP storage |
| `localStorage` | survives browser restart | longest theft window; shared across tabs; easy accidental retention | prohibited |

Additional controls:

- Use one explicit key under Console namespace；logout/401 clears it synchronously.
- Never use console logging、analytics、Sentry breadcrumbs、request dumps or persisted state plugins for token data.
- Axios interceptors may attach the token only when URL is relative and resolves to the current origin/API base.
- Error adapters may expose RuntimeEnvelope `code`、`message`、`phase` and safe diagnostics, but never Axios request
  config、headers or submitted form state.
- Token input uses password type, disables autocomplete persistence where browsers honor it, and clears the form
  after success/failure handling.
- Console must ship a restrictive same-origin Content Security Policy or equivalent Spring response headers that
  prohibit inline third-party script by default. CSP reduces but does not eliminate the sessionStorage/XSS risk.

## Configuration Contract

| Property / env mapping | Default | Requirement |
|---|---|---|
| `foggy.runtime-console.enabled` / `FOGGY_RUNTIME_CONSOLE_ENABLED` | `false` | explicit opt-in; only controls Console delivery |
| `foggy.runtime-api.enabled` / `FOGGY_RUNTIME_API_ENABLED` | existing `false` | must be `true` when Console enabled |
| `foggy.runtime-api.security-mode` / `FOGGY_RUNTIME_API_SECURITY_MODE` | existing `none-dev-test-only` | Console requires effective `auth-code` |
| `foggy.runtime-api.auth-code` / `FOGGY_RUNTIME_API_AUTH_CODE` | blank | required for Console; never printed |
| `foggy.runtime-api.auth-scope` / `FOGGY_RUNTIME_API_AUTH_SCOPE` | `mutations` | Console requires `management-all` |

`/console/` is fixed for MVP and is not a runtime-configurable property. This avoids a server property changing the
Vite asset base after compilation. If future deployments require an arbitrary path, that is a separate compatibility
design, not a hidden 9.5.2 option.

## Frontend Technical Selection

### Application Stack

- Vue 3.5.x + TypeScript 5.9.x + Vite 7.x, based on the existing data-viewer verification application lane.
- `vue-router` 4.x with hash history. `/console/` serves one `index.html`; routes use
  `/console/#/overview`、`#/datasources` 等，不要求 Java 为每个 route 实现 SPA forward。
- Axios single client for `/api/v1` and RuntimeEnvelope normalization.
- Vue Composition API typed stores/composables；不新增 Pinia/Vuex。
- Element Plus for app shell/forms/dialogs/feedback；vxe-pc-ui/vxe-table through viewer components for data result
  presentation。Console 直接声明它使用的 peer/runtime dependencies。

### Reusing `foggy-data-viewer`

- Reuse only public exports such as `DataTable`、`QueryPanel`、`PivotViewer`/`PivotGrid` and documented types.
- Console owns RuntimeEnvelope-to-viewer adapters. Viewer must not learn login、Runtime auth header、bundle、
  datasource or launcher concepts.
- The repository currently ignores `addons/foggy-data-viewer/frontend/dist`; therefore a clean Console build must
  compile against a reproducible package/source boundary, not a developer's local dist directory.
- For this monorepo MVP, implementation may use a Vite/TypeScript alias to the viewer public source entry
  `addons/foggy-data-viewer/frontend/src/index.ts`, while declaring compatible peer dependencies in Console. If an
  exact published package is proven installable during implementation, using that exact locked package is also
  acceptable. In both cases imports must remain from the public `foggy-data-viewer` entry, not internal component
  paths, and clean-clone build is must-pass.
- Swapping the monorepo source alias for a verified exact npm package later is an internal build choice if public
  component behavior and acceptance evidence remain unchanged.

### Development and Production Access

- Development: Vite serves the SPA and proxies `/api/v1` to a configurable local Java Runtime. The browser itself
  supplies `X-Foggy-Runtime-Code`; Vite config must not contain a hard-coded token or inject a fixed auth Header.
- Production: browser loads Console and calls Runtime API from the same Spring Boot origin. No CORS dependency and
  no Node process.
- Vite `base` should use a relative asset strategy compatible with `/console/`; the API base should be derived from
  the current servlet context prefix before `/console/`, so a non-root Spring context does not accidentally send
  requests to another origin/root path.
- Dev proxy and production client must preserve `X-NS` and optional data-plane `Authorization` independently from
  the management Header. MVP Console does not persist a second business Authorization token.

## Build, Packaging and Deployment

### Frontend-to-Maven Lifecycle

The Console Addon POM must provide a reproducible, build-only Node integration:

1. Pin a Vite-compatible Node/npm toolchain in the module build metadata. Current verified environment is Node
   `22.23.1` / npm `10.9.8`; Vite 7 requires Node `^20.19.0 || >=22.12.0`.
2. Run `npm ci` from `frontend/package-lock.json`; release/package builds must not use `npm install`.
3. Run frontend typecheck, unit tests in the test lane, and `npm run build` before Maven resource packaging.
4. Copy generated `frontend/dist/**` into
   `${project.build.outputDirectory}/META-INF/resources/console/`.
5. Package the Addon JAR and assert it contains
   `META-INF/resources/console/index.html` plus hashed assets.

A pinned build-only Maven frontend runner/plugin may be added inside the new Addon POM. Its exact plugin version is
an Ultra implementation detail, but it must download/use a pinned Node/npm or validate a pinned supported toolchain;
it may not silently depend on an arbitrary developer PATH. A skip property may support focused Java development,
but release/package/launcher smoke evidence must run with frontend build enabled and fail when assets are missing.

### `dist` Policy

- Do not commit `addons/foggy-runtime-console/frontend/dist`.
- Commit source、`package.json`、`package-lock.json`、TypeScript/Vite/Vitest/Playwright config and tests.
- Do not copy data-viewer local ignored dist into Console source resources.
- Generated assets exist only under frontend build output、Maven `target/classes` and packaged JAR.

### Launcher Assembly

- Add root reactor module entry for `addons/foggy-runtime-console`.
- Add a launcher opt-in `runtime-console` profile, or an equivalently explicit profile, that assembles both
  `foggy-runtime-api` and `foggy-runtime-console`. Do not make Console silently appear in the default launcher.
- launcher contains dependency/config bindings and Spring Boot repackage only；no Console Controller、auth policy or
  frontend source belongs there.
- Deployment runs one Spring Boot process. Expected entry is
  `http(s)://host[:port][/context]/console/`.

## Compatibility and Rollback

- Default `auth-scope=mutations` preserves current direct Runtime API callers and existing tests.
- `management-all` is explicit and intentionally makes previously open Runtime reads/executions require the
  management Header. This is a deployment choice required for Console, not a silent default breaking change.
- `GET /api/v1/access/check` is additive. Existing RuntimeEnvelope and error codes remain stable.
- Existing `Authorization` data-plane behavior, anonymous/public model compatibility and `X-NS` namespace priority
  remain unchanged.
- Console Addon is optional. Removing the launcher profile/dependency and setting Console disabled restores the
  pre-9.5.2 packaging topology.
- Setting auth-scope back to `mutations` restores pre-9.5.2 read protection semantics for deployments that do not
  enable Console.
- No database/schema/registry migration is introduced.
- Browser support targets current evergreen Chrome/Edge/Firefox. Responsive desktop/tablet administration is
  required；phone-first operation and IE are not.
- “Production” access mode means packaged same-origin Spring Boot delivery rather than Vite dev proxy；它不改变
  当前 Runtime API 的支持级别，也不承诺账号、审计、轮换或客户 IAM。

## Phased Implementation Plan

### M0 — Baseline and Contract Guards

- Create module/root/launcher skeleton without pages.
- Freeze endpoint inventory and default mutation compatibility tests.
- Add clean-clone/frontend toolchain checks before relying on viewer reuse.

### M1 — Server Authentication Closure

- Add `auth-scope` enum/config validation and `management-all` centralized policy.
- Add protected `GET /api/v1/access/check` and no-store response.
- Add positive/negative endpoint inventory tests, including every current GET and read-like POST.

### M2 — Console Shell and Session

- Add Vue/Vite/TypeScript app、hash router、Axios client、RuntimeEnvelope adapter、sessionStorage token flow、
  CSP/security headers and login/overview/session pages.
- Establish relative `/console/` assets and servlet-context-aware API base.

### M3 — Runtime Management Pages

- Implement datasource/namespace、bundle/resource and model lifecycle pages.
- Add confirmation、secret-safe form behavior、namespace propagation and stable error diagnostics.

### M4 — Workbenches and Viewer Reuse

- Implement query、tables/SQL、compose and FSScript workbenches.
- Adapt Runtime responses into viewer table/pivot components without changing viewer ownership.

### M5 — Maven Packaging, Launcher and Evidence

- Bind npm clean build into Addon Maven lifecycle and verify JAR contents.
- Add launcher opt-in assembly/config and packaged Spring Boot smoke.
- Run JUnit、Vitest、Playwright、security/static scans and update canonical architecture/dev docs.

Each milestone must keep the application buildable. If a milestone requires changing authentication meaning、module
ownership、public API shapes or the no-Node deployment model, set `NEEDS_REPLAN` before continuing.

## Acceptance Criteria

- [x] AC-1: Root reactor contains optional `addons/foggy-runtime-console`; module ownership and dependency direction
  match this contract, and no Console business code is placed in launcher or data-viewer.
- [x] AC-2: `/console/` loads from Addon JAR static resources in a packaged launcher; hash routes refresh without 404,
  and no production Node process is required.
- [x] AC-3: Console is disabled by default. Enabling it without Runtime API、auth-code or
  `management-all` fails safely and does not expose an unprotected operational UI.
- [x] AC-4: `auth-scope=mutations` preserves the current protected/unprotected inventory；unknown scope fails startup.
- [x] AC-5: `auth-scope=management-all` rejects missing/wrong Runtime code for every `/api/v1/**` endpoint,
  including all current GET management reads and read-like POST endpoints, before Controller business logic.
- [x] AC-6: `GET /api/v1/access/check` succeeds only with a valid `X-Foggy-Runtime-Code`, returns minimal no-store
  data, and never accepts or returns token material through URL/body/cookie/response.
- [x] AC-7: Login、reload revalidation、401 expiry handling and logout follow the sessionStorage contract；no token
  appears in localStorage、logs、URL、error UI、test reports or committed fixtures.
- [x] AC-8: Overview、datasource/namespace、bundle/resource、model pages complete the listed existing API flows with
  confirmation and RuntimeEnvelope diagnostics.
- [x] AC-9: Query、tables/SQL、compose and FSScript workbenches validate/execute current payloads without inventing a
  second contract；management token is never promoted to model `Authorization`.
- [x] AC-10: Query results use `foggy-data-viewer` public table/query/pivot exports；Console clean build does not rely
  on viewer ignored dist or internal component paths.
- [x] AC-11: Frontend uses Vue 3/TypeScript/Vite、Axios、Element Plus/vxe and Vue Router；no Pinia、second UI
  framework、SSR、microfrontend or BFF is added.
- [x] AC-12: `npm ci` + typecheck + unit + build pass from a clean tree；Console dist remains untracked and Maven JAR
  contains the generated `/META-INF/resources/console/` assets.
- [x] AC-13: Dev Vite proxy and packaged same-origin mode both work without hard-coded token headers；servlet context、
  `X-NS` and optional independent `Authorization` are not conflated.
- [x] AC-14: Focused JUnit、affected reactor、Vitest and Playwright tests actually pass, with exact commands、test
  totals and evidence paths recorded；`git diff --check` passes.
- [x] AC-15: Architecture/module boundary、configuration and developer deployment docs are updated in the
  implementation change；no account/RBAC/audit/version-management claim is introduced.

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-3/AC-4 | must-pass | major | JUnit property/context tests | current default Runtime tests | default compatibility、invalid config and Console activation results |
| AC-5/AC-6 | must-pass | critical | interceptor inventory + random-port integration | existing `RuntimeApiAuthCodeGateTest` | every route negative/positive matrix and access-check HTTP results |
| AC-7 | must-pass | critical | Vitest + Playwright + secret scan | none | reload/logout/401 behavior and no-leak scan |
| AC-8/AC-9 | must-pass | major | API adapter unit + SQLite-backed Playwright | existing Runtime controller tests | page action/result/error evidence |
| AC-10/AC-11 | must-pass | major | clean frontend build + component tests | viewer library/verification tests | public import list、rendered result table/pivot and dependency review |
| AC-2/AC-12 | must-pass | major | Maven package + JAR inspection + launcher smoke | existing launcher repackage pattern | exact jar path/content and `/console/` HTTP smoke |
| AC-13 | must-pass | major | Vite proxy smoke + packaged same-origin/context smoke | verification app proxy pattern as input only | no fixed header、correct API path/Header evidence |
| AC-14/AC-15 | must-pass | major | affected reactor、docs/static review、diff check | repository test conventions | exact commands/results/changed paths |
| visual polish | waivable | minor | targeted manual/Playwright screenshots | none | bounded issue and owner waiver if deferred |

### Test Boundary

- JUnit owns server auth policy、access check、configuration fail-closed、static resource enablement、RuntimeEnvelope
  and launcher/JAR assembly behavior. It does not test browser state management.
- Vitest owns token store、route guard、Axios Header restrictions、RuntimeEnvelope adapters、form validation and
  deterministic component state. It does not replace real server auth tests.
- Playwright owns user-visible login/logout/reload、navigation、critical management flow、result rendering and
  packaged same-origin smoke. It must use a disposable local Runtime/SQLite fixture and dummy token only.
- Existing Runtime API controller/service JUnit remains the source of detailed payload/business correctness；
  Playwright should cover representative flows, not duplicate every API permutation.

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated
- assurance_rationale: 本事项扩大 auth-code 的可选保护范围并在浏览器持有共享管理 secret；鉴权旁路或
  token 泄漏会影响 Runtime 管理与数据访问。
- lightweight_validation:
  - `git diff --check`、changed-path review、token/URL/log 静态扫描；单次预期 `<5m`。
  - focused `RuntimeApiAuthCodeGateTest`、access/config tests；单次预期 `<5m`。
  - Console `npm run typecheck`、focused Vitest；单次预期 `<5m`。
  - `jar tf`/unzip asset inventory and source-vs-dist tracking check；单次预期 `<5m`。
- medium_validation:
  - `mvn -B -ntp -pl foggy-runtime-api,addons/foggy-runtime-console,foggy-mcp-launcher -am test -DskipITs`
    or implementation-equivalent affected reactor；预期 `5-30m`。
  - clean `npm ci && npm run test && npm run build` in Console frontend；预期 `5-30m`。
  - launcher `runtime-console` profile package and local HTTP smoke；预期 `5-30m`。
  - Playwright critical flow against packaged/local Runtime + SQLite；预期 `5-30m`。
- expensive_validation: none required by default.
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；本事项没有 release/tag/publish、数据库语义或广泛公共 SPI 变更。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: only if focused/affected tests cannot prove auth inventory、packaged resource identity
  or no-leak must-pass outcomes.
- maximum_expensive_attempts: 0 without explicit user approval and replan.
- reusable_evidence:
  - current Runtime auth gate tests establish mutation-only baseline but cannot prove `management-all` or browser
    secret handling.
  - current data-viewer Vitest and verification-app Playwright establish component/tool compatibility only；Console
    must provide its own build and critical-flow evidence.
- minimum_revalidation_radius:
  - auth policy/input changes invalidate server auth matrix and Console login E2E；pure visual changes do not.
  - frontend source/build config changes invalidate clean npm build and JAR asset evidence；pure backend test/docs
    changes do not invalidate unchanged browser rendering evidence.
  - launcher dependency/repackage changes invalidate packaged `/console/` smoke but not unrelated Runtime API unit
    correctness.
- stop_when_evidence_is_sufficient:
  - AC-1 至 AC-15 各有直接证据；
  - default mutation compatibility and management-all full inventory both pass；
  - clean frontend/Maven build produces the inspected Addon/launcher artifacts；
  - Playwright proves valid/invalid login、reload、logout、one management mutation and one query result；
  - static/security review finds no token in URL、logs、localStorage、errors or tracked generated dist；
  - independent signoff finds no module-boundary or security blocker.
- validation_not_required:
  - full root release authority、source seal、artifact promotion、all database matrix、remote CI、real customer IAM、
    multi-user concurrency、SSO、tag、release、publish and push.
- stop_or_replan_conditions:
  - implementation requires weakening `management-all`、accepting token in URL/cookie、mapping auth-code to data
    Authorization、placing API logic in launcher/Console or introducing a production Node service；
  - clean build cannot reuse viewer without internal-path coupling or generated dist tracking；
  - two consecutive medium/expensive validations fail for environment/toolchain reasons and evidence cannot be
    recovered with a smaller focused lane.

## Waiver Policy

- waivable_items:
  - non-critical responsive polish、secondary empty states、advanced editor shortcuts and optional pivot controls.
- authorized_role: repository owner
- non_waivable_guards:
  - server-side `management-all` enforcement and compatible `mutations` default.
  - access-check correctness and no token in URL/log/error/localStorage.
  - Runtime auth-code and data-plane Authorization separation.
  - no production Node/BFF and no Console business API in launcher/viewer.
  - clean build、JAR static asset identity and representative Playwright flow.
- required_risk_record: any waiver must record bounded UI impact、detection、fallback、owner and follow-up；安全、
  权限或 secret handling 失败不得写成通过。

## Risks and Open Questions

- known_risks:
  - `sessionStorage` 仍可被同源 XSS 读取；必须依赖 CSP、无 inline third-party script、依赖审查和
    不渲染未转义服务端内容降低风险。
  - `management-all` 是共享 secret gate，不提供用户归属、审计、细粒度授权或轮换；这属于明确
    产品边界，不应在 UI 中暗示企业 IAM。
  - Console 查询页调用的模型可能另需数据面 `Authorization`；MVP 不持久化第二 token，受保护模型
    应按现有权限合同拒绝，不能拿 Runtime code 代替。
  - data-viewer 当前构建产物被忽略，monorepo source alias 必须只走公开 entry；若出现内部路径需求，
    应修复公开组件边界或 replan，而不是复制源码。
  - Vite 7 提高 Node 最低版本；Maven 构建必须固定 toolchain，不能依赖开发机偶然版本。
  - FSScript、SQL 和 datasource 表单能力较高；误操作风险通过 shared token 无法追责，需明确确认和
    安全文案，但审计仍是非目标。
- open_questions: none
- implementation_autonomy:
  - Ultra 可在满足 clean-build、公开 import 和锁版本门槛下选择 exact npm package 或 monorepo public
    source alias。
  - Ultra 可选择经验证的 pinned Maven frontend runner/plugin 版本、组件目录和内部 typed store 结构。
  - Ultra 可调整非安全视觉布局和路由名称，但不得删除已列核心页面闭环或改变固定 `/console/` 入口。

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、`docs/architecture/module-boundaries.md`、Runtime auth/permissions 架构、
  `foggy-runtime-api` controller/security tests、data-viewer frontend package/exports 和 launcher POM。
- 在 scope 内自主决定具体 Java 类、Vue 组件、Maven plugin version 和内部目录；不要生成模块级竞争
  requirement/prompt 文档。
- 先建立 auth-scope/access-check 负向测试、default compatibility baseline 和 clean frontend build，
  再实现 UI 操作。任何中间候选都不能启用未受服务端保护的 Console。
- 真实鉴权只在 `foggy-runtime-api`；Console route guard 只能控制 UX。launcher 只装配。
- 不得把 token 写入测试 snapshot、Playwright trace、screenshot、console log、URL、localStorage 或错误
  序列化。测试使用明显的 dummy secret，并在 evidence 收集前扫描。
- 运行与改动面匹配的 focused、affected Maven、frontend unit 和 Playwright，记录精确命令、测试数、
  结果、证据路径和未运行原因。
- 未经用户明确批准，不运行预计超过 30 分钟或包含 authority/replay/rehearsal/source-seal 的大型
  链路；本事项默认不提出 full-chain 建议。
- 达到 evidence sufficiency 后停止，不为非核心视觉完美扩大矩阵。
- 如需改变目标、页面核心闭环、模块归属、默认兼容、安全/secret 策略、API path 或单 JVM 部署，
  将状态设为 `NEEDS_REPLAN` 并停止相关扩展。
- 完成实现后同步 canonical architecture/dev docs，填写 `Implementation Result`，将状态改为
  `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - 在 `foggy-runtime-api` 新增 `mutations|management-all` 鉴权范围、受保护的
    `GET /api/v1/access/check` 和完整端点清单回归；默认仍为 `mutations`。
  - 新增默认关闭的 `addons/foggy-runtime-console`，包含 fail-closed 启用守卫、`/console/`
    静态资源与安全响应头，以及 Vue 3/TypeScript/Vite 单页 Console。
  - Console 完成登录/session、概览、datasource/namespace、bundle/resource、model、query、
    table/SQL、Compose/CTE 和高风险 FSScript 页面；Runtime 管理凭据与数据面
    `Authorization` 独立。
  - Maven 使用固定 Node/npm 和 lockfile 执行前端 clean build；launcher 仅通过显式
    `runtime-console` profile 装配 Runtime API 与 Console。
- changed_paths:
  - reactor/assembly:
    - `pom.xml`
    - `foggy-mcp-launcher/pom.xml`
  - Runtime API:
    - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/**`
    - `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/**`
  - Console Addon:
    - `addons/foggy-runtime-console/**`
  - canonical docs:
    - `docs/architecture/{README.md,module-boundaries.md,runtime-permissions-and-preaggregation.md}`
    - `docs/dev-guide/bundle-namespace.md`
    - `docs/9.5.2/{README.md,workitems/FEATURE-runtime-web-console-mvp.md}`
- tests_and_results:
  - Runtime API 聚焦命令
    `mvn -B -ntp -pl foggy-runtime-api -Dtest=RuntimeApiAuthCodeGateTest,RuntimeApiAuthScopeConfigurationTest test`：
    14 tests、0 failures、0 errors。
  - Console JUnit：
    `RuntimeConsoleAutoConfigurationTest` 4 tests、`RuntimeConsoleHttpSmokeTest` 2 tests，全部通过。
  - Console frontend clean lane（`addons/foggy-runtime-console/frontend`）：
    `npm ci --no-audit --no-fund`、`npm run typecheck`、`npm run test:unit`、`npm run build`；
    Vitest 4 files / 10 tests 全部通过，类型检查和生产构建通过。
  - `npm run test:e2e`：Playwright desktop Chromium 与 Pixel 7 mobile 共 4 tests，全部通过；
    覆盖无效/有效登录、reload revalidation、logout、响应式导航、datasource 创建和查询结果渲染。
  - `mvn -B -ntp -pl addons/foggy-runtime-console,foggy-mcp-launcher -am -Pruntime-console
    -DskipTests package`：实际执行 29-module reactor 和前端 lifecycle，全部 `SUCCESS`；
    `foggy-runtime-api` 150 tests、Console JUnit 6 tests、launcher 22 tests 均通过，最终
    `BUILD SUCCESS`。
  - `git diff --check` 通过；仅报告两个既有 CRLF 文件的转换提示。
- manual_or_experience_evidence:
  - `jar tf addons/foggy-runtime-console/target/foggy-runtime-console-9.1.0.beta.jar` 确认包含
    `META-INF/resources/console/index.html` 和 `assets/`；launcher 可执行 JAR 的 `BOOT-INF/lib`
    同时包含 `foggy-runtime-api-9.1.0.beta.jar` 与 `foggy-runtime-console-9.1.0.beta.jar`。
  - 使用打包后的 launcher、`lite` profile 和临时 SQLite 实例完成真实 HTTP smoke：
    `/console` 为 302 至 `/console/`，`/console/` 为 200，并返回 CSP、`no-referrer`、
    `nosniff` 和 `no-store`。
  - 同一实例中 access check 对缺失/错误管理凭据返回 401，对有效测试凭据返回 200 和
    `Cache-Control: no-store`；`management-all` 下 capabilities 对缺失凭据返回 401、有效凭据
    返回 200。实例随后优雅停止，临时 SQLite 文件已删除。
  - 源码静态检查未发现 token 写入 localStorage、URL 或 console log；数据面
    `Authorization` 仅保存在易失内存。Git 未跟踪 Console `dist`、Playwright report 或
    test-results。
  - Console 仅从 `foggy-data-viewer` 公开入口导入 `DataTable` 和
    `EnhancedColumnSchema`，未依赖 ignored dist 或内部组件路径。
- deviations: none
- residual_risks:
  - `sessionStorage` 中的共享管理凭据仍可被同源 XSS 读取；本次通过严格 CSP、无第三方运行时
    script 和不渲染未转义凭据降低风险，但账号、轮换、RBAC 与审计仍明确不在 MVP 范围。
  - Vite 对 Element Plus/vxe 聚合 chunk 给出大于 500 kB 的非阻断警告；当前不影响功能、
    延迟加载页面或 JAR 交付，后续可按实际首屏性能数据再拆包。
- reused_evidence:
  - 复用现有 Runtime Controller/service、launcher smoke、viewer 公开组件和 SQLite lite
    基线；新增测试负责覆盖 auth-scope、Console session、前端适配与打包边界。
- omitted_validation_and_reason:
  - 未运行 release/tag/publish、真实客户 IAM、全数据库矩阵或远程 CI；交付契约明确不要求，
    focused、affected reactor、浏览器和真实打包 HTTP 证据未触发升级条件。
  - 独立交付签收尚未执行；本文件只推进到 `READY_FOR_SIGNOFF`，未设置 `ACCEPTED`。
- readiness: READY_FOR_SIGNOFF

## References

- repository rules: `CLAUDE.md`
- canonical module boundaries: `docs/architecture/module-boundaries.md`
- Runtime/model lifecycle: `docs/architecture/runtime-and-model-lifecycle.md`
- Runtime permissions: `docs/architecture/runtime-permissions-and-preaggregation.md`
- prior auth gate: `docs/9.2.1/workitems/P1-runtime-api-auth-code-management-gate.md`
- prior auth inventory: `docs/9.2.1/workitems/P1-runtime-api-auth-code-technical-closure.md`
- current iteration style: `docs/9.5.1/README.md`, `docs/9.5.1/workitems/`
- viewer frontend: `addons/foggy-data-viewer/frontend/package.json`,
  `addons/foggy-data-viewer/frontend/src/index.ts`
- viewer consumer baseline: `addons/foggy-data-viewer/verification-app/package.json`
- Runtime auth implementation: `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/security/`
- Runtime API routes: `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/RuntimeApiRoutes.java`
