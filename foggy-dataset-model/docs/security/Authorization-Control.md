# 数据权限控制指南 (DataSetResultStep)

本文档介绍如何使用 `DataSetResultStep` 实现数据权限控制，包括查询前置条件注入、结果集脱敏等场景。

> **另一种方式**：如需在 SQL 生成阶段进行行级数据过滤，请参阅 [QueryModel-Accesses-Control.md](./QueryModel-Accesses-Control.md)。

## 概述

Foggy Framework 提供两种数据权限控制方式：

| 方式 | 执行时机 | 适用场景 | 配置位置 |
|------|---------|---------|---------|
| **accesses** | SQL 生成阶段 | 行级数据隔离 | .qm 文件 |
| **DataSetResultStep** (本文档) | 查询前后 | 动态条件注入、结果脱敏 | Java 代码 |

本文档介绍 `DataSetResultStep` 方式，使用 **Step 模式** 实现数据权限控制，通过返回值控制执行流程。

## 核心组件

### SecurityContext

`SecurityContext` 是权限信息的载体，包含以下字段：

```java
public static class SecurityContext {
    private String authorization;  // 原始授权头（如 Bearer token）
    private String userId;         // 用户ID
    private List<String> roles;    // 角色列表
    private String tenantId;       // 租户ID（多租户场景）
    private String deptId;         // 部门ID（数据权限场景）
    private Map<String, Object> attributes;  // 额外属性
}
```

### DataSetResultStep

Step 接口定义了两个关键方法：

```java
public interface DataSetResultStep {
    int CONTINUE = 0;  // 继续执行
    int ABORT = 1;     // 中止执行

    // 查询执行前（添加权限条件）
    default int beforeQuery(ModelResultContext ctx) {
        return CONTINUE;
    }

    // 查询结果处理（数据脱敏等）
    default int process(ModelResultContext ctx) {
        return CONTINUE;
    }
}
```

## 使用方法

### 方式一：创建自定义权限步骤

```java
package com.example.security;

import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.*;
import com.foggyframework.dataset.jdbc.model.def.query.request.SliceRequestDef;
import org.springframework.stereotype.Component;

@Component
public class TenantAuthStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        ModelResultContext.SecurityContext security = ctx.getSecurityContext();
        if (security == null || security.getAuthorization() == null) {
            return CONTINUE;
        }

        // 1. 从 token 解析租户信息
        String tenantId = parseTenantFromToken(security.getAuthorization());
        if (tenantId == null) {
            return CONTINUE;
        }

        // 2. 添加租户过滤条件
        SliceRequestDef tenantFilter = new SliceRequestDef();
        tenantFilter.setField("tenant_id");
        tenantFilter.setOp("eq");
        tenantFilter.setValue(tenantId);

        // 3. 加入现有过滤条件
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        if (slice == null) {
            slice = new ArrayList<>();
            ctx.getRequest().getParam().setSlice(slice);
        }
        slice.add(tenantFilter);

        return CONTINUE;
    }

    private String parseTenantFromToken(String authorization) {
        // 实现 token 解析逻辑
        // 例如：JWT 解析、Redis 查询等
        if (authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            // ... 解析逻辑
        }
        return null;
    }

    @Override
    public int order() {
        return 1000;  // 高优先级，最先执行
    }
}
```

使用 `@Component` 注解后，Spring 会自动将其注册到 `DataSetResultFilterManager`。

### 方式二：继承 AuthorizationStep

框架提供了 `AuthorizationStep` 基类，简化常见场景：

```java
@Component
public class MyAuthStep extends AuthorizationStep {

    @Override
    protected void parseSecurityContext(ModelResultContext.SecurityContext security) {
        String token = security.getAuthorization();
        if (token != null && token.startsWith("Bearer ")) {
            // 解析 JWT token
            Claims claims = parseJwt(token.substring(7));
            security.setTenantId(claims.get("tenant_id", String.class));
            security.setUserId(claims.get("user_id", String.class));
            security.setDeptId(claims.get("dept_id", String.class));
        }
    }

    @Override
    protected boolean shouldFilterByUser(ModelResultContext.SecurityContext security) {
        // 非管理员需要按用户过滤
        List<String> roles = security.getRoles();
        return roles == null || !roles.contains("ADMIN");
    }
}
```

配置字段映射：

```java
@Bean
public AuthorizationStep authorizationStep() {
    AuthorizationStep step = new MyAuthStep();
    step.setTenantIdColumn("org_id");      // 自定义租户字段名
    step.setDeptIdColumn("department_id"); // 自定义部门字段名
    step.setUserIdColumn("creator_id");    // 自定义用户字段名
    return step;
}
```

## 数据流向

```
HTTP 请求 (Authorization: Bearer xxx)
    ↓
LocalDatasetAccessor.queryModel(authorization)
    ↓
SecurityContext.fromAuthorization(authorization)
    ↓
SemanticQueryServiceV3Impl
    ↓
ModelResultContext { securityContext: SecurityContext }
    ↓
DataSetResultStep.beforeQuery(ctx)  ← 在这里添加权限条件
    ↓
执行 SQL 查询（已包含权限条件）
    ↓
DataSetResultStep.process(ctx)  ← 在这里处理结果（脱敏等）
    ↓
返回结果
```

## 常见场景

### 场景1：多租户数据隔离

```java
@Override
public int beforeQuery(ModelResultContext ctx) {
    SecurityContext security = ctx.getSecurityContext();
    if (security == null) return CONTINUE;

    String tenantId = security.getTenantId();
    if (tenantId != null) {
        addFilter(ctx, "tenant_id", "eq", tenantId);
    }
    return CONTINUE;
}
```

### 场景2：部门数据权限

```java
@Override
public int beforeQuery(ModelResultContext ctx) {
    SecurityContext security = ctx.getSecurityContext();
    if (security == null) return CONTINUE;

    // 查询用户所属部门及下级部门
    List<String> deptIds = getDeptAndChildren(security.getDeptId());

    if (!deptIds.isEmpty()) {
        addFilter(ctx, "dept_id", "in", deptIds);
    }
    return CONTINUE;
}
```

### 场景3：敏感数据脱敏

```java
@Override
public int process(ModelResultContext ctx) {
    SecurityContext security = ctx.getSecurityContext();

    // 非管理员需要脱敏
    if (!isAdmin(security)) {
        for (Object item : ctx.getPagingResult().getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            // 手机号脱敏
            String phone = (String) row.get("phone");
            if (phone != null && phone.length() == 11) {
                row.put("phone", phone.substring(0, 3) + "****" + phone.substring(7));
            }
        }
    }
    return CONTINUE;
}
```

### 场景4：按角色控制可查询字段

```java
@Override
public int beforeQuery(ModelResultContext ctx) {
    SecurityContext security = ctx.getSecurityContext();
    if (security == null) return CONTINUE;

    List<String> columns = ctx.getRequest().getParam().getColumns();

    // 非管理员不能查询敏感字段
    if (!isAdmin(security)) {
        List<String> sensitiveFields = Arrays.asList("salary", "id_card", "bank_account");
        columns.removeAll(sensitiveFields);
    }
    return CONTINUE;
}
```

## 执行顺序

Step 按 `order()` 返回值排序，**值越大越先执行**：

```java
@Override
public int order() {
    return 1000;  // 高优先级
}
```

建议的顺序：
- 1000+：权限验证（最先）
- 0：普通处理
- -100：格式转换（最后）

## 注意事项

1. **SecurityContext 可能为空**：始终检查 `ctx.getSecurityContext() != null`
2. **字段名需匹配模型**：添加的过滤条件字段名必须在 TM 模型中定义
3. **性能考虑**：避免在 `beforeQuery` 中执行耗时操作
4. **中止查询**：返回 `ABORT` 可以中止整个查询流程

## 相关文件

- `ModelResultContext.java` - 上下文定义（含 SecurityContext）
- `DataSetResultStep.java` - Step 接口
- `AuthorizationStep.java` - 权限步骤基类
- `DefaultDataSetResultFilterManagerImpl.java` - 管理器实现
- [QueryModel-Accesses-Control.md](./QueryModel-Accesses-Control.md) - accesses 方式的权限控制
