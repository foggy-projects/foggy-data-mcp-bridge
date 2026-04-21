# P0-Compose Query QueryPlan 派生查询与关系复用规范-需求

## 文档作用

- doc_type: workitem
- intended_for: design-review / execution-agent
- purpose: 定义 8.2.0.beta 中 Compose Query 从“QM 即黑盒视图”扩展到“QueryPlan 即可复用关系节点”的统一规范，覆盖派生查询、二段聚合、union/join 组合与 SQL 编译边界

## 基本信息

- 目标版本：`8.2.0.beta`
- 需求等级：`P0`
- 状态：`draft`
- 责任项目：`foggy-data-mcp-bridge`
- 责任模块：`foggy-dataset-model` / `foggy-dataset-mcp`
- 交付模式：`single-root-delivery`
- 来源：Compose Query 设计深化

## 前置依赖（blocking）

### ✅ BUG F-3：`_resolve_effective_visible` 跨模型 denied 泄漏 · **已解除**（2026-04-21）

- Upstream workitem：`foggy-odoo-bridge-pro/docs/prompts/v1.4/workitems/BUG-v14-metadata-v3-denied-columns-cross-model-leak.md`（status=`resolved`）
- Upstream 需求：`foggy-data-mcp-bridge-python/docs/v1.6/P0-BUG-F3-resolve-effective-visible-cross-model-denied-leak-需求.md`
- **签收记录**：`foggy-data-mcp-bridge-python/docs/v1.6/acceptance/REQ-P0-BUG-F3-acceptance.md` · decision **`accepted`** · 2026-04-21
- 双端修复完成：
  - Python 侧：`_resolve_effective_visible` 返回形态 `Optional[Set]` → `Optional[Dict[str, Set]]`（per-model）· 7 F-3 tests green · 2430 passed
  - Java 侧：`SemanticServiceV3Impl.mergeFieldInfo` 辅助 + 6 处 `fields.put` 改合并 · 7 F-3 tests green · sqlite 1246 passed
  - Odoo Pro 侧：vendored sync 完成 · xfail 已撤 · fast lane 570 passed
- 对本规范的解锁效果：
  - M5 `BaseModelPlan` 权限绑定链路集成测试可安全覆盖多模型字段独立可见性
  - M8 Odoo Pro `OdooEmbeddedAuthorityResolver` 多模型验收 unblock
  - `script` 工具多模型场景可对外发布

### 其他非阻断但需并行跟踪

- REQ-FORMULA-EXTEND F-1 ~ F-8 签收遗留项（见 root CLAUDE.md v1.4 签收记录）与本规范无直接阻断关系，但 Compose Query 编译出的 SQL 会继承 v1.4 formula 引擎能力；任何 formula 层新增限制自动应用到 `DerivedQueryPlan.columns` 表达式

## 背景

当前 ROADMAP 中 8.2.0 的规划将 CTE 能力纳入里程碑，但现有 Compose Query 的主语义仍然是：

1. `dsl({ model: 'XxxQM', ... })` 基于物理 QM 构造一次查询
2. `withJoin()` 将多个 QM 结果在 SQL 层做 JOIN
3. `column()` 支持 ID 下推
4. 内存 `compute/filter/sort` 属于补充能力

这套设计足以支撑“QM 与 QM 的横向组合”，但不足以表达更高阶的“关系复用”场景，例如：

- 二段聚合：先按 `salesperson + customer` 聚合，再按 `salesperson` 二次聚合
- union 后再聚合：`A union B` 之后继续 `groupBy`
- join 后再筛选/排序/limit：把 join 结果作为新的可查询关系
- 命名中间节点：同一中间结果被后续多个分支复用

这些场景的本质不是“执行结果内存加工”，而是**在一个逻辑关系表达式上继续构建查询**。因此需要把 Compose Query 的核心抽象从“DataSetResult 包装”提升为“QueryPlan 关系节点”。

## 命名约定（本文档统一）

本文档采用以下命名约定，覆盖全部语法与语义示例：

- 顶层入口函数统一为 `from(...)`，规范上它等价于"声明一个关系节点"
- 旧名称 `dsl(...)` 在实现层保留为过渡别名，但不再出现在本规范的示例与语义定义中
- 原 DSL body 中的 `from: plan` 字段统一更名为 `source: plan`，避免与顶层 `from(...)` 视觉/语义双义
- Python 对等入口为 `from_(...)`（Python `from` 是硬关键字）
- `from` 在 foggy-fsscript 中通过 `FsscriptDialect.isKeywordAsIdentifier(FROM, '(')` 方言钩子支持"作为函数名"，不影响现有 SQL 解析路径

## 目标

- 明确 `QueryPlan` 是 Compose Query 的一等抽象，表示“可继续组合、尚未最终执行的逻辑关系”
- 支持在 `QueryPlan` 基础上继续 `query/groupBy/orderBy/limit/union/join`
- 支持把二段聚合、union 后再查、join 后再查统一描述为“派生查询”
- 明确派生查询的可见字段规则、聚合语义、排序语义、别名语义
- 明确 SQL 编译阶段如何在 `WITH cte AS (...)` 与 `FROM (...) t` 之间选择
- 明确本期**不开放**内存结果再查询，不引入 `DataSetResult.query(...)`

## 非目标

- 不在本需求中定义窗口函数 DSL 细节
- 不在本需求中定义递归 CTE DSL 细节
- 不开放 `DataSetResult.memoryQuery(...)` 或其他内存二次加工能力
- 不支持跨数据源 `QueryPlan` 组合
- 不要求用户显式区分最终编译为 `WITH` 还是 `FROM (...)`
- 不引入 Cube/Looker 风格 measure 内置 `inner/outer group by` 语义

## 设计原则

### 1. QueryPlan 是关系，不是结果

`QueryPlan` 表示一个尚未最终执行的逻辑关系节点（logical relation），而不是已物化的结果集。它可以来源于：

- 物理 QM：`from({ model: 'SaleOrderQM', ... })`
- 派生查询：`plan.query({ ... })`
- union：`planA.union(planB)`
- join：`planA.join(planB, { ... })`

规范上禁止把 `DataSetResult` 重新当成 DSL 数据源。

### 2. model 只表示物理模型

`model` 字段仅用于指向物理 QM 标识，不承担“上一步查询结果”的语义。

如需在上一步关系上继续查询，统一使用：

- `plan.query({ ... })`
- 或规范内核 `from({ source: plan, ... })`

禁止以下语义混用：

```javascript
from({
  model: previousPlan,
  ...
})
```

### 3. 派生查询只看上一步输出 schema

一个 `QueryPlan` 一旦形成输出列集合，后续对该 `QueryPlan` 的派生查询只能引用这些输出列/别名，不能隐式回到底层 QM 字段。

例如第一段输出：

- `salespersonId`
- `salespersonName`
- `customerId`
- `customerOverdueAmount`

则第二段只能基于上述四列继续查询，不能再直接引用底层 `docState`、`paymentState` 等未投影字段。

### 4. 编译形式属于后端策略，不属于 DSL 语义

同一个 `QueryPlan` 最终可以编译为：

- `SELECT ... FROM (...) t`
- `WITH cte_x AS (...) SELECT ... FROM cte_x`
- 多层 `WITH`

用户只声明关系组合，编译器按方言与复用次数决定物理 SQL 结构。

### 5. 安全上下文属于系统请求上下文，不属于脚本 DSL 参数

在 JavaScript 编排场景下，`QueryPlan` 的执行必须继承宿主请求的系统上下文，而不是由脚本自由拼装安全参数。

需要明确区分两类参数：

- 业务参数：可由脚本中的 `from({...})` / `plan.query({...})` 直接传入，例如普通筛选值、排序值、分页值、业务常量
- 安全参数：必须由请求 header、MCP `ToolExecutionContext` 或服务端构造的 `ComposeQueryContext` / `SemanticRequestContext` 提供，不能由脚本覆盖

典型安全参数包括：

- `authorization`
- `namespace`
- `tenantId`
- `userId`
- `roles`
- `deptId`
- `fieldAccess`
- `deniedColumns`
- `systemSlice`
- 行级权限推导所依赖的安全属性集
- traceId / requestId / correlationId 等链路上下文

规范上，这些值属于“系统请求上下文”，不是 DSL 语义输入。

## 用户语法

### 1. 基础查询

```javascript
const overdueByCustomer = from({
  model: 'ReceivableLineQM',
  columns: [
    'salespersonId',
    'salespersonName',
    'customer$id as customerId',
    'SUM(IIF(isOverdue == 1, residualAmount, 0)) AS customerOverdueAmount'
  ],
  slice: [
    { field: 'docType', op: '=', value: 'AR' },
    { field: 'docState', op: '=', value: 'posted' },
    { field: 'paymentState', op: 'in', value: ['not_paid', 'partial', 'in_payment'] },
    { field: 'customerOverdueAmount', op: '>', value: 0 }
  ],
  groupBy: ['salespersonId', 'salespersonName', 'customerId']
});
```

### 2. 派生查询

```javascript
const salespersonOverdue = overdueByCustomer.query({
  columns: [
    'salespersonId',
    'salespersonName',
    'SUM(customerOverdueAmount) AS arOverdueAmount',
    'COUNT(*) AS arOverdueCustomerCount',
    'MAX(customerOverdueAmount) AS maxSingleCustomerOverdue'
  ],
  groupBy: ['salespersonId', 'salespersonName'],
  orderBy: ['-arOverdueAmount']
});
```

### 3. 规范内核：source

以下两种写法语义等价：

```javascript
const p2 = p1.query({ columns: [...], groupBy: [...] });

const p2 = from({
  source: p1,
  columns: [...],
  groupBy: [...]
});
```

其中 `source:` 字段是规范内核（在另一个 `QueryPlan` 上继续构建查询），`plan.query()` 是它的语法糖。

DSL body 中：

- `model:` 指向物理 QM 标识（基础模型节点 BaseModelPlan）
- `source:` 指向已有 `QueryPlan`（派生查询节点 DerivedQueryPlan）
- `model:` 与 `source:` 互斥，不允许同时出现

### 4. union

```javascript
const merged = currentReceivable.union(historyReceivable, {
  all: true
});

const finalPlan = merged.query({
  columns: [
    'salespersonId',
    'SUM(amount) AS totalAmount'
  ],
  groupBy: ['salespersonId']
});
```

### 5. join

```javascript
const joined = salesPlan.join(leadPlan, {
  type: 'left',
  on: [
    { left: 'partnerId', op: '=', right: 'partnerId' }
  ]
});

const finalPlan = joined.query({
  columns: [
    'partnerName',
    'totalSales',
    'leadCount'
  ],
  orderBy: ['-totalSales']
});
```

## 核心语义

### 1. from 返回 QueryPlan

`from({...})` 返回值规范上定义为 `QueryPlan`，而非自动执行的结果包装。

`QueryPlan` 暴露的核心能力：

- `query(nextDsl)`：基于当前输出 schema 构造派生查询
- `union(otherPlan, options)`：生成 union 关系
- `join(otherPlan, options)`：生成 join 关系
- `execute()`：触发最终 SQL 编译与执行
- `toSql()`：输出最终 SQL 文本与参数，仅用于调试/组合层

### 2. 派生查询字段可见性

派生查询只能看到上一步 `columns` 形成的输出 schema：

- 显式别名优先：`SUM(amount) AS totalAmount` 后续只能按 `totalAmount` 引用
- 无别名字段按规范化输出名暴露
- 未投影字段不可见
- 不支持在派生层重新走维度路径解析，例如未投影 `customer$province` 时，后续不能再次导航该字段

### 3. 聚合语义按阶段切断

每一个 `QueryPlan` 节点都构成一个新的关系阶段：

- 第一阶段有 `groupBy`，则其输出行为等价于 SQL 子查询/CTE 的结果表
- 第二阶段再写 `SUM(customerOverdueAmount)`，语义是对第一阶段结果列再聚合
- `COUNT(*)` 统计的是上一阶段输出行数，而非底层物理表明细行数

### 4. orderBy 语义

- 最终查询上的 `orderBy` 始终生效
- 中间阶段的 `orderBy` 仅在与 `limit/start` 共同出现时才具有保留意义
- 如果中间阶段只有 `orderBy` 但无分页/窗口约束，编译器可忽略或下沉优化，不承诺最终顺序可见

### 5. distinct 语义

`distinct` 作用于当前关系阶段输出列。进入下一阶段后，它的结果表现为普通关系输入，不保留额外标记。

### 6. limit/start 语义

`limit/start` 作用于当前关系阶段的结果，后续派生查询看到的是已经截断后的关系。

这意味着：

- `top10Customers.query({...})` 的输入就是 Top 10 结果
- 不允许编译器把该 `limit` 随意上提或消除

## 编排场景下的权限与请求上下文

### 1. QueryPlan 全链路继承同一请求上下文

在一次 JavaScript Compose Query 执行过程中，脚本内创建的所有 `QueryPlan` 默认继承同一个 `ComposeQueryContext`，该上下文在内部派生 `SemanticRequestContext` 给底层 QM 使用。

执行链路应统一为：

1. HTTP Header / MCP 调用上下文进入系统
2. 宿主层解析出 `ToolExecutionContext`
3. 服务端构造 `ComposeQueryContext`，其中至少包含：
   - `principal`（身份信息，含 `userId / tenantId / roles / deptId / namespace / policySnapshotId?`）
   - `authorityResolver`（权限回调接口，由宿主按嵌入或远程模式注入）
   - `traceContext`
4. `from()` / `QueryPlan.query()` / `union()` / `join()` 在整个计划树中传递该上下文
5. 每个底层 QM 节点在各自查询分析阶段独立完成权限注入

这意味着：

- 同一脚本内的多个 `QueryPlan` 不应出现不同的安全上下文
- `union` / `join` / 派生查询只组合关系，不切换权限主体
- 权限控制始终由底层 QM 在自身 pipeline 中独立完成

### 2. 上游权限场景下，身份信息通过固定协议传入

当权限控制由上游系统负责时，Compose Query 入口不要求一开始就携带某个具体模型的 `fieldAccess` / `deniedColumns` / `systemSlice` 成品。

入口阶段应由上游按约定协议传入“权限主体信息”，例如：

- `Authorization`
- `X-Namespace`
- `X-User-Id`
- `X-Tenant-Id`
- `X-Roles`
- `X-Dept-Id`
- `X-Policy-Snapshot-Id`

这些值的作用是标识“谁在查、以什么权限身份在查”，而不是直接声明某个模型上的最终权限结果。

这样设计的原因是：

- JavaScript 编排能力下，入口时尚未确定最终会命中哪些 QM / TM
- 如果仍沿用单 DSL 思路，要求入口直接携带模型级权限结果，会导致协议不稳定且难以复用
- 上游更适合传递稳定的身份上下文，而不是预判脚本执行后会涉及哪些模型

### 3. 脚本不可覆写安全上下文

规范上禁止以下行为：

- 在 `from({...})` 中显式传入 `authorization`、`tenantId`、`userId`、`roles` 等安全参数
- 在 `plan.query({...})` 中切换 `namespace` 或权限主体
- 在同一脚本内人为构造多套权限上下文并做 `union` / `join`
- 通过脚本直接访问原始 header map 并据此拼接底层安全参数

如确有“以其他身份执行”或“切换命名空间”的需求，应由宿主层单独定义受控能力，不纳入本期 Compose Query DSL 规范。

### 4. BaseModelPlan 首次暴露 schema 前，按模型回调上游解析权限

在“权限落在上游”的前提下，推荐的标准流程为：

1. 上游按约定 header / ToolExecutionContext 协议调用 Compose Query
2. JavaScript 脚本执行过程中，一旦出现 `from({ model: 'XxxQM', ... })` 或其他 `BaseModelPlan`
3. 系统在该 `BaseModelPlan` 首次暴露 schema / 继续参与编排前，收集该脚本此刻需要解析的全部未绑定模型及其底层表信息（来自 `JoinGraph`）
4. 系统通过 `ComposeQueryContext.authorityResolver` 发起一次批量权限解析请求（单个 request 承载 `models: [...]`）
5. 上游按约定格式返回每个模型对应的 `fieldAccess` / `deniedColumns` / `systemSlice`
6. 系统将这些结果分别绑定到对应的 `BaseModelPlan`
7. 后续 `query` / `union` / `join` / schema 推导基于各自受控 schema 继续进行

请求协议固定为批量形态（`models: [...]`），即使当前触发解析的只有一个模型，也以长度为 1 的列表发起，目的是避免后续引入批量优化时破坏协议兼容性。对于第一版，宿主 resolver 内部可以循环实现，不要求真正并发执行。

该流程的关键点是：

- 权限解析发生在具体模型首次被使用时，而不是脚本入口阶段
- 在获得 `deniedColumns` 之前，系统不应把该基础模型的完整 schema 暴露给后续编排
- 上游看到的是“实际命中的模型和表”，而不是抽象脚本文本
- 同一脚本执行周期内，权限结果按"模型 + principal"维度被动缓存；是否做持久化或 TTL 由宿主 resolver 自行决定，本规范不强制

### 5. 回调请求中需要携带模型与底层表信息

系统回调上游权限服务（无论远程 HTTP 还是嵌入 SPI）时，至少应携带：

- 当前请求身份信息（`policySnapshotId` 可空，仅作追踪/审计）
- `namespace`
- 本次实际命中的模型列表
- 每个模型对应的底层数据库表集合

其中“模型对应的底层数据库表集合”应从该模型的 `JoinGraph` 派生，用于帮助上游做表级/列级权限裁决。

请求体形态固定为批量形态：

```json
{
  "principal": {
    "authorization": "Bearer xxx",
    "userId": "u001",
    "tenantId": "t001",
    "roles": ["sales_mgr"],
    "deptId": "dept001",
    "policySnapshotId": null
  },
  "namespace": "default",
  "traceId": "trace-xxx",
  "models": [
    {
      "model": "SaleOrderQM",
      "tables": ["sale_order", "res_partner", "sale_order_line"]
    },
    {
      "model": "CrmLeadQM",
      "tables": ["crm_lead", "res_partner"]
    }
  ]
}
```

单模型解析也以长度为 1 的 `models` 数组下发，不引入单模型快捷路径。

### 6. 上游返回结果必须按模型绑定，不返回一份全局权限成品

上游权限服务返回时，必须以“模型”为最小绑定单元，而不是返回一份全局 `deniedColumns` / `systemSlice` 让系统自行猜测归属。

建议返回结构如下：

```json
{
  "bindings": {
    "SaleOrderQM": {
      "fieldAccess": ["partner$id", "partner$caption", "amountTotal"],
      "deniedColumns": [
        { "table": "sale_order", "column": "internal_cost" }
      ],
      "systemSlice": [
        { "field": "orgId", "op": "=", "value": "org001" }
      ]
    },
    "CrmLeadQM": {
      "fieldAccess": ["partner$id", "expectedRevenue"],
      "deniedColumns": [],
      "systemSlice": [
        { "field": "ownerDeptId", "op": "=", "value": "dept001" }
      ]
    }
  }
}
```

理由是：

- 同一脚本中不同 QM 可能对应完全不同的表域和权限规则
- 只有按模型绑定，系统才能把权限结果稳定注入到对应的 `BaseModelPlan`
- 这也更符合 `JoinPlan` / `UnionPlan` 下“各叶子模型各自独立受控”的设计原则

### 7. 权限注入仍以底层 QM 为边界

即使在 `QueryPlan` 派生查询、`union`、`join` 场景下，权限注入的最小边界仍然是底层物理 QM，而不是派生层。

具体要求：

- `dsl({ model: 'XxxQM', ... })` 构造的基础节点，必须在自身分析阶段注入权限 slice
- `DerivedQueryPlan` 不重新定义一套独立权限模型，只消费上一步已受控的关系输出
- `UnionPlan` / `JoinPlan` 两侧各自保留底层 QM 独立权限注入结果，再进入组合
- 派生层可追加业务筛选，但不应削弱底层已注入的强制权限条件

### 8. 上游权限协议需要同时适配远程回调与内嵌引擎

上游权限对接不能只假设“我们主动 HTTP 回调”这一种形态，因为存在内嵌引擎场景（例如 foggy-odoo-bridge-pro 嵌入模式下，Foggy 引擎与 Odoo 同进程运行）。

因此规范上应支持两种接入模式：

- 远程回调模式：宿主在 `ComposeQueryContext` 中注入一个 `HttpAuthorityResolver`，运行时发起 HTTP 请求
- 内嵌回调模式：宿主在 `ComposeQueryContext` 中注入一个本地实现的 `AuthorityResolver`（SPI / Bean / 任意局部对象均可）

两种模式的输入输出契约（`AuthorityRequest` / `AuthorityResolution`）必须完全一致，差别只在调用方式，不在数据结构。嵌入模式下：

- 不要求宿主使用 HTTP header 协议；身份信息直接由宿主在构造 `ComposeQueryContext.principal` 时填好
- 典型实现是 `OdooEmbeddedAuthorityResolver(env)`：内部循环调用现有的 `compute_query_governance_with_result(env, user.id, model_name)`，按 `models[]` 顺序组装返回结果
- 远程模式下则需把 `principal` 序列化为 header（`Authorization / X-User-Id / X-Tenant-Id / X-Roles / X-Dept-Id / X-Policy-Snapshot-Id / X-Trace-Id`）发送给上游权限服务

**第一版范围**：只要求嵌入模式（Odoo Pro）可用；远程 HTTP 模式可延后到 8.3.0，但接口签名必须在本期就定好，避免后续破坏性修改。

### 9. 系统级强制条件使用上下文承载，不暴露为普通 DSL 字段

对于“必须强制附加，但又不适合体现在用户 DSL 中”的条件，建议统一承载于服务端请求上下文，例如：

- `SemanticRequestContext.securityContext`
- `SemanticRequestContext.fieldAccess`
- `SemanticRequestContext.deniedColumns`
- `SemanticRequestContext.systemSlice`
- 其他只读的系统级过滤上下文

此类条件不应要求 AI 在脚本中显式书写，也不应暴露为可被脚本删除或覆盖的普通 `slice` / `columns` 参数。

### 10. 失败策略必须 fail-closed

在上游权限模式下，只要出现以下任一情况，本次 Compose Query 都应失败，而不是降级放行：

- 上游权限接口调用失败
- 上游返回结构不合法
- 某个实际命中的模型缺少权限绑定结果
- `fieldAccess` / `deniedColumns` / `systemSlice` 无法成功注入对应模型

这是编排场景下的必要约束，避免“部分模型已鉴权、部分模型未鉴权”导致的权限泄露。

### 11. 对 AI 友好的安全设计目标

在 AI 生成 JavaScript 编排脚本的场景下，权限设计应满足以下目标：

- AI 只需要关注业务查询结构，不需要承担安全参数拼装责任
- AI 即使生成错误查询，也不应突破宿主系统已确定的权限边界
- 生成 DSL 时可依赖稳定、最小化的输入面，降低 prompt 和工具调用复杂度
- 服务端可以对“业务参数”和“安全上下文”做明确分层校验，提升生成稳定性与可解释性

### 12. 兼容迁移要求

从单 DSL 模式迁移到 JavaScript 编排模式时，需要对原有请求参数做一次权限分类清理：

- 标记哪些参数是纯业务过滤条件
- 标记哪些参数实质属于权限上下文
- 对后者改为从 header / 认证结果 / MCP 上下文注入
- 清理 DSL 层对这些安全参数的直接暴露

若不完成这一步，Compose Query 的编排能力会放大原先“请求参数即权限输入”的设计风险。

## ComposeQueryContext 与 AuthorityResolver SPI

### 1. ComposeQueryContext 是编排执行的唯一入口上下文

`ComposeQueryContext` 是 Compose Query 执行期唯一的入口对象，承载本次请求：

- `principal`：身份信息（`userId / tenantId / roles / deptId / authorizationHint? / policySnapshotId?`）
- `namespace`：命名空间，决定模型加载与 `X-NS` 传递
- `authorityResolver`：权限解析回调，类型为 `AuthorityResolver` 接口
- `traceContext`：`traceId / requestId / correlationId` 等链路上下文
- `params`：宿主注入的业务参数（只读），脚本中通过受控通道读取

脚本内任何 `QueryPlan` 都绑定到同一个 `ComposeQueryContext`，不允许在脚本运行期间替换或派生不同上下文。

### 2. AuthorityResolver SPI

`AuthorityResolver` 是宿主可实现的权限解析接口：

- 输入：`AuthorityRequest`（含 `principal / namespace / traceId / models[]`）
- 输出：`AuthorityResolution`（含 `bindings: { modelName -> ModelBinding }`）
- `ModelBinding` 固定为 `{ fieldAccess?, deniedColumns, systemSlice }`，允许 `fieldAccess` 为空

第一版要求宿主至少提供一种实现：

- 嵌入模式：宿主直接实现 `AuthorityResolver`（如 Odoo Pro 的 `OdooEmbeddedAuthorityResolver`）
- 远程模式：`HttpAuthorityResolver`（可延后，但签名第一版必须定好）

### 3. 脚本不可见 ComposeQueryContext

`ComposeQueryContext` 只在服务端存在，不对脚本暴露任何访问 API。脚本不能：

- 读取 `principal` / `authorityResolver` / `traceContext`
- 主动触发 `authorityResolver.resolve(...)`
- 构造或替换 `ComposeQueryContext`

脚本能观察到的只有"查询结果是否被权限收窄"这一行为后果，看不到权限决策本身。

## 白名单与隔离

为避免 JavaScript 宿主被当作任意脚本执行环境，本规范强制三层白名单治理。第一版必须落地，不能推迟。

### 1. Layer A：脚本宿主层（JS 顶层）

`script` 工具内运行的 JavaScript 宿主，只暴露以下能力：

**允许**

- 关系编排全局：`from / (Plan 实例上的 query / union / join / execute / toSql)`
- 纯语法：`const / let / if / for / while / return / ternary / 基础算术与比较`
- 受控内置：`Number / String / Boolean / Array`、`Math`、`JSON.parse / JSON.stringify`
- 宿主参数通道：`params.xxx`（只读）

**禁止**

- 动态执行：`eval / Function / new Function`
- 异步与时序：`setTimeout / setInterval / Promise / async / await`
- 网络与 IO：`fetch / XMLHttpRequest / WebSocket / File / fs`
- 全局入口：`globalThis / window / process / require / import`
- 反射：`Object.getPrototypeOf / Reflect.*` 等敏感反射族
- 隐式时间源：`Date.now() / new Date()` 必须换成受控 `now()` 注入（若第一版未实装，直接禁止所有 Date 访问）

**结果**：脚本顶层只能做关系编排，不能做普通程序运算以外的副作用。

### 2. Layer B：DSL 表达式层（FSScript 内部）

`columns` / `slice` / `orderBy` 等表达式字符串内部，沿用 v1.4 已落地的 `AllowedFunctions` 白名单，不新做：

- 聚合：`SUM / COUNT / AVG / MIN / MAX`
- 条件：`IIF / IF / CASE / COALESCE / IS_NULL / IS_NOT_NULL / BETWEEN`
- 时间：`DATE_DIFF / DATE_ADD / NOW`
- 数值与字符串：沿用现有白名单

**Compose 层附加约束**：`DerivedQueryPlan` 的 `columns` 表达式中，禁用任何可能逃脱 schema 推导的函数（例如未来可能引入的 `RAW_SQL(...)` 之类，必须在实现层显式标记"仅允许出现在 BaseModelPlan"）。

### 3. Layer C：Plan 级动词白名单

`QueryPlan` 对象只暴露以下方法，其他一律不开放：

- `plan.query(...)`
- `plan.union(other, opts)`
- `plan.join(other, opts)`
- `plan.execute()`
- `plan.toSql()`

显式禁止：`plan.raw() / plan.memoryFilter() / plan.toArray() / plan.forEach()` 等任何能让结果集被脚本遍历/逐行加工的方法。`execute()` 返回的 `DataSetResult` 同样只暴露最小取值面，不允许被二次编排。

### 4. 违规处理

任一层白名单被违反时，编排执行必须 fail-closed，错误统一归为 `compose-sandbox-violation` 错误码，不允许降级放行。

## union 规范

### 1. union 输入约束

- `union` 双侧必须来自同一数据源
- 双侧输出列数量必须一致
- 双侧输出列按位置对齐
- 输出列名以左侧为准，右侧按位置适配

### 2. union all 与 union distinct

- `all: true` 表示 `UNION ALL`
- `all: false` 或默认表示 `UNION`

### 3. union 后继续查询

`union` 的返回值仍是 `QueryPlan`，允许继续：

- `query({...})`
- `join(...)`
- 再次 `union(...)`

### 4. union 后字段可见性

union 后仅暴露 union 输出 schema，不允许访问某一侧独有但未对齐输出的字段。

## join 规范

### 1. join 输入约束

- 仅支持同数据源 `QueryPlan` 之间 join
- join 两侧都应先各自完成权限注入与 SQL 生成
- 组合层不关心各自内部 TM join 细节，仍遵循“QM / QueryPlan 即黑盒关系”

### 2. join 条件

join `on` 应基于左右两侧输出 schema 中可见字段定义，不允许直接引用底层未投影字段。

### 3. join 后字段命名

join 结果的输出字段若发生重名，必须在 join 后派生查询中显式别名消歧。规范不接受“自动覆盖后者”。

推荐形式：

```javascript
const joined = a.join(b, {
  type: 'left',
  on: [{ left: 'partnerId', op: '=', right: 'partnerId' }]
}).query({
  columns: [
    'a.partnerName AS salesPartnerName',
    'b.partnerName AS leadPartnerName',
    'a.totalSales',
    'b.leadCount'
  ]
});
```

### 4. join 后继续派生

join 返回的仍是 `QueryPlan`，可以继续：

- 选择列
- 聚合
- 再 join
- union

## SQL 编译策略

### 1. 逻辑计划与 SQL 形式解耦

编译器输入为 `QueryPlan` 树，输出为最终 SQL。`WITH`、子查询、别名命名都属于编译策略，不属于 DSL 语义。

### 2. 单次引用优先内联

当某一派生节点仅被父查询单次引用时，编译器可优先生成：

```sql
SELECT ...
FROM (
  ...
) t
```

### 3. 多次复用优先 CTE

当同一个 `QueryPlan` 节点被多个父分支复用时，编译器可优先生成：

```sql
WITH reused_plan AS (
  ...
)
SELECT ...
FROM reused_plan a
JOIN reused_plan b ON ...
```

### 4. 方言差异

不同数据库对 CTE 与派生子查询的优化可能不同，因此编译器需要：

- 基于 `FDialect` 判断是否支持 CTE
- 在不支持 CTE 的方言下自动回退为子查询
- 为未来保留 backend-specific compile policy，但不暴露为用户 DSL 语义

### 5. 语义优先于优化

无论编译成 `WITH` 还是 `FROM (...)`，都必须保证以下语义不变：

- 阶段边界不丢失
- `limit/start/distinct/groupBy` 的阶段作用域不被错误改写
- 派生查询只能访问上一步输出 schema

## 典型示例

### 1. 二段聚合

```javascript
const overdueByCustomer = from({
  model: 'ReceivableLineQM',
  columns: [
    'salespersonId',
    'salespersonName',
    'customer$id AS customerId',
    'SUM(IIF(isOverdue == 1, residualAmount, 0)) AS customerOverdueAmount'
  ],
  slice: [
    { field: 'docType', op: '=', value: 'AR' },
    { field: 'docState', op: '=', value: 'posted' },
    { field: 'paymentState', op: 'in', value: ['not_paid', 'partial', 'in_payment'] },
    { field: 'customerOverdueAmount', op: '>', value: 0 }
  ],
  groupBy: ['salespersonId', 'salespersonName', 'customerId']
});

return overdueByCustomer.query({
  columns: [
    'salespersonId',
    'salespersonName',
    'SUM(customerOverdueAmount) AS arOverdueAmount',
    'COUNT(*) AS arOverdueCustomerCount',
    'MAX(customerOverdueAmount) AS maxSingleCustomerOverdue'
  ],
  groupBy: ['salespersonId', 'salespersonName'],
  orderBy: ['-arOverdueAmount']
});
```

### 2. union 后再聚合

```javascript
const currentPlan = from({
  model: 'CurrentReceivableQM',
  columns: ['salespersonId', 'amount']
});

const archivedPlan = from({
  model: 'ArchivedReceivableQM',
  columns: ['salespersonId', 'amount']
});

return currentPlan.union(archivedPlan, { all: true }).query({
  columns: [
    'salespersonId',
    'SUM(amount) AS totalAmount'
  ],
  groupBy: ['salespersonId']
});
```

### 3. join 后再筛选

```javascript
const salesPlan = from({
  model: 'SaleOrderQM',
  columns: [
    'partner$id AS partnerId',
    'partner$caption AS partnerName',
    'SUM(amountTotal) AS totalSales'
  ],
  groupBy: ['partnerId', 'partnerName']
});

const leadPlan = from({
  model: 'CrmLeadQM',
  columns: [
    'partner$id AS partnerId',
    'COUNT(*) AS leadCount'
  ],
  groupBy: ['partnerId']
});

return salesPlan.join(leadPlan, {
  type: 'left',
  on: [{ left: 'partnerId', op: '=', right: 'partnerId' }]
}).query({
  columns: [
    'partnerName',
    'totalSales',
    'leadCount'
  ],
  slice: [
    { field: 'totalSales', op: '>', value: 10000 }
  ],
  orderBy: ['-totalSales']
});
```

## 与 ROADMAP 的关系

本规范是 `8.2.0 窗口函数 + CTE 支持` 中 CTE 能力的语义前置：

- ROADMAP 中的 `WITH` 支持，不应仅理解为“SQL 语法支持”
- 更核心的是为 Compose Query 引入“关系节点复用”的抽象闭环
- 后续窗口函数、递归 CTE 若要落地，也应依附于 `QueryPlan` 作为关系节点的统一编译模型

## 实现建议

### 1. 对象模型

- `from()` 返回 `QueryPlan`
- `execute()` 返回 `DataSetResult`
- `DataSetResult` 本期只负责取值，不承担派生查询语义
- `dsl()` 在实现层保留为 `from()` 的别名，仅为迁移过渡用

### 2. AST / 计划节点

建议最少包含以下节点类型：

- `BaseModelPlan`
- `DerivedQueryPlan`
- `UnionPlan`
- `JoinPlan`

### 3. 统一 schema 推导

每个 `QueryPlan` 节点都要能在执行前推导输出 schema，用于：

- 字段引用校验
- join / union 兼容性校验
- SQL 别名生成

### 4. 显式错误

以下情况应在编译前抛出结构化错误，而不是静默容忍：

- 派生查询引用了未投影字段
- union 两侧列数不一致
- union 两侧列类型明显不可兼容
- join `on` 引用了不存在字段
- join 后输出列名冲突但未显式消歧

## 验收标准

- `from()` / `query()` / `union()` / `join()` 的关系语义有统一规范文档
- 二段聚合、union 后再聚合、join 后再筛选都有可执行的规范示例
- 文档明确声明本期不开放内存二次加工
- 文档明确声明 `model` 仅表示物理 QM，派生查询统一走 `source/query`
- 文档明确声明 SQL 编译时 `WITH` 与 `FROM (...)` 是后端策略，不是 DSL 语义
- `ComposeQueryContext` 对象模型与 `AuthorityResolver` SPI 接口在本期内落地并可被 foggy-odoo-bridge-pro 嵌入式注入
- 三层白名单（A 宿主 / B FSScript / C Plan 动词）有明文章节并有对应防护测试
- `P0-ComposeQuery-沙箱白名单错误码与防护用例清单.md` 中定义的全部错误码在 Java / Python 两仓同名落地，所有用例 ID（A-01 ~ A-10 / B-01 ~ B-07 / C-01 ~ C-07）测试通过
- ✅ 前置依赖 F-3 已于 2026-04-21 accepted（见本文档 §前置依赖），M5 BaseModelPlan 集成测试 + M8 `OdooEmbeddedAuthorityResolver` 多模型验收 unblock

## 待后续细化

- 递归 CTE DSL 设计
- 窗口函数与 `QueryPlan` 阶段边界的交互
- `toSql()` 的稳定契约与调试输出格式
- 方言级 compile policy 的开关与 explain 输出
