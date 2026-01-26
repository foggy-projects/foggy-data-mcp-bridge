package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限过滤步骤示例
 *
 * <p>演示如何在 beforeQuery 中根据 SecurityContext 添加数据权限过滤条件。
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>多租户数据隔离（按 tenant_id 过滤）</li>
 *   <li>部门数据权限（按 dept_id 过滤）</li>
 *   <li>用户数据隔离（按 user_id 过滤）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 1. 在 Spring 配置中注册为 Bean
 * @Bean
 * public DataSetResultStep authorizationStep() {
 *     return new AuthorizationStep();
 * }
 *
 * // 2. 自定义权限解析逻辑
 * public class MyAuthStep extends AuthorizationStep {
 *     @Override
 *     protected void parseSecurityContext(ModelResultContext.SecurityContext ctx) {
 *         // 从 JWT token 解析用户信息
 *         String token = ctx.getAuthorization();
 *         if (token != null && token.startsWith("Bearer ")) {
 *             // 解析 token，设置 userId、tenantId、roles 等
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
public class AuthorizationStep implements DataSetResultStep {

    /**
     * 租户ID字段名（可配置）
     */
    protected String tenantIdColumn = "tenant_id";

    /**
     * 部门ID字段名（可配置）
     */
    protected String deptIdColumn = "dept_id";

    /**
     * 用户ID字段名（可配置）
     */
    protected String userIdColumn = "user_id";

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        ModelResultContext.SecurityContext security = ctx.getSecurityContext();
        if (security == null) {
            log.debug("No SecurityContext, skipping authorization filter");
            return CONTINUE;
        }

        // 可选：解析 authorization token 填充其他字段
        parseSecurityContext(security);

        // 获取现有的过滤条件
        List<SliceRequestDef> existingSlice = ctx.getRequest().getParam().getSlice();
        List<SliceRequestDef> newSlice = existingSlice != null ? new ArrayList<>(existingSlice) : new ArrayList<>();

        // 添加租户过滤（如果配置了 tenantId）
        if (StringUtils.isNotEmpty(security.getTenantId())) {
            SliceRequestDef tenantFilter = new SliceRequestDef();
            tenantFilter.setField(tenantIdColumn);
            tenantFilter.setOp("eq");
            tenantFilter.setValue(security.getTenantId());
            newSlice.add(tenantFilter);
            log.debug("Added tenant filter: {} = {}", tenantIdColumn, security.getTenantId());
        }

        // 添加部门过滤（如果配置了 deptId）
        if (StringUtils.isNotEmpty(security.getDeptId())) {
            SliceRequestDef deptFilter = new SliceRequestDef();
            deptFilter.setField(deptIdColumn);
            deptFilter.setOp("eq");
            deptFilter.setValue(security.getDeptId());
            newSlice.add(deptFilter);
            log.debug("Added dept filter: {} = {}", deptIdColumn, security.getDeptId());
        }

        // 添加用户过滤（如果配置了 userId 且需要用户级别数据隔离）
        if (shouldFilterByUser(security) && StringUtils.isNotEmpty(security.getUserId())) {
            SliceRequestDef userFilter = new SliceRequestDef();
            userFilter.setField(userIdColumn);
            userFilter.setOp("eq");
            userFilter.setValue(security.getUserId());
            newSlice.add(userFilter);
            log.debug("Added user filter: {} = {}", userIdColumn, security.getUserId());
        }

        // 更新过滤条件
        if (newSlice.size() > (existingSlice != null ? existingSlice.size() : 0)) {
            ctx.getRequest().getParam().setSlice(newSlice);
        }

        return CONTINUE;
    }

    /**
     * 解析 SecurityContext
     *
     * <p>子类可以重写此方法，从 authorization token 中解析用户信息。
     *
     * @param security 安全上下文
     */
    protected void parseSecurityContext(ModelResultContext.SecurityContext security) {
        // 默认不做解析，子类可重写
        // 例如：从 JWT token 解析 userId, tenantId, roles 等
    }

    /**
     * 判断是否需要按用户过滤
     *
     * <p>子类可以重写此方法，根据角色或其他条件判断。
     *
     * @param security 安全上下文
     * @return true 表示需要按用户过滤
     */
    protected boolean shouldFilterByUser(ModelResultContext.SecurityContext security) {
        // 默认不按用户过滤（通常只按租户/部门过滤）
        // 子类可根据角色判断，例如非管理员才需要按用户过滤
        return false;
    }

    @Override
    public int order() {
        // 权限过滤应该最先执行
        return 1000;
    }

    // Setter methods for configuration
    public void setTenantIdColumn(String tenantIdColumn) {
        this.tenantIdColumn = tenantIdColumn;
    }

    public void setDeptIdColumn(String deptIdColumn) {
        this.deptIdColumn = deptIdColumn;
    }

    public void setUserIdColumn(String userIdColumn) {
        this.userIdColumn = userIdColumn;
    }
}
