# Java Programmatic Permission Control

In addition to [declaratively defining permissions in QM files](./authorization.md), Foggy Dataset Model also supports dynamic permission control through Java code. This approach is more flexible and suitable for complex business scenarios.

## 1. Core Architecture

### 1.1 Execution Flow

During query execution, `DataSetResultFilterManager` calls all registered `DataSetResultStep`:

```java
// Pre-query preprocessing
dataSetResultFilterManager.beforeQuery(context);

// Execute query
PagingResultImpl result = jdbcService.query(context.getRequest());

// Post-query processing
dataSetResultFilterManager.process(context);
```

### 1.2 Core Interface

**DataSetResultStep** - Preprocessing step interface:

```java
public interface DataSetResultStep extends FoggyStep<ModelResultContext> {

    /**
     * Pre-query processing
     * - Add permission filter conditions (modify slice)
     * - Control visible fields (modify columns/exColumns)
     * - Validate request legitimacy
     */
    default int beforeQuery(ModelResultContext ctx) {
        return CONTINUE;
    }

    /**
     * Post-query processing
     * - Data masking
     * - Format conversion
     * - Result set filtering
     */
    default int process(ModelResultContext ctx) {
        return CONTINUE;
    }
}
```

**Return Value Description**:
- `CONTINUE` (0) - Continue to next step
- `ABORT` (1) - Abort execution chain (non-error case)

---

## 2. Row-Level Permission Control

### 2.1 Basic Example

Control row-level permissions by adding filter conditions to `queryRequest.param.slice`:

```java
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import org.springframework.stereotype.Component;

@Component
public class TenantAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        // 1. Get current user's tenant information
        String tenantId = getCurrentUserTenantId();

        // 2. Create tenant filter condition
        SliceRequestDef tenantFilter = new SliceRequestDef();
        tenantFilter.setField("tenant_id");
        tenantFilter.setOp("eq");  // equals
        tenantFilter.setValue(tenantId);

        // 3. Add to existing filter conditions
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        if (slice == null) {
            slice = new ArrayList<>();
            ctx.getRequest().getParam().setSlice(slice);
        }
        slice.add(tenantFilter);

        return CONTINUE;
    }

    private String getCurrentUserTenantId() {
        // Get current user tenant ID from SecurityContext or ThreadLocal
        return SecurityContextHolder.getContext().getTenantId();
    }

    @Override
    public int order() {
        return 1000;  // Permission filtering should be executed first
    }
}
```

### 2.2 Using SecurityContext

`ModelResultContext` has built-in `SecurityContext` for passing authentication information:

```java
@Component
public class RoleBasedAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        // 1. Get security context
        ModelResultContext.SecurityContext security = ctx.getSecurityContext();
        if (security == null) {
            throw new UnauthorizedException("Not logged in");
        }

        // 2. Add different filter conditions based on roles
        String userId = security.getUserId();
        List<String> roles = security.getRoles();

        if (roles.contains("ADMIN")) {
            // Admin has no restrictions, don't add filter conditions
            return CONTINUE;
        }

        if (roles.contains("MANAGER")) {
            // Manager can view department data
            addFilter(ctx, "dept_id", security.getDeptId());
        } else {
            // Regular users can only view their own data
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

### 2.3 Complex Filter Conditions

Supports all [DSL operators](../tm-qm/query-dsl.md#2-slice-filter-conditions):

```java
@Component
public class ComplexAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        String userId = getCurrentUserId();
        List<SliceRequestDef> slice = getOrCreateSlice(ctx);

        // Example 1: IN condition - can only view data in specified regions
        SliceRequestDef regionFilter = new SliceRequestDef();
        regionFilter.setField("region_id");
        regionFilter.setOp("in");
        regionFilter.setValue(Arrays.asList("R001", "R002", "R003"));
        slice.add(regionFilter);

        // Example 2: Range condition - can only view data from last 30 days
        SliceRequestDef dateFilter = new SliceRequestDef();
        dateFilter.setField("order_date");
        dateFilter.setOp(">=");
        dateFilter.setValue(LocalDate.now().minusDays(30));
        slice.add(dateFilter);

        // Example 3: OR condition - can view orders created by self or assigned to self
        CondRequestDef creatorCond = new CondRequestDef();
        creatorCond.setField("creator_id");
        creatorCond.setOp("eq");
        creatorCond.setValue(userId);

        CondRequestDef assigneeCond = new CondRequestDef();
        assigneeCond.setField("assignee_id");
        assigneeCond.setOp("eq");
        assigneeCond.setValue(userId);

        // Use $or syntax to create OR condition group
        SliceRequestDef orFilter = SliceRequestDef.or(Arrays.asList(creatorCond, assigneeCond));
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

## 3. Column-Level Permission Control

### 3.1 Control Visible Fields

Control field-level permissions by modifying `columns` and `exColumns`:

```java
@Component
public class ColumnAuthorizationStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        List<String> roles = getCurrentUserRoles();
        DbQueryRequestDef param = ctx.getRequest().getParam();

        // Non-finance personnel cannot view amount fields
        if (!roles.contains("FINANCE")) {
            List<String> exColumns = param.getExColumns();
            if (exColumns == null) {
                exColumns = new ArrayList<>();
                param.setExColumns(exColumns);
            }

            // Add fields to exclude
            exColumns.add("total_amount");
            exColumns.add("cost_amount");
            exColumns.add("profit_amount");
        }

        // Non-admins cannot view sensitive fields
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

### 3.2 Dynamic Query Field Adjustment

```java
@Component
public class DynamicColumnStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        DbQueryRequestDef param = ctx.getRequest().getParam();
        List<String> columns = param.getColumns();

        // If columns not specified, set default fields based on permissions
        if (columns == null || columns.isEmpty()) {
            columns = getDefaultColumnsForUser();
            param.setColumns(columns);
        } else {
            // Filter out fields user doesn't have permission for
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
        // Check if user has permission to view this field
        // Can read field permission configuration from config file or database
        return true;  // Example: allow all fields
    }

    @Override
    public int order() {
        return 1000;
    }
}
```

---

## 4. Post-Query Data Processing

### 4.1 Data Masking

Process query results in `process()` method:

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

        // Non-admins need to mask phone numbers and ID cards
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
        return 5000;  // Data processing steps can execute later
    }
}
```

### 4.2 Amount Unit Conversion

```java
@Component
public class MoneyConversionStep implements DataSetResultStep {

    @Override
    public int process(ModelResultContext ctx) {
        PagingResultImpl result = ctx.getPagingResult();
        if (result == null || result.getData() == null) {
            return CONTINUE;
        }

        // Amount field list (get from config or metadata)
        List<String> moneyFields = Arrays.asList(
            "total_amount", "cost_amount", "profit_amount"
        );

        // Convert cents to yuan
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

## 5. Registration and Configuration

### 5.1 Auto Registration

Simply declare Step as a Spring Bean for auto-registration:

```java
@Component
public class MyAuthorizationStep implements DataSetResultStep {
    // ...
}
```

### 5.2 Manual Registration

Register through Spring configuration class:

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

### 5.3 Execution Order

Control execution order through `order()` method (smaller number executes first):

```java
@Override
public int order() {
    return 1000;  // Recommended: permission filtering 1000, data processing 5000
}
```

**System built-in step order**:
- `QueryRequestValidationStep` - 0 (parameter validation)
- `AuthorizationStep` - 1000 (permission filtering)
- `InlineExpressionPreprocessStep` - 2000 (expression preprocessing)
- `AutoGroupByStep` - 3000 (auto grouping)
- `SemanticMoneyStep` - 5000 (amount conversion)

---

## 6. Complete Example

### 6.1 Multi-Tenant + Role Permissions

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
        // 1. Get security context
        ModelResultContext.SecurityContext security = ctx.getSecurityContext();
        if (security == null) {
            throw new UnauthorizedException("User not logged in");
        }

        // 2. Parse JWT token (if needed)
        parseJwtToken(security);

        // 3. Get user information
        String userId = security.getUserId();
        String tenantId = security.getTenantId();
        List<String> roles = security.getRoles();

        log.debug("User {} (tenant {}, roles {}) initiated query", userId, tenantId, roles);

        // 4. Add tenant filter (required for all users)
        addTenantFilter(ctx, tenantId);

        // 5. Add row permissions based on roles
        if (!roles.contains("ADMIN")) {
            if (roles.contains("MANAGER")) {
                addDepartmentFilter(ctx, security.getDeptId());
            } else {
                addUserFilter(ctx, userId);
            }
        }

        // 6. Control column permissions
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
        // Post-query data masking
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
            // Parse JWT, populate userId, tenantId, roles, etc.
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

## 7. SecurityContext Setup

### 7.1 Setup in Controller

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
        // 1. Create SecurityContext
        ModelResultContext.SecurityContext security =
            ModelResultContext.SecurityContext.builder()
                .authorization(authorization)
                .userId(getCurrentUserId())
                .tenantId(getCurrentTenantId())
                .roles(getCurrentUserRoles())
                .deptId(getCurrentDeptId())
                .build();

        // 2. Set to request extension data (for Step use)
        request.setSecurityContext(security);

        // 3. Execute query
        return queryFacade.query(request);
    }
}
```

### 7.2 Setup in Filter

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
            // Parse JWT or Session, set to ThreadLocal
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

## 8. Comparison with QM Permissions

| Comparison Dimension | QM File Permissions (Declarative) | Java Step Permissions (Programmatic) |
|----------------------|----------------------------------|--------------------------------------|
| **Definition Location** | `accesses` in `.qm` file | `DataSetResultStep` in Java code |
| **Execution Timing** | Query construction time (SQL generation phase) | Query request preprocessing phase |
| **Flexibility** | Low, suitable for fixed rules | High, suitable for complex dynamic logic |
| **Permission Granularity** | Row-level (based on fields or dimensions) | Row-level + Column-level + Result set |
| **Context Access** | Get from Spring Bean via `import` | Direct access to `SecurityContext` |
| **Data Masking** | Not supported | Supported (process in `process()`) |
| **Parameter Validation** | Not supported | Supported (validate in `beforeQuery()`) |
| **Use Cases** | Simple, fixed permission rules | Complex, dynamic business logic |

**Recommended Usage Strategy**:
- Simple permissions (e.g., filter by team, department) → Use QM file
- Complex permissions (e.g., multi-tenant + role + column permissions) → Use Java Step
- Data masking, format conversion → Must use Java Step

---

## 9. Notes

### 9.1 Security

- **SQL Injection Protection**: Use `SliceRequestDef` to set filter conditions, system will automatically parameterize
- **Avoid Direct SQL Concatenation**: Don't manually construct SQL strings
- **Verify User Identity**: Must verify `SecurityContext` in `beforeQuery()`

### 9.2 Performance

- **Index Optimization**: Ensure permission filter fields (e.g., `tenant_id`, `dept_id`) have indexes
- **Avoid Duplicate Queries**: Don't execute additional database queries in Step
- **Cache User Information**: User information parsed from JWT token should be cached to `SecurityContext`

### 9.3 Debugging

Enable logs to view execution process:

```yaml
logging:
  level:
    com.foggyframework.dataset.db.model.plugins: DEBUG
```

---

## Next Steps

- [QM Permission Control](./authorization.md) - Declarative permission configuration
- [DSL Query API](./query-api.md) - Query interface reference
- [JSON Query DSL](../tm-qm/query-dsl.md) - Complete DSL syntax
