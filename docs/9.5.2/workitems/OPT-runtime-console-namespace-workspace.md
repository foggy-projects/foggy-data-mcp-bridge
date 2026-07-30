---
doc_type: delivery-spec
delivery_type: optimization
version: 9.5.2-follow-up
ticket: runtime-console-namespace-workspace
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console Namespace 资源工作区

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 固定 Namespace 用户心智、语义模型归属、资源卡片和详情抽屉的已确认方向，并给出
  Console 架构是否需要重构的结论与交付边界。
- canonical_path: docs/9.5.2/workitems/OPT-runtime-console-namespace-workspace.md

## Goal

- version_goal: 在不改变 Runtime 模型生命周期和 API 语义的前提下，把 Namespace 从技术参数
  重构为用户可理解的“数据与模型空间”。
- target_outcome: 用户进入一个空间后，能够在同一上下文中理解默认数据源、Bundle 来源和
  分析模型；相同资源使用统一卡片语言，模型详情通过右侧抽屉按需展开。
- critical_outcomes:
  - Namespace 的用户名称、边界和当前状态清楚，不再要求用户先理解 `X-NS`。
  - 语义模型进入 Namespace 工作区，不再与 Namespace 作为无关系的顶级同级入口出现。
  - 保留现有 QM 卡片的可扫描表达，并将适合浏览的 Bundle 等资源统一到同一视觉词汇。
  - “描述模型”从页面下方的内联原始输出改为右侧抽屉，不打断模型目录浏览。
  - Namespace、Bundle、QM、TM 的关系表达符合真实语义，不制造错误的严格所有权。
  - 通过有界前端重构消除继续堆叠大页面的风险，不重写 Console 技术栈。
- success_is_sufficient_when: 用户可以从一个明确的当前空间出发，在概览、分析模型、Bundle
  来源和空间设置间完成主要浏览与管理任务；现有 Runtime API、权限和模型生命周期行为保持兼容，
  focused E2E 能覆盖空间切换、卡片浏览和详情抽屉。

## Scope

- in_scope:
  - 将用户可见名称调整为“数据与模型空间”，首次或辅助文案保留技术名 `Namespace`。
  - 重组 Console 顶级导航与 Namespace 工作区的信息架构。
  - Namespace 工作区采用“概览 / 分析模型（QM） / Bundle 来源 / 空间设置”四个任务页签。
  - 在 Namespace 中复用并完善现有 QM 卡片；Bundle 和其他离散、可浏览资源可复用同一资源卡片
    词汇，但不要求所有信息块都卡片化。
  - 将模型描述、来源、物理表和后续可用的依赖信息放入右侧详情抽屉。
  - 将 resource export/save、原始 JSON 和文件路径操作降级为 Bundle 的高级操作。
  - 建立单一、可观察的当前 Namespace 上下文，路由、顶部选择器、请求 `X-NS` 和工作区选中态
    必须一致。
  - 抽取可复用的资源卡片、模型目录、模型详情抽屉和 Namespace 子工作区；页面负责组合，不再
    继续承载全部请求、状态、对话框和样式。
  - 为旧模型入口提供内部导航兼容，避免已有 `/models` hash URL 直接失效。
- affected_modules:
  - `addons/foggy-runtime-console/frontend`
  - `docs/9.5.2`
- external_dependencies: 现有 Vue 3、Vue Router、Element Plus、Runtime API v1；首期不新增依赖。

## Non-Goals

- out_of_scope:
  - 不把 Namespace 改造成新的数据库实体，也不新增 Namespace CRUD；当前 Namespace 仍由默认
    数据源绑定、Bundle 和请求上下文等关系推导。
  - 不把全局 datasource registry 移入 Namespace；空间只拥有默认数据源绑定。
  - 首期不扩展 Runtime API 来返回 QM→TM typed dependency，也不从物理表名猜测 TM。
  - 不在本次增加 TM/QM DSL 编辑器、模型版本管理、模型血缘图或拖拽编排器。
  - 不重写 Console，不替换 Vue/Vite/Element Plus，不引入 Pinia 或新的 BFF。
  - 不改变 Bundle 注册、validate、refresh、catalog generation、权限或 namespace isolation 语义。
- do_not_touch:
  - `foggy-dataset-model-engine` 的模型加载、查询、权限、刷新和 provenance 内部语义。
  - `foggy-runtime-api` 的 RuntimeEnvelope、鉴权 Header 和现有请求兼容。
  - `foggy-data-viewer` 的组件库边界。
  - 与本事项无关的 Console 工作台和当前工作树中的用户改动。
- non_blocking_or_waivable_items:
  - 非关键卡片微动效、装饰细节和高级筛选可以后续优化。
  - 首期可以只展示 Runtime API 已可靠返回的 Bundle/QM 来源信息。
  - 未获得 typed dependency 前，模型抽屉可以不展示“依赖 TM”，但不得展示猜测结果。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 用户名称采用“数据与模型空间”，并保留 `Namespace` 技术别名 | 不同系统对 namespace 含义不同，先告诉用户它在 Foggy 中隔离什么 | API、Header 和代码术语仍保持 `namespace` |
| 语义模型进入 Namespace 工作区 | Namespace 本来就是 datasource binding、Bundle、TM/QM、查询和刷新的隔离轴 | 不改变模型 API；顶级旧路由保留兼容跳转 |
| 用户浏览主线表达为 Namespace → Bundle → QM，并在 QM 内表达其 TM 依赖 | 接近用户理解模型来源和用途的顺序 | `QM → TM` 是 uses/depends-on，不是严格 owns/contains |
| Bundle 是 TM/QM 资源来源，QM 与 TM 不是只能一对一嵌套 | 一个 QM 可依赖多个 TM，一个 TM 也可被多个 QM 复用 | UI 不使用会暗示独占所有权的树形删除/移动语义 |
| 保留现有 QM 卡片表达 | 当前卡片能同时提供标题、说明、字段数、Bundle 和时间字段，适合扫描 | 修复细节不等于更换视觉方向 |
| Bundle 等离散资源采用一致的资源卡片词汇 | 用户可以用同一种方式识别名称、状态、来源、摘要和操作 | 设置表单、诊断日志等不强制卡片化 |
| “描述模型”打开右侧抽屉 | 详情是按需信息，抽屉可保持列表位置和比较上下文 | 抽屉必须处理 loading/error/empty、Escape、焦点返回和移动端宽度 |
| 原始资源操作进入 Bundle 高级操作 | export/save JSON 是管理动作，不应与常用浏览任务竞争 | 能力不删除，原有确认和诊断必须保留 |
| datasource registry 保持顶级入口 | 数据源定义是 Runtime 全局资源；Namespace 只引用默认绑定 | 空间设置提供绑定入口和跳转，不复制 datasource CRUD |
| 当前空间采用单一权威上下文 | 浏览空间与请求空间分离会让用户难以判断操作实际落在哪个 Namespace | 顶部、路由、工作区和 `X-NS` 同步；切换必须明确反馈 |
| 采用 Console 前端有界重构 | 当前页面职责已超过可持续组合边界；抽取 feature 能力可支撑本次及后续资源页 | 保留 API client、session、设计 token、组件库和 Runtime 契约 |

## Semantic Relationship Model

用户可见的浏览路径：

```text
数据与模型空间（Namespace）
├── 默认数据源绑定
└── Bundle 来源
    ├── QM 分析模型
    └── TM 表模型

QM ── uses / depends on ──> 一个或多个 TM
TM ── may be reused by ──> 多个 QM
```

这里有两类关系，界面不得混淆：

- `Namespace → Bundle → resource` 是隔离与来源关系；
- `QM → TM` 是模型依赖图关系。

因此可以把 `ns → bundle → qm → tm` 作为用户探索顺序，但不能实现成带级联所有权含义的严格树。
在缺少 typed dependency API 时，首期只准确展示 `Namespace → Bundle → QM` 和已返回的物理表；
TM 依赖区域应隐藏或显示“当前 Runtime 未提供依赖信息”，不能由 `physicalTables` 反推。

## Recommended Information Architecture

```text
Runtime Console
├── 运行概览
├── 数据源
├── 数据与模型空间
│   ├── 概览
│   ├── 分析模型（QM）
│   ├── Bundle 来源
│   └── 空间设置
├── 查询工作台
├── Tables / SQL
└── 执行工具
```

- 顶部空间控件应表达“当前数据与模型空间”，优先为可搜索选择器；在 Namespace 仍由关系推导的
  阶段，可保留“输入其他空间”的次级入口。
- 选择空间后，模型目录、Bundle、绑定和后续工作台请求都使用同一上下文。
- “分析模型”页签按 Bundle 分组或筛选 QM；`sourceKnown=false` 的模型进入明确的“来源未知”
  分组，仍允许查看详情。
- Bundle 卡片展示名称、状态、路径摘要、监听状态、模型资源统计和主要操作；export/save 等原始
  操作放入“高级操作”。
- 模型卡片保留标题、canonical name、简述、字段数、Bundle、主时间字段和来源状态；主操作为
  “查看详情”，批量选择与刷新保持可用但不抢占卡片主信息。
- 模型详情抽屉承载语义描述、字段/示例、来源、物理表、生命周期信息和未来依赖信息；路径校验与
  全局生命周期诊断仍属于模型工作区工具，不与单模型描述混在同一详情区。

## Console Architecture Assessment

### Conclusion

有必要重构，但只需要 **Console 前端有界重构**，没有证据支持全量重写。

### Evidence

- `NamespacesPage.vue` 当前约 854 行，同时拥有 Namespace 发现、浏览态/请求态、数据源绑定、
  Bundle CRUD、resource export/save、对话框和页面样式。
- `ModelsPage.vue` 当前约 453 行，同时拥有目录、卡片选择、describe、路径校验、refresh 和
  lifecycle diagnostics；模型描述仍以内联区块出现在卡片列表之后。
- `ConsoleShell.vue` 当前约 911 行，同时承担应用导航、顶部 Namespace 自由文本输入、上下文侧栏、
  两个移动端抽屉和大量 shell 样式。
- Namespace 页同时维护 `selectedNamespace` 与 `session.namespace`。用户可以正在浏览一个空间，
  但其他请求仍落在另一个当前空间，增加误操作和解释成本。
- 顶级导航把 Namespace 和语义模型放成同级页面，掩盖了 Runtime 已明确的隔离关系。
- Namespace 列表由当前 session、binding 和 Bundle 关系推导，而不是独立 Namespace entity；
  现有“打开 Namespace”行为若没有解释，容易被理解为创建实体。
- Runtime 已为 QM 列表提供 `bundleName`、`sourceNamespace`、`resourceIdentity`、
  `physicalTables`、`primaryTimeField` 和 `fieldCount`，足够完成首期按空间/Bundle 组织。
- Engine 内部 `ModelProvenance` 已保存 typed model dependencies，但当前模型列表/描述 API 没有
  提供 TM dependency name；首期前端不应跨越该契约边界。

### UX Audit Findings

| Severity | Principle | Finding | User impact | Required direction |
|---:|---|---|---|---|
| 3 | Match / Structure | Namespace 与语义模型作为顶级同级模块，且 Namespace 缺少 Foggy 语义解释 | 新用户需要猜测两者关系，无法形成稳定心智 | 改为“数据与模型空间”，模型成为空间内任务 |
| 3 | Visibility / Error prevention | 浏览中的 `selectedNamespace` 与实际请求的 `session.namespace` 可不同 | 用户可能在错误空间执行刷新、绑定或查询 | 建立单一权威上下文并持续显示当前空间 |
| 2 | Recognition / Structure | 模型描述位于卡片列表下方，并与路径校验并列 | 用户点击后要寻找结果，长列表中容易失去原位置 | 使用右侧详情抽屉和明确选中态 |
| 2 | Minimalist design | Bundle、原始 resource JSON 和空间绑定在同一页面竞争 | 常用浏览被管理细节淹没 | 任务页签 + 高级操作渐进披露 |
| 2 | Consistency | QM 已有成熟卡片，Bundle 等资源仍使用不同密度和动作结构 | 用户需重复学习相似资源的读法 | 抽取统一资源卡片词汇 |
| 2 | Affordance / Tolerance | 顶部 Namespace 是可直接改写的自由文本 | 切换和输入错误看起来相同，缺少候选与上下文确认 | 使用可搜索选择器和次级手动输入 |

### Strengths to Preserve

- QM 卡片已经具备清晰的标题、说明、来源状态和关键元数据，符合识别优于回忆。
- refresh/remove 等高影响操作已有确认、busy 状态和结果反馈，应在重构后保留。
- Console 已有统一 API client、session、设计 token、context rail 和移动端 Drawer 基础，不需要
  另起一套架构。
- Runtime 的 Namespace isolation、Bundle source provenance 和原子刷新语义已经清楚，前端只需
  正确映射，不必重新定义后端模型。

### Recommended Refactor Boundary

重构后的职责边界应满足：

- Shell 只负责全局导航、当前空间选择、响应式框架和 RouterView。
- Namespace workspace 只负责任务页签、空间级汇总和子能力组合。
- model catalog 能独立提供加载、筛选、选择、卡片和批量动作。
- model detail drawer 独立管理 describe 请求、loading/error/empty 和焦点生命周期。
- Bundle catalog 独立提供卡片、编辑与高级 resource actions。
- 当前 Namespace 由一个 composable/store 负责，路由恢复和 API Header 使用同一值。
- 资源卡片共享布局、状态、metadata 和 action slots，具体资源只提供自己的内容。

具体文件名、目录名和组件拆分数量由 Ultra 在上述职责边界内自主决定。

不推荐：

- 继续只在 `NamespacesPage.vue` 内加入模型卡片和抽屉；
- 用 CSS 复制出第二套 Bundle 卡片；
- 为本次引入新的全局状态框架；
- 为表现层关系新增严格的后端所有权层级；
- 在同一交付里顺带重写所有 Console 页面。

## Acceptance Criteria

- [x] AC-1: 顶级导航使用“数据与模型空间”且语义模型不再作为无关系的同级主入口；旧
  `/models` 路径仍能到达当前空间的分析模型视图。
- [x] AC-2: Namespace 工作区包含“概览 / 分析模型（QM） / Bundle 来源 / 空间设置”，刷新后
  能恢复当前空间和子视图，空 Namespace 仍保持兼容。
- [x] AC-3: 顶部、路由、工作区选中态和 Runtime 请求使用同一 Namespace；不存在“正在浏览 A、
  请求落在 B”的无提示状态。
- [x] AC-4: Namespace 概览用用户语言解释其用途，并同时显示默认数据源、Bundle 数、可见 QM 数
  和当前空间状态；缺失信息有明确空态。
- [x] AC-5: 现有 QM 卡片表达被保留并复用，至少展示 caption/canonical name、description、
  field count、Bundle、primary time field 和 source status 中 Runtime 实际返回的字段。
- [x] AC-6: Bundle 使用与 QM 一致的资源卡片词汇，状态和主要操作可扫描；原始 resource
  export/save 从主视图移入 Bundle 高级操作，能力不丢失。
- [x] AC-7: 点击模型“查看详情/描述模型”打开右侧抽屉；抽屉具备 loading、error、empty、关闭、
  Escape、焦点返回和窄屏行为，不再在卡片列表下方渲染同一描述区。
- [x] AC-8: UI 明确区分 Bundle 来源关系和 QM→TM 依赖关系；没有 typed dependency 数据时不从
  物理表猜测 TM，也不显示错误的严格层级。
- [x] AC-9: 模型选择、selected refresh、refresh all、路径校验和生命周期诊断仍可完成，危险或
  广泛影响操作继续二次确认。
- [x] AC-10: Shell、Namespace workspace、model catalog/detail 和 Bundle catalog 的职责按本文
  边界拆分，不在单一页面复制完整卡片/抽屉/请求实现。
- [x] AC-11: 桌面与移动端均能完成空间切换、QM 浏览、详情抽屉和 Bundle 高级操作；键盘焦点与
  当前/选中状态可感知。
- [x] AC-12: `npm run typecheck`、`npm run test:unit`、`npm run build` 和 focused Playwright
  实际运行通过，且没有改动 Runtime API 与模型引擎契约。

## Contract / Data / Security Constraints

- API or event contract:
  - 首期继续使用现有 `/api/v1/models`、`/models/{model}/describe`、`/bundles`、
    `/namespaces/{namespace}/datasource` 和 resources API。
  - `X-NS`、`X-Foggy-Runtime-Code`、RuntimeEnvelope 和业务 `Authorization` 语义不变。
  - `/models` 前端 hash route 可重定向或兼容映射，不删除服务端 API。
  - 若后续新增 dependency API，应返回 typed model key/kind/source，不允许只返回无法区分
    TM/QM 的字符串；该契约不属于首期实现。
- data and migration: 无数据库变更、无模型资源迁移、无 Namespace 实体迁移。
- compatibility and rollback:
  - UI 改动可通过回退 Console 前端构建恢复。
  - 旧模型入口需保持导航兼容；Runtime API 调用方不受影响。
  - `sourceKnown=false`、无 Bundle、空 Namespace 和无模型场景必须保留。
- permissions and secrets:
  - management token、数据源 secret 和业务 Authorization 不得进入 URL、页面详情或测试证据。
  - 详情抽屉只展示现有 describe 允许返回的信息。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1–AC-3 | must-pass | major | unit + Playwright | 现有 route/session tests 可复用未受影响部分 | 路由、空间切换和请求 Header 断言 |
| AC-4–AC-8 | must-pass | major | unit + Playwright + visual manual | 现有 Runtime API mock fixtures | 桌面/移动端截图、抽屉状态和数据映射断言 |
| AC-9 | must-pass | major | affected Playwright | 现有 Console lifecycle E2E 可复用 | selected/all refresh、validate 和确认行为 |
| AC-10 | must-pass | medium | source review + typecheck | 无 | 职责边界 review 记录与精确 changed paths |
| AC-11 | must-pass | major | Playwright desktop/mobile + keyboard manual | 现有 responsive shell evidence 仅作参考 | viewport、Escape、focus return 和选中态证据 |
| AC-12 | must-pass | major | typecheck + unit + build + focused E2E | 未修改测试的历史结果不能替代本次运行 | 精确命令、exit code、测试数量和失败说明 |
| QM→TM dependency API | non-goal | deferred | none | Engine provenance 代码证据 | 记录未展示猜测依赖即可 |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: `npm run typecheck`、相关 unit tests、静态 route/API mapping review，
  预计每轮 1–5 分钟。
- medium_validation: `npm run build` 和 focused Playwright Namespace/Models flow，预计每轮
  5–15 分钟。
- expensive_validation: 无默认昂贵验证；只有 Console Maven package 或受影响 launcher smoke
  被代码改动触发时才建议，预计 10–25 分钟。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: 仅在实现意外改变 Runtime API/auth、静态资源装配或跨模块
  公共契约时重新评估；纯前端重构不触发。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: Console addon Java/Maven 装配、runtime-api 或 launcher 被修改。
- maximum_expensive_attempts: 2；连续两次因非产品环境问题失败则设置 `NEEDS_REPLAN`。
- reusable_evidence: 未触及路径的现有 Console API mock 和 lifecycle tests 可复用；任何涉及
  Namespace 状态、路由、模型卡片或 Drawer 的旧 UI 证据均需重跑。
- stop_when_evidence_is_sufficient: 所有 must-pass AC 有一次当前源码对应的绿色自动化证据，
  desktop/mobile/keyboard 体验证据覆盖关键状态，且 source review 确认无 API/engine 改动。
- validation_not_required: release authority、数据库矩阵、publish、tag、source-seal、模型查询
  结果正确性全链路。

## Waiver Policy

- waivable_items: 非关键装饰、微动效、高级筛选、未被 API 支持的 TM dependency 展示。
- authorized_role: product owner / delivery owner
- non_waivable_guards: Namespace 请求一致性、权限/secret、安全确认、API 兼容、禁止伪造模型依赖、
  抽屉键盘可达性。
- required_risk_record: 任一 waivable 项必须在 Implementation Result 中记录用户影响和后续入口。

## Bug Context

N/A。本事项是已确认的体验优化与架构收敛，不把当前信息架构问题伪装成单点 BUG。

## Risks and Open Questions

- known_risks:
  - 当前 Console 前端工作树已有大量未提交改动；实现必须逐文件确认并保留用户现有工作。
  - Namespace 不是独立实体，选择器候选仍只能从 session、binding 和 Bundle 关系推导；文案与
    “输入其他空间”入口必须避免暗示持久化创建。
  - 旧 `/models` 路由、context rail 和其他工作台都读取 session Namespace，切换到单一上下文时
    需要避免路由循环或 stale request。
  - 大量模型/Bundle 同时使用卡片会拉长页面；首期应提供搜索/分组并验证真实数据密度。
  - Element Plus Drawer 已提供基础焦点管理，但异步内容、关闭后焦点返回和嵌套高级操作仍需实测。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `AGENTS.md`/`CLAUDE.md`、Console 模块规范和前端专项技能。
- 本文件已由用户批准；实现必须遵守 `APPROVED` 范围，不能重新打开已确认的产品决策。
- 在 scope 内自主决定具体文件、组件、composable 和样式结构，不照抄本文示意为僵化目录。
- 先保护当前脏工作树；不得覆盖或回退与本事项无关的用户改动。
- 如需改变目标、范围、已确认决策、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，不得声称未实际运行的测试通过。
- 未经用户明确批准，不得主动运行预计超过 30 分钟或包含
  authority/replay/rehearsal/source-seal 的大型链路；如认为最终候选需要，只提出一次包含预计
  耗时、范围和决策价值的建议。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary:
  - 顶级导航收敛为“数据与模型空间”，旧 `/models`、`/bundles` hash 路由分别兼容跳转到当前
    Namespace 的分析模型和 Bundle 子视图。
  - 路由 `ns`、顶部可搜索输入、session storage、工作区选中态和请求 `X-NS` 统一使用同一个
    Namespace；空间和四个任务子视图均可在刷新后恢复，空 Namespace 仍不发送 `X-NS`。
  - Namespace 工作区拆为共享数据 composable、统一 `ResourceCard`、`ModelCatalog`、
    `ModelDetailDrawer` 和 `BundleCatalog`；概览与设置由页面负责组合。
  - QM 与 Bundle 使用同一资源卡片词汇；模型 describe 移入右侧抽屉；resource export/save 与
    原始 JSON 移入 Bundle 高级抽屉；模型刷新、路径校验、生命周期诊断和高影响确认保留。
  - 详情明确声明物理表不是 typed TM dependency，在 Runtime API 未提供 typed dependency 时不
    展示或推测 QM→TM 依赖。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/components/ConsoleShell.vue`
  - `addons/foggy-runtime-console/frontend/src/components/resources/ResourceCard.vue`
  - `addons/foggy-runtime-console/frontend/src/features/bundles/BundleCatalog.vue`
  - `addons/foggy-runtime-console/frontend/src/features/models/ModelCatalog.vue`
  - `addons/foggy-runtime-console/frontend/src/features/models/ModelDetailDrawer.vue`
  - `addons/foggy-runtime-console/frontend/src/features/namespace/types.ts`
  - `addons/foggy-runtime-console/frontend/src/features/namespace/useNamespaceWorkspaceData.ts`
  - `addons/foggy-runtime-console/frontend/src/pages/NamespacesPage.vue`
  - `addons/foggy-runtime-console/frontend/src/router.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `docs/9.5.2/workitems/OPT-runtime-console-namespace-workspace.md`
- tests_and_results:
  - `git diff --check`：exit 0。
  - `npm run typecheck`：exit 0。
  - `npm run test:unit`：exit 0；4 个 test files、10 个 tests 全部通过。
  - `npm run build`：exit 0；Vite 转换 2460 个模块并完成生产构建；仅有依赖包 PURE 注释和既有
    大 chunk 警告。
  - `npm run test:e2e -- --grep "namespace workspace keeps"`：exit 0；desktop Chromium 与
    Pixel 7 mobile Chromium 共 2 个 focused tests 全部通过（7.2s）。
  - `npx playwright test`：exit 0；desktop/mobile 共 6 个 Console E2E 全部通过（18.2s）。
- manual_or_experience_evidence:
  - desktop 1280×720 全页截图：
    `addons/foggy-runtime-console/frontend/test-results/runtime-console-namespace--3ae14--and-keyboard-focus-aligned-desktop-chromium/namespace-workspace-desktop.png`
  - Pixel 7 全页截图：
    `addons/foggy-runtime-console/frontend/test-results/runtime-console-namespace--3ae14--and-keyboard-focus-aligned-mobile-chromium/namespace-workspace-mobile.png`
  - focused Playwright 实测空间切换、刷新恢复、QM 卡片、详情抽屉、Bundle 高级操作；按 Escape
    关闭模型抽屉后断言焦点返回“查看详情”，移动端断言抽屉宽度不超过 viewport。
- deviations: none
- residual_risks:
  - Playwright Linux 宿主未安装 CJK 字体（`fc-list :lang=zh` 无结果），因此截图中的中文显示为
    fallback 方框；DOM 文案、可访问名称和交互断言均通过，Console CSS 已声明 Noto/思源/微软雅黑
    字体 fallback，未为本次有界重构引入大体积字体依赖。
  - Vite 仍报告既有 vendor 大 chunk 警告；本交付未新增依赖，Namespace 功能保持懒加载页面边界。
- reused_evidence:
  - 保留并复用当前工作树已有的 Console session/storage、API mock、登录、数据源、查询与执行工具
    E2E；最终全量 6 个 E2E 已基于当前源码重跑，不以历史结果代替。
- omitted_validation_and_reason:
  - 未运行 Maven package、launcher smoke、release authority、数据库矩阵、replay、tag、publish
    或 source-seal；改动严格限制在 Console 前端与交付记录，未触发后端或装配验证条件，且契约明确
    禁止默认运行大型 authority/replay。
- readiness: READY_FOR_SIGNOFF

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-30
- acceptance_record: docs/9.5.2/acceptance/OPT-runtime-console-namespace-workspace-signoff.md
- blocking_items: none
- follow_up_required: yes

## References

- requirement / issue: 用户确认保留 Namespace 中的模型卡片，Bundle 等资源采用相同版面，
  模型描述改为右侧抽屉，并要求先落档再评估 Console 架构。
- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/dev-guide/bundle-namespace.md`
- related work items:
  - `docs/9.5.2/workitems/FEATURE-runtime-web-console-mvp.md`
