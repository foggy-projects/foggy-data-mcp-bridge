---
doc_type: delivery-spec
delivery_type: cross-repository
version: 9.5.1
ticket: runtime-internal-permissions-and-preaggregation
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: repository-owner-via-user-request
approved_at: 2026-07-25
open_questions: []
---

# Delivery Spec: Runtime 内生权限与权限安全预聚合

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 Runtime API 直连场景的模型作者内生权限、平台 HTTP 能力、模型/列/行授权以及
  行权限与预聚合路由的已确认边界，并把 CLI、配套 Skill 和存量 TM/QM 兼容纳入同一交付。
- canonical_path:
  `docs/9.5.1/workitems/FEATURE-runtime-internal-permissions-and-preaggregation.md`
- architecture_source:
  `docs/architecture/runtime-permissions-and-preaggregation.md`

## Goal

- version_goal: 在 main-after-9.5.0 架构上，为 Runtime API 直连调用补齐由 TM/QM 作者控制的
  内生权限闭环，并保证所有预聚合、缓存和查询变体都不会绕过有效行权限。
- target_outcome:
  - Runtime 透明传递可选 opaque Authorization token，并为每个数据面请求建立非空
    `RequestIdentity`，不内置客户 IAM 语义；
  - QM 可以声明动作化模型权限解析器，并把同一不可变决策用于模型、列和结构化行权限；
  - TM/QM 作者可直接使用平台维护的 `get/post`，常规场景无需自行创建 Spring Bean；
  - 行权限无法在某个预聚合上等价表达时跳过该候选并安全回源；
  - 元数据、字段建议、维度成员和所有数据缓存按同一授权签名隔离；
  - `foggy-runtime` CLI 分离管理 auth-code 与可选数据查询 Authorization，配套中英文 Skill
    准确说明权限配置、查询与预聚合回源；
  - 存量 TM/QM 无需迁移，未启用新权限声明时保持既有开放与查询结果语义。
- critical_outcomes:
  - 未声明权限的开放 QM 在无 token 时仍可 list/describe/validate/query/member query。
  - 受保护 QM 按 `action + resource` 返回显式 `allow`，模型、列和行决策在所有直接入口一致
    执行并 fail closed。
  - 普通查询者不能通过 DSL、Compose、CTE 或原始脚本入口获得作者级 `get/post`/Bean 能力。
  - 权限谓词先于预聚合和缓存求值，任何优化失败都不得删除、放宽或后置权限。
  - 维度成员查询和缓存不能跨权限身份泄露字段或成员值。
  - 预聚合不能由某个用户权限快照构建后作为全局表共享；无法证明 scope 时拒绝或回源。
  - opaque token、敏感 Header 和权限值不进入普通日志、SQL 文本、诊断正文或明文缓存 key。
  - 存量 TM/QM、旧 `get/post`、旧 CLI 命令和无行权限的预聚合路径有明确兼容回归证据。
- success_is_sufficient_when: AC-1 至 AC-14 全部具有实际运行证据，权限与预聚合 SQLite
  结果级集成测试、CLI 回归和 Skill 源包校验通过，受影响模块回归通过，且独立签收未发现
  权限绕过或未披露的存量模型不兼容。

## Scope

- in_scope:
  - Runtime models list/describe、query validate/execute 和 dimension member query 读取可选
    `Authorization` Header，并建立包含 `ANONYMOUS` 或 `OPAQUE_SUBJECT` 的不可变
    `RequestIdentity`；管理 principal 与数据面身份保持分离。
  - 在 `foggy-runtime-cli` 保留既有 `--auth-code`/`FOGGY_RUNTIME_API_AUTH_CODE` 管理契约，
    新增可选 `--authorization`/`FOGGY_RUNTIME_AUTHORIZATION` 数据身份输入；完整 Header 值
    原样发送且不自动补 `Bearer`，两套凭据可以共存但不能串权。
  - 在 QM 定义内增加 `modelPermissions` public/resolver 模式；resolver 按
    `DISCOVER/DESCRIBE/VALIDATE/EXECUTE/MEMBER_QUERY + resource` 返回统一
    `permissionDecision.allow`。
  - 权限决策可携带供列权限使用的 `attributes`、typed `rowPredicates`、`decisionId`、
    `policyVersion`、`expiresAt` 和可选 `providerFingerprint`。
  - 由平台自动配置标准 HTTP Bean，使现有 FSScript 全局 `get/post` 开箱可用，并补齐
    Header、HTTPS/完整 URL、query/body、超时、响应类型、大小限制、脱敏和 trace。
  - 将使用完整 evaluator 的 `/api/v1/fsscript/execute` 纳入作者/管理面保护。
  - 继续复用 TM/QM `fieldPermissions`，让动态字段权限消费统一的权限决策，并覆盖 metadata、
    查询列、用户计算字段、slice/having/group/order/join 等用户可寻址位置。
  - 保留 QM `accesses` 兼容，并补齐 TM 基础行限制、QM 只收窄、typed predicate AST、
    空集合/null/类型语义及 provenance。
  - 修复模型元数据、字段建议、dimension member query 和 member cache 的权限传递与隔离。
  - 将有效行权限字段、operator、lineage 和 proof status 纳入预聚合 requirement、候选匹配、
    安全诊断与 Hybrid 判断。
  - 冻结 `GLOBAL`/`SECURITY_SCOPED` 预聚合构建契约；现有预聚合按 `GLOBAL` 安全处理，未实现
    scoped 构建时显式拒绝该配置。
  - 对 Compose、CTE、Union、Pivot、Hybrid 等执行路径逐叶子 QM 授权并在叶子 scan 注入权限。
  - 由引擎计算绑定 action/resource、模型、列、行、generation、策略版本和 scope 的
    `authorizationSignature`，用于任何可能返回数据、元数据或成员值的缓存。
  - 更新 TM/QM、Runtime API、CLI、权限错误和预聚合回源的开发者文档及示例。
  - 更新 `foggy-ai-analysis` 的英文/中文 Skill 源，以及配套 `foggy-semantic-query` Skill 的
    权限与预聚合说明；完成源码一致性和可打包校验，不修改本机安装副本冒充源更新。
  - 建立存量 TM/QM、旧 `get/post`、旧 CLI 命令、无权限预聚合命中和有权限安全回源的兼容
    回归门槛。
- affected_modules:
  - `foggy-runtime-api`
  - `foggy-fsscript`
  - `foggy-dataset-model-engine`
  - `foggy-mcp-launcher`
  - `addons/foggy-dataset-model-preagg`（仅在物化字段契约或兼容测试确需时）
  - `addons/foggy-dataset-model-cache`
  - `docs/architecture`
  - `docs/9.5.1`
  - sibling repository `../foggy-runtime-cli`
  - sibling repository `../foggy-ai-analysis`（`locales/en` 与 `locales/zh-CN`）
  - paired Skill source `../.codex/skills/foggy-semantic-query`
- external_dependencies:
  - 客户业务权限系统只通过本地 stub/mock 验证，不依赖真实外部服务或真实 token。
  - CLI 和 Skill 位于独立源码仓库/目录；实现必须分别遵守其本地规范并记录各仓库 changed
    paths 与测试结果。本事项要求源码与包校验完成，但不授权 tag、release、publish 或 push。

## Non-Goals

- out_of_scope:
  - 不把 Authorization 变成所有 Runtime 数据请求的全局必填 Header。
  - 不在 Runtime 固化 JWT、角色、用户、租户、部门或客户授权项结构。
  - 不在本事项中实现列值脱敏、动态 masking、部分字符替换或按身份改写字段值。
  - 不把 `fieldAccess`、`deniedColumns`、`systemSlice` 或 `policySnapshotId` 开放为普通
    Runtime 请求参数。
  - 不让管理 auth code 自动绕过受保护 QM 的业务权限。
  - 不向普通 query DSL、Compose 或 CTE 开放动态 HTTP 调用或 Spring Bean import。
  - 不为模型作者设置语义层 host allowlist；部署级代理和出口网络策略仍可配置。
  - 不要求每个预聚合都物化所有可能的权限字段，也不因权限回源自动重建预聚合表。
  - 不要求 9.5.1 实现 `SECURITY_SCOPED` 的构建与刷新；未实现时必须 fail fast，不能降级成
    `GLOBAL` 共享。
  - 不在本事项中设计通用 IAM、权限管理后台或 token 签发能力。
  - 不新增面向数据面的完整脚本执行接口；如未来需要，另立受限 evaluator 事项。
  - 不要求存量 TM/QM 补写 `modelPermissions`、改写 `accesses`、重建成员权限或执行数据迁移。
  - 不运行 release authority、全数据库矩阵、tag、release、publish 或 remote push。
- do_not_touch:
  - `systemSlice` 及相关 trusted-host 治理契约的内部属性。
  - 9.5.0 Model SPI v2 模块、包根和 legacy exit 决策。
  - 已签收版本的 acceptance 记录和历史权限结论。
  - 已发布 CLI/Skill release 资产和本机已安装 Skill 副本；变更只落在当前源码与新版本说明。
- non_blocking_or_waivable_items:
  - 非敏感诊断文案、trace 字段命名和示例权限服务 URL 可以由 owner 在不改变安全语义时豁免。
  - 自定义 Bean 的高级示例可后补，不阻断平台标准 `get/post` 主路径。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Runtime 查询 token 可为空且只透明传递 | 身份和业务授权解释属于模型作者及其业务权限系统 | 无 Header 时建立 `ANONYMOUS` identity；未声明权限的 QM 保持匿名可用 |
| 管理 principal 与数据面身份分离 | 管理 TM/QM 不等于拥有某次查询的业务数据权限 | 管理凭据和任意业务 Authorization 都不能彼此替代或自动提升 |
| 模型权限位于 QM 的 `modelPermissions.resolver` | QM 是 Runtime 用户查询表面，且现有加载器消费 `queryModel` export | 不增加独立权限 export，也不以裸 `visible` 代替直接查询授权 |
| 模型权限按 `action + resource` 返回 `allow` | catalog 隐藏不能阻止直接指定模型名，不同操作也可能有不同授权 | list 使用 `DISCOVER`；其他直接入口分别使用对应动作，不以 `queryable` 布尔值合并 |
| 未配置权限即公开，显式 public 可选 | 保持作者确认的开放模型兼容语义，同时支持严格发布检查 | `modelPermissions: { mode: 'public' }` 与未配置运行时等价 |
| 配置解析器后，即使 token 为空也调用 | 作者可以自行定义匿名策略 | 拒绝、异常、超时或非法返回只对受保护模型 fail closed |
| 权限决策使用 typed obligations | 引擎必须证明行权限、缓存和预聚合等价性 | 新模型返回 attributes 与 typed row predicates；旧 `accesses` 只作为兼容逃生口 |
| 引擎计算最终 `authorizationSignature` | 上游 fingerprint、token 或业务 DSL 均不能单独证明最终权限相同 | 签名绑定 action/resource、字段、行 AST、generation、策略版本和 scope |
| TM/QM 作者属于可信计算基并可使用 `get/post` | 发布模型实质上是可信代码发布 | 作者能力由管理权限、审计和发布流程保护，不由查询 token 授予 |
| 平台提供标准 HTTP Bean | 常规权限接入不应要求客户编写 Java Bean | 保持旧 `service/apiPath/params/data/returnClass` 语法兼容 |
| 完整 FSScript execute 属于作者/管理面 | 完整 evaluator 与模型脚本拥有同等级宿主能力 | 标准 HTTP Bean 可用前或同一交付内必须先收紧该入口 |
| 本迭代列权限只控制字段可发现与可引用 | 列值脱敏与字段访问控制具有不同执行、缓存和聚合语义 | 不新增 masking；未来另立策略事项 |
| TM 基础行限制与 QM 行限制只能 AND 收窄 | 下层物理模型约束不能被查询表面删除 | 保留现有 QM `accesses`，不得引入放宽或 override 语义 |
| 元数据和成员查询使用同一权限快照 | 字段名、候选值和成员列表本身也可能泄露受限数据 | member cache 绑定授权签名；无法签名时禁用共享缓存 |
| 权限谓词先进入 pre-aggregation requirement | 预聚合只是优化路径 | 无法证明等价时跳过候选；权限解析失败则查询失败 |
| 权限字段可只用于过滤后 rollup | 字段无需出现在用户最终结果中 | 候选仍必须物化足够粒度或可证明函数依赖 |
| 预聚合构建区分 `GLOBAL` 与 `SECURITY_SCOPED` | 查询安全匹配不能修复构建时错误共享的用户快照 | 现有表按 GLOBAL 处理；未实现 scoped 时拒绝配置，不能静默降级 |
| 多模型计划逐叶子授权 | 外层 QM 授权不能代表各数据域已被约束 | Compose/CTE/Union/Hybrid 在叶子 scan 注入权限 |
| trusted-host 与 Runtime 内生权限入口分离 | 请求携带治理字段不能成为信任模式切换开关 | `systemSlice` 等字段继续只由可信路由/适配器建立 |
| CLI 管理凭据与数据身份分离 | `--auth-code` 管理模型不代表拥有业务数据，Authorization 也不是发布权限 | 保留 `--auth-code`/`X-Foggy-Runtime-Code`；新增可选 `--authorization`/`FOGGY_RUNTIME_AUTHORIZATION`，两者可共存但不串权 |
| 新权限语法 opt-in 且存量模型无迁移 | 现有 TM/QM 大量省略权限或使用既有 `fieldPermissions`、`accesses`、成员权限 | 未声明 `modelPermissions` 继续公开；模型加载和查询结果兼容是 must-pass，不以 optional 字段推测替代回归 |
| 配套 Skill 与实际兼容栈同步 | CLI/Runtime 能力若已变化而 Skill 仍称权限全部延期，会诱导错误配置 | 同步英文/中文 analysis Skill 与 semantic-query Skill；实现前不提前宣称支持，发布动作另行执行 |

## Acceptance Criteria

- [x] AC-1: Runtime models list/describe、query validate/execute 和 member query 均把可选
  Authorization 原样传入同一类只读权限上下文；无 Header 或空白 Header 时建立 `ANONYMOUS`
  identity，非空 Header 时建立 `OPAQUE_SUBJECT` identity；管理 principal 不自动成为数据权限。
- [x] AC-2: QM `modelPermissions` public/resolver 模式能被加载、验证和执行；未配置或显式
  public 时产生公开决策，resolver 模式即使 token 为 null 也执行，非法返回、超时和异常均
  fail closed。
- [x] AC-3: resolver 按 `DISCOVER/DESCRIBE/VALIDATE/EXECUTE/MEMBER_QUERY + resource`
  返回必填 `allow`；list 只过滤被拒绝模型且不隐藏同 namespace 的公开模型，直接指定模型名
  不能绕过对应动作授权，拒绝错误不枚举受限资源。
- [x] AC-4: 同一 `namespace + model + action` 在请求内消费统一、不可变、已验证的
  `permissionDecision`；受控 attributes 进入 `fieldPermissions`，typed row predicates 进入
  权限 AST，decisionId/policyVersion/expiresAt/providerFingerprint 按契约处理，独立请求重新求值。
- [x] AC-5: FSScript starter/auto-configuration 提供标准 HTTP Bean；`get/post` 无需客户
  自建 Bean 即可工作，支持约定的新请求结构并兼容旧结构。null Header 被省略，敏感内容不写
  日志，跨 origin 重定向不转发 Authorization。
- [x] AC-6: `/api/v1/fsscript/execute` 使用完整 evaluator 时受现有作者/管理凭据保护；
  缺失或无效管理凭据被拒绝，任意业务 Authorization token 不构成作者授权。
- [x] AC-7: TM/QM `fieldPermissions` 对 metadata、查询列、用户计算字段依赖、slice、having、
  groupBy、orderBy 和 join 引用使用同一有效字段集；受限字段不能通过错误建议枚举，本事项不新增
  列 masking 行为。
- [x] AC-8: TM 基础行限制和 QM 行限制以 AND 合并；typed predicate AST 记录来源、binding、
  operator、类型、字段依赖和可证明状态；空 `IN` 为恒假，null/类型语义明确，自定义 SQL/无法
  解析表达式标记为不可证明且绝不静默删除。
- [x] AC-9: dimension member query 先执行模型动作、字段和行权限；成员来源查询使用同一权限
  快照，member cache 绑定 `authorizationSignature` 或在无法稳定签名时禁用共享缓存，两个
  权限身份不能枚举到彼此成员。
- [x] AC-10: 预聚合候选包含全部权限字段且粒度、operator、lineage 和度量兼容时可命中并与
  源表结果一致；缺字段、不可证明或 Hybrid 策略不一致时跳过当前候选并最终安全回源。现有表按
  `GLOBAL` 处理，不使用用户快照构建；未实现 `SECURITY_SCOPED` 时配置必须 fail fast。
- [x] AC-11: FULL、rollup、final-stage、Hybrid、Pivot、Compose、CTE 和 Union 路径对每个
  叶子 QM 执行模型、列和行权限，并在叶子 scan 注入行谓词；不得只授权外层模型或在 join/
  aggregate 后补过滤。
- [x] AC-12: 引擎计算的 `authorizationSignature` 绑定 action/resource、namespace、catalog
  generation、模型/列/行决策、policyVersion 和 scope；查询、SQL、预聚合路由、metadata 和
  member cache 均不得跨不同权限集合串读，TTL 不超过 `expiresAt`，公开模型使用显式
  `PUBLIC` identity，日志、错误和缓存 key 不暴露原始 token。
- [x] AC-13: `foggy-runtime-cli` 保留既有 `--auth-code`、`FOGGY_RUNTIME_API_AUTH_CODE` 和
  `X-Foggy-Runtime-Code` 行为；新增可选 `--authorization` 与
  `FOGGY_RUNTIME_AUTHORIZATION`，把调用方提供的完整值原样作为数据面 `Authorization`
  发送且不自动补 `Bearer`。两套 Header 可同时存在但不能互相授权；缺少新选项时不发送
  Authorization 并保持旧命令兼容。CLI README/help/tests 明确端点范围，且任何输出、日志、
  命令计划、持久化配置和跨 origin 重定向都不泄漏该值。
- [x] AC-14: 所有现有 demo/fixture TM/QM 无文件改写即可 load/validate/refresh/query；未声明
  `modelPermissions` 的 QM 无 token 继续公开，既有 `fieldPermissions`、非空/空 `accesses`、
  `memberPermission/memberPermissions` 和旧 `get/post` 结果保持。无有效行权限时既有预聚合
  行为不因身份机制改变；有行权限且候选不安全时只允许计划回源、结果必须等价。配套
  `foggy-ai-analysis` 英文/中文源与 `foggy-semantic-query` 权限说明和包校验同步完成。唯一
  预先披露的非 TM/QM 调用兼容变化是完整 `/api/v1/fsscript/execute` 需要管理凭据。

## Contract / Data / Security Constraints

- API or event contract:
  - `Authorization` 在数据查询面保持可选 opaque Header。
  - 数据面始终建立非空 `RequestIdentity`；缺少 Header 表示 `ANONYMOUS`，不是权限上下文缺失。
  - 管理凭据继续使用现有 Runtime 管理认证契约；存在任意 Authorization Header 不等于通过
    作者/管理认证。
  - 新权限拒绝使用稳定、可诊断但不枚举受限资源的 Runtime error code；响应不得包含 token、
    权限值、受限字段建议、成员样例或上游正文。
  - `modelPermissions` 位于 `export const queryModel` 对象内，支持省略、显式 public 和 resolver
    三种作者表达；resolver 输入包含 action/resource，输出包含必填 allow。
- data and migration:
  - 不要求数据库 DDL/DML 或已物化预聚合表迁移。
  - 旧预聚合缺少权限字段时正常变为不合格候选并回源，不得错误命中。
  - 现有预聚合按 `GLOBAL` 语义处理；任何使用请求级权限构建并全局共享的路径都必须拒绝。
  - `SECURITY_SCOPED` 若未实现完整 scope identity、刷新和匹配链路，则配置验证失败。
- compatibility and rollback:
  - 既有未声明权限的 QM 行为保持公开。
  - 存量 TM/QM 不要求新增字段、改写脚本、重新发布格式或执行数据库/预聚合数据迁移。
  - 未配置/空 `fieldPermissions` 继续不增加字段限制；既有静态/动态 `fieldPermissions`、
    QM `accesses`、TM/QM member permissions 和旧 `get/post` 参数与结果保持兼容。
  - 旧 `accesses` 不能证明权限 AST 时继续安全回源，不因兼容而猜测预聚合等价。
  - 没有有效行权限的存量查询不得仅因新增 RequestIdentity 或 Authorization 支持而改变预聚合
    命中；有行权限时允许因安全证明不足而回源，但结果语义必须与源表权限查询一致。
  - CLI 原有参数、退出码和不传 Authorization 的命令保持；新增 data-plane 选项是 optional。
  - 完整 FSScript execute 纳入管理保护是唯一已确认的调用方收紧，不把它伪装成“完全零影响”；
    既有未认证脚本调用方需改用管理 auth-code。
  - 若实现发现必须改变既有 TM/QM 语法、默认公开语义或查询结果，必须设置 `NEEDS_REPLAN`，
    不能通过静默迁移或批量改写 fixture 规避兼容失败。
  - 可通过回滚本 work item 实现恢复旧行为；回滚不得留下“HTTP Bean 已开放但脚本入口未保护”
    的中间状态。
- permissions and secrets:
  - 标准 HTTP 能力需要超时、响应大小上限、敏感 Header 脱敏、跨 origin 重定向保护和 trace
    关联。
  - 原始 token 不进入 SQL、普通日志、诊断详情、FSScript 编译缓存或明文查询缓存 key。
  - 权限解析器失败不得回退到无权限条件查询。
  - 引擎计算的 `authorizationSignature` 是缓存隔离依据；providerFingerprint 和 token 摘要只能
    作为附加输入，缓存 TTL 不得超过权限决策 expiresAt。
  - 维度成员查询与成员缓存执行同一模型、字段和行权限，不得以维表名或普通 cachePrefix 代替
    权限隔离。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/AC-3 | must-pass | critical | Runtime controller/service/context tests | 现有 Runtime model/query tests | anonymous/opaque identity、管理面分离、逐动作拒绝、直接模型名和多模型 list 结果 |
| AC-2/AC-4 | must-pass | critical | QM loader/validation/unit + SQLite query integration | 现有 field/access tests | public/resolver 模式、typed decision、请求内复用、跨请求重算和 expiry |
| AC-5 | must-pass | major | FSScript HTTP unit/context tests | 现有 Get/Post fun definitions | 新旧请求结构、Header、HTTPS URL、null Header、重定向、超时/失败 |
| AC-6 | must-pass | critical | Runtime auth interceptor/controller negative tests | `RuntimeApiAuthCodeGateTest` | 无效凭据拒绝、有效管理凭据允许、业务 token 不提升 |
| AC-7/AC-8 | must-pass | critical | field permission + typed predicate unit/SQLite tests | 现有 field/access tests | 全引用位置字段拒绝、空 IN/null/类型、TM+QM AND、自定义谓词不可证明 |
| AC-9 | must-pass | critical | member loader/query/cache integration tests | 现有 member permission tests | 两个身份成员不串读、行过滤生效、无签名时共享缓存关闭 |
| AC-10 | must-pass | critical | SQLite preagg parity + build-mode validation | 现有 preagg/access tests | 命中、下一候选、回源、GLOBAL 构建安全、scoped 未实现时拒绝 |
| AC-11 | must-pass | critical | focused FULL/rollup/final/Hybrid/Pivot/Compose/CTE/Union tests | 现有 preagg/compose/pivot suites | 每个叶子模型授权、scan 前注入或明确安全跳过证据 |
| AC-12 | must-pass | critical | cache signature unit/integration tests | 现有 L1/L2/preagg cache tests | 查询/metadata/member 两身份不串读、PUBLIC 复用、expiry 和 token 不泄漏 |
| AC-13 | must-pass | critical | CLI unit/HTTP tests + help/README review | 现有 CLI auth-code/client tests | 旧 auth-code 不变、新 Authorization 原样发送、双 Header 不串权、缺省兼容和秘密不泄漏 |
| AC-14 | must-pass | critical | 全量现有 model fixture 回归 + CLI compatibility + bilingual Skill/package validation | 现有 model suites、Skill release validation | 原文件加载和 golden parity、无权限 preagg 命中、有权限安全回源、Skill 中英文语义与包内容一致 |
| docs/compatibility | must-pass | major | static review + `git diff --check` | canonical architecture doc | TM/QM/CLI/Skill 示例、迁移与例外说明、各仓库 changed-path 清单 |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated
- assurance_rationale: 本事项改变数据权限、跨身份缓存隔离和预聚合路由，错误会造成数据越权。
- lightweight_validation:
  - `git diff --check`、敏感字段静态扫描和聚焦 JUnit；单次预期 `<5m`。
  - 精确检查完整 evaluator 路由已进入管理保护，查询 DTO/DSL 未新增治理字段。
  - 在 `foggy-runtime-cli` 运行聚焦参数解析与 HTTP Header 测试，并检查 `--help`；
    单次预期 `<5m`。
  - 对 `foggy-ai-analysis` 英文/中文源和 `foggy-semantic-query` 执行链接、术语、敏感示例及
    package manifest 静态校验；单次预期 `<5m`。
- medium_validation:
  - `mvn -B -ntp -pl foggy-fsscript,foggy-runtime-api,foggy-dataset-model-engine -am test -DskipITs`
    或按实现切片运行等价的受影响 reactor；预期 `5-30m`。
  - 若改动 cache/preagg addon，运行
    `mvn -B -ntp -pl addons/foggy-dataset-model-cache,addons/foggy-dataset-model-preagg -am test -DskipITs`
    或仅包含真实改动 addon 的等价命令；预期 `5-30m`。
  - 对新增权限/预聚合 SQLite IT 使用 `-DskipITs=false verify` 运行并记录精确类名、测试数和结果；
    预期 `5-30m`。
  - launcher 自动配置/装配的聚焦 context 或 smoke test；预期 `<5m`。
  - 在 `../foggy-runtime-cli` 运行完整 `python -m pytest`；预期 `<5m`。
  - 使用仓库既有打包脚本分别生成并解包校验 analysis Skill 英文/中文包和
    semantic-query Skill 包，但不上传、不发布；预期 `5-30m`。
- expensive_validation:
  - 默认无。只有实现改变广泛公共 API/SPI、跨方言 SQL 语义，或 focused/affected evidence
    无法覆盖权限正确性时才提出一次最终全链建议。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger:
  - 最终候选出现跨模块公共契约扩大、非 SQLite 特有 SQL 改写，或权限/缓存结果无法由受影响
    模块和 SQLite 集成证据确定。
- estimated_full_chain_wall_clock: not-estimated；若触发，必须基于当时真实命令和环境另行给出区间。
- full_chain_prerequisites: clean final candidate、测试权限服务 stub、可复现 SQLite fixture。
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 仅限上述 full-chain trigger，不能因 assurance 为 elevated 自动运行。
- maximum_expensive_attempts: 0 without explicit user approval；获批后最多 1 次。
- reusable_evidence:
  - 现有 Runtime auth、field permission、accesses、preagg、compose、pivot 和 cache 测试可作为
    回归基线，但不能替代新增 token、模型权限和权限安全预聚合结果证据。
  - 现有 CLI auth-code/client 测试及 Skill 双语 release-validation 规则可复用，但必须新增
    data Authorization、存量命令和权限文档一致性证据。
- minimum_revalidation_radius:
  - 只重跑依赖已变更权限输入、模型契约、缓存签名、预聚合选择或被测产物 identity 的证据；
    纯文档、receipt 或无关测试调整不自动使其他产品正确性证据失效。
- stop_when_evidence_is_sufficient:
  - AC-1 至 AC-14 各有直接通过证据；
  - 至少一个真实 SQLite fixture 同时证明授权结果、预聚合等价命中和缺粒度安全回源；
  - 受影响模块测试通过，安全 review 未发现开放 evaluator、跨身份查询/成员缓存、用户快照
    全局预聚合或权限丢失路径；
  - 存量模型全集无需改写，CLI 旧命令兼容，Skill 英文/中文与 semantic-query 源包验证通过。
- validation_not_required:
  - release authority、source seal、artifact promotion、Playwright/UI、真实客户权限系统、
    全数据库矩阵、CI、tag、release、publish 和 push。

## Waiver Policy

- waivable_items:
  - 不影响安全含义的诊断文案、trace 名称、示例 URL 和高级自定义 Bean 文档。
- authorized_role: repository owner
- non_waivable_guards:
  - 开放模型无 token 兼容。
  - 受保护模型 fail closed。
  - 管理 principal 与数据面身份分离。
  - 完整 FSScript evaluator 的作者/管理面隔离。
  - 行权限不丢失、逐叶子授权、预聚合不越权、查询和成员缓存不跨权限串读。
  - 不把用户权限快照构建成全局共享预聚合。
  - token 和敏感权限值不泄漏。
  - 存量 TM/QM 无迁移兼容，以及 CLI 管理/数据凭据不串权。
  - CLI 与配套 Skill 源码、帮助和权限说明在同一兼容栈内同步完成。
- required_risk_record:
  - 任何 waiver 必须记录影响范围、可检测方式、回滚方案和 owner；不得把未通过安全项写成通过。

## Risks and Open Questions

- known_risks:
  - model list 可能对多个受保护 QM 调用权限系统，需要请求内复用、超时、单模型失败隔离，并为
    后续 namespace 级 batch provider 保留扩展点。
  - 标准 HTTP Bean 若先于 FSScript 作者面保护启用，会扩大现有原始脚本入口的网络能力。
  - 动态行权限若只体现为最终 JDBC WHERE，预聚合匹配无法证明等价并会过度回源。
  - 当前维度成员加载和缓存没有完整权限上下文，可能发生跨身份成员值泄漏。
  - 缓存若遗漏 action、列、行、策略版本、expiry 或 scope，可能发生跨身份数据泄漏。
  - 预聚合构建若意外继承某次用户权限并作为 GLOBAL 表发布，会把不完整快照共享给其他用户。
  - Authorization 与管理凭据共存时，不能把“存在业务 token”误判为作者身份。
  - CLI 若把两个凭据复用为同一配置项或在所有请求无差别打印，会造成越权或秘密泄漏。
  - Skill 若只更新单一语言、已安装副本或仍保留“生产权限全部延期”，会与实际 Runtime
    契约冲突并误导模型作者。
  - 存量模型兼容若只验证 `accesses: []`，会漏掉真实非空 accesses、动态字段权限和成员权限。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、`docs/architecture/runtime-permissions-and-preaggregation.md`、
  相关模块规范，以及 Foggy 语义查询/Runtime 建模技能；进入 CLI、analysis Skill 或
  semantic-query Skill 源目录前还要读取各自更近的仓库规范。
- 在 scope 内自主选择具体类、内部对象和谓词表示；不得改变已冻结的作者/查询者信任边界。
- 实现顺序必须保证完整 FSScript evaluator 在标准 HTTP Bean 对普通部署可用前已经纳入
  作者/管理保护；任何中间候选都不能扩大未授权网络能力。
- 新增 TM 行权限作者语法时，应与现有 TM/QM FSScript 风格一致，并保留 QM `accesses`
  兼容；若需要改变 `modelPermissions` public/resolver 模式、动作集合、必填 `allow`、
  Authorization 可选性、行权限 AND 语义、成员缓存隔离或 GLOBAL/SECURITY_SCOPED 边界，
  设置 `NEEDS_REPLAN`。
- CLI 必须保留现有管理 auth-code 契约并新增独立 optional data Authorization；Skill 文档在
  Runtime/CLI 实现和测试明确后更新，避免提前宣称支持。只修改可发布源，不修改安装副本或
  历史 release 资产。
- 优先建立开放模型、受保护模型、FSScript gate、维度成员跨身份缓存、权限缓存、逐叶子授权和
  预聚合越权的负向回归测试，同时建立存量 TM/QM 和旧 CLI 命令的 compatibility baseline，
  再完成实现并运行通过。
- 运行与改动面匹配的 focused、affected module 和 SQLite integration 验证，记录精确命令、
  测试数、结果、证据路径和未运行原因。
- 未经用户明确批准，不运行预计超过 30 分钟或包含 authority/replay/rehearsal/source-seal 的
  大型链路；若满足触发条件，只提出一次带预计耗时和决策价值的建议。
- 达到 evidence sufficiency 后停止；不得为了提高证据数量运行与签收决定无关的矩阵。
- 完成后填写 `Implementation Result`，将状态改为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。结果必须按 bridge、CLI、analysis Skill、semantic-query Skill 分别列出 changed
  paths、精确测试命令和未运行原因。

## Implementation Result

- implementation_summary:
  - M0：分离管理 auth-code 与数据面 Authorization，并将完整
    `/api/v1/fsscript/execute` 纳入管理面保护。
  - M1-M2：新增 `RequestIdentity`、动作化 `modelPermissions`、不可变权限决策会话、
    typed row predicates、字段决策桥接，并贯通 models list/describe、validate/execute 与
    member query。
  - M3：引擎生成最终 `authorizationSignature`，将 action/resource、catalog generation、
    模型/列/行决策、策略版本、scope 和 expiry 纳入查询及成员缓存隔离，缺少稳定签名时
    fail closed。
  - M4-M5：预聚合增加 `GLOBAL`/`SECURITY_SCOPED` 构建边界和权限 requirement/matcher；
    Compose、CTE、Pivot、Hybrid 等多阶段路径按叶子权限处理并在不能证明等价时安全回源。
  - M6：提供受约束的标准 FSScript HTTP client，完成 CLI 双凭据、member 命令、README/help，
    并同步 analysis 中英文 Skill 与 semantic-query Skill。
- changed_paths:
  - bridge:
    - `foggy-runtime-api/**`
    - `foggy-fsscript/**`
    - `foggy-dataset-model-engine/**`
    - `addons/foggy-dataset-model-cache/**`
    - `addons/foggy-dataset-model-preagg/**`
    - `docs/9.5.1/**`
  - CLI:
    - `../foggy-runtime-cli/README.md`
    - `../foggy-runtime-cli/src/foggy_runtime_cli/{client.py,main.py}`
    - `../foggy-runtime-cli/tests/{test_cli.py,test_client_http.py}`
  - analysis Skill:
    - `../foggy-ai-analysis/README.md`
    - `../foggy-ai-analysis/locales/{en,zh-CN}/SKILL.md`
    - `../foggy-ai-analysis/locales/{en,zh-CN}/references/{production-permission-next-phase.md,runtime-cli-command-rules.md,tm-qm-configuration.md}`
  - semantic-query Skill:
    - `../.codex/skills/foggy-semantic-query/SKILL.md`
    - `../.codex/skills/foggy-semantic-query/references/{pre-aggregation.md,query-model-dsl.md}`
- tests_and_results:
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipTests install`：
    实际执行 Engine 全量单测，3179 tests、0 failures、0 errors、2 skipped。
  - `mvn -B -ntp -pl foggy-mcp-launcher -Dtest=DataViewerApiSmokeTest,LauncherDefaultRouteIsolationSmokeTest,LauncherExplicitTestRoutesSmokeTest test`：
    10 tests、全部通过。
  - `mvn -B -ntp -pl addons/foggy-dataset-model-cache test`：
    124 tests、全部通过。
  - Runtime 聚焦命令
    `mvn -pl foggy-runtime-api -Dtest=RuntimeApiAuthCodeGateTest,RuntimeModelsControllerCompatibilityTest,RuntimeModelOperationsDescribeTest,RuntimeModelValidationIsolationTest test`：
    15 tests、全部通过。
  - 权限、签名、预聚合、Compose、Pivot、成员权限聚焦 Engine 命令：
    121 tests、全部通过；自动配置补充回归 `DbModelAutoConfigurationTest` 12 tests、全部通过。
  - `mvn -pl foggy-fsscript -Dtest=FsscriptHttpClientTest test`：
    5 tests、全部通过。
  - `mvn -pl addons/foggy-dataset-model-preagg -Dtest=PreAggRefreshServiceTest test`：
    4 tests、全部通过。
  - `PYTHONPATH=src python3 -m unittest discover -s tests -v`（CLI）：
    94 tests、全部通过。
  - analysis en、analysis zh-CN、semantic-query 分别通过 `quick_validate.py`；三个临时 ZIP
    均通过 `python3 -m zipfile -t` 完整性校验。
- manual_or_experience_evidence:
  - CLI 全局 help 同时显示 `--auth-code`、`--authorization` 与 `members`，member 子命令显示
    `list MODEL DIMENSION`。
  - 静态敏感信息扫描未发现生产代码记录 Authorization/token；命中仅为测试假值。
  - bridge、CLI/semantic-query 路径和 analysis Skill 均通过 `git diff --check`
    （仅存在仓库既有 CRLF 转换警告）。
- deviations: none
- residual_risks:
  - `SECURITY_SCOPED` 构建仍按批准边界保持未实现并 fail fast；本次只交付安全拒绝，不交付
    scoped refresh。
  - 上级仓库存在失效的 Odoo 嵌套 worktree 元数据，导致无路径限定的 `git status/diff`
    失败；已对本交付所有明确路径完成限定检查，不影响产品测试。
- reused_evidence:
  - 复用并扩展既有 Runtime auth、field/access/member、preagg、Compose、Pivot、cache 和
    demo/fixture 全量测试基线。
- omitted_validation_and_reason:
  - 未运行 release、publish、push、真实客户权限服务、全数据库矩阵及超过 30 分钟的 authority/
    replay/full-chain；交付契约明确不要求，且 focused/affected/Engine 全量证据未触发升级条件。
  - 环境无 PowerShell，未执行 PowerShell 包装脚本；改用同源 `quick_validate.py` 与相对路径
    ZIP 生成/解包完整性校验。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue:
  - repository owner 2026-07-25 确认的 Runtime 内生权限、开放模型、作者 `get/post` 和
    权限安全预聚合语义。
- architecture / glossary:
  - `docs/architecture/runtime-permissions-and-preaggregation.md`
  - `docs/architecture/system-overview.md`
  - `docs/architecture/runtime-and-model-lifecycle.md`
- related work items:
  - `docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`
