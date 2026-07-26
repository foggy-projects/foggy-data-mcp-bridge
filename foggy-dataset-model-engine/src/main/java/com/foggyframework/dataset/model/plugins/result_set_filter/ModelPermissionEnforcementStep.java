package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionService;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.permission.PermissionDecision;
import com.foggyframework.dataset.model.semantic.permission.PermissionPredicate;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import jakarta.annotation.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authorizes source query execution before field resolution, caches, or
 * pre-aggregation routing are evaluated.
 */
@Component
@Order(-40)
public class ModelPermissionEnforcementStep implements DataSetResultStep {

    @Resource
    private ModelPermissionService modelPermissionService;

    @Resource
    private QueryModelLoader queryModelLoader;

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null || ctx.getQueryModel() == null) {
            return CONTINUE;
        }
        AuthorizationTarget target = resolveAuthorizationTarget(ctx);
        PermissionDecision decision = modelPermissionService.evaluate(
                target.queryModel(),
                ctx.getNamespace(),
                target.action(),
                ctx.getRequestIdentity(),
                ctx.getPermissionSession()
        );
        if (!decision.isAllow()) {
            throw ModelPermissionException.denied();
        }
        boolean alreadyApplied = ctx.getPermissionDecision() == decision;
        ctx.setPermissionDecision(decision);
        if (!alreadyApplied) {
            mergeDecisionAttributes(ctx, decision);
            mergeRowPredicates(ctx, target.mapPredicates(decision.getRowPredicates()));
        }
        return CONTINUE;
    }

    private AuthorizationTarget resolveAuthorizationTarget(ModelResultContext ctx) {
        QueryModel queryModel = ctx.getQueryModel();
        String modelName = queryModel.getName();
        int separator = modelName == null
                ? -1
                : modelName.indexOf(SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
        if (separator <= 0) {
            PermissionAction action = ctx.getPermissionAction() != null
                    ? ctx.getPermissionAction()
                    : PermissionAction.EXECUTE;
            return AuthorizationTarget.direct(queryModel, action);
        }

        String sourceModelName = modelName.substring(0, separator);
        String dimensionFieldBase = modelName.substring(separator + 1);
        QueryModel sourceModel = queryModelLoader.getJdbcQueryModel(sourceModelName, ctx.getNamespace());
        if (sourceModel == null) {
            throw ModelPermissionException.invalid(
                    new IllegalStateException("synthetic member source model is unavailable"));
        }
        ctx.setPermissionAction(PermissionAction.MEMBER_QUERY);
        return AuthorizationTarget.synthetic(
                sourceModel,
                dimensionFieldBase,
                collectSyntheticFields(queryModel)
        );
    }

    private Set<String> collectSyntheticFields(QueryModel queryModel) {
        Set<String> fields = new LinkedHashSet<>();
        if (queryModel.getJdbcQueryColumns() == null) {
            return fields;
        }
        for (DbQueryColumn column : queryModel.getJdbcQueryColumns()) {
            if (column != null && column.getName() != null) {
                fields.add(column.getName());
            }
        }
        return fields;
    }

    private void mergeDecisionAttributes(ModelResultContext ctx, PermissionDecision decision) {
        if (decision.getAttributes().isEmpty()) {
            return;
        }
        ModelResultContext.SecurityContext securityContext = ctx.getSecurityContext();
        if (securityContext == null) {
            securityContext = ModelResultContext.SecurityContext.fromAuthorization(ctx.getAuthorization());
            ctx.setSecurityContext(securityContext);
            ctx.setRequestIdentity(ctx.getRequestIdentity());
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (securityContext.getAttributes() != null) {
            merged.putAll(securityContext.getAttributes());
        }
        merged.putAll(decision.getAttributes());
        securityContext.setAttributes(Map.copyOf(merged));
    }

    private void mergeRowPredicates(ModelResultContext ctx, List<PermissionPredicate> predicates) {
        if (predicates.isEmpty()) {
            return;
        }
        List<SliceRequestDef> merged = new ArrayList<>();
        if (ctx.getSystemSlice() != null) {
            merged.addAll(ctx.getSystemSlice());
        }
        predicates.stream().map(PermissionPredicate::toSlice).forEach(merged::add);
        ctx.setSystemSlice(List.copyOf(merged));
    }

    private record AuthorizationTarget(
            QueryModel queryModel,
            PermissionAction action,
            String dimensionFieldBase,
            Set<String> syntheticFields
    ) {
        private static AuthorizationTarget direct(QueryModel queryModel, PermissionAction action) {
            return new AuthorizationTarget(queryModel, action, null, Set.of());
        }

        private static AuthorizationTarget synthetic(
                QueryModel sourceModel,
                String dimensionFieldBase,
                Set<String> syntheticFields
        ) {
            return new AuthorizationTarget(
                    sourceModel,
                    PermissionAction.MEMBER_QUERY,
                    dimensionFieldBase,
                    Set.copyOf(syntheticFields)
            );
        }

        private List<PermissionPredicate> mapPredicates(List<PermissionPredicate> predicates) {
            if (dimensionFieldBase == null || predicates.isEmpty()) {
                return predicates;
            }
            List<PermissionPredicate> mapped = new ArrayList<>(predicates.size());
            for (PermissionPredicate predicate : predicates) {
                String mappedField = mapField(predicate.getField());
                Set<String> mappedReferences = new LinkedHashSet<>();
                for (String referencedField : predicate.getReferencedFields()) {
                    mappedReferences.add(mapField(referencedField));
                }
                mapped.add(new PermissionPredicate(
                        predicate.getOrigin(),
                        predicate.getBinding(),
                        mappedField,
                        predicate.getOperator(),
                        predicate.getValueType(),
                        predicate.getValue(),
                        mappedReferences,
                        predicate.getProofStatus()
                ));
            }
            return List.copyOf(mapped);
        }

        private String mapField(String sourceField) {
            if (sourceField == null) {
                throw unmappablePredicate();
            }
            String mapped = sourceField;
            if (sourceField.equals(dimensionFieldBase)) {
                mapped = "id";
            } else if (sourceField.startsWith(dimensionFieldBase + "$")) {
                mapped = sourceField.substring(dimensionFieldBase.length() + 1);
            } else if (sourceField.startsWith(dimensionFieldBase + ".")) {
                mapped = sourceField.substring(dimensionFieldBase.length() + 1)
                        .replace('.', '$');
            }
            if (!syntheticFields.contains(mapped)) {
                throw unmappablePredicate();
            }
            return mapped;
        }

        private ModelPermissionException unmappablePredicate() {
            return ModelPermissionException.invalid(new IllegalArgumentException(
                    "row permission cannot be represented by the member source query"));
        }
    }
}
