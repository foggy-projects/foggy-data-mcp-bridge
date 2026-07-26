package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolution;
import com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolver;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
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

    @Resource
    private QueryModelLoader queryModelLoader;

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null || ctx.getQueryModel() == null) {
            return CONTINUE;
        }
        String modelName = ctx.getQueryModel().getName();
        int separator = modelName == null
                ? -1
                : modelName.indexOf(SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
        if (separator > 0) {
            resolveSyntheticMemberFieldPermission(ctx, modelName, separator);
            return CONTINUE;
        }
        FieldPermissionResolution resolution = fieldPermissionResolver.resolve(ctx);
        ctx.setFieldAccess(resolution.getEffectiveFieldAccess());
        return CONTINUE;
    }

    private void resolveSyntheticMemberFieldPermission(
            ModelResultContext ctx,
            String modelName,
            int separator
    ) {
        String sourceModelName = modelName.substring(0, separator);
        String dimensionFieldBase = modelName.substring(separator + 1);
        QueryModel sourceModel = queryModelLoader.getJdbcQueryModel(sourceModelName, ctx.getNamespace());
        if (sourceModel == null) {
            throw ModelPermissionException.invalid(
                    new IllegalStateException("synthetic member source model is unavailable"));
        }
        FieldPermissionResolution sourceResolution = fieldPermissionResolver.resolve(
                sourceModel,
                ctx.getNamespace(),
                ctx.getSecurityContext(),
                ctx.getFieldAccess(),
                ctx.getDeniedColumns()
        );
        if (sourceResolution.getEffectiveFieldAccess() != null
                && !sourceResolution.getEffectiveFieldAccess().contains(dimensionFieldBase)) {
            throw ModelPermissionException.denied();
        }
        // The synthetic schema only exposes fields belonging to the already
        // authorized source dimension. Its id/caption aliases are not source-QM
        // field names and must not be revalidated against the source allowlist.
        ctx.setFieldAccess(null);
        ctx.setDeniedColumns(null);
    }
}
