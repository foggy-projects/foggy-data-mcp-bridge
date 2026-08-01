---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.3
ticket: FEATURE-runtime-authoring-workspace-api
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: owner
approved_at: 2026-07-31
open_questions: []
---

# Delivery Spec: Runtime authoring workspace API

## Document Purpose

- intended_for: ultra-implementation / independent-signoff
- purpose: 冻结单 Runtime、单 Namespace、单 writable Bundle 的持久草稿工作区、不可变 revision、
  TM/QM/FSScript 资源、diff、detached validation 和 candidate query 公共 API 契约。
- canonical_path:
  `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-api.md`

## Goal

- version_goal: 在已验收的 detached authoring foundation 和 candidate-query internal primitive 上，
  建立不触碰 live Bundle/catalog 的 Runtime-local 模型创作工作区 API，供后续 Console 手工闭环复用。
- target_outcome:
  - 创建不可猜测的持久 workspace，固定一个目标 Namespace、一个 enabled Runtime-managed external
    Bundle、`baseBundleRevision` 和 `baseNamespaceSourceRevision`。
  - 以不可变源码快照和内容寻址 `candidateRevision` 保存 `.tm`、`.qm`、`.fsscript`；保存、删除、
    diff、validate 和 query 都显式 pin revision。
  - 完整 detached model validation 和普通 JDBC candidate query 使用同一候选 revision、生产 loader、
    数据源与权限路径，不创建临时 Namespace、持久 candidate catalog 或 query result。
  - Bundle inventory 返回事实型来源与可编辑能力；configured external、JAR/classpath 永远不能进入
    workspace 编辑或被草稿同名资源遮蔽。
- critical_outcomes:
  - 路径、symlink、quota、乐观并发和全批次原子性是不可豁免的持久化安全边界。
  - `workspaceId` 不是权限身份；所有 workspace 读写、validate/query 路由均由管理凭据保护，业务
    `Authorization` 不能提升为管理权限。
  - source/head 任一漂移都 fail closed；不得重新读取可移动草稿目录、复活 selected Bundle 中已删除
    的 live 文件、回退 live 同名 QM 或污染 live catalog/cache/inventory/source revision。
- success_is_sufficient_when: AC-1 至 AC-10 全部通过；真实临时 Bundle、重启恢复、并发/故障注入、
  真实 SQLite candidate query、HTTP auth/envelope 与 affected engine/Runtime lanes 均有自动化证据。

## Scope

- in_scope:
  - Runtime API 的 authoring workspace routes、DTO、稳定错误、持久 store、配置、revision/state 编排。
  - Runtime-managed external Bundle 的安全基线快照，以及 `.tm`、`.qm`、`.fsscript` resource
    list/read/save/delete 和 base-to-candidate diff。
  - 完整 TM/QM/FSScript detached validation；普通 JDBC candidate query validate/execute 复用已验收
    `RuntimeCandidateQueryService` 和 engine candidate session。
  - selected Bundle 的完整替换视图：候选中删除的资源不能从 selected live Bundle fallback；其他
    Bundle 只读依赖仍按目标 Namespace 复用。
  - `/bundles` 的完整 live inventory 与事实型 capability 扩展，包括当前 JAR/classpath Bundle。
  - 与新 API 直接相关的 auth inventory、RuntimeEnvelope、持久化、隔离、并发和安全回归测试。
- affected_modules:
  - `foggy-runtime-api`
  - `foggy-dataset-model-engine`（仅在完成 selected-Bundle replacement/deletion 或共享内部 revision
    guard 所必需时；不得扩展公共 Model SPI）
  - `foggy-fsscript`（仅在完整只读 Bundle inventory 需要收窄的内部适配时）
  - `docs/architecture`
  - `docs/9.5.3`
- external_dependencies: none；复用现有 Java 17、Spring Boot、Jackson、JUnit、SQLite 和文件 API。

## Non-Goals

- out_of_scope:
  - publish、apply、refresh、promotion、release package、rollback、rebase 或 revision 合并。
  - Console/Vue/Playwright、AI Agent、VS Code 插件、Git clone/branch/commit/push 或跨 Runtime 控制面。
  - JAR 多 Namespace binding、JAR/configured external fork、编辑、覆盖、升级或热切换。
  - 临时 Namespace、持久 candidate catalog/cache、compiled model、query result 或查询历史。
  - candidate pivot、Compose/CTE、Semantic SQL、memory-grid、pre-aggregation、hybrid query、synthetic
    member；继续返回已验收的稳定 unsupported 错误。
  - 多用户账号、workspace owner/RBAC、审批、审计或把共享 auth-code 描述为生产 IAM。
- do_not_touch:
  - 现有 `/api/v1/query/*`、`/api/v1/resources/save`、`/api/v1/models/validate` 的请求与行为语义。
  - `addons/foggy-runtime-console/frontend`
  - `foggy-mcp-launcher`
  - Model SPI v2 公共模块、Maven 依赖图和 datasource/query 权限语义。
  - 已签收 spike、candidate feature/BUG 的 Implementation Result 与历史 acceptance 记录。
- non_blocking_or_waivable_items:
  - nested/fat-JAR packaging 不由本 workitem 新建全矩阵；现有标准 `jar:` evidence 可复用，但实际
    live JAR/classpath Bundle 必须在 inventory 中按事实只读展示。
  - candidate 高级模式保持 fail-closed unsupported 是明确 non-goal，不是交付失败。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 公共前缀固定为 `/api/v1/authoring/workspaces` | 与 live model/query/resource API 隔离，给后续 Console 一个唯一 authoring contract | 不改造现有 query/models/resources routes |
| workspace 由当前 Runtime 本地拥有 | 当前共享 auth-code 没有用户 identity | `workspaceId` 不能作为 auth、Namespace 或 Bundle 名 |
| ID 由服务端 CSPRNG 生成，至少 128 bit entropy | 防止枚举和路径注入 | opaque、URL-safe；不接受 caller-supplied ID |
| source 只接受 enabled Runtime-managed external real directory | 首期只写一个受 Runtime registry 管理的 Bundle | configured external、JAR/classpath、disabled/path mismatch/symlink 均拒绝 |
| create 复制完整允许资源集，不直接编辑 active 目录 | 草稿与 live source 生命周期分离 | 初始 candidate 可为空；workspace API 永不返回内部绝对路径 |
| base 与 candidate 使用同一 canonical content hash | API pin 必须与 engine candidate identity 相同 | 非空 candidate 的 revision 必须等于 candidate session 返回值 |
| 每次变更创建新 immutable revision 后再原子切换 head | query/validate 不读取可移动目录 | 不可原地修改已暴露 revision；失败不产生可见半成品 |
| `DRAFT`、`VALIDATED`、`STALE`、`DISCARDED` 是首期完整状态集 | publish 尚未进入本 workitem | `STALE` 无本期 rebase；保留草稿并提示新建 workspace |
| validate 成功是 query execute 的前置条件 | 防止只验证一个 QM 时忽略同 Bundle 其他损坏资源 | evidence 必须绑定 exact candidate/base revisions |
| selected Bundle 在 detached view 中被候选完整替换 | 删除资源也是草稿语义 | 缺失资源不能 fallback 到 selected live Bundle；其他 Bundle 仍只读 fallback |
| API 只承诺 base 和 current candidate 可寻址 | 控制 immutable revision 的磁盘增长 | 旧 head 在无 in-flight reader 后可回收；不提供 revision history API |
| 全部 workspace routes 强制管理 auth-code | 草稿内容、diff、验证和查询均为作者/管理面 | 即使 `auth-scope=mutations`，GET/query 也保护；`Authorization` 只用于数据面 |
| Bundle capability 是服务端事实 | Console 不应从 path/source 文本猜可编辑性 | 现有字段保留；新增字段是 additive JSON contract |

## Public API Contract

### Routes

| Method | Route | Contract |
|---|---|---|
| `POST` | `/api/v1/authoring/workspaces` | 创建 workspace；body 只接受 `namespace`、`sourceBundle` |
| `GET` | `/api/v1/authoring/workspaces` | 列出 workspace metadata；可按 `namespace`、`state` 过滤，默认不列 `DISCARDED` |
| `GET` | `/api/v1/authoring/workspaces/{workspaceId}` | 读取 metadata 并执行 source currentness 检查 |
| `DELETE` | `/api/v1/authoring/workspaces/{workspaceId}?expectedCandidateRevision=...` | 原子转为 `DISCARDED`；状态终结且不可执行 |
| `GET` | `/api/v1/authoring/workspaces/{workspaceId}/resources?candidateRevision=...` | 列 current pinned revision 的资源 metadata |
| `GET` | `/api/v1/authoring/workspaces/{workspaceId}/resources/content?path=...&candidateRevision=...` | 读取一个 UTF-8 资源；path 使用 query value，不使用 wildcard filesystem path |
| `POST` | `/api/v1/authoring/workspaces/{workspaceId}/resources/save` | body=`expectedCandidateRevision` + `files[{path,content}]`，全批次 upsert |
| `POST` | `/api/v1/authoring/workspaces/{workspaceId}/resources/delete` | body=`expectedCandidateRevision` + `paths[]`，全批次删除；不存在 path 拒绝整批 |
| `POST` | `/api/v1/authoring/workspaces/{workspaceId}/diff` | body=`candidateRevision`、可选 `includeContent=false`；只比较 immutable base 与 pinned current candidate |
| `POST` | `/api/v1/authoring/workspaces/{workspaceId}/validate` | body=`candidateRevision`；完整 detached validation，并记录 exact revision evidence |
| `POST` | `/api/v1/authoring/workspaces/{workspaceId}/query/{model}/validate` | body=`candidateRevision` + `request`；普通 JDBC validate，复用 candidate service |
| `POST` | `/api/v1/authoring/workspaces/{workspaceId}/query/{model}/execute` | body=`candidateRevision` + `request`；普通 JDBC execute，复用 candidate service与业务 `Authorization` |

所有请求和响应继续使用现有 `RuntimeEnvelope`。auth interceptor 的 401/503 HTTP 行为不变；通过
auth 后的业务失败沿用 Runtime API 的 HTTP 200 + `success=false` 约定。客户端不能提交
`candidatePath`、base revision、Namespace 或 source Bundle 来覆盖 workspace metadata。

### Workspace and Revision Fields

- workspace response 至少返回：`workspaceId`、`targetNamespace`、`sourceBundle`、
  `sourceKind=runtime-managed`、`baseBundleRevision`、`baseNamespaceSourceRevision`、
  `candidateRevision`、`state`、`createdAt`、`updatedAt`、可选 `lastValidation` 和 stale diagnostics。
- 时间使用 UTC ISO-8601。revision 使用 `sha256:<lowercase-hex>`；不以时间、目录名或随机值充当
  content revision。
- canonical hash 只覆盖允许资源，按规范化 POSIX 相对路径排序，并覆盖每个路径的 UTF-8 bytes、
  内容长度和原始内容 bytes；不规范化行尾、BOM 或文件内容。空资源集有稳定 SHA-256 identity。
- 实现必须复用或抽取 engine candidate session 的现有 content-revision 算法，不得在 Runtime API 中
  维护一个“看似相同”的第二套 hash；相同非空 snapshot 的 API/engine identity 是强制断言。
- `baseBundleRevision` 和 base snapshot 创建后不变；`candidateRevision` 只在成功 save/delete 后切换。
  非空 revision 交给 candidate session 后必须得到完全相同的 `candidateRevision`。
- create 在复制前后检查 Namespace committed source revision，并核对 selected live Bundle 的真实目录
  identity 与内容 hash；观察到漂移则不创建 workspace。
- 每个读写/validate/query 动作绑定 caller 提交的 current `candidateRevision`。不匹配返回
  `WORKSPACE_REVISION_CONFLICT`；长时 validate/query 在返回前再次核对 head 和 source currentness。
- resource metadata 固定包含 `path/type/size/sha256`；content read 额外包含 UTF-8 `content`。diff
  response 按 path 排序并包含 `changeType=ADDED|MODIFIED|DELETED`、base/candidate SHA-256 以及在
  `includeContent=true` 时的 nullable base/candidate content。任何 mutation response 返回提交后的
  workspace state 和 `candidateRevision`。
- query response 在既有 semantic response 与 candidate identity 外还返回 `workspaceId`、
  `baseBundleRevision` 和 exact requested `candidateRevision`，使客户端不依赖内部 path 推断来源。

### State Transitions

| Event | Result |
|---|---|
| create | `DRAFT`；initial candidate 等于 base snapshot |
| successful save/delete | 新 revision；旧 validation evidence 失效；`DRAFT`，若已 stale 则仍为 `STALE` |
| complete validation success | exact current revision 转为 `VALIDATED`，记录通过 evidence |
| validation failure | 保持/回到 `DRAFT`，记录 sanitized failure evidence；不得部分发布 catalog |
| Namespace source 或 selected live Bundle content/identity drift | 原子转为 `STALE`；validate/query fail closed |
| candidate query validate/execute | 不改变 content/state，不持久化 query payload/result |
| discard | `DISCARDED` 终态；仅 metadata get/list 可见，不再允许 resource/diff/validate/query |

本 workitem 不提供 rebase。`STALE` workspace 可继续受 auth 保护地读取、保存、删除资源和查看 diff，
但状态保持 `STALE`；恢复执行的首期方式是创建新 workspace 并显式迁移所需内容。

### Resource and Store Safety

- 仅允许小写后缀 `.tm`、`.qm`、`.fsscript`；路径必须是使用 `/` 的 canonical relative path。
  拒绝 absolute path、反斜杠、空/`.`/`..` segment、重复分隔符、NUL/control character、规范化后重复
  path 和 case-fold collision。create 时遇到非严格 UTF-8 的允许资源同样 fail closed。
- workspace root、revision root、现有目标和任一父级出现 symlink 时 fail closed；real path 必须保持
  在配置 root 内。API、日志和错误不得返回 store/candidate/live Bundle 的内部绝对路径。
- 默认配置组固定为：
  - `foggy.runtime-api.authoring-workspaces.path=.foggy-runtime/authoring-workspaces`
  - `max-active-workspaces=128`
  - `max-resources-per-revision=512`
  - `max-resource-bytes=1048576`
  - `max-revision-bytes=16777216`
  - `max-batch-operations=128`
  - `max-path-bytes=512`
- limits 必须在 `/capabilities` 或 authoring capability data 中可观察；实现测试可降低配置值验证边界。
- 每批先完成 path/type/duplicate/overlay/quota/expected revision 全量校验，再写 staging revision；metadata
  与 head 原子提交。任一 validation、I/O、metadata persistence 或 atomic move 失败后，旧 head、状态、
  evidence 和所有可见文件完全不变，临时文件可安全清理。
- store metadata 有显式 schema version；Runtime 重启后恢复 exact metadata/revisions/state。未知 schema、
  hash mismatch、缺文件、越界 path 或 symlink 返回稳定 store corruption，不静默跳过或读取。
- store 只需长期保留 immutable base 和 current candidate；旧 head/evidence source 在无 in-flight reader
  后回收，启动时清理无 metadata 引用的 staging/orphan revision，不能形成无界 revision history。
- 并发保证范围是同一 Runtime 进程内的请求；多个进程或共享 NFS 同时使用一个 workspace root 是
  non-goal，必须在文档中明确禁止。

### Validation, Query and Overlay

- 完整 model validation 先通过 request-local production FSScript loader load/parse 每个 `.fsscript`
  及其 imports，再遍历所有 TM 和 QM 并构建依赖；未被 TM/QM import 的脚本也不能逃逸 syntax/import
  validation。空 candidate 或任一错误均不能进入 `VALIDATED`。
- validation evidence 至少记录 candidate/base revisions、时间、文件/通过/失败/级联计数和 sanitized
  issues；内容变化使 evidence 失效。stack trace、Authorization、内部 path、连接信息和 secret 不持久化。
- query validate/execute 只能以 workspace metadata 组装已验收 `RuntimeCandidateQueryService` command；
  server 固定 source Bundle、Namespace、immutable revision directory 与 base source revision。
- query execute 只接受当前 `VALIDATED` exact revision；query validate 同样不得绕过完整 validation
  evidence。两个 phase 均透传独立可选 `Authorization`，保留既有 action、row/field/physical-column、
  datasource 和 cache isolation 语义。
- save、完整 validate、candidate open 和 query 前后均检查 overlay ownership。草稿任何允许资源都只能
  替换 selected Bundle 自身资源；遮蔽 configured external、JAR/classpath 或其他 Runtime-managed
  Bundle 返回 `WORKSPACE_OVERLAY_FORBIDDEN`。
- detached Bundle view 必须把 selected live Bundle 整体替换为 candidate snapshot。candidate 中已删除的
  selected TM/QM/FSScript 不得从 live selected Bundle fallback；缺失应产生 not-found/validation failure。
- validation/query 成功、失败、权限拒绝、database failure、stale、revision conflict 和 close 后均不改变
  live catalog identity、shared FSScript/query cache、Bundle inventory 或 committed source revision。

### Bundle Capability Inventory

- `/api/v1/bundles` 必须列出 `SystemBundlesContext` 当前全部 live Bundle，而不再只列 external；仍合并
  inactive Runtime registry record，且不声称 inactive record 是 live JAR/classpath。
- `BundleInfo` 保留现有字段并 additive 返回：`sourceType`（`external-filesystem`、
  `external-resource`、`jar`、`classpath`）、`editable`、`workspaceEligible`、
  `managedByRuntimeApi`、`namespaceBindings`（首期恰好一个 canonical Namespace）和 opaque
  `sourceIdentity`。非 filesystem Spring resource location 使用 `external-resource`，不得伪装为 JAR
  artifact 或 writable directory。
- 只有 enabled Runtime-managed、live/registry name+Namespace+real path 一致、非 symlink、可读写的
  external filesystem directory 可返回 `editable=true`、`workspaceEligible=true`。configured external、
  JAR/classpath 和 inactive/mismatch record 必须为 false。
- `sourceIdentity` 对同一 loaded artifact/source 稳定且不可包含 secret；客户端只可比较，不得解析。
  它应是 opaque digest/identifier，不是 raw absolute classpath/JAR URL；JAR/classpath path 不得伪装为
  可保存 filesystem path。
- `/capabilities` 的既有 capability map additive 返回 `authoring.workspaces`、
  `authoring.resources`、`authoring.diff`、`authoring.validate`、`authoring.query`=`supported`，并在 data
  中 additive 返回 `authoringWorkspaceLimits` 对象及上述 configured quota；明确不报告
  publish/apply/rollback/Git/JAR binding 为已支持。

### Stable Error Families

至少冻结以下 code；message 可改进但不能用异常类名或绝对路径替代稳定 code：

- `WORKSPACE_INVALID_REQUEST`
- `WORKSPACE_NOT_FOUND`
- `WORKSPACE_STATE_INVALID`
- `WORKSPACE_SOURCE_INELIGIBLE`
- `WORKSPACE_LIMIT_EXCEEDED`
- `WORKSPACE_REVISION_CONFLICT`
- `WORKSPACE_RESOURCE_PATH_INVALID`
- `WORKSPACE_RESOURCE_TYPE_UNSUPPORTED`
- `WORKSPACE_RESOURCE_NOT_FOUND`
- `WORKSPACE_OVERLAY_FORBIDDEN`
- `WORKSPACE_STALE`
- `WORKSPACE_NOT_VALIDATED`
- `WORKSPACE_VALIDATION_FAILED`
- `WORKSPACE_STORE_FAILURE`
- `WORKSPACE_STORE_CORRUPT`

既有 candidate `CANDIDATE_*` code 在 workspace query mapping 后保持可辨识，不折叠为通用 500。
对 `DISCARDED` workspace 的 resource/diff/validate/query 调用返回 `WORKSPACE_STATE_INVALID`；只有
authenticated get 或显式包含 discarded 的 list 返回 tombstone metadata。

## Acceptance Criteria

- [x] AC-1: `/bundles` 列出 live external/JAR/classpath 与 inactive managed record，并准确返回
  `sourceType/editable/workspaceEligible/managedByRuntimeApi/namespaceBindings/sourceIdentity`；只有符合
  confirmed eligibility 的 Bundle 可创建 workspace。
- [x] AC-2: create 生成至少 128-bit 不可猜测 ID，原子复制完整 TM/QM/FSScript base snapshot，固定两个
  base revision 和 initial candidate；重启后 exact metadata、state、base/head 内容可恢复，且响应/日志
  不暴露内部 path。
- [x] AC-3: resource list/read/save/delete 只接受 exact pinned revision 和 canonical allowed paths；
  traversal、absolute/backslash、symlink、case/normalized duplicate、unsupported type、quota 和不存在删除
  均 fail closed，且多文件批次在 deterministic/persistence/I/O failure 下零部分写。
- [x] AC-4: 并发 save/delete/validate/discard 使用 optimistic revision 与原子 state/head transition；同一
  base 的竞争请求最多一个提交，内容变更生成新 revision、使旧 evidence 失效，immutable revision
  不被原地修改。
- [x] AC-5: get/list/resource/diff 返回稳定、排序、revision-pinned 的 metadata/content；diff 准确表达
  `ADDED/MODIFIED/DELETED` 及 base/candidate hash/可选内容；discard 是终态且不能再执行草稿动作。
- [x] AC-6: 完整 validation 使用真实 production detached loader 覆盖 candidate TM/QM/FSScript、同
  Namespace external/JAR 只读依赖和成功/失败 evidence；仅 exact current revision 成功后进入
  `VALIDATED`，live state 全程不变。
- [x] AC-7: workspace query validate/execute 只通过已验收 `RuntimeCandidateQueryService`，使用 immutable
  server-owned path 和 exact workspace revisions；真实 SQLite 返回草稿结果，Authorization/权限/cache
  guards 保持，未验证 revision 与 unsupported modes 稳定拒绝。
- [x] AC-8: selected Bundle 被 candidate 完整替换；覆盖其他 Bundle 的 TM/QM/FSScript 在 save/validate/
  query 均拒绝，删除 selected live 资源后不会从 live fallback 复活，也不会回退 live 同名 QM。
- [x] AC-9: Namespace committed source、selected Bundle content/identity 或 workspace head 在动作前后任一
  漂移都会持久转为 `STALE` 或返回 revision conflict；不返回迟到 query/validation 成功，不修改 live
  catalog/cache/inventory/revision。
- [x] AC-10: 全部 workspace route 在 `mutations` 和 `management-all` scope 下均要求正确
  `X-Foggy-Runtime-Code`；业务 `Authorization` 单独透传且不能提升管理权限；RuntimeEnvelope、stable
  errors、既有 routes/DTO JSON 字段和 live query/resource/model 行为保持兼容。

## Contract / Data / Security Constraints

- API or event contract: 新 route/DTO/BundleInfo 字段均为 additive；保留 `RuntimeEnvelope`、`X-NS`、
  `X-Foggy-Runtime-Code` 和独立 `Authorization` 语义。create 的有效 Namespace 使用既有
  `X-NS > body.namespace` 优先级且必须显式非空；两者同时提供但 canonical value 不同时返回
  `WORKSPACE_INVALID_REQUEST`，不能静默创建到错误 Namespace。
- data and migration: 新增 versioned filesystem workspace store，无数据库和 Bundle registry schema
  migration；不得把草稿写入 source Bundle。首次版本无需迁移旧 workspace，未知版本 fail closed。
- compatibility and rollback: 回退产品代码后 workspace 目录保持 inert，不影响 live Runtime；不得为
  兼容新 API 改写低层 `/resources/save` 或现有 candidate/live query 行为。
- permissions and secrets: workspace 是 model-author code surface，不是 untrusted script sandbox；管理
  auth 是必要条件但不是数据面身份。不得持久化/记录 auth-code、Authorization、query payload/result、
  JDBC secret、绝对 path 或未经 sanitization 的 stack trace。
- auth fail-closed: workspace route 即使在全局 `none-dev-test-only` 或 `auth-scope=mutations` 下也必须
  要求 configured auth-code；未配置时返回既有 HTTP 503 `RUNTIME_AUTH_CODE_NOT_CONFIGURED`，不能退化为
  匿名 authoring API。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1 | must-pass | major | complete Bundle inventory + eligibility unit/HTTP tests | bundle controller/probe evidence | external/JAR/classpath/inactive matrix and no inferred editability |
| AC-2 | must-pass | critical | real temp store create/reload/corruption tests | Runtime registries | exact restart recovery, entropy shape, snapshot drift rejection |
| AC-3/AC-4 | must-pass | critical | negative path/symlink/quota + multithread + I/O/persist fault injection | resource batch test | zero partial writes and single-winner CAS evidence |
| AC-5 | must-pass | major | DTO/HTTP resource/diff/discard tests | RuntimeEnvelope tests | deterministic ordering/content/status and terminal discard |
| AC-6 | must-pass | major | real external/standard-JAR/FSScript detached validation | 9.5.3 probe/isolation | all-resource evidence tied to exact revision; live identity snapshots |
| AC-7 | must-pass | critical | real SQLite candidate validate/execute + permissions/cache tests | accepted candidate feature/R2 | server-owned path, exact identity, no bypass/fallback |
| AC-8/AC-9 | must-pass | critical | delete/other-owner overlay + source/head drift races | candidate stale/overlay tests | no selected-live resurrection, stable stale/conflict, unchanged live state |
| AC-10 | must-pass | critical | interceptor inventory + random-port route/envelope/secret tests | Runtime auth tests | every workspace method denied/allowed matrix and compatibility lane |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated
- lightweight_validation:
  - `git diff --check` 及每个 untracked 文件的等效 no-index whitespace check，单次 `<5m`。
  - workspace hash/path/quota/state/store focused unit tests，单次 `<5m`。
- medium_validation:
  - Runtime workspace store/service/controller、Bundle capability、auth-code 和 restart/fault/concurrency focused
    lane，预计 `5-15m`。
  - engine candidate replacement/deletion、real SQLite、detached external/JAR/FSScript 与 permission/cache
    affected lane，预计 `5-15m`。
  - Runtime existing bundle/resource/model/query compatibility lane，预计 `5-15m`。
- expensive_validation: none by default；不运行完整 Maven reactor、Console 或数据库矩阵。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；公共契约、安全和持久化由 focused HTTP/fault/concurrency +
  affected engine/Runtime lanes直接覆盖。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若实现需要 publish/refresh、公共 Model SPI、数据库/registry migration、
  Console/launcher 或跨进程共享 store，设置 `NEEDS_REPLAN`，不自行扩展。
- maximum_expensive_attempts: 0
- reusable_evidence:
  - `DetachedModelAuthoringFoundationProbeTest`
  - `DetachedModelValidationSessionBuilderTest`
  - `CandidateQuerySessionTest`、`CandidateQueryRealExecutionTest`
  - `RuntimeCandidateQueryServiceTest`、`RuntimeModelValidationIsolationTest`
  - `RuntimeBundlesControllerTest`、`RuntimeCapabilitiesControllerEnabledTest`
  - `RuntimeApiAuthCodeGateTest`
- implementation_validation_commands:
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=CandidateQuerySessionTest,CandidateQueryRealExecutionTest,DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspaceServiceTest,RuntimeAuthoringWorkspacesControllerTest,RuntimeCandidateQueryServiceTest,RuntimeApiAuthCodeGateTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelValidationIsolationTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`
- stop_when_evidence_is_sufficient: AC-1 至 AC-10 均有直接正/负向自动化证据，真实 SQLite/JAR/
  external/store-restart/concurrency/fault cases 和两条 affected lane 通过，working-tree checks 通过；此后
  停止扩展。
- validation_not_required:
  - 完整 Maven reactor
  - Console build/Vitest/Playwright
  - launcher compile/package
  - 数据库矩阵或外部服务
  - authority/replay/rehearsal/source-seal/tag/release/publish

## Waiver Policy

- waivable_items: 仅 nested/fat-JAR 额外 packaging matrix 可由 owner 作为 scoped risk 明确 waiver；普通
  JDBC 之外的 candidate modes 是 non-goal，不需要 waiver。
- authorized_role: owner
- non_waivable_guards: 管理 auth、业务 Authorization 不串权、path/symlink/quota、全批次原子性、
  optimistic revision、store integrity、source stale、overlay ownership、selected-live deletion、live-state
  isolation 和证据真实性。
- required_risk_record: waiver 必须记录具体 packaging、当前标准 `jar:` evidence、生产装配影响和后续
  owner；不得用 waiver 把 JAR 标成 editable/workspaceEligible。

## Risks and Open Questions

- known_risks:
  - committed source revision 只覆盖 Runtime 已知 mutation；本 workitem 额外 hash selected live Bundle，
    但其他只读 dependency Bundle 被外部绕过 watcher 修改仍可能无法即时观察，保留为部署约束和后续
    source identity 增强项。
  - workspace store 只保证单 Runtime 进程并发；共享 NFS/多进程 writer 未提供 lease/consensus。
  - 共享 auth-code 是 Runtime 管理 possession proof，不提供用户 owner、审计或细粒度 workspace ACL。
  - TM/QM/FSScript validation 是 model-author execution surface，不承诺数据库、filesystem、host Bean 或
    external service sandbox；本期只保证 Runtime live model state 隔离。
  - `STALE` 尚无 rebase；用户需读取 diff/content 后新建 workspace，后续 publish workitem 再冻结
    rebase/apply 恢复语义。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、9.5.3 design、已验收 spike/candidate feature/BUG/signoff 和
  Runtime/model lifecycle 架构。
- 在 scope 内自主决定类、包和内部文件布局；不得让客户端提交任意 filesystem path，不得用全局或
  ThreadLocal mutation 替换 live loader/context。
- 优先建立 path/symlink/atomic/CAS/delete-fallback/auth RED evidence，再完成生产实现；不得删除或放宽
  失败断言掩盖缺口。
- 如需改变目标、route、状态、quota、兼容、安全或持久化边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 记录精确命令、测试 classes/tests/失败/跳过/耗时和证据路径；不得声称未运行的验证通过。
- 未经用户明确批准，不得运行完整 reactor、Console/Playwright、launcher、数据库矩阵或任何
  authority/replay/rehearsal/source-seal/tag/release/publish 链路。
- 达到 evidence sufficiency 后停止；不得顺手实现 publish、Console、Git、rebase 或 JAR binding。
- 完成后填写 `Implementation Result`，将状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> Ultra implementation completed on the uncommitted `main` working tree at base HEAD `0fcea8ab`.

- implementation_summary:
  - 新增 Runtime-local、versioned filesystem authoring workspace store 与公共 API，覆盖安全 create、
    list/get/discard、TM/QM/FSScript list/read/save/delete、base-to-candidate diff、完整 validation 和普通
    JDBC candidate query。
  - workspace 以 immutable content revision、CAS 和 revision lease 管理 head/state；重启恢复、store
    corruption、持久化/I/O fault、并发 writer、迟到 validation/query 与 source/head drift 均 fail closed。
  - detached view 完整替换 selected Bundle，阻止已删除资源从 live 回退，并对其他 Bundle 的同名
    TM/QM/FSScript 在 save、validate 和 query 三个入口执行 overlay ownership guard。
  - `/bundles` 扩展为 external/JAR/classpath/inactive managed 全量事实 inventory；capabilities、配置、
    RuntimeEnvelope route 和 auth-code guard 同步扩展，既有 Runtime route/DTO 行为保持兼容。
- changed_paths:
  - `foggy-dataset-model-engine`：抽取共享 `CandidateContentRevision`，补 selected-Bundle replacement、
    FSScript full validation 与对应 candidate/detached probes。
  - `foggy-runtime-api`：新增 workspace DTO/store/service/controller，扩展 routes/config/auth/capabilities、
    Bundle inventory/controller 及 focused HTTP/store/service/real-execution tests。
  - `docs/architecture/runtime-and-model-lifecycle.md`、`docs/9.5.3/README.md` 与本 canonical spec。
- tests_and_results:
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=CandidateQuerySessionTest,CandidateQueryRealExecutionTest,DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：4 classes / 22 tests，
    0 failures / 0 errors / 0 skipped，`BUILD SUCCESS`，29.388s。
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspaceServiceTest,RuntimeAuthoringWorkspaceRealExecutionTest,RuntimeAuthoringWorkspacesControllerTest,RuntimeCandidateQueryServiceTest,RuntimeApiAuthCodeGateTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelValidationIsolationTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：10 classes / 104 tests，
    0 failures / 0 errors / 0 skipped，`BUILD SUCCESS`，01:01 min。
  - 一次未带 `-am` 的探索性 Runtime 命令因本地仓库中没有当前未提交 engine candidate class 而在
    `testCompile` 停止、未执行测试；它不计为通过或产品测试失败，最终 canonical `-am` lane 已从
    reactor 当前源码完成验证。
- manual_or_experience_evidence:
  - 真实临时 external Bundle、标准 `jar:` dependency、真实 SQLite candidate validate/execute、重启恢复、
    store corruption、fault injection、并发 CAS/lease、source drift、overlay ownership、管理 auth 和兼容性
    均由上述自动化测试直接覆盖。
  - 最终 working-tree `git diff --check` 与所有 untracked 文件的 `git diff --no-index --check` 均通过；
    未发现测试生成物进入工作树。
- deviations: none；Runtime lane 增加 `RuntimeAuthoringWorkspaceRealExecutionTest`，用于提供契约要求的
  真实 SQLite evidence，不改变批准范围。
- residual_risks:
  - 未新增 nested/fat-JAR packaging matrix；复用标准 `jar:` Resource evidence。
  - store 仅保证单 Runtime 进程；共享 NFS 或多进程 writer 不受支持。
  - 其他只读 dependency Bundle 被绕过 watcher 外部修改时，可能无法立即观察漂移。
  - 共享 auth-code 不提供 workspace owner、RBAC 或审计；model-author script surface 也不是宿主 sandbox。
  - `STALE` 尚无 rebase；publish/apply/rollback/Git/JAR binding 和高级 candidate query 均为后续 workitem。
- reused_evidence: 已验收 foundation/candidate-query 的 external/JAR/FSScript isolation、真实 SQLite、
  permission/cache 与 unsupported-mode probes，并在本 workitem affected lanes 中重新执行对应测试。
- omitted_validation_and_reason: 按批准的 elevated budget 未运行完整 Maven reactor、Console/Playwright、
  launcher、数据库矩阵、authority/replay/rehearsal/source-seal/tag/release/publish；均为明确禁止或 out of scope。
- readiness: READY_FOR_SIGNOFF

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-api-signoff-r2.md`
- blocking_items: none
- follow_up_required: yes

## References

- requirement / issue: owner 要求按 9.5.3 已验收路线继续推进第 2 项 Runtime authoring workspace API。
- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/architecture/module-boundaries.md`
  - `docs/design/runtime-console-product-charter.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
- related work items:
  - `docs/9.5.3/workitems/SPIKE-runtime-model-authoring-foundations.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-candidate-query-overlay.md`
  - `docs/9.5.3/workitems/BUG-runtime-candidate-query-fail-closed-guards.md`
  - `docs/9.5.3/acceptance/FEATURE-runtime-candidate-query-overlay-signoff-r2.md`
