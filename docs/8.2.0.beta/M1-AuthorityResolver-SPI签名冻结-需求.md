---
type: signature-freeze-requirement
version: 8.2.0.beta
milestone: M1
req_id: M1-AuthorityResolver-SPI
status: frozen
priority: P0
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
cross_repo:
  - foggy-data-mcp-bridge (Java) — frozen 2026-04-21 · mvn test green
  - foggy-data-mcp-bridge-python (Python) — frozen 2026-04-21 · pytest green
  - foggy-odoo-bridge-pro (Python, downstream consumer) — can start M3 after vendored sync
python_landed_at: 2026-04-21
python_test_baseline: 2491 passed / 1 skipped (v1.6 F-3 2430 + M1 61)
python_packages:
  - foggy.dataset_model.engine.compose.context
  - foggy.dataset_model.engine.compose.security
java_landed_at: 2026-04-21
java_test_baseline: foggy-dataset-model sqlite lane 1134 passed / 0 failures (M1 tests 49 / 49 across sqlite + mysql + postgres)
java_packages:
  - com.foggyframework.dataset.db.model.engine.compose.context
  - com.foggyframework.dataset.db.model.engine.compose.security
---

# M1 · AuthorityResolver SPI 签名冻结

## 文档作用

- doc_type: signature-freeze-requirement
- intended_for: execution-agent / cross-repo owner
- purpose: 在 8.2.0.beta M1 阶段把 `ComposeQueryContext` 与 `AuthorityResolver` 相关的对象模型、接口签名、错误契约一次性冻结，让 M2-M10 可以并行开工而不互相阻塞，并让下游 `foggy-odoo-bridge-pro` v1.6 REQ-001 能在不等待 Foggy 实现的情况下先实现 `OdooEmbeddedAuthorityResolver`

## 冻结边界

### 纳入本次冻结

- `ComposeQueryContext` 对象
- `Principal` 对象
- `AuthorityResolver` SPI 接口
- `AuthorityRequest` / `ModelQuery` 请求模型
- `AuthorityResolution` / `ModelBinding` / `DeniedColumn` / `SystemSliceCondition` 响应模型
- `AuthorityResolutionError` 错误类型与 error code 枚举
- 命名空间、trace 字段的约定
- Java / Python 两仓对等签名

### 不在本次冻结范围

- `QueryPlan` / `BaseModelPlan` / `DerivedQueryPlan` / `UnionPlan` / `JoinPlan` 内部实现（M2 阶段）
- `HttpAuthorityResolver` 的 HTTP 请求头/URL 约定（保留签名、实现延后）
- sandbox 违规错误码抛出点（M9 阶段）
- SQL 编译策略（M6 阶段）

## 冻结原则

1. **Java interface 与 Python Protocol 形态完全对等** —— 字段名、方法名、可空性、错误语义在两端一致
2. **每个字段命名必须与 root CLAUDE.md v1.3 已有约定对齐** —— `deniedColumns / systemSlice / fieldAccess` 沿用现有拼写，不改写为新命名
3. **字段可空性在冻结时明示**，不留模糊；`Optional<List<T>> = null` 的语义与 `List<T> = []` 的语义分开描述
4. **接口内不携带行为** —— SPI 只定义"输入/输出/错误"三面，不规定实现内部调用谁
5. **冻结后修改需升级版本签名** —— 8.2.0.beta 内不再接受字段增删；字段扩展走 8.3.0 或以 `extensions: Map<String, Any>` 承载

## Java 对象与接口定义

### 1. `Principal`

package: `com.foggyframework.dataset.db.model.engine.compose.context`

```java
public final class Principal {
    private final String userId;                 // required, non-null
    private final String tenantId;               // nullable
    private final List<String> roles;            // required, may be empty but not null
    private final String deptId;                 // nullable
    private final String authorizationHint;      // nullable; 仅远程模式序列化为 Authorization header
    private final String policySnapshotId;       // nullable; 仅审计/追踪

    // 全 arg 构造器 + 链式 Builder；无 setter
    public static Builder builder() { ... }

    public String userId() { ... }
    public String tenantId() { ... }
    public List<String> roles() { ... }        // 不可变副本
    public String deptId() { ... }
    public String authorizationHint() { ... }
    public String policySnapshotId() { ... }
}
```

**不变量**：`userId` 非空；`roles` 非 null（空列表合法）；其他字段可为 null。

### 2. `ComposeQueryContext`

package: `com.foggyframework.dataset.db.model.engine.compose.context`

```java
public final class ComposeQueryContext {
    private final Principal principal;                   // required
    private final String namespace;                      // required, non-blank
    private final AuthorityResolver authorityResolver;   // required, non-null
    private final TraceContext trace;                    // required, non-null（允许空 traceId）
    private final Map<String, Object> params;            // required, unmodifiable; 可为空 map
    // 可选扩展（本期不暴露给脚本，只作为服务端链路透传）
    private final Map<String, String> extensions;        // nullable

    public static Builder builder() { ... }

    // getter 同上；对 params / extensions 返回 unmodifiable 视图
}
```

**脚本可见性**：ComposeQueryContext 本体绝不对脚本暴露；脚本只能通过受控入口 `params.xxx` 读取 `params` map 中的业务参数。

### 3. `AuthorityResolver` SPI

package: `com.foggyframework.dataset.db.model.engine.compose.security`

```java
public interface AuthorityResolver {
    /**
     * Resolve per-model authority bindings for a batch of models.
     *
     * Contract:
     *  - 即使 request.models().size() == 1，实现也必须按 batch 协议处理
     *  - 返回的 bindings map 的 key 必须与 request.models().stream().map(ModelQuery::model) 一一对应
     *  - 不允许返回 null；全部模型失败时必须抛 AuthorityResolutionException
     *  - 实现必须是 fail-closed：任一模型解析失败即整体失败
     *
     * @throws AuthorityResolutionException 当任一模型无法解析或结果不合法
     */
    AuthorityResolution resolve(AuthorityRequest request) throws AuthorityResolutionException;
}
```

### 4. `AuthorityRequest` / `ModelQuery`

```java
public final class AuthorityRequest {
    private final Principal principal;           // required
    private final String namespace;              // required, non-blank
    private final String traceId;                // nullable
    private final List<ModelQuery> models;       // required, non-empty, unmodifiable
    private final Map<String, String> extensions; // nullable

    public static Builder builder() { ... }
}

public final class ModelQuery {
    private final String model;                  // required, non-blank; QM model name
    private final List<String> tables;           // required, non-null, unmodifiable
                                                 // 物理表集合，从 JoinGraph 派生；至少包含 root 主表

    public static Builder builder() { ... }
}
```

**不变量**：`models.size() >= 1`；`model` 非空；`tables` 非 null（空列表合法但实现层应避免产生空表集合）。

### 5. `AuthorityResolution` / `ModelBinding`

```java
public final class AuthorityResolution {
    private final Map<String, ModelBinding> bindings;   // required, unmodifiable, key = QM model name
    private final Map<String, String> extensions;       // nullable

    public static Builder builder() { ... }
}

public final class ModelBinding {
    private final List<String> fieldAccess;             // nullable; 第一版 Odoo Pro 返回 null
    private final List<DeniedColumn> deniedColumns;     // required, unmodifiable, 可为空列表
    private final List<SystemSliceCondition> systemSlice; // required, unmodifiable, 可为空列表

    public static Builder builder() { ... }
}
```

**fieldAccess 为 null 的语义**：表示该模型未启用 QM 字段白名单，走 `deniedColumns` 主路径。空列表 `[]` 与 `null` 语义不同：空列表意味着"没有任何字段对该 principal 可见"。

### 6. `DeniedColumn` / `SystemSliceCondition`

**复用 v1.3 已有类型**，不新建：

```java
// 已有：com.foggyframework.dataset.db.model.semantic.domain.DeniedColumn
public final class DeniedColumn {
    private final String schema;     // nullable, null 匹配任意 schema
    private final String table;      // required
    private final String column;     // required
}

// 已有：v1.3 已定义的 SystemSliceCondition 或对齐类型
public final class SystemSliceCondition {
    private final String field;      // QM 字段名
    private final String op;         // =, !=, in, not_in, >, <, >=, <=, is_null, is_not_null
    private final Object value;      // 类型按 op 决定
}
```

如 v1.3 的 `SystemSliceCondition` 定义位于非 `engine.compose` 包，本期通过 re-export / type alias 方式让两处使用同一语义，**不做重复定义**。

### 7. `AuthorityResolutionException`

```java
public class AuthorityResolutionException extends RuntimeException {
    private final String code;                  // 来自下方错误码枚举
    private final String modelInvolved;         // nullable; 失败模型名
    private final String phase;                 // "authority-resolve" 等阶段枚举

    public AuthorityResolutionException(String code, String message, String modelInvolved, String phase, Throwable cause) { ... }

    public String code() { ... }
    public String modelInvolved() { ... }
    public String phase() { ... }
}
```

错误码命名空间：`compose-authority-resolve/<kind>`

| code | 触发情形 |
|---|---|
| `compose-authority-resolve/resolver-not-available` | `ComposeQueryContext.authorityResolver` 为 null |
| `compose-authority-resolve/model-binding-missing` | 响应 `bindings` 缺少某个 request.models 中的模型 |
| `compose-authority-resolve/model-not-mapped` | 模型在 resolver 内部无法映射（如 Odoo Pro 的 `QM_TO_ODOO_MODEL` 未命中） |
| `compose-authority-resolve/principal-mismatch` | 嵌入模式下 principal 与宿主 session 不一致 |
| `compose-authority-resolve/upstream-failure` | 远程 HTTP 调用失败或返回 5xx |
| `compose-authority-resolve/invalid-response` | 响应无法反序列化或字段缺失 |
| `compose-authority-resolve/ir-rule-unmapped-field` | Odoo `ir.rule.domain_force` 字段无法映射为 QM 字段 |

**错误消息必须 sanitize**：不回显物理列名 / 原始 `ir.rule.domain_force` 文本 / 其他用户身份。

## Python 对等定义

package: `foggy.dataset_model.engine.compose.context` / `foggy.dataset_model.engine.compose.security`

### 1. Dataclass 形态

```python
from dataclasses import dataclass, field
from typing import Protocol, Optional, List, Dict, Any

@dataclass(frozen=True)
class Principal:
    user_id: str
    tenant_id: Optional[str] = None
    roles: List[str] = field(default_factory=list)
    dept_id: Optional[str] = None
    authorization_hint: Optional[str] = None
    policy_snapshot_id: Optional[str] = None

@dataclass(frozen=True)
class ComposeQueryContext:
    principal: Principal
    namespace: str
    authority_resolver: "AuthorityResolver"
    trace: "TraceContext"
    params: Dict[str, Any] = field(default_factory=dict)
    extensions: Optional[Dict[str, str]] = None

@dataclass(frozen=True)
class ModelQuery:
    model: str
    tables: List[str] = field(default_factory=list)

@dataclass(frozen=True)
class AuthorityRequest:
    principal: Principal
    namespace: str
    trace_id: Optional[str]
    models: List[ModelQuery]
    extensions: Optional[Dict[str, str]] = None

@dataclass(frozen=True)
class ModelBinding:
    field_access: Optional[List[str]]          # None ≠ []
    denied_columns: List["DeniedColumn"] = field(default_factory=list)
    system_slice: List["SystemSliceCondition"] = field(default_factory=list)

@dataclass(frozen=True)
class AuthorityResolution:
    bindings: Dict[str, ModelBinding]
    extensions: Optional[Dict[str, str]] = None
```

### 2. SPI Protocol

```python
class AuthorityResolver(Protocol):
    def resolve(self, request: AuthorityRequest) -> AuthorityResolution: ...
```

### 3. 异常

```python
class AuthorityResolutionError(Exception):
    def __init__(self, code: str, message: str,
                 model_involved: Optional[str] = None,
                 phase: str = "authority-resolve",
                 cause: Optional[BaseException] = None):
        super().__init__(message)
        self.code = code
        self.model_involved = model_involved
        self.phase = phase
        self.__cause__ = cause
```

错误码与 Java 同名（字符串完全一致，跨语言统一 `compose-authority-resolve/<kind>`）。

### 4. `DeniedColumn` / `SystemSliceCondition`

复用 v1.3 已有定义（`foggy.dataset_model.semantic.domain.DeniedColumn` 等），不新建。

## 命名约定冻结表

| 概念 | Java 命名 | Python 命名 | 说明 |
|------|----------|------------|------|
| Context 对象 | `ComposeQueryContext` | `ComposeQueryContext` | 同名 |
| 身份 | `Principal` | `Principal` | 同名 |
| 用户 ID | `userId` | `user_id` | Java camelCase / Python snake_case |
| 角色 | `roles: List<String>` | `roles: List[str]` | 同语义 |
| 部门 | `deptId` | `dept_id` | |
| 策略快照 | `policySnapshotId` | `policy_snapshot_id` | 本期可 null/None |
| Resolver 接口 | `AuthorityResolver` | `AuthorityResolver` | Java interface / Python Protocol |
| 请求 | `AuthorityRequest` | `AuthorityRequest` | |
| 响应 | `AuthorityResolution` | `AuthorityResolution` | |
| 模型级 binding | `ModelBinding` | `ModelBinding` | |
| 字段白名单 | `fieldAccess: List<String> \| null` | `field_access: Optional[List[str]]` | None 意味着不启用白名单 |
| 列黑名单 | `deniedColumns: List<DeniedColumn>` | `denied_columns: List[DeniedColumn]` | 复用 v1.3 |
| 系统行条件 | `systemSlice: List<SystemSliceCondition>` | `system_slice: List[SystemSliceCondition]` | 复用 v1.3 |
| 错误类 | `AuthorityResolutionException` | `AuthorityResolutionError` | Java/Python 惯例差异 |
| 错误码命名空间 | `compose-authority-resolve/<kind>` | 同 | 字符串完全一致 |

## 实现顺序

1. Java 端先落地所有 interface / class / Builder / 异常；补对应单元测试（Builder 行为 / 不变量校验 / toString）
2. Python 端同 PR 落地 dataclass / Protocol / 异常；补对应单元测试
3. 提供 `ComposeQueryContextTest` / `AuthorityResolverContractTest` 两组测试，做"契约合规 mock"
4. 发布 M1 冻结声明（更新本仓 8.2.0.beta progress.md 的 M1 状态为 `ready-for-review`）

## 合规测试要求

### 两仓同名测试

| 测试 | Java 路径 | Python 路径 | 作用 |
|------|----------|-------------|------|
| Principal 不变量 | `PrincipalTest.java` | `test_principal.py` | userId 非空 / roles 非 null 等 |
| ComposeQueryContext 构造 | `ComposeQueryContextTest.java` | `test_compose_query_context.py` | 必填字段缺失时抛出 |
| AuthorityRequest 批量契约 | `AuthorityRequestTest.java` | `test_authority_request.py` | models 非空、model 非空 |
| AuthorityResolution key 对齐 | `AuthorityResolutionTest.java` | `test_authority_resolution.py` | bindings key 必须与 request 模型一一对应 |
| Resolver Mock 契约 | `AuthorityResolverContractTest.java` | `test_authority_resolver_contract.py` | 使用 fake resolver 验证 fail-closed 语义 |
| 错误码枚举覆盖 | `AuthorityResolutionErrorCodeTest.java` | `test_authority_resolution_error_code.py` | 7 个错误码字符串同名存在 |

### 跨语言字符串一致性断言

在 Java `AuthorityResolutionErrorCodeTest` 与 Python `test_authority_resolution_error_code.py` 中各自列出 7 个错误码字符串常量；CI 阶段可加一步 parity 检查（对比两仓导出的字符串列表 JSON），本期至少要求人工 review。

## 验收标准

- Java 侧 7 个类 + 1 个 interface + 1 个异常全部落地，`mvn test -pl foggy-dataset-model` 不回归
- Python 侧 7 个 dataclass + 1 个 Protocol + 1 个 Exception 全部落地，`pytest -q` 不回归
- 上述 6 组合规测试在两仓同名同结构落地并通过
- 本文档 status 变为 `frozen`，且被 `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-progress.md` 引用为 M1 签收证据
- 下游 `foggy-odoo-bridge-pro/docs/prompts/v1.6/P0-01-progress.md` 的 M2 前置依赖清单里"上游 `AuthorityResolver` Protocol 交付"转为 `ready`

## 对下游的解锁作用

M1 冻结完成后，以下并行工作可立即启动：

- Odoo Pro v1.6 REQ-001 M3 `OdooEmbeddedAuthorityResolver` 实现（不再等 Foggy 实现 M2-M7）
- 本仓 M2-M4 / M6 / M7 / M9 所有依赖 SPI 签名稳定的工作
- Java / Python 两仓的 `AuthorityResolverContractTest` 类 mock 即刻可用

## 冻结后变更流程

- 8.2.0.beta 生命周期内**禁止**对本文档列出的字段做增删、类型变更、可空性变更
- 错误码可新增，但不可重命名或删除
- 如必须变更，必须升级到 8.3.0 并留下 `DEPRECATED-*` 字段转译器

## 风险

- R1 Java / Python 两仓签名漂移 → 6 组合规测试 + 错误码字符串 parity 检查守护
- R2 与 v1.3 `DeniedColumn` / `SystemSliceCondition` 语义分歧 → 明确复用 v1.3 既有类型，不引入 compose 专用重复定义
- R3 下游 Odoo Pro 尚未准备好 import 路径 → 发布 M1 前先检查 Odoo Pro vendored lib 是否已同步
