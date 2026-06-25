# Runtime API Auth-Code Technical Closure

## 文档作用

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: 记录 Runtime API 授权码 gate 的技术覆盖复核、补齐项、测试证据和小验收状态。

## Metadata

- version: 9.2.1
- priority: P1
- status: signed-off
- source_type: technical-closure
- owner_modules: `foggy-runtime-api`
- created_at: 2026-06-25
- updated_at: 2026-06-25

## Background

在 `P1-runtime-api-auth-code-management-gate` 签收后，用户明确当前目标不是面向客户的完整安全体系，也不需要引入 RBAC、用户权限、轮换、审计或安全分层方案。

本次只对技术边界负责：配置授权码后，Runtime API 管理面应由同一个 auth-code gate 收口；查询、SQL、compose、fsscript 执行等非管理面入口不在本次扩展范围内。

## Coverage Review

### Protected By Auth-Code Gate

| Area | Endpoint Pattern | Methods | Closure Result |
|---|---|---|---|
| Runtime bundle registry | `/api/v1/bundles` | POST | covered |
| Runtime bundle registry | `/api/v1/bundles/{name}` | PUT, DELETE | covered |
| Runtime datasource registry | `/api/v1/datasources` | POST | covered |
| Runtime datasource registry | `/api/v1/datasources/{name}` | PUT, DELETE | covered |
| Runtime datasource management probe | `/api/v1/datasources/{name}/test` | POST | newly covered in this closure |
| Namespace datasource binding | `/api/v1/namespaces/{namespace}/datasource` | PUT | covered |
| Runtime resource write | `/api/v1/resources/save` | POST | covered |
| Model lifecycle mutation | `/api/v1/models/validate` | POST | covered |
| Model lifecycle mutation | `/api/v1/models/refresh` | POST | covered |
| Legacy FSScript bundle API | `/api/bundles/add` | POST | covered when runtime-api interceptor is active |
| Legacy FSScript bundle API | `/api/bundles/remove/{bundleName}` | DELETE | covered when runtime-api interceptor is active |

### Intentionally Outside This Gate

| Area | Endpoint Pattern | Methods | Reason |
|---|---|---|---|
| Capabilities | `/api/v1/capabilities` | GET | Discovery endpoint remains open. |
| Bundle/datasource read | `/api/v1/bundles`, `/api/v1/datasources`, `/api/v1/namespaces/{namespace}/datasource` | GET | Read-only management discovery, not a mutation. |
| Model metadata | `/api/v1/models`, `/api/v1/models/{model}/describe` | GET, POST | Metadata/query support, not management mutation. |
| Runtime resource export | `/api/v1/resources/export` | POST | Read/export path; write path is protected. |
| Dataset query | `/api/v1/query/{model}/validate`, `/api/v1/query/{model}/execute` | POST | Query/read execution remains a non-goal. |
| Table/SQL read probes | `/api/v1/tables/list`, `/api/v1/tables/inspect`, `/api/v1/sql/query` | POST | Read-only data/source inspection remains a non-goal. |
| Compose/FSScript execution | `/api/v1/compose/**`, `/api/v1/fsscript/execute` | POST | Script/query execution boundary is separate from management gate. |

## Target Outcome

- 管理面受保护路径清单有单元测试锁定。
- `POST /api/v1/datasources/{name}/test` 作为 datasource 管理探测入口纳入 auth-code gate。
- `X-Foggy-Runtime-Code` 与 `Authorization: Bearer <code>` 契约保持不变。
- 配置授权码后，受保护管理操作缺少或错误授权码时仍被拦截。
- 未配置授权码且未声明 `securityMode=auth-code` 时继续保持 dev/test 默认兼容。
- 不引入复杂权限方案，不扩大到查询读取接口授权。

## Task Split / Ownership

| Task | Owner Module | Status | Notes |
|---|---|---|---|
| Runtime API endpoint inventory | `foggy-runtime-api` | done | Controller mapping 已盘点并分类。 |
| Datasource test gate补齐 | `foggy-runtime-api` | done | `POST /api/v1/datasources/{name}/test` 纳入拦截器。 |
| Auth-code inventory tests | `foggy-runtime-api` | done | 新增管理面覆盖清单测试和非目标边界测试。 |
| Capabilities wording | `foggy-runtime-api` | done | warning 从 mutating management operations 调整为 management operations。 |
| Dev guide wording | docs | done | 文档改为管理操作授权码配置。 |

## Acceptance Criteria

- 配置 `foggy.runtime-api.auth-code` 后，管理面覆盖清单中的路径缺少授权码会被拒绝。
- `POST /api/v1/datasources/{name}/test` 缺少授权码时返回 `RUNTIME_AUTH_REQUIRED`。
- 查询、SQL、compose、fsscript 执行和只读/导出路径不被本次管理 gate 意外拦截。
- capabilities warning 与当前管理操作口径一致。
- 单元测试通过，文档同步更新。

## Constraints / Non-Goals

- 不设计 RBAC、用户、角色、租户级权限。
- 不设计授权码轮换、密钥托管或审计日志。
- 不处理查询读取、SQL 查询、compose/fsscript 执行入口鉴权。
- 不改变旧 `/api/bundles/**` 默认关闭策略。

## Progress Tracking

### Development Progress

| Item | Status | Notes |
|---|---|---|
| Endpoint inventory | done | Runtime API Controller POST/PUT/DELETE 映射已逐项分类。 |
| Interceptor update | done | Datasource test path 已纳入 pattern/path 双分支保护。 |
| Capabilities warning | done | 已去掉只适用于 mutation 的 wording。 |
| Documentation update | done | Workitem、README、dev-guide 已更新。 |

### Testing Progress

| Test Area | Status | Required Evidence |
|---|---|---|
| Focused auth-code tests | pass | `mvn -pl foggy-runtime-api -Dtest=RuntimeApiAuthCodeGateTest test`: 9 passed. |
| Runtime API full regression | pass | `mvn -pl foggy-runtime-api test`: 41 passed. |
| Datasource test rejection | pass | `RuntimeApiAuthCodeGateTest#shouldRejectDatasourceTestWithoutAuthCode`. |
| Management inventory gate | pass | `RuntimeApiAuthCodeGateTest#shouldRequireAuthCodeForRuntimeManagementOperationInventory`. |
| Non-goal boundary | pass | `RuntimeApiAuthCodeGateTest#shouldLeaveReadAndExecutionEndpointsOutsideManagementAuthGate`. |

### Experience Progress

- experience: N/A
- reason: 纯后端 API gate 技术收口，无 UI 页面、表单、按钮或可视化交互变化。

## Execution Check-In

- completed_work: Runtime API 管理面覆盖复核完成；datasource test 管理探测入口已纳入 auth-code gate；测试和文档已补齐。
- touched_code_paths:
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/security/RuntimeApiAuthInterceptor.java`
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeCapabilitiesController.java`
  - `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/RuntimeApiAuthCodeGateTest.java`
  - `docs/dev-guide/bundle-namespace.md`
- self_check:
  - management gate scope reviewed: yes
  - auth-code contract preserved: yes
  - non-goals preserved: yes
  - no complex permission model introduced: yes
  - conclusion: self-check-only
- test_status:
  - `mvn -pl foggy-runtime-api -Dtest=RuntimeApiAuthCodeGateTest test`: pass, 9 tests
  - `mvn -pl foggy-runtime-api test`: pass, 41 tests
- remaining_risks:
  - Query/read and script execution endpoints remain outside management gate by design.
  - Auth-code remains a shared secret rather than a customer-facing permission model.
- acceptance_readiness: accepted

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: `docs/9.2.1/acceptance/P1-runtime-api-auth-code-technical-closure-acceptance.md`
- blocking_items: none
- follow_up_required: no
