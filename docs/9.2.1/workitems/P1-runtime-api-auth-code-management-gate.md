# Runtime API Auth-Code Management Gate

## 文档作用

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: 记录 Runtime API 管理写操作的轻量授权码收敛方案、实现范围、测试要求和进度。

## Metadata

- version: 9.2.1
- priority: P1
- status: signed-off
- source_type: security-follow-up
- owner_modules: `foggy-runtime-api`, `foggy-fsscript`
- created_at: 2026-06-25
- updated_at: 2026-06-25

## Background

9.2.1 第一批 FSScript 稳定性工作已签收，但明确排除了 `/api/bundles` 管理接口暴露和鉴权策略。

后续盘点发现：

- `foggy-runtime-api` 当前只有 `securityMode = none-dev-test-only` 描述字段，没有真正的授权码配置或请求校验。
- `/api/v1/bundles`、datasource mutation、resource save、model refresh/validate 等 Runtime API 管理写操作可以在 Runtime API 启用后直接调用。
- `foggy-fsscript` 旧的 `/api/bundles/**` Controller 是独立入口，如果默认暴露，会绕过 runtime-api 的路径治理。

## Target Outcome

实现一个非 RBAC、非多角色的轻量 gate：

- 配置 Runtime API 授权码后，受保护的 Runtime 管理写操作必须携带授权码。
- 支持 `X-Foggy-Runtime-Code: <code>`，并兼容 `Authorization: Bearer <code>`。
- 未配置授权码且未声明 auth-code 模式时，保持 dev/test 默认兼容。
- 声明 `securityMode=auth-code` 但缺少授权码时，管理写操作 fail-closed。
- 旧 `/api/bundles/**` 不再默认暴露，避免绕过 Runtime API gate。

## Protected Operation Scope

| Area | Endpoint Pattern | Methods | Reason |
|---|---|---|---|
| Runtime bundle registry | `/api/v1/bundles`, `/api/v1/bundles/{name}` | POST, PUT, DELETE | 添加、更新、删除运行时 Bundle |
| Runtime datasource registry | `/api/v1/datasources`, `/api/v1/datasources/{name}` | POST, PUT, DELETE | 写入或删除运行时数据源 |
| Namespace datasource binding | `/api/v1/namespaces/{namespace}/datasource` | PUT | 修改命名空间数据源绑定 |
| Runtime resource write | `/api/v1/resources/save` | POST | 写入 TM/QM/FSScript 资源 |
| Model lifecycle mutation | `/api/v1/models/validate`, `/api/v1/models/refresh` | POST | 临时装载 Bundle 或清理/刷新模型缓存 |
| Legacy FSScript bundle API | `/api/bundles/**` | POST, DELETE | 旧管理入口，默认关闭；与 Runtime API 同时启用时由 Runtime API gate 覆盖 |

## Non-Goals

- 不引入用户、角色、租户级权限模型。
- 不处理查询类 API 的数据读取鉴权，例如 `query.execute`、`sql.query`。
- 不改变 FSScript 沙箱、反射权限、脚本能力白名单。
- 不设计授权码轮换、密钥托管或审计日志。

## Acceptance Criteria

- 默认未配置授权码时，现有 Runtime API 单元测试行为保持兼容。
- 配置 `foggy.runtime-api.auth-code` 后，受保护写操作缺少授权码返回拒绝响应。
- 授权码错误时返回拒绝响应，且不进入 Controller 业务逻辑。
- `X-Foggy-Runtime-Code` 正确时允许受保护写操作。
- `Authorization: Bearer` 正确时允许受保护写操作。
- 配置授权码后 `/api/v1/capabilities` 返回的 effective security mode 为 `auth-code`。
- `securityMode=auth-code` 但未配置授权码时，受保护写操作 fail-closed。
- 旧 `foggy-fsscript` `/api/bundles/**` Controller 默认不装配。
- 单元测试覆盖通过，文档同步更新。

## Constraints

- 保持轻量实现，优先集中拦截，不在每个 Controller 内散落鉴权逻辑。
- 授权码不得写入日志或响应体。
- 错误响应使用 Runtime API `RuntimeEnvelope` 风格。
- 旧接口默认关闭属于安全收敛，若业务仍需要旧接口，必须显式配置开启。

## Progress Tracking

### Development Progress

| Item | Status | Notes |
|---|---|---|
| Runtime auth-code properties | done | Added `authCode`, `isAuthCodeRequired()`, and effective security mode helpers. |
| Runtime API interceptor/config | done | Added centralized management mutation gate for protected paths. |
| Legacy FSScript bundle API default-off | done | Added conditional property on old Controller. |
| Launcher config | done | Added `FOGGY_RUNTIME_API_SECURITY_MODE` and `FOGGY_RUNTIME_API_AUTH_CODE` bindings. |
| Documentation update | done | Workitem and dev-guide deployment note updated. |

### Testing Progress

| Test Area | Status | Required Evidence |
|---|---|---|
| Default compatibility | pass | `mvn -pl foggy-runtime-api test` passed 38 tests without auth-code on existing coverage. |
| Missing/wrong auth-code rejection | pass | `RuntimeApiAuthCodeGateTest` rejects protected mutation before business logic. |
| Header and Bearer success path | pass | `RuntimeApiAuthCodeGateTest` allows correct `X-Foggy-Runtime-Code` and `Authorization: Bearer`. |
| Fail-closed config | pass | `RuntimeApiAuthCodeGateTest` covers `securityMode=auth-code` with blank code. |
| Legacy controller default-off | pass | `BundleManagementControllerConditionalTest` covers missing property does not register Controller. |
| FSScript regression | pass | `mvn -pl foggy-fsscript test` passed 362 tests. |

### Quality Gate Progress

- quality_status: reviewed
- quality_decision: ready-for-coverage-audit
- quality_record: `docs/9.2.1/quality/P1-runtime-api-auth-code-management-gate-implementation-quality.md`
- blocking_findings: none
- follow_up_required: no

### Coverage Audit Progress

- coverage_status: reviewed
- coverage_conclusion: ready-for-acceptance
- coverage_record: `docs/9.2.1/coverage/P1-runtime-api-auth-code-management-gate-coverage-audit.md`
- blocking_gaps: none
- follow_up_required: no

### Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: `docs/9.2.1/acceptance/P1-runtime-api-auth-code-management-gate-acceptance.md`
- blocking_items: none
- follow_up_required: no

### Experience Progress

- experience: N/A
- reason: 纯后端 API 安全收敛，无 UI 页面、表单、按钮或可视化交互变化。

## Execution Check-In

- completed_work: Runtime API auth-code gate implemented; legacy FSScript bundle management Controller default-off; launcher and dev-guide docs updated.
- touched_code_paths:
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/config/FoggyRuntimeApiProperties.java`
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/security/RuntimeApiAuthInterceptor.java`
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/security/RuntimeApiSecurityConfiguration.java`
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeCapabilitiesController.java`
  - `foggy-fsscript/src/main/java/com/foggyframework/bundle/controller/BundleManagementController.java`
  - `foggy-mcp-launcher/src/main/resources/application.yml`
  - `docs/dev-guide/bundle-namespace.md`
- self_check:
  - scope implemented as intended: yes
  - non-goals preserved: yes
  - authorization code not logged or returned: yes
  - default compatibility preserved: yes
  - conclusion: formal-quality-gate-complete
- test_status:
  - `mvn -pl foggy-runtime-api -Dtest=RuntimeApiAuthCodeGateTest test`: pass, 6 tests
  - `mvn -pl foggy-fsscript -Dtest=BundleManagementControllerConditionalTest test`: pass, 2 tests
  - `mvn -pl foggy-runtime-api test`: pass, 38 tests
  - `mvn -pl foggy-fsscript test`: pass, 362 tests
- remaining_risks:
  - Query/read endpoints such as `query.execute` and `sql.query` remain outside this workitem by design.
  - Auth-code is a shared secret, not per-user authorization; audit, rotation, and credential storage are still out of scope.
  - If legacy `/api/bundles/**` is explicitly re-enabled in a standalone FSScript-only app without runtime-api, deployment must provide its own protection.
- quality_status: reviewed
- quality_decision: ready-for-coverage-audit
- quality_record: `docs/9.2.1/quality/P1-runtime-api-auth-code-management-gate-implementation-quality.md`
- coverage_status: reviewed
- coverage_conclusion: ready-for-acceptance
- coverage_record: `docs/9.2.1/coverage/P1-runtime-api-auth-code-management-gate-coverage-audit.md`
- acceptance_status: signed-off
- acceptance_decision: accepted
- acceptance_record: `docs/9.2.1/acceptance/P1-runtime-api-auth-code-management-gate-acceptance.md`
- acceptance_readiness: accepted
