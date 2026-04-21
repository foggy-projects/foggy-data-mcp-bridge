# P0-ComposeQuery QueryPlan 派生查询与关系复用规范-实现规划

## 文档作用

- doc_type: implementation-plan
- intended_for: execution-agent / reviewer
- purpose: 将 8.2.0.beta 已确认的 Compose Query QueryPlan 方案拆成可执行实现规划，覆盖 API、权限协议、schema 规则、SQL 编译边界、MCP 工具契约与错误模型

## 目标范围

本规划仅覆盖 8.2.0.beta 已确认范围：

- `from`（顶层入口；`dsl` 保留为实现层过渡别名）
- `query`
- `union`
- `join`
- `execute`
- `toSql`
- `ComposeQueryContext` 对象模型
- `AuthorityResolver` SPI 接口（嵌入模式第一版必须，远程 HTTP 模式可延后但签名本期冻结）
- 三层白名单（A 宿主 / B FSScript / C Plan 动词）的防护实现

本期不进入范围：

- 时间比较语义
- 窗口函数进入 `QueryPlan`
- `exists / semiJoin / antiJoin`
- 递归 CTE
- 内存加工

## 前置依赖（blocking）

### ✅ BUG F-3：`_resolve_effective_visible` 跨模型 denied 泄漏 · **已解除**（2026-04-21）

- 详细说明参见 `需求.md §前置依赖` · 签收记录 `foggy-data-mcp-bridge-python/docs/v1.6/acceptance/REQ-P0-BUG-F3-acceptance.md` · decision **`accepted`**
- 对实施计划的解锁：
  - M1-M4 / M6-M7 / M9 原本就不受 F-3 影响
  - **M5 BaseModelPlan 集成测试** — 多模型字段独立可见性可覆盖（F-3 已修）
  - **M8 Odoo Pro `OdooEmbeddedAuthorityResolver` 多模型验收** — unblock（vendored sync 完成 · xfail 已撤 · fast lane 570 passed）
  - `script` 工具多模型场景可对外宣布"对 Odoo Pro 可用"

## 实施原则

### 1. QueryPlan 先成形，再继续关系编排

`from()` 返回 `QueryPlan`，而不是立即物化结果集。后续 `query / union / join` 都构造新的 `QueryPlan` 节点。`dsl()` 继续保留，仅作为实现层别名指向同一底层函数，便于过渡期兼容。

### 2. BaseModelPlan 在首次参与编排前必须完成权限绑定

在 JavaScript 编排场景中，一旦出现 `from({ model: 'XxxQM', ... })`，且该节点需要继续参与：

- schema 推导
- `query`
- `union`
- `join`
- `toSql`
- `execute`

则系统必须先为该 `BaseModelPlan` 解析并绑定：

- `fieldAccess`
- `deniedColumns`
- `systemSlice`

没有完成绑定前，不允许把该模型的完整 schema 暴露给后续编排。

### 3. 权限落在上游，Foggy 负责协议执行与绑定

8.2.0.beta 的编排权限设计按“权限落在上游”实现：

- 上游通过 `ComposeQueryContext` 注入 `principal` 与 `authorityResolver`
- Foggy 在 `BaseModelPlan` 首次参与编排前，通过 `authorityResolver.resolve(AuthorityRequest)` 批量请求权限
- 上游返回模型级权限 binding（`Map<modelName, ModelBinding>`）
- Foggy 将 binding 注入对应 `BaseModelPlan`

嵌入模式由宿主直接 new 一个 `AuthorityResolver` 实例放进 `ComposeQueryContext`；远程模式由 Foggy 内置的 `HttpAuthorityResolver` 按配置的 URL 发 HTTP 请求。两种模式签名一致。

### 4. 单 DSL 工具保持不变

**存量单 DSL 查询入口完全不改协议、不改行为、不改测试基线。** 8.2.0.beta 严格按新增路径交付：独立的 `script` 工具入口 + `ComposeQueryContext` + `AuthorityResolver` SPI。现有 `query_model` 工具中关于 DSL 使用的文本描述，可被 `script` 工具以文档复用形式引用，但不做结构性调整。

## QueryPlan API 规划

### 1. 最小 API 面

8.2.0.beta 仅定义以下 API：

- `from({ ... }) -> QueryPlan`（顶层入口；`dsl(...)` 作为实现层别名保留）
- `plan.query({ ... }) -> QueryPlan`
- `plan.union(otherPlan, options) -> QueryPlan`
- `plan.join(otherPlan, options) -> QueryPlan`
- `plan.execute() -> DataSetResult`
- `plan.toSql() -> SqlPreview`

其中 `from({ model: 'X', ... })` 构造 `BaseModelPlan`；`from({ source: plan, ... })` 是 `plan.query(...)` 的规范内核等价写法。`model` 与 `source` 互斥。

不在本期开放：

- `withJoin()`
- `memoryQuery()`
- `compute() / filter() / sort()` 的结果集二次加工能力

### 2. 行为约束

- `query` 只接受上一步输出 schema 中的字段
- `union` 结果仍是 `QueryPlan`
- `join` 结果仍是 `QueryPlan`
- `toSql()` 仅用于调试/排障/Explain，不作为稳定跨系统协议

## ComposeQueryContext 与 AuthorityResolver SPI

### 1. ComposeQueryContext

```java
public final class ComposeQueryContext {
    private final Principal principal;
    private final String namespace;
    private final AuthorityResolver authorityResolver;
    private final TraceContext trace;
    private final Map<String, Object> params;   // 只读业务参数通道

    // 脚本不可见：getter 只对服务端可见
}

public final class Principal {
    private final String userId;
    private final String tenantId;
    private final List<String> roles;
    private final String deptId;
    private final String authorizationHint;     // 可空；远程模式下用于拼 Authorization header
    private final String policySnapshotId;      // 可空；仅审计/追踪
}
```

Python 对等（`ComposeQueryContext` 同名 dataclass，`authority_resolver: AuthorityResolver` Protocol）。

### 2. AuthorityResolver SPI

```java
public interface AuthorityResolver {
    AuthorityResolution resolve(AuthorityRequest request);
}

public final class AuthorityRequest {
    private final Principal principal;
    private final String namespace;
    private final String traceId;
    private final List<ModelQuery> models;       // 固定批量形态，哪怕只有 1 个
}

public final class ModelQuery {
    private final String model;                  // QM 名
    private final List<String> tables;           // 从 JoinGraph 派生
}

public final class AuthorityResolution {
    private final Map<String, ModelBinding> bindings;  // key = QM 名
}

public final class ModelBinding {
    private final List<String> fieldAccess;            // 允许为空，复用 deniedColumns 主路径
    private final List<DeniedPhysicalColumn> deniedColumns;
    private final List<SystemSliceCondition> systemSlice;
}
```

Python 对等 Protocol：

```python
class AuthorityResolver(Protocol):
    def resolve(self, request: AuthorityRequest) -> AuthorityResolution: ...
```

### 3. 宿主注入形态

- 嵌入模式（Odoo Pro）：
  - 宿主侧实现 `OdooEmbeddedAuthorityResolver(env)`
  - 内部对每个 `ModelQuery` 调用现有 `compute_query_governance_with_result(env, user.id, model_name)`
  - 第一版不要求真正并行，循环实现即可
  - 宿主在构造 `ComposeQueryContext` 时把它放进去
- 远程模式（本期延后，但签名本期冻结）：
  - Foggy 内置 `HttpAuthorityResolver`
  - 按 `principal` 拼 `Authorization / X-User-Id / X-Tenant-Id / X-Roles / X-Dept-Id / X-Policy-Snapshot-Id / X-Trace-Id`
  - POST JSON 到配置的上游权限服务 URL

### 4. 入口工具输入协议

`script` 工具 body 仅保留：

- `script`：JavaScript 文本

身份、namespace、trace 等从 `ToolExecutionContext` / HTTP header 取出，在 MCP 层构造 `ComposeQueryContext`，不进脚本参数面。

远程模式下的 header 协议示意：

- `Authorization`
- `X-Namespace`
- `X-User-Id`
- `X-Tenant-Id`
- `X-Roles`
- `X-Dept-Id`
- `X-Policy-Snapshot-Id`
- `X-Trace-Id`

嵌入模式下宿主直接填 `ComposeQueryContext.principal`，不要求经过 header 序列化。

### 5. 上游回调时机

每个 `BaseModelPlan` 在首次参与以下操作前，必须完成权限解析：

- schema 推导
- `query`
- `union`
- `join`
- `toSql`
- `execute`

系统可以把同一批 pending 的 `BaseModelPlan` 合并成一次 `authorityResolver.resolve(...)` 调用。

### 6. 请求 / 响应协议

请求固定批量形态（单模型也以长度为 1 的 `models` 下发）：

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
  "traceId": "trace-123",
  "models": [
    {
      "model": "SaleOrderQM",
      "tables": ["sale_order", "sale_order_line", "res_partner"]
    }
  ]
}
```

响应按模型绑定返回（key 必须匹配请求的 `model` 名）：

```json
{
  "bindings": {
    "SaleOrderQM": {
      "fieldAccess": null,
      "deniedColumns": [
        { "table": "sale_order", "column": "internal_cost" }
      ],
      "systemSlice": [
        { "field": "orgId", "op": "=", "value": "org001" }
      ]
    }
  }
}
```

不接受：

- 缺少 `bindings` 或某个 `model` 无对应 binding
- 返回结构无法映射到请求模型
- 返回全局规则而不分模型

### 7. 缓存与失败策略

- 第一版不强制 Foggy 侧做跨请求缓存；是否启用缓存、TTL 如何、是否失效由宿主 resolver 自决
- 同一次脚本执行内可做"请求内去重"，避免同一 `(namespace + principal + model)` 在同一脚本内多次回调
- 任一模型权限解析失败、返回不完整、或 binding 无法绑定到对应 BaseModelPlan 时，整个脚本执行 `fail-closed`
- 不允许"部分模型已鉴权、部分模型未鉴权"继续执行

## 白名单与隔离实现规划

对应需求文档 §白名单与隔离（Layer A/B/C），实现期需要落地：

- Layer A 宿主白名单：在 JS 引擎注册 `ComposeQueryContext` 时，显式剥离全局，仅注入 `from / Number / String / Boolean / Array / Math / JSON / params` 等受控成员
- Layer A 禁用项：对 `eval / Function / setTimeout / fetch / process / require / Date / globalThis / window / Reflect / Object.getPrototypeOf` 做 deny list 校验，出现即抛 `compose-sandbox-violation`
- Layer B FSScript：沿用 v1.4 `AllowedFunctions`，Compose 期仅新增一项约束——`DerivedQueryPlan` 的 `columns` 表达式禁用可能逃脱 schema 推导的函数
- Layer C Plan 动词：`QueryPlan` 类型只 export `query / union / join / execute / toSql` 五个方法，其它内部字段/方法通过封装隐藏

必须附带专门的防护测试集（compose-sandbox-violation-tests），每层至少 5 条用例

## 模型与表收集规则

### 1. 模型收集

模型收集范围仅限 `BaseModelPlan` 叶子节点。

- `DerivedQueryPlan` 不新增模型
- `UnionPlan` 汇总左右两侧叶子模型
- `JoinPlan` 汇总左右两侧叶子模型

### 2. 表收集

每个 `BaseModelPlan` 的物理表集合从对应 `QueryModel` 的 `JoinGraph` 提取。

抽取规则建议：

- 至少包含 root 主表
- 包含 `JoinGraph` 中所有实际存在的 `QueryObject` 对应表
- 去重后按稳定顺序输出
- 默认返回物理表名；如当前方言/实现已有 schema 信息，可扩展为 `schema.table`

8.2.0.beta 不强制把别名回传给上游权限服务。

### 3. 收集时机

表收集发生在基础模型首次权限回调前，不等最终 SQL 生成。

## Schema 与别名规则

### 1. BaseModelPlan 输出 schema

基础模型的可见 schema 受以下因素共同决定：

- 用户请求的 `columns`
- 别名定义
- 聚合和 `groupBy`
- 权限 binding，尤其是 `fieldAccess` 与 `deniedColumns`

### 2. DerivedQueryPlan 规则

- 仅可见上一步输出 schema
- 显式别名优先
- 未投影字段不可见
- 不回到底层重新做字段导航

### 3. JoinPlan 规则

- `join on` 仅允许引用左右两侧可见 schema
- `join` 结果若发生重名，后续 `query` 必须显式别名消歧
- 8.2.0.beta 不接受“自动覆盖同名字段”

### 4. UnionPlan 规则

- 双侧列数必须一致
- 按位置对齐
- 输出列名以左侧为准
- 双侧类型明显不兼容时直接报错

### 5. deniedColumns 对 schema 的影响

`deniedColumns` 会直接影响基础模型可见 schema。

因此：

- 相关列不能继续参与 `query / union / join`
- 权限绑定前不能暴露“未裁剪 schema”
- 这是要求 BaseModelPlan 提前做权限绑定的根本原因

## SQL 编译边界

### 1. 本期必须支持的计划组合

8.2.0.beta 必须支持：

- `BaseModelPlan -> execute`
- `BaseModelPlan -> query -> execute`
- `BaseModelPlan + BaseModelPlan -> union -> query -> execute`
- `BaseModelPlan + BaseModelPlan -> join -> query -> execute`
- 多段 `query` 链式派生

### 2. 编译策略

- 单次引用优先内联子查询
- 多次复用优先 CTE
- 方言不支持 CTE 时自动回退为子查询

### 3. pushdown 边界

本期仅支持能够完整编译为同数据源 SQL 的 `QueryPlan`。

遇到以下情况直接报不支持：

- 跨数据源 `union / join`
- 需要内存加工才能继续的编排
- 需要窗口函数进入 `QueryPlan` 的表达
- 需要 `exists / recursive / lateral` 等未纳入本期的语义

### 4. `toSql()` 边界

`toSql()` 输出调试用 SQL 文本和参数预览，约束如下：

- 仅对已完成权限绑定的 `QueryPlan` 可用
- 不承诺是长期稳定公共协议
- 主要用于排障、Explain、开发调试

## MCP / HTTP 工具契约

### 1. 保持原单 DSL 工具不变

现有单 DSL 工具继续保留，行为不变。

### 2. 新增 script 工具入口

新增 Compose Query `script` 工具：

- body 参数仅接收脚本文本
- 其他上下文由程序固定通过 header / `ToolExecutionContext` 注入
- 服务端在 MCP 层把 `ToolExecutionContext` 转成 `ComposeQueryContext`，再把宿主提供的 `AuthorityResolver` 绑进去

建议最小参数形态：

```json
{
  "script": "const a = dsl({...}); return a.execute();"
}
```

### 3. AI 友好性要求

- AI 不负责构造安全参数
- AI 只负责生成 JavaScript 查询结构
- 工具层对 header / 上下文做固定注入和校验

## 错误模型规划

### 1. 设计原则

- 错误应结构化
- 优先在 schema 推导或编译前失败，而不是执行后才暴露
- 权限错误和 DSL 结构错误要区分

### 2. 错误分类

建议至少区分：

- 权限协议错误
- 权限解析失败
- 未授权字段访问
- 派生查询引用未投影字段
- `union` 列数不匹配
- `union` 类型不兼容
- `join on` 字段不存在
- `join` 输出列冲突未消歧
- 跨数据源不支持
- 本期未支持语义
- `toSql()` / `execute()` 前权限未绑定

### 3. 失败口径

- 权限接口失败：直接失败
- 返回结果不完整：直接失败
- schema 校验失败：直接失败
- SQL 编译遇到本期未支持语义：直接失败

### 4. 报错要求

报错至少应包含：

- 错误码
- 用户可读消息
- 当前阶段（permission-resolve / schema-derive / compile / execute）
- 涉及的模型名或计划节点

## 测试规划

### 1. 模型层

- `QueryPlan` 节点构造与 schema 推导测试
- `union / join / query` 组合语义测试
- 权限 binding 对 schema 裁剪的测试

### 2. 权限协议层

- 上游权限回调 mock 测试
- 单模型首次使用即触发权限解析测试
- 缓存命中测试
- `fail-closed` 测试

### 3. SQL 编译层

- 单次引用内联子查询测试
- 多次复用 CTE 测试
- 方言回退测试

### 4. MCP 工具层

- 新 `script` 工具参数解析测试
- header / `ToolExecutionContext` 传递测试
- 与权限协议联动测试

## 交付顺序建议

1. `ComposeQueryContext` / `Principal` / `AuthorityResolver` SPI / `AuthorityRequest` / `AuthorityResolution` / `ModelBinding` 对象与接口（先把协议签名冻住）
2. `QueryPlan` 对象模型与最小 API（`from / query / union / join / execute / toSql`）
3. foggy-fsscript `ComposeQueryDialect`（`isKeywordAsIdentifier(FROM, '(')`），以及 Layer A 宿主沙箱
4. schema 推导与别名/冲突校验
5. BaseModelPlan 首次使用 hook + `authorityResolver` 调用链路（含批量合并）
6. SQL 编译器支持 `query / union / join`
7. MCP 新 `script` 工具入口
8. Odoo Pro 侧 `OdooEmbeddedAuthorityResolver` 接入示范与集成测试
9. 白名单防护测试集（Layer A/B/C）
10. 集成测试与文档回写
