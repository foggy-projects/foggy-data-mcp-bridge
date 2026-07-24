package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolution;
import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolver;
import jakarta.annotation.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Resolves TM/QM declarative field permissions before the existing request
 * field access validator runs.
 */
@Component
@Order(-30)
public class ModelFieldPermissionResolveStep implements DataSetResultStep {

    @Resource
    private FieldPermissionResolver fieldPermissionResolver;

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null || ctx.getQueryModel() == null) {
            return CONTINUE;
        }
        FieldPermissionResolution resolution = fieldPermissionResolver.resolve(ctx);
        ctx.setFieldAccess(resolution.getEffectiveFieldAccess());
        return CONTINUE;
    }
}
