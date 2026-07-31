---
doc_role: design-and-roadmap
version: 9.5.3
status: PROPOSED
recorded_at: 2026-07-31
technical_baseline: workitems/SPIKE-runtime-model-authoring-foundations.md
---

# Runtime 模型创作工作区设计与路线

## 1. 结论

Runtime Console 应提供 TM/QM/FSScript 的有界手工创作闭环，但不建设大型 Web IDE。推荐的最小
模型是：

> 一个工作区锚定一个目标 Namespace，并且只允许修改其中一个 writable Bundle；工作区以该
> Bundle 的不可变草稿 revision 覆盖目标 Namespace 的 live 资源视图，其余 Bundle 作为只读
> 依赖按原身份复用。

工作区不是普通业务 Namespace，也不需要复制整个 Namespace。它只持久化源码草稿、基线身份和
验证证据，不持久化第二套已编译 catalog。每次 validate/query 都从不可变 candidate revision
构造 request-local catalog，用完即释放。

detached validation 已经证明这种 overlay 可以工作；9.5.3 进一步实现了受治理的内部 candidate
query 执行原语。当前缺失的是完整 Bundle capability inventory、工作区 store/resource/revision API
和后续 publish 契约。

## 2. 进一步简化后的边界

为保持流程自洽，9.5.3 采用以下简化：

1. **不创建临时 Namespace。** 隔离由 request-local Bundle、FSScript cache 和 candidate catalog
   提供，目标 Namespace 仅提供数据源、权限和只读依赖上下文。
2. **一个工作区只写一个 Bundle。** 跨 Bundle 修改拆成多个 change set；同 Namespace 其他
   Bundle 只读。
3. **Git 不是前提。** 所有草稿先生成内容寻址的 `candidateRevision`；Git commit 是可选映射，
   发布永远使用已经验证的不可变 revision，不重新读取可移动的 branch HEAD。
4. **不直接编辑 active 目录。** 当前 `/resources/save` 是低层文件保存能力，不承担工作区发布。
   工作区保存只更新草稿；publish 才执行受控的原子替换、refresh 和失败恢复。
5. **不复制依赖 Bundle。** 工作区记录创建时的目标 Namespace `sourceRevision`。依赖源发生变化
   后，工作区进入 `STALE`，必须 rebase/revalidate，避免静默使用漂移依赖。
6. **不持久化 candidate catalog/cache。** 只持久化源码 revision 与证据；编译产物保持请求级。
7. **JAR 不进入编辑、fork、升级或回滚流程。** JAR/classpath 永远只读，也不允许 writable
   Bundle 用同名 TM/QM 静默遮蔽它；升级继续通过重打 JAR/Launcher、发布和重启完成。
8. **不建设跨 Runtime 控制面。** 开发和生产 Runtime 各自在本地 Console 完成导入、验证和
   apply；跨环境只传不可变 release package 或 Git commit。

## 3. 工作区身份与状态

建议最小身份：

| 字段 | 含义 |
|---|---|
| `workspaceId` | Runtime 生成的不可猜测标识 |
| `targetNamespace` | 数据源、权限和只读依赖的运行上下文 |
| `sourceBundle` | 唯一可编辑 Bundle |
| `baseBundleRevision` | 创建工作区时该 Bundle 的内容 revision |
| `baseNamespaceSourceRevision` | 创建时整个 Namespace 的依赖源身份 |
| `candidateRevision` | 当前草稿的不可变内容 hash |
| `sourceKind` | `runtime-managed`、后续可选 `git` |
| `state` | `DRAFT`、`VALIDATED`、`STALE`、`PUBLISHED`、`DISCARDED` |

`save draft`、`validate`、`query`、`publish` 必须是不同动作：

- `save draft`：保存 `.tm`、`.qm`、`.fsscript` 到工作区，生成新 `candidateRevision`。
- `validate`：对该 revision 运行完整 TM/QM/FSScript 解析和依赖构建，不写 live 状态。
- `query`：使用同一 candidate revision、目标 Namespace 数据源和既有权限流水线执行
  validate/preview；不得绕过权限或切换到 live QM。
- `publish`：校验基线仍然 current，并把已验证 revision 原子应用到目标 Bundle，然后刷新目标
  Namespace；失败时保留或恢复旧 revision。

任何内容变更都会使先前验证证据失效。任何 Namespace source revision 变化都会把工作区标记为
`STALE`。`workspaceId` 不能作为权限身份或 Namespace 名称。

## 4. Bundle 能力规则

| Bundle 来源 | 浏览/依赖 | 工作区编辑 | publish | 说明 |
|---|---:|---:|---:|---|
| Runtime-managed external filesystem | 是 | 是 | 是 | 9.5.3 首个 writable 类型 |
| configured external filesystem | 是 | 否 | 否 | 可 export；修改仍由部署配置或外部工具负责 |
| JAR/classpath | 是 | 永远否 | 永远否 | 只读依赖；Console 不提供 fork、覆盖或升级 |
| Git checkout | 后续 | 后续 | 后续 | 作为 workspace source adapter，不改变核心 revision 契约 |

Runtime API 必须返回事实型 capability，例如 `sourceType`、`editable`、`workspaceEligible`、
`managedByRuntimeApi`、`namespaceBindings` 和稳定 artifact/source identity。前端不得根据
`path`、文件扩展名或“是否能看见”推测可编辑性。

### JAR 多 Namespace

“一个不可变 JAR Bundle 被多个 Namespace 使用”在目标模型上可行，但当前实现尚不支持：
`BundleDefinition` 只有一个 `getNamespace()`，资源查找按单值等值过滤，`/bundles` 又只列
external Bundle。

后续应增加显式的 **Namespace binding/mount 层**，让多个 Namespace 绑定同一只读 artifact，
同时各自构建 catalog、解析数据源和权限。不能通过复制 JAR、伪造多个可写 Bundle 或把 JAR
隐式暴露给所有 Namespace 实现。该能力不阻塞首期工作区：首期只复用已经属于目标 Namespace
的 JAR 依赖。

## 5. 当前技术证据

| 能力 | 结论 | 分类 | 证据 |
|---|---|---|---|
| 草稿 TM/QM import 草稿内 FSScript | 支持 | `reusable-now` | `DetachedModelAuthoringFoundationProbeTest` |
| 草稿 QM 依赖同 Namespace external TM/FSScript | 支持 | `reusable-now` | 真实 external Bundle 探针 |
| 草稿 QM 依赖真实 JAR TM/FSScript | 支持 | `reusable-now` | 动态 JAR + Spring `jar:` Resource 探针 |
| 草稿对 live 同名资源的优先级 | source 明确优先 | `reusable-now`，需策略 guard | detached context 先查 source、再 fallback live |
| 成功/失败验证隔离 | live catalog、cache、Bundle inventory、source revision 均不变 | `reusable-now` | focused isolation assertions |
| candidate query | 已有 engine/Runtime 内部调用边界；无 REST route | `delivered-internal-primitive` | `CandidateQuerySession`、`RuntimeCandidateQueryService`、真实 SQLite focused tests |
| Bundle inventory | 只列 external，JAR/classpath 不可见 | `small-extension` | `listExternalBundles()` 调用链 |
| Bundle Namespace cardinality | 当前单值 | `new-runtime-primitive`（多 mount） | `BundleDefinition#getNamespace()` |
| `.fsscript` export/save | 当前不允许 | `small-extension` | resource allowlist 只有 TM/QM/model-list |
| configured external 写入 | 禁止 | `reusable-now` | save 仅接受 Runtime registry record |
| Runtime-managed external 写入 | 支持低层保存 | `reusable-now`，不直接作为 publish | SHA 冲突检查与原子单文件写 |

### Candidate query 已实现边界

当前 `/query/{model}` 仍只服务 live catalog，没有被改造成草稿入口。新增的 Runtime 内部
request-local candidate execution port 显式携带 detached catalog resolution 和 Bundle view，并保证：

- candidate TM/QM/FSScript 与只读依赖来自同一 detached session；
- validate、SQL generation 和 execute 始终使用 candidate model identity；
- 数据源 binding、Namespace 和 Authorization 沿用目标 Runtime 的既有规则；
- 权限、result filter、query execution step 和安全限制不被旁路；
- candidate 不访问共享 L1/L2 cache，不启用 pre-aggregation/hybrid query，session 关闭后释放
  request-local 引用；
- source/content revision 在执行前后校验，其他 Bundle 同名覆盖 fail closed；
- 响应携带 `candidateRevision`、base source revision、phase 和诊断证据；
- pivot、Compose/CTE、Semantic SQL、memory-grid 和 synthetic member 在具备完整 request-local pin
  前返回稳定 unsupported 错误。

该端口没有新增 REST、临时 Namespace、持久 candidate catalog 或 workspace 状态。它解除查询侧
核心阻断，但不能被描述为已经交付工作区、资源编辑或发布能力。

## 6. 创建、验证和发布流程

### 开发 Runtime

1. 用户在目标 Namespace 选择一个 `workspaceEligible` Bundle。
2. Runtime 从当前 Bundle 或后续 Git checkout 创建内部草稿快照，并记录两个 base revision。
3. 用户手工或 Agent 修改 TM/QM/FSScript；每次保存生成新 candidate revision。
4. 对同一 revision 执行 diff、validate 和 candidate query。
5. publish 前检查 Namespace/source Bundle 未漂移，并明确展示目标、revision 和影响模型。
6. 原子替换目标 Bundle revision并 refresh；成功后记录已发布 revision，失败则保持/恢复旧版本。

### 生产 Runtime

1. 从已验证 candidate revision 生成不可变 release package；不以 branch 名称作为发布输入。
2. 在生产 Runtime 导入为工作区草稿，并锚定生产目标 Namespace。
3. 使用生产依赖、数据源和权限执行 validate/candidate query；这不是数据库或外部服务沙箱。
4. 显式确认后原子 apply 到正式 Bundle 并 refresh。
5. 保留前一 Bundle revision 和发布证据，支持受控 rollback。

这样保留了“先隔离验证、再更新正式 Namespace”的语义，但不引入临时业务 Namespace、JAR
复制或跨 Runtime 中央编排。

## 7. 后续交付顺序

| 顺序 | 建议 workitem | 交付边界 | 进入条件 |
|---:|---|---|---|
| 1 | Runtime candidate-query overlay | request-local candidate resolve/validate/query port、权限与 cache 隔离、stale source guard；无 UI；已实现并完成独立验收 | 本技术探针独立验收 |
| 2 | Runtime authoring workspace API | 内部 workspace store、内容 revision、单 writable Bundle、TM/QM/FSScript 资源、diff/validate/query；无 Git | 1 独立验收通过 |
| 3 | Console 最小手工闭环 | 创建/打开工作区、资源树、轻量编辑、diff、诊断、validate/query、开发环境 publish/恢复 | 2 的 API 契约冻结 |
| 4 | Release package 与生产 promotion | 不可变导出/导入、生产 revalidate、apply、rollback 与证据 | 3 的开发闭环验收 |
| 5 | 可选 Git adapter | clone/branch/commit/push、commit 与 candidate revision 映射；无 Git 路径保持完整 | 4 的 revision 契约稳定 |
| 6 | JAR 多 Namespace binding | 只读 artifact 多 mount、冲突检查、per-NS catalog/权限隔离；不含 JAR 升级 | 有真实多 Namespace 部署需求 |
| 7 | VS Code 插件与 Console Agent | 复用同一 workspace/diff/validate/query/publish API；Agent 高风险动作逐步确认 | 手工闭环长期稳定 |

每个 workitem 在实施前单独冻结 canonical delivery spec。当前不把 2 至 7 合成一个大交付，也不
提前承诺 Git、JAR mount 或 Agent 的 UI。

## 8. 下一 workitem 的必须通过项

顺序 2 是 candidate-query overlay 验收后的下一 workitem。其交付契约至少应要求：

- workspace 使用不可猜测 identity，固定一个目标 Namespace、一个 enabled Runtime-managed writable
  Bundle、`baseBundleRevision` 和 `baseNamespaceSourceRevision`；
- store 只持久化草稿源码、内容寻址 revision、状态和验证证据，不持久化 candidate catalog、查询
  结果或临时 Namespace；
- resource contract 首期只允许 `.tm`、`.qm`、`.fsscript`，并具备规范化路径、symlink/traversal、
  数量/大小、原子 batch 和 optimistic revision guard；
- save、diff、validate 和 query 是不同动作；每次内容变化都生成新 revision 并使旧验证证据失效；
- validate/query 只能调用已验收的 candidate port，并固定调用方提交的 candidate/base revision，
  不能重新读取可移动目录状态或回退 live QM；
- 依赖 source revision 漂移后 workspace 明确进入 `STALE`，需要 rebase/revalidate；configured external
  与 JAR/classpath 始终只读且不可被同名遮蔽；
- Runtime API 返回事实型 Bundle capability 和稳定 workspace/error envelope，Console 不从路径猜测
  editable/workspaceEligible；
- 本阶段不实现 publish/apply/rollback、Git、Console、Agent、JAR 多 Namespace 或跨 Runtime 编排，
  这些能力继续由后续独立 workitem 冻结。
