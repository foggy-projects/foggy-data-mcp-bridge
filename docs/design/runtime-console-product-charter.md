---
doc_role: product-charter
status: current
scope: foggy-runtime-console
origin_iteration: 9.5.2
last_reviewed: 2026-07-31
---

# Runtime Console 产品章程

## 1. 文档定位

本文定义 Runtime Console 跨迭代持续有效的产品目标、目标用户、体验原则和能力边界。后续
Console 需求、设计和交付契约应引用本文，并在扩展能力前判断是否仍属于 Runtime Console。

本文不是某个功能的实现规格，也不替代 Runtime API、模型生命周期和权限架构。实现事实仍以
`docs/architecture/`、Runtime API 契约和对应版本 workitem 为准。

## 2. 产品定位

Runtime Console 是面向持有管理凭据的 Runtime 运维人员和语义模型开发者，用于操作、检查和
诊断**单个 Foggy Runtime** 的轻量管理工作台。

它把已有 Runtime API 能力组织成安全、可理解、可恢复的浏览器操作闭环，降低直接调用 API、
编写请求和定位运行问题的成本。它不是通用 AI 数据 BI 平台、业务用户分析门户或企业级多租户
管理后台。

产品成功不以页面或功能数量衡量，而以用户能否回答并处理以下问题衡量：

1. 当前连接的是哪个 Runtime，它是否处于可用状态？
2. 当前操作属于哪个 Namespace，后续请求是否使用同一上下文？
3. 当前空间有哪些数据源绑定、Bundle 来源、QM/TM 和资源关系？
4. 模型能否校验、刷新和查询；失败发生在哪个生命周期阶段？
5. 必要时能否下钻到 Table、SQL、Compose 或 FSScript 完成工程诊断？

## 3. 目标用户

| 用户 | 优先级 | 核心任务 |
|---|---:|---|
| Runtime 运维与部署人员 | 核心 | 检查运行状态，配置和测试数据源，诊断 Namespace，执行受控生命周期操作 |
| TM/QM、Bundle 开发者 | 核心 | 检查模型结构和来源，校验、刷新和验证查询，定位模型问题 |
| Runtime 集成开发者 | 次级 | 调试 Query、SQL、Compose、FSScript 和 Runtime API 返回 |
| 数据分析师 | 辅助 | 验证模型与查询结果，但不把 Console 作为日常 BI 分析产品 |

企业管理员、普通业务用户和 BI 消费者不是当前 Console 的目标用户。界面可以保留必要的 Runtime
术语和较高信息密度，但常用任务不得要求用户先理解 Header、内部状态或原始 JSON。

## 4. 核心产品原则

### 4.1 单 Runtime、真实上下文

- Console 只操作当前同源 Runtime，不承担多个 Runtime 或集群聚合。
- Namespace 是所有相关页面和请求的权威上下文。
- 顶部状态、路由、页面选中态和实际 `X-NS` 必须一致。
- 数据源注册表等 Runtime 全局资源必须明确标记为全局，不伪装成 Namespace 私有资源。

### 4.2 任务优先，资源就地

- 顶部导航表达稳定任务域，不按代码模块或 Controller 组织。
- 左侧区域只承担当前任务的资源索引、筛选和选中态，不混入全局创建或上下文切换动作。
- 主区域承载详情、编辑、验证、执行、结果和诊断。
- 同类资源使用一致的名称、状态、来源、摘要和操作词汇。

### 4.3 薄而真实的 Runtime 操作面

- Console 复用并忠实表达 Runtime API、RuntimeEnvelope、TM/QM、Bundle 和查询语义。
- 不在浏览器中发明第二套资源状态、发布语义或权限语义。
- 前端便利校验不能替代服务端校验；浏览器路由和可见性不能作为安全边界。
- Console 不承载 Runtime 管理业务 API，也不把管理凭据提升为数据面身份。

### 4.4 安全操作与可恢复诊断

- 高风险、破坏性或发布类操作必须说明目标、范围、结果和恢复方式，并按风险提供确认。
- 加载、成功、空、失败和迟到响应均应有明确状态。
- 错误优先解释发生阶段、影响和下一步，不只展示技术异常。
- 用户输入和草稿应尽量保留；Namespace 切换必须清除可能被误认的新空间结果。

### 4.5 渐进披露

- 常用浏览、校验和诊断任务默认使用结构化界面。
- 原始 JSON、路径覆盖、Compose 和 FSScript 等专家能力放在清晰标记的高级区域。
- 专家能力可以高效，但不得让普通操作依赖复制内部 payload。
- 不为隐藏复杂度而篡改底层语义；无法安全简化时应明确展示边界。

## 5. 信息架构

| 区域 | 职责 | 不承担 |
|---|---|---|
| 顶部命令区 | Runtime 身份、当前 Namespace、连接状态、退出 | 资源 CRUD、长表单、页面级动作 |
| 顶部主导航 | 概览、数据源、数据与模型空间、查询、SQL、执行工具等稳定任务域 | 动态资源列表 |
| 左侧资源区 | 当前页面的 Namespace、Bundle、QM、Table、历史或其他候选资源 | 新建 Namespace、全局功能菜单 |
| 主工作区 | 结构化详情、编辑、校验、执行、结果和诊断 | 跨 Runtime 聚合 |
| 高级操作区 | 原始 payload、资源路径、Compose、FSScript 等专家入口 | 默认主流程 |

Namespace 首先是请求隔离和资源归属上下文，不是已经具备独立存储与生命周期的 CRUD 实体。
Namespace 的创建、删除、归档或发现规则必须先形成独立设计，不能通过增加一个文本框暗示已经
存在相应产品契约。

## 6. 能力边界

### 6.1 边界内

- Runtime 状态、能力和安全模式的安全化展示。
- 数据源注册、诊断、连接测试和 Namespace 默认绑定。
- Namespace、Bundle、资源、QM/TM 的发现、关系展示和详情检查。
- Bundle 资源的现有导出、保存和受控高级操作。
- 模型 validate、refresh、generation 和生命周期诊断。
- Query、只读 SQL、Compose 和 FSScript 的验证、执行与结果检查。
- TM 草稿等不自动保存、不自动注册、不自动发布的辅助产物。
- 搜索、筛选、批量安全操作、错误恢复、响应式和可访问性优化。

### 6.2 必须独立设计后才能进入

- Namespace 创建、删除、归档和生命周期。
- TM/QM/FSScript 完整编辑、保存、注册、校验、发布和回滚闭环。
- AI Agent 生成或修改模型、脚本、查询以及代表用户执行工具。
- 持久化或共享的查询、草稿、历史、偏好和个人工作区。
- 多 Runtime、集群或环境切换。
- 新的数据面身份传递、权限模拟或管理面代查。

这些能力并非永久排除，但会引入新的资源所有权、状态、安全或人机协作语义，必须建立专门的
设计和交付契约。

### 6.3 非目标

- 通用 AI 数据 BI 平台、Dashboard 和业务报表设计器。
- 面向普通业务用户的自然语言分析门户。
- 账号、SSO、OAuth/OIDC、RBAC、租户、审批和审计平台。
- 多 Runtime/集群运维、部署编排、监控告警和长期指标平台。
- Console 自建 Git 托管、完整多人协作/代码审查平台或通用 Web IDE；受控接入外部 Git
  checkout/commit/push 和维护发布 revision 不属于自建 Git 平台。
- 任意数据库 DDL/DML 管理工具。
- 独立 Node 服务、BFF、SSR、微前端或另一套 Console 业务 API。

## 7. 视觉与交互语言

Console 采用克制、精确、适合高信息密度工程界面的视觉语言。黑白和线条可以作为基础，但不是
只能使用黑白。

- 以中性色背景、清晰线条、稳定网格和明确文字层级建立主体。
- 允许有限强调色，但颜色必须承担语义，例如当前选中、链接、成功、警告、错误或高风险动作。
- 同一种语义使用同一种颜色；不使用无意义的装饰色、彩虹状态或大面积渐变争夺注意力。
- 状态不能只靠颜色表达，必须同时提供文字、图标、形状或位置等冗余线索。
- 一个操作区域只保留一个明确主动作，次要与高级动作降低视觉权重。
- 优先支持桌面端高效操作和平板端管理；移动端保证信息检查和基本闭环，不以手机端完整工程作业
  为设计目标。
- 视觉个性不得牺牲对比度、键盘焦点、触控目标、错误辨识和数据可读性。

## 8. 新需求准入检查

一项需求同时满足以下条件时，通常仍属于 Runtime Console：

1. 主要服务于 Runtime 运维、模型开发或集成调试人员。
2. 聚焦单个 Runtime 的操作、检查、验证或诊断。
3. 忠实使用现有 Runtime 契约，或先完成必要的 Runtime 契约设计。
4. 不引入未经设计的身份、协作、发布、持久化或资源生命周期。
5. 不把 Console 转变为通用 AI BI 产品或数据库管理平台。

任一条件不满足时，应暂停实现并创建独立设计或版本 workitem。若能力改变部署拓扑、权限边界、
Runtime API、模型生命周期或持久化状态，还必须同步 canonical 架构。

## 9. 模型创作方向与开放问题

Runtime Console 可以提供有边界的 TM/QM/FSScript 手工编辑，用于完成产品闭环、AI 不可用时
继续工作，以及对 AI 结果做小范围检查和微调。该能力不是大型 Web IDE：

- 不在 Console 中建设终端、插件市场、任意文件系统和完整 Git IDE。
- 专业开发场景可以跳转到部署方提供的 VS Code Server，并由独立 Foggy VS Code 插件复用同一
  校验、工作区和发布契约。
- 手工编辑与 AI Agent 修改同一个工作草稿，共享 diff、validate、测试、保存和发布流水线，
  不形成两套互相漂移的能力。
- Git 仓库是首选权威源，但不能作为使用前提；无 Git 场景仍需支持受控本地快照、编辑、验证和
  发布。
- TM/QM 可以 import FSScript，工作区必须以完整 Bundle 和可执行依赖闭包为边界，不能只复制
  被选中的 `.tm`/`.qm` 文件。
- Namespace 是工作区的运行一致性边界，Bundle 是源码和可编辑性边界。工作区应从目标
  Namespace 的 Bundle 集合和数据源上下文建立隔离快照，再对选中的可写 Bundle 叠加修改。
- 每个 Bundle 必须显式报告来源类型和可编辑能力。JAR/classpath、配置托管或其他不可写 Bundle
  作为只读依赖继承，不能因为模型在 Console 中可见就显示保存入口。
- JAR/classpath Bundle 永远只读，但同一个不可变 Bundle artifact 可以被多个 Namespace 显式挂载
  和复用；各 Namespace 必须独立构建 catalog、解析数据源与权限上下文，不能共享已编译的可变
  模型状态。
- JAR/classpath Bundle 的版本升级不属于 Console authoring/promotion 流程。用户修改这类 Bundle
  后必须重新构建业务 JAR 和 Launcher、重新发布并重启服务；Console 不提供 JAR artifact
  注册、热切换、回滚或回收能力。
- 对允许派生的只读文件/远程 Bundle，如需修改，必须显式创建带来源记录的 writable
  fork/overlay。JAR/classpath Bundle 不允许在 Console 中 fork 后覆盖其同名模型；只能在可写
  Bundle 中新增依赖它的新资源，修改 JAR 内资源必须回到源码工程重新构建和部署。

是否集成 AI 编程 Agent 以及隔离工作区和发布流程的具体契约，仍需独立设计。后续至少需要回答：

- 手工编辑是只生成/下载草稿，还是可以保存、注册、validate、refresh 和回滚？
- 工作区以 Bundle 为源代码粒度、以 Namespace 为运行隔离粒度时，如何表达跨 Bundle 只读依赖？
- Runtime 如何表达“基于目标 Namespace 快照、只替换部分 Bundle”的 workspace catalog，
  避免复制 JAR Bundle 或将工作区误注册成普通业务 Namespace？
- 如何让同一个进程内已加载的只读 JAR Bundle 被多个 Namespace 挂载，同时保持模型冲突检查、
  source identity、缓存与 catalog generation 隔离？
- Console 编辑的是 Git worktree、Runtime-managed Bundle、本地快照、内存候选还是已发布模型？
- 无 Git 场景如何生成不可变 revision、保留上一个可恢复版本并避免本地工作区成为无治理的源码
  真值？
- Agent 可以建议、生成、修改到什么程度，哪些工具调用必须由用户逐步确认？
- 手工与 Agent 是否共享同一草稿、diff、校验、发布和恢复链路？
- 如何避免管理 Token 被误当作用户身份，如何记录 Agent 的动作证据？
- 隔离 Namespace 如何复用目标 Namespace 的数据源、宿主能力和配置，同时避免把“catalog 隔离”
  误报为数据库、文件系统或外部服务安全沙箱？
- 如何把经过验证的不可变内容 revision 提升到目标 Namespace，而不是重新读取可能已经移动的
  Git branch 或目录？
- 没有 Git、审计和多人协作能力时，哪些生产写操作必须明确禁止？

在这些问题冻结前，现有 TM 草稿、结构化查看和高级 FSScript 执行保持辅助与诊断定位，不应
自然扩张成未定义的在线 IDE。
