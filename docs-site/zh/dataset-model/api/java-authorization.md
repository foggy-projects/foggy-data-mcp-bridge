# Java 编程式权限控制

除了在 [QM 文件中声明式定义权限](./authorization.md)，Foggy Dataset Model 还支持通过 Java 代码动态控制权限。这种方式更灵活，适合复杂的业务场景。

## 1. 核心架构

### 1.1 执行流程

在查询执行过程中，`DataSetResultFilterManager` 会调用所有注册的 `DataSetResultStep`：

```java
// 查询前预处理
dataSetResultFilterManager.beforeQuery(context);

// 执行查询
PagingResultImpl result = jdbcService.query(context.getRequest());

// 查询后处理
dataSetResultFilterManager.process(context);
```

### 1.2 核心接口

**DataSetResultStep** - 预处理步骤接口：

```java
public interface DataSetResultStep extends FoggyStep<ModelResultContext> {

    /**
     * 查询前处理
     * - 添加权限过滤条件（修改 slice）
     * - 控制可见字段（修改 columns/exColumns）
     * - 验证请求合法性
     */
    default int beforeQuery(ModelResultContext ctx) {
        return CONTINUE;
    }

    /**
     * 查询后处理
     * - 数据脱敏
     * - 格式转换
     * - 结果集过滤
     */
    default int process(ModelResultContext ctx) {
        return CONTINUE;
    }
}
```

**返回值说明**：
- `CONTINUE` (0) - 继续执行下一个步骤
- `ABORT` (1) - 中止执行链（非错误情况）

---

## 2. 行级权限控制

### 2.1 基本示例

通过向 `queryRequest.param.slice` 添加过滤条件来控制行级权限：

```java
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import org.springframework.stereotype.Component;

@Component
public class TenantAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        // 1. 获取当前用户的租户信息
        String tenantId = getCurrentUserTenantId();

        // 2. 创建租户过滤条件
        SliceRequestDef tenantFilter = new SliceRequestDef();
        tenantFilter.setField("tenant_id");
        tenantFilter.setOp("eq");  // 等于
        tenantFilter.setValue(tenantId);

        // 3. 添加到现有过滤条件
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        if (slice == null) {
            slice = new ArrayList<>();
            ctx.getRequest().getParam().setSlice(slice);
        }
        slice.add(tenantFilter);

        return CONTINUE;
    }

    private String getCurrentUserTenantId() {
        // 从 SecurityContext 或 ThreadLocal 获取当前用户租户ID
        return SecurityContextHolder.getContext().getTenantId();
    }

    @Override
    public int order() {
        return 1000;  // 权限过滤应优先执行
    }
}
```

### 2.2 使用 SecurityContext

`ModelResultContext` 内置了 `SecurityContext` 用于传递认证信息：

```java
@Component
public class RoleBasedAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        // 1. 获取安全上下文
        ModelResultContext.SecurityContext security = ctx.getSecurityContext();
        if (security == null) {
            throw new UnauthorizedException("未登录");
        }

        // 2. 根据角色添加不同的过滤条件
        String userId = security.getUserId();
        List<String> roles = security.getRoles();

        if (roles.contains("ADMIN")) {
            // 管理员无限制，不添加过滤条件
            return CONTINUE;
        }

        if (roles.contains("MANAGER")) {
            // 经理可查看本部门数据
            addFilter(ctx, "dept_id", security.getDeptId());
        } else {
            // 普通用户只能查看自己的数据
            addFilter(ctx, "user_id", userId);
        }

        return CONTINUE;
    }

    private void addFilter(ModelResultContext ctx, String field, String value) {
        SliceRequestDef filter = new SliceRequestDef();
        filter.setField(field);
        filter.setOp("eq");
        filter.setValue(value);

        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        if (slice == null) {
            slice = new ArrayList<>();
            ctx.getRequest().getParam().setSlice(slice);
        }
        slice.add(filter);
    }

    @Override
    public int order() {
        return 1000;
    }
}
```

### 2.3 复杂过滤条件

支持所有 [DSL 操作符](../tm-qm/query-dsl.md#2-slice-过滤条件)：

```java
@Component
public class ComplexAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        String userId = getCurrentUserId();
        List<SliceRequestDef> slice = getOrCreateSlice(ctx);

        // 示例 1: IN 条件 - 只能查看指定区域的数据
        SliceRequestDef regionFilter = new SliceRequestDef();
        regionFilter.setField("region_id");
        regionFilter.setOp("in");
        regionFilter.setValue(Arrays.asList("R001", "R002", "R003"));
        slice.add(regionFilter);

        // 示例 2: 范围条件 - 只能查看最近 30 天的数据
        SliceRequestDef dateFilter = new SliceRequestDef();
        dateFilter.setField("order_date");
        dateFilter.setOp(">=");
        dateFilter.setValue(LocalDate.now().minusDays(30));
        slice.add(dateFilter);

        // 示例 3: OR 条件 - 可以查看自己创建的或指派给自己的订单
        SliceRequestDef orFilter = new SliceRequestDef();
        orFilter.setField("order");  // 逻辑组的名称可以是任意值

        CondRequestDef creatorCond = new CondRequestDef();
        creatorCond.setField("creator_id");
        creatorCond.setOp("eq");
        creatorCond.setValue(userId);

        CondRequestDef assigneeCond = new CondRequestDef();
        assigneeCond.setField("assignee_id");
        assigneeCond.setOp("eq");
        assigneeCond.setValue(userId);

        orFilter.setChildren(Arrays.asList(creatorCond, assigneeCond));
        slice.add(orFilter);

        return CONTINUE;
    }

    private List<SliceRequestDef> getOrCreateSlice(ModelResultContext ctx) {
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        if (slice == null) {
            slice = new ArrayList<>();
            ctx.getRequest().getParam().setSlice(slice);
        }
        return slice;
    }

    @Override
    public int order() {
        return 1000;
    }
}
```

---

## 3. 列级权限控制

### 3.1 控制可见字段

通过修改 `columns` 和 `exColumns` 来控制字段级权限：

```java
@Component
public class ColumnAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        List<String> roles = getCurrentUserRoles();
        DbQueryRequestDef param = ctx.getRequest().getParam();

        // 非财务人员不能查看金额字段
        if (!roles.contains("FINANCE")) {
            List<String> exColumns = param.getExColumns();
            if (exColumns == null) {
                exColumns = new ArrayList<>();
                param.setExColumns(exColumns);
            }

            // 添加要排除的字段
            exColumns.add("total_amount");
            exColumns.add("cost_amount");
            exColumns.add("profit_amount");
        }

        // 非管理员不能查看敏感字段
        if (!roles.contains("ADMIN")) {
            List<String> exColumns = param.getExColumns();
            if (exColumns == null) {
                exColumns = new ArrayList<>();
                param.setExColumns(exColumns);
            }

            exColumns.add("customer$phone");
            exColumns.add("customer$id_card");
        }

        return CONTINUE;
    }

    @Override
    public int order() {
        return 1000;
    }
}
```

### 3.2 动态调整查询字段

```java
@Component
public class DynamicColumnStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        DbQueryRequestDef param = ctx.getRequest().getParam();
        List<String> columns = param.getColumns();

        // 如果未指定 columns，根据权限设置默认字段
        if (columns == null || columns.isEmpty()) {
            columns = getDefaultColumnsForUser();
            param.setColumns(columns);
        } else {
            // 过滤掉用户没有权限的字段
            columns = columns.stream()
                    .filter(this::hasPermission)
                    .collect(Collectors.toList());
            param.setColumns(columns);
        }

        return CONTINUE;
    }

    private List<String> getDefaultColumnsForUser() {
        List<String> roles = getCurrentUserRoles();

        if (roles.contains("ADMIN")) {
            return Arrays.asList(
                "order_id", "customer$caption", "total_amount",
                "customer$phone", "customer$id_card"
            );
        } else if (roles.contains("SALES")) {
            return Arrays.asList(
                "order_id", "customer$caption", "total_amount"
            );
        } else {
            return Arrays.asList(
                "order_id", "customer$caption"
            );
        }
    }

    private boolean hasPermission(String column) {
        // 检查用户是否有权限查看该字段
        // 可以从配置文件或数据库读取字段权限配置
        return true;  // 示例：允许所有字段
    }

    @Override
    public int order() {
        return 1000;
    }
}
```

---

## 4. 查询后数据处理

### 4.1 数据脱敏

在 `process()` 方法中处理查询结果：

```java
@Component
public class DataMaskingStep implements DataSetResultStep {

    @Override
    public int process(ModelResultContext ctx) {
        PagingResultImpl result = ctx.getPagingResult();
        if (result == null || result.getData() == null) {
            return CONTINUE;
        }

        List<String> roles = getCurrentUserRoles();

        // 非管理员需要脱敏手机号和身份证
        if (!roles.contains("ADMIN")) {
            for (Map<String, Object> row : result.getData()) {
                maskPhoneNumber(row, "customer$phone");
                maskIdCard(row, "customer$id_card");
            }
        }

        return CONTINUE;
    }

    private void maskPhoneNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof String) {
            String phone = (String) value;
            if (phone.length() == 11) {
                row.put(key, phone.substring(0, 3) + "****" + phone.substring(7));
            }
        }
    }

    private void maskIdCard(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof String) {
            String idCard = (String) value;
            if (idCard.length() == 18) {
                row.put(key, idCard.substring(0, 6) + "********" + idCard.substring(14));
            }
        }
    }

    @Override
    public int order() {
        return 5000;  // 数据处理步骤可以晚一些执行
    }
}
```

### 4.2 金额单位转换

```java
@Component
public class MoneyConversionStep implements DataSetResultStep {

    @Override
    public int process(ModelResultContext ctx) {
        PagingResultImpl result = ctx.getPagingResult();
        if (result == null || result.getData() == null) {
            return CONTINUE;
        }

        // 金额字段列表（从配置或元数据获取）
        List<String> moneyFields = Arrays.asList(
            "total_amount", "cost_amount", "profit_amount"
        );

        // 将分转换为元
        for (Map<String, Object> row : result.getData()) {
            for (String field : moneyFields) {
                Object value = row.get(field);
                if (value instanceof Long) {
                    row.put(field, ((Long) value) / 100.0);
                } else if (value instanceof Integer) {
                    row.put(field, ((Integer) value) / 100.0);
                }
            }
        }

        return CONTINUE;
    }

    @Override
    public int order() {
        return 5000;
    }
}
```

---

## 5. 注册和配置

### 5.1 自动注册

将 Step 声明为 Spring Bean 即可自动注册：

```java
@Component
public class MyAuthorizationStep implements DataSetResultStep {
    // ...
}
```

### 5.2 手动注册

通过 Spring 配置类注册：

```java
@Configuration
public class DatasetAuthConfig {

    @Bean
    public DataSetResultStep tenantAuthStep() {
        TenantAuthorizationStep step = new TenantAuthorizationStep();
        step.setTenantIdColumn("tenant_id");
        return step;
    }

    @Bean
    public DataSetResultStep columnAuthStep() {
        return new ColumnAuthorizationStep();
    }

    @Bean
    public DataSetResultStep dataMaskingStep() {
        return new DataMaskingStep();
    }
}
```

### 5.3 执行顺序

通过 `order()` 方法控制执行顺序（数字越小越先执行）：

```java
@Override
public int order() {
    return 1000;  // 推荐值：权限过滤 1000，数据处理 5000
}
```

**系统内置步骤的顺序**：
- `QueryRequestValidationStep` - 0（参数校验）
- `AuthorizationStep` - 1000（权限过滤）
- `InlineExpressionPreprocessStep` - 2000（表达式预处理）
- `AutoGroupByStep` - 3000（自动分组）
- `SemanticMoneyStep` - 5000（金额转换）

---

## 6. 完整示例

### 6.1 多租户 + 角色权限

```java
@Component
@Slf4j
public class MultiTenantAuthStep implements DataSetResultStep {

    @Resource
    private UserService userService;

    @Resource
    private TenantService tenantService;

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        // 1. 获取安全上下文
        ModelResultContext.SecurityContext security = ctx.getSecurityContext();
        if (security == null) {
            throw new UnauthorizedException("用户未登录");
        }

        // 2. 解析 JWT token（如果需要）
        parseJwtToken(security);

        // 3. 获取用户信息
        String userId = security.getUserId();
        String tenantId = security.getTenantId();
        List<String> roles = security.getRoles();

        log.debug("用户 {} (租户 {}, 角色 {}) 发起查询", userId, tenantId, roles);

        // 4. 添加租户过滤（所有用户都必须）
        addTenantFilter(ctx, tenantId);

        // 5. 根据角色添加行权限
        if (!roles.contains("ADMIN")) {
            if (roles.contains("MANAGER")) {
                addDepartmentFilter(ctx, security.getDeptId());
            } else {
                addUserFilter(ctx, userId);
            }
        }

        // 6. 控制列权限
        if (!roles.contains("FINANCE")) {
            hideFinanceColumns(ctx);
        }

        if (!roles.contains("ADMIN")) {
            hideSensitiveColumns(ctx);
        }

        return CONTINUE;
    }

    @Override
    public int process(ModelResultContext ctx) {
        // 查询后数据脱敏
        List<String> roles = getCurrentUserRoles();
        if (!roles.contains("ADMIN")) {
            maskSensitiveData(ctx.getPagingResult());
        }

        return CONTINUE;
    }

    private void parseJwtToken(ModelResultContext.SecurityContext security) {
        String authorization = security.getAuthorization();
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            // 解析 JWT，填充 userId, tenantId, roles 等
            JwtClaims claims = JwtUtils.parse(token);
            security.setUserId(claims.getUserId());
            security.setTenantId(claims.getTenantId());
            security.setRoles(claims.getRoles());
            security.setDeptId(claims.getDeptId());
        }
    }

    private void addTenantFilter(ModelResultContext ctx, String tenantId) {
        SliceRequestDef filter = new SliceRequestDef();
        filter.setField("tenant_id");
        filter.setOp("eq");
        filter.setValue(tenantId);
        getOrCreateSlice(ctx).add(filter);
    }

    private void addDepartmentFilter(ModelResultContext ctx, String deptId) {
        SliceRequestDef filter = new SliceRequestDef();
        filter.setField("dept_id");
        filter.setOp("eq");
        filter.setValue(deptId);
        getOrCreateSlice(ctx).add(filter);
    }

    private void addUserFilter(ModelResultContext ctx, String userId) {
        SliceRequestDef filter = new SliceRequestDef();
        filter.setField("user_id");
        filter.setOp("eq");
        filter.setValue(userId);
        getOrCreateSlice(ctx).add(filter);
    }

    private void hideFinanceColumns(ModelResultContext ctx) {
        List<String> exColumns = getOrCreateExColumns(ctx);
        exColumns.addAll(Arrays.asList(
            "total_amount", "cost_amount", "profit_amount", "tax_amount"
        ));
    }

    private void hideSensitiveColumns(ModelResultContext ctx) {
        List<String> exColumns = getOrCreateExColumns(ctx);
        exColumns.addAll(Arrays.asList(
            "customer$phone", "customer$id_card", "customer$email"
        ));
    }

    private void maskSensitiveData(PagingResultImpl result) {
        if (result == null || result.getData() == null) {
            return;
        }

        for (Map<String, Object> row : result.getData()) {
            maskPhoneNumber(row, "customer$phone");
            maskIdCard(row, "customer$id_card");
        }
    }

    private List<SliceRequestDef> getOrCreateSlice(ModelResultContext ctx) {
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        if (slice == null) {
            slice = new ArrayList<>();
            ctx.getRequest().getParam().setSlice(slice);
        }
        return slice;
    }

    private List<String> getOrCreateExColumns(ModelResultContext ctx) {
        DbQueryRequestDef param = ctx.getRequest().getParam();
        List<String> exColumns = param.getExColumns();
        if (exColumns == null) {
            exColumns = new ArrayList<>();
            param.setExColumns(exColumns);
        }
        return exColumns;
    }

    @Override
    public int order() {
        return 1000;
    }
}
```

---

## 7. SecurityContext 设置

### 7.1 在 Controller 中设置

```java
@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    @Resource
    private QueryFacade queryFacade;

    @PostMapping("/query")
    public PagingResultImpl query(
            @RequestBody PagingRequest<DbQueryRequestDef> request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        // 1. 创建 SecurityContext
        ModelResultContext.SecurityContext security =
            ModelResultContext.SecurityContext.builder()
                .authorization(authorization)
                .userId(getCurrentUserId())
                .tenantId(getCurrentTenantId())
                .roles(getCurrentUserRoles())
                .deptId(getCurrentDeptId())
                .build();

        // 2. 设置到请求扩展数据中（供 Step 使用）
        request.setSecurityContext(security);

        // 3. 执行查询
        return queryFacade.query(request);
    }
}
```

### 7.2 在 Filter 中设置

```java
@Component
public class SecurityContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            // 解析 JWT 或 Session，设置到 ThreadLocal
            SecurityContextHolder.setContext(
                parseAuthorizationHeader(authorization)
            );
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clear();
        }
    }
}
```

---

## 8. 与 QM 权限的对比

| 对比维度 | QM 文件权限（声明式） | Java Step 权限（编程式） |
|---------|---------------------|----------------------|
| **定义位置** | `.qm` 文件中的 `accesses` | Java 代码中的 `DataSetResultStep` |
| **执行时机** | 查询构建时（SQL 生成阶段） | 查询请求预处理阶段 |
| **灵活性** | 较低，适合固定规则 | 高，适合复杂动态逻辑 |
| **权限粒度** | 行级（基于字段或维度） | 行级 + 列级 + 结果集 |
| **上下文获取** | 通过 `import` 从 Spring Bean 获取 | 直接访问 `SecurityContext` |
| **数据脱敏** | 不支持 | 支持（在 `process()` 中处理） |
| **参数验证** | 不支持 | 支持（在 `beforeQuery()` 中验证） |
| **适用场景** | 简单、固定的权限规则 | 复杂、动态的业务逻辑 |

**推荐使用策略**：
- 简单权限（如按团队、部门过滤）→ 使用 QM 文件
- 复杂权限（如多租户 + 角色 + 列权限）→ 使用 Java Step
- 数据脱敏、格式转换 → 必须使用 Java Step

---

## 9. 注意事项

### 9.1 安全性

- **SQL 注入防护**：使用 `SliceRequestDef` 设置过滤条件，系统会自动参数化
- **避免直接拼接 SQL**：不要手动构造 SQL 字符串
- **验证用户身份**：在 `beforeQuery()` 中必须验证 `SecurityContext`

### 9.2 性能

- **索引优化**：确保权限过滤字段（如 `tenant_id`、`dept_id`）有索引
- **避免重复查询**：不要在 Step 中执行额外的数据库查询
- **缓存用户信息**：从 JWT token 解析的用户信息应缓存到 `SecurityContext`

### 9.3 调试

开启日志查看执行过程：

```yaml
logging:
  level:
    com.foggyframework.dataset.db.model.plugins: DEBUG
```

---

## 下一步

- [QM 权限控制](./authorization.md) - 声明式权限配置
- [DSL 查询 API](./query-api.md) - 查询接口参考
- [JSON 查询 DSL](../tm-qm/query-dsl.md) - 完整的 DSL 语法
