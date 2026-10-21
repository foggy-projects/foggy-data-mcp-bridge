package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.expression.CalculatedDependencyGraph;
import com.foggyframework.dataset.model.impl.query.QueryFieldBindings;
import com.foggyframework.dataset.model.semantic.member.MemberFieldBinding;
import com.foggyframework.dataset.model.semantic.member.permission.*;
import com.foggyframework.dataset.model.spi.*;
import jakarta.annotation.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/** Final member guard: external/system patches cannot reopen a restricted child subtree. */
@Component
@Order(-5)
public class SyntheticMemberFinalPermissionStep implements DataSetResultStep {
    @Resource private QueryModelLoader queryModelLoader;
    @Resource private SyntheticMemberInternalPatchStep patches;

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        QueryModel model = ctx.getQueryModel();
        if (model == null || model.getName() == null || !model.getName().contains("#")) return CONTINUE;
        DbQueryRequestDef request = ctx.getRequest().getParam();
        String root = model.getName().substring(model.getName().indexOf('#') + 1);
        QueryModel source = MemberFieldBinding.sourceModel(model, queryModelLoader, ctx.getNamespace());
        LinkedHashSet<String> schema = new LinkedHashSet<>();
        model.getJdbcQueryColumns().forEach(c -> schema.add(c.getName()));
        validateExternalDependencies(ctx, model, schema);
        Map<String, SyntheticMemberEffectivePermission> activated = new LinkedHashMap<>();
        Object rootPermission = ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY);
        if (rootPermission instanceof SyntheticMemberEffectivePermission permission) activated.put("", permission);

        // A policy can introduce another child dependency. Iterate to a fixed point;
        // each dimension is applied once, with its TM/QM override resolved locally.
        boolean changed;
        do {
            changed = false;
            Set<String> dependencies = dependencies(model, request, schema);
            for (DbDimension dimension : MemberFieldBinding.dimensions(source.getJdbcModel())) {
                String path = MemberFieldBinding.path(dimension);
                if (!path.startsWith(root + "$")) continue;
                String prefix = path.substring(root.length() + 1) + "$";
                if (activated.containsKey(prefix) || dependencies.stream().noneMatch(f -> f.startsWith(prefix))) continue;
                SyntheticMemberEffectivePermission permission = patches.resolveEffectivePermission(source, path);
                if (permission == null) permission = new SyntheticMemberEffectivePermission();
                activated.put(prefix, permission);
                changed = true;
                if (permission.hasQueryBuilders()) throw RX.throwAUserTip(
                        "child member queryBuilder cannot be safely applied through ancestor: " + path);
                validateVisible(permission, prefix, schema, dependencies);
                List<MemberPermissionSliceDef> slices = new ArrayList<>();
                if (permission.getForcedSlice() != null) for (var original : permission.getForcedSlice()) {
                    MemberPermissionSliceDef mapped = new MemberPermissionSliceDef();
                    mapped.setField(prefix + original.getField());
                    mapped.setOp(original.getOp());
                    mapped.setValue(scopedValue(original.resolveValue(patches.buildValueBuilderContext(ctx)), prefix));
                    slices.add(mapped);
                }
                patches.applyForcedSlice(request, slices, schema, ctx, source.getName(), path);
                List<OrderRequestDef> orders = new ArrayList<>();
                if (permission.getForcedOrderBy() != null) for (var original : permission.getForcedOrderBy()) {
                    OrderRequestDef mapped = new OrderRequestDef();
                    mapped.setField(prefix + original.getField());
                    mapped.setDir(original.getDir());
                    mapped.setNullFirst(original.isNullFirst());
                    mapped.setNullLast(original.isNullLast());
                    orders.add(mapped);
                }
                patches.applyForcedOrderBy(request, null, orders, schema);
            }
        } while (changed);
        activated.forEach((prefix, permission) -> checkHierarchy(request, permission, prefix));
        return CONTINUE;
    }

    private static void validateExternalDependencies(ModelResultContext ctx, QueryModel model, Set<String> schema) {
        // Client-provided patches are not privileged system predicates. Validate their
        // dependencies separately so trusted row policies may still filter hidden columns.
        Object raw = ctx.getExtData().get(com.foggyframework.dataset.model.semantic.member.SyntheticMemberExternalPatch.EXT_DATA_KEY);
        if (!(raw instanceof com.foggyframework.dataset.model.semantic.member.SyntheticMemberExternalPatch patch)) return;
        Set<String> denied = ctx.getDeniedColumns() == null || ctx.getDeniedColumns().isEmpty()
                || model.getPhysicalColumnMapping() == null ? Set.of()
                : model.getPhysicalColumnMapping().toDeniedQmFields(ctx.getDeniedColumns());
        if (ctx.getFieldAccess() == null && denied.isEmpty()) return;
        DbQueryRequestDef external = new DbQueryRequestDef();
        external.setColumns(patch.getVisibleColumns() == null ? List.of("1 AS memberPermissionProbe") : patch.getVisibleColumns());
        external.setSlice(patch.getForcedSlice());
        external.setOrderBy(patch.getForcedOrderBy());
        for (String field : dependencies(model, external, schema)) {
            if ((ctx.getFieldAccess() != null && !ctx.getFieldAccess().contains(field)) || denied.contains(field))
                throw RX.throwB("member external patch field access denied: " + field);
        }
    }

    private static Object scopedValue(Object value, String prefix) {
        if (value instanceof Map<?, ?> map && map.get("$field") instanceof String field)
            return Map.of("$field", prefix + field);
        return value;
    }

    private static void validateVisible(SyntheticMemberEffectivePermission permission, String prefix,
                                        Set<String> schema, Set<String> dependencies) {
        if (permission.getVisibleColumns() == null) return;
        Set<String> allowed = new HashSet<>();
        for (String field : permission.getVisibleColumns()) {
            String mapped = prefix + field;
            if (!schema.contains(mapped)) throw RX.throwAUserTip("child member visibleColumns contains unknown field: " + mapped);
            allowed.add(mapped);
        }
        for (String field : dependencies) if (field.startsWith(prefix) && !allowed.contains(field))
            throw RX.throwAUserTip("child member field access denied: " + field);
    }

    static void checkHierarchy(DbQueryRequestDef request, SyntheticMemberEffectivePermission permission, String prefix) {
        Consumer<CondRequestDef> check = condition -> {
            if (!Set.of("childrenOf", "descendantsOf", "ancestorsOf", "selfAndDescendantsOf", "selfAndAncestorsOf")
                    .contains(Objects.toString(condition.getOp(), ""))) return;
            if (!prefix.isEmpty() && (condition.getField() == null || !condition.getField().startsWith(prefix))) return;
            if (Boolean.FALSE.equals(permission.getHierarchyEnabled()))
                throw RX.throwAUserTip("synthetic member-QM 内部权限已禁用层级操作");
            if (permission.getAllowedHierarchyOps() != null && !permission.getAllowedHierarchyOps().contains(condition.getOp()))
                throw RX.throwAUserTip("synthetic member-QM 内部权限不允许层级操作: " + condition.getOp());
        };
        visit(request.getSlice(), check);
        visit(request.getHaving(), check);
        visit(request.getPostSlice(), check);
    }

    private static Set<String> dependencies(QueryModel model, DbQueryRequestDef request, Set<String> schema) {
        CalculatedDependencyGraph graph = CalculatedDependencyGraph.forQuery(model, request.getCalculatedFields());
        Set<String> result = new LinkedHashSet<>();
        Map<String, String> outputs = new LinkedHashMap<>();
        Consumer<String> add = expression -> {
            if (expression == null || expression.isBlank()) return;
            String bound = outputs.getOrDefault(expression, expression);
            String source = QueryFieldBindings.bind(model, bound, request.getCalculatedFields()).expression();
            result.addAll(graph.base(source));
        };
        List<String> selected = request.getColumns() == null || request.getColumns().isEmpty()
                ? model.getSelectColumns(true).stream().map(DbColumn::getName).toList() : request.getColumns();
        for (String field : selected) {
            if (request.getExColumns() != null && request.getExColumns().contains(field)) continue;
            var binding = QueryFieldBindings.bind(model, field, request.getCalculatedFields());
            outputs.putIfAbsent(binding.outputName(), binding.expression());
            add.accept(field);
        }
        if (request.getCalculatedFields() != null) request.getCalculatedFields().forEach(c -> result.addAll(graph.base(c)));
        if (request.getPostAggregateCalculations() != null) request.getPostAggregateCalculations().forEach(c -> add.accept(c.getMeasure()));
        Consumer<CondRequestDef> condition = c -> {
            add.accept(c.getField());
            add.accept(c.getExpr());
            if (c.getValue() instanceof Map<?, ?> ref && ref.get("$field") instanceof String field) add.accept(field);
        };
        visit(request.getSlice(), condition);
        visit(request.getHaving(), condition);
        visit(request.getPostSlice(), condition);
        if (request.getOrderBy() != null) request.getOrderBy().forEach(o -> add.accept(o.getField()));
        if (request.getGroupBy() != null) request.getGroupBy().forEach(g -> add.accept(g.getField()));
        return result;
    }

    private static void visit(List<? extends CondRequestDef> conditions, Consumer<CondRequestDef> action) {
        if (conditions == null) return;
        for (CondRequestDef condition : conditions) if (condition != null) {
            action.accept(condition);
            visit(condition.getAnd(), action);
            visit(condition.getOr(), action);
        }
    }
}
